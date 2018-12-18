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
import org.candlepin.model.CertificateSerial;

import java.util.Date;


/**
 * Test suite for the CertificateSerialTranslator (manifest import/export) class
 */
public class CertificateSerialTranslatorTest extends
    AbstractTranslatorTest<CertificateSerial, CertificateSerialDTO, CertificateSerialTranslator> {

    protected CertificateSerialTranslator translator = new CertificateSerialTranslator();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(
            this.translator, CertificateSerial.class, CertificateSerialDTO.class);
    }

    @Override
    protected CertificateSerialTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected CertificateSerial initSourceObject() {
        CertificateSerial source = new CertificateSerial();

        // ID is automatically generated for this object
        // ID is also used as the serial here
        source.setExpiration(new Date());
        source.setCollected(true);
        source.setRevoked(true);

        return source;
    }

    @Override
    protected CertificateSerialDTO initDestinationObject() {
        // Nothing fancy to do here.
        return new CertificateSerialDTO();
    }

    @Override
    protected void verifyOutput(CertificateSerial source, CertificateSerialDTO dest,
        boolean childrenGenerated) {

        if (source != null) {
            // This DTO does not have any nested objects, so we don't need to worry about the
            // childrenGenerated flag

            assertEquals(source.getId(), dest.getId());
            assertEquals(source.getSerial(), dest.getSerial());
            assertEquals(source.getExpiration(), dest.getExpiration());
            assertEquals(source.isCollected(), dest.isCollected());
            assertEquals(source.isRevoked(), dest.isRevoked());
        }
        else {
            assertNull(dest);
        }
    }
}
