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

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;

import org.candlepin.model.Consumer;
import org.candlepin.model.GuestId;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

/*
 * Test the Javascript pool criteria. This works because we configure an enforcer for the
 * unit tests that by default, will always return success. As such if we see pools getting
 * filtered out below, we know it was because of the hibernate query mechanism.
 */
@RunWith(MockitoJUnitRunner.class)
public class PoolCriteriaTest extends DatabaseTestFixture {

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
            null, null, false, true);

        assertEquals(1, results.size());
        assertEquals(physicalPool.getId(), results.get(0).getId());

        // Make the consumer a guest and try again:
        consumer.setFact("virt.is_guest", "true");
        results = poolCurator.listAvailableEntitlementPools(consumer, null,
            null, null, false, true);

        assertEquals(2, results.size());

    }

    // Virt only can also be on the product:
    @Test
    public void virtOnlyProductAttributeFiltering() {

        consumer = this.createConsumer(owner);
        Product targetProduct = TestUtil.createProduct();
        targetProduct.setAttribute("virt_only", "true");
        this.productCurator.create(targetProduct);

        this.createPoolAndSub(owner, targetProduct, 1L, new Date(),
            new Date());

        List<Pool> results = poolCurator.listAvailableEntitlementPools(consumer, null,
            null, null, false, true);

        assertEquals(0, results.size());
        // Make the consumer a guest and try again:
        consumer.setFact("virt.is_guest", "true");
        results = poolCurator.listAvailableEntitlementPools(consumer, null,
            null, null, false, true);

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
        virtPool.setAttribute("requires_host", "");
        poolCurator.merge(virtPool);

        List<Pool> results = poolCurator.listAvailableEntitlementPools(consumer, null,
            null, null, false, true);

        assertEquals(0, results.size());

        // Make the consumer a guest and try again:
        consumer.setFact("virt.is_guest", "true");
        consumer.setFact("virt.uuid", "GUESTUUID");
        consumerCurator.update(consumer);
        assertEquals(host.getUuid(), consumerCurator.getHost("GUESTUUID").getUuid());
        results = poolCurator.listAvailableEntitlementPools(consumer, null,
            null, null, false, true);

        assertEquals(1, results.size());
    }



//
//    @Test
//    public void createCriteriaNoMatch() {
//
//        consumer = this.createConsumer(owner);
//        Product targetProduct = TestUtil.createProduct();
//        this.productCurator.create(targetProduct);
//        Pool targetPool = this.createPoolAndSub(owner, targetProduct, 1L,
//            new Date(), new Date());
//        //Pool targetPool = TestUtil.createPool(targetProduct);
//        List<Pool> pools = new LinkedList<Pool>();
//        pools.add(targetPool);
//
//        Session sess = this.poolCurator.currentSession();
//        Criteria testCritReal = sess.createCriteria(Pool.class);
//        Owner notTheOwner = this.createOwner();
//        when(consumerMock.getOwner()).thenReturn(notTheOwner);
//        Criteria testCrit = poolCriteria.availableEntitlementCriteria(consumer);
//
//        pools = testCrit.list();
//
//        assertEquals(pools.size(), 1);
//    }
}

//    @Test
//    public void poolFilterConsumerIsGuest() {
//        consumer = TestUtil.createConsumer(owner);
//        consumer.addGuestId(new GuestId());
//        consumer.setFact("virt.is_guest", "true");
//
//        Product targetProduct = TestUtil.createProduct();
//        Pool targetPool = TestUtil.createPool(targetProduct);
//        List<Pool> pools = new LinkedList<Pool>();
//        pools.add(targetPool);
//
//        List<Pool> newPools = poolFilter.filterPools(consumer, pools);
//        assertEquals(pools, newPools);
//    }
//
//    @Test
//    public void poolFilterConsumerIsNotGuestVirtOnlyPools() {
//        consumer = TestUtil.createConsumer(owner);
//        consumer.addGuestId(new GuestId());
//
//        Product targetProduct = TestUtil.createProduct();
//        Pool targetPool = TestUtil.createPool(targetProduct);
//        targetPool.setAttribute("virt_only", "true");
//        List<Pool> pools = new LinkedList<Pool>();
//        pools.add(targetPool);
//
//        List<Pool> newPools = poolFilter.filterPools(consumer, pools);
//        assertTrue(newPools.isEmpty());
//    }
//
//    @Test
//    public void poolFilterConsumerIsGuestVirtOnlyPools() {
//        consumer = TestUtil.createConsumer(owner);
//        consumer.addGuestId(new GuestId());
//        consumer.setFact("virt.is_guest", "true");
//
//        Product targetProduct = TestUtil.createProduct();
//        Pool targetPool = TestUtil.createPool(targetProduct);
//        targetPool.setAttribute("virt_only", "true");
//        List<Pool> pools = new LinkedList<Pool>();
//        pools.add(targetPool);
//
//        List<Pool> newPools = poolFilter.filterPools(consumer, pools);
//        assertEquals(pools, newPools);
//    }
//
//    @Test
//    public void poolFilterConsumerIsGuestVirtOnlyPoolsMatch() {
//        consumer = TestUtil.createConsumer(owner);
//        consumer.setFact("virt.is_guest", "true");
//        String guestId = "1234567";
//        consumer.addGuestId(new GuestId(guestId));
//        when(consumerCuratorMock.getHost(guestId)).thenReturn(consumer);
//
//        poolFilter = new JsPoolCriteria(this.provider.get(), configMock, consumerCuratorMock);
//
//        Product targetProduct = TestUtil.createProduct();
//        Pool targetPool = TestUtil.createPool(targetProduct);
//        targetPool.setAttribute("virt_only", "true");
//        targetPool.setAttribute("requires_host", guestId);
//        List<Pool> pools = new LinkedList<Pool>();
//        pools.add(targetPool);
//
//        List<Pool> newPools = poolFilter.filterPools(consumer, pools);
//        assertEquals(pools, newPools);
//    }
//
//    @Test
//    public void poolFilterConsumerIsGuestVirtOnlyPoolsNoMatch() {
//        consumer = TestUtil.createConsumer(owner);
//        consumer.setFact("virt.is_guest", "true");
//        String guestId = "1234567";
//        String notAValidGuestId = "thisisnotaguestid";
//        consumer.addGuestId(new GuestId(guestId));
//
//        when(consumerCuratorMock.getHost(notAValidGuestId)).thenReturn(null);
//        poolFilter = new JsPoolCriteria(this.provider.get(), configMock, consumerCuratorMock);
//
//        Product targetProduct = TestUtil.createProduct();
//        Pool targetPool = TestUtil.createPool(targetProduct);
//        targetPool.setAttribute("virt_only", "true");
//        targetPool.setAttribute("requires_host", notAValidGuestId);
//        List<Pool> pools = new LinkedList<Pool>();
//        pools.add(targetPool);
//
//        List<Pool> newPools = poolFilter.filterPools(consumer, pools);
//        assertTrue(newPools.isEmpty());
//    }
//
//}
