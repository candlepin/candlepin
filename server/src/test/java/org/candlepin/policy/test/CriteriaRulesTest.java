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
package org.candlepin.policy.test;

import static org.junit.Assert.assertEquals;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.GuestId;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Date;
import java.util.List;

/*
 * Test the Javascript pool criteria. This works because we configure an enforcer for the
 * unit tests that by default, will always return success. As such if we see pools getting
 * filtered out below, we know it was because of the hibernate query mechanism.
 */
@RunWith(MockitoJUnitRunner.class)
public class CriteriaRulesTest extends DatabaseTestFixture {

    private Owner owner;
    private Consumer consumer;

    @Before
    public void setUp() {
        owner = this.createOwner();
    }

    @Test
    public void virtOnlyPoolAttributeFiltering() {

        consumer = this.createConsumer(owner);
        Product targetProduct = TestUtil.createProduct();
        this.productCurator.create(targetProduct);
        Pool physicalPool = this.createPoolAndSub(owner, targetProduct, 1L, new Date(),
            new Date());

        Pool virtPool = this.createPoolAndSub(owner, targetProduct, 1L, new Date(),
            new Date());
        virtPool.setAttribute("virt_only", "true");
        poolCurator.merge(virtPool);

        List<Pool> results = poolCurator.listAvailableEntitlementPools(consumer, null,
            null, null, false);

        assertEquals(1, results.size());
        assertEquals(physicalPool.getId(), results.get(0).getId());

        // Make the consumer a guest and try again:
        consumer.setFact("virt.is_guest", "true");
        results = poolCurator.listAvailableEntitlementPools(consumer, null,
            null, null, false);

        assertEquals(2, results.size());

    }

    // Virt only can also be on the product:
    @Test
    public void virtOnlyProductAttributeFiltering() {

        consumer = this.createConsumer(owner);
        Product targetProduct = TestUtil.createProduct();
        targetProduct.setAttribute("virt_only", "true");
        this.productCurator.create(targetProduct);
        this.createPoolAndSub(owner, targetProduct, 1L, new Date(), new Date());

        this.createPoolAndSub(owner, targetProduct, 1L, new Date(),
            new Date());

        List<Pool> results = poolCurator.listAvailableEntitlementPools(consumer, null,
            null, null, false);

        assertEquals(0, results.size());
        // Make the consumer a guest and try again:
        consumer.setFact("virt.is_guest", "true");
        results = poolCurator.listAvailableEntitlementPools(consumer, null,
            null, null, false);

        assertEquals(2, results.size());
    }

    @Test
    public void requiresHostPoolAttributeFiltering() {

        consumer = this.createConsumer(owner);

        Consumer host = createConsumer(owner);
        host.addGuestId(new GuestId("GUESTUUID", host));
        consumerCurator.update(host);

        Product targetProduct = TestUtil.createProduct();
        this.productCurator.create(targetProduct);

        Pool virtPool = this.createPoolAndSub(owner, targetProduct, 1L, new Date(),
            new Date());
        virtPool.setAttribute("virt_only", "true");
        virtPool.setAttribute("requires_host", host.getUuid());
        poolCurator.merge(virtPool);

        // Another pool requiring a different host:
        Pool anotherVirtPool = this.createPoolAndSub(owner, targetProduct, 1L, new Date(),
            new Date());
        anotherVirtPool.setAttribute("virt_only", "true");
        anotherVirtPool.setAttribute("requires_host", "SOMEOTHERUUID");
        poolCurator.merge(anotherVirtPool);

        List<Pool> results = poolCurator.listAvailableEntitlementPools(consumer, null,
            null, null, false);

        assertEquals(0, results.size());

        // Make the consumer a guest and try again:
        consumer.setFact("virt.is_guest", "true");
        consumer.setFact("virt.uuid", "GUESTUUID");
        consumerCurator.update(consumer);
        assertEquals(host.getUuid(), consumerCurator.getHost("GUESTUUID", owner).getUuid());
        results = poolCurator.listAvailableEntitlementPools(consumer, null,
            null, null, false);

        assertEquals(1, results.size());
        assertEquals(virtPool.getId(), results.get(0).getId());
    }

    @Test
    public void manifestConsumerVirtOnlyNoRequiresHost() {
        // create a manifest consumer
        ConsumerType type = new ConsumerType(ConsumerType.ConsumerTypeEnum.CANDLEPIN);
        consumerTypeCurator.create(type);
        Consumer c = new Consumer("test-consumer", "test-user", owner, type);
        consumerCurator.create(c);

        Consumer host = createConsumer(owner);
        host.addGuestId(new GuestId("GUESTUUID", host));
        consumerCurator.update(host);

        Product targetProduct = TestUtil.createProduct();
        this.productCurator.create(targetProduct);

        Pool virtPool = this.createPoolAndSub(owner, targetProduct, 1L, new Date(),
            new Date());
        virtPool.setAttribute("virt_only", "true");
        virtPool.setAttribute("requires_host", host.getUuid());
        poolCurator.merge(virtPool);

        List<Pool> results = poolCurator.listAvailableEntitlementPools(
            c, null, null, null, false);
        assertEquals(0, results.size());
    }

    @Test
    public void manifestConsumerVirtOnly() {
        // create a manifest consumer
        ConsumerType type = new ConsumerType(ConsumerType.ConsumerTypeEnum.CANDLEPIN);
        consumerTypeCurator.create(type);
        Consumer c = new Consumer("test-consumer", "test-user", owner, type);
        consumerCurator.create(c);

        Consumer host = createConsumer(owner);
        host.addGuestId(new GuestId("GUESTUUID", host));
        consumerCurator.update(host);

        Product targetProduct = TestUtil.createProduct();
        this.productCurator.create(targetProduct);

        Pool virtPool = this.createPoolAndSub(owner, targetProduct, 1L, new Date(),
            new Date());
        virtPool.setAttribute("virt_only", "true");
        poolCurator.merge(virtPool);

        List<Pool> results = poolCurator.listAvailableEntitlementPools(
            c, null, null, null, false);
        assertEquals(1, results.size());
    }
}


