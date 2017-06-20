/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.dto.api.v1;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.DTOFactory;
import org.candlepin.model.Certificate;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.IdentityCertificate;

import static org.junit.Assert.*;

import junitparams.JUnitParamsRunner;

import org.junit.runner.RunWith;



/**
 * Test suite for the CertificateTranslator class
 */
@RunWith(JUnitParamsRunner.class)
public class CertificateTranslatorTest extends
    AbstractTranslatorTest<Certificate, CertificateDTO, CertificateTranslator> {

    protected CertificateTranslator translator = new CertificateTranslator();

    protected CertificateSerialTranslatorTest certificateTranslatorTest =
        new CertificateSerialTranslatorTest();

    @Override
    protected void initFactory(DTOFactory factory) {
        factory.registerTranslator(CertificateSerial.class, new CertificateSerialTranslator());
        factory.registerTranslator(Certificate.class, this.translator);
    }

    @Override
    protected CertificateTranslator initTranslator() {
        return this.translator;
    }

    @Override
    protected Certificate initSourceEntity() {
        IdentityCertificate cert = new IdentityCertificate();

        cert.setId("123");
        cert.setKey("cert_key");
        cert.setCert("cert_cert");
        cert.setSerial(this.certificateTranslatorTest.initSourceEntity());

        return cert;
    }

    @Override
    protected CertificateDTO initDestDTO() {
        // Nothing fancy to do here.
        return new CertificateDTO();
    }

    @Override
    protected void verifyDTO(Certificate source, CertificateDTO dto, boolean childrenGenerated) {
        if (source != null) {
            Certificate src = (Certificate) source;
            CertificateDTO dest = (CertificateDTO) dto;

            assertEquals(src.getId(), dest.getId());
            assertEquals(src.getKey(), dest.getKey());
            assertEquals(src.getCert(), dest.getCert());

            if (childrenGenerated) {
                this.certificateTranslatorTest.verifyDTO(src.getSerial(), dest.getSerial(), true);
            }
            else {
                assertNull(dest.getSerial());
            }
        }
        else {
            assertNull(dto);
        }
    }
}
