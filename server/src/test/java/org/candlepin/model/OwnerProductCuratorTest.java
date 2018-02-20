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

import org.candlepin.model.activationkeys.ActivationKey;

import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
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

        Product resultA = this.productShareManager.resolveProductById(owner, product.getId(), false);
        assertEquals(resultA, product);

        Product resultB = this.productShareManager.resolveProductById(owner, product.getId(), true);
        assertEquals(resultB, product);

        assertSame(resultA, resultB);
    }

    @Test
    public void testGetProductByIdNoMapping() {
        Owner owner = this.createOwner();
        Product product = this.createProduct();

        Product resultA = this.productShareManager.resolveProductById(owner, product.getId(), true);
        assertNull(resultA);

        Product resultB = this.productShareManager.resolveProductById(owner, product.getId(), true);
        assertNull(resultB);
    }

    @Test
    public void testGetProductByIdWrongProductId() {
        Owner owner = this.createOwner();
        Product product1 = this.createProduct();
        Product product2 = this.createProduct();
        this.createOwnerProductMapping(owner, product1);

        Product resultA = this.productShareManager.resolveProductById(owner, product2.getId(), true);
        assertNull(resultA);

        Product resultB = this.productShareManager.resolveProductById(owner, product2.getId(), true);
        assertNull(resultB);
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

        Collection<String> ids = Collections.<String>emptyList();
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

        List<Product> productList1 = this.ownerProductCurator.getProductsByVersions(owner1,
            Collections.<String, Integer>singletonMap(product1.getId(), product1.getEntityVersion())).list();

        List<Product> productList2 = this.ownerProductCurator.getProductsByVersions(owner2,
            Collections.<String, Integer>singletonMap(product2.getId(), product2.getEntityVersion())).list();

        // productList1 should contain only product2 and product3
        // productList2 should contain only product1 and product3

        assertEquals(2, productList1.size());
        assertEquals(2, productList2.size());

        List<String> uuidList1 = new LinkedList<String>();
        for (Product product : productList1) {
            uuidList1.add(product.getUuid());
        }

        List<String> uuidList2 = new LinkedList<String>();
        for (Product product : productList2) {
            uuidList2.add(product.getUuid());
        }

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

        List<Product> productList1 = this.ownerProductCurator.getProductsByVersions(null,
            Collections.<String, Integer>singletonMap(product1.getId(), product1.getEntityVersion())).list();

        List<Product> productList2 = this.ownerProductCurator.getProductsByVersions(null,
            Collections.<String, Integer>singletonMap(product2.getId(), product2.getEntityVersion())).list();

        // Both lists should contain both products1, 2 and 3

        assertEquals(3, productList1.size());
        assertEquals(3, productList2.size());

        List<String> uuidList1 = new LinkedList<String>();
        for (Product product : productList1) {
            uuidList1.add(product.getUuid());
        }

        List<String> uuidList2 = new LinkedList<String>();
        for (Product product : productList2) {
            uuidList2.add(product.getUuid());
        }

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

        Map<String, Integer> versions = new HashMap<String, Integer>();
        versions.put(p1.getId(), p1.getEntityVersion());
        versions.put(p4.getId(), p4.getEntityVersion());
        versions.put("bad_id", p7.getEntityVersion());

        List<Product> productList1 = this.ownerProductCurator.getProductsByVersions(owner1, versions).list();
        List<Product> productList2 = this.ownerProductCurator.getProductsByVersions(owner2, versions).list();
        List<Product> productList3 = this.ownerProductCurator.getProductsByVersions(null, versions).list();

        // List 1 should contain products 2, 3, 5 and 6
        // List 2 should contain products 1, 3, 4 and 6
        // List 3 should contain products 1 through 6

        assertEquals(4, productList1.size());
        assertEquals(4, productList2.size());
        assertEquals(6, productList3.size());

        List<String> uuidList1 = new LinkedList<String>();
        for (Product product : productList1) {
            uuidList1.add(product.getUuid());
        }

        List<String> uuidList2 = new LinkedList<String>();
        for (Product product : productList2) {
            uuidList2.add(product.getUuid());
        }

        List<String> uuidList3 = new LinkedList<String>();
        for (Product product : productList3) {
            uuidList3.add(product.getUuid());
        }

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

        List<Product> productList1 = this.ownerProductCurator.getProductsByVersions(owner1, null).list();
        assertEquals(0, productList1.size());

        List<Product> productList2 = this.ownerProductCurator.getProductsByVersions(owner1,
            Collections.<String, Integer>emptyMap()).list();
        assertEquals(0, productList2.size());

        List<Product> productList3 = this.ownerProductCurator.getProductsByVersions(null, null).list();
        assertEquals(0, productList3.size());

        List<Product> productList4 = this.ownerProductCurator.getProductsByVersions(null,
            Collections.<String, Integer>emptyMap()).list();
        assertEquals(0, productList4.size());
    }
}
