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
import static junitparams.JUnitParamsRunner.*;

import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.gutterball.TestUtils;
import org.candlepin.gutterball.curator.ComplianceSnapshotCurator;
import org.candlepin.gutterball.guice.I18nProvider;
import org.candlepin.gutterball.model.ConsumerState;
import org.candlepin.gutterball.model.snapshot.Compliance;
import org.candlepin.gutterball.report.dto.ConsumerStatusComplianceDto;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MultivaluedMap;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;



@RunWith(JUnitParamsRunner.class)
public class ConsumerStatusReportTest {
    /**
     * Kludge implementation so we don't have to endlessly mock this class.
     *
     * If/when we get the Java EE 7 classes, this can be removed and we can simply import
     * javax.ws.rs.core.MultivaluedHashMap.
     */
    private class MultivaluedHashMap<K, V> extends HashMap<K, List<V>> implements MultivaluedMap<K, V> {
        public void add(K key, V value) {
            List<V> values = this.get(key);

            if (values == null) {
                values = new LinkedList<V>();
                this.put(key, values);
            }

            values.add(value);
        }

        public V getFirst(K key) {
            List<V> values = this.get(key);
            return values != null && !values.isEmpty() ? values.get(0) : null;
        }

        public void putSingle(K key, V value) {
            this.remove(key);
            this.add(key, value);
        }
    }



    private HttpServletRequest mockReq = mock(HttpServletRequest.class);

    private ComplianceSnapshotCurator complianceSnapshotCurator;

    private ConsumerStatusReport report;

    @Before
    public void setUp() throws Exception {
        I18nProvider i18nProvider = new I18nProvider(this.mockReq);
        StatusReasonMessageGenerator messageGenerator = mock(StatusReasonMessageGenerator.class);

        Page<Iterator<Compliance>> page = new Page<Iterator<Compliance>>();
        page.setPageData((new LinkedList<Compliance>()).iterator());

        this.complianceSnapshotCurator = mock(ComplianceSnapshotCurator.class);

        // Indentation note: This is what checkstyle actually wants. :/
        when(complianceSnapshotCurator.getSnapshotIterator(
            any(Date.class), any(List.class), any(List.class), any(List.class), any(List.class),
            any(List.class), any(List.class), any(Map.class), any(PageRequest.class)
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
    @Parameters(method = "buildParamsForRunReportTest")
    public void testRunReport(String targetDate, List<String> consumerIds, List<String> ownerFilters,
        List<String> statusFilters, List<String> productNameFilters, List<String> subSkuFilters,
        List<String> subNameFilters, Boolean managementEnabled) {

        MultivaluedMap<String, String> params = new MultivaluedHashMap<String, String>();
        Map<String, String> attributeFilters = new HashMap<String, String>();
        Date expectedDate = null;

        if (targetDate != null) {
            params.add("on_date", targetDate);
            expectedDate = report.parseDateTime(targetDate);
        }

        if (consumerIds != null && !consumerIds.isEmpty()) {
            params.put("consumer_uuid", consumerIds);
        }

        if (ownerFilters != null && !ownerFilters.isEmpty()) {
            params.put("owner", ownerFilters);
        }

        if (statusFilters != null && !statusFilters.isEmpty()) {
            params.put("status", statusFilters);
        }

        if (productNameFilters != null && !productNameFilters.isEmpty()) {
            params.put("product_name", productNameFilters);
        }

        if (subSkuFilters != null && !subSkuFilters.isEmpty()) {
            params.put("sku", subSkuFilters);
        }

        if (subNameFilters != null && !subNameFilters.isEmpty()) {
            params.put("subscription_name", subNameFilters);
        }

        if (managementEnabled != null) {
            params.add("management_enabled", managementEnabled ? "true" : "false");
            attributeFilters.put("management_enabled", managementEnabled ? "1" : "0");
        }

        PageRequest pageRequest = null;

        report.run(params, pageRequest);

        verify(complianceSnapshotCurator).getSnapshotIterator(
            expectedDate != null ? eq(expectedDate) : any(Date.class), eq(consumerIds),
            eq(ownerFilters), eq(statusFilters), eq(productNameFilters), eq(subSkuFilters),
            eq(subNameFilters), eq(attributeFilters), eq(pageRequest)
        );

        verifyNoMoreInteractions(complianceSnapshotCurator);
    }

    public Object[][] buildParamsForRunReportTest() {

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.MONTH, Calendar.APRIL);
        cal.set(Calendar.DAY_OF_MONTH, 12);

        String onDateString = formatDate(cal.getTime());
        List<String> consumerIdFilters = Arrays.asList("consumer1");
        List<String> ownerFilters = Arrays.asList("owner1");
        List<String> statusFilters = Arrays.asList("valid");
        List<String> productNameFilters = Arrays.asList("prod1");
        List<String> subSkuFilters = Arrays.asList("sku1");
        List<String> subNameFilters = Arrays.asList("sub1");

        List<String> consumerIdFiltersMulti = Arrays.asList("consumer1", "consumer2", "consumer3");
        List<String> ownerFiltersMulti = Arrays.asList("owner1", "owner2", "owner3");
        List<String> statusFiltersMulti = Arrays.asList("valid", "partial", "non-compliant");
        List<String> productNameFiltersMulti = Arrays.asList("prod1", "prod2", "prod3");
        List<String> subSkuFiltersMulti = Arrays.asList("sku1", "sku2", "sku3");
        List<String> subNameFiltersMulti = Arrays.asList("sub1", "sub2", "sub3");

        Object[][] params = this.buildArray(
            null,
            new Object[] { null, onDateString },
            new Object[] { null, consumerIdFilters, consumerIdFiltersMulti },
            new Object[] { null, ownerFilters, ownerFiltersMulti },
            new Object[] { null, statusFilters, statusFiltersMulti },
            new Object[] { null, productNameFilters, productNameFiltersMulti },
            new Object[] { null, subSkuFilters, subSkuFiltersMulti },
            new Object[] { null, subNameFilters, subNameFiltersMulti },
            new Object[] { null, Boolean.TRUE, Boolean.FALSE }
        );

        return params;
    }


    private Object[][] buildArray(Object[] base, Object[]... lists) {
        if (lists == null) {
            throw new IllegalArgumentException();
        }

        if (base == null) {
            base = new Object[0];
        }

        if (lists.length == 0) {
            // We're done! base contains our completed row.
            Object[][] ret = new Object[1][];
            ret[0] = base;

            return ret;
        }
        else {
            if (lists[0].length == 0) {
                throw new IllegalArgumentException("lists contains an empty list");
            }

            int resultCount = 1;

            for (int r = 0; r < lists.length; ++r) {
                resultCount *= lists[r].length;
            }

            if (resultCount == 0) {
                throw new IllegalArgumentException("lists contains an empty list");
            }

            Object[][] results = new Object[resultCount][];
            Object[][] listSubset = Arrays.copyOfRange(lists, 1, lists.length);
            int offset = 0;

            for (Object value : lists[0]) {
                Object[] bcopy = Arrays.copyOf(base, base.length + 1);
                bcopy[base.length] = value;

                for (Object[] row : this.buildArray(bcopy, listSubset)) {
                    results[offset++] = row;
                }
            }

            return results;
        }
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
            any(Date.class), eq(uuids), eq(owners), eq(statuses), any(List.class),
            any(List.class), any(List.class), any(Map.class), eq(pageRequest)
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
                any(Date.class), any(List.class), any(List.class), any(List.class), any(List.class),
                any(List.class), any(List.class), any(Map.class), any(PageRequest.class)
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
                any(Date.class), any(List.class), any(List.class), any(List.class), any(List.class),
                any(List.class), any(List.class), any(Map.class), any(PageRequest.class)
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
