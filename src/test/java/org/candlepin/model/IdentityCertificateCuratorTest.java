/**
 * Copyright (c) 2009 - 2021 Red Hat, Inc.
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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class IdentityCertificateCuratorTest extends DatabaseTestFixture {

    @Test
    public void shouldListOnlyExpiredIdCerts() {
        String idCert = createIdCert().getId();
        String include1 = createExpiredIdCertWithConsumer().getId();
        String include2 = createExpiredIdCertWithConsumer().getId();
        String exclude1 = createExpiredIdCertWithUpstreamConsumer().getId();
        String exclude2 = createExpiredIdCertWithUpstreamConsumer().getId();

        List<ExpiredCertificate> expiredCertificates = this.identityCertificateCurator.listAllExpired();

        assertEquals(2, expiredCertificates.size());
        Set<String> expiredCertIds = expiredCertificates.stream()
            .map(ExpiredCertificate::getCertId)
            .collect(Collectors.toSet());
        assertFalse(expiredCertIds.contains(idCert));
        assertFalse(expiredCertIds.contains(exclude1));
        assertFalse(expiredCertIds.contains(exclude2));
        assertTrue(expiredCertIds.contains(include1));
        assertTrue(expiredCertIds.contains(include2));
    }

    @Test
    public void noExpiredCertsToList() {
        assertEquals(0, this.identityCertificateCurator.listAllExpired().size());
    }

    @Test
    public void deleteById() {
        Set<String> certIds = Set.of(
            createExpiredIdCert().getId(),
            createExpiredIdCert().getId(),
            createIdCert().getId()
        );

        int deletedCerts = this.identityCertificateCurator.deleteByIds(certIds);

        assertEquals(3, deletedCerts);
    }

    @Test
    public void nothingToDelete() {
        assertEquals(0, this.identityCertificateCurator.deleteByIds(null));
        assertEquals(0, this.identityCertificateCurator.deleteByIds(List.of()));
        assertEquals(0, this.identityCertificateCurator.deleteByIds(List.of("UnknownId")));
    }

    private IdentityCertificate createExpiredIdCert() {
        IdentityCertificate idCert = TestUtil.createIdCert(Util.yesterday());
        return saveCert(idCert);
    }

    private IdentityCertificate createExpiredIdCertWithConsumer() {
        IdentityCertificate idCert = TestUtil.createIdCert(Util.yesterday());
        Owner owner = createOwner();
        Consumer consumer = createConsumer(owner);
        idCert = saveCert(idCert);
        consumer.setIdCert(idCert);
        consumerCurator.update(consumer);
        return idCert;
    }

    private IdentityCertificate createExpiredIdCertWithUpstreamConsumer() {
        IdentityCertificate idCert = TestUtil.createIdCert(Util.yesterday());
        Owner owner = createOwner();
        UpstreamConsumer upstream = new UpstreamConsumer();
        upstream.setUuid(TestUtil.randomString("uuid"));
        upstream.setName(TestUtil.randomString("upstream"));
        upstream.setType(createConsumerType());
        owner.setUpstreamConsumer(upstream);
        idCert = saveCert(idCert);
        upstream.setIdCert(idCert);
        ownerCurator.saveOrUpdate(owner);
        return idCert;
    }

    private IdentityCertificate createIdCert() {
        IdentityCertificate idCert = TestUtil.createIdCert(TestUtil.createDateOffset(2, 0, 0));
        return saveCert(idCert);
    }

    private IdentityCertificate saveCert(IdentityCertificate cert) {
        cert.setId(null);
        certSerialCurator.create(cert.getSerial());
        return identityCertificateCurator.create(cert);
    }

}
