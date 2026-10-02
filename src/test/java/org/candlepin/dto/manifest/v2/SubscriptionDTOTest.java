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
    public void testBuilderWithAllFields() {
        Date startDate = new Date();
        Date endDate = new Date(startDate.getTime() + 86400000L);

        SubscriptionDTO dto = SubscriptionDTO.builder()
            .setSubscriptionId("sub-123")
            .setStartDate(startDate)
            .setEndDate(endDate)
            .setContractNumber("contract-456")
            .setAccountNumber("acc-789")
            .setProductId("prod-001")
            .build();

        assertEquals("sub-123", dto.subscriptionId());
        assertEquals(startDate, dto.startDate());
        assertEquals(endDate, dto.endDate());
        assertEquals("contract-456", dto.contractNumber());
        assertEquals("acc-789", dto.accountNumber());
        assertEquals("prod-001", dto.productId());
    }

    @Test
    public void testBuilderWithNullFields() {
        SubscriptionDTO dto = new SubscriptionDTO.Builder().build();

        assertNull(dto.subscriptionId());
        assertNull(dto.startDate());
        assertNull(dto.endDate());
        assertNull(dto.contractNumber());
        assertNull(dto.accountNumber());
        assertNull(dto.productId());
    }
}
