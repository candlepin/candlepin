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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.config.ConfigProperties;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.AttributeValidator;
import org.candlepin.util.ObjectMapperFactory;
import org.candlepin.util.PropertyValidationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.persistence.LockModeType;
import javax.persistence.PersistenceException;
import javax.validation.ConstraintViolationException;


public class ProductCuratorTest extends DatabaseTestFixture {

    @BeforeEach
    public void setUp() throws Exception {
        config.setProperty(ConfigProperties.INTEGER_ATTRIBUTES, "product.count, product.multiplier");
        config.setProperty(ConfigProperties.NON_NEG_INTEGER_ATTRIBUTES, "product.pos_count");
        config.setProperty(ConfigProperties.LONG_ATTRIBUTES, "product.long_count, product.long_multiplier");
        config.setProperty(ConfigProperties.NON_NEG_LONG_ATTRIBUTES, "product.long_pos_count");
        config.setProperty(ConfigProperties.BOOLEAN_ATTRIBUTES, "product.bool_val_str, product.bool_val_num");

        // Inject this attributeValidator into the curator
        Field field = ProductCurator.class.getDeclaredField("attributeValidator");
        field.setAccessible(true);
        field.set(this.productCurator, new AttributeValidator(this.config, this.i18nProvider));
    }

    /**
     * Creates and persists a very basic product using the given product ID and namespace. If the
     * namespace is null or empty, the product will be created in the global namespace.
     *
     * @param productId
     *  the string to use for the product ID and name
     *
     * @param namespace
     *  the namespace in which to create the product
     *
     * @return
     *  the newly created product
     */
    private Product createNamespacedProduct(String productId, String namespace) {
        Product product = new Product()
            .setId(productId)
            .setName(productId)
            .setNamespace(namespace);

        return this.createProduct(product);
    }

    private static Stream<Arguments> lockModeTypeSource() {
        return Stream.of(
            Arguments.of((LockModeType) null),
            Arguments.of(LockModeType.NONE),
            Arguments.of(LockModeType.OPTIMISTIC),
            Arguments.of(LockModeType.PESSIMISTIC_READ),
            Arguments.of(LockModeType.PESSIMISTIC_WRITE),
            Arguments.of(LockModeType.READ),
            Arguments.of(LockModeType.WRITE));
    }

    @Test
    public void testGetProductQueryBuilder() {
        ProductQueryBuilder builder = this.productCurator.getProductQueryBuilder();

        // Verify it is not null
        assertNotNull(builder);

        // Verify it can be used for simple queries
        List<Product> products = builder.getResultList();
        assertNotNull(products);
    }

    @Test
    public void testGetProductById() {
        Product product1 = this.createNamespacedProduct("test_prod-1", null); // global namespace
        Product product2 = this.createNamespacedProduct("test_prod-2", "namespace-1");
        Product product3 = this.createNamespacedProduct("test_prod-3", "namespace-2");

        Product output = this.productCurator.getProductById(product2.getNamespace(), product2.getId());
        assertEquals(product2, output);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "namespace-1")
    public void testGetProductByIdRestrictsLookupToNamespace(String namespace) {
        String id = "test_prod-1";

        Product product1 = this.createNamespacedProduct(id, namespace);
        Product product2 = this.createNamespacedProduct(id, "namespace-2");

        if (namespace != null && !namespace.isEmpty()) {
            Product product3 = this.createNamespacedProduct(id, null);
        }

        Product output = this.productCurator.getProductById(namespace, id);
        assertEquals(product1, output);
    }

    @Test
    public void testGetProductByIdDoesNotFallBackToGlobalNamespace() {
        Product product1 = this.createNamespacedProduct("test_prod-1", null);

        Product output = this.productCurator.getProductById("namespace-1", product1.getId());
        assertNull(output);
    }

    @Test
    public void testGetProductByIdHandlesNullProductId() {
        Product product1 = this.createNamespacedProduct("test_prod-1", null);
        Product product2 = this.createNamespacedProduct("test_prod-1", "namespace-1");

        Product output = this.productCurator.getProductById("namespace-1", null);
        assertNull(output);
    }

    @ParameterizedTest
    @MethodSource("lockModeTypeSource")
    public void testGetProductByIdWithLockMode(LockModeType lockMode) {
        String id = "test_prod-1";

        Product product1 = this.createNamespacedProduct(id, null); // global namespace
        Product product2 = this.createNamespacedProduct(id, "namespace-1");
        Product product3 = this.createNamespacedProduct("test_prod-2", "namespace-1");
        Product product4 = this.createNamespacedProduct(id, "namespace-2");

        Product output = this.productCurator.getProductById(product2.getNamespace(), id, lockMode);
        assertEquals(product2, output);
    }

    @Test
    public void testGetProductsByIds() {
        Product product1 = this.createNamespacedProduct("test_prod-1", null); // global namespace
        Product product2 = this.createNamespacedProduct("test_prod-2", "namespace-1");
        Product product3 = this.createNamespacedProduct("test_prod-3", "namespace-1");
        Product product4 = this.createNamespacedProduct("test_prod-4", "namespace-2");

        String namespace = "namespace-1";
        List<String> ids = List.of(product2.getId(), product3.getId(), product4.getId());

        Map<String, Product> expected = Map.of(
            product2.getId(), product2,
            product3.getId(), product3);

        Map<String, Product> output = this.productCurator.getProductsByIds(namespace, ids);
        assertThat(output)
            .isNotNull()
            .containsExactlyInAnyOrderEntriesOf(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "namespace-1")
    public void testGetProductsByIdsRestrictsLookupToNamespace(String namespace) {
        Map<String, List<Product>> productMap = new HashMap<>();

        for (String ns : List.of("", "namespace-1", "namespace-2")) {
            Product product1 = this.createNamespacedProduct("test_prod-1", ns);
            Product product2 = this.createNamespacedProduct("test_prod-2", ns);
            Product product3 = this.createNamespacedProduct("test_prod-3", ns);

            productMap.put(ns, List.of(product1, product2, product3));
        }

        List<String> ids = List.of("test_prod-1", "test_prod-3", "test_prod-404");

        Map<String, Product> expected = productMap.get(namespace != null ? namespace : "")
            .stream()
            .filter(entity -> ids.contains(entity.getId()))
            .collect(Collectors.toMap(Product::getId, Function.identity()));

        Map<String, Product> output = this.productCurator.getProductsByIds(namespace, ids);

        assertThat(output)
            .isNotNull()
            .containsExactlyInAnyOrderEntriesOf(expected);
    }

    @Test
    public void testGetProductsByIdsDoesNotFallBackToGlobalNamespace() {
        Product product1 = this.createNamespacedProduct("test_prod-1", null);
        Product product2 = this.createNamespacedProduct("test_prod-2", "namespace-1");

        Map<String, Product> output = this.productCurator.getProductsByIds("namespace-1",
            List.of(product1.getId()));

        assertThat(output)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testGetProductsByIdsHandlesNullCollection() {
        Product product1 = this.createNamespacedProduct("test_prod-1", null);
        Product product2 = this.createNamespacedProduct("test_prod-1", "namespace-1");

        Map<String, Product> output = this.productCurator.getProductsByIds("namespace-1", null);

        assertThat(output)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testGetProductsByIdsHandlesNullElements() {
        Product product1 = this.createNamespacedProduct("test_prod-1", null);
        Product product2 = this.createNamespacedProduct("test_prod-1", "namespace-1");

        List<String> ids = Arrays.asList("test_prod-1", null);

        Map<String, Product> output = this.productCurator.getProductsByIds("namespace-1", ids);

        assertThat(output)
            .isNotNull()
            .hasSize(1)
            .containsEntry(product2.getId(), product2);
    }

    @ParameterizedTest
    @MethodSource("lockModeTypeSource")
    public void testGetProductsByIdsWithLockMode(LockModeType lockMode) {
        Product product1nsG = this.createNamespacedProduct("test_prod-1", null);
        Product product1ns1 = this.createNamespacedProduct("test_prod-1", "namespace-1");
        Product product1ns2 = this.createNamespacedProduct("test_prod-1", "namespace-2");
        Product product2nsG = this.createNamespacedProduct("test_prod-2", null);
        Product product2ns1 = this.createNamespacedProduct("test_prod-2", "namespace-1");
        Product product2ns2 = this.createNamespacedProduct("test_prod-2", "namespace-2");
        Product product3nsG = this.createNamespacedProduct("test_prod-3", null);
        Product product3ns1 = this.createNamespacedProduct("test_prod-3", "namespace-1");
        Product product3ns2 = this.createNamespacedProduct("test_prod-3", "namespace-2");

        List<String> ids = List.of("test_prod-1", "test_prod-2", "test_prod-404");
        Map<String, Product> expected = Map.of(
            product1ns1.getId(), product1ns1,
            product2ns1.getId(), product2ns1);

        Map<String, Product> output = this.productCurator.getProductsByIds("namespace-1", ids, lockMode);

        assertThat(output)
            .isNotNull()
            .containsExactlyInAnyOrderEntriesOf(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "namespace-1", "bad_namespace" })
    public void testGetProductsByNamespaceRestrictsLookupToNamespace(String namespace) {
        Map<String, List<Product>> productMap = new HashMap<>();

        for (String ns : List.of("", "namespace-1", "namespace-2")) {
            Product product1 = this.createNamespacedProduct("test_prod-1", ns);
            Product product2 = this.createNamespacedProduct("test_prod-2", ns);
            Product product3 = this.createNamespacedProduct("test_prod-3", ns);

            productMap.put(ns, List.of(product1, product2, product3));
        }

        List<Product> expected = productMap.getOrDefault(namespace != null ? namespace : "", List.of());

        List<Product> output = this.productCurator.getProductsByNamespace(namespace);

        assertThat(output)
            .isNotNull()
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    @ParameterizedTest
    @MethodSource("lockModeTypeSource")
    public void testGetProductsByNamespaceWithLockMode(LockModeType lockMode) {
        Product product1nsG = this.createNamespacedProduct("test_prod-1", null);
        Product product1ns1 = this.createNamespacedProduct("test_prod-1", "namespace-1");
        Product product1ns2 = this.createNamespacedProduct("test_prod-1", "namespace-2");
        Product product2nsG = this.createNamespacedProduct("test_prod-2", null);
        Product product2ns2 = this.createNamespacedProduct("test_prod-2", "namespace-2");
        Product product3nsG = this.createNamespacedProduct("test_prod-3", null);
        Product product3ns1 = this.createNamespacedProduct("test_prod-3", "namespace-1");
        Product product3ns2 = this.createNamespacedProduct("test_prod-3", "namespace-2");

        List<Product> expected = List.of(product1ns1, product3ns1);

        List<Product> output = this.productCurator.getProductsByNamespace("namespace-1", lockMode);

        assertThat(output)
            .isNotNull()
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    public void testResolveProductId() {
        Product product1nsG = this.createNamespacedProduct("test_prod-1", null);
        Product product1ns1 = this.createNamespacedProduct("test_prod-1", "namespace-1");
        Product product1ns2 = this.createNamespacedProduct("test_prod-1", "namespace-2");
        Product product2nsG = this.createNamespacedProduct("test_prod-2", null);
        Product product2ns1 = this.createNamespacedProduct("test_prod-2", "namespace-1");
        Product product2ns2 = this.createNamespacedProduct("test_prod-2", "namespace-2");

        Product output = this.productCurator.resolveProductId(product1ns1.getNamespace(),
            product2ns1.getId());

        assertEquals(product2ns1, output);
    }

    @Test
    public void testResolveProductIdFallsBackToGlobalNamespace() {
        Product product1nsG = this.createNamespacedProduct("test_prod-1", null);
        Product product1ns2 = this.createNamespacedProduct("test_prod-1", "namespace-2");

        Product output = this.productCurator.resolveProductId("namespace-1", product1nsG.getId());
        assertEquals(product1nsG, output);
    }

    @Test
    public void testResolveProductIdDoesNotFallbackFromGlobalNamespace() {
        Product product1nsG = this.createNamespacedProduct("test_prod-1", null);
        Product product1ns1 = this.createNamespacedProduct("test_prod-1", "namespace-1");
        Product product1ns2 = this.createNamespacedProduct("test_prod-1", "namespace-2");

        Product output = this.productCurator.resolveProductId(null, product1nsG.getId());
        assertEquals(product1nsG, output);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "test_prod-404" })
    public void testResolveProductIdHandlesInvalidProductIds(String id) {
        Product product1nsG = this.createNamespacedProduct("test_prod-1", null);
        Product product1ns1 = this.createNamespacedProduct("test_prod-1", "namespace-1");
        Product product1ns2 = this.createNamespacedProduct("test_prod-1", "namespace-2");

        Product output = this.productCurator.resolveProductId("namespace-1", id);
        assertNull(output);
    }

    @ParameterizedTest
    @MethodSource("lockModeTypeSource")
    public void testResolveProductIdWithLockMode(LockModeType lockMode) {
        Product product1nsG = this.createNamespacedProduct("test_prod-1", null);
        Product product1ns1 = this.createNamespacedProduct("test_prod-1", "namespace-1");
        Product product1ns2 = this.createNamespacedProduct("test_prod-1", "namespace-2");
        Product product2nsG = this.createNamespacedProduct("test_prod-2", null);
        Product product2ns1 = this.createNamespacedProduct("test_prod-2", "namespace-1");
        Product product2ns2 = this.createNamespacedProduct("test_prod-2", "namespace-2");

        Product output = this.productCurator.resolveProductId("namespace-1", "test_prod-1", lockMode);

        assertEquals(product1ns1, output);
    }

    @Test
    public void testResolveProductIds() {
        Product product1 = this.createNamespacedProduct("test_prod-1", null); // global namespace
        Product product2 = this.createNamespacedProduct("test_prod-2", "namespace-1");
        Product product3 = this.createNamespacedProduct("test_prod-3", "namespace-1");
        Product product4 = this.createNamespacedProduct("test_prod-4", "namespace-2");

        String namespace = "namespace-1";
        List<String> ids = List.of(product2.getId(), product3.getId(), product4.getId());

        Map<String, Product> expected = Map.of(
            product2.getId(), product2,
            product3.getId(), product3);

        Map<String, Product> output = this.productCurator.resolveProductIds(namespace, ids);
        assertThat(output)
            .isNotNull()
            .containsExactlyInAnyOrderEntriesOf(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "namespace-1", "namespace-404" })
    public void testResolveProductIdsPrefersSpecifiedNamespaceOverGlobal(String namespace) {
        Map<String, List<Product>> productMap = new HashMap<>();

        for (String ns : List.of("", "namespace-1", "namespace-2")) {
            Product product1 = this.createNamespacedProduct("test_prod-1", ns);
            Product product2 = this.createNamespacedProduct("test_prod-2", ns);
            Product product3 = this.createNamespacedProduct("test_prod-3", ns);

            productMap.put(ns, List.of(product1, product2, product3));
        }

        List<String> ids = List.of("test_prod-1", "test_prod-3", "test_prod-404");

        List<Product> expectedProducts = productMap.get(namespace != null ? namespace : "");
        if (expectedProducts == null) {
            expectedProducts = productMap.get("");
        }

        Map<String, Product> expected = expectedProducts.stream()
            .filter(entity -> ids.contains(entity.getId()))
            .collect(Collectors.toMap(Product::getId, Function.identity()));

        Map<String, Product> output = this.productCurator.resolveProductIds(namespace, ids);

        assertThat(output)
            .isNotNull()
            .containsExactlyInAnyOrderEntriesOf(expected);
    }

    @Test
    public void testResolveProductIdsCanFallBackToGlobalNamespace() {
        Product product1 = this.createNamespacedProduct("test_prod-1", null);
        Product product2 = this.createNamespacedProduct("test_prod-2", "namespace-1");

        Map<String, Product> output = this.productCurator.resolveProductIds("namespace-1",
            List.of(product1.getId(), product2.getId()));

        assertThat(output)
            .isNotNull()
            .hasSize(2)
            .containsEntry(product1.getId(), product1)
            .containsEntry(product2.getId(), product2);
    }

    @Test
    public void testResolveProductIdsHandlesNullCollection() {
        Product product1 = this.createNamespacedProduct("test_prod-1", null);
        Product product2 = this.createNamespacedProduct("test_prod-1", "namespace-1");

        Map<String, Product> output = this.productCurator.resolveProductIds("namespace-1", null);

        assertThat(output)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testResolveProductIdsHandlesNullElements() {
        Product product1 = this.createNamespacedProduct("test_prod-1", null);
        Product product2 = this.createNamespacedProduct("test_prod-1", "namespace-1");

        List<String> ids = Arrays.asList("test_prod-1", null);

        Map<String, Product> output = this.productCurator.resolveProductIds("namespace-1", ids);

        assertThat(output)
            .isNotNull()
            .hasSize(1)
            .containsEntry(product2.getId(), product2);
    }

    @ParameterizedTest
    @MethodSource("lockModeTypeSource")
    public void testResolveProductIdsWithLockMode(LockModeType lockMode) {
        Product product1nsG = this.createNamespacedProduct("test_prod-1", null);
        Product product1ns1 = this.createNamespacedProduct("test_prod-1", "namespace-1");
        Product product1ns2 = this.createNamespacedProduct("test_prod-1", "namespace-2");
        Product product2nsG = this.createNamespacedProduct("test_prod-2", null);
        Product product2ns2 = this.createNamespacedProduct("test_prod-2", "namespace-2");
        Product product3nsG = this.createNamespacedProduct("test_prod-3", null);
        Product product3ns1 = this.createNamespacedProduct("test_prod-3", "namespace-1");
        Product product3ns2 = this.createNamespacedProduct("test_prod-3", "namespace-2");

        List<String> ids = List.of("test_prod-1", "test_prod-2", "test_prod-404");
        Map<String, Product> expected = Map.of(
            product1ns1.getId(), product1ns1,
            product2nsG.getId(), product2nsG);

        Map<String, Product> output = this.productCurator.resolveProductIds("namespace-1", ids, lockMode);

        assertThat(output)
            .isNotNull()
            .containsExactlyInAnyOrderEntriesOf(expected);
    }

    @Test
    public void testResolveProductsByNamespaceRestrictsLookupToNamespace() {
        Product product1nsG = this.createNamespacedProduct("test_prod-1", null);
        Product product1ns1 = this.createNamespacedProduct("test_prod-1", "namespace-1");
        Product product1ns2 = this.createNamespacedProduct("test_prod-1", "namespace-2");
        Product product2nsG = this.createNamespacedProduct("test_prod-2", null);
        Product product2ns1 = this.createNamespacedProduct("test_prod-2", "namespace-1");
        Product product2ns2 = this.createNamespacedProduct("test_prod-2", "namespace-2");
        Product product3nsG = this.createNamespacedProduct("test_prod-3", null);
        Product product3ns1 = this.createNamespacedProduct("test_prod-3", "namespace-1");
        Product product3ns2 = this.createNamespacedProduct("test_prod-3", "namespace-2");

        List<Product> expected = List.of(product1ns1, product2ns1, product3ns1);

        Collection<Product> output = this.productCurator.resolveProductsByNamespace("namespace-1");

        assertThat(output)
            .isNotNull()
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    public void testResolveProductsByNamespaceFallsBackToGlobalNamespace() {
        Product product1nsG = this.createNamespacedProduct("test_prod-1", null);
        Product product1ns1 = this.createNamespacedProduct("test_prod-1", "namespace-1");
        Product product2nsG = this.createNamespacedProduct("test_prod-2", null);
        Product product3nsG = this.createNamespacedProduct("test_prod-3", null);
        Product product3ns1 = this.createNamespacedProduct("test_prod-3", "namespace-1");

        List<Product> expected = List.of(product1ns1, product2nsG, product3ns1);

        Collection<Product> output = this.productCurator.resolveProductsByNamespace("namespace-1");

        assertThat(output)
            .isNotNull()
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    public void testResolveProductsByNamespaceWithGlobalNamespaceHasNoFallback() {
        Product product1nsG = this.createNamespacedProduct("test_prod-1", null);
        Product product1ns1 = this.createNamespacedProduct("test_prod-1", "namespace-1");
        Product product2nsG = this.createNamespacedProduct("test_prod-2", null);
        Product product3nsG = this.createNamespacedProduct("test_prod-3", null);
        Product product3ns1 = this.createNamespacedProduct("test_prod-3", "namespace-1");

        List<Product> expected = List.of(product1nsG, product2nsG, product3nsG);

        Collection<Product> output = this.productCurator.resolveProductsByNamespace(null);

        assertThat(output)
            .isNotNull()
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    @ParameterizedTest
    @MethodSource("lockModeTypeSource")
    public void testResolveProductsByNamespaceWithLockMode(LockModeType lockMode) {
        Product product1nsG = this.createNamespacedProduct("test_prod-1", null);
        Product product1ns1 = this.createNamespacedProduct("test_prod-1", "namespace-1");
        Product product1ns2 = this.createNamespacedProduct("test_prod-1", "namespace-2");
        Product product2nsG = this.createNamespacedProduct("test_prod-2", null);
        Product product2ns2 = this.createNamespacedProduct("test_prod-2", "namespace-2");
        Product product3ns1 = this.createNamespacedProduct("test_prod-3", "namespace-1");

        List<Product> expected = List.of(product1ns1, product2nsG, product3ns1);

        Collection<Product> output = this.productCurator.resolveProductsByNamespace("namespace-1", lockMode);

        assertThat(output)
            .isNotNull()
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void normalCreate() {
        Product prod = new Product("cptest-label", "My Product");
        this.productCurator.create(prod);

        assertNotNull(prod.getUuid());

        Product fetched = this.getEntityManager()
            .createQuery("SELECT p FROM Product p WHERE p.uuid = :uuid", Product.class)
            .setParameter("uuid", prod.getUuid())
            .getSingleResult();

        assertNotNull(fetched);
    }

    @Test
    public void testProductNameRequired() {
        Product prod = new Product("some product id", null);
        assertThrows(PersistenceException.class, () -> productCurator.create(prod, true));
    }

    @Test
    public void testProductIdRequired() {
        Product prod = new Product(null, "My Product Name");
        assertThrows(ConstraintViolationException.class, () -> productCurator.create(prod, true));
    }

    @Test
    public void nameNonUnique() {
        Product prod = new Product("label1", "name");
        productCurator.create(prod);

        Product prod2 = new Product("label2", "name");
        productCurator.create(prod2);

        assertEquals(prod.getName(), prod2.getName());
        assertNotEquals(prod.getUuid(), prod2.getUuid());
    }

    @Test
    public void testCannotPersistIdenticalProducts() {
        Product p1 = new Product()
            .setId("test-product")
            .setName("test-product");

        this.productCurator.create(p1, true);
        this.productCurator.clear();

        Product p2 = new Product()
            .setId("test-product")
            .setName("test-product");

        assertThrows(PersistenceException.class, () -> this.productCurator.create(p2, true));
    }

    @Test
    public void testWithSimpleJsonAttribute() throws Exception {
        Map<String, String> data = new HashMap<>();
        data.put("a", "1");
        data.put("b", "2");
        ObjectMapper mapper = ObjectMapperFactory.getObjectMapper();
        String jsonData = mapper.writeValueAsString(data);

        Product prod = new Product("cptest-label", "My Product");
        prod.setAttribute("content_sets", jsonData);
        productCurator.create(prod);

        Product lookedUp = productCurator.get(prod.getUuid());
        assertEquals(jsonData, lookedUp.getAttributeValue("content_sets"));

        data = mapper.readValue(lookedUp.getAttributeValue("content_sets"),
            new TypeReference<>() {});
        assertEquals("1", data.get("a"));
        assertEquals("2", data.get("b"));
    }

    @Test
    public void testJsonListOfHashes() throws Exception {
        List<Map<String, String>> data = new ArrayList<>();
        Map<String, String> contentSet1 = new HashMap<>();
        contentSet1.put("name", "cs1");
        contentSet1.put("url", "url");

        Map<String, String> contentSet2 = new HashMap<>();
        contentSet2.put("name", "cs2");
        contentSet2.put("url", "url2");

        data.add(contentSet1);
        data.add(contentSet2);

        ObjectMapper mapper = ObjectMapperFactory.getObjectMapper();
        String jsonData = mapper.writeValueAsString(data);

        Product prod = TestUtil.createProduct("cptest-label", "My Product");
        prod.setAttribute("content_sets", jsonData);
        productCurator.create(prod);

        Product lookedUp = productCurator.get(prod.getUuid());
        assertEquals(jsonData, lookedUp.getAttributeValue("content_sets"));

        data = mapper.readValue(lookedUp.getAttributeValue("content_sets"), new TypeReference<>() {});
        Map<String, String> cs1 = data.get(0);
        assertEquals("cs1", cs1.get("name"));

        Map<String, String> cs2 = data.get(1);
        assertEquals("cs2", cs2.get("name"));
    }

    /**
     * Test whether the creation date of the product variable is set properly when persisted for the
     * first time.
     */
    @Test
    public void testCreationDate() {
        Product prod = TestUtil.createProduct("test-label", "test-product-name");
        productCurator.create(prod);

        assertNotNull(prod.getCreated());
    }

    @Test
    public void testInitialUpdate() {
        Product prod = TestUtil.createProduct("test-label", "test-product-name");
        productCurator.create(prod);

        assertNotNull(prod.getUpdated());
    }

    @Test
    public void testDependentProducts() {
        Product prod = new Product("test-label", "test-product-name");
        HashSet<String> dependentProductIds = new HashSet<>();
        dependentProductIds.add("ProductX");
        prod.setDependentProductIds(dependentProductIds);
        productCurator.create(prod);

        Product lookedUp = productCurator.get(prod.getUuid());

        assertThat(lookedUp.getDependentProductIds())
            .isNotNull()
            .singleElement()
            .isEqualTo("ProductX");
    }

    @Test
    public void testProductFullConstructor() {
        Product prod = new Product("cp_test-label", "variant", "version", "arch", "", "SVC");
        productCurator.create(prod);

        productCurator.get(prod.getUuid());
    }

    @Test
    public void setMultiplierBasic() {
        Product product = TestUtil.createProduct("test", "Test Product");
        product.setMultiplier(4L);

        assertEquals(Long.valueOf(4), product.getMultiplier());
    }

    @Test
    public void setMultiplierNull() {
        Product product = TestUtil.createProduct("test", "Test Product");
        product.setMultiplier(null);

        assertEquals(Long.valueOf(1), product.getMultiplier());
    }

    @Test
    public void setMultiplierNegative() {
        Product product = TestUtil.createProduct("test", "Test Product");
        product.setMultiplier(-15L);

        assertEquals(Long.valueOf(1), product.getMultiplier());
    }

    private Product createTestProduct() {
        Product product = TestUtil.createProduct("testProductId", "Test Product");
        product.setAttribute("a1", "a1");
        product.setAttribute("a2", "a2");
        product.setAttribute("a3", "a3");
        product.setMultiplier(1L);

        return product;
    }

    @Test
    public void testUpdateProduct() {
        Product original = createTestProduct();
        productCurator.create(original);

        Product modified = productCurator.get(original.getUuid());
        String newName = "new name";
        modified.setName(newName);

        // Hack up the attributes, keep a1, remove a2, modify a3, add a4:
        modified.removeAttribute("a2");
        modified.setAttribute("a3", "a3-modified");
        modified.setAttribute("a4", "a4");

        productCurator.merge(modified);

        Product lookedUp = productCurator.get(original.getUuid());
        assertEquals(newName, lookedUp.getName());
        assertEquals(3, lookedUp.getAttributes().size());
        assertEquals("a1", lookedUp.getAttributeValue("a1"));
        assertEquals("a3-modified", lookedUp.getAttributeValue("a3"));
        assertEquals("a4", lookedUp.getAttributeValue("a4"));
    }

    @Test
    public void testProductAttributeValidationSuccessCreate() {
        Product original = createTestProduct();
        original.setAttribute("product.count", "1");
        original.setAttribute("product.pos_count", "5");
        original.setAttribute("product.long_multiplier", String.valueOf(Integer.MAX_VALUE * 1000L));
        original.setAttribute("product.long_pos_count", "23");
        original.setAttribute("product.bool_val_str", "true");
        original.setAttribute("product.bool_val_num", "0");
        productCurator.create(original);
        assertNotNull(original.getUuid());
    }

    @Test
    public void testProductAttributeValidationSuccessUpdate() {
        Product original = createTestProduct();
        productCurator.create(original);
        assertNotNull(original.getUuid());
        original.setAttribute("product.count", "134");
        original.setAttribute("product.pos_count", "333");
        original.setAttribute("product.long_multiplier", String.valueOf(Integer.MAX_VALUE * 100L));
        original.setAttribute("product.long_pos_count", "10");
        original.setAttribute("product.bool_val_str", "false");
        original.setAttribute("product.bool_val_num", "1");
        productCurator.merge(original);
    }

    @Test
    public void testProductAttributeCreationFailBadInt() {
        Product original = createTestProduct();
        original.setAttribute("product.count", "1.0");
        assertThrows(PropertyValidationException.class, () -> productCurator.create(original));
    }

    @Test
    public void testProductAttributeCreationSuccessZeroInt() {
        Product original = createTestProduct();
        original.setAttribute("product.pos_count", "0");
        productCurator.create(original);
    }

    @Test
    public void testProductAttributeCreationFailBadPosInt() {
        Product original = createTestProduct();
        original.setAttribute("product.pos_count", "-5");
        assertThrows(PropertyValidationException.class, () -> productCurator.create(original));
    }

    @Test
    public void testProductAttributeCreationFailBadLong() {
        Product original = createTestProduct();
        original.setAttribute("product.long_multiplier", "ZZ");
        assertThrows(PropertyValidationException.class, () -> productCurator.create(original));
    }

    @Test
    public void testProductAttributeCreationFailBadPosLong() {
        Product original = createTestProduct();
        original.setAttribute("product.long_pos_count", "-1");
        assertThrows(PropertyValidationException.class, () -> productCurator.create(original));
    }

    @Test
    public void testProductAttributeCreationFailBadStringBool() {
        Product original = createTestProduct();
        original.setAttribute("product.bool_val_str", "yes");
        assertThrows(PropertyValidationException.class, () -> productCurator.create(original));
    }

    @Test
    public void testProductAttributeCreationFailNumberBool() {
        Product original = createTestProduct();
        original.setAttribute("product.bool_val_num", "2");
        assertThrows(PropertyValidationException.class, () -> productCurator.create(original));
    }

    @Test
    public void testProductAttributeUpdateFailInt() {
        Product original = createTestProduct();
        productCurator.create(original);
        assertNotNull(original.getUuid());
        original.setAttribute("product.count", "one");
        assertThrows(PropertyValidationException.class, () -> productCurator.merge(original));
    }

    @Test
    public void testProductAttributeUpdateFailPosInt() {
        Product original = createTestProduct();
        productCurator.create(original);
        assertNotNull(original.getUuid());
        original.setAttribute("product.pos_count", "-44");
        assertThrows(PropertyValidationException.class, () -> productCurator.merge(original));
    }

    @Test
    public void testProductAttributeUpdateSuccessZeroInt() {
        Product original = createTestProduct();
        productCurator.create(original);
        assertNotNull(original.getUuid());
        original.setAttribute("product.pos_count", "0");
        productCurator.merge(original);
    }

    @Test
    public void testProductAttributeUpdateFailLong() {
        Product original = createTestProduct();
        productCurator.create(original);
        assertNotNull(original.getUuid());
        original.setAttribute("product.long_multiplier", "10^23");
        assertThrows(PropertyValidationException.class, () -> productCurator.merge(original));
    }

    @Test
    public void testProductAttributeUpdateFailPosLong() {
        Product original = createTestProduct();
        productCurator.create(original);
        assertNotNull(original.getUuid());
        original.setAttribute("product.long_pos_count", "-23");
        assertThrows(PropertyValidationException.class, () -> productCurator.merge(original));
    }

    @Test
    public void testProductAttributeUpdateFailStringBool() {
        Product original = createTestProduct();
        productCurator.create(original);
        assertNotNull(original.getUuid());
        original.setAttribute("product.bool_val_str", "flase");
        assertThrows(PropertyValidationException.class, () -> productCurator.merge(original));
    }

    @Test
    public void testProductAttributeUpdateFailNumberBool() {
        Product original = createTestProduct();
        productCurator.create(original);
        assertNotNull(original.getUuid());
        original.setAttribute("product.bool_val_num", "6");
        assertThrows(PropertyValidationException.class, () -> productCurator.merge(original));
    }

    @Test
    public void testSubstringConfigList() {
        Product original = createTestProduct();
        original.setAttribute("product.pos", "-5");
        productCurator.create(original);
    }

    @Test
    public void ensureProductHasSubscription() {
        Owner owner = this.createOwner();

        Product product = TestUtil.createProduct();

        Pool pool = new Pool()
            .setOwner(owner)
            .setProduct(product)
            .setQuantity(16L)
            .setStartDate(TestUtil.createDateOffset(-1, 0, 0))
            .setEndDate(TestUtil.createDateOffset(1, 0, 0))
            .setContractNumber("1")
            .setAccountNumber("2")
            .setOrderNumber("3");

        this.productCurator.create(product);
        this.poolCurator.create(pool);

        assertTrue(this.productCurator.productHasParentSubscriptions(product));
    }

    @Test
    public void ensureIndirectProductReferencesDoNotCountAsHavingSubscriptions() {
        Owner owner = this.createOwner();

        Product product = TestUtil.createProduct();
        Product providedProduct = TestUtil.createProduct();
        Product derivedProduct = TestUtil.createProduct();
        Product derivedProvidedProduct = TestUtil.createProduct();

        product.addProvidedProduct(providedProduct);
        product.setDerivedProduct(derivedProduct);
        derivedProduct.addProvidedProduct(derivedProvidedProduct);

        this.productCurator.create(derivedProvidedProduct);
        this.productCurator.create(derivedProduct);
        this.productCurator.create(providedProduct);
        this.productCurator.create(product);

        Pool pool = new Pool()
            .setOwner(owner)
            .setProduct(product)
            .setQuantity(16L)
            .setStartDate(TestUtil.createDateOffset(-1, 0, 0))
            .setEndDate(TestUtil.createDateOffset(1, 0, 0))
            .setContractNumber("1")
            .setAccountNumber("2")
            .setOrderNumber("3");

        this.poolCurator.create(pool);

        assertFalse(this.productCurator.productHasParentSubscriptions(providedProduct));
        assertFalse(this.productCurator.productHasParentSubscriptions(derivedProduct));
        assertFalse(this.productCurator.productHasParentSubscriptions(derivedProvidedProduct));
    }

    @Test
    public void ensureDoesNotHaveSubscription() {
        Owner owner = this.createOwner();

        Product noSub = this.createProduct("p1", "p1");
        assertFalse(this.productCurator.productHasParentSubscriptions(noSub));
    }

    @Test
    public void testProductWithBrandingCRUDOperations() {
        Product marketingProduct = createTestProduct();
        marketingProduct.addBranding(
            new Branding(marketingProduct, "eng_prod_id_1", "Brand No 1", "OS"));
        marketingProduct.addBranding(
            new Branding(marketingProduct, "eng_prod_id_2", "Brand No 2", "OS"));

        // Create
        marketingProduct = productCurator.create(marketingProduct);
        productCurator.flush();

        // Detach
        productCurator.detach(marketingProduct);

        // Get
        marketingProduct = productCurator.get(marketingProduct.getUuid());

        // Merge
        marketingProduct.setMultiplier(3L);
        productCurator.merge(marketingProduct);

        // Delete
        productCurator.delete(marketingProduct);
    }

    @Test
    public void testGetPoolsReferencingProducts() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Product product1 = this.createProduct("p1", "product_1");
        Product product2 = this.createProduct("p2", "product_2");
        Product product3 = this.createProduct("p3", "product_3");

        Pool pool1 = this.createPool(owner1, product1);
        Pool pool2 = this.createPool(owner1, product2);
        Pool pool3 = this.createPool(owner2, product3);

        Map<String, Set<String>> output = this.productCurator.getPoolsReferencingProducts(
            Arrays.asList(product1.getUuid(), product2.getUuid()));

        assertNotNull(output);
        assertEquals(2, output.size());

        assertTrue(output.containsKey(product1.getUuid()));
        assertEquals(Set.of(pool1.getId()), output.get(product1.getUuid()));

        assertTrue(output.containsKey(product2.getUuid()));
        assertEquals(Set.of(pool2.getId()), output.get(product2.getUuid()));
    }

    @Test
    public void testGetPoolsReferencingProductsWithNoMatch() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Product product1 = this.createProduct("p1", "product_1");
        Product product2 = this.createProduct("p2", "product_2");
        Product product3 = this.createProduct("p3", "product_3");

        Pool pool1 = this.createPool(owner1, product1);
        Pool pool2 = this.createPool(owner1, product2);
        Pool pool3 = this.createPool(owner2, product3);

        Map<String, Set<String>> output = this.productCurator.getPoolsReferencingProducts(
            Arrays.asList("bad uuid", "another bad uuid"));

        assertNotNull(output);
        assertEquals(0, output.size());
    }

    @Test
    public void testGetPoolsReferencingProductsWithEmptyInput() {
        Map<String, Set<String>> output = this.productCurator.getPoolsReferencingProducts(
            Collections.emptyList());

        assertNotNull(output);
        assertEquals(0, output.size());
    }

    @Test
    public void testGetPoolsReferencingProductsWithNullInput() {
        Map<String, Set<String>> output = this.productCurator.getPoolsReferencingProducts(null);

        assertNotNull(output);
        assertEquals(0, output.size());
    }

    @Test
    public void testGetProductsReferencingProducts() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Product product1 = this.createProduct("p1", "product_1");
        Product product2 = this.createProduct("p2", "product_2");
        Product product3 = this.createProduct("p3", "product_3");
        Product product4 = this.createProduct("p4", "product_4");

        Product refProduct1 = TestUtil.createProduct("ref_p1", "ref product 1");
        refProduct1.addProvidedProduct(product1);
        Product refProduct2 = TestUtil.createProduct("ref_p2", "ref product 2")
            .setDerivedProduct(product2);
        Product refProduct3 = TestUtil.createProduct("ref_p3", "ref product 3")
            .setDerivedProduct(product4);
        refProduct3.addProvidedProduct(product3);

        refProduct1 = this.createProduct(refProduct1);
        refProduct2 = this.createProduct(refProduct2);
        refProduct3 = this.createProduct(refProduct3);

        Map<String, Set<String>> output = this.productCurator.getProductsReferencingProducts(
            Arrays.asList(product1.getUuid(), product2.getUuid()));

        assertNotNull(output);
        assertEquals(2, output.size());

        assertTrue(output.containsKey(product1.getUuid()));
        assertEquals(Set.of(refProduct1.getUuid()), output.get(product1.getUuid()));

        assertTrue(output.containsKey(product2.getUuid()));
        assertEquals(Set.of(refProduct2.getUuid()), output.get(product2.getUuid()));
    }

    @Test
    public void testGetProductsReferencingProductsWithNoMatch() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Product product1 = this.createProduct("p1", "product_1");
        Product product2 = this.createProduct("p2", "product_2");
        Product product3 = this.createProduct("p3", "product_3");
        Product product4 = this.createProduct("p4", "product_4");

        Product refProduct1 = TestUtil.createProduct("ref_p1", "ref product 1");
        refProduct1.addProvidedProduct(product1);
        Product refProduct2 = TestUtil.createProduct("ref_p2", "ref product 2")
            .setDerivedProduct(product2);
        Product refProduct3 = TestUtil.createProduct("ref_p3", "ref product 3")
            .setDerivedProduct(product4);
        refProduct3.addProvidedProduct(product3);

        refProduct1 = this.createProduct(refProduct1);
        refProduct2 = this.createProduct(refProduct2);
        refProduct3 = this.createProduct(refProduct3);

        Map<String, Set<String>> output = this.productCurator.getProductsReferencingProducts(
            Arrays.asList("bad uuid", "another bad uuid"));

        assertNotNull(output);
        assertEquals(0, output.size());
    }

    @Test
    public void testGetProductsReferencingProductsWithEmptyInput() {
        Map<String, Set<String>> output = this.productCurator.getProductsReferencingProducts(
            Collections.emptyList());

        assertNotNull(output);
        assertEquals(0, output.size());
    }

    @Test
    public void testGetProductsReferencingProductsWithNullInput() {
        Map<String, Set<String>> output = this.productCurator.getProductsReferencingProducts(null);

        assertNotNull(output);
        assertEquals(0, output.size());
    }

    private Product createProductWithChildren(String productId, int provided, boolean derived) {
        Product product = new Product()
            .setId(productId)
            .setName(productId);

        for (int i = 0; i < provided; ++i) {
            String pid = productId + "_provided-" + i;
            Product providedProduct = this.createProduct(pid);

            product.addProvidedProduct(providedProduct);
        }

        if (derived) {
            String pid = productId + "_derived";
            Product derivedProduct = this.createProduct(pid);

            product.setDerivedProduct(derivedProduct);
        }

        return this.createProduct(product);
    }

    @Test
    public void testGetChildrenProductsOfProductsByUuidsIncludesProvidedProducts() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Product product1 = this.createProductWithChildren("p1", 0, false);
        Product product2 = this.createProductWithChildren("p2", 1, false);
        Product product3 = this.createProductWithChildren("p3", 2, false);
        Product product4 = this.createProductWithChildren("p4", 3, false);

        List<Product> products = List.of(product1, product2, product3, product4);

        Set<Product> expected = products.stream()
            .flatMap(product -> product.getProvidedProducts().stream())
            .collect(Collectors.toSet());

        List<String> input = products.stream()
            .map(Product::getUuid)
            .collect(Collectors.toList());

        Set<Product> output = this.productCurator.getChildrenProductsOfProductsByUuids(input);
        assertNotNull(output);
        assertEquals(expected, output);
    }

    @Test
    public void testGetChildrenProductsOfProductsByUuidsIncludesDerivedProducts() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Product product1 = this.createProductWithChildren("p1", 0, false);
        Product product2 = this.createProductWithChildren("p2", 0, true);
        Product product3 = this.createProductWithChildren("p3", 0, false);
        Product product4 = this.createProductWithChildren("p4", 0, true);

        List<Product> products = List.of(product1, product2, product3, product4);

        Set<Product> expected = products.stream()
            .map(Product::getDerivedProduct)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        List<String> input = products.stream()
            .map(Product::getUuid)
            .collect(Collectors.toList());

        Set<Product> output = this.productCurator.getChildrenProductsOfProductsByUuids(input);
        assertNotNull(output);
        assertEquals(expected, output);
    }

    @Test
    public void testGetChildrenProductsOfProductsByUuidsIgnoresInvalidProductUuids() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Product product1 = this.createProductWithChildren("p1", 1, true);
        Product product2 = this.createProductWithChildren("p2", 2, true);
        Product product3 = this.createProductWithChildren("p3", 3, true);
        Product product4 = this.createProductWithChildren("p4", 4, true);

        List<Product> products = List.of(product2, product3);

        Set<Product> expected = new HashSet<>();

        products.stream()
            .flatMap(product -> product.getProvidedProducts().stream())
            .forEach(expected::add);

        products.stream()
            .map(Product::getDerivedProduct)
            .filter(Objects::nonNull)
            .forEach(expected::add);

        List<String> input = Arrays.asList(product2.getUuid(), "invalid", product3.getUuid(), null);

        Set<Product> output = this.productCurator.getChildrenProductsOfProductsByUuids(input);
        assertNotNull(output);
        assertEquals(expected, output);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testGetChildrenProductsOfProductsByUuidsHandlesNullAndEmptyCollections(List<String> input) {
        // Create some products just to ensure it doesn't pull random existing things for this case
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        this.createProduct("p1", "product_1");
        this.createProduct("p2", "product_2");
        this.createProduct("p3", "product_3");
        this.createProduct("p4", "product_4");

        Set<Product> output = this.productCurator.getChildrenProductsOfProductsByUuids(input);
        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

    @Test
    public void testAttributesRetained() {
        Map<String, String> attributes = Map.of(
            "attr1", "val1",
            "attr2", "val2",
            "attr3", "val3");

        Product product = new Product()
            .setId("p1")
            .setName("product 1")
            .setAttributes(attributes);

        product = this.productCurator.create(product);

        assertNotNull(product);
        assertEquals(attributes, product.getAttributes());

        this.productCurator.flush();
        this.productCurator.clear();

        Product refreshed = this.productCurator.get(product.getUuid());

        assertNotNull(refreshed);
        assertEquals(attributes, refreshed.getAttributes());
    }

    @Test
    public void testProductHasParentProductsWithoutParentProduct() {
        Product product = this.createProduct();

        assertFalse(this.productCurator.productHasParentProducts(product));
    }

    @Test
    public void testProductHasParentProductsAsDerivedProduct() {
        Product product = this.createProduct();

        Product parent = this.createProduct().setDerivedProduct(product);
        this.productCurator.merge(parent);

        assertTrue(this.productCurator.productHasParentProducts(product));
    }

    @Test
    public void testProductHasParentProductsAsProvidedProduct() {
        Product product = this.createProduct();

        Product parent = this.createProduct();
        parent.addProvidedProduct(product);
        this.productCurator.merge(parent);

        assertTrue(this.productCurator.productHasParentProducts(product));
    }

    @Test
    public void testProductHasParentProductsWithMixedLinkage() {
        Product product = this.createProduct();

        Product parent1 = this.createProduct();
        parent1.addProvidedProduct(product);
        this.productCurator.merge(parent1);

        Product parent2 = this.createProduct();
        parent2.addProvidedProduct(product);
        this.productCurator.merge(parent2);

        Product parent3 = this.createProduct().setDerivedProduct(product);
        this.productCurator.merge(parent3);
        Product parent4 = this.createProduct().setDerivedProduct(product);
        this.productCurator.merge(parent4);

        assertTrue(this.productCurator.productHasParentProducts(product));
    }

    @Test
    public void testProductHasParentProductsWithNullContent() {
        assertFalse(this.productCurator.productHasParentProducts(null));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "test_uuid" })
    public void testProductHasParentProductsWithUnmanagedProduct(String uuid) {
        Product product = new Product()
            .setUuid(uuid);

        // This should not throw an exception or otherwise fail
        assertFalse(this.productCurator.productHasParentProducts(product));
    }

}
