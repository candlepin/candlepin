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
package org.fedoraproject.candlepin.controller;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anySet;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.fedoraproject.candlepin.audit.Event;
import org.fedoraproject.candlepin.audit.EventFactory;
import org.fedoraproject.candlepin.audit.EventSink;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.guice.PrincipalProvider;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.PoolCurator;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.service.SubscriptionServiceAdapter;
import org.fedoraproject.candlepin.test.TestUtil;
import org.fedoraproject.candlepin.util.Util;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * PoolManagerTest
 */
@RunWith(MockitoJUnitRunner.class)
public class PoolManagerTest {
    @Mock private PoolCurator mockCurator;
    @Mock private SubscriptionServiceAdapter mockAdapter;
    @Mock private EventSink mockEventSink;
    @Mock private Config mockConfig;
    @Mock private Entitler mockEntitler;
    @Mock private PrincipalProvider mockProvider;

    private EventFactory eventFactory;
    private PoolManager manager;
    private Principal principal;

    @Before
    public void init() {
        this.eventFactory = new EventFactory(mockProvider);
        this.principal = TestUtil.createOwnerPrincipal();
        this.manager = new PoolManager(mockCurator, mockAdapter, mockEventSink,
            eventFactory, mockConfig, mockEntitler);
        when(this.mockProvider.get()).thenReturn(this.principal);
    }

    @Test
    public void testRefreshPoolsForDeactivatingPools() {
        List<Subscription> subscriptions = Util.newList();
        List<Pool> pools = Util.newList();
        Pool p = TestUtil.createPool(TestUtil.createProduct());
        p.setSubscriptionId(112L);
        pools.add(p);
        when(mockAdapter.getSubscriptions(any(Owner.class)))
            .thenReturn(subscriptions);
        when(mockCurator.listAvailableEntitlementPools(any(Consumer.class),
                any(Owner.class), anyString(), anyBoolean())).thenReturn(pools);
        this.manager.refreshPools(getOwner());
        verify(this.mockEntitler).deletePool(same(p));
    }

    @Test
    public void refreshPoolsCreatingPoolsForExistingSubscriptions() {
        List<Subscription> subscriptions = Util.newList();
        List<Pool> pools = Util.newList();
        Subscription s = TestUtil.createSubscription(getOwner(),
            TestUtil.createProduct());
        subscriptions.add(s);
        when(mockAdapter.getSubscriptions(any(Owner.class)))
            .thenReturn(subscriptions);
        when(mockCurator.listAvailableEntitlementPools(any(Consumer.class),
                any(Owner.class), anyString(), anyBoolean())).thenReturn(pools);
        this.manager.refreshPools(getOwner());
        verifyZeroInteractions(mockEntitler);
        verify(this.mockCurator, times(1)).create(any(Pool.class));
    }

    /**
     * This test case passes existing pool & subscription with changed values.
     * Thus updatePoolForSubscription needs to be called.
     */
    @Test
    public void refreshPoolsUpdatingPoolsForSubscriptionsWithIncreaseInQuantity() {
        List<Subscription> subscriptions = Util.newList();
        List<Pool> pools = Util.newList();
        Subscription s = TestUtil.createSubscription(getOwner(),
            TestUtil.createProduct());
        s.setId(123L);
        subscriptions.add(s);
        Pool p = new Pool(s.getOwner(), s.getProduct().getId(), new HashSet<String>(),
            s.getQuantity() + 10, s.getStartDate(), Util.tomorrow());
        p.setId(423L);
        p.setSubscriptionId(s.getId());
        pools.add(p);
        when(mockAdapter.getSubscriptions(any(Owner.class)))
            .thenReturn(subscriptions);
        when(mockCurator.listAvailableEntitlementPools(any(Consumer.class),
                any(Owner.class), anyString(), anyBoolean())).thenReturn(pools);
        this.manager.refreshPools(getOwner());
        verify(mockEventSink, times(1)).sendEvent(any(Event.class));
        verify(mockCurator, times(1)).merge(any(Pool.class));

    }

    @Test
    public void testUpdatePoolForSubscriptionWithNoChanges() {
        Subscription s = TestUtil.createSubscription(getOwner(),
            TestUtil.createProduct());
        Pool p = new Pool(s.getOwner(), s.getProduct().getId(), new HashSet<String>(),
            s.getQuantity(), s.getStartDate(), s.getEndDate());
        p.setSubscriptionId(s.getId());
        this.manager.updatePoolForSubscription(p, s);
        verifyZeroInteractions(mockCurator);
        verifyZeroInteractions(mockProvider);
    }

    @Test
    public void testUpdatePoolForSubscriptionWithQuantityChange() {
        Subscription s = TestUtil.createSubscription(getOwner(),
            TestUtil.createProduct());
        Pool p = new Pool(s.getOwner(), s.getProduct().getId(), new HashSet<String>(),
            s.getQuantity().longValue() + 10, s.getStartDate(), s.getEndDate());
        this.manager.updatePoolForSubscription(p, s);
        verifyZeroInteractions(this.mockEntitler);
        verify(mockEventSink, times(1)).sendEvent(any(Event.class));
        verify(mockCurator, times(1)).merge(any(Pool.class));
        assertEquals(s.getQuantity(), p.getQuantity());
    }

    /**
     * @return
     */
    private Owner getOwner() {
        return principal.getOwner();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testUpdatePoolForSubscriptionWithDateChange() {
        Subscription s = TestUtil.createSubscription(getOwner(),
            TestUtil.createProduct());
        Pool p = new Pool(s.getOwner(), s.getProduct().getId(), new HashSet<String>(),
            s.getQuantity(), s.getStartDate(), Util.tomorrow());
        this.manager.updatePoolForSubscription(p, s);
        verify(mockCurator).retrieveFreeEntitlementsOfPool(any(Pool.class), eq(true));
        verify(mockEntitler).regenerateCertificatesOf(anySet());
        verify(mockEventSink, times(1)).sendEvent(any(Event.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testUpdatePoolForSubscriptionWithBothChanges() {
        Subscription s = TestUtil.createSubscription(getOwner(),
            TestUtil.createProduct());
        Pool p = new Pool(s.getOwner(), s.getProduct().getId(), new HashSet<String>(),
            s.getQuantity().longValue() + 4, s.getStartDate(), Util.tomorrow());
        this.manager.updatePoolForSubscription(p, s);
        verify(mockEntitler).regenerateCertificatesOf(anySet());
        verifyAndAssertForAllChanges(s, p);
    }

    /**
     * @param s
     * @param p
     */

    private void verifyAndAssertForAllChanges(Subscription s, Pool p) {
        verify(mockCurator).retrieveFreeEntitlementsOfPool(any(Pool.class), eq(true));
        verify(mockEventSink, times(1)).sendEvent(any(Event.class));
        assertEquals(s.getQuantity(), p.getQuantity());
        assertEquals(s.getEndDate(), p.getEndDate());
        assertEquals(s.getStartDate(), p.getStartDate());
    }

    public void testUpdatePoolForSubscriptionWithBothChangesAndFewEntitlementsToRegen() {
        final Subscription s = TestUtil.createSubscription(getOwner(),
            TestUtil.createProduct());
        final Pool p = new Pool(s.getOwner(), s.getProduct().getId(), new HashSet<String>(),
            s.getQuantity().longValue() + 4, s.getStartDate(), Util.tomorrow());
        when(this.mockCurator.retrieveFreeEntitlementsOfPool(any(Pool.class),
            anyBoolean())).thenReturn(new ArrayList<Entitlement>() {
                private static final long serialVersionUID = 1L;

                {
                    for (int i = 0; i < 4; i++) {
                        add(mock(Entitlement.class));
                    }
                }
            });
        this.manager.updatePoolForSubscription(p, s);
        verify(mockEntitler, times(4)).regenerateCertificatesOf(any(Entitlement.class));
        verifyAndAssertForAllChanges(s, p);
    }

    @Test
    public void testCreatePoolForSubscription() {
        final Subscription s = TestUtil.createSubscription(
            getOwner(), TestUtil.createProduct());
        this.manager.createPoolForSubscription(s);
        verify(this.mockCurator, times(1)).create(argThat(new ArgumentMatcher<Pool>() {
            @Override
            public boolean matches(Object arg0) {
                Pool pool = (Pool) arg0;
                //is it right to check reference?
                //equals not implemented for most of the objects below.
                return pool.getOwner() == s.getOwner() &&
                    pool.getProductId() == s.getProduct().getId() &&
                    pool.getStartDate() == s.getStartDate() &&
                    pool.getEndDate() == s.getEndDate() &&
                    pool.getSubscriptionId() == s.getId() &&
                    pool.getQuantity().equals(
                        s.getQuantity() * s.getProduct().getMultiplier());
            }
        }));
    }

}
