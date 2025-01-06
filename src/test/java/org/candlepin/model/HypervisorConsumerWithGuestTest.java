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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.test.TestUtil;

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

    @Test
    public void testEqualsWithSameInstance() {
        HypervisorConsumerWithGuest actual = new HypervisorConsumerWithGuest("hypervisor-consumer-uuid",
            "hypervisor-consumer-name", "guest-consumer-uuid", "guest-id");

        assertTrue(actual.equals(actual));
    }

    @Test
    public void testEqualsWithDifferentClass() {
        HypervisorConsumerWithGuest actual = new HypervisorConsumerWithGuest("hypervisor-consumer-uuid",
            "hypervisor-consumer-name", "guest-consumer-uuid", "guest-id");

        assertFalse(actual.equals(TestUtil.randomString()));
    }

    @Test
    public void testEqualsWithDifferentHypervisorConsumerUuid() {
        String hypervisorConsumerUuid = "hypervisor-consumer-uuid";
        String hypervisorConsumerName = "hypervisor-consumer-name";
        String guestConsumerUuid = "guest-consumer-uuid";
        String guestId = "guest-consumer-uuid";

        HypervisorConsumerWithGuest obj1 = new HypervisorConsumerWithGuest(hypervisorConsumerUuid,
            hypervisorConsumerName, guestConsumerUuid, guestId);

        HypervisorConsumerWithGuest obj2 = new HypervisorConsumerWithGuest("different",
            hypervisorConsumerName, guestConsumerUuid, guestId);

        assertFalse(obj1.equals(obj2));
    }

    @Test
    public void testEqualsWithDifferentHypervisorConsumerName() {
        String hypervisorConsumerUuid = "hypervisor-consumer-uuid";
        String hypervisorConsumerName = "hypervisor-consumer-name";
        String guestConsumerUuid = "guest-consumer-uuid";
        String guestId = "guest-consumer-uuid";

        HypervisorConsumerWithGuest obj1 = new HypervisorConsumerWithGuest(hypervisorConsumerUuid,
            hypervisorConsumerName, guestConsumerUuid, guestId);

        HypervisorConsumerWithGuest obj2 = new HypervisorConsumerWithGuest(hypervisorConsumerUuid,
            "different", guestConsumerUuid, guestId);

        assertFalse(obj1.equals(obj2));
    }

    @Test
    public void testEqualsWithDifferentGuestConsumerUuid() {
        String hypervisorConsumerUuid = "hypervisor-consumer-uuid";
        String hypervisorConsumerName = "hypervisor-consumer-name";
        String guestConsumerUuid = "guest-consumer-uuid";
        String guestId = "guest-consumer-uuid";

        HypervisorConsumerWithGuest obj1 = new HypervisorConsumerWithGuest(hypervisorConsumerUuid,
            hypervisorConsumerName, guestConsumerUuid, guestId);

        HypervisorConsumerWithGuest obj2 = new HypervisorConsumerWithGuest(hypervisorConsumerUuid,
            hypervisorConsumerName, "different", guestId);

        assertFalse(obj1.equals(obj2));
    }

    @Test
    public void testEqualsWithDifferentGuestId() {
        String hypervisorConsumerUuid = "hypervisor-consumer-uuid";
        String hypervisorConsumerName = "hypervisor-consumer-name";
        String guestConsumerUuid = "guest-consumer-uuid";
        String guestId = "guest-consumer-uuid";

        HypervisorConsumerWithGuest obj1 = new HypervisorConsumerWithGuest(hypervisorConsumerUuid,
            hypervisorConsumerName, guestConsumerUuid, guestId);

        HypervisorConsumerWithGuest obj2 = new HypervisorConsumerWithGuest(hypervisorConsumerUuid,
            hypervisorConsumerName, guestConsumerUuid, "different");

        assertFalse(obj1.equals(obj2));
    }

    @Test
    public void testEquals() {
        String hypervisorConsumerUuid = "hypervisor-consumer-uuid";
        String hypervisorConsumerName = "hypervisor-consumer-name";
        String guestConsumerUuid = "guest-consumer-uuid";
        String guestId = "guest-consumer-uuid";

        HypervisorConsumerWithGuest obj1 = new HypervisorConsumerWithGuest(hypervisorConsumerUuid,
            hypervisorConsumerName, guestConsumerUuid, guestId);

        HypervisorConsumerWithGuest obj2 = new HypervisorConsumerWithGuest(hypervisorConsumerUuid,
            hypervisorConsumerName, guestConsumerUuid, guestId);

        assertTrue(obj1.equals(obj2));
    }

    @Test
    public void testHashCode() {
        String hypervisorConsumerUuid = "hypervisor-consumer-uuid";
        String hypervisorConsumerName = "hypervisor-consumer-name";
        String guestConsumerUuid = "guest-consumer-uuid";
        String guestId = "guest-consumer-uuid";

        HypervisorConsumerWithGuest obj1 = new HypervisorConsumerWithGuest(hypervisorConsumerUuid,
            hypervisorConsumerName, guestConsumerUuid, guestId);

        HypervisorConsumerWithGuest obj2 = new HypervisorConsumerWithGuest(hypervisorConsumerUuid,
            hypervisorConsumerName, guestConsumerUuid, guestId);

        assertEquals(obj1.hashCode(), obj2.hashCode());
    }

}

