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

import org.candlepin.gutterball.curator.ComplianceDataCurator;
import org.candlepin.gutterball.guice.I18nProvider;

import com.google.inject.Inject;
import com.mongodb.DBObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.ws.rs.core.MultivaluedMap;

/**
 * ConsumerStatusListReport
 */
public class ConsumerStatusReport extends Report<MultiRowResult<DBObject>> {

    protected static final String REPORT_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

    private ComplianceDataCurator complianceDataCurator;

    /**
     * @param i18nProvider
     * @param key
     * @param description
     */
    @Inject
    public ConsumerStatusReport(I18nProvider i18nProvider, ComplianceDataCurator curator) {
        super(i18nProvider, "consumer_status_report",
                i18nProvider.get().tr("List the status of all consumers"));
        this.complianceDataCurator = curator;
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
            builder.init("on_date", i18n.tr("The date to filter on."))
                .mustBeDate(REPORT_DATE_FORMAT)
                .getParameter()
        );

    }

    @Override
    protected MultiRowResult<DBObject> execute(MultivaluedMap<String, String> queryParams) {
        // At this point we would execute a lookup against the DW data store to formulate
        // the report result set.
        MultiRowResult<DBObject> result = new MultiRowResult<DBObject>();

        List<String> consumerIds = queryParams.get("consumer_uuid");
        List<String> statusFilters = queryParams.get("status");
        List<String> ownerFilters = queryParams.get("owner");

        Date targetDate = queryParams.containsKey("on_date") ?
            parseDate(queryParams.getFirst("on_date")) : new Date();
        Iterable<DBObject> complianceSnapshots = complianceDataCurator.getComplianceForTimespan(
            targetDate, consumerIds, ownerFilters, statusFilters);
        for (DBObject snapshot : complianceSnapshots) {
            result.add(snapshot);
        }
        return result;
    }

    private Date parseDate(String date) {
        if (date == null || date.isEmpty()) {
            return null;
        }

        try {
            SimpleDateFormat formatter = new SimpleDateFormat(REPORT_DATE_FORMAT);
            return formatter.parse(date);
        }
        catch (ParseException e) {
            throw new RuntimeException("Could not parse date parameter.");
        }
    }
}
