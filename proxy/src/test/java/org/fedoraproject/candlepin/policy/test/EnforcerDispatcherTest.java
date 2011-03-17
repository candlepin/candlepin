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
package org.fedoraproject.candlepin.policy.test;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.policy.CandlepinConsumerTypeEnforcer;
import org.fedoraproject.candlepin.policy.EnforcerDispatcher;
import org.fedoraproject.candlepin.policy.js.entitlement.EntitlementRules;
import org.fedoraproject.candlepin.policy.js.pool.PoolHelper;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * EnforcerDispatcherTest
 */
public class EnforcerDispatcherTest {
    private CandlepinConsumerTypeEnforcer ce;
    private EntitlementRules rules;
    private EnforcerDispatcher ed;
    
    @Before
    public void init() {
        rules = mock(EntitlementRules.class);
        ce = mock(CandlepinConsumerTypeEnforcer.class);
        ed = new EnforcerDispatcher(rules, ce);
    }
    
    @Test
    public void postEntitlementCandlepinConsumer() {
        Consumer c = mock(Consumer.class);
        PoolHelper ph = mock(PoolHelper.class);
        Entitlement e = mock(Entitlement.class);
        ConsumerType type = mock(ConsumerType.class);
        when(c.getType()).thenReturn(type);
        when(type.isType(eq(ConsumerTypeEnum.CANDLEPIN))).thenReturn(true);
        
        ed.postEntitlement(c, ph, e);
        
        verify(rules, never()).postEntitlement(eq(c), eq(ph), eq(e));
        verify(ce, atLeastOnce()).postEntitlement(eq(c), eq(ph), eq(e));
    }
    
    @Test
    public void postEntitlementRegularConsumer() {
        Consumer c = mock(Consumer.class);
        PoolHelper ph = mock(PoolHelper.class);
        Entitlement e = mock(Entitlement.class);
        ConsumerType type = mock(ConsumerType.class);
        when(c.getType()).thenReturn(type);
        when(type.isType(eq(ConsumerTypeEnum.CANDLEPIN))).thenReturn(false);
        
        ed.postEntitlement(c, ph, e);
        
        verify(rules, atLeastOnce()).postEntitlement(eq(c), eq(ph), eq(e));
        verify(ce, never()).postEntitlement(eq(c), eq(ph), eq(e));
    }
    
    @Test
    public void preEntitlementCandlepinConsumer() {
        Consumer c = mock(Consumer.class);
        Pool p = mock(Pool.class);
        ConsumerType type = mock(ConsumerType.class);
        when(c.getType()).thenReturn(type);
        when(type.isType(eq(ConsumerTypeEnum.CANDLEPIN))).thenReturn(true);
        
        ed.preEntitlement(c, p, 10);
        
        verify(rules, never()).preEntitlement(eq(c), eq(p), eq(10));
        verify(ce, atLeastOnce()).preEntitlement(eq(c), eq(p), eq(10));        
    }
    
    @Test
    public void preEntitlementRegularConsumer() {
        Consumer c = mock(Consumer.class);
        Pool p = mock(Pool.class);
        ConsumerType type = mock(ConsumerType.class);
        when(c.getType()).thenReturn(type);
        when(type.isType(eq(ConsumerTypeEnum.CANDLEPIN))).thenReturn(false);
        
        ed.preEntitlement(c, p, 10);
        
        verify(rules, atLeastOnce()).preEntitlement(eq(c), eq(p), eq(10));
        verify(ce, never()).preEntitlement(eq(c), eq(p), eq(10));        
    }
    
    @Test
    public void bestPoolCandlepinConsumer() {
        Consumer c = mock(Consumer.class);
        String[] pids = {"10", "20", "30"};
        Pool p = mock(Pool.class);
        List<Pool> pools = new ArrayList<Pool>();
        pools.add(p);
        ConsumerType type = mock(ConsumerType.class);
        when(c.getType()).thenReturn(type);
        when(type.isType(eq(ConsumerTypeEnum.CANDLEPIN))).thenReturn(true);
        

        ed.selectBestPools(c, pids, pools);
        verify(rules, never()).selectBestPools(eq(c), eq(pids), eq(pools));
        verify(ce, atLeastOnce()).selectBestPools(eq(c), eq(pids), eq(pools));
    }
    
    @Test
    public void bestPoolRegularConsumer() {
        Consumer c = mock(Consumer.class);
        String[] pids = {"10", "20", "30"};
        Pool p = mock(Pool.class);
        List<Pool> pools = new ArrayList<Pool>();
        pools.add(p);
        ConsumerType type = mock(ConsumerType.class);
        when(c.getType()).thenReturn(type);
        when(type.isType(eq(ConsumerTypeEnum.CANDLEPIN))).thenReturn(false);

        ed.selectBestPools(c, pids, pools);
        verify(rules, atLeastOnce()).selectBestPools(eq(c), eq(pids), eq(pools));
        verify(ce, never()).selectBestPools(eq(c), eq(pids), eq(pools));
    }
}
