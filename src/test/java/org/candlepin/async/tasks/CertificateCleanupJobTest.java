/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.async.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.candlepin.async.JobExecutionException;
import org.candlepin.model.AnonymousCloudConsumer;
import org.candlepin.model.AnonymousContentAccessCertificate;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.Owner;
import org.candlepin.model.SCACertificate;
import org.candlepin.model.SubscriptionsCertificate;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Set;


class CertificateCleanupJobTest extends DatabaseTestFixture {

    private static final Date VALID = TestUtil.createDateOffset(2, 0, 0);
    private static final Date EXPIRED = TestUtil.createDateOffset(0, 0, -10);

    private Owner owner;

    private CertificateCleanupJob job;

    @BeforeEach
    public void setUp() {
        this.owner = this.createOwner("test-owner", "Test Owner");
        job = injector.getInstance(CertificateCleanupJob.class);
    }

    @Test
    void shouldCleanExpiredIdentityAndSimpleContentAccessCertificates() throws JobExecutionException {
        ConsumerType consumerType = new ConsumerType(ConsumerType.ConsumerTypeEnum.SYSTEM);
        consumerType = this.consumerTypeCurator.create(consumerType);
        Consumer consumer1 = createConsumer(consumerType, VALID, VALID);
        Consumer consumer2 = createConsumer(consumerType, EXPIRED, VALID);
        Consumer consumer3 = createConsumer(consumerType, VALID, EXPIRED);
        Consumer consumer4 = createConsumer(consumerType, EXPIRED, EXPIRED);

        job.execute(null);
        this.consumerCurator.flush();
        this.consumerCurator.clear();

        consumer1 = findConsumer(consumer1);
        assertNotNull(consumer1.getIdCert());
        assertNotNull(consumer1.getContentAccessCert());

        consumer2 = findConsumer(consumer2);
        assertNull(consumer2.getIdCert());
        assertNotNull(consumer2.getContentAccessCert());

        consumer3 = findConsumer(consumer3);
        assertNotNull(consumer3.getIdCert());
        assertNull(consumer3.getContentAccessCert());

        consumer4 = findConsumer(consumer4);
        assertNull(consumer4.getIdCert());
        assertNull(consumer4.getContentAccessCert());
    }

    @Test
    public void shouldCleanExpiredAnonymousCertificatesButNotDeleteAnonConsumer()
        throws JobExecutionException {
        CertificateSerial expiredSerial = new CertificateSerial();
        expiredSerial.setExpiration(TestUtil.createDateOffset(0, 0, -7));
        certSerialCurator.create(expiredSerial);

        AnonymousContentAccessCertificate expiredCert = new AnonymousContentAccessCertificate();
        expiredCert.setKey("key-1");
        expiredCert.setCert("cert-1");
        expiredCert.setSerial(expiredSerial);

        expiredCert = anonymousContentAccessCertCurator.create(expiredCert);

        AnonymousCloudConsumer consumerWithExpiredCert = new AnonymousCloudConsumer();
        consumerWithExpiredCert.setContentAccessCert(expiredCert)
            .setCloudAccountId("cloud-account-1")
            .setCloudInstanceId("cloud-instance-1")
            .setProductIds(Set.of("SKU00001"))
            .setCloudProviderShortName("GCP")
            .setCloudOfferingId("RH-offering-1");
        this.anonymousCloudConsumerCurator.create(consumerWithExpiredCert);

        CertificateSerial serial = new CertificateSerial();
        serial.setExpiration(TestUtil.createDateOffset(0, 0, 7));
        certSerialCurator.create(serial);

        AnonymousContentAccessCertificate cert = new AnonymousContentAccessCertificate();
        cert.setKey("key-2");
        cert.setCert("cert-2");
        cert.setSerial(serial);

        cert = anonymousContentAccessCertCurator.create(cert);

        AnonymousCloudConsumer consumerWithValidCert = new AnonymousCloudConsumer();
        consumerWithValidCert.setContentAccessCert(cert)
            .setCloudAccountId("cloud-account-2")
            .setCloudInstanceId("cloud-instance-2")
            .setProductIds(Set.of("SKU00002"))
            .setCloudProviderShortName("GCP")
            .setCloudOfferingId("RH-offering-2");
        this.anonymousCloudConsumerCurator.create(consumerWithValidCert);
        assertThat(this.anonymousCloudConsumerCurator.getByUuid(consumerWithExpiredCert.getUuid()))
            .isNotNull();

        job.execute(null);
        certSerialCurator.flush();
        certSerialCurator.clear();

        assertThat(getAnonymousCertsFromDB())
            .singleElement()
            .isEqualTo(cert);

        assertThat(certSerialCurator.get(expiredSerial.getId()))
            .isNull();

        assertThat(certSerialCurator.get(serial.getId()))
            .isNotNull()
            .returns(serial.getId(), CertificateSerial::getId);

        // When the CertificateCleanupJob deletes anonymous certificates, we should NOT be deleting the
        // anonymous cloud consumer itself!
        assertThat(this.anonymousCloudConsumerCurator.getByUuid(consumerWithExpiredCert.getUuid()))
            .isNotNull();
    }

    @Test
    void shouldNotFailWithForeignKeyViolationWhenCleaningUpExpiredAndRevokedSerials()
        throws JobExecutionException {

        SubscriptionsCertificate subsCert = new SubscriptionsCertificate()
                .setCreated(new Date())
                .setUpdated(new Date());
        subsCert.setKey("key_abc");
        subsCert.setCert("cert_abcd");
        CertificateSerial expiredSerial = new CertificateSerial();
        expiredSerial.setExpiration(TestUtil.createDateOffset(0, 0, -7));
        subsCert.setSerial(expiredSerial);

        // This should NEVER happen under normal circumstances. Subscription Certificates are not revocable
        // through any normal code path, and this could only happen through a db schema upgrade accident.
        expiredSerial.setRevoked(Boolean.TRUE);

        this.subscriptionsCertificateCurator.create(subsCert);

        // This should not fail with a FK violation, even though we have an expired+revoked serial
        // that is still referenced with a foreign key in another table (cp_certificate
        job.execute(null);
    }

    private Consumer findConsumer(Consumer consumer) {
        return this.consumerCurator.getConsumer(consumer.getUuid());
    }

    private Consumer createConsumer(ConsumerType consumerType, Date idExpiration, Date caExpiration) {
        Consumer consumer = new Consumer()
            .setName("c1")
            .setUsername("u1")
            .setOwner(owner)
            .setType(consumerType)
            .setIdCert(createIdCert(idExpiration))
            .setContentAccessCert(createContentAccessCert(caExpiration));

        return consumerCurator.create(consumer);
    }

    private IdentityCertificate createIdCert(Date expiration) {
        IdentityCertificate idCert = TestUtil.createIdCert(expiration);
        return saveCert(idCert);
    }

    private SCACertificate createContentAccessCert(Date expiration) {
        SCACertificate certificate = new SCACertificate();
        certificate.setKey("crt_key");
        certificate.setSerial(new CertificateSerial(expiration));
        certificate.setCert("cert_1");
        return saveCert(certificate);
    }

    private IdentityCertificate saveCert(IdentityCertificate cert) {
        cert.setId(null);
        certSerialCurator.create(cert.getSerial());
        return identityCertificateCurator.create(cert);
    }

    private SCACertificate saveCert(SCACertificate cert) {
        cert.setId(null);
        certSerialCurator.create(cert.getSerial());
        return caCertCurator.create(cert);
    }

    private List<AnonymousContentAccessCertificate> getAnonymousCertsFromDB() {
        String query = "select c from AnonymousContentAccessCertificate c";
        return getEntityManager()
            .createQuery(query, AnonymousContentAccessCertificate.class)
            .getResultList();
    }

}
