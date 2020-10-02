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
package org.candlepin.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.util.Util;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;



/**
 * CertificateSerialCuratorTest
 */
public class CertificateSerialCuratorTest extends DatabaseTestFixture {

    /**
     * Utility class for building and fetching CertificateSerial instances
     */
    private static class CertSerialBuilder {
        private CertificateSerialCurator curator;
        private List<CertificateSerial> created;

        private Date expiration;
        private boolean collected;
        private boolean revoked;

        public CertSerialBuilder(CertificateSerialCurator curator) {
            this.curator = curator;
            this.created = new ArrayList<>();

            this.expiration = getDefaultExpiry();
            this.collected = false;
            this.revoked = false;
        }


        public CertSerialBuilder collected(boolean collected) {
            this.collected = collected;
            return this;
        }

        public CertSerialBuilder withExpDate(String date) {
            this.expiration = Util.toDate(date);
            return this;
        }

        public CertSerialBuilder withExpDate(Date expiration) {
            this.expiration = expiration;
            return this;
        }

        public CertSerialBuilder revoked(boolean revoked) {
            this.revoked = revoked;
            return this;
        }

        public CertificateSerial build() {
            CertificateSerial serial = new CertificateSerial(expiration);
            serial.setCollected(collected);
            serial.setRevoked(revoked);
            serial = this.curator.create(serial);

            created.add(serial);

            return serial;
        }

        public Stream<CertificateSerial> fetch(Predicate<CertificateSerial> filter) {
            return this.created.stream().filter(filter);
        }

        private Date getDefaultExpiry() {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.YEAR, 1);
            return cal.getTime();
        }
    }


    @Test
    public void testSerialCreation() {
        CertificateSerial serial = new CertificateSerial(new Date());
        serial = certSerialCurator.create(serial);
        assertNotNull(serial);
        assertNotNull(serial.getId());
    }

    @Test
    @SuppressWarnings("indentation")
    public void testGetUncollectedRevokedCertSerials() {
        Date now = new Date();
        Date lastWeek = Util.addDaysToDt(-7);
        Date nextWeek = Util.addDaysToDt(7);

        CertSerialBuilder builder = new CertSerialBuilder(this.certSerialCurator);
        builder.withExpDate(lastWeek).collected(false).revoked(false).build();
        builder.withExpDate(lastWeek).collected(false).revoked(true).build();
        builder.withExpDate(lastWeek).collected(true).revoked(false).build();
        builder.withExpDate(lastWeek).collected(true).revoked(true).build();
        builder.withExpDate(now).collected(false).revoked(false).build();
        builder.withExpDate(now).collected(false).revoked(true).build();
        builder.withExpDate(now).collected(true).revoked(false).build();
        builder.withExpDate(now).collected(true).revoked(true).build();
        builder.withExpDate(nextWeek).collected(false).revoked(false).build();
        builder.withExpDate(nextWeek).collected(false).revoked(true).build();
        builder.withExpDate(nextWeek).collected(true).revoked(false).build();
        builder.withExpDate(nextWeek).collected(true).revoked(true).build();

        List<Long> expected = builder
            .fetch((serial) -> serial != null && !serial.isCollected() &&
                serial.isRevoked() && serial.getExpiration().compareTo(now) >= 0)
            .map(CertificateSerial::getId)
            .collect(Collectors.toList());

        List<Long> uncollected = this.certSerialCurator.getUncollectedRevokedCertSerials().list();

        assertNotNull(uncollected);
        assertEquals(expected.size(), uncollected.size());
        assertTrue(uncollected.containsAll(expected));
    }

    @Test
    public void testGetUncollectedRevokedCertSerialsWithNoMatchingSerials() {
        Date now = new Date();
        Date lastWeek = Util.addDaysToDt(-7);
        Date nextWeek = Util.addDaysToDt(7);

        CertSerialBuilder builder = new CertSerialBuilder(this.certSerialCurator);
        builder.withExpDate(lastWeek).collected(false).revoked(false).build();
        // builder.withExpDate(lastWeek).collected(false).revoked(true).build();
        builder.withExpDate(lastWeek).collected(true).revoked(false).build();
        builder.withExpDate(lastWeek).collected(true).revoked(true).build();
        builder.withExpDate(now).collected(false).revoked(false).build();
        // builder.withExpDate(now).collected(false).revoked(true).build();
        builder.withExpDate(now).collected(true).revoked(false).build();
        builder.withExpDate(now).collected(true).revoked(true).build();
        builder.withExpDate(nextWeek).collected(false).revoked(false).build();
        // builder.withExpDate(nextWeek).collected(false).revoked(true).build();
        builder.withExpDate(nextWeek).collected(true).revoked(false).build();
        builder.withExpDate(nextWeek).collected(true).revoked(true).build();

        List<Long> uncollected = this.certSerialCurator.getUncollectedRevokedCertSerials().list();

        assertNotNull(uncollected);
        assertTrue(uncollected.isEmpty());
    }

    @Test
    public void testGetUncollectedRevokedCertSerialsWithNoData() {
        List<Long> uncollected = this.certSerialCurator.getUncollectedRevokedCertSerials().list();

        assertNotNull(uncollected);
        assertTrue(uncollected.isEmpty());
    }

    @Test
    public void testGetExpiredRevokedCertSerials() {
        Date now = new Date();
        Date lastWeek = Util.addDaysToDt(-7);
        Date nextWeek = Util.addDaysToDt(7);

        CertSerialBuilder builder = new CertSerialBuilder(this.certSerialCurator);
        builder.withExpDate(lastWeek).collected(false).revoked(false).build();
        builder.withExpDate(lastWeek).collected(false).revoked(true).build();
        builder.withExpDate(lastWeek).collected(true).revoked(false).build();
        builder.withExpDate(lastWeek).collected(true).revoked(true).build();
        builder.withExpDate(now).collected(false).revoked(false).build();
        builder.withExpDate(now).collected(false).revoked(true).build();
        builder.withExpDate(now).collected(true).revoked(false).build();
        builder.withExpDate(now).collected(true).revoked(true).build();
        builder.withExpDate(nextWeek).collected(false).revoked(false).build();
        builder.withExpDate(nextWeek).collected(false).revoked(true).build();
        builder.withExpDate(nextWeek).collected(true).revoked(false).build();
        builder.withExpDate(nextWeek).collected(true).revoked(true).build();

        Date cutoff = Util.midnight();
        List<Long> expected = builder
            .fetch((serial) -> serial != null && serial.isRevoked() && serial.getExpiration().before(cutoff))
            .map(CertificateSerial::getId)
            .collect(Collectors.toList());

        List<Long> uncollected = this.certSerialCurator.getExpiredRevokedCertSerials().list();

        assertNotNull(uncollected);
        assertEquals(expected.size(), uncollected.size());
        assertTrue(uncollected.containsAll(expected));
    }

    @Test
    public void testGetExpiredRevokedCertSerialsWithNoMatchingSerials() {
        Date now = new Date();
        Date lastWeek = Util.addDaysToDt(-7);
        Date nextWeek = Util.addDaysToDt(7);

        CertSerialBuilder builder = new CertSerialBuilder(this.certSerialCurator);
        builder.withExpDate(lastWeek).collected(false).revoked(false).build();
        // builder.withExpDate(lastWeek).collected(false).revoked(true).build();
        builder.withExpDate(lastWeek).collected(true).revoked(false).build();
        // builder.withExpDate(lastWeek).collected(true).revoked(true).build();
        builder.withExpDate(now).collected(false).revoked(false).build();
        builder.withExpDate(now).collected(false).revoked(true).build();
        builder.withExpDate(now).collected(true).revoked(false).build();
        builder.withExpDate(now).collected(true).revoked(true).build();
        builder.withExpDate(nextWeek).collected(false).revoked(false).build();
        builder.withExpDate(nextWeek).collected(false).revoked(true).build();
        builder.withExpDate(nextWeek).collected(true).revoked(false).build();
        builder.withExpDate(nextWeek).collected(true).revoked(true).build();

        List<Long> expected = builder
            .fetch((serial) -> serial != null && serial.isRevoked() && serial.getExpiration().before(now))
            .map(CertificateSerial::getId)
            .collect(Collectors.toList());

        List<Long> uncollected = this.certSerialCurator.getExpiredRevokedCertSerials().list();

        assertNotNull(uncollected);
        assertEquals(expected.size(), uncollected.size());
        assertTrue(uncollected.containsAll(expected));
    }

    @Test
    public void testGetExpiredRevokedCertSerialsWithNoData() {
        List<Long> uncollected = this.certSerialCurator.getExpiredRevokedCertSerials().list();

        assertNotNull(uncollected);
        assertTrue(uncollected.isEmpty());
    }

    @Test
    public void testGetExpiredRevokedCertSerialsWithSpecifiedDate() {
        Date now = new Date();
        Date lastWeek = Util.addDaysToDt(-7);
        Date nextWeek = Util.addDaysToDt(7);
        Date cutoff = Util.addDaysToDt(1);

        CertSerialBuilder builder = new CertSerialBuilder(this.certSerialCurator);
        builder.withExpDate(lastWeek).collected(false).revoked(false).build();
        builder.withExpDate(lastWeek).collected(false).revoked(true).build();
        builder.withExpDate(lastWeek).collected(true).revoked(false).build();
        builder.withExpDate(lastWeek).collected(true).revoked(true).build();
        builder.withExpDate(now).collected(false).revoked(false).build();
        builder.withExpDate(now).collected(false).revoked(true).build();
        builder.withExpDate(now).collected(true).revoked(false).build();
        builder.withExpDate(now).collected(true).revoked(true).build();
        builder.withExpDate(nextWeek).collected(false).revoked(false).build();
        builder.withExpDate(nextWeek).collected(false).revoked(true).build();
        builder.withExpDate(nextWeek).collected(true).revoked(false).build();
        builder.withExpDate(nextWeek).collected(true).revoked(true).build();

        List<Long> expected = builder
            .fetch((serial) -> serial != null && serial.isRevoked() && serial.getExpiration().before(cutoff))
            .map(CertificateSerial::getId)
            .collect(Collectors.toList());

        List<Long> uncollected = this.certSerialCurator.getExpiredRevokedCertSerials(cutoff).list();

        assertNotNull(uncollected);
        assertEquals(expected.size(), uncollected.size());
        assertTrue(uncollected.containsAll(expected));
    }

    @Test
    public void testGetExpiredRevokedCertSerialsWithNullDateThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
            this.certSerialCurator.getExpiredRevokedCertSerials(null)
        );
    }

    @Test
    public void testListBySerialIds() {
        CertSerialBuilder builder = new CertSerialBuilder(this.certSerialCurator);

        CertificateSerial serial = builder.withExpDate("03/10/2010").collected(false).revoked(false).build();
        CertificateSerial serial1 = builder.withExpDate("03/10/2012").collected(true).revoked(true).build();

        List<String> ids = Arrays.asList(
            String.valueOf(serial.getSerial()),
            String.valueOf(serial1.getSerial())
        );

        List<CertificateSerial> serials = certSerialCurator.listBySerialIds(ids).list();
        assertEquals(2, serials.size());

        // verify
        Map<BigInteger, CertificateSerial> values = new HashMap<>();

        for (CertificateSerial s : serials) {
            values.put(s.getSerial(), s);
        }

        assertNotNull(values.get(serial.getSerial()));
        assertNotNull(values.get(serial1.getSerial()));
    }

    @Test
    public void testListBySerialIdsReturnsNullGivenNull() {
        assertNull(certSerialCurator.listBySerialIds(null));
    }

    @Test
    public void certSerialCreateWithManuallySetId() {
        Long expectedSerialNumber = Util.generateUniqueLong();
        CertificateSerial serial = new CertificateSerial(expectedSerialNumber, new Date());
        // When manually setting the id for an entity, hibernate requires that
        // we call merge instead of save/persist.
        certSerialCurator.merge(serial);
        assertNotNull(serial);
        assertNotNull(serial.getId());
        assertEquals(expectedSerialNumber, serial.getId());

        CandlepinQuery<CertificateSerial> serialQuery =
            certSerialCurator.listBySerialIds(Collections.singletonList(serial.getId().toString()));

        assertEquals(1, serialQuery.getRowCount());
    }

    @Test
    public void deleteAllExpiredCertsThatHaveBeenRevokedButNotYetBeenCollected() {
        CertSerialBuilder builder = new CertSerialBuilder(this.certSerialCurator);

        // Should not get deleted as it has not yet been expired.
        CertificateSerial serial1 = builder.collected(false).revoked(true).build();
        // Should not get deleted since it hasn't been revoked.
        CertificateSerial serial2 = builder.withExpDate("03/10/2010").collected(false).revoked(false).build();
        // Should get deleted as it is expired, revoked and not collected.
        CertificateSerial serial3 = builder.withExpDate("03/10/2012").collected(false).revoked(true).build();
        // Should not get deleted since it has been collected.
        CertificateSerial serial4 = builder.withExpDate("03/10/2012").collected(true).revoked(true).build();

        List<String> expected = builder
            .fetch(Objects::nonNull)
            .map((serial) -> serial.getId().toString())
            .collect(Collectors.toList());

        certSerialCurator.deleteRevokedExpiredAndNotCollectedSerials();

        List<CertificateSerial> fetched =
            certSerialCurator.listBySerialIds(expected).list();
        assertEquals(3, fetched.size());
        assertTrue(fetched.contains(serial1));
        assertTrue(fetched.contains(serial2));
        assertFalse(fetched.contains(serial3));
        assertTrue(fetched.contains(serial4));
    }

}
