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
package org.candlepin.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.GuestId;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collection;
import java.util.Date;
import java.util.List;



/*
 * Test the Javascript pool criteria. This works because we configure an enforcer for the
 * unit tests that by default, will always return success. As such if we see pools getting
 * filtered out below, we know it was because of the hibernate query mechanism.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CriteriaRulesTest extends DatabaseTestFixture {

    private Owner owner;
    private Consumer consumer;

    @BeforeEach
    public void setUp() {
        owner = this.createOwner();
    }

    @Test
    public void virtOnlyPoolAttributeFiltering() {

        consumer = this.createConsumer(owner);
        Product targetProduct = this.createProduct(owner);

        Pool physicalPool = this.createPool(owner, targetProduct, 1L, new Date(), new Date());
        Pool virtPool = this.createPool(owner, targetProduct, 1L, new Date(), new Date());
        virtPool.setAttribute(Product.Attributes.VIRT_ONLY, "true");
        poolCurator.merge(virtPool);
        poolCurator.flush();

        List<Pool> results = poolCurator.listAvailableEntitlementPools(consumer, (String) null,
            (Collection<String>) null, null);

        assertEquals(1, results.size());
        assertEquals(physicalPool.getId(), results.get(0).getId());

        // Make the consumer a guest and try again:
        consumer.setFact("virt.is_guest", "true");
        results = poolCurator.listAvailableEntitlementPools(consumer, (String) null,
            (Collection<String>) null, null);

        assertEquals(2, results.size());

    }

    // Virt only can also be on the product:
    @Test
    public void virtOnlyProductAttributeFiltering() {
        consumer = this.createConsumer(owner);

        Product targetProduct = TestUtil.createProduct();
        targetProduct.setAttribute(Product.Attributes.VIRT_ONLY, "true");
        targetProduct = this.createProduct(targetProduct, owner);

        this.createPool(owner, targetProduct, 1L, new Date(), new Date());
        this.createPool(owner, targetProduct, 1L, new Date(), new Date());

        List<Pool> results = poolCurator.listAvailableEntitlementPools(consumer, (String) null,
            (Collection<String>) null, null);

        assertEquals(0, results.size());
        // Make the consumer a guest and try again:
        consumer.setFact("virt.is_guest", "true");
        results = poolCurator.listAvailableEntitlementPools(consumer, (String) null,
            (Collection<String>) null, null);

        assertEquals(2, results.size());
    }

    @Test
    public void requiresHostPoolAttributeFiltering() {

        consumer = this.createConsumer(owner);

        Consumer host = createConsumer(owner);
        host.addGuestId(new GuestId("GUESTUUID", host));
        consumerCurator.update(host);

        Product targetProduct = this.createProduct(owner);

        Pool virtPool = this.createPool(owner, targetProduct, 1L, new Date(), new Date());
        virtPool.setAttribute(Product.Attributes.VIRT_ONLY, "true");
        virtPool.setAttribute(Pool.Attributes.REQUIRES_HOST, host.getUuid());
        poolCurator.merge(virtPool);

        // Another pool requiring a different host:
        Pool anotherVirtPool = this.createPool(owner, targetProduct, 1L, new Date(), new Date());
        anotherVirtPool.setAttribute(Product.Attributes.VIRT_ONLY, "true");
        anotherVirtPool.setAttribute(Pool.Attributes.REQUIRES_HOST, "SOMEOTHERUUID");
        poolCurator.merge(anotherVirtPool);
        poolCurator.flush();

        List<Pool> results = poolCurator.listAvailableEntitlementPools(consumer, (String) null,
            (Collection<String>) null, null);

        assertEquals(0, results.size());

        // Make the consumer a guest and try again:
        consumer.setFact("virt.is_guest", "true");
        consumer.setFact("virt.uuid", "GUESTUUID");
        consumerCurator.update(consumer);
        assertEquals(host.getUuid(), consumerCurator.getHost("GUESTUUID", owner.getId()).getUuid());
        results = poolCurator.listAvailableEntitlementPools(consumer, (String) null,
            (Collection<String>) null, null);

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

        Product targetProduct = this.createProduct(owner);

        Pool virtPool = this.createPool(owner, targetProduct, 1L, new Date(), new Date());
        virtPool.setAttribute(Product.Attributes.VIRT_ONLY, "true");
        virtPool.setAttribute(Pool.Attributes.REQUIRES_HOST, host.getUuid());
        poolCurator.merge(virtPool);
        poolCurator.flush();

        List<Pool> results = poolCurator.listAvailableEntitlementPools(c, (String) null, (Collection<String>)
            null, null);
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

        Product targetProduct = this.createProduct(owner);

        Pool virtPool = this.createPool(owner, targetProduct, 1L, new Date(), new Date());
        virtPool.setAttribute(Product.Attributes.VIRT_ONLY, "true");
        poolCurator.merge(virtPool);
        poolCurator.flush();

        List<Pool> results = poolCurator.listAvailableEntitlementPools(c, (String) null, (Collection<String>)
            null, null);
        assertEquals(1, results.size());
    }
}


