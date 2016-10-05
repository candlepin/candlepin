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
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.candlepin.TestingModules;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialCurator;

import com.google.inject.Guice;
import com.google.inject.Injector;

import org.junit.Before;
import org.junit.Test;



/**
 * CertificateSerialTest
 */
public class CertificateSerialResourceTest {

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
        CandlepinQuery cqmock = mock(CandlepinQuery.class);
        CertificateSerialCurator csc = mock(CertificateSerialCurator.class);
        CertificateSerialResource csr = new CertificateSerialResource(csc);

        when(csc.listAll()).thenReturn(cqmock);

        CandlepinQuery<CertificateSerial> result = csr.getCertificateSerials();
        assertSame(cqmock, result);

        verify(csc, times(1)).listAll();
    }

    @Test
    public void getSerial() {
        CertificateSerialCurator csc = mock(CertificateSerialCurator.class);
        CertificateSerialResource csr = new CertificateSerialResource(csc);
        CertificateSerial cs = mock(CertificateSerial.class);
        when(csc.find(10L)).thenReturn(cs);
        assertEquals(cs, csr.getCertificateSerial(10L));
    }
}
