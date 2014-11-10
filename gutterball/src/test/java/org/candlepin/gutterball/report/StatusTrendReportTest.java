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
import static org.mockito.Mockito.*;

import org.candlepin.common.config.MapConfiguration;
import org.candlepin.gutterball.GutterballTestingModule;
import org.candlepin.gutterball.curator.ComplianceSnapshotCurator;
import org.candlepin.gutterball.guice.I18nProvider;

import com.google.inject.Guice;
import com.google.inject.Injector;

import org.junit.Before;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MultivaluedMap;



public class StatusTrendReportTest {

    private Injector injector;
    private HttpServletRequest mockRequest;
    private ComplianceSnapshotCurator complianceSnapshotCurator;
    private I18nProvider i18nProvider;

    @Before
    public void setUp() throws Exception {
        MapConfiguration config = new MapConfiguration();
        GutterballTestingModule testingModule = new GutterballTestingModule(config);
        this.injector = Guice.createInjector(testingModule);

        this.mockRequest =  this.injector.getInstance(HttpServletRequest.class);
        this.complianceSnapshotCurator = this.injector.getInstance(ComplianceSnapshotCurator.class);

        this.i18nProvider = new I18nProvider(this.mockRequest);
    }

    @Test
    public void testDateFormatValidatedOnOnDateParameter() {
        MultivaluedMap<String, String> params;

        params = mock(MultivaluedMap.class);
        when(params.containsKey("start_date")).thenReturn(true);
        when(params.getFirst("start_date")).thenReturn("not a date");
        when(params.get("start_date")).thenReturn(Arrays.asList("not a date"));

        this.validateParams(
            params,
            "start_date",
            "Invalid date string. Expected format: " + ConsumerStatusReport.REPORT_DATE_FORMAT
        );


        params = mock(MultivaluedMap.class);
        when(params.containsKey("end_date")).thenReturn(true);
        when(params.getFirst("end_date")).thenReturn("not a date");
        when(params.get("end_date")).thenReturn(Arrays.asList("not a date"));

        this.validateParams(
            params,
            "end_date",
            "Invalid date string. Expected format: " + ConsumerStatusReport.REPORT_DATE_FORMAT
        );
    }

    @Test
    public void testParameterIncompatibility() {
        MultivaluedMap<String, String> params;

        params = mock(MultivaluedMap.class);
        when(params.containsKey("sku")).thenReturn(true);
        when(params.getFirst("sku")).thenReturn("testsku");
        when(params.containsKey("subscription_name")).thenReturn(true);
        when(params.getFirst("subscription_name")).thenReturn("test subscription");

        this.validateParams(
            params,
            "sku",
            "Parameter must not be used with subscription_name."
        );

        params = mock(MultivaluedMap.class);
        when(params.containsKey("sku")).thenReturn(true);
        when(params.getFirst("sku")).thenReturn("testsku");
        when(params.containsKey("management_enabled")).thenReturn(true);
        when(params.getFirst("management_enabled")).thenReturn("true");

        this.validateParams(
            params,
            "sku",
            "Parameter must not be used with management_enabled."
        );

        params = mock(MultivaluedMap.class);
        when(params.containsKey("subscription_name")).thenReturn(true);
        when(params.getFirst("subscription_name")).thenReturn("test subscription");
        when(params.containsKey("management_enabled")).thenReturn(true);
        when(params.getFirst("management_enabled")).thenReturn("true");

        this.validateParams(
            params,
            "subscription_name",
            "Parameter must not be used with management_enabled."
        );
    }

    private void validateParams(MultivaluedMap<String, String> params, String expectedParam,
            String expectedMessage) {

        StatusTrendReport report = new StatusTrendReport(this.i18nProvider, this.complianceSnapshotCurator);

        try {
            report.validateParameters(params);
            fail("Expected param validation error.");
        }
        catch (ParameterValidationException e) {
            assertEquals(expectedParam, e.getParamName());
            assertEquals(expectedParam + ": " + expectedMessage, e.getMessage());
        }
    }

    @Test
    public void testDefaultReporting() {
        HashMap<String, Long> testcount = new HashMap<String, Long>();
        HashMap<Date, Map<String, Long>> testoutput = new HashMap<Date, Map<String, Long>>();
        testcount.put("testcount1", 1L);
        testoutput.put(new Date(), testcount);

        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);

        ComplianceSnapshotCurator mockCSCurator = mock(ComplianceSnapshotCurator.class);
        when(mockCSCurator.getComplianceStatusCounts(null, null)).thenReturn(testoutput);

        StatusTrendReport report = new StatusTrendReport(this.i18nProvider, mockCSCurator);

        StatusTrendReportResult actual = report.run(params);
        StatusTrendReportResult expected = new StatusTrendReportResult(testoutput);

        assertEquals(expected, actual);

        verify(mockCSCurator).getComplianceStatusCounts(null, null);
        verifyNoMoreInteractions(mockCSCurator);
    }

    @Test
    public void testReportingByDate() throws Exception {
        HashMap<String, Long> testcount = new HashMap<String, Long>();
        HashMap<Date, Map<String, Long>> testoutput = new HashMap<Date, Map<String, Long>>();
        testcount.put("testcount1", 1L);
        testoutput.put(new Date(), testcount);

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        Date startDate = formatter.parse("2014-11-07");
        Date endDate = formatter.parse("2014-11-08");

        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("start_date")).thenReturn(true);
        when(params.get("start_date")).thenReturn(Arrays.asList("2014-11-07"));
        when(params.getFirst("start_date")).thenReturn("2014-11-07");
        when(params.containsKey("end_date")).thenReturn(true);
        when(params.get("end_date")).thenReturn(Arrays.asList("2014-11-08"));
        when(params.getFirst("end_date")).thenReturn("2014-11-08");

        ComplianceSnapshotCurator mockCSCurator = mock(ComplianceSnapshotCurator.class);
        when(mockCSCurator.getComplianceStatusCounts(startDate, endDate)).thenReturn(testoutput);

        StatusTrendReport report = new StatusTrendReport(this.i18nProvider, mockCSCurator);

        StatusTrendReportResult actual = report.run(params);
        StatusTrendReportResult expected = new StatusTrendReportResult(testoutput);

        assertEquals(expected, actual);

        verify(mockCSCurator).getComplianceStatusCounts(startDate, endDate);
        verifyNoMoreInteractions(mockCSCurator);
    }

    @Test
    public void testReportingBySku() {
        HashMap<String, Long> testcount = new HashMap<String, Long>();
        HashMap<Date, Map<String, Long>> testoutput = new HashMap<Date, Map<String, Long>>();
        testcount.put("testcount1", 1L);
        testoutput.put(new Date(), testcount);

        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("sku")).thenReturn(true);
        when(params.getFirst("sku")).thenReturn("testsku1");
        when(params.get("sku")).thenReturn(Arrays.asList("testsku1"));

        ComplianceSnapshotCurator mockCSCurator = mock(ComplianceSnapshotCurator.class);
        when(mockCSCurator.getComplianceStatusCountsBySku(null, null, "testsku1")).thenReturn(testoutput);

        StatusTrendReport report = new StatusTrendReport(this.i18nProvider, mockCSCurator);

        StatusTrendReportResult actual = report.run(params);
        StatusTrendReportResult expected = new StatusTrendReportResult(testoutput);

        assertEquals(expected, actual);

        verify(mockCSCurator).getComplianceStatusCountsBySku(null, null, "testsku1");
        verifyNoMoreInteractions(mockCSCurator);
    }

    @Test
    public void testReportingByDateAndSku() throws Exception {
        HashMap<String, Long> testcount = new HashMap<String, Long>();
        HashMap<Date, Map<String, Long>> testoutput = new HashMap<Date, Map<String, Long>>();
        testcount.put("testcount1", 1L);
        testoutput.put(new Date(), testcount);

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        Date startDate = formatter.parse("2014-11-07");
        Date endDate = formatter.parse("2014-11-08");

        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("start_date")).thenReturn(true);
        when(params.get("start_date")).thenReturn(Arrays.asList("2014-11-07"));
        when(params.getFirst("start_date")).thenReturn("2014-11-07");
        when(params.containsKey("end_date")).thenReturn(true);
        when(params.get("end_date")).thenReturn(Arrays.asList("2014-11-08"));
        when(params.getFirst("end_date")).thenReturn("2014-11-08");
        when(params.containsKey("sku")).thenReturn(true);
        when(params.getFirst("sku")).thenReturn("testsku1");
        when(params.get("sku")).thenReturn(Arrays.asList("testsku1"));

        ComplianceSnapshotCurator mockCSCurator = mock(ComplianceSnapshotCurator.class);
        when(mockCSCurator.getComplianceStatusCountsBySku(startDate, endDate, "testsku1"))
            .thenReturn(testoutput);

        StatusTrendReport report = new StatusTrendReport(this.i18nProvider, mockCSCurator);

        StatusTrendReportResult actual = report.run(params);
        StatusTrendReportResult expected = new StatusTrendReportResult(testoutput);

        assertEquals(expected, actual);

        verify(mockCSCurator).getComplianceStatusCountsBySku(startDate, endDate, "testsku1");
        verifyNoMoreInteractions(mockCSCurator);
    }

    @Test
    public void testReportingBySubscription() {
        HashMap<String, Long> testcount = new HashMap<String, Long>();
        HashMap<Date, Map<String, Long>> testoutput = new HashMap<Date, Map<String, Long>>();
        testcount.put("testcount1", 1L);
        testoutput.put(new Date(), testcount);

        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("subscription_name")).thenReturn(true);
        when(params.getFirst("subscription_name")).thenReturn("test product");
        when(params.get("subscription_name")).thenReturn(Arrays.asList("test product"));

        ComplianceSnapshotCurator mockCSCurator = mock(ComplianceSnapshotCurator.class);
        when(mockCSCurator.getComplianceStatusCountsBySubscription(null, null, "test product"))
            .thenReturn(testoutput);

        StatusTrendReport report = new StatusTrendReport(this.i18nProvider, mockCSCurator);

        StatusTrendReportResult actual = report.run(params);
        StatusTrendReportResult expected = new StatusTrendReportResult(testoutput);

        assertEquals(expected, actual);

        verify(mockCSCurator).getComplianceStatusCountsBySubscription(null, null, "test product");
        verifyNoMoreInteractions(mockCSCurator);
    }

    @Test
    public void testReportingByDateAndSubscription() throws Exception {
        HashMap<String, Long> testcount = new HashMap<String, Long>();
        HashMap<Date, Map<String, Long>> testoutput = new HashMap<Date, Map<String, Long>>();
        testcount.put("testcount1", 1L);
        testoutput.put(new Date(), testcount);

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        Date startDate = formatter.parse("2014-11-07");
        Date endDate = formatter.parse("2014-11-08");

        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("start_date")).thenReturn(true);
        when(params.get("start_date")).thenReturn(Arrays.asList("2014-11-07"));
        when(params.getFirst("start_date")).thenReturn("2014-11-07");
        when(params.containsKey("end_date")).thenReturn(true);
        when(params.get("end_date")).thenReturn(Arrays.asList("2014-11-08"));
        when(params.getFirst("end_date")).thenReturn("2014-11-08");
        when(params.containsKey("subscription_name")).thenReturn(true);
        when(params.getFirst("subscription_name")).thenReturn("test product");
        when(params.get("subscription_name")).thenReturn(Arrays.asList("test product"));

        ComplianceSnapshotCurator mockCSCurator = mock(ComplianceSnapshotCurator.class);
        when(mockCSCurator.getComplianceStatusCountsBySubscription(startDate, endDate, "test product"))
            .thenReturn(testoutput);

        StatusTrendReport report = new StatusTrendReport(this.i18nProvider, mockCSCurator);

        StatusTrendReportResult actual = report.run(params);
        StatusTrendReportResult expected = new StatusTrendReportResult(testoutput);

        assertEquals(expected, actual);

        verify(mockCSCurator).getComplianceStatusCountsBySubscription(startDate, endDate, "test product");
        verifyNoMoreInteractions(mockCSCurator);
    }

    @Test
    public void testReportingByManagementEnabled() {
        HashMap<String, Long> testcount = new HashMap<String, Long>();
        HashMap<Date, Map<String, Long>> testoutput = new HashMap<Date, Map<String, Long>>();
        testcount.put("testcount1", 1L);
        testoutput.put(new Date(), testcount);

        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("management_enabled")).thenReturn(true);
        when(params.getFirst("management_enabled")).thenReturn("true");
        when(params.get("management_enabled")).thenReturn(Arrays.asList("true"));

        HashMap<String, String> attributes = new HashMap<String, String>();
        attributes.put("management_enabled", "1");

        ComplianceSnapshotCurator mockCSCurator = mock(ComplianceSnapshotCurator.class);
        when(mockCSCurator.getComplianceStatusCountsByAttributes(null, null, attributes))
            .thenReturn(testoutput);

        StatusTrendReport report = new StatusTrendReport(this.i18nProvider, mockCSCurator);

        StatusTrendReportResult actual = report.run(params);
        StatusTrendReportResult expected = new StatusTrendReportResult(testoutput);

        assertEquals(expected, actual);

        verify(mockCSCurator).getComplianceStatusCountsByAttributes(null, null, attributes);
        verifyNoMoreInteractions(mockCSCurator);
    }

    @Test
    public void testReportingByDateAndManagementEnabled() throws Exception {
        HashMap<String, Long> testcount = new HashMap<String, Long>();
        HashMap<Date, Map<String, Long>> testoutput = new HashMap<Date, Map<String, Long>>();
        testcount.put("testcount1", 1L);
        testoutput.put(new Date(), testcount);

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        Date startDate = formatter.parse("2014-11-07");
        Date endDate = formatter.parse("2014-11-08");

        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("start_date")).thenReturn(true);
        when(params.get("start_date")).thenReturn(Arrays.asList("2014-11-07"));
        when(params.getFirst("start_date")).thenReturn("2014-11-07");
        when(params.containsKey("end_date")).thenReturn(true);
        when(params.get("end_date")).thenReturn(Arrays.asList("2014-11-08"));
        when(params.getFirst("end_date")).thenReturn("2014-11-08");
        when(params.containsKey("management_enabled")).thenReturn(true);
        when(params.getFirst("management_enabled")).thenReturn("true");
        when(params.get("management_enabled")).thenReturn(Arrays.asList("true"));

        HashMap<String, String> attributes = new HashMap<String, String>();
        attributes.put("management_enabled", "1");

        ComplianceSnapshotCurator mockCSCurator = mock(ComplianceSnapshotCurator.class);
        when(mockCSCurator.getComplianceStatusCountsByAttributes(startDate, endDate, attributes))
            .thenReturn(testoutput);

        StatusTrendReport report = new StatusTrendReport(this.i18nProvider, mockCSCurator);

        StatusTrendReportResult actual = report.run(params);
        StatusTrendReportResult expected = new StatusTrendReportResult(testoutput);

        assertEquals(expected, actual);

        verify(mockCSCurator).getComplianceStatusCountsByAttributes(startDate, endDate, attributes);
        verifyNoMoreInteractions(mockCSCurator);
    }
}
