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

import org.candlepin.gutterball.EmbeddedMongoRule;
import org.candlepin.gutterball.curator.ComplianceDataCurator;
import org.candlepin.gutterball.curator.ConsumerCurator;
import org.candlepin.gutterball.guice.I18nProvider;
import org.candlepin.gutterball.model.Consumer;

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

    private ConsumerCurator consumerCurator;

    @Before
    public void setUp() throws Exception {
        I18nProvider i18nProvider = new I18nProvider(mockReq);

        consumerCurator = new ConsumerCurator(serverRule.getMongoConnection());
        complianceDataCurator = new ComplianceDataCurator(serverRule.getMongoConnection(), consumerCurator);
        report = new ConsumerStatusReport(i18nProvider, complianceDataCurator);

        // Ensure test data is only created once. Ran into issues when using @BeforeClass
        // as it required everything to become static.
        if (!setupComplete) {
            setupTestData();
            setupComplete = true;
        }
    }

    @Test
    public void testDateFormatValidatedOnOnDateParameter() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);

        when(params.containsKey("on_date")).thenReturn(true);
        when(params.get("on_date")).thenReturn(Arrays.asList("13-21-2010"));

        validateParams(params, "on_date", "Invalid date string. Expected format: " +
                ConsumerStatusReport.REPORT_DATE_FORMAT);
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

        MultiRowResult<DBObject> results = report.run(params);
        assertEquals(2, results.size());

        List<String> foundConsumers = new ArrayList<String>();
        for (DBObject snap : results) {
            foundConsumers.add((String) ((DBObject) snap.get("consumer")).get("uuid"));
        }
        assertTrue(foundConsumers.containsAll(Arrays.asList("c1", "c2")));
    }

    @Test
    public void testGetAllLatestStatusReports() {
        MultiRowResult<DBObject> results = report.run(mock(MultivaluedMap.class));

        // c1 was deleted before the report date.
        List<String> expectedConsumerUuids = Arrays.asList("c2", "c3", "c4");
        assertEquals(expectedConsumerUuids.size(), results.size());

        // Make sure that both consumer instances are found and that the correct
        // snapshots are used.
        List<String> consumerUuids = new ArrayList<String>();
        for (DBObject snap : results) {
            DBObject consumer = (DBObject) snap.get("consumer");
            consumerUuids.add((String) consumer.get("uuid"));
        }
        assertTrue(consumerUuids.containsAll(expectedConsumerUuids));
    }

    @Test
    public void testDeletedConsumerNotIncludedInLatestResults() {
        MultiRowResult<DBObject> results = report.run(mock(MultivaluedMap.class));

        List<String> expectedConsumerUuids = Arrays.asList("c2", "c3", "c4");
        assertEquals(expectedConsumerUuids.size(), results.size());

        // Make sure that both consumer instances are found and that the correct
        // snapshots are used.
        List<String> consumerUuids = new ArrayList<String>();
        for (DBObject snap : results) {
            DBObject consumer = (DBObject) snap.get("consumer");
            consumerUuids.add((String) consumer.get("uuid"));
        }
        assertTrue(consumerUuids.containsAll(expectedConsumerUuids));
    }

    @Test
    public void testDeletedConsumerIncludedIfDeletedAfterTargetDate() {
        // May, June, July 10 -- 2014
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.MONTH, Calendar.MAY);
        cal.set(Calendar.DAY_OF_MONTH, 12);

        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("on_date")).thenReturn(true);
        String targetDateString = formatDate(cal.getTime());
        when(params.get("on_date")).thenReturn(Arrays.asList(targetDateString));
        when(params.getFirst("on_date")).thenReturn(targetDateString);

        MultiRowResult<DBObject> results = report.run(params);

        List<String> expectedConsumerUuids = Arrays.asList("c1", "c2", "c3", "c4");
        assertEquals(expectedConsumerUuids.size(), results.size());

        // Make sure that both consumer instances are found and that the correct
        // snapshots are used.
        List<String> consumerUuids = new ArrayList<String>();
        for (DBObject snap : results) {
            DBObject consumer = (DBObject) snap.get("consumer");
            consumerUuids.add((String) consumer.get("uuid"));
        }
        assertTrue(consumerUuids.containsAll(expectedConsumerUuids));
    }

    @Test
    public void testGetByOwner() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("owner")).thenReturn(true);
        when(params.get("owner")).thenReturn(Arrays.asList("o2"));

        MultiRowResult<DBObject> results = report.run(params);
        assertEquals(1, results.size());
        DBObject snap = results.get(0);
        DBObject consumer = (DBObject) snap.get("consumer");
        DBObject owner = (DBObject) consumer.get("owner");
        assertEquals("o2", owner.get("key"));
        assertEquals("c3", consumer.get("uuid"));
    }

    @Test
    public void testGetByConsumerUuid() {
        performGetByIdTest();
    }

    @Test
    public void assertLatestStatusIsReturnedForConsumer() {
        DBObject snap = performGetByIdTest();
        DBObject status = (DBObject) snap.get("status");

        Calendar cal = Calendar.getInstance();
        cal.setTime((Date) status.get("date"));

        assertEquals(2012, cal.get(Calendar.YEAR));
        assertEquals(10, cal.get(Calendar.DAY_OF_MONTH));
        assertEquals(Calendar.JULY, cal.get(Calendar.MONTH));
    }

    @Test
    public void testGetByStatus() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("status")).thenReturn(true);
        when(params.get("status")).thenReturn(Arrays.asList("partial"));

        MultiRowResult<DBObject> results = report.run(params);
        assertEquals(1, results.size());
        DBObject consumer = (DBObject) results.get(0).get("consumer");
        assertEquals("c3", consumer.get("uuid"));
    }

    private void setupTestData() {
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

    private void createInitialConsumer(Date createdOn, String uuid, String owner, String status) {
        createSnapshot(createdOn, uuid, owner, status);
        consumerCurator.insert(new Consumer(uuid, createdOn, createOwner(owner, owner)));
    }

    private void createSnapshot(Date date, String uuid, String owner, String status) {
        complianceDataCurator.insert(createComplianceSnapshot(date, uuid, owner, status));
    }

    private String formatDate(Date date) {
        SimpleDateFormat formatter = new SimpleDateFormat(ConsumerStatusReport.REPORT_DATE_FORMAT);
        return formatter.format(date);
    }

    private void setConsumerDeleted(Date deletedOn, String uuid, String owner) {
        createSnapshot(deletedOn, uuid, owner, "invalid");
        consumerCurator.setConsumerDeleted(uuid, deletedOn);
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

    private DBObject performGetByIdTest() {
        MultivaluedMap<String, String> params = mock(MultivaluedMap.class);
        when(params.containsKey("consumer_uuid")).thenReturn(true);
        when(params.get("consumer_uuid")).thenReturn(Arrays.asList("c1", "c4"));

        MultiRowResult<DBObject> results = report.run(params);
        // C1 should get filtered out since it was deleted before the target date.
        assertEquals(1, results.size());
        DBObject snap = results.get(0);
        DBObject consumer = (DBObject) snap.get("consumer");
        assertEquals("c4", consumer.get("uuid"));
        return snap;
    }
}
