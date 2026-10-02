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

public class ContentDTOTest {

    @Test
    public void testBuilderWithAllFields() {
        ContentDTO dto = ContentDTO.builder()
            .setId("content-id")
            .setType("content-type")
            .setLabel("content-label")
            .setName("content-name")
            .setVendor("content-vendor")
            .setContentUrl("https://content.example.com")
            .setRequiredTags("tag1,tag2")
            .setReleaseVer("8.0")
            .setGpgUrl("https://gpg.example.com")
            .setArches("x86_64,aarch64")
            .build();

        assertEquals("content-id", dto.id());
        assertEquals("content-type", dto.type());
        assertEquals("content-label", dto.label());
        assertEquals("content-name", dto.name());
        assertEquals("content-vendor", dto.vendor());
        assertEquals("https://content.example.com", dto.contentUrl());
        assertEquals("tag1,tag2", dto.requiredTags());
        assertEquals("8.0", dto.releaseVer());
        assertEquals("https://gpg.example.com", dto.gpgUrl());
        assertEquals("x86_64,aarch64", dto.arches());
    }

    @Test
    public void testBuilderWithNullFields() {
        ContentDTO dto = new ContentDTO.Builder().build();

        assertNull(dto.id());
        assertNull(dto.type());
        assertNull(dto.label());
        assertNull(dto.name());
        assertNull(dto.vendor());
        assertNull(dto.contentUrl());
        assertNull(dto.requiredTags());
        assertNull(dto.releaseVer());
        assertNull(dto.gpgUrl());
        assertNull(dto.arches());
    }
}
