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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.Certificate;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.IdentityCertificate;



/**
 * Test suite for the CertificateTranslator class
 */
public class CertificateTranslatorTest
    extends AbstractTranslatorTest<Certificate, CertificateDTO, CertificateTranslator> {

    protected CertificateTranslator translator = new CertificateTranslator();

    protected CertificateSerialTranslatorTest certificateTranslatorTest =
        new CertificateSerialTranslatorTest();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(new CertificateSerialTranslator(),
            CertificateSerial.class, CertificateSerialDTO.class);

        modelTranslator.registerTranslator(this.translator, Certificate.class, CertificateDTO.class);
    }

    @Override
    protected CertificateTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected IdentityCertificate initSourceObject() {
        IdentityCertificate cert = new IdentityCertificate();

        cert.setId("123");
        cert.setKey("cert_key");
        cert.setCert("cert_cert");
        cert.setSerial(this.certificateTranslatorTest.initSourceObject());

        return cert;
    }

    @Override
    protected CertificateDTO initDestinationObject() {
        // Nothing fancy to do here.
        return new CertificateDTO();
    }

    @Override
    protected void verifyOutput(Certificate source, CertificateDTO dest, boolean childrenGenerated) {
        if (source != null) {
            assertEquals(source.getId(), dest.getId());
            assertEquals(source.getKey(), dest.getKey());
            assertEquals(source.getCert(), dest.getCert());

            if (childrenGenerated) {
                this.certificateTranslatorTest.verifyOutput(source.getSerial(), dest.getSerial(), true);
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
