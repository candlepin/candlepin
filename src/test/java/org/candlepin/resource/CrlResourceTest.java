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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.candlepin.model.CertificateSerialCurator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

@ExtendWith(MockitoExtension.class)
public class CrlResourceTest {

    @Mock
    private CertificateSerialCurator certSerialCurator;

    private CrlResource resource;

    @BeforeEach
    public void init() throws Exception {
        this.resource = new CrlResource(this.certSerialCurator);
    }

    @Test
    public void noRevokedSerialsEntries() {
        when(certSerialCurator.listNonExpiredRevokedSerialIds()).thenReturn(List.of());

        List<Long> response = this.resource.getCurrentCrl();

        assertTrue(response.isEmpty());
    }

    @Test
    public void serialsFound() {
        List<Long> serials = List.of(1L, 2L, 3L);
        when(certSerialCurator.listNonExpiredRevokedSerialIds()).thenReturn(serials);

        List<Long> response = this.resource.getCurrentCrl();

        assertEquals(3, response.size());
        assertEquals(serials, response);
    }

}
