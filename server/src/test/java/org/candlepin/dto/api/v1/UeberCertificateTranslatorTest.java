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
package org.candlepin.dto.api.v1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.UeberCertificate;



/**
 * Test suite for the UeberCertificateTranslator class
 */
public class UeberCertificateTranslatorTest extends
    AbstractTranslatorTest<UeberCertificate, UeberCertificateDTO, UeberCertificateTranslator> {

    protected UeberCertificateTranslator translator = new UeberCertificateTranslator();

    protected CertificateSerialTranslatorTest certSerialTranslatorTest =
        new CertificateSerialTranslatorTest();

    protected OwnerTranslatorTest ownerTranslatorTest = new OwnerTranslatorTest();
    protected NestedOwnerTranslatorTest nestedOwnerTranslatorTest = new NestedOwnerTranslatorTest();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.translator,
            UeberCertificate.class, UeberCertificateDTO.class);

        this.certSerialTranslatorTest.initModelTranslator(modelTranslator);
        this.ownerTranslatorTest.initModelTranslator(modelTranslator);
    }

    @Override
    protected UeberCertificateTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected UeberCertificate initSourceObject() {
        UeberCertificate cert = new UeberCertificate();

        cert.setId("123");
        cert.setKey("cert_key");
        cert.setCert("cert_cert");
        cert.setSerial(this.certSerialTranslatorTest.initSourceObject());
        cert.setOwner(this.ownerTranslatorTest.initSourceObject());

        return cert;
    }

    @Override
    protected UeberCertificateDTO initDestinationObject() {
        // Nothing fancy to do here.
        return new UeberCertificateDTO();
    }

    @Override
    protected void verifyOutput(UeberCertificate source, UeberCertificateDTO dest,
        boolean childrenGenerated) {

        if (source != null) {
            assertEquals(source.getId(), dest.getId());
            assertEquals(source.getKey(), dest.getKey());
            assertEquals(source.getCert(), dest.getCert());
            assertEquals(source.getCreated(), dest.getCreated());
            assertEquals(source.getUpdated(), dest.getUpdated());

            if (childrenGenerated) {
                this.certSerialTranslatorTest.verifyOutput(source.getSerial(),
                    dest.getSerial(), true);
                this.nestedOwnerTranslatorTest.verifyOutput(source.getOwner(),
                    dest.getOwner(), true);
            }
            else {
                assertNull(dest.getSerial());
                assertNull(dest.getOwner());
            }
        }
        else {
            assertNull(dest);
        }
    }
}
