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
package org.candlepin.policy.js.entitlement;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.bind.PoolOperations;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.controller.PoolConverter;
import org.candlepin.controller.PoolManager;
import org.candlepin.controller.PoolService;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificateCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.model.dto.Subscription;
import org.candlepin.policy.SystemPurposeComplianceRules;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsRunnerProvider;
import org.candlepin.policy.js.JsRunnerRequestCache;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.policy.js.pool.PoolRules;
import org.candlepin.test.TestUtil;
import org.candlepin.util.DateSourceImpl;
import org.candlepin.util.ObjectMapperFactory;
import org.candlepin.util.Util;

import com.google.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;


/**
 * HostedVirtLimitEntitlementRulesTest: Complex tests around the hosted virt limit bonus pool
 * functionality.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class HostedVirtLimitEntitlementRulesTest {

    @Mock
    private RulesCurator rulesCurator;
    @Mock
    private ConsumerCurator consumerCurator;
    @Mock
    private ConsumerTypeCurator consumerTypeCurator;
    @Mock
    private PoolManager poolManager;
    @Mock
    private EntitlementCurator entCurMock;
    @Mock
    private Provider<JsRunnerRequestCache> cacheProvider;
    @Mock
    private JsRunnerRequestCache cache;
    @Mock
    private OwnerCurator ownerCurator;
    @Mock
    private EnvironmentCurator environmentCurator;
    @Mock
    private PoolConverter poolConverter;
    @Mock
    private PoolRules poolRules;
    @Mock
    private PoolCurator poolCurator;
    @Mock
    private EventSink sink;
    @Mock
    private EventFactory eventFactory;
    @Mock
    private EntitlementCurator entitlementCurator;
    @Mock
    private EntitlementCertificateCurator entitlementCertCurator;
    @Mock
    private ComplianceRules complianceRules;
    @Mock
    private SystemPurposeComplianceRules systemPurposeComplianceRules;
    @Mock
    private I18n i18n;

    private PoolService poolService;
    private Enforcer enforcer;
    private DevConfig config;
    private Owner owner;
    private Consumer consumer;

    @BeforeEach
    public void createEnforcer() {
        this.config = TestConfig.defaults();
        this.config.setProperty(ConfigProperties.PRODUCT_CACHE_MAX, "100");
        InputStream is = this.getClass().getResourceAsStream(
            RulesCurator.DEFAULT_RULES_FILE);
        Rules rules = new Rules(Util.readFile(is));

        when(rulesCurator.getRules()).thenReturn(rules);
        when(rulesCurator.getUpdated()).thenReturn(TestUtil.createDate(2010, 1, 1));
        when(cacheProvider.get()).thenReturn(cache);

        JsRunner jsRules = new JsRunnerProvider(rulesCurator, cacheProvider).get();

        ModelTranslator translator = new StandardTranslator(
            consumerTypeCurator, environmentCurator, ownerCurator);
        poolService = spy(new PoolService(poolCurator, sink, eventFactory, poolRules, entitlementCurator,
            consumerCurator, consumerTypeCurator, entitlementCertCurator, complianceRules,
            systemPurposeComplianceRules, config, i18n));
        enforcer = new EntitlementRules(
            new DateSourceImpl(),
            jsRules,
            I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK),
            config,
            consumerCurator,
            consumerTypeCurator,
            ObjectMapperFactory.getRulesObjectMapper(),
            translator,
            poolService);

        owner = TestUtil.createOwner();

        ConsumerType consumerType = this.mockConsumerType(new ConsumerType(ConsumerTypeEnum.SYSTEM));
        consumer = new Consumer()
            .setName("test consumer")
            .setUsername("test user")
            .setOwner(owner)
            .setType(consumerType);
    }

    @Test
    public void hostedParentConsumerPostCreatesNoPool() {
        Pool pool = setupVirtLimitPool();

        Map<String, Entitlement> entitlements = new HashMap<>();
        entitlements.put(pool.getId(), new Entitlement(pool, consumer, owner, 1));

        Map<String, PoolQuantity> poolQuantityMap = new HashMap<>();
        poolQuantityMap.put(pool.getId(), new PoolQuantity(pool, 1));

        this.config.setProperty(ConfigProperties.STANDALONE, "false");

        PoolOperations poolOperations = enforcer.postEntitlement(
            consumer, entitlements, null, false, poolQuantityMap);

        assertTrue(poolOperations.creations().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void hostedVirtLimitAltersBonusPoolQuantity() {
        this.config.setProperty(ConfigProperties.STANDALONE, "false");
        PoolRules poolRules = createRules(config);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, "10");
        ConsumerType ctype = this.mockConsumerType(new ConsumerType(ConsumerTypeEnum.CANDLEPIN));
        consumer.setType(ctype);
        Pool p = TestUtil.copyFromSub(s);
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<>());
        assertEquals(2, pools.size());

        Pool physicalPool = pools.get(0);
        physicalPool.setId("physical");
        Pool virtBonusPool = pools.get(1);
        virtBonusPool.setId("virt");

        assertEquals(10L, physicalPool.getQuantity());
        assertEquals(0, physicalPool.getAttributes().size());

        // Quantity on bonus pool should be virt limit * sub quantity:
        assertEquals(100L, virtBonusPool.getQuantity());
        assertEquals("true", virtBonusPool.getAttributeValue(Product.Attributes.VIRT_ONLY));
        assertEquals("10", virtBonusPool.getProduct().getAttributeValue(Product.Attributes.VIRT_LIMIT));

        Entitlement entitlement = new Entitlement(physicalPool, consumer, owner, 1);
        List<Pool> poolList = new ArrayList<>();
        poolList.add(virtBonusPool);
        ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
        when(poolService.getBySubscriptionIds(anyString(), captor.capture()))
            .thenReturn(poolList);
        when(poolService.getBySubscriptionId(physicalPool.getOwner(),
            physicalPool.getSubscriptionId()))
            .thenReturn(poolList);

        Map<String, Entitlement> entitlements = new HashMap<>();
        entitlements.put(physicalPool.getId(), entitlement);
        Map<String, PoolQuantity> poolQuantityMap = new HashMap<>();
        poolQuantityMap.put(physicalPool.getId(), new PoolQuantity(physicalPool, 1));

        PoolOperations poolOperations = enforcer.postEntitlement(
            consumer, entitlements, null, false, poolQuantityMap);
        Set<String> subscriptionIds = captor.getValue();
        assertEquals(1, subscriptionIds.size());
        assertEquals("subId", subscriptionIds.iterator().next());

        assertEquals(1, poolOperations.updates().size());
        Map.Entry<Pool, Long> poolUpdate = poolOperations.updates().entrySet().iterator().next();
        assertEquals(virtBonusPool, poolUpdate.getKey());
        assertEquals(90L, poolUpdate.getValue().longValue());

        poolService.postUnbind(entitlement);
        verify(poolService).setPoolQuantity(virtBonusPool, 110L);
    }

    @Test
    public void batchHostedVirtLimitAltersBonusPoolQuantity() {
        this.config.setProperty(ConfigProperties.STANDALONE, "false");
        PoolRules poolRules = createRules(config);
        ConsumerType ctype = this.mockConsumerType(new ConsumerType(ConsumerTypeEnum.CANDLEPIN));
        consumer.setType(ctype);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, "10");
        Pool p = TestUtil.copyFromSub(s);
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<>());
        assertEquals(2, pools.size());

        Pool physicalPool = pools.get(0);
        physicalPool.setId("physical");
        Pool virtBonusPool = pools.get(1);
        virtBonusPool.setId("virt");

        assertEquals(10L, physicalPool.getQuantity());
        assertEquals(0, physicalPool.getAttributes().size());

        // Quantity on bonus pool should be virt limit * sub quantity:
        assertEquals(100L, virtBonusPool.getQuantity());
        assertEquals("true", virtBonusPool.getAttributeValue(Product.Attributes.VIRT_ONLY));
        assertEquals("10", virtBonusPool.getProduct().getAttributeValue(Product.Attributes.VIRT_LIMIT));
        Entitlement e = new Entitlement(physicalPool, consumer, owner, 1);

        Subscription s2 = createVirtLimitSub("virtLimitProduct2", 10, "10");
        s2.setId("subId2");
        Pool p2 = TestUtil.copyFromSub(s2);
        List<Pool> pools2 = poolRules.createAndEnrichPools(p2, new LinkedList<>());
        assertEquals(2, pools.size());

        Pool physicalPool2 = pools2.get(0);
        physicalPool2.setId("physical2");
        Pool virtBonusPool2 = pools2.get(1);
        virtBonusPool2.setId("virt2");

        assertEquals(10L, physicalPool2.getQuantity());
        assertEquals(0, physicalPool2.getAttributes().size());

        // Quantity on bonus pool should be virt limit * sub quantity:
        assertEquals(100L, virtBonusPool2.getQuantity());
        assertEquals("true", virtBonusPool2.getAttributeValue(Product.Attributes.VIRT_ONLY));
        assertEquals("10", virtBonusPool2.getProduct().getAttributeValue(Product.Attributes.VIRT_LIMIT));
        Entitlement e2 = new Entitlement(physicalPool2, consumer, owner, 1);

        List<Pool> poolList = new ArrayList<>();
        poolList.add(virtBonusPool);

        List<Pool> poolList2 = new ArrayList<>();
        poolList.add(virtBonusPool2);

        ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
        when(poolService.getBySubscriptionIds(anyString(), captor.capture()))
            .thenReturn(poolList);
        when(poolService.getBySubscriptionId(physicalPool.getOwner(),
            physicalPool.getSubscriptionId()))
            .thenReturn(poolList);
        when(poolService.getBySubscriptionId(physicalPool.getOwner(),
            physicalPool2.getSubscriptionId()))
            .thenReturn(poolList2);

        Map<String, Entitlement> entitlements = new HashMap<>();
        entitlements.put(physicalPool.getId(), e);
        entitlements.put(physicalPool2.getId(), e2);
        Map<String, PoolQuantity> poolQuantityMap = new HashMap<>();
        poolQuantityMap.put(physicalPool.getId(), new PoolQuantity(physicalPool, 1));
        poolQuantityMap.put(physicalPool2.getId(), new PoolQuantity(physicalPool2, 1));

        PoolOperations poolOperations = enforcer.postEntitlement(
            consumer, entitlements, null, false, poolQuantityMap);
        @SuppressWarnings("unchecked")
        Set<String> subscriptionIds = captor.getValue();
        assertEquals(2, subscriptionIds.size());
        assertThat(subscriptionIds, hasItems("subId", "subId2"));
        assertEquals(2, poolOperations.updates().size());

        Map<Pool, Long> poolUpdate = poolOperations.updates();
        assertEquals((Long) 90L, poolUpdate.get(virtBonusPool));
        assertEquals((Long) 90L, poolUpdate.get(virtBonusPool2));

        poolService.postUnbind(e);
        verify(poolService).setPoolQuantity(virtBonusPool, 110L);
        poolService.postUnbind(e2);
        verify(poolService).setPoolQuantity(virtBonusPool2, 110L);
    }

    /*
     * Bonus pools in hosted mode for products with the host_limited attribute are created during
     * binding.
     */
    @Test
    public void hostedVirtLimitWithHostLimitedCreatesBonusPoolsOnBind() {
        this.config.setProperty(ConfigProperties.STANDALONE, "false");
        PoolRules poolRules = createRules(config);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, "unlimited");
        s.getProduct().setAttribute(Product.Attributes.HOST_LIMITED, "true");
        Pool p = TestUtil.copyFromSub(s);
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<>());
        assertEquals(2, pools.size());

        Pool physicalPool = pools.get(0);
        physicalPool.setId("physical");

        assertEquals(10L, physicalPool.getQuantity());
        assertEquals(0, physicalPool.getAttributes().size());

        Entitlement e = new Entitlement(physicalPool, consumer, owner, 1);
        Map<String, Entitlement> entitlements = new HashMap<>();
        entitlements.put(physicalPool.getId(), e);
        Map<String, PoolQuantity> poolQuantityMap = new HashMap<>();
        poolQuantityMap.put(physicalPool.getId(), new PoolQuantity(physicalPool, 1));
        PoolOperations poolOperations = enforcer.postEntitlement(
            consumer, entitlements, null, false, poolQuantityMap);
        assertEquals(1, poolOperations.creations().size());
    }

    @Test
    public void hostedVirtLimitUnlimitedBonusPoolQuantity() {
        this.config.setProperty(ConfigProperties.STANDALONE, "false");
        PoolRules poolRules = createRules(config);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, "unlimited");
        Pool p = TestUtil.copyFromSub(s);
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<>());
        assertEquals(2, pools.size());

        Pool physicalPool = pools.get(0);
        physicalPool.setId("physical");
        Pool virtBonusPool = pools.get(1);
        virtBonusPool.setId("virt");

        assertEquals(10L, physicalPool.getQuantity());
        assertEquals(0, physicalPool.getAttributes().size());

        // Quantity on bonus pool should be -1:
        assertEquals(-1L, virtBonusPool.getQuantity());
        assertEquals("true", virtBonusPool.getAttributeValue(Product.Attributes.VIRT_ONLY));
        assertEquals("unlimited",
            virtBonusPool.getProduct().getAttributeValue(Product.Attributes.VIRT_LIMIT));

        Entitlement e = new Entitlement(physicalPool, consumer, owner, 1);
        List<Pool> poolList = new ArrayList<>();
        poolList.add(virtBonusPool);
        when(poolService.getBySubscriptionId(physicalPool.getOwner(),
            physicalPool.getSubscriptionId()))
            .thenReturn(poolList);

        Map<String, Entitlement> entitlements = new HashMap<>();
        entitlements.put(physicalPool.getId(), e);
        Map<String, PoolQuantity> poolQuantityMap = new HashMap<>();
        poolQuantityMap.put(physicalPool.getId(), new PoolQuantity(physicalPool, 1));
        enforcer.postEntitlement(consumer, entitlements, null, false,
            poolQuantityMap);
        verify(poolService, never()).setPoolQuantity(any(Pool.class), anyInt());

        poolService.postUnbind(e);
        verify(poolService, never()).setPoolQuantity(any(Pool.class), anyInt());
    }

    /*
     * Bonus pools should not be created when we are in a hosted scenario without distributor binds.
     */
    @Test
    public void noBonusPoolsForHostedNonDistributorBinds() {
        this.config.setProperty(ConfigProperties.STANDALONE, "false");
        PoolRules poolRules = createRules(config);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, "unlimited");
        Pool p = TestUtil.copyFromSub(s);
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<>());
        assertEquals(2, pools.size());

        Pool physicalPool = pools.get(0);
        physicalPool.setId("physical");
        Pool virtBonusPool = pools.get(1);
        virtBonusPool.setId("virt");

        assertEquals(10L, physicalPool.getQuantity());
        assertEquals(0, physicalPool.getAttributes().size());

        // Quantity on bonus pool should be -1:
        assertEquals(-1L, virtBonusPool.getQuantity());
        assertEquals("true", virtBonusPool.getAttributeValue(Product.Attributes.VIRT_ONLY));
        assertEquals("unlimited",
            virtBonusPool.getProduct().getAttributeValue(Product.Attributes.VIRT_LIMIT));

        Entitlement e = new Entitlement(physicalPool, consumer, owner, 1);

        Map<String, Entitlement> entitlements = new HashMap<>();
        entitlements.put(physicalPool.getId(), e);
        Map<String, PoolQuantity> poolQuantityMap = new HashMap<>();
        poolQuantityMap.put(physicalPool.getId(), new PoolQuantity(physicalPool, 1));
        enforcer.postEntitlement(consumer, entitlements, null, false,
            poolQuantityMap);
        verify(poolService, never()).createPool(any(Pool.class));
        verify(poolService, never()).setPoolQuantity(any(Pool.class), anyInt());

        poolService.postUnbind(e);
        verify(poolService, never()).setPoolQuantity(any(Pool.class), anyInt());
        verify(poolService, never()).setPoolQuantity(any(Pool.class), anyLong());
    }

    @Test
    public void exportAllPhysicalZeroBonusPoolQuantity() {
        this.config.setProperty(ConfigProperties.STANDALONE, "false");
        PoolRules poolRules = createRules(config);
        ConsumerType ctype = this.mockConsumerType(new ConsumerType(ConsumerTypeEnum.CANDLEPIN));
        consumer.setType(ctype);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, "unlimited");
        Pool p = TestUtil.copyFromSub(s);
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<>());
        assertEquals(2, pools.size());

        Pool physicalPool = pools.get(0);
        physicalPool.setId("physical");
        Pool virtBonusPool = pools.get(1);
        virtBonusPool.setId("virt");

        assertEquals(10L, physicalPool.getQuantity());
        assertEquals(0, physicalPool.getAttributes().size());

        // Quantity on bonus pool should be -1:
        assertEquals(-1L, virtBonusPool.getQuantity());
        assertEquals("true", virtBonusPool.getAttributeValue(Product.Attributes.VIRT_ONLY));
        assertEquals("unlimited",
            virtBonusPool.getProduct().getAttributeValue(Product.Attributes.VIRT_LIMIT));

        Entitlement e = new Entitlement(physicalPool, consumer, owner, 10);
        physicalPool.setConsumed(10L);
        physicalPool.setExported(0L);
        List<Pool> poolList = new ArrayList<>();
        poolList.add(virtBonusPool);
        ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
        when(poolService.getBySubscriptionIds(anyString(), captor.capture()))
            .thenReturn(poolList);
        when(poolService.getBySubscriptionId(physicalPool.getOwner(), "subId"))
            .thenReturn(poolList);
        Map<String, Entitlement> entitlements = new HashMap<>();
        entitlements.put(physicalPool.getId(), e);
        Map<String, PoolQuantity> poolQuantityMap = new HashMap<>();
        poolQuantityMap.put(physicalPool.getId(), new PoolQuantity(physicalPool, 10));
        PoolOperations poolOperations = enforcer.postEntitlement(
            consumer, entitlements, null, false, poolQuantityMap);

        @SuppressWarnings("unchecked")
        Set<String> subscriptionIds = captor.getValue();
        assertEquals(1, subscriptionIds.size());
        assertEquals("subId", subscriptionIds.iterator().next());

        assertEquals(1, poolOperations.updates().size());
        Map.Entry<Pool, Long> poolUpdate = poolOperations.updates().entrySet().iterator().next();
        assertEquals(virtBonusPool, poolUpdate.getKey());
        assertEquals((Long) 0L, poolUpdate.getValue());

        virtBonusPool.setQuantity(0L);

        poolService.postUnbind(e);

        ArgumentCaptor<Pool> quantityCaptor = ArgumentCaptor.forClass(Pool.class);
        verify(poolCurator).merge(quantityCaptor.capture());

        assertEquals(-1, quantityCaptor.getValue().getQuantity());
    }

    @Test
    public void hostedVirtLimitDoesNotAlterQuantitiesForHostLimited() {
        this.config.setProperty(ConfigProperties.STANDALONE, "false");
        ConsumerType ctype = this.mockConsumerType(new ConsumerType(ConsumerTypeEnum.CANDLEPIN));
        consumer.setType(ctype);

        Pool virtBonusPool = setupVirtLimitPool();
        virtBonusPool.setQuantity(100L);
        virtBonusPool.setAttribute(Product.Attributes.HOST_LIMITED, "true");
        virtBonusPool.setAttribute(Product.Attributes.VIRT_ONLY, "true");
        virtBonusPool.setAttribute(Product.Attributes.VIRT_LIMIT, "10");
        virtBonusPool.setAttribute(Pool.Attributes.DERIVED_POOL, "true");

        Entitlement e = new Entitlement(virtBonusPool, consumer, owner, 1);
        List<Pool> poolList = new ArrayList<>();
        poolList.add(virtBonusPool);
        when(poolService.getBySubscriptionId(virtBonusPool.getOwner(),
            virtBonusPool.getSubscriptionId()))
            .thenReturn(poolList);

        Map<String, Entitlement> entitlements = new HashMap<>();
        entitlements.put(virtBonusPool.getId(), e);
        Map<String, PoolQuantity> poolQuantityMap = new HashMap<>();
        poolQuantityMap.put(virtBonusPool.getId(), new PoolQuantity(virtBonusPool, 1));
        enforcer.postEntitlement(consumer, entitlements, null, false,
            poolQuantityMap);
        verify(poolService, never()).setPoolQuantity(virtBonusPool, -10L);

        poolService.postUnbind(e);
        verify(poolService, never()).setPoolQuantity(virtBonusPool, 10L);
    }

    private PoolRules createRules(Configuration config) {
        return new PoolRules(config, entCurMock, poolConverter);
    }

    private ConsumerType mockConsumerType(ConsumerType ctype) {
        if (ctype == null) {
            return ctype;
        }
        // Ensure the type has an ID
        if (ctype.getId() == null) {
            ctype.setId("test-ctype-" + ctype.getLabel() + "-" + TestUtil.randomInt());
        }

        when(consumerTypeCurator.getByLabel(ctype.getLabel())).thenReturn(ctype);
        when(consumerTypeCurator.getByLabel(eq(ctype.getLabel()), anyBoolean())).thenReturn(ctype);
        when(consumerTypeCurator.get(ctype.getId())).thenReturn(ctype);

        doAnswer((Answer<ConsumerType>) invocation -> {
            Object[] args = invocation.getArguments();
            Consumer consumer = (Consumer) args[0];
            ConsumerTypeCurator curator = (ConsumerTypeCurator) invocation.getMock();
            ConsumerType ctype1;

            if (consumer == null || consumer.getTypeId() == null) {
                throw new IllegalArgumentException("consumer is null or lacks a type ID");
            }

            ctype1 = curator.get(consumer.getTypeId());
            if (ctype1 == null) {
                throw new IllegalStateException("No such consumer type: " + consumer.getTypeId());
            }

            return ctype1;
        }).when(consumerTypeCurator).getConsumerType(any(Consumer.class));

        return ctype;
    }

    private Subscription createVirtLimitSub(String productId, int quantity, String virtLimit) {
        Product product = TestUtil.createProduct(productId, productId)
            .setAttribute(Product.Attributes.VIRT_LIMIT, virtLimit);

        return TestUtil.createSubscription(owner, product)
            .setQuantity((long) quantity)
            .setId("subId");
    }

    private Pool setupVirtLimitPool() {
        Product product = TestUtil.createProduct("a-product", "A virt_limit product");
        Pool pool = TestUtil.createPool(owner, product);
        pool.setAttribute(Product.Attributes.VIRT_LIMIT, "10");
        pool.setId("fakeid" + TestUtil.randomInt());
        return pool;
    }

}
