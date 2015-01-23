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
import org.candlepin.gutterball.model.snapshot.Compliance;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.inject.Provider;
import javax.ws.rs.core.MultivaluedMap;

/**
 * ConsumerStatusListReport
 */
public class ConsumerStatusReport extends Report<ConsumerStatusReportResult> {
    private static Logger log = LoggerFactory.getLogger(ConsumerStatusReport.class);

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
            builder.init("status", i18n.tr("The subscription status to filter on."))
                .multiValued()
                .getParameter()
        );

        addParameter(
            builder.init("on_date", i18n.tr("The date to filter on. Defaults to NOW."))
                .mustBeDate(REPORT_DATETIME_FORMAT)
                .getParameter()
        );

        addParameter(
            builder.init("page", i18n.tr("The page at which to begin retrieving results."))
                .mustBeInteger()
                .getParameter()
        );

        addParameter(
            builder.init("per_page", i18n.tr("The number of results to return per page."))
                .mustBeInteger()
                .getParameter()
        );

    }

    @Override
    protected ConsumerStatusReportResult execute(MultivaluedMap<String, String> queryParams) {
        // At this point we would execute a lookup against the DW data store to formulate
        // the report result set.

        List<String> consumerIds = queryParams.get("consumer_uuid");
        List<String> statusFilters = queryParams.get("status");
        List<String> ownerFilters = queryParams.get("owner");

        Date targetDate = queryParams.containsKey("on_date") ?
            parseDateTime(queryParams.getFirst("on_date")) :
            new Date();

        // Pagination stuff
        int page = queryParams.containsKey("page") ?
            Integer.parseInt(queryParams.getFirst("page")) :
            1;

        int perPage = queryParams.containsKey("per_page") ?
            Integer.parseInt(queryParams.getFirst("per_page")) :
            100;

        int offset = 0;
        int results = 0;

        if (page > 0 && perPage > 0) {
            offset = (page - 1) * perPage;
            results = perPage;
        }

        Iterator<Compliance> iterator = this.complianceSnapshotCurator.getSnapshotIterator(
            targetDate,
            consumerIds,
            ownerFilters,
            statusFilters,
            offset,
            results
        );

        return new ConsumerStatusReportResult(iterator, this.messageGenerator);
    }
}
