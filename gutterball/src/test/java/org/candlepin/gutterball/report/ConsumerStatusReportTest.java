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

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.gutterball.TestUtils;
import org.candlepin.gutterball.curator.ComplianceSnapshotCurator;
import org.candlepin.gutterball.guice.I18nProvider;
import org.candlepin.gutterball.model.ConsumerState;
import org.candlepin.gutterball.model.snapshot.Compliance;
import org.candlepin.gutterball.report.dto.ConsumerStatusComplianceDto;

import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MultivaluedMap;

@RunWith(JukitoRunner.class)
public class ConsumerStatusReportTest {

    public static class Module extends JukitoModule {

        @Override
        protected void configureTest() {
            bindMock(ComplianceSnapshotCurator.class);
        }

    }

    @Inject
    private HttpServletRequest mockReq;

    @Inject
    private ComplianceSnapshotCurator complianceSnapshotCurator;

    private ConsumerStatusReport report;

    @Before
    public void setUp() throws Exception {
        I18nProvider i18nProvider = new I18nProvider(mockReq);
        StatusReasonMessageGenerator messageGenerator = mock(StatusReasonMessageGenerator.class);

        Page<Iterator<Compliance>> page = new Page<Iterator<Compliance>>();
        page.setPageData((new LinkedList<Compliance>()).iterator());

        // Indentation note: This is what checkstyle actually wants. :/
        when(complianceSnapshotCurator.getSnapshotIterator(
                any(Date.class), any(List.class), any(List.class), any(List.class), any(Map.class),
                any(PageRequest.class)
        )).thenReturn(page);

        report = new ConsumerStatusReport(i18nProvider, complianceSnapshotCurator, messageGenerator);
    }

    @Test
    public void testDateFormatValidatedOnOnDateParameter() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);

        when(params.containsKey("on_date")).thenReturn(true);
        when(params.get("on_date")).thenReturn(Arrays.asList("13-21-2010"));

        validateParams(params, "on_date", "Invalid date string. Expected format: " +
                ConsumerStatusReport.REPORT_DATETIME_FORMAT);
    }

    @Test
    public void testReportOnTargetDate() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.MONTH, Calendar.APRIL);
        cal.set(Calendar.DAY_OF_MONTH, 12);

        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        String onDateString = formatDate(cal.getTime());
        when(params.containsKey("on_date")).thenReturn(true);
        when(params.getFirst("on_date")).thenReturn(onDateString);
        when(params.get("on_date")).thenReturn(Arrays.asList(onDateString));

        List<String> uuids = null;
        List<String> owners = null;
        List<String> status = null;
        PageRequest pageRequest = null;

        report.run(params, pageRequest);

        verify(complianceSnapshotCurator).getSnapshotIterator(eq(cal.getTime()),
                eq(uuids), eq(owners), eq(status), any(Map.class), eq(pageRequest));
        verifyNoMoreInteractions(complianceSnapshotCurator);
    }

    @Test
    public void testGetByOwner() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("owner")).thenReturn(true);
        when(params.get("owner")).thenReturn(Arrays.asList("o2"));

        List<String> uuids = null;
        List<String> owners = Arrays.asList("o2");
        List<String> status = null;
        PageRequest pageRequest = null;

        report.run(params, pageRequest);

        verify(complianceSnapshotCurator).getSnapshotIterator(any(Date.class),
                eq(uuids), eq(owners), eq(status), any(Map.class), eq(pageRequest));
        verifyNoMoreInteractions(complianceSnapshotCurator);
    }

    @Test
    public void testGetByStatus() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("status")).thenReturn(true);
        when(params.get("status")).thenReturn(Arrays.asList("partial"));

        List<String> uuids = null;
        List<String> owners = null;
        PageRequest pageRequest = null;

        report.run(params, pageRequest);
        verify(complianceSnapshotCurator).getSnapshotIterator(any(Date.class),
                eq(uuids), eq(owners),
                eq(Arrays.asList("partial")),
                any(Map.class),
                eq(pageRequest));
        verifyNoMoreInteractions(complianceSnapshotCurator);
    }

    @Test
    public void testGetPaginatedResults() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);

        List<String> uuids = null;
        List<String> owners = null;
        List<String> statuses = null;
        PageRequest pageRequest = new PageRequest();
        pageRequest.setPage(3);
        pageRequest.setPerPage(10);

        report.run(params, pageRequest);
        verify(complianceSnapshotCurator).getSnapshotIterator(
                any(Date.class),
                eq(uuids),
                eq(owners),
                eq(statuses),
                any(Map.class),
                eq(pageRequest)
        );
        verifyNoMoreInteractions(complianceSnapshotCurator);
    }

    @Test
    public void testDefaultResultSetContainsCustomDto() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("custom_results")).thenReturn(false);

        List<Compliance> complianceList = new LinkedList<Compliance>();
        complianceList.add(TestUtils.createComplianceSnapshot(new Date(), "abcd", "an-owner", "valid",
            new ConsumerState("abcd", "an-owner", new Date())));

        Page<Iterator<Compliance>> page = new Page<Iterator<Compliance>>();
        page.setPageData(complianceList.iterator());

        when(complianceSnapshotCurator.getSnapshotIterator(
                any(Date.class), any(List.class), any(List.class), any(List.class), any(Map.class),
                any(PageRequest.class)
        )).thenReturn(page);

        ComplianceTransformerIterator result = (ComplianceTransformerIterator) report.run(params, null);
        assertNotNull(result);
        assertTrue(result.hasNext());
        assertTrue(result.next() instanceof ConsumerStatusComplianceDto);
    }

    @Test
    public void testCustomResultSetContainsComplianceObjects() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("custom_results")).thenReturn(true);
        when(params.getFirst("custom_results")).thenReturn("1");

        List<Compliance> complianceList = new LinkedList<Compliance>();
        complianceList.add(TestUtils.createComplianceSnapshot(new Date(), "abcd", "an-owner", "valid"));

        Page<Iterator<Compliance>> page = new Page<Iterator<Compliance>>();
        page.setPageData(complianceList.iterator());

        when(complianceSnapshotCurator.getSnapshotIterator(
                any(Date.class), any(List.class), any(List.class), any(List.class),
                any(Map.class), any(PageRequest.class)
        )).thenReturn(page);

        ReasonGeneratingReportResult result = (ReasonGeneratingReportResult) report.run(params, null);
        assertNotNull(result);
        assertTrue(result.hasNext());
        assertTrue(result.next() instanceof Compliance);
    }

    private String formatDate(Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat(ConsumerStatusReport.REPORT_DATETIME_FORMAT);
        return formatter.format(date);
    }

    private void validateParams(MultivaluedMap<String, String> params, String expectedParam,
            String expectedMessage) {
        try {
            report.validateParameters(params);
            fail("Expected param validation error.");
        }
        catch (ParameterValidationException e) {
            assertEquals(expectedParam, e.getParamName());
            assertEquals(expectedParam + ": " + expectedMessage, e.getMessage());
        }
    }

}
