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

package org.candlepin.gutterball.curator;

import static org.candlepin.gutterball.TestUtils.*;
import static org.junit.Assert.*;
import static junitparams.JUnitParamsRunner.*;

import org.candlepin.gutterball.DatabaseTestFixture;
import org.candlepin.gutterball.TestUtils;
import org.candlepin.gutterball.jackson.GutterballObjectMapper;
import org.candlepin.gutterball.model.ConsumerState;
import org.candlepin.gutterball.model.Event;
import org.candlepin.gutterball.model.snapshot.Compliance;
import org.candlepin.gutterball.model.snapshot.ComplianceStatus;
import org.candlepin.gutterball.model.snapshot.Consumer;
import org.candlepin.gutterball.model.snapshot.Entitlement;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RunWith(JUnitParamsRunner.class)
public class ComplianceSnapshotCuratorTest extends DatabaseTestFixture {

    private Date baseTestingDate;

    @Before
    public void initData() {
        Compliance compliance;
        Calendar cal = Calendar.getInstance();

        // Set up deleted consumer test data
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.MONTH, Calendar.MARCH);
        cal.set(Calendar.DAY_OF_MONTH, 10);
        cal.set(Calendar.HOUR, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);

        baseTestingDate = cal.getTime();

        // Create some entitlements
        Entitlement entitlement1 = createEntitlement(
            "testsku1",
            "test product 1",
            1,
            baseTestingDate,
            new HashMap<String, String>() {
                {
                    this.put("attrib1", "val1");
                    this.put("attrib2", "val2");
                    this.put("attrib3", "val3");
                }
            }
        );

        Entitlement entitlement2 = createEntitlement(
            "testsku2",
            "test product 2",
            2,
            baseTestingDate,
            new HashMap<String, String>() {
                {
                    this.put("attrib1", "val1");
                    this.put("attrib3", "val3");
                }
            }
        );

        Entitlement entitlement3 = createEntitlement(
            "testsku3",
            "test product 3",
            3,
            baseTestingDate,
            new HashMap<String, String>() {
                {
                    this.put("attrib2", "val2");
                }
            }
        );

        this.beginTransaction();

        // Consumer created
        cal.set(Calendar.MONTH, Calendar.MARCH);
        compliance = createInitialConsumer(cal.getTime(), "c1", "o1", "invalid");
        attachEntitlement(compliance, entitlement1);
        // Simulate status change
        cal.set(Calendar.MONTH, Calendar.MAY);
        compliance = createSnapshot(cal.getTime(), "c1", "o1", "valid");
        attachEntitlement(compliance, entitlement1);
        // Consumer was deleted
        cal.set(Calendar.MONTH, Calendar.JUNE);
        compliance = setConsumerDeleted(cal.getTime(), "c1", "o1");
        attachEntitlement(compliance, entitlement1);

        cal.set(Calendar.MONTH, Calendar.APRIL);
        compliance = createInitialConsumer(cal.getTime(), "c2", "o1", "invalid");
        attachEntitlement(compliance, entitlement1);
        attachEntitlement(compliance, entitlement2);

        cal.set(Calendar.MONTH, Calendar.MAY);
        compliance = createInitialConsumer(cal.getTime(), "c3", "o2", "invalid");
        attachEntitlement(compliance, entitlement2);
        cal.set(Calendar.MONTH, Calendar.JUNE);
        compliance = createSnapshot(cal.getTime(), "c3", "o2", "partial");
        attachEntitlement(compliance, entitlement2);

        cal.set(Calendar.MONTH, Calendar.MAY);
        compliance = createInitialConsumer(cal.getTime(), "c4", "o3", "invalid");
        attachEntitlement(compliance, entitlement2);
        attachEntitlement(compliance, entitlement3);
        cal.set(Calendar.MONTH, Calendar.JUNE);
        compliance = createSnapshot(cal.getTime(), "c4", "o3", "partial");
        attachEntitlement(compliance, entitlement2);
        attachEntitlement(compliance, entitlement3);
        cal.set(Calendar.MONTH, Calendar.JULY);
        compliance = createSnapshot(cal.getTime(), "c4", "o3", "valid");
        attachEntitlement(compliance, entitlement2);
        attachEntitlement(compliance, entitlement3);

        this.commitTransaction();

        /*
        {
            Sat Mar 10 00:00:00 EST 2012={invalid=1},
            Tue Apr 10 00:00:00 EDT 2012={invalid=1},
            Thu May 10 00:00:00 EDT 2012={valid=1, invalid=2},
            Sun Jun 10 00:00:00 EDT 2012={partial=2},
            Tue Jul 10 00:00:00 EDT 2012={valid=1}
        }
        */
    }

    @Test
    public void ensurePersistableFromJson() throws Exception {
        String eventJson = loadJsonFile("org/candlepin/gutterball/jackson/compliance-created.json");
        GutterballObjectMapper mapper = new GutterballObjectMapper();
        Event event = mapper.readValue(eventJson, Event.class);
        Compliance complianceSnap = mapper.readValue(event.getNewEntity(), Compliance.class);
        // This is normally done by the event handlers.
        complianceSnap.setDate(complianceSnap.getStatus().getDate());
        complianceSnapshotCurator.create(complianceSnap);
    }

    @Test
    public void testGetAllLatestStatusReports() {
        // c1 was deleted before the report date.
        List<String> expectedConsumerUuids = Arrays.asList("c2", "c3", "c4");
        List<Compliance> snaps = complianceSnapshotCurator.getSnapshotsOnDate(null,
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

        List<Compliance> snaps =
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


        for (Compliance cs : snaps) {
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
        List<Compliance> snaps = complianceSnapshotCurator.getSnapshotsOnDate(cal.getTime(),
                null, null, null);
        assertEquals(expectedConsumerUuids.size(), snaps.size());
        assertTrue(getUuidsFromSnapshots(snaps).containsAll(expectedConsumerUuids));
    }

    @Test
    public void testGetByOwner() {
        String expectedOwner = "o2";

        List<Compliance> snaps = complianceSnapshotCurator.getSnapshotsOnDate(new Date(),
                null, Arrays.asList(expectedOwner), null);
        assertEquals(1, snaps.size());
        Compliance snap = snaps.get(0);

        Consumer consumerSnapshot = snap.getConsumer();
        assertEquals(expectedOwner, consumerSnapshot.getOwner().getKey());
        assertEquals("c3", consumerSnapshot.getUuid());
    }

    @Test
    public void testGetByConsumerUuid() {
        performGetByIdTest();
    }

    @Test
    public void assertLatestStatusIsReturnedForConsumer() {
        Compliance snap = performGetByIdTest();
        ComplianceStatus status = snap.getStatus();
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
        List<Compliance> snaps = complianceSnapshotCurator.getSnapshotsOnDate(null,
                null, null, Arrays.asList(expectedStatus));
        assertEquals(1, snaps.size());
        Compliance snap = snaps.get(0);
        assertEquals("c3", snap.getConsumer().getUuid());
    }

    @Test
    public void testReportOnTargetDate() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.MONTH, Calendar.APRIL);
        cal.set(Calendar.DAY_OF_MONTH, 12);

        Date onDate = cal.getTime();
        Set<Compliance> results = complianceSnapshotCurator.getComplianceForTimespan(
                onDate, onDate, null, null);
        assertEquals(2, results.size());
        List<String> foundUuids = TestUtils.getUuidsFromSnapshots(new ArrayList<Compliance>(results));
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

        Set<Compliance> results = complianceSnapshotCurator.getComplianceForTimespan(null, null,
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

        Set<Compliance> results = complianceSnapshotCurator.getComplianceForTimespan(startDate,
                endDate, null, null);

        // Ensure consumers are all found
        assertEquals(4, results.size());
        processAndCheckResults(expectedUuidsNumReports, results);
    }

    @Test
    public void testGetComplianceStatusCounts() {
        Map<Date, Map<String, Long>> expected = this.buildMapForAllStatusCounts();
        Map<Date, Map<String, Long>> actual = this.complianceSnapshotCurator.getComplianceStatusCounts();

        assertEquals(expected, actual);
    }

    @Test
    @Parameters(method = "buildMapForStatusCountsAfterDate")
    public void testGetComplianceStatusCountsAfterDate(Date date, Map<Date, Map<String, Long>> expected) {
        Map<Date, Map<String, Long>> actual = this.complianceSnapshotCurator.getComplianceStatusCounts(
            date,
            null
        );

        assertEquals(expected, actual);
    }

    @Test
    @Parameters(method = "buildMapForStatusCountsBeforeDate")
    public void testGetComplianceStatusCountsBeforeDate(Date date, Map<Date, Map<String, Long>> expected) {
        Map<Date, Map<String, Long>> actual = this.complianceSnapshotCurator.getComplianceStatusCounts(
            null,
            date
        );

        assertEquals(expected, actual);
    }

    @Test
    @Parameters(method = "buildMapForStatusCountsBetweenDates")
    public void testGetComplianceStatusCountsBetweenDates(Date startDate, Date endDate,
        Map<Date, Map<String, Long>> expected) {

        Map<Date, Map<String, Long>> actual = this.complianceSnapshotCurator.getComplianceStatusCounts(
            startDate,
            endDate
        );

        assertEquals(expected, actual);
    }

    @Test
    @Parameters(method = "buildMapForStatusCountsWithSku")
    public void testGetComplianceStatusCountsWithValidSku(Date startDate, Date endDate, String sku,
        Map<Date, Map<String, Long>> expected) {
        Map<Date, Map<String, Long>> actual;

        actual = this.complianceSnapshotCurator.getComplianceStatusCountsBySku(
            startDate,
            endDate,
            sku
        );

        assertEquals(expected, actual);
    }

    @Test
    @Parameters(method = "buildMapForStatusCountsWithSubscriptionName")
    public void testGetComplianceStatusCountsWithSubscription(Date startDate, Date endDate,
        String subscriptionName, Map<Date, Map<String, Long>> expected) {
        Map<Date, Map<String, Long>> actual;

        actual = this.complianceSnapshotCurator.getComplianceStatusCountsBySubscription(
            startDate,
            endDate,
            subscriptionName
        );

        assertEquals(expected, actual);
    }

    @Test
    @Parameters(method = "buildMapForStatusCountsWithAttributes")
    public void testGetComplianceStatusCountsWithAttributes(Date startDate, Date endDate,
        Map<String, String> attributes, Map<Date, Map<String, Long>> expected) {
        Map<Date, Map<String, Long>> actual;

        actual = this.complianceSnapshotCurator.getComplianceStatusCountsByAttributes(
            startDate,
            endDate,
            attributes
        );

        assertEquals(expected, actual);
    }

    private Map<Date, Map<String, Long>> buildMapForAllStatusCounts() {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.DAY_OF_MONTH, 10);

        Map<Date, Map<String, Long>> expected = new HashMap<Date, Map<String, Long>>();
        HashMap<String, Long> counts;

        cal.set(Calendar.MONTH, Calendar.MARCH);
        counts = new HashMap<String, Long>();
        counts.put("invalid", 1L);
        expected.put(cal.getTime(), counts);

        cal.set(Calendar.MONTH, Calendar.APRIL);
        counts = new HashMap<String, Long>();
        counts.put("invalid", 1L);
        expected.put(cal.getTime(), counts);

        cal.set(Calendar.MONTH, Calendar.MAY);
        counts = new HashMap<String, Long>();
        counts.put("valid", 1L);
        counts.put("invalid", 2L);
        expected.put(cal.getTime(), counts);

        cal.set(Calendar.MONTH, Calendar.JUNE);
        counts = new HashMap<String, Long>();
        counts.put("partial", 2L);
        expected.put(cal.getTime(), counts);

        cal.set(Calendar.MONTH, Calendar.JULY);
        counts = new HashMap<String, Long>();
        counts.put("valid", 1L);
        expected.put(cal.getTime(), counts);

        return expected;
    }

    public Object[][] buildMapForStatusCountsBeforeDate() {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.DAY_OF_MONTH, 10);

        Map<Date, Map<String, Long>> expected = new HashMap<Date, Map<String, Long>>();
        HashMap<String, Long> counts;

        Object[][] output = new Object[2][];

        output[0] = $(null, this.buildMapForAllStatusCounts());

        cal.set(Calendar.MONTH, Calendar.MARCH);
        counts = new HashMap<String, Long>();
        counts.put("invalid", 1L);
        expected.put(cal.getTime(), counts);

        cal.set(Calendar.MONTH, Calendar.APRIL);
        counts = new HashMap<String, Long>();
        counts.put("invalid", 1L);
        expected.put(cal.getTime(), counts);

        cal.set(Calendar.MONTH, Calendar.MAY);
        counts = new HashMap<String, Long>();
        counts.put("valid", 1L);
        counts.put("invalid", 2L);
        expected.put(cal.getTime(), counts);

        cal.set(Calendar.MONTH, Calendar.MAY);
        Date endDate = cal.getTime();

        output[1] = $(endDate, expected);

        return output;
    }

    public Object[][] buildMapForStatusCountsAfterDate() {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.DAY_OF_MONTH, 10);

        Map<Date, Map<String, Long>> expected = new HashMap<Date, Map<String, Long>>();
        HashMap<String, Long> counts;

        Object[][] output = new Object[2][];

        output[0] = $(null, this.buildMapForAllStatusCounts());

        cal.set(Calendar.MONTH, Calendar.MAY);
        counts = new HashMap<String, Long>();
        counts.put("valid", 1L);
        counts.put("invalid", 2L);
        expected.put(cal.getTime(), counts);

        cal.set(Calendar.MONTH, Calendar.JUNE);
        counts = new HashMap<String, Long>();
        counts.put("partial", 2L);
        expected.put(cal.getTime(), counts);

        cal.set(Calendar.MONTH, Calendar.JULY);
        counts = new HashMap<String, Long>();
        counts.put("valid", 1L);
        expected.put(cal.getTime(), counts);

        cal.set(Calendar.MONTH, Calendar.MAY);
        Date startDate = cal.getTime();

        output[1] = $(startDate, expected);

        return output;
    }

    public Object[] buildMapForStatusCountsBetweenDates() {
        LinkedList<Object[]> tests = new LinkedList<Object[]>();
        Object[][] beforeSet = this.buildMapForStatusCountsBeforeDate();
        Object[][] afterSet = this.buildMapForStatusCountsAfterDate();

        for (Object[] before : beforeSet) {
            for (Object[] after : afterSet) {
                tests.add(new Object[] {
                    after[0],
                    before[0],
                    this.intersect(
                        (Map<Date, Map<String, Long>>) after[1],
                        (Map<Date, Map<String, Long>>) before[1]
                    )
                });
            }
        }

        return tests.toArray();
    }

    public Object[] buildMapForStatusCountsWithSku() {
        LinkedList<Object[]> tests = new LinkedList<Object[]>();
        Object[][] beforeSet = this.buildMapForStatusCountsBeforeDate();
        Object[][] afterSet = this.buildMapForStatusCountsAfterDate();
        Object[][] skuSet = this.buildSubMapForStatusCountsWithSku();

        for (Object[] before : beforeSet) {
            for (Object[] after : afterSet) {
                for (Object[] sku : skuSet) {
                    tests.add(new Object[] {
                        after[0],
                        before[0],
                        sku[0],
                        this.intersect(
                            (Map<Date, Map<String, Long>>) after[1],
                            (Map<Date, Map<String, Long>>) before[1],
                            (Map<Date, Map<String, Long>>) sku[1]
                        )
                    });
                }
            }
        }

        return tests.toArray();
    }

    public Object[] buildMapForStatusCountsWithSubscriptionName() {
        LinkedList<Object[]> tests = new LinkedList<Object[]>();
        Object[][] beforeSet = this.buildMapForStatusCountsBeforeDate();
        Object[][] afterSet = this.buildMapForStatusCountsAfterDate();
        Object[][] nameSet = this.buildSubMapForStatusCountsWithSubscriptionName();

        for (Object[] before : beforeSet) {
            for (Object[] after : afterSet) {
                for (Object[] name : nameSet) {
                    tests.add(new Object[] {
                        after[0],
                        before[0],
                        name[0],
                        this.intersect(
                            (Map<Date, Map<String, Long>>) after[1],
                            (Map<Date, Map<String, Long>>) before[1],
                            (Map<Date, Map<String, Long>>) name[1]
                        )
                    });
                }
            }
        }

        return tests.toArray();
    }

    public Object[] buildMapForStatusCountsWithAttributes() {
        LinkedList<Object[]> tests = new LinkedList<Object[]>();
        Object[][] beforeSet = this.buildMapForStatusCountsBeforeDate();
        Object[][] afterSet = this.buildMapForStatusCountsAfterDate();
        Object[][] attributeSet = this.buildSubMapForStatusCountsWithAttributes();

        for (Object[] before : beforeSet) {
            for (Object[] after : afterSet) {
                for (Object[] attribute : attributeSet) {
                    tests.add(new Object[] {
                        after[0],
                        before[0],
                        attribute[0],
                        this.intersect(
                            (Map<Date, Map<String, Long>>) after[1],
                            (Map<Date, Map<String, Long>>) before[1],
                            (Map<Date, Map<String, Long>>) attribute[1]
                        )
                    });
                }
            }
        }

        return tests.toArray();
    }

    private Object[][] buildSubMapForStatusCountsWithSku() {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.DAY_OF_MONTH, 10);

        Map<Date, Map<String, Long>> expected;
        Map<Date, Map<String, Long>> actual;
        HashMap<String, Long> counts;

        Object[][] output = new Object[5][];

        output[0] = $(null, this.buildMapForAllStatusCounts());


        expected = new HashMap<Date, Map<String, Long>>();

        cal.set(Calendar.MONTH, Calendar.MARCH);
        counts = new HashMap<String, Long>();
        counts.put("invalid", 1L);
        expected.put(cal.getTime(), counts);

        cal.set(Calendar.MONTH, Calendar.APRIL);
        counts = new HashMap<String, Long>();
        counts.put("invalid", 1L);
        expected.put(cal.getTime(), counts);

        cal.set(Calendar.MONTH, Calendar.MAY);
        counts = new HashMap<String, Long>();
        counts.put("valid", 1L);
        expected.put(cal.getTime(), counts);

        output[1] = $("testsku1", expected);


        expected = new HashMap<Date, Map<String, Long>>();

        cal.set(Calendar.MONTH, Calendar.APRIL);
        counts = new HashMap<String, Long>();
        counts.put("invalid", 1L);
        expected.put(cal.getTime(), counts);

        cal.set(Calendar.MONTH, Calendar.MAY);
        counts = new HashMap<String, Long>();
        counts.put("invalid", 2L);
        expected.put(cal.getTime(), counts);

        cal.set(Calendar.MONTH, Calendar.JUNE);
        counts = new HashMap<String, Long>();
        counts.put("partial", 2L);
        expected.put(cal.getTime(), counts);

        cal.set(Calendar.MONTH, Calendar.JULY);
        counts = new HashMap<String, Long>();
        counts.put("valid", 1L);
        expected.put(cal.getTime(), counts);

        output[2] = $("testsku2", expected);


        expected = new HashMap<Date, Map<String, Long>>();

        cal.set(Calendar.MONTH, Calendar.MAY);
        counts = new HashMap<String, Long>();
        counts.put("invalid", 1L);
        expected.put(cal.getTime(), counts);

        cal.set(Calendar.MONTH, Calendar.JUNE);
        counts = new HashMap<String, Long>();
        counts.put("partial", 1L);
        expected.put(cal.getTime(), counts);

        cal.set(Calendar.MONTH, Calendar.JULY);
        counts = new HashMap<String, Long>();
        counts.put("valid", 1L);
        expected.put(cal.getTime(), counts);

        output[3] = $("testsku3", expected);


        output[4] = $("badsku", new HashMap<Date, Map<String, Long>>());

        return output;
    }

    private Object[][] buildSubMapForStatusCountsWithSubscriptionName() {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.DAY_OF_MONTH, 10);

        Map<Date, Map<String, Long>> expected;
        Map<Date, Map<String, Long>> actual;
        HashMap<String, Long> counts;

        Object[][] output = new Object[5][];

        output[0] = $(null, this.buildMapForAllStatusCounts());

        expected = new HashMap<Date, Map<String, Long>>();

        cal.set(Calendar.MONTH, Calendar.MARCH);
        counts = new HashMap<String, Long>();
        counts.put("invalid", 1L);
        expected.put(cal.getTime(), counts);

        cal.set(Calendar.MONTH, Calendar.APRIL);
        counts = new HashMap<String, Long>();
        counts.put("invalid", 1L);
        expected.put(cal.getTime(), counts);

        cal.set(Calendar.MONTH, Calendar.MAY);
        counts = new HashMap<String, Long>();
        counts.put("valid", 1L);
        expected.put(cal.getTime(), counts);

        output[1] = $("test product 1", expected);


        expected = new HashMap<Date, Map<String, Long>>();

        cal.set(Calendar.MONTH, Calendar.APRIL);
        counts = new HashMap<String, Long>();
        counts.put("invalid", 1L);
        expected.put(cal.getTime(), counts);

        cal.set(Calendar.MONTH, Calendar.MAY);
        counts = new HashMap<String, Long>();
        counts.put("invalid", 2L);
        expected.put(cal.getTime(), counts);

        cal.set(Calendar.MONTH, Calendar.JUNE);
        counts = new HashMap<String, Long>();
        counts.put("partial", 2L);
        expected.put(cal.getTime(), counts);

        cal.set(Calendar.MONTH, Calendar.JULY);
        counts = new HashMap<String, Long>();
        counts.put("valid", 1L);
        expected.put(cal.getTime(), counts);

        output[2] = $("test product 2", expected);


        expected = new HashMap<Date, Map<String, Long>>();

        cal.set(Calendar.MONTH, Calendar.MAY);
        counts = new HashMap<String, Long>();
        counts.put("invalid", 1L);
        expected.put(cal.getTime(), counts);

        cal.set(Calendar.MONTH, Calendar.JUNE);
        counts = new HashMap<String, Long>();
        counts.put("partial", 1L);
        expected.put(cal.getTime(), counts);

        cal.set(Calendar.MONTH, Calendar.JULY);
        counts = new HashMap<String, Long>();
        counts.put("valid", 1L);
        expected.put(cal.getTime(), counts);

        output[3] = $("test product 3", expected);


        output[4] = $("bad product name", new HashMap<Date, Map<String, Long>>());

        return output;
    }

    @SuppressWarnings("checkstyle:methodlength")
    private Object[][] buildSubMapForStatusCountsWithAttributes() {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.DAY_OF_MONTH, 10);

        Map<Date, Map<String, Long>> expected;
        Map<Date, Map<String, Long>> actual;
        HashMap<String, Long> counts;
        Map<String, String> attributes;

        Object[][] output = new Object[7][];

        output[0] = $(null, this.buildMapForAllStatusCounts());

        expected = new HashMap<Date, Map<String, Long>>();

        cal.set(Calendar.MONTH, Calendar.MARCH);
        counts = new HashMap<String, Long>();
        counts.put("invalid", 1L);
        expected.put(cal.getTime(), counts);

        cal.set(Calendar.MONTH, Calendar.APRIL);
        counts = new HashMap<String, Long>();
        counts.put("invalid", 1L);
        expected.put(cal.getTime(), counts);

        cal.set(Calendar.MONTH, Calendar.MAY);
        counts = new HashMap<String, Long>();
        counts.put("invalid", 2L);
        counts.put("valid", 1L);
        expected.put(cal.getTime(), counts);

        cal.set(Calendar.MONTH, Calendar.JUNE);
        counts = new HashMap<String, Long>();
        counts.put("partial", 2L);
        expected.put(cal.getTime(), counts);

        cal.set(Calendar.MONTH, Calendar.JULY);
        counts = new HashMap<String, Long>();
        counts.put("valid", 1L);
        expected.put(cal.getTime(), counts);

        attributes = new HashMap<String, String>() {
            {
                this.put("attrib1", "val1");
            }
        };

        output[1] = $(attributes, expected);


        expected = new HashMap<Date, Map<String, Long>>();

        cal.set(Calendar.MONTH, Calendar.MARCH);
        counts = new HashMap<String, Long>();
        counts.put("invalid", 1L);
        expected.put(cal.getTime(), counts);

        cal.set(Calendar.MONTH, Calendar.APRIL);
        counts = new HashMap<String, Long>();
        counts.put("invalid", 1L);
        expected.put(cal.getTime(), counts);

        cal.set(Calendar.MONTH, Calendar.MAY);
        counts = new HashMap<String, Long>();
        counts.put("invalid", 1L);
        counts.put("valid", 1L);
        expected.put(cal.getTime(), counts);

        cal.set(Calendar.MONTH, Calendar.JUNE);
        counts = new HashMap<String, Long>();
        counts.put("partial", 1L);
        expected.put(cal.getTime(), counts);

        cal.set(Calendar.MONTH, Calendar.JULY);
        counts = new HashMap<String, Long>();
        counts.put("valid", 1L);
        expected.put(cal.getTime(), counts);

        attributes = new HashMap<String, String>() {
            {
                this.put("attrib2", "val2");
            }
        };

        output[2] = $(attributes, expected);


        expected = new HashMap<Date, Map<String, Long>>();

        cal.set(Calendar.MONTH, Calendar.MARCH);
        counts = new HashMap<String, Long>();
        counts.put("invalid", 1L);
        expected.put(cal.getTime(), counts);

        cal.set(Calendar.MONTH, Calendar.APRIL);
        counts = new HashMap<String, Long>();
        counts.put("invalid", 1L);
        expected.put(cal.getTime(), counts);

        cal.set(Calendar.MONTH, Calendar.MAY);
        counts = new HashMap<String, Long>();
        counts.put("valid", 1L);
        expected.put(cal.getTime(), counts);

        attributes = new HashMap<String, String>() {
            {
                this.put("attrib1", "val1");
                this.put("attrib2", "val2");
            }
        };

        output[3] = $(attributes, expected);

        expected = new HashMap<Date, Map<String, Long>>();
        attributes = new HashMap<String, String>() {
            {
                this.put("badattrib", "val1");
            }
        };

        output[4] = $(attributes, expected);

        attributes = new HashMap<String, String>() {
            {
                this.put("attrib1", "badval");
            }
        };

        output[5] = $(attributes, expected);

        attributes = new HashMap<String, String>() {
            {
                this.put("badattrib", "badval");
            }
        };

        output[6] = $(attributes, expected);


        return output;
    }

    @SuppressWarnings("checkstyle:indentation")
    private Map<Date, Map<String, Long>> intersect(Map<Date, Map<String, Long>>... maps) {
        HashMap<Date, Map<String, Long>> intersection = new HashMap<Date, Map<String, Long>>();
        HashMap<String, Long> counts;
        Map<String, Long> control, temp;
        long mincount, count;
        boolean insert;

        if (maps.length > 0) {
            // Note: Checkstyle doesn't gracefully handle loop labels.
            dateloop: for (Date date : maps[0].keySet()) {
                for (Map<Date, Map<String, Long>> map : maps) {
                    if (!map.containsKey(date)) {
                        continue dateloop;
                    }
                }

                counts = new HashMap<String, Long>();
                control = maps[0].get(date);

                statusloop: for (String status : control.keySet()) {
                    mincount = Integer.MAX_VALUE;
                    insert = false;

                    for (Map<Date, Map<String, Long>> map : maps) {
                        temp = map.get(date);
                        if (!temp.containsKey(status)) {
                            continue statusloop;
                        }

                        count = temp.get(status);
                        mincount = (count < mincount ? count : mincount);
                        insert = true;
                    }

                    if (insert) {
                        counts.put(status, mincount);
                    }
                }

                if (counts.size() > 0) {
                    intersection.put(date, counts);
                }
            }
        }

        return intersection;
    }

    private Compliance performGetByIdTest() {
        List<Compliance> snaps = complianceSnapshotCurator.getSnapshotsOnDate(new Date(),
                Arrays.asList("c1", "c4"), null, null);
        // C1 should get filtered out since it was deleted before the target date.
        assertEquals(1, snaps.size());
        Compliance snap = snaps.get(0);
        assertEquals("c4", snap.getConsumer().getUuid());
        return snap;
    }

    private Compliance createInitialConsumer(Date createdOn, String uuid, String owner, String status) {
        Compliance snapshot = createSnapshot(createdOn, uuid, owner, status);
        consumerStateCurator.create(new ConsumerState(uuid, owner, createdOn));

        return snapshot;
    }

    private Compliance createSnapshot(Date date, String uuid, String owner, String status) {
        Compliance snapshot = createComplianceSnapshot(date, uuid, owner, status);
        complianceSnapshotCurator.create(snapshot);

        return snapshot;
    }

    private Compliance setConsumerDeleted(Date deletedOn, String uuid, String owner) {
        Compliance snapshot = createSnapshot(deletedOn, uuid, owner, "invalid");
        consumerStateCurator.setConsumerDeleted(uuid, deletedOn);

        return snapshot;
    }

    private Entitlement createEntitlement(String sku, String productName, int quantity,
        Date startDate, Map<String, String> attributes) {

        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.setTime(startDate);
        cal.set(Calendar.YEAR, cal.get(Calendar.YEAR) + 1);

        Entitlement entitlement = new Entitlement(quantity, startDate, cal.getTime());
        entitlement.setProductId(sku);
        entitlement.setProductName(productName);
        entitlement.setAttributes(attributes);

        return entitlement;
    }

    private void attachEntitlement(Compliance compliance, Entitlement entitlement) {
        // Make a (shallow) copy of the entitlement...
        Entitlement copy = new Entitlement(
            entitlement.getQuantity(),
            entitlement.getStartDate(),
            entitlement.getEndDate()
        );

        copy.setProductId(entitlement.getProductId());
        copy.setProductName(entitlement.getProductName());
        copy.setAttributes(entitlement.getAttributes());

        compliance.addEntitlementSnapshot(copy);
        complianceSnapshotCurator.save(compliance);
    }

    private void processAndCheckResults(Map<String, Integer> expected, Set<Compliance> results) {
        // Make sure that we find the correct counts.
        HashMap<String, Integer> processed = new HashMap<String, Integer>();
        for (Compliance cs : results) {
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

    private String loadJsonFile(String testFile) throws Exception {
        URL fileUrl = Thread.currentThread().getContextClassLoader().getResource(testFile);
        assertNotNull(fileUrl);
        File f = new File(fileUrl.toURI());
        assertTrue(f.exists());
        return new String(Files.readAllBytes(f.toPath()));
    }
}
