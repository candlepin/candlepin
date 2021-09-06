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

import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class IdentityCertificateCuratorTest extends DatabaseTestFixture {

    @Test
    public void shouldListOnlyExpiredIdCerts() {
        String idCert = createIdCert().getId();
        createExpiredIdCert();
        createExpiredIdCert();

        List<ExpiredCertificate> expiredCertificates = this.identityCertificateCurator.listAllExpired();

        assertEquals(2, expiredCertificates.size());
        Set<String> expiredCertIds = expiredCertificates.stream()
            .map(ExpiredCertificate::getCertId)
            .collect(Collectors.toSet());
        assertFalse(expiredCertIds.contains(idCert));
    }

    @Test
    public void noExpiredCertsToList() {
        assertEquals(0, this.identityCertificateCurator.listAllExpired().size());
    }

    @Test
    public void deleteById() {
        Set<String> certIds = new HashSet<>(Arrays.asList(
            createExpiredIdCert().getId(),
            createExpiredIdCert().getId(),
            createIdCert().getId()
        ));

        int deletedCerts = this.identityCertificateCurator.deleteByIds(certIds);

        assertEquals(3, deletedCerts);
    }

    @Test
    public void nothingToDelete() {
        assertEquals(0, this.identityCertificateCurator.deleteByIds(null));
        assertEquals(0, this.identityCertificateCurator.deleteByIds(Arrays.asList()));
        assertEquals(0, this.identityCertificateCurator.deleteByIds(Arrays.asList("UnknownId")));
    }

    private IdentityCertificate createExpiredIdCert() {
        IdentityCertificate idCert = TestUtil.createIdCert(Util.yesterday());
        return saveCert(idCert);
    }

    private IdentityCertificate createIdCert() {
        IdentityCertificate idCert = TestUtil.createIdCert(TestUtil.createFutureDate(2));
        return saveCert(idCert);
    }

    private IdentityCertificate saveCert(IdentityCertificate cert) {
        cert.setId(null);
        certSerialCurator.create(cert.getSerial());
        return identityCertificateCurator.create(cert);
    }

}
