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

public class SubscriptionDTOTest {

    @Test
    public void testMutableFields() {
        Date startDate = new Date();
        Date endDate = new Date(startDate.getTime() + 86400000L);

        SubscriptionDTO dto = new SubscriptionDTO()
            .setSubscriptionId("sub-123")
            .setStartDate(startDate)
            .setEndDate(endDate)
            .setContractNumber("contract-456")
            .setAccountNumber("acc-789")
            .setProductId("prod-001");

        assertEquals("sub-123", dto.getSubscriptionId());
        assertEquals(startDate, dto.getStartDate());
        assertEquals(endDate, dto.getEndDate());
        assertEquals("contract-456", dto.getContractNumber());
        assertEquals("acc-789", dto.getAccountNumber());
        assertEquals("prod-001", dto.getProductId());
    }

    @Test
    public void testEmptyDto() {
        SubscriptionDTO dto = new SubscriptionDTO();

        assertNull(dto.getSubscriptionId());
        assertNull(dto.getStartDate());
        assertNull(dto.getEndDate());
        assertNull(dto.getContractNumber());
        assertNull(dto.getAccountNumber());
        assertNull(dto.getProductId());
    }
}
