/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.candlepin.model.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.GuestId;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.test.DatabaseTestFixture;

import org.junit.Test;

import java.util.List;

/**
 *
 */
public class ConsumerCuratorTest extends DatabaseTestFixture {

    @Test
    @SuppressWarnings("unchecked")
    public void normalCreate() {
        Owner owner = new Owner("test-owner", "Test Owner");
        owner = ownerCurator.create(owner);
        ConsumerType ct = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        ct = consumerTypeCurator.create(ct);
        Consumer consumer = new Consumer("testConsumer", "testUser", owner, ct);
        consumerCurator.create(consumer);

        List<Product> results = entityManager().createQuery(
                "select c from Consumer as c").getResultList();
        assertEquals(1, results.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void addGuestConsumers() {
        Owner owner = new Owner("test-owner", "Test Owner");
        owner = ownerCurator.create(owner);
        ConsumerType ct = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        ct = consumerTypeCurator.create(ct);
        Consumer consumer = new Consumer("hostConsumer", "testUser", owner, ct);
        consumerCurator.create(consumer);
        Consumer gConsumer1 = new Consumer("guestConsumer1", "testUser", owner, ct);
        gConsumer1.getFacts().put("virt.uuid", "test-guest-1");
        consumerCurator.create(gConsumer1);
        Consumer gConsumer2 = new Consumer("guestConsumer2", "testUser", owner, ct);
        gConsumer2.getFacts().put("virt.uuid", "test-guest-2");
        consumerCurator.create(gConsumer2);
        consumer.addGuestId(new GuestId("test-guest-1"));
        consumer.addGuestId(new GuestId("test-guest-2"));
        consumerCurator.update(consumer);

        List<Consumer> guests = consumerCurator.getGuests(consumer);
        assertTrue(guests.size() == 2);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void addGuestsNotConsumers() {
        Owner owner = new Owner("test-owner", "Test Owner");
        owner = ownerCurator.create(owner);
        ConsumerType ct = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        ct = consumerTypeCurator.create(ct);
        Consumer consumer = new Consumer("hostConsumer", "testUser", owner, ct);
        consumerCurator.create(consumer);
        consumer.addGuestId(new GuestId("test-guest-1"));
        consumer.addGuestId(new GuestId("test-guest-2"));
        consumerCurator.update(consumer);

        List<Consumer> guests = consumerCurator.getGuests(consumer);
        assertTrue(guests.size() == 0);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void getGuestConsumerSharedId() throws Exception {
        Owner owner = new Owner("test-owner", "Test Owner");
        owner = ownerCurator.create(owner);
        ConsumerType ct = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        ct = consumerTypeCurator.create(ct);
        Consumer hConsumer1 = new Consumer("hostConsumer1", "testUser", owner, ct);
        consumerCurator.create(hConsumer1);
        Consumer hConsumer2 = new Consumer("hostConsumer2", "testUser", owner, ct);
        consumerCurator.create(hConsumer2);
        Consumer gConsumer1 = new Consumer("guestConsumer1", "testUser", owner, ct);
        gConsumer1.getFacts().put("virt.uuid", "test-guest-1");
        consumerCurator.create(gConsumer1);

        // This can happen so fast the consumers end up with the same update time,
        // it's 5 milliseconds, deal with it.
        Thread.sleep(5);

        Consumer gConsumer2 = new Consumer("guestConsumer2", "testUser", owner, ct);
        gConsumer2.getFacts().put("virt.uuid", "test-guest-1");
        consumerCurator.create(gConsumer2);

        hConsumer1.addGuestId(new GuestId("test-guest-1"));
        hConsumer2.addGuestId(new GuestId("test-guest-1"));
        consumerCurator.update(hConsumer1);
        consumerCurator.update(hConsumer2);

        List<Consumer> guests1 = consumerCurator.getGuests(hConsumer1);
        List<Consumer> guests2 = consumerCurator.getGuests(hConsumer2);
        assertTrue(guests1.size() == 0);
        assertTrue(guests2.size() == 1);
        assertEquals("guestConsumer2", guests2.get(0).getName());
    }

    @Test
    public void noHostRegistered() {
        Consumer host = consumerCurator.getHost("system-uuid-for-guest");
        assertTrue(host == null);
    }

    @Test
    public void noGuestsRegistered() {
        Owner owner = new Owner("test-owner", "Test Owner");
        owner = ownerCurator.create(owner);
        ConsumerType ct = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        ct = consumerTypeCurator.create(ct);
        Consumer consumer = new Consumer("hostConsumer", "testUser", owner, ct);
        consumer = consumerCurator.create(consumer);

        List<Consumer> guests = consumerCurator.getGuests(consumer);
        assertTrue(guests.size() == 0);
    }
}
