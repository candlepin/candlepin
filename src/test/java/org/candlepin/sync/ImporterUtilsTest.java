/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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
package org.candlepin.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.candlepin.dto.manifest.v1.CertificateDTO;
import org.candlepin.dto.manifest.v1.CertificateSerialDTO;
import org.candlepin.model.CdnCertificate;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.RevocableCertificate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

public class ImporterUtilsTest {

    private CertificateDTO certDTO;

    @BeforeEach
    public void setUp() {
        certDTO = new CertificateDTO();
        certDTO.setCertificate("test-cert");
        certDTO.setKey("test-key");
        certDTO.setUpdated(new Date());
        certDTO.setCreated(new Date());
    }

    @Test
    public void populateFullyPopulatedCertificate()  {
        RevocableCertificate certEntity = new CdnCertificate();

        CertificateSerialDTO serialDTO = new CertificateSerialDTO();
        serialDTO.setUpdated(new Date());
        serialDTO.setCreated(new Date());
        serialDTO.setRevoked(false);
        serialDTO.setExpiration(new Date());
        certDTO.setSerial(serialDTO);

        ImporterUtils.populateEntity(certEntity, certDTO);

        assertEquals(certDTO.getCertificate(), certEntity.getCert());
        assertEquals(certDTO.getKey(), certEntity.getKey());
        assertEquals(certDTO.getUpdated(), certEntity.getUpdated());
        assertEquals(certDTO.getCreated(), certEntity.getCreated());

        CertificateSerial certSerialEntity = certEntity.getSerial();
        assertNotNull(certSerialEntity);
        // No need to assert on CertificateSerial's id or serial fields since they aren't populated.
        assertEquals(serialDTO.getExpiration(), certSerialEntity.getExpiration());
        assertEquals(serialDTO.isRevoked(), certSerialEntity.isRevoked());
        assertEquals(serialDTO.getCreated(), certSerialEntity.getCreated());
        assertEquals(serialDTO.getUpdated(), certSerialEntity.getUpdated());
    }
}
