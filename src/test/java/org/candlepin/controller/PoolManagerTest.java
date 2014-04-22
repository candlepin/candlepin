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
package org.candlepin.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.candlepin.audit.Event;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EntitlementCertificateCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.PoolFilterBuilder;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.model.SourceStack;
import org.candlepin.model.SourceSubscription;
import org.candlepin.model.Subscription;
import org.candlepin.paging.Page;
import org.candlepin.paging.PageRequest;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.js.ProductCache;
import org.candlepin.policy.js.activationkey.ActivationKeyRules;
import org.candlepin.policy.js.autobind.AutobindRules;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.policy.js.entitlement.Enforcer;
import org.candlepin.policy.js.entitlement.Enforcer.CallerType;
import org.candlepin.policy.js.entitlement.PreUnbindHelper;
import org.candlepin.policy.js.pool.PoolRules;
import org.candlepin.policy.js.pool.PoolUpdate;
import org.candlepin.service.EntitlementCertServiceAdapter;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
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
    private EntitlementCurator entitlementCurator;
    @Mock
    private EntitlementCertificateCurator certCuratorMock;
    @Mock
    private EntitlementCertServiceAdapter entCertAdapterMock;
    @Mock
    private Enforcer enforcerMock;
    @Mock
    private AutobindRules autobindRules;
    @Mock
    private PoolRules poolRulesMock;
    @Mock
    private ConsumerCurator consumerCuratorMock;
    @Mock
    private EnvironmentCurator envCurator;

    @Mock
    private EventFactory eventFactory;

    @Mock
    private ComplianceRules complianceRules;

    @Mock
    private ActivationKeyRules activationKeyRules;

    private CandlepinPoolManager manager;
    private UserPrincipal principal;

    private Owner o;
    private Pool pool;
    private Product product;
    private ComplianceStatus dummyComplianceStatus;
    private ProductCache productCache;

    @Before
    public void init() throws Exception {
        product = TestUtil.createProduct();
        o = new Owner("key", "displayname");
        pool = TestUtil.createPool(o, product);

        when(mockConfig.getInt(eq(ConfigProperties.PRODUCT_CACHE_MAX))).thenReturn(100);
        this.productCache = new ProductCache(mockConfig, mockProductAdapter);

        this.principal = TestUtil.createOwnerPrincipal();
        this.manager = spy(new CandlepinPoolManager(mockPoolCurator, mockSubAdapter,
            productCache, entCertAdapterMock, mockEventSink, eventFactory,
            mockConfig, enforcerMock, poolRulesMock, entitlementCurator,
            consumerCuratorMock, certCuratorMock, complianceRules, autobindRules,
            activationKeyRules));

        when(entCertAdapterMock.generateEntitlementCert(any(Entitlement.class),
            any(Subscription.class), any(Product.class))).thenReturn(
                new EntitlementCertificate());

        dummyComplianceStatus = new ComplianceStatus(new Date());
        when(complianceRules.getStatus(any(Consumer.class), any(Date.class))).thenReturn(
            dummyComplianceStatus);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testRefreshPoolsOnlyRegeneratesFloatingWhenNecessary() {
        List<Subscription> subscriptions = Util.newList();
        Product product = TestUtil.createProduct();
        Subscription sub = TestUtil.createSubscription(getOwner(), product);
        sub.setId("testing-subid");
        subscriptions.add(sub);

        // Set up pools
        List<Pool> pools = Util.newList();

        // Should be unchanged
        Pool p = TestUtil.createPool(product);
        p.setSourceSubscription(new SourceSubscription(sub.getId(), "master"));
        pools.add(p);

        // Should be regenerated because it has no subscription id
        Pool floating = TestUtil.createPool(TestUtil.createProduct());
        floating.setSourceSubscription(null);
        pools.add(floating);
        when(mockSubAdapter.getSubscriptions(any(Owner.class))).thenReturn(
            subscriptions);

        mockPoolsList(pools);
        this.manager.getRefresher().add(getOwner()).run();
        List<Pool> expectedFloating = new LinkedList();

        // Make sure that only the floating pool was regenerated
        expectedFloating.add(floating);
        verify(this.manager).updateFloatingPools(eq(expectedFloating));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testRefreshPoolsOnlyRegeneratesWhenNecessary() {
        List<Subscription> subscriptions = Util.newList();
        Product product = TestUtil.createProduct();
        Subscription sub = TestUtil.createSubscription(getOwner(), product);
        sub.setId("testing-subid");
        subscriptions.add(sub);

        // Set up pools
        List<Pool> pools = Util.newList();

        // Should be unchanged
        Pool p = TestUtil.createPool(product);
        p.setSourceSubscription(new SourceSubscription(sub.getId(), "master"));
        p.setOwner(sub.getOwner());
        pools.add(p);

        when(mockSubAdapter.getSubscriptions(any(Owner.class))).thenReturn(
            subscriptions);

        mockPoolsList(pools);
        this.manager.getRefresher().add(getOwner()).run();
        List<Pool> expectedModified = new LinkedList();

        // Make sure that only the floating pool was regenerated
        expectedModified.add(p);
        verify(this.manager).updateFloatingPools(eq(new LinkedList()));
        verify(this.manager).updatePoolsForSubscription(
            eq(expectedModified), eq(sub), eq(false));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testRefreshPoolsDeletesOrphanedPools() {
        List<Subscription> subscriptions = Util.newList();
        List<Pool> pools = Util.newList();
        Pool p = TestUtil.createPool(TestUtil.createProduct());
        p.setSourceSubscription(new SourceSubscription("112", "master"));
        pools.add(p);
        when(mockSubAdapter.getSubscriptions(any(Owner.class))).thenReturn(
            subscriptions);

        mockPoolsList(pools);
        this.manager.getRefresher().add(getOwner()).run();
        verify(this.manager).deletePool(same(p));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testRefreshPoolsDeletesOrphanedHostedVirtBonusPool() {
        List<Subscription> subscriptions = Util.newList();
        List<Pool> pools = Util.newList();
        Pool p = TestUtil.createPool(TestUtil.createProduct());
        p.setSourceSubscription(new SourceSubscription("112", "master"));

        // Make it look like a hosted virt bonus pool:
        p.setAttribute("pool_derived", "true");
        p.setSourceStack(null);
        p.setSourceEntitlement(null);

        pools.add(p);
        when(mockSubAdapter.getSubscriptions(any(Owner.class))).thenReturn(
            subscriptions);

        mockPoolsList(pools);
        this.manager.getRefresher().add(getOwner()).run();
        verify(this.manager).deletePool(same(p));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testRefreshPoolsSkipsOrphanedEntitlementDerivedPools() {
        List<Subscription> subscriptions = Util.newList();
        List<Pool> pools = Util.newList();
        Pool p = TestUtil.createPool(TestUtil.createProduct());
        p.setSourceSubscription(new SourceSubscription("112", "master"));

        // Mock a pool with a source entitlement:
        p.setAttribute("pool_derived", "true");
        p.setSourceStack(null);
        p.setSourceEntitlement(new Entitlement());

        pools.add(p);
        when(mockSubAdapter.getSubscriptions(any(Owner.class))).thenReturn(
            subscriptions);

        mockPoolsList(pools);
        this.manager.getRefresher().add(getOwner()).run();
        verify(this.manager, never()).deletePool(same(p));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testRefreshPoolsSkipsOrphanedStackDerivedPools() {
        List<Subscription> subscriptions = Util.newList();
        List<Pool> pools = Util.newList();
        Pool p = TestUtil.createPool(TestUtil.createProduct());
        p.setSourceSubscription(new SourceSubscription("112", "master"));

        // Mock a pool with a source stack ID:
        p.setAttribute("pool_derived", "true");
        p.setSourceStack(new SourceStack(new Consumer(), "blah"));
        p.setSourceEntitlement(null);

        pools.add(p);
        when(mockSubAdapter.getSubscriptions(any(Owner.class))).thenReturn(
            subscriptions);

        mockPoolsList(pools);
        this.manager.getRefresher().add(getOwner()).run();
        verify(this.manager, never()).deletePool(same(p));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testRefreshPoolsSortsStackDerivedPools() {
        List<Subscription> subscriptions = Util.newList();
        List<Pool> pools = Util.newList();

        // Pool has no subscription ID:
        Pool p = TestUtil.createPool(TestUtil.createProduct());
        p.setSourceStack(new SourceStack(new Consumer(), "a"));

        pools.add(p);
        when(mockSubAdapter.getSubscriptions(any(Owner.class))).thenReturn(
            subscriptions);

        mockPoolsList(pools);

        this.manager.getRefresher().add(getOwner()).run();
        ArgumentCaptor<List> poolCaptor = ArgumentCaptor.forClass(List.class);
        verify(this.poolRulesMock).updatePools(poolCaptor.capture());
        assertEquals(1, poolCaptor.getValue().size());
        assertEquals(p, poolCaptor.getValue().get(0));
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

        mockPoolsList(pools);

        List<Pool> newPools = new LinkedList<Pool>();
        Pool p = TestUtil.createPool(s.getProduct());
        p.setSourceSubscription(new SourceSubscription(s.getId(), "master"));
        newPools.add(p);
        when(poolRulesMock.createPools(s)).thenReturn(newPools);

        this.manager.getRefresher().add(getOwner()).run();
        verify(this.mockPoolCurator, times(1)).create(any(Pool.class));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void refreshPoolsCleanupPoolThatLostVirtLimit() {
        List<Subscription> subscriptions = Util.newList();
        List<Pool> pools = Util.newList();
        Subscription s = TestUtil.createSubscription(getOwner(),
            TestUtil.createProduct());
        s.setId("01923");
        subscriptions.add(s);
        Pool p = TestUtil.createPool(s.getProduct());
        p.setSourceSubscription(new SourceSubscription(s.getId(), "master"));
        p.setAttribute(PoolManager.DELETE_FLAG, "true");
        p.setOwner(s.getOwner());
        pools.add(p);

        when(mockSubAdapter.getSubscriptions(any(Owner.class))).thenReturn(
            subscriptions);

        mockPoolsList(pools);

        List<PoolUpdate> updates = new LinkedList();
        PoolUpdate u = new PoolUpdate(p);
        u.setQuantityChanged(true);
        u.setOrderChanged(true);
        updates.add(u);
        when(poolRulesMock.updatePools(s, pools)).thenReturn(updates);

        this.manager.getRefresher().add(getOwner()).run();
        verify(this.mockPoolCurator, times(1)).delete(any(Pool.class));
    }

    @Test
    public void testLazyRegenerate() {
        Entitlement e = new Entitlement();
        manager.regenerateCertificatesOf(e, false, true);
        assertTrue(e.getDirty());
        verifyZeroInteractions(entCertAdapterMock);
    }

    @Test
    public void testLazyRegenerateForConsumer() {
        Entitlement e = new Entitlement();
        Consumer c = new Consumer();
        c.addEntitlement(e);
        manager.regenerateEntitlementCertificates(c, true);
        assertTrue(e.getDirty());
        verifyZeroInteractions(entCertAdapterMock);
    }

    @Test
    public void testNonLazyRegenerate() throws Exception {
        Subscription s = TestUtil.createSubscription(getOwner(),
            product);
        s.setId("testSubId");
        pool.setSourceSubscription(new SourceSubscription(s.getId(), "master"));
        Entitlement e = new Entitlement(pool, TestUtil.createConsumer(o),
            1);
        e.setDirty(true);

        when(mockSubAdapter.getSubscription(pool.getSubscriptionId())).thenReturn(s);

        manager.regenerateCertificatesOf(e, false, false);
        assertFalse(e.getDirty());

        verify(entCertAdapterMock).revokeEntitlementCertificates(e);
        verify(entCertAdapterMock).generateEntitlementCert(eq(e), eq(s),
            eq(product));
        verify(mockEventSink, times(1)).sendEvent(any(Event.class));
    }

    /**
     * @return
     */
    private Owner getOwner() {
        // just grab the first one
        return principal.getOwners().get(0);
    }

    @Test
    public void testCreatePoolForSubscription() {
        final Subscription s = TestUtil.createSubscription(getOwner(),
            TestUtil.createProduct());

        List<Pool> newPools = new LinkedList<Pool>();
        Pool p = TestUtil.createPool(s.getProduct());
        p.setSourceSubscription(new SourceSubscription(s.getId(), "master"));
        newPools.add(p);
        when(poolRulesMock.createPools(s)).thenReturn(newPools);

        this.manager.createPoolsForSubscription(s);
        verify(this.mockPoolCurator, times(1)).create(any(Pool.class));
    }

    @Test
    public void testRevokeAllEntitlements() {
        Consumer c = TestUtil.createConsumer(o);

        Entitlement e1 = new Entitlement(pool, c,
            1);
        Entitlement e2 = new Entitlement(pool, c,
            1);
        List<Entitlement> entitlementList = new ArrayList<Entitlement>();
        entitlementList.add(e1);
        entitlementList.add(e2);

        when(entitlementCurator.listByConsumer(eq(c))).thenReturn(entitlementList);
        when(mockPoolCurator.lockAndLoad(any(Pool.class))).thenReturn(pool);

        PreUnbindHelper preHelper =  mock(PreUnbindHelper.class);
        ValidationResult result = new ValidationResult();
        when(preHelper.getResult()).thenReturn(result);

        int total = manager.revokeAllEntitlements(c);

        assertEquals(2, total);
        verify(entitlementCurator, never()).listModifying(any(Entitlement.class));
    }

    @Test
    public void testRevokeCleansUpPoolsWithSourceEnt() throws Exception {
        Entitlement e = new Entitlement(pool, TestUtil.createConsumer(o),
            1);
        List<Pool> poolsWithSource = createPoolsWithSourceEntitlement(e, product);
        when(mockPoolCurator.listBySourceEntitlement(e)).thenReturn(poolsWithSource);
        PreUnbindHelper preHelper =  mock(PreUnbindHelper.class);
        ValidationResult result = new ValidationResult();
        when(preHelper.getResult()).thenReturn(result);
        when(mockConfig.standalone()).thenReturn(true);

        when(mockPoolCurator.lockAndLoad(any(Pool.class))).thenReturn(pool);

        manager.revokeEntitlement(e);

        verify(entCertAdapterMock).revokeEntitlementCertificates(e);
        verify(entitlementCurator).delete(e);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testEntitleWithADate() throws Exception {
        Product product = TestUtil.createProduct();
        List<Pool> pools = Util.newList();
        Pool pool1 = TestUtil.createPool(product);
        pools.add(pool1);
        Pool pool2 = TestUtil.createPool(product);
        pools.add(pool2);
        Date now = new Date();


        ValidationResult result = mock(ValidationResult.class);
        Page page = mock(Page.class);

        when(page.getPageData()).thenReturn(pools);
        when(mockPoolCurator.listAvailableEntitlementPools(any(Consumer.class),
            any(Owner.class), any(String.class), eq(now), anyBoolean(),
            any(PoolFilterBuilder.class), any(PageRequest.class),
            anyBoolean())).thenReturn(page);

        when(mockPoolCurator.lockAndLoad(any(Pool.class))).thenReturn(pool1);
        when(enforcerMock.preEntitlement(any(Consumer.class), any(Pool.class), anyInt(),
            any(CallerType.class))).thenReturn(result);

        when(result.isSuccessful()).thenReturn(true);

        List<PoolQuantity> bestPools = new ArrayList<PoolQuantity>();
        bestPools.add(new PoolQuantity(pool1, 1));
        when(autobindRules.selectBestPools(any(Consumer.class), any(String[].class),
            any(List.class), any(ComplianceStatus.class), any(String.class),
            any(Set.class), eq(false)))
            .thenReturn(bestPools);

        List<Entitlement> e = manager.entitleByProducts(TestUtil.createConsumer(o),
            new String[] { product.getId() }, now);

        assertNotNull(e);
        assertEquals(e.size(), 1);
    }

    @SuppressWarnings("rawtypes")
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
        p.setSourceSubscription(new SourceSubscription(sub.getId(), "master"));
        p.setStartDate(expiredStart);
        p.setEndDate(expiredDate);
        p.setConsumed(1L);
        pools.add(p);

        when(mockPoolCurator.lockAndLoad(any(Pool.class))).thenReturn(p);

        mockPoolsList(pools);

        List<Entitlement> poolEntitlements = Util.newList();
        Entitlement ent = TestUtil.createEntitlement();
        ent.setPool(p);
        ent.setQuantity(1);
        poolEntitlements.add(ent);

        when(mockPoolCurator.entitlementsIn(eq(p))).thenReturn(poolEntitlements);

        ValidationResult result = new ValidationResult();
        when(preHelper.getResult()).thenReturn(result);

        this.manager.getRefresher().add(sub.getOwner()).run();

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

        when(mockPoolCurator.lockAndLoad(any(Pool.class))).thenReturn(p);
        when(mockPoolCurator.entitlementsIn(p)).thenReturn(
                new ArrayList<Entitlement>(p.getEntitlements()));
        PreUnbindHelper preHelper =  mock(PreUnbindHelper.class);
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
            1);
        e1.setId("1");

        Entitlement e2 = new Entitlement(newPool, TestUtil.createConsumer(o),
            1);
        e2.setId("2");

        newPool.getEntitlements().add(e1);
        newPool.getEntitlements().add(e2);
        return newPool;
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testEntitleByProductsEmptyArray() throws Exception {
        Product product = TestUtil.createProduct();
        List<Pool> pools = Util.newList();
        Pool pool1 = TestUtil.createPool(product);
        pools.add(pool1);
        Date now = new Date();

        ValidationResult result = mock(ValidationResult.class);

        // Setup an installed product for the consumer, we'll make the bind request
        // with no products specified, so this should get used instead:
        String [] installedPids = new String [] { product.getId() };
        ComplianceStatus mockCompliance = new ComplianceStatus(now);
        mockCompliance.addNonCompliantProduct(installedPids[0]);
        when(complianceRules.getStatus(any(Consumer.class),
            any(Date.class))).thenReturn(mockCompliance);


        Page page = mock(Page.class);
        when(page.getPageData()).thenReturn(pools);

        when(mockPoolCurator.listAvailableEntitlementPools(any(Consumer.class),
            any(Owner.class), anyString(), eq(now),
            anyBoolean(), any(PoolFilterBuilder.class),
            any(PageRequest.class), anyBoolean()))
                .thenReturn(page);

        when(mockPoolCurator.lockAndLoad(any(Pool.class))).thenReturn(pool1);
        when(enforcerMock.preEntitlement(any(Consumer.class), any(Pool.class), anyInt(),
            any(CallerType.class))).thenReturn(result);

        when(result.isSuccessful()).thenReturn(true);

        List<PoolQuantity> bestPools = new ArrayList<PoolQuantity>();
        bestPools.add(new PoolQuantity(pool1, 1));
        when(autobindRules.selectBestPools(any(Consumer.class), any(String[].class),
            any(List.class), any(ComplianceStatus.class), any(String.class),
            any(Set.class), eq(false)))
            .thenReturn(bestPools);

        // Make the call but provide a null array of product IDs (simulates healing):
        manager.entitleByProducts(TestUtil.createConsumer(o),
            null, now);

        verify(autobindRules).selectBestPools(any(Consumer.class), eq(installedPids),
            any(List.class), eq(mockCompliance), any(String.class),
            any(Set.class), eq(false));
    }

    @Test
    public void testRefreshPoolsRemovesOtherOwnerPoolsForSameSub() {
        PreUnbindHelper preHelper =  mock(PreUnbindHelper.class);
        Owner other = new Owner("otherkey", "othername");

        List<Subscription> subscriptions = Util.newList();

        Subscription sub = TestUtil.createSubscription(getOwner(),
            TestUtil.createProduct());
        sub.setId("123");
        subscriptions.add(sub);

        when(mockSubAdapter.getSubscriptions(any(Owner.class))).thenReturn(
            subscriptions);

        List<Pool> pools = Util.newList();
        Pool p = TestUtil.createPool(other, sub.getProduct());
        p.setSourceSubscription(new SourceSubscription(sub.getId(), "master"));
        p.setConsumed(1L);
        pools.add(p);

        when(mockPoolCurator.lockAndLoad(any(Pool.class))).thenReturn(p);

        mockPoolsList(pools);

        List<Entitlement> poolEntitlements = Util.newList();
        Entitlement ent = TestUtil.createEntitlement();
        ent.setPool(p);
        ent.setQuantity(1);
        poolEntitlements.add(ent);

        when(mockPoolCurator.entitlementsIn(eq(p))).thenReturn(poolEntitlements);

        ValidationResult result = new ValidationResult();
        when(preHelper.getResult()).thenReturn(result);

        this.manager.getRefresher().add(sub.getOwner()).run();

        // The pool left over from the pre-migrated subscription should be deleted
        // and granted entitlements should be revoked
        verify(mockPoolCurator).delete(eq(p));
        verify(entCertAdapterMock).revokeEntitlementCertificates(eq(ent));
        verify(entitlementCurator).delete(eq(ent));
        // Make sure pools that don't match the owner were removed from the list
        // They shouldn't cause us to attempt to update existing pools when we
        // haven't created them in the first place
        verify(poolRulesMock).createPools(eq(sub));
    }

    private void mockPoolsList(List<Pool> toReturn) {
        when(mockPoolCurator.getPoolsForOwnerRefresh(any(Owner.class), any(List.class)))
            .thenReturn(toReturn);
    }
}
