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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Set;

class ContentAccessCertificateCuratorTest extends DatabaseTestFixture {

    private static final String CERT_1 = "content_1";
    private static final String CERT_2 = "content_2";

    private Owner owner1;
    private Owner owner2;
    private Consumer consumer1;
    private Consumer consumer2;

    @BeforeEach
    void setUp() {
        owner1 = new Owner("owner_1", "Owner 1");
        owner2 = new Owner("owner_2", "Owner 2");
        this.ownerCurator.save(owner1);
        this.ownerCurator.save(owner2);
        ConsumerType cType = new ConsumerType(ConsumerType.ConsumerTypeEnum.CANDLEPIN);
        this.consumerTypeCurator.save(cType);
        consumer1 = this.consumerCurator
            .saveOrUpdate(new Consumer("consumer_1", "consumer_1", owner1, cType));
        consumer2 = this.consumerCurator
            .saveOrUpdate(new Consumer("consumer_2", "consumer_2", owner2, cType));
        this.caCertCurator.save(createCertificate(consumer1, CERT_1, Util.yesterday()));
        this.caCertCurator.save(createCertificate(consumer2, CERT_2));
    }

    @Test
    public void getByConsumer() {
        ContentAccessCertificate contentAccess = caCertCurator
            .getForConsumer(consumer1);

        assertEquals(CERT_1, contentAccess.getCert());
    }

    @Test
    public void removeByOwner() {
        caCertCurator.deleteForOwner(owner1);

        assertNull(caCertCurator.getForConsumer(consumer1));
        assertNotNull(caCertCurator.getForConsumer(consumer2));
    }

    @Test
    public void shouldListExpiredCerts() {
        List<ExpiredCertificate> expiredCertificates = this.caCertCurator.listAllExpired();

        assertEquals(1, expiredCertificates.size());
        assertEquals(consumer1.getContentAccessCert().getId(), expiredCertificates.get(0).getCertId());
    }

    @Test
    public void noExpiredCerts() {
        List<ExpiredCertificate> expiredCertificates = this.caCertCurator.listAllExpired();

        assertEquals(1, expiredCertificates.size());
        assertEquals(consumer1.getContentAccessCert().getId(), expiredCertificates.get(0).getCertId());
    }


    @Test
    public void deleteById() {
        Set<String> certIds = Set.of(
            consumer1.getContentAccessCert().getId()
        );
        consumer1.setContentAccessCert(null);
        this.consumerCurator.update(consumer1);

        int deletedCerts = this.caCertCurator.deleteByIds(certIds);

        assertEquals(1, deletedCerts);
    }

    @Test
    public void nothingToDelete() {
        assertEquals(0, this.caCertCurator.deleteByIds(null));
        assertEquals(0, this.caCertCurator.deleteByIds(List.of()));
        assertEquals(0, this.caCertCurator.deleteByIds(List.of("UnknownId")));
    }

    private ContentAccessCertificate createCertificate(Consumer consumer, String cert) {
        return createCertificate(consumer, cert, TestUtil.createDateOffset(2, 0, 0));
    }

    private ContentAccessCertificate createCertificate(Consumer consumer, String cert, Date expiration) {
        ContentAccessCertificate certificate = new ContentAccessCertificate();
        certificate.setKey("crt_key");
        CertificateSerial serial = new CertificateSerial(expiration);
        this.certSerialCurator.save(serial);
        certificate.setSerial(serial);
        certificate.setConsumer(consumer);
        certificate.setCert(cert);
        certificate.setContent("content_1");
        consumer.setContentAccessCert(certificate);
        return certificate;
    }

}
