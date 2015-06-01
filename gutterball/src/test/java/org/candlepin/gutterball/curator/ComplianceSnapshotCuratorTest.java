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

import org.candlepin.common.config.PropertyConverter;
import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.gutterball.DatabaseTestFixture;
import org.candlepin.gutterball.jackson.GutterballObjectMapper;
import org.candlepin.gutterball.model.ConsumerState;
import org.candlepin.gutterball.model.Event;
import org.candlepin.gutterball.model.snapshot.Compliance;
import org.candlepin.gutterball.model.snapshot.ComplianceStatus;
import org.candlepin.gutterball.model.snapshot.CompliantProductReference;
import org.candlepin.gutterball.model.snapshot.Consumer;
import org.candlepin.gutterball.model.snapshot.ConsumerInstalledProduct;
import org.candlepin.gutterball.model.snapshot.Entitlement;
import org.candlepin.gutterball.model.snapshot.NonCompliantProductReference;
import org.candlepin.gutterball.model.snapshot.PartiallyCompliantProductReference;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;



@RunWith(JUnitParamsRunner.class)
public class ComplianceSnapshotCuratorTest extends DatabaseTestFixture {

    private Date baseTestingDate;

    public Calendar getCalendar() {
        Calendar cal = Calendar.getInstance();

        cal.clear();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        return cal;
    }

    @Before
    @SuppressWarnings("checkstyle:methodlength")
    public void initData() {
        Compliance compliance;
        Calendar cal = this.getCalendar();

        // Set up deleted consumer test data
        cal.set(Calendar.MONTH, Calendar.MARCH);
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.DAY_OF_MONTH, 10);

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
                    this.put("management_enabled", "1");
                }
            }
        );
        entitlement1.setProvidedProducts(new HashMap<String, String>() { {
                this.put("p1", "p1");
            }
        });

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
        entitlement2.setProvidedProducts(new HashMap<String, String>() { {
                this.put("p2", "p2");
            }
        });

        Entitlement entitlement3 = createEntitlement(
            "testsku3",
            "test product 3",
            3,
            baseTestingDate,
            new HashMap<String, String>() {
                {
                    this.put("attrib2", "val2");
                    this.put("management_enabled", "0");
                }
            }
        );
        entitlement3.setProvidedProducts(new HashMap<String, String>() { {
                this.put("p3", "p3");
            }
        });

        this.beginTransaction();

        // Consumer products
        Set<String> c1prods = new HashSet(Arrays.asList("p1"));
        Set<String> c2prods = new HashSet(Arrays.asList("p1", "p2"));
        Set<String> c3prods = new HashSet(Arrays.asList("p3", "p4"));
        Set<String> c4prods = new HashSet(Arrays.asList("p1", "p4"));

        // Consumer created
        cal.set(Calendar.MONTH, Calendar.MARCH);
        compliance = createInitialConsumerWithCompliances(cal.getTime(), "c1", "o1", "invalid",
            c1prods, null, null, c1prods);
        attachEntitlement(compliance, entitlement1);
        // Simulate status change
        cal.set(Calendar.MONTH, Calendar.MAY);
        compliance = createSnapshotWithProductsAndCompliances(cal.getTime(), "c1", "o1", "valid",
            c1prods, c1prods, null, null);
        attachEntitlement(compliance, entitlement1);
        // Consumer was deleted
        cal.set(Calendar.MONTH, Calendar.JUNE);
        compliance = setConsumerDeleted(cal.getTime(), "c1", "o1");
        attachEntitlement(compliance, entitlement1);

        cal.set(Calendar.MONTH, Calendar.APRIL);
        compliance = createInitialConsumerWithCompliances(cal.getTime(), "c2", "o1", "invalid",
            c2prods, null, null, c2prods);
        attachEntitlement(compliance, entitlement1);
        attachEntitlement(compliance, entitlement2);

        cal.set(Calendar.MONTH, Calendar.MAY);
        compliance = createInitialConsumerWithCompliances(cal.getTime(), "c3", "o2", "invalid",
            c3prods, null, null, c3prods);
        attachEntitlement(compliance, entitlement2);
        cal.set(Calendar.MONTH, Calendar.JUNE);
        compliance = createSnapshotWithProductsAndCompliances(cal.getTime(), "c3", "o2", "partial",
            c3prods, new HashSet(Arrays.asList("p3")), new HashSet(Arrays.asList("p4")), null);
        attachEntitlement(compliance, entitlement2);

        cal.set(Calendar.MONTH, Calendar.MAY);
        compliance = createInitialConsumerWithCompliances(cal.getTime(), "c4", "o3", "invalid",
            c4prods, null, null, c4prods);
        attachEntitlement(compliance, entitlement2);
        attachEntitlement(compliance, entitlement3);
        cal.set(Calendar.MONTH, Calendar.JUNE);
        compliance = createSnapshotWithProductsAndCompliances(cal.getTime(), "c4", "o3", "partial",
            c4prods, null, c4prods, null);
        attachEntitlement(compliance, entitlement2);
        attachEntitlement(compliance, entitlement3);
        cal.set(Calendar.MONTH, Calendar.JULY);
        compliance = createSnapshotWithProductsAndCompliances(cal.getTime(), "c4", "o3", "valid",
            c4prods, c4prods, null, null);
        attachEntitlement(compliance, entitlement2);
        attachEntitlement(compliance, entitlement3);

        this.commitTransaction();

        /*
            Entitlements:
                testsku1 - p1
                testsku2 - p2
                testsku3 - p3, p4

            Installed Products:
                c1:
                    p1

                c2:
                    p1
                    p2

                c3:
                    p3
                    p4

                c4:
                    p1
                    p4

            Entitlement distribution:
                c1:
                    testsku1 (test product 1)
                        attrib1: val1
                        attrib2: val2
                        attrib3: val3

                c2:
                    testsku1 (test product 1)
                        attrib1: val1
                        attrib2: val2
                        attrib3: val3
                    testsku2 (test product 2)
                        attrib1: val1
                        attrib3: val3

                c3:
                    testsku2 (test product 2)
                        attrib1: val1
                        attrib3: val3

                c4:
                    testsku2 (test product 2)
                        attrib1: val1
                        attrib3: val3
                    testsku3 (test product 3)
                        attrib2: val2

            Timeline of the above events:
                March 10th:
                    - consumer c1 is registered
                    - consumer c1 reports it is in an invalid state

                April 10th:
                    - consumer c2 is registered
                    - consumer c2 reports it is in an invalid state

                May 10th:
                    - consumer c3 is registered
                    - consumer c4 is registered
                    - consumer c1 reports it is in a valid state
                    - consumer c3 reports it is in an invalid state
                    - consumer c4 reports it is in an invalid state

                June 10th:
                    - consumer c1 is deleted
                    - consumer c3 reports it is in a partial state
                    - consumer c4 reports it is in a partial state

                July 10th:
                    - consumer c4 reports it is in a valid state
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
    public void testGetAllLatestStatusReportsIterator() {
        // c1 was deleted before the report date.
        List<String> expectedConsumerUuids = Arrays.asList("c2", "c3", "c4");
        Iterator<Compliance> snaps = complianceSnapshotCurator.getSnapshotIterator(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        int received = 0;
        while (snaps.hasNext()) {
            Compliance compliance = snaps.next();

            assertTrue(expectedConsumerUuids.contains(compliance.getConsumer().getUuid()));
            ++received;
        }

        assertEquals(expectedConsumerUuids.size(), received);
    }

    @Test
    public void testFilterLatestByManagementEnabled() {
        // c1 was deleted before the report date.
        List<String> expectedConsumerUuids = Arrays.asList("c2");

        Map<String, String> attributeFilters = new HashMap<String, String>();
        attributeFilters.put("management_enabled", "1");
        Iterator<Compliance> snaps = complianceSnapshotCurator.getSnapshotIterator(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            attributeFilters
        );

        int received = 0;
        Compliance comp = null;
        while (snaps.hasNext()) {
            comp = snaps.next();
            assertTrue(expectedConsumerUuids.contains(comp.getConsumer().getUuid()));
            assertTrue(hasManagementEnabledEnt(comp.getEntitlements()));
            ++received;
        }
        assertEquals(expectedConsumerUuids.size(), received);
    }

    @Test
    public void testFilterLatestByNonManagementEnabled() {
        // c1 was deleted before the report date.
        List<String> expectedConsumerUuids = Arrays.asList("c3", "c4");

        Map<String, String> attributeFilters = new HashMap<String, String>();
        attributeFilters.put("management_enabled", "0");
        Iterator<Compliance> snaps = complianceSnapshotCurator.getSnapshotIterator(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            attributeFilters
        );

        int received = 0;
        while (snaps.hasNext()) {
            Compliance comp = snaps.next();
            assertTrue(expectedConsumerUuids.contains(comp.getConsumer().getUuid()));

            assertFalse(hasManagementEnabledEnt(comp.getEntitlements()));
            ++received;
        }
        assertEquals(expectedConsumerUuids.size(), received);
    }

    private boolean hasManagementEnabledEnt(Set<Entitlement> ents) {
        for (Entitlement ent : ents) {
            Map<String, String> attrs = ent.getAttributes();
            if (attrs.containsKey("management_enabled") && "1".equals(attrs.get("management_enabled"))) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void testGetSnapshotIteratorOnDate() {
        Calendar cal = this.getCalendar();
        cal.setTime(baseTestingDate);
        cal.set(Calendar.MONTH, Calendar.JUNE);
        cal.set(Calendar.DAY_OF_MONTH, 12);

        Iterator<Compliance> snaps = complianceSnapshotCurator.getSnapshotIterator(
            cal.getTime(),
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        List<String> expectedConsumerUuids = Arrays.asList("c2", "c3", "c4");
        Map<String, Date> expectedStatusDates = new HashMap<String, Date>();
        cal.set(Calendar.DAY_OF_MONTH, 10);

        cal.set(Calendar.MONTH, Calendar.APRIL);
        expectedStatusDates.put("c2", cal.getTime());

        cal.set(Calendar.MONTH, Calendar.JUNE);
        expectedStatusDates.put("c3", cal.getTime());

        cal.set(Calendar.MONTH, Calendar.JUNE);
        expectedStatusDates.put("c4", cal.getTime());

        int received = 0;
        while (snaps.hasNext()) {
            Compliance compliance = snaps.next();
            String uuid = compliance.getConsumer().getUuid();

            assertTrue(expectedConsumerUuids.contains(uuid));
            assertEquals(
                "Invalid status found for " + uuid,
                expectedStatusDates.get(uuid),
                compliance.getStatus().getDate()
            );

            ++received;
        }

        assertEquals(expectedConsumerUuids.size(), received);
    }

    @Test
    public void testGetPaginatedSnapshotIteratorOnDate() {
        Calendar cal = this.getCalendar();
        cal.setTime(baseTestingDate);
        cal.set(Calendar.MONTH, Calendar.JUNE);
        cal.set(Calendar.DAY_OF_MONTH, 12);

        List<String> expectedConsumerUuids = Arrays.asList("c2", "c3", "c4");
        Map<String, Date> expectedStatusDates = new HashMap<String, Date>();
        cal.set(Calendar.DAY_OF_MONTH, 10);

        cal.set(Calendar.MONTH, Calendar.APRIL);
        expectedStatusDates.put("c2", cal.getTime());

        cal.set(Calendar.MONTH, Calendar.JUNE);
        expectedStatusDates.put("c3", cal.getTime());

        cal.set(Calendar.MONTH, Calendar.JUNE);
        expectedStatusDates.put("c4", cal.getTime());


        List<Compliance> snaps = new LinkedList<Compliance>();

        for (int offset = 0; offset < 3; ++offset) {
            PageRequest pageRequest = new PageRequest();
            pageRequest.setPage(offset + 1);
            pageRequest.setPerPage(1);

            Page<Iterator<Compliance>> page = complianceSnapshotCurator.getSnapshotIterator(
                cal.getTime(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                pageRequest
            );

            Iterator<Compliance> iterator = page.getPageData();

            while (iterator.hasNext()) {
                snaps.add(iterator.next());
            }
        }

        assertTrue(getUuidsFromSnapshots(snaps).containsAll(Arrays.asList("c2", "c3", "c4")));

        for (Compliance cs : snaps) {
            String uuid = cs.getConsumer().getUuid();
            assertEquals("Invalid status found for " + uuid,
                    expectedStatusDates.get(uuid), cs.getStatus().getDate());
        }
    }

    @Test
    public void testDeletedConsumerIncludedInIteratorIfDeletedAfterTargetDate() {
        // May, June, July 10 -- 2014
        Calendar cal = this.getCalendar();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.MONTH, Calendar.MAY);
        cal.set(Calendar.DAY_OF_MONTH, 12);

        List<String> expectedConsumerUuids = Arrays.asList("c1", "c2", "c3", "c4");
        Iterator<Compliance> snaps = this.complianceSnapshotCurator.getSnapshotIterator(
            cal.getTime(),
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        int received = 0;
        while (snaps.hasNext()) {
            Compliance compliance = snaps.next();
            assertTrue(expectedConsumerUuids.contains(compliance.getConsumer().getUuid()));

            ++received;
        }

        assertEquals(expectedConsumerUuids.size(), received);
    }

    @Test
    public void testGetIteratorByOwner() {
        String expectedOwner = "o2";

        Iterator<Compliance> snaps = complianceSnapshotCurator.getSnapshotIterator(
            new Date(),
            null,
            Arrays.asList(expectedOwner),
            null,
            null,
            null,
            null,
            null
        );

        assertTrue(snaps.hasNext());
        Compliance compliance = snaps.next();
        Consumer consumerSnapshot = compliance.getConsumer();

        assertEquals(expectedOwner, consumerSnapshot.getOwner().getKey());
        assertEquals("c3", consumerSnapshot.getUuid());
        assertFalse(snaps.hasNext());
    }

    private Compliance performGetByIdTest() {
        Iterator<Compliance> snaps = complianceSnapshotCurator.getSnapshotIterator(
            new Date(),
            Arrays.asList("c1", "c4"),
            null,
            null,
            null,
            null,
            null,
            null
        );

        // C1 should get filtered out since it was deleted before the target date.
        assertTrue(snaps.hasNext());
        Compliance compliance = snaps.next();
        assertEquals("c4", compliance.getConsumer().getUuid());
        assertFalse(snaps.hasNext());

        return compliance;
    }

    @Test
    public void testGetByConsumerUuid() {
        this.performGetByIdTest();
    }

    @Test
    public void assertLatestStatusIsReturnedForConsumer() {
        Compliance snap = this.performGetByIdTest();
        ComplianceStatus status = snap.getStatus();
        assertEquals(snap.getDate(), status.getDate());

        Calendar cal = this.getCalendar();
        cal.setTime((Date) snap.getDate());

        assertEquals(2012, cal.get(Calendar.YEAR));
        assertEquals(10, cal.get(Calendar.DAY_OF_MONTH));
        assertEquals(Calendar.JULY, cal.get(Calendar.MONTH));
    }

    @Test
    public void testGetIteratorByStatus() {
        String expectedStatus = "partial";
        Iterator<Compliance> snaps = complianceSnapshotCurator.getSnapshotIterator(
            null,
            null,
            null,
            Arrays.asList(expectedStatus),
            null,
            null,
            null,
            null
        );

        assertTrue(snaps.hasNext());
        Compliance compliance = snaps.next();

        assertEquals("c3", compliance.getConsumer().getUuid());
        assertFalse(snaps.hasNext());
    }


    @Test
    public void testGetIteratorByProductName() {
        String expectedProduct = "p1";

        // Note: c1 is deleted before the test is run (June 10, 2012)
        List<String> expectedConsumerUuids = Arrays.asList("c2", "c4");

        Iterator<Compliance> snaps = complianceSnapshotCurator.getSnapshotIterator(
            null,
            null,
            null,
            null,
            Arrays.asList(expectedProduct),
            null,
            null,
            null
        );

        int received = 0;
        while (snaps.hasNext()) {
            Compliance comp = snaps.next();
            assertTrue(expectedConsumerUuids.contains(comp.getConsumer().getUuid()));

            // Ensure the product was listed.
            boolean found = false;
            for (ConsumerInstalledProduct product : comp.getConsumer().getInstalledProducts()) {
                if (expectedProduct.equalsIgnoreCase(product.getProductName())) {
                    found = true;
                    break;
                }
            }

            assertTrue("Expected product not found in consumer's installed product list.", found);

            ++received;
        }

        assertEquals(expectedConsumerUuids.size(), received);
    }

    @Test
    public void testGetIteratorBySubscriptionSku() {
        String expectedSku = "testsku2";
        List<String> expectedConsumerUuids = Arrays.asList("c2", "c3", "c4");

        Iterator<Compliance> snaps = complianceSnapshotCurator.getSnapshotIterator(
            null,
            null,
            null,
            null,
            null,
            Arrays.asList(expectedSku),
            null,
            null
        );

        int received = 0;
        while (snaps.hasNext()) {
            Compliance comp = snaps.next();
            assertTrue(expectedConsumerUuids.contains(comp.getConsumer().getUuid()));

            // Ensure the product was listed.
            boolean found = false;
            for (Entitlement entitlement : comp.getEntitlements()) {
                if (expectedSku.equalsIgnoreCase(entitlement.getProductId())) {
                    found = true;
                    break;
                }
            }

            assertTrue("Expected subscription sku not found in compliance entitlement list.", found);

            ++received;
        }

        assertEquals(expectedConsumerUuids.size(), received);
    }

    @Test
    public void testGetIteratorBySubscriptionName() {
        String expectedSubscription = "test product 2";
        List<String> expectedConsumerUuids = Arrays.asList("c2", "c3", "c4");

        Iterator<Compliance> snaps = complianceSnapshotCurator.getSnapshotIterator(
            null,
            null,
            null,
            null,
            null,
            null,
            Arrays.asList(expectedSubscription),
            null
        );

        int received = 0;
        while (snaps.hasNext()) {
            Compliance comp = snaps.next();
            assertTrue(expectedConsumerUuids.contains(comp.getConsumer().getUuid()));

            // Ensure the product was listed.
            boolean found = false;
            for (Entitlement entitlement : comp.getEntitlements()) {
                if (expectedSubscription.equalsIgnoreCase(entitlement.getProductName())) {
                    found = true;
                    break;
                }
            }

            assertTrue("Expected subscription name not found in compliance entitlement list.", found);

            ++received;
        }

        assertEquals(expectedConsumerUuids.size(), received);
    }

    @Test
    public void testReportOnTargetDate() {
        Calendar cal = this.getCalendar();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.MONTH, Calendar.APRIL);
        cal.set(Calendar.DAY_OF_MONTH, 12);

        Date onDate = cal.getTime();

        Iterator<Compliance> snaps = this.complianceSnapshotCurator.getSnapshotIteratorForConsumer(
            "c1",
            onDate,
            onDate
        );

        int received = 0;

        while (snaps.hasNext()) {
            Compliance compliance = snaps.next();
            assertEquals("c1", compliance.getConsumer().getUuid());
            ++received;
        }

        assertEquals(1, received);
    }

    private void testConsumerTrendReport(Map<String, Integer> expectedCountsPerUUID, Date startDate,
        Date endDate) {

        for (Map.Entry<String, Integer> entry : expectedCountsPerUUID.entrySet()) {
            String uuid = entry.getKey();

            Iterator<Compliance> snaps = this.complianceSnapshotCurator.getSnapshotIteratorForConsumer(
                uuid,
                startDate,
                endDate
            );

            int received = 0;
            while (snaps.hasNext()) {
                Compliance compliance = snaps.next();
                assertEquals(uuid, compliance.getConsumer().getUuid());
                ++received;
            }

            assertEquals(entry.getValue().intValue(), received);
        }

    }


    @Test
    public void testGetAllStatusReports() {
        HashMap<String, Integer> expected = new HashMap<String, Integer>() {
            {
                put("c1", 3);
                put("c2", 1);
                put("c3", 2);
                put("c4", 3);
            }
        };

        this.testConsumerTrendReport(expected, null, null);
    }

    @Test
    public void testGetStatusReportsTimeframe() {
        Calendar cal = this.getCalendar();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.MONTH, Calendar.JUNE);
        cal.set(Calendar.DAY_OF_MONTH, 12);

        Date startDate = cal.getTime();
        Date endDate = new Date();
        HashMap<String, Integer> expected = new HashMap<String, Integer>() {
            {
                put("c2", 1);
                put("c3", 1);
                put("c4", 2);
            }
        };

        this.testConsumerTrendReport(expected, startDate, endDate);
    }

    @Test
    public void testGetComplianceStatusCounts() {
        Map<Date, Map<String, Integer>> expected = this.buildMapForAllStatusCounts();
        Map<Date, Map<String, Integer>> actual = this.complianceSnapshotCurator
            .getComplianceStatusCounts(null, null, null, null, null, null, null);

        assertEquals(expected, actual);
    }

    @Test
    public void testGetPaginatedComplianceStatusCounts() {
        Map<Date, Map<String, Integer>> expected = this.buildMapForAllStatusCounts();
        Map<Date, Map<String, Integer>> data;

        int p = 1;
        int pages = 0;
        PageRequest pageRequest = new PageRequest();

        pageRequest.setPerPage(1);


        do {
            pageRequest.setPage(p);

            Page<Map<Date, Map<String, Integer>>> page = this.complianceSnapshotCurator
                .getComplianceStatusCounts(null, null, null, null, null, null, null, pageRequest);

            if (pages == 0) {
                pages = page.getMaxRecords();
            }

            data = page.getPageData();

            // Make sure we got exactly one result back...
            assertEquals(1, data.size());

            // ...and it contains something from our expected results.
            for (Map.Entry<Date, Map<String, Integer>> entry : data.entrySet()) {
                Map<String, Integer> record = expected.remove(entry.getKey());

                assertNotNull(record);
                assertEquals(record, entry.getValue());
            }
        } while(++p <= pages);

        // Make sure we've exhausted every expected result.
        assertEquals(0, expected.size());
    }

    @Test
    @Parameters(method = "buildMapForStatusCountsAfterDate")
    public void testGetComplianceStatusCountsAfterDate(Date date, Map<Date, Map<String, Integer>> expected) {
        Map<Date, Map<String, Integer>> actual = this.complianceSnapshotCurator
            .getComplianceStatusCounts(date, null, null, null, null, null, null);

        assertEquals(expected, actual);
    }

    @Test
    @Parameters(method = "buildMapForStatusCountsBeforeDate")
    public void testGetComplianceStatusCountsBeforeDate(Date date, Map<Date, Map<String, Integer>> expected) {
        Map<Date, Map<String, Integer>> actual = this.complianceSnapshotCurator
            .getComplianceStatusCounts(null, date, null, null, null, null, null);

        assertEquals(expected, actual);
    }

    @Test
    @Parameters(method = "buildMapForStatusCountsBetweenDates")
    public void testGetComplianceStatusCountsBetweenDates(Date startDate, Date endDate,
        Map<Date, Map<String, Integer>> expected) {

        Map<Date, Map<String, Integer>> actual = this.complianceSnapshotCurator
            .getComplianceStatusCounts(startDate, endDate, null, null, null, null, null);

        assertEquals(expected, actual);
    }

    @Test
    @Parameters(method = "buildMapForStatusCountsWithSku")
    public void testGetComplianceStatusCountsWithValidSku(Date startDate, Date endDate, String sku,
        Map<Date, Map<String, Integer>> expected) {
        Map<Date, Map<String, Integer>> actual;

        actual = this.complianceSnapshotCurator
            .getComplianceStatusCounts(startDate, endDate, null, null, sku, null, null);

        assertEquals(expected, actual);
    }

    @Test
    @Parameters(method = "buildMapForStatusCountsWithSubscriptionName")
    public void testGetComplianceStatusCountsWithSubscription(Date startDate, Date endDate,
        String subscriptionName, Map<Date, Map<String, Integer>> expected) {
        Map<Date, Map<String, Integer>> actual;

        actual = this.complianceSnapshotCurator
            .getComplianceStatusCounts(startDate, endDate, null, null, null, subscriptionName, null);

        assertEquals(expected, actual);
    }

    @Test
    @Parameters(method = "buildMapForStatusCountsWithAttributes")
    public void testGetComplianceStatusCountsWithAttributes(Date startDate, Date endDate,
        Map<String, String> attributes, Map<Date, Map<String, Integer>> expected) {
        Map<Date, Map<String, Integer>> actual;

        actual = this.complianceSnapshotCurator
            .getComplianceStatusCounts(startDate, endDate, null, null, null, null, attributes);

        assertEquals(expected, actual);
    }

    @Test
    @Parameters(method = "buildSubMapForStatusCountsForConsumer")
    public void testGetComplianceStatusCountsByConsumers(List<String> consumers,
        Map<Date, Map<String, Integer>> expected) {

        Map<Date, Map<String, Integer>> actual = this.complianceSnapshotCurator
            .getComplianceStatusCounts(null, null, null, consumers, null, null, null);

        assertEquals(expected, actual);
    }

    @Test
    @Parameters(method = "buildMapForStatusCountsForConsumersBetweenDates")
    public void testGetComplianceStatusCountsByConsumersAndDate(Date startDate, Date endDate,
        List<String> consumers, Map<Date, Map<String, Integer>> expected) {

        Map<Date, Map<String, Integer>> actual = this.complianceSnapshotCurator
            .getComplianceStatusCounts(startDate, endDate, null, consumers, null, null, null);

        assertEquals(expected, actual);
    }






    private Map<Date, Map<String, Integer>> buildMapForAllStatusCounts() {
        Calendar cal = this.getCalendar();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.DAY_OF_MONTH, 10);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);

        Map<Date, Map<String, Integer>> expected = new TreeMap<Date, Map<String, Integer>>();
        HashMap<String, Integer> counts;

        cal.set(Calendar.MONTH, Calendar.MARCH);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 1);
        for (int i = 0; i < 31; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.APRIL);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 2);
        for (int i = 0; i < 30; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.MAY);
        counts = new HashMap<String, Integer>();
        counts.put("valid", 1);
        counts.put("invalid", 3);
        for (int i = 0; i < 31; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.JUNE);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 1);
        counts.put("partial", 2);
        for (int i = 0; i < 30; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.JULY);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 1);
        counts.put("partial", 1);
        counts.put("valid", 1);
        expected.put(cal.getTime(), counts);

        return expected;
    }

    public Object[][] buildMapForStatusCountsBeforeDate() {
        Calendar cal = this.getCalendar();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.DAY_OF_MONTH, 10);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);

        Map<Date, Map<String, Integer>> expected = new TreeMap<Date, Map<String, Integer>>();
        HashMap<String, Integer> counts;

        Object[][] output = new Object[2][];

        output[0] = $(null, this.buildMapForAllStatusCounts());

        cal.set(Calendar.MONTH, Calendar.MARCH);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 1);
        for (int i = 0; i < 31; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.APRIL);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 2);
        for (int i = 0; i < 30; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.MAY);
        counts = new HashMap<String, Integer>();
        counts.put("valid", 1);
        counts.put("invalid", 3);
        expected.put(cal.getTime(), counts);

        cal.set(Calendar.MONTH, Calendar.MAY);
        Date endDate = cal.getTime();

        output[1] = $(endDate, expected);

        return output;
    }

    public Object[][] buildMapForStatusCountsAfterDate() {
        Calendar cal = this.getCalendar();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.DAY_OF_MONTH, 10);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);

        Map<Date, Map<String, Integer>> expected = new TreeMap<Date, Map<String, Integer>>();
        HashMap<String, Integer> counts;

        Object[][] output = new Object[2][];

        output[0] = $(null, this.buildMapForAllStatusCounts());

        cal.set(Calendar.MONTH, Calendar.MAY);
        counts = new HashMap<String, Integer>();
        counts.put("valid", 1);
        counts.put("invalid", 3);
        for (int i = 0; i < 31; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.JUNE);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 1);
        counts.put("partial", 2);
        for (int i = 0; i < 30; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.JULY);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 1);
        counts.put("partial", 1);
        counts.put("valid", 1);
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
                        (Map<Date, Map<String, Integer>>) after[1],
                        (Map<Date, Map<String, Integer>>) before[1]
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
                            (Map<Date, Map<String, Integer>>) after[1],
                            (Map<Date, Map<String, Integer>>) before[1],
                            (Map<Date, Map<String, Integer>>) sku[1]
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
                            (Map<Date, Map<String, Integer>>) after[1],
                            (Map<Date, Map<String, Integer>>) before[1],
                            (Map<Date, Map<String, Integer>>) name[1]
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

        // Impl note:
        // This intersection stuff to check date filtering does not work with dates beyond our data
        // set. If our dates require extrapolating the data, the expected output will be missing
        // all of the necessary extrapolated statuses.

        for (Object[] before : beforeSet) {
            for (Object[] after : afterSet) {
                for (Object[] attribute : attributeSet) {
                    tests.add(new Object[] {
                        after[0],
                        before[0],
                        attribute[0],
                        this.intersect(
                            (Map<Date, Map<String, Integer>>) after[1],
                            (Map<Date, Map<String, Integer>>) before[1],
                            (Map<Date, Map<String, Integer>>) attribute[1]
                        )
                    });
                }
            }
        }

        return tests.toArray();
    }

    private Map<Date, Map<String, Integer>> buildMapForStatusCounts() {
        Calendar cal = this.getCalendar();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.DAY_OF_MONTH, 10);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);

        Map<Date, Map<String, Integer>> expected = new TreeMap<Date, Map<String, Integer>>();
        HashMap<String, Integer> counts;

        cal.set(Calendar.MONTH, Calendar.MARCH);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 1);
        for (int i = 0; i < 31; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.APRIL);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 2);
        for (int i = 0; i < 30; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.MAY);
        counts = new HashMap<String, Integer>();
        counts.put("valid", 1);
        counts.put("invalid", 3);
        for (int i = 0; i < 31; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.JUNE);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 1);
        counts.put("partial", 2);
        for (int i = 0; i < 30; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.JULY);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 1);
        counts.put("partial", 1);
        counts.put("valid", 1);
        expected.put(cal.getTime(), counts);

        return expected;
    }

    private Object[][] buildSubMapForStatusCountsWithSku() {
        Calendar cal = this.getCalendar();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.DAY_OF_MONTH, 10);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);

        Map<Date, Map<String, Integer>> expected;
        Map<Date, Map<String, Integer>> actual;
        HashMap<String, Integer> counts;

        Object[][] output = new Object[5][];

        output[0] = $(null, this.buildMapForAllStatusCounts());


        expected = new TreeMap<Date, Map<String, Integer>>();

        cal.set(Calendar.MONTH, Calendar.MARCH);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 1);
        for (int i = 0; i < 31; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.APRIL);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 2);
        for (int i = 0; i < 30; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.MAY);
        counts = new HashMap<String, Integer>();
        counts.put("valid", 1);
        counts.put("invalid", 1);
        expected.put(cal.getTime(), counts);

        output[1] = $("testsku1", expected);


        expected = new TreeMap<Date, Map<String, Integer>>();

        cal.set(Calendar.MONTH, Calendar.APRIL);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 1);
        for (int i = 0; i < 30; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.MAY);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 3);
        for (int i = 0; i < 31; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.JUNE);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 1);
        counts.put("partial", 2);
        for (int i = 0; i < 30; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.JULY);
        counts = new HashMap<String, Integer>();
        counts.put("valid", 1);
        counts.put("invalid", 1);
        counts.put("partial", 1);
        expected.put(cal.getTime(), counts);

        output[2] = $("testsku2", expected);


        expected = new TreeMap<Date, Map<String, Integer>>();

        cal.set(Calendar.MONTH, Calendar.MAY);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 1);
        for (int i = 0; i < 31; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.JUNE);
        counts = new HashMap<String, Integer>();
        counts.put("partial", 1);
        for (int i = 0; i < 30; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.JULY);
        counts = new HashMap<String, Integer>();
        counts.put("valid", 1);
        expected.put(cal.getTime(), counts);

        output[3] = $("testsku3", expected);


        output[4] = $("badsku", new HashMap<Date, Map<String, Integer>>());

        return output;
    }

    private Object[][] buildSubMapForStatusCountsForConsumer() {
        Calendar cal = this.getCalendar();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.DAY_OF_MONTH, 10);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);

        Object[][] output = new Object[5][];
        Map<Date, Map<String, Integer>> expected;
        HashMap<String, Integer> counts;

        output[0] = $(null, this.buildMapForAllStatusCounts());
        output[1] = $(new LinkedList<String>(), this.buildMapForAllStatusCounts());

        expected = new TreeMap<Date, Map<String, Integer>>();

        cal.set(Calendar.MONTH, Calendar.MARCH);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 1);
        for (int i = 0; i < 61; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.MAY);
        counts = new HashMap<String, Integer>();
        counts.put("valid", 1);
        expected.put(cal.getTime(), counts);

        output[2] = $(Arrays.asList("c1"), expected);


        expected = new TreeMap<Date, Map<String, Integer>>();

        cal.set(Calendar.MONTH, Calendar.APRIL);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 1);
        for (int i = 0; i < 30; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.MAY);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 2);
        for (int i = 0; i < 31; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.JUNE);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 1);
        counts.put("partial", 1);
        expected.put(cal.getTime(), counts);

        output[3] = $(Arrays.asList("c2", "c3"), expected);

        output[4] = $(Arrays.asList("nonexistant"), new HashMap<Date, Map<String, Integer>>());

        return output;
    }

    public Object[] buildMapForStatusCountsForConsumersBetweenDates() {
        LinkedList<Object[]> tests = new LinkedList<Object[]>();
        Object[][] beforeSet = this.buildMapForStatusCountsBeforeDate();
        Object[][] afterSet = this.buildMapForStatusCountsAfterDate();
        Object[][] consumerSet = this.buildSubMapForStatusCountsForConsumer();

        for (Object[] before : beforeSet) {
            for (Object[] after : afterSet) {
                for (Object[] consumers : consumerSet) {
                    tests.add(new Object[] {
                        after[0],
                        before[0],
                        consumers[0],
                        this.intersect(
                            (Map<Date, Map<String, Integer>>) after[1],
                            (Map<Date, Map<String, Integer>>) before[1],
                            (Map<Date, Map<String, Integer>>) consumers[1]
                        )
                    });
                }
            }
        }

        return tests.toArray();
    }

    private Object[][] buildSubMapForStatusCountsWithSubscriptionName() {
        Calendar cal = this.getCalendar();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.DAY_OF_MONTH, 10);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);

        Map<Date, Map<String, Integer>> expected;
        Map<Date, Map<String, Integer>> actual;
        HashMap<String, Integer> counts;

        Object[][] output = new Object[5][];

        output[0] = $(null, this.buildMapForAllStatusCounts());

        expected = new TreeMap<Date, Map<String, Integer>>();

        cal.set(Calendar.MONTH, Calendar.MARCH);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 1);
        for (int i = 0; i < 31; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.APRIL);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 2);
        for (int i = 0; i < 30; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.MAY);
        counts = new HashMap<String, Integer>();
        counts.put("valid", 1);
        counts.put("invalid", 1);
        expected.put(cal.getTime(), counts);

        output[1] = $("test product 1", expected);


        expected = new TreeMap<Date, Map<String, Integer>>();

        cal.set(Calendar.MONTH, Calendar.APRIL);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 1);
        for (int i = 0; i < 30; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.MAY);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 3);
        for (int i = 0; i < 31; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.JUNE);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 1);
        counts.put("partial", 2);
        for (int i = 0; i < 30; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.JULY);
        counts = new HashMap<String, Integer>();
        counts.put("valid", 1);
        counts.put("invalid", 1);
        counts.put("partial", 1);
        expected.put(cal.getTime(), counts);

        output[2] = $("test product 2", expected);


        expected = new TreeMap<Date, Map<String, Integer>>();

        cal.set(Calendar.MONTH, Calendar.MAY);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 1);
        for (int i = 0; i < 31; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.JUNE);
        counts = new HashMap<String, Integer>();
        counts.put("partial", 1);
        for (int i = 0; i < 30; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.JULY);
        counts = new HashMap<String, Integer>();
        counts.put("valid", 1);
        expected.put(cal.getTime(), counts);

        output[3] = $("test product 3", expected);


        output[4] = $("bad product name", new HashMap<Date, Map<String, Integer>>());

        return output;
    }

    @SuppressWarnings("checkstyle:methodlength")
    private Object[][] buildSubMapForStatusCountsWithAttributes() {
        long millis;
        Calendar cal = this.getCalendar();
        cal.set(Calendar.YEAR, 2012);
        cal.set(Calendar.DAY_OF_MONTH, 10);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);

        Map<Date, Map<String, Integer>> expected;
        Map<Date, Map<String, Integer>> actual;
        HashMap<String, Integer> counts;
        Map<String, String> attributes;

        Object[][] output = new Object[7][];

        output[0] = $(null, this.buildMapForAllStatusCounts());

        expected = new TreeMap<Date, Map<String, Integer>>();

        cal.set(Calendar.MONTH, Calendar.MARCH);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 1);
        for (int i = 0; i < 31; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.APRIL);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 2);
        for (int i = 0; i < 30; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.MAY);
        counts = new HashMap<String, Integer>();
        counts.put("valid", 1);
        counts.put("invalid", 3);
        for (int i = 0; i < 31; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.JUNE);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 1);
        counts.put("partial", 2);
        for (int i = 0; i < 30; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.JULY);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 1);
        counts.put("partial", 1);
        counts.put("valid", 1);
        expected.put(cal.getTime(), counts);

        attributes = new HashMap<String, String>() {
            {
                this.put("attrib1", "val1");
            }
        };

        output[1] = $(attributes, expected);


        expected = new TreeMap<Date, Map<String, Integer>>();

        cal.set(Calendar.MONTH, Calendar.MARCH);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 1);
        for (int i = 0; i < 31; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.APRIL);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 2);
        for (int i = 0; i < 30; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.MAY);
        counts = new HashMap<String, Integer>();
        counts.put("valid", 1);
        counts.put("invalid", 2);
        for (int i = 0; i < 31; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.JUNE);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 1);
        counts.put("partial", 1);
        for (int i = 0; i < 30; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.JULY);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 1);
        counts.put("valid", 1);
        expected.put(cal.getTime(), counts);

        attributes = new HashMap<String, String>() {
            {
                this.put("attrib2", "val2");
            }
        };

        output[2] = $(attributes, expected);


        expected = new TreeMap<Date, Map<String, Integer>>();

        cal.set(Calendar.MONTH, Calendar.MARCH);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 1);
        for (int i = 0; i < 31; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.APRIL);
        counts = new HashMap<String, Integer>();
        counts.put("invalid", 2);
        for (int i = 0; i < 30; ++i) {
            expected.put(cal.getTime(), counts);
            cal.add(Calendar.DATE, 1);
        }

        cal.set(Calendar.MONTH, Calendar.MAY);
        counts = new HashMap<String, Integer>();
        counts.put("valid", 1);
        counts.put("invalid", 1);
        expected.put(cal.getTime(), counts);

        attributes = new HashMap<String, String>() {
            {
                this.put("attrib1", "val1");
                this.put("attrib2", "val2");
            }
        };

        output[3] = $(attributes, expected);

        expected = new TreeMap<Date, Map<String, Integer>>();
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
    private Map<Date, Map<String, Integer>> intersect(Map<Date, Map<String, Integer>>... maps) {
        TreeMap<Date, Map<String, Integer>> intersection = new TreeMap<Date, Map<String, Integer>>();
        HashMap<String, Integer> counts;
        Map<String, Integer> control, temp;
        int mincount, count;
        boolean insert;

        if (maps.length > 0) {
            // Note: Checkstyle doesn't gracefully handle loop labels.
            dateloop: for (Date date : maps[0].keySet()) {
                for (Map<Date, Map<String, Integer>> map : maps) {
                    if (!map.containsKey(date)) {
                        continue dateloop;
                    }
                }

                counts = new HashMap<String, Integer>();
                control = maps[0].get(date);

                statusloop: for (String status : control.keySet()) {
                    mincount = Integer.MAX_VALUE;
                    insert = false;

                    for (Map<Date, Map<String, Integer>> map : maps) {
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

    private Compliance createInitialConsumer(Date createdOn, String uuid, String owner,
        String status, Set<String> products) {

        Compliance snapshot = this.createSnapshotWithProducts(createdOn, uuid, owner, status, products);
        this.consumerStateCurator.create(new ConsumerState(uuid, owner, createdOn));

        return snapshot;
    }

    private Compliance createInitialConsumerWithCompliances(Date createdOn, String uuid, String owner,
        String status, Set<String> products, Set<String> compliant, Set<String> partiallyCompliant,
        Set<String> nonCompliant) {

        Compliance snapshot = this.createSnapshotWithProductsAndCompliances(createdOn, uuid, owner,
            status, products, compliant, partiallyCompliant, nonCompliant);

        this.consumerStateCurator.create(new ConsumerState(uuid, owner, createdOn));

        return snapshot;
    }

    private Compliance createSnapshotWithProducts(Date date, String uuid, String owner,
        String status, Set<String> products) {

        Compliance snapshot = createComplianceSnapshotWithProducts(date, uuid, owner, status, null,
            products);
        complianceSnapshotCurator.create(snapshot);

        return snapshot;
    }

    private Compliance createSnapshotWithProductsAndCompliances(Date date, String uuid, String owner,
        String status, Set<String> products, Set<String> compliant, Set<String> partiallyCompliant,
        Set<String> nonCompliant) {

        Compliance snapshot = createComplianceSnapshotWithProductsAndCompliances(date, uuid, owner,
            status, null, products, compliant, partiallyCompliant, nonCompliant);
        complianceSnapshotCurator.create(snapshot);

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

        Calendar cal = this.getCalendar();
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

        // Check that the management enabled flag is updated.
        compliance.getStatus().setManagementEnabled(determineIfManaged(compliance));

        complianceSnapshotCurator.save(compliance);
    }

    private boolean determineIfManaged(Compliance compliance) {
        for (Entitlement ent : compliance.getEntitlements()) {
            boolean managed = entitlementIsManaged(ent);
            if (managed) {
                return true;
            }
        }
        return false;
    }

    private boolean entitlementIsManaged(Entitlement e) {
        boolean managed = false;
        Map<String, String> attrs = e.getAttributes();
        if (attrs != null && attrs.containsKey("management_enabled")) {
            managed = PropertyConverter.toBoolean(attrs.get("management_enabled"));
        }
        return managed;
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
