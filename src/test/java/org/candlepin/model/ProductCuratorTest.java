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
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DevConfig;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.AttributeValidator;
import org.candlepin.util.PropertyValidationException;
import org.candlepin.util.Util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.hibernate.HibernateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.persistence.PersistenceException;
import javax.validation.ConstraintViolationException;



public class ProductCuratorTest extends DatabaseTestFixture {

    @Inject private DevConfig config;

    private Owner owner;
    private Product product;
    private Product derivedProduct;
    private Product providedProduct;
    private Product derivedProvidedProduct;
    private Pool pool;

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

        this.owner = this.createOwner();

        this.product = TestUtil.createProduct();
        this.providedProduct = TestUtil.createProduct();
        this.derivedProduct = TestUtil.createProduct();
        this.derivedProvidedProduct = TestUtil.createProduct();

        this.product.addProvidedProduct(this.providedProduct);
        this.product.setDerivedProduct(this.derivedProduct);
        this.derivedProduct.addProvidedProduct(this.derivedProvidedProduct);

        this.productCurator.create(this.derivedProvidedProduct);
        this.productCurator.create(this.derivedProduct);
        this.productCurator.create(this.providedProduct);
        this.productCurator.create(this.product);

        this.pool = new Pool()
            .setOwner(owner)
            .setProduct(product)
            .setQuantity(16L)
            .setStartDate(TestUtil.createDate(2006, 10, 21))
            .setEndDate(TestUtil.createDate(2020, 1, 1))
            .setContractNumber("1")
            .setAccountNumber("2")
            .setOrderNumber("3");

        this.poolCurator.create(pool);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void normalCreate() {
        Product prod = new Product("cptest-label", "My Product");
        productCurator.create(prod);

        List<Product> results = this.getEntityManager().createQuery("select p from Product as p")
            .getResultList();

        assertEquals(5, results.size());
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
        ObjectMapper mapper = new ObjectMapper();
        String jsonData = mapper.writeValueAsString(data);

        Product prod = new Product("cptest-label", "My Product");
        prod.setAttribute("content_sets", jsonData);
        productCurator.create(prod);

        Product lookedUp = productCurator.get(prod.getUuid());
        assertEquals(jsonData, lookedUp.getAttributeValue("content_sets"));

        data = mapper.readValue(lookedUp.getAttributeValue("content_sets"),
            new TypeReference<Map<String, String>>(){});
        assertEquals("1", data.get("a"));
        assertEquals("2", data.get("b"));
    }

    @Test
    public void testJsonListOfHashes() throws Exception {
        List<Map<String, String>> data = new LinkedList<>();
        Map<String, String> contentSet1 = new HashMap<>();
        contentSet1.put("name", "cs1");
        contentSet1.put("url", "url");

        Map<String, String> contentSet2 = new HashMap<>();
        contentSet2.put("name", "cs2");
        contentSet2.put("url", "url2");

        data.add(contentSet1);
        data.add(contentSet2);

        ObjectMapper mapper = new ObjectMapper();
        String jsonData = mapper.writeValueAsString(data);

        Product prod = TestUtil.createProduct("cptest-label", "My Product");
        prod.setAttribute("content_sets", jsonData);
        productCurator.create(prod);

        Product lookedUp = productCurator.get(prod.getUuid());
        assertEquals(jsonData, lookedUp.getAttributeValue("content_sets"));

        data = mapper.readValue(lookedUp.getAttributeValue("content_sets"),
            new TypeReference<List<Map<String, String>>>(){});
        Map<String, String> cs1 = data.get(0);
        assertEquals("cs1", cs1.get("name"));

        Map<String, String> cs2 = data.get(1);
        assertEquals("cs2", cs2.get("name"));
    }

    /**
     *Test whether the creation date of the product variable is set properly
     *when persisted for the first time.
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
        assertThat(lookedUp.getDependentProductIds(), hasItem("ProductX"));
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
    public void testGetProductIdFromContentId() {
        Product p = createTestProduct();
        Content content = TestUtil.createContent("best-content");
        p.addContent(content, true);

        contentCurator.create(content);
        productCurator.create(p);
        this.ownerProductCurator.mapProductToOwner(p, this.owner);
        this.ownerContentCurator.mapContentToOwner(content, this.owner);

        List<String> contentIds = new LinkedList<>();
        contentIds.add(content.getId());
        List<Product> products = productCurator.getProductsByContent(owner, contentIds).list();
        assertEquals(1, products.size());
        assertEquals(p, products.get(0));
    }

    @Test
    public void testGetProductIdFromContentUuid() {
        Product p = createTestProduct();
        Content content = TestUtil.createContent("best-content");
        p.addContent(content, true);

        contentCurator.create(content);
        productCurator.create(p);

        List<String> contentUuids = new LinkedList<>();
        contentUuids.add(content.getUuid());

        List<Product> products = productCurator.getProductsByContentUuids(contentUuids).list();
        assertEquals(1, products.size());
        assertEquals(p, products.get(0));
    }

    @Test
    public void ensureProductHasSubscription() {
        assertTrue(productCurator.productHasSubscriptions(owner, product));
    }

    @Test
    public void ensureIndirectProductReferencesDoNotCountAsHavingSubscriptions() {
        assertFalse(productCurator.productHasSubscriptions(owner, providedProduct));
        assertFalse(productCurator.productHasSubscriptions(owner, derivedProduct));
        assertFalse(productCurator.productHasSubscriptions(owner, derivedProvidedProduct));
    }

    @Test
    public void ensureDoesNotHaveSubscription() {
        Product noSub = this.createProduct("p1", "p1", owner);
        assertFalse(productCurator.productHasSubscriptions(owner, noSub));
    }

    @Test
    public void testPoolProvidedProducts() {
        Set<String> uuids = productCurator.getPoolProvidedProductUuids(pool.getId());
        assertEquals(Set.of(providedProduct.getUuid()), uuids);
    }

    @Test
    public void testDerivedPoolProvidedProducts() {
        Set<String> uuids = productCurator.getDerivedPoolProvidedProductUuids(pool.getId());
        assertEquals(Set.of(derivedProvidedProduct.getUuid()), uuids);
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
    public void testProductCannotUpdateImmutableBrandingCollectionByAddingItems() {
        Product marketingProduct = createTestProduct();
        marketingProduct.addBranding(
            new Branding(marketingProduct, "eng_prod_id_1", "Brand No 1", "OS"));
        marketingProduct.addBranding(
            new Branding(marketingProduct, "eng_prod_id_2", "Brand No 2", "OS"));

        productCurator.create(marketingProduct);
        productCurator.flush();

        marketingProduct.addBranding(
            new Branding(marketingProduct, "eng_prod_id_3", "Brand No 3", "OS"));
        productCurator.merge(marketingProduct);

        PersistenceException pe = assertThrows(PersistenceException.class, () -> productCurator.flush());
        assertEquals(HibernateException.class, pe.getCause().getClass());
        assertTrue(pe.getCause().getMessage().contains("changed an immutable collection instance"));
    }

    @Test
    public void testProductCannotUpdateImmutableBrandingCollectionByRemovingItems() {
        Product marketingProduct = createTestProduct();
        marketingProduct.addBranding(
            new Branding(marketingProduct, "eng_prod_id_1", "Brand No 1", "OS"));
        marketingProduct.addBranding(
            new Branding(marketingProduct, "eng_prod_id_2", "Brand No 2", "OS"));

        productCurator.create(marketingProduct);
        productCurator.flush();

        marketingProduct.getBranding().clear();
        productCurator.merge(marketingProduct);

        PersistenceException pe = assertThrows(PersistenceException.class, () -> productCurator.flush());
        assertEquals(HibernateException.class, pe.getCause().getClass());
        assertTrue(pe.getCause().getMessage().contains("changed an immutable collection instance"));
    }

    @Test
    public void testProductCannotUpdateImmutableBrandingCollectionByUpdatingItem() {
        Product marketingProduct = createTestProduct();
        marketingProduct.addBranding(
            new Branding(marketingProduct, "eng_prod_id_1", "Brand No 1", "OS"));

        productCurator.create(marketingProduct);
        productCurator.flush();

        ((Branding) marketingProduct.getBranding().toArray()[0]).setName("new name");
        productCurator.merge(marketingProduct);
        productCurator.flush();
        productCurator.evict(marketingProduct);

        Product lookedUp = productCurator.get(marketingProduct.getUuid());
        assertNotEquals("new name", ((Branding) lookedUp.getBranding().toArray()[0]).getName());
    }

    @Test
    public void testCannotCreateProductWithBrandingWithNullId() {
        Product marketingProduct = createTestProduct();
        marketingProduct.addBranding(
            new Branding(marketingProduct, null, "Brand No 1", "OS"));

        IllegalStateException ise = assertThrows(IllegalStateException.class,
            () -> productCurator.create(marketingProduct));
        assertEquals(ise.getMessage(),
            "Product contains a Branding with a null product id, name or type.",
            "The exception should have a different message.");
    }

    @Test
    public void testCannotCreateProductWithBrandingWithNullType() {
        Product marketingProduct = createTestProduct();
        marketingProduct.addBranding(
            new Branding(marketingProduct, "eng_prod_id_1", "Brand No 1", null));

        IllegalStateException ise = assertThrows(IllegalStateException.class,
            () -> productCurator.create(marketingProduct));
        assertEquals(ise.getMessage(),
            "Product contains a Branding with a null product id, name or type.",
            "The exception should have a different message.");
    }

    @Test
    public void testCannotCreateProductWithBrandingWithNullName() {
        Product marketingProduct = createTestProduct();
        marketingProduct.addBranding(
            new Branding(marketingProduct, "eng_prod_id_1", null, "OS"));

        IllegalStateException ise = assertThrows(IllegalStateException.class,
            () -> productCurator.create(marketingProduct));
        assertEquals(ise.getMessage(),
            "Product contains a Branding with a null product id, name or type.",
            "The exception should have a different message.");
    }

    @Test
    public void testGetPoolsReferencingProducts() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Product product1 = this.createProduct("p1", "product_1");
        Product product2 = this.createProduct("p2", "product_2", owner1);
        Product product3 = this.createProduct("p3", "product_3", owner2);

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
        Product product2 = this.createProduct("p2", "product_2", owner1);
        Product product3 = this.createProduct("p3", "product_3", owner2);

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
        Product product2 = this.createProduct("p2", "product_2", owner1);
        Product product3 = this.createProduct("p3", "product_3", owner2);
        Product product4 = this.createProduct("p4", "product_4", owner2);

        Product refProduct1 = TestUtil.createProduct("ref_p1", "ref product 1");
        refProduct1.addProvidedProduct(product1);
        Product refProduct2 = TestUtil.createProduct("ref_p2", "ref product 2")
            .setDerivedProduct(product2);
        Product refProduct3 = TestUtil.createProduct("ref_p3", "ref product 3")
            .setDerivedProduct(product4);
        refProduct3.addProvidedProduct(product3);

        refProduct1 = this.createProduct(refProduct1, owner1);
        refProduct2 = this.createProduct(refProduct2, owner1);
        refProduct3 = this.createProduct(refProduct3, owner2);

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
        Product product2 = this.createProduct("p2", "product_2", owner1);
        Product product3 = this.createProduct("p3", "product_3", owner2);
        Product product4 = this.createProduct("p4", "product_4", owner2);

        Product refProduct1 = TestUtil.createProduct("ref_p1", "ref product 1");
        refProduct1.addProvidedProduct(product1);
        Product refProduct2 = TestUtil.createProduct("ref_p2", "ref product 2")
            .setDerivedProduct(product2);
        Product refProduct3 = TestUtil.createProduct("ref_p3", "ref product 3")
            .setDerivedProduct(product4);
        refProduct3.addProvidedProduct(product3);

        refProduct1 = this.createProduct(refProduct1, owner1);
        refProduct2 = this.createProduct(refProduct2, owner1);
        refProduct3 = this.createProduct(refProduct3, owner2);

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

    private Product createProductWithChildren(String productId, int provided, boolean derived,
        Owner... owners) {

        Product product = new Product()
            .setId(productId)
            .setName(productId);

        for (int i = 0; i < provided; ++i) {
            String pid = productId + "_provided-" + i;
            Product providedProduct = this.createProduct(pid, owners);

            product.addProvidedProduct(providedProduct);
        }

        if (derived) {
            String pid = productId + "_derived";
            Product derivedProduct = this.createProduct(pid, owners);

            product.setDerivedProduct(derivedProduct);
        }

        return this.createProduct(product, owners);
    }

    @Test
    public void testGetChildrenProductsOfProductsByUuidsIncludesProvidedProducts() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Product product1 = this.createProductWithChildren("p1", 0, false);
        Product product2 = this.createProductWithChildren("p2", 1, false, owner1);
        Product product3 = this.createProductWithChildren("p3", 2, false, owner2);
        Product product4 = this.createProductWithChildren("p4", 3, false, owner1, owner2);

        List<Product> products = List.of(product1, product2, product3, product4);

        Collection<Product> expected = products.stream()
            .flatMap(product -> product.getProvidedProducts().stream())
            .collect(Collectors.toSet());

        List<String> input = products.stream()
            .map(Product::getUuid)
            .collect(Collectors.toList());

        Collection<Product> output = this.productCurator.getChildrenProductsOfProductsByUuids(input);
        assertNotNull(output);
        assertTrue(Util.collectionsAreEqual(expected, output),
            String.format("Collections are not equal.\n  Expected: %s\n  Actual: %s", expected, output));
    }

    @Test
    public void testGetChildrenProductsOfProductsByUuidsIncludesDerivedProducts() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Product product1 = this.createProductWithChildren("p1", 0, false);
        Product product2 = this.createProductWithChildren("p2", 0, true, owner1);
        Product product3 = this.createProductWithChildren("p3", 0, false, owner2);
        Product product4 = this.createProductWithChildren("p4", 0, true, owner1, owner2);

        List<Product> products = List.of(product1, product2, product3, product4);

        Collection<Product> expected = products.stream()
            .map(Product::getDerivedProduct)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        List<String> input = products.stream()
            .map(Product::getUuid)
            .collect(Collectors.toList());

        Collection<Product> output = this.productCurator.getChildrenProductsOfProductsByUuids(input);
        assertNotNull(output);
        assertTrue(Util.collectionsAreEqual(expected, output),
            String.format("Collections are not equal.\n  Expected: %s\n  Actual: %s", expected, output));
    }

    @Test
    public void testGetChildrenProductsOfProductsByUuidsIgnoresInvalidProductUuids() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Product product1 = this.createProductWithChildren("p1", 1, true);
        Product product2 = this.createProductWithChildren("p2", 2, true, owner1);
        Product product3 = this.createProductWithChildren("p3", 3, true, owner2);
        Product product4 = this.createProductWithChildren("p4", 4, true, owner1, owner2);

        List<Product> products = List.of(product2, product3);

        Collection<Product> expected = new HashSet<>();

        products.stream()
            .flatMap(product -> product.getProvidedProducts().stream())
            .forEach(expected::add);

        products.stream()
            .map(Product::getDerivedProduct)
            .filter(Objects::nonNull)
            .forEach(expected::add);

        List<String> input = Arrays.asList(product2.getUuid(), "invalid", product3.getUuid(), null);

        Collection<Product> output = this.productCurator.getChildrenProductsOfProductsByUuids(input);
        assertNotNull(output);
        assertTrue(Util.collectionsAreEqual(expected, output),
            String.format("Collections are not equal.\n  Expected: %s\n  Actual: %s", expected, output));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testGetChildrenProductsOfProductsByUuidsHandlesNullAndEmptyCollections(List<String> input) {
        // Create some products just to ensure it doesn't pull random existing things for this case
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        this.createProduct("p1", "product_1");
        this.createProduct("p2", "product_2", owner1);
        this.createProduct("p3", "product_3", owner2);
        this.createProduct("p4", "product_4", owner2);

        Collection<Product> output = this.productCurator.getChildrenProductsOfProductsByUuids(input);
        assertNotNull(output);
        assertTrue(output.isEmpty());
    }

}
