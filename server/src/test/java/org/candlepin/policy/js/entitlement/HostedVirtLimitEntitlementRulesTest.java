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
package org.candlepin.policy.js.entitlement;

import static org.hamcrest.Matchers.hasItems;
import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.dto.Subscription;
import org.candlepin.test.TestUtil;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * HostedVirtLimitEntitlementRulesTest: Complex tests around the hosted virt limit
 * bonus pool functionality.
 */
public class HostedVirtLimitEntitlementRulesTest extends EntitlementRulesTestFixture {

    @Test
    public void hostedParentConsumerPostCreatesNoPool() {
        Pool pool = setupVirtLimitPool();
        List<Pool> pools = new ArrayList<Pool>();
        pools.add(pool);

        Map<String, Entitlement> entitlements = new HashMap<String, Entitlement>();
        entitlements.put(pool.getId(), new Entitlement(pool, consumer, 1));

        Map<String, PoolQuantity> poolQuantityMap = new HashMap<String, PoolQuantity>();
        poolQuantityMap.put(pool.getId(), new PoolQuantity(pool, 1));

        PoolManager poolManager = mock(PoolManager.class);
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        enforcer.postEntitlement(poolManager, consumer, entitlements, null, false, poolQuantityMap);

        verify(poolManager, never()).createPools(any(List.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void hostedVirtLimitAltersBonusPoolQuantity() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, "10");
        consumer.setType(new ConsumerType(ConsumerTypeEnum.CANDLEPIN));
        Pool p = TestUtil.copyFromSub(s);
        when(poolManagerMock.isManaged(eq(p))).thenReturn(true);
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<Pool>());
        assertEquals(2, pools.size());

        Pool physicalPool = pools.get(0);
        physicalPool.setId("physical");
        Pool virtBonusPool = pools.get(1);
        virtBonusPool.setId("virt");

        assertEquals(new Long(10), physicalPool.getQuantity());
        assertEquals(0, physicalPool.getAttributes().size());

        // Quantity on bonus pool should be virt limit * sub quantity:
        assertEquals(new Long(100), virtBonusPool.getQuantity());
        assertEquals("true", virtBonusPool.getAttributeValue(Product.Attributes.VIRT_ONLY));
        assertEquals("10", virtBonusPool.getProduct().getAttributeValue(Product.Attributes.VIRT_LIMIT));

        Entitlement e = new Entitlement(physicalPool, consumer, 1);
        List<Pool> poolList = new ArrayList<Pool>();
        poolList.add(virtBonusPool);
        ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
        ArgumentCaptor<Owner> ownerCaptor = ArgumentCaptor.forClass(Owner.class);
        when(poolManagerMock.lookupBySubscriptionIds(ownerCaptor.capture(), captor.capture()))
            .thenReturn(poolList);
        when(poolManagerMock.lookupBySubscriptionId(eq(physicalPool.getOwner()),
            eq(physicalPool.getSubscriptionId())))
            .thenReturn(poolList);

        Map<String, Entitlement> entitlements = new HashMap<String, Entitlement>();
        entitlements.put(physicalPool.getId(), e);
        Map<String, PoolQuantity> poolQuantityMap = new HashMap<String, PoolQuantity>();
        poolQuantityMap.put(physicalPool.getId(), new PoolQuantity(physicalPool, 1));

        List<Pool> physicalPools = new ArrayList<Pool>();
        physicalPools.add(physicalPool);
        enforcer.postEntitlement(poolManagerMock, consumer, entitlements, null, false, poolQuantityMap);
        Set<String> subscriptionIds = captor.getValue();
        assertEquals(1, subscriptionIds.size());
        assertEquals("subId", subscriptionIds.iterator().next());
        verify(poolManagerMock).updatePoolQuantity(eq(virtBonusPool), eq(-10L));

        enforcer.postUnbind(consumer, poolManagerMock, e);
        verify(poolManagerMock).updatePoolQuantity(eq(virtBonusPool), eq(10L));
    }

    @Test
    public void batchHostedVirtLimitAltersBonusPoolQuantity() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);

        consumer.setType(new ConsumerType(ConsumerTypeEnum.CANDLEPIN));
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, "10");
        Pool p = TestUtil.copyFromSub(s);
        when(poolManagerMock.isManaged(eq(p))).thenReturn(true);
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<Pool>());
        assertEquals(2, pools.size());

        Pool physicalPool = pools.get(0);
        physicalPool.setId("physical");
        Pool virtBonusPool = pools.get(1);
        virtBonusPool.setId("virt");

        assertEquals(new Long(10), physicalPool.getQuantity());
        assertEquals(0, physicalPool.getAttributes().size());

        // Quantity on bonus pool should be virt limit * sub quantity:
        assertEquals(new Long(100), virtBonusPool.getQuantity());
        assertEquals("true", virtBonusPool.getAttributeValue(Product.Attributes.VIRT_ONLY));
        assertEquals("10", virtBonusPool.getProduct().getAttributeValue(Product.Attributes.VIRT_LIMIT));
        Entitlement e = new Entitlement(physicalPool, consumer, 1);

        Subscription s2 = createVirtLimitSub("virtLimitProduct2", 10, "10");
        s2.setId("subId2");
        Pool p2 = TestUtil.copyFromSub(s2);
        when(poolManagerMock.isManaged(eq(p2))).thenReturn(true);
        List<Pool> pools2 = poolRules.createAndEnrichPools(p2, new LinkedList<Pool>());
        assertEquals(2, pools.size());

        Pool physicalPool2 = pools2.get(0);
        physicalPool2.setId("physical2");
        Pool virtBonusPool2 = pools2.get(1);
        virtBonusPool2.setId("virt2");

        assertEquals(new Long(10), physicalPool2.getQuantity());
        assertEquals(0, physicalPool2.getAttributes().size());

        // Quantity on bonus pool should be virt limit * sub quantity:
        assertEquals(new Long(100), virtBonusPool2.getQuantity());
        assertEquals("true", virtBonusPool2.getAttributeValue(Product.Attributes.VIRT_ONLY));
        assertEquals("10", virtBonusPool2.getProduct().getAttributeValue(Product.Attributes.VIRT_LIMIT));
        Entitlement e2 = new Entitlement(physicalPool2, consumer, 1);

        List<Pool> poolList = new ArrayList<Pool>();
        poolList.add(virtBonusPool);

        List<Pool> poolList2 = new ArrayList<Pool>();
        poolList.add(virtBonusPool2);

        ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
        ArgumentCaptor<Owner> ownerCaptor = ArgumentCaptor.forClass(Owner.class);
        when(poolManagerMock.lookupBySubscriptionIds(ownerCaptor.capture(), captor.capture()))
            .thenReturn(poolList);
        when(poolManagerMock.lookupBySubscriptionId(eq(physicalPool.getOwner()),
            eq(physicalPool.getSubscriptionId())))
            .thenReturn(poolList);
        when(poolManagerMock.lookupBySubscriptionId(eq(physicalPool.getOwner()),
            eq(physicalPool2.getSubscriptionId())))
            .thenReturn(poolList2);

        Map<String, Entitlement> entitlements = new HashMap<String, Entitlement>();
        entitlements.put(physicalPool.getId(), e);
        entitlements.put(physicalPool2.getId(), e2);
        Map<String, PoolQuantity> poolQuantityMap = new HashMap<String, PoolQuantity>();
        poolQuantityMap.put(physicalPool.getId(), new PoolQuantity(physicalPool, 1));
        poolQuantityMap.put(physicalPool2.getId(), new PoolQuantity(physicalPool2, 1));

        enforcer.postEntitlement(poolManagerMock, consumer, entitlements, null, false, poolQuantityMap);
        @SuppressWarnings("unchecked")
        Set<String> subscriptionIds = captor.getValue();
        assertEquals(2, subscriptionIds.size());
        assertThat(subscriptionIds, hasItems("subId", "subId2"));
        verify(poolManagerMock).updatePoolQuantity(eq(virtBonusPool), eq(-10L));
        verify(poolManagerMock).updatePoolQuantity(eq(virtBonusPool2), eq(-10L));

        enforcer.postUnbind(consumer, poolManagerMock, e);
        verify(poolManagerMock).updatePoolQuantity(eq(virtBonusPool), eq(10L));
        enforcer.postUnbind(consumer, poolManagerMock, e2);
        verify(poolManagerMock).updatePoolQuantity(eq(virtBonusPool2), eq(10L));
    }

    /*
     * Bonus pools in hosted mode for products with the host_limited attribute
     * are created during binding.
     */
    @Test
    public void hostedVirtLimitWithHostLimitedCreatesBonusPoolsOnBind() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, "unlimited");
        s.getProduct().setAttribute(Product.Attributes.HOST_LIMITED, "true");
        Pool p = TestUtil.copyFromSub(s);
        when(poolManagerMock.isManaged(eq(p))).thenReturn(true);
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<Pool>());
        assertEquals(2, pools.size());

        Pool physicalPool = pools.get(0);
        physicalPool.setId("physical");

        assertEquals(new Long(10), physicalPool.getQuantity());
        assertEquals(0, physicalPool.getAttributes().size());

        Entitlement e = new Entitlement(physicalPool, consumer, 1);
        Map<String, Entitlement> entitlements = new HashMap<String, Entitlement>();
        entitlements.put(physicalPool.getId(), e);
        Map<String, PoolQuantity> poolQuantityMap = new HashMap<String, PoolQuantity>();
        poolQuantityMap.put(physicalPool.getId(), new PoolQuantity(physicalPool, 1));
        enforcer.postEntitlement(poolManagerMock, consumer, entitlements, null, false, poolQuantityMap);
        verify(poolManagerMock).createPools(any(List.class));
    }

    @Test
    public void hostedVirtLimitUnlimitedBonusPoolQuantity() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, "unlimited");
        Pool p = TestUtil.copyFromSub(s);
        when(poolManagerMock.isManaged(eq(p))).thenReturn(true);
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<Pool>());
        assertEquals(2, pools.size());

        Pool physicalPool = pools.get(0);
        physicalPool.setId("physical");
        Pool virtBonusPool = pools.get(1);
        virtBonusPool.setId("virt");

        assertEquals(new Long(10), physicalPool.getQuantity());
        assertEquals(0, physicalPool.getAttributes().size());

        // Quantity on bonus pool should be -1:
        assertEquals(new Long(-1), virtBonusPool.getQuantity());
        assertEquals("true", virtBonusPool.getAttributeValue(Product.Attributes.VIRT_ONLY));
        assertEquals("unlimited",
            virtBonusPool.getProduct().getAttributeValue(Product.Attributes.VIRT_LIMIT));

        Entitlement e = new Entitlement(physicalPool, consumer, 1);
        List<Pool> poolList = new ArrayList<Pool>();
        poolList.add(virtBonusPool);
        when(poolManagerMock.lookupBySubscriptionId(eq(physicalPool.getOwner()),
            eq(physicalPool.getSubscriptionId())))
            .thenReturn(poolList);

        Map<String, Entitlement> entitlements = new HashMap<String, Entitlement>();
        entitlements.put(physicalPool.getId(), e);
        Map<String, PoolQuantity> poolQuantityMap = new HashMap<String, PoolQuantity>();
        poolQuantityMap.put(physicalPool.getId(), new PoolQuantity(physicalPool, 1));
        enforcer.postEntitlement(poolManagerMock, consumer, entitlements, null, false, poolQuantityMap);
        verify(poolManagerMock, never()).updatePoolQuantity(any(Pool.class), anyInt());

        enforcer.postUnbind(consumer, poolManagerMock, e);
        verify(poolManagerMock, never()).updatePoolQuantity(any(Pool.class), anyInt());
    }

    /*
     * Bonus pools should not be created when we are in a hosted scenario without
     * distributor binds.
     */
    @Test
    public void noBonusPoolsForHostedNonDistributorBinds() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, "unlimited");
        Pool p = TestUtil.copyFromSub(s);
        when(poolManagerMock.isManaged(eq(p))).thenReturn(true);
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<Pool>());
        assertEquals(2, pools.size());

        Pool physicalPool = pools.get(0);
        physicalPool.setId("physical");
        Pool virtBonusPool = pools.get(1);
        virtBonusPool.setId("virt");

        assertEquals(new Long(10), physicalPool.getQuantity());
        assertEquals(0, physicalPool.getAttributes().size());

        // Quantity on bonus pool should be -1:
        assertEquals(new Long(-1), virtBonusPool.getQuantity());
        assertEquals("true", virtBonusPool.getAttributeValue(Product.Attributes.VIRT_ONLY));
        assertEquals("unlimited",
            virtBonusPool.getProduct().getAttributeValue(Product.Attributes.VIRT_LIMIT));

        Entitlement e = new Entitlement(physicalPool, consumer, 1);

        Map<String, Entitlement> entitlements = new HashMap<String, Entitlement>();
        entitlements.put(physicalPool.getId(), e);
        Map<String, PoolQuantity> poolQuantityMap = new HashMap<String, PoolQuantity>();
        poolQuantityMap.put(physicalPool.getId(), new PoolQuantity(physicalPool, 1));
        enforcer.postEntitlement(poolManagerMock, consumer, entitlements, null, false, poolQuantityMap);
        verify(poolManagerMock, never()).createPool(any(Pool.class));
        verify(poolManagerMock, never()).updatePoolQuantity(any(Pool.class), anyInt());

        enforcer.postUnbind(consumer, poolManagerMock, e);
        verify(poolManagerMock, never()).updatePoolQuantity(any(Pool.class), anyInt());
        verify(poolManagerMock, never()).setPoolQuantity(any(Pool.class), anyLong());
    }

    @Test
    public void exportAllPhysicalZeroBonusPoolQuantity() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        consumer.setType(new ConsumerType(ConsumerTypeEnum.CANDLEPIN));
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, "unlimited");
        Pool p = TestUtil.copyFromSub(s);
        when(poolManagerMock.isManaged(eq(p))).thenReturn(true);
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<Pool>());
        assertEquals(2, pools.size());

        Pool physicalPool = pools.get(0);
        physicalPool.setId("physical");
        Pool virtBonusPool = pools.get(1);
        virtBonusPool.setId("virt");

        assertEquals(new Long(10), physicalPool.getQuantity());
        assertEquals(0, physicalPool.getAttributes().size());

        // Quantity on bonus pool should be -1:
        assertEquals(new Long(-1), virtBonusPool.getQuantity());
        assertEquals("true", virtBonusPool.getAttributeValue(Product.Attributes.VIRT_ONLY));
        assertEquals("unlimited",
            virtBonusPool.getProduct().getAttributeValue(Product.Attributes.VIRT_LIMIT));

        Entitlement e = new Entitlement(physicalPool, consumer, 10);
        physicalPool.setConsumed(10L);
        physicalPool.setExported(10L);
        List<Pool> poolList = new ArrayList<Pool>();
        poolList.add(virtBonusPool);
        ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
        ArgumentCaptor<Owner> ownerCaptor = ArgumentCaptor.forClass(Owner.class);
        when(poolManagerMock.lookupBySubscriptionIds(ownerCaptor.capture(), captor.capture()))
            .thenReturn(poolList);
        when(poolManagerMock.lookupBySubscriptionId(eq(physicalPool.getOwner()), eq("subId")))
            .thenReturn(poolList);
        Map<String, Entitlement> entitlements = new HashMap<String, Entitlement>();
        entitlements.put(physicalPool.getId(), e);
        Map<String, PoolQuantity> poolQuantityMap = new HashMap<String, PoolQuantity>();
        poolQuantityMap.put(physicalPool.getId(), new PoolQuantity(physicalPool, 10));
        enforcer.postEntitlement(poolManagerMock, consumer, entitlements, null, false, poolQuantityMap);

        @SuppressWarnings("unchecked")
        Set<String> subscriptionIds = captor.getValue();
        assertEquals(1, subscriptionIds.size());
        assertEquals("subId", subscriptionIds.iterator().next());

        verify(poolManagerMock).setPoolQuantity(eq(virtBonusPool), eq(0L));
        virtBonusPool.setQuantity(0L);

        enforcer.postUnbind(consumer, poolManagerMock, e);
        verify(poolManagerMock).setPoolQuantity(eq(virtBonusPool), eq(-1L));
    }

    @Test
    public void hostedVirtLimitDoesNotAlterQuantitiesForHostLimited() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        consumer.setType(new ConsumerType(ConsumerTypeEnum.CANDLEPIN));

        Pool virtBonusPool = setupVirtLimitPool();
        virtBonusPool.setQuantity(100L);
        virtBonusPool.setAttribute(Product.Attributes.HOST_LIMITED, "true");
        virtBonusPool.setAttribute(Product.Attributes.VIRT_ONLY, "true");
        virtBonusPool.setAttribute(Product.Attributes.VIRT_LIMIT, "10");
        virtBonusPool.setAttribute(Pool.Attributes.DERIVED_POOL, "true");

        Entitlement e = new Entitlement(virtBonusPool, consumer, 1);
        List<Pool> poolList = new ArrayList<Pool>();
        poolList.add(virtBonusPool);
        when(poolManagerMock.lookupBySubscriptionId(eq(virtBonusPool.getOwner()),
            eq(virtBonusPool.getSubscriptionId())))
            .thenReturn(poolList);

        Map<String, Entitlement> entitlements = new HashMap<String, Entitlement>();
        entitlements.put(virtBonusPool.getId(), e);
        Map<String, PoolQuantity> poolQuantityMap = new HashMap<String, PoolQuantity>();
        poolQuantityMap.put(virtBonusPool.getId(), new PoolQuantity(virtBonusPool, 1));
        enforcer.postEntitlement(poolManagerMock, consumer, entitlements, null, false, poolQuantityMap);
        verify(poolManagerMock, never()).updatePoolQuantity(eq(virtBonusPool), eq(-10L));

        enforcer.postUnbind(consumer, poolManagerMock, e);
        verify(poolManagerMock, never()).updatePoolQuantity(eq(virtBonusPool), eq(10L));
    }

}
