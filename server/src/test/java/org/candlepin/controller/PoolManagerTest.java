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

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.candlepin.audit.Event;
import org.candlepin.audit.Event.Target;
import org.candlepin.audit.Event.Type;
import org.candlepin.audit.EventBuilder;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.AbstractHibernateObject;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EntitlementCertificateCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.PoolFilterBuilder;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.SourceStack;
import org.candlepin.model.SourceSubscription;
import org.candlepin.model.dto.Subscription;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.js.activationkey.ActivationKeyRules;
import org.candlepin.policy.js.autobind.AutobindRules;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.policy.js.entitlement.Enforcer;
import org.candlepin.policy.js.entitlement.Enforcer.CallerType;
import org.candlepin.policy.js.entitlement.PreUnbindHelper;
import org.candlepin.policy.js.pool.PoolRules;
import org.candlepin.policy.js.pool.PoolUpdate;
import org.candlepin.resource.dto.AutobindData;
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
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
    private ProductCurator mockProductCurator;
    @Mock
    private ProductServiceAdapter mockProductAdapter;
    @Mock
    private EventSink mockEventSink;
    @Mock
    private Configuration mockConfig;
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
    private EventBuilder eventBuilder;

    @Mock
    private ComplianceRules complianceRules;

    @Mock
    private ActivationKeyRules activationKeyRules;
    @Mock
    private ProductCurator productCuratorMock;
    @Mock
    private ContentCurator contentCuratorMock;

    private CandlepinPoolManager manager;
    private UserPrincipal principal;

    private Owner o;
    private Pool pool;
    private Product product;
    private ComplianceStatus dummyComplianceStatus;
    private PoolRules poolRules;

    protected static Map<String, List<Pool>> subToPools;

    @Before
    public void init() throws Exception {
        o = new Owner("key", "displayname");
        product = TestUtil.createProduct(o);
        pool = TestUtil.createPool(o, product);

        when(mockConfig.getInt(eq(ConfigProperties.PRODUCT_CACHE_MAX))).thenReturn(100);
        when(eventFactory.getEventBuilder(any(Target.class), any(Type.class))).thenReturn(eventBuilder);
        when(eventBuilder.setNewEntity(any(AbstractHibernateObject.class))).thenReturn(eventBuilder);
        when(eventBuilder.setOldEntity(any(AbstractHibernateObject.class))).thenReturn(eventBuilder);

        this.principal = TestUtil.createOwnerPrincipal();
        this.manager = spy(new CandlepinPoolManager(
            mockPoolCurator, mockProductCurator, entCertAdapterMock, mockEventSink,
            eventFactory, mockConfig, enforcerMock, poolRulesMock, entitlementCurator,
            consumerCuratorMock, certCuratorMock, complianceRules, autobindRules,
            activationKeyRules, productCuratorMock, contentCuratorMock)
        );

        when(entCertAdapterMock.generateEntitlementCert(any(Entitlement.class),
            any(Product.class))).thenReturn(
                new EntitlementCertificate());

        dummyComplianceStatus = new ComplianceStatus(new Date());
        when(complianceRules.getStatus(any(Consumer.class), any(Date.class))).thenReturn(
            dummyComplianceStatus);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testRefreshPoolsOnlyRegeneratesFloatingWhenNecessary() {
        List<Subscription> subscriptions = Util.newList();
        Product product = TestUtil.createProduct(o);
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
        Pool floating = TestUtil.createPool(TestUtil.createProduct(o));
        floating.setSourceSubscription(null);
        pools.add(floating);
        mockSubsList(subscriptions);

        mockPoolsList(pools);
        this.manager.getRefresher(mockSubAdapter).add(getOwner()).run();
        List<Pool> expectedFloating = new LinkedList();

        // Make sure that only the floating pool was regenerated
        expectedFloating.add(floating);
        verify(this.manager)
            .updateFloatingPools(eq(expectedFloating), eq(true), any(Set.class));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testRefreshPoolsOnlyRegeneratesWhenNecessary() {
        List<Subscription> subscriptions = Util.newList();
        Product product = TestUtil.createProduct(o);
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

        mockSubsList(subscriptions);

        mockPoolsList(pools);
        this.manager.getRefresher(mockSubAdapter).add(getOwner()).run();
        List<Pool> expectedModified = new LinkedList();

        // Make sure that only the floating pool was regenerated
        expectedModified.add(p);
        verify(this.manager)
            .updateFloatingPools(eq(new LinkedList()), eq(true), any(Set.class));
        verify(this.manager)
            .updatePoolsForSubscription(eq(expectedModified), eq(sub), eq(false), any(Set.class));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testRefreshPoolsDeletesOrphanedPools() {
        List<Subscription> subscriptions = Util.newList();
        List<Pool> pools = Util.newList();
        Pool p = TestUtil.createPool(TestUtil.createProduct(o));
        p.setSourceSubscription(new SourceSubscription("112", "master"));
        pools.add(p);
        mockSubsList(subscriptions);

        mockPoolsList(pools);
        this.manager.getRefresher(mockSubAdapter).add(getOwner()).run();
        verify(this.manager).deletePool(same(p));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testRefreshPoolsDeletesOrphanedHostedVirtBonusPool() {
        List<Subscription> subscriptions = Util.newList();
        List<Pool> pools = Util.newList();
        Pool p = TestUtil.createPool(TestUtil.createProduct(o));
        p.setSourceSubscription(new SourceSubscription("112", "master"));

        // Make it look like a hosted virt bonus pool:
        p.setAttribute("pool_derived", "true");
        p.setSourceStack(null);
        p.setSourceEntitlement(null);

        pools.add(p);
        mockSubsList(subscriptions);

        mockPoolsList(pools);
        this.manager.getRefresher(mockSubAdapter).add(getOwner()).run();
        verify(this.manager).deletePool(same(p));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testRefreshPoolsSkipsOrphanedEntitlementDerivedPools() {
        List<Subscription> subscriptions = Util.newList();
        List<Pool> pools = Util.newList();
        Pool p = TestUtil.createPool(TestUtil.createProduct(o));
        p.setSourceSubscription(new SourceSubscription("112", "master"));

        // Mock a pool with a source entitlement:
        p.setAttribute("pool_derived", "true");
        p.setSourceStack(null);
        p.setSourceEntitlement(new Entitlement());

        pools.add(p);
        mockSubsList(subscriptions);

        mockPoolsList(pools);
        this.manager.getRefresher(mockSubAdapter).add(getOwner()).run();
        verify(this.manager, never()).deletePool(same(p));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testRefreshPoolsSkipsOrphanedStackDerivedPools() {
        List<Subscription> subscriptions = Util.newList();
        List<Pool> pools = Util.newList();
        Pool p = TestUtil.createPool(TestUtil.createProduct(o));
        p.setSourceSubscription(new SourceSubscription("112", "master"));

        // Mock a pool with a source stack ID:
        p.setAttribute("pool_derived", "true");
        p.setSourceStack(new SourceStack(new Consumer(), "blah"));
        p.setSourceEntitlement(null);

        pools.add(p);
        mockSubsList(subscriptions);

        mockPoolsList(pools);
        this.manager.getRefresher(mockSubAdapter).add(getOwner()).run();
        verify(this.manager, never()).deletePool(same(p));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testRefreshPoolsSkipsDevelopmentPools() {
        List<Subscription> subscriptions = Util.newList();
        List<Pool> pools = Util.newList();
        Pool p = TestUtil.createPool(TestUtil.createProduct(o));
        p.setSourceSubscription(null);

        // Mock a development pool
        p.setAttribute("dev_pool", "true");

        pools.add(p);
        mockSubsList(subscriptions);

        mockPoolsList(pools);
        this.manager.getRefresher(mockSubAdapter).add(getOwner()).run();
        verify(this.manager, never()).deletePool(same(p));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testRefreshPoolsSortsStackDerivedPools() {
        List<Subscription> subscriptions = Util.newList();
        List<Pool> pools = Util.newList();

        // Pool has no subscription ID:
        Pool p = TestUtil.createPool(TestUtil.createProduct(o));
        p.setSourceStack(new SourceStack(new Consumer(), "a"));

        pools.add(p);
        mockSubsList(subscriptions);

        mockPoolsList(pools);

        this.manager.getRefresher(mockSubAdapter).add(getOwner()).run();
        ArgumentCaptor<List> poolCaptor = ArgumentCaptor.forClass(List.class);
        verify(this.poolRulesMock).updatePools(poolCaptor.capture(), any(Set.class));
        assertEquals(1, poolCaptor.getValue().size());
        assertEquals(p, poolCaptor.getValue().get(0));
    }

    @Test
    public void refreshPoolsCreatingPoolsForExistingSubscriptions() {
        List<Subscription> subscriptions = Util.newList();
        List<Pool> pools = Util.newList();
        Subscription s = TestUtil.createSubscription(getOwner(),
            TestUtil.createProduct(o));
        subscriptions.add(s);
        mockSubsList(subscriptions);

        mockPoolsList(pools);

        List<Pool> newPools = new LinkedList<Pool>();
        Pool p = TestUtil.createPool(s.getProduct());
        p.setSourceSubscription(new SourceSubscription(s.getId(), "master"));
        newPools.add(p);
        when(poolRulesMock.enrichAndCreateAdditionalPools(eq(s), any(List.class))).thenReturn(newPools);

        this.manager.getRefresher(mockSubAdapter).add(getOwner()).run();
        verify(this.mockPoolCurator, times(1)).create(any(Pool.class));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void refreshPoolsCleanupPoolThatLostVirtLimit() {
        List<Subscription> subscriptions = Util.newList();
        List<Pool> pools = Util.newList();
        Subscription s = TestUtil.createSubscription(getOwner(),
            TestUtil.createProduct(o));
        s.setId("01923");
        subscriptions.add(s);
        Pool p = TestUtil.createPool(s.getProduct());
        p.setSourceSubscription(new SourceSubscription(s.getId(), "master"));
        p.setMarkedForDelete(true);
        p.setOwner(s.getOwner());
        pools.add(p);

        mockSubsList(subscriptions);

        mockPoolsList(pools);

        List<PoolUpdate> updates = new LinkedList();
        PoolUpdate u = new PoolUpdate(p);
        u.setQuantityChanged(true);
        u.setOrderChanged(true);
        updates.add(u);
        when(poolRulesMock.updatePools(eq(s), eq(pools), any(Set.class))).thenReturn(updates);

        this.manager.getRefresher(mockSubAdapter).add(getOwner()).run();
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

        verify(entCertAdapterMock).generateEntitlementCert(eq(e), eq(product));
        verify(mockEventSink, times(1)).queueEvent(any(Event.class));
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
            TestUtil.createProduct(o));

        List<Pool> newPools = new LinkedList<Pool>();
        Pool p = TestUtil.createPool(s.getProduct());
        p.setSourceSubscription(new SourceSubscription(s.getId(), "master"));
        newPools.add(p);
        when(poolRulesMock.enrichAndCreateAdditionalPools(eq(s), any(List.class))).thenReturn(newPools);

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
        when(mockConfig.getBoolean(ConfigProperties.STANDALONE)).thenReturn(true);

        when(mockPoolCurator.lockAndLoad(any(Pool.class))).thenReturn(pool);

        manager.revokeEntitlement(e);

        verify(entitlementCurator).delete(e);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testEntitleWithADate() throws Exception {
        Product product = TestUtil.createProduct(o);
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

        AutobindData data = AutobindData.create(TestUtil.createConsumer(o))
                .forProducts(new String[] { product.getUuid() }).on(now);
        List<Entitlement> e = manager.entitleByProducts(data);

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
            TestUtil.createProduct(o));
        sub.setStartDate(expiredStart);
        sub.setEndDate(expiredDate);
        sub.setId("123");
        subscriptions.add(sub);

        mockSubsList(subscriptions);

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

        this.manager.getRefresher(mockSubAdapter).add(sub.getOwner()).run();

        verify(mockPoolCurator).delete(eq(p));

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
        verify(mockEventSink, times(3)).queueEvent((Event) any());
    }

    @Test
    public void testCleanupExpiredPools() {
        Pool p = createPoolWithEntitlements();
        p.setSubscriptionId("subid");
        List<Pool> pools = new LinkedList<Pool>();
        pools.add(p);

        when(mockPoolCurator.lockAndLoad(any(Pool.class))).thenReturn(p);
        when(mockPoolCurator.listExpiredPools()).thenReturn(pools);
        when(mockPoolCurator.entitlementsIn(p)).thenReturn(
                new ArrayList<Entitlement>(p.getEntitlements()));
        Subscription sub = new Subscription();
        sub.setId(p.getSubscriptionId());
        when(mockSubAdapter.getSubscription(any(String.class))).thenReturn(sub);
        when(mockSubAdapter.isReadOnly()).thenReturn(false);
        PreUnbindHelper preHelper =  mock(PreUnbindHelper.class);
        ValidationResult result = new ValidationResult();
        when(preHelper.getResult()).thenReturn(result);

        manager.cleanupExpiredPools();

        // And the pool should be deleted:
        verify(mockPoolCurator).delete(p);
    }

    @Test
    public void testCleanupExpiredPoolsReadOnlySubscriptions() {
        Pool p = createPoolWithEntitlements();
        p.setSubscriptionId("subid");
        List<Pool> pools = new LinkedList<Pool>();
        pools.add(p);

        when(mockPoolCurator.lockAndLoad(any(Pool.class))).thenReturn(p);
        when(mockPoolCurator.listExpiredPools()).thenReturn(pools);
        when(mockPoolCurator.entitlementsIn(p)).thenReturn(
                new ArrayList<Entitlement>(p.getEntitlements()));
        Subscription sub = new Subscription();
        sub.setId(p.getSubscriptionId());
        when(mockSubAdapter.getSubscription(any(String.class))).thenReturn(sub);
        when(mockSubAdapter.isReadOnly()).thenReturn(true);
        PreUnbindHelper preHelper =  mock(PreUnbindHelper.class);
        ValidationResult result = new ValidationResult();
        when(preHelper.getResult()).thenReturn(result);

        manager.cleanupExpiredPools();

        // And the pool should be deleted:
        verify(mockPoolCurator).delete(p);
        verify(mockSubAdapter, never()).getSubscription(any(String.class));
        verify(mockSubAdapter, never()).deleteSubscription(any(Subscription.class));
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
        Product product = TestUtil.createProduct(o);
        List<Pool> pools = Util.newList();
        Pool pool1 = TestUtil.createPool(product);
        pools.add(pool1);
        Date now = new Date();

        ValidationResult result = mock(ValidationResult.class);

        // Setup an installed product for the consumer, we'll make the bind request
        // with no products specified, so this should get used instead:
        String [] installedPids = new String [] { product.getUuid() };
        ComplianceStatus mockCompliance = new ComplianceStatus(now);
        mockCompliance.addNonCompliantProduct(installedPids[0]);
        when(complianceRules.getStatus(any(Consumer.class),
            any(Date.class), any(Boolean.class))).thenReturn(mockCompliance);


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
        AutobindData data = AutobindData.create(TestUtil.createConsumer(o)).on(now);
        manager.entitleByProducts(data);

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
            TestUtil.createProduct(getOwner()));
        sub.setId("123");
        subscriptions.add(sub);

        mockSubsList(subscriptions);

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

        this.manager.getRefresher(mockSubAdapter).add(sub.getOwner()).run();

        // The pool left over from the pre-migrated subscription should be deleted
        // and granted entitlements should be revoked
        verify(mockPoolCurator).delete(eq(p));
        verify(entitlementCurator).delete(eq(ent));
        // Make sure pools that don't match the owner were removed from the list
        // They shouldn't cause us to attempt to update existing pools when we
        // haven't created them in the first place
        verify(poolRulesMock).enrichAndCreateAdditionalPools(eq(sub), any(List.class));
    }

    private void mockSubsList(List<Subscription> subs) {
        List<String> subIds = new LinkedList<String>();
        for (Subscription sub : subs) {
            subIds.add(sub.getId());
            when(mockSubAdapter.getSubscription(eq(sub.getId()))).thenReturn(sub);
        }
        when(mockSubAdapter.getSubscriptionIds(any(Owner.class))).thenReturn(subIds);
        when(mockSubAdapter.getSubscriptions(any(Owner.class))).thenReturn(subs);
    }

    private void mockPoolsList(List<Pool> pools) {
        List<Pool> floating = new LinkedList<Pool>();
        subToPools = new HashMap<String, List<Pool>>();
        for (Pool pool : pools) {
            String subid = pool.getSubscriptionId();
            if (subid != null) {
                if (!subToPools.containsKey(subid)) {
                    subToPools.put(subid, new LinkedList<Pool>());
                }
                subToPools.get(subid).add(pool);
            }
            else {
                floating.add(pool);
            }
        }
        for (String subid : subToPools.keySet()) {
            when(mockPoolCurator.getPoolsBySubscriptionId(eq(subid))).thenReturn(subToPools.get(subid));
        }
        when(mockPoolCurator.getOwnersFloatingPools(any(Owner.class))).thenReturn(floating);
        when(mockPoolCurator.getPoolsFromBadSubs(any(Owner.class), any(Collection.class)))
            .thenAnswer(new Answer<List<Pool>>() {

                @Override
                public List<Pool> answer(InvocationOnMock iom) throws Throwable {
                    Collection<String> subIds = (Collection<String>) iom.getArguments()[1];
                    List<Pool> results = new LinkedList<Pool>();
                    for (Entry<String, List<Pool>> entry : PoolManagerTest.subToPools.entrySet()) {
                        for (Pool pool : entry.getValue()) {
                            if (!subIds.contains(pool.getSubscriptionId())) {
                                results.add(pool);
                            }
                        }
                    }
                    return results;
                }
            });
    }

    @Test
    public void createPoolsForExistingSubscriptionsNoneExist() {
        Owner owner = this.getOwner();
        PoolRules pRules = new PoolRules(manager, mockConfig, entitlementCurator,
                productCuratorMock);
        List<Subscription> subscriptions = Util.newList();
        List<Pool> pools = Util.newList();
        Product prod = TestUtil.createProduct(owner);
        Set<Product> products = new HashSet<Product>();
        products.add(prod);
        prod.setAttribute("virt_limit", "4");
        // productCache.addProducts(products);
        Subscription s = TestUtil.createSubscription(owner, prod);
        subscriptions.add(s);

        when(productCuratorMock.lookupById(prod.getOwner(), prod.getId())).thenReturn(prod);

        when(mockSubAdapter.getSubscriptions(any(Owner.class))).thenReturn(
            subscriptions);
        when(mockConfig.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);

        List<Pool> existingPools = new LinkedList<Pool>();
        List<Pool> newPools = pRules.enrichAndCreateAdditionalPools(s, existingPools);

        assertEquals(newPools.size(), 2);
        assert (newPools.get(0).getSourceSubscription().getSubscriptionSubKey()
            .equals("derived") || newPools.get(0).getSourceSubscription()
            .getSubscriptionSubKey().equals("derived"));
        assert (newPools.get(0).getSourceSubscription().getSubscriptionSubKey()
            .equals("master") || newPools.get(0).getSourceSubscription()
            .getSubscriptionSubKey().equals("master"));
    }

    @Test
    public void createPoolsForExistingSubscriptionsMasterExist() {
        Owner owner = this.getOwner();
        PoolRules pRules = new PoolRules(manager, mockConfig, entitlementCurator,
                productCuratorMock);
        List<Subscription> subscriptions = Util.newList();
        List<Pool> pools = Util.newList();
        Product prod = TestUtil.createProduct(owner);
        Set<Product> products = new HashSet<Product>();
        products.add(prod);
        // productCache.addProducts(products);
        prod.setAttribute("virt_limit", "4");
        Subscription s = TestUtil.createSubscription(owner, prod);
        subscriptions.add(s);

        when(productCuratorMock.lookupById(prod.getOwner(), prod.getId())).thenReturn(prod);
        when(mockSubAdapter.getSubscriptions(any(Owner.class))).thenReturn(
            subscriptions);
        when(mockConfig.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);

        List<Pool> existingPools = new LinkedList<Pool>();
        Pool p = TestUtil.createPool(s.getProduct());
        p.setSourceSubscription(new SourceSubscription(s.getId(), "master"));
        existingPools.add(p);
        List<Pool> newPools = pRules.enrichAndCreateAdditionalPools(s, existingPools);
        assertEquals(newPools.size(), 1);
        assertEquals(newPools.get(0).getSourceSubscription().getSubscriptionSubKey(), "derived");
    }

    @Test
    public void createPoolsForExistingSubscriptionsBonusExist() {
        Owner owner = this.getOwner();
        PoolRules pRules = new PoolRules(manager, mockConfig, entitlementCurator,
                productCuratorMock);
        List<Subscription> subscriptions = Util.newList();
        Product prod = TestUtil.createProduct(owner);
        Set<Product> products = new HashSet<Product>();
        products.add(prod);
        // productCache.addProducts(products);
        prod.setAttribute("virt_limit", "4");
        Subscription s = TestUtil.createSubscription(owner, prod);
        subscriptions.add(s);
        when(productCuratorMock.lookupById(prod.getOwner(), prod.getId())).thenReturn(prod);
        when(mockSubAdapter.getSubscriptions(any(Owner.class))).thenReturn(
            subscriptions);
        when(mockConfig.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);

        List<Pool> existingPools = new LinkedList<Pool>();
        Pool p = TestUtil.createPool(s.getProduct());
        p.setSourceSubscription(new SourceSubscription(s.getId(), "derived"));
        existingPools.add(p);
        pRules.enrichAndCreateAdditionalPools(s, existingPools);
        List<Pool> newPools = pRules.enrichAndCreateAdditionalPools(s, existingPools);
        assertEquals(newPools.size(), 1);
        assertEquals(newPools.get(0).getSourceSubscription().getSubscriptionSubKey(), "master");
    }

    @Test
    public void testGetChangedProductsNoNewProducts() {
        Product oldProduct = TestUtil.createProduct("fake id", "fake name", o);

        Set<Product> products = new HashSet<Product>();

        when(productCuratorMock.lookupById(oldProduct.getOwner(), oldProduct.getId()))
            .thenReturn(oldProduct);

        Set<Product> changed = manager.getChangedProducts(o, products);

        verify(productCuratorMock, times(0)).lookupById(oldProduct.getOwner(), oldProduct.getId());

        assertTrue(changed.isEmpty());
    }

    @Test
    public void testGetChangedProductsAllBrandNew() {
        Product newProduct = TestUtil.createProduct("fake id", "fake name", o);

        Set<Product> products = new HashSet<Product>();
        products.add(newProduct);

        when(productCuratorMock.lookupById(newProduct.getOwner(), newProduct.getId()))
            .thenReturn(null);

        Set<Product> changed = manager.getChangedProducts(o, products);

        assertTrue(changed.isEmpty());
    }

    private void mockProduct(Product p) {
        when(productCuratorMock.lookupById(p.getOwner(), p.getId())).thenReturn(p);
    }

    @Test
    public void testGetChangedProductsAllIdentical() {
        Product oldProduct = TestUtil.createProduct("fake id", "fake name", o);
        mockProduct(oldProduct);

        Set<Product> products = new HashSet<Product>();
        products.add(oldProduct);

        Set<Product> changed = manager.getChangedProducts(o, products);

        assertTrue(changed.isEmpty());
    }

    @Test
    public void testGetChangedProductsNameChanged() {
        Product newProduct = TestUtil.createProduct("fake id", "fake name new", o);
        Product oldProduct = TestUtil.createProduct("fake id", "fake name", o);

        Set<Product> products = new HashSet<Product>();
        products.add(newProduct);

        mockProduct(oldProduct);

        Set<Product> changed = manager.getChangedProducts(o, products);

        assertEquals(1, changed.size());
    }

    @Test
    public void testGetChangedProductsMultiplierChanged() {
        Product newProduct = TestUtil.createProduct("fake id", "fake name", o);
        Product oldProduct = TestUtil.createProduct("fake id", "fake name", o);

        oldProduct.setMultiplier(1L);
        newProduct.setMultiplier(2L);

        Set<Product> products = new HashSet<Product>();
        products.add(newProduct);

        mockProduct(oldProduct);

        Set<Product> changed = manager.getChangedProducts(o, products);

        assertEquals(1, changed.size());
    }

    @Test
    public void testGetChangedProductsAttributeAdded() {
        Product newProduct = TestUtil.createProduct("fake id", "fake name", o);
        Product oldProduct = TestUtil.createProduct("fake id", "fake name", o);

        newProduct.setAttribute("fake attr", "value");

        Set<Product> products = new HashSet<Product>();
        products.add(newProduct);

        mockProduct(oldProduct);

        Set<Product> changed = manager.getChangedProducts(o, products);

        assertEquals(1, changed.size());
    }

    @Test
    public void testGetChangedProductsAttributeRemoved() {
        Product newProduct = TestUtil.createProduct("fake id", "fake name", o);
        Product oldProduct = TestUtil.createProduct("fake id", "fake name", o);

        oldProduct.setAttribute("fake attr", "value");

        Set<Product> products = new HashSet<Product>();
        products.add(newProduct);

        mockProduct(oldProduct);

        Set<Product> changed = manager.getChangedProducts(o, products);

        assertEquals(1, changed.size());
    }

    @Test
    public void testGetChangedProductsAttributeModified() {
        Product newProduct = TestUtil.createProduct("fake id", "fake name", o);
        Product oldProduct = TestUtil.createProduct("fake id", "fake name", o);

        oldProduct.setAttribute("fake attr", "value");
        newProduct.setAttribute("fake attr", "value new");

        Set<Product> products = new HashSet<Product>();
        products.add(newProduct);

        mockProduct(oldProduct);

        Set<Product> changed = manager.getChangedProducts(o, products);

        assertEquals(1, changed.size());
    }

    @Test
    public void testGetChangedProductsAttributeSwapped() {
        Product newProduct = TestUtil.createProduct("fake id", "fake name", o);
        Product oldProduct = TestUtil.createProduct("fake id", "fake name", o);

        oldProduct.setAttribute("fake attr", "value");
        newProduct.setAttribute("other fake attr", "value");

        Set<Product> products = new HashSet<Product>();
        products.add(newProduct);

        mockProduct(oldProduct);

        Set<Product> changed = manager.getChangedProducts(o, products);

        assertEquals(1, changed.size());
    }

    @Test
    public void testGetChangedProductsContentAdded() {
        Product newProduct = TestUtil.createProduct("fake id", "fake name", o);
        Product oldProduct = TestUtil.createProduct("fake id", "fake name", o);

        Content content = new Content();

        newProduct.addContent(content);

        Set<Product> products = new HashSet<Product>();
        products.add(newProduct);

        mockProduct(oldProduct);

        Set<Product> changed = manager.getChangedProducts(o, products);

        assertEquals(1, changed.size());
    }

    @Test
    public void testGetChangedProductsContentRemoved() {
        Product newProduct = TestUtil.createProduct("fake id", "fake name", o);
        Product oldProduct = TestUtil.createProduct("fake id", "fake name", o);

        Content content = new Content();

        oldProduct.addContent(content);

        Set<Product> products = new HashSet<Product>();
        products.add(newProduct);

        mockProduct(oldProduct);

        Set<Product> changed = manager.getChangedProducts(o, products);

        assertEquals(1, changed.size());
    }

    @Test
    public void testGetChangedProductsContentSwapped() {
        Product newProduct = TestUtil.createProduct("fake id", "fake name", o);
        Product oldProduct = TestUtil.createProduct("fake id", "fake name", o);

        Content content = new Content(o, "foobar", null, null, null, null, null, null, null);
        Content content2 = new Content(o, "baz", null, null, null, null, null, null, null);

        oldProduct.addContent(content);
        newProduct.addContent(content2);

        Set<Product> products = new HashSet<Product>();
        products.add(newProduct);

        mockProduct(oldProduct);

        Set<Product> changed = manager.getChangedProducts(o, products);

        assertEquals(1, changed.size());
    }

    @Test
    public void testGetChangedProductsContentEnabledToggled() {
        Product newProduct = TestUtil.createProduct("fake id", "fake name", o);
        Product oldProduct = TestUtil.createProduct("fake id", "fake name", o);

        Content content = new Content(o, "foobar", null, null, null, null, null, null, null);

        oldProduct.addContent(content);
        newProduct.addEnabledContent(content);

        Set<Product> products = new HashSet<Product>();
        products.add(newProduct);

        mockProduct(oldProduct);

        Set<Product> changed = manager.getChangedProducts(o, products);

        assertEquals(1, changed.size());
    }

    @Test
    public void testFabricateSubscriptionFromPool() {
        Product product = TestUtil.createProduct("product", "Product", o);
        Product provided1 = TestUtil.createProduct("provided-1", "Provided 1", o);
        Product provided2 = TestUtil.createProduct("provided-2", "Provided 2", o);

        Pool pool = mock(Pool.class);

        HashSet<Product> provided = new HashSet<Product>();
        provided.add(provided1);
        provided.add(provided2);

        Long quantity = new Long(42);

        Date startDate = new Date(System.currentTimeMillis() - 86400000);
        Date endDate = new Date(System.currentTimeMillis() + 86400000);
        Date updated = new Date();

        String subscriptionId = "test-subscription-1";

        when(pool.getOwner()).thenReturn(o);
        when(pool.getProduct()).thenReturn(product);
        when(pool.getProvidedProducts()).thenReturn(provided);
        when(pool.getQuantity()).thenReturn(quantity);
        when(pool.getStartDate()).thenReturn(startDate);
        when(pool.getEndDate()).thenReturn(endDate);
        when(pool.getUpdated()).thenReturn(updated);
        when(pool.getSubscriptionId()).thenReturn(subscriptionId);
        // TODO: Add other attributes to check here.

        Subscription fabricated = manager.fabricateSubscriptionFromPool(pool);

        assertEquals(o, fabricated.getOwner());
        assertEquals(product, fabricated.getProduct());
        assertEquals(provided, fabricated.getProvidedProducts());
        assertEquals(quantity, fabricated.getQuantity());
        assertEquals(startDate, fabricated.getStartDate());
        assertEquals(endDate, fabricated.getEndDate());
        assertEquals(updated, fabricated.getModified());
        assertEquals(subscriptionId, fabricated.getId());
    }

    private Content buildContent(Owner owner) {
        Content content = new Content();

        int rand = TestUtil.randomInt();
        HashSet<String> modifiedProductIds = new HashSet<String>(
            Arrays.asList("mpid-a-" + rand, "mpid-d-" + rand, "mpid-c-" + rand)
        );

        content.setId("cid" + rand);
        content.setOwner(owner);

        content.setContentUrl("https://www.content_url.com/" + rand);
        content.setGpgUrl("https://www.gpg_url.com/" + rand);
        content.setLabel("content_label-" + rand);
        content.setName("content-" + rand);
        content.setReleaseVer("content_releasever-" + rand);
        content.setRequiredTags("content_tags-" + rand);
        content.setType("content_type-" + rand);
        content.setVendor("content_vendor-" + rand);
        content.setArches("content_arches-" + rand);
        content.setModifiedProductIds(modifiedProductIds);

        return content;
    }

    private Content copyContent(Content source) {
        Content copy = (new Content()).copyProperties(source);
        copy.setId(source.getId());
        copy.setOwner(source.getOwner());

        return copy;
    }

    private Content mockContent(Content content) {
        when(contentCuratorMock.lookupById(content.getOwner(), content.getId())).thenReturn(content);
        return content;
    }

    @Test
    public void testGetChangedContentNewContent() {
        Owner owner = TestUtil.createOwner();

        Content c1 = this.mockContent(this.buildContent(owner));
        Content c2 = this.buildContent(owner);

        Content c1m = this.copyContent(c1);

        Set<Content> result = manager.getChangedContent(owner, Arrays.asList(c1m, c2));

        assertEquals(0, result.size());
    }

    @Test
    public void testGetChangedContentDifferingContentURL() {
        Owner owner = TestUtil.createOwner();

        Content c1 = this.mockContent(this.buildContent(owner));
        Content c2 = this.mockContent(this.buildContent(owner));

        Content c1m = this.copyContent(c1);
        Content c2m = this.copyContent(c2);

        c1m.setContentUrl("modified_value");


        Set<Content> result = manager.getChangedContent(owner, Arrays.asList(c1m, c2m));

        assertEquals(1, result.size());
        assertEquals(c1m, result.toArray()[0]);
    }

    @Test
    public void testGetChangedContentDifferingGPGURL() {
        Owner owner = TestUtil.createOwner();

        Content c1 = this.mockContent(this.buildContent(owner));
        Content c2 = this.mockContent(this.buildContent(owner));

        Content c1m = this.copyContent(c1);
        Content c2m = this.copyContent(c2);

        c1m.setGpgUrl("modified_value");


        Set<Content> result = manager.getChangedContent(owner, Arrays.asList(c1m, c2m));

        assertEquals(1, result.size());
        assertEquals(c1m, result.toArray()[0]);
    }

    @Test
    public void testGetChangedContentDifferingLabel() {
        Owner owner = TestUtil.createOwner();

        Content c1 = this.mockContent(this.buildContent(owner));
        Content c2 = this.mockContent(this.buildContent(owner));

        Content c1m = this.copyContent(c1);
        Content c2m = this.copyContent(c2);

        c1m.setLabel("modified_value");


        Set<Content> result = manager.getChangedContent(owner, Arrays.asList(c1m, c2m));

        assertEquals(1, result.size());
        assertEquals(c1m, result.toArray()[0]);
    }

    @Test
    public void testGetChangedContentDifferingName() {
        Owner owner = TestUtil.createOwner();

        Content c1 = this.mockContent(this.buildContent(owner));
        Content c2 = this.mockContent(this.buildContent(owner));

        Content c1m = this.copyContent(c1);
        Content c2m = this.copyContent(c2);

        c1m.setName("modified_value");


        Set<Content> result = manager.getChangedContent(owner, Arrays.asList(c1m, c2m));

        assertEquals(1, result.size());
        assertEquals(c1m, result.toArray()[0]);
    }

    @Test
    public void testGetChangedContentDifferingReleaseVersion() {
        Owner owner = TestUtil.createOwner();

        Content c1 = this.mockContent(this.buildContent(owner));
        Content c2 = this.mockContent(this.buildContent(owner));

        Content c1m = this.copyContent(c1);
        Content c2m = this.copyContent(c2);

        c1m.setReleaseVer("modified_value");


        Set<Content> result = manager.getChangedContent(owner, Arrays.asList(c1m, c2m));

        assertEquals(1, result.size());
        assertEquals(c1m, result.toArray()[0]);
    }

    @Test
    public void testGetChangedContentDifferingRequiredTags() {
        Owner owner = TestUtil.createOwner();

        Content c1 = this.mockContent(this.buildContent(owner));
        Content c2 = this.mockContent(this.buildContent(owner));

        Content c1m = this.copyContent(c1);
        Content c2m = this.copyContent(c2);

        c1m.setRequiredTags("modified_value");


        Set<Content> result = manager.getChangedContent(owner, Arrays.asList(c1m, c2m));

        assertEquals(1, result.size());
        assertEquals(c1m, result.toArray()[0]);
    }

    @Test
    public void testGetChangedContentDifferingType() {
        Owner owner = TestUtil.createOwner();

        Content c1 = this.mockContent(this.buildContent(owner));
        Content c2 = this.mockContent(this.buildContent(owner));

        Content c1m = this.copyContent(c1);
        Content c2m = this.copyContent(c2);

        c1m.setType("modified_value");


        Set<Content> result = manager.getChangedContent(owner, Arrays.asList(c1m, c2m));

        assertEquals(1, result.size());
        assertEquals(c1m, result.toArray()[0]);
    }

    @Test
    public void testGetChangedContentDifferingVendor() {
        Owner owner = TestUtil.createOwner();

        Content c1 = this.mockContent(this.buildContent(owner));
        Content c2 = this.mockContent(this.buildContent(owner));

        Content c1m = this.copyContent(c1);
        Content c2m = this.copyContent(c2);

        c1m.setVendor("modified_value");


        Set<Content> result = manager.getChangedContent(owner, Arrays.asList(c1m, c2m));

        assertEquals(1, result.size());
        assertEquals(c1m, result.toArray()[0]);
    }

    @Test
    public void testGetChangedContentDifferingArches() {
        Owner owner = TestUtil.createOwner();

        Content c1 = this.mockContent(this.buildContent(owner));
        Content c2 = this.mockContent(this.buildContent(owner));

        Content c1m = this.copyContent(c1);
        Content c2m = this.copyContent(c2);

        c1m.setArches("modified_value");


        Set<Content> result = manager.getChangedContent(owner, Arrays.asList(c1m, c2m));

        assertEquals(1, result.size());
        assertEquals(c1m, result.toArray()[0]);
    }

    @Test
    public void testGetChangedContentDifferingModifiedProductIds() {
        Owner owner = TestUtil.createOwner();

        Content c1 = this.mockContent(this.buildContent(owner));
        Content c2 = this.mockContent(this.buildContent(owner));

        Content c1m = this.copyContent(c1);
        Content c2m = this.copyContent(c2);

        HashSet<String> modifiedProductIds = new HashSet<String>();
        Set<Content> result;

        // New modified product
        modifiedProductIds.clear();
        modifiedProductIds.addAll(c1.getModifiedProductIds());
        modifiedProductIds.add("modified_value");

        c1m.setModifiedProductIds(modifiedProductIds);
        result = manager.getChangedContent(owner, Arrays.asList(c1m, c2m));

        assertEquals(1, result.size());
        assertEquals(c1m, result.toArray()[0]);


        // Removed modified product
        modifiedProductIds.clear();
        modifiedProductIds.addAll(c1.getModifiedProductIds());
        modifiedProductIds.remove(modifiedProductIds.toArray()[0]);

        c1m.setModifiedProductIds(modifiedProductIds);
        result = manager.getChangedContent(owner, Arrays.asList(c1m, c2m));

        assertEquals(1, result.size());
        assertEquals(c1m, result.toArray()[0]);


        // Replaced modified product
        modifiedProductIds.clear();
        modifiedProductIds.addAll(c1.getModifiedProductIds());
        modifiedProductIds.remove(modifiedProductIds.toArray()[0]);
        modifiedProductIds.add("modified_value");

        c1m.setModifiedProductIds(modifiedProductIds);
        result = manager.getChangedContent(owner, Arrays.asList(c1m, c2m));

        assertEquals(1, result.size());
        assertEquals(c1m, result.toArray()[0]);
    }
}
