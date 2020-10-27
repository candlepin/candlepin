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
package org.candlepin.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * Test suite for the OwnerProductCurator class
 */
public class OwnerProductCuratorTest extends DatabaseTestFixture {

    /**
     * Injects a mapping from an owner to a product directly, avoiding the use of our curators.
     *
     * @param owner
     * @param product
     * @return a new OwnerProduct mapping object
     */
    private OwnerProduct createOwnerProductMapping(Owner owner, Product product) {
        OwnerProduct mapping = new OwnerProduct(owner, product);
        this.getEntityManager().persist(mapping);
        this.getEntityManager().flush();

        return mapping;
    }

    private boolean isProductMappedToOwner(Product product, Owner owner) {
        String jpql = "SELECT count(op) FROM OwnerProduct op " +
            "WHERE op.owner.id = :owner_id AND op.product.uuid = :product_uuid";

        long count = (Long) this.getEntityManager()
            .createQuery(jpql)
            .setParameter("owner_id", owner.getId())
            .setParameter("product_uuid", product.getUuid())
            .getSingleResult();

        return count > 0;
    }

    @Test
    public void testGetProductById() {
        Owner owner = this.createOwner();
        Product product = this.createProduct();
        this.createOwnerProductMapping(owner, product);

        Product resultA = this.ownerProductCurator.getProductById(owner, product.getId());
        assertEquals(resultA, product);

        Product resultB = this.ownerProductCurator.getProductById(owner.getId(), product.getId());
        assertEquals(resultB, product);

        assertSame(resultA, resultB);
    }

    @Test
    public void testGetProductByIdNoMapping() {
        Owner owner = this.createOwner();
        Product product = this.createProduct();

        Product resultA = this.ownerProductCurator.getProductById(owner, product.getId());
        assertNull(resultA);

        Product resultB = this.ownerProductCurator.getProductById(owner.getId(), product.getId());
        assertNull(resultB);
    }

    @Test
    public void testGetProductByIdWrongProductId() {
        Owner owner = this.createOwner();
        Product product1 = this.createProduct();
        Product product2 = this.createProduct();
        this.createOwnerProductMapping(owner, product1);

        Product resultA = this.ownerProductCurator.getProductById(owner, product2.getId());
        assertNull(resultA);

        Product resultB = this.ownerProductCurator.getProductById(owner.getId(), product2.getId());
        assertNull(resultB);
    }

    @Test
    public void testGetOwnersByProduct() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();
        Product product = this.createProduct();
        this.createOwnerProductMapping(owner1, product);
        this.createOwnerProductMapping(owner2, product);

        Collection<Owner> ownersA = this.ownerProductCurator.getOwnersByProduct(product).list();
        Collection<Owner> ownersB = this.ownerProductCurator.getOwnersByProduct(product.getId()).list();

        assertTrue(ownersA.contains(owner1));
        assertTrue(ownersA.contains(owner2));
        assertFalse(ownersA.contains(owner3));
        assertEquals(ownersA, ownersB);
    }

    @Test
    public void testGetOwnersByProductWithUnmappedProduct() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();
        Product product = this.createProduct();

        Collection<Owner> ownersA = this.ownerProductCurator.getOwnersByProduct(product).list();
        Collection<Owner> ownersB = this.ownerProductCurator.getOwnersByProduct(product.getId()).list();

        assertTrue(ownersA.isEmpty());
        assertTrue(ownersB.isEmpty());
    }

    @Test
    public void testGetProductsByOwner() {
        Owner owner = this.createOwner();
        Product product1 = this.createProduct();
        Product product2 = this.createProduct();
        Product product3 = this.createProduct();
        this.createOwnerProductMapping(owner, product1);
        this.createOwnerProductMapping(owner, product2);

        Collection<Product> productsA = this.ownerProductCurator.getProductsByOwner(owner).list();
        Collection<Product> productsB = this.ownerProductCurator.getProductsByOwner(owner.getId()).list();

        assertTrue(productsA.contains(product1));
        assertTrue(productsA.contains(product2));
        assertFalse(productsA.contains(product3));
        assertEquals(productsA, productsB);
    }

    @Test
    public void testGetProductsByOwnerWithUnmappedProduct() {
        Owner owner = this.createOwner();
        Product product1 = this.createProduct();
        Product product2 = this.createProduct();
        Product product3 = this.createProduct();

        Collection<Product> productsA = this.ownerProductCurator.getProductsByOwner(owner).list();
        Collection<Product> productsB = this.ownerProductCurator.getProductsByOwner(owner.getId()).list();

        assertTrue(productsA.isEmpty());
        assertTrue(productsB.isEmpty());
    }

    @Test
    public void testGetProductsByIds() {
        Owner owner = this.createOwner();
        Product product1 = this.createProduct();
        Product product2 = this.createProduct();
        Product product3 = this.createProduct();
        this.createOwnerProductMapping(owner, product1);
        this.createOwnerProductMapping(owner, product2);

        Collection<String> ids = Arrays.asList(product1.getId(), product2.getId(), product3.getId(), "dud");
        Collection<Product> productsA = this.ownerProductCurator.getProductsByIds(owner, ids).list();
        Collection<Product> productsB = this.ownerProductCurator.getProductsByIds(owner.getId(), ids).list();

        assertEquals(2, productsA.size());
        assertTrue(productsA.contains(product1));
        assertTrue(productsA.contains(product2));
        assertFalse(productsA.contains(product3));
        assertEquals(productsA, productsB);
    }

    @Test
    public void testGetProductsByIdsNullList() {
        Owner owner = this.createOwner();
        Product product1 = this.createProduct();
        Product product2 = this.createProduct();
        Product product3 = this.createProduct();
        this.createOwnerProductMapping(owner, product1);
        this.createOwnerProductMapping(owner, product2);

        Collection<String> ids = null;
        Collection<Product> productsA = this.ownerProductCurator.getProductsByIds(owner, ids).list();
        Collection<Product> productsB = this.ownerProductCurator.getProductsByIds(owner.getId(), ids).list();

        assertTrue(productsA.isEmpty());
        assertTrue(productsB.isEmpty());
    }

    @Test
    public void testGetProductsByIdsEmptyList() {
        Owner owner = this.createOwner();
        Product product1 = this.createProduct();
        Product product2 = this.createProduct();
        Product product3 = this.createProduct();
        this.createOwnerProductMapping(owner, product1);
        this.createOwnerProductMapping(owner, product2);

        Collection<String> ids = Collections.emptyList();
        Collection<Product> productsA = this.ownerProductCurator.getProductsByIds(owner, ids).list();
        Collection<Product> productsB = this.ownerProductCurator.getProductsByIds(owner.getId(), ids).list();

        assertTrue(productsA.isEmpty());
        assertTrue(productsB.isEmpty());
    }

    @Test
    public void testGetOwnerCount() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();
        Product product = this.createProduct();

        assertEquals(0L, this.ownerProductCurator.getOwnerCount(product));

        this.createOwnerProductMapping(owner1, product);
        assertEquals(1L, this.ownerProductCurator.getOwnerCount(product));

        this.createOwnerProductMapping(owner2, product);
        assertEquals(2L, this.ownerProductCurator.getOwnerCount(product));
    }

    @Test
    public void testIsProductMappedToOwner() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();
        Product product = this.createProduct();

        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product, owner1));
        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product, owner2));
        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product, owner3));

        this.createOwnerProductMapping(owner1, product);

        assertTrue(this.ownerProductCurator.isProductMappedToOwner(product, owner1));
        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product, owner2));
        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product, owner3));

        this.createOwnerProductMapping(owner2, product);

        assertTrue(this.ownerProductCurator.isProductMappedToOwner(product, owner1));
        assertTrue(this.ownerProductCurator.isProductMappedToOwner(product, owner2));
        assertFalse(this.ownerProductCurator.isProductMappedToOwner(product, owner3));
    }

    @Test
    public void testMapProductToOwner() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();
        Product product1 = this.createProduct();
        Product product2 = this.createProduct();
        Product product3 = this.createProduct();

        List<Owner> owners = Arrays.asList(owner1, owner2, owner3);
        List<Product> products = Arrays.asList(product1, product2, product3);

        int mapped = 0;
        for (int i = 0; i < owners.size(); ++i) {
            for (int j = 0; j < products.size(); ++j) {
                int offset = 0;

                for (Owner owner : owners) {
                    for (Product product : products) {
                        if (mapped > offset++) {
                            assertTrue(this.isProductMappedToOwner(product, owner));
                        }
                        else {
                            assertFalse(this.isProductMappedToOwner(product, owner));
                        }
                    }
                }

                boolean result = this.ownerProductCurator.mapProductToOwner(products.get(j), owners.get(i));
                assertTrue(result);

                result = this.ownerProductCurator.mapProductToOwner(products.get(j), owners.get(i));
                assertFalse(result);

                ++mapped;
            }
        }
    }

    @Test
    public void testMapProductToOwnerUnmappedOwner() {
        Owner owner = new Owner("unmapped");
        Product product = this.createProduct();

        assertThrows(IllegalStateException.class, () ->
            this.ownerProductCurator.mapProductToOwner(product, owner)
        );
    }

    @Test
    public void testMapProductToOwnerUnmappedProduct() {
        Owner owner = this.createOwner();
        Product product = TestUtil.createProduct();

        assertThrows(IllegalStateException.class, () ->
            this.ownerProductCurator.mapProductToOwner(product, owner)
        );
    }

    @Test
    public void testMapProductToOwners() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();
        Product product1 = this.createProduct();
        Product product2 = this.createProduct();
        Product product3 = this.createProduct();

        assertFalse(this.isProductMappedToOwner(product1, owner1));
        assertFalse(this.isProductMappedToOwner(product2, owner1));
        assertFalse(this.isProductMappedToOwner(product3, owner1));
        assertFalse(this.isProductMappedToOwner(product1, owner2));
        assertFalse(this.isProductMappedToOwner(product2, owner2));
        assertFalse(this.isProductMappedToOwner(product3, owner2));
        assertFalse(this.isProductMappedToOwner(product1, owner3));
        assertFalse(this.isProductMappedToOwner(product2, owner3));
        assertFalse(this.isProductMappedToOwner(product3, owner3));

        this.ownerProductCurator.mapProductToOwners(product1, owner1, owner2);

        assertTrue(this.isProductMappedToOwner(product1, owner1));
        assertFalse(this.isProductMappedToOwner(product2, owner1));
        assertFalse(this.isProductMappedToOwner(product3, owner1));
        assertTrue(this.isProductMappedToOwner(product1, owner2));
        assertFalse(this.isProductMappedToOwner(product2, owner2));
        assertFalse(this.isProductMappedToOwner(product3, owner2));
        assertFalse(this.isProductMappedToOwner(product1, owner3));
        assertFalse(this.isProductMappedToOwner(product2, owner3));
        assertFalse(this.isProductMappedToOwner(product3, owner3));
    }

    @Test
    public void testMapOwnerToProducts() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();
        Product product1 = this.createProduct();
        Product product2 = this.createProduct();
        Product product3 = this.createProduct();

        assertFalse(this.isProductMappedToOwner(product1, owner1));
        assertFalse(this.isProductMappedToOwner(product2, owner1));
        assertFalse(this.isProductMappedToOwner(product3, owner1));
        assertFalse(this.isProductMappedToOwner(product1, owner2));
        assertFalse(this.isProductMappedToOwner(product2, owner2));
        assertFalse(this.isProductMappedToOwner(product3, owner2));
        assertFalse(this.isProductMappedToOwner(product1, owner3));
        assertFalse(this.isProductMappedToOwner(product2, owner3));
        assertFalse(this.isProductMappedToOwner(product3, owner3));

        this.ownerProductCurator.mapOwnerToProducts(owner1, product1, product2);

        assertTrue(this.isProductMappedToOwner(product1, owner1));
        assertTrue(this.isProductMappedToOwner(product2, owner1));
        assertFalse(this.isProductMappedToOwner(product3, owner1));
        assertFalse(this.isProductMappedToOwner(product1, owner2));
        assertFalse(this.isProductMappedToOwner(product2, owner2));
        assertFalse(this.isProductMappedToOwner(product3, owner2));
        assertFalse(this.isProductMappedToOwner(product1, owner3));
        assertFalse(this.isProductMappedToOwner(product2, owner3));
        assertFalse(this.isProductMappedToOwner(product3, owner3));
    }

    @Test
    public void testRemoveProductFromOwner() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();
        Product product1 = this.createProduct();
        Product product2 = this.createProduct();
        Product product3 = this.createProduct();

        List<Owner> owners = Arrays.asList(owner1, owner2, owner3);
        List<Product> products = Arrays.asList(product1, product2, product3);

        this.createOwnerProductMapping(owner1, product1);
        this.createOwnerProductMapping(owner1, product2);
        this.createOwnerProductMapping(owner1, product3);
        this.createOwnerProductMapping(owner2, product1);
        this.createOwnerProductMapping(owner2, product2);
        this.createOwnerProductMapping(owner2, product3);
        this.createOwnerProductMapping(owner3, product1);
        this.createOwnerProductMapping(owner3, product2);
        this.createOwnerProductMapping(owner3, product3);

        int removed = 0;
        for (int i = 0; i < owners.size(); ++i) {
            for (int j = 0; j < products.size(); ++j) {
                int offset = 0;

                for (Owner owner : owners) {
                    for (Product product : products) {
                        if (removed > offset++) {
                            assertFalse(this.isProductMappedToOwner(product, owner));
                        }
                        else {
                            assertTrue(this.isProductMappedToOwner(product, owner));
                        }
                    }
                }

                boolean result = this.ownerProductCurator.removeOwnerFromProduct(
                    products.get(j), owners.get(i)
                );

                assertTrue(result);

                result = this.ownerProductCurator.removeOwnerFromProduct(
                    products.get(j), owners.get(i)
                );

                assertFalse(result);


                ++removed;
            }
        }
    }

    @Test
    public void testClearOwnersForProduct() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();
        Product product1 = this.createProduct();
        Product product2 = this.createProduct();
        Product product3 = this.createProduct();

        this.createOwnerProductMapping(owner1, product1);
        this.createOwnerProductMapping(owner1, product2);
        this.createOwnerProductMapping(owner1, product3);
        this.createOwnerProductMapping(owner2, product1);
        this.createOwnerProductMapping(owner2, product2);
        this.createOwnerProductMapping(owner2, product3);
        this.createOwnerProductMapping(owner3, product1);
        this.createOwnerProductMapping(owner3, product2);
        this.createOwnerProductMapping(owner3, product3);

        this.ownerProductCurator.clearOwnersForProduct(product1);

        assertFalse(this.isProductMappedToOwner(product1, owner1));
        assertTrue(this.isProductMappedToOwner(product2, owner1));
        assertTrue(this.isProductMappedToOwner(product3, owner1));
        assertFalse(this.isProductMappedToOwner(product1, owner2));
        assertTrue(this.isProductMappedToOwner(product2, owner2));
        assertTrue(this.isProductMappedToOwner(product3, owner2));
        assertFalse(this.isProductMappedToOwner(product1, owner3));
        assertTrue(this.isProductMappedToOwner(product2, owner3));
        assertTrue(this.isProductMappedToOwner(product3, owner3));
    }

    @Test
    public void testClearProductsForOwner() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();
        Product product1 = this.createProduct();
        Product product2 = this.createProduct();
        Product product3 = this.createProduct();

        this.createOwnerProductMapping(owner1, product1);
        this.createOwnerProductMapping(owner1, product2);
        this.createOwnerProductMapping(owner1, product3);
        this.createOwnerProductMapping(owner2, product1);
        this.createOwnerProductMapping(owner2, product2);
        this.createOwnerProductMapping(owner2, product3);
        this.createOwnerProductMapping(owner3, product1);
        this.createOwnerProductMapping(owner3, product2);
        this.createOwnerProductMapping(owner3, product3);

        this.ownerProductCurator.clearProductsForOwner(owner1);

        assertFalse(this.isProductMappedToOwner(product1, owner1));
        assertFalse(this.isProductMappedToOwner(product2, owner1));
        assertFalse(this.isProductMappedToOwner(product3, owner1));
        assertTrue(this.isProductMappedToOwner(product1, owner2));
        assertTrue(this.isProductMappedToOwner(product2, owner2));
        assertTrue(this.isProductMappedToOwner(product3, owner2));
        assertTrue(this.isProductMappedToOwner(product1, owner3));
        assertTrue(this.isProductMappedToOwner(product2, owner3));
        assertTrue(this.isProductMappedToOwner(product3, owner3));
    }

    @Test
    public void testUpdateOwnerProductReferences() {
        Owner owner = this.createOwner();
        Product original = this.createProduct();
        Product updated = this.createProduct();
        Product untouched = this.createProduct();
        this.createOwnerProductMapping(owner, original);
        this.createOwnerProductMapping(owner, untouched);

        Pool pool = TestUtil.createPool(owner, original);
        this.poolCurator.create(pool);

        ActivationKey key = TestUtil.createActivationKey(owner, null);
        key.setProducts(Util.asSet(original));

        this.activationKeyCurator.create(key);

        assertTrue(this.isProductMappedToOwner(original, owner));
        assertFalse(this.isProductMappedToOwner(updated, owner));
        assertTrue(this.isProductMappedToOwner(untouched, owner));

        Map<String, String> uuidMap = new HashMap<>();
        uuidMap.put(original.getUuid(), updated.getUuid());

        this.ownerProductCurator.updateOwnerProductReferences(owner, uuidMap);

        assertFalse(this.isProductMappedToOwner(original, owner));
        assertTrue(this.isProductMappedToOwner(updated, owner));
        assertTrue(this.isProductMappedToOwner(untouched, owner));

        this.activationKeyCurator.refresh(key);
        Collection<Product> products = key.getProducts();
        assertEquals(1, products.size());
        assertEquals(updated.getUuid(), products.iterator().next().getUuid());

        this.poolCurator.refresh(pool);
        assertEquals(updated.getUuid(), pool.getProduct().getUuid());
    }

    @Test
    public void testRemoveOwnerProductReferences() {
        Owner owner = this.createOwner();
        Product original = this.createProduct();
        this.createOwnerProductMapping(owner, original);

        ActivationKey key = TestUtil.createActivationKey(owner, null);
        key.setProducts(Util.asSet(original));

        this.activationKeyCurator.create(key);

        assertTrue(this.isProductMappedToOwner(original, owner));

        this.ownerProductCurator.removeOwnerProductReferences(owner, Arrays.asList(original.getUuid()));

        assertFalse(this.isProductMappedToOwner(original, owner));

        this.activationKeyCurator.refresh(key);
        Collection<Product> products = key.getProducts();
        assertEquals(0, products.size());
    }

    @Test
    public void testGetProductsByVersions() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();

        Product product1 = this.createProduct("p1", "p1", owner1);
        Product product2 = this.createProduct("p1", "p1", owner2);
        Product product3 = this.createProduct("p1", "p1", owner3);
        Product product4 = this.createProduct("p2", "p2", owner2);

        Map<String, List<Product>> productMap1 = this.ownerProductCurator.getProductsByVersions(owner1,
            Collections.singletonMap(product1.getId(), product1.getEntityVersion()));
        Map<String, List<Product>> productMap2 = this.ownerProductCurator.getProductsByVersions(owner2,
            Collections.singletonMap(product2.getId(), product2.getEntityVersion()));

        // productMap1 should contain only product2 and product3
        // productMap2 should contain only product1 and product3

        assertEquals(1, productMap1.size());
        assertNotNull(productMap1.get("p1"));
        assertEquals(1, productMap2.size());
        assertNotNull(productMap2.get("p1"));

        List<String> uuidList1 = productMap1.values()
            .stream()
            .flatMap(List::stream)
            .map(Product::getUuid)
            .collect(Collectors.toList());

        List<String> uuidList2 = productMap2.values()
            .stream()
            .flatMap(List::stream)
            .map(Product::getUuid)
            .collect(Collectors.toList());

        assertEquals(Arrays.asList(product2.getUuid(), product3.getUuid()), uuidList1);
        assertEquals(Arrays.asList(product1.getUuid(), product3.getUuid()), uuidList2);
    }

    @Test
    public void testGetProductsByVersionsNoOwner() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();

        Product product1 = this.createProduct("p1", "p1", owner1);
        Product product2 = this.createProduct("p1", "p1", owner2);
        Product product3 = this.createProduct("p1", "p1", owner3);
        Product product4 = this.createProduct("p2", "p2", owner2);

        Map<String, List<Product>> productMap1 = this.ownerProductCurator.getProductsByVersions(null,
            Collections.singletonMap(product1.getId(), product1.getEntityVersion()));
        Map<String, List<Product>> productMap2 = this.ownerProductCurator.getProductsByVersions(null,
            Collections.singletonMap(product2.getId(), product2.getEntityVersion()));

        // Both maps should contain both products 1, 2 and 3

        assertEquals(1, productMap1.size());
        assertNotNull(productMap1.get("p1"));
        assertEquals(1, productMap2.size());
        assertNotNull(productMap2.get("p1"));

        List<String> uuidList1 = productMap1.values()
            .stream()
            .flatMap(List::stream)
            .map(Product::getUuid)
            .collect(Collectors.toList());

        List<String> uuidList2 = productMap2.values()
            .stream()
            .flatMap(List::stream)
            .map(Product::getUuid)
            .collect(Collectors.toList());

        // We're counting on .equals not caring about order here
        assertEquals(Arrays.asList(product1.getUuid(), product2.getUuid(), product3.getUuid()), uuidList1);
        assertEquals(Arrays.asList(product1.getUuid(), product2.getUuid(), product3.getUuid()), uuidList2);
    }

    @Test
    public void testGetProductsByVersionsMultipleVersions() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();

        Product p1 = this.createProduct("p1", "p1", owner1);
        Product p2 = this.createProduct("p1", "p1", owner2);
        Product p3 = this.createProduct("p1", "p1", owner3);
        Product p4 = this.createProduct("p2", "p2", owner1);
        Product p5 = this.createProduct("p2", "p2", owner2);
        Product p6 = this.createProduct("p2", "p2", owner3);
        Product p7 = this.createProduct("p3", "p3", owner1);
        Product p8 = this.createProduct("p3", "p3", owner2);
        Product p9 = this.createProduct("p3", "p3", owner3);

        Map<String, Integer> versions = new HashMap<>();
        versions.put(p1.getId(), p1.getEntityVersion());
        versions.put(p4.getId(), p4.getEntityVersion());
        versions.put("bad_id", p7.getEntityVersion());

        Map<String, List<Product>> productMap1 = this.ownerProductCurator
            .getProductsByVersions(owner1, versions);
        Map<String, List<Product>> productMap2 = this.ownerProductCurator
            .getProductsByVersions(owner2, versions);
        Map<String, List<Product>> productMap3 = this.ownerProductCurator
            .getProductsByVersions(null, versions);

        // Map 1 should contain products with ids "p1" or "p2" not owned by owner 1: (p2, p3, p5, p6)
        // Map 2 should contain products with ids "p1" or "p2" not owned by owner 2: (p1, p3, p4, p6)
        // Map 3 should contain all products with ids "p1" or "p2": (p1, p2, p3, p4, p5, p6)

        assertEquals(2, productMap1.size());
        assertEquals(2, productMap2.size());
        assertEquals(2, productMap3.size());

        assertNotNull(productMap1.get("p1"));
        assertEquals(2, productMap1.get("p1").size());
        assertNotNull(productMap1.get("p2"));
        assertEquals(2, productMap1.get("p2").size());

        assertNotNull(productMap2.get("p1"));
        assertEquals(2, productMap2.get("p1").size());
        assertNotNull(productMap2.get("p2"));
        assertEquals(2, productMap2.get("p2").size());

        assertNotNull(productMap3.get("p1"));
        assertEquals(3, productMap3.get("p1").size());
        assertNotNull(productMap3.get("p2"));
        assertEquals(3, productMap3.get("p2").size());

        List<String> uuidList1 = productMap1.values()
            .stream()
            .flatMap(List::stream)
            .map(Product::getUuid)
            .collect(Collectors.toList());

        List<String> uuidList2 = productMap2.values()
            .stream()
            .flatMap(List::stream)
            .map(Product::getUuid)
            .collect(Collectors.toList());

        List<String> uuidList3 = productMap3.values()
            .stream()
            .flatMap(List::stream)
            .map(Product::getUuid)
            .collect(Collectors.toList());

        // We're counting on .equals not caring about order here
        assertEquals(Arrays.asList(p2.getUuid(), p3.getUuid(), p5.getUuid(), p6.getUuid()), uuidList1);
        assertEquals(Arrays.asList(p1.getUuid(), p3.getUuid(), p4.getUuid(), p6.getUuid()), uuidList2);
        assertEquals(
            Arrays.asList(p1.getUuid(), p2.getUuid(), p3.getUuid(), p4.getUuid(), p5.getUuid(), p6.getUuid()),
            uuidList3
        );
    }

    @Test
    public void testGetProductsByVersionsNoVersionInfo() {
        Owner owner1 = this.createOwner();

        Map<String, List<Product>> productMap1 = this.ownerProductCurator
            .getProductsByVersions(owner1, null);
        assertEquals(0, productMap1.size());

        Map<String, List<Product>> productMap2 = this.ownerProductCurator
            .getProductsByVersions(owner1, Collections.emptyMap());
        assertEquals(0, productMap2.size());

        Map<String, List<Product>> productMap3 = this.ownerProductCurator.getProductsByVersions(null, null);
        assertEquals(0, productMap3.size());

        Map<String, List<Product>> productMap4 = this.ownerProductCurator
            .getProductsByVersions(null, Collections.emptyMap());
        assertEquals(0, productMap4.size());
    }

    @Test
    public void testGetProductsByVersionsDoesntFailWithLargeDataSets() {
        Owner owner = this.createOwner();

        int versionCount = 10000;

        Map<String, Integer> versionMap = new HashMap<>();
        for (int i = 0; i < versionCount; ++i) {
            versionMap.put("entity-" + i, i);
        }

        this.ownerProductCurator.getProductsByVersions(owner, versionMap);
    }
}
