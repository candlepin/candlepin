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

import org.candlepin.gutterball.curator.ComplianceSnapshotCurator;
import org.candlepin.gutterball.guice.I18nProvider;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.MultivaluedMap;

/**
 * ConsumerStatusListReport
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
            "status_trend_report",
            i18nProvider.get().tr("List the status trends for all consumers")
        );

        this.curator = curator;
    }

    @Override
    protected void initParameters() {
        ReportParameterBuilder builder = new ReportParameterBuilder(i18n);

        this.addParameter(
            builder.init("start_date", i18n.tr("The start date on which to filter."))
                .mustBeDate(REPORT_DATE_FORMAT)
                .getParameter()
        );

        this.addParameter(
            builder.init("end_date", i18n.tr("The end date on which to filter."))
                .mustBeDate(REPORT_DATE_FORMAT)
                .getParameter()
        );

        this.addParameter(
            builder.init("sku", i18n.tr("The entitlement sku on which to filter."))
                .mustNotHave("subscription_name", "management_enabled")
                .getParameter()
        );

        this.addParameter(
            builder.init("subscription_name", i18n.tr("The name of a subscription on which to filter."))
                .mustNotHave("sku", "management_enabled")
                .getParameter()
        );

        this.addParameter(
            builder.init(
                "management_enabled",
                i18n.tr(
                    "[Boolean] Whether or not to filter on subscriptions which have management enabled."
                )
            )
                .mustNotHave("sku", "subscription_name")
                .getParameter()
        );
    }

    @Override
    protected StatusTrendReportResult execute(MultivaluedMap<String, String> queryParams) {
        Map<String, String> attributes = new HashMap<String, String>();

        Date startDate = this.parseDate(queryParams.getFirst("start_date"));
        Date endDate = this.parseDate(queryParams.getFirst("end_date"));
        String sku = queryParams.getFirst("sku");
        String subscriptionName = queryParams.getFirst("subscription_name");

        // TODO:
        // Replace this with something to allow attributes to be specified directly.
        String managementEnabled = queryParams.getFirst("management_enabled");
        if (managementEnabled != null) {
            attributes.put("management_enabled", Boolean.parseBoolean(managementEnabled) ? "1" : "0");
        }

        Map<Date, Map<String, Long>> result;

        if (sku != null) {
            result = this.curator.getComplianceStatusCountsBySku(startDate, endDate, sku);
        }
        else if (subscriptionName != null) {
            result = this.curator.getComplianceStatusCountsBySubscription(
                startDate,
                endDate,
                subscriptionName
            );
        }
        else if (managementEnabled != null) {
            result = this.curator.getComplianceStatusCountsByAttributes(
                startDate,
                endDate,
                attributes
            );
        }
        else {
            result = this.curator.getComplianceStatusCounts(startDate, endDate);
        }

        return new StatusTrendReportResult(result);
    }

}
