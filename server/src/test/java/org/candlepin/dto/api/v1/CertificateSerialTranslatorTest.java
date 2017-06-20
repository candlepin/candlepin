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
import org.candlepin.model.CertificateSerial;

import static org.junit.Assert.*;

import junitparams.JUnitParamsRunner;

import org.junit.runner.RunWith;

import java.util.Date;



/**
 * Test suite for the UpstreamConsumerTranslator class
 */
@RunWith(JUnitParamsRunner.class)
public class CertificateSerialTranslatorTest extends
    AbstractTranslatorTest<CertificateSerial, CertificateSerialDTO, CertificateSerialTranslator> {

    protected CertificateSerialTranslator translator = new CertificateSerialTranslator();

    @Override
    protected void initFactory(DTOFactory factory) {
        factory.registerTranslator(CertificateSerial.class, this.translator);
    }

    @Override
    protected CertificateSerialTranslator initTranslator() {
        return this.translator;
    }

    @Override
    protected CertificateSerial initSourceEntity() {
        CertificateSerial source = new CertificateSerial();

        // ID is automatically generated for this object
        // ID is also used as the serial here
        source.setExpiration(new Date());
        source.setCollected(true);
        source.setRevoked(true);

        return source;
    }

    @Override
    protected CertificateSerialDTO initDestDTO() {
        // Nothing fancy to do here.
        return new CertificateSerialDTO();
    }

    @Override
    protected void verifyDTO(CertificateSerial source, CertificateSerialDTO dto, boolean childrenGenerated) {
        if (source != null) {
            CertificateSerial src = (CertificateSerial) source;
            CertificateSerialDTO dest = (CertificateSerialDTO) dto;

            // This DTO does not have any nested objects, so we don't need to worry about the
            // childrenGenerated flag

            assertEquals(src.getId(), dest.getId());
            assertEquals(src.getSerial(), dest.getSerial());
            assertEquals(src.getExpiration(), dest.getExpiration());
            assertEquals(src.isCollected(), dest.isCollected());
            assertEquals(src.isRevoked(), dest.isRevoked());
        }
        else {
            assertNull(dto);
        }
    }
}
