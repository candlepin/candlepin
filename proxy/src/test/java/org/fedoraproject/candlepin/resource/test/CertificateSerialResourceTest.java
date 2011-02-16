/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.resource.test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.fedoraproject.candlepin.model.CertificateSerial;
import org.fedoraproject.candlepin.model.CertificateSerialCurator;
import org.fedoraproject.candlepin.resource.CertificateSerialResource;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;


/**
 * CertificateSerialTest
 */
public class CertificateSerialResourceTest {

    @Test
    public void listall() {
        CertificateSerialCurator csc = mock(CertificateSerialCurator.class);
        CertificateSerialResource csr = new CertificateSerialResource(csc);
        List<CertificateSerial> serials = new ArrayList<CertificateSerial>();
        serials.add(mock(CertificateSerial.class));
        when(csc.listAll()).thenReturn(serials);
        assertEquals(serials, csr.getCertificateSerials());
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
