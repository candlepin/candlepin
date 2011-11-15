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
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.fedoraproject.candlepin.audit.Event;
import org.fedoraproject.candlepin.audit.EventFactory;
import org.fedoraproject.candlepin.audit.EventSink;
import org.fedoraproject.candlepin.auth.UserPrincipal;
import org.fedoraproject.candlepin.config.Config;
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
import org.fedoraproject.candlepin.model.ProvidedProduct;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.PoolRules;
import org.fedoraproject.candlepin.policy.ValidationResult;
import org.fedoraproject.candlepin.policy.js.compliance.ComplianceRules;
import org.fedoraproject.candlepin.policy.js.compliance.ComplianceStatus;
import org.fedoraproject.candlepin.policy.js.entitlement.PreEntHelper;
import org.fedoraproject.candlepin.policy.js.entitlement.PreUnbindHelper;
import org.fedoraproject.candlepin.policy.js.pool.PoolUpdate;
import org.fedoraproject.candlepin.service.EntitlementCertServiceAdapter;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.fedoraproject.candlepin.service.SubscriptionServiceAdapter;
import org.fedoraproject.candlepin.test.TestUtil;
import org.fedoraproject.candlepin.util.Util;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private EntitlementCurator entitlementCurator;
    @Mock
    private EntitlementCertificateCurator certCuratorMock;
    @Mock
    private EntitlementCertServiceAdapter entCertAdapterMock;
    @Mock
    private Enforcer enforcerMock;
    @Mock
    private PoolRules poolRulesMock;
    @Mock
    private ConsumerCurator consumerCuratorMock;

    @Mock
    private EventFactory eventFactory;

    @Mock
    private ComplianceRules complianceRules;

    private CandlepinPoolManager manager;
    private UserPrincipal principal;

    private Owner o;
    private Pool pool;
    private Product product;

    @Before
    public void init() throws Exception {
        product = TestUtil.createProduct();
        o = new Owner("key", "displayname");
        pool = TestUtil.createPool(o, product);

        this.principal = TestUtil.createOwnerPrincipal();
        this.manager = spy(new CandlepinPoolManager(mockPoolCurator, mockSubAdapter,
            mockProductAdapter, entCertAdapterMock, mockEventSink,
            eventFactory, mockConfig, enforcerMock, poolRulesMock, entitlementCurator,
            consumerCuratorMock, certCuratorMock, complianceRules));

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
                any(Owner.class), anyString(), any(Date.class),
                anyBoolean(), anyBoolean())).thenReturn(pools);
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
                any(Owner.class), anyString(), any(Date.class),
                anyBoolean(), anyBoolean())).thenReturn(pools);

        List<Pool> newPools = new LinkedList<Pool>();
        Pool p = TestUtil.createPool(s.getProduct());
        p.setSubscriptionId(s.getId());
        newPools.add(p);
        when(poolRulesMock.createPools(s)).thenReturn(newPools);

        this.manager.refreshPools(getOwner());
        verify(this.mockPoolCurator, times(1)).create(any(Pool.class));
    }

    @Test
    public void testRefreshPoolsWithModifiedProductCausesEntitlementRegen() {
        Subscription s = TestUtil.createSubscription(getOwner(),
            TestUtil.createProduct());
        Pool p = new Pool(s.getOwner(), s.getProduct().getId(), s.getProduct().getName(),
            new HashSet<ProvidedProduct>(), s.getQuantity(), s.getStartDate(),
            s.getEndDate(), s.getContractNumber(), s.getAccountNumber());
        p.setSubscriptionId(s.getId());

        s.setProduct(TestUtil.createProduct());

        List<PoolUpdate> updatedPools = new LinkedList<PoolUpdate>();
        updatedPools.add(new PoolUpdate(p, false, false, true));
        when(poolRulesMock.updatePools(any(Subscription.class), any(List.class)))
            .thenReturn(updatedPools);


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
        Pool p = new Pool(s.getOwner(), s.getProduct().getId(), s.getProduct().getName(),
            new HashSet<ProvidedProduct>(), s.getQuantity(), s.getStartDate(),
            s.getEndDate(), s.getContractNumber(), s.getAccountNumber());
        p.setSubscriptionId(s.getId());

        Set<Product> providedProducts = new HashSet<Product>();
        providedProducts.add(TestUtil.createProduct());
        s.setProvidedProducts(providedProducts);

        List<PoolUpdate> updatedPools = new LinkedList<PoolUpdate>();
        updatedPools.add(new PoolUpdate(p, false, false, true));
        when(poolRulesMock.updatePools(any(Subscription.class), any(List.class)))
            .thenReturn(updatedPools);

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
        Pool p = new Pool(s.getOwner(), s.getProduct().getId(), s.getProduct().getName(),
            new HashSet<ProvidedProduct>(), s.getQuantity(), s.getStartDate(),
            s.getEndDate(), s.getContractNumber(), s.getAccountNumber());
        p.setSubscriptionId(s.getId());

        List<PoolUpdate> updatedPools = new LinkedList<PoolUpdate>();
        when(poolRulesMock.updatePools(any(Subscription.class), any(List.class)))
            .thenReturn(updatedPools);

        this.manager.updatePoolForSubscription(p, s);
        verifyZeroInteractions(mockPoolCurator);
    }

    @Test
    public void testUpdatePoolForSubscriptionWithQuantityChange() {
        Subscription s = TestUtil.createSubscription(getOwner(),
            TestUtil.createProduct());
        Pool p = new Pool(s.getOwner(), s.getProduct().getId(), s.getProduct().getName(),
            new HashSet<ProvidedProduct>(), s.getQuantity().longValue() + 10,
            s.getStartDate(), s.getEndDate(), s.getContractNumber(), s.getAccountNumber());

        List<PoolUpdate> updatedPools = new LinkedList<PoolUpdate>();
        updatedPools.add(new PoolUpdate(p, false, true, false));
        when(poolRulesMock.updatePools(any(Subscription.class), any(List.class)))
            .thenReturn(updatedPools);

        this.manager.updatePoolForSubscription(p, s);
        verify(mockEventSink, times(1)).sendEvent(any(Event.class));
        verify(mockPoolCurator, times(1)).merge(any(Pool.class));
    }

    /**
     * @return
     */
    private Owner getOwner() {
        // just grab the first one
        return principal.getOwners().get(0);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testUpdatePoolForSubscriptionWithDateChange() {
        Subscription s = TestUtil.createSubscription(getOwner(),
            TestUtil.createProduct());
        Pool p = new Pool(s.getOwner(), s.getProduct().getId(), s.getProduct().getName(),
            new HashSet<ProvidedProduct>(), s.getQuantity(), s.getStartDate(),
            Util.tomorrow(), s.getContractNumber(), s.getAccountNumber());

        List<PoolUpdate> updatedPools = new LinkedList<PoolUpdate>();
        updatedPools.add(new PoolUpdate(p, true, false, false));
        when(poolRulesMock.updatePools(any(Subscription.class), any(List.class)))
            .thenReturn(updatedPools);

        this.manager.updatePoolForSubscription(p, s);
        verify(mockPoolCurator).retrieveFreeEntitlementsOfPool(any(Pool.class),
            eq(true));
        verify(manager).regenerateCertificatesOf(anySet());
        verify(mockEventSink, times(1)).sendEvent(any(Event.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testUpdatePoolForSubscriptionWithDateAndQuantityChanges() {
        Subscription s = TestUtil.createSubscription(getOwner(),
            TestUtil.createProduct());
        Pool p = new Pool(s.getOwner(), s.getProduct().getId(), s.getProduct().getName(),
            new HashSet<ProvidedProduct>(), s.getQuantity(),
            s.getStartDate(), s.getEndDate(), s.getContractNumber(), s.getAccountNumber());

        List<PoolUpdate> updatedPools = new LinkedList<PoolUpdate>();
        updatedPools.add(new PoolUpdate(p, true, true, false));
        when(poolRulesMock.updatePools(any(Subscription.class), any(List.class)))
            .thenReturn(updatedPools);

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
        final Pool p = new Pool(s.getOwner(), s.getProduct().getId(),
            s.getProduct().getName(), new HashSet<ProvidedProduct>(),
            s.getQuantity().longValue(), s.getStartDate(), s.getEndDate(),
            s.getContractNumber(), s.getAccountNumber());
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

        List<PoolUpdate> updatedPools = new LinkedList<PoolUpdate>();
        updatedPools.add(new PoolUpdate(p, true, true, false));
        when(poolRulesMock.updatePools(any(Subscription.class), any(List.class)))
            .thenReturn(updatedPools);

        this.manager.updatePoolForSubscription(p, s);
        verify(manager, times(1)).regenerateCertificatesOf(any(Iterable.class));
        verifyAndAssertForAllChanges(s, p, 5);
    }

    @Test
    public void testCreatePoolForSubscription() {
        final Subscription s = TestUtil.createSubscription(getOwner(),
            TestUtil.createProduct());

        List<Pool> newPools = new LinkedList<Pool>();
        Pool p = TestUtil.createPool(s.getProduct());
        p.setSubscriptionId(s.getId());
        newPools.add(p);
        when(poolRulesMock.createPools(s)).thenReturn(newPools);

        this.manager.createPoolsForSubscription(s);
        verify(this.mockPoolCurator, times(1)).create(any(Pool.class));
    }

    @Test
    public void testRevokeCleansUpPoolsWithSourceEnt() throws Exception {
        Entitlement e = new Entitlement(pool, TestUtil.createConsumer(o),
            pool.getStartDate(), pool.getEndDate(), 1);
        List<Pool> poolsWithSource = createPoolsWithSourceEntitlement(e, product);
        when(mockPoolCurator.listBySourceEntitlement(e)).thenReturn(poolsWithSource);
        PreUnbindHelper preHelper =  mock(PreUnbindHelper.class);
        when(enforcerMock.preUnbind(eq(e.getConsumer()), eq(e.getPool())))
            .thenReturn(preHelper);
        ValidationResult result = new ValidationResult();
        when(preHelper.getResult()).thenReturn(result);
        when(mockConfig.standalone()).thenReturn(true);

        manager.revokeEntitlement(e);

        verify(entCertAdapterMock).revokeEntitlementCertificates(e);
        verify(entitlementCurator).delete(e);
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

        when(mockPoolCurator.listByOwner(any(Owner.class),
            any(Date.class))).thenReturn(pools);
        when(mockPoolCurator.lockAndLoad(any(Pool.class))).thenReturn(pool1);
        when(enforcerMock.preEntitlement(any(Consumer.class), any(Pool.class),
            anyInt())).thenReturn(badHelper).thenReturn(goodHelper);

        when(badHelper.getResult()).thenReturn(badResult);
        when(goodHelper.getResult()).thenReturn(goodResult);

        when(badResult.isSuccessful()).thenReturn(false);
        when(goodResult.isSuccessful()).thenReturn(true);

        Map<Pool, Integer> bestPools = new HashMap<Pool, Integer>();
        bestPools.put(pool1, 1);
        when(enforcerMock.selectBestPools(any(Consumer.class), any(String[].class),
            any(List.class), any(ComplianceStatus.class))).thenReturn(bestPools);

        Entitlement e = manager.entitleByProduct(TestUtil.createConsumer(o),
            product.getId());

        assertNotNull(e);

    }

    @SuppressWarnings("unchecked")
    @Test
    public void testEntitleWithADate() throws Exception {
        Product product = TestUtil.createProduct();
        List<Pool> pools = Util.newList();
        Pool pool1 = TestUtil.createPool(product);
        pools.add(pool1);
        Pool pool2 = TestUtil.createPool(product);
        pools.add(pool2);
        Date now = new Date();

        PreEntHelper helper = mock(PreEntHelper.class);

        ValidationResult result = mock(ValidationResult.class);

        when(mockPoolCurator.listByOwner(any(Owner.class), eq(now))).thenReturn(pools);
        when(mockPoolCurator.lockAndLoad(any(Pool.class))).thenReturn(pool1);
        when(enforcerMock.preEntitlement(any(Consumer.class), any(Pool.class),
            anyInt())).thenReturn(helper);

        when(helper.getResult()).thenReturn(result);
        when(result.isSuccessful()).thenReturn(true);

        Map<Pool, Integer> bestPools = new HashMap<Pool, Integer>();
        bestPools.put(pool1, 1);
        when(enforcerMock.selectBestPools(any(Consumer.class), any(String[].class),
            any(List.class), any(ComplianceStatus.class))).thenReturn(bestPools);

        List<Entitlement> e = manager.entitleByProducts(TestUtil.createConsumer(o),
            new String[] { product.getId() }, now);

        assertNotNull(e);
        assertEquals(e.size(), 1);
    }

    @Test
    public void testRefreshPoolsRemovesExpiredSubscriptionsAlongWithItsPoolsAndEnts() {
        PreUnbindHelper preHelper =  mock(PreUnbindHelper.class);

        Date expiredStart = TestUtil.createDate(2004, 5, 5);
        Date expiredDate = TestUtil.createDate(2005, 5, 5);

        List<Subscription> subscriptions = Util.newList();

        Subscription sub = TestUtil.createSubscription(getOwner(),
            TestUtil.createProduct());
        sub.setStartDate(expiredStart);
        sub.setEndDate(expiredDate);
        sub.setId("123");
        subscriptions.add(sub);

        when(mockSubAdapter.getSubscriptions(any(Owner.class))).thenReturn(
            subscriptions);

        List<Pool> pools = Util.newList();
        Pool p = TestUtil.createPool(sub.getOwner(), sub.getProduct());
        p.setSubscriptionId(sub.getId());
        p.setStartDate(expiredStart);
        p.setEndDate(expiredDate);
        p.setConsumed(1L);
        pools.add(p);

        when(mockPoolCurator.listAvailableEntitlementPools(any(Consumer.class),
            any(Owner.class), anyString(), any(Date.class),
            anyBoolean(), anyBoolean())).thenReturn(pools);

        List<Entitlement> poolEntitlements = Util.newList();
        Entitlement ent = TestUtil.createEntitlement();
        ent.setPool(p);
        ent.setQuantity(1);
        poolEntitlements.add(ent);

        when(mockPoolCurator.entitlementsIn(eq(p))).thenReturn(poolEntitlements);
        when(enforcerMock.preUnbind(eq(ent.getConsumer()),
            eq(ent.getPool()))).thenReturn(preHelper);

        ValidationResult result = new ValidationResult();
        when(preHelper.getResult()).thenReturn(result);

        manager.refreshPools(sub.getOwner());

        verify(mockSubAdapter).deleteSubscription(eq(sub));
        verify(mockPoolCurator).delete(eq(p));

        // Verify the entitlement was removed.
        verify(entCertAdapterMock).revokeEntitlementCertificates(eq(ent));
        verify(entitlementCurator).delete(eq(ent));
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
        PreUnbindHelper preHelper =  mock(PreUnbindHelper.class);
        for (Entitlement e : p.getEntitlements()) {
            when(enforcerMock.preUnbind(eq(e.getConsumer()), eq(p))).thenReturn(preHelper);
        }
        ValidationResult result = new ValidationResult();
        when(preHelper.getResult()).thenReturn(result);

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

    @Test
    public void testEntitleByProductsEmptyArray() throws Exception {
        Product product = TestUtil.createProduct();
        List<Pool> pools = Util.newList();
        Pool pool1 = TestUtil.createPool(product);
        pools.add(pool1);
        Date now = new Date();

        PreEntHelper helper = mock(PreEntHelper.class);

        ValidationResult result = mock(ValidationResult.class);

        // Setup an installed product for the consumer, we'll make the bind request
        // with no products specified, so this should get used instead:
        String [] installedPids = new String [] { product.getId() };
        ComplianceStatus mockCompliance = new ComplianceStatus(now);
        mockCompliance.addNonCompliantProduct(installedPids[0]);
        when(complianceRules.getStatus(any(Consumer.class),
            any(Date.class))).thenReturn(mockCompliance);

        when(mockPoolCurator.listByOwner(any(Owner.class), eq(now))).thenReturn(pools);
        when(mockPoolCurator.lockAndLoad(any(Pool.class))).thenReturn(pool1);
        when(enforcerMock.preEntitlement(any(Consumer.class), any(Pool.class),
            anyInt())).thenReturn(helper);

        when(helper.getResult()).thenReturn(result);
        when(result.isSuccessful()).thenReturn(true);

        Map<Pool, Integer> bestPools = new HashMap<Pool, Integer>();
        bestPools.put(pool1, 1);
        when(enforcerMock.selectBestPools(any(Consumer.class), any(String[].class),
            any(List.class), any(ComplianceStatus.class))).thenReturn(bestPools);

        // Make the call but provide a null array of product IDs (simulates healing):
        manager.entitleByProducts(TestUtil.createConsumer(o),
            null, now);

        verify(enforcerMock).selectBestPools(any(Consumer.class), eq(installedPids),
            any(List.class), eq(mockCompliance));


    }
}
