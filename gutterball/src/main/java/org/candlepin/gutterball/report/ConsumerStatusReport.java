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

import org.candlepin.gutterball.guice.I18nProvider;

import com.google.inject.Inject;

import java.util.Date;

import javax.ws.rs.core.MultivaluedMap;

/**
 * ConsumerStatusListReport
 */
public class ConsumerStatusReport extends Report {

    /**
     * @param i18nProvider
     * @param key
     * @param description
     */
    @Inject
    public ConsumerStatusReport(I18nProvider i18nProvider) {
        super(i18nProvider, "consumer_status_report",
                i18nProvider.get().tr("List the status of all consumers"));
    }

    /* (non-Javadoc)
     * @see org.candlepin.gutterball.Report#validateParameters()
     */
    @Override
    protected void validateParameters(MultivaluedMap<String, String> params)
        throws ParameterValidationException {
        if (params.containsKey("hours")) {

            if (params.containsKey("start_date") || params.containsKey("end_date")) {
                throw new ParameterValidationException(
                        i18n.tr("Parameter {0} cannot be used with {1} or {2} parameters",
                                "hours", "start_date", "end_date"));
            }

            String hours = params.getFirst("hours");
            try {
                Integer.parseInt(hours);
            }
            catch (NumberFormatException nfe) {
                throw new ParameterValidationException(
                        i18n.tr("Parameter {0} must be an Integer value", "hours"));
            }
        }

        if (params.containsKey("start_date") && !params.containsKey("end_date")) {
            throw new ParameterValidationException(
                    i18n.tr("Missing required parameter {0}. Must be used with {1}",
                            "end_date", "start_date"));
        }

        if (params.containsKey("end_date") && !params.containsKey("start_date")) {
            throw new ParameterValidationException(
                    i18n.tr("Missing required parameter {0}. Must be used with {1}",
                            "start_date", "end_date"));
        }
    }

    @Override
    protected void initParameters() {
        addParameter("owner", i18n.tr("The Owner key(s) to filter on."), false, true);
        addParameter("status", i18n.tr("The subscription status to filter on."),
                false, true);
        addParameter("satalite_server", i18n.tr("The target satalite server"),
                false, false);
        addParameter("life_cycle_state",
                i18n.tr("The host life cycle state to filter on.") + " [active, inactive]",
                false, true);
        addParameter("hours",
                i18n.tr("The number of hours to filter on (used indepent of date range)."),
                false, false);
        addParameter("start_date",
                i18n.tr("The start date to filter on (used with {0}).", "end_date"),
                false, false);
        addParameter("end_date",
                i18n.tr("The end date to filter on (used with {0})", "end_date"),
                false, false);
    }

    @Override
    protected ReportResult execute(MultivaluedMap<String, String> queryParameters) {
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
