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

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.config.ConfigProperties;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.model.Subscription;
import org.candlepin.policy.js.pool.PoolHelper;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * HostedVirtLimitEntitlementRulesTest: Complex tests around the hosted virt limit
 * bonus pool functionality.
 */
public class HostedVirtLimitEntitlementRulesTest extends EntitlementRulesTestFixture {

    @Test
    public void hostedParentConsumerPostCreatesNoPool() {
        Pool pool = setupVirtLimitPool();
        Entitlement e = new Entitlement(pool, consumer, 1);

        PoolHelper postHelper = mock(PoolHelper.class);
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        enforcer.postEntitlement(consumer, postHelper, e);

        verify(postHelper, never()).createHostRestrictedPool(pool.getProductId(),
            pool, pool.getAttributeValue("virt_limit"));
    }

    @Test
    public void hostedVirtLimitAltersBonusPoolQuantity() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, "10");
        consumer.setType(new ConsumerType(ConsumerTypeEnum.CANDLEPIN));
        List<Pool> pools = poolRules.createPools(s);
        assertEquals(2, pools.size());

        Pool physicalPool = pools.get(0);
        physicalPool.setId("physical");
        Pool virtBonusPool = pools.get(1);
        virtBonusPool.setId("virt");

        assertEquals(new Long(10), physicalPool.getQuantity());
        assertEquals(0, physicalPool.getAttributes().size());

        // Quantity on bonus pool should be virt limit * sub quantity:
        assertEquals(new Long(100), virtBonusPool.getQuantity());
        assertEquals("true", virtBonusPool.getAttributeValue("virt_only"));
        assertEquals("10", virtBonusPool.getProductAttribute("virt_limit").getValue());

        Entitlement e = new Entitlement(physicalPool, consumer, 1);
        PoolHelper postHelper = new PoolHelper(poolManagerMock, productCache, e);
        List<Pool> poolList = new ArrayList<Pool>();
        poolList.add(virtBonusPool);
        when(poolManagerMock.lookupBySubscriptionId(eq(physicalPool.getSubscriptionId())))
            .thenReturn(poolList);

        enforcer.postEntitlement(consumer, postHelper, e);
        verify(poolManagerMock).updatePoolQuantity(eq(virtBonusPool), eq(-10L));

        enforcer.postUnbind(consumer, postHelper, e);
        verify(poolManagerMock).updatePoolQuantity(eq(virtBonusPool), eq(10L));
    }

    /*
     * Bonus pools in hosted mode for products with the host_limited attribute
     * are created during binding.
     */
    @Test
    public void hostedVirtLimitWithHostLimitedCreatesBonusPoolsOnBind() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, "unlimited");
        s.getProduct().setAttribute("host_limited", "true");
        List<Pool> pools = poolRules.createPools(s);
        assertEquals(2, pools.size());

        Pool physicalPool = pools.get(0);
        physicalPool.setId("physical");

        assertEquals(new Long(10), physicalPool.getQuantity());
        assertEquals(0, physicalPool.getAttributes().size());

        Entitlement e = new Entitlement(physicalPool, consumer, 1);
        PoolHelper postHelper = new PoolHelper(poolManagerMock, productCache, e);

        enforcer.postEntitlement(consumer, postHelper, e);
        verify(poolManagerMock).createPool(any(Pool.class));
    }

    @Test
    public void hostedVirtLimitUnlimitedBonusPoolQuantity() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, "unlimited");
        List<Pool> pools = poolRules.createPools(s);
        assertEquals(2, pools.size());

        Pool physicalPool = pools.get(0);
        physicalPool.setId("physical");
        Pool virtBonusPool = pools.get(1);
        virtBonusPool.setId("virt");

        assertEquals(new Long(10), physicalPool.getQuantity());
        assertEquals(0, physicalPool.getAttributes().size());

        // Quantity on bonus pool should be -1:
        assertEquals(new Long(-1), virtBonusPool.getQuantity());
        assertEquals("true", virtBonusPool.getAttributeValue("virt_only"));
        assertEquals("unlimited", virtBonusPool.getProductAttribute("virt_limit")
            .getValue());

        Entitlement e = new Entitlement(physicalPool, consumer, 1);
        PoolHelper postHelper = new PoolHelper(poolManagerMock, productCache, e);
        List<Pool> poolList = new ArrayList<Pool>();
        poolList.add(virtBonusPool);
        when(poolManagerMock.lookupBySubscriptionId(eq(physicalPool.getSubscriptionId())))
            .thenReturn(poolList);

        enforcer.postEntitlement(consumer, postHelper, e);
        verify(poolManagerMock, never()).updatePoolQuantity(any(Pool.class), anyInt());

        enforcer.postUnbind(consumer, postHelper, e);
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
        List<Pool> pools = poolRules.createPools(s);
        assertEquals(2, pools.size());

        Pool physicalPool = pools.get(0);
        physicalPool.setId("physical");
        Pool virtBonusPool = pools.get(1);
        virtBonusPool.setId("virt");

        assertEquals(new Long(10), physicalPool.getQuantity());
        assertEquals(0, physicalPool.getAttributes().size());

        // Quantity on bonus pool should be -1:
        assertEquals(new Long(-1), virtBonusPool.getQuantity());
        assertEquals("true", virtBonusPool.getAttributeValue("virt_only"));
        assertEquals("unlimited", virtBonusPool.getProductAttribute("virt_limit")
            .getValue());

        Entitlement e = new Entitlement(physicalPool, consumer, 1);
        PoolHelper postHelper = new PoolHelper(poolManagerMock, productCache, e);

        enforcer.postEntitlement(consumer, postHelper, e);
        verify(poolManagerMock, never()).createPool(any(Pool.class));
        verify(poolManagerMock, never()).updatePoolQuantity(any(Pool.class), anyInt());

        enforcer.postUnbind(consumer, postHelper, e);
        verify(poolManagerMock, never()).updatePoolQuantity(any(Pool.class), anyInt());
        verify(poolManagerMock, never()).setPoolQuantity(any(Pool.class), anyLong());
    }

    @Test
    public void exportAllPhysicalZeroBonusPoolQuantity() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        consumer.setType(new ConsumerType(ConsumerTypeEnum.CANDLEPIN));
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, "unlimited");
        List<Pool> pools = poolRules.createPools(s);
        assertEquals(2, pools.size());

        Pool physicalPool = pools.get(0);
        physicalPool.setId("physical");
        Pool virtBonusPool = pools.get(1);
        virtBonusPool.setId("virt");

        assertEquals(new Long(10), physicalPool.getQuantity());
        assertEquals(0, physicalPool.getAttributes().size());

        // Quantity on bonus pool should be -1:
        assertEquals(new Long(-1), virtBonusPool.getQuantity());
        assertEquals("true", virtBonusPool.getAttributeValue("virt_only"));
        assertEquals("unlimited", virtBonusPool.getProductAttribute("virt_limit")
            .getValue());

        Entitlement e = new Entitlement(physicalPool, consumer, 10);
        physicalPool.setConsumed(10L);
        physicalPool.setExported(10L);
        PoolHelper postHelper = new PoolHelper(poolManagerMock, productCache, e);
        List<Pool> poolList = new ArrayList<Pool>();
        poolList.add(virtBonusPool);
        when(poolManagerMock.lookupBySubscriptionId(eq(physicalPool.getSubscriptionId())))
            .thenReturn(poolList);

        enforcer.postEntitlement(consumer, postHelper, e);
        verify(poolManagerMock).setPoolQuantity(eq(virtBonusPool), eq(0L));
        virtBonusPool.setQuantity(0L);

        enforcer.postUnbind(consumer, postHelper, e);
        verify(poolManagerMock).setPoolQuantity(eq(virtBonusPool), eq(-1L));
    }

    @Test
    public void hostedVirtLimitDoesNotAlterQuantitiesForHostLimited() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        consumer.setType(new ConsumerType(ConsumerTypeEnum.CANDLEPIN));

        Pool virtBonusPool = setupVirtLimitPool();
        virtBonusPool.setQuantity(100L);
        virtBonusPool.setAttribute("host_limited", "true");
        virtBonusPool.setAttribute("virt_only", "true");
        virtBonusPool.setAttribute("virt_limit", "10");
        virtBonusPool.setAttribute("pool_derived", "true");

        Entitlement e = new Entitlement(virtBonusPool, consumer, 1);
        PoolHelper postHelper = new PoolHelper(poolManagerMock, productCache, e);
        List<Pool> poolList = new ArrayList<Pool>();
        poolList.add(virtBonusPool);
        when(poolManagerMock.lookupBySubscriptionId(eq(virtBonusPool.getSubscriptionId())))
            .thenReturn(poolList);

        enforcer.postEntitlement(consumer, postHelper, e);
        verify(poolManagerMock, never()).updatePoolQuantity(eq(virtBonusPool), eq(-10L));

        enforcer.postUnbind(consumer, postHelper, e);
        verify(poolManagerMock, never()).updatePoolQuantity(eq(virtBonusPool), eq(10L));
    }

}
