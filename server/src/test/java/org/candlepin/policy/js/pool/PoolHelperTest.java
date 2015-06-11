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

import static org.junit.Assert.*;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.dto.Subscription;
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
    private Owner owner;

    @Before
    public void init() {
        pool = mock(Pool.class);
        sub = mock(Subscription.class);
        pm = mock(PoolManager.class);
        psa = mock(ProductServiceAdapter.class);
        ent = mock(Entitlement.class);

        Configuration config = mock(Configuration.class);
        when(config.getInt(eq(ConfigProperties.PRODUCT_CACHE_MAX))).thenReturn(100);

        // default to an empty list, override in the test
        when(pool.getProvidedProducts()).thenReturn(Collections.EMPTY_SET);
        when(sub.getProvidedProducts()).thenReturn(Collections.EMPTY_SET);

        owner = TestUtil.createOwner();
    }

    @Test
    public void orderDataChangedOrderNumber() {
        when(pool.getOrderNumber()).thenReturn("123A");
        when(pool.getAccountNumber()).thenReturn("456");
        when(pool.getContractNumber()).thenReturn("789");
        when(sub.getOrderNumber()).thenReturn("123");
        when(sub.getAccountNumber()).thenReturn("456");
        when(sub.getContractNumber()).thenReturn("789");

        PoolHelper ph = new PoolHelper(pm, null);
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

        PoolHelper ph = new PoolHelper(pm, null);
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

        PoolHelper ph = new PoolHelper(pm, null);
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

        PoolHelper ph = new PoolHelper(pm, null);
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

        PoolHelper ph = new PoolHelper(pm, null);
        assertFalse(ph.checkForOrderChanges(pool, sub));
    }

    @Test
    public void copyProductAttributesForHostRestrictedPools() {
        Product targetProduct = TestUtil.createProduct(owner);
        Consumer cons = TestUtil.createConsumer();
        targetProduct.getAttributes().clear();
        targetProduct.setAttribute("A1", "V1");
        targetProduct.setAttribute("A2", "V2");
        Pool targetPool = TestUtil.createPool(targetProduct);
        targetPool.setId("jso_speedwagon");

        // when(psa.getProductById(targetProduct.getUuid())).thenReturn(targetProduct);
        when(ent.getConsumer()).thenReturn(cons);

        PoolHelper ph = new PoolHelper(pm, ent);
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
        Product mainPoolProduct = TestUtil.createProduct(owner);
        mainPoolProduct.getAttributes().clear();
        mainPoolProduct.setAttribute("A1", "V1");
        mainPoolProduct.setAttribute("A2", "V2");

        Product derivedProduct1 = TestUtil.createProduct("sub-pp-1", "Sub Provided 1", owner);
        Product derivedProduct2 = TestUtil.createProduct("sub-pp-2", "Sub Provided 2", owner);

        Set<Product> derivedProducts = new HashSet<Product>();
        derivedProducts.add(derivedProduct1);
        derivedProducts.add(derivedProduct2);

        Product subProduct = TestUtil.createProduct(owner);
        subProduct.getAttributes().clear();
        subProduct.setAttribute("SA1", "SV1");
        subProduct.setAttribute("SA2", "SV2");

        Pool targetPool = TestUtil.createPool(mainPoolProduct);
        targetPool.setId("sub-prod-pool");
        targetPool.setDerivedProduct(subProduct);

        targetPool.setDerivedProvidedProducts(derivedProducts);

        // when(psa.getProductById(subProduct.getUuid())).thenReturn(subProduct);
        when(ent.getConsumer()).thenReturn(cons);

        PoolHelper ph = new PoolHelper(pm, ent);
        Pool hostRestrictedPool = ph.createHostRestrictedPool(
            targetPool.getDerivedProduct(), targetPool, "unlimited"
        );

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
