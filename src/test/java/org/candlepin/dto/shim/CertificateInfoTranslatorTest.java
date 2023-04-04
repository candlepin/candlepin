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
package org.candlepin.dto.shim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.server.v1.CertificateDTO;
import org.candlepin.dto.api.server.v1.CertificateSerialDTO;
import org.candlepin.dto.api.v1.CertificateSerialTranslator;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.service.model.CertificateInfo;


/**
 * Test suite for the CertificateTranslator class
 */
public class CertificateInfoTranslatorTest
    extends AbstractTranslatorTest<CertificateInfo, CertificateDTO, CertificateInfoTranslator> {

    protected CertificateInfoTranslator translator = new CertificateInfoTranslator();

    protected CertificateSerialInfoTranslatorTest serialTranslatorTest =
        new CertificateSerialInfoTranslatorTest();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(new CertificateSerialTranslator(),
            CertificateSerial.class, CertificateSerialDTO.class);

        modelTranslator.registerTranslator(this.translator, CertificateInfo.class, CertificateDTO.class);
    }

    @Override
    protected CertificateInfoTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected IdentityCertificate initSourceObject() {
        IdentityCertificate cert = new IdentityCertificate();

        cert.setId("123");
        cert.setKey("cert_key");
        cert.setCert("cert_cert");
        cert.setSerial(this.serialTranslatorTest.initSourceObject());

        return cert;
    }

    @Override
    protected CertificateDTO initDestinationObject() {
        return new CertificateDTO();
    }

    @Override
    protected void verifyOutput(CertificateInfo source, CertificateDTO dest, boolean childrenGenerated) {
        if (source != null) {
            assertEquals(source.getKey(), dest.getKey());
            assertEquals(source.getCertificate(), dest.getCert());

            if (childrenGenerated) {
                this.serialTranslatorTest.verifyOutput(source.getSerial(), dest.getSerial(), true);
            }
            else {
                assertNull(dest.getSerial());
            }
        }
        else {
            assertNull(dest);
        }
    }
}
