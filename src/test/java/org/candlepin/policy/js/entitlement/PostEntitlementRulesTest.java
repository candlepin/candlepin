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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import org.candlepin.bind.PoolOperations;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.controller.PoolService;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsRunnerProvider;
import org.candlepin.policy.js.JsRunnerRequestCache;
import org.candlepin.test.TestUtil;
import org.candlepin.util.DateSourceImpl;
import org.candlepin.util.ObjectMapperFactory;
import org.candlepin.util.Util;

import com.google.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import org.xnap.commons.i18n.I18nFactory;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


/**
 * PostEntitlementRulesTest: Tests for post-entitlement rules, as well as the post-unbind rules
 * which tend to clean up after them.
 *
 * These tests only cover standalone/universal situations. See hosted specific test suites for
 * behaviour which is specific to hosted.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PostEntitlementRulesTest {

    @Mock
    private RulesCurator rulesCurator;
    @Mock
    private ConsumerCurator consumerCurator;
    @Mock
    private ConsumerTypeCurator consumerTypeCurator;
    @Mock
    private Provider<JsRunnerRequestCache> cacheProvider;
    @Mock
    private JsRunnerRequestCache cache;
    @Mock
    private OwnerCurator ownerCurator;
    @Mock
    private EnvironmentCurator environmentCurator;
    @Mock
    private PoolService poolService;

    private Enforcer enforcer;
    private DevConfig config;
    private Owner owner;
    private Consumer consumer;

    @BeforeEach
    public void createEnforcer() {
        this.config = TestConfig.defaults();
        this.config.setProperty(ConfigProperties.PRODUCT_CACHE_MAX, "100");

        InputStream is = this.getClass().getResourceAsStream(RulesCurator.DEFAULT_RULES_FILE);
        Rules rules = new Rules(Util.readFile(is));

        when(rulesCurator.getRules()).thenReturn(rules);
        when(rulesCurator.getUpdated()).thenReturn(TestUtil.createDate(2010, 1, 1));
        when(cacheProvider.get()).thenReturn(cache);

        JsRunner jsRules = new JsRunnerProvider(rulesCurator, cacheProvider).get();

        ModelTranslator translator = new StandardTranslator(
            consumerTypeCurator, environmentCurator, ownerCurator);
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
    public void virtLimitSubPool() {
        this.config.setProperty(ConfigProperties.STANDALONE, "true");
        Pool pool = setupVirtLimitPool();
        pool.setAttribute(Product.Attributes.VIRT_LIMIT, "10");
        Entitlement e = new Entitlement(pool, consumer, owner, 5);

        Map<String, Entitlement> entitlements = new HashMap<>();
        entitlements.put(pool.getId(), e);
        Map<String, PoolQuantity> poolQuantityMap = new HashMap<>();
        poolQuantityMap.put(pool.getId(), new PoolQuantity(pool, 5));
        // Pool quantity should be virt_limit:
        PoolOperations poolOperations = enforcer.postEntitlement(
            consumer, entitlements, null, false, poolQuantityMap);

        List<Pool> pools = poolOperations.creations();
        assertEquals(1, pools.size());
        assertEquals(10L, pools.get(0).getQuantity().longValue());
    }

    @Test
    public void virtLimitSubPoolBatch() {
        this.config.setProperty(ConfigProperties.STANDALONE, "true");
        Pool pool = setupVirtLimitPool();
        pool.setAttribute(Product.Attributes.VIRT_LIMIT, "10");
        Entitlement e = new Entitlement(pool, consumer, owner, 5);

        Pool pool2 = setupVirtLimitPool();
        pool2.setAttribute(Product.Attributes.VIRT_LIMIT, "10");
        Entitlement e2 = new Entitlement(pool2, consumer, owner, 5);

        Map<String, Entitlement> entitlements = new HashMap<>();
        entitlements.put(pool.getId(), e);
        entitlements.put(pool2.getId(), e2);
        Map<String, PoolQuantity> poolQuantityMap = new HashMap<>();
        poolQuantityMap.put(pool.getId(), new PoolQuantity(pool, 5));
        poolQuantityMap.put(pool2.getId(), new PoolQuantity(pool2, 5));

        // Pool quantity should be virt_limit:
        PoolOperations poolOperations = enforcer.postEntitlement(
            consumer, entitlements, null, false, poolQuantityMap);

        List<Pool> pools = poolOperations.creations();
        assertEquals(2, pools.size());
        assertEquals(10L, pools.get(0).getQuantity().longValue());
        assertEquals(10L, pools.get(1).getQuantity().longValue());
    }

    @Test
    public void unlimitedVirtLimitSubPool() {
        this.config.setProperty(ConfigProperties.STANDALONE, "true");
        Pool pool = setupVirtLimitPool();
        pool.setAttribute(Product.Attributes.VIRT_LIMIT, "unlimited");
        Entitlement e = new Entitlement(pool, consumer, owner, 5);

        Map<String, Entitlement> entitlements = new HashMap<>();
        entitlements.put(pool.getId(), e);
        Map<String, PoolQuantity> poolQuantityMap = new HashMap<>();
        poolQuantityMap.put(pool.getId(), new PoolQuantity(pool, 5));

        PoolOperations poolOperations = enforcer.postEntitlement(
            consumer, entitlements, null, false, poolQuantityMap);

        // Pool quantity should be virt_limit:
        List<Pool> pools = poolOperations.creations();
        assertEquals(1, pools.size());
        assertEquals(-1L, pools.get(0).getQuantity().longValue());
    }

    @Test
    public void unlimitedVirtLimitSubPoolBatch() {
        this.config.setProperty(ConfigProperties.STANDALONE, "true");
        Pool pool = setupVirtLimitPool();
        pool.setAttribute(Product.Attributes.VIRT_LIMIT, "unlimited");
        Entitlement e = new Entitlement(pool, consumer, owner, 5);

        Pool pool2 = setupVirtLimitPool();
        pool2.setAttribute(Product.Attributes.VIRT_LIMIT, "unlimited");
        Entitlement e2 = new Entitlement(pool2, consumer, owner, 5);

        Map<String, Entitlement> entitlements = new HashMap<>();
        entitlements.put(pool.getId(), e);
        entitlements.put(pool2.getId(), e2);
        Map<String, PoolQuantity> poolQuantityMap = new HashMap<>();
        poolQuantityMap.put(pool.getId(), new PoolQuantity(pool, 5));
        poolQuantityMap.put(pool2.getId(), new PoolQuantity(pool2, 5));

        PoolOperations poolOperations = enforcer.postEntitlement(
            consumer, entitlements, null, false, poolQuantityMap);

        // Pool quantity should be virt_limit:
        List<Pool> pools = poolOperations.creations();
        assertEquals(2, pools.size());
        assertEquals(-1L, pools.get(0).getQuantity().longValue());
        assertEquals(-1L, pools.get(1).getQuantity().longValue());
    }

    // Sub-pools should not be created when distributors bind:
    @Test
    public void noSubPoolsForDistributorBinds() {
        this.config.setProperty(ConfigProperties.STANDALONE, "true");
        ConsumerType cType = this.mockConsumerType(new ConsumerType(ConsumerTypeEnum.CANDLEPIN));
        consumer.setType(cType);
        Pool pool = setupVirtLimitPool();
        Entitlement e = new Entitlement(pool, consumer, owner, 1);

        Map<String, Entitlement> entitlements = new HashMap<>();
        entitlements.put(pool.getId(), e);
        Map<String, PoolQuantity> poolQuantityMap = new HashMap<>();
        poolQuantityMap.put(pool.getId(), new PoolQuantity(pool, 1));
        PoolOperations poolOperations = enforcer.postEntitlement(
            consumer, entitlements, null, false, poolQuantityMap);

        assertTrue(poolOperations.creations().isEmpty());
        assertTrue(poolOperations.updates().isEmpty());
    }

    // Sub-pools should not be created when guests bind:
    @Test
    public void noSubPoolsForGuestBinds() {
        this.config.setProperty(ConfigProperties.STANDALONE, "true");
        Pool pool = setupVirtLimitPool();
        consumer.setFact("virt.is_guest", "true");
        Entitlement e = new Entitlement(pool, consumer, owner, 1);

        Map<String, Entitlement> entitlements = new HashMap<>();
        entitlements.put(pool.getId(), e);
        Map<String, PoolQuantity> poolQuantityMap = new HashMap<>();
        poolQuantityMap.put(pool.getId(), new PoolQuantity(pool, 1));
        PoolOperations poolOperations = enforcer.postEntitlement(
            consumer, entitlements, null, false, poolQuantityMap);

        assertTrue(poolOperations.creations().isEmpty());
        assertTrue(poolOperations.updates().isEmpty());
    }

    private ConsumerType mockConsumerType(ConsumerType ctype) {
        if (ctype != null) {
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
                ConsumerType ctype1 = null;

                if (consumer == null || consumer.getTypeId() == null) {
                    throw new IllegalArgumentException("consumer is null or lacks a type ID");
                }

                ctype1 = curator.get(consumer.getTypeId());
                if (ctype1 == null) {
                    throw new IllegalStateException("No such consumer type: " + consumer.getTypeId());
                }

                return ctype1;
            }).when(consumerTypeCurator).getConsumerType(any(Consumer.class));
        }

        return ctype;
    }

    private Pool setupVirtLimitPool() {
        String productId = "a-product";
        Product product = TestUtil.createProduct(productId, "A virt_limit product");
        Pool pool = TestUtil.createPool(owner, product);
        pool.setAttribute(Product.Attributes.VIRT_LIMIT, "10");
        pool.setId("fakeid" + TestUtil.randomInt());
        return pool;
    }
}
