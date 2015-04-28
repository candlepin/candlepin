/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */

package org.candlepin.gutterball.report;

import org.candlepin.common.config.ConversionException;
import org.candlepin.common.config.PropertyConverter;
import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.gutterball.curator.ComplianceSnapshotCurator;
import org.candlepin.gutterball.guice.I18nProvider;

import com.google.inject.Inject;

import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import javax.ws.rs.core.MultivaluedMap;



/**
 * The StatusTrendReport class processes the API request to generate a status trend report.
 *
 * The status trend report shows the per-day counts of consumers, grouped by status, optionally
 * limited to a date range and/or filtered by select criteria.
 */
public class StatusTrendReport extends Report<StatusTrendReportResult> {
    private static Logger log = LoggerFactory.getLogger(StatusTrendReport.class);
    private ComplianceSnapshotCurator curator;

    /**
     * @param i18nProvider
     * @param key
     * @param description
     */
    @Inject
    public StatusTrendReport(I18nProvider i18nProvider, ComplianceSnapshotCurator curator) {
        super(
            i18nProvider,
            "status_trend",
            i18nProvider.get().tr("Lists the per-day status counts for all consumers")
        );

        this.curator = curator;
    }

    @Override
    protected void initParameters() {
        ReportParameterBuilder builder = new ReportParameterBuilder(i18n);

        ParameterValidator yearValidator = new ParameterValidator() {
            public void validate(ParameterDescriptor descriptor, String value) {
                if (value == null || !value.matches("\\A[23456789]\\d{3}-\\d{1,2}-\\d{1,2}\\z")) {
                    throw new ParameterValidationException(
                        descriptor.getName(),
                        i18n.tr("Invalid year; years must be no earlier than 2000.")
                    );
                }
            }
        };

        this.addParameter(
            builder.init("start_date", i18n.tr("The start date on which to filter"))
                .mustBeDate(REPORT_DATE_FORMAT)
                .mustSatisfy(yearValidator)
                .getParameter()
        );

        this.addParameter(
            builder.init("end_date", i18n.tr("The end date on which to filter"))
                .mustBeDate(REPORT_DATE_FORMAT)
                .mustSatisfy(yearValidator)
                .getParameter()
        );

        this.addParameter(
            builder.init("consumer_uuid", i18n.tr("The consumer UUID(s) on which to filter"))
                .multiValued()
                .getParameter()
        );

        this.addParameter(
            builder.init("owner", i18n.tr("An owner key on which to filter"))
                .getParameter()
        );

        this.addParameter(
            builder.init("sku", i18n.tr("The entitlement sku on which to filter"))
                .mustNotHave("subscription_name", "management_enabled")
                .getParameter()
        );

        this.addParameter(
            builder.init("subscription_name", i18n.tr("The name of a subscription on which to filter"))
                .mustNotHave("sku", "management_enabled")
                .getParameter()
        );

        this.addParameter(
            builder.init(
                "management_enabled",
                i18n.tr("Filter on subscriptions which have management enabled set to this value (boolean)")
            )
                .mustNotHave("sku", "subscription_name")
                .getParameter()
        );

        this.addParameter(
            builder.init(
                "timezone",
                i18n.tr("The timezone to use when processing the request and returning results")
            )
                .mustBeTimeZone()
                .getParameter()
        );
    }

    @Override
    protected StatusTrendReportResult execute(MultivaluedMap<String, String> queryParams,
        PageRequest pageRequest) {

        Map<String, String> attributes = new HashMap<String, String>();

        TimeZone timezone = this.parseTimeZone(queryParams.getFirst("timezone"));
        Date startDate = this.parseDate(queryParams.getFirst("start_date"), timezone);
        Date endDate = this.parseDate(queryParams.getFirst("end_date"), timezone);
        String ownerKey = queryParams.getFirst("owner");
        String sku = queryParams.getFirst("sku");
        String subscriptionName = queryParams.getFirst("subscription_name");
        List<String> consumerUuids = queryParams.get("consumer_uuid");

        Page<Map<Date, Map<String, Integer>>> page;
        StatusTrendReportResult output = new StatusTrendReportResult();

        // TODO:
        // Replace this with something to allow attributes to be specified directly.
        String managementEnabled = queryParams.getFirst("management_enabled");
        if (managementEnabled != null) {
            try {
                attributes.put(
                    "management_enabled", (PropertyConverter.toBoolean(managementEnabled) ? "1" : "0")
                );
            }
            catch (ConversionException e) {
                // This shouldn't happen; but if it does, do nothing. Maybe assume false?
            }
        }

        if (sku != null) {
            page = this.curator.getComplianceStatusCounts(
                startDate,
                endDate,
                ownerKey,
                consumerUuids,
                sku,
                null,
                null,
                pageRequest
            );
        }
        else if (subscriptionName != null) {
            page = this.curator.getComplianceStatusCounts(
                startDate,
                endDate,
                ownerKey,
                consumerUuids,
                null,
                subscriptionName,
                null,
                pageRequest
            );
        }
        else if (managementEnabled != null) {
            page = this.curator.getComplianceStatusCounts(
                startDate,
                endDate,
                ownerKey,
                consumerUuids,
                null,
                null,
                attributes,
                pageRequest
            );
        }
        else {
            page = this.curator.getComplianceStatusCounts(
                startDate,
                endDate,
                ownerKey,
                consumerUuids,
                null,
                null,
                null,
                pageRequest
            );
        }

        ResteasyProviderFactory.pushContext(Page.class, page);
        Map<Date, Map<String, Integer>> result = page.getPageData();

        // Process query result & convert dates
        SimpleDateFormat formatter = new SimpleDateFormat(REPORT_DATETIME_FORMAT);
        if (timezone != null) {
            formatter.setTimeZone(timezone);
        }

        for (Map.Entry<Date, Map<String, Integer>> entry : result.entrySet()) {
            output.put(formatter.format(entry.getKey()), entry.getValue());
        }

        return output;
    }

}
