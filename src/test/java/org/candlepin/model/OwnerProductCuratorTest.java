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
package org.candlepin.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.opentest4j.AssertionFailedError;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

    /**
     * Equivalency of dates down to milliseconds is sufficient
     * @param date1
     * @param date2
     */
    private void assertInstantEqualsMillis(Instant date1, Instant date2) {
        if (date1 == null && date2 == null) {
            return;
        }
        else if (date1 == null || date2 == null) {
            throw new AssertionFailedError();
        }
        else {
            assertEquals(date1.truncatedTo(ChronoUnit.MILLIS), date2.truncatedTo(ChronoUnit.MILLIS));
        }
    }

    // @Test
    // public void testGetProductById() {
    //     Owner owner = this.createOwner();
    //     Product product = this.createProduct();
    //     this.createOwnerProductMapping(owner, product);

    //     Product resultA = this.ownerProductCurator.getProductById(owner, product.getId());
    //     assertEquals(resultA, product);

    //     Product resultB = this.ownerProductCurator.getProductById(owner.getId(), product.getId());
    //     assertEquals(resultB, product);

    //     assertSame(resultA, resultB);
    // }

    // @Test
    // public void testGetProductByIdNoMapping() {
    //     Owner owner = this.createOwner();
    //     Product product = this.createProduct();

    //     Product resultA = this.ownerProductCurator.getProductById(owner, product.getId());
    //     assertNull(resultA);

    //     Product resultB = this.ownerProductCurator.getProductById(owner.getId(), product.getId());
    //     assertNull(resultB);
    // }

    // @Test
    // public void testGetProductByIdWrongProductId() {
    //     Owner owner = this.createOwner();
    //     Product product1 = this.createProduct();
    //     Product product2 = this.createProduct();
    //     this.createOwnerProductMapping(owner, product1);

    //     Product resultA = this.ownerProductCurator.getProductById(owner, product2.getId());
    //     assertNull(resultA);

    //     Product resultB = this.ownerProductCurator.getProductById(owner.getId(), product2.getId());
    //     assertNull(resultB);
    // }

    // @Test
    // public void testGetProductsByOwnerCPQWithUnmappedProduct() {
    //     Owner owner = this.createOwner();
    //     Product product1 = this.createProduct();
    //     Product product2 = this.createProduct();
    //     Product product3 = this.createProduct();

    //     Collection<Product> productsA = this.ownerProductCurator.getProductsByOwnerCPQ(owner).list();
    //     Collection<Product> productsB = this.ownerProductCurator.getProductsByOwnerCPQ(owner.getId()).list();

    //     assertTrue(productsA.isEmpty());
    //     assertTrue(productsB.isEmpty());
    // }

    // @Test
    // public void testGetProductsByIds() {
    //     Owner owner = this.createOwner();
    //     Product product1 = this.createProduct();
    //     Product product2 = this.createProduct();
    //     Product product3 = this.createProduct();
    //     this.createOwnerProductMapping(owner, product1);
    //     this.createOwnerProductMapping(owner, product2);

    //     Collection<String> ids = Arrays.asList(product1.getId(), product2.getId(), product3.getId(), "dud");
    //     Collection<Product> productsA = this.ownerProductCurator.getProductsByIds(owner, ids).list();
    //     Collection<Product> productsB = this.ownerProductCurator.getProductsByIds(owner.getId(), ids).list();

    //     assertEquals(2, productsA.size());
    //     assertTrue(productsA.contains(product1));
    //     assertTrue(productsA.contains(product2));
    //     assertFalse(productsA.contains(product3));
    //     assertEquals(productsA, productsB);
    // }

    // @Test
    // public void testGetProductsByIdsNullList() {
    //     Owner owner = this.createOwner();
    //     Product product1 = this.createProduct();
    //     Product product2 = this.createProduct();
    //     Product product3 = this.createProduct();
    //     this.createOwnerProductMapping(owner, product1);
    //     this.createOwnerProductMapping(owner, product2);

    //     Collection<String> ids = null;
    //     Collection<Product> productsA = this.ownerProductCurator.getProductsByIds(owner, ids).list();
    //     Collection<Product> productsB = this.ownerProductCurator.getProductsByIds(owner.getId(), ids).list();

    //     assertTrue(productsA.isEmpty());
    //     assertTrue(productsB.isEmpty());
    // }

    // @Test
    // public void testGetProductsByIdsEmptyList() {
    //     Owner owner = this.createOwner();
    //     Product product1 = this.createProduct();
    //     Product product2 = this.createProduct();
    //     Product product3 = this.createProduct();
    //     this.createOwnerProductMapping(owner, product1);
    //     this.createOwnerProductMapping(owner, product2);

    //     Collection<String> ids = Collections.emptyList();
    //     Collection<Product> productsA = this.ownerProductCurator.getProductsByIds(owner, ids).list();
    //     Collection<Product> productsB = this.ownerProductCurator.getProductsByIds(owner.getId(), ids).list();

    //     assertTrue(productsA.isEmpty());
    //     assertTrue(productsB.isEmpty());
    // }

    // private Map<String, Set<String>> getEmptySyspurposeAttributeMap() {
    //     return Map.of(
    //         "usage", new HashSet<>(),
    //         "roles", new HashSet<>(),
    //         "addons", new HashSet<>(),
    //         "support_type", new HashSet<>(),
    //         "support_level", new HashSet<>()
    //     );
    // }

    // @Test
    // public void testGetSyspurposeAttributesByOwner() {
    //     Owner owner = this.createOwner();

    //     Product product1 = new Product();
    //     product1.setId("test-product-" + TestUtil.randomInt());
    //     product1.setName("test-product-" + TestUtil.randomInt());
    //     product1.setAttribute(SystemPurposeAttributeType.USAGE.toString(), "usage1a, usage1b");
    //     product1.setAttribute(SystemPurposeAttributeType.ADDONS.toString(), "addons1a, addons1b");
    //     product1.setAttribute(SystemPurposeAttributeType.ROLES.toString(), "role1a");
    //     product1.setAttribute(SystemPurposeAttributeType.SERVICE_LEVEL.toString(), "Standard");
    //     this.createProduct(product1);
    //     this.createOwnerProductMapping(owner, product1);
    //     this.createPool(owner, product1, 10L, new Date(), TestUtil.createDate(2100, 1, 1));

    //     Product product2 = new Product();
    //     product2.setId("test-product-" + TestUtil.randomInt());
    //     product2.setName("test-product-" + TestUtil.randomInt());
    //     product2.setAttribute(SystemPurposeAttributeType.USAGE.toString(), "usage2a, usage2b");
    //     product2.setAttribute(SystemPurposeAttributeType.ADDONS.toString(), "addons2a, addons2b");
    //     product2.setAttribute(SystemPurposeAttributeType.ROLES.toString(), "role2a");
    //     product2.setAttribute(SystemPurposeAttributeType.SERVICE_LEVEL.toString(), "Layered");
    //     product2.setAttribute(Product.Attributes.SUPPORT_LEVEL_EXEMPT, "true");
    //     this.createProduct(product2);
    //     this.createOwnerProductMapping(owner, product2);
    //     this.createPool(owner, product2, 10L, new Date(), TestUtil.createDate(2100, 1, 1));

    //     // This will be for a product with an expired pool
    //     Product product3 = new Product();
    //     product3.setId("test-product-" + TestUtil.randomInt());
    //     product3.setName("test-product-" + TestUtil.randomInt());
    //     product3.setAttribute(SystemPurposeAttributeType.USAGE.toString(), "usage3a, usage3b");
    //     product3.setAttribute(SystemPurposeAttributeType.ADDONS.toString(), "addons3a, addons3b");
    //     product3.setAttribute(SystemPurposeAttributeType.ROLES.toString(), "role3a");
    //     this.createProduct(product3);
    //     this.createOwnerProductMapping(owner, product3);
    //     this.createPool(owner, product3, 10L, TestUtil.createDate(2000, 1, 1),
    //         TestUtil.createDate(2001, 1, 1));

    //     // This will be for product with no pool
    //     Product product4 = new Product();
    //     product4.setId("test-product-" + TestUtil.randomInt());
    //     product4.setName("test-product-" + TestUtil.randomInt());
    //     product4.setAttribute(SystemPurposeAttributeType.USAGE.toString(), "usage4a, usage4b");
    //     product4.setAttribute(SystemPurposeAttributeType.ADDONS.toString(), "addons4a, addons4b");
    //     product4.setAttribute(SystemPurposeAttributeType.ROLES.toString(), "role4a");
    //     this.createProduct(product4);
    //     this.createOwnerProductMapping(owner, product4);

    //     Map<String, Set<String>> expected = getEmptySyspurposeAttributeMap();
    //     Set<String> useage = new HashSet<>();
    //     useage.add("usage1a");
    //     useage.add("usage1b");
    //     useage.add("usage2a");
    //     useage.add("usage2b");
    //     Set<String> addons = new HashSet<>();
    //     addons.add("addons1a");
    //     addons.add("addons1b");
    //     addons.add("addons2a");
    //     addons.add("addons2b");
    //     Set<String> roles = new HashSet<>();
    //     roles.add("role1a");
    //     roles.add("role2a");
    //     Set<String> support = new HashSet<>();
    //     support.add("Standard");

    //     expected.get(SystemPurposeAttributeType.USAGE.toString()).addAll(useage);
    //     expected.get(SystemPurposeAttributeType.ADDONS.toString()).addAll(addons);
    //     expected.get(SystemPurposeAttributeType.ROLES.toString()).addAll(roles);
    //     expected.get(SystemPurposeAttributeType.SERVICE_LEVEL.toString()).addAll(support);

    //     Map<String, Set<String>> result = this.ownerProductCurator.getSyspurposeAttributesByOwner(owner);
    //     assertEquals(result, expected);
    // }

    // @Test
    // public void testGetNoSyspurposeAttributesByOwner() {
    //     Owner owner = this.createOwner();
    //     Map<String, Set<String>> result = this.ownerProductCurator.getSyspurposeAttributesByOwner(owner);
    //     assertEquals(result, getEmptySyspurposeAttributeMap());
    // }

    // @Test
    // public void testGetSyspurposeAttributesNullOwner() {
    //     Owner owner = this.createOwner();
    //     Map<String, Set<String>> result =
    //         this.ownerProductCurator.getSyspurposeAttributesByOwner((Owner) null);
    //     assertEquals(result, getEmptySyspurposeAttributeMap());
    // }

    // @Test
    // public void testGetSyspurposeAttributesNullOwnerId() {
    //     Owner owner = this.createOwner();
    //     Map<String, Set<String>> result =
    //         this.ownerProductCurator.getSyspurposeAttributesByOwner((String) null);
    //     assertEquals(result, getEmptySyspurposeAttributeMap());
    // }

    // private List<OwnerProduct> buildOwnerProductData(List<Owner> owners, List<Product> products) {
    //     Instant last = Instant.now();
    //     List<OwnerProduct> output = new ArrayList<>();

    //     for (Owner owner : owners) {
    //         for (Product product : products) {
    //             OwnerProduct op = new OwnerProduct(owner, product)
    //                 .setOrphanedDate(last);

    //             this.ownerProductCurator.create(op, false);
    //             output.add(op);

    //             last = last.minusSeconds(300);
    //         }
    //     }

    //     this.ownerProductCurator.flush();

    //     return output;
    // }

    // @Test
    // public void testGetOwnerProduct() {
    //     Owner owner = this.createOwner();
    //     Product product = this.createProduct();

    //     OwnerProduct result = this.ownerProductCurator.getOwnerProduct(owner.getId(), product.getId());
    //     assertNull(result);

    //     OwnerProduct expected = new OwnerProduct()
    //         .setOwner(owner)
    //         .setProduct(product);

    //     this.ownerProductCurator.create(expected, true);
    //     this.ownerProductCurator.clear();

    //     result = this.ownerProductCurator.getOwnerProduct(owner.getId(), product.getId());
    //     assertNotNull(result);
    //     assertNotNull(result.getOwner());
    //     assertNotNull(result.getProduct());

    //     assertEquals(owner, result.getOwner());
    //     assertEquals(product, result.getProduct());
    // }

    // @Test
    // public void testGetOwnerProductHandlesInvalidIds() {
    //     Owner owner = this.createOwner();
    //     Product product = this.createProduct();
    //     OwnerProduct expected = new OwnerProduct()
    //         .setOwner(owner)
    //         .setProduct(product);

    //     this.ownerProductCurator.create(expected, true);
    //     this.ownerProductCurator.clear();


    //     OwnerProduct result = this.ownerProductCurator.getOwnerProduct(owner.getId(), "invalid_product_id");
    //     assertNull(result);

    //     result = this.ownerProductCurator.getOwnerProduct("invalid_owner_id", product.getId());
    //     assertNull(result);

    //     result = this.ownerProductCurator.getOwnerProduct("invalid_owner_id", "invalid_product_id");
    //     assertNull(result);
    // }

    // @Test
    // public void testGetOwnerProductHandlesNullIds() {
    //     Owner owner = this.createOwner();
    //     Product product = this.createProduct();
    //     OwnerProduct expected = new OwnerProduct()
    //         .setOwner(owner)
    //         .setProduct(product);

    //     this.ownerProductCurator.create(expected, true);
    //     this.ownerProductCurator.clear();


    //     OwnerProduct result = this.ownerProductCurator.getOwnerProduct(owner.getId(), null);
    //     assertNull(result);

    //     result = this.ownerProductCurator.getOwnerProduct(null, product.getId());
    //     assertNull(result);

    //     result = this.ownerProductCurator.getOwnerProduct(null, null);
    //     assertNull(result);
    // }

    // @Test
    // public void testGetOwnerProductOrphanedDates() {
    //     Owner owner1 = this.createOwner();
    //     Owner owner2 = this.createOwner();
    //     Owner owner3 = this.createOwner();
    //     Product product1 = this.createProduct();
    //     Product product2 = this.createProduct();
    //     Product product3 = this.createProduct();

    //     List<OwnerProduct> ownerProducts = this.buildOwnerProductData(List.of(owner1, owner2, owner3),
    //         List.of(product1, product2, product3));

    //     List<Product> expectedProducts = List.of(product2, product3);

    //     Map<String, Instant> expected = ownerProducts.stream()
    //         .filter(op -> op.getOwner().equals(owner2))
    //         .filter(op -> expectedProducts.contains(op.getProduct()))
    //         .collect(Collectors.toMap(op -> op.getProduct().getId(), op -> op.getOrphanedDate()));

    //     this.ownerProductCurator.clear();

    //     Map<String, Instant> output = this.ownerProductCurator.getOwnerProductOrphanedDates(owner2,
    //         List.of(product2.getId(), product3.getId()));

    //     assertNotNull(output);
    //     assertEquals(expected.size(), output.size());

    //     for (Map.Entry<String, Instant> entry : expected.entrySet()) {
    //         assertTrue(output.containsKey(entry.getKey()));
    //         assertInstantEqualsMillis(entry.getValue(), output.get(entry.getKey()));
    //     }

    //     for (Map.Entry<String, Instant> entry : output.entrySet()) {
    //         assertTrue(expected.containsKey(entry.getKey()));
    //         assertInstantEqualsMillis(entry.getValue(), expected.get(entry.getKey()));
    //     }
    // }

    // @Test
    // public void testGetOwnerProductOrphanedDatesIncludesProductsWithNullDates() {
    //     Owner owner = this.createOwner();
    //     Product product1 = this.createProduct();
    //     Product product2 = this.createProduct();

    //     OwnerProduct op1 = new OwnerProduct(owner, product1);
    //     OwnerProduct op2 = new OwnerProduct(owner, product2)
    //         .setOrphanedDate(Instant.now());

    //     this.ownerProductCurator.create(op1);
    //     this.ownerProductCurator.create(op2);
    //     this.ownerProductCurator.flush();

    //     Map<String, Instant> output = this.ownerProductCurator.getOwnerProductOrphanedDates(owner,
    //         List.of(product1.getId(), product2.getId()));

    //     assertNotNull(output);
    //     assertEquals(2, output.size());

    //     assertTrue(output.containsKey(product1.getId()));
    //     assertNull(output.get(product1.getId()));

    //     assertTrue(output.containsKey(product2.getId()));
    //     assertInstantEqualsMillis(op2.getOrphanedDate(), output.get(product2.getId()));
    // }

    // @Test
    // public void testGetOwnerProductOrphanedDatesIgnoresInvalidProductIds() {
    //     Owner owner = this.createOwner();
    //     Product product1 = this.createProduct();
    //     Product product2 = this.createProduct();

    //     OwnerProduct op1 = new OwnerProduct(owner, product1);
    //     OwnerProduct op2 = new OwnerProduct(owner, product2)
    //         .setOrphanedDate(Instant.now());

    //     this.ownerProductCurator.create(op1);
    //     this.ownerProductCurator.create(op2);
    //     this.ownerProductCurator.flush();

    //     Map<String, Instant> output = this.ownerProductCurator.getOwnerProductOrphanedDates(owner,
    //         List.of(product1.getId(), product2.getId(), "bad_product_id"));

    //     assertNotNull(output);
    //     assertEquals(2, output.size());

    //     assertTrue(output.containsKey(product1.getId()));
    //     assertNull(output.get(product1.getId()));

    //     assertTrue(output.containsKey(product2.getId()));
    //     assertInstantEqualsMillis(op2.getOrphanedDate(), output.get(product2.getId()));
    // }

    // @Test
    // public void testGetOwnerProductOrphanedDateRequiresOwner() {
    //     assertThrows(IllegalArgumentException.class, () ->
    //         this.ownerProductCurator.getOwnerProductOrphanedDates((Owner) null, List.of("a", "b", "c")));
    // }

    // @Test
    // public void testGetOwnerProductOrphanedDatesByOwnerId() {
    //     Owner owner1 = this.createOwner();
    //     Owner owner2 = this.createOwner();
    //     Owner owner3 = this.createOwner();
    //     Product product1 = this.createProduct();
    //     Product product2 = this.createProduct();
    //     Product product3 = this.createProduct();

    //     List<OwnerProduct> ownerProducts = this.buildOwnerProductData(List.of(owner1, owner2, owner3),
    //         List.of(product1, product2, product3));

    //     List<Product> expectedProducts = List.of(product2, product3);

    //     Map<String, Instant> expected = ownerProducts.stream()
    //         .filter(op -> op.getOwner().equals(owner2))
    //         .filter(op -> expectedProducts.contains(op.getProduct()))
    //         .collect(Collectors.toMap(op -> op.getProduct().getId(), op -> op.getOrphanedDate()));

    //     this.ownerProductCurator.clear();

    //     Map<String, Instant> output = this.ownerProductCurator.getOwnerProductOrphanedDates(owner2.getId(),
    //         List.of(product2.getId(), product3.getId()));

    //     assertNotNull(output);
    //     assertEquals(expected.size(), output.size());

    //     for (Map.Entry<String, Instant> entry : expected.entrySet()) {
    //         assertTrue(output.containsKey(entry.getKey()));
    //         assertInstantEqualsMillis(entry.getValue(), output.get(entry.getKey()));
    //     }

    //     for (Map.Entry<String, Instant> entry : output.entrySet()) {
    //         assertTrue(expected.containsKey(entry.getKey()));
    //         assertInstantEqualsMillis(entry.getValue(), expected.get(entry.getKey()));
    //     }
    // }

    // @Test
    // public void testGetOwnerProductOrphanedDatesByOwnerIdIncludesProductsWithNullDates() {
    //     Owner owner = this.createOwner();
    //     Product product1 = this.createProduct();
    //     Product product2 = this.createProduct();

    //     OwnerProduct op1 = new OwnerProduct(owner, product1);
    //     OwnerProduct op2 = new OwnerProduct(owner, product2)
    //         .setOrphanedDate(Instant.now());

    //     this.ownerProductCurator.create(op1);
    //     this.ownerProductCurator.create(op2);
    //     this.ownerProductCurator.flush();

    //     Map<String, Instant> output = this.ownerProductCurator.getOwnerProductOrphanedDates(owner.getId(),
    //         List.of(product1.getId(), product2.getId()));

    //     assertNotNull(output);
    //     assertEquals(2, output.size());

    //     assertTrue(output.containsKey(product1.getId()));
    //     assertNull(output.get(product1.getId()));

    //     assertTrue(output.containsKey(product2.getId()));
    //     assertInstantEqualsMillis(op2.getOrphanedDate(), output.get(product2.getId()));
    // }

    // @Test
    // public void testGetOwnerProductOrphanedDatesByOwnerIdIgnoresInvalidProductIds() {
    //     Owner owner = this.createOwner();
    //     Product product1 = this.createProduct();
    //     Product product2 = this.createProduct();

    //     OwnerProduct op1 = new OwnerProduct(owner, product1);
    //     OwnerProduct op2 = new OwnerProduct(owner, product2)
    //         .setOrphanedDate(Instant.now());

    //     this.ownerProductCurator.create(op1);
    //     this.ownerProductCurator.create(op2);
    //     this.ownerProductCurator.flush();

    //     Map<String, Instant> output = this.ownerProductCurator.getOwnerProductOrphanedDates(owner.getId(),
    //         List.of(product1.getId(), product2.getId(), "bad_product_id"));

    //     assertNotNull(output);
    //     assertEquals(2, output.size());

    //     assertTrue(output.containsKey(product1.getId()));
    //     assertNull(output.get(product1.getId()));

    //     assertTrue(output.containsKey(product2.getId()));
    //     assertInstantEqualsMillis(op2.getOrphanedDate(), output.get(product2.getId()));
    // }

    // @Test
    // public void testGetOwnerProductOrphanedDateByOwnerIdRequiresOwnerId() {
    //     assertThrows(IllegalArgumentException.class, () ->
    //         this.ownerProductCurator.getOwnerProductOrphanedDates((String) null, List.of("a", "b", "c")));
    // }

    // public static Stream<Arguments> productOrphanedDatesProvider() {
    //     return Stream.of(
    //         Arguments.of(Instant.now().plusSeconds(5000)),
    //         Arguments.of((Instant) null));
    // }

    // @ParameterizedTest(name = "{displayName} {index}: {0}")
    // @MethodSource("productOrphanedDatesProvider")
    // public void testUpdateOwnerProductOrphanedDates(Instant update) {
    //     Owner owner1 = this.createOwner();
    //     Owner owner2 = this.createOwner();
    //     Owner owner3 = this.createOwner();
    //     Product product1 = this.createProduct();
    //     Product product2 = this.createProduct();
    //     Product product3 = this.createProduct();

    //     List<OwnerProduct> ownerProducts = this.buildOwnerProductData(List.of(owner1, owner2, owner3),
    //         List.of(product1, product2, product3));

    //     List<Product> expectedProducts = List.of(product2, product3);
    //     List<OwnerProduct> expected = ownerProducts.stream()
    //         .filter(op -> op.getOwner().equals(owner2))
    //         .filter(op -> expectedProducts.contains(op.getProduct()))
    //         .collect(Collectors.toList());

    //     this.ownerProductCurator.clear();

    //     int output = this.ownerProductCurator.updateOwnerProductOrphanedDates(owner2,
    //         List.of(product2.getId(), product3.getId()), update);

    //     assertEquals(2, output);

    //     for (OwnerProduct op : ownerProducts) {
    //         OwnerProduct refreshed = this.ownerProductCurator
    //             .getOwnerProduct(op.getOwner().getId(), op.getProduct().getId());
    //         assertNotNull(refreshed);

    //         if (expected.contains(op)) {
    //             assertInstantEqualsMillis(update, refreshed.getOrphanedDate());
    //         }
    //         else {
    //             assertInstantEqualsMillis(op.getOrphanedDate(), refreshed.getOrphanedDate());
    //         }
    //     }
    // }

    // @Test
    // public void testUpdateOwnerProductOrphanedDatesIgnoresInvalidProductIds() {
    //     Owner owner = this.createOwner();
    //     Product product1 = this.createProduct();
    //     Product product2 = this.createProduct();

    //     OwnerProduct op1 = new OwnerProduct(owner, product1);
    //     OwnerProduct op2 = new OwnerProduct(owner, product2)
    //         .setOrphanedDate(Instant.now());

    //     this.ownerProductCurator.create(op1);
    //     this.ownerProductCurator.create(op2);
    //     this.ownerProductCurator.flush();
    //     this.ownerProductCurator.clear();

    //     Instant update = Instant.now().plusSeconds(5000);

    //     int count = this.ownerProductCurator.updateOwnerProductOrphanedDates(owner,
    //         List.of(product1.getId(), "bad_product_id", "another_bad_id"), update);

    //     assertEquals(1, count);

    //     OwnerProduct refreshedOp1 = this.ownerProductCurator
    //         .getOwnerProduct(op1.getOwner().getId(), op1.getProduct().getId());
    //     assertNotNull(refreshedOp1);
    //     assertInstantEqualsMillis(update, refreshedOp1.getOrphanedDate());

    //     OwnerProduct refreshedOp2 = this.ownerProductCurator
    //         .getOwnerProduct(op2.getOwner().getId(), op2.getProduct().getId());
    //     assertNotNull(refreshedOp2);
    //     assertInstantEqualsMillis(op2.getOrphanedDate(), refreshedOp2.getOrphanedDate());
    // }

    // @Test
    // @SuppressWarnings("indentation")
    // public void testUpdateOwnerProductOrphanedDatesRequiresOwner() {
    //     assertThrows(IllegalArgumentException.class, () ->
    //         this.ownerProductCurator.updateOwnerProductOrphanedDates((Owner) null, List.of("a", "b", "c"),
    //             Instant.now()));
    // }

    // @ParameterizedTest(name = "{displayName} {index}: {0}")
    // @MethodSource("productOrphanedDatesProvider")
    // public void testUpdateOwnerProductByOwnerIdOrphanedDates(Instant update) {
    //     Owner owner1 = this.createOwner();
    //     Owner owner2 = this.createOwner();
    //     Owner owner3 = this.createOwner();
    //     Product product1 = this.createProduct();
    //     Product product2 = this.createProduct();
    //     Product product3 = this.createProduct();

    //     List<OwnerProduct> ownerProducts = this.buildOwnerProductData(List.of(owner1, owner2, owner3),
    //         List.of(product1, product2, product3));

    //     List<Product> expectedProducts = List.of(product2, product3);
    //     List<OwnerProduct> expected = ownerProducts.stream()
    //         .filter(op -> op.getOwner().equals(owner2))
    //         .filter(op -> expectedProducts.contains(op.getProduct()))
    //         .collect(Collectors.toList());

    //     this.ownerProductCurator.clear();

    //     int output = this.ownerProductCurator.updateOwnerProductOrphanedDates(owner2.getId(),
    //         List.of(product2.getId(), product3.getId()), update);

    //     assertEquals(2, output);

    //     for (OwnerProduct op : ownerProducts) {
    //         OwnerProduct refreshed = this.ownerProductCurator
    //             .getOwnerProduct(op.getOwner().getId(), op.getProduct().getId());
    //         assertNotNull(refreshed);

    //         if (expected.contains(op)) {
    //             assertInstantEqualsMillis(update, refreshed.getOrphanedDate());
    //         }
    //         else {
    //             assertInstantEqualsMillis(op.getOrphanedDate(), refreshed.getOrphanedDate());
    //         }
    //     }
    // }

    // @Test
    // public void testUpdateOwnerProductByOwnerIdOrphanedDatesIgnoresInvalidProductIds() {
    //     Owner owner = this.createOwner();
    //     Product product1 = this.createProduct();
    //     Product product2 = this.createProduct();

    //     OwnerProduct op1 = new OwnerProduct(owner, product1);
    //     OwnerProduct op2 = new OwnerProduct(owner, product2)
    //         .setOrphanedDate(Instant.now());

    //     this.ownerProductCurator.create(op1);
    //     this.ownerProductCurator.create(op2);
    //     this.ownerProductCurator.flush();
    //     this.ownerProductCurator.clear();

    //     Instant update = Instant.now().plusSeconds(5000);

    //     int count = this.ownerProductCurator.updateOwnerProductOrphanedDates(owner.getId(),
    //         List.of(product1.getId(), "bad_product_id", "another_bad_id"), update);

    //     assertEquals(1, count);

    //     OwnerProduct refreshedOp1 = this.ownerProductCurator
    //         .getOwnerProduct(op1.getOwner().getId(), op1.getProduct().getId());
    //     assertNotNull(refreshedOp1);
    //     assertInstantEqualsMillis(update, refreshedOp1.getOrphanedDate());

    //     OwnerProduct refreshedOp2 = this.ownerProductCurator
    //         .getOwnerProduct(op2.getOwner().getId(), op2.getProduct().getId());
    //     assertNotNull(refreshedOp2);
    //     assertInstantEqualsMillis(op2.getOrphanedDate(), refreshedOp2.getOrphanedDate());
    // }

    // @Test
    // @SuppressWarnings("indentation")
    // public void testUpdateOwnerByOwnerIdProductOrphanedDatesRequiresOwner() {
    //     assertThrows(IllegalArgumentException.class, () ->
    //         this.ownerProductCurator.updateOwnerProductOrphanedDates((String) null, List.of("a", "b", "c"),
    //             Instant.now()));
    // }
}
