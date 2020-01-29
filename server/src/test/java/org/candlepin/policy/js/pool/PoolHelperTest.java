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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Mockito.anyListOf;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.bind.PoolOperationCallback;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.Branding;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.test.TestUtil;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PoolHelperTest
 */
public class PoolHelperTest {

    private PoolManager pm;
    private ProductServiceAdapter psa;
    private Entitlement ent;
    private Owner owner;
    private ProductCurator productCurator;

    @Before
    public void init() {
        pm = mock(PoolManager.class);
        psa = mock(ProductServiceAdapter.class);
        ent = mock(Entitlement.class);
        productCurator = Mockito.mock(ProductCurator.class);
        Configuration config = mock(Configuration.class);
        when(config.getInt(eq(ConfigProperties.PRODUCT_CACHE_MAX))).thenReturn(100);

        owner = TestUtil.createOwner();
    }

    @Test
    public void orderDataChangedOrderNumber() {
        Pool pool = new Pool();
        pool.setOrderNumber("123A");
        pool.setAccountNumber("456");
        pool.setContractNumber("789");
        Pool existingPool = new Pool();
        existingPool.setOrderNumber("123");
        existingPool.setAccountNumber("456");
        existingPool.setContractNumber("789");

        assertTrue(PoolHelper.checkForOrderChanges(existingPool, pool));
    }

    @Test
    public void orderDataChangedAccountNumber() {
        Pool pool = new Pool();
        pool.setOrderNumber("123");
        pool.setAccountNumber("456A");
        pool.setContractNumber("789");
        Pool existingPool = new Pool();
        existingPool.setOrderNumber("123");
        existingPool.setAccountNumber("456");
        existingPool.setContractNumber("789");

        assertTrue(PoolHelper.checkForOrderChanges(existingPool, pool));
    }

    @Test
    public void orderDataChangedContractNumber() {
        Pool pool = new Pool();
        pool.setOrderNumber("123");
        pool.setAccountNumber("456");
        pool.setContractNumber("789A");
        Pool existingPool = new Pool();
        existingPool.setOrderNumber("123");
        existingPool.setAccountNumber("456");
        existingPool.setContractNumber("789");

        assertTrue(PoolHelper.checkForOrderChanges(existingPool, pool));
    }

    @Test
    public void orderDataChanged() {
        Pool pool = new Pool();
        pool.setOrderNumber("123");
        pool.setAccountNumber("456");
        pool.setContractNumber("789");
        Pool existingPool = new Pool();
        existingPool.setOrderNumber("123");
        existingPool.setAccountNumber("456");
        existingPool.setContractNumber("789");

        assertFalse(PoolHelper.checkForOrderChanges(existingPool, pool));
    }

    @Test
    public void usingPrimitiveEqualsOnStringIsBad() {
        Pool pool = new Pool();
        pool.setOrderNumber("123");
        pool.setAccountNumber("456");
        pool.setContractNumber("789");
        Pool existingPool = new Pool();
        existingPool.setOrderNumber(new String("123"));
        existingPool.setAccountNumber("456");
        existingPool.setContractNumber("789");

        assertFalse(PoolHelper.checkForOrderChanges(existingPool, pool));
    }

    @Test
    public void copyProductAttributesForHostRestrictedPools() {
        Product targetProduct = TestUtil.createProduct();
        Consumer cons = TestUtil.createConsumer();
        targetProduct.setAttribute("A1", "V1");
        targetProduct.setAttribute("A2", "V2");
        Pool targetPool = TestUtil.createPool(targetProduct);
        targetPool.setId("jso_speedwagon");
        targetPool.setAttribute(Product.Attributes.VIRT_LIMIT, "unlimited");

        Product targetProduct2 = TestUtil.createProduct();
        targetProduct2.setAttribute("B1", "V1");
        targetProduct2.setAttribute("B2", "V2");
        Pool targetPool2 = TestUtil.createPool(targetProduct2);
        targetPool2.setId("jso_speedwagon2");
        targetPool2.setAttribute(Product.Attributes.VIRT_LIMIT, "unlimited");

        // when(psa.getProductById(targetProduct.getUuid())).thenReturn(targetProduct);
        when(ent.getConsumer()).thenReturn(cons);

        List<Pool> targetPools = new ArrayList<>();
        targetPools.add(targetPool);
        targetPools.add(targetPool2);
        Map<String, Entitlement> entitlements = new HashMap<>();
        entitlements.put(targetPool.getId(), ent);
        entitlements.put(targetPool2.getId(), ent);

        Map<String, Map<String, String>> attributes = new HashMap<>();
        attributes.put(targetPool.getId(), PoolHelper.getFlattenedAttributes(targetPool));
        attributes.put(targetPool2.getId(), PoolHelper.getFlattenedAttributes(targetPool2));

        when(pm.createPools(anyListOf(Pool.class))).then(returnsFirstArg());
        PoolOperationCallback poolOperationCallback = PoolHelper.createHostRestrictedPools(pm,
            cons, targetPools, entitlements, attributes, productCurator);
        List<Pool> pools = poolOperationCallback.getPoolCreates();

        assertEquals(2, pools.size());
        Pool first = null, second = null;
        for (Pool pool : pools) {
            if (pool.getAttributeValue("source_pool_id").contentEquals("jso_speedwagon")) {
                first = pool;
            }
            else {
                second = pool;
            }
            assertEquals(2, pool.getProduct().getAttributes().size());
        }
        assertTrue(first.getProduct().hasAttribute("A1"));
        assertTrue(first.getProduct().hasAttribute("A1"));
        assertTrue(second.getProduct().hasAttribute("B1"));
        assertTrue(second.getProduct().hasAttribute("B1"));
        assertTrue(second.getAttributeValue("source_pool_id").contentEquals("jso_speedwagon2"));
    }

    @Test
    public void hostRestrictedPoolCreatedWithSubProductPoolData() {
        Consumer cons = TestUtil.createConsumer();

        // Create a product for the main pool to be sure that
        // the attributes do not get copied to the sub pool.
        Product mainPoolProduct = TestUtil.createProduct();
        mainPoolProduct.setAttribute("A1", "V1");
        mainPoolProduct.setAttribute("A2", "V2");

        Product derivedProduct1 = TestUtil.createProduct("sub-pp-1", "Sub Provided 1");
        Product derivedProduct2 = TestUtil.createProduct("sub-pp-2", "Sub Provided 2");

        Set<Product> derivedProducts = new HashSet<>();
        derivedProducts.add(derivedProduct1);
        derivedProducts.add(derivedProduct2);

        Product subProduct = TestUtil.createProduct();
        subProduct.setAttribute("SA1", "SV1");
        subProduct.setAttribute("SA2", "SV2");

        Pool targetPool = TestUtil.createPool(mainPoolProduct);
        targetPool.setId("sub-prod-pool");
        targetPool.setDerivedProduct(subProduct);

        targetPool.getDerivedProduct().setProvidedProducts(derivedProducts);
        when(productCurator.getPoolDerivedProvidedProductsCached(targetPool))
            .thenReturn(derivedProducts);
        targetPool.setAttribute(Product.Attributes.VIRT_LIMIT, "unlimited");
        // when(psa.getProductById(subProduct.getUuid())).thenReturn(subProduct);
        when(ent.getConsumer()).thenReturn(cons);

        List<Pool> targetPools = new ArrayList<>();
        targetPools.add(targetPool);

        Map<String, Entitlement> entitlements = new HashMap<>();
        entitlements.put(targetPool.getId(), ent);

        Map<String, Map<String, String>> attributes = new HashMap<>();
        attributes.put(targetPool.getId(), PoolHelper.getFlattenedAttributes(targetPool));
        when(pm.createPools(anyListOf(Pool.class))).then(returnsFirstArg());
        PoolOperationCallback poolOperationCallback = PoolHelper.createHostRestrictedPools(pm,
            cons, targetPools, entitlements, attributes, productCurator);

        List<Pool> hostRestrictedPools = poolOperationCallback.getPoolCreates();
        assertEquals(1, hostRestrictedPools.size());
        Pool hostRestrictedPool = hostRestrictedPools.get(0);
        assertEquals(targetPool.getId(), hostRestrictedPool.getAttributeValue("source_pool_id"));
        assertEquals(-1L, (long) hostRestrictedPool.getQuantity());
        assertEquals(2, hostRestrictedPool.getProduct().getAttributes().size());
        assertTrue(hostRestrictedPool.getProduct().hasAttribute("SA1"));
        assertEquals("SV1", hostRestrictedPool.getProduct().getAttributeValue("SA1"));
        assertTrue(hostRestrictedPool.getProduct().hasAttribute("SA2"));
        assertEquals("SV2", hostRestrictedPool.getProduct().getAttributeValue("SA2"));

        // Check that the sub provided products made it to the sub pool
        Collection<Product> providedProducts = hostRestrictedPool.getProduct().getProvidedProducts();

        assertEquals(2, providedProducts.size());
        assertTrue(providedProducts.contains(derivedProduct1));
        assertTrue(providedProducts.contains(derivedProduct2));
    }

    @Test
    public void clonePoolTest() {
        Product product = TestUtil.createProduct();
        Product product2 = TestUtil.createProduct();
        Map<String, String> attributes = new HashMap<>();
        for (int i = 0; i < 3; i++) {
            attributes.put("a" + i, "b" + i);
        }
        Branding branding = new Branding(null, "id", "name", "type");
        product2.setBranding(Arrays.asList(branding));
        Pool pool = TestUtil.createPool(owner, product);
        String quant = "unlimited";
        Pool clone = PoolHelper.clonePool(pool, product2, quant, attributes, "TaylorSwift", null,
            ent, TestUtil.createConsumer(), productCurator);
        assertEquals(owner, clone.getOwner());
        assertEquals(new Long(-1L), clone.getQuantity());
        assertEquals(product2, clone.getProduct());
        assertEquals(attributes.size() + 1, clone.getAttributes().size());
        for (int i = 0; i < 3; i++) {
            assertEquals("b" + i, clone.getAttributeValue("a" + i));
        }
        assertNotEquals(pool.getSourceSubscription(), clone);
        assertEquals(pool.getSourceSubscription().getSubscriptionId(), clone.getSubscriptionId());
        assertEquals(pool.getSourceSubscription().getSubscriptionId(),
            clone.getSourceSubscription().getSubscriptionId());
        assertEquals("TaylorSwift",
            clone.getSourceSubscription().getSubscriptionSubKey());

        assertEquals(1, clone.getProduct().getBranding().size());
        Branding brandingClone = clone.getProduct().getBranding().iterator().next();
        assertEquals(branding, brandingClone);
    }
}
