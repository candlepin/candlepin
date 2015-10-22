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

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.candlepin.auth.UserPrincipal;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.Branding;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolAttribute;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.model.dto.Subscription;
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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;


/**
 * JsPoolRulesTest: Tests for the default rules.
 */
@RunWith(MockitoJUnitRunner.class)
public class PoolRulesTest {

    private PoolRules poolRules;
    private static final String DERIVED_ATTR = "lookformeimderived";

    @Mock private RulesCurator rulesCuratorMock;
    @Mock private ProductServiceAdapter productAdapterMock;
    @Mock private PoolManager poolManagerMock;
    @Mock private Configuration configMock;
    @Mock private EntitlementCurator entCurMock;
    @Mock private ProductCurator prodCuratorMock;

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

        poolRules = new PoolRules(poolManagerMock, configMock, entCurMock, prodCuratorMock);
        principal = TestUtil.createOwnerPrincipal();
        owner = principal.getOwners().get(0);
    }

    @Test
    public void hostedVirtLimitBadValueDoesntTraceBack() {
        when(configMock.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        Product product = TestUtil.createProduct(owner);

        when(this.prodCuratorMock.lookupById(product.getOwner(), product.getId())).thenReturn(product);
        Subscription s = TestUtil.createSubscription(owner, product);
        s.getProduct().addAttribute(new ProductAttribute("virt_limit", "badvalue"));
        s.setQuantity(10L);

        List<Pool> pools = null;
        try {
            pools = poolRules.createAndEnrichPools(s);
        }
        catch (Exception e) {
            fail(
                "Create pools should not have thrown an exception on bad value for virt_limit: " +
                e.getMessage()
            );
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
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct(owner));
        Product product1 = TestUtil.createProduct(owner);
        Product product2 = TestUtil.createProduct(owner);
        Product product3 = TestUtil.createProduct(owner);
        s.getProvidedProducts().add(product1);
        s.getProvidedProducts().add(product2);

        // Setup a pool with a single (different) provided product:
        Pool p = TestUtil.copyFromSub(s);
        p.getProvidedProducts().clear();
        p.getProvidedProducts().add(product3);

        List<Pool> existingPools = new LinkedList<Pool>();
        existingPools.add(p);
        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools,
                new HashSet<Product>());
        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertTrue(update.getProductsChanged());
        assertFalse(update.getDatesChanged());
        assertFalse(update.getQuantityChanged());
    }

    @Test
    public void productNameChanged() {
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct(owner));

        // Setup a pool with a single (different) provided product:
        Pool p = TestUtil.copyFromSub(s);
        p.getProduct().setName("somethingelse");

        List<Pool> existingPools = Arrays.asList(p);
        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools,
                TestUtil.stubChangedProducts(s.getProduct()));

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertTrue(update.getProductsChanged());
        assertFalse(update.getDatesChanged());
        assertFalse(update.getQuantityChanged());
        assertEquals(s.getProduct().getName(), update.getPool().getProductName());
    }

    @Test
    public void datesNameChanged() {
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct(owner));

        // Setup a pool with a single (different) provided product:
        Pool p = TestUtil.copyFromSub(s);
        p.setEndDate(new Date());

        List<Pool> existingPools = Arrays.asList(p);
        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools,
                new HashSet<Product>());

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertFalse(update.getProductsChanged());
        assertTrue(update.getDatesChanged());
        assertFalse(update.getQuantityChanged());
        assertEquals(s.getEndDate(), update.getPool().getEndDate());
    }

    @Test
    public void quantityChanged() {
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct(owner));

        // Setup a pool with a single (different) provided product:
        Pool p = TestUtil.copyFromSub(s);
        p.setQuantity(2000L);

        List<Pool> existingPools = Arrays.asList(p);
        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools,
                new HashSet<Product>());

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertFalse(update.getProductsChanged());
        assertFalse(update.getDatesChanged());
        assertTrue(update.getQuantityChanged());
        assertEquals(s.getQuantity(), update.getPool().getQuantity());
    }

    @Test
    public void brandingChanged() {
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct(owner));

        Pool p = TestUtil.copyFromSub(s);

        // Add some branding to the subscription and do an update:
        Branding b1 = new Branding("8000", "OS", "Awesome OS Branded");
        Branding b2 = new Branding("8001", "OS", "Awesome OS Branded 2");
        s.getBranding().add(b1);
        s.getBranding().add(b2);

        List<Pool> existingPools = Arrays.asList(p);
        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools,
                new HashSet<Product>());

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);

        assertFalse(update.getProductsChanged());
        assertFalse(update.getDatesChanged());
        assertFalse(update.getQuantityChanged());

        assertTrue(update.getBrandingChanged());
        assertTrue(update.changed());

        assertEquals(2, update.getPool().getBranding().size());
        assertTrue(update.getPool().getBranding().contains(b1));
        assertTrue(update.getPool().getBranding().contains(b2));
    }

    @Test
    public void brandingDidntChange() {
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct(owner));

        // Add some branding to the subscription and do an update:
        Branding b1 = new Branding("8000", "OS", "Awesome OS Branded");
        Branding b2 = new Branding("8001", "OS", "Awesome OS Branded 2");
        s.getBranding().add(b1);
        s.getBranding().add(b2);

        when(productAdapterMock.getProductById(s.getProduct().getOwner(), s.getProduct().getId()))
            .thenReturn(s.getProduct());

        // Copy the pool with the branding to begin with:
        Pool p = TestUtil.copyFromSub(s);

        List<Pool> existingPools = Arrays.asList(p);
        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools,
                new HashSet<Product>());

        assertEquals(0, updates.size());
    }

    @Test
    public void virtOnlyQuantityChanged() {
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct(owner));
        s.getProduct().addAttribute(new ProductAttribute("virt_limit", "5"));
        s.setQuantity(10L);

        when(productAdapterMock.getProductById(s.getProduct().getOwner(), s.getProduct().getId()))
            .thenReturn(s.getProduct());

        // Setup a pool with a single (different) provided product:
        Pool p = TestUtil.copyFromSub(s);
        p.addAttribute(new PoolAttribute("virt_only", "true"));
        p.addAttribute(new PoolAttribute("pool_derived", "true"));
        p.setQuantity(40L);

        List<Pool> existingPools = Arrays.asList(p);
        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools,
                null);

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertFalse(update.getProductsChanged());
        assertFalse(update.getDatesChanged());
        assertFalse(update.getProductAttributesChanged());
        assertTrue(update.getQuantityChanged());
        assertEquals(Long.valueOf(50), update.getPool().getQuantity());
    }

    private Subscription createSubscriptionWithSubProduct() {
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct(owner));
        Product subProd = TestUtil.createProduct(owner);
        s.setDerivedProduct(subProd);
        when(productAdapterMock.getProductById(s.getProduct().getOwner(), s.getProduct().getId()))
            .thenReturn(s.getProduct());
        when(productAdapterMock.getProductById(
            s.getDerivedProduct().getOwner(), s.getDerivedProduct().getId())
        ).thenReturn(s.getDerivedProduct());
        return s;
    }

    @Test
    public void updatePoolSubProvidedProductsChanged() {
        // Subscription with two provided products:
        Subscription s = createSubscriptionWithSubProduct();
        Product product1 = TestUtil.createProduct(owner);
        Product product2 = TestUtil.createProduct(owner);
        Product product3 = TestUtil.createProduct(owner);
        s.getDerivedProvidedProducts().add(product1);
        s.getDerivedProvidedProducts().add(product2);

        // Setup a pool with a single (different) provided product:
        Pool p = TestUtil.copyFromSub(s);
        p.getProvidedProducts().clear();
        p.getProvidedProducts().add(product3);

        List<Pool> existingPools = Arrays.asList(p);

        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools,
                new HashSet<Product>());
        assertEquals(1, updates.size());
        assertEquals(2, updates.get(0).getPool().getDerivedProvidedProducts().size());
    }

    @Test
    public void productAttributesCopiedOntoPoolWhenCreatingNewPool() {
        Product product = TestUtil.createProduct(owner);

        Subscription sub = TestUtil.createSubscription(owner, product);
        String testAttributeKey = "multi-entitlement";
        String expectedAttributeValue = "yes";
        sub.getProduct().setAttribute(testAttributeKey, expectedAttributeValue);

        when(prodCuratorMock.lookupById(product.getOwner(), product.getId()))
            .thenReturn(product);

        List<Pool> pools = this.poolRules.createAndEnrichPools(sub);
        assertEquals(1, pools.size());

        Pool resultPool = pools.get(0);
        assertNotNull(resultPool.getProduct());
        assertTrue(resultPool.getProduct().hasAttribute(testAttributeKey));
        assertEquals(
            expectedAttributeValue,
            resultPool.getProduct().getAttributeValue(testAttributeKey)
        );
    }

    @Test
    public void brandingCopiedWhenCreatingPools() {
        Product product = TestUtil.createProduct(owner);

        Subscription sub = TestUtil.createSubscription(owner, product);
        Branding b1 = new Branding("8000", "OS", "Branded Awesome OS");
        Branding b2 = new Branding("8001", "OS", "Branded Awesome OS 2");
        sub.getBranding().add(b1);
        sub.getBranding().add(b2);

        when(this.prodCuratorMock.lookupById(product.getOwner(), product.getId()))
            .thenReturn(product);

        List<Pool> pools = this.poolRules.createAndEnrichPools(sub);
        assertEquals(1, pools.size());

        Pool resultPool = pools.get(0);
        assertEquals(2, resultPool.getBranding().size());
        assertTrue(resultPool.getBranding().contains(b1));
        assertTrue(resultPool.getBranding().contains(b2));
    }

    @Test
    public void subProductAttributesCopiedOntoPoolWhenCreatingNewPool() {
        Product product = TestUtil.createProduct(owner);
        Product subProduct = TestUtil.createProduct(owner);

        Subscription sub = TestUtil.createSubscription(owner, product);
        sub.setDerivedProduct(subProduct);
        String testAttributeKey = "multi-entitlement";
        String expectedAttributeValue = "yes";
        subProduct.setAttribute(testAttributeKey, expectedAttributeValue);

        when(this.prodCuratorMock.lookupById(product.getOwner(), product.getId()))
            .thenReturn(product);
        when(this.prodCuratorMock.lookupById(subProduct.getOwner(), subProduct.getId()))
            .thenReturn(subProduct);

        List<Pool> pools = this.poolRules.createAndEnrichPools(sub);
        assertEquals(1, pools.size());

        Pool resultPool = pools.get(0);
        assertNotNull(resultPool.getDerivedProduct());
        assertTrue(resultPool.getDerivedProduct().hasAttribute(testAttributeKey));
        assertEquals(
            expectedAttributeValue,
            resultPool.getDerivedProduct().getAttributeValue(testAttributeKey)
        );
    }

    @Test
    public void subProductIdCopiedOntoPoolWhenCreatingNewPool() {
        Product product = TestUtil.createProduct(owner);
        Product subProduct = TestUtil.createProduct(owner);

        Subscription sub = TestUtil.createSubscription(owner, product);
        sub.setDerivedProduct(subProduct);

        when(this.prodCuratorMock.lookupById(product.getOwner(), product.getId()))
            .thenReturn(product);
        when(this.prodCuratorMock.lookupById(subProduct.getOwner(), subProduct.getId()))
            .thenReturn(subProduct);

        List<Pool> pools = this.poolRules.createAndEnrichPools(sub);
        assertEquals(1, pools.size());

        Pool resultPool = pools.get(0);
        assertEquals(subProduct, resultPool.getDerivedProduct());
    }

    @Test
    public void derivedProvidedProductsCopiedOntoMasterPoolWhenCreatingNewPool() {
        Product product = TestUtil.createProduct(owner);
        Product subProduct = TestUtil.createProduct(owner);
        Product subProvidedProduct = TestUtil.createProduct(owner);

        Subscription sub = TestUtil.createSubscription(owner, product);
        sub.setDerivedProduct(subProduct);
        Set<Product> subProvided = new HashSet<Product>();
        subProvided.add(subProvidedProduct);
        sub.setDerivedProvidedProducts(subProvided);

        when(this.prodCuratorMock.lookupById(product.getOwner(), product.getId()))
            .thenReturn(product);
        when(this.prodCuratorMock.lookupById(subProduct.getOwner(), subProduct.getId()))
            .thenReturn(subProduct);

        List<Pool> pools = this.poolRules.createAndEnrichPools(sub);
        assertEquals(1, pools.size());

        Pool resultPool = pools.get(0);
        assertEquals(1, resultPool.getDerivedProvidedProducts().size());
    }

    private Subscription createVirtLimitSub(String productId, int quantity, int virtLimit) {
        Product product = new Product(productId, productId, owner);
        product.setAttribute("virt_limit", Integer.toString(virtLimit));
        when(prodCuratorMock.lookupById(product.getOwner(), product.getId()))
            .thenReturn(product);
        Subscription s = TestUtil.createSubscription(owner, product);
        s.setQuantity(new Long(quantity));
        return s;
    }

    /*
     * Bonus pools should be created at pool creation time if the
     * host_limited attribute is present on the product.  A tag will
     * be added to the created pool. Host specific bonus pools will
     * still be created during binding.
     */
    @Test
    public void virtLimitWithHostLimitedCreatesTaggedBonusPool() {
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, 10);
        s.getProduct().setAttribute("host_limited", "true");
        List<Pool> pools = poolRules.createAndEnrichPools(s);
        assertEquals(2, pools.size());
        for (Pool p : pools) {
            if (p.getSourceSubscription().getSubscriptionSubKey().equals("derived")) {
                assertTrue(p.hasAttribute("unmapped_guests_only"));
                assertEquals("true", p.getAttributeValue("unmapped_guests_only"));
            }
        }
    }

    // Make sure host_limited false is working:
    @Test
    public void hostedVirtLimitWithHostLimitedFalseCreatesBonusPools() {
        when(configMock.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, 10);
        s.getProduct().setAttribute("host_limited", "false");
        List<Pool> pools = poolRules.createAndEnrichPools(s);
        assertEquals(2, pools.size());
    }

    @Test
    public void hostedVirtLimitSubCreatesBonusVirtOnlyPool() {
        when(configMock.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, 10);
        List<Pool> pools = poolRules.createAndEnrichPools(s);
        assertEquals(2, pools.size());

        Pool physicalPool = pools.get(0);
        Pool virtBonusPool = pools.get(1);

        assertEquals(new Long(10), physicalPool.getQuantity());
        assertEquals(0, physicalPool.getAttributes().size());

        // Quantity on bonus pool should be virt limit * sub quantity:
        assertEquals(new Long(100), virtBonusPool.getQuantity());
        assertEquals("true", virtBonusPool.getAttributeValue("virt_only"));
        assertEquals("10", virtBonusPool.getProduct().getAttributeValue("virt_limit"));
    }

    @Test
    public void hostedVirtLimitSubCreatesUnlimitedBonusVirtOnlyPool() {
        when(configMock.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, 10);
        s.getProduct().setAttribute("virt_limit", "unlimited");
        List<Pool> pools = poolRules.createAndEnrichPools(s);
        assertEquals(2, pools.size());

        Pool virtBonusPool = pools.get(1);

        // Quantity on bonus pool should be unlimited:
        assertEquals(new Long(-1), virtBonusPool.getQuantity());
    }

    @Test
    public void hostedVirtLimitSubUpdatesUnlimitedBonusVirtOnlyPool() {
        when(configMock.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, 10);
        s.getProduct().setAttribute("virt_limit", "unlimited");
        List<Pool> pools = poolRules.createAndEnrichPools(s);
        assertEquals(2, pools.size());

        Pool virtBonusPool = pools.get(1);

        // Quantity on bonus pool should be unlimited:
        assertEquals(new Long(-1), virtBonusPool.getQuantity());

        // Now we update the sub and see if that unlimited pool gets adjusted:
        s.getProduct().setAttribute("virt_limit", "10");
        List<PoolUpdate> updates = poolRules.updatePools(s, pools,
                TestUtil.stubChangedProducts(s.getProduct()));
        assertEquals(2, updates.size());

        PoolUpdate virtUpdate = updates.get(1);
        assertEquals(new Long(100), virtUpdate.getPool().getQuantity());
    }

    @Test
    public void hostedVirtLimitRemoved() {
        when(configMock.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, 10);
        s.getProduct().setAttribute("virt_limit", "4");
        List<Pool> pools = poolRules.createAndEnrichPools(s);
        assertEquals(2, pools.size());

        // Now we remove virt_limit on the incoming subscription product and see if
        // the unlimited pool gets adjusted and flagged for cleanup:
        s.setProduct(TestUtil.createProduct(s.getProduct().getId(),
                s.getProduct().getName(), owner));
        List<PoolUpdate> updates = poolRules.updatePools(s, pools,
                TestUtil.stubChangedProducts(s.getProduct()));
        assertEquals(2, updates.size());

        // Regular pool should be in a sane state:
        PoolUpdate baseUpdate = updates.get(0);
        assertEquals(new Long(10), baseUpdate.getPool().getQuantity());
        assertFalse(baseUpdate.getPool().isMarkedForDelete());

        // Virt bonus pool should have quantity 0 and be flagged for cleanup:
        PoolUpdate virtUpdate = updates.get(1);
        assertEquals(new Long(0), virtUpdate.getPool().getQuantity());
        assertTrue(virtUpdate.getPool().isMarkedForDelete());
    }

    @Test
    public void hostedVirtLimitSubWithMultiplierCreatesUnlimitedBonusVirtOnlyPool() {
        when(configMock.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, 10);
        s.getProduct().setAttribute("virt_limit", "unlimited");
        s.getProduct().setMultiplier(5L);
        List<Pool> pools = poolRules.createAndEnrichPools(s);
        assertEquals(2, pools.size());

        Pool virtBonusPool = pools.get(1);

        // Quantity on bonus pool should be unlimited:
        assertEquals(new Long(-1), virtBonusPool.getQuantity());
    }

    @Test
    public void hostedVirtLimitSubCreateAttributesTest() {
        when(configMock.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, 10);
        s.getProduct().setAttribute("physical_only", "true");
        List<Pool> pools = poolRules.createAndEnrichPools(s);

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
        when(configMock.getBoolean(ConfigProperties.STANDALONE)).thenReturn(true);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, 10);

        Product provided1 = TestUtil.createProduct(owner);
        Product provided2 = TestUtil.createProduct(owner);
        Product derivedProd = TestUtil.createProduct(owner);
        Product derivedProvidedProd1 = TestUtil.createProduct(owner);
        Product derivedProvidedProd2 = TestUtil.createProduct(owner);

        when(prodCuratorMock.lookupById(owner, provided1.getId())).thenReturn(provided1);
        when(prodCuratorMock.lookupById(owner, provided2.getId())).thenReturn(provided2);
        when(prodCuratorMock.lookupById(owner, derivedProd.getId())).thenReturn(derivedProd);
        when(prodCuratorMock.lookupById(owner, derivedProvidedProd1.getId()))
            .thenReturn(derivedProvidedProd1);
        when(prodCuratorMock.lookupById(owner, derivedProvidedProd2.getId()))
            .thenReturn(derivedProvidedProd2);

        s.getProvidedProducts().add(provided1);
        s.getProvidedProducts().add(provided2);
        s.setDerivedProduct(derivedProd);
        when(productAdapterMock.getProductById(owner, derivedProd.getId())).thenReturn(derivedProd);
        s.getDerivedProvidedProducts().add(derivedProvidedProd1);
        s.getDerivedProvidedProducts().add(derivedProvidedProd2);
        List<Pool> pools = poolRules.createAndEnrichPools(s);

        // Should be virt_only pool for unmapped guests:
        assertEquals(2, pools.size());

        Pool physicalPool = pools.get(0);
        assertEquals(0, physicalPool.getAttributes().size());
        assertProvidedProducts(s.getProvidedProducts(), physicalPool.getProvidedProducts());
        assertProvidedProducts(s.getDerivedProvidedProducts(),
                physicalPool.getDerivedProvidedProducts());

        Pool unmappedVirtPool = pools.get(1);
        assert ("true".equals(unmappedVirtPool.getAttributeValue("virt_only")));
        assert ("true".equals(unmappedVirtPool.getAttributeValue("unmapped_guests_only")));

        // The derived provided products of the sub should be promoted to provided products
        // on the unmappedVirtPool
        assertProvidedProducts(s.getDerivedProvidedProducts(),
                unmappedVirtPool.getProvidedProducts());
        assertProvidedProducts(new HashSet<Product>(),
                unmappedVirtPool.getDerivedProvidedProducts());

        // Test for BZ 1204311 - Refreshing pools should not change unmapped guest pools
        // Refresh is a no-op in multiorg
        // List<PoolUpdate> updates = poolRules.updatePools(s, pools);
        // assertTrue(updates.isEmpty());
    }

    @Test
    public void standaloneVirtLimitSubCreateDerived() {
        when(configMock.getBoolean(ConfigProperties.STANDALONE)).thenReturn(true);
        Subscription s = createVirtLimitSubWithDerivedProducts("virtLimitProduct",
                "derivedProd", 10, 10);
        List<Pool> pools = poolRules.createAndEnrichPools(s);

        // Should be virt_only pool for unmapped guests:
        assertEquals(2, pools.size());

        Pool physicalPool = pools.get(0);
        assertEquals(0, physicalPool.getAttributes().size());
        assertFalse(physicalPool.getProduct().hasAttribute(DERIVED_ATTR));

        Pool unmappedVirtPool = pools.get(1);
        assert ("true".equals(unmappedVirtPool.getAttributeValue("virt_only")));
        assert ("true".equals(unmappedVirtPool.getAttributeValue("unmapped_guests_only")));
        assertEquals("derivedProd", unmappedVirtPool.getProductId());

        assertProvidedProducts(s.getDerivedProvidedProducts(),
                unmappedVirtPool.getProvidedProducts());
        assertProvidedProducts(new HashSet<Product>(),
                unmappedVirtPool.getDerivedProvidedProducts());
        assertTrue(unmappedVirtPool.getProduct().hasAttribute(DERIVED_ATTR));
    }

    private void assertProvidedProducts(Set<Product> expectedProducts,
            Set<Product> providedProducts) {
        assertEquals(expectedProducts.size(), providedProducts.size());
        for (Product expected : expectedProducts) {
            boolean found = false;
            for (Product provided : providedProducts) {
                if (provided.getId().equals(expected.getId())) {
                    found = true;
                    break;
                }
            }
            assertTrue(found);
        }

    }

    private Subscription createVirtLimitSubWithDerivedProducts(String productId,
            String derivedProductId, int quantity, int virtLimit) {

        Product product = new Product(productId, productId, owner);
        product.setAttribute("virt_limit", Integer.toString(virtLimit));
        when(prodCuratorMock.lookupById(product.getOwner(), product.getId()))
            .thenReturn(product);

        Product derivedProd = new Product(derivedProductId, derivedProductId, owner);
        // We'll look for this to make sure it makes it to correct pools:
        derivedProd.setAttribute(DERIVED_ATTR, "nobodycares");
        when(prodCuratorMock.lookupById(derivedProd.getOwner(), derivedProd.getId()))
            .thenReturn(derivedProd);

        // Create some provided products:
        Product provided1 = TestUtil.createProduct(owner);
        when(prodCuratorMock.lookupById(provided1.getOwner(), provided1.getId()))
            .thenReturn(provided1);
        Product provided2 = TestUtil.createProduct(owner);
        when(prodCuratorMock.lookupById(provided2.getOwner(), provided2.getId()))
            .thenReturn(provided2);

        // Create some derived provided products:
        Product derivedProvided1 = TestUtil.createProduct(owner);
        when(prodCuratorMock.lookupById(derivedProvided1.getOwner(), derivedProvided1.getId()))
            .thenReturn(derivedProvided1);
        Product derivedProvided2 = TestUtil.createProduct(owner);
        when(prodCuratorMock.lookupById(derivedProvided2.getOwner(), derivedProvided2.getId()))
            .thenReturn(derivedProvided2);


        Subscription s = TestUtil.createSubscription(owner, product);
        s.setQuantity(new Long(quantity));
        s.setDerivedProduct(derivedProd);

        Set<Product> derivedProds = Util.newSet();
        derivedProds.add(derivedProvided1);
        derivedProds.add(derivedProvided2);
        s.setDerivedProvidedProducts(derivedProds);

        return s;
    }


    @Test
    public void standaloneVirtLimitSubUpdate() {
        when(configMock.getBoolean(ConfigProperties.STANDALONE)).thenReturn(true);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, 10);
        List<Pool> pools = poolRules.createAndEnrichPools(s);

        // Should be unmapped virt_only pool:
        assertEquals(2, pools.size());

        Pool physicalPool = pools.get(0);
        assertEquals(0, physicalPool.getAttributes().size());

        s.setQuantity(50L);
        List<PoolUpdate> updates = poolRules.updatePools(s, pools,
                new HashSet<Product>());
        assertEquals(2, updates.size());
        physicalPool = updates.get(0).getPool();
        Pool unmappedPool = updates.get(1).getPool();
        assertEquals(new Long(50), physicalPool.getQuantity());
        assertEquals(0, physicalPool.getAttributes().size());
    }

    private Subscription createVirtOnlySub(String productId, int quantity) {
        Product product = new Product(productId, productId, owner);
        product.setAttribute("virt_only", "true");
        when(prodCuratorMock.lookupById(product.getOwner(), product.getId()))
            .thenReturn(product);
        Subscription s = TestUtil.createSubscription(owner, product);
        s.setQuantity(new Long(quantity));
        return s;
    }

    @Test
    public void hostedVirtOnlySubCreate() {
        when(configMock.getBoolean(ConfigProperties.STANDALONE)).thenReturn(true);
        Subscription s = createVirtOnlySub("virtOnlyProduct", 10);
        List<Pool> pools = poolRules.createAndEnrichPools(s);
        assertEquals(1, pools.size());
        assertEquals("true", pools.get(0).getProduct().getAttributeValue("virt_only"));
        assertEquals(new Long(10), pools.get(0).getQuantity());
    }

    @Test
    public void hostedVirtOnlySubCreateWithMultiplier() {
        when(configMock.getBoolean(ConfigProperties.STANDALONE)).thenReturn(true);
        Subscription s = createVirtOnlySub("virtOnlyProduct", 10);
        s.getProduct().setMultiplier(new Long(5));
        List<Pool> pools = poolRules.createAndEnrichPools(s);
        assertEquals(1, pools.size());
        assertEquals("true", pools.get(0).getProduct().getAttributeValue("virt_only"));
        assertEquals(new Long(50), pools.get(0).getQuantity());
    }

    @Test
    public void hostedVirtOnlySubUpdate() {
        when(configMock.getBoolean(ConfigProperties.STANDALONE)).thenReturn(true);
        Subscription s = createVirtOnlySub("virtOnlyProduct", 10);
        List<Pool> pools = poolRules.createAndEnrichPools(s);
        assertEquals(1, pools.size());
        s.setQuantity(new Long(20));

        List<PoolUpdate> updates = poolRules.updatePools(s, pools,
                new HashSet<Product>());
        assertEquals(1, updates.size());
        Pool updated = updates.get(0).getPool();
        assertEquals(new Long(20), updated.getQuantity());
    }

    @Test
    public void standaloneVirtSubPoolUpdateNoChanges() {
        when(configMock.getBoolean(ConfigProperties.STANDALONE)).thenReturn(true);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, 10);
        List<Pool> pools = poolRules.createAndEnrichPools(s);
        assertEquals(2, pools.size());
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

        List<PoolUpdate> updates = poolRules.updatePools(s, pools,
                new HashSet<Product>());
        assertEquals(0, updates.size());
    }

    @Test
    public void standaloneVirtSubPoolUpdateVirtLimitChanged() {
        when(configMock.getBoolean(ConfigProperties.STANDALONE)).thenReturn(true);
        Subscription s = createVirtLimitSub("virtLimitProduct", 10, 10);
        List<Pool> pools = poolRules.createAndEnrichPools(s);
        assertEquals(2, pools.size());
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
        List<PoolUpdate> updates = poolRules.updatePools(s, pools,
                TestUtil.stubChangedProducts(s.getProduct()));
        assertEquals(3, updates.size());
        Pool regular = updates.get(0).getPool();
        Pool unmappedSubPool = updates.get(1).getPool();
        Pool subPool = updates.get(2).getPool();
        assertEquals("40", regular.getProduct().getAttributeValue("virt_limit"));
        assertEquals(new Long(40), subPool.getQuantity());
        assertEquals(new Long(800), unmappedSubPool.getQuantity());
    }

    @Test
    public void dontUpdateVirtOnlyNoVirtLimit() {
        when(configMock.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct(owner));
        s.setQuantity(10L);
        when(productAdapterMock.getProductById(s.getProduct().getOwner(), s.getProduct().getId()))
            .thenReturn(s.getProduct());

        // Setup a pool with a single (different) provided product:
        Pool p = TestUtil.copyFromSub(s);
        p.addAttribute(new PoolAttribute("virt_only", "true"));
        p.addAttribute(new PoolAttribute("pool_derived", "true"));
        p.setQuantity(10L);

        List<Pool> existingPools = new LinkedList<Pool>();
        existingPools.add(p);
        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools,
                new HashSet<Product>());

        assertEquals(0, updates.size());
    }

    @Test
    public void updateVirtOnlyNoVirtLimit() {
        when(configMock.getBoolean(ConfigProperties.STANDALONE)).thenReturn(false);
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct(owner));
        s.setQuantity(10L);

        // Setup a pool with a single (different) provided product:
        Pool p = TestUtil.copyFromSub(s);
        p.addAttribute(new PoolAttribute("virt_only", "true"));
        p.addAttribute(new PoolAttribute("pool_derived", "true"));
        p.setQuantity(20L);

        List<Pool> existingPools = new LinkedList<Pool>();
        existingPools.add(p);
        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools,
                new HashSet<Product>());

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertFalse(update.getProductsChanged());
        assertFalse(update.getDatesChanged());
        assertTrue(update.getQuantityChanged());
        assertEquals(Long.valueOf(10), update.getPool().getQuantity());
    }

    @Test
    public void contractNumberChanged() {
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct(owner));
        s.setContractNumber("123");

        // Setup a pool with a single (different) provided product:
        Pool p = TestUtil.copyFromSub(s);
        p.setQuantity(2000L);
        p.setContractNumber("ABC");

        List<Pool> existingPools = new LinkedList<Pool>();
        existingPools.add(p);
        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools,
                new HashSet<Product>());

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertTrue(update.getOrderChanged());
        assertEquals("123", update.getPool().getContractNumber());
    }

    @Test
    public void orderNumberChanged() {
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct(owner));
        s.setOrderNumber("123");

        // Setup a pool with a single (different) provided product:
        Pool p = TestUtil.copyFromSub(s);
        p.setQuantity(2000L);
        p.setOrderNumber("ABC");

        List<Pool> existingPools = new LinkedList<Pool>();
        existingPools.add(p);
        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools,
                new HashSet<Product>());

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertTrue(update.getOrderChanged());
        assertEquals("123", update.getPool().getOrderNumber());
    }

    @Test
    public void accountNumberChanged() {
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct(owner));
        s.setAccountNumber("123");

        // Setup a pool with a single (different) provided product:
        Pool p = TestUtil.copyFromSub(s);
        p.setQuantity(2000L);
        p.setAccountNumber("ABC");

        List<Pool> existingPools = new LinkedList<Pool>();
        existingPools.add(p);
        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools,
                new HashSet<Product>());

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertTrue(update.getOrderChanged());
        assertEquals("123", update.getPool().getAccountNumber());
    }

    @Test
    public void productNameChangedDevPool() {
        Pool p = TestUtil.createPool(TestUtil.createProduct(owner));
        p.setSourceSubscription(null);
        p.setAttribute(Pool.DEVELOPMENT_POOL_ATTRIBUTE, "true");
        List<Pool> floatingPools = new ArrayList<Pool>();
        floatingPools.add(p);

        Product changed = p.getProduct();
        changed.setName("somethingelse");
        Set<Product> changedProducts = new HashSet<Product>();
        changedProducts.add(changed);

        List<PoolUpdate> updates = this.poolRules.updatePools(floatingPools, changedProducts);
        assertEquals(0, updates.size());
    }

    @Test
    public void noPoolsCreatedTest() {
        Product product = TestUtil.createProduct(owner);
        List<Pool> existingPools = new ArrayList<Pool>();
        Pool masterPool = TestUtil.createPool(product);
        masterPool.setSubscriptionSubKey("master");
        existingPools.add(masterPool);
        Pool derivedPool = TestUtil.createPool(product);
        derivedPool.setSubscriptionSubKey("derived");
        existingPools.add(derivedPool);
        List<Pool> pools = this.poolRules.createAndEnrichPools(masterPool, existingPools);
        assertEquals(0, pools.size());
    }

    @Test
    public void derivedPoolCreateCreatedTest() {
        Product product = TestUtil.createProduct(owner);
        product.setAttribute("virt_limit", "4");
        List<Pool> existingPools = new ArrayList<Pool>();
        Pool masterPool = TestUtil.createPool(product);
        masterPool.setSubscriptionSubKey("master");
        existingPools.add(masterPool);
        List<Pool> pools = this.poolRules.createAndEnrichPools(masterPool, existingPools);
        assertEquals(1, pools.size());
        assertEquals("derived", pools.get(0).getSubscriptionSubKey());
    }

    @Test(expected = IllegalStateException.class)
    public void cantCreateMasterPoolFromDerivedPoolTest() {
        Product product = TestUtil.createProduct(owner);
        List<Pool> existingPools = new ArrayList<Pool>();
        Pool masterPool = TestUtil.createPool(product);
        masterPool.setSubscriptionSubKey("derived");
        existingPools.add(masterPool);
        List<Pool> pools = this.poolRules.createAndEnrichPools(masterPool, existingPools);
    }

}
