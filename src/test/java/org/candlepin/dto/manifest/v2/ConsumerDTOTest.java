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
    public void testBuilderWithAllFields() {
        ConsumerTypeDTO type = new ConsumerTypeDTO("type-id", "type-name");
        ConsumerDTO dto = new ConsumerDTO.Builder()
            .setUuid("uuid-123")
            .setName("consumer-name")
            .setType(type)
            .setOwnerId("owner-456")
            .setUrlWeb("https://web.example.com")
            .setUrlApi("https://api.example.com")
            .build();

        assertEquals("uuid-123", dto.uuid());
        assertEquals("consumer-name", dto.name());
        assertEquals(type, dto.type());
        assertEquals("owner-456", dto.ownerId());
        assertEquals("https://web.example.com", dto.urlWeb());
        assertEquals("https://api.example.com", dto.urlApi());
    }

    @Test
    public void testBuilderWithNullFields() {
        ConsumerDTO dto = new ConsumerDTO.Builder().build();

        assertNull(dto.uuid());
        assertNull(dto.name());
        assertNull(dto.type());
        assertNull(dto.ownerId());
        assertNull(dto.urlWeb());
        assertNull(dto.urlApi());
    }
}
