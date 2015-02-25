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

import static org.candlepin.gutterball.TestUtils.createComplianceSnapshot;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.candlepin.gutterball.DatabaseTestFixture;
import org.candlepin.gutterball.guice.I18nProvider;
import org.candlepin.gutterball.model.ConsumerState;
import org.candlepin.gutterball.model.snapshot.Compliance;
import org.candlepin.gutterball.report.dto.ConsumerTrendComplianceDto;

import org.jukito.JukitoRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MultivaluedMap;

/**
 * Test ConsumerTrendReport
 */
@RunWith(JukitoRunner.class)
public class ConsumerTrendReportTest extends DatabaseTestFixture {

    @Inject
    private HttpServletRequest mockReq;

    private ConsumerTrendReport report;

    @Before
    public void setUp() throws Exception {
        I18nProvider i18nProvider = new I18nProvider(mockReq);
        StatusReasonMessageGenerator messageGenerator = mock(StatusReasonMessageGenerator.class);
        report = new ConsumerTrendReport(i18nProvider, complianceSnapshotCurator, messageGenerator);
    }

    @Before
    public void initData() {
        Calendar cal = Calendar.getInstance();

        // Set up deleted consumer test data
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.MONTH, Calendar.MARCH);
        cal.set(Calendar.DAY_OF_MONTH, 10);
        cal.set(Calendar.HOUR, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);

        // Consumer created
        createInitialConsumer(cal.getTime(), "c1", "o1", "invalid");

        // Simulate status change
        cal.set(Calendar.MONTH, Calendar.MAY);
        createSnapshot(cal.getTime(), "c1", "o1", "valid");

        // Consumer was deleted
        cal.set(Calendar.MONTH, Calendar.JUNE);
        setConsumerDeleted(cal.getTime(), "c1", "o1");

        cal.set(Calendar.MONTH, Calendar.APRIL);
        createInitialConsumer(cal.getTime(), "c2", "o1", "invalid");

        cal.set(Calendar.MONTH, Calendar.MAY);
        createInitialConsumer(cal.getTime(), "c3", "o2", "invalid");
        cal.set(Calendar.MONTH, Calendar.JUNE);
        createSnapshot(cal.getTime(), "c3", "o2", "partial");

        cal.set(Calendar.MONTH, Calendar.MAY);
        createInitialConsumer(cal.getTime(), "c4", "o3", "invalid");
        cal.set(Calendar.MONTH, Calendar.JUNE);
        createSnapshot(cal.getTime(), "c4", "o3", "partial");
        cal.set(Calendar.MONTH, Calendar.JULY);
        createSnapshot(cal.getTime(), "c4", "o3", "valid");
    }

    @Test
    public void testConsumerUUIDRequirement() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);

        validateParams(params, "consumer_uuid", "Required parameter.");

        when(params.containsKey("consumer_uuid")).thenReturn(true);
        when(params.get("consumer_uuid")).thenReturn(Arrays.asList("completely-random-uuid"));
        when(params.getFirst("consumer_uuid")).thenReturn("completely-random-uuid");

        report.validateParameters(params);
    }

    @Test
    public void testDateFormatValidatedOnStartDateParameter() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);

        when(params.containsKey("consumer_uuid")).thenReturn(true);
        when(params.get("consumer_uuid")).thenReturn(Arrays.asList("completely-random-uuid"));
        when(params.getFirst("consumer_uuid")).thenReturn("completely-random-uuid");

        when(params.containsKey("start_date")).thenReturn(true);
        when(params.get("start_date")).thenReturn(Arrays.asList("13-21-2010"));

        validateParams(params, "start_date", "Invalid date string. Expected format: " +
                ConsumerTrendReport.REPORT_DATETIME_FORMAT);
    }

    @Test
    public void testDateFormatValidatedOnEndDateParameter() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);

        when(params.containsKey("consumer_uuid")).thenReturn(true);
        when(params.get("consumer_uuid")).thenReturn(Arrays.asList("completely-random-uuid"));
        when(params.getFirst("consumer_uuid")).thenReturn("completely-random-uuid");

        when(params.containsKey("end_date")).thenReturn(true);
        when(params.get("end_date")).thenReturn(Arrays.asList("13-21-2010"));

        validateParams(params, "end_date", "Invalid date string. Expected format: " +
                ConsumerTrendReport.REPORT_DATETIME_FORMAT);
    }

    @Test
    public void testStartDateMustHaveValidatedOnEndDateParameter() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);

        when(params.containsKey("consumer_uuid")).thenReturn(true);
        when(params.get("consumer_uuid")).thenReturn(Arrays.asList("completely-random-uuid"));
        when(params.getFirst("consumer_uuid")).thenReturn("completely-random-uuid");

        when(params.containsKey("start_date")).thenReturn(true);
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.MONTH, Calendar.APRIL);
        cal.set(Calendar.DAY_OF_MONTH, 12);
        when(params.get("start_date")).thenReturn(Arrays.asList(formatDate(cal.getTime())));

        validateParams(params, "start_date", "Parameter must be used with end_date.");
    }

    @Test
    public void testEndDateMustHaveValidatedOnStartDateParameter() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);

        when(params.containsKey("consumer_uuid")).thenReturn(true);
        when(params.get("consumer_uuid")).thenReturn(Arrays.asList("completely-random-uuid"));
        when(params.getFirst("consumer_uuid")).thenReturn("completely-random-uuid");

        when(params.containsKey("end_date")).thenReturn(true);
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.MONTH, Calendar.APRIL);
        cal.set(Calendar.DAY_OF_MONTH, 12);
        when(params.get("end_date")).thenReturn(Arrays.asList(formatDate(cal.getTime())));

        validateParams(params, "end_date", "Parameter must be used with start_date.");
    }

    @Test
    public void testReportOnTargetDate() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.MONTH, Calendar.APRIL);
        cal.set(Calendar.DAY_OF_MONTH, 12);

        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        String onDateString = formatDate(cal.getTime());
        when(params.containsKey("consumer_uuid")).thenReturn(true);
        when(params.get("consumer_uuid")).thenReturn(Arrays.asList("c2"));
        when(params.getFirst("consumer_uuid")).thenReturn("c2");
        when(params.containsKey("start_date")).thenReturn(true);
        when(params.getFirst("start_date")).thenReturn(onDateString);
        when(params.get("start_date")).thenReturn(Arrays.asList(onDateString));
        when(params.containsKey("end_date")).thenReturn(true);
        when(params.getFirst("end_date")).thenReturn(onDateString);
        when(params.get("end_date")).thenReturn(Arrays.asList(onDateString));

        ConsumerTrendReportDefaultResult result = (ConsumerTrendReportDefaultResult) report.run(params, null);

        int received = 0;
        while (result.hasNext()) {
            ConsumerTrendComplianceDto compliance = result.next();
            Map<String, Object> consumer = (Map<String, Object>) compliance.get("consumer");
            assertEquals("c2", consumer.get("uuid"));

            ++received;
        }

        assertEquals(1, received);
    }

    @Test
    public void testReportOnDateRange() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.MONTH, Calendar.APRIL);
        cal.set(Calendar.DAY_OF_MONTH, 12);

        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        String onDateString = formatDate(cal.getTime());
        String endDateString = formatDate(new Date());
        when(params.containsKey("consumer_uuid")).thenReturn(true);
        when(params.get("consumer_uuid")).thenReturn(Arrays.asList("c4"));
        when(params.getFirst("consumer_uuid")).thenReturn("c4");
        when(params.containsKey("start_date")).thenReturn(true);
        when(params.getFirst("start_date")).thenReturn(onDateString);
        when(params.get("start_date")).thenReturn(Arrays.asList(onDateString));
        when(params.containsKey("end_date")).thenReturn(true);
        when(params.getFirst("end_date")).thenReturn(endDateString);
        when(params.get("end_date")).thenReturn(Arrays.asList(endDateString));

        ConsumerTrendReportDefaultResult result = (ConsumerTrendReportDefaultResult) report.run(params, null);

        int received = 0;
        while (result.hasNext()) {
            ConsumerTrendComplianceDto compliance = result.next();
            Map<String, Object> consumer = (Map<String, Object>) compliance.get("consumer");
            assertEquals("c4", consumer.get("uuid"));

            ++received;
        }

        assertEquals(3, received);
    }

    @Test
    public void testGetAllStatusReportsForConsumer() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("consumer_uuid")).thenReturn(true);
        when(params.get("consumer_uuid")).thenReturn(Arrays.asList("c4"));
        when(params.getFirst("consumer_uuid")).thenReturn("c4");

        ConsumerTrendReportDefaultResult result = (ConsumerTrendReportDefaultResult) report.run(params, null);

        int received = 0;
        while (result.hasNext()) {
            ConsumerTrendComplianceDto compliance = result.next();
            Map<String, Object> consumer = (Map<String, Object>) compliance.get("consumer");
            assertEquals("c4", consumer.get("uuid"));

            ++received;
        }

        assertEquals(3, received);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDefaultResultSetContainsCustomMap() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("custom_results")).thenReturn(false);

        String uuid = "c3";
        when(params.containsKey("consumer_uuid")).thenReturn(true);
        when(params.getFirst("consumer_uuid")).thenReturn(uuid);

        createComplianceSnapshot(new Date(), uuid, "an-owner", "valid");
        // This cast will not fail when generic type differs. Checking below.
        ConsumerTrendReportDefaultResult result = (ConsumerTrendReportDefaultResult) report.run(params, null);
        assertNotNull(result);
        assertTrue(result.hasNext());

        ConsumerTrendComplianceDto firstCompliance = result.next();
        assertTrue(firstCompliance instanceof ConsumerTrendComplianceDto);
    }

    @Test
    public void testCustomResultSetContainsComplianceObjects() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("custom_results")).thenReturn(true);
        when(params.getFirst("custom_results")).thenReturn("1");

        String uuid = "c3";
        when(params.containsKey("consumer_uuid")).thenReturn(true);
        when(params.getFirst("consumer_uuid")).thenReturn(uuid);

        createComplianceSnapshot(new Date(), uuid, "an-owner", "valid");
        // This cast will not fail when generic type differs. Checking below.
        ReasonGeneratingReportResult result = (ReasonGeneratingReportResult) report.run(params, null);
        assertNotNull(result);
        assertTrue(result.hasNext());

        Compliance firstCompliance = result.next();
        assertTrue(firstCompliance instanceof Compliance);
    }

    private String formatDate(Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat(ConsumerTrendReport.REPORT_DATETIME_FORMAT);
        return formatter.format(date);
    }

    private void createInitialConsumer(Date createdOn, String uuid, String owner, String status) {
        createSnapshot(createdOn, uuid, owner, status);
        consumerStateCurator.create(new ConsumerState(uuid, owner, createdOn));
    }

    private void createSnapshot(Date date, String uuid, String owner, String status) {
        complianceSnapshotCurator.create(createComplianceSnapshot(date, uuid, owner, status));
    }

    private void setConsumerDeleted(Date deletedOn, String uuid, String owner) {
        createSnapshot(deletedOn, uuid, owner, "invalid");
        consumerStateCurator.setConsumerDeleted(uuid, deletedOn);
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
