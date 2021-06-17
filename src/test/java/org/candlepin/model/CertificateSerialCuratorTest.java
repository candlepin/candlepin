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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;



public class CertificateSerialCuratorTest extends DatabaseTestFixture {

    private static final Date EXPIRED = TestUtil.createDate(2010, 10, 3);
    private static final Date NOT_EXPIRED = TestUtil.createDateOffset(2, 0, 0);

    /**
     * Utility class for building and fetching CertificateSerial instances
     */
    private static class CertSerialBuilder {
        private CertificateSerialCurator curator;
        private List<CertificateSerial> created;

        private Date expiration;
        private boolean revoked;

        public CertSerialBuilder(CertificateSerialCurator curator) {
            this.curator = curator;
            this.created = new ArrayList<>();

            this.expiration = getDefaultExpiry();
            this.revoked = false;
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
            serial.setRevoked(revoked);
            serial = this.curator.create(serial);

            created.add(serial);

            return serial;
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
    public void listExistingRevokedSerials() {
        CertSerialBuilder builder = new CertSerialBuilder(this.certSerialCurator);

        builder.revoked(true).build();
        builder.revoked(false).build();
        builder.withExpDate(EXPIRED).revoked(true).build();
        builder.withExpDate(NOT_EXPIRED).revoked(true).build();

        List<Long> serialIds = certSerialCurator.listNonExpiredRevokedSerialIds();

        assertEquals(2, serialIds.size());
    }

    @Test
    public void noRevokedSerialsToList() {
        CertSerialBuilder builder = new CertSerialBuilder(this.certSerialCurator);

        builder.revoked(false).build();
        builder.withExpDate(EXPIRED).revoked(true).build();

        List<Long> serialIds = certSerialCurator.listNonExpiredRevokedSerialIds();

        assertEquals(0, serialIds.size());
    }

    @Test
    public void revokesSpecifiedSerials() {
        CertSerialBuilder builder = new CertSerialBuilder(this.certSerialCurator);

        CertificateSerial build = builder.withExpDate(EXPIRED).revoked(false).build();
        CertificateSerial build1 = builder.withExpDate(EXPIRED).revoked(false).build();
        CertificateSerial build2 = builder.withExpDate(EXPIRED).revoked(true).build();

        int revokedSerials = certSerialCurator.revokeByIds(List.of(
            build.getId(),
            build1.getId(),
            build2.getId()
        ));

        assertEquals(2, revokedSerials);
    }

    @Test
    public void nothingToRevoke() {
        long unknownId = 1123L;
        assertEquals(0, certSerialCurator.revokeByIds(null));
        assertEquals(0, certSerialCurator.revokeByIds(List.of()));
        assertEquals(0, certSerialCurator.revokeByIds(List.of(unknownId)));
    }


    @Test
    public void revokesSpecifiedSerial() {
        CertSerialBuilder builder = new CertSerialBuilder(this.certSerialCurator);

        CertificateSerial serial1 = builder.withExpDate(EXPIRED).revoked(false).build();
        CertificateSerial serial2 = builder.withExpDate(NOT_EXPIRED).revoked(false).build();

        certSerialCurator.revokeById(serial1.getId());
        certSerialCurator.revokeById(serial2.getId());

        certSerialCurator.flush();
        certSerialCurator.clear();

        for (CertificateSerial serial : certSerialCurator.listAll().list()) {
            assertTrue(serial.isRevoked());
        }
    }

}
