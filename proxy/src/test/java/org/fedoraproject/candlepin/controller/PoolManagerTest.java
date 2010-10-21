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
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anySet;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.spy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.fedoraproject.candlepin.audit.Event;
import org.fedoraproject.candlepin.audit.EventFactory;
import org.fedoraproject.candlepin.audit.EventSink;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.guice.PrincipalProvider;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCertificate;
import org.fedoraproject.candlepin.model.EntitlementCertificateCurator;
import org.fedoraproject.candlepin.model.EntitlementCurator;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.PoolCurator;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.ValidationResult;
import org.fedoraproject.candlepin.policy.js.entitlement.PreEntHelper;
import org.fedoraproject.candlepin.service.EntitlementCertServiceAdapter;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.fedoraproject.candlepin.service.SubscriptionServiceAdapter;
import org.fedoraproject.candlepin.test.TestUtil;
import org.fedoraproject.candlepin.util.Util;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * PoolManagerTest
 */
@RunWith(MockitoJUnitRunner.class)
public class PoolManagerTest {

    @Mock
    private PoolCurator mockPoolCurator;
    @Mock
    private SubscriptionServiceAdapter mockSubAdapter;
    @Mock
    private ProductServiceAdapter mockProductAdapter;
    @Mock
    private EventSink mockEventSink;
    @Mock
    private Config mockConfig;
    @Mock
    private PrincipalProvider mockProvider;
    @Mock
    private EntitlementCurator entitlementCurator;
    @Mock
    private EntitlementCertificateCurator certCuratorMock;
    @Mock
    private EntitlementCertServiceAdapter entCertAdapterMock;
    @Mock
    private Enforcer enforcerMock;
    @Mock
    private ConsumerCurator consumerCuratorMock;

    @Mock
    private EventFactory eventFactory;

    private PoolManager manager;
    private Principal principal;

    private Owner o;
    private Pool pool;
    private Product product;

    @Before
    public void init() throws Exception {
        product = TestUtil.createProduct();
        o = new Owner("key", "displayname");
        pool = TestUtil.createPool(o, product);

        this.principal = TestUtil.createOwnerPrincipal();
        this.manager = spy(new PoolManager(mockPoolCurator, mockSubAdapter,
            mockProductAdapter, entCertAdapterMock, mockEventSink,
            eventFactory, mockConfig, enforcerMock, entitlementCurator,
            consumerCuratorMock, certCuratorMock));
        when(this.mockProvider.get()).thenReturn(this.principal);
        when(entCertAdapterMock.generateEntitlementCert(any(Entitlement.class),
            any(Subscription.class), any(Product.class))).thenReturn(
                new EntitlementCertificate());
    }

    @Test
    public void testRefreshPoolsForDeactivatingPools() {
        List<Subscription> subscriptions = Util.newList();
        List<Pool> pools = Util.newList();
        Pool p = TestUtil.createPool(TestUtil.createProduct());
        p.setSubscriptionId("112");
        pools.add(p);
        when(mockSubAdapter.getSubscriptions(any(Owner.class))).thenReturn(
            subscriptions);
        when(
            mockPoolCurator.listAvailableEntitlementPools(any(Consumer.class),
                any(Owner.class), anyString(), anyBoolean())).thenReturn(pools);
        this.manager.refreshPools(getOwner());
        verify(this.manager).deletePool(same(p));
    }

    @Test
    public void refreshPoolsCreatingPoolsForExistingSubscriptions() {
        List<Subscription> subscriptions = Util.newList();
        List<Pool> pools = Util.newList();
        Subscription s = TestUtil.createSubscription(getOwner(),
            TestUtil.createProduct());
        subscriptions.add(s);
        when(mockSubAdapter.getSubscriptions(any(Owner.class))).thenReturn(
            subscriptions);
        when(
            mockPoolCurator.listAvailableEntitlementPools(any(Consumer.class),
                any(Owner.class), anyString(), anyBoolean())).thenReturn(pools);
        this.manager.refreshPools(getOwner());
        verify(this.mockPoolCurator, times(1)).create(any(Pool.class));
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
        s.setId("123");
        subscriptions.add(s);
        Pool p = new Pool(s.getOwner(), s.getProduct().getId(),
            new HashSet<String>(), s.getQuantity() + 10, s.getStartDate(),
            Util.tomorrow(), s.getContractNumber());
        p.setId("423");
        p.setSubscriptionId(s.getId());
        pools.add(p);
        when(mockSubAdapter.getSubscriptions(any(Owner.class))).thenReturn(
            subscriptions);
        when(
            mockPoolCurator.listAvailableEntitlementPools(any(Consumer.class),
                any(Owner.class), anyString(), anyBoolean())).thenReturn(pools);
        this.manager.refreshPools(getOwner());
        verify(mockEventSink, times(1)).sendEvent(any(Event.class));
        verify(mockPoolCurator, times(1)).merge(any(Pool.class));

    }

    @Test
    public void testRefreshPoolsWithModifiedProductCausesEntitlementRegen() {
        Subscription s = TestUtil.createSubscription(getOwner(),
            TestUtil.createProduct());
        Pool p = new Pool(s.getOwner(), s.getProduct().getId(),
            new HashSet<String>(), s.getQuantity(), s.getStartDate(),
            s.getEndDate(), s.getContractNumber());
        p.setSubscriptionId(s.getId());
        
        s.setProduct(TestUtil.createProduct());
        
        this.manager.updatePoolForSubscription(p, s);
        verify(mockPoolCurator).retrieveFreeEntitlementsOfPool(any(Pool.class),
            eq(true));
        verify(manager).regenerateCertificatesOf(anySet());
        verify(mockEventSink, times(1)).sendEvent(any(Event.class));
    }

    @Test
    public void testRefreshPoolsWithNewProvidedProductsCausesEntitlementRegen() {
        Subscription s = TestUtil.createSubscription(getOwner(),
            TestUtil.createProduct());
        Pool p = new Pool(s.getOwner(), s.getProduct().getId(),
            new HashSet<String>(), s.getQuantity(), s.getStartDate(),
            s.getEndDate(), s.getContractNumber());
        p.setSubscriptionId(s.getId());
        
        Set<Product> providedProducts = new HashSet<Product>();
        providedProducts.add(TestUtil.createProduct());
        s.setProvidedProducts(providedProducts);
        
        this.manager.updatePoolForSubscription(p, s);
        verify(mockPoolCurator).retrieveFreeEntitlementsOfPool(any(Pool.class),
            eq(true));
        verify(manager).regenerateCertificatesOf(anySet());
        verify(mockEventSink, times(1)).sendEvent(any(Event.class));
    }

    @Test
    public void testRefreshPoolsWithRemovedProvidedProductsCausesEntitlementRegen() {
        Subscription s = TestUtil.createSubscription(getOwner(),
            TestUtil.createProduct());

        Product providedProduct = TestUtil.createProduct();
        
        Set<Product> providedProducts = new HashSet<Product>();
        providedProducts.add(providedProduct);
        s.setProvidedProducts(providedProducts);
        
        Pool p = new Pool(s.getOwner(), s.getProduct().getId(),
            new HashSet<String>(), s.getQuantity(), s.getStartDate(),
            s.getEndDate(), s.getContractNumber());
        p.setSubscriptionId(s.getId());
        Set<String> providedProductIds = new HashSet<String>();
        providedProductIds.add(providedProduct.getId());
        
        p.setProvidedProductIds(providedProductIds);

        providedProducts.clear();
        
        this.manager.updatePoolForSubscription(p, s);
        verify(mockPoolCurator).retrieveFreeEntitlementsOfPool(any(Pool.class),
            eq(true));
        verify(manager).regenerateCertificatesOf(anySet());
        verify(mockEventSink, times(1)).sendEvent(any(Event.class));
    }
    
    @Test
    public void testUpdatePoolForSubscriptionWithNoChanges() {
        Subscription s = TestUtil.createSubscription(getOwner(),
            TestUtil.createProduct());
        Pool p = new Pool(s.getOwner(), s.getProduct().getId(),
            new HashSet<String>(), s.getQuantity(), s.getStartDate(),
            s.getEndDate(), s.getContractNumber());
        p.setSubscriptionId(s.getId());
        this.manager.updatePoolForSubscription(p, s);
        verifyZeroInteractions(mockPoolCurator);
        verifyZeroInteractions(mockProvider);
    }

    @Test
    public void testUpdatePoolForSubscriptionWithQuantityChange() {
        Subscription s = TestUtil.createSubscription(getOwner(),
            TestUtil.createProduct());
        Pool p = new Pool(s.getOwner(), s.getProduct().getId(),
            new HashSet<String>(), s.getQuantity().longValue() + 10,
            s.getStartDate(), s.getEndDate(), s.getContractNumber());
        this.manager.updatePoolForSubscription(p, s);
        verify(mockEventSink, times(1)).sendEvent(any(Event.class));
        verify(mockPoolCurator, times(1)).merge(any(Pool.class));
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
        Pool p = new Pool(s.getOwner(), s.getProduct().getId(),
            new HashSet<String>(), s.getQuantity(), s.getStartDate(),
            Util.tomorrow(), s.getContractNumber());
        this.manager.updatePoolForSubscription(p, s);
        verify(mockPoolCurator).retrieveFreeEntitlementsOfPool(any(Pool.class),
            eq(true));
        verify(manager).regenerateCertificatesOf(anySet());
        verify(mockEventSink, times(1)).sendEvent(any(Event.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testUpdatePoolForSubscriptionWithBothChanges() {
        Subscription s = TestUtil.createSubscription(getOwner(),
            TestUtil.createProduct());
        Pool p = new Pool(s.getOwner(), s.getProduct().getId(),
            new HashSet<String>(), s.getQuantity().longValue() + 4,
            s.getStartDate(), Util.tomorrow(), s.getContractNumber());
        this.manager.updatePoolForSubscription(p, s);
        verify(manager).regenerateCertificatesOf(anySet());
        verifyAndAssertForAllChanges(s, p, 1);
    }

    private void verifyAndAssertForAllChanges(Subscription s, Pool p,
        int expectedEventCount) {
        verify(mockPoolCurator).retrieveFreeEntitlementsOfPool(any(Pool.class),
            eq(true));
        verify(mockEventSink, times(expectedEventCount)).sendEvent(any(Event.class));

        assertEquals(s.getQuantity(), p.getQuantity());
        assertEquals(s.getEndDate(), p.getEndDate());
        assertEquals(s.getStartDate(), p.getStartDate());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testUpdatePoolForSubscriptionWithBothChangesAndFewEntitlementsToRegen() {
        final Subscription s = TestUtil.createSubscription(getOwner(),
            TestUtil.createProduct());
        final Pool p = new Pool(s.getOwner(), s.getProduct().getId(), new HashSet<String>(),
            s.getQuantity().longValue() + 4, s.getStartDate(), Util.tomorrow(),
            s.getContractNumber());
        List<Entitlement> mockedEntitlements = new ArrayList<Entitlement>() {
            private static final long serialVersionUID = 1L;

            {
                for (int i = 0; i < 4; i++) {
                    Entitlement e = mock(Entitlement.class);
                    when(e.getPool()).thenReturn(p);
                    add(e);
                }
            }
        };
        when(this.mockPoolCurator.retrieveFreeEntitlementsOfPool(any(Pool.class),
            anyBoolean())).thenReturn(mockedEntitlements);
        this.manager.updatePoolForSubscription(p, s);
        verify(manager, times(1)).regenerateCertificatesOf(any(Iterable.class));
        verifyAndAssertForAllChanges(s, p, 5);
    }

    @Test
    public void testCreatePoolForSubscription() {
        final Subscription s = TestUtil.createSubscription(getOwner(),
            TestUtil.createProduct());
        this.manager.createPoolForSubscription(s);
        verify(this.mockPoolCurator, times(1)).create(
            argThat(new ArgumentMatcher<Pool>() {
                @Override
                public boolean matches(Object arg0) {
                    Pool pool = (Pool) arg0;
                    // is it right to check reference?
                    // equals not implemented for most of the objects below.
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

    @Test
    public void testRevokeCleansUpPoolsWithSourceEnt() throws Exception {
        Entitlement e = new Entitlement(pool, TestUtil.createConsumer(o),
            pool.getStartDate(), pool.getEndDate(), 1);
        List<Pool> poolsWithSource = createPoolsWithSourceEntitlement(e, product);
        when(mockPoolCurator.listBySourceEntitlement(e)).thenReturn(poolsWithSource);

        manager.revokeEntitlement(e);

        verify(entCertAdapterMock).revokeEntitlementCertificates(e);
        verify(entitlementCurator).delete(e);

        for (Pool p : poolsWithSource) {
            verify(mockPoolCurator).delete(p);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testGrantByProductPicksPoolWithAvailableEntitlements() throws Exception {
        Product product = TestUtil.createProduct();
        List<Pool> pools = Util.newList();
        Pool pool1 = TestUtil.createPool(product);
        pools.add(pool1);
        Pool pool2 = TestUtil.createPool(product);
        pools.add(pool2);
       
        PreEntHelper badHelper = mock(PreEntHelper.class);
        PreEntHelper goodHelper = mock(PreEntHelper.class);
        
        ValidationResult badResult = mock(ValidationResult.class);
        ValidationResult goodResult = mock(ValidationResult.class);
        
        when(mockPoolCurator.listByOwner(any(Owner.class))).thenReturn(pools);
        when(enforcerMock.preEntitlement(any(Consumer.class), any(Pool.class),
            anyInt())).thenReturn(badHelper).thenReturn(goodHelper);
        
        when(badHelper.getResult()).thenReturn(badResult);
        when(goodHelper.getResult()).thenReturn(goodResult);

        when(badResult.isSuccessful()).thenReturn(false);
        when(goodResult.isSuccessful()).thenReturn(true);
        
        when(enforcerMock.selectBestPool(any(Consumer.class), anyString(),
            any(List.class))).thenReturn(pool1);
        
        Entitlement e = manager.entitleByProduct(TestUtil.createConsumer(o),
            product.getId(), 1);
        
        assertNotNull(e);

    }
    
    private List<Pool> createPoolsWithSourceEntitlement(Entitlement e, Product p) {
        List<Pool> pools = new LinkedList<Pool>();
        Pool pool1 = TestUtil.createPool(e.getOwner(), p);
        pools.add(pool1);
        Pool pool2 = TestUtil.createPool(e.getOwner(), p);
        pools.add(pool2);
        return pools;
    }

    @Test
    public void testCleanup() throws Exception {
        Pool p = createPoolWithEntitlements();

        when(mockPoolCurator.entitlementsIn(p)).thenReturn(
                new ArrayList<Entitlement>(p.getEntitlements()));

        manager.deletePool(p);

        // And the pool should be deleted:
        verify(mockPoolCurator).delete(p);

        // Check that appropriate events were sent out:
        verify(eventFactory).poolDeleted(p);
        verify(mockEventSink, times(3)).sendEvent((Event) any());
    }

    private Pool createPoolWithEntitlements() {
        Pool newPool = TestUtil.createPool(o, product);
        Entitlement e1 = new Entitlement(newPool, TestUtil.createConsumer(o),
            newPool.getStartDate(), newPool.getEndDate(), 1);
        Entitlement e2 = new Entitlement(newPool, TestUtil.createConsumer(o),
            newPool.getStartDate(), newPool.getEndDate(), 1);
        newPool.getEntitlements().add(e1);
        newPool.getEntitlements().add(e2);
        return newPool;
    }

}
