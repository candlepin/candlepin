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

import static org.candlepin.util.Util.addDaysToDt;
import static org.candlepin.util.Util.addToFields;
import static org.candlepin.util.Util.newSet;
import static org.candlepin.util.Util.toDate;
import static org.candlepin.util.Util.tomorrow;
import static org.candlepin.util.Util.yesterday;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.candlepin.test.DatabaseTestFixture;

import org.junit.Test;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;

/**
 * CertificateSerialCuratorTest
 */
public class CertificateSerialCuratorTest extends DatabaseTestFixture {
    @Inject private ProductCurator productCurator;
    @Inject private EntitlementCurator entitlementCurator;
    @Inject private CertificateSerialCurator certSerialCurator;

    private class CertSerialBuilder {
        private Date dt = new Date();
        private boolean collected = false;
        private boolean revoked = false;

        public CertSerialBuilder collected(boolean val) {
            this.collected = val;
            return this;
        }

        public CertSerialBuilder withExpDate(String date) {
            this.dt = toDate(date);
            return this;
        }

        public CertSerialBuilder withExpDate(Date dt) {
            this.dt = dt;
            return this;
        }

        public CertSerialBuilder revoked(boolean re) {
            this.revoked = re;
            return this;
        }

        public CertificateSerial save() {
            CertificateSerial serial = new CertificateSerial(dt);
            serial.setCollected(collected);
            serial.setRevoked(revoked);
            serial = certSerialCurator.create(serial);
            return serial;
        }
    }

    public CertSerialBuilder createCS() {
        return this.new CertSerialBuilder();
    }

    @Test
    public void testSerialCreation() {
        CertificateSerial serial = new CertificateSerial(new Date());
        serial = certSerialCurator.create(serial);
        assertNotNull(serial);
        assertNotNull(serial.getId());
    }

    @Test
    public void testRetrieveToBeCollectedSerials1() {
        createCS().withExpDate("01/10/2010").collected(false).revoked(true).save();
        createCS().withExpDate("02/10/2010").collected(true).revoked(true).save();
        createCS().withExpDate("03/10/2010").collected(false).revoked(false).save();
        createCS().withExpDate("04/10/2010").collected(true).revoked(true).save();

        List<CertificateSerial> lcs = this.certSerialCurator.retrieveTobeCollectedSerials().list();
        Set<Date> dates = extractExpiredDates(lcs);
        assertEquals(1, lcs.size());
        assertTrue(dates.contains(toDate("01/10/2010")));
    }

    /**
     * @param lcs
     * @return
     */
    private Set<Date> extractExpiredDates(List<CertificateSerial> lcs) {
        Set<Date> dates = newSet();
        for (Iterator<CertificateSerial> iterator = lcs.iterator(); iterator.hasNext();) {
            CertificateSerial certificateSerial = iterator.next();
            dates.add(certificateSerial.getExpiration());
        }
        return dates;
    }

    @Test
    public void testRetrieveToBeCollectedSerials2() {
        createCS().withExpDate("01/10/2010").collected(true).revoked(true).save();
        List<CertificateSerial> lcs = this.certSerialCurator.retrieveTobeCollectedSerials().list();
        assertEquals(0, lcs.size());
    }

    @Test
    public void testGetExpiredSerialsWithFewExpiredAndRevokedSerials() {
        Date yesterday = yesterday();
        Date threeDaysPrev = addDaysToDt(-3);
        createCS().withExpDate(yesterday).collected(true).revoked(true).save();
        createCS().withExpDate(addDaysToDt(3)).collected(true).revoked(true).save();
        createCS().withExpDate(threeDaysPrev).collected(true).revoked(true).save();
        List<CertificateSerial> lcs = this.certSerialCurator.getExpiredSerials().list();
        Set<Date> dates = extractExpiredDates(lcs);
        assertEquals(2, lcs.size());
        assertTrue(dates.contains(yesterday));
        assertTrue(dates.contains(threeDaysPrev));
    }

    @Test
    public void testGetExpiredSerialsWithEmptyDatabase() {
        List<CertificateSerial> lcs = this.certSerialCurator.getExpiredSerials().list();
        assertEquals(0, lcs.size());
    }

    @Test
    public void testGetExpiredSerialsWithNonRevokedButExpiredSerials() {
        createCS().withExpDate(yesterday()).revoked(false).save();
        List<CertificateSerial> lcs = this.certSerialCurator.getExpiredSerials().list();
        assertEquals(0, lcs.size());
    }

    @Test
    public void testDeleteExpiredSerialsWithEmptyDatabase() {
        assertEquals(0, this.certSerialCurator.deleteExpiredSerials());
    }

    @Test
    public void testDeleteExpiredSerialsWithNonRevokedButExpiredSerials() {
        createCS().withExpDate(yesterday()).revoked(false).save();
        createCS().withExpDate(addDaysToDt(-20)).revoked(false).save();
        assertEquals(0, this.certSerialCurator.deleteExpiredSerials());
    }

    @Test
    public void testDeleteExpiredSerialsWithRevokedAndExpiredSerials() {
        createCS().withExpDate(addDaysToDt(-2)).revoked(true).save();
        createCS().withExpDate(addDaysToDt(-10)).revoked(true).save();
        assertEquals(2, this.certSerialCurator.deleteExpiredSerials());
    }

    /**
     * This tests the batching logic for IN clause limit
     */
    @Test
    public void testDelete50ExpiredSerials() {
        setPrivateField(CertificateSerialCurator.class, "inClauseLimit", 50);
        for (int i = 0; i < 50; i++) {
            createCS().withExpDate(addDaysToDt(-10)).revoked(true).save();
        }
        assertEquals(50, this.certSerialCurator.deleteExpiredSerials());
    }

    @Test
    public void testDelete55ExpiredSerials() {
        setPrivateField(CertificateSerialCurator.class, "inClauseLimit", 55);
        for (int i = 0; i < 55; i++) {
            createCS().withExpDate(addDaysToDt(-10)).revoked(true).save();
        }
        assertEquals(55, this.certSerialCurator.deleteExpiredSerials());
    }

    private void setPrivateField(Class<CertificateSerialCurator> clazz, String intField, int val) {
        Field f;
        try {
            f = clazz.getDeclaredField(intField);
            f.setAccessible(true);
            f.setInt(clazz, val);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testDeleteExpiredSerialsWithMixedSerials() {
        createCS().withExpDate(addToFields(0, -3, 0)).revoked(true).save();
        createCS().withExpDate(addToFields(0, 0, -1)).revoked(true).save();
        createCS().withExpDate(addDaysToDt(-10)).revoked(true).save();
        createCS().withExpDate(addDaysToDt(30)).revoked(true).save();
        createCS().withExpDate(tomorrow()).revoked(false).save();
        assertEquals(3, this.certSerialCurator.deleteExpiredSerials());
    }

    @Test
    public void testListBySerialIds() {
        CertificateSerial serial = createCS().withExpDate("03/10/2010")
            .collected(false).revoked(false).save();
        CertificateSerial serial1 = createCS().withExpDate("03/10/2012")
            .collected(true).revoked(true).save();

        String[] ids = new String[2];
        ids[0] = String.valueOf(serial.getSerial());
        ids[1] = String.valueOf(serial1.getSerial());

        List<CertificateSerial> serials = certSerialCurator.listBySerialIds(ids).list();
        assertEquals(2, serials.size());

        // verify
        Map<BigInteger, CertificateSerial> values =
            new HashMap<BigInteger, CertificateSerial>();

        for (CertificateSerial s : serials) {
            values.put(s.getSerial(), s);
        }

        assertNotNull(values.get(serial.getSerial()));
        assertNotNull(values.get(serial1.getSerial()));
    }

    @Test
    public void givenNullReturnsNull() {
        assertEquals(null, certSerialCurator.listBySerialIds(null));
    }

}
