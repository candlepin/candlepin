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
import org.candlepin.gutterball.model.snapshot.Compliance;

import com.google.inject.Inject;

import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.xnap.commons.i18n.I18n;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.inject.Provider;
import javax.ws.rs.core.MultivaluedMap;

/**
 * ConsumerStatusListReport
 */
public class ConsumerStatusReport extends Report<ReportResult> {

    private static final String CUSTOM_RESULTS_PARAM = "custom_results";
    private ComplianceSnapshotCurator complianceSnapshotCurator;
    private StatusReasonMessageGenerator messageGenerator;

    /**
     * @param i18nProvider
     * @param key
     * @param description
     */
    @Inject
    public ConsumerStatusReport(Provider<I18n> i18nProvider, ComplianceSnapshotCurator curator,
            StatusReasonMessageGenerator messageGenerator) {
        super(i18nProvider, "consumer_status",
                i18nProvider.get().tr("List the status of all consumers"));
        this.complianceSnapshotCurator = curator;
        this.messageGenerator = messageGenerator;
    }

    @Override
    protected void initParameters() {
        ReportParameterBuilder builder = new ReportParameterBuilder(i18n);

        addParameter(
            builder.init("consumer_uuid", i18n.tr("Filters the results by the specified consumer UUID."))
                .multiValued()
                .getParameter()
        );

        addParameter(
            builder.init("owner", i18n.tr("The Owner key(s) to filter on."))
                .multiValued()
                .getParameter());

        addParameter(
            builder.init("status", i18n.tr("The subscription status to filter on [{0}, {1}, {2}].",
                    "valid", "invalid", "partial"))
                .multiValued()
                .getParameter()
        );

        addParameter(
            builder.init("on_date", i18n.tr("The date to filter on. Defaults to NOW."))
                .mustBeDate(REPORT_DATETIME_FORMAT)
                .getParameter()
        );

        addParameter(
            builder.init(
                "management_enabled",
                i18n.tr("Filter on subscriptions which have management enabled set to this value (boolean)"))
                .getParameter()
        );

        addParameter(
            builder.init(CUSTOM_RESULTS_PARAM, i18n.tr("Enables/disables custom report result " +
                    "functionality via attribute filtering (boolean).")).getParameter());

        addParameter(builder.init("include_reasons", i18n.tr("Include status reasons in results"))
                .mustNotHave(CUSTOM_RESULTS_PARAM)
                .getParameter());

        addParameter(builder.init("include", i18n.tr("Includes the specified attribute in the result JSON"))
                .mustHave(CUSTOM_RESULTS_PARAM)
                .mustNotHave("exclude")
                .multiValued()
                .getParameter());

        addParameter(builder.init("exclude", i18n.tr("Excludes the specified attribute in the result JSON"))
                .mustHave(CUSTOM_RESULTS_PARAM)
                .mustNotHave("include")
                .multiValued()
                .getParameter());

    }

    @Override
    protected ReportResult execute(MultivaluedMap<String, String> queryParams, PageRequest pageRequest) {
        // At this point we would execute a lookup against the DW data store to formulate
        // the report result set.

        List<String> consumerIds = queryParams.get("consumer_uuid");
        List<String> statusFilters = queryParams.get("status");
        List<String> ownerFilters = queryParams.get("owner");

        Date targetDate = queryParams.containsKey("on_date") ?
            parseDateTime(queryParams.getFirst("on_date")) :
            new Date();

        Map<String, String> attributeFilters = new HashMap<String, String>();
        String managementEnabled = queryParams.getFirst("management_enabled");
        if (managementEnabled != null) {
            try {
                attributeFilters.put(
                    "management_enabled", (PropertyConverter.toBoolean(managementEnabled) ? "1" : "0")
                );
            }
            catch (ConversionException e) {
                // This shouldn't happen; but if it does, do nothing. Maybe assume false?
            }
        }

        boolean includeReasons = true;
        if (queryParams.containsKey("include_reasons")) {
            includeReasons = PropertyConverter.toBoolean(queryParams.getFirst("include_reasons"));
        }

        String custom = queryParams.containsKey(CUSTOM_RESULTS_PARAM) ?
            queryParams.getFirst(CUSTOM_RESULTS_PARAM) : "";
        boolean useCustom = PropertyConverter.toBoolean(custom);

        Page<Iterator<Compliance>> page = this.complianceSnapshotCurator.getSnapshotIterator(
            targetDate,
            consumerIds,
            ownerFilters,
            statusFilters,
            attributeFilters,
            pageRequest
        );

        ResteasyProviderFactory.pushContext(Page.class, page);

        return useCustom ?
            new ReasonGeneratingReportResult(page.getPageData(), this.messageGenerator) :
            new ConsumerStatusReportDefaultResult(page.getPageData(), includeReasons);
    }
}
