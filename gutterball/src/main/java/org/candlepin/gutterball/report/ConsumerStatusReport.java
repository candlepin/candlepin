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
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.ws.rs.core.MultivaluedMap;

/**
 * ConsumerStatusListReport
 */
public class ConsumerStatusReport extends Report<MultiRowResult<ConsumerStatusReportRow>> {

    private static final String REPORT_DATE_FORMAT = "yyyy-MM-dd";

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
            builder.init("hours", i18n.tr("The number of hours to filter on (used indepent of date range)."))
                   .mustBeInteger()
                   .mustNotHave("start_date", "end_date")
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
            builder.init("start_date", i18n.tr("The start date to filter on (used with {0}).", "end_date"))
                .mustNotHave("hours")
                .mustHave("end_date")
                .isDate(REPORT_DATE_FORMAT)
                .getParameter()
        );

        addParameter(
            builder.init("end_date", i18n.tr("The end date to filter on (used with {0})", "start_date"))
                .mustNotHave("hours")
                .mustHave("start_date")
                .isDate(REPORT_DATE_FORMAT)
                .getParameter()
        );
    }

    @Override
    protected MultiRowResult<ConsumerStatusReportRow> execute(MultivaluedMap<String, String> queryParams) {
        // At this point we would execute a lookup against the DW data store to formulate
        // the report result set.
        MultiRowResult<ConsumerStatusReportRow> result = new MultiRowResult<ConsumerStatusReportRow>();

        List<String> consumerIds = queryParams.get("consumer_uuid");
        List<String> statusFilers = queryParams.get("status");
        List<String> ownerFilters = queryParams.get("owner");

        Date startDate = null;
        Date endDate = null;
        Iterable<DBObject> complianceSnapshots = null;

        // Determine if we should lookup for the last x hours.
        if (queryParams.containsKey("hours")) {
            Calendar cal = Calendar.getInstance();
            startDate = cal.getTime();

            int hours = Integer.parseInt(queryParams.getFirst("hours"));
            cal.add(Calendar.HOUR, hours * -1);
            endDate = cal.getTime();
        }
        else {
            startDate = parseDate(queryParams.getFirst("start_date"));
            endDate = parseDate(queryParams.getFirst("end_date"));
        }

        complianceSnapshots = complianceDataCurator.getComplianceForTimespan(startDate, endDate,
                consumerIds, ownerFilters, statusFilers);

        for(DBObject snapshot : complianceSnapshots) {
            // FIXME Having to do this is wacky! Let's try and fix this.
            DBObject consumer = (DBObject) snapshot.get("consumer");
            DBObject owner = (DBObject) consumer.get("owner");
            DBObject status = (DBObject) snapshot.get("status");

            result.addRow(new ConsumerStatusReportRow(
                (String)consumer.get("name"),
                (String) consumer.get("uuid"),
                (String) status.get("status"),
                (String) owner.get("displayName"),
                (Date) consumer.get("lastCheckin"))
            );
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
