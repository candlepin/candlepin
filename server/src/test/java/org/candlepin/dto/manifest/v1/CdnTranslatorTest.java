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
package org.candlepin.dto.manifest.v1;

import static org.junit.Assert.*;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.Cdn;
import org.candlepin.model.CdnCertificate;
import org.candlepin.model.CertificateSerial;

import java.util.Date;

/**
 * Test suite for the CdnTranslator (manifest import/export) class
 */
public class CdnTranslatorTest extends AbstractTranslatorTest<Cdn, CdnDTO, CdnTranslator> {

    protected CdnTranslator translator = new CdnTranslator();

    private CertificateTranslatorTest certificateTranslatorTest = new CertificateTranslatorTest();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        this.certificateTranslatorTest.initModelTranslator(modelTranslator);
        modelTranslator.registerTranslator(this.translator, Cdn.class, CdnDTO.class);
    }

    @Override
    protected CdnTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected Cdn initSourceObject() {
        Cdn source = new Cdn();
        source.setName("cdn-name");
        source.setId("cdn-id");
        source.setUrl("cdn-url");
        source.setLabel("cdn-label");

        CdnCertificate cdnCert = new CdnCertificate();
        cdnCert.setId("cdn-cert-id");
        cdnCert.setKey("cdn-cert-key");
        cdnCert.setCert("cdn-cert-cert");
        CertificateSerial serial = new CertificateSerial();
        serial.setRevoked(false);
        serial.setCollected(false);
        serial.setExpiration(new Date());
        serial.setSerial(5L);
        serial.setId(1L);
        cdnCert.setSerial(serial);
        source.setCertificate(cdnCert);

        return source;
    }

    @Override
    protected CdnDTO initDestinationObject() {
        return new CdnDTO();
    }

    @Override
    protected void verifyOutput(Cdn source, CdnDTO dest, boolean childrenGenerated) {
        if (source != null) {
            assertEquals(source.getId(), dest.getId());
            assertEquals(source.getLabel(), dest.getLabel());
            assertEquals(source.getName(), dest.getName());
            assertEquals(source.getUrl(), dest.getUrl());

            if (childrenGenerated) {
                this.certificateTranslatorTest.verifyOutput(
                    source.getCertificate(), dest.getCertificate(), true);
            }
            else {
                assertNull(dest.getCertificate());
            }
        }
        else {
            assertNull(dest);
        }
    }
}
