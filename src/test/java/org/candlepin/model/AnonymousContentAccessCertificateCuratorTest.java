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
package org.candlepin.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;

import java.util.List;

public class AnonymousContentAccessCertificateCuratorTest extends DatabaseTestFixture {

    @Test
    public void testListAllExpiredWithExpiredCert() {
        CertificateSerial expiredSerial = new CertificateSerial();
        expiredSerial.setExpiration(TestUtil.createDateOffset(0, 0, -7));
        certSerialCurator.save(expiredSerial);

        AnonymousContentAccessCertificate expiredCert = new AnonymousContentAccessCertificate();
        expiredCert.setKey("key-1");
        expiredCert.setCert("cert-1");
        expiredCert.setSerial(expiredSerial);

        expiredCert = anonymousContentAccessCertCurator.create(expiredCert);

        CertificateSerial serial = new CertificateSerial();
        serial.setExpiration(TestUtil.createDateOffset(0, 0, 7));
        certSerialCurator.save(serial);

        AnonymousContentAccessCertificate cert = new AnonymousContentAccessCertificate();
        cert.setKey("key-2");
        cert.setCert("cert-2");
        cert.setSerial(serial);

        cert = anonymousContentAccessCertCurator.create(cert);

        List<CertSerial> actual = this.anonymousContentAccessCertCurator
            .listAllExpired();

        assertThat(actual)
            .singleElement()
            .returns(expiredCert.getId(), CertSerial::certId)
            .returns(expiredSerial.getId(), CertSerial::serial);
    }

    @Test
    public void testDeleteByIds() {
        CertificateSerial serial1 = new CertificateSerial();
        serial1.setExpiration(TestUtil.createDateOffset(0, 0, 7));
        certSerialCurator.save(serial1);

        AnonymousContentAccessCertificate cert1 = new AnonymousContentAccessCertificate();
        cert1.setKey("key-1");
        cert1.setCert("cert-1");
        cert1.setSerial(serial1);

        cert1 = anonymousContentAccessCertCurator.create(cert1);

        CertificateSerial serial2 = new CertificateSerial();
        serial2.setExpiration(TestUtil.createDateOffset(0, 0, 7));
        certSerialCurator.save(serial2);

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
        String query = "select c from AnonymousContentAccessCertificate c";
        return getEntityManager()
            .createQuery(query, AnonymousContentAccessCertificate.class)
            .getResultList();
    }

}
