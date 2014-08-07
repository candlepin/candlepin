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

package org.candlepin.gutterball.resource;

import org.candlepin.gutterball.report.Report;
import org.candlepin.gutterball.report.ReportFactory;
import org.candlepin.gutterball.report.ReportResult;

import com.google.inject.Inject;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;


/**
 * An object that defines the REST end points for running reports and accessing
 * the details of reports.
 */
@Path("/reports")
public class ReportsResource {

    private ReportFactory reportFactory;

    @Inject
    public ReportsResource(ReportFactory reportFactory) {
        this.reportFactory = reportFactory;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Report> getReports() {
        return this.reportFactory.getReports();
    }

    @GET
    @Path("{report_key}")
    @Produces(MediaType.APPLICATION_JSON)
    public Report getReportDetails(@PathParam("report_key") String reportKey) {
        return this.reportFactory.getReport(reportKey);
    }

    @Path("{report_key}/run")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public ReportResult run(@Context UriInfo uriInfo,
        @PathParam("report_key") String reportKey) {
        Report r = this.reportFactory.getReport(reportKey);
        if (r == null) {
            // TODO: Throw an appropriate exception once they are moved
            //       into candlepin-common.
            throw new RuntimeException("Report " + reportKey + " not found.");
        }
        return r.run(uriInfo.getQueryParameters());
    }

}
