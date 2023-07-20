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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.candlepin.async.JobExecutionException;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ContentAccessCertificate;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.Owner;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;



class CertificateCleanupJobTest extends DatabaseTestFixture {

    private static final Date VALID = TestUtil.createDateOffset(2, 0, 0);
    private static final Date EXPIRED = Util.yesterday();

    private Owner owner;
    private ConsumerType ct;
    private Consumer consumer1;
    private Consumer consumer2;
    private Consumer consumer3;
    private Consumer consumer4;

    private CertificateCleanupJob job;

    @BeforeEach
    public void setUp() {
        this.owner = this.createOwner("test-owner", "Test Owner");
        this.ct = new ConsumerType(ConsumerType.ConsumerTypeEnum.SYSTEM);
        this.ct = this.consumerTypeCurator.create(ct);

        this.consumer1 = createConsumer(VALID, VALID);
        this.consumer2 = createConsumer(EXPIRED, VALID);
        this.consumer3 = createConsumer(VALID, EXPIRED);
        this.consumer4 = createConsumer(EXPIRED, EXPIRED);

        job = injector.getInstance(CertificateCleanupJob.class);
    }

    @Test
    void shouldCleanExpiredCertificates() throws JobExecutionException {
        job.execute(null);
        this.consumerCurator.flush();
        this.consumerCurator.clear();

        Consumer consumer1 = findConsumer(this.consumer1);
        assertNotNull(consumer1.getIdCert());
        assertNotNull(consumer1.getContentAccessCert());

        Consumer consumer2 = findConsumer(this.consumer2);
        assertNull(consumer2.getIdCert());
        assertNotNull(consumer2.getContentAccessCert());

        Consumer consumer3 = findConsumer(this.consumer3);
        assertNotNull(consumer3.getIdCert());
        assertNull(consumer3.getContentAccessCert());

        Consumer consumer4 = findConsumer(this.consumer4);
        assertNull(consumer4.getIdCert());
        assertNull(consumer4.getContentAccessCert());
    }

    private Consumer findConsumer(Consumer consumer) {
        return this.consumerCurator.getConsumer(consumer.getUuid());
    }

    private Consumer createConsumer(Date idExpiration, Date caExpiration) {
        Consumer consumer = new Consumer()
            .setName("c1")
            .setUsername("u1")
            .setOwner(owner)
            .setType(ct)
            .setIdCert(createIdCert(idExpiration))
            .setContentAccessCert(createContentAccessCert(caExpiration));

        return consumerCurator.create(consumer);
    }

    private IdentityCertificate createIdCert(Date expiration) {
        IdentityCertificate idCert = TestUtil.createIdCert(expiration);
        return saveCert(idCert);
    }

    private ContentAccessCertificate createContentAccessCert(Date expiration) {
        ContentAccessCertificate certificate = new ContentAccessCertificate();
        certificate.setKey("crt_key");
        certificate.setSerial(new CertificateSerial(expiration));
        certificate.setCert("cert_1");
        certificate.setContent("content_1");
        return saveCert(certificate);
    }

    private IdentityCertificate saveCert(IdentityCertificate cert) {
        cert.setId(null);
        certSerialCurator.create(cert.getSerial());
        return identityCertificateCurator.create(cert);
    }

    private ContentAccessCertificate saveCert(ContentAccessCertificate cert) {
        cert.setId(null);
        certSerialCurator.create(cert.getSerial());
        return caCertCurator.create(cert);
    }

}
