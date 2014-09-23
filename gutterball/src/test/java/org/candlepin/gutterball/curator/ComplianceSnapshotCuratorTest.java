package org.candlepin.gutterball.curator;

import static org.candlepin.gutterball.TestUtils.*;
import static org.junit.Assert.*;

import org.candlepin.gutterball.DatabaseTestFixture;
import org.candlepin.gutterball.TestUtils;
import org.candlepin.gutterball.model.jpa.ComplianceSnapshot;
import org.candlepin.gutterball.model.jpa.ComplianceStatusSnapshot;
import org.candlepin.gutterball.model.jpa.ConsumerSnapshot;
import org.candlepin.gutterball.model.jpa.ConsumerState;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ComplianceSnapshotCuratorTest extends DatabaseTestFixture {

    private Date baseTestingDate;

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

        baseTestingDate = cal.getTime();

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
    public void testGetAllLatestStatusReports() {
        // c1 was deleted before the report date.
        List<String> expectedConsumerUuids = Arrays.asList("c2", "c3", "c4");
        List<ComplianceSnapshot> snaps = complianceSnapshotCurator.getSnapshotsOnDate(null,
                null, null, null);
        assertEquals(expectedConsumerUuids.size(), snaps.size());
        assertTrue(getUuidsFromSnapshots(snaps).containsAll(expectedConsumerUuids));
    }

    @Test
    public void testGetSnapshotOnDate() {
        Calendar cal = Calendar.getInstance();
        cal.setTime(baseTestingDate);
        cal.set(Calendar.MONTH, Calendar.JUNE);
        cal.set(Calendar.DAY_OF_MONTH, 12);

        List<ComplianceSnapshot> snaps =
            complianceSnapshotCurator.getSnapshotsOnDate(cal.getTime(), null, null, null);
        assertEquals(3, snaps.size());

        assertTrue(getUuidsFromSnapshots(snaps).containsAll(Arrays.asList("c2", "c3", "c4")));

        Map<String, Date> expectedStatusDates = new HashMap<String, Date>();
        cal.set(Calendar.DAY_OF_MONTH, 10);

        cal.set(Calendar.MONTH, Calendar.APRIL);
        expectedStatusDates.put("c2", cal.getTime());

        cal.set(Calendar.MONTH, Calendar.JUNE);
        expectedStatusDates.put("c3", cal.getTime());

        cal.set(Calendar.MONTH, Calendar.JUNE);
        expectedStatusDates.put("c4", cal.getTime());


        for (ComplianceSnapshot cs : snaps) {
            String uuid = cs.getConsumer().getUuid();
            assertEquals("Invalid status found for " + uuid,
                    expectedStatusDates.get(uuid), cs.getStatus().getDate());
        }

    }

    @Test
    public void testDeletedConsumerIncludedIfDeletedAfterTargetDate() {
        // May, June, July 10 -- 2014
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.MONTH, Calendar.MAY);
        cal.set(Calendar.DAY_OF_MONTH, 12);

        List<String> expectedConsumerUuids = Arrays.asList("c1", "c2", "c3", "c4");
        List<ComplianceSnapshot> snaps = complianceSnapshotCurator.getSnapshotsOnDate(cal.getTime(),
                null, null, null);
        assertEquals(expectedConsumerUuids.size(), snaps.size());
        assertTrue(getUuidsFromSnapshots(snaps).containsAll(expectedConsumerUuids));
    }

    @Test
    public void testGetByOwner() {
        String expectedOwner = "o2";

        List<ComplianceSnapshot> snaps = complianceSnapshotCurator.getSnapshotsOnDate(new Date(),
                null, Arrays.asList(expectedOwner), null);
        assertEquals(1, snaps.size());
        ComplianceSnapshot snap = snaps.get(0);

        ConsumerSnapshot consumerSnapshot = snap.getConsumer();
        assertEquals(expectedOwner, consumerSnapshot.getOwner().getKey());
        assertEquals("c3", consumerSnapshot.getUuid());
    }

    @Test
    public void testGetByConsumerUuid() {
        performGetByIdTest();
    }

    @Test
    public void assertLatestStatusIsReturnedForConsumer() {
        ComplianceSnapshot snap = performGetByIdTest();
        ComplianceStatusSnapshot status = snap.getStatus();
        assertEquals(snap.getDate(), status.getDate());

        Calendar cal = Calendar.getInstance();
        cal.setTime((Date) snap.getDate());

        assertEquals(2012, cal.get(Calendar.YEAR));
        assertEquals(10, cal.get(Calendar.DAY_OF_MONTH));
        assertEquals(Calendar.JULY, cal.get(Calendar.MONTH));
    }

    @Test
    public void testGetByStatus() {
        String expectedStatus = "partial";
        List<ComplianceSnapshot> snaps = complianceSnapshotCurator.getSnapshotsOnDate(null,
                null, null, Arrays.asList(expectedStatus));
        assertEquals(1, snaps.size());
        ComplianceSnapshot snap = snaps.get(0);
        assertEquals("c3", snap.getConsumer().getUuid());
    }

    @Test
    public void testReportOnTargetDate() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.MONTH, Calendar.APRIL);
        cal.set(Calendar.DAY_OF_MONTH, 12);

        Date onDate = cal.getTime();
        Set<ComplianceSnapshot> results = complianceSnapshotCurator.getComplianceForTimespan(
                onDate, onDate, null, null);
        assertEquals(2, results.size());
        List<String> foundUuids = TestUtils.getUuidsFromSnapshots(new ArrayList<ComplianceSnapshot>(results));
        assertTrue(foundUuids.containsAll(Arrays.asList("c1", "c2")));
    }

    @Test
    public void testGetAllStatusReports() {
        HashMap<String, Integer> expectedUuidsNumReports = new HashMap<String, Integer>() {
            {
                put("c1", 3);
                put("c2", 1);
                put("c3", 2);
                put("c4", 3);
            }
        };

        Set<ComplianceSnapshot> results = complianceSnapshotCurator.getComplianceForTimespan(null, null,
                null, null);

        // Ensure consumers are all found
        assertEquals(9, results.size());
        processAndCheckResults(expectedUuidsNumReports, results);
    }

    @Test
    public void testGetStatusReportsTimeframe() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.MONTH, Calendar.JUNE);
        cal.set(Calendar.DAY_OF_MONTH, 12);

        Date startDate = cal.getTime();
        Date endDate = new Date();
        HashMap<String, Integer> expectedUuidsNumReports = new HashMap<String, Integer>() {
            {
                put("c2", 1);
                put("c3", 1);
                put("c4", 2);
            }
        };

        Set<ComplianceSnapshot> results = complianceSnapshotCurator.getComplianceForTimespan(startDate,
                endDate, null, null);

        // Ensure consumers are all found
        assertEquals(4, results.size());
        processAndCheckResults(expectedUuidsNumReports, results);
    }

    private ComplianceSnapshot performGetByIdTest() {
        List<ComplianceSnapshot> snaps = complianceSnapshotCurator.getSnapshotsOnDate(new Date(),
                Arrays.asList("c1", "c4"), null, null);
        // C1 should get filtered out since it was deleted before the target date.
        assertEquals(1, snaps.size());
        ComplianceSnapshot snap = snaps.get(0);
        assertEquals("c4", snap.getConsumer().getUuid());
        return snap;
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

    private void processAndCheckResults(Map<String, Integer> expected, Set<ComplianceSnapshot> results) {
        // Make sure that we find the correct counts.
        HashMap<String, Integer> processed = new HashMap<String, Integer>();
        for (ComplianceSnapshot cs : results) {
            String uuid = cs.getConsumer().getUuid();
            if (!processed.containsKey(uuid)) {
                processed.put(uuid, 1);
            }
            else {
                processed.put(uuid, processed.get(uuid) + 1);
            }
        }
        assertTrue(processed.keySet().containsAll(expected.keySet()));

        for (String uuid : expected.keySet()) {
            assertTrue(processed.containsKey(uuid));
            assertEquals(expected.get(uuid), processed.get(uuid));
        }
    }
}
