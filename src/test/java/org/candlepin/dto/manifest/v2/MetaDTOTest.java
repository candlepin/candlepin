/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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

package org.candlepin.dto.manifest.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import java.util.Date;

public class MetaDTOTest {

    @Test
    public void testBuilderWithAllFields() {
        Date created = new Date();
        MetaDTO dto = MetaDTO.builder()
            .setManifestVersion("2.0")
            .setVersion("1.0.0")
            .setCreated(created)
            .setPrincipalName("admin")
            .setWebAppPrefix("candlepin")
            .setCdnLabel("cdn-label")
            .build();

        assertEquals("2.0", dto.manifestVersion());
        assertEquals("1.0.0", dto.version());
        assertEquals(created, dto.created());
        assertEquals("admin", dto.principalName());
        assertEquals("candlepin", dto.webAppPrefix());
        assertEquals("cdn-label", dto.cdnLabel());
    }

    @Test
    public void testBuilderWithNullFields() {
        MetaDTO dto = new MetaDTO.Builder().build();

        assertNull(dto.manifestVersion());
        assertNull(dto.version());
        assertNull(dto.created());
        assertNull(dto.principalName());
        assertNull(dto.webAppPrefix());
        assertNull(dto.cdnLabel());
    }
}
