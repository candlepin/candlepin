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

import static org.candlepin.gutterball.TestUtils.*;
import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.HashSet;

public class ReportFactoryTest {

    private ReportFactory factory;
    private HashSet<Report> reports;

    @Before
    public void setUpTest() {

        reports = new HashSet<Report>();
        reports.add(mockReport("test_report_1", "DESC_1"));
        reports.add(mockReport("test_report_2", "DESC_2"));
        factory = new ReportFactory(reports);
    }

    @Test
    public void ensureGetByKey() {
        Report r = factory.getReport("test_report_2");
        assertNotNull(r);
        assertEquals("DESC_2", r.getDescription());
    }

    @Test
    public void ensureGetByKeyReturnsNullWhenReportNotFound() {
        assertNull(factory.getReport("i_do_not_exist"));
    }

    @Test
    public void ensureAll() {
        Collection<Report> all = factory.getReports();
        assertEquals(2, all.size());
        assertTrue(reports.containsAll(all));
    }

}
