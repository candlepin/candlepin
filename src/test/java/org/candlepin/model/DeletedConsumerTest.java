/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import java.util.UUID;

public class DeletedConsumerTest {

    @Test
    public void testConstructors() {
        DeletedConsumer deletedConsumer = new DeletedConsumer();
        assertNull(deletedConsumer.getConsumerUuid());
        assertNull(deletedConsumer.getConsumerName());
        assertNull(deletedConsumer.getOwnerId());
        assertNull(deletedConsumer.getOwnerKey());
        assertNull(deletedConsumer.getOwnerDisplayName());
        assertNull(deletedConsumer.getPrincipalName());

        String expectedConsumerUuid = UUID.randomUUID().toString();
        String expectedConsumerName = "test-consumer-name";
        String expectedOwnerId = "test-owner-id";
        String expectedOwnerKey = "test-owner-key";
        String expectedOwnerDisplayName = "test-owner-display-name";
        deletedConsumer = new DeletedConsumer(expectedConsumerUuid, expectedOwnerId,
            expectedOwnerKey, expectedOwnerDisplayName);

        assertEquals(expectedConsumerUuid, deletedConsumer.getConsumerUuid());
        assertEquals(expectedOwnerId, deletedConsumer.getOwnerId());
        assertEquals(expectedOwnerKey, deletedConsumer.getOwnerKey());
        assertEquals(expectedOwnerDisplayName, deletedConsumer.getOwnerDisplayName());
    }

    @Test
    public void testGetSetConsumerUuid() {
        DeletedConsumer deletedConsumer = new DeletedConsumer();

        // Verify initial state is as we expect
        assertNull(deletedConsumer.getConsumerUuid());

        String expectedConsumerUuid = UUID.randomUUID().toString();

        // Update the field, and ensure we're returning a self-ref
        DeletedConsumer out = deletedConsumer.setConsumerUuid(expectedConsumerUuid);
        assertSame(out, deletedConsumer);

        //Verify final state is as expected
        assertEquals(expectedConsumerUuid, deletedConsumer.getConsumerUuid());
    }

    @Test
    public void testGetSetConsumerName() {
        DeletedConsumer deletedConsumer = new DeletedConsumer();

        // Verify initial state is as we expect
        assertNull(deletedConsumer.getConsumerName());

        String expectedConsumerName = "test-consumer-name";

        // Update the field, and ensure we're returning a self-ref
        DeletedConsumer out = deletedConsumer.setConsumerName(expectedConsumerName);
        assertSame(out, deletedConsumer);

        //Verify final state is as expected
        assertEquals(expectedConsumerName, deletedConsumer.getConsumerName());
    }

    @Test
    public void testGetSetOwnerId() {
        DeletedConsumer deletedConsumer = new DeletedConsumer();

        // Verify initial state is as we expect
        assertNull(deletedConsumer.getOwnerId());

        String expectedOwnerId = "test-owner-id";

        // Update the field, and ensure we're returning a self-ref
        DeletedConsumer out = deletedConsumer.setOwnerId(expectedOwnerId);
        assertSame(out, deletedConsumer);

        //Verify final state is as expected
        assertEquals(expectedOwnerId, deletedConsumer.getOwnerId());
    }

    @Test
    public void testGetSetOwnerKey() {
        DeletedConsumer deletedConsumer = new DeletedConsumer();

        // Verify initial state is as we expect
        assertNull(deletedConsumer.getOwnerKey());

        String expectedOwnerKey = "test-owner-key";

        // Update the field, and ensure we're returning a self-ref
        DeletedConsumer out = deletedConsumer.setOwnerKey(expectedOwnerKey);
        assertSame(out, deletedConsumer);

        //Verify final state is as expected
        assertEquals(expectedOwnerKey, deletedConsumer.getOwnerKey());
    }

    @Test
    public void testGetSetOwnerDisplayName() {
        DeletedConsumer deletedConsumer = new DeletedConsumer();

        // Verify initial state is as we expect
        assertNull(deletedConsumer.getOwnerDisplayName());

        String expectedOwnerDisplayName = "test-owner-display-name";

        // Update the field, and ensure we're returning a self-ref
        DeletedConsumer out = deletedConsumer.setOwnerDisplayName(expectedOwnerDisplayName);
        assertSame(out, deletedConsumer);

        //Verify final state is as expected
        assertEquals(expectedOwnerDisplayName, deletedConsumer.getOwnerDisplayName());
    }

    @Test
    public void testGetSetPrincipalName() {
        DeletedConsumer deletedConsumer = new DeletedConsumer();

        // Verify initial state is as we expect
        assertNull(deletedConsumer.getPrincipalName());

        String expectedPrincipalName = "test-principal-name";

        // Update the field, and ensure we're returning a self-ref
        DeletedConsumer out = deletedConsumer.setPrincipalName(expectedPrincipalName);
        assertSame(out, deletedConsumer);

        //Verify final state is as expected
        assertEquals(expectedPrincipalName, deletedConsumer.getPrincipalName());
    }
}
