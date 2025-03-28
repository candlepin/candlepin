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
package org.candlepin.policy.js.pool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.bind.PoolOperations;
import org.candlepin.controller.PoolService;
import org.candlepin.model.Branding;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



@ExtendWith(MockitoExtension.class)
public class PoolHelperTest {

    @Mock
    private PoolService poolService;
    @Mock
    private Entitlement ent;
    private Owner owner;

    @BeforeEach
    public void init() {
        owner = TestUtil.createOwner();
    }

    @Test
    public void orderDataChangedOrderNumber() {
        Pool pool = new Pool()
            .setOrderNumber("123A")
            .setAccountNumber("456")
            .setContractNumber("789");

        Pool existingPool = new Pool()
            .setOrderNumber("123")
            .setAccountNumber("456")
            .setContractNumber("789");

        assertTrue(PoolHelper.checkForOrderChanges(existingPool, pool));
    }

    @Test
    public void orderDataChangedAccountNumber() {
        Pool pool = new Pool()
            .setOrderNumber("123")
            .setAccountNumber("456A")
            .setContractNumber("789");

        Pool existingPool = new Pool()
            .setOrderNumber("123")
            .setAccountNumber("456")
            .setContractNumber("789");

        assertTrue(PoolHelper.checkForOrderChanges(existingPool, pool));
    }

    @Test
    public void orderDataChangedContractNumber() {
        Pool pool = new Pool()
            .setOrderNumber("123")
            .setAccountNumber("456")
            .setContractNumber("789A");

        Pool existingPool = new Pool()
            .setOrderNumber("123")
            .setAccountNumber("456")
            .setContractNumber("789");

        assertTrue(PoolHelper.checkForOrderChanges(existingPool, pool));
    }

    @Test
    public void orderDataChanged() {
        Pool pool = new Pool()
            .setOrderNumber("123")
            .setAccountNumber("456")
            .setContractNumber("789");

        Pool existingPool = new Pool()
            .setOrderNumber("123")
            .setAccountNumber("456")
            .setContractNumber("789");

        assertFalse(PoolHelper.checkForOrderChanges(existingPool, pool));
    }

    @Test
    public void copyProductAttributesForHostRestrictedPools() {
        Product targetProduct = TestUtil.createProduct();
        Consumer cons = TestUtil.createConsumer();
        targetProduct.setAttribute("A1", "V1");
        targetProduct.setAttribute("A2", "V2");

        Pool targetPool = TestUtil.createPool(targetProduct)
            .setId("jso_speedwagon")
            .setAttribute(Product.Attributes.VIRT_LIMIT, "unlimited");

        Product targetProduct2 = TestUtil.createProduct();
        targetProduct2.setAttribute("B1", "V1");
        targetProduct2.setAttribute("B2", "V2");

        Pool targetPool2 = TestUtil.createPool(targetProduct2)
            .setId("jso_speedwagon2")
            .setAttribute(Product.Attributes.VIRT_LIMIT, "unlimited");

        List<Pool> targetPools = new ArrayList<>();
        targetPools.add(targetPool);
        targetPools.add(targetPool2);
        Map<String, Entitlement> entitlements = new HashMap<>();
        entitlements.put(targetPool.getId(), ent);
        entitlements.put(targetPool2.getId(), ent);

        Map<String, Map<String, String>> attributes = new HashMap<>();
        attributes.put(targetPool.getId(), PoolHelper.getFlattenedAttributes(targetPool));
        attributes.put(targetPool2.getId(), PoolHelper.getFlattenedAttributes(targetPool2));

        PoolOperations poolOperations = PoolHelper.createHostRestrictedPools(poolService,
            cons, targetPools, entitlements, attributes);
        List<Pool> pools = poolOperations.creations();

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
        Consumer consumer = TestUtil.createConsumer();

        // Create a product for the main pool to be sure that
        // the attributes do not get copied to the sub pool.
        Product derivedProvidedProduct1 = TestUtil.createProduct("sub-pp-1", "Sub Provided 1");
        Product derivedProvidedProduct2 = TestUtil.createProduct("sub-pp-2", "Sub Provided 2");

        Product subProduct = TestUtil.createProduct()
            .setAttribute("SA1", "SV1")
            .setAttribute("SA2", "SV2");
        subProduct.addProvidedProduct(derivedProvidedProduct1);
        subProduct.addProvidedProduct(derivedProvidedProduct2);

        Product mainPoolProduct = TestUtil.createProduct()
            .setAttribute("A1", "V1")
            .setAttribute("A2", "V2")
            .setDerivedProduct(subProduct);

        Pool targetPool = TestUtil.createPool(mainPoolProduct)
            .setId("sub-prod-pool")
            .setAttribute(Product.Attributes.VIRT_LIMIT, "unlimited");

        List<Pool> targetPools = new ArrayList<>();
        targetPools.add(targetPool);

        Map<String, Entitlement> entitlements = new HashMap<>();
        entitlements.put(targetPool.getId(), ent);

        Map<String, Map<String, String>> attributes = new HashMap<>();
        attributes.put(targetPool.getId(), PoolHelper.getFlattenedAttributes(targetPool));

        PoolOperations poolOperations = PoolHelper.createHostRestrictedPools(poolService,
            consumer, targetPools, entitlements, attributes);

        List<Pool> hostRestrictedPools = poolOperations.creations();
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
        assertTrue(providedProducts.contains(derivedProvidedProduct1));
        assertTrue(providedProducts.contains(derivedProvidedProduct2));
    }

    @Test
    public void clonePoolTest() {
        Branding branding = new Branding("id", "name", "type");

        Product product = TestUtil.createProduct();
        Product product2 = TestUtil.createProduct()
            .addBranding(branding);

        Map<String, String> attributes = new HashMap<>();
        for (int i = 0; i < 3; i++) {
            attributes.put("a" + i, "b" + i);
        }

        Pool pool = TestUtil.createPool(owner, product);
        String quant = "unlimited";
        Pool clone = PoolHelper.clonePool(pool, product2, quant, attributes, "TaylorSwift",
            ent, TestUtil.createConsumer());
        assertEquals(owner, clone.getOwner());
        assertEquals(-1L, clone.getQuantity());
        assertEquals(product2, clone.getProduct());
        assertEquals(attributes.size() + 1, clone.getAttributes().size());
        for (int i = 0; i < 3; i++) {
            assertEquals("b" + i, clone.getAttributeValue("a" + i));
        }
        assertNotEquals(pool.getSourceSubscription(), clone.getSourceSubscription());
        assertEquals(pool.getSourceSubscription().getSubscriptionId(), clone.getSubscriptionId());
        assertEquals(pool.getSourceSubscription().getSubscriptionId(),
            clone.getSourceSubscription().getSubscriptionId());
        assertEquals("TaylorSwift", clone.getSourceSubscription().getSubscriptionSubKey());

        assertEquals(1, clone.getProduct().getBranding().size());
        Branding brandingClone = clone.getProduct().getBranding().iterator().next();
        assertEquals(branding, brandingClone);
    }
}
