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

import static org.candlepin.gutterball.TestUtils.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.candlepin.gutterball.report.Report;
import org.candlepin.gutterball.report.ReportFactory;
import org.candlepin.gutterball.report.ReportResult;

import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;
import javax.ws.rs.core.UriInfo;

@RunWith(JukitoRunner.class)
public class ReportsResourceTest {

    @Inject
    private ReportsResource reportsResource;

    @Inject
    private UriInfo uriInfo;

    @Test
    public void testGetReports() {
        Collection<Report> reports = reportsResource.getReports();
        assertEquals(2, reports.size());
    }

    @Test
    public void testGetReportDetails() {
        Report r = reportsResource.getReportDetails("test_report_1");
        assertNotNull(r);
        assertEquals("DESC_1", r.getDescription());
    }

    @Test
    public void testExceptionThrownWhenReportNotFound() {
        try {
            reportsResource.run(uriInfo, "invalid_report_key");
            fail("Expected exception due to invalid report key");
        }
        catch (RuntimeException e) {
            assertEquals("Report invalid_report_key not found.", e.getMessage());
        }
    }

    @Test
    public void testValidReportRun() {
        ReportResult result = mock(ReportResult.class);
        when(ReportsResourceModule.MOCK_REPORT.run(uriInfo.getQueryParameters())).thenReturn(result);
        assertEquals(result, reportsResource.run(uriInfo, "test_report_1"));
    }

    public static class ReportsResourceModule extends JukitoModule {

        public static final Report MOCK_REPORT = mockReport("test_report_1", "DESC_1");

        @Override
        protected void configureTest() {
            Set<Report> reports = new HashSet<Report>();
            reports.add(MOCK_REPORT);
            reports.add(mockReport("test_report_2", "DESC_2"));

            ReportFactory factory = new ReportFactory(reports);
            bind(ReportFactory.class).toInstance(factory);
        }

    }

}
