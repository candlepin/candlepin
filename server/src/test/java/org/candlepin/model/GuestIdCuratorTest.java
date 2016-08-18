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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.test.DatabaseTestFixture;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import javax.inject.Inject;

/**
 * GuestIdCuratorTest
 */
public class GuestIdCuratorTest extends DatabaseTestFixture {
    @Inject private OwnerCurator ownerCurator;
    @Inject private ConsumerCurator consumerCurator;
    @Inject private ConsumerTypeCurator consumerTypeCurator;
    @Inject private GuestIdCurator curator;

    private Owner owner;
    private ConsumerType ct;

    @Before
    @Override
    public void init() {
        super.init();
        owner = new Owner("test-owner", "Test Owner");
        owner = ownerCurator.create(owner);
        ct = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        ct = consumerTypeCurator.create(ct);
    }

    @Test
    public void listByConsumerTest() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        for (int i = 0; i < 5; i++) {
            consumer.addGuestId(new GuestId("" + i));
        }
        consumerCurator.create(consumer);

        List<GuestId> result = curator.listByConsumer(consumer);
        assertEquals(5, result.size());
        for (int i = 0; i < 5; i++) {
            assertTrue(result.contains(new GuestId("" + i)));
        }
    }

    @Test
    public void listByConsumerTestEmpty() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumerCurator.create(consumer);

        List<GuestId> result = curator.listByConsumer(consumer);
        assertEquals(0, result.size());
    }

    @Test
    public void findByConsumerAndIdDoesntExist() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer.addGuestId(new GuestId("1"));
        consumerCurator.create(consumer);

        GuestId result = curator.findByConsumerAndId(consumer, "2");
        assertNull(result);
    }

    @Test
    public void findByConsumerAndIdOtherConsumer() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer.addGuestId(new GuestId("1"));
        consumerCurator.create(consumer);

        Consumer other = new Consumer("testConsumer2", "testUser2", owner, ct);
        other.addGuestId(new GuestId("2"));
        consumerCurator.create(other);

        GuestId result = curator.findByConsumerAndId(consumer, "2");
        assertNull(result);
    }

    @Test
    public void findByConsumerAndId() {
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer.addGuestId(new GuestId("1"));
        consumerCurator.create(consumer);

        GuestId result = curator.findByConsumerAndId(consumer, "1");
        assertEquals(new GuestId("1"), result);
    }

    @Test
    public void findByConsumerAndIdCaseInsensitive() {
        String guestId = "GuEsTiD";
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer.addGuestId(new GuestId(guestId));
        consumerCurator.create(consumer);

        GuestId result = curator.findByConsumerAndId(consumer, guestId.toUpperCase());
        /**
         * Note that we keep the original case of the guestId, we only search
         * in case insensitive way
         */
        assertEquals(new GuestId(guestId), result);
    }


    @Test
    public void findByGuestIdAndOrgCaseInsensitive() {
        String guestId = "GuEsTiD";
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumer.addGuestId(new GuestId(guestId));
        consumerCurator.create(consumer);

        GuestId result = curator.findByGuestIdAndOrg(guestId.toUpperCase(), owner);
        /**
         * Note that we keep the original case of the guestId, we only search
         * in case insensitive way
         */
        assertEquals(new GuestId(guestId), result);
    }
}
