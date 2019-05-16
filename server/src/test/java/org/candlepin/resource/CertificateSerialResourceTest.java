/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.candlepin.TestingModules;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.CertificateSerialDTO;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;
import org.candlepin.model.EmptyCandlepinQuery;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

import org.junit.Before;
import org.junit.Test;



/**
 * CertificateSerialTest
 */
public class CertificateSerialResourceTest {

    @Inject protected ModelTranslator modelTranslator;

    @Before
    public void init() {
        Injector injector = Guice.createInjector(
            new TestingModules.MockJpaModule(),
            new TestingModules.ServletEnvironmentModule(),
            new TestingModules.StandardTest()
        );

        injector.injectMembers(this);
    }

    @Test
    public void listall() {
        CandlepinQuery cqmock = new EmptyCandlepinQuery();

        CertificateSerialCurator csc = mock(CertificateSerialCurator.class);
        when(csc.listAll()).thenReturn(cqmock);

        CertificateSerialResource csr = new CertificateSerialResource(csc, this.modelTranslator);

        CandlepinQuery<CertificateSerialDTO> result = csr.getCertificateSerials();
        assertNotNull(result);

        verify(csc, times(1)).listAll();
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
