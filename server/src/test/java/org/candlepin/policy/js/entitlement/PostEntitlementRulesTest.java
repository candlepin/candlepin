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
import org.candlepin.policy.js.pool.PoolHelper;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * PostEntitlementRulesTest: Tests for post-entitlement rules, as well as the post-unbind
 * rules which tend to clean up after them.
 *
 * These tests only cover standalone/universal situations. See hosted specific test
 * suites for behaviour which is specific to hosted.
 */
public class PostEntitlementRulesTest extends EntitlementRulesTestFixture {

    @Test
    public void virtLimitSubPool() {
        Pool pool = setupVirtLimitPool();
        pool.setAttribute("virt_limit", "10");
        Entitlement e = new Entitlement(pool, consumer, 5);

        Map<String, Entitlement> entitlements = new HashMap<String, Entitlement>();
        entitlements.put(pool.getId(), e);
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(true);
        // Pool quantity should be virt_limit:
        Class<List<Pool>> listClass = (Class<List<Pool>>) (Class) ArrayList.class;
        ArgumentCaptor<List<Pool>> poolsArg = ArgumentCaptor.forClass(listClass);
        when(poolManagerMock.createPools(poolsArg.capture())).thenReturn(new LinkedList<Pool>());
        enforcer.postEntitlement(poolManagerMock, consumer, entitlements, null);

        List<Pool> pools = poolsArg.getValue();
        assertEquals(1, pools.size());
        assertEquals(10L, pools.get(0).getQuantity().longValue());
    }

    @Test
    public void unlimitedVirtLimitSubPool() {
        Pool pool = setupVirtLimitPool();
        pool.setAttribute("virt_limit", "unlimited");
        Entitlement e = new Entitlement(pool, consumer, 5);

        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(true);
        Map<String, Entitlement> entitlements = new HashMap<String, Entitlement>();
        entitlements.put(pool.getId(), e);
        Class<List<Pool>> listClass = (Class<List<Pool>>) (Class) ArrayList.class;
        ArgumentCaptor<List<Pool>> poolsArg = ArgumentCaptor.forClass(listClass);
        when(poolManagerMock.createPools(poolsArg.capture())).thenReturn(new LinkedList<Pool>());
        enforcer.postEntitlement(poolManagerMock, consumer, entitlements, null);

        // Pool quantity should be virt_limit:
        List<Pool> pools = poolsArg.getValue();
        assertEquals(1, pools.size());
        assertEquals(-1L, pools.get(0).getQuantity().longValue());
    }

    // Sub-pools should not be created when distributors bind:
    @Test
    public void noSubPoolsForDistributorBinds() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(true);
        consumer.setType(new ConsumerType(ConsumerTypeEnum.CANDLEPIN));
        Pool pool = setupVirtLimitPool();
        Entitlement e = new Entitlement(pool, consumer, 1);


        Map<String, Entitlement> entitlements = new HashMap<String, Entitlement>();
        entitlements.put(pool.getId(), e);
        enforcer.postEntitlement(poolManagerMock, consumer, entitlements, null);

        verify(poolManagerMock, never()).createPools(any(List.class));
        verify(poolManagerMock, never()).updatePoolQuantity(any(Pool.class), anyInt());

        enforcer.postUnbind(consumer, poolManagerMock, e);
        verify(poolManagerMock, never()).updatePoolQuantity(any(Pool.class), anyInt());
        verify(poolManagerMock, never()).setPoolQuantity(any(Pool.class), anyLong());
    }

    // Sub-pools should not be created when guests bind:
    @Test
    public void noSubPoolsForGuestBinds() {
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(true);
        Pool pool = setupVirtLimitPool();
        consumer.setFact("virt.is_guest", "true");
        Entitlement e = new Entitlement(pool, consumer, 1);

        Map<String, Entitlement> entitlements = new HashMap<String, Entitlement>();
        entitlements.put(pool.getId(), e);
        enforcer.postEntitlement(poolManagerMock, consumer, entitlements, null);
        verify(poolManagerMock, never()).createPools(any(List.class));
        verify(poolManagerMock, never()).updatePoolQuantity(any(Pool.class), anyInt());

        enforcer.postUnbind(consumer, poolManagerMock, e);
        verify(poolManagerMock, never()).updatePoolQuantity(any(Pool.class), anyInt());
        verify(poolManagerMock, never()).setPoolQuantity(any(Pool.class), anyLong());
    }
}
