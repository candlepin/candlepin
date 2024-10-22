/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.model.SourceSubscription.DERIVED_POOL_SUB_KEY;
import static org.candlepin.model.SourceSubscription.PRIMARY_POOL_SUB_KEY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.audit.Event;
import org.candlepin.audit.Event.Target;
import org.candlepin.audit.Event.Type;
import org.candlepin.audit.EventBuilder;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.bind.BindChain;
import org.candlepin.bind.BindChainFactory;
import org.candlepin.bind.BindContext;
import org.candlepin.bind.BindContextFactory;
import org.candlepin.bind.CheckBonusPoolQuantitiesOp;
import org.candlepin.bind.ComplianceOp;
import org.candlepin.bind.HandleCertificatesOp;
import org.candlepin.bind.HandleEntitlementsOp;
import org.candlepin.bind.PoolOpProcessor;
import org.candlepin.bind.PoolOperations;
import org.candlepin.bind.PostBindBonusPoolsOp;
import org.candlepin.bind.PreEntitlementRulesCheckOp;
import org.candlepin.bind.PreEntitlementRulesCheckOpFactory;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.controller.refresher.RefreshResult;
import org.candlepin.controller.refresher.RefreshResult.EntityState;
import org.candlepin.controller.refresher.RefreshWorker;
import org.candlepin.model.Branding;
import org.candlepin.model.CdnCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EntitlementCertificateCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Eventful;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Pool.PoolType;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.PoolQualifier;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.SourceStack;
import org.candlepin.model.SourceSubscription;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.dto.Subscription;
import org.candlepin.paging.Page;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.policy.SystemPurposeComplianceRules;
import org.candlepin.policy.ValidationError;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.activationkey.ActivationKeyRules;
import org.candlepin.policy.js.autobind.AutobindRules;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.policy.js.entitlement.Enforcer;
import org.candlepin.policy.js.entitlement.Enforcer.CallerType;
import org.candlepin.policy.js.entitlement.PreUnbindHelper;
import org.candlepin.policy.js.pool.PoolRules;
import org.candlepin.policy.js.pool.PoolUpdate;
import org.candlepin.resource.dto.AutobindData;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.model.SubscriptionInfo;
import org.candlepin.test.TestUtil;

import org.hamcrest.core.IsCollectionContaining;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Provider;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;



@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PoolManagerTest {

    @Mock
    private PoolCurator poolCurator;
    @Mock
    private SubscriptionServiceAdapter mockSubAdapter;
    @Mock
    private ProductCurator mockProductCurator;
    @Mock
    private EventSink mockEventSink;
    @Mock
    private Configuration config;
    @Mock
    private EntitlementCurator entitlementCurator;
    @Mock
    private EntitlementCertificateCurator certCurator;
    @Mock
    private EntitlementCertificateService mockecService;
    @Mock
    private Enforcer enforcer;
    @Mock
    private AutobindRules autobindRules;
    @Mock
    private PoolRules poolRules;
    @Mock
    private ConsumerCurator consumerCuratorMock;
    @Mock
    private ConsumerTypeCurator consumerTypeCurator;
    @Mock
    private EventFactory eventFactory;
    @Mock
    private EventBuilder eventBuilder;
    @Mock
    private ComplianceRules complianceRules;
    @Mock
    private SystemPurposeComplianceRules systemPurposeComplianceRules;
    @Mock
    private ActivationKeyRules activationKeyRules;
    @Mock
    private OwnerCurator mockOwnerCurator;
    @Mock
    private CdnCurator cdnCurator;
    @Mock
    private BindChainFactory mockBindChainFactory;
    @Mock
    private BindContextFactory mockBindContextFactory;
    @Mock
    private PreEntitlementRulesCheckOpFactory mockPreEntitlementRulesCheckFactory;
    @Mock
    private ContentCurator mockContentCurator;
    @Mock
    private OwnerCurator ownerCurator;
    @Mock
    private PoolOpProcessor poolOpProcessor;

    private PoolConverter poolConverter;
    private PoolManager manager;
    private PoolService poolService;
    private RefresherFactory refresherFactory;
    private UserPrincipal principal;
    private RefreshWorker refreshWorker;

    private Owner owner;
    private Pool pool;
    private Product product;
    private ComplianceStatus dummyComplianceStatus;
    private I18n i18n;

    private Provider<RefreshWorker> refreshWorkerProvider;

    protected static Map<String, List<Pool>> subToPools;

    @BeforeEach
    public void init() throws Exception {
        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);

        owner = TestUtil.createOwner("key", "displayname");
        product = TestUtil.createProduct();
        pool = TestUtil.createPool(owner, product);

        poolConverter = new PoolConverter(ownerCurator, mockProductCurator, cdnCurator);

        TestUtil.mockTransactionalFunctionality(mock(EntityManager.class), poolCurator,
            mockProductCurator, entitlementCurator, certCurator, consumerCuratorMock,
            consumerTypeCurator, mockOwnerCurator, cdnCurator, mockContentCurator);

        when(mockOwnerCurator.getByKey(owner.getKey())).thenReturn(owner);

        when(config.getInt(ConfigProperties.PRODUCT_CACHE_MAX)).thenReturn(100);
        when(eventFactory.getEventBuilder(any(Target.class), any(Type.class))).thenReturn(eventBuilder);

        when(eventBuilder.setEventData(any(Eventful.class))).thenReturn(eventBuilder);

        this.principal = TestUtil.createOwnerPrincipal(owner);

        this.refreshWorker = spy(new RefreshWorker(this.poolCurator, this.mockProductCurator,
            this.mockContentCurator));

        this.refreshWorkerProvider = () -> refreshWorker;

        this.poolService = spy(new PoolService(
            poolCurator, mockEventSink, eventFactory, poolRules, entitlementCurator,
            consumerCuratorMock, consumerTypeCurator, certCurator,
            complianceRules, systemPurposeComplianceRules, config, i18n));

        this.manager = spy(new PoolManager(
            poolCurator, mockEventSink, eventFactory, config, enforcer, poolRules, entitlementCurator,
            consumerCuratorMock, consumerTypeCurator, mockecService, complianceRules, autobindRules,
            activationKeyRules, mockOwnerCurator, i18n, poolService, mockBindChainFactory,
            refreshWorkerProvider, poolOpProcessor, poolConverter));

        this.refresherFactory = new RefresherFactory(ownerCurator, manager, poolCurator, poolConverter);

        setupBindChain();

        Map<String, EntitlementCertificate> entCerts = new HashMap<>();
        entCerts.put(pool.getId(), new EntitlementCertificate());
        when(mockecService.generateEntitlementCertificates(any(Consumer.class), anyMap(), anyMap(),
            anyMap(), eq(false))).thenReturn(entCerts);
        dummyComplianceStatus = new ComplianceStatus(new Date());
        when(complianceRules.getStatus(any(Consumer.class), any(Date.class))).thenReturn(
            dummyComplianceStatus);

        doAnswer(returnsFirstArg()).when(this.consumerCuratorMock).lock(any(Consumer.class));
        doAnswer(returnsFirstArg()).when(this.poolCurator).lock(any(Pool.class));
        when(ownerCurator.getByKey(anyString())).thenReturn(owner);
    }

    protected ConsumerType mockConsumerType(ConsumerType ctype) {
        if (ctype != null) {
            // Ensure the type has an ID
            if (ctype.getId() == null) {
                ctype.setId("test-ctype-" + ctype.getLabel() + "-" + TestUtil.randomInt());
            }

            when(consumerTypeCurator.getByLabel(ctype.getLabel())).thenReturn(ctype);
            when(consumerTypeCurator.getByLabel(eq(ctype.getLabel()), anyBoolean())).thenReturn(ctype);
            when(consumerTypeCurator.get(ctype.getId())).thenReturn(ctype);

            doAnswer(new Answer<ConsumerType>() {
                @Override
                public ConsumerType answer(InvocationOnMock invocation) {
                    Object[] args = invocation.getArguments();
                    Consumer consumer = (Consumer) args[0];
                    ConsumerTypeCurator curator = (ConsumerTypeCurator) invocation.getMock();
                    ConsumerType ctype = null;

                    if (consumer == null || consumer.getTypeId() == null) {
                        throw new IllegalArgumentException("consumer is null or lacks a type ID");
                    }

                    ctype = curator.get(consumer.getTypeId());
                    if (ctype == null) {
                        throw new IllegalStateException("No such consumer type: " + consumer.getTypeId());
                    }

                    return ctype;
                }
            }).when(consumerTypeCurator).getConsumerType(any(Consumer.class));
        }

        return ctype;
    }

    private void setupBindChain() {
        final HandleEntitlementsOp entitlementsOp = new HandleEntitlementsOp(poolCurator, entitlementCurator);
        final PostBindBonusPoolsOp postBindBonusPoolsOp = new PostBindBonusPoolsOp(poolService,
            consumerTypeCurator, poolCurator, enforcer, poolOpProcessor);
        final CheckBonusPoolQuantitiesOp checkBonusPoolQuantitiesOp = new CheckBonusPoolQuantitiesOp(manager);
        final HandleCertificatesOp certificatesOp = new HandleCertificatesOp(mockecService, certCurator,
            entitlementCurator);
        final ComplianceOp complianceOp = new ComplianceOp(complianceRules, systemPurposeComplianceRules);

        when(mockPreEntitlementRulesCheckFactory.create(any(CallerType.class)))
            .thenAnswer(new Answer<PreEntitlementRulesCheckOp>() {
                @Override
                public PreEntitlementRulesCheckOp answer(InvocationOnMock invocation) {
                    Object[] args = invocation.getArguments();
                    CallerType type = (CallerType) args[0];
                    return new PreEntitlementRulesCheckOp(enforcer, type);
                }
            });

        when(mockBindContextFactory.create(any(Consumer.class), anyMap()))
            .thenAnswer(new Answer<BindContext>() {
                @Override
                public BindContext answer(InvocationOnMock invocation) {
                    Object[] args = invocation.getArguments();
                    Consumer consumer = (Consumer) args[0];
                    Map<String, Integer> pQ = (Map<String, Integer>) args[1];
                    return new BindContext(poolCurator,
                        consumerCuratorMock,
                        consumerTypeCurator,
                        mockOwnerCurator,
                        i18n,
                        consumer,
                        pQ);
                }
            });

        when(mockBindChainFactory.create(any(Consumer.class), anyMap(), any(CallerType.class)))
            .thenAnswer(new Answer<BindChain>() {
                @Override
                public BindChain answer(InvocationOnMock invocation) {
                    Object[] args = invocation.getArguments();
                    Consumer consumer = (Consumer) args[0];
                    Map<String, Integer> pQ = (Map<String, Integer>) args[1];
                    CallerType type = (CallerType) args[2];
                    return new BindChain(mockBindContextFactory,
                        mockPreEntitlementRulesCheckFactory,
                        entitlementsOp,
                        postBindBonusPoolsOp,
                        checkBonusPoolQuantitiesOp,
                        certificatesOp,
                        complianceOp,
                        consumer,
                        pQ,
                        type);
                }
            });
    }

    private void assertPoolsAreEqual(Pool pool1, Pool pool2) {
        assertEquals(pool1.getAccountNumber(), pool2.getAccountNumber());
        assertEquals(pool1.getContractNumber(), pool2.getContractNumber());
        assertEquals(pool1.getOrderNumber(), pool2.getOrderNumber());
        assertEquals(pool1.getType(), pool2.getType());
        assertEquals(pool1.getOwner(), pool2.getOwner());
        assertEquals(pool1.getQuantity(), pool2.getQuantity());
        assertEquals(pool1.getActiveSubscription(), pool2.getActiveSubscription());
        assertEquals(pool1.getSourceEntitlement(), pool2.getSourceEntitlement());
        assertEquals(pool1.getSourceStack(), pool2.getSourceStack());
        assertEquals(pool1.getSubscriptionId(), pool2.getSubscriptionId());
        assertEquals(pool1.getSubscriptionSubKey(), pool2.getSubscriptionSubKey());
        assertEquals(pool1.getStartDate(), pool2.getStartDate());
        assertEquals(pool1.getEndDate(), pool2.getEndDate());
        assertEquals(pool1.getProduct(), pool2.getProduct());
        assertEquals(pool1.getAttributes(), pool2.getAttributes());
        assertEquals(pool1.getEntitlements(), pool2.getEntitlements());
        assertEquals(pool1.getConsumed(), pool2.getConsumed());
        assertEquals(pool1.getExported(), pool2.getExported());
        assertEquals(pool1.getCalculatedAttributes(), pool2.getCalculatedAttributes());
        assertEquals(pool1.isMarkedForDelete(), pool2.isMarkedForDelete());
        assertEquals(pool1.getUpstreamConsumerId(), pool2.getUpstreamConsumerId());
        assertEquals(pool1.getUpstreamEntitlementId(), pool2.getUpstreamEntitlementId());
        assertEquals(pool1.getUpstreamPoolId(), pool2.getUpstreamPoolId());
        assertEquals(pool1.getCertificate(), pool2.getCertificate());
        assertEquals(pool1.getCdn(), pool2.getCdn());
    }

    @Test
    public void doesntMergeDeletedPools() {
        reset(poolCurator);

        Map<String, EventBuilder> poolEvents = new HashMap<>();
        List<PoolUpdate> updatedPools = new ArrayList<>();
        Pool deletedPool = Mockito.mock(Pool.class);
        Pool normalPool = Mockito.mock(Pool.class);

        when(normalPool.getId()).thenReturn("normal-pool-id");

        Set<String> existingPoolIds = new HashSet<>();
        existingPoolIds.add("normal-pool-id");

        when(poolCurator.getExistingPoolIdsByIds(anyIterable())).thenReturn(existingPoolIds);
        when(poolCurator.exists(deletedPool)).thenReturn(false);
        when(poolCurator.exists(normalPool)).thenReturn(true);

        PoolUpdate deletedPu = Mockito.mock(PoolUpdate.class);
        PoolUpdate normalPu = Mockito.mock(PoolUpdate.class);
        when(deletedPu.getPool()).thenReturn(deletedPool);
        when(normalPu.getPool()).thenReturn(normalPool);

        updatedPools.add(deletedPu);
        updatedPools.add(normalPu);

        manager.processPoolUpdates(poolEvents, updatedPools);

        verify(poolCurator, never()).merge(deletedPool);
        verify(poolCurator, times(1)).merge(normalPool);
    }

    @Test
    public void deletePoolsTest() {
        Product prod = TestUtil.createProduct();
        Pool pool = TestUtil.createPool(prod);
        pool.setId("test-id");

        List<Pool> pools = List.of(pool);

        when(poolCurator.lockAndLoad(anyIterable())).thenReturn(pools);

        doNothing().when(poolCurator).batchDelete(eq(pools), anySet());
        this.poolService.deletePools(pools);
        verify(poolCurator).batchDelete(eq(pools), anySet());
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testRefreshPoolsOnlyRegeneratesFloatingWhenNecessary() {
        List<Subscription> subscriptions = new ArrayList<>();

        Owner owner = this.getOwner();
        Product product = TestUtil.createProduct();
        Subscription sub = TestUtil.createSubscription(owner, product);
        sub.setId("testing-subid");
        subscriptions.add(sub);

        // Set up pools
        List<Pool> pools = new ArrayList<>();

        // Should be unchanged
        Pool p = TestUtil.createPool(product);
        p.setSourceSubscription(new SourceSubscription(sub.getId(), PRIMARY_POOL_SUB_KEY));
        pools.add(p);

        // Should be regenerated because it has no subscription id
        Pool floating = TestUtil.createPool(TestUtil.createProduct());
        floating.setSourceSubscription(null);
        pools.add(floating);
        this.mockSubscriptions(owner, subscriptions);

        mockPoolsList(pools);
        when(mockOwnerCurator.getByKey(owner.getKey())).thenReturn(owner);
        this.mockProducts(null, product);
        this.mockRefresh(owner, List.of(product), Collections.emptyList());

        when(poolCurator.listByOwnerAndTypes(eq(owner.getId()), any(PoolType.class))).thenReturn(pools);

        this.refresherFactory.getRefresher(mockSubAdapter).add(owner).run();
        List<Pool> expectedFloating = new LinkedList();

        // Make sure that only the floating pool was regenerated
        expectedFloating.add(floating);
        verify(this.manager).updateFloatingPools(eq(expectedFloating), eq(true), anyMap());
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testRefreshPoolsOnlyRegeneratesWhenNecessary() {
        List<Subscription> subscriptions = new ArrayList<>();

        Owner owner = this.getOwner();
        Product product = TestUtil.createProduct();

        Subscription sub = TestUtil.createSubscription(owner, product);
        sub.setId("testing-subid");
        subscriptions.add(sub);

        // Set up pools
        List<Pool> pools = new ArrayList<>();

        // Should be unchanged
        Pool p = TestUtil.createPool(product);
        p.setSourceSubscription(new SourceSubscription(sub.getId(), PRIMARY_POOL_SUB_KEY));
        p.setOwner(owner);
        pools.add(p);

        this.mockSubscriptions(owner, subscriptions);
        mockPoolsList(pools);
        when(mockOwnerCurator.getByKey(owner.getKey())).thenReturn(owner);
        this.mockProducts(null, product);
        this.mockRefresh(owner, List.of(product), Collections.emptyList());

        when(poolCurator.listByOwnerAndTypes(eq(owner.getId()), any(PoolType.class))).thenReturn(pools);

        this.refresherFactory.getRefresher(mockSubAdapter).add(owner).run();
        List<Pool> expectedModified = new LinkedList();

        // Make sure that only the floating pool was regenerated
        expectedModified.add(p);
        verify(this.manager).updateFloatingPools(eq(new LinkedList()), eq(true), anyMap());
        ArgumentCaptor<Pool> argPool = ArgumentCaptor.forClass(Pool.class);
        verify(this.manager).updatePoolsForPrimaryPool(eq(expectedModified), argPool.capture(),
            eq(sub.getQuantity()), eq(false), anyMap());
        assertPoolsAreEqual(TestUtil.copyFromSub(sub), argPool.getValue());
    }

    private void mockSubscriptions(Owner owner, Collection<? extends SubscriptionInfo> subscriptions) {
        Set<String> sids = new HashSet<>();

        for (SubscriptionInfo subscription : subscriptions) {
            sids.add(subscription.getId());
            doAnswer(iom -> subscription).when(this.mockSubAdapter).getSubscription(subscription.getId());
        }

        doAnswer(iom -> sids).when(this.mockSubAdapter).getSubscriptionIds(owner.getKey());
        doAnswer(iom -> subscriptions).when(this.mockSubAdapter).getSubscriptions(owner.getKey());
    }

    private void mockProducts(Owner owner, final Map<String, Product> productMap) {
        String namespace = owner != null ? owner.getKey() : null;

        Answer<Product> singleLookupAnswer = iom -> {
            String pid = iom.getArgument(1);
            return productMap.get(pid);
        };

        Answer<Map<String, Product>> multiLookupAnswer = iom -> {
            Collection<String> pids = iom.getArgument(1);

            return Optional.ofNullable(pids)
                .orElse(List.of())
                .stream()
                .map(productMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Product::getId, Function.identity(), (p1, p2) -> p2));
        };

        when(mockProductCurator.getProductById(eq(namespace), anyString(), any(LockModeType.class)))
            .thenAnswer(singleLookupAnswer);

        when(mockProductCurator.getProductById(eq(namespace), anyString()))
            .thenAnswer(singleLookupAnswer);

        when(mockProductCurator.resolveProductId(eq(namespace), anyString(), any(LockModeType.class)))
            .thenAnswer(singleLookupAnswer);

        when(mockProductCurator.resolveProductId(eq(namespace), anyString()))
            .thenAnswer(singleLookupAnswer);

        when(mockProductCurator.getProductsByIds(eq(namespace), anyCollection(), any(LockModeType.class)))
            .thenAnswer(multiLookupAnswer);

        when(mockProductCurator.getProductsByIds(eq(namespace), anyCollection()))
            .thenAnswer(multiLookupAnswer);
    }

    private void mockProducts(Owner owner, Product... products) {
        Map<String, Product> productMap = Stream.of(products)
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(Product::getId, Function.identity(), (p1, p2) -> p2));

        this.mockProducts(owner, productMap);
    }

    private void mockRefresh(Owner owner, Collection<Product> products, Collection<Content> contents) {
        doAnswer(new Answer<RefreshResult>() {
            @Override
            public RefreshResult answer(InvocationOnMock iom) {
                RefreshResult output = new RefreshResult();

                if (products != null) {
                    for (Product product : products) {
                        output.addEntity(Product.class, product, EntityState.CREATED);
                    }
                }

                if (contents != null) {
                    for (Content content : contents) {
                        output.addEntity(Content.class, content, EntityState.CREATED);
                    }
                }

                return output;
            }
        })
            .when(this.refreshWorker).execute(owner);
    }

    @Test
    public void productAttributesCopiedOntoPoolWhenCreatingNewPool() {
        // TODO: Why is this test in pool manager? It looks like a pool rules test.
        PoolRules pRules = new PoolRules(config, entitlementCurator, poolConverter);
        Product product = TestUtil.createProduct();

        String testAttributeKey = "multi-entitlement";
        String expectedAttributeValue = "yes";
        product.setAttribute(testAttributeKey, expectedAttributeValue);

        Subscription sub = TestUtil.createSubscription(owner, product);

        this.mockProducts(owner, product);

        List<Pool> pools = pRules.createAndEnrichPools(sub, new LinkedList<>());
        assertEquals(1, pools.size());

        Pool resultPool = pools.get(0);
        assertNotNull(resultPool.getProduct());
        assertTrue(resultPool.getProduct().hasAttribute(testAttributeKey));
        assertEquals(expectedAttributeValue, resultPool.getProduct().getAttributeValue(testAttributeKey));
    }

    @Test
    public void subProductAttributesCopiedOntoPoolWhenCreatingNewPool() {
        // TODO: Why is this test in pool manager? It looks like a pool rules test.

        Product product = TestUtil.createProduct();
        Product subProduct = TestUtil.createProduct();
        product.setDerivedProduct(subProduct);

        Subscription sub = TestUtil.createSubscription(owner, product);

        String testAttributeKey = "multi-entitlement";
        String expectedAttributeValue = "yes";
        subProduct.setAttribute(testAttributeKey, expectedAttributeValue);

        this.mockProducts(owner, product, subProduct);

        PoolRules pRules = new PoolRules(config, entitlementCurator, poolConverter);
        List<Pool> pools = pRules.createAndEnrichPools(sub, new LinkedList<>());
        assertEquals(1, pools.size());

        Pool resultPool = pools.get(0);
        assertNotNull(resultPool.getDerivedProduct());
        assertTrue(resultPool.getDerivedProduct().hasAttribute(testAttributeKey));
        assertEquals(expectedAttributeValue,
            resultPool.getDerivedProduct().getAttributeValue(testAttributeKey));
    }

    @Test
    public void subProductIsCopiedOntoPoolWhenCreatingNewPool() {
        // TODO: Why is this test in pool manager? It looks like a pool rules test.

        Product product = TestUtil.createProduct();
        Product subProduct = TestUtil.createProduct();
        product.setDerivedProduct(subProduct);

        Subscription sub = TestUtil.createSubscription(owner, product);

        this.mockProducts(owner, product, subProduct);

        PoolRules pRules = new PoolRules(config, entitlementCurator, poolConverter);
        List<Pool> pools = pRules.createAndEnrichPools(sub, new LinkedList<>());
        assertEquals(1, pools.size());

        Pool resultPool = pools.get(0);
        assertEquals(subProduct, resultPool.getDerivedProduct());
    }

    @Test
    public void derivedProvidedProductsCopiedOntoPrimaryPoolWhenCreatingNewPool() {
        // TODO: Why is this test in pool manager? It looks like a pool rules test.

        Product product = TestUtil.createProduct();
        Product subProduct = TestUtil.createProduct();
        Product subProvidedProduct = TestUtil.createProduct();
        product.setDerivedProduct(subProduct);
        subProduct.addProvidedProduct(subProvidedProduct);

        Subscription sub = TestUtil.createSubscription(owner, product);

        this.mockProducts(owner, product, subProduct, subProvidedProduct);

        PoolRules pRules = new PoolRules(config, entitlementCurator, poolConverter);
        List<Pool> pools = pRules.createAndEnrichPools(sub, new LinkedList<>());
        assertEquals(1, pools.size());

        Pool resultPool = pools.get(0);
        assertNotNull(resultPool);

        Product resultantDerivedProduct = resultPool.getDerivedProduct();
        assertNotNull(resultantDerivedProduct);
        assertNotNull(resultantDerivedProduct.getProvidedProducts());
        assertEquals(1, resultantDerivedProduct.getProvidedProducts().size());
    }

    @Test
    public void providedProductsCopiedWhenCreatingPools() {
        // TODO: Why is this test in pool manager? It looks like a pool rules test.

        Product product = TestUtil.createProduct();

        Subscription sub = TestUtil.createSubscription(owner, product);
        Product p1 = TestUtil.createProduct();
        Product p2 = TestUtil.createProduct();

        product.addProvidedProduct(p1);
        product.addProvidedProduct(p2);

        this.mockProducts(owner, product);

        PoolRules pRules = new PoolRules(config, entitlementCurator, poolConverter);
        List<Pool> pools = pRules.createAndEnrichPools(sub, new LinkedList<>());
        assertEquals(1, pools.size());

        Pool resultPool = pools.get(0);
        assertEquals(2, resultPool.getProduct().getProvidedProducts().size());
        assertTrue(resultPool.getProduct().getProvidedProducts().contains(p1));
        assertTrue(resultPool.getProduct().getProvidedProducts().contains(p2));
    }

    @Test
    public void brandingCopiedWhenCreatingPools() {
        // TODO: Why is this test in pool manager? It looks like a pool rules test.

        Product product = TestUtil.createProduct();

        Subscription sub = TestUtil.createSubscription(owner, product);
        Branding b1 = new Branding("8000", "Branded Awesome OS", "OS");
        Branding b2 = new Branding("8001", "Branded Awesome OS 2", "OS");
        product.addBranding(b1);
        product.addBranding(b2);

        this.mockProducts(owner, product);

        PoolRules pRules = new PoolRules(config, entitlementCurator, poolConverter);
        List<Pool> pools = pRules.createAndEnrichPools(sub, new LinkedList<>());
        assertEquals(1, pools.size());

        Pool resultPool = pools.get(0);
        assertEquals(2, resultPool.getProduct().getBranding().size());
        assertTrue(resultPool.getProduct().getBranding().contains(b1));
        assertTrue(resultPool.getProduct().getBranding().contains(b2));
    }

    @Test
    public void testRefreshPoolsDeletesOrphanedPools() {
        List<Subscription> subscriptions = new ArrayList<>();

        Product product = TestUtil.createProduct();
        Pool pool = TestUtil.createPool(product)
            .setSourceSubscription(new SourceSubscription("112", PRIMARY_POOL_SUB_KEY))
            .setManaged(true);

        List<Pool> pools = List.of(pool);

        this.mockSubscriptions(owner, subscriptions);
        mockPoolsList(pools);

        when(poolCurator.listByOwnerAndTypes(eq(owner.getId()), any(PoolType.class))).thenReturn(pools);

        when(poolCurator.getPoolsBySubscriptionIds(anyList())).thenReturn(Collections.emptyList());

        this.mockRefresh(owner, List.of(product), Collections.emptyList());

        Owner owner = getOwner();
        when(mockOwnerCurator.getByKey(owner.getKey())).thenReturn(owner);
        this.refresherFactory.getRefresher(mockSubAdapter).add(owner).run();

        verify(this.poolService).deletePools(pools);
    }

    @Test
    public void testRefreshPoolsDeletesOrphanedHostedVirtBonusPool() {
        List<Subscription> subscriptions = new ArrayList<>();

        Product product = TestUtil.createProduct();

        Pool pool = TestUtil.createPool(product)
            .setSourceSubscription(new SourceSubscription("112", PRIMARY_POOL_SUB_KEY))
            // Make it look like a hosted virt bonus pool:
            .setAttribute(Pool.Attributes.DERIVED_POOL, "true")
            .setSourceStack(null)
            .setSourceEntitlement(null)
            .setManaged(true);

        List<Pool> pools = List.of(pool);

        this.mockSubscriptions(owner, subscriptions);
        mockPoolsList(pools);

        Owner owner = getOwner();
        when(mockOwnerCurator.getByKey(owner.getKey())).thenReturn(owner);

        this.mockRefresh(owner, List.of(product), Collections.emptyList());

        when(poolCurator.listByOwnerAndTypes(eq(owner.getId()), any(PoolType.class))).thenReturn(pools);

        when(poolCurator.getPoolsBySubscriptionIds(anyList())).thenReturn(Collections.emptyList());

        this.refresherFactory.getRefresher(mockSubAdapter).add(owner).run();
        List<Pool> delPools = List.of(pool);
        verify(this.poolService).deletePools(delPools);
    }

    @Test
    public void testRefreshPoolsSkipsOrphanedEntitlementDerivedPools() {
        List<Subscription> subscriptions = new ArrayList<>();
        List<Pool> pools = new ArrayList<>();
        Product product = TestUtil.createProduct();
        Pool p = TestUtil.createPool(product);
        p.setSourceSubscription(new SourceSubscription("112", PRIMARY_POOL_SUB_KEY));

        // Mock a pool with a source entitlement:
        p.setAttribute(Pool.Attributes.DERIVED_POOL, "true");
        p.setSourceStack(null);
        p.setSourceEntitlement(new Entitlement());

        pools.add(p);
        this.mockSubscriptions(owner, subscriptions);
        mockPoolsList(pools);

        Owner owner = getOwner();
        when(mockOwnerCurator.getByKey(owner.getKey())).thenReturn(owner);

        this.mockRefresh(owner, List.of(product), Collections.emptyList());

        when(poolCurator.listByOwnerAndTypes(eq(owner.getId()), any(PoolType.class))).thenReturn(pools);

        this.refresherFactory.getRefresher(mockSubAdapter).add(owner).run();
        verify(this.poolService, never()).deletePool(same(p));
    }

    @Test
    public void testRefreshPoolsSkipsOrphanedStackDerivedPools() {
        List<Subscription> subscriptions = new ArrayList<>();
        List<Pool> pools = new ArrayList<>();
        Product product = TestUtil.createProduct();
        Pool p = TestUtil.createPool(product);
        p.setSourceSubscription(new SourceSubscription("112", PRIMARY_POOL_SUB_KEY));

        // Mock a pool with a source stack ID:
        p.setAttribute(Pool.Attributes.DERIVED_POOL, "true");
        p.setSourceStack(new SourceStack(new Consumer(), "blah"));
        p.setSourceEntitlement(null);

        pools.add(p);
        this.mockSubscriptions(owner, subscriptions);
        mockPoolsList(pools);

        this.mockRefresh(owner, List.of(product), Collections.emptyList());

        when(poolCurator.listByOwnerAndTypes(eq(owner.getId()), any(PoolType.class))).thenReturn(pools);

        Owner owner = getOwner();
        when(mockOwnerCurator.getByKey(owner.getKey())).thenReturn(owner);
        this.refresherFactory.getRefresher(mockSubAdapter).add(owner).run();
        verify(this.poolService, never()).deletePool(same(p));
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testRefreshPoolsSortsStackDerivedPools() {
        List<Subscription> subscriptions = new ArrayList<>();
        List<Pool> pools = new ArrayList<>();

        // Pool has no subscription ID:
        Product product = TestUtil.createProduct();
        Pool p = TestUtil.createPool(product);
        p.setSourceStack(new SourceStack(new Consumer(), "a"));

        pools.add(p);
        this.mockSubscriptions(owner, subscriptions);
        mockPoolsList(pools);

        Owner owner = getOwner();
        when(mockOwnerCurator.getByKey(owner.getKey())).thenReturn(owner);

        this.mockRefresh(owner, List.of(product), Collections.emptyList());

        when(poolCurator.listByOwnerAndTypes(eq(owner.getId()), any(PoolType.class))).thenReturn(pools);

        this.refresherFactory.getRefresher(mockSubAdapter).add(owner).run();
        ArgumentCaptor<List> poolCaptor = ArgumentCaptor.forClass(List.class);
        verify(this.poolRules).updatePools(poolCaptor.capture(), anyMap());
        assertEquals(1, poolCaptor.getValue().size());
        assertEquals(p, poolCaptor.getValue().get(0));
    }

    @Test
    public void refreshPoolsCreatingPoolsForExistingSubscriptions() {
        List<Subscription> subscriptions = new ArrayList<>();
        List<Pool> pools = new ArrayList<>();

        Owner owner = this.getOwner();
        Product product = TestUtil.createProduct();

        Subscription s = TestUtil.createSubscription(owner, product);
        subscriptions.add(s);

        this.mockSubscriptions(owner, subscriptions);
        mockPoolsList(pools);

        List<Pool> newPools = new LinkedList<>();
        Pool p = TestUtil.createPool(product);
        p.setSourceSubscription(new SourceSubscription(s.getId(), PRIMARY_POOL_SUB_KEY));
        newPools.add(p);
        ArgumentCaptor<Pool> argPool = ArgumentCaptor.forClass(Pool.class);

        when(poolRules.createAndEnrichPools(argPool.capture(), anyList())).thenReturn(newPools);
        when(mockOwnerCurator.getByKey(owner.getKey())).thenReturn(owner);
        this.mockProducts(null, product);
        this.mockRefresh(owner, List.of(product), Collections.emptyList());

        when(poolCurator.listByOwnerAndTypes(eq(owner.getId()), any(PoolType.class))).thenReturn(pools);

        when(poolCurator.getPoolsBySubscriptionIds(anyList())).thenReturn(Collections.emptyList());

        when(poolCurator.getPoolsBySubscriptionId(anyString())).thenReturn(Collections.emptyList());

        this.refresherFactory.getRefresher(mockSubAdapter).add(owner).run();

        assertPoolsAreEqual(TestUtil.copyFromSub(s), argPool.getValue());
        verify(this.poolCurator, times(1)).create(any(Pool.class), anyBoolean());
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void refreshPoolsCleanupPoolThatLostVirtLimit() {
        List<Subscription> subscriptions = new ArrayList<>();
        List<Pool> pools = new ArrayList<>();

        Owner owner = getOwner();
        Product product = TestUtil.createProduct();

        Subscription s = TestUtil.createSubscription(owner, product);
        s.setId("01923");
        subscriptions.add(s);
        Pool p = TestUtil.createPool(product);
        p.setSourceSubscription(new SourceSubscription(s.getId(), PRIMARY_POOL_SUB_KEY));
        p.setMarkedForDelete(true);
        p.setOwner(owner);
        pools.add(p);

        this.mockSubscriptions(owner, subscriptions);

        mockPoolsList(pools);

        List<PoolUpdate> updates = new LinkedList();
        PoolUpdate u = new PoolUpdate(p);
        u.setQuantityChanged(true);
        u.setOrderChanged(true);
        updates.add(u);
        ArgumentCaptor<Pool> argPool = ArgumentCaptor.forClass(Pool.class);
        when(poolRules.updatePools(argPool.capture(), eq(pools), eq(s.getQuantity()), anyMap()))
            .thenReturn(updates);

        when(mockOwnerCurator.getByKey(owner.getKey())).thenReturn(owner);
        this.mockProducts(null, product);
        this.mockRefresh(owner, List.of(product), Collections.emptyList());

        when(poolCurator.listByOwnerAndTypes(eq(owner.getId()), any(PoolType.class))).thenReturn(pools);

        this.refresherFactory.getRefresher(mockSubAdapter).add(owner).run();
        verify(poolRules).createAndEnrichPools(argPool.capture(), anyList());
        assertPoolsAreEqual(TestUtil.copyFromSub(s), argPool.getValue());
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

        List<Pool> newPools = new LinkedList<>();
        Pool p = TestUtil.createPool(product);
        p.setSourceSubscription(new SourceSubscription(s.getId(), PRIMARY_POOL_SUB_KEY));
        newPools.add(p);
        when(poolRules.createAndEnrichPools(eq(s), anyList())).thenReturn(newPools);

        this.manager.createAndEnrichPools(s);
        verify(this.poolCurator, times(1)).create(any(Pool.class), anyBoolean());
    }

    @Test
    public void testCreateAndEnrichPoolForPool() {
        List<Pool> newPools = new LinkedList<>();
        Pool p = TestUtil.createPool(TestUtil.createProduct());
        newPools.add(p);
        when(poolRules.createAndEnrichPools(eq(p), anyList())).thenReturn(newPools);

        this.manager.createAndEnrichPools(p);
        verify(this.poolCurator, times(1)).create(any(Pool.class), anyBoolean());
    }

    @Test
    public void testRevokeAllEntitlements() {
        Consumer c = TestUtil.createConsumer(owner);

        Entitlement e1 = new Entitlement(pool, c, owner, 1);
        Entitlement e2 = new Entitlement(pool, c, owner, 1);
        List<Entitlement> entitlementList = new ArrayList<>();
        entitlementList.add(e1);
        entitlementList.add(e2);

        when(entitlementCurator.listByConsumer(c)).thenReturn(entitlementList);
        when(poolCurator.lockAndLoad(any(Pool.class))).thenReturn(pool);

        PreUnbindHelper preHelper = mock(PreUnbindHelper.class);
        ValidationResult result = new ValidationResult();
        when(preHelper.getResult()).thenReturn(result);

        int total = this.poolService.revokeAllEntitlements(c);

        assertEquals(2, total);
        verify(entitlementCurator, times(1)).markDependentEntitlementsDirty(any());
        // TODO assert batch revokes have been called
    }

    @Test
    public void testRevokeCleansUpPoolsWithSourceEnt() {
        Entitlement e = new Entitlement(pool, TestUtil.createConsumer(owner), owner, 1);
        List<Pool> poolsWithSource = createPoolsWithSourceEntitlement(e, product);

        when(poolCurator.listBySourceEntitlement(e)).thenReturn(poolsWithSource);
        PreUnbindHelper preHelper = mock(PreUnbindHelper.class);
        ValidationResult result = new ValidationResult();
        when(preHelper.getResult()).thenReturn(result);
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(true);

        when(poolCurator.lockAndLoad(any(Pool.class))).thenReturn(pool);

        this.poolService.revokeEntitlement(e);
        List<Entitlement> entsToDelete = List.of(e);
        verify(entitlementCurator).batchDelete(entsToDelete);
    }

    @Test
    public void testBatchRevokeCleansUpCorrectPoolsWithSourceEnt() {
        Consumer c = TestUtil.createConsumer(owner);
        Pool pool2 = TestUtil.createPool(owner, product);

        Entitlement e = new Entitlement(pool, c, owner, 1);
        Entitlement e2 = new Entitlement(pool2, c, owner, 1);
        Entitlement e3 = new Entitlement(pool2, c, owner, 1);

        List<Entitlement> entsToDelete = new ArrayList<>();
        entsToDelete.add(e);
        entsToDelete.add(e2);

        List<Pool> poolsWithSource = createPoolsWithSourceEntitlement(e, product);
        poolsWithSource.get(0).getEntitlements().add(e3);
        Set<Pool> poolsWithSourceAsSet = new HashSet<>(poolsWithSource);
        when(poolCurator.listBySourceEntitlements(entsToDelete)).thenReturn(poolsWithSourceAsSet);

        PreUnbindHelper preHelper = mock(PreUnbindHelper.class);
        ValidationResult result = new ValidationResult();
        when(preHelper.getResult()).thenReturn(result);

        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(true);

        when(poolCurator.lockAndLoad(pool)).thenReturn(pool);
        when(poolCurator.lockAndLoad(pool2)).thenReturn(pool2);

        this.poolService.revokeEntitlements(entsToDelete);
        entsToDelete.add(e3);
        verify(entitlementCurator).batchDelete(entsToDelete);
        verify(poolCurator).batchDelete(eq(poolsWithSourceAsSet), any());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testEntitleWithADate() throws Exception {
        // TODO: Fix this test with proper mocks -- the current mocks were hiding a bug in the
        // test where we were sending the wrong product IDs in, and still got the correct result.

        Product product = TestUtil.createProduct();
        List<Pool> pools = new ArrayList<>();
        Pool pool1 = TestUtil.createPool(product);
        pools.add(pool1);
        Pool pool2 = TestUtil.createPool(product);
        pools.add(pool2);
        Date now = new Date();

        ValidationResult result = mock(ValidationResult.class);
        Page page = mock(Page.class);

        when(page.getPageData()).thenReturn(pools);
        when(poolCurator.listAvailableEntitlementPools(any(PoolQualifier.class)))
            .thenReturn(page);

        when(poolCurator.listAllByIds(nullable(Set.class))).thenReturn(List.of(pool1));
        when(enforcer.preEntitlement(any(Consumer.class), any(Pool.class), anyInt(),
            any(CallerType.class))).thenReturn(result);

        when(enforcer.postEntitlement(any(Consumer.class), anyMap(),
            anyList(), eq(false), anyMap())).thenReturn(new PoolOperations());
        when(result.isSuccessful()).thenReturn(true);

        List<PoolQuantity> bestPools = new ArrayList<>();
        bestPools.add(new PoolQuantity(pool1, 1));
        when(autobindRules.selectBestPools(any(Consumer.class), anyCollection(), anyList(),
            nullable(ComplianceStatus.class), nullable(String.class), anySet(), eq(false)))
            .thenReturn(bestPools);

        ConsumerType ctype = this.mockConsumerType(TestUtil.createConsumerType());
        Consumer consumer = TestUtil.createConsumer(ctype, owner);

        AutobindData data = new AutobindData(consumer, owner)
            .on(now)
            .forProducts(Set.of(product.getId()));

        doNothing().when(poolCurator).flush();
        doNothing().when(poolCurator).clear();

        List<Entitlement> e = manager.entitleByProducts(data);

        assertNotNull(e);
        assertEquals(1, e.size());
    }

    @Test
    public void testEntitleByProductRetry() {
        Date now = new Date();

        Consumer consumer = TestUtil.createConsumer(owner);
        Product product = TestUtil.createProduct();
        Pool pool1 = TestUtil.createPool(product)
            .setId("poolId1");
        Pool pool2 = TestUtil.createPool(product)
            .setId("poolId2");

        List<Pool> pools = List.of(pool1, pool2);

        ValidationResult validationResult = new ValidationResult();
        validationResult.addError(new ValidationError("rulefailed.no.entitlements.available"));

        Map<String, ValidationResult> resultMap = new HashMap<>();
        resultMap.put("poolId1", validationResult);
        resultMap.put("poolId2", validationResult);

        Page<List<Pool>> page = new Page<>();
        page.setPageData(pools);

        doReturn(page).when(poolCurator).listAvailableEntitlementPools(any(PoolQualifier.class));

        doAnswer(iom -> iom.getArgument(1)).when(enforcer)
            .filterPools(eq(consumer), anyList(), anyBoolean());

        when(poolCurator.listAllByIds(nullable(Set.class))).thenReturn(new LinkedList<>());

        when(enforcer.preEntitlement(any(Consumer.class), any(Pool.class), any(Integer.class),
            any(CallerType.class))).thenReturn(validationResult);

        // Impl note: this list *must* be mutable, or we'll cause an exception deep in the guts of
        // the autobind flow
        List<PoolQuantity> bestPools = new ArrayList<>();
        bestPools.add(new PoolQuantity(pool1, 1));

        AutobindData data = new AutobindData(consumer, owner)
            .on(now)
            .forProducts(Set.of(product.getId()));

        assertThrows(EntitlementRefusedException.class, () -> manager.entitleByProducts(data));

        // Impl note: Enforcer.preEntitlement gets hit once per iteration for each pool, for a total
        // of 8 times for this test.
        verify(enforcer, times(4)).preEntitlement(eq(consumer), eq(pool1), any(Integer.class),
            any(CallerType.class));
        verify(enforcer, times(4)).preEntitlement(eq(consumer), eq(pool2), any(Integer.class),
            any(CallerType.class));
    }

    @Test
    public void testRefreshPoolsRemovesExpiredSubscriptionsAlongWithItsPoolsAndEnts() {
        PreUnbindHelper preHelper = mock(PreUnbindHelper.class);

        Date expiredStart = TestUtil.createDate(2004, 5, 5);
        Date expiredDate = TestUtil.createDate(2005, 5, 5);

        List<Subscription> subscriptions = new ArrayList<>();

        Owner owner = this.getOwner();
        Product product = TestUtil.createProduct();

        Subscription sub = TestUtil.createSubscription(owner, product);
        sub.setStartDate(expiredStart);
        sub.setEndDate(expiredDate);
        sub.setId("123");
        subscriptions.add(sub);

        this.mockSubscriptions(owner, subscriptions);

        Pool pool = TestUtil.createPool(owner, product)
            .setId("test-pool")
            .setSourceSubscription(new SourceSubscription(sub.getId(), PRIMARY_POOL_SUB_KEY))
            .setStartDate(expiredStart)
            .setEndDate(expiredDate)
            .setConsumed(1L)
            .setManaged(true);

        List<Pool> pools = List.of(pool);

        when(poolCurator.lockAndLoad(anyCollection())).thenReturn(pools);
        mockPoolsList(pools);

        Entitlement ent = TestUtil.createEntitlement()
            .setId("test-ent")
            .setPool(pool)
            .setQuantity(1);

        pool.setEntitlements(Set.of(ent));
        List<Entitlement> poolEntitlements = List.of(ent);

        when(poolCurator.getEntitlementIdsForPools(anyCollection()))
            .thenReturn(List.of(ent.getId()));

        when(entitlementCurator.listAllByIds(anyCollection())).thenReturn(poolEntitlements);

        ValidationResult result = new ValidationResult();
        when(preHelper.getResult()).thenReturn(result);
        when(mockOwnerCurator.getByKey(owner.getKey())).thenReturn(owner);
        this.mockProducts(null, product);
        this.mockRefresh(owner, List.of(product), Collections.emptyList());

        when(poolCurator.listByOwnerAndTypes(eq(owner.getId()), any(PoolType.class))).thenReturn(pools);

        when(poolCurator.getPoolsBySubscriptionIds(anyList())).thenReturn(Collections.emptyList());

        when(consumerCuratorMock.getConsumers(anyCollection())).thenReturn(Collections.emptyList());

        // Any positive value is acceptable here
        when(entitlementCurator.getInBlockSize()).thenReturn(50);

        this.refresherFactory.getRefresher(mockSubAdapter).add(owner).run();

        verify(poolCurator).batchDelete(eq(pools), anyCollection());
        verify(entitlementCurator).batchDeleteByIds(Set.of(ent.getId()));
    }

    private List<Pool> createPoolsWithSourceEntitlement(Entitlement e, Product p) {
        List<Pool> pools = new LinkedList<>();
        Pool pool1 = TestUtil.createPool(e.getOwner(), p);
        pools.add(pool1);
        Pool pool2 = TestUtil.createPool(e.getOwner(), p);
        pools.add(pool2);
        return pools;
    }

    @Test
    public void testCleanup() {
        Pool p = createPoolWithEntitlements();

        when(poolCurator.lockAndLoad(any(Pool.class))).thenReturn(p);
        when(poolCurator.entitlementsIn(p)).thenReturn(new ArrayList<>(p.getEntitlements()));
        PreUnbindHelper preHelper = mock(PreUnbindHelper.class);
        ValidationResult result = new ValidationResult();
        when(preHelper.getResult()).thenReturn(result);

        this.poolService.deletePool(p);

        // And the pool should be deleted:
        verify(poolCurator).delete(p);

        // Check that appropriate events were sent out:
        verify(eventFactory).poolDeleted(p);
        verify(mockEventSink, times(1)).queueEvent(any());
    }

    @Test
    public void testCleanupExpiredPools() {
        Pool p = createPoolWithEntitlements();
        p.setSubscriptionId("subid");
        List<Pool> pools = new LinkedList<>();
        pools.add(p);

        when(poolCurator.lockAndLoad(any(Pool.class))).thenReturn(p);
        when(poolCurator.listExpiredPools(anyInt())).thenReturn(pools);
        when(poolCurator.entitlementsIn(p)).thenReturn(new ArrayList<>(p.getEntitlements()));
        Subscription sub = new Subscription();
        sub.setId(p.getSubscriptionId());
        when(mockSubAdapter.getSubscription(any(String.class))).thenReturn(sub);
        when(mockSubAdapter.isReadOnly()).thenReturn(false);
        PreUnbindHelper preHelper = mock(PreUnbindHelper.class);
        ValidationResult result = new ValidationResult();
        when(preHelper.getResult()).thenReturn(result);

        manager.cleanupExpiredPools();

        // And the pool should be deleted:
        when(poolCurator.lockAndLoad(anyCollection())).thenReturn(pools);
    }

    @Test
    public void testCleanupExpiredPoolsReadOnlySubscriptions() {
        Pool p = createPoolWithEntitlements();
        p.setSubscriptionId("subid");
        List<Pool> pools = List.of(p);

        when(poolCurator.lockAndLoad(anyCollection())).thenReturn(pools);
        when(poolCurator.listExpiredPools(anyInt())).thenReturn(pools);
        when(poolCurator.entitlementsIn(p)).thenReturn(new ArrayList<>(p.getEntitlements()));
        Subscription sub = new Subscription();
        sub.setId(p.getSubscriptionId());
        when(mockSubAdapter.getSubscription(any(String.class))).thenReturn(sub);
        when(mockSubAdapter.isReadOnly()).thenReturn(true);
        PreUnbindHelper preHelper = mock(PreUnbindHelper.class);
        ValidationResult result = new ValidationResult();
        when(preHelper.getResult()).thenReturn(result);

        manager.cleanupExpiredPools();

        // And the pool should be deleted:
        verify(poolCurator).batchDelete(eq(pools), anySet());
        verify(mockSubAdapter, never()).getSubscription(any(String.class));
        // verify(mockSubAdapter, never()).deleteSubscription(any(String.class));
    }

    private Pool createPoolWithEntitlements() {
        Pool newPool = TestUtil.createPool(owner, product);
        Entitlement e1 = new Entitlement(newPool, TestUtil.createConsumer(owner), owner, 1);
        e1.setId("1");

        Entitlement e2 = new Entitlement(newPool, TestUtil.createConsumer(owner), owner, 1);
        e2.setId("2");

        newPool.getEntitlements().add(e1);
        newPool.getEntitlements().add(e2);
        return newPool;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test
    public void testEntitleByProductsEmptyArray() throws Exception {
        Product product = TestUtil.createProduct();
        List<Pool> pools = new ArrayList<>();
        Pool pool1 = TestUtil.createPool(product);
        pools.add(pool1);
        Date now = new Date();

        ValidationResult result = mock(ValidationResult.class);

        // Setup an installed product for the consumer, we'll make the bind request
        // with no products specified, so this should get used instead:
        Set<String> installedPids = Set.of(product.getId());
        ComplianceStatus mockCompliance = new ComplianceStatus(now);
        mockCompliance.addNonCompliantProduct(product.getId());
        when(complianceRules.getStatus(any(Consumer.class),
            any(Date.class), any(Boolean.class))).thenReturn(mockCompliance);

        Page page = mock(Page.class);
        when(page.getPageData()).thenReturn(pools);

        when(poolCurator
            .listAvailableEntitlementPools(any(PoolQualifier.class)))
            .thenReturn(page);

        when(poolCurator.listAllByIds(anyList())).thenReturn(List.of(pool1));

        when(enforcer.preEntitlement(any(Consumer.class), any(Pool.class), anyInt(),
            any(CallerType.class))).thenReturn(result);
        when(enforcer.postEntitlement(any(Consumer.class), anyMap(),
            anyList(), eq(false), anyMap())).thenReturn(new PoolOperations());
        when(result.isSuccessful()).thenReturn(true);

        List<PoolQuantity> bestPools = new ArrayList<>();
        bestPools.add(new PoolQuantity(pool1, 1));
        when(autobindRules.selectBestPools(any(Consumer.class), anySet(), anyList(),
            any(ComplianceStatus.class), any(String.class), anySet(), eq(false)))
            .thenReturn(bestPools);

        // Make the call but provide a null array of product IDs (simulates healing):
        ConsumerType ctype = this.mockConsumerType(TestUtil.createConsumerType());
        Consumer consumer = TestUtil.createConsumer(ctype, owner);

        AutobindData data = new AutobindData(consumer, owner)
            .on(now);

        manager.entitleByProducts(data);

        verify(autobindRules).selectBestPools(any(Consumer.class), eq(installedPids),
            anyList(), eq(mockCompliance), nullable(String.class),
            anySet(), eq(false));
    }

    @Test
    public void testRefreshPoolsRemovesOtherOwnerPoolsForSameSub() {
        PreUnbindHelper preHelper = mock(PreUnbindHelper.class);
        Owner other = new Owner()
            .setKey("otherkey")
            .setDisplayName("othername");

        List<Subscription> subscriptions = new ArrayList<>();

        Owner owner = this.getOwner();
        Product product = TestUtil.createProduct();

        Subscription sub = TestUtil.createSubscription(owner, product);
        sub.setId("123");
        subscriptions.add(sub);

        this.mockSubscriptions(owner, subscriptions);

        List<Pool> pools = new ArrayList<>();
        Pool p = TestUtil.copyFromSub(sub);
        p.setOwner(other);
        p.setSourceSubscription(new SourceSubscription(sub.getId(), PRIMARY_POOL_SUB_KEY));
        p.setConsumed(1L);
        pools.add(p);

        when(poolCurator.lockAndLoad(any(Pool.class))).thenReturn(p);

        mockPoolsList(pools);

        List<Entitlement> poolEntitlements = new ArrayList<>();
        Entitlement ent = TestUtil.createEntitlement();
        ent.setPool(p);
        ent.setQuantity(1);
        poolEntitlements.add(ent);

        when(poolCurator.entitlementsIn(p)).thenReturn(poolEntitlements);

        ValidationResult result = new ValidationResult();
        when(preHelper.getResult()).thenReturn(result);
        when(mockOwnerCurator.getByKey(owner.getKey())).thenReturn(owner);
        this.mockProducts(null, product);
        this.mockRefresh(owner, List.of(product), Collections.emptyList());

        when(poolCurator.listByOwnerAndTypes(eq(owner.getId()), any(PoolType.class))).thenReturn(pools);

        this.refresherFactory.getRefresher(mockSubAdapter).add(owner).run();

        // The pool left over from the pre-migrated subscription should be deleted
        // and granted entitlements should be revoked
        List<Entitlement> entsToDelete = List.of(ent);

        verify(poolCurator).delete(p);
        verify(entitlementCurator).batchDelete(entsToDelete);
        // Make sure pools that don't match the owner were removed from the list
        // They shouldn't cause us to attempt to update existing pools when we
        // haven't created them in the first place
        ArgumentCaptor<Pool> argPool = ArgumentCaptor.forClass(Pool.class);
        verify(poolRules).createAndEnrichPools(argPool.capture(), anyList());
        assertPoolsAreEqual(TestUtil.copyFromSub(sub), argPool.getValue());
    }

    private void mockPoolsList(List<Pool> pools) {
        List<Pool> floating = new LinkedList<>();
        subToPools = new HashMap<>();

        for (Pool pool : pools) {
            String subid = pool.getSubscriptionId();
            if (subid != null) {
                subToPools.computeIfAbsent(subid, (sid) -> new LinkedList<>())
                    .add(pool);
            }
            else {
                floating.add(pool);
            }
        }

        for (String subid : subToPools.keySet()) {
            when(poolCurator.getPoolsBySubscriptionId(subid)).thenReturn(subToPools.get(subid));
        }

        doAnswer(iom -> {
            Collection<String> subids = iom.getArgument(0);
            Map<String, List<Pool>> map = new HashMap<>();

            for (String subid : subids) {
                if (subToPools.containsKey(subid)) {
                    map.put(subid, subToPools.get(subid));
                }
            }

            return map;
        }).when(poolCurator).mapPoolsBySubscriptionIds(anyCollection());

        when(poolCurator.getOwnersFloatingPools(any(Owner.class))).thenReturn(floating);
        when(poolCurator.getPoolsFromBadSubs(any(Owner.class), anyCollection()))
            .thenAnswer(new Answer<List<Pool>>() {
                @SuppressWarnings("unchecked")
                @Override
                public List<Pool> answer(InvocationOnMock iom) {
                    Collection<String> subIds = (Collection<String>) iom.getArguments()[1];
                    List<Pool> results = new LinkedList<>();
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
        PoolRules pRules = new PoolRules(config, entitlementCurator, poolConverter);

        Product prod = TestUtil.createProduct();
        Set<Product> products = new HashSet<>();
        products.add(prod);
        prod.setAttribute(Product.Attributes.VIRT_LIMIT, "4");
        // productCache.addProducts(products);
        Subscription subscription = TestUtil.createSubscription(owner, prod);
        List<SubscriptionInfo> subscriptions = List.of(subscription);

        this.mockProducts(owner, prod);
        this.mockSubscriptions(owner, subscriptions);
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);

        List<Pool> existingPools = new LinkedList<>();
        List<Pool> newPools = pRules.createAndEnrichPools(subscription, existingPools);

        assertEquals(2, newPools.size());
        assertTrue(
            newPools.get(0).getSourceSubscription().getSubscriptionSubKey().equals(DERIVED_POOL_SUB_KEY) ||
                newPools.get(1).getSourceSubscription().getSubscriptionSubKey().equals(DERIVED_POOL_SUB_KEY));
        assertTrue(newPools.get(0).getSourceSubscription().getSubscriptionSubKey()
            .startsWith(PRIMARY_POOL_SUB_KEY) ||
            newPools.get(1).getSourceSubscription().getSubscriptionSubKey().startsWith(PRIMARY_POOL_SUB_KEY));
    }

    @Test
    public void createPoolsForExistingPoolNoneExist() {
        PoolRules pRules = new PoolRules(config, entitlementCurator, poolConverter);

        Owner owner = this.getOwner();
        Product prod = TestUtil.createProduct()
            .setAttribute(Product.Attributes.VIRT_LIMIT, "4");

        Pool pool = TestUtil.createPool(owner, prod)
            .setManaged(true);

        List<Pool> existingPools = new LinkedList<>();
        List<Pool> newPools = pRules.createAndEnrichPools(pool, existingPools);

        assertEquals(2, newPools.size());

        assertTrue(
            newPools.get(0).getSourceSubscription().getSubscriptionSubKey().equals(DERIVED_POOL_SUB_KEY) ||
                newPools.get(1).getSourceSubscription().getSubscriptionSubKey().equals(DERIVED_POOL_SUB_KEY));
        assertTrue(newPools.get(0).getSourceSubscription().getSubscriptionSubKey()
            .startsWith(PRIMARY_POOL_SUB_KEY) ||
            newPools.get(1).getSourceSubscription().getSubscriptionSubKey().startsWith(PRIMARY_POOL_SUB_KEY));
    }

    @Test
    public void createPoolsForExistingSubscriptionsPrimaryExist() {
        Owner owner = this.getOwner();
        PoolRules pRules = new PoolRules(config, entitlementCurator, poolConverter);

        Product prod = TestUtil.createProduct();
        Set<Product> products = new HashSet<>();
        products.add(prod);
        // productCache.addProducts(products);
        prod.setAttribute(Product.Attributes.VIRT_LIMIT, "4");

        Subscription subscription = TestUtil.createSubscription(owner, prod);
        List<SubscriptionInfo> subscriptions = List.of(subscription);

        this.mockProducts(owner, prod);
        this.mockSubscriptions(owner, subscriptions);
        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);

        List<Pool> existingPools = new LinkedList<>();
        Pool p = TestUtil.createPool(prod);
        p.setSourceSubscription(new SourceSubscription(subscription.getId(), PRIMARY_POOL_SUB_KEY));
        existingPools.add(p);
        List<Pool> newPools = pRules.createAndEnrichPools(subscription, existingPools);
        assertEquals(1, newPools.size());
        assertEquals(newPools.get(0).getSourceSubscription().getSubscriptionSubKey(), DERIVED_POOL_SUB_KEY);
    }

    @Test
    public void createPoolsForPoolPrimaryExist() {
        PoolRules pRules = new PoolRules(config, entitlementCurator, poolConverter);

        Owner owner = this.getOwner();
        Product prod = TestUtil.createProduct()
            .setAttribute(Product.Attributes.VIRT_LIMIT, "4");
        Pool pool = TestUtil.createPool(prod)
            .setSourceSubscription(new SourceSubscription(TestUtil.randomString(), PRIMARY_POOL_SUB_KEY))
            .setManaged(true);

        List<Pool> existingPools = List.of(pool);

        List<Pool> newPools = pRules.createAndEnrichPools(pool, existingPools);
        assertEquals(1, newPools.size());
        assertEquals(DERIVED_POOL_SUB_KEY, newPools.get(0).getSourceSubscription().getSubscriptionSubKey());
    }

    @Test
    public void createPoolsForExistingSubscriptionsBonusExist() {
        Owner owner = this.getOwner();
        PoolRules pRules = new PoolRules(config, entitlementCurator, poolConverter);

        Product prod = TestUtil.createProduct();
        Set<Product> products = new HashSet<>();
        products.add(prod);
        // productCache.addProducts(products);
        prod.setAttribute(Product.Attributes.VIRT_LIMIT, "4");

        Subscription subscription = TestUtil.createSubscription(owner, prod);
        List<SubscriptionInfo> subscriptions = List.of(subscription);

        this.mockProducts(owner, prod);
        this.mockSubscriptions(owner, subscriptions);

        when(config.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);

        List<Pool> existingPools = new LinkedList<>();
        Pool p = TestUtil.createPool(prod);
        p.setSourceSubscription(new SourceSubscription(subscription.getId(), DERIVED_POOL_SUB_KEY));
        existingPools.add(p);
        pRules.createAndEnrichPools(subscription, existingPools);
        List<Pool> newPools = pRules.createAndEnrichPools(subscription, existingPools);
        assertEquals(1, newPools.size());
        assertEquals(PRIMARY_POOL_SUB_KEY, newPools.get(0).getSourceSubscription().getSubscriptionSubKey());
    }

    @Test
    public void createPoolsForPoolBonusExist() {
        PoolRules pRules = new PoolRules(config, entitlementCurator, poolConverter);
        Product prod = TestUtil.createProduct();
        prod.setAttribute(Product.Attributes.VIRT_LIMIT, "4");
        List<Pool> existingPools = new LinkedList<>();
        Pool p = TestUtil.createPool(prod);
        p.setSourceSubscription(new SourceSubscription(TestUtil.randomString(), DERIVED_POOL_SUB_KEY));
        existingPools.add(p);
        assertThrows(IllegalStateException.class, () -> pRules.createAndEnrichPools(p, existingPools));
    }

    @Test
    public void expiredEntitlementEvent() {
        Date now = new Date();

        Product p = TestUtil.createProduct();
        p.setAttribute(Product.Attributes.HOST_LIMITED, "true");
        p.setAttribute(Product.Attributes.VIRT_LIMIT, "unlimited");

        Consumer guest = TestUtil.createConsumer(owner);
        guest.setFact("virt.is_guest", "true");
        guest.addInstalledProduct(new ConsumerInstalledProduct()
            .setProductId(p.getId())
            .setProductName(p.getName()));

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
        Set<Entitlement> entitlements = new HashSet<>();
        entitlements.add(ent);
        pool.setEntitlements(entitlements);

        Event event = new Event(Type.EXPIRED, Target.ENTITLEMENT, principal.getData());
        event.setConsumerUuid(guest.getUuid());
        event.setOwnerKey(owner.getOwnerKey());
        when(eventFactory.entitlementExpired(ent)).thenReturn(event);
        when(poolCurator.lockAndLoad(pool)).thenReturn(pool);
        this.poolService.revokeEntitlement(ent);
        String message = event.getMessageText();
        assertNotNull(message);
        message = message.split(": ")[1];
        assertEquals(message,
            i18n.tr("Unmapped guest entitlement expired without establishing a host/guest mapping."));
    }

    @Test
    public void testDeleteExcessEntitlements() throws EntitlementRefusedException {
        ConsumerType ctype = this.mockConsumerType(TestUtil.createConsumerType());
        Consumer consumer = TestUtil.createConsumer(ctype, owner);
        Subscription sub = TestUtil.createSubscription(owner, product);
        sub.setId("testing-subid");
        pool.setSourceSubscription(new SourceSubscription(sub.getId(), PRIMARY_POOL_SUB_KEY));

        Pool derivedPool = TestUtil.createPool(owner, product, 1);
        derivedPool.setAttribute(Pool.Attributes.DERIVED_POOL, "true");
        derivedPool.setSourceSubscription(new SourceSubscription(sub.getId(), "der"));
        derivedPool.setConsumed(3L);
        derivedPool.setId("derivedPool");
        Entitlement primaryEnt = new Entitlement(pool, consumer, owner, 5);
        Entitlement derivedEnt = new Entitlement(derivedPool, consumer, owner, 1);
        derivedEnt.setId("1");

        Set<Entitlement> ents = new HashSet<>();
        ents.add(derivedEnt);
        derivedPool.setEntitlements(ents);

        // before
        assertEquals(3, derivedPool.getConsumed().intValue());
        assertEquals(1, derivedPool.getEntitlements().size());

        Collection<Pool> overPools = List.of(derivedPool);
        when(poolCurator.lock(anyCollection())).thenReturn(overPools);
        when(poolCurator.lockAndLoad(pool)).thenReturn(pool);
        when(enforcer.update(any(Consumer.class), any(Entitlement.class), any(Integer.class)))
            .thenReturn(new ValidationResult());
        when(enforcer.postEntitlement(any(Consumer.class), anyMap(),
            anyList(), eq(true), anyMap())).thenReturn(new PoolOperations());
        when(poolCurator.getOversubscribedBySubscriptionIds(any(String.class), anyMap()))
            .thenReturn(List.of(derivedPool));
        when(poolCurator.retrieveOrderedEntitlementsOf(anyList()))
            .thenReturn(List.of(derivedEnt));
        when(poolCurator.lockAndLoad(derivedPool)).thenReturn(derivedPool);
        pool.setId("primarypool");

        when(enforcer.postEntitlement(eq(consumer), anyMap(),
            anyList(),
            eq(true), anyMap())).thenReturn(mock(PoolOperations.class));
        manager.adjustEntitlementQuantity(consumer, primaryEnt, 3);

        Class<List<Entitlement>> listClass = (Class<List<Entitlement>>) (Class) ArrayList.class;
        ArgumentCaptor<List<Entitlement>> arg = ArgumentCaptor.forClass(listClass);
        verify(entitlementCurator).batchDelete(arg.capture());

        List<Entitlement> entsDeleted = arg.getValue();
        assertThat(entsDeleted, IsCollectionContaining.hasItem(derivedEnt));
        assertEquals(2, derivedPool.getConsumed().intValue());
    }

    @Test
    public void testDeleteExcessEntitlementsBatch() throws EntitlementRefusedException {
        ConsumerType ctype = this.mockConsumerType(TestUtil.createConsumerType());
        Consumer consumer = TestUtil.createConsumer(ctype, owner);
        Subscription sub = TestUtil.createSubscription(owner, product);
        sub.setId("testing-subid");
        pool.setSourceSubscription(new SourceSubscription(sub.getId(), PRIMARY_POOL_SUB_KEY));

        final Pool derivedPool = TestUtil.createPool(owner, product, 1);
        derivedPool.setAttribute(Pool.Attributes.DERIVED_POOL, "true");
        derivedPool.setSourceSubscription(new SourceSubscription(sub.getId(), "der"));
        derivedPool.setConsumed(3L);
        derivedPool.setId("derivedPool");
        Entitlement primaryEnt = new Entitlement(pool, consumer, owner, 5);
        Entitlement derivedEnt = new Entitlement(derivedPool, consumer, owner, 1);
        derivedEnt.setId("1");
        Entitlement derivedEnt2 = new Entitlement(derivedPool, consumer, owner, 1);
        derivedEnt2.setId("2");

        final Pool derivedPool2 = TestUtil.createPool(owner, product, 1);
        derivedPool2.setAttribute(Pool.Attributes.DERIVED_POOL, "true");
        derivedPool2.setSourceSubscription(new SourceSubscription(sub.getId(), "der"));
        derivedPool2.setConsumed(2L);
        derivedPool2.setId("derivedPool2");
        Entitlement derivedEnt3 = new Entitlement(derivedPool2, consumer, owner, 1);
        derivedEnt3.setId("3");

        Set<Entitlement> ents = new HashSet<>();
        ents.add(derivedEnt);
        ents.add(derivedEnt2);
        derivedPool.setEntitlements(ents);

        Set<Entitlement> ents2 = new HashSet<>();
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
        assertEquals(2, derivedPool3.getConsumed().intValue());

        when(poolCurator.lockAndLoad(pool)).thenReturn(pool);
        when(enforcer.update(any(Consumer.class), any(Entitlement.class), any(Integer.class)))
            .thenReturn(new ValidationResult());
        when(enforcer.postEntitlement(any(Consumer.class), anyMap(),
            anyList(), eq(true), anyMap())).thenReturn(new PoolOperations());
        when(poolCurator.getOversubscribedBySubscriptionIds(any(String.class), anyMap())).thenReturn(
            Arrays.asList(derivedPool, derivedPool2, derivedPool3));
        when(poolCurator.retrieveOrderedEntitlementsOf(Arrays.asList(derivedPool, derivedPool2)))
            .thenReturn(Arrays.asList(derivedEnt, derivedEnt2, derivedEnt3));
        Collection<Pool> overPools = Arrays.asList(derivedPool, derivedPool2);
        when(poolCurator.lock(anyCollection())).thenReturn(overPools);
        when(poolCurator.lockAndLoad(derivedPool)).thenReturn(derivedPool);
        when(poolCurator.lockAndLoad(derivedPool2)).thenReturn(derivedPool2);
        when(poolCurator.lockAndLoad(derivedPool3)).thenReturn(derivedPool3);
        pool.setId("primarypool");

        when(enforcer.postEntitlement(eq(consumer), anyMap(),
            anyList(), eq(true), anyMap())).thenReturn(mock(PoolOperations.class));
        manager.adjustEntitlementQuantity(consumer, primaryEnt, 3);

        Class<List<Entitlement>> listClass = (Class<List<Entitlement>>) (Class) ArrayList.class;
        ArgumentCaptor<List<Entitlement>> arg = ArgumentCaptor.forClass(listClass);
        verify(entitlementCurator).batchDelete(arg.capture());

        List<Entitlement> entsDeleted = arg.getValue();
        assertThat(entsDeleted, IsCollectionContaining.hasItems(derivedEnt, derivedEnt2, derivedEnt3));

        assertEquals(1, derivedPool.getConsumed().intValue());
        assertEquals(1, derivedPool2.getConsumed().intValue());
        assertEquals(2, derivedPool3.getConsumed().intValue());
    }

    @Test
    public void testListAvailableEntitlementPoolsWithNullPoolQualifier() {
        Page<List<Pool>> actual = manager.listAvailableEntitlementPools(null);

        assertThat(actual.getPageData())
            .isEmpty();
    }

    @Test
    public void testListAvailableEntitlementPoolsWithEmptyCuratorPage() {
        Page<List<Pool>> emptyPage = new Page<>();
        emptyPage.setPageData(Collections.emptyList());
        emptyPage.setMaxRecords(0);

        doReturn(emptyPage).when(poolCurator).listAvailableEntitlementPools(nullable(PoolQualifier.class));

        Page<List<Pool>> actual = manager.listAvailableEntitlementPools(new PoolQualifier());

        assertThat(actual.getPageData())
            .isEmpty();
    }

    @Test
    public void testListAvailableEntitlementPoolsWithNullConsumerAndActivationKey() {
        Pool pool1 = TestUtil.createPool(product)
            .setId(TestUtil.randomString());
        Pool pool2 = TestUtil.createPool(product)
            .setId(TestUtil.randomString());

        Page<List<Pool>> page = new Page<>();
        page.setPageData(List.of(pool1, pool2));

        PoolQualifier qualifier = new PoolQualifier()
            .addProductId(product.getId())
            .addIds(List.of(pool1.getId(), pool2.getId()));

        doReturn(page).when(poolCurator).listAvailableEntitlementPools(qualifier);

        Page<List<Pool>> actual = manager.listAvailableEntitlementPools(qualifier);

        assertThat(actual.getPageData())
            .hasSize(2)
            .containsExactlyInAnyOrder(pool1, pool2);
    }

    @Test
    public void testListAvailableEntitlementPoolsWithActivationKeyFilteringAndPaging() {
        Pool pool1 = TestUtil.createPool(product)
            .setId(TestUtil.randomString());
        Pool pool2 = TestUtil.createPool(product)
            .setId(TestUtil.randomString());

        ActivationKey key = TestUtil.createActivationKey(owner, List.of(pool1));

        PoolQualifier qualifier = new PoolQualifier()
            .addProductId(product.getId())
            .addIds(List.of(pool1.getId(), pool2.getId()))
            .setActivationKey(key)
            .setOffset(1)
            .setLimit(10);

        Page<List<Pool>> page = new Page<>();
        page.setPageData(List.of(pool1, pool2));

        doReturn(page).when(poolCurator).listAvailableEntitlementPools(qualifier);
        doReturn(List.of(pool1)).when(poolCurator).takeSubList(qualifier, List.of(pool1));

        ValidationResult errorResult = new ValidationResult();
        errorResult.addError(TestUtil.randomString());

        doReturn(new ValidationResult())
            .when(activationKeyRules)
            .runPoolValidationForActivationKey(any(ActivationKey.class), eq(pool1), nullable(Long.class));
        doReturn(errorResult)
            .when(activationKeyRules)
            .runPoolValidationForActivationKey(any(ActivationKey.class), eq(pool2), nullable(Long.class));

        Page<List<Pool>> actual = manager.listAvailableEntitlementPools(qualifier);

        assertThat(actual.getPageData())
            .singleElement()
            .isEqualTo(pool1);
    }

}
