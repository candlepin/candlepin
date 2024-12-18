/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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
package org.candlepin.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class HypervisorConsumerWithGuestTest {

    @Test
    public void testConstructor() {
        String hypervisorConsumerUuid = "hypervisor-consumer-uuid";
        String hypervisorConsumerName = "hypervisor-consumer-name";
        String guestConsumerUuid = "guest-consumer-uuid";
        String guestId = "guest-id";

        HypervisorConsumerWithGuest actual = new HypervisorConsumerWithGuest(hypervisorConsumerUuid,
            hypervisorConsumerName, guestConsumerUuid, guestId);

        assertEquals(hypervisorConsumerUuid, actual.getHypervisorConsumerUuid());
        assertEquals(hypervisorConsumerName, actual.getHypervisorConsumerName());
        assertEquals(guestConsumerUuid, actual.getGuestConsumerUuid());
        assertEquals(guestId, actual.getGuestId());
    }

    @Test
    public void testSetHypervisorConsumerUuid() {
        HypervisorConsumerWithGuest actual = new HypervisorConsumerWithGuest("hypervisor-consumer-uuid",
            "hypervisor-consumer-name", "guest-consumer-uuid", "guest-id");

        String updatedValue = "updated-value";
        actual.setHypervisorConsumerUuid(updatedValue);

        assertEquals(updatedValue, actual.getHypervisorConsumerUuid());
    }

    @Test
    public void testSetHypervisorConsumerName() {
        HypervisorConsumerWithGuest actual = new HypervisorConsumerWithGuest("hypervisor-consumer-uuid",
            "hypervisor-consumer-name", "guest-consumer-uuid", "guest-id");

        String updatedValue = "updated-value";
        actual.setHypervisorConsumerName(updatedValue);

        assertEquals(updatedValue, actual.getHypervisorConsumerName());
    }

    @Test
    public void testSetGuestConsumerUuid() {
        HypervisorConsumerWithGuest actual = new HypervisorConsumerWithGuest("hypervisor-consumer-uuid",
            "hypervisor-consumer-name", "guest-consumer-uuid", "guest-id");

        String updatedValue = "updated-value";
        actual.setGuestConsumerUuid(updatedValue);

        assertEquals(updatedValue, actual.getGuestConsumerUuid());
    }

    @Test
    public void testSetGuestId() {
        HypervisorConsumerWithGuest actual = new HypervisorConsumerWithGuest("hypervisor-consumer-uuid",
            "hypervisor-consumer-name", "guest-consumer-uuid", "guest-id");

        String updatedValue = "updated-value";
        actual.setGuestId(updatedValue);

        assertEquals(updatedValue, actual.getGuestId());
    }

}
