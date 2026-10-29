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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class ContentDTOTest {

    @Test
    public void testMutableFields() {
        ContentDTO dto = new ContentDTO()
            .setEnabled(true)
            .setId("content-id")
            .setType("content-type")
            .setLabel("content-label")
            .setName("content-name")
            .setVendor("content-vendor")
            .setContentUrl("https://content.example.com")
            .setRequiredTags("tag1,tag2")
            .setReleaseVer("8.0")
            .setGpgUrl("https://gpg.example.com")
            .setArches("x86_64,aarch64");

        assertTrue(dto.isEnabled());
        assertEquals("content-id", dto.getId());
        assertEquals("content-type", dto.getType());
        assertEquals("content-label", dto.getLabel());
        assertEquals("content-name", dto.getName());
        assertEquals("content-vendor", dto.getVendor());
        assertEquals("https://content.example.com", dto.getContentUrl());
        assertEquals("tag1,tag2", dto.getRequiredTags());
        assertEquals("8.0", dto.getReleaseVer());
        assertEquals("https://gpg.example.com", dto.getGpgUrl());
        assertEquals("x86_64,aarch64", dto.getArches());
    }

    @Test
    public void testEnabledPreservedByCopiesAndEquality() {
        ContentDTO enabled = new ContentDTO().setId("content").setEnabled(true);
        ContentDTO copy = new ContentDTO(enabled);

        assertTrue(copy.isEnabled());
        assertEquals(enabled, copy);
        assertEquals(enabled.hashCode(), copy.hashCode());
        assertTrue(enabled.clone().isEnabled());
        copy.setEnabled(false);
        assertNotEquals(enabled, copy);
        enabled.populate(copy);
        assertFalse(enabled.isEnabled());
    }

    @Test
    public void testEmptyDto() {
        ContentDTO dto = new ContentDTO();

        assertFalse(dto.isEnabled());
        assertNull(dto.getId());
        assertNull(dto.getType());
        assertNull(dto.getLabel());
        assertNull(dto.getName());
        assertNull(dto.getVendor());
        assertNull(dto.getContentUrl());
        assertNull(dto.getRequiredTags());
        assertNull(dto.getReleaseVer());
        assertNull(dto.getGpgUrl());
        assertNull(dto.getArches());
    }
}
