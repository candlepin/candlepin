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
import static org.mockito.Mockito.*;

import org.candlepin.gutterball.DatabaseTestFixture;
import org.candlepin.gutterball.guice.I18nProvider;
import org.candlepin.gutterball.model.ConsumerState;
import org.candlepin.gutterball.model.snapshot.ComplianceSnapshot;

import org.jukito.JukitoRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

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
        report = new ConsumerTrendReport(i18nProvider, complianceSnapshotCurator);
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
    public void testDateFormatValidatedOnStartDateParameter() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);

        when(params.containsKey("start_date")).thenReturn(true);
        when(params.get("start_date")).thenReturn(Arrays.asList("13-21-2010"));

        validateParams(params, "start_date", "Invalid date string. Expected format: " +
                ConsumerTrendReport.REPORT_DATE_FORMAT);
    }

    @Test
    public void testDateFormatValidatedOnEndDateParameter() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);

        when(params.containsKey("end_date")).thenReturn(true);
        when(params.get("end_date")).thenReturn(Arrays.asList("13-21-2010"));

        validateParams(params, "end_date", "Invalid date string. Expected format: " +
                ConsumerTrendReport.REPORT_DATE_FORMAT);
    }

    @Test
    public void testStartDateMustHaveValidatedOnStartDateParameter() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);

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
        when(params.containsKey("start_date")).thenReturn(true);
        when(params.getFirst("start_date")).thenReturn(onDateString);
        when(params.get("start_date")).thenReturn(Arrays.asList(onDateString));
        when(params.containsKey("end_date")).thenReturn(true);
        when(params.getFirst("end_date")).thenReturn(onDateString);
        when(params.get("end_date")).thenReturn(Arrays.asList(onDateString));

        ConsumerTrendReportResult result = report.run(params);
        assertEquals(2, result.keySet().size());

        List<String> foundConsumers = new ArrayList<String>();
        for (String uuid : result.keySet()) {
            // There should only be one snapshot
            Set<ComplianceSnapshot> snapshots = result.get(uuid);
            assertEquals(1, snapshots.size());
            foundConsumers.add(uuid);
        }
        assertTrue(foundConsumers.containsAll(Arrays.asList("c1", "c2")));
    }

    @Test
    public void testGetAllStatusReports() {
        ConsumerTrendReportResult result = report.run(mock(MultivaluedMap.class));
        HashMap<String, Integer> expectedUuidsNumReports = new HashMap<String, Integer>() {
            {
                put("c1", 3);
                put("c2", 1);
                put("c3", 2);
                put("c4", 3);
            }
        };

        // Ensure consumers are all found
        assertEquals(expectedUuidsNumReports.keySet(), result.keySet());

        for (String uuid : result.keySet()) {
            Set<ComplianceSnapshot> snapshots = result.get(uuid);
            assertEquals((int) expectedUuidsNumReports.get(uuid), snapshots.size());
        }
    }



    @Test
    public void testGetStatusReportsTimeframe() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.MONTH, Calendar.JUNE);
        cal.set(Calendar.DAY_OF_MONTH, 12);

        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        String startDateString = formatDate(cal.getTime());
        String endDateString = formatDate(new Date());
        when(params.containsKey("start_date")).thenReturn(true);
        when(params.getFirst("start_date")).thenReturn(startDateString);
        when(params.get("start_date")).thenReturn(Arrays.asList(startDateString));
        when(params.containsKey("end_date")).thenReturn(true);
        when(params.getFirst("end_date")).thenReturn(endDateString);
        when(params.get("end_date")).thenReturn(Arrays.asList(endDateString));
        ConsumerTrendReportResult result = report.run(params);
        HashMap<String, Integer> expectedUuidsNumReports = new HashMap<String, Integer>() {
            {
                put("c2", 1);
                put("c3", 1);
                put("c4", 2);
            }
        };

        // Ensure consumers are all found
        assertEquals(expectedUuidsNumReports.keySet(), result.keySet());

        for (String uuid : result.keySet()) {
            Set<ComplianceSnapshot> snapshots = result.get(uuid);
            assertEquals((int) expectedUuidsNumReports.get(uuid), snapshots.size());
        }
    }

    private String formatDate(Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat(ConsumerTrendReport.REPORT_DATE_FORMAT);
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
