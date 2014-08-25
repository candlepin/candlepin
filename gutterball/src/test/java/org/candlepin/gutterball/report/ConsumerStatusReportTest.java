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

import org.candlepin.gutterball.EmbeddedMongoRule;
import org.candlepin.gutterball.curator.ComplianceDataCurator;
import org.candlepin.gutterball.guice.I18nProvider;

import com.mongodb.DBObject;

import org.jukito.JukitoRunner;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MultivaluedMap;

@RunWith(JukitoRunner.class)
public class ConsumerStatusReportTest {

    @SuppressWarnings("checkstyle:visibilitymodifier")
    @ClassRule
    public static EmbeddedMongoRule serverRule = new EmbeddedMongoRule();

    // Used to determine if the setup was already run.
    private static boolean setupComplete = false;

    @Inject
    private HttpServletRequest mockReq;

    private ComplianceDataCurator complianceDataCurator;
    private ConsumerStatusReport report;

    @Before
    public void setUp() throws Exception {
        I18nProvider i18nProvider = new I18nProvider(mockReq);
        complianceDataCurator = new ComplianceDataCurator(serverRule.getMongoConnection());
        report = new ConsumerStatusReport(i18nProvider, complianceDataCurator);

        // Ensure test data is only created once. Ran into issues when using @BeforeClass
        // as it required everything to become static.
        if (!setupComplete) {
            setupTestData();
            setupComplete = true;
        }
    }

    @Test
    public void startDateCanNotBeUsedWithHoursParam() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("hours")).thenReturn(true);
        when(params.get("hours")).thenReturn(new ArrayList<String>());
        when(params.containsKey("start_date")).thenReturn(true);

        validateParams(params, "hours", "Parameter must not be used with start_date.");
    }

    @Test
    public void endDateCanNotBeUsedWithHoursParam() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("hours")).thenReturn(true);
        when(params.get("hours")).thenReturn(new ArrayList<String>());
        when(params.containsKey("end_date")).thenReturn(true);

        validateParams(params, "hours", "Parameter must not be used with end_date.");
    }

    @Test
    public void hoursParamMustBeAnInteger() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("hours")).thenReturn(true);
        when(params.get("hours")).thenReturn(Arrays.asList("24a"));

        validateParams(params, "hours", "Parameter must be an Integer value.");
    }

    @Test
    public void endDateMustBeSpecifiedWithStartDate() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("start_date")).thenReturn(true);
        when(params.get("start_date")).thenReturn(Arrays.asList("2014-08-16T00:00:00.000+0000"));
        when(params.containsKey("end_date")).thenReturn(false);

        validateParams(params, "start_date", "Parameter must be used with end_date.");
    }

    @Test
    public void startDateMustBeSpecifiedWithEndDate() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("start_date")).thenReturn(false);
        when(params.containsKey("end_date")).thenReturn(true);
        when(params.get("end_date")).thenReturn(Arrays.asList("2014-08-16T00:00:00.000+0000"));

        validateParams(params, "end_date", "Parameter must be used with start_date.");
    }

    @Test
    public void testDateFormatValidatedOnStartDate() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);

        when(params.containsKey("start_date")).thenReturn(true);
        when(params.get("start_date")).thenReturn(Arrays.asList("13-21-2010"));

        validateParams(params, "start_date", "Invalid date string. Expected format: " +
                ConsumerStatusReport.REPORT_DATE_FORMAT);
    }

    @Test
    public void testDateFormatValidatedOnEndDate() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("end_date")).thenReturn(true);
        when(params.get("end_date")).thenReturn(Arrays.asList("2010-18-4"));

        validateParams(params, "end_date", "Invalid date string. Expected format: " +
                ConsumerStatusReport.REPORT_DATE_FORMAT);
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

    @Test
    public void testGetStartEndDate() {

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, 2004);
        Date startDate = cal.getTime();

        cal.set(Calendar.YEAR, 2010);
        Date endDate = cal.getTime();

        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        String startDateString = formatDate(startDate);
        when(params.containsKey("start_date")).thenReturn(true);
        when(params.getFirst("start_date")).thenReturn(startDateString);
        when(params.get("start_date")).thenReturn(Arrays.asList(startDateString));

        String endDateString = formatDate(endDate);
        when(params.containsKey("end_date")).thenReturn(true);
        when(params.getFirst("end_date")).thenReturn(endDateString);
        when(params.get("end_date")).thenReturn(Arrays.asList(endDateString));

        MultiRowResult<DBObject> results = report.run(params);
        assertEquals(2, results.size());

        List<String> foundConsumers = new ArrayList<String>();
        for (DBObject snap : results) {
            foundConsumers.add((String) ((DBObject) snap.get("consumer")).get("uuid"));
        }
        assertTrue(foundConsumers.containsAll(Arrays.asList("c3", "c4")));
    }

    @Test
    public void testGetStartEndDateSameDate() {
        DBObject snapshot = complianceDataCurator.all().next();
        Date target = (Date) ((DBObject) snapshot.get("status")).get("date");
        String targetDate = formatDate(target);

        String consumerUuid = (String) ((DBObject) snapshot.get("consumer")).get("uuid");

        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("start_date")).thenReturn(true);
        when(params.getFirst("start_date")).thenReturn(targetDate);
        when(params.get("start_date")).thenReturn(Arrays.asList(targetDate));

        when(params.containsKey("end_date")).thenReturn(true);
        when(params.getFirst("end_date")).thenReturn(targetDate);
        when(params.get("end_date")).thenReturn(Arrays.asList(targetDate));

        MultiRowResult<DBObject> results = report.run(params);
        assertEquals(1, results.size());

        DBObject result = results.get(0);
        DBObject consumer = (DBObject) result.get("consumer");
        assertEquals(consumerUuid, consumer.get("uuid"));
    }

    @Test
    public void testGetByOwner() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("start_date")).thenReturn(false);
        when(params.containsKey("end_date")).thenReturn(false);

        when(params.containsKey("owner")).thenReturn(true);
        when(params.get("owner")).thenReturn(Arrays.asList("o3"));

        MultiRowResult<DBObject> results = report.run(params);
        assertEquals(1, results.size());
        DBObject r = results.get(0);
        DBObject consumer = (DBObject) r.get("consumer");
        DBObject owner = (DBObject) consumer.get("owner");
        assertEquals("o3", owner.get("key"));
    }

    @Test
    public void testGetByConsumerUuid() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("start_date")).thenReturn(false);
        when(params.containsKey("end_date")).thenReturn(false);

        when(params.containsKey("consumer_uuid")).thenReturn(true);
        when(params.get("consumer_uuid")).thenReturn(Arrays.asList("c1"));

        MultiRowResult<DBObject> results = report.run(params);
        assertEquals(1, results.size());
        DBObject row = results.get(0);
        DBObject consumer = (DBObject) row.get("consumer");
        assertEquals("c1", consumer.get("uuid"));
    }

    @Test
    public void testGetByStatus() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("start_date")).thenReturn(false);
        when(params.containsKey("end_date")).thenReturn(false);
        when(params.containsKey("status")).thenReturn(true);
        when(params.get("status")).thenReturn(Arrays.asList("valid"));

        MultiRowResult<DBObject> results = report.run(params);
        assertEquals(2, results.size());
        for (DBObject r : results) {
            DBObject status = (DBObject) r.get("status");
            assertEquals("valid", status.get("status"));
        }
    }

    @Test
    public void testGetAllLatestStatusReports() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("start_date")).thenReturn(false);
        when(params.containsKey("end_date")).thenReturn(false);

        MultiRowResult<DBObject> results = report.run(params);
        assertEquals(4, results.size());

        // Make sure that both consumer instances are found and that the correct
        // snapshots are used.
        List<String> consumerUuids = new ArrayList<String>();
        for (DBObject snap : results) {
            DBObject consumer = (DBObject) snap.get("consumer");
            consumerUuids.add((String) consumer.get("uuid"));
        }
        assertTrue(consumerUuids.containsAll(Arrays.asList("c1", "c2", "c3", "c4")));
    }

    @Test
    public void testGetByHours() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);

        String hours = "5";
        when(params.containsKey("hours")).thenReturn(true);
        when(params.getFirst("hours")).thenReturn(hours);
        when(params.get("hours")).thenReturn(Arrays.asList(hours));

        MultiRowResult<DBObject> results = report.run(params);
        assertEquals(1, results.size());
        DBObject row = results.get(0);
        DBObject consumer = (DBObject) row.get("consumer");
        DBObject status = (DBObject) row.get("status");
        assertEquals("c1", consumer.get("uuid"));
        assertEquals("invalid", status.get("status"));
    }

    private void setupTestData() {
        Calendar cal = Calendar.getInstance();

        // Created within the last 4 hours.
        cal.add(Calendar.HOUR_OF_DAY, -4);
        complianceDataCurator.insert(createComplianceSnapshot(cal.getTime(), "c1", "o1", "invalid"));

        cal.set(Calendar.DAY_OF_MONTH, -2);
        complianceDataCurator.insert(createComplianceSnapshot(cal.getTime(), "c1", "o1", "partial"));

        // Values for c1 and c2 are set to the same so that we
        // can verify that the latest snapshot is being for c1.
        cal.add(Calendar.MINUTE, 4);
        Date expectedDate = cal.getTime();
        complianceDataCurator.insert(createComplianceSnapshot(expectedDate, "c1", "o2", "valid"));
        complianceDataCurator.insert(createComplianceSnapshot(expectedDate, "c2", "o2", "valid"));

        cal.set(Calendar.YEAR, 2008);
        cal.set(Calendar.MONTH, 8);
        cal.set(Calendar.DAY_OF_MONTH, 18);

        complianceDataCurator.insert(createComplianceSnapshot(cal.getTime(), "c3", "o2", "invalid"));
        cal.set(Calendar.DAY_OF_MONTH, 19);
        Date time = cal.getTime();
        complianceDataCurator.insert(createComplianceSnapshot(time, "c4", "o3", "invalid"));
    }

    private String formatDate(Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat(ConsumerStatusReport.REPORT_DATE_FORMAT);
        return formatter.format(date);
    }
}
