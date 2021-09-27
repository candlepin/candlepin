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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.candlepin.test.DatabaseTestFixture;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.util.List;

import javax.inject.Inject;



public class KeyPairCuratorTest extends DatabaseTestFixture {
    @Inject private KeyPairCurator keyPairCurator;

    @Test
    public void testSameConsumerGetsSameKey() {
        Owner owner = createOwner();

        Consumer consumer = createConsumer(owner);

        KeyPair keyPair1 = keyPairCurator.getConsumerKeyPair(consumer);
        KeyPair keyPair2 = keyPairCurator.getConsumerKeyPair(consumer);

        assertEquals(keyPair1.getPrivate(), keyPair2.getPrivate());
    }

    @Test
    public void testTwoConsumersGetDifferentKeys() {
        Owner owner = createOwner();

        Consumer consumer1 = createConsumer(owner);
        Consumer consumer2 = createConsumer(owner);

        KeyPair keyPair1 = keyPairCurator.getConsumerKeyPair(consumer1);
        KeyPair keyPair2 = keyPairCurator.getConsumerKeyPair(consumer2);

        assertNotEquals(keyPair1.getPrivate(), keyPair2.getPrivate());
    }

    @Test
    public void shouldListKeyPairs() {
        Owner owner = this.ownerCurator.create(new Owner("test-owner", "Test Owner"));
        ConsumerType ct = this.consumerTypeCurator.create(new ConsumerType(ConsumerType.ConsumerTypeEnum.SYSTEM));
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer.setKeyPair(new org.candlepin.model.KeyPair());
        consumer = this.consumerCurator.create(consumer);
        Consumer consumer2 = new Consumer("testConsumer", "testUser", owner, ct);
        consumer2.setKeyPair(new org.candlepin.model.KeyPair());
        consumer2 = this.consumerCurator.create(consumer2);

        List<String> found = this.keyPairCurator
            .findKeyPairIdsOf(List.of(consumer.getId(), consumer2.getId()));

        assertEquals(2, found.size());
        for (String keyPairId : found) {
            assertNotNull(keyPairId);
        }
    }

    @Test
    public void shouldDeleteKeyPairs() {
        Owner owner = this.ownerCurator.create(new Owner("test-owner", "Test Owner"));
        ConsumerType ct = this.consumerTypeCurator.create(new ConsumerType(ConsumerType.ConsumerTypeEnum.SYSTEM));
        KeyPair keyPair = keyPairCurator.getKeyPair();
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer.setKeyPair(new org.candlepin.model.KeyPair(keyPair.getPrivate(), keyPair.getPublic()));
        consumer = this.consumerCurator.create(consumer);
        Consumer consumer2 = new Consumer("testConsumer", "testUser", owner, ct);
        consumer2.setKeyPair(new org.candlepin.model.KeyPair(keyPair.getPrivate(), keyPair.getPublic()));
        consumer2 = this.consumerCurator.create(consumer2);

        int unlinked = this.keyPairCurator
            .unlinkKeyPairsFromConsumers(List.of(consumer.getId(), consumer2.getId()));
        int deleted = this.keyPairCurator
            .bulkDeleteKeyPairs(List.of(consumer.getKeyPair().getId(), consumer2.getKeyPair().getId()));
        this.keyPairCurator.flush();
        this.keyPairCurator.clear();

        assertEquals(2, unlinked);
        assertEquals(2, deleted);
        assertNull(this.consumerCurator.getConsumer(consumer.getUuid()).getKeyPair());
        assertNull(this.consumerCurator.getConsumer(consumer2.getUuid()).getKeyPair());
    }

    @Test
    public void nokeyPairsToDelete() {
        assertEquals(0, keyPairCurator.bulkDeleteKeyPairs(null));
        assertEquals(0, keyPairCurator.bulkDeleteKeyPairs(List.of()));
        assertEquals(0, keyPairCurator.bulkDeleteKeyPairs(List.of("unknown")));
    }

}
