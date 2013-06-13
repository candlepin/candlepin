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
package org.candlepin.policy.js.pool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductPoolAttribute;
import org.candlepin.model.ProvidedProduct;
import org.candlepin.model.SubProvidedProduct;
import org.candlepin.model.Subscription;
import org.candlepin.policy.js.ProductCache;
import org.candlepin.policy.js.pool.PoolHelper;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

/**
 * PoolHelperTest
 */
public class PoolHelperTest {

    private Pool pool;
    private Subscription sub;
    private Product product;
    private PoolManager pm;
    private ProductServiceAdapter psa;
    private Entitlement ent;
    private ProductCache productCache;

    @Before
    public void init() {
        pool = mock(Pool.class);
        sub = mock(Subscription.class);
        product = mock(Product.class);
        pm = mock(PoolManager.class);
        psa = mock(ProductServiceAdapter.class);
        ent = mock(Entitlement.class);

        Config config = mock(Config.class);
        when(config.getInt(eq(ConfigProperties.PRODUCT_CACHE_MAX))).thenReturn(100);
        productCache = new ProductCache(config, psa);

        // default to an empty list, override in the test
        when(pool.getProvidedProducts()).thenReturn(Collections.EMPTY_SET);
        when(sub.getProvidedProducts()).thenReturn(Collections.EMPTY_SET);
    }

    @Test
    public void productsDidnotChange() {
        when(pool.getProductId()).thenReturn("prodid123");
        when(pool.getProductName()).thenReturn("Awesome Product");
        when(product.getId()).thenReturn("prodid123");
        when(product.getName()).thenReturn("Awesome Product");
        when(sub.getProduct()).thenReturn(product);

        PoolHelper ph = new PoolHelper(pm, productCache, null);
        assertFalse(ph.checkForChangedProducts(pool, sub));
    }

    @Test
    public void nameChanged() {
        when(pool.getProductId()).thenReturn("prodid123");
        when(pool.getProductName()).thenReturn("Awesome Product");
        when(product.getId()).thenReturn("prodid123");
        when(product.getName()).thenReturn("Awesome Product Changed");
        when(sub.getProduct()).thenReturn(product);

        PoolHelper ph = new PoolHelper(pm, productCache, null);
        assertTrue(ph.checkForChangedProducts(pool, sub));
    }

    @Test
    public void orderDataChangedOrderNumber() {
        when(pool.getOrderNumber()).thenReturn("123A");
        when(pool.getAccountNumber()).thenReturn("456");
        when(pool.getContractNumber()).thenReturn("789");
        when(sub.getOrderNumber()).thenReturn("123");
        when(sub.getAccountNumber()).thenReturn("456");
        when(sub.getContractNumber()).thenReturn("789");

        PoolHelper ph = new PoolHelper(pm, productCache, null);
        assertTrue(ph.checkForOrderChanges(pool, sub));
    }

    @Test
    public void orderDataChangedAccountNumber() {
        when(pool.getOrderNumber()).thenReturn("123");
        when(pool.getAccountNumber()).thenReturn("456A");
        when(pool.getContractNumber()).thenReturn("789");
        when(sub.getOrderNumber()).thenReturn("123");
        when(sub.getAccountNumber()).thenReturn("456");
        when(sub.getContractNumber()).thenReturn("789");

        PoolHelper ph = new PoolHelper(pm, productCache, null);
        assertTrue(ph.checkForOrderChanges(pool, sub));
    }

    @Test
    public void orderDataChangedContractNumber() {
        when(pool.getOrderNumber()).thenReturn("123");
        when(pool.getAccountNumber()).thenReturn("456");
        when(pool.getContractNumber()).thenReturn("789A");
        when(sub.getOrderNumber()).thenReturn("123");
        when(sub.getAccountNumber()).thenReturn("456");
        when(sub.getContractNumber()).thenReturn("789");

        PoolHelper ph = new PoolHelper(pm, productCache, null);
        assertTrue(ph.checkForOrderChanges(pool, sub));
    }

    @Test
    public void orderDataChanged() {
        when(pool.getOrderNumber()).thenReturn("123");
        when(pool.getAccountNumber()).thenReturn("456");
        when(pool.getContractNumber()).thenReturn("789");
        when(sub.getOrderNumber()).thenReturn("123");
        when(sub.getAccountNumber()).thenReturn("456");
        when(sub.getContractNumber()).thenReturn("789");

        PoolHelper ph = new PoolHelper(pm, productCache, null);
        assertFalse(ph.checkForOrderChanges(pool, sub));
    }

    @Test
    public void usingPrimitiveEqualsOnStringIsBad() {
        when(pool.getOrderNumber()).thenReturn("123");
        when(pool.getAccountNumber()).thenReturn("456");
        when(pool.getContractNumber()).thenReturn("789");
        when(sub.getOrderNumber()).thenReturn(new String("123"));
        when(sub.getAccountNumber()).thenReturn("456");
        when(sub.getContractNumber()).thenReturn("789");

        PoolHelper ph = new PoolHelper(pm, productCache, null);
        assertFalse(ph.checkForOrderChanges(pool, sub));
    }

    @Test
    public void productIdDifferent() {
        when(pool.getProductId()).thenReturn("prodid123");
        when(pool.getProductName()).thenReturn("Awesome Product");
        when(product.getId()).thenReturn("prodidnew");
        when(product.getName()).thenReturn("Awesome Product");
        when(sub.getProduct()).thenReturn(product);

        PoolHelper ph = new PoolHelper(pm, productCache, null);
        assertTrue(ph.checkForChangedProducts(pool, sub));
    }

    @Test
    public void attributeChanged() {
        when(pool.getProductId()).thenReturn("prodid123");
        when(pool.getProductName()).thenReturn("Awesome Product");
        when(product.getId()).thenReturn("prodid123");
        when(product.getName()).thenReturn("Awesome Product");
        when(sub.getProduct()).thenReturn(product);

        Set<ProvidedProduct> poolprods = new HashSet<ProvidedProduct>();
        ProvidedProduct pp = new ProvidedProduct("productid", "Awesome Product");
        pp.setId(pp.getProductId());
        poolprods.add(pp);
        Set<Product> subprods = new HashSet<Product>();
        Product p = new Product("productid", "Awesome Product");
        p.setAttribute("hola", "mundo");
        subprods.add(p);
        when(pool.getProvidedProducts()).thenReturn(poolprods);
        when(sub.getProvidedProducts()).thenReturn(subprods);

        ProductPoolAttribute pppa = mock(ProductPoolAttribute.class);
        when(pppa.getName()).thenReturn("hola");
        when(pppa.getValue()).thenReturn("mundo nuevo");
        when(pppa.getProductId()).thenReturn("productid"); // matches sub prod
        when(pppa.getId()).thenReturn("hoy");
        Set<ProductPoolAttribute> attribs =
            new HashSet<ProductPoolAttribute>();
        attribs.add(pppa);
        when(pool.getProductAttributes()).thenReturn(attribs);

        PoolHelper ph = new PoolHelper(pm, productCache, null);
        assertTrue(ph.checkForChangedProducts(pool, sub));
    }

    @Test
    public void copyProductAttributesOntoPoolAddsNewAttribute() {
        Product targetProduct = TestUtil.createProduct();
        targetProduct.getAttributes().clear();
        targetProduct.setAttribute("A1", "V1");
        targetProduct.setAttribute("A2", "V2");
        Subscription sourceSub = TestUtil.createSubscription(targetProduct);

        Pool targetPool = TestUtil.createPool(targetProduct);
        // createPool will simulate the copy automatically - reset them.
        targetPool.setProductAttributes(new HashSet<ProductPoolAttribute>());

        PoolHelper ph = new PoolHelper(pm, productCache, null);
        when(psa.getProductById(targetProduct.getId())).thenReturn(targetProduct);
        assertTrue("Update expected.",
            ph.copyProductAttributesOntoPool(sourceSub.getProduct().getId(), targetPool));
        assertEquals(2, targetPool.getProductAttributes().size());
        assertTrue(targetPool.hasProductAttribute("A1"));
        assertTrue(targetPool.hasProductAttribute("A2"));
    }

    @Test
    public void copyProductAttributesOntoPoolUpdatesExistingAttribute() {
        Product targetProduct = TestUtil.createProduct();
        targetProduct.getAttributes().clear();
        targetProduct.setAttribute("A1", "V-updated");
        Subscription sourceSub = TestUtil.createSubscription(targetProduct);

        Pool targetPool = TestUtil.createPool(targetProduct);
        targetPool.setProductAttribute("A1", "V1", targetProduct.getId());

        PoolHelper ph = new PoolHelper(pm, productCache, null);
        when(psa.getProductById(targetProduct.getId())).thenReturn(targetProduct);
        assertTrue("Update expected.",
            ph.copyProductAttributesOntoPool(sourceSub.getProduct().getId(), targetPool));
        assertEquals(1, targetPool.getProductAttributes().size());
        assertTrue(targetPool.hasProductAttribute("A1"));
        assertEquals("V-updated", targetPool.getProductAttribute("A1").getValue());
    }

    @Test
    public void copyProductAttributesOntoPoolWithNulls() {
        Product targetProduct = TestUtil.createProduct();
        targetProduct.getAttributes().clear();
        targetProduct.setAttribute("A1", "V-updated");
        Subscription sourceSub = TestUtil.createSubscription(targetProduct);

        Pool targetPool = TestUtil.createPool(targetProduct);
        targetPool.setProductAttribute("A1", null, targetProduct.getId());

        PoolHelper ph = new PoolHelper(pm, productCache, null);
        when(psa.getProductById(targetProduct.getId())).thenReturn(targetProduct);
        assertTrue("Update expected.",
            ph.copyProductAttributesOntoPool(sourceSub.getProduct().getId(), targetPool));
        assertEquals(1, targetPool.getProductAttributes().size());
        assertTrue(targetPool.hasProductAttribute("A1"));
        assertEquals("V-updated", targetPool.getProductAttribute("A1").getValue());

        targetProduct = TestUtil.createProduct();
        targetProduct.getAttributes().clear();
        targetProduct.setAttribute("A1", null);
        sourceSub = TestUtil.createSubscription(targetProduct);

        targetPool = TestUtil.createPool(targetProduct);
        targetPool.setProductAttribute("A1", "V-updated-new", targetProduct.getId());

        ph = new PoolHelper(pm, productCache, null);
        when(psa.getProductById(targetProduct.getId())).thenReturn(targetProduct);
        assertTrue("Update expected.",
            ph.copyProductAttributesOntoPool(sourceSub.getProduct().getId(), targetPool));
        assertEquals(1, targetPool.getProductAttributes().size());
        assertTrue(targetPool.hasProductAttribute("A1"));
        assertEquals(null, targetPool.getProductAttribute("A1").getValue());

        targetProduct = TestUtil.createProduct();
        targetProduct.getAttributes().clear();
        targetProduct.setAttribute("A1", null);
        sourceSub = TestUtil.createSubscription(targetProduct);

        targetPool = TestUtil.createPool(targetProduct);
        targetPool.setProductAttribute("A1", null, targetProduct.getId());

        ph = new PoolHelper(pm, productCache, null);
        when(psa.getProductById(targetProduct.getId())).thenReturn(targetProduct);
        assertTrue("Update expected.",
            ph.copyProductAttributesOntoPool(sourceSub.getProduct().getId(), targetPool));
        assertEquals(1, targetPool.getProductAttributes().size());
        assertTrue(targetPool.hasProductAttribute("A1"));
        assertEquals(null, targetPool.getProductAttribute("A1").getValue());
    }

    @Test
    public void copyProductAttributesOntoPoolRemovesNonExistingAttribute() {
        Product targetProduct = TestUtil.createProduct();
        targetProduct.getAttributes().clear();
        Subscription sourceSub = TestUtil.createSubscription(targetProduct);
        Pool targetPool = TestUtil.createPool(targetProduct);

        targetPool.setProductAttribute("A1", "V1", targetProduct.getId());

        PoolHelper ph = new PoolHelper(pm, productCache, null);
        when(psa.getProductById(targetProduct.getId())).thenReturn(targetProduct);
        assertTrue("Update expected.",
            ph.copyProductAttributesOntoPool(sourceSub.getProduct().getId(), targetPool));
        assertTrue(targetPool.getProductAttributes().isEmpty());
    }

    @Test
    public void copyProductAttributesForHostRestrictedPools() {
        Product targetProduct = TestUtil.createProduct();
        Consumer cons = TestUtil.createConsumer();
        targetProduct.getAttributes().clear();
        targetProduct.setAttribute("A1", "V1");
        targetProduct.setAttribute("A2", "V2");
        Pool targetPool = TestUtil.createPool(targetProduct);
        targetPool.setId("jso_speedwagon");

        when(psa.getProductById(targetProduct.getId())).thenReturn(targetProduct);
        when(ent.getConsumer()).thenReturn(cons);

        PoolHelper ph = new PoolHelper(pm, productCache, ent);
        Pool hostRestrictedPool = ph.createHostRestrictedPool(targetProduct.getId(),
            targetPool, "unlimited");

        assertEquals(targetPool.getId(),
            hostRestrictedPool.getAttributeValue("source_pool_id"));
        assertEquals(2, hostRestrictedPool.getProductAttributes().size());
        assertTrue(hostRestrictedPool.hasProductAttribute("A1"));
        assertTrue(hostRestrictedPool.hasProductAttribute("A2"));
    }

    @Test
    public void hostRestrictedPoolCreatedWithSubProductPoolData() {
        Consumer cons = TestUtil.createConsumer();
        Owner owner = cons.getOwner();

        // Create a product for the main pool to be sure that
        // the attributes do not get copied to the sub pool.
        Product mainPoolProduct = TestUtil.createProduct();
        mainPoolProduct.getAttributes().clear();
        mainPoolProduct.setAttribute("A1", "V1");
        mainPoolProduct.setAttribute("A2", "V2");

        SubProvidedProduct subProvided1 = new SubProvidedProduct("sub-pp-1", "Sub Provided 1");
        SubProvidedProduct subProvided2 = new SubProvidedProduct("sub-pp-2", "Sub Provided 2");

        Set<SubProvidedProduct> subProvidedProducts = new HashSet<SubProvidedProduct>();
        subProvidedProducts.add(subProvided1);
        subProvidedProducts.add(subProvided2);

        Product subProduct = TestUtil.createProduct();
        subProduct.getAttributes().clear();
        subProduct.setAttribute("SA1", "SV1");
        subProduct.setAttribute("SA2", "SV2");

        Pool targetPool = TestUtil.createPool(owner, mainPoolProduct, 4);
        targetPool.setId("sub-prod-pool");
        targetPool.setSubProductId(subProduct.getId());
        targetPool.setSubProductName(subProduct.getName());
        targetPool.setSubProvidedProducts(subProvidedProducts);

        when(psa.getProductById(subProduct.getId())).thenReturn(subProduct);
        when(ent.getConsumer()).thenReturn(cons);

        PoolHelper ph = new PoolHelper(pm, productCache, ent);
        Pool hostRestrictedPool = ph.createHostRestrictedPool(targetPool.getProductId(),
            targetPool, "quantity-ignored-for-sub-product-pool");

        assertEquals(targetPool.getId(),
            hostRestrictedPool.getAttributeValue("source_pool_id"));
        assertEquals(2, hostRestrictedPool.getProductAttributes().size());
        assertTrue(hostRestrictedPool.hasProductAttribute("SA1"));
        assertEquals("SV1", hostRestrictedPool.getProductAttribute("SA1").getValue());
        assertTrue(hostRestrictedPool.hasProductAttribute("SA2"));
        assertEquals("SV2", hostRestrictedPool.getProductAttribute("SA2").getValue());

        // Check that the sub provided products made it to the sub pool
        Set<String> providedProdIds = new HashSet<String>();
        for (ProvidedProduct pp : hostRestrictedPool.getProvidedProducts()) {
            providedProdIds.add(pp.getProductId());
            // Make sure that the correct pool was associated
            assertEquals(hostRestrictedPool, pp.getPool());
        }
        assertEquals(2, providedProdIds.size());
        assertTrue(providedProdIds.contains(subProvided1.getProductId()));
        assertTrue(providedProdIds.contains(subProvided2.getProductId()));

        // Ensure that the quantity on the subpool is unlimited despite the parent having
        // a quantity of 4.
        assertEquals(-1L, (long) hostRestrictedPool.getQuantity());
    }

}
