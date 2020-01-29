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
package org.candlepin.policy;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Mockito.*;

import org.candlepin.auth.UserPrincipal;
import org.candlepin.bind.PoolOperationCallback;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.model.dto.Subscription;
import org.candlepin.policy.js.pool.PoolHelper;
import org.candlepin.policy.js.pool.PoolRules;
import org.candlepin.policy.js.pool.PoolUpdate;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;



/**
 * JsPoolRulesTest: Tests for the default rules.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PoolRulesStackDerivedTest {

    private PoolRules poolRules;
    private Consumer consumer;

    @Mock private RulesCurator rulesCuratorMock;
    @Mock private OwnerProductCurator ownerProductCuratorMock;
    @Mock private PoolManager poolManagerMock;
    @Mock private Configuration configMock;
    @Mock private EntitlementCurator entCurMock;
    @Mock private ProductCurator  productCurator;

    private UserPrincipal principal;
    private Owner owner;

    private Subscription sub1;
    private Subscription sub2;
    private Subscription sub3;
    private Subscription sub4;

    private Pool pool1;
    private Pool pool2;
    private Pool pool3;
    private Pool pool4;

    // Marketing products:
    private Product prod1;
    private Product prod2;
    private Product prod3;

    // Engineering (provided) products:
    private Product provided1;
    private Product provided2;
    private Product provided3;
    private Product provided4;

    private List<Entitlement> stackedEnts = new LinkedList<>();

    private Pool stackDerivedPool;
    private Pool stackDerivedPool2;

    private static final String STACK = "a-stack";

    @BeforeEach
    public void setUp() {

        // Load the default production rules:
        InputStream is = this.getClass().getResourceAsStream(
            RulesCurator.DEFAULT_RULES_FILE);
        Rules rules = new Rules(Util.readFile(is));

        when(rulesCuratorMock.getUpdated()).thenReturn(new Date());
        when(rulesCuratorMock.getRules()).thenReturn(rules);

        when(configMock.getInt(eq(ConfigProperties.PRODUCT_CACHE_MAX))).thenReturn(100);

        poolRules = new PoolRules(poolManagerMock, configMock, entCurMock, ownerProductCuratorMock,
            productCurator);
        principal = TestUtil.createOwnerPrincipal();
        owner = principal.getOwners().get(0);

        ConsumerType ctype = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        ctype.setId("test-ctype");

        consumer = new Consumer("consumer", "registeredbybob", owner, ctype);

        // Two subtly different products stacked together:
        prod1 = TestUtil.createProduct("prod1", "prod1");
        prod1.setAttribute(Product.Attributes.VIRT_LIMIT, "2");
        prod1.setAttribute(Product.Attributes.STACKING_ID, STACK);
        prod1.setAttribute("testattr1", "1");
        when(ownerProductCuratorMock.getProductById(owner, prod1.getId())).thenReturn(prod1);

        prod2 = TestUtil.createProduct("prod2", "prod2");
        prod2.setAttribute(Product.Attributes.VIRT_LIMIT, "unlimited");
        prod2.setAttribute(Product.Attributes.STACKING_ID, STACK);
        prod2.setAttribute("testattr2", "2");
        when(ownerProductCuratorMock.getProductById(owner, prod2.getId())).thenReturn(prod2);

        prod3 = TestUtil.createProduct("prod3", "prod3");
        prod3.setAttribute(Product.Attributes.VIRT_LIMIT, "9");
        prod3.setAttribute(Product.Attributes.STACKING_ID, STACK + "3");
        prod3.setAttribute("testattr2", "2");
        when(ownerProductCuratorMock.getProductById(owner, prod3.getId())).thenReturn(prod3);

        provided1 = TestUtil.createProduct();
        provided2 = TestUtil.createProduct();
        provided3 = TestUtil.createProduct();
        provided4 = TestUtil.createProduct();

        prod1.addProvidedProduct(provided1);
        prod2.addProvidedProduct(provided2);
        prod3.addProvidedProduct(provided3);

        // Create three subscriptions with various start/end dates:
        sub1 = createStackedVirtSub(owner, prod1, TestUtil.createDate(2010, 1, 1),
            TestUtil.createDate(2015, 1, 1));
        pool1 = copyFromSub(sub1);

        sub2 = createStackedVirtSub(owner, prod2, TestUtil.createDate(2011, 1, 1),
            TestUtil.createDate(2017, 1, 1));

        pool2 = copyFromSub(sub2);
        prod2.addProvidedProduct(provided3);
        sub3 = createStackedVirtSub(owner, prod2, TestUtil.createDate(2012, 1, 1),
            TestUtil.createDate(2020, 1, 1));
        pool3 = copyFromSub(sub3);

        sub4 = createStackedVirtSub(owner, prod3, TestUtil.createDate(2012, 1, 1),
            TestUtil.createDate(2020, 1, 1));
        pool4 = copyFromSub(sub4);

        // Initial entitlement from one of the pools:
        stackedEnts.add(createEntFromPool(pool2));

        CandlepinQuery cqmock = mock(CandlepinQuery.class);
        when(cqmock.list()).thenReturn(stackedEnts);
        when(entCurMock.findByStackId(consumer, STACK)).thenReturn(cqmock);

        pool2.setAttribute(Product.Attributes.VIRT_LIMIT, "60");
        pool4.setAttribute(Product.Attributes.VIRT_LIMIT, "80");

        List<Pool> reqPools = new ArrayList<>();
        reqPools.add(pool2);
        Map<String, Entitlement> entitlements = new HashMap<>();
        entitlements.put(pool2.getId(), stackedEnts.get(0));
        Map<String, Map<String, String>> attributes = new HashMap<>();
        attributes.put(pool2.getId(), PoolHelper.getFlattenedAttributes(pool2));
        when(poolManagerMock.createPools(anyListOf(Pool.class))).then(returnsFirstArg());
        PoolOperationCallback poolOperationCallback = PoolHelper.createHostRestrictedPools(poolManagerMock,
            consumer, reqPools, entitlements, attributes, productCurator);
        stackDerivedPool = poolOperationCallback.getPoolCreates().get(0);

        reqPools.clear();
        reqPools.add(pool4);
        entitlements.clear();
        entitlements.put(pool4.getId(), createEntFromPool(pool4));
        attributes.clear();
        attributes.put(pool4.getId(), PoolHelper.getFlattenedAttributes(pool4));
        stackDerivedPool2 = PoolHelper.createHostRestrictedPools(poolManagerMock, consumer, reqPools,
            entitlements, attributes, productCurator).getPoolCreates().get(0);
    }

    private static int lastPoolId = 1;
    /**
     * Creates a Pool and caches stuff
     * @param sub
     * @return
     */
    private Pool copyFromSub(Subscription sub) {
        Pool pool = TestUtil.copyFromSub(sub);
        pool.setId("" + lastPoolId++);
        when(productCurator.getPoolProvidedProductsCached(pool))
            .thenReturn((Set<Product>) pool.getProduct().getProvidedProducts());
        when(productCurator.getPoolDerivedProvidedProductsCached(pool))
            .thenReturn((Set<Product>) pool.getProduct().getProvidedProducts());
        return pool;
    }

    private Subscription createStackedVirtSub(Owner owner, Product product, Date start, Date end) {
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct());
        s.setStartDate(start);
        s.setEndDate(end);
        s.setProduct(product.toDTO());
        s.setContractNumber(Integer.toString(TestUtil.randomInt()));
        s.setOrderNumber(Integer.toString(TestUtil.randomInt()));
        s.setAccountNumber(Integer.toString(TestUtil.randomInt()));
        return s;
    }

    private Entitlement createEntFromPool(Pool pool) {
        Entitlement e = new Entitlement(pool, consumer, owner, 2);
        e.setCreated(new Date());
        try {
            Thread.sleep(1);
        }
        catch (InterruptedException e1) {
            e1.printStackTrace();
        }
        return e;
    }

    @Test
    public void initialDates() {
        assertEquals(pool2.getStartDate(), stackDerivedPool.getStartDate());
        assertEquals(pool2.getEndDate(), stackDerivedPool.getEndDate());
    }

    @Test
    public void initialOrderInfo() {
        assertEquals(pool2.getAccountNumber(), stackDerivedPool.getAccountNumber());
        assertEquals(pool2.getAccountNumber(), stackDerivedPool.getAccountNumber());
        assertEquals(pool2.getOrderNumber(), stackDerivedPool.getOrderNumber());
    }

    @Test
    public void initialProduct() {
        assertEquals(pool2.getProductId(), stackDerivedPool.getProductId());
        assertEquals(pool2.getProductName(), stackDerivedPool.getProductName());
    }

    @Test
    public void initialProvidedProducts() {
        assertEquals(1, stackDerivedPool.getProduct().getProvidedProducts().size());
        assertEquals(provided2.getUuid(),
            stackDerivedPool.getProduct().getProvidedProducts().iterator().next().getUuid());
    }

    @Test
    public void initialAttributes() {
        assertEquals(3, stackDerivedPool.getProduct().getAttributes().size());
        assertEquals("2", stackDerivedPool.getProduct().getAttributeValue("testattr2"));
    }

    @Test
    public void addEarlierStartDate() {
        stackedEnts.add(createEntFromPool(pool1));
        PoolUpdate update = poolRules.updatePoolFromStack(stackDerivedPool, null);
        assertTrue(update.changed());
        assertTrue(update.getDatesChanged());
        assertEquals(pool1.getStartDate(), stackDerivedPool.getStartDate());
        assertEquals(pool2.getEndDate(), stackDerivedPool.getEndDate());
    }

    @Test
    public void addLaterEndDate() {
        stackedEnts.add(createEntFromPool(pool1));
        stackedEnts.add(createEntFromPool(pool3));
        PoolUpdate update = poolRules.updatePoolFromStack(stackDerivedPool, null);
        assertTrue(update.changed());
        assertTrue(update.getDatesChanged());
        assertEquals(pool1.getStartDate(), stackDerivedPool.getStartDate());
        assertEquals(pool3.getEndDate(), stackDerivedPool.getEndDate());
    }

    @Test
    public void mergedProductAttributes() {
        Entitlement ent1 = createEntFromPool(pool1);
        ent1.setCreated(new Date(System.currentTimeMillis() - 86400000));

        stackedEnts.add(ent1);
        stackedEnts.add(createEntFromPool(pool3));
        PoolUpdate update = poolRules.updatePoolFromStack(stackDerivedPool, null);
        assertTrue(update.changed());

        assertTrue(update.getProductAttributesChanged());
        assertEquals(pool1.getProductAttributes(), stackDerivedPool.getProductAttributes());
    }

    @Test
    public void removeEldestEntitlement() {
        stackedEnts.add(createEntFromPool(pool1));
        stackedEnts.add(createEntFromPool(pool3));
        poolRules.updatePoolFromStack(stackDerivedPool, null);

        // Should change a variety of settings on the pool.
        stackedEnts.remove(0);
        PoolUpdate update = poolRules.updatePoolFromStack(stackDerivedPool, null);
        assertTrue(update.changed());
        assertFalse(update.getDatesChanged());

        // Should have changed from pool2 to pool1's info:
        assertEquals(pool1.getProductId(), stackDerivedPool.getProductId());
        assertEquals(pool1.getProductName(), stackDerivedPool.getProductName());

        assertEquals(pool1.getAccountNumber(), stackDerivedPool.getAccountNumber());
        assertEquals(pool1.getContractNumber(), stackDerivedPool.getContractNumber());
        assertEquals(pool1.getOrderNumber(), stackDerivedPool.getOrderNumber());
    }

    @Test
    public void removeEarliestStartingEntitlement() {
        stackedEnts.add(createEntFromPool(pool1));
        stackedEnts.add(createEntFromPool(pool3));
        poolRules.updatePoolFromStack(stackDerivedPool, null);

        // Should change a variety of settings on the pool.
        stackedEnts.remove(1);
        PoolUpdate update = poolRules.updatePoolFromStack(stackDerivedPool, null);
        assertTrue(update.changed());
        assertTrue(update.getDatesChanged());

        assertEquals(pool2.getStartDate(), stackDerivedPool.getStartDate());
        assertEquals(pool3.getEndDate(), stackDerivedPool.getEndDate());
    }

    @Test
    public void virtLimitFromFirstVirtLimitEnt() {
        stackedEnts.clear();
        stackedEnts.add(createEntFromPool(pool1));
        stackedEnts.add(createEntFromPool(pool3));

        PoolUpdate update = poolRules.updatePoolFromStack(stackDerivedPool, null);
        assertTrue(update.changed());
        assertTrue(update.getQuantityChanged());
        assertEquals((Long) 2L, stackDerivedPool.getQuantity());
    }

    @Test
    public void virtLimitFromFirstVirtLimitEntBatch() {
        CandlepinQuery cqmock = mock(CandlepinQuery.class);

        stackedEnts.clear();
        Entitlement e1 = createEntFromPool(pool1);
        e1.setQuantity(4);
        stackedEnts.add(e1);
        Entitlement e2 = createEntFromPool(pool4);
        e2.setQuantity(16);
        stackedEnts.add(e2);
        Class<Set<String>> listClass = (Class<Set<String>>) (Class) HashSet.class;
        ArgumentCaptor<Set<String>> arg = ArgumentCaptor.forClass(listClass);

        when(cqmock.list()).thenReturn(stackedEnts);
        when(entCurMock.findByStackIds(eq(consumer), arg.capture())).thenReturn(cqmock);

        List<PoolUpdate> updates = poolRules.updatePoolsFromStack(consumer,
            Arrays.asList(stackDerivedPool, stackDerivedPool2), null, false);
        Set<String> stackIds = arg.getValue();
        assertEquals(2, stackIds.size());
        assertThat(stackIds, hasItems(STACK, STACK + "3"));

        for (PoolUpdate update : updates) {
            assertTrue(update.changed());
            assertTrue(update.getQuantityChanged());
        }
        assertEquals((Long) 2L, stackDerivedPool.getQuantity());
        assertEquals((Long) 9L, stackDerivedPool2.getQuantity());
    }

    @Test
    public void virtLimitFromLastVirtLimitEntWhenFirstIsRemoved() {
        stackedEnts.clear();
        stackedEnts.add(createEntFromPool(pool1));
        stackedEnts.add(createEntFromPool(pool2));
        PoolUpdate update = poolRules.updatePoolFromStack(stackDerivedPool, null);
        assertEquals((Long) 2L, stackDerivedPool.getQuantity());

        stackedEnts.remove(0);
        update = poolRules.updatePoolFromStack(stackDerivedPool, null);
        assertTrue(update.changed());
        assertTrue(update.getQuantityChanged());
        assertEquals(new Long("-1"), stackDerivedPool.getQuantity());
    }

    @Test
    public void virtLimitNotChangedWhenLastVirtEntIsRemovedFromStack() {
        // Remove virt_limit from pool1 so that it is not considered
        // as virt limiting.
        Product product = pool1.getProduct();
        product.clearAttributes();
        product.setAttribute(Product.Attributes.STACKING_ID, STACK);
        product.setAttribute("testattr2", "2");

        stackedEnts.clear();
        stackedEnts.add(createEntFromPool(pool1));
        stackedEnts.add(createEntFromPool(pool2));

        PoolUpdate update = poolRules.updatePoolFromStack(stackDerivedPool, null);
        assertEquals(new Long("-1"), stackDerivedPool.getQuantity());

        stackedEnts.remove(0);
        update = poolRules.updatePoolFromStack(stackDerivedPool, null);
        assertTrue(update.changed());
        assertTrue(update.getDatesChanged());
        assertFalse(update.getQuantityChanged());
        assertEquals(new Long("-1"), stackDerivedPool.getQuantity());
    }

}

