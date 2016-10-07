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

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

import org.candlepin.model.activationkeys.ActivationKey;

import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



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
        this.beginTransaction();

        OwnerProduct mapping = new OwnerProduct(owner, product);
        this.entityManager().persist(mapping);
        this.entityManager().flush();

        this.commitTransaction();

        return mapping;
    }

    private boolean isProductMappedToOwner(Product product, Owner owner) {
        String jpql = "SELECT count(op) FROM OwnerProduct op " +
            "WHERE op.owner.id = :owner_id AND op.product.uuid = :product_uuid";

        long count = (Long) this.entityManager()
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

        Collection<Owner> ownersA = this.ownerProductCurator.getOwnersByProduct(product);
        Collection<Owner> ownersB = this.ownerProductCurator.getOwnersByProduct(product.getId());

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

        Collection<Owner> ownersA = this.ownerProductCurator.getOwnersByProduct(product);
        Collection<Owner> ownersB = this.ownerProductCurator.getOwnersByProduct(product.getId());

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

        Collection<Product> productsA = this.ownerProductCurator.getProductsByOwner(owner);
        Collection<Product> productsB = this.ownerProductCurator.getProductsByOwner(owner.getId());

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

        Collection<Product> productsA = this.ownerProductCurator.getProductsByOwner(owner);
        Collection<Product> productsB = this.ownerProductCurator.getProductsByOwner(owner.getId());

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
        Collection<Product> productsA = this.ownerProductCurator.getProductsByIds(owner, ids);
        Collection<Product> productsB = this.ownerProductCurator.getProductsByIds(owner.getId(), ids);

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
        Collection<Product> productsA = this.ownerProductCurator.getProductsByIds(owner, ids);
        Collection<Product> productsB = this.ownerProductCurator.getProductsByIds(owner.getId(), ids);

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

        Collection<String> ids = Collections.<String>emptyList();
        Collection<Product> productsA = this.ownerProductCurator.getProductsByIds(owner, ids);
        Collection<Product> productsB = this.ownerProductCurator.getProductsByIds(owner.getId(), ids);

        assertTrue(productsA.isEmpty());
        assertTrue(productsB.isEmpty());
    }

    @Test
    public void testGetOwnerCount() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();
        Product product = this.createProduct();

        assertEquals(0L, (long) this.ownerProductCurator.getOwnerCount(product));

        this.createOwnerProductMapping(owner1, product);
        assertEquals(1L, (long) this.ownerProductCurator.getOwnerCount(product));

        this.createOwnerProductMapping(owner2, product);
        assertEquals(2L, (long) this.ownerProductCurator.getOwnerCount(product));
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

    @Test(expected = IllegalStateException.class)
    public void testMapProductToOwnerUnmappedOwner() {
        Owner owner = TestUtil.createOwner();
        Product product = this.createProduct();

        this.ownerProductCurator.mapProductToOwner(product, owner);
    }

    @Test(expected = IllegalStateException.class)
    public void testMapProductToOwnerUnmappedProduct() {
        Owner owner = this.createOwner();
        Product product = TestUtil.createProduct();

        this.ownerProductCurator.mapProductToOwner(product, owner);
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
        this.createOwnerProductMapping(owner, original);

        ActivationKey key = TestUtil.createActivationKey(owner, null);
        key.setProducts(Util.asSet(original));

        Pool pool1 = TestUtil.createPool(owner, original);
        Pool pool2 = TestUtil.createPool(owner);
        pool2.addProvidedProduct(original);
        Pool pool3 = TestUtil.createPool(owner);
        pool3.setDerivedProduct(original);
        Pool pool4 = TestUtil.createPool(owner);
        pool4.setDerivedProvidedProducts(Arrays.asList(original));

        this.activationKeyCurator.create(key);
        this.poolCurator.create(pool1);
        this.productCurator.create(pool2.getProduct());
        this.poolCurator.create(pool2);
        this.productCurator.create(pool3.getProduct());
        this.poolCurator.create(pool3);
        this.productCurator.create(pool4.getProduct());
        this.poolCurator.create(pool4);

        assertTrue(this.isProductMappedToOwner(original, owner));
        assertFalse(this.isProductMappedToOwner(updated, owner));

        Map<String, String> uuidMap = new HashMap<String, String>();
        uuidMap.put(original.getUuid(), updated.getUuid());

        this.ownerProductCurator.updateOwnerProductReferences(owner, uuidMap);

        assertFalse(this.isProductMappedToOwner(original, owner));
        assertTrue(this.isProductMappedToOwner(updated, owner));

        this.activationKeyCurator.refresh(key);
        Collection<Product> products = key.getProducts();
        assertEquals(1, products.size());
        assertEquals(updated.getUuid(), products.iterator().next().getUuid());

        this.poolCurator.refresh(pool1);
        assertEquals(updated.getUuid(), pool1.getProduct().getUuid());

        this.poolCurator.refresh(pool2);
        assertNotEquals(updated.getUuid(), pool2.getProduct().getUuid());
        products = pool2.getProvidedProducts();
        assertEquals(1, products.size());
        assertEquals(updated.getUuid(), products.iterator().next().getUuid());

        this.poolCurator.refresh(pool3);
        assertNotEquals(updated.getUuid(), pool3.getProduct().getUuid());
        assertEquals(updated.getUuid(), pool3.getDerivedProduct().getUuid());

        this.poolCurator.refresh(pool4);
        assertNotEquals(updated.getUuid(), pool4.getProduct().getUuid());
        products = pool4.getDerivedProvidedProducts();
        assertEquals(1, products.size());
        assertEquals(updated.getUuid(), products.iterator().next().getUuid());
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

        this.ownerProductCurator.removeOwnerProductReferences(original, owner);

        assertFalse(this.isProductMappedToOwner(original, owner));

        this.activationKeyCurator.refresh(key);
        Collection<Product> products = key.getProducts();
        assertEquals(0, products.size());
    }

}
