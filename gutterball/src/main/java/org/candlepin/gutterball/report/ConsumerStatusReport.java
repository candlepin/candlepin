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

    @Override
    protected void initParameters() {
        ReportParameterBuilder builder = new ReportParameterBuilder(i18n);

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
                .getParameter()
        );
        addParameter(
            builder.init("end_date", i18n.tr("The end date to filter on (used with {0})", "start_date"))
                .mustNotHave("hours")
                .mustHave("start_date")
                .getParameter()
        );
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
                "ACME_Corporation",
                new Date()));
        result.addRow(new ConsumerStatusReportRow(
                "devbox3.bugsquat.net",
                "112112-1222-333",
                "Invalid",
                "ACME_Corporation",
                new Date()));
        return result;
    }

}
