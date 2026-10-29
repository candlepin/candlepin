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

public class ConsumerDTOTest {

    @Test
    public void testMutableFields() {
        ConsumerTypeDTO type = new ConsumerTypeDTO()
            .setId("type-id")
            .setLabel("type-label");
        ConsumerDTO dto = new ConsumerDTO()
            .setUuid("uuid-123")
            .setName("consumer-name")
            .setType(type)
            .setOwnerId("owner-456")
            .setUrlWeb("https://web.example.com")
            .setUrlApi("https://api.example.com");

        assertEquals("uuid-123", dto.getUuid());
        assertEquals("consumer-name", dto.getName());
        assertEquals(type, dto.getType());
        assertEquals("owner-456", dto.getOwnerId());
        assertEquals("https://web.example.com", dto.getUrlWeb());
        assertEquals("https://api.example.com", dto.getUrlApi());
    }

    @Test
    public void testEmptyDto() {
        ConsumerDTO dto = new ConsumerDTO();

        assertNull(dto.getUuid());
        assertNull(dto.getName());
        assertNull(dto.getType());
        assertNull(dto.getOwnerId());
        assertNull(dto.getUrlWeb());
        assertNull(dto.getUrlApi());
    }
}
