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

import java.util.Date;

import javax.ws.rs.core.MultivaluedMap;

import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * ConsumerStatusListReport
 */
public class ConsumerStatusReport extends Report {

    private I18n i18n;

    /**
     * @param key
     * @param description
     */
    @Inject
    public ConsumerStatusReport(Provider<I18n> i18nProvider) {
        super("consumer_status_report", i18nProvider.get().tr("List the status of all consumers"));
        this.i18n = i18nProvider.get();
    }

    /* (non-Javadoc)
     * @see org.candlepin.gutterball.Report#validateParameters()
     */
    @Override
    public void validateParameters(MultivaluedMap<String, String> params) {
        if (params.containsKey("hours")) {

            if (params.containsKey("start_date") || params.containsKey("end_date")) {
                throw new RuntimeException("Invalid parameter 'hours'. Can not be used " +
                        "with 'start_date' or 'end_date' paramters");
            }

            String hours = params.getFirst("hours");
            try {
                Integer.parseInt(hours);
            }
            catch (NumberFormatException nfe) {
                throw new RuntimeException("Invalid paramter 'hours'. Must be an Integer value.");
            }
        }

        if (params.containsKey("start_date") && !params.containsKey("end_date")) {
            throw new RuntimeException("Missing parameter 'end_date'.");
        }

        if (params.containsKey("end_date") && !params.containsKey("start_date")) {
            throw new RuntimeException("Missing parameter 'start_date'.");
        }
    }

    @Override
    protected void initParameters() {
        addParameter("owner", "The Owner key(s) to filter on.", false, true);
        addParameter("status", "The subscription status to filter on.",
                false, true);
        addParameter("satalite_server", "The target satalite server",
                false, false);
        addParameter("life_cycle_state", "The host life cycle state to filter on. " +
                "[active, inactive]", false, true);
        addParameter("hours", "The number of hours filter on (used indepent of date" +
                " range).", false, false);
        addParameter("start_date", "The start date to filter on (used with end_date).",
                false, false);
        addParameter("end_date", "The end date to filter on (used with start_date)",
                false, false);
    }

    public ReportResult run(MultivaluedMap<String, String> queryParameters) {
        // At this point we would execute a lookup against the DW data store to formulate
        // the report result set.
        //
        // FIXME: Hard coded result data.
        MultiRowResult<ConsumerStatusReportRow> result = new MultiRowResult<ConsumerStatusReportRow>();
        result.addRow(new ConsumerStatusReportRow(
                "devbox.bugsquat.net",
                "112112-1221-23-3",
                "Current",
                "dhcp-8-29-250.lab.eng.rdu2.redhat.com",
                "ACME_Corporation",
                new Date(),
                "Active"));
        result.addRow(new ConsumerStatusReportRow(
                "devbox3.bugsquat.net",
                "112112-1222-333",
                "Invalid",
                "dhcp-8-29-250.lab.eng.rdu2.redhat.com",
                "ACME_Corporation",
                new Date(),
                "Inactive"));
        return result;
    }

}
