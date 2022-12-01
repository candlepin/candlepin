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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;



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
    public void testGetProductsByOwnerId() {
        Owner owner = this.createOwner();
        Product product1 = this.createProduct("product1", owner);
        Product product2 = this.createProduct("product2", owner);
        Product product3 = this.createProduct("product3", owner);

        List<Product> expected = List.of(product1, product2, product3);
        List<Product> actual = this.ownerProductCurator.getProductsByOwner(owner.getId());

        assertNotNull(actual);
        assertEquals(expected.size(), actual.size());
        expected.forEach(product -> assertTrue(actual.contains(product)));
    }

    @Test
    public void testGetProductsByOwnerIdDoesNotIncludeUnmappedProducts() {
        Owner owner = this.createOwner();

        Product product1 = this.createProduct("product1", owner);
        Product product2 = this.createProduct("product2", owner);
        Product product3 = this.createProduct("product3", owner);

        Product productU = this.createProduct("unmapped_product");
        Product product4 = new Product()
            .setId("product4")
            .setName("product4")
            .setDerivedProduct(productU);

        this.createProduct(product4, owner);

        List<Product> expected = List.of(product1, product2, product3, product4);
        List<Product> actual = this.ownerProductCurator.getProductsByOwner(owner.getId());

        assertNotNull(actual);
        assertEquals(expected.size(), actual.size());
        expected.forEach(product -> assertTrue(actual.contains(product)));
    }

    @Test
    public void testGetProductsByOwnerIdDoesNotIncludeOtherOrgProduct() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Product product1 = this.createProduct("product1", owner1);
        Product product2 = this.createProduct("product2", owner2);
        Product product3 = this.createProduct("product3", owner1, owner2);

        List<Product> expected = List.of(product1, product3);
        List<Product> actual = this.ownerProductCurator.getProductsByOwner(owner1.getId());

        assertNotNull(actual);
        assertEquals(expected.size(), actual.size());
        expected.forEach(product -> assertTrue(actual.contains(product)));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testGetProductsByOwnerIdAcceptsNullAndEmptyInput(String input) {
        Owner owner = this.createOwner();
        Product product1 = this.createProduct("product1", owner);
        Product product2 = this.createProduct("product2", owner);
        Product product3 = this.createProduct("product3", owner);

        List<Product> output = this.ownerProductCurator.getProductsByOwner(input);
        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    @Test
    public void testGetProductsByOwnerIdAcceptsInvalidInput() {
        Owner owner = this.createOwner();
        Product product1 = this.createProduct("product1", owner);
        Product product2 = this.createProduct("product2", owner);
        Product product3 = this.createProduct("product3", owner);

        List<Product> output = this.ownerProductCurator.getProductsByOwner("invalid owner id");
        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    @Test
    public void testGetProductsByOwner() {
        Owner owner = this.createOwner();
        Product product1 = this.createProduct("product1", owner);
        Product product2 = this.createProduct("product2", owner);
        Product product3 = this.createProduct("product3", owner);

        List<Product> expected = List.of(product1, product2, product3);
        List<Product> actual = this.ownerProductCurator.getProductsByOwner(owner);

        assertNotNull(actual);
        assertEquals(expected.size(), actual.size());
        expected.forEach(product -> assertTrue(actual.contains(product)));
    }

    @Test
    public void testGetProductsByOwnerDoesNotIncludeUnmappedProduct() {
        Owner owner = this.createOwner();

        Product product1 = this.createProduct("product1", owner);
        Product product2 = this.createProduct("product2", owner);
        Product product3 = this.createProduct("product3", owner);

        Product productU = this.createProduct("unmapped_product");
        Product product4 = new Product()
            .setId("product4")
            .setName("product4")
            .setDerivedProduct(productU);

        this.createProduct(product4, owner);

        List<Product> expected = List.of(product1, product2, product3, product4);
        List<Product> actual = this.ownerProductCurator.getProductsByOwner(owner);

        assertNotNull(actual);
        assertEquals(expected.size(), actual.size());
        expected.forEach(product -> assertTrue(actual.contains(product)));
    }

    @Test
    public void testGetProductsByOwnerDoesNotIncludeOtherOrgProduct() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Product product1 = this.createProduct("product1", owner1);
        Product product2 = this.createProduct("product2", owner2);
        Product product3 = this.createProduct("product3", owner1, owner2);

        List<Product> expected = List.of(product1, product3);
        List<Product> actual = this.ownerProductCurator.getProductsByOwner(owner1);

        assertNotNull(actual);
        assertEquals(expected.size(), actual.size());
        expected.forEach(product -> assertTrue(actual.contains(product)));
    }

    @Test
    public void testGetProductsByOwnerAcceptsNullInput() {
        Owner owner = this.createOwner();
        Product product1 = this.createProduct("product1", owner);
        Product product2 = this.createProduct("product2", owner);
        Product product3 = this.createProduct("product3", owner);

        List<Product> output = this.ownerProductCurator.getProductsByOwner((Owner) null);
        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testGetProductsByOwnerAcceptsOwnerWithNullAndEmptyId(String ownerId) {
        Owner owner = this.createOwner();
        Product product1 = this.createProduct("product1", owner);
        Product product2 = this.createProduct("product2", owner);
        Product product3 = this.createProduct("product3", owner);

        Owner input = new Owner().setId(ownerId);

        List<Product> output = this.ownerProductCurator.getProductsByOwner(input);
        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    @Test
    public void testGetProductsByOwnerAcceptsOwnerWithInvalidId() {
        Owner owner = this.createOwner();
        Product product1 = this.createProduct("product1", owner);
        Product product2 = this.createProduct("product2", owner);
        Product product3 = this.createProduct("product3", owner);

        Owner input = new Owner().setId("invalid owner id");

        List<Product> output = this.ownerProductCurator.getProductsByOwner(input);
        assertNotNull(output);
        assertTrue(output.isEmpty());
    }




    @Test
    public void testGetProductsByOwnerCPQ() {
        Owner owner = this.createOwner();
        Product product1 = this.createProduct();
        Product product2 = this.createProduct();
        Product product3 = this.createProduct();
        this.createOwnerProductMapping(owner, product1);
        this.createOwnerProductMapping(owner, product2);

        Collection<Product> productsA = this.ownerProductCurator.getProductsByOwnerCPQ(owner).list();
        Collection<Product> productsB = this.ownerProductCurator.getProductsByOwnerCPQ(owner.getId()).list();

        assertTrue(productsA.contains(product1));
        assertTrue(productsA.contains(product2));
        assertFalse(productsA.contains(product3));
        assertEquals(productsA, productsB);
    }

    @Test
    public void testGetProductsByOwnerCPQWithUnmappedProduct() {
        Owner owner = this.createOwner();
        Product product1 = this.createProduct();
        Product product2 = this.createProduct();
        Product product3 = this.createProduct();

        Collection<Product> productsA = this.ownerProductCurator.getProductsByOwnerCPQ(owner).list();
        Collection<Product> productsB = this.ownerProductCurator.getProductsByOwnerCPQ(owner.getId()).list();

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

        assertTrue(this.isProductMappedToOwner(original, owner));
        assertFalse(this.isProductMappedToOwner(updated, owner));
        assertTrue(this.isProductMappedToOwner(untouched, owner));

        Map<String, String> uuidMap = new HashMap<>();
        uuidMap.put(original.getUuid(), updated.getUuid());

        this.ownerProductCurator.updateOwnerProductReferences(owner, uuidMap);

        assertFalse(this.isProductMappedToOwner(original, owner));
        assertTrue(this.isProductMappedToOwner(updated, owner));
        assertTrue(this.isProductMappedToOwner(untouched, owner));

        this.poolCurator.refresh(pool);
        assertEquals(updated.getUuid(), pool.getProduct().getUuid());
    }

    @Test
    public void testRemoveOwnerProductReferences() {
        Owner owner = this.createOwner();
        Product product = this.createProduct();
        this.createOwnerProductMapping(owner, product);

        ActivationKey key = TestUtil.createActivationKey(owner, null)
            .addProduct(product);

        this.activationKeyCurator.create(key);

        assertTrue(this.isProductMappedToOwner(product, owner));

        this.ownerProductCurator.removeOwnerProductReferences(owner, List.of(product.getUuid()));

        assertFalse(this.isProductMappedToOwner(product, owner));

        this.activationKeyCurator.refresh(key);
        Collection<String> productIds = key.getProductIds();
        assertNotNull(productIds);
        assertTrue(productIds.isEmpty());
    }

    @Test
    public void testGetProductsByVersionsSingleVersion() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();

        Product p1a = this.createProduct("c1", "p1a", owner1);
        Product p1b = this.createProduct("c1", "p1b", owner2);
        Product p1c = this.createProduct("c1", "p1c", owner3);
        Product p2a = this.createProduct("c2", "p2a", owner1);

        Map<String, List<Product>> productMap1 = this.ownerProductCurator
            .getProductsByVersions(Collections.singleton(p1a.getEntityVersion()));
        Map<String, List<Product>> productMap2 = this.ownerProductCurator
            .getProductsByVersions(Collections.singleton(p1b.getEntityVersion()));

        assertEquals(1, productMap1.size());
        assertNotNull(productMap1.get("c1"));
        assertEquals(1, productMap2.size());
        assertNotNull(productMap2.get("c1"));

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

        assertEquals(1, uuidList1.size());
        assertThat(uuidList1, hasItems(p1a.getUuid()));

        assertEquals(1, uuidList2.size());
        assertThat(uuidList2, hasItems(p1b.getUuid()));
    }

    @Test
    public void testGetProductsByVersionsMultipleVersions() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();

        Product p1a = this.createProduct("c1", "p1a", owner1);
        Product p2a = this.createProduct("c2", "p2a", owner1);
        Product p3a = this.createProduct("c3", "p3a", owner1);

        Product p1b = this.createProduct("c1", "p1b", owner2);
        Product p2b = this.createProduct("c2", "p2b", owner2);
        Product p3b = this.createProduct("c3", "p3b", owner2);

        Product p1c = this.createProduct("c1", "p1c", owner3);
        Product p2c = this.createProduct("c2", "p2c", owner3);
        Product p3c = this.createProduct("c3", "p3c", owner3);

        Map<String, List<Product>> productMap1 = this.ownerProductCurator.getProductsByVersions(
            List.of(p1a.getEntityVersion(), p1b.getEntityVersion(), p2c.getEntityVersion()));

        Map<String, List<Product>> productMap2 = this.ownerProductCurator.getProductsByVersions(
            List.of(p1a.getEntityVersion(), p2b.getEntityVersion(), p3c.getEntityVersion()));

        assertEquals(2, productMap1.size());
        assertNotNull(productMap1.get("c1"));
        assertEquals(2, productMap1.get("c1").size());
        assertNotNull(productMap1.get("c2"));
        assertEquals(1, productMap1.get("c2").size());
        assertNull(productMap1.get("c3"));

        assertEquals(3, productMap2.size());
        assertNotNull(productMap2.get("c1"));
        assertEquals(1, productMap2.get("c1").size());
        assertNotNull(productMap2.get("c2"));
        assertEquals(1, productMap2.get("c2").size());
        assertNotNull(productMap2.get("c3"));
        assertEquals(1, productMap2.get("c3").size());

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

        assertEquals(3, uuidList1.size());
        assertThat(uuidList1, hasItems(p1a.getUuid(), p1b.getUuid(), p2c.getUuid()));

        assertEquals(3, uuidList2.size());
        assertThat(uuidList2, hasItems(p1a.getUuid(), p2b.getUuid(), p3c.getUuid()));
    }

    @Test
    public void testGetProductsByVersionsNoVersionInfo() {
        Owner owner1 = this.createOwner();

        Map<String, List<Product>> productMap1 = this.ownerProductCurator
            .getProductsByVersions(null);
        assertEquals(0, productMap1.size());

        Map<String, List<Product>> productMap2 = this.ownerProductCurator
            .getProductsByVersions(Collections.emptyList());
        assertEquals(0, productMap2.size());
    }

    @Test
    public void testGetProductsByVersionsDoesntFailWithLargeDataSets() {
        Owner owner = this.createOwner();

        int seed = 13579;
        List<Long> versions = new Random(seed)
            .longs()
            .boxed()
            .limit(100000)
            .collect(Collectors.toList());

        this.ownerProductCurator.getProductsByVersions(versions);
    }

    private Map<String, Set<String>> getEmptySyspurposeAttributeMap() {
        return Map.of(
            "usage", new HashSet<>(),
            "roles", new HashSet<>(),
            "addons", new HashSet<>(),
            "support_type", new HashSet<>(),
            "support_level", new HashSet<>()
        );
    }

    @Test
    public void testGetSyspurposeAttributesByOwner() {
        Owner owner = this.createOwner();

        Product product1 = new Product();
        product1.setId("test-product-" + TestUtil.randomInt());
        product1.setName("test-product-" + TestUtil.randomInt());
        product1.setAttribute(SystemPurposeAttributeType.USAGE.toString(), "usage1a, usage1b");
        product1.setAttribute(SystemPurposeAttributeType.ADDONS.toString(), "addons1a, addons1b");
        product1.setAttribute(SystemPurposeAttributeType.ROLES.toString(), "role1a");
        product1.setAttribute(SystemPurposeAttributeType.SERVICE_LEVEL.toString(), "Standard");
        this.createProduct(product1);
        this.createOwnerProductMapping(owner, product1);
        this.createPool(owner, product1, 10L, new Date(), TestUtil.createDate(2100, 1, 1));

        Product product2 = new Product();
        product2.setId("test-product-" + TestUtil.randomInt());
        product2.setName("test-product-" + TestUtil.randomInt());
        product2.setAttribute(SystemPurposeAttributeType.USAGE.toString(), "usage2a, usage2b");
        product2.setAttribute(SystemPurposeAttributeType.ADDONS.toString(), "addons2a, addons2b");
        product2.setAttribute(SystemPurposeAttributeType.ROLES.toString(), "role2a");
        product2.setAttribute(SystemPurposeAttributeType.SERVICE_LEVEL.toString(), "Layered");
        product2.setAttribute(Product.Attributes.SUPPORT_LEVEL_EXEMPT, "true");
        this.createProduct(product2);
        this.createOwnerProductMapping(owner, product2);
        this.createPool(owner, product2, 10L, new Date(), TestUtil.createDate(2100, 1, 1));

        // This will be for a product with an expired pool
        Product product3 = new Product();
        product3.setId("test-product-" + TestUtil.randomInt());
        product3.setName("test-product-" + TestUtil.randomInt());
        product3.setAttribute(SystemPurposeAttributeType.USAGE.toString(), "usage3a, usage3b");
        product3.setAttribute(SystemPurposeAttributeType.ADDONS.toString(), "addons3a, addons3b");
        product3.setAttribute(SystemPurposeAttributeType.ROLES.toString(), "role3a");
        this.createProduct(product3);
        this.createOwnerProductMapping(owner, product3);
        this.createPool(owner, product3, 10L, TestUtil.createDate(2000, 1, 1),
            TestUtil.createDate(2001, 1, 1));

        // This will be for product with no pool
        Product product4 = new Product();
        product4.setId("test-product-" + TestUtil.randomInt());
        product4.setName("test-product-" + TestUtil.randomInt());
        product4.setAttribute(SystemPurposeAttributeType.USAGE.toString(), "usage4a, usage4b");
        product4.setAttribute(SystemPurposeAttributeType.ADDONS.toString(), "addons4a, addons4b");
        product4.setAttribute(SystemPurposeAttributeType.ROLES.toString(), "role4a");
        this.createProduct(product4);
        this.createOwnerProductMapping(owner, product4);

        Map<String, Set<String>> expected = getEmptySyspurposeAttributeMap();
        Set<String> useage = new HashSet<>();
        useage.add("usage1a");
        useage.add("usage1b");
        useage.add("usage2a");
        useage.add("usage2b");
        Set<String> addons = new HashSet<>();
        addons.add("addons1a");
        addons.add("addons1b");
        addons.add("addons2a");
        addons.add("addons2b");
        Set<String> roles = new HashSet<>();
        roles.add("role1a");
        roles.add("role2a");
        Set<String> support = new HashSet<>();
        support.add("Standard");

        expected.get(SystemPurposeAttributeType.USAGE.toString()).addAll(useage);
        expected.get(SystemPurposeAttributeType.ADDONS.toString()).addAll(addons);
        expected.get(SystemPurposeAttributeType.ROLES.toString()).addAll(roles);
        expected.get(SystemPurposeAttributeType.SERVICE_LEVEL.toString()).addAll(support);

        Map<String, Set<String>> result = this.ownerProductCurator.getSyspurposeAttributesByOwner(owner);
        assertEquals(result, expected);
    }

    @Test
    public void testGetNoSyspurposeAttributesByOwner() {
        Owner owner = this.createOwner();
        Map<String, Set<String>> result = this.ownerProductCurator.getSyspurposeAttributesByOwner(owner);
        assertEquals(result, getEmptySyspurposeAttributeMap());
    }

    @Test
    public void testGetSyspurposeAttributesNullOwner() {
        Owner owner = this.createOwner();
        Map<String, Set<String>> result =
            this.ownerProductCurator.getSyspurposeAttributesByOwner((Owner) null);
        assertEquals(result, getEmptySyspurposeAttributeMap());
    }

    @Test
    public void testGetSyspurposeAttributesNullOwnerId() {
        Owner owner = this.createOwner();
        Map<String, Set<String>> result =
            this.ownerProductCurator.getSyspurposeAttributesByOwner((String) null);
        assertEquals(result, getEmptySyspurposeAttributeMap());
    }

    private List<OwnerProduct> buildOwnerProductData(List<Owner> owners, List<Product> products) {
        Instant last = Instant.now();
        List<OwnerProduct> output = new ArrayList<>();

        for (Owner owner : owners) {
            for (Product product : products) {
                OwnerProduct op = new OwnerProduct(owner, product)
                    .setOrphanedDate(last);

                this.ownerProductCurator.create(op, false);
                output.add(op);

                last = last.minusSeconds(300);
            }
        }

        this.ownerProductCurator.flush();

        return output;
    }

    @Test
    public void testGetOwnerProduct() {
        Owner owner = this.createOwner();
        Product product = this.createProduct();

        OwnerProduct result = this.ownerProductCurator.getOwnerProduct(owner.getId(), product.getId());
        assertNull(result);

        OwnerProduct expected = new OwnerProduct()
            .setOwner(owner)
            .setProduct(product);

        this.ownerProductCurator.create(expected, true);
        this.ownerProductCurator.clear();

        result = this.ownerProductCurator.getOwnerProduct(owner.getId(), product.getId());
        assertNotNull(result);
        assertNotNull(result.getOwner());
        assertNotNull(result.getProduct());

        assertEquals(owner, result.getOwner());
        assertEquals(product, result.getProduct());
    }

    @Test
    public void testGetOwnerProductHandlesInvalidIds() {
        Owner owner = this.createOwner();
        Product product = this.createProduct();
        OwnerProduct expected = new OwnerProduct()
            .setOwner(owner)
            .setProduct(product);

        this.ownerProductCurator.create(expected, true);
        this.ownerProductCurator.clear();


        OwnerProduct result = this.ownerProductCurator.getOwnerProduct(owner.getId(), "invalid_product_id");
        assertNull(result);

        result = this.ownerProductCurator.getOwnerProduct("invalid_owner_id", product.getId());
        assertNull(result);

        result = this.ownerProductCurator.getOwnerProduct("invalid_owner_id", "invalid_product_id");
        assertNull(result);
    }

    @Test
    public void testGetOwnerProductHandlesNullIds() {
        Owner owner = this.createOwner();
        Product product = this.createProduct();
        OwnerProduct expected = new OwnerProduct()
            .setOwner(owner)
            .setProduct(product);

        this.ownerProductCurator.create(expected, true);
        this.ownerProductCurator.clear();


        OwnerProduct result = this.ownerProductCurator.getOwnerProduct(owner.getId(), null);
        assertNull(result);

        result = this.ownerProductCurator.getOwnerProduct(null, product.getId());
        assertNull(result);

        result = this.ownerProductCurator.getOwnerProduct(null, null);
        assertNull(result);
    }

    @Test
    public void testGetOwnerProductOrphanedDates() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();
        Product product1 = this.createProduct();
        Product product2 = this.createProduct();
        Product product3 = this.createProduct();

        List<OwnerProduct> ownerProducts = this.buildOwnerProductData(List.of(owner1, owner2, owner3),
            List.of(product1, product2, product3));

        List<Product> expectedProducts = List.of(product2, product3);

        Map<String, Instant> expected = ownerProducts.stream()
            .filter(op -> op.getOwner().equals(owner2))
            .filter(op -> expectedProducts.contains(op.getProduct()))
            .collect(Collectors.toMap(op -> op.getProduct().getId(), op -> op.getOrphanedDate()));

        this.ownerProductCurator.clear();

        Map<String, Instant> output = this.ownerProductCurator.getOwnerProductOrphanedDates(owner2,
            List.of(product2.getId(), product3.getId()));

        assertNotNull(output);
        assertEquals(expected.size(), output.size());

        for (Map.Entry<String, Instant> entry : expected.entrySet()) {
            assertTrue(output.containsKey(entry.getKey()));
            assertEquals(entry.getValue(), output.get(entry.getKey()));
        }

        for (Map.Entry<String, Instant> entry : output.entrySet()) {
            assertTrue(expected.containsKey(entry.getKey()));
            assertEquals(entry.getValue(), expected.get(entry.getKey()));
        }
    }

    @Test
    public void testGetOwnerProductOrphanedDatesIncludesProductsWithNullDates() {
        Owner owner = this.createOwner();
        Product product1 = this.createProduct();
        Product product2 = this.createProduct();

        OwnerProduct op1 = new OwnerProduct(owner, product1);
        OwnerProduct op2 = new OwnerProduct(owner, product2)
            .setOrphanedDate(Instant.now());

        this.ownerProductCurator.create(op1);
        this.ownerProductCurator.create(op2);
        this.ownerProductCurator.flush();

        Map<String, Instant> output = this.ownerProductCurator.getOwnerProductOrphanedDates(owner,
            List.of(product1.getId(), product2.getId()));

        assertNotNull(output);
        assertEquals(2, output.size());

        assertTrue(output.containsKey(product1.getId()));
        assertNull(output.get(product1.getId()));

        assertTrue(output.containsKey(product2.getId()));
        assertEquals(op2.getOrphanedDate(), output.get(product2.getId()));
    }

    @Test
    public void testGetOwnerProductOrphanedDatesIgnoresInvalidProductIds() {
        Owner owner = this.createOwner();
        Product product1 = this.createProduct();
        Product product2 = this.createProduct();

        OwnerProduct op1 = new OwnerProduct(owner, product1);
        OwnerProduct op2 = new OwnerProduct(owner, product2)
            .setOrphanedDate(Instant.now());

        this.ownerProductCurator.create(op1);
        this.ownerProductCurator.create(op2);
        this.ownerProductCurator.flush();

        Map<String, Instant> output = this.ownerProductCurator.getOwnerProductOrphanedDates(owner,
            List.of(product1.getId(), product2.getId(), "bad_product_id"));

        assertNotNull(output);
        assertEquals(2, output.size());

        assertTrue(output.containsKey(product1.getId()));
        assertNull(output.get(product1.getId()));

        assertTrue(output.containsKey(product2.getId()));
        assertEquals(op2.getOrphanedDate(), output.get(product2.getId()));
    }

    @Test
    public void testGetOwnerProductOrphanedDateRequiresOwner() {
        assertThrows(IllegalArgumentException.class, () ->
            this.ownerProductCurator.getOwnerProductOrphanedDates((Owner) null, List.of("a", "b", "c")));
    }

    @Test
    public void testGetOwnerProductOrphanedDatesByOwnerId() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();
        Product product1 = this.createProduct();
        Product product2 = this.createProduct();
        Product product3 = this.createProduct();

        List<OwnerProduct> ownerProducts = this.buildOwnerProductData(List.of(owner1, owner2, owner3),
            List.of(product1, product2, product3));

        List<Product> expectedProducts = List.of(product2, product3);

        Map<String, Instant> expected = ownerProducts.stream()
            .filter(op -> op.getOwner().equals(owner2))
            .filter(op -> expectedProducts.contains(op.getProduct()))
            .collect(Collectors.toMap(op -> op.getProduct().getId(), op -> op.getOrphanedDate()));

        this.ownerProductCurator.clear();

        Map<String, Instant> output = this.ownerProductCurator.getOwnerProductOrphanedDates(owner2.getId(),
            List.of(product2.getId(), product3.getId()));

        assertNotNull(output);
        assertEquals(expected.size(), output.size());

        for (Map.Entry<String, Instant> entry : expected.entrySet()) {
            assertTrue(output.containsKey(entry.getKey()));
            assertEquals(entry.getValue(), output.get(entry.getKey()));
        }

        for (Map.Entry<String, Instant> entry : output.entrySet()) {
            assertTrue(expected.containsKey(entry.getKey()));
            assertEquals(entry.getValue(), expected.get(entry.getKey()));
        }
    }

    @Test
    public void testGetOwnerProductOrphanedDatesByOwnerIdIncludesProductsWithNullDates() {
        Owner owner = this.createOwner();
        Product product1 = this.createProduct();
        Product product2 = this.createProduct();

        OwnerProduct op1 = new OwnerProduct(owner, product1);
        OwnerProduct op2 = new OwnerProduct(owner, product2)
            .setOrphanedDate(Instant.now());

        this.ownerProductCurator.create(op1);
        this.ownerProductCurator.create(op2);
        this.ownerProductCurator.flush();

        Map<String, Instant> output = this.ownerProductCurator.getOwnerProductOrphanedDates(owner.getId(),
            List.of(product1.getId(), product2.getId()));

        assertNotNull(output);
        assertEquals(2, output.size());

        assertTrue(output.containsKey(product1.getId()));
        assertNull(output.get(product1.getId()));

        assertTrue(output.containsKey(product2.getId()));
        assertEquals(op2.getOrphanedDate(), output.get(product2.getId()));
    }

    @Test
    public void testGetOwnerProductOrphanedDatesByOwnerIdIgnoresInvalidProductIds() {
        Owner owner = this.createOwner();
        Product product1 = this.createProduct();
        Product product2 = this.createProduct();

        OwnerProduct op1 = new OwnerProduct(owner, product1);
        OwnerProduct op2 = new OwnerProduct(owner, product2)
            .setOrphanedDate(Instant.now());

        this.ownerProductCurator.create(op1);
        this.ownerProductCurator.create(op2);
        this.ownerProductCurator.flush();

        Map<String, Instant> output = this.ownerProductCurator.getOwnerProductOrphanedDates(owner.getId(),
            List.of(product1.getId(), product2.getId(), "bad_product_id"));

        assertNotNull(output);
        assertEquals(2, output.size());

        assertTrue(output.containsKey(product1.getId()));
        assertNull(output.get(product1.getId()));

        assertTrue(output.containsKey(product2.getId()));
        assertEquals(op2.getOrphanedDate(), output.get(product2.getId()));
    }

    @Test
    public void testGetOwnerProductOrphanedDateByOwnerIdRequiresOwnerId() {
        assertThrows(IllegalArgumentException.class, () ->
            this.ownerProductCurator.getOwnerProductOrphanedDates((String) null, List.of("a", "b", "c")));
    }

    public static Stream<Arguments> productOrphanedDatesProvider() {
        return Stream.of(
            Arguments.of(Instant.now().plusSeconds(5000)),
            Arguments.of((Instant) null));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @MethodSource("productOrphanedDatesProvider")
    public void testUpdateOwnerProductOrphanedDates(Instant update) {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();
        Product product1 = this.createProduct();
        Product product2 = this.createProduct();
        Product product3 = this.createProduct();

        List<OwnerProduct> ownerProducts = this.buildOwnerProductData(List.of(owner1, owner2, owner3),
            List.of(product1, product2, product3));

        List<Product> expectedProducts = List.of(product2, product3);
        List<OwnerProduct> expected = ownerProducts.stream()
            .filter(op -> op.getOwner().equals(owner2))
            .filter(op -> expectedProducts.contains(op.getProduct()))
            .collect(Collectors.toList());

        this.ownerProductCurator.clear();

        int output = this.ownerProductCurator.updateOwnerProductOrphanedDates(owner2,
            List.of(product2.getId(), product3.getId()), update);

        assertEquals(2, output);

        for (OwnerProduct op : ownerProducts) {
            OwnerProduct refreshed = this.ownerProductCurator
                .getOwnerProduct(op.getOwner().getId(), op.getProduct().getId());
            assertNotNull(refreshed);

            if (expected.contains(op)) {
                assertEquals(update, refreshed.getOrphanedDate());
            }
            else {
                assertEquals(op.getOrphanedDate(), refreshed.getOrphanedDate());
            }
        }
    }

    @Test
    public void testUpdateOwnerProductOrphanedDatesIgnoresInvalidProductIds() {
        Owner owner = this.createOwner();
        Product product1 = this.createProduct();
        Product product2 = this.createProduct();

        OwnerProduct op1 = new OwnerProduct(owner, product1);
        OwnerProduct op2 = new OwnerProduct(owner, product2)
            .setOrphanedDate(Instant.now());

        this.ownerProductCurator.create(op1);
        this.ownerProductCurator.create(op2);
        this.ownerProductCurator.flush();
        this.ownerProductCurator.clear();

        Instant update = Instant.now().plusSeconds(5000);

        int count = this.ownerProductCurator.updateOwnerProductOrphanedDates(owner,
            List.of(product1.getId(), "bad_product_id", "another_bad_id"), update);

        assertEquals(1, count);

        OwnerProduct refreshedOp1 = this.ownerProductCurator
            .getOwnerProduct(op1.getOwner().getId(), op1.getProduct().getId());
        assertNotNull(refreshedOp1);
        assertEquals(update, refreshedOp1.getOrphanedDate());

        OwnerProduct refreshedOp2 = this.ownerProductCurator
            .getOwnerProduct(op2.getOwner().getId(), op2.getProduct().getId());
        assertNotNull(refreshedOp2);
        assertEquals(op2.getOrphanedDate(), refreshedOp2.getOrphanedDate());
    }

    @Test
    @SuppressWarnings("indentation")
    public void testUpdateOwnerProductOrphanedDatesRequiresOwner() {
        assertThrows(IllegalArgumentException.class, () ->
            this.ownerProductCurator.updateOwnerProductOrphanedDates((Owner) null, List.of("a", "b", "c"),
                Instant.now()));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @MethodSource("productOrphanedDatesProvider")
    public void testUpdateOwnerProductByOwnerIdOrphanedDates(Instant update) {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();
        Product product1 = this.createProduct();
        Product product2 = this.createProduct();
        Product product3 = this.createProduct();

        List<OwnerProduct> ownerProducts = this.buildOwnerProductData(List.of(owner1, owner2, owner3),
            List.of(product1, product2, product3));

        List<Product> expectedProducts = List.of(product2, product3);
        List<OwnerProduct> expected = ownerProducts.stream()
            .filter(op -> op.getOwner().equals(owner2))
            .filter(op -> expectedProducts.contains(op.getProduct()))
            .collect(Collectors.toList());

        this.ownerProductCurator.clear();

        int output = this.ownerProductCurator.updateOwnerProductOrphanedDates(owner2.getId(),
            List.of(product2.getId(), product3.getId()), update);

        assertEquals(2, output);

        for (OwnerProduct op : ownerProducts) {
            OwnerProduct refreshed = this.ownerProductCurator
                .getOwnerProduct(op.getOwner().getId(), op.getProduct().getId());
            assertNotNull(refreshed);

            if (expected.contains(op)) {
                assertEquals(update, refreshed.getOrphanedDate());
            }
            else {
                assertEquals(op.getOrphanedDate(), refreshed.getOrphanedDate());
            }
        }
    }

    @Test
    public void testUpdateOwnerProductByOwnerIdOrphanedDatesIgnoresInvalidProductIds() {
        Owner owner = this.createOwner();
        Product product1 = this.createProduct();
        Product product2 = this.createProduct();

        OwnerProduct op1 = new OwnerProduct(owner, product1);
        OwnerProduct op2 = new OwnerProduct(owner, product2)
            .setOrphanedDate(Instant.now());

        this.ownerProductCurator.create(op1);
        this.ownerProductCurator.create(op2);
        this.ownerProductCurator.flush();
        this.ownerProductCurator.clear();

        Instant update = Instant.now().plusSeconds(5000);

        int count = this.ownerProductCurator.updateOwnerProductOrphanedDates(owner.getId(),
            List.of(product1.getId(), "bad_product_id", "another_bad_id"), update);

        assertEquals(1, count);

        OwnerProduct refreshedOp1 = this.ownerProductCurator
            .getOwnerProduct(op1.getOwner().getId(), op1.getProduct().getId());
        assertNotNull(refreshedOp1);
        assertEquals(update, refreshedOp1.getOrphanedDate());

        OwnerProduct refreshedOp2 = this.ownerProductCurator
            .getOwnerProduct(op2.getOwner().getId(), op2.getProduct().getId());
        assertNotNull(refreshedOp2);
        assertEquals(op2.getOrphanedDate(), refreshedOp2.getOrphanedDate());
    }

    @Test
    @SuppressWarnings("indentation")
    public void testUpdateOwnerByOwnerIdProductOrphanedDatesRequiresOwner() {
        assertThrows(IllegalArgumentException.class, () ->
            this.ownerProductCurator.updateOwnerProductOrphanedDates((String) null, List.of("a", "b", "c"),
                Instant.now()));
    }

    private Long getProductEntityVersion(String uuid) {
        // We need to query this directly, since the objects will automatically regenerate the
        // entity version if it's null
        return this.getEntityManager()
            .createQuery("SELECT entityVersion FROM Product WHERE uuid = :uuid", Long.class)
            .setParameter("uuid", uuid)
            .getSingleResult();
    }

    @Test
    public void testClearProductEntityVersionByEntity() {
        Owner owner = createOwner("test-owner", "owner-test");
        Product product1 = this.createProduct(owner);
        Product product2 = this.createProduct(owner);

        long version1 = product1.getEntityVersion();
        assertNotEquals(0, version1);

        long version2 = product2.getEntityVersion();
        assertNotEquals(0, version2);

        this.ownerProductCurator.clearProductEntityVersion(product1);

        Long fetched1 = this.getProductEntityVersion(product1.getUuid());
        Long fetched2 = this.getProductEntityVersion(product2.getUuid());

        assertNull(fetched1);

        assertNotNull(fetched2);
        assertEquals(version2, fetched2);
    }

    @Test
    public void testClearProductEntityVersionByEntityWithNullEntity() {
        Owner owner = createOwner("test-owner", "owner-test");
        Product product1 = this.createProduct(owner);
        Product product2 = this.createProduct(owner);

        long version1 = product1.getEntityVersion();
        assertNotEquals(0, version1);

        long version2 = product2.getEntityVersion();
        assertNotEquals(0, version2);

        // This should be a silent no-op
        this.ownerProductCurator.clearProductEntityVersion((Product) null);

        Long fetched1 = this.getProductEntityVersion(product1.getUuid());
        Long fetched2 = this.getProductEntityVersion(product2.getUuid());

        assertNotNull(fetched1);
        assertEquals(version1, fetched1);

        assertNotNull(fetched2);
        assertEquals(version2, fetched2);
    }

    @Test
    public void testClearProductEntityVersionByEntityWithNullEntityUuid() {
        Owner owner = createOwner("test-owner", "owner-test");
        Product product1 = this.createProduct(owner);
        Product product2 = this.createProduct(owner);

        long version1 = product1.getEntityVersion();
        assertNotEquals(0, version1);

        long version2 = product2.getEntityVersion();
        assertNotEquals(0, version2);

        // This should be a silent no-op
        this.ownerProductCurator.clearProductEntityVersion(new Product());

        Long fetched1 = this.getProductEntityVersion(product1.getUuid());
        Long fetched2 = this.getProductEntityVersion(product2.getUuid());

        assertNotNull(fetched1);
        assertEquals(version1, fetched1);

        assertNotNull(fetched2);
        assertEquals(version2, fetched2);
    }

    @Test
    public void testClearProductEntityVersionByUUID() {
        Owner owner = createOwner("test-owner", "owner-test");
        Product product1 = this.createProduct(owner);
        Product product2 = this.createProduct(owner);

        long version1 = product1.getEntityVersion();
        assertNotEquals(0, version1);

        long version2 = product2.getEntityVersion();
        assertNotEquals(0, version2);

        this.ownerProductCurator.clearProductEntityVersion(product1.getUuid());

        Long fetched1 = this.getProductEntityVersion(product1.getUuid());
        Long fetched2 = this.getProductEntityVersion(product2.getUuid());

        assertNull(fetched1);

        assertNotNull(fetched2);
        assertEquals(version2, fetched2);
    }

    @Test
    public void testClearProductEntityVersionByUUIDWithNullUUID() {
        Owner owner = createOwner("test-owner", "owner-test");
        Product product1 = this.createProduct(owner);
        Product product2 = this.createProduct(owner);

        long version1 = product1.getEntityVersion();
        assertNotEquals(0, version1);

        long version2 = product2.getEntityVersion();
        assertNotEquals(0, version2);

        // This should be a silent no-op
        this.ownerProductCurator.clearProductEntityVersion((String) null);

        Long fetched1 = this.getProductEntityVersion(product1.getUuid());
        Long fetched2 = this.getProductEntityVersion(product2.getUuid());

        assertNotNull(fetched1);
        assertEquals(version1, fetched1);

        assertNotNull(fetched2);
        assertEquals(version2, fetched2);
    }

    @Test
    public void testClearProductEntityVersionByUUIDWithEmptyUUID() {
        Owner owner = createOwner("test-owner", "owner-test");
        Product product1 = this.createProduct(owner);
        Product product2 = this.createProduct(owner);

        long version1 = product1.getEntityVersion();
        assertNotEquals(0, version1);

        long version2 = product2.getEntityVersion();
        assertNotEquals(0, version2);

        // This should be a silent no-op
        this.ownerProductCurator.clearProductEntityVersion("");

        Long fetched1 = this.getProductEntityVersion(product1.getUuid());
        Long fetched2 = this.getProductEntityVersion(product2.getUuid());

        assertNotNull(fetched1);
        assertEquals(version1, fetched1);

        assertNotNull(fetched2);
        assertEquals(version2, fetched2);
    }

    @Test
    public void testRebuildOwnerProductMapping() {
        Owner owner1 = this.createOwner("test_owner-1");
        Owner owner2 = this.createOwner("test_owner-2");
        Owner owner3 = this.createOwner("test_owner-3");

        Product product1 = this.createProduct("test_product-1", owner1, owner2);
        Product product2 = this.createProduct("test_product-2", owner1, owner2);
        Product product3 = this.createProduct("test_product-3", owner2);
        Product product4 = this.createProduct("test_product-4", owner3);
        Product product5 = this.createProduct("test_product-5", owner3);

        // Remap owner2 to products: 2, 3, and 4
        Map<String, String> pidMap = Map.of(
            product2.getId(), product2.getUuid(),
            product3.getId(), product3.getUuid(),
            product4.getId(), product4.getUuid());

        this.ownerProductCurator.rebuildOwnerProductMapping(owner2, pidMap);

        // Verify owner1 mappings are unaffected
        assertTrue(this.isProductMappedToOwner(product1, owner1));
        assertTrue(this.isProductMappedToOwner(product2, owner1));
        assertFalse(this.isProductMappedToOwner(product3, owner1));
        assertFalse(this.isProductMappedToOwner(product4, owner1));
        assertFalse(this.isProductMappedToOwner(product5, owner1));

        // Verify owner3 mappings are unaffected
        assertFalse(this.isProductMappedToOwner(product1, owner3));
        assertFalse(this.isProductMappedToOwner(product2, owner3));
        assertFalse(this.isProductMappedToOwner(product3, owner3));
        assertTrue(this.isProductMappedToOwner(product4, owner3));
        assertTrue(this.isProductMappedToOwner(product5, owner3));

        // Verify owner2 is mapped according to the config above
        assertFalse(this.isProductMappedToOwner(product1, owner2));
        assertTrue(this.isProductMappedToOwner(product2, owner2));
        assertTrue(this.isProductMappedToOwner(product3, owner2));
        assertTrue(this.isProductMappedToOwner(product4, owner2));
        assertFalse(this.isProductMappedToOwner(product5, owner2));
    }

    @Test
    public void testRebuildOwnerProductMappingWithConflictingProductIDs() {
        Owner owner1 = this.createOwner("test_owner-1");
        Owner owner2 = this.createOwner("test_owner-2");

        Product product1v1 = new Product()
            .setId("test_product-1")
            .setName("test product 1 v1");

        Product product1v2 = new Product()
            .setId("test_product-1")
            .setName("test product 1 v2");

        this.createProduct(product1v1, owner1, owner2);
        this.createProduct(product1v2);

        this.ownerProductCurator.flush();

        Map<String, String> pidMap = Map.of(product1v2.getId(), product1v2.getUuid());
        this.ownerProductCurator.rebuildOwnerProductMapping(owner2, pidMap);

        // owner 2 should now point to v2 of the product; owner1 should remain unaffected
        assertTrue(this.isProductMappedToOwner(product1v1, owner1));
        assertFalse(this.isProductMappedToOwner(product1v2, owner1));

        assertFalse(this.isProductMappedToOwner(product1v1, owner2));
        assertTrue(this.isProductMappedToOwner(product1v2, owner2));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testRebuildOwnerProductMappingAcceptsNullAndEmptyPIDMap(Map<String, String> pidMap) {
        Owner owner1 = this.createOwner("test_owner-1");
        Owner owner2 = this.createOwner("test_owner-2");
        Owner owner3 = this.createOwner("test_owner-3");

        Product product1 = this.createProduct("test_product-1", owner1, owner2);
        Product product2 = this.createProduct("test_product-2", owner1, owner2);
        Product product3 = this.createProduct("test_product-3", owner2);
        Product product4 = this.createProduct("test_product-4", owner3);
        Product product5 = this.createProduct("test_product-5", owner3);

        // As designed, passing a null or empty map should result in the org being mapped to nothing
        this.ownerProductCurator.rebuildOwnerProductMapping(owner2, pidMap);

        // Verify owner1 mappings are unaffected
        assertTrue(this.isProductMappedToOwner(product1, owner1));
        assertTrue(this.isProductMappedToOwner(product2, owner1));
        assertFalse(this.isProductMappedToOwner(product3, owner1));
        assertFalse(this.isProductMappedToOwner(product4, owner1));
        assertFalse(this.isProductMappedToOwner(product5, owner1));

        // Verify owner3 mappings are unaffected
        assertFalse(this.isProductMappedToOwner(product1, owner3));
        assertFalse(this.isProductMappedToOwner(product2, owner3));
        assertFalse(this.isProductMappedToOwner(product3, owner3));
        assertTrue(this.isProductMappedToOwner(product4, owner3));
        assertTrue(this.isProductMappedToOwner(product5, owner3));

        // Verify owner2 isn't mapped to anything
        assertFalse(this.isProductMappedToOwner(product1, owner2));
        assertFalse(this.isProductMappedToOwner(product2, owner2));
        assertFalse(this.isProductMappedToOwner(product3, owner2));
        assertFalse(this.isProductMappedToOwner(product4, owner2));
        assertFalse(this.isProductMappedToOwner(product5, owner2));
    }

    @Test
    public void testRebuildOwnerProductMappingRequiresOwner() {
        Map<String, String> pidMap = Map.of("p1", "p1_uuid");

        assertThrows(IllegalArgumentException.class, () ->
            this.ownerProductCurator.rebuildOwnerProductMapping(null, pidMap));
    }

    @Test
    public void testRebuildOwnerProductMappingRequiresOwnerWithID() {
        Map<String, String> pidMap = Map.of("p1", "p1_uuid");

        Owner owner = new Owner();

        assertThrows(IllegalArgumentException.class, () ->
            this.ownerProductCurator.rebuildOwnerProductMapping(owner, pidMap));
    }

}
