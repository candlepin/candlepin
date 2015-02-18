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

import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.Subscription;
import org.candlepin.policy.js.ProductCache;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.test.TestUtil;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * PoolHelperTest
 */
public class PoolHelperTest {

    private Pool pool;
    private Subscription sub;
    private PoolManager pm;
    private ProductServiceAdapter psa;
    private Entitlement ent;
    private ProductCache productCache;

    @Before
    public void init() {
        pool = mock(Pool.class);
        sub = mock(Subscription.class);
        pm = mock(PoolManager.class);
        psa = mock(ProductServiceAdapter.class);
        ent = mock(Entitlement.class);

        Configuration config = mock(Configuration.class);
        when(config.getInt(eq(ConfigProperties.PRODUCT_CACHE_MAX))).thenReturn(100);
        productCache = new ProductCache(config, psa);

        // default to an empty list, override in the test
        when(pool.getProvidedProducts()).thenReturn(Collections.EMPTY_SET);
        when(sub.getProvidedProducts()).thenReturn(Collections.EMPTY_SET);
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

    // @Test
    // public void copyProductAttributesOntoPoolAddsNewAttribute() {
    //     Product targetProduct = TestUtil.createProduct();
    //     targetProduct.getAttributes().clear();
    //     targetProduct.setAttribute("A1", "V1");
    //     targetProduct.setAttribute("A2", "V2");
    //     Subscription sourceSub = TestUtil.createSubscription(targetProduct);

    //     Pool targetPool = TestUtil.createPool(targetProduct);
    //     // createPool will simulate the copy automatically - reset them.
    //     targetPool.setProductAttributes(new HashSet<ProductPoolAttribute>());

    //     PoolHelper ph = new PoolHelper(pm, productCache, null);
    //     when(psa.getProductById(targetProduct.getId())).thenReturn(targetProduct);
    //     assertTrue("Update expected.",
    //         ph.copyProductAttributesOntoPool(sourceSub.getProduct().getId(), targetPool));
    //     assertEquals(2, targetPool.getProduct().getAttributes().size());
    //     assertTrue(targetPool.getProduct().hasAttribute("A1"));
    //     assertTrue(targetPool.getProduct().hasAttribute("A2"));
    // }

    // @Test
    // public void copyProductAttributesOntoPoolUpdatesExistingAttribute() {
    //     Product targetProduct = TestUtil.createProduct();
    //     targetProduct.getAttributes().clear();
    //     targetProduct.setAttribute("A1", "V-updated");
    //     Subscription sourceSub = TestUtil.createSubscription(targetProduct);

    //     Pool targetPool = TestUtil.createPool(targetProduct);
    //     targetPool.setProductAttribute("A1", "V1", targetProduct.getId());

    //     PoolHelper ph = new PoolHelper(pm, productCache, null);
    //     when(psa.getProductById(targetProduct.getId())).thenReturn(targetProduct);
    //     assertTrue("Update expected.",
    //         ph.copyProductAttributesOntoPool(sourceSub.getProduct().getId(), targetPool));
    //     assertEquals(1, targetPool.getProduct().getAttributes().size());
    //     assertTrue(targetPool.getProduct().hasAttribute("A1"));
    //     assertEquals("V-updated", targetPool.getProduct().getAttributeValue("A1"));
    // }

    // @Test
    // public void copyProductAttributesOntoPoolWithNulls() {
    //     Product targetProduct = TestUtil.createProduct();
    //     targetProduct.getAttributes().clear();
    //     targetProduct.setAttribute("A1", "V-updated");
    //     Subscription sourceSub = TestUtil.createSubscription(targetProduct);

    //     Pool targetPool = TestUtil.createPool(targetProduct);
    //     targetPool.setProductAttribute("A1", null, targetProduct.getId());

    //     PoolHelper ph = new PoolHelper(pm, productCache, null);
    //     when(psa.getProductById(targetProduct.getId())).thenReturn(targetProduct);
    //     assertTrue("Update expected.",
    //         ph.copyProductAttributesOntoPool(sourceSub.getProduct().getId(), targetPool));
    //     assertEquals(1, targetPool.getProduct().getAttributes().size());
    //     assertTrue(targetPool.getProduct().hasAttribute("A1"));
    //     assertEquals("V-updated", targetPool.getProduct().getAttributeValue("A1"));

    //     targetProduct = TestUtil.createProduct();
    //     targetProduct.getAttributes().clear();
    //     targetProduct.setAttribute("A1", null);
    //     sourceSub = TestUtil.createSubscription(targetProduct);

    //     targetPool = TestUtil.createPool(targetProduct);
    //     targetPool.setProductAttribute("A1", "V-updated-new", targetProduct.getId());

    //     ph = new PoolHelper(pm, productCache, null);
    //     when(psa.getProductById(targetProduct.getId())).thenReturn(targetProduct);
    //     assertTrue("Update expected.",
    //         ph.copyProductAttributesOntoPool(sourceSub.getProduct().getId(), targetPool));
    //     assertEquals(1, targetPool.getProduct().getAttributes().size());
    //     assertTrue(targetPool.getProduct().hasAttribute("A1"));
    //     assertEquals(null, targetPool.getProduct().getAttributeValue("A1"));

    //     targetProduct = TestUtil.createProduct();
    //     targetProduct.getAttributes().clear();
    //     targetProduct.setAttribute("A1", null);
    //     sourceSub = TestUtil.createSubscription(targetProduct);

    //     targetPool = TestUtil.createPool(targetProduct);
    //     targetPool.setProductAttribute("A1", null, targetProduct.getId());

    //     ph = new PoolHelper(pm, productCache, null);
    //     when(psa.getProductById(targetProduct.getId())).thenReturn(targetProduct);
    //     assertFalse("No update expected.",
    //         ph.copyProductAttributesOntoPool(sourceSub.getProduct().getId(), targetPool));
    //     assertEquals(1, targetPool.getProduct().getAttributes().size());
    //     assertTrue(targetPool.getProduct().hasAttribute("A1"));
    //     assertEquals(null, targetPool.getProduct().getAttributeValue("A1"));
    // }

    // @Test
    // public void copyProductAttributesOntoPoolRemovesNonExistingAttribute() {
    //     Product targetProduct = TestUtil.createProduct();
    //     targetProduct.getAttributes().clear();
    //     Subscription sourceSub = TestUtil.createSubscription(targetProduct);
    //     Pool targetPool = TestUtil.createPool(targetProduct);

    //     targetPool.setProductAttribute("A1", "V1", targetProduct.getId());

    //     PoolHelper ph = new PoolHelper(pm, productCache, null);
    //     when(psa.getProductById(targetProduct.getId())).thenReturn(targetProduct);
    //     assertTrue("Update expected.",
    //         ph.copyProductAttributesOntoPool(sourceSub.getProduct().getId(), targetPool));
    //     assertTrue(targetPool.getProduct().getAttributes().isEmpty());
    // }

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
        Pool hostRestrictedPool = ph.createHostRestrictedPool(targetProduct, targetPool, "unlimited");

        assertEquals(targetPool.getId(),
            hostRestrictedPool.getAttributeValue("source_pool_id"));
        assertEquals(2, hostRestrictedPool.getProduct().getAttributes().size());
        assertTrue(hostRestrictedPool.getProduct().hasAttribute("A1"));
        assertTrue(hostRestrictedPool.getProduct().hasAttribute("A2"));
    }

    @Test
    public void hostRestrictedPoolCreatedWithSubProductPoolData() {
        Consumer cons = TestUtil.createConsumer();

        // Create a product for the main pool to be sure that
        // the attributes do not get copied to the sub pool.
        Product mainPoolProduct = TestUtil.createProduct();
        mainPoolProduct.getAttributes().clear();
        mainPoolProduct.setAttribute("A1", "V1");
        mainPoolProduct.setAttribute("A2", "V2");

        Owner owner = TestUtil.createOwner();
        Product derivedProduct1 = TestUtil.createProduct("sub-pp-1", "Sub Provided 1", owner);
        Product derivedProduct2 = TestUtil.createProduct("sub-pp-2", "Sub Provided 2", owner);

        Set<Product> derivedProducts = new HashSet<Product>();
        derivedProducts.add(derivedProduct1);
        derivedProducts.add(derivedProduct2);

        Product subProduct = TestUtil.createProduct();
        subProduct.getAttributes().clear();
        subProduct.setAttribute("SA1", "SV1");
        subProduct.setAttribute("SA2", "SV2");

        Pool targetPool = TestUtil.createPool(mainPoolProduct);
        targetPool.setId("sub-prod-pool");
        targetPool.setDerivedProduct(subProduct);

        targetPool.setDerivedProvidedProducts(derivedProducts);

        when(psa.getProductById(subProduct.getId())).thenReturn(subProduct);
        when(ent.getConsumer()).thenReturn(cons);

        PoolHelper ph = new PoolHelper(pm, productCache, ent);
        Pool hostRestrictedPool = ph.createHostRestrictedPool(
            targetPool.getDerivedProduct(), targetPool, "unlimited");

        assertEquals(targetPool.getId(), hostRestrictedPool.getAttributeValue("source_pool_id"));
        assertEquals(-1L, (long) hostRestrictedPool.getQuantity());
        assertEquals(2, hostRestrictedPool.getProduct().getAttributes().size());
        assertTrue(hostRestrictedPool.getProduct().hasAttribute("SA1"));
        assertEquals("SV1", hostRestrictedPool.getProduct().getAttributeValue("SA1"));
        assertTrue(hostRestrictedPool.getProduct().hasAttribute("SA2"));
        assertEquals("SV2", hostRestrictedPool.getProduct().getAttributeValue("SA2"));

        // Check that the sub provided products made it to the sub pool
        Set<Product> providedProducts = hostRestrictedPool.getProvidedProducts();

        assertEquals(2, providedProducts.size());
        assertTrue(providedProducts.contains(derivedProduct1));
        assertTrue(providedProducts.contains(derivedProduct2));
    }

}
