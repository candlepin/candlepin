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
package org.candlepin.policy.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.candlepin.auth.UserPrincipal;
import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.model.ProductPoolAttribute;
import org.candlepin.model.ProvidedProduct;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.model.Subscription;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.policy.js.ProductCache;
import org.candlepin.policy.js.pool.PoolHelper;
import org.candlepin.policy.js.pool.PoolRules;
import org.candlepin.policy.js.pool.PoolUpdate;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;


/**
 * JsPoolRulesTest: Tests for the default rules.
 */
@RunWith(MockitoJUnitRunner.class)
public class PoolRulesStackDerivedTest {

    private PoolRules poolRules;
    private Consumer consumer;

    @Mock private RulesCurator rulesCuratorMock;
    @Mock private ProductServiceAdapter productAdapterMock;
    @Mock private PoolManager poolManagerMock;
    @Mock private Config configMock;
    @Mock private EntitlementCurator entCurMock;

    private ProductCache productCache;
    private UserPrincipal principal;
    private Owner owner;

    private Subscription sub1;
    private Subscription sub2;
    private Subscription sub3;

    private Pool pool1;
    private Pool pool2;
    private Pool pool3;

    // Marketing products:
    private Product prod1;
    private Product prod2;

    // Engineering (provided) products:
    private Product provided1;
    private Product provided2;
    private Product provided3;

    private List<Entitlement> stackedEnts = new LinkedList<Entitlement>();

    private Pool stackDerivedPool;

    private static final String STACK = "a-stack";

    @Before
    public void setUp() {

        // Load the default production rules:
        InputStream is = this.getClass().getResourceAsStream(
            RulesCurator.DEFAULT_RULES_FILE);
        Rules rules = new Rules(Util.readFile(is));

        when(rulesCuratorMock.getUpdated()).thenReturn(new Date());
        when(rulesCuratorMock.getRules()).thenReturn(rules);

        when(configMock.getInt(eq(ConfigProperties.PRODUCT_CACHE_MAX))).thenReturn(100);
        productCache = new ProductCache(configMock, productAdapterMock);

        poolRules = new PoolRules(poolManagerMock, productCache, configMock, entCurMock);
        principal = TestUtil.createOwnerPrincipal();
        owner = principal.getOwners().get(0);

        consumer = new Consumer("consumer", "registeredbybob", owner,
            new ConsumerType(ConsumerTypeEnum.SYSTEM));

        // Two subtly different products stacked together:
        prod1 = TestUtil.createProduct();
        prod1.addAttribute(new ProductAttribute("virt_limit", "2"));
        prod1.addAttribute(new ProductAttribute("stacking_id", STACK));
        prod1.addAttribute(new ProductAttribute("testattr1", "1"));
        when(productAdapterMock.getProductById(prod1.getId())).thenReturn(prod1);

        prod2 = TestUtil.createProduct();
        prod2.addAttribute(new ProductAttribute("virt_limit", "unlimited"));
        prod2.addAttribute(new ProductAttribute("stacking_id", STACK));
        prod2.addAttribute(new ProductAttribute("testattr2", "2"));
        when(productAdapterMock.getProductById(prod2.getId())).thenReturn(prod2);

        provided1 = TestUtil.createProduct();
        provided2 = TestUtil.createProduct();
        provided3 = TestUtil.createProduct();

        // Create three subscriptions with various start/end dates:
        sub1 = createStackedVirtSub(owner, prod1,
            TestUtil.createDate(2010, 1, 1),
            TestUtil.createDate(2015, 1, 1));
        sub1.getProvidedProducts().add(provided1);
        pool1 = TestUtil.copyFromSub(sub1);

        sub2 = createStackedVirtSub(owner, prod2,
            TestUtil.createDate(2011, 1, 1),
            TestUtil.createDate(2017, 1, 1));
        sub2.getProvidedProducts().add(provided2);
        pool2 = TestUtil.copyFromSub(sub2);

        sub3 = createStackedVirtSub(owner, prod2,
            TestUtil.createDate(2012, 1, 1),
            TestUtil.createDate(2020, 1, 1));
        sub3.getProvidedProducts().add(provided3);
        pool3 = TestUtil.copyFromSub(sub3);

        // Initial entitlement from one of the pools:
        stackedEnts.add(createEntFromPool(pool2));
        when(entCurMock.findByStackId(consumer, STACK)).thenReturn(stackedEnts);

        PoolHelper helper = new PoolHelper(poolManagerMock, productCache,
            stackedEnts.get(0));
        stackDerivedPool = helper.createHostRestrictedPool(prod2.getId(), pool2, "6");
    }

    private Subscription createStackedVirtSub(Owner owner, Product product,
        Date start, Date end) {
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct());
        s.setStartDate(start);
        s.setEndDate(end);
        s.setProduct(product);
        s.setContractNumber(Integer.toString(TestUtil.randomInt()));
        s.setOrderNumber(Integer.toString(TestUtil.randomInt()));
        s.setAccountNumber(Integer.toString(TestUtil.randomInt()));
        return s;
    }

    private Entitlement createEntFromPool(Pool pool) {
        Entitlement e = new Entitlement(pool, consumer, 2);
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
        assertEquals(1, stackDerivedPool.getProvidedProducts().size());
        assertEquals(provided2.getId(),
            stackDerivedPool.getProvidedProducts().iterator().next().getProductId());
    }

    @Test
    public void initialAttributes() {
        assertEquals(5, stackDerivedPool.getProductAttributes().size());
        assertEquals("2", stackDerivedPool.getProductAttributeValue("testattr2"));
    }

    @Test
    public void addEarlierStartDate() {
        stackedEnts.add(createEntFromPool(pool1));
        PoolUpdate update = poolRules.updatePoolFromStack(stackDerivedPool);
        assertTrue(update.changed());
        assertTrue(update.getDatesChanged());
        assertEquals(pool1.getStartDate(), stackDerivedPool.getStartDate());
        assertEquals(pool2.getEndDate(), stackDerivedPool.getEndDate());
    }

    @Test
    public void addLaterEndDate() {
        stackedEnts.add(createEntFromPool(pool1));
        stackedEnts.add(createEntFromPool(pool3));
        PoolUpdate update = poolRules.updatePoolFromStack(stackDerivedPool);
        assertTrue(update.changed());
        assertTrue(update.getDatesChanged());
        assertEquals(pool1.getStartDate(), stackDerivedPool.getStartDate());
        assertEquals(pool3.getEndDate(), stackDerivedPool.getEndDate());
    }

    @Test
    public void mergedProductAttributes() {
        stackedEnts.add(createEntFromPool(pool1));
        stackedEnts.add(createEntFromPool(pool3));
        PoolUpdate update = poolRules.updatePoolFromStack(stackDerivedPool);
        assertTrue(update.changed());
        assertTrue(update.getProductAttributesChanged());
        assertEquals(6, stackDerivedPool.getProductAttributes().size());

        assertEquals("2", stackDerivedPool.getProductAttributeValue("testattr2"));
        assertEquals("1", stackDerivedPool.getProductAttributeValue("testattr1"));
    }

    @Test
    public void mergedProvidedProducts() {
        stackedEnts.add(createEntFromPool(pool1));
        stackedEnts.add(createEntFromPool(pool3));
        PoolUpdate update = poolRules.updatePoolFromStack(stackDerivedPool);
        assertTrue(update.getProductsChanged());
        assertEquals(3, stackDerivedPool.getProvidedProducts().size());
        assertTrue(stackDerivedPool.getProvidedProducts().contains(
            new ProvidedProduct(provided1.getId(), provided1.getName())));
        assertTrue(stackDerivedPool.getProvidedProducts().contains(
            new ProvidedProduct(provided2.getId(), provided2.getName())));
        assertTrue(stackDerivedPool.getProvidedProducts().contains(
            new ProvidedProduct(provided3.getId(), provided3.getName())));
    }

    @Test
    public void removeEldestEntitlement() {
        stackedEnts.add(createEntFromPool(pool1));
        stackedEnts.add(createEntFromPool(pool3));
        poolRules.updatePoolFromStack(stackDerivedPool);

        // Should change a variety of settings on the pool.
        stackedEnts.remove(0);
        PoolUpdate update = poolRules.updatePoolFromStack(stackDerivedPool);
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
        poolRules.updatePoolFromStack(stackDerivedPool);

        // Should change a variety of settings on the pool.
        stackedEnts.remove(1);
        PoolUpdate update = poolRules.updatePoolFromStack(stackDerivedPool);
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

        PoolUpdate update = poolRules.updatePoolFromStack(stackDerivedPool);
        assertTrue(update.changed());
        assertTrue(update.getQuantityChanged());
        assertEquals((Long) 2L, stackDerivedPool.getQuantity());
    }

    @Test
    public void virtLimitFromLastVirtLimitEntWhenFirstIsRemoved() {
        stackedEnts.clear();
        stackedEnts.add(createEntFromPool(pool1));
        stackedEnts.add(createEntFromPool(pool2));
        PoolUpdate update = poolRules.updatePoolFromStack(stackDerivedPool);
        assertEquals((Long) 2L, stackDerivedPool.getQuantity());

        stackedEnts.remove(0);
        update = poolRules.updatePoolFromStack(stackDerivedPool);
        assertTrue(update.changed());
        assertTrue(update.getQuantityChanged());
        assertEquals(new Long("-1"), stackDerivedPool.getQuantity());
    }

    @Test
    public void virtLimitNotChangedWhenLastVirtEntIsRemovedFromStack() {
        // Remove virt_limit from pool1 so that it is not considered
        // as virt limiting.
        pool1.getProductAttributes().clear();
        pool1.getProductAttributes().add(new ProductPoolAttribute(
            "stacking_id", STACK, pool1.getProductId()));
        pool1.getProductAttributes().add(new ProductPoolAttribute(
            "testattr2", "2", pool1.getProductId()));

        stackedEnts.clear();
        stackedEnts.add(createEntFromPool(pool1));
        stackedEnts.add(createEntFromPool(pool2));
        PoolUpdate update = poolRules.updatePoolFromStack(stackDerivedPool);
        assertEquals(new Long("-1"), stackDerivedPool.getQuantity());

        stackedEnts.remove(0);
        update = poolRules.updatePoolFromStack(stackDerivedPool);
        assertTrue(update.changed());
        assertTrue(update.getDatesChanged());
        assertFalse(update.getQuantityChanged());
        assertEquals(new Long("-1"), stackDerivedPool.getQuantity());
    }

}

