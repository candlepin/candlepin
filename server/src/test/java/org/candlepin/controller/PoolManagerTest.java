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

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasItem;
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
import org.candlepin.model.Branding;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.Content;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EntitlementCertificateCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.Eventful;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Pool.PoolType;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.PoolFilterBuilder;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.SourceStack;
import org.candlepin.model.SourceSubscription;
import org.candlepin.model.dto.ContentData;
import org.candlepin.model.dto.ProductData;
import org.candlepin.model.dto.Subscription;
import org.candlepin.pinsetter.core.PinsetterKernel;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.policy.ValidationError;
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
import org.candlepin.service.OwnerServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.test.MockResultIterator;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;



/**
 * PoolManagerTest
 */
@RunWith(MockitoJUnitRunner.class)
public class PoolManagerTest {
    private static Logger log = LoggerFactory.getLogger(PoolManagerTest.class);

    private I18n i18n;

    @Mock private PoolCurator mockPoolCurator;
    @Mock private OwnerServiceAdapter mockOwnerAdapter;
    @Mock private SubscriptionServiceAdapter mockSubAdapter;
    @Mock private ProductCurator mockProductCurator;
    @Mock private ProductManager mockProductManager;
    @Mock private EventSink mockEventSink;
    @Mock private Configuration mockConfig;
    @Mock private EntitlementCurator entitlementCurator;
    @Mock private EntitlementCertificateCurator certCuratorMock;
    @Mock private EntitlementCertificateGenerator mockECGenerator;
    @Mock private Enforcer enforcerMock;
    @Mock private AutobindRules autobindRules;
    @Mock private PoolRules poolRulesMock;
    @Mock private ConsumerCurator consumerCuratorMock;
    @Mock private EnvironmentCurator envCurator;
    @Mock private EventFactory eventFactory;
    @Mock private EventBuilder eventBuilder;
    @Mock private ComplianceRules complianceRules;
    @Mock private ActivationKeyRules activationKeyRules;
    @Mock private ContentManager mockContentManager;
    @Mock private OwnerCurator mockOwnerCurator;
    @Mock private OwnerContentCurator mockOwnerContentCurator;
    @Mock private OwnerProductCurator mockOwnerProductCurator;
    @Mock private OwnerManager mockOwnerManager;
    @Mock private PinsetterKernel pinsetterKernel;

    @Captor private ArgumentCaptor<Map<String, Entitlement>> entMapCaptor;
    @Captor private ArgumentCaptor<Map<String, Product>> productMapCaptor;

    private CandlepinPoolManager manager;
    private UserPrincipal principal;

    private Owner owner;
    private Pool pool;
    private Product product;
    private ComplianceStatus dummyComplianceStatus;

    protected static Map<String, List<Pool>> subToPools;

    @Before
    public void init() throws Exception {
        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);

        owner = TestUtil.createOwner("key", "displayname");
        product = TestUtil.createProduct();
        pool = TestUtil.createPool(owner, product);

        when(mockOwnerCurator.lookupByKey(eq(owner.getKey()))).thenReturn(owner);

        when(mockConfig.getInt(eq(ConfigProperties.PRODUCT_CACHE_MAX))).thenReturn(100);
        when(eventFactory.getEventBuilder(any(Target.class), any(Type.class))).thenReturn(eventBuilder);

        when(eventBuilder.setNewEntity(any(Eventful.class))).thenReturn(eventBuilder);
        when(eventBuilder.setOldEntity(any(Eventful.class))).thenReturn(eventBuilder);

        this.principal = TestUtil.createOwnerPrincipal(owner);
        this.manager = spy(new CandlepinPoolManager(
            mockPoolCurator, mockEventSink, eventFactory, mockConfig, enforcerMock, poolRulesMock,
            entitlementCurator, consumerCuratorMock, certCuratorMock, mockECGenerator,
            complianceRules, autobindRules, activationKeyRules, mockProductCurator, mockProductManager,
            mockContentManager, mockOwnerContentCurator, mockOwnerCurator, mockOwnerProductCurator,
            mockOwnerManager, pinsetterKernel, i18n
        ));

        Map<String, EntitlementCertificate> entCerts = new HashMap<String, EntitlementCertificate>();
        entCerts.put(pool.getId(), new EntitlementCertificate());

        dummyComplianceStatus = new ComplianceStatus(new Date());
        when(complianceRules.getStatus(any(Consumer.class), any(Date.class))).thenReturn(
            dummyComplianceStatus);

        when(consumerCuratorMock.lockAndLoad(any(Consumer.class))).thenAnswer(new Answer<Consumer>() {
            @Override
            public Consumer answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                return (Consumer) args[0];
            }
        });
    }

    @Test
    public void doesntMergeDeletedPools() {
        reset(mockPoolCurator);

        Map<String, EventBuilder> poolEvents = new HashMap<String, EventBuilder>();
        List<PoolUpdate> updatedPools = new ArrayList<PoolUpdate>();
        Pool deletedPool = Mockito.mock(Pool.class);
        Pool normalPool = Mockito.mock(Pool.class);
        when(mockPoolCurator.exists(deletedPool)).thenReturn(false);
        when(mockPoolCurator.exists(normalPool)).thenReturn(true);

        PoolUpdate deletedPu = Mockito.mock(PoolUpdate.class);
        PoolUpdate normalPu = Mockito.mock(PoolUpdate.class);
        when(deletedPu.getPool()).thenReturn(deletedPool);
        when(normalPu.getPool()).thenReturn(normalPool);

        updatedPools.add(deletedPu);
        updatedPools.add(normalPu);

        manager.processPoolUpdates(poolEvents, updatedPools);

        verify(mockPoolCurator, never()).merge(deletedPool);
        verify(mockPoolCurator, times(1)).merge(normalPool);
    }

    @Test
    public void deletePoolsTest() {
        List<Pool> pools = new ArrayList<Pool>();
        pools.add(TestUtil.createPool(TestUtil.createProduct()));
        doNothing().when(mockPoolCurator).batchDelete(eq(pools), anySetOf(String.class));
        manager.deletePools(pools);
        verify(mockPoolCurator).batchDelete(eq(pools), anySetOf(String.class));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testRefreshPoolsOnlyRegeneratesFloatingWhenNecessary() {
        List<Subscription> subscriptions = Util.newList();

        Owner owner = this.getOwner();
        Product product = TestUtil.createProduct();
        Subscription sub = TestUtil.createSubscription(owner, product);
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
        mockSubsList(subscriptions);

        mockPoolsList(pools);
        when(mockOwnerCurator.lookupByKey(owner.getKey())).thenReturn(owner);
        this.mockProducts(owner, product);
        this.mockProductImport(owner, product);
        this.mockContentImport(owner, new Content[] {});

        CandlepinQuery<Pool> cqmock = mock(CandlepinQuery.class);
        when(cqmock.list()).thenReturn(pools);
        when(cqmock.iterator()).thenReturn(pools.iterator());
        when(mockPoolCurator.listByOwnerAndType(eq(owner), any(PoolType.class))).thenReturn(cqmock);

        this.manager.getRefresher(mockSubAdapter, mockOwnerAdapter).add(owner).run();
        List<Pool> expectedFloating = new LinkedList();

        // Make sure that only the floating pool was regenerated
        expectedFloating.add(floating);
        verify(this.manager).updateFloatingPools(eq(expectedFloating), eq(true), any(Map.class));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testRefreshPoolsOnlyRegeneratesWhenNecessary() {
        List<Subscription> subscriptions = Util.newList();

        Owner owner = this.getOwner();
        Product product = TestUtil.createProduct();
        product.setLocked(true);

        Subscription sub = TestUtil.createSubscription(owner, product);
        sub.setId("testing-subid");
        subscriptions.add(sub);

        // Set up pools
        List<Pool> pools = Util.newList();

        // Should be unchanged
        Pool p = TestUtil.createPool(product);
        p.setSourceSubscription(new SourceSubscription(sub.getId(), "master"));
        p.setOwner(owner);
        pools.add(p);

        mockSubsList(subscriptions);
        mockPoolsList(pools);
        when(mockOwnerCurator.lookupByKey(owner.getKey())).thenReturn(owner);
        this.mockProducts(owner, product);
        this.mockProductImport(owner, product);
        this.mockContentImport(owner, new Content[] {});

        CandlepinQuery<Pool> cqmock = mock(CandlepinQuery.class);
        when(cqmock.list()).thenReturn(pools);
        when(cqmock.iterator()).thenReturn(pools.iterator());
        when(mockPoolCurator.listByOwnerAndType(eq(owner), any(PoolType.class))).thenReturn(cqmock);

        this.manager.getRefresher(mockSubAdapter, mockOwnerAdapter).add(owner).run();
        List<Pool> expectedModified = new LinkedList();

        // Make sure that only the floating pool was regenerated
        expectedModified.add(p);
        verify(this.manager).updateFloatingPools(eq(new LinkedList()), eq(true), any(Map.class));
        ArgumentCaptor<Pool> argPool = ArgumentCaptor.forClass(Pool.class);
        verify(this.manager).updatePoolsForMasterPool(eq(expectedModified), argPool.capture(),
            eq(sub.getQuantity()), eq(false), any(Map.class));
        TestUtil.assertPoolsAreEqual(TestUtil.copyFromSub(sub), argPool.getValue());
    }

    private void mockProduct(Owner owner, Product p) {
        when(mockOwnerProductCurator.getProductById(eq(owner), eq(p.getId()))).thenReturn(p);
    }

    private void mockProducts(Owner owner, final Map<String, Product> products) {
        when(mockOwnerProductCurator.getProductById(eq(owner), any(String.class)))
            .thenAnswer(new Answer<Product>() {
                @Override
                public Product answer(InvocationOnMock invocation) throws Throwable {
                    Object[] args = invocation.getArguments();
                    String pid = (String) args[1];

                    return products.get(pid);
                }
            });

        when(mockOwnerProductCurator.getProductsByIds(eq(owner), any(Collection.class)))
            .thenAnswer(new Answer<CandlepinQuery<Product>>() {
                @Override
                public CandlepinQuery<Product> answer(InvocationOnMock invocation) throws Throwable {
                    Object[] args = invocation.getArguments();
                    Collection<String> pids = (Collection<String>) args[1];
                    List<Product> output = new LinkedList<Product>();

                    for (String pid : pids) {
                        Product product = products.get(pid);

                        if (product != null) {
                            output.add(product);
                        }
                    }

                    CandlepinQuery cqmock = mock(CandlepinQuery.class);
                    when(cqmock.list()).thenReturn(output);
                    when(cqmock.iterator()).thenReturn(output.iterator());
                    when(cqmock.iterate()).thenReturn(new MockResultIterator(output.iterator()));

                    return cqmock;
                }
            });
    }

    private void mockProducts(Owner owner, Product... products) {
        Map<String, Product> productMap = new HashMap<String, Product>();

        for (Product product : products) {
            productMap.put(product.getId(), product);
        }

        this.mockProducts(owner, productMap);
    }

    private void mockProductImport(Owner owner, final Map<String, Product> products) {
        when(mockProductManager.importProducts(eq(owner), any(Map.class), any(Map.class)))
            .thenAnswer(new Answer<ImportResult<Product>>() {
                @Override
                public ImportResult<Product> answer(InvocationOnMock invocation) throws Throwable {
                    Object[] args = invocation.getArguments();
                    Map<String, ProductData> productData = (Map<String, ProductData>) args[1];
                    ImportResult<Product> importResult = new ImportResult<Product>();
                    Map<String, Product> output = importResult.getCreatedEntities();

                    if (productData != null) {
                        for (String pid : productData.keySet()) {
                            Product product = products.get(pid);

                            if (product != null) {
                                output.put(product.getId(), product);
                            }
                        }
                    }

                    return importResult;
                }
            });
    }

    private void mockProductImport(Owner owner, Product... products) {
        Map<String, Product> productMap = new HashMap<String, Product>();

        for (Product product : products) {
            productMap.put(product.getId(), product);
        }

        this.mockProductImport(owner, productMap);
    }

    private void mockContentImport(Owner owner, final Map<String, Content> contents) {
        when(mockContentManager.importContent(eq(owner), any(Map.class), any(Set.class)))
            .thenAnswer(new Answer<ImportResult<Content>>() {
                @Override
                public ImportResult<Content> answer(InvocationOnMock invocation) throws Throwable {
                    Object[] args = invocation.getArguments();
                    Map<String, ContentData> contentData = (Map<String, ContentData>) args[1];
                    ImportResult<Content> importResult = new ImportResult<Content>();
                    Map<String, Content> output = importResult.getCreatedEntities();

                    if (contentData != null) {
                        for (String pid : contentData.keySet()) {
                            Content content = contents.get(pid);

                            if (content != null) {
                                output.put(content.getId(), content);
                            }
                        }
                    }

                    return importResult;
                }
            });
    }

    private void mockContentImport(Owner owner, Content... contents) {
        Map<String, Content> contentMap = new HashMap<String, Content>();

        for (Content content : contents) {
            contentMap.put(content.getId(), content);
        }

        this.mockContentImport(owner, contentMap);
    }

    @Test
    public void productAttributesCopiedOntoPoolWhenCreatingNewPool() {
        // Why is this test in pool manager? It looks like a pool rules test.

        PoolRules pRules = new PoolRules(manager, mockConfig, entitlementCurator,
            mockOwnerProductCurator, mockProductCurator);
        Product product = TestUtil.createProduct();
        product.setLocked(true);

        String testAttributeKey = "multi-entitlement";
        String expectedAttributeValue = "yes";
        product.setAttribute(testAttributeKey, expectedAttributeValue);

        Subscription sub = TestUtil.createSubscription(owner, product);

        this.mockProducts(owner, product);

        List<Pool> pools = pRules.createAndEnrichPools(sub);
        assertEquals(1, pools.size());

        Pool resultPool = pools.get(0);
        assertNotNull(resultPool.getProduct());
        assertTrue(resultPool.getProduct().hasAttribute(testAttributeKey));
        assertEquals(expectedAttributeValue, resultPool.getProduct().getAttributeValue(testAttributeKey));
    }

    @Test
    public void subProductAttributesCopiedOntoPoolWhenCreatingNewPool() {
        Product product = TestUtil.createProduct();
        Product subProduct = TestUtil.createProduct();

        Subscription sub = TestUtil.createSubscription(owner, product);
        sub.setDerivedProduct(subProduct.toDTO());
        String testAttributeKey = "multi-entitlement";
        String expectedAttributeValue = "yes";
        subProduct.setAttribute(testAttributeKey, expectedAttributeValue);

        this.mockProducts(owner, product, subProduct);

        PoolRules pRules = new PoolRules(manager, mockConfig, entitlementCurator,
            mockOwnerProductCurator, mockProductCurator);
        List<Pool> pools = pRules.createAndEnrichPools(sub);
        assertEquals(1, pools.size());

        Pool resultPool = pools.get(0);
        assertNotNull(resultPool.getDerivedProduct());
        assertTrue(resultPool.getDerivedProduct().hasAttribute(testAttributeKey));
        assertEquals(expectedAttributeValue,
            resultPool.getDerivedProduct().getAttributeValue(testAttributeKey));
    }

    @Test
    public void subProductIdCopiedOntoPoolWhenCreatingNewPool() {
        Product product = TestUtil.createProduct();
        Product subProduct = TestUtil.createProduct();

        Subscription sub = TestUtil.createSubscription(owner, product);
        sub.setDerivedProduct(subProduct.toDTO());

        this.mockProducts(owner, product, subProduct);

        PoolRules pRules = new PoolRules(manager, mockConfig, entitlementCurator,
            mockOwnerProductCurator, mockProductCurator);
        List<Pool> pools = pRules.createAndEnrichPools(sub);
        assertEquals(1, pools.size());

        Pool resultPool = pools.get(0);
        assertEquals(subProduct, resultPool.getDerivedProduct());
    }

    @Test
    public void derivedProvidedProductsCopiedOntoMasterPoolWhenCreatingNewPool() {
        Product product = TestUtil.createProduct();
        Product subProduct = TestUtil.createProduct();
        Product subProvidedProduct = TestUtil.createProduct();

        Subscription sub = TestUtil.createSubscription(owner, product);
        sub.setDerivedProduct(subProduct.toDTO());
        Set<ProductData> subProvided = new HashSet<ProductData>();
        subProvided.add(subProvidedProduct.toDTO());
        sub.setDerivedProvidedProducts(subProvided);

        this.mockProducts(owner, product, subProduct, subProvidedProduct);

        PoolRules pRules = new PoolRules(manager, mockConfig, entitlementCurator,
            mockOwnerProductCurator, mockProductCurator);
        List<Pool> pools = pRules.createAndEnrichPools(sub);
        assertEquals(1, pools.size());

        Pool resultPool = pools.get(0);
        assertEquals(1, resultPool.getDerivedProvidedProducts().size());
    }

    @Test
    public void brandingCopiedWhenCreatingPools() {
        Product product = TestUtil.createProduct();

        Subscription sub = TestUtil.createSubscription(owner, product);
        Branding b1 = new Branding("8000", "OS", "Branded Awesome OS");
        Branding b2 = new Branding("8001", "OS", "Branded Awesome OS 2");
        sub.getBranding().add(b1);
        sub.getBranding().add(b2);

        this.mockProducts(owner, product);

        PoolRules pRules = new PoolRules(manager, mockConfig, entitlementCurator,
            mockOwnerProductCurator, mockProductCurator);
        List<Pool> pools = pRules.createAndEnrichPools(sub);
        assertEquals(1, pools.size());

        Pool resultPool = pools.get(0);
        assertEquals(2, resultPool.getBranding().size());
        assertTrue(resultPool.getBranding().contains(b1));
        assertTrue(resultPool.getBranding().contains(b2));
    }

    @Test
    public void testRefreshPoolsDeletesOrphanedPools() {
        List<Subscription> subscriptions = Util.newList();
        List<Pool> pools = Util.newList();
        Product product = TestUtil.createProduct();
        Pool p = TestUtil.createPool(product);
        p.setSourceSubscription(new SourceSubscription("112", "master"));
        pools.add(p);

        mockSubsList(subscriptions);
        mockPoolsList(pools);

        CandlepinQuery<Pool> cqmock = mock(CandlepinQuery.class);
        when(cqmock.list()).thenReturn(pools);
        when(cqmock.iterator()).thenReturn(pools.iterator());
        when(mockPoolCurator.listByOwnerAndType(eq(owner), any(PoolType.class))).thenReturn(cqmock);

        this.mockProductImport(owner, product);
        this.mockContentImport(owner, new Content[] {});

        Owner owner = getOwner();
        when(mockOwnerCurator.lookupByKey(owner.getKey())).thenReturn(owner);
        this.manager.getRefresher(mockSubAdapter, mockOwnerAdapter).add(owner).run();
        List<Pool> poolsToDelete = Arrays.asList(p);
        verify(this.manager).deletePools(eq(poolsToDelete));
    }

    @Test
    public void testRefreshPoolsDeletesOrphanedHostedVirtBonusPool() {
        List<Subscription> subscriptions = Util.newList();
        List<Pool> pools = Util.newList();
        Product product = TestUtil.createProduct();
        Pool p = TestUtil.createPool(product);
        p.setSourceSubscription(new SourceSubscription("112", "master"));

        // Make it look like a hosted virt bonus pool:
        p.setAttribute(Pool.Attributes.DERIVED_POOL, "true");
        p.setSourceStack(null);
        p.setSourceEntitlement(null);

        pools.add(p);
        mockSubsList(subscriptions);
        mockPoolsList(pools);

        Owner owner = getOwner();
        when(mockOwnerCurator.lookupByKey(owner.getKey())).thenReturn(owner);

        this.mockProductImport(owner, product);
        this.mockContentImport(owner, new Content[] {});

        CandlepinQuery<Pool> cqmock = mock(CandlepinQuery.class);
        when(cqmock.list()).thenReturn(pools);
        when(cqmock.iterator()).thenReturn(pools.iterator());
        when(mockPoolCurator.listByOwnerAndType(eq(owner), any(PoolType.class))).thenReturn(cqmock);

        this.manager.getRefresher(mockSubAdapter, mockOwnerAdapter).add(owner).run();
        List<Pool> delPools = Arrays.asList(p);
        verify(this.manager).deletePools(eq(delPools));
    }

    @Test
    public void testRefreshPoolsSkipsOrphanedEntitlementDerivedPools() {
        List<Subscription> subscriptions = Util.newList();
        List<Pool> pools = Util.newList();
        Product product = TestUtil.createProduct();
        Pool p = TestUtil.createPool(product);
        p.setSourceSubscription(new SourceSubscription("112", "master"));

        // Mock a pool with a source entitlement:
        p.setAttribute(Pool.Attributes.DERIVED_POOL, "true");
        p.setSourceStack(null);
        p.setSourceEntitlement(new Entitlement());

        pools.add(p);
        mockSubsList(subscriptions);
        mockPoolsList(pools);

        Owner owner = getOwner();
        when(mockOwnerCurator.lookupByKey(owner.getKey())).thenReturn(owner);

        this.mockProductImport(owner, product);
        this.mockContentImport(owner, new Content[] {});

        CandlepinQuery<Pool> cqmock = mock(CandlepinQuery.class);
        when(cqmock.list()).thenReturn(pools);
        when(cqmock.iterator()).thenReturn(pools.iterator());
        when(mockPoolCurator.listByOwnerAndType(eq(owner), any(PoolType.class))).thenReturn(cqmock);

        this.manager.getRefresher(mockSubAdapter, mockOwnerAdapter).add(owner).run();
        verify(this.manager, never()).deletePool(same(p));
    }

    @Test
    public void testRefreshPoolsSkipsOrphanedStackDerivedPools() {
        List<Subscription> subscriptions = Util.newList();
        List<Pool> pools = Util.newList();
        Product product = TestUtil.createProduct();
        Pool p = TestUtil.createPool(product);
        p.setSourceSubscription(new SourceSubscription("112", "master"));

        // Mock a pool with a source stack ID:
        p.setAttribute(Pool.Attributes.DERIVED_POOL, "true");
        p.setSourceStack(new SourceStack(new Consumer(), "blah"));
        p.setSourceEntitlement(null);

        pools.add(p);
        mockSubsList(subscriptions);
        mockPoolsList(pools);

        this.mockProductImport(owner, product);
        this.mockContentImport(owner, new Content[] {});

        CandlepinQuery<Pool> cqmock = mock(CandlepinQuery.class);
        when(cqmock.list()).thenReturn(pools);
        when(cqmock.iterator()).thenReturn(pools.iterator());
        when(mockPoolCurator.listByOwnerAndType(eq(owner), any(PoolType.class))).thenReturn(cqmock);

        Owner owner = getOwner();
        when(mockOwnerCurator.lookupByKey(owner.getKey())).thenReturn(owner);
        this.manager.getRefresher(mockSubAdapter, mockOwnerAdapter).add(owner).run();
        verify(this.manager, never()).deletePool(same(p));
    }

    @Test
    public void testRefreshPoolsSkipsDevelopmentPools() {
        List<Subscription> subscriptions = Util.newList();
        List<Pool> pools = Util.newList();
        Product product = TestUtil.createProduct();
        Pool p = TestUtil.createPool(product);
        p.setSourceSubscription(null);

        // Mock a development pool
        p.setAttribute(Pool.Attributes.DEVELOPMENT_POOL, "true");

        pools.add(p);
        mockSubsList(subscriptions);
        mockPoolsList(pools);

        CandlepinQuery<Pool> cqmock = mock(CandlepinQuery.class);
        when(cqmock.list()).thenReturn(pools);
        when(cqmock.iterator()).thenReturn(pools.iterator());
        when(mockPoolCurator.listByOwnerAndType(eq(owner), any(PoolType.class))).thenReturn(cqmock);

        this.mockProductImport(owner, product);
        this.mockContentImport(owner, new Content[] {});

        Owner owner = getOwner();
        when(mockOwnerCurator.lookupByKey(owner.getKey())).thenReturn(owner);

        this.manager.getRefresher(mockSubAdapter, mockOwnerAdapter).add(owner).run();
        verify(this.manager, never()).deletePool(same(p));
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testRefreshPoolsSortsStackDerivedPools() {
        List<Subscription> subscriptions = Util.newList();
        List<Pool> pools = Util.newList();

        // Pool has no subscription ID:
        Product product = TestUtil.createProduct();
        Pool p = TestUtil.createPool(product);
        p.setSourceStack(new SourceStack(new Consumer(), "a"));

        pools.add(p);
        mockSubsList(subscriptions);
        mockPoolsList(pools);

        Owner owner = getOwner();
        when(mockOwnerCurator.lookupByKey(owner.getKey())).thenReturn(owner);

        this.mockProductImport(owner, product);
        this.mockContentImport(owner, new Content[] {});

        CandlepinQuery<Pool> cqmock = mock(CandlepinQuery.class);
        when(cqmock.list()).thenReturn(pools);
        when(cqmock.iterator()).thenReturn(pools.iterator());
        when(mockPoolCurator.listByOwnerAndType(eq(owner), any(PoolType.class))).thenReturn(cqmock);

        this.manager.getRefresher(mockSubAdapter, mockOwnerAdapter).add(owner).run();
        ArgumentCaptor<List> poolCaptor = ArgumentCaptor.forClass(List.class);
        verify(this.poolRulesMock).updatePools(poolCaptor.capture(), any(Map.class));
        assertEquals(1, poolCaptor.getValue().size());
        assertEquals(p, poolCaptor.getValue().get(0));
    }

    @Test
    public void refreshPoolsCreatingPoolsForExistingSubscriptions() {
        List<Subscription> subscriptions = Util.newList();
        List<Pool> pools = Util.newList();

        Owner owner = this.getOwner();
        Product product = TestUtil.createProduct();
        product.setLocked(true);

        Subscription s = TestUtil.createSubscription(owner, product);
        subscriptions.add(s);

        mockSubsList(subscriptions);
        mockPoolsList(pools);

        List<Pool> newPools = new LinkedList<Pool>();
        Pool p = TestUtil.createPool(product);
        p.setId("test");
        p.setSourceSubscription(new SourceSubscription(s.getId(), "master"));
        newPools.add(p);
        ArgumentCaptor<Pool> argPool = ArgumentCaptor.forClass(Pool.class);

        when(poolRulesMock.createAndEnrichPools(argPool.capture(), any(List.class))).thenReturn(newPools);
        when(mockOwnerCurator.lookupByKey(owner.getKey())).thenReturn(owner);
        this.mockProducts(owner, product);
        this.mockProductImport(owner, product);
        this.mockContentImport(owner, new Content[] {});

        CandlepinQuery<Pool> cqmock = mock(CandlepinQuery.class);
        when(cqmock.list()).thenReturn(pools);
        when(cqmock.iterator()).thenReturn(pools.iterator());
        when(mockPoolCurator.listByOwnerAndType(eq(owner), any(PoolType.class))).thenReturn(cqmock);

        this.manager.getRefresher(mockSubAdapter, mockOwnerAdapter).add(owner).run();

        TestUtil.assertPoolsAreEqual(TestUtil.copyFromSub(s), argPool.getValue());
        verify(this.mockPoolCurator, times(1)).create(any(Pool.class));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void refreshPoolsCleanupPoolThatLostVirtLimit() {
        List<Subscription> subscriptions = Util.newList();
        List<Pool> pools = Util.newList();

        Owner owner = getOwner();
        Product product = TestUtil.createProduct();
        product.setLocked(true);

        Subscription s = TestUtil.createSubscription(owner, product);
        s.setId("01923");
        subscriptions.add(s);
        Pool p = TestUtil.createPool(product);
        p.setSourceSubscription(new SourceSubscription(s.getId(), "master"));
        p.setMarkedForDelete(true);
        p.setOwner(owner);
        pools.add(p);

        mockSubsList(subscriptions);

        mockPoolsList(pools);

        List<PoolUpdate> updates = new LinkedList();
        PoolUpdate u = new PoolUpdate(p);
        u.setQuantityChanged(true);
        u.setOrderChanged(true);
        updates.add(u);
        ArgumentCaptor<Pool> argPool = ArgumentCaptor.forClass(Pool.class);
        when(poolRulesMock.updatePools(argPool.capture(), eq(pools), eq(s.getQuantity()), any(Map.class)))
            .thenReturn(updates);

        when(mockOwnerCurator.lookupByKey(owner.getKey())).thenReturn(owner);
        this.mockProducts(owner, product);
        this.mockProductImport(owner, product);
        this.mockContentImport(owner, new Content[] {});

        CandlepinQuery<Pool> cqmock = mock(CandlepinQuery.class);
        when(cqmock.list()).thenReturn(pools);
        when(cqmock.iterator()).thenReturn(pools.iterator());
        when(mockPoolCurator.listByOwnerAndType(eq(owner), any(PoolType.class))).thenReturn(cqmock);

        this.manager.getRefresher(mockSubAdapter, mockOwnerAdapter).add(owner).run();
        verify(poolRulesMock).createAndEnrichPools(argPool.capture(), any(List.class));
        TestUtil.assertPoolsAreEqual(TestUtil.copyFromSub(s), argPool.getValue());
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
        Product product = TestUtil.createProduct();
        Subscription s = TestUtil.createSubscription(getOwner(), product);

        List<Pool> newPools = new LinkedList<Pool>();
        Pool p = TestUtil.createPool(product);
        p.setSourceSubscription(new SourceSubscription(s.getId(), "master"));
        newPools.add(p);
        when(poolRulesMock.createAndEnrichPools(eq(s), any(List.class))).thenReturn(newPools);

        this.manager.createAndEnrichPools(s);
        verify(this.mockPoolCurator, times(1)).create(any(Pool.class));
    }

    @Test
    public void testCreateAndEnrichPoolForPool() {
        List<Pool> newPools = new LinkedList<Pool>();
        Pool p = TestUtil.createPool(TestUtil.createProduct());
        newPools.add(p);
        when(poolRulesMock.createAndEnrichPools(eq(p), any(List.class))).thenReturn(newPools);

        this.manager.createAndEnrichPools(p);
        verify(this.mockPoolCurator, times(1)).create(any(Pool.class));
    }

    @Test
    public void testRevokeAllEntitlements() {
        Consumer c = TestUtil.createConsumer(owner);

        Entitlement e1 = new Entitlement(pool, c, 1);
        Entitlement e2 = new Entitlement(pool, c, 1);
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
        //TODO assert batch revokes have been called
    }

    @Test
    public void testRevokeCleansUpPoolsWithSourceEnt() throws Exception {
        Entitlement e = new Entitlement(pool, TestUtil.createConsumer(owner), 1);
        List<Pool> poolsWithSource = createPoolsWithSourceEntitlement(e, product);

        CandlepinQuery<Pool> cqmock = mock(CandlepinQuery.class);
        when(cqmock.list()).thenReturn(poolsWithSource);

        when(mockPoolCurator.listBySourceEntitlement(e)).thenReturn(cqmock);
        PreUnbindHelper preHelper =  mock(PreUnbindHelper.class);
        ValidationResult result = new ValidationResult();
        when(preHelper.getResult()).thenReturn(result);
        when(mockConfig.getBoolean(ConfigProperties.STANDALONE)).thenReturn(true);

        when(mockPoolCurator.lockAndLoad(any(Pool.class))).thenReturn(pool);

        manager.revokeEntitlement(e);
        List<Entitlement> entsToDelete = Arrays.asList(e);
        verify(entitlementCurator).batchDelete(eq(entsToDelete));
    }

    @Test
    public void testBatchRevokeCleansUpCorrectPoolsWithSourceEnt() throws Exception {
        Consumer c = TestUtil.createConsumer(owner);
        Pool pool2 = TestUtil.createPool(owner, product);

        Entitlement e = new Entitlement(pool, c, 1);
        Entitlement e2 = new Entitlement(pool2, c, 1);
        Entitlement e3 = new Entitlement(pool2, c, 1);

        List<Entitlement> entsToDelete = Util.newList();
        entsToDelete.add(e);
        entsToDelete.add(e2);

        List<Pool> poolsWithSource = createPoolsWithSourceEntitlement(e, product);
        poolsWithSource.get(0).getEntitlements().add(e3);
        when(mockPoolCurator.listBySourceEntitlements(entsToDelete)).thenReturn(poolsWithSource);

        PreUnbindHelper preHelper = mock(PreUnbindHelper.class);
        ValidationResult result = new ValidationResult();
        when(preHelper.getResult()).thenReturn(result);

        when(mockConfig.getBoolean(ConfigProperties.STANDALONE)).thenReturn(true);

        when(mockPoolCurator.lockAndLoad(eq(pool))).thenReturn(pool);
        when(mockPoolCurator.lockAndLoad(eq(pool2))).thenReturn(pool2);

        manager.revokeEntitlements(entsToDelete);
        entsToDelete.add(e3);
        verify(entitlementCurator).batchDelete(eq(entsToDelete));
        verify(mockPoolCurator).batchDelete(eq(poolsWithSource), anySetOf(String.class));
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
            any(Owner.class), any(String.class), any(String.class), eq(now),
            any(PoolFilterBuilder.class), any(PageRequest.class), anyBoolean(), anyBoolean(), anyBoolean()))
            .thenReturn(page);

        when(mockPoolCurator.lockAndLoadByIds(any(List.class))).thenReturn(Arrays.asList(pool1));
        when(enforcerMock.preEntitlement(any(Consumer.class), any(Pool.class), anyInt(),
            any(CallerType.class))).thenReturn(result);

        when(result.isSuccessful()).thenReturn(true);

        List<PoolQuantity> bestPools = new ArrayList<PoolQuantity>();
        bestPools.add(new PoolQuantity(pool1, 1));
        when(autobindRules.selectBestPools(any(Consumer.class), any(String[].class),
            any(List.class), any(ComplianceStatus.class), any(String.class),
            any(Set.class), eq(false)))
            .thenReturn(bestPools);

        AutobindData data = AutobindData.create(TestUtil.createConsumer(owner))
            .forProducts(new String[] { product.getUuid() }).on(now);

        doNothing().when(mockPoolCurator).flush();
        doNothing().when(mockPoolCurator).clear();
        List<Entitlement> e = manager.entitleByProducts(data);

        assertNotNull(e);
        assertEquals(e.size(), 1);
    }

    @Test
    public void testEntitlebyProductRetry() throws Exception {
        Product product = TestUtil.createProduct();
        List<Pool> pools = Util.newList();
        Pool pool1 = TestUtil.createPool(product);
        pool1.setId("poolId1");
        pools.add(pool1);
        Pool pool2 = TestUtil.createPool(product);
        pool2.setId("poolId2");
        pools.add(pool2);
        Date now = new Date();

        Map<String, ValidationResult> resultMap = new HashMap<String, ValidationResult>();
        ValidationResult result = mock(ValidationResult.class);
        resultMap.put("poolId1", result);
        resultMap.put("poolId2", result);
        Page page = mock(Page.class);

        when(page.getPageData()).thenReturn(pools);
        when(mockPoolCurator.listAvailableEntitlementPools(any(Consumer.class), any(Owner.class),
            any(String.class), any(String.class), eq(now),
            any(PoolFilterBuilder.class), any(PageRequest.class), anyBoolean(), anyBoolean(), anyBoolean()))
            .thenReturn(page);

        when(mockPoolCurator.lockAndLoad(any(Pool.class))).thenReturn(pool1);
        when(mockPoolCurator.lockAndLoadByIds(any(List.class))).thenReturn(Arrays.asList(pool1));
        when(enforcerMock.preEntitlement(any(Consumer.class), anyCollectionOf(PoolQuantity.class),
            any(CallerType.class))).thenReturn(resultMap);

        when(result.isSuccessful()).thenReturn(false);
        List<ValidationError> errors = new ArrayList<ValidationError>();
        errors.add(new ValidationError("rulefailed.no.entitlements.available"));
        when(result.getErrors()).thenReturn(errors);
        List<PoolQuantity> bestPools = new ArrayList<PoolQuantity>();
        bestPools.add(new PoolQuantity(pool1, 1));
        when(autobindRules.selectBestPools(any(Consumer.class), any(String[].class), any(List.class),
            any(ComplianceStatus.class), any(String.class), any(Set.class), eq(false))).thenReturn(bestPools);

        AutobindData data = AutobindData.create(TestUtil.createConsumer(owner))
            .forProducts(new String[] { product.getUuid() }).on(now);

        doNothing().when(mockPoolCurator).flush();

        try {
            List<Entitlement> e = manager.entitleByProducts(data);
            fail();
        }
        catch (EntitlementRefusedException e) {
            assertNotNull(e);
            verify(autobindRules, times(4)).selectBestPools(any(Consumer.class), any(String[].class),
                any(List.class), any(ComplianceStatus.class), any(String.class), any(Set.class),
                eq(false));
        }
    }

    @Test
    public void testRefreshPoolsRemovesExpiredSubscriptionsAlongWithItsPoolsAndEnts() {
        PreUnbindHelper preHelper =  mock(PreUnbindHelper.class);

        Date expiredStart = TestUtil.createDate(2004, 5, 5);
        Date expiredDate = TestUtil.createDate(2005, 5, 5);

        List<Subscription> subscriptions = Util.newList();

        Owner owner = this.getOwner();
        Product product = TestUtil.createProduct();

        Subscription sub = TestUtil.createSubscription(owner, product);
        sub.setStartDate(expiredStart);
        sub.setEndDate(expiredDate);
        sub.setId("123");
        subscriptions.add(sub);

        mockSubsList(subscriptions);

        List<Pool> pools = Util.newList();
        Pool p = TestUtil.createPool(owner, product);
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
        p.getEntitlements().addAll(poolEntitlements);

        ValidationResult result = new ValidationResult();
        when(preHelper.getResult()).thenReturn(result);
        when(mockOwnerCurator.lookupByKey(owner.getKey())).thenReturn(owner);
        this.mockProducts(owner, product);
        this.mockProductImport(owner, product);
        this.mockContentImport(owner, new Content[] {});

        CandlepinQuery<Pool> cqmock = mock(CandlepinQuery.class);
        when(cqmock.list()).thenReturn(pools);
        when(cqmock.iterator()).thenReturn(pools.iterator());
        when(mockPoolCurator.listByOwnerAndType(eq(owner), any(PoolType.class))).thenReturn(cqmock);

        this.manager.getRefresher(mockSubAdapter, mockOwnerAdapter).add(owner).run();

        List<Entitlement> entsToDelete = Arrays.asList(ent);
        List<Pool> poolsToDelete = Arrays.asList(p);
        verify(mockPoolCurator).batchDelete(eq(poolsToDelete), anySetOf(String.class));
        verify(entitlementCurator).batchDelete(eq(entsToDelete));
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
        when(mockPoolCurator.entitlementsIn(p)).thenReturn(new ArrayList<Entitlement>(p.getEntitlements()));
        PreUnbindHelper preHelper =  mock(PreUnbindHelper.class);
        ValidationResult result = new ValidationResult();
        when(preHelper.getResult()).thenReturn(result);

        manager.deletePool(p);

        // And the pool should be deleted:
        verify(mockPoolCurator).delete(p);

        // Check that appropriate events were sent out:
        verify(eventFactory).poolDeleted(p);
        verify(mockEventSink, times(1)).queueEvent((Event) any());
    }

    @Test
    public void testCleanupExpiredPools() {
        Pool p = createPoolWithEntitlements();
        p.setSubscriptionId("subid");
        List<Pool> pools = new LinkedList<Pool>();
        pools.add(p);

        when(mockPoolCurator.lockAndLoad(any(Pool.class))).thenReturn(p);
        when(mockPoolCurator.listExpiredPools(anyInt())).thenReturn(pools);
        when(mockPoolCurator.entitlementsIn(p)).thenReturn(new ArrayList<Entitlement>(p.getEntitlements()));
        Subscription sub = new Subscription();
        sub.setId(p.getSubscriptionId());
        when(mockSubAdapter.getSubscription(any(String.class))).thenReturn(sub);
        when(mockSubAdapter.isReadOnly()).thenReturn(false);
        PreUnbindHelper preHelper =  mock(PreUnbindHelper.class);
        ValidationResult result = new ValidationResult();
        when(preHelper.getResult()).thenReturn(result);

        manager.cleanupExpiredPools();

        // And the pool should be deleted:
        verify(mockPoolCurator).batchDelete(eq(pools), anySetOf(String.class));
    }

    @Test
    public void testCleanupExpiredPoolsReadOnlySubscriptions() {
        Pool p = createPoolWithEntitlements();
        p.setSubscriptionId("subid");
        List<Pool> pools = new LinkedList<Pool>();
        pools.add(p);

        when(mockPoolCurator.lockAndLoad(any(Pool.class))).thenReturn(p);
        when(mockPoolCurator.listExpiredPools(anyInt())).thenReturn(pools);
        when(mockPoolCurator.entitlementsIn(p)).thenReturn(new ArrayList<Entitlement>(p.getEntitlements()));
        Subscription sub = new Subscription();
        sub.setId(p.getSubscriptionId());
        when(mockSubAdapter.getSubscription(any(String.class))).thenReturn(sub);
        when(mockSubAdapter.isReadOnly()).thenReturn(true);
        PreUnbindHelper preHelper =  mock(PreUnbindHelper.class);
        ValidationResult result = new ValidationResult();
        when(preHelper.getResult()).thenReturn(result);

        manager.cleanupExpiredPools();

        // And the pool should be deleted:
        verify(mockPoolCurator).batchDelete(eq(pools), anySetOf(String.class));
        verify(mockSubAdapter, never()).getSubscription(any(String.class));
        verify(mockSubAdapter, never()).deleteSubscription(any(Subscription.class));
    }

    private Pool createPoolWithEntitlements() {
        Pool newPool = TestUtil.createPool(owner, product);
        Entitlement e1 = new Entitlement(newPool, TestUtil.createConsumer(owner), 1);
        e1.setId("1");

        Entitlement e2 = new Entitlement(newPool, TestUtil.createConsumer(owner), 1);
        e2.setId("2");

        newPool.getEntitlements().add(e1);
        newPool.getEntitlements().add(e2);
        return newPool;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
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
        String [] installedPids = new String [] { product.getUuid() };
        ComplianceStatus mockCompliance = new ComplianceStatus(now);
        mockCompliance.addNonCompliantProduct(installedPids[0]);
        when(complianceRules.getStatus(any(Consumer.class),
            any(Date.class), any(Boolean.class))).thenReturn(mockCompliance);

        Page page = mock(Page.class);
        when(page.getPageData()).thenReturn(pools);

        when(mockPoolCurator.listAvailableEntitlementPools(any(Consumer.class),
            any(Owner.class), anyString(), anyString(), eq(now),
            any(PoolFilterBuilder.class),
            any(PageRequest.class), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(page);

        when(mockPoolCurator.lockAndLoadByIds(anyListOf(String.class))).thenReturn(pools);
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
        AutobindData data = AutobindData.create(TestUtil.createConsumer(owner)).on(now);
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

        Owner owner = this.getOwner();
        Product product = TestUtil.createProduct();
        product.setLocked(true);

        Subscription sub = TestUtil.createSubscription(owner, product);
        sub.setId("123");
        subscriptions.add(sub);

        mockSubsList(subscriptions);

        List<Pool> pools = Util.newList();
        Pool p = TestUtil.copyFromSub(sub);
        p.setOwner(other);
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
        when(mockOwnerCurator.lookupByKey(owner.getKey())).thenReturn(owner);
        this.mockProducts(owner, product);
        this.mockProductImport(owner, product);
        this.mockContentImport(owner, new Content[] {});

        CandlepinQuery<Pool> cqmock = mock(CandlepinQuery.class);
        when(cqmock.list()).thenReturn(pools);
        when(cqmock.iterator()).thenReturn(pools.iterator());
        when(mockPoolCurator.listByOwnerAndType(eq(owner), any(PoolType.class))).thenReturn(cqmock);

        this.manager.getRefresher(mockSubAdapter, mockOwnerAdapter).add(owner).run();

        // The pool left over from the pre-migrated subscription should be deleted
        // and granted entitlements should be revoked
        List<Entitlement> entsToDelete = Arrays.asList(ent);

        verify(mockPoolCurator).delete(eq(p));
        verify(entitlementCurator).batchDelete(eq(entsToDelete));
        // Make sure pools that don't match the owner were removed from the list
        // They shouldn't cause us to attempt to update existing pools when we
        // haven't created them in the first place
        ArgumentCaptor<Pool> argPool = ArgumentCaptor.forClass(Pool.class);
        verify(poolRulesMock).createAndEnrichPools(argPool.capture(), any(List.class));
        TestUtil.assertPoolsAreEqual(TestUtil.copyFromSub(sub), argPool.getValue());
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
                @SuppressWarnings("unchecked")
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
            mockOwnerProductCurator, mockProductCurator);
        List<Subscription> subscriptions = Util.newList();
        Product prod = TestUtil.createProduct();
        Set<Product> products = new HashSet<Product>();
        products.add(prod);
        prod.setAttribute(Product.Attributes.VIRT_LIMIT, "4");
        // productCache.addProducts(products);
        Subscription s = TestUtil.createSubscription(owner, prod);
        subscriptions.add(s);

        this.mockProducts(owner, prod);

        when(mockSubAdapter.getSubscriptions(any(Owner.class))).thenReturn(
            subscriptions);
        when(mockConfig.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);

        List<Pool> existingPools = new LinkedList<Pool>();
        List<Pool> newPools = pRules.createAndEnrichPools(s, existingPools);

        assertEquals(newPools.size(), 2);
        assertTrue(newPools.get(0).getSourceSubscription().getSubscriptionSubKey().equals("derived") ||
            newPools.get(1).getSourceSubscription().getSubscriptionSubKey().equals("derived"));
        assertTrue(newPools.get(0).getSourceSubscription().getSubscriptionSubKey().startsWith("master") ||
            newPools.get(1).getSourceSubscription().getSubscriptionSubKey().startsWith("master"));
    }

    @Test
    public void createPoolsForExistingPoolNoneExist() {
        Owner owner = this.getOwner();
        PoolRules pRules = new PoolRules(manager, mockConfig, entitlementCurator,
            mockOwnerProductCurator, mockProductCurator);
        Product prod = TestUtil.createProduct();
        prod.setAttribute(Product.Attributes.VIRT_LIMIT, "4");
        Pool p = TestUtil.createPool(owner, prod);
        List<Pool> existingPools = new LinkedList<Pool>();
        List<Pool> newPools = pRules.createAndEnrichPools(p, existingPools);

        assertEquals(2, newPools.size());

        assertTrue(newPools.get(0).getSourceSubscription().getSubscriptionSubKey().equals("derived") ||
            newPools.get(1).getSourceSubscription().getSubscriptionSubKey().equals("derived"));
        assertTrue(newPools.get(0).getSourceSubscription().getSubscriptionSubKey().startsWith("master") ||
            newPools.get(1).getSourceSubscription().getSubscriptionSubKey().startsWith("master"));
    }

    @Test
    public void createPoolsForExistingSubscriptionsMasterExist() {
        Owner owner = this.getOwner();
        PoolRules pRules = new PoolRules(manager, mockConfig, entitlementCurator,
            mockOwnerProductCurator, mockProductCurator);
        List<Subscription> subscriptions = Util.newList();
        Product prod = TestUtil.createProduct();
        Set<Product> products = new HashSet<Product>();
        products.add(prod);
        // productCache.addProducts(products);
        prod.setAttribute(Product.Attributes.VIRT_LIMIT, "4");
        Subscription s = TestUtil.createSubscription(owner, prod);
        subscriptions.add(s);

        this.mockProducts(owner, prod);
        when(mockSubAdapter.getSubscriptions(any(Owner.class))).thenReturn(subscriptions);
        when(mockConfig.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);

        List<Pool> existingPools = new LinkedList<Pool>();
        Pool p = TestUtil.createPool(prod);
        p.setSourceSubscription(new SourceSubscription(s.getId(), "master"));
        existingPools.add(p);
        List<Pool> newPools = pRules.createAndEnrichPools(s, existingPools);
        assertEquals(newPools.size(), 1);
        assertEquals(newPools.get(0).getSourceSubscription().getSubscriptionSubKey(), "derived");
    }

    @Test
    public void createPoolsForPoolMasterExist() {
        Owner owner = this.getOwner();
        Product prod = TestUtil.createProduct();
        prod.setAttribute(Product.Attributes.VIRT_LIMIT, "4");
        PoolRules pRules = new PoolRules(manager, mockConfig, entitlementCurator,
            mockOwnerProductCurator, mockProductCurator);
        List<Pool> existingPools = new LinkedList<Pool>();
        Pool p = TestUtil.createPool(prod);
        p.setSourceSubscription(new SourceSubscription(TestUtil.randomString(), "master"));
        existingPools.add(p);
        List<Pool> newPools = pRules.createAndEnrichPools(p, existingPools);
        assertEquals(1, newPools.size());
        assertEquals("derived", newPools.get(0).getSourceSubscription().getSubscriptionSubKey());
    }

    @Test
    public void createPoolsForExistingSubscriptionsBonusExist() {
        Owner owner = this.getOwner();
        PoolRules pRules = new PoolRules(manager, mockConfig, entitlementCurator,
            mockOwnerProductCurator, mockProductCurator);
        List<Subscription> subscriptions = Util.newList();
        Product prod = TestUtil.createProduct();
        Set<Product> products = new HashSet<Product>();
        products.add(prod);
        // productCache.addProducts(products);
        prod.setAttribute(Product.Attributes.VIRT_LIMIT, "4");
        Subscription s = TestUtil.createSubscription(owner, prod);
        subscriptions.add(s);

        this.mockProducts(owner, prod);

        when(mockSubAdapter.getSubscriptions(any(Owner.class))).thenReturn(subscriptions);
        when(mockConfig.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);

        List<Pool> existingPools = new LinkedList<Pool>();
        Pool p = TestUtil.createPool(prod);
        p.setSourceSubscription(new SourceSubscription(s.getId(), "derived"));
        existingPools.add(p);
        pRules.createAndEnrichPools(s, existingPools);
        List<Pool> newPools = pRules.createAndEnrichPools(s, existingPools);
        assertEquals(newPools.size(), 1);
        assertEquals(newPools.get(0).getSourceSubscription().getSubscriptionSubKey(), "master");
    }

    @Test(expected = IllegalStateException.class)
    public void createPoolsForPoolBonusExist() {
        Owner owner = this.getOwner();
        PoolRules pRules = new PoolRules(manager, mockConfig, entitlementCurator,
            mockOwnerProductCurator, mockProductCurator);
        Product prod = TestUtil.createProduct();
        prod.setAttribute(Product.Attributes.VIRT_LIMIT, "4");
        List<Pool> existingPools = new LinkedList<Pool>();
        Pool p = TestUtil.createPool(prod);
        p.setSourceSubscription(new SourceSubscription(TestUtil.randomString(), "derived"));
        existingPools.add(p);
        pRules.createAndEnrichPools(p, existingPools);
        List<Pool> newPools = pRules.createAndEnrichPools(p, existingPools);
        assertEquals(1, newPools.size());
        assertEquals("master", newPools.get(0).getSourceSubscription().getSubscriptionSubKey());
    }

    @Test
    public void testFabricateSubscriptionFromPool() {
        Product product = TestUtil.createProduct("product", "Product");
        Product provided1 = TestUtil.createProduct("provided-1", "Provided 1");
        Product provided2 = TestUtil.createProduct("provided-2", "Provided 2");
        product.setLocked(true);
        provided1.setLocked(true);
        provided2.setLocked(true);

        ProductData productDTO = product.toDTO();
        ProductData provided1DTO = provided1.toDTO();
        ProductData provided2DTO = provided2.toDTO();

        Pool pool = mock(Pool.class);

        HashSet<Product> provided = new HashSet<Product>();
        HashSet<ProductData> providedDTOs = new HashSet<ProductData>();
        provided.add(provided1);
        provided.add(provided2);
        providedDTOs.add(provided1DTO);
        providedDTOs.add(provided2DTO);

        Long quantity = new Long(42);

        Date startDate = new Date(System.currentTimeMillis() - 86400000);
        Date endDate = new Date(System.currentTimeMillis() + 86400000);
        Date updated = new Date();

        String subscriptionId = "test-subscription-1";

        when(pool.getOwner()).thenReturn(owner);
        when(pool.getProduct()).thenReturn(product);
        when(mockProductCurator.getPoolProvidedProductsCached(any(Pool.class))).thenReturn(provided);
        when(pool.getQuantity()).thenReturn(quantity);
        when(pool.getStartDate()).thenReturn(startDate);
        when(pool.getEndDate()).thenReturn(endDate);
        when(pool.getUpdated()).thenReturn(updated);
        when(pool.getSubscriptionId()).thenReturn(subscriptionId);
        // TODO: Add other attributes to check here.

        Subscription fabricated = manager.fabricateSubscriptionFromPool(pool);
        pool.populateAllTransientProvidedProducts(mockProductCurator);
        assertEquals(owner, fabricated.getOwner());
        assertEquals(productDTO, fabricated.getProduct());
        assertEquals(providedDTOs, fabricated.getProvidedProducts());
        assertEquals(quantity, fabricated.getQuantity());
        assertEquals(startDate, fabricated.getStartDate());
        assertEquals(endDate, fabricated.getEndDate());
        assertEquals(updated, fabricated.getModified());
        assertEquals(subscriptionId, fabricated.getId());
    }


    /**
     * See BZ1292283
     */
    @Test
    public void testFabricateSubWithMultiplier() {
        Product product = TestUtil.createProduct("product", "Product");

        Pool pool = mock(Pool.class);

        Long quantity = new Long(22);
        Long multiplier = new Long(2);
        product.setMultiplier(multiplier);

        when(pool.getQuantity()).thenReturn(quantity);
        when(pool.getProduct()).thenReturn(product);
        Subscription fabricated = manager.fabricateSubscriptionFromPool(pool);

        assertEquals((Long) (quantity / multiplier), fabricated.getQuantity());
    }

    @Test
    public void testFabricateSubWithZeroInstanceMultiplier() {
        // Product product = TestUtil.createProduct("product", "Product");

        Pool pool = mock(Pool.class);

        Long quantity = new Long(64);
        Long multiplier = new Long(2);

        product.setMultiplier(multiplier);
        product.setAttribute(Product.Attributes.INSTANCE_MULTIPLIER, "0");

        when(pool.getQuantity()).thenReturn(quantity);
        when(pool.getProduct()).thenReturn(product);
        Subscription fabricated = manager.fabricateSubscriptionFromPool(pool);

        assertEquals((Long) 32L, fabricated.getQuantity());
    }

    @Test
    public void testFabricateSubWithMultiplierAndInstanceMultiplier() {
        Product product = TestUtil.createProduct("product", "Product");

        Pool pool = mock(Pool.class);

        Long quantity = new Long(64);
        Long multiplier = new Long(2);

        product.setMultiplier(multiplier);
        product.setAttribute(Product.Attributes.INSTANCE_MULTIPLIER, "4");

        when(pool.getQuantity()).thenReturn(quantity);
        when(pool.getProduct()).thenReturn(product);
        Subscription fabricated = manager.fabricateSubscriptionFromPool(pool);

        assertEquals((Long) 8L, fabricated.getQuantity());
    }

    private Content buildContent(Owner owner) {
        Content content = new Content();

        int rand = TestUtil.randomInt();
        HashSet<String> modifiedProductIds = new HashSet<String>(
            Arrays.asList("mpid-a-" + rand, "mpid-d-" + rand, "mpid-c-" + rand)
        );

        content.setId("cid" + rand);

        content.setContentUrl("https://www.content_url.com/" + rand);
        content.setGpgUrl("https://www.gpg_url.com/" + rand);
        content.setLabel("content_label-" + rand);
        content.setName("content-" + rand);
        content.setReleaseVersion("content_releasever-" + rand);
        content.setRequiredTags("content_tags-" + rand);
        content.setType("content_type-" + rand);
        content.setVendor("content_vendor-" + rand);
        content.setArches("content_arches-" + rand);
        content.setModifiedProductIds(modifiedProductIds);

        // Since CPM sets all inbound products and content as "locked," we do this to ensure we
        // don't always trigger a change because the lock state looks different from our mocks.
        content.setLocked(true);

        return content;
    }

    @Test
    public void expiredEntitlementEvent() {
        Date now = new Date();

        Product p = TestUtil.createProduct();
        p.setAttribute(Product.Attributes.HOST_LIMITED, "true");
        p.setAttribute(Product.Attributes.VIRT_LIMIT, "unlimited");

        Consumer guest = TestUtil.createConsumer(owner);
        guest.setFact("virt.is_guest", "true");
        guest.addInstalledProduct(new ConsumerInstalledProduct(p));

        Pool pool = TestUtil.createPool(owner, p);
        pool.setAttribute(Pool.Attributes.UNMAPPED_GUESTS_ONLY, "true");
        pool.setAttribute(Pool.Attributes.VIRT_ONLY, "true");
        pool.setAttribute(Pool.Attributes.DERIVED_POOL, "true");
        pool.setAttribute(Pool.Attributes.PHYSICAL_ONLY, "false");
        pool.setAttribute(Product.Attributes.VIRT_LIMIT, "0");
        pool.setStartDate(new Date(now.getTime() - (1000 * 60 * 60 * 24 * 2)));

        Entitlement ent = TestUtil.createEntitlement(owner, guest, pool, null);
        ent.setEndDateOverride(new Date(now.getTime() - (1000 * 60 * 60 * 24 * 1)));
        ent.setId("test-ent-id");
        ent.setQuantity(1);
        Set<Entitlement> entitlements = new HashSet<Entitlement>();
        entitlements.add(ent);
        pool.setEntitlements(entitlements);

        Event event = new Event();
        event.setConsumerId(guest.getUuid());
        event.setOldEntity(ent.getId());
        event.setOwnerId(owner.getId());
        event.setTarget(Target.ENTITLEMENT);
        event.setType(Type.EXPIRED);
        when(eventFactory.entitlementExpired(eq(ent))).thenReturn(event);
        when(mockPoolCurator.lockAndLoad(eq(pool))).thenReturn(pool);
        manager.revokeEntitlement(ent);
        String message = event.getMessageText();
        assertNotNull(message);
        message = message.split(": ")[1];
        assertEquals(message,
            i18n.tr("Unmapped guest entitlement expired without establishing a host/guest mapping."));
    }

    @Test
    public void testDeleteExcessEntitlements() throws EntitlementRefusedException {
        Consumer consumer = TestUtil.createConsumer(owner);
        Subscription sub = TestUtil.createSubscription(owner, product);
        sub.setId("testing-subid");
        pool.setSourceSubscription(new SourceSubscription(sub.getId(), "master"));

        Pool derivedPool = TestUtil.createPool(owner, product, 1);
        derivedPool.setAttribute(Pool.Attributes.DERIVED_POOL, "true");
        derivedPool.setSourceSubscription(new SourceSubscription(sub.getId(), "der"));
        derivedPool.setConsumed(3L);
        derivedPool.setId("derivedPool");
        Entitlement masterEnt = new Entitlement(pool, consumer, 5);
        Entitlement derivedEnt = new Entitlement(derivedPool, consumer, 1);
        derivedEnt.setId("1");

        Set<Entitlement> ents = new HashSet<Entitlement>();
        ents.add(derivedEnt);
        derivedPool.setEntitlements(ents);

        // before
        assertEquals(3, derivedPool.getConsumed().intValue());
        assertEquals(1, derivedPool.getEntitlements().size());

        when(mockPoolCurator.lockAndLoad(pool)).thenReturn(pool);
        when(enforcerMock.update(any(Consumer.class), any(Entitlement.class), any(Integer.class)))
            .thenReturn(new ValidationResult());
        when(mockPoolCurator.lookupOversubscribedBySubscriptionIds(any(Owner.class), anyMap()))
            .thenReturn(Arrays.asList(derivedPool));
        when(mockPoolCurator.retrieveFreeEntitlementsOfPools(anyListOf(Pool.class), eq(true)))
            .thenReturn(Arrays.asList(derivedEnt));
        when(mockPoolCurator.lockAndLoad(eq(derivedPool))).thenReturn(derivedPool);
        pool.setId("masterpool");

        manager.adjustEntitlementQuantity(consumer, masterEnt, 3);

        Class<List<Entitlement>> listClass = (Class<List<Entitlement>>) (Class) ArrayList.class;
        ArgumentCaptor<List<Entitlement>> arg = ArgumentCaptor.forClass(listClass);
        verify(entitlementCurator).batchDelete(arg.capture());

        List<Entitlement> entsDeleted = arg.getValue();
        assertThat(entsDeleted, hasItem(derivedEnt));
        assertEquals(2, derivedPool.getConsumed().intValue());
    }

    @Test
    public void testDeleteExcessEntitlementsBatch() throws EntitlementRefusedException {
        Consumer consumer = TestUtil.createConsumer(owner);
        Subscription sub = TestUtil.createSubscription(owner, product);
        sub.setId("testing-subid");
        pool.setSourceSubscription(new SourceSubscription(sub.getId(), "master"));

        Pool derivedPool = TestUtil.createPool(owner, product, 1);
        derivedPool.setAttribute(Pool.Attributes.DERIVED_POOL, "true");
        derivedPool.setSourceSubscription(new SourceSubscription(sub.getId(), "der"));
        derivedPool.setConsumed(3L);
        derivedPool.setId("derivedPool");
        Entitlement masterEnt = new Entitlement(pool, consumer, 5);
        Entitlement derivedEnt = new Entitlement(derivedPool, consumer, 1);
        derivedEnt.setId("1");
        Entitlement derivedEnt2 = new Entitlement(derivedPool, consumer, 1);
        derivedEnt2.setId("2");

        Pool derivedPool2 = TestUtil.createPool(owner, product, 1);
        derivedPool2.setAttribute(Pool.Attributes.DERIVED_POOL, "true");
        derivedPool2.setSourceSubscription(new SourceSubscription(sub.getId(), "der"));
        derivedPool2.setConsumed(2L);
        derivedPool2.setId("derivedPool2");
        Entitlement derivedEnt3 = new Entitlement(derivedPool2, consumer, 1);
        derivedEnt3.setId("3");

        Set<Entitlement> ents = new HashSet<Entitlement>();
        ents.add(derivedEnt);
        ents.add(derivedEnt2);
        derivedPool.setEntitlements(ents);

        Set<Entitlement> ents2 = new HashSet<Entitlement>();
        ents2.add(derivedEnt3);
        derivedPool2.setEntitlements(ents2);

        Pool derivedPool3 = TestUtil.createPool(owner, product, 1);
        derivedPool3.setAttribute(Pool.Attributes.DERIVED_POOL, "true");
        derivedPool3.setSourceSubscription(new SourceSubscription(sub.getId(), "der"));
        derivedPool3.setConsumed(2L);
        derivedPool3.setId("derivedPool3");

        // before
        assertEquals(3, derivedPool.getConsumed().intValue());
        assertEquals(2, derivedPool2.getConsumed().intValue());
        assertEquals(2, derivedPool2.getConsumed().intValue());

        when(mockPoolCurator.lockAndLoad(pool)).thenReturn(pool);
        when(enforcerMock.update(any(Consumer.class), any(Entitlement.class), any(Integer.class)))
            .thenReturn(new ValidationResult());
        when(mockPoolCurator.lookupOversubscribedBySubscriptionIds(any(Owner.class), anyMap())).thenReturn(
            Arrays.asList(derivedPool, derivedPool2, derivedPool3));
        when(mockPoolCurator.retrieveFreeEntitlementsOfPools(anyListOf(Pool.class), eq(true))).thenReturn(
            Arrays.asList(derivedEnt, derivedEnt2, derivedEnt3));
        when(mockPoolCurator.lockAndLoad(eq(derivedPool))).thenReturn(derivedPool);
        when(mockPoolCurator.lockAndLoad(eq(derivedPool2))).thenReturn(derivedPool2);
        pool.setId("masterpool");

        manager.adjustEntitlementQuantity(consumer, masterEnt, 3);

        Class<List<Entitlement>> listClass = (Class<List<Entitlement>>) (Class) ArrayList.class;
        ArgumentCaptor<List<Entitlement>> arg = ArgumentCaptor.forClass(listClass);
        verify(entitlementCurator).batchDelete(arg.capture());

        List<Entitlement> entsDeleted = arg.getValue();
        assertThat(entsDeleted, hasItems(derivedEnt, derivedEnt2, derivedEnt3));

        assertEquals(1, derivedPool.getConsumed().intValue());
        assertEquals(1, derivedPool2.getConsumed().intValue());
        assertEquals(2, derivedPool3.getConsumed().intValue());
    }

    @Test
    public void testCreatePools() {
        List<Pool> pools = new ArrayList<Pool>();
        for (int i = 0; i < 5; i++) {
            pools.add(TestUtil.createPool(owner, product));
        }

        Class<List<Pool>> listClass = (Class<List<Pool>>) (Class) ArrayList.class;
        ArgumentCaptor<List<Pool>> poolsArg = ArgumentCaptor.forClass(listClass);
        when(mockPoolCurator.saveOrUpdateAll(poolsArg.capture(), eq(false), anyBoolean())).thenReturn(pools);
        manager.createPools(pools);
        List<Pool> saved = poolsArg.getValue();
        assertEquals(saved.size(), pools.size());
        assertThat(saved, hasItems(pools.toArray(new Pool[0])));
    }

    @Test
    public void testFind() {
        List<Pool> pools = new ArrayList<Pool>();
        List<String> ids = new ArrayList<String>();
        for (int i = 0; i < 5; i++) {
            pools.add(TestUtil.createPool(owner, product));
            pools.get(i).setId("id" + i);
            ids.add("id" + i);
        }

        Class<List<String>> listClass = (Class<List<String>>) (Class) ArrayList.class;
        ArgumentCaptor<List<String>> poolsArg = ArgumentCaptor.forClass(listClass);
        CandlepinQuery cqmock = mock(CandlepinQuery.class);
        when(cqmock.list()).thenReturn(pools);
        when(mockPoolCurator.listAllByIds(poolsArg.capture())).thenReturn(cqmock);
        List<Pool> found = manager.secureFind(ids);
        List<String> argument = poolsArg.getValue();
        assertEquals(pools, found);
        assertEquals(argument, ids);
    }

    @Test
    public void testNullArgumentsDontBreakStuff() {
        manager.lookupBySubscriptionIds(owner, null);
        manager.lookupBySubscriptionIds(owner, new ArrayList<String>());
        manager.createPools(null);
        manager.createPools(new ArrayList<Pool>());
        manager.secureFind(new ArrayList<String>());
    }

    @Test
    public void testlookupBySubscriptionIds() {
        List<Pool> pools = new ArrayList<Pool>();
        List<String> subids = new ArrayList<String>();
        for (int i = 0; i < 5; i++) {
            pools.add(TestUtil.createPool(owner, product));
            pools.get(i).setId("id" + i);
            subids.add("subid" + i);
        }

        Class<List<String>> listClass = (Class<List<String>>) (Class) ArrayList.class;
        ArgumentCaptor<List<String>> poolsArg = ArgumentCaptor.forClass(listClass);
        ArgumentCaptor<Owner> ownerArg = ArgumentCaptor.forClass(Owner.class);
        when(mockPoolCurator.lookupBySubscriptionIds(ownerArg.capture(), poolsArg.capture()))
            .thenReturn(pools);
        List<Pool> found = manager.lookupBySubscriptionIds(owner, subids);
        List<String> argument = poolsArg.getValue();
        assertEquals(pools, found);
        assertEquals(argument, subids);
    }

    private void mockOwner(Owner owner) {
        if (owner.getId() != null) {
            when(this.mockOwnerCurator.find(eq(owner.getId()))).thenReturn(owner);
        }

        if (owner.getKey() != null) {
            when(this.mockOwnerCurator.lookupByKey(eq(owner.getKey()))).thenReturn(owner);
        }
    }

}
