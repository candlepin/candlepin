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
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.candlepin.auth.UserPrincipal;
import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolAttribute;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.model.ProductPoolAttribute;
import org.candlepin.model.ProvidedProduct;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.model.Subscription;
import org.candlepin.policy.js.ProductCache;
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
public class PoolRulesTest {

    private PoolRules poolRules;

    @Mock private RulesCurator rulesCuratorMock;
    @Mock private ProductServiceAdapter productAdapterMock;
    @Mock private PoolManager poolManagerMock;
    @Mock private Config configMock;
    @Mock private EntitlementCurator entCurMock;

    private ProductCache productCache;
    private UserPrincipal principal;
    private Owner owner;

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
    }

    @Test
    public void hostedVirtLimitBadValueDoesntTraceBack() {
        when(configMock.standalone()).thenReturn(false);
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct());
        s.getProduct().addAttribute(new ProductAttribute("virt_limit", "badvalue"));
        s.setQuantity(10L);


        List<Pool> pools = null;
        try {
            pools = poolRules.createPools(s);
        }
        catch (Exception e) {
            fail("Create pools should not have thrown an exception on bad value for " +
                 "virt_limit. " + e.getMessage());
        }
        assertEquals(1, pools.size());

        Pool physicalPool = pools.get(0);
        physicalPool.setId("physical");

        assertEquals(new Long(10), physicalPool.getQuantity());
        assertEquals(0, physicalPool.getAttributes().size());
    }

    @Test
    public void providedProductsChanged() {
        // Subscription with two provided products:
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct());
        Product product1 = TestUtil.createProduct();
        Product product2 = TestUtil.createProduct();
        Product product3 = TestUtil.createProduct();
        s.getProvidedProducts().add(product1);
        s.getProvidedProducts().add(product2);

        // Setup a pool with a single (different) provided product:
        Pool p = TestUtil.copyFromSub(s);
        p.getProvidedProducts().clear();
        p.getProvidedProducts().add(
            new ProvidedProduct(product3.getId(), product3.getName(), p));

        List<Pool> existingPools = new LinkedList<Pool>();
        existingPools.add(p);
        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools);
        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertTrue(update.getProductsChanged());
        assertFalse(update.getDatesChanged());
        assertFalse(update.getQuantityChanged());
    }

    @Test
    public void productNameChanged() {
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct());

        // Setup a pool with a single (different) provided product:
        Pool p = TestUtil.copyFromSub(s);
        p.setProductName("somethingelse");

        List<Pool> existingPools = Arrays.asList(p);
        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools);

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertTrue(update.getProductsChanged());
        assertFalse(update.getDatesChanged());
        assertFalse(update.getQuantityChanged());
        assertEquals(s.getProduct().getName(), update.getPool().getProductName());
    }

    @Test
    public void datesNameChanged() {
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct());

        // Setup a pool with a single (different) provided product:
        Pool p = TestUtil.copyFromSub(s);
        p.setEndDate(new Date());

        List<Pool> existingPools = Arrays.asList(p);
        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools);

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertFalse(update.getProductsChanged());
        assertTrue(update.getDatesChanged());
        assertFalse(update.getQuantityChanged());
        assertEquals(s.getEndDate(), update.getPool().getEndDate());
    }

    @Test
    public void quantityChanged() {
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct());

        // Setup a pool with a single (different) provided product:
        Pool p = TestUtil.copyFromSub(s);
        p.setQuantity(2000L);

        List<Pool> existingPools = Arrays.asList(p);
        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools);

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertFalse(update.getProductsChanged());
        assertFalse(update.getDatesChanged());
        assertTrue(update.getQuantityChanged());
        assertEquals(s.getQuantity(), update.getPool().getQuantity());
    }

    @Test
    public void virtOnlyQuantityChanged() {
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct());
        s.getProduct().addAttribute(new ProductAttribute("virt_limit", "5"));
        s.setQuantity(10L);

        // Setup a pool with a single (different) provided product:
        Pool p = TestUtil.copyFromSub(s);
        p.addAttribute(new PoolAttribute("virt_only", "true"));
        p.addAttribute(new PoolAttribute("pool_derived", "true"));
        p.setQuantity(40L);

        List<Pool> existingPools = Arrays.asList(p);
        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools);

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertFalse(update.getProductsChanged());
        assertFalse(update.getDatesChanged());
        assertTrue(update.getQuantityChanged());
        assertEquals(Long.valueOf(50), update.getPool().getQuantity());
    }

    @Test
    public void updatePoolWithNewProductAttributes() {
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct());
        Pool p = TestUtil.copyFromSub(s);

        // Update the subscription's product.
        String testAttributeKey = "multi-entitlement";
        s.getProduct().setAttribute(testAttributeKey, "yes");

        when(productAdapterMock.getProductById(s.getProduct().getId()))
            .thenReturn(s.getProduct());
        List<Pool> existingPools = Arrays.asList(p);
        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools);

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        Pool updatedPool = update.getPool();
        assertTrue(updatedPool.hasProductAttribute(testAttributeKey));
    }

    @Test
    public void updatePoolWithNewSubProductAttributes() {
        Subscription s = createSubscriptionWithSubProduct();
        Pool p = TestUtil.copyFromSub(s);

        // Update the subscription's sub-product:
        s.getDerivedProduct().setAttribute("a", "new value");

        List<Pool> existingPools = Arrays.asList(p);
        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools);

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        Pool updatedPool = update.getPool();
        assertTrue(updatedPool.hasSubProductAttribute("a"));
    }

    @Test
    public void updateDerivedPoolWithNewSubProductAttributes() {
        Subscription s = createSubscriptionWithSubProduct();
        Pool p = TestUtil.copyFromSub(s);

        // Simulate that this is a derived pool. When we add a new sub-product attribute
        // to the subscription, it should show up as a primary product attribute on a
        // sub pool:
        p.setAttribute("pool_derived", "true");

        // Update the subscription's sub-product:
        s.getDerivedProduct().setAttribute("a", "new value");

        List<Pool> existingPools = Arrays.asList(p);
        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools);

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        Pool updatedPool = update.getPool();
        assertTrue(updatedPool.hasProductAttribute("a"));
        assertFalse(updatedPool.hasSubProductAttribute("a"));
    }

    @Test
    public void updatePoolWithModifiedProductAttributes() {
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct());
        Pool p = TestUtil.copyFromSub(s);

        String testAttributeKey = "multi-entitlement";
        String expectedAttributeValue = "yes";

        // Simulate an attribute that was added during pool creation:
        p.setProductAttribute(testAttributeKey, "no", s.getProduct().getId());

        // Update the subscription's product with new attribute value:
        s.getProduct().setAttribute(testAttributeKey, expectedAttributeValue);

        when(productAdapterMock.getProductById(s.getProduct().getId()))
            .thenReturn(s.getProduct());

        List<Pool> existingPools = new LinkedList<Pool>();
        existingPools.add(p);
        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools);

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        Pool updatedPool = update.getPool();
        assertTrue(updatedPool.hasProductAttribute(testAttributeKey));
        assertEquals(expectedAttributeValue,
            updatedPool.getProductAttribute(testAttributeKey).getValue());
    }

    @Test
    public void updatePoolWithModifiedSubProductAttributes() {
        Subscription s = createSubscriptionWithSubProduct();
        s.getDerivedProduct().setAttribute("a", "orig value");
        Pool p = TestUtil.copyFromSub(s);

        // Update the subscription's sub-product:
        String newVal = "new value";
        s.getDerivedProduct().setAttribute("a", newVal);

        List<Pool> existingPools = Arrays.asList(p);
        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools);

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        Pool updatedPool = update.getPool();
        assertTrue(updatedPool.hasSubProductAttribute("a"));
        assertEquals(newVal, updatedPool.getDerivedProductAttribute("a").getValue());
    }

    private Subscription createSubscriptionWithSubProduct() {
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct());
        Product subProd = TestUtil.createProduct();
        s.setDerivedProduct(subProd);
        when(productAdapterMock.getProductById(s.getProduct().getId()))
            .thenReturn(s.getProduct());
        when(productAdapterMock.getProductById(s.getDerivedProduct().getId()))
            .thenReturn(s.getDerivedProduct());
        return s;
    }

    @Test
    public void updateDerivedPoolWithModifiedSubProductAttributes() {
        Subscription s = createSubscriptionWithSubProduct();
        Pool p = TestUtil.copyFromSub(s);
        p.setAttribute("pool_derived", "true");

        s.getDerivedProduct().setAttribute("a", "orig value");
        // Simulate that this is a derived pool. When we add a new sub-product attribute
        // to the subscription, it should show up as a primary product attribute on a
        // sub pool:

        // Update the subscription's sub-product:
        String newVal = "new value";
        s.getDerivedProduct().setAttribute("a", newVal);

        List<Pool> existingPools = Arrays.asList(p);
        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools);

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        Pool updatedPool = update.getPool();
        assertTrue(updatedPool.hasProductAttribute("a"));
        assertEquals(newVal, updatedPool.getProductAttribute("a").getValue());
        assertFalse(updatedPool.hasSubProductAttribute("a"));
    }

    @Test
    public void updatePoolSubProvidedProductsChanged() {
        // Subscription with two provided products:
        Subscription s = createSubscriptionWithSubProduct();
        Product product1 = TestUtil.createProduct();
        Product product2 = TestUtil.createProduct();
        Product product3 = TestUtil.createProduct();
        s.getDerivedProvidedProducts().add(product1);
        s.getDerivedProvidedProducts().add(product2);

        // Setup a pool with a single (different) provided product:
        Pool p = TestUtil.copyFromSub(s);
        p.getProvidedProducts().clear();
        p.getProvidedProducts().add(
            new ProvidedProduct(product3.getId(), product3.getName(), p));

        List<Pool> existingPools = Arrays.asList(p);

        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools);
        assertEquals(1, updates.size());
        assertTrue(updates.get(0).getDerivedProductAttributesChanged());
        assertEquals(2, updates.get(0).getPool().getDerivedProvidedProducts().size());
    }

    @Test
    public void productIdChangeOnProductPoolAttributeTriggersUpdate() {
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct());
        String testAttributeKey = "multi-entitlement";
        s.getProduct().setAttribute(testAttributeKey, "yes");

        Pool p = TestUtil.copyFromSub(s);
        p.setProductAttribute(testAttributeKey, "yes", s.getProduct().getId());

        // Change the sub's product's ID
        String expectedProductId = "NEW_TEST_ID";
        s.getProduct().setId(expectedProductId);

        when(productAdapterMock.getProductById(s.getProduct().getId()))
            .thenReturn(s.getProduct());

        List<Pool> existingPools = new LinkedList<Pool>();
        existingPools.add(p);
        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools);

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        Pool updatedPool = update.getPool();
        assertTrue(updatedPool.hasProductAttribute(testAttributeKey));

        ProductPoolAttribute provided =
            updatedPool.getProductAttribute(testAttributeKey);
        assertEquals("Wrong product id.", expectedProductId, provided.getProductId());
    }

    @Test
    public void productAttributesCopiedOntoPoolWhenCreatingNewPool() {
        Product product = TestUtil.createProduct();

        Subscription sub = TestUtil.createSubscription(owner, product);
        String testAttributeKey = "multi-entitlement";
        String expectedAttributeValue = "yes";
        sub.getProduct().setAttribute(testAttributeKey, expectedAttributeValue);

        when(this.productAdapterMock.getProductById(anyString())).thenReturn(product);

        List<Pool> pools = this.poolRules.createPools(sub);
        assertEquals(1, pools.size());

        Pool resultPool = pools.get(0);
        assertTrue(resultPool.hasProductAttribute(testAttributeKey));
        assertEquals(expectedAttributeValue,
            resultPool.getProductAttribute(testAttributeKey).getValue());
    }

    @Test
    public void subProductAttributesCopiedOntoPoolWhenCreatingNewPool() {
        Product product = TestUtil.createProduct();
        Product subProduct = TestUtil.createProduct();

        Subscription sub = TestUtil.createSubscription(owner, product);
        sub.setDerivedProduct(subProduct);
        String testAttributeKey = "multi-entitlement";
        String expectedAttributeValue = "yes";
        subProduct.setAttribute(testAttributeKey, expectedAttributeValue);

        when(this.productAdapterMock.getProductById(anyString())).thenReturn(product);
        when(this.productAdapterMock.getProductById(anyString())).thenReturn(subProduct);

        List<Pool> pools = this.poolRules.createPools(sub);
        assertEquals(1, pools.size());

        Pool resultPool = pools.get(0);
        assertTrue(resultPool.hasSubProductAttribute(testAttributeKey));
        assertEquals(expectedAttributeValue,
            resultPool.getDerivedProductAttribute(testAttributeKey).getValue());
    }

    @Test
    public void subProductIdCopiedOntoPoolWhenCreatingNewPool() {
        Product product = TestUtil.createProduct();
        Product subProduct = TestUtil.createProduct();

        Subscription sub = TestUtil.createSubscription(owner, product);
        sub.setDerivedProduct(subProduct);

        when(this.productAdapterMock.getProductById(anyString())).thenReturn(product);
        when(this.productAdapterMock.getProductById(anyString())).thenReturn(subProduct);

        List<Pool> pools = this.poolRules.createPools(sub);
        assertEquals(1, pools.size());

        Pool resultPool = pools.get(0);
        assertEquals(subProduct.getId(), resultPool.getDerivedProductId());
        assertEquals(subProduct.getName(), resultPool.getDerivedProductName());
    }

    @Test
    public void subProvidedProductsCopiedOntoPoolWhenCreatingNewPool() {
        Product product = TestUtil.createProduct();
        Product subProduct = TestUtil.createProduct();
        Product subProvidedProduct = TestUtil.createProduct();

        Subscription sub = TestUtil.createSubscription(owner, product);
        sub.setDerivedProduct(subProduct);
        Set<Product> subProvided = new HashSet<Product>();
        subProvided.add(subProvidedProduct);
        sub.setDerivedProvidedProducts(subProvided);

        when(this.productAdapterMock.getProductById(anyString())).thenReturn(product);
        when(this.productAdapterMock.getProductById(anyString())).thenReturn(subProduct);

        List<Pool> pools = this.poolRules.createPools(sub);
        assertEquals(1, pools.size());

        Pool resultPool = pools.get(0);
        assertEquals(1, resultPool.getDerivedProvidedProducts().size());
    }

    private Subscription createVirtLimitSub(String productId, int quantity, int virtLimit) {
        Product product = new Product(productId, productId);
        product.setAttribute("virt_limit", Integer.toString(virtLimit));
        when(productAdapterMock.getProductById(productId)).thenReturn(product);
        Subscription s = TestUtil.createSubscription(product);
        s.setQuantity(new Long(quantity));
        return s;
    }

    /*
     * Bonus pools should not be created at pool creation time if the
     * host_limited attribute is present on the product.  Instead the bonus
     * pools will be created during binding.
     */
    @Test
    public void hostedVirtLimitWithHostLimitedSkipsBonusPools() {
        when(configMock.standalone()).thenReturn(false);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, 10);
        s.getProduct().setAttribute("host_limited", "true");
        List<Pool> pools = poolRules.createPools(s);
        assertEquals(1, pools.size());
    }

    // Make sure host_limited false is working:
    @Test
    public void hostedVirtLimitWithHostLimitedFalseCreatesBonusPools() {
        when(configMock.standalone()).thenReturn(false);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, 10);
        s.getProduct().setAttribute("host_limited", "false");
        List<Pool> pools = poolRules.createPools(s);
        assertEquals(2, pools.size());
    }

    @Test
    public void hostedVirtLimitSubCreatesBonusVirtOnlyPool() {
        when(configMock.standalone()).thenReturn(false);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, 10);
        List<Pool> pools = poolRules.createPools(s);
        assertEquals(2, pools.size());

        Pool physicalPool = pools.get(0);
        Pool virtBonusPool = pools.get(1);

        assertEquals(new Long(10), physicalPool.getQuantity());
        assertEquals(0, physicalPool.getAttributes().size());

        // Quantity on bonus pool should be virt limit * sub quantity:
        assertEquals(new Long(100), virtBonusPool.getQuantity());
        assertEquals("true", virtBonusPool.getAttributeValue("virt_only"));
        assertEquals("10", virtBonusPool.getProductAttribute("virt_limit").getValue());
    }

    @Test
    public void hostedVirtLimitSubCreatesUnlimitedBonusVirtOnlyPool() {
        when(configMock.standalone()).thenReturn(false);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, 10);
        s.getProduct().setAttribute("virt_limit", "unlimited");
        List<Pool> pools = poolRules.createPools(s);
        assertEquals(2, pools.size());

        Pool virtBonusPool = pools.get(1);

        // Quantity on bonus pool should be unlimited:
        assertEquals(new Long(-1), virtBonusPool.getQuantity());
    }

    @Test
    public void hostedVirtLimitSubUpdatesUnlimitedBonusVirtOnlyPool() {
        when(configMock.standalone()).thenReturn(false);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, 10);
        s.getProduct().setAttribute("virt_limit", "unlimited");
        List<Pool> pools = poolRules.createPools(s);
        assertEquals(2, pools.size());

        Pool virtBonusPool = pools.get(1);

        // Quantity on bonus pool should be unlimited:
        assertEquals(new Long(-1), virtBonusPool.getQuantity());

        // Now we update the sub and see if that unlimited pool gets adjusted:
        s.getProduct().setAttribute("virt_limit", "10");
        List<PoolUpdate> updates = poolRules.updatePools(s, pools);
        assertEquals(2, updates.size());

        PoolUpdate virtUpdate = updates.get(1);
        assertEquals(new Long(100), virtUpdate.getPool().getQuantity());
    }

    @Test
    public void hostedVirtLimitRemoved() {
        when(configMock.standalone()).thenReturn(false);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, 10);
        s.getProduct().setAttribute("virt_limit", "4");
        List<Pool> pools = poolRules.createPools(s);
        assertEquals(2, pools.size());

        // Now we update the sub and see if that unlimited pool gets adjusted:
        s.getProduct().getAttributes().clear();
        List<PoolUpdate> updates = poolRules.updatePools(s, pools);
        assertEquals(2, updates.size());

        // Regular pool should be in a sane state:
        PoolUpdate baseUpdate = updates.get(0);
        assertEquals(new Long(10), baseUpdate.getPool().getQuantity());
        assertFalse(baseUpdate.getPool().hasAttribute(PoolManager.DELETE_FLAG));

        // Virt bonus pool should have quantity 0 and be flagged for cleanup:
        PoolUpdate virtUpdate = updates.get(1);
        assertEquals(new Long(0), virtUpdate.getPool().getQuantity());
        assertEquals("true", virtUpdate.getPool().getAttributeValue(
            PoolManager.DELETE_FLAG));
    }

    @Test
    public void hostedVirtLimitSubWithMultiplierCreatesUnlimitedBonusVirtOnlyPool() {
        when(configMock.standalone()).thenReturn(false);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, 10);
        s.getProduct().setAttribute("virt_limit", "unlimited");
        s.getProduct().setMultiplier(5L);
        List<Pool> pools = poolRules.createPools(s);
        assertEquals(2, pools.size());

        Pool virtBonusPool = pools.get(1);

        // Quantity on bonus pool should be unlimited:
        assertEquals(new Long(-1), virtBonusPool.getQuantity());
    }

    @Test
    public void hostedVirtLimitSubCreateAttributesTest() {
        when(configMock.standalone()).thenReturn(false);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, 10);
        s.getProduct().setAttribute("physical_only", "true");
        List<Pool> pools = poolRules.createPools(s);

        // Should be no virt_only bonus pool:
        assertEquals(2, pools.size());

        int virtOnlyCount = 0;
        for (Pool pool : pools) {
            if (pool.hasAttribute("virt_only") &&
                    pool.attributeEquals("virt_only", "true")) {
                virtOnlyCount++;
                assertEquals("false", pool.getAttributeValue("physical_only"));
            }
        }
        assertEquals(1, virtOnlyCount);
    }

    @Test
    public void standaloneVirtLimitSubCreate() {
        when(configMock.standalone()).thenReturn(true);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, 10);
        List<Pool> pools = poolRules.createPools(s);

        // Should be no virt_only bonus pool:
        assertEquals(1, pools.size());

        Pool physicalPool = pools.get(0);
        assertEquals(0, physicalPool.getAttributes().size());
    }

    @Test
    public void standaloneVirtLimitSubUpdate() {
        when(configMock.standalone()).thenReturn(true);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, 10);
        List<Pool> pools = poolRules.createPools(s);

        // Should be no virt_only bonus pool:
        assertEquals(1, pools.size());

        Pool physicalPool = pools.get(0);
        assertEquals(0, physicalPool.getAttributes().size());

        s.setQuantity(50L);
        List<PoolUpdate> updates = poolRules.updatePools(s, pools);
        assertEquals(1, updates.size());
        physicalPool = updates.get(0).getPool();
        assertEquals(new Long(50), physicalPool.getQuantity());
        assertEquals(0, physicalPool.getAttributes().size());
    }

    private Subscription createVirtOnlySub(String productId, int quantity) {
        Product product = new Product(productId, productId);
        product.setAttribute("virt_only", "true");
        when(productAdapterMock.getProductById(productId)).thenReturn(product);
        Subscription s = TestUtil.createSubscription(product);
        s.setQuantity(new Long(quantity));
        return s;
    }

    @Test
    public void hostedVirtOnlySubCreate() {
        when(configMock.standalone()).thenReturn(true);
        Subscription s = createVirtOnlySub("virtOnlyProduct", 10);
        List<Pool> pools = poolRules.createPools(s);
        assertEquals(1, pools.size());
        assertEquals("true", pools.get(0).getProductAttribute("virt_only").getValue());
        assertEquals(new Long(10), pools.get(0).getQuantity());
    }

    @Test
    public void hostedVirtOnlySubCreateWithMultiplier() {
        when(configMock.standalone()).thenReturn(true);
        Subscription s = createVirtOnlySub("virtOnlyProduct", 10);
        s.getProduct().setMultiplier(new Long(5));
        List<Pool> pools = poolRules.createPools(s);
        assertEquals(1, pools.size());
        assertEquals("true", pools.get(0).getProductAttribute("virt_only").getValue());
        assertEquals(new Long(50), pools.get(0).getQuantity());
    }

    @Test
    public void hostedVirtOnlySubUpdate() {
        when(configMock.standalone()).thenReturn(true);
        Subscription s = createVirtOnlySub("virtOnlyProduct", 10);
        List<Pool> pools = poolRules.createPools(s);
        assertEquals(1, pools.size());
        s.setQuantity(new Long(20));

        List<PoolUpdate> updates = poolRules.updatePools(s, pools);
        assertEquals(1, updates.size());
        Pool updated = updates.get(0).getPool();
        assertEquals(new Long(20), updated.getQuantity());
    }

    @Test
    public void standaloneVirtSubPoolUpdateNoChanges() {
        when(configMock.standalone()).thenReturn(true);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, 10);
        List<Pool> pools = poolRules.createPools(s);
        assertEquals(1, pools.size());
        Entitlement ent = mock(Entitlement.class);
        when(ent.getQuantity()).thenReturn(1);

        // Now make a pool that would have been created for guests only after a host
        // bound to the parent pool:
        Pool consumerSpecificPool = TestUtil.copyFromSub(s);
        consumerSpecificPool.setAttribute("requires_host", "FAKEUUID");
        consumerSpecificPool.setAttribute("pool_derived", "true");
        consumerSpecificPool.setAttribute("virt_only", "true");
        consumerSpecificPool.setQuantity(10L);
        consumerSpecificPool.setSourceEntitlement(ent);
        pools.add(consumerSpecificPool);

        List<PoolUpdate> updates = poolRules.updatePools(s, pools);
        assertEquals(0, updates.size());
    }

    @Test
    public void standaloneVirtSubPoolUpdateVirtLimitChanged() {
        when(configMock.standalone()).thenReturn(true);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, 10);
        List<Pool> pools = poolRules.createPools(s);
        assertEquals(1, pools.size());
        s.setQuantity(new Long(20));
        Entitlement ent = mock(Entitlement.class);
        when(ent.getQuantity()).thenReturn(4);

        // Now make a pool that would have been created for guests only after a host
        // bound to the parent pool:
        Pool consumerSpecificPool = TestUtil.copyFromSub(s);
        consumerSpecificPool.setAttribute("requires_host", "FAKEUUID");
        consumerSpecificPool.setAttribute("pool_derived", "true");
        consumerSpecificPool.setAttribute("virt_only", "true");
        consumerSpecificPool.setQuantity(10L);
        consumerSpecificPool.setSourceEntitlement(ent);
        pools.add(consumerSpecificPool);

        s.getProduct().setAttribute("virt_limit", "40");
        List<PoolUpdate> updates = poolRules.updatePools(s, pools);
        assertEquals(2, updates.size());
        Pool regular = updates.get(0).getPool();
        Pool subPool = updates.get(1).getPool();
        assertEquals("40", regular.getProductAttribute("virt_limit").getValue());
        assertEquals(new Long(40), subPool.getQuantity());
    }

    @Test
    public void dontUpdateVirtOnlyNoVirtLimit() {
        when(configMock.standalone()).thenReturn(false);
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct());
        s.setQuantity(10L);
        when(productAdapterMock.getProductById(s.getProduct().getId()))
            .thenReturn(s.getProduct());

        // Setup a pool with a single (different) provided product:
        Pool p = TestUtil.copyFromSub(s);
        p.addAttribute(new PoolAttribute("virt_only", "true"));
        p.addAttribute(new PoolAttribute("pool_derived", "true"));
        p.setQuantity(10L);

        List<Pool> existingPools = new LinkedList<Pool>();
        existingPools.add(p);
        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools);

        assertEquals(0, updates.size());
    }

    @Test
    public void updateVirtOnlyNoVirtLimit() {
        when(configMock.standalone()).thenReturn(false);
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct());
        s.setQuantity(10L);

        // Setup a pool with a single (different) provided product:
        Pool p = TestUtil.copyFromSub(s);
        p.addAttribute(new PoolAttribute("virt_only", "true"));
        p.addAttribute(new PoolAttribute("pool_derived", "true"));
        p.setQuantity(20L);

        List<Pool> existingPools = new LinkedList<Pool>();
        existingPools.add(p);
        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools);

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertFalse(update.getProductsChanged());
        assertFalse(update.getDatesChanged());
        assertTrue(update.getQuantityChanged());
        assertEquals(Long.valueOf(10), update.getPool().getQuantity());
    }

    @Test
    public void contractNumberChanged() {
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct());
        s.setContractNumber("123");

        // Setup a pool with a single (different) provided product:
        Pool p = TestUtil.copyFromSub(s);
        p.setQuantity(2000L);
        p.setContractNumber("ABC");

        List<Pool> existingPools = new LinkedList<Pool>();
        existingPools.add(p);
        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools);

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertTrue(update.getOrderChanged());
        assertEquals("123", update.getPool().getContractNumber());
    }

    @Test
    public void orderNumberChanged() {
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct());
        s.setOrderNumber("123");

        // Setup a pool with a single (different) provided product:
        Pool p = TestUtil.copyFromSub(s);
        p.setQuantity(2000L);
        p.setOrderNumber("ABC");

        List<Pool> existingPools = new LinkedList<Pool>();
        existingPools.add(p);
        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools);

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertTrue(update.getOrderChanged());
        assertEquals("123", update.getPool().getOrderNumber());
    }

    @Test
    public void accountNumberChanged() {
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct());
        s.setAccountNumber("123");

        // Setup a pool with a single (different) provided product:
        Pool p = TestUtil.copyFromSub(s);
        p.setQuantity(2000L);
        p.setAccountNumber("ABC");

        List<Pool> existingPools = new LinkedList<Pool>();
        existingPools.add(p);
        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools);

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertTrue(update.getOrderChanged());
        assertEquals("123", update.getPool().getAccountNumber());
    }

}
