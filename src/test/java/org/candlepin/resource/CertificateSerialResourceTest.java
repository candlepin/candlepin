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
package org.candlepin.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.SimpleModelTranslator;
import org.candlepin.dto.api.server.v1.CertificateSerialDTO;
import org.candlepin.dto.api.v1.CertificateSerialTranslator;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;



public class CertificateSerialResourceTest {

    private ModelTranslator modelTranslator;

    @BeforeEach
    public void init() {
        this.modelTranslator = new SimpleModelTranslator();
        this.modelTranslator.registerTranslator(
            new CertificateSerialTranslator(), CertificateSerial.class, CertificateSerialDTO.class);
    }

    @Test
    public void getSerial() {
        CertificateSerialCurator csc = mock(CertificateSerialCurator.class);
        CertificateSerialResource csr = new CertificateSerialResource(csc, this.modelTranslator);
        CertificateSerial cs = new CertificateSerial(10L);
        when(csc.get(cs.getId())).thenReturn(cs);

        CertificateSerialDTO output = csr.getCertificateSerial(10L);
        assertNotNull(output);

        CertificateSerialDTO dto = this.modelTranslator.translate(cs, CertificateSerialDTO.class);
        assertEquals(dto, output);
    }
}
