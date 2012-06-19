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
package org.candlepin.policy.test;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.policy.EnforcerDispatcher;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.policy.js.entitlement.EntitlementRules;
import org.candlepin.policy.js.entitlement.ManifestEntitlementRules;
import org.candlepin.policy.js.pool.PoolHelper;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * EnforcerDispatcherTest
 */
public class EnforcerDispatcherTest {
    private ManifestEntitlementRules ce;
    private EntitlementRules rules;
    private EnforcerDispatcher ed;
    private ComplianceStatus compliance;

    @Before
    public void init() {
        rules = mock(EntitlementRules.class);
        ce = mock(ManifestEntitlementRules.class);
        ed = new EnforcerDispatcher(rules, ce);
        compliance = mock(ComplianceStatus.class);
    }

    @Test
    public void postEntitlementManifestConsumer() {
        Consumer c = mock(Consumer.class);
        PoolHelper ph = mock(PoolHelper.class);
        Entitlement e = mock(Entitlement.class);
        ConsumerType type = mock(ConsumerType.class);
        when(c.getType()).thenReturn(type);
        when(type.isManifest()).thenReturn(true);

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
        when(type.isManifest()).thenReturn(false);

        ed.postEntitlement(c, ph, e);

        verify(rules, atLeastOnce()).postEntitlement(eq(c), eq(ph), eq(e));
        verify(ce, never()).postEntitlement(eq(c), eq(ph), eq(e));
    }

    @Test
    public void preEntitlementManifestConsumer() {
        Consumer c = mock(Consumer.class);
        Pool p = mock(Pool.class);
        ConsumerType type = mock(ConsumerType.class);
        when(c.getType()).thenReturn(type);
        when(type.isManifest()).thenReturn(true);

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
        when(type.isManifest()).thenReturn(false);

        ed.preEntitlement(c, p, 10);

        verify(rules, atLeastOnce()).preEntitlement(eq(c), eq(p), eq(10));
        verify(ce, never()).preEntitlement(eq(c), eq(p), eq(10));
    }

    @Test
    public void bestPoolManifestConsumer() {
        Consumer c = mock(Consumer.class);
        String[] pids = {"10", "20", "30"};
        Pool p = mock(Pool.class);
        List<Pool> pools = new ArrayList<Pool>();
        pools.add(p);
        ConsumerType type = mock(ConsumerType.class);
        when(c.getType()).thenReturn(type);
        when(type.isManifest()).thenReturn(true);

        String test = null;
        Set<String> exempt = new HashSet<String>();
        ed.selectBestPools(c, pids, pools, compliance, test, exempt);
        verify(rules, never()).selectBestPools(eq(c), eq(pids), eq(pools),
            eq(compliance), eq(test), eq(exempt));
        verify(ce, atLeastOnce()).selectBestPools(eq(c), eq(pids), eq(pools),
            eq(compliance), eq(test), eq(exempt));
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
        when(type.isManifest()).thenReturn(false);

        String test = null;
        Set<String> exempt = new HashSet<String>();
        ed.selectBestPools(c, pids, pools, compliance, test, exempt);
        verify(rules, atLeastOnce()).selectBestPools(eq(c), eq(pids), eq(pools),
            eq(compliance), eq(test), eq(exempt));
        verify(ce, never()).selectBestPools(eq(c), eq(pids), eq(pools),
            eq(compliance), eq(test), eq(exempt));
    }
}
