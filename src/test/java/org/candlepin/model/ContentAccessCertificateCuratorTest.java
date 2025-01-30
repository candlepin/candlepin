/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.List;

public class ContentAccessCertificateCuratorTest extends DatabaseTestFixture {

    @Test
    public void testGetForConsumerWithInvalidConsumer() {
        assertNull(caCertCurator.getForConsumer(null));

        Consumer consumer = new Consumer();
        // Null consumer ID
        assertNull(caCertCurator.getForConsumer(consumer));

        consumer.setId("");
        assertNull(caCertCurator.getForConsumer(consumer));

        consumer.setId("  ");
        assertNull(caCertCurator.getForConsumer(consumer));
    }

    @Test
    public void testGetForConsumerWithHNoExistingCertificate() {
        Owner owner = createOwner();
        Consumer consumer = createConsumer(owner);

        SCACertificate actual = caCertCurator.getForConsumer(consumer);

        assertNull(actual);
    }

    @Test
    public void testGetForConsumerWithExistingCertificate() {
        Owner owner1 = createOwner();
        Owner owner2 = createOwner();

        Consumer consumer1 = createConsumer(owner1);
        Consumer consumer2 = createConsumer(owner2);

        CertificateSerial serial1 = new CertificateSerial();
        serial1.setExpiration(TestUtil.createDateOffset(0, 0, 7));
        certSerialCurator.save(serial1);

        CertificateSerial serial2 = new CertificateSerial();
        serial2.setExpiration(TestUtil.createDateOffset(0, 0, 7));
        certSerialCurator.save(serial2);

        SCACertificate cert1 = new SCACertificate();
        cert1.setConsumer(consumer1);
        cert1.setKey(TestUtil.randomString());
        cert1.setCert(TestUtil.randomString());
        cert1.setSerial(serial1);

        SCACertificate cert2 = new SCACertificate();
        cert2.setConsumer(consumer2);
        cert2.setKey(TestUtil.randomString());
        cert2.setCert(TestUtil.randomString());
        cert2.setSerial(serial2);

        cert1 = caCertCurator.create(cert1);
        cert2 = caCertCurator.create(cert2);

        consumer1.setContentAccessCert(cert1);
        consumer2.setContentAccessCert(cert2);

        SCACertificate actual = caCertCurator.getForConsumer(consumer1);

        assertThat(actual)
            .isNotNull()
            .isEqualTo(cert1);
    }

    @Test
    public void testDeleteForOwnerWithInvalidOwner() {
        int actual = caCertCurator.deleteForOwner(null);
        assertEquals(0, actual);

        Owner owner = new Owner();

        // Null owner key
        actual = caCertCurator.deleteForOwner(owner);
        assertEquals(0, actual);

        owner.setKey("  ");
        actual = caCertCurator.deleteForOwner(owner);
        assertEquals(0, actual);
    }

    @Test
    public void testDeleteForOwner() {
        Owner owner1 = createOwner();
        Owner owner2 = createOwner();

        Consumer consumer1 = createConsumer(owner1);
        Consumer consumer2 = createConsumer(owner2);

        CertificateSerial serial1 = new CertificateSerial();
        serial1.setExpiration(TestUtil.createDateOffset(0, 0, 7));
        certSerialCurator.save(serial1);

        CertificateSerial serial2 = new CertificateSerial();
        serial2.setExpiration(TestUtil.createDateOffset(0, 0, 7));
        certSerialCurator.save(serial2);

        SCACertificate cert1 = new SCACertificate();
        cert1.setConsumer(consumer1);
        cert1.setKey(TestUtil.randomString());
        cert1.setCert(TestUtil.randomString());
        cert1.setSerial(serial1);

        SCACertificate cert2 = new SCACertificate();
        cert2.setConsumer(consumer2);
        cert2.setKey(TestUtil.randomString());
        cert2.setCert(TestUtil.randomString());
        cert2.setSerial(serial2);

        cert1 = caCertCurator.create(cert1);
        cert2 = caCertCurator.create(cert2);

        consumer1.setContentAccessCert(cert1);
        consumer2.setContentAccessCert(cert2);

        int actual = caCertCurator.deleteForOwner(owner1);

        assertEquals(1, actual);
        assertThat(getSCACertificatesFromDB())
            .isNotNull()
            .hasSize(1)
            .containsExactly(cert2);
    }

    @Test
    public void testListAllExpired() {
        CertificateSerial serial = new CertificateSerial();
        serial.setExpiration(TestUtil.createDateOffset(0, 0, 7));
        certSerialCurator.save(serial);

        CertificateSerial expiredSerial = new CertificateSerial();
        expiredSerial.setExpiration(TestUtil.createDateOffset(0, 0, -7));
        certSerialCurator.save(expiredSerial);

        SCACertificate cert = new SCACertificate();
        cert.setKey(TestUtil.randomString());
        cert.setCert(TestUtil.randomString());
        cert.setSerial(serial);

        SCACertificate expiredCert = new SCACertificate();
        expiredCert.setKey(TestUtil.randomString());
        expiredCert.setCert(TestUtil.randomString());
        expiredCert.setSerial(expiredSerial);

        caCertCurator.create(cert);
        caCertCurator.create(expiredCert);

        List<CertSerial> actual = caCertCurator.listAllExpired();

        assertThat(actual)
            .isNotNull()
            .hasSize(1)
            .first()
            .returns(expiredSerial.getSerial().longValue(), CertSerial::serial);
    }

    @Test
    public void testDeleteByIdsWithNullOrEmptyIds() {
        int actual = caCertCurator.deleteByIds(null);
        assertEquals(0, actual);

        actual = caCertCurator.deleteByIds(List.of());
        assertEquals(0, actual);
    }

    @Test
    public void testDeleteByIds() {
        CertificateSerial serial1 = new CertificateSerial();
        serial1.setExpiration(TestUtil.createDateOffset(0, 0, 7));
        certSerialCurator.save(serial1);

        CertificateSerial serial2 = new CertificateSerial();
        serial2.setExpiration(TestUtil.createDateOffset(0, 0, 7));
        certSerialCurator.save(serial2);

        SCACertificate cert1 = new SCACertificate();
        cert1.setKey(TestUtil.randomString());
        cert1.setCert(TestUtil.randomString());
        cert1.setSerial(serial1);

        SCACertificate cert2 = new SCACertificate();
        cert2.setKey(TestUtil.randomString());
        cert2.setCert(TestUtil.randomString());
        cert2.setSerial(serial2);

        cert1 = caCertCurator.create(cert1);
        cert2 = caCertCurator.create(cert2);

        int deleted = caCertCurator.deleteByIds(List.of(cert1.getId()));

        assertEquals(1, deleted);

        assertThat(getSCACertificatesFromDB())
            .isNotNull()
            .singleElement()
            .isEqualTo(cert2);
    }

    @Test
    public void testListCertSerialsWithNullOrEmptyConsumerIds() {
        List<CertSerial> actual = caCertCurator.listCertSerials(null);
        assertThat(actual).isEmpty();

        actual = caCertCurator.listCertSerials(List.of());
        assertThat(actual).isEmpty();
    }

    @Test
    public void testListCertSerials() {
        Owner owner1 = createOwner();
        Owner owner2 = createOwner();
        Owner owner3 = createOwner();

        Consumer consumer1 = createConsumer(owner1);
        Consumer consumer2 = createConsumer(owner2);
        Consumer consumer3 = createConsumer(owner3);

        CertificateSerial serial1 = new CertificateSerial();
        serial1.setExpiration(TestUtil.createDateOffset(0, 0, 7));
        certSerialCurator.save(serial1);

        CertificateSerial serial2 = new CertificateSerial();
        serial2.setExpiration(TestUtil.createDateOffset(0, 0, 7));
        certSerialCurator.save(serial2);

        CertificateSerial serial3 = new CertificateSerial();
        serial3.setExpiration(TestUtil.createDateOffset(0, 0, 7));
        certSerialCurator.save(serial3);

        SCACertificate cert1 = new SCACertificate();
        cert1.setConsumer(consumer1);
        cert1.setKey(TestUtil.randomString());
        cert1.setCert(TestUtil.randomString());
        cert1.setSerial(serial1);

        SCACertificate cert2 = new SCACertificate();
        cert2.setConsumer(consumer2);
        cert2.setKey(TestUtil.randomString());
        cert2.setCert(TestUtil.randomString());
        cert2.setSerial(serial2);

        SCACertificate cert3 = new SCACertificate();
        cert3.setConsumer(consumer3);
        cert3.setKey(TestUtil.randomString());
        cert3.setCert(TestUtil.randomString());
        cert3.setSerial(serial3);

        cert1 = caCertCurator.create(cert1);
        cert2 = caCertCurator.create(cert2);
        cert3 = caCertCurator.create(cert3);

        consumer1.setContentAccessCert(cert1);
        consumer2.setContentAccessCert(cert2);
        consumer3.setContentAccessCert(cert3);

        List<CertSerial> actual = caCertCurator
            .listCertSerials(List.of(consumer1.getId(), consumer2.getId()));

        assertThat(actual)
            .isNotNull()
            .hasSize(2)
            .extractingResultOf("serial")
            .containsExactlyInAnyOrder(cert1.getSerial().getSerial().longValue(),
                cert2.getSerial().getSerial().longValue());
    }

    @ParameterizedTest(name = "{displayName} {index}: {0} {1}")
    @NullAndEmptySource
    public void testDeleteForConsumersWithNullOrEmptyConsumerUuids(List<String> consumerUuids) {
        int actual = caCertCurator.deleteForConsumers(consumerUuids);

        assertEquals(0, actual);
    }

    @Test
    public void testDeleteForConsumers() {
        Owner owner = createOwner();

        Consumer consumer1 = createConsumer(owner);
        Consumer consumer2 = createConsumer(owner);

        CertificateSerial serial1 = new CertificateSerial();
        serial1.setExpiration(TestUtil.createDateOffset(0, 0, 7));
        certSerialCurator.save(serial1);

        CertificateSerial serial2 = new CertificateSerial();
        serial2.setExpiration(TestUtil.createDateOffset(0, 0, 7));
        certSerialCurator.save(serial2);

        SCACertificate cert1 = new SCACertificate();
        cert1.setConsumer(consumer1);
        cert1.setKey(TestUtil.randomString());
        cert1.setCert(TestUtil.randomString());
        cert1.setSerial(serial1);

        SCACertificate cert2 = new SCACertificate();
        cert2.setConsumer(consumer2);
        cert2.setKey(TestUtil.randomString());
        cert2.setCert(TestUtil.randomString());
        cert2.setSerial(serial2);

        cert1 = caCertCurator.create(cert1);
        cert2 = caCertCurator.create(cert2);

        consumer1.setContentAccessCert(cert1);
        consumer2.setContentAccessCert(cert2);

        int actual = caCertCurator.deleteForConsumers(List.of(consumer1.getUuid(), consumer2.getUuid()));

        assertEquals(2, actual);
        assertThat(getSCACertificatesFromDB())
            .isEmpty();

        caCertCurator.flush();
        caCertCurator.clear();

        List<CertificateSerial> serials = getCertificateSerialsFromDB();
        assertThat(serials)
            .isNotNull()
            .hasSize(2)
            .extracting(CertificateSerial::isRevoked)
            .containsOnly(true);
    }

    private List<SCACertificate> getSCACertificatesFromDB() {
        String query = "SELECT c FROM SCACertificate c";

        return getEntityManager()
            .createQuery(query, SCACertificate.class)
            .getResultList();
    }

    private List<CertificateSerial> getCertificateSerialsFromDB() {
        String query = "SELECT c FROM CertificateSerial c";

        return getEntityManager()
            .createQuery(query, CertificateSerial.class)
            .getResultList();
    }
}
