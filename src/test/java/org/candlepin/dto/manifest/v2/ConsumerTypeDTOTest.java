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

public class ConsumerTypeDTOTest {

    @Test
    public void testMutableFields() {
        ConsumerTypeDTO dto = new ConsumerTypeDTO()
            .setId("type-id")
            .setName("type-name");

        assertEquals("type-id", dto.getId());
        assertEquals("type-name", dto.getName());
    }

    @Test
    public void testEmptyDto() {
        ConsumerTypeDTO dto = new ConsumerTypeDTO();

        assertNull(dto.getId());
        assertNull(dto.getName());
    }
}
