package org.candlepin.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.junit.jupiter.api.Test;

public class AnonymousContentAccessCertificateCuratorTest extends DatabaseTestFixture {

    @Test
    public void testListAllExpiredWithExpiredCert() {
        CertificateSerial expiredSerial = new CertificateSerial();
        expiredSerial.setExpiration(TestUtil.createDateOffset(0, 0, -7));
        this.certSerialCurator.save(expiredSerial);

        AnonymousContentAccessCertificate expiredCert = new AnonymousContentAccessCertificate();
        expiredCert.setKey("key-1");
        expiredCert.setCert("cert-1");
        expiredCert.setSerial(expiredSerial);

        expiredCert = anonymousContentAccessCertCurator.create(expiredCert);

        CertificateSerial serial = new CertificateSerial();
        serial.setExpiration(TestUtil.createDateOffset(0, 0, 7));
        this.certSerialCurator.save(serial);

        AnonymousContentAccessCertificate cert = new AnonymousContentAccessCertificate();
        cert.setKey("key-2");
        cert.setCert("cert-2");
        cert.setSerial(serial);

        cert = anonymousContentAccessCertCurator.create(cert);

        List<ExpiredCertificate> actual = this.anonymousContentAccessCertCurator
            .listAllExpired();

        assertThat(actual)
            .singleElement()
            .returns(expiredCert.getId(), ExpiredCertificate::getCertId)
            .returns(expiredSerial.getId(), ExpiredCertificate::getSerial);
    }

    @Test
    public void testDeleteByIds() {
        CertificateSerial serial1 = new CertificateSerial();
        serial1.setExpiration(TestUtil.createDateOffset(0, 0, 7));
        this.certSerialCurator.save(serial1);

        AnonymousContentAccessCertificate cert1 = new AnonymousContentAccessCertificate();
        cert1.setKey("key-1");
        cert1.setCert("cert-1");
        cert1.setSerial(serial1);

        cert1 = anonymousContentAccessCertCurator.create(cert1);

        CertificateSerial serial2 = new CertificateSerial();
        serial2.setExpiration(TestUtil.createDateOffset(0, 0, 7));
        this.certSerialCurator.save(serial2);

        AnonymousContentAccessCertificate cert2 = new AnonymousContentAccessCertificate();
        cert2.setKey("key-2");
        cert2.setCert("cert-2");
        cert2.setSerial(serial2);

        cert2 = anonymousContentAccessCertCurator.create(cert2);

        int actual = anonymousContentAccessCertCurator.deleteByIds(List.of(cert1.getId()));

        assertEquals(1, actual);

        assertThat(getAnonymousCertsFromDB())
            .singleElement()
            .isEqualTo(cert2);
    }

    private List<AnonymousContentAccessCertificate> getAnonymousCertsFromDB() {
        return this.getEntityManager()
            .createQuery("select c from AnonymousContentAccessCertificate c", AnonymousContentAccessCertificate.class)
            .getResultList();
    }

}
