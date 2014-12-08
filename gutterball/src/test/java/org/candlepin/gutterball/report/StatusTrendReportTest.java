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
import static junitparams.JUnitParamsRunner.*;

import org.candlepin.common.config.MapConfiguration;
import org.candlepin.gutterball.GutterballTestingModule;
import org.candlepin.gutterball.curator.ComplianceSnapshotCurator;
import org.candlepin.gutterball.guice.I18nProvider;

import com.google.inject.Guice;
import com.google.inject.Injector;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MultivaluedMap;



@RunWith(JUnitParamsRunner.class)
public class StatusTrendReportTest {

    private Injector injector;
    private HttpServletRequest mockRequest;
    private ComplianceSnapshotCurator complianceSnapshotCurator;
    private I18nProvider i18nProvider;

    private Date testDate;
    private String testDateString;

    @Before
    public void setUp() throws Exception {
        MapConfiguration config = new MapConfiguration();
        GutterballTestingModule testingModule = new GutterballTestingModule(config);
        this.injector = Guice.createInjector(testingModule);

        this.mockRequest =  this.injector.getInstance(HttpServletRequest.class);
        this.complianceSnapshotCurator = this.injector.getInstance(ComplianceSnapshotCurator.class);

        this.i18nProvider = new I18nProvider(this.mockRequest);

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        this.testDate = formatter.parse("2014-11-26T00:00:00.000+0000");
        this.testDateString = formatter.format(this.testDate);
    }

    @Test
    @Parameters(method = "invalidDateProvider")
    public void testDateValidationWithMalformedDate(String date, String expected) {
        MultivaluedMap<String, String> params;

        params = mock(MultivaluedMap.class);
        when(params.containsKey("start_date")).thenReturn(true);
        when(params.getFirst("start_date")).thenReturn(date);
        when(params.get("start_date")).thenReturn(Arrays.asList(date));

        this.validateParams(
            params,
            "start_date",
            expected
        );

        params = mock(MultivaluedMap.class);
        when(params.containsKey("end_date")).thenReturn(true);
        when(params.getFirst("end_date")).thenReturn(date);
        when(params.get("end_date")).thenReturn(Arrays.asList(date));

        this.validateParams(
            params,
            "end_date",
            expected
        );
    }

    public Object[] invalidDateProvider() {
        String dateFormat = ConsumerStatusReport.REPORT_DATE_FORMAT;

        return $(
            $("not a date", "Invalid date string. Expected format: " + dateFormat),
            $("2014-10-20asdlkasf", "Invalid date string. Expected format: " + dateFormat),
            $("2014-1nope0-20", "Invalid date string. Expected format: " + dateFormat),
            $("1999-10-20", "Invalid year; years must be no earlier than 2000."),
            $("2014-13-20", "Invalid date string. Expected format: " + dateFormat),
            $("2014-12-45", "Invalid date string. Expected format: " + dateFormat)
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
        HashMap<String, Integer> testcount = new HashMap<String, Integer>();
        HashMap<Date, Map<String, Integer>> testoutput = new HashMap<Date, Map<String, Integer>>();
        testcount.put("testcount1", 1);
        testoutput.put(this.testDate, testcount);

        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);

        ComplianceSnapshotCurator mockCSCurator = mock(ComplianceSnapshotCurator.class);
        when(mockCSCurator.getComplianceStatusCounts(null, null, null)).thenReturn(testoutput);

        StatusTrendReport report = new StatusTrendReport(this.i18nProvider, mockCSCurator);

        StatusTrendReportResult actual = report.run(params);
        StatusTrendReportResult expected = new StatusTrendReportResult();
        expected.put(this.testDateString, testcount);

        assertEquals(expected, actual);

        verify(mockCSCurator).getComplianceStatusCounts(null, null, null);
        verifyNoMoreInteractions(mockCSCurator);
    }

    @Test
    public void testReportingByDate() throws Exception {
        HashMap<String, Integer> testcount = new HashMap<String, Integer>();
        HashMap<Date, Map<String, Integer>> testoutput = new HashMap<Date, Map<String, Integer>>();
        testcount.put("testcount1", 1);
        testoutput.put(this.testDate, testcount);

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
        when(mockCSCurator.getComplianceStatusCounts(startDate, endDate, null)).thenReturn(testoutput);

        StatusTrendReport report = new StatusTrendReport(this.i18nProvider, mockCSCurator);

        StatusTrendReportResult actual = report.run(params);
        StatusTrendReportResult expected = new StatusTrendReportResult();
        expected.put(this.testDateString, testcount);

        assertEquals(expected, actual);

        verify(mockCSCurator).getComplianceStatusCounts(startDate, endDate, null);
        verifyNoMoreInteractions(mockCSCurator);
    }

    @Test
    public void testReportingByDateAndOwner() throws Exception {
        HashMap<String, Integer> testcount = new HashMap<String, Integer>();
        HashMap<Date, Map<String, Integer>> testoutput = new HashMap<Date, Map<String, Integer>>();
        testcount.put("testcount1", 1);
        testoutput.put(this.testDate, testcount);

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        Date startDate = formatter.parse("2014-11-07");
        Date endDate = formatter.parse("2014-11-08");
        String owner = "test_owner";

        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("start_date")).thenReturn(true);
        when(params.get("start_date")).thenReturn(Arrays.asList("2014-11-07"));
        when(params.getFirst("start_date")).thenReturn("2014-11-07");
        when(params.containsKey("end_date")).thenReturn(true);
        when(params.get("end_date")).thenReturn(Arrays.asList("2014-11-08"));
        when(params.getFirst("end_date")).thenReturn("2014-11-08");
        when(params.containsKey("owner")).thenReturn(true);
        when(params.get("owner")).thenReturn(Arrays.asList(owner));
        when(params.getFirst("owner")).thenReturn(owner);

        ComplianceSnapshotCurator mockCSCurator = mock(ComplianceSnapshotCurator.class);
        when(mockCSCurator.getComplianceStatusCounts(startDate, endDate, owner)).thenReturn(testoutput);

        StatusTrendReport report = new StatusTrendReport(this.i18nProvider, mockCSCurator);

        StatusTrendReportResult actual = report.run(params);
        StatusTrendReportResult expected = new StatusTrendReportResult();
        expected.put(this.testDateString, testcount);

        assertEquals(expected, actual);

        verify(mockCSCurator).getComplianceStatusCounts(startDate, endDate, owner);
        verifyNoMoreInteractions(mockCSCurator);
    }

    @Test
    public void testReportingBySku() {
        HashMap<String, Integer> testcount = new HashMap<String, Integer>();
        HashMap<Date, Map<String, Integer>> testoutput = new HashMap<Date, Map<String, Integer>>();
        testcount.put("testcount1", 1);
        testoutput.put(this.testDate, testcount);

        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("sku")).thenReturn(true);
        when(params.getFirst("sku")).thenReturn("testsku1");
        when(params.get("sku")).thenReturn(Arrays.asList("testsku1"));

        ComplianceSnapshotCurator mockCSCurator = mock(ComplianceSnapshotCurator.class);
        when(mockCSCurator.getComplianceStatusCountsBySku(null, null, null, "testsku1"))
            .thenReturn(testoutput);

        StatusTrendReport report = new StatusTrendReport(this.i18nProvider, mockCSCurator);

        StatusTrendReportResult actual = report.run(params);
        StatusTrendReportResult expected = new StatusTrendReportResult();
        expected.put(this.testDateString, testcount);

        assertEquals(expected, actual);

        verify(mockCSCurator).getComplianceStatusCountsBySku(null, null, null, "testsku1");
        verifyNoMoreInteractions(mockCSCurator);
    }

    @Test
    public void testReportingBySkuAndOwner() {
        HashMap<String, Integer> testcount = new HashMap<String, Integer>();
        HashMap<Date, Map<String, Integer>> testoutput = new HashMap<Date, Map<String, Integer>>();
        testcount.put("testcount1", 1);
        testoutput.put(this.testDate, testcount);

        String sku = "testsku1";
        String owner = "test_owner";

        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("sku")).thenReturn(true);
        when(params.getFirst("sku")).thenReturn(sku);
        when(params.get("sku")).thenReturn(Arrays.asList(sku));
        when(params.containsKey("owner")).thenReturn(true);
        when(params.get("owner")).thenReturn(Arrays.asList(owner));
        when(params.getFirst("owner")).thenReturn(owner);

        ComplianceSnapshotCurator mockCSCurator = mock(ComplianceSnapshotCurator.class);
        when(mockCSCurator.getComplianceStatusCountsBySku(null, null, owner, sku)).thenReturn(testoutput);

        StatusTrendReport report = new StatusTrendReport(this.i18nProvider, mockCSCurator);

        StatusTrendReportResult actual = report.run(params);
        StatusTrendReportResult expected = new StatusTrendReportResult();
        expected.put(this.testDateString, testcount);

        assertEquals(expected, actual);

        verify(mockCSCurator).getComplianceStatusCountsBySku(null, null, owner, sku);
        verifyNoMoreInteractions(mockCSCurator);
    }

    @Test
    public void testReportingByDateAndSku() throws Exception {
        HashMap<String, Integer> testcount = new HashMap<String, Integer>();
        HashMap<Date, Map<String, Integer>> testoutput = new HashMap<Date, Map<String, Integer>>();
        testcount.put("testcount1", 1);
        testoutput.put(this.testDate, testcount);

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        Date startDate = formatter.parse("2014-11-07");
        Date endDate = formatter.parse("2014-11-08");
        String sku = "testsku1";

        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("start_date")).thenReturn(true);
        when(params.get("start_date")).thenReturn(Arrays.asList("2014-11-07"));
        when(params.getFirst("start_date")).thenReturn("2014-11-07");
        when(params.containsKey("end_date")).thenReturn(true);
        when(params.get("end_date")).thenReturn(Arrays.asList("2014-11-08"));
        when(params.getFirst("end_date")).thenReturn("2014-11-08");
        when(params.containsKey("sku")).thenReturn(true);
        when(params.getFirst("sku")).thenReturn(sku);
        when(params.get("sku")).thenReturn(Arrays.asList(sku));

        ComplianceSnapshotCurator mockCSCurator = mock(ComplianceSnapshotCurator.class);
        when(mockCSCurator.getComplianceStatusCountsBySku(startDate, endDate, null, sku))
            .thenReturn(testoutput);

        StatusTrendReport report = new StatusTrendReport(this.i18nProvider, mockCSCurator);

        StatusTrendReportResult actual = report.run(params);
        StatusTrendReportResult expected = new StatusTrendReportResult();
        expected.put(this.testDateString, testcount);

        assertEquals(expected, actual);

        verify(mockCSCurator).getComplianceStatusCountsBySku(startDate, endDate, null, sku);
        verifyNoMoreInteractions(mockCSCurator);
    }

    @Test
    public void testReportingByDateOwnerAndSku() throws Exception {
        HashMap<String, Integer> testcount = new HashMap<String, Integer>();
        HashMap<Date, Map<String, Integer>> testoutput = new HashMap<Date, Map<String, Integer>>();
        testcount.put("testcount1", 1);
        testoutput.put(this.testDate, testcount);

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        Date startDate = formatter.parse("2014-11-07");
        Date endDate = formatter.parse("2014-11-08");

        String sku = "testsku1";
        String owner = "test_owner";

        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("start_date")).thenReturn(true);
        when(params.get("start_date")).thenReturn(Arrays.asList("2014-11-07"));
        when(params.getFirst("start_date")).thenReturn("2014-11-07");
        when(params.containsKey("end_date")).thenReturn(true);
        when(params.get("end_date")).thenReturn(Arrays.asList("2014-11-08"));
        when(params.getFirst("end_date")).thenReturn("2014-11-08");
        when(params.containsKey("sku")).thenReturn(true);
        when(params.getFirst("sku")).thenReturn(sku);
        when(params.get("sku")).thenReturn(Arrays.asList(sku));
        when(params.containsKey("owner")).thenReturn(true);
        when(params.get("owner")).thenReturn(Arrays.asList(owner));
        when(params.getFirst("owner")).thenReturn(owner);

        ComplianceSnapshotCurator mockCSCurator = mock(ComplianceSnapshotCurator.class);
        when(mockCSCurator.getComplianceStatusCountsBySku(startDate, endDate, owner, sku))
            .thenReturn(testoutput);

        StatusTrendReport report = new StatusTrendReport(this.i18nProvider, mockCSCurator);

        StatusTrendReportResult actual = report.run(params);
        StatusTrendReportResult expected = new StatusTrendReportResult();
        expected.put(this.testDateString, testcount);

        assertEquals(expected, actual);

        verify(mockCSCurator).getComplianceStatusCountsBySku(startDate, endDate, owner, sku);
        verifyNoMoreInteractions(mockCSCurator);
    }

    @Test
    public void testReportingBySubscription() {
        HashMap<String, Integer> testcount = new HashMap<String, Integer>();
        HashMap<Date, Map<String, Integer>> testoutput = new HashMap<Date, Map<String, Integer>>();
        testcount.put("testcount1", 1);
        testoutput.put(this.testDate, testcount);

        String subscription = "test product";

        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("subscription_name")).thenReturn(true);
        when(params.getFirst("subscription_name")).thenReturn(subscription);
        when(params.get("subscription_name")).thenReturn(Arrays.asList(subscription));

        ComplianceSnapshotCurator mockCSCurator = mock(ComplianceSnapshotCurator.class);
        when(mockCSCurator.getComplianceStatusCountsBySubscription(null, null, null, subscription))
            .thenReturn(testoutput);

        StatusTrendReport report = new StatusTrendReport(this.i18nProvider, mockCSCurator);

        StatusTrendReportResult actual = report.run(params);
        StatusTrendReportResult expected = new StatusTrendReportResult();
        expected.put(this.testDateString, testcount);

        assertEquals(expected, actual);

        verify(mockCSCurator).getComplianceStatusCountsBySubscription(null, null, null, subscription);
        verifyNoMoreInteractions(mockCSCurator);
    }

    @Test
    public void testReportingBySubscriptionAndOwner() {
        HashMap<String, Integer> testcount = new HashMap<String, Integer>();
        HashMap<Date, Map<String, Integer>> testoutput = new HashMap<Date, Map<String, Integer>>();
        testcount.put("testcount1", 1);
        testoutput.put(this.testDate, testcount);

        String subscription = "test product";
        String owner = "test_owner";

        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("subscription_name")).thenReturn(true);
        when(params.getFirst("subscription_name")).thenReturn(subscription);
        when(params.get("subscription_name")).thenReturn(Arrays.asList(subscription));
        when(params.containsKey("owner")).thenReturn(true);
        when(params.get("owner")).thenReturn(Arrays.asList(owner));
        when(params.getFirst("owner")).thenReturn(owner);

        ComplianceSnapshotCurator mockCSCurator = mock(ComplianceSnapshotCurator.class);
        when(mockCSCurator.getComplianceStatusCountsBySubscription(null, null, owner, subscription))
            .thenReturn(testoutput);

        StatusTrendReport report = new StatusTrendReport(this.i18nProvider, mockCSCurator);

        StatusTrendReportResult actual = report.run(params);
        StatusTrendReportResult expected = new StatusTrendReportResult();
        expected.put(this.testDateString, testcount);

        assertEquals(expected, actual);

        verify(mockCSCurator).getComplianceStatusCountsBySubscription(null, null, owner, subscription);
        verifyNoMoreInteractions(mockCSCurator);
    }

    @Test
    public void testReportingByDateAndSubscription() throws Exception {
        HashMap<String, Integer> testcount = new HashMap<String, Integer>();
        HashMap<Date, Map<String, Integer>> testoutput = new HashMap<Date, Map<String, Integer>>();
        testcount.put("testcount1", 1);
        testoutput.put(this.testDate, testcount);

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        Date startDate = formatter.parse("2014-11-07");
        Date endDate = formatter.parse("2014-11-08");
        String subscription = "test product";

        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("start_date")).thenReturn(true);
        when(params.get("start_date")).thenReturn(Arrays.asList("2014-11-07"));
        when(params.getFirst("start_date")).thenReturn("2014-11-07");
        when(params.containsKey("end_date")).thenReturn(true);
        when(params.get("end_date")).thenReturn(Arrays.asList("2014-11-08"));
        when(params.getFirst("end_date")).thenReturn("2014-11-08");
        when(params.containsKey("subscription_name")).thenReturn(true);
        when(params.getFirst("subscription_name")).thenReturn(subscription);
        when(params.get("subscription_name")).thenReturn(Arrays.asList(subscription));

        ComplianceSnapshotCurator mockCSCurator = mock(ComplianceSnapshotCurator.class);
        when(mockCSCurator.getComplianceStatusCountsBySubscription(startDate, endDate, null, subscription))
            .thenReturn(testoutput);

        StatusTrendReport report = new StatusTrendReport(this.i18nProvider, mockCSCurator);

        StatusTrendReportResult actual = report.run(params);
        StatusTrendReportResult expected = new StatusTrendReportResult();
        expected.put(this.testDateString, testcount);

        assertEquals(expected, actual);

        verify(mockCSCurator).getComplianceStatusCountsBySubscription(startDate, endDate, null, subscription);
        verifyNoMoreInteractions(mockCSCurator);
    }

    @Test
    public void testReportingByDateOwnerAndSubscription() throws Exception {
        HashMap<String, Integer> testcount = new HashMap<String, Integer>();
        HashMap<Date, Map<String, Integer>> testoutput = new HashMap<Date, Map<String, Integer>>();
        testcount.put("testcount1", 1);
        testoutput.put(this.testDate, testcount);

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        Date startDate = formatter.parse("2014-11-07");
        Date endDate = formatter.parse("2014-11-08");
        String subscription = "test product";
        String owner = "test_owner";

        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("start_date")).thenReturn(true);
        when(params.get("start_date")).thenReturn(Arrays.asList("2014-11-07"));
        when(params.getFirst("start_date")).thenReturn("2014-11-07");
        when(params.containsKey("end_date")).thenReturn(true);
        when(params.get("end_date")).thenReturn(Arrays.asList("2014-11-08"));
        when(params.getFirst("end_date")).thenReturn("2014-11-08");
        when(params.containsKey("subscription_name")).thenReturn(true);
        when(params.getFirst("subscription_name")).thenReturn(subscription);
        when(params.get("subscription_name")).thenReturn(Arrays.asList(subscription));
        when(params.containsKey("owner")).thenReturn(true);
        when(params.get("owner")).thenReturn(Arrays.asList(owner));
        when(params.getFirst("owner")).thenReturn(owner);

        ComplianceSnapshotCurator mockCSCurator = mock(ComplianceSnapshotCurator.class);
        when(mockCSCurator.getComplianceStatusCountsBySubscription(startDate, endDate, owner, subscription))
            .thenReturn(testoutput);

        StatusTrendReport report = new StatusTrendReport(this.i18nProvider, mockCSCurator);

        StatusTrendReportResult actual = report.run(params);
        StatusTrendReportResult expected = new StatusTrendReportResult();
        expected.put(this.testDateString, testcount);

        assertEquals(expected, actual);

        verify(mockCSCurator)
            .getComplianceStatusCountsBySubscription(startDate, endDate, owner, subscription);

        verifyNoMoreInteractions(mockCSCurator);
    }

    @Test
    public void testReportingByManagementEnabled() {
        HashMap<String, Integer> testcount = new HashMap<String, Integer>();
        HashMap<Date, Map<String, Integer>> testoutput = new HashMap<Date, Map<String, Integer>>();
        testcount.put("testcount1", 1);
        testoutput.put(this.testDate, testcount);

        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("management_enabled")).thenReturn(true);
        when(params.getFirst("management_enabled")).thenReturn("true");
        when(params.get("management_enabled")).thenReturn(Arrays.asList("true"));

        HashMap<String, String> attributes = new HashMap<String, String>();
        attributes.put("management_enabled", "1");

        ComplianceSnapshotCurator mockCSCurator = mock(ComplianceSnapshotCurator.class);
        when(mockCSCurator.getComplianceStatusCountsByAttributes(null, null, null, attributes))
            .thenReturn(testoutput);

        StatusTrendReport report = new StatusTrendReport(this.i18nProvider, mockCSCurator);

        StatusTrendReportResult actual = report.run(params);
        StatusTrendReportResult expected = new StatusTrendReportResult();
        expected.put(this.testDateString, testcount);

        assertEquals(expected, actual);

        verify(mockCSCurator).getComplianceStatusCountsByAttributes(null, null, null, attributes);
        verifyNoMoreInteractions(mockCSCurator);
    }

    @Test
    public void testReportingByOwnerAndManagementEnabled() {
        HashMap<String, Integer> testcount = new HashMap<String, Integer>();
        HashMap<Date, Map<String, Integer>> testoutput = new HashMap<Date, Map<String, Integer>>();
        testcount.put("testcount1", 1);
        testoutput.put(this.testDate, testcount);

        String owner = "test_owner";

        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("management_enabled")).thenReturn(true);
        when(params.getFirst("management_enabled")).thenReturn("true");
        when(params.get("management_enabled")).thenReturn(Arrays.asList("true"));
        when(params.containsKey("owner")).thenReturn(true);
        when(params.get("owner")).thenReturn(Arrays.asList(owner));
        when(params.getFirst("owner")).thenReturn(owner);

        HashMap<String, String> attributes = new HashMap<String, String>();
        attributes.put("management_enabled", "1");

        ComplianceSnapshotCurator mockCSCurator = mock(ComplianceSnapshotCurator.class);
        when(mockCSCurator.getComplianceStatusCountsByAttributes(null, null, owner, attributes))
            .thenReturn(testoutput);

        StatusTrendReport report = new StatusTrendReport(this.i18nProvider, mockCSCurator);

        StatusTrendReportResult actual = report.run(params);
        StatusTrendReportResult expected = new StatusTrendReportResult();
        expected.put(this.testDateString, testcount);

        assertEquals(expected, actual);

        verify(mockCSCurator).getComplianceStatusCountsByAttributes(null, null, owner, attributes);
        verifyNoMoreInteractions(mockCSCurator);
    }

    @Test
    public void testReportingByDateAndManagementEnabled() throws Exception {
        HashMap<String, Integer> testcount = new HashMap<String, Integer>();
        HashMap<Date, Map<String, Integer>> testoutput = new HashMap<Date, Map<String, Integer>>();
        testcount.put("testcount1", 1);
        testoutput.put(this.testDate, testcount);

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
        when(mockCSCurator.getComplianceStatusCountsByAttributes(startDate, endDate, null, attributes))
            .thenReturn(testoutput);

        StatusTrendReport report = new StatusTrendReport(this.i18nProvider, mockCSCurator);

        StatusTrendReportResult actual = report.run(params);
        StatusTrendReportResult expected = new StatusTrendReportResult();
        expected.put(this.testDateString, testcount);

        assertEquals(expected, actual);

        verify(mockCSCurator).getComplianceStatusCountsByAttributes(startDate, endDate, null, attributes);
        verifyNoMoreInteractions(mockCSCurator);
    }

    @Test
    public void testReportingByDateOwnerAndManagementEnabled() throws Exception {
        HashMap<String, Integer> testcount = new HashMap<String, Integer>();
        HashMap<Date, Map<String, Integer>> testoutput = new HashMap<Date, Map<String, Integer>>();
        testcount.put("testcount1", 1);
        testoutput.put(this.testDate, testcount);

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        Date startDate = formatter.parse("2014-11-07");
        Date endDate = formatter.parse("2014-11-08");
        String owner = "test_owner";

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
        when(params.containsKey("owner")).thenReturn(true);
        when(params.get("owner")).thenReturn(Arrays.asList(owner));
        when(params.getFirst("owner")).thenReturn(owner);

        HashMap<String, String> attributes = new HashMap<String, String>();
        attributes.put("management_enabled", "1");

        ComplianceSnapshotCurator mockCSCurator = mock(ComplianceSnapshotCurator.class);
        when(mockCSCurator.getComplianceStatusCountsByAttributes(startDate, endDate, owner, attributes))
            .thenReturn(testoutput);

        StatusTrendReport report = new StatusTrendReport(this.i18nProvider, mockCSCurator);

        StatusTrendReportResult actual = report.run(params);
        StatusTrendReportResult expected = new StatusTrendReportResult();
        expected.put(this.testDateString, testcount);

        assertEquals(expected, actual);

        verify(mockCSCurator).getComplianceStatusCountsByAttributes(startDate, endDate, owner, attributes);
        verifyNoMoreInteractions(mockCSCurator);
    }

    @Test
    public void testReportingWithTimeZoneAdjustmnet() throws Exception {
        HashMap<String, Integer> testcount = new HashMap<String, Integer>();
        HashMap<Date, Map<String, Integer>> testoutput = new HashMap<Date, Map<String, Integer>>();
        testcount.put("testcount1", 1);
        testoutput.put(this.testDate, testcount);

        String tzString = "GMT+1400";
        TimeZone timezone = TimeZone.getTimeZone(tzString);

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        formatter.setTimeZone(timezone);
        String dateString = formatter.format(this.testDate);

        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("timezone")).thenReturn(true);
        when(params.get("timezone")).thenReturn(Arrays.asList(tzString));
        when(params.getFirst("timezone")).thenReturn(tzString);

        ComplianceSnapshotCurator mockCSCurator = mock(ComplianceSnapshotCurator.class);
        when(mockCSCurator.getComplianceStatusCounts(null, null, null)).thenReturn(testoutput);

        StatusTrendReport report = new StatusTrendReport(this.i18nProvider, mockCSCurator);

        StatusTrendReportResult actual = report.run(params);
        StatusTrendReportResult expected = new StatusTrendReportResult();
        expected.put(dateString, testcount);


        assertEquals(expected, actual);

        verify(mockCSCurator).getComplianceStatusCounts(null, null, null);
        verifyNoMoreInteractions(mockCSCurator);
    }
}
