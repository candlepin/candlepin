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
package org.candlepin.policy.entitlement;

import org.candlepin.bind.PoolOperationCallback;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.model.dto.Subscription;
import org.candlepin.test.TestUtil;

import org.hamcrest.Matchers;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HostedVirtLimitEntitlementRulesTest: Complex tests around the hosted virt limit
 * bonus pool functionality.
 */
public class HostedVirtLimitEntitlementRulesTest extends EntitlementRulesTestFixture {

    @Test
    public void hostedParentConsumerPostCreatesNoPool() {
        Pool pool = setupVirtLimitPool();
        List<Pool> pools = new ArrayList<>();
        pools.add(pool);

        Map<String, Entitlement> entitlements = new HashMap<>();
        entitlements.put(pool.getId(), new Entitlement(pool, consumer, owner, 1));

        Map<String, PoolQuantity> poolQuantityMap = new HashMap<>();
        poolQuantityMap.put(pool.getId(), new PoolQuantity(pool, 1));

        PoolManager poolManager = mock(PoolManager.class);
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        enforcer.postEntitlement(poolManager, consumer, owner, entitlements, null, false, poolQuantityMap);

        verify(poolManager, never()).createPools(any(List.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void hostedVirtLimitAltersBonusPoolQuantity() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, "10");
        ConsumerType ctype = this.mockConsumerType(new ConsumerType(ConsumerTypeEnum.CANDLEPIN));
        consumer.setType(ctype);
        Pool p = TestUtil.copyFromSub(s);
        when(poolManagerMock.isManaged(eq(p))).thenReturn(true);
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<>());
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

        Entitlement e = new Entitlement(physicalPool, consumer, owner, 1);
        List<Pool> poolList = new ArrayList<>();
        poolList.add(virtBonusPool);
        ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
        when(poolManagerMock.getBySubscriptionIds(anyString(), captor.capture()))
            .thenReturn(poolList);
        when(poolManagerMock.getBySubscriptionId(eq(physicalPool.getOwner()),
            eq(physicalPool.getSubscriptionId())))
            .thenReturn(poolList);

        Map<String, Entitlement> entitlements = new HashMap<>();
        entitlements.put(physicalPool.getId(), e);
        Map<String, PoolQuantity> poolQuantityMap = new HashMap<>();
        poolQuantityMap.put(physicalPool.getId(), new PoolQuantity(physicalPool, 1));

        List<Pool> physicalPools = new ArrayList<>();
        physicalPools.add(physicalPool);
        PoolOperationCallback poolOperationCallback = enforcer.postEntitlement(poolManagerMock,
            consumer, owner, entitlements, null, false, poolQuantityMap);
        Set<String> subscriptionIds = captor.getValue();
        assertEquals(1, subscriptionIds.size());
        assertEquals("subId", subscriptionIds.iterator().next());


        assertEquals(1, poolOperationCallback.getPoolUpdates().size());
        Map.Entry<Pool, Long> poolUpdate =
            poolOperationCallback.getPoolUpdates().entrySet().iterator().next();
        assertEquals(virtBonusPool, poolUpdate.getKey());
        assertEquals(90L, poolUpdate.getValue().longValue());

        enforcer.postUnbind(poolManagerMock, e);
        verify(poolManagerMock).setPoolQuantity(eq(virtBonusPool), eq(110L));
    }

    @Test
    public void batchHostedVirtLimitAltersBonusPoolQuantity() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);

        ConsumerType ctype = this.mockConsumerType(new ConsumerType(ConsumerTypeEnum.CANDLEPIN));
        consumer.setType(ctype);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, "10");
        Pool p = TestUtil.copyFromSub(s);
        when(poolManagerMock.isManaged(eq(p))).thenReturn(true);
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<>());
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
        Entitlement e = new Entitlement(physicalPool, consumer, owner, 1);

        Subscription s2 = createVirtLimitSub("virtLimitProduct2", 10, "10");
        s2.setId("subId2");
        Pool p2 = TestUtil.copyFromSub(s2);
        when(poolManagerMock.isManaged(eq(p2))).thenReturn(true);
        List<Pool> pools2 = poolRules.createAndEnrichPools(p2, new LinkedList<>());
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
        Entitlement e2 = new Entitlement(physicalPool2, consumer, owner, 1);

        List<Pool> poolList = new ArrayList<>();
        poolList.add(virtBonusPool);

        List<Pool> poolList2 = new ArrayList<>();
        poolList.add(virtBonusPool2);

        ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
        when(poolManagerMock.getBySubscriptionIds(anyString(), captor.capture()))
            .thenReturn(poolList);
        when(poolManagerMock.getBySubscriptionId(eq(physicalPool.getOwner()),
            eq(physicalPool.getSubscriptionId())))
            .thenReturn(poolList);
        when(poolManagerMock.getBySubscriptionId(eq(physicalPool.getOwner()),
            eq(physicalPool2.getSubscriptionId())))
            .thenReturn(poolList2);

        Map<String, Entitlement> entitlements = new HashMap<>();
        entitlements.put(physicalPool.getId(), e);
        entitlements.put(physicalPool2.getId(), e2);
        Map<String, PoolQuantity> poolQuantityMap = new HashMap<>();
        poolQuantityMap.put(physicalPool.getId(), new PoolQuantity(physicalPool, 1));
        poolQuantityMap.put(physicalPool2.getId(), new PoolQuantity(physicalPool2, 1));

        PoolOperationCallback poolOperationCallback = enforcer.postEntitlement(poolManagerMock,
            consumer, owner, entitlements, null, false, poolQuantityMap);
        @SuppressWarnings("unchecked")
        Set<String> subscriptionIds = captor.getValue();
        assertEquals(2, subscriptionIds.size());
        assertThat(subscriptionIds, Matchers.hasItems("subId", "subId2"));
        assertEquals(2, poolOperationCallback.getPoolUpdates().size());

        Map<Pool, Long> poolUpdate = poolOperationCallback.getPoolUpdates();
        assertEquals((Long) 90L, (Long) poolUpdate.get(virtBonusPool));
        assertEquals((Long) 90L, (Long) poolUpdate.get(virtBonusPool2));

        enforcer.postUnbind(poolManagerMock, e);
        verify(poolManagerMock).setPoolQuantity(eq(virtBonusPool), eq(110L));
        enforcer.postUnbind(poolManagerMock, e2);
        verify(poolManagerMock).setPoolQuantity(eq(virtBonusPool2), eq(110L));
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
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<>());
        assertEquals(2, pools.size());

        Pool physicalPool = pools.get(0);
        physicalPool.setId("physical");

        assertEquals(new Long(10), physicalPool.getQuantity());
        assertEquals(0, physicalPool.getAttributes().size());

        Entitlement e = new Entitlement(physicalPool, consumer, owner, 1);
        Map<String, Entitlement> entitlements = new HashMap<>();
        entitlements.put(physicalPool.getId(), e);
        Map<String, PoolQuantity> poolQuantityMap = new HashMap<>();
        poolQuantityMap.put(physicalPool.getId(), new PoolQuantity(physicalPool, 1));
        PoolOperationCallback poolOperationCallback = enforcer.postEntitlement(poolManagerMock,
            consumer, owner, entitlements, null, false, poolQuantityMap);
        assertEquals(1, poolOperationCallback.getPoolCreates().size());
    }

    @Test
    public void hostedVirtLimitUnlimitedBonusPoolQuantity() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, "unlimited");
        Pool p = TestUtil.copyFromSub(s);
        when(poolManagerMock.isManaged(eq(p))).thenReturn(true);
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<>());
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

        Entitlement e = new Entitlement(physicalPool, consumer, owner, 1);
        List<Pool> poolList = new ArrayList<>();
        poolList.add(virtBonusPool);
        when(poolManagerMock.getBySubscriptionId(eq(physicalPool.getOwner()),
            eq(physicalPool.getSubscriptionId())))
            .thenReturn(poolList);

        Map<String, Entitlement> entitlements = new HashMap<>();
        entitlements.put(physicalPool.getId(), e);
        Map<String, PoolQuantity> poolQuantityMap = new HashMap<>();
        poolQuantityMap.put(physicalPool.getId(), new PoolQuantity(physicalPool, 1));
        enforcer.postEntitlement(poolManagerMock, consumer, owner, entitlements, null, false,
            poolQuantityMap);
        verify(poolManagerMock, never()).setPoolQuantity(any(Pool.class), anyInt());

        enforcer.postUnbind(poolManagerMock, e);
        verify(poolManagerMock, never()).setPoolQuantity(any(Pool.class), anyInt());
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
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<>());
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

        Entitlement e = new Entitlement(physicalPool, consumer, owner, 1);

        Map<String, Entitlement> entitlements = new HashMap<>();
        entitlements.put(physicalPool.getId(), e);
        Map<String, PoolQuantity> poolQuantityMap = new HashMap<>();
        poolQuantityMap.put(physicalPool.getId(), new PoolQuantity(physicalPool, 1));
        enforcer.postEntitlement(poolManagerMock, consumer, owner, entitlements, null, false,
            poolQuantityMap);
        verify(poolManagerMock, never()).createPool(any(Pool.class));
        verify(poolManagerMock, never()).setPoolQuantity(any(Pool.class), anyInt());

        enforcer.postUnbind(poolManagerMock, e);
        verify(poolManagerMock, never()).setPoolQuantity(any(Pool.class), anyInt());
        verify(poolManagerMock, never()).setPoolQuantity(any(Pool.class), anyLong());
    }

    @Test
    public void exportAllPhysicalZeroBonusPoolQuantity() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        ConsumerType ctype = this.mockConsumerType(new ConsumerType(ConsumerTypeEnum.CANDLEPIN));
        consumer.setType(ctype);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, "unlimited");
        Pool p = TestUtil.copyFromSub(s);
        when(poolManagerMock.isManaged(eq(p))).thenReturn(true);
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<>());
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

        Entitlement e = new Entitlement(physicalPool, consumer, owner, 10);
        physicalPool.setConsumed(10L);
        physicalPool.setExported(0L);
        List<Pool> poolList = new ArrayList<>();
        poolList.add(virtBonusPool);
        ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
        when(poolManagerMock.getBySubscriptionIds(anyString(), captor.capture()))
            .thenReturn(poolList);
        when(poolManagerMock.getBySubscriptionId(eq(physicalPool.getOwner()), eq("subId")))
            .thenReturn(poolList);
        Map<String, Entitlement> entitlements = new HashMap<>();
        entitlements.put(physicalPool.getId(), e);
        Map<String, PoolQuantity> poolQuantityMap = new HashMap<>();
        poolQuantityMap.put(physicalPool.getId(), new PoolQuantity(physicalPool, 10));
        PoolOperationCallback poolOperationCallback = enforcer.postEntitlement(poolManagerMock,
            consumer, owner, entitlements, null, false, poolQuantityMap);

        @SuppressWarnings("unchecked")
        Set<String> subscriptionIds = captor.getValue();
        assertEquals(1, subscriptionIds.size());
        assertEquals("subId", subscriptionIds.iterator().next());

        assertEquals(1, poolOperationCallback.getPoolUpdates().size());
        Map.Entry<Pool, Long> poolUpdate =
            poolOperationCallback.getPoolUpdates().entrySet().iterator().next();
        assertEquals(virtBonusPool, poolUpdate.getKey());
        assertEquals((Long) 0L, (Long) poolUpdate.getValue().longValue());

        virtBonusPool.setQuantity(0L);

        enforcer.postUnbind(poolManagerMock, e);
        verify(poolManagerMock).setPoolQuantity(eq(virtBonusPool), eq(-1L));
    }

    @Test
    public void hostedVirtLimitDoesNotAlterQuantitiesForHostLimited() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        ConsumerType ctype = this.mockConsumerType(new ConsumerType(ConsumerTypeEnum.CANDLEPIN));
        consumer.setType(ctype);

        Pool virtBonusPool = setupVirtLimitPool();
        virtBonusPool.setQuantity(100L);
        virtBonusPool.setAttribute(Product.Attributes.HOST_LIMITED, "true");
        virtBonusPool.setAttribute(Product.Attributes.VIRT_ONLY, "true");
        virtBonusPool.setAttribute(Product.Attributes.VIRT_LIMIT, "10");
        virtBonusPool.setAttribute(Pool.Attributes.DERIVED_POOL, "true");

        Entitlement e = new Entitlement(virtBonusPool, consumer, owner, 1);
        List<Pool> poolList = new ArrayList<>();
        poolList.add(virtBonusPool);
        when(poolManagerMock.getBySubscriptionId(eq(virtBonusPool.getOwner()),
            eq(virtBonusPool.getSubscriptionId())))
            .thenReturn(poolList);

        Map<String, Entitlement> entitlements = new HashMap<>();
        entitlements.put(virtBonusPool.getId(), e);
        Map<String, PoolQuantity> poolQuantityMap = new HashMap<>();
        poolQuantityMap.put(virtBonusPool.getId(), new PoolQuantity(virtBonusPool, 1));
        enforcer.postEntitlement(poolManagerMock, consumer, owner, entitlements, null, false,
            poolQuantityMap);
        verify(poolManagerMock, never()).setPoolQuantity(eq(virtBonusPool), eq(-10L));

        enforcer.postUnbind(poolManagerMock, e);
        verify(poolManagerMock, never()).setPoolQuantity(eq(virtBonusPool), eq(10L));
    }

}
