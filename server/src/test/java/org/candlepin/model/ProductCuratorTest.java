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

import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.AttributeValidator;
import org.candlepin.util.PropertyValidationException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.persistence.PersistenceException;
import javax.validation.ConstraintViolationException;



public class ProductCuratorTest extends DatabaseTestFixture {
    private static Logger log = LoggerFactory.getLogger(ProductCuratorTest.class);

    @Inject private Configuration config;

    private Owner owner;
    private Product product;
    private Product derivedProduct;
    private Product providedProduct;
    private Product derivedProvidedProduct;
    private Pool pool;

    @Before
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

        product = TestUtil.createProduct();
        productCurator.create(product);

        providedProduct = TestUtil.createProduct();
        productCurator.create(providedProduct);

        Set<Product> providedProducts = new HashSet<>();
        providedProducts.add(providedProduct);

        derivedProduct = TestUtil.createProduct();
        productCurator.create(derivedProduct);

        derivedProvidedProduct = TestUtil.createProduct();
        productCurator.create(derivedProvidedProduct);

        Set<Product> derivedProvidedProducts = new HashSet<>();
        derivedProvidedProducts.add(derivedProvidedProduct);

        pool = new Pool(
            owner,
            product,
            providedProducts,
            16L,
            TestUtil.createDate(2006, 10, 21),
            TestUtil.createDate(2020, 1, 1),
            "1",
            "2",
            "3"
        );

        pool.setDerivedProduct(derivedProduct);
        pool.setDerivedProvidedProducts(derivedProvidedProducts);
        poolCurator.create(pool);
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

    @Test(expected = PersistenceException.class)
    public void nameRequired() {

        Product prod = new Product("someproductlabel", null);
        productCurator.create(prod);

    }

    @Test(expected = ConstraintViolationException.class)
    public void labelRequired() {

        Product prod = new Product(null, "My Product Name");
        productCurator.create(prod);
    }

    @Test
    public void nameNonUnique() {
        Product prod = new Product("label1", "name");
        productCurator.create(prod);

        Product prod2 = new Product("label2", "name");
        productCurator.create(prod2);

        assertEquals(prod.getName(), prod2.getName());
        assertFalse(prod.getUuid().equals(prod2.getUuid()));
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

    /**
     * Test whether the product updation date is updated when merging.
     */
    @Test
    public void testSubsequentUpdate() {
        Product prod = TestUtil.createProduct("test-label", "test-product-name");
        productCurator.create(prod);

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -2);
        prod.setUpdated(calendar.getTime());

        long updated = prod.getUpdated().getTime();

        prod.setName("test-changed-name");
        prod = this.productCurator.merge(prod);
        this.productCurator.flush();

        assertTrue(prod.getUpdated().getTime() > updated);
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
        original.setAttribute("product.long_multiplier", (new Long(Integer.MAX_VALUE * 1000)).toString());
        original.setAttribute("product.long_pos_count", "23");
        original.setAttribute("product.bool_val_str", "true");
        original.setAttribute("product.bool_val_num", "0");
        productCurator.create(original);
        assertTrue(original.getUuid() != null);
    }

    @Test
    public void testProductAttributeValidationSuccessUpdate() {
        Product original = createTestProduct();
        productCurator.create(original);
        assertTrue(original.getUuid() != null);
        original.setAttribute("product.count", "134");
        original.setAttribute("product.pos_count", "333");
        original.setAttribute("product.long_multiplier", (new Long(Integer.MAX_VALUE * 100)).toString());
        original.setAttribute("product.long_pos_count", "10");
        original.setAttribute("product.bool_val_str", "false");
        original.setAttribute("product.bool_val_num", "1");
        productCurator.merge(original);
    }

    @Test(expected = PropertyValidationException.class)
    public void testProductAttributeCreationFailBadInt() {
        Product original = createTestProduct();
        original.setAttribute("product.count", "1.0");
        productCurator.create(original);
    }

    @Test
    public void testProductAttributeCreationSuccessZeroInt() {
        Product original = createTestProduct();
        original.setAttribute("product.pos_count", "0");
        productCurator.create(original);
    }

    @Test(expected = PropertyValidationException.class)
    public void testProductAttributeCreationFailBadPosInt() {
        Product original = createTestProduct();
        original.setAttribute("product.pos_count", "-5");
        productCurator.create(original);
    }

    @Test(expected = PropertyValidationException.class)
    public void testProductAttributeCreationFailBadLong() {
        Product original = createTestProduct();
        original.setAttribute("product.long_multiplier", "ZZ");
        productCurator.create(original);
    }

    @Test(expected = PropertyValidationException.class)
    public void testProductAttributeCreationFailBadPosLong() {
        Product original = createTestProduct();
        original.setAttribute("product.long_pos_count", "-1");
        productCurator.create(original);
    }

    @Test(expected = PropertyValidationException.class)
    public void testProductAttributeCreationFailBadStringBool() {
        Product original = createTestProduct();
        original.setAttribute("product.bool_val_str", "yes");
        productCurator.create(original);
    }

    @Test(expected = PropertyValidationException.class)
    public void testProductAttributeCreationFailNumberBool() {
        Product original = createTestProduct();
        original.setAttribute("product.bool_val_num", "2");
        productCurator.create(original);
    }

    @Test(expected = PropertyValidationException.class)
    public void testProductAttributeUpdateFailInt() {
        Product original = createTestProduct();
        productCurator.create(original);
        assertTrue(original.getUuid() != null);
        original.setAttribute("product.count", "one");
        productCurator.merge(original);
    }

    @Test(expected = PropertyValidationException.class)
    public void testProductAttributeUpdateFailPosInt() {
        Product original = createTestProduct();
        productCurator.create(original);
        assertTrue(original.getUuid() != null);
        original.setAttribute("product.pos_count", "-44");
        productCurator.merge(original);
    }

    @Test
    public void testProductAttributeUpdateSuccessZeroInt() {
        Product original = createTestProduct();
        productCurator.create(original);
        assertTrue(original.getUuid() != null);
        original.setAttribute("product.pos_count", "0");
        productCurator.merge(original);
    }

    @Test(expected = PropertyValidationException.class)
    public void testProductAttributeUpdateFailLong() {
        Product original = createTestProduct();
        productCurator.create(original);
        assertTrue(original.getUuid() != null);
        original.setAttribute("product.long_multiplier", "10^23");
        productCurator.merge(original);
    }

    @Test(expected = PropertyValidationException.class)
    public void testProductAttributeUpdateFailPosLong() {
        Product original = createTestProduct();
        productCurator.create(original);
        assertTrue(original.getUuid() != null);
        original.setAttribute("product.long_pos_count", "-23");
        productCurator.merge(original);
    }

    @Test(expected = PropertyValidationException.class)
    public void testProductAttributeUpdateFailStringBool() {
        Product original = createTestProduct();
        productCurator.create(original);
        assertTrue(original.getUuid() != null);
        original.setAttribute("product.bool_val_str", "flase");
        productCurator.merge(original);
    }

    @Test(expected = PropertyValidationException.class)
    public void testProductAttributeUpdateFailNumberBool() {
        Product original = createTestProduct();
        productCurator.create(original);
        assertTrue(original.getUuid() != null);
        original.setAttribute("product.bool_val_num", "6");
        productCurator.merge(original);
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
    public void ensureProvidedProductHasSubscription() {
        assertTrue(productCurator.productHasSubscriptions(owner, providedProduct));
    }

    @Test
    public void ensureDerivedProductHasSubscription() {
        assertTrue(productCurator.productHasSubscriptions(owner, derivedProduct));
    }

    @Test
    public void ensureDerivedProvidedProductHasSubscription() {
        assertTrue(productCurator.productHasSubscriptions(owner, derivedProvidedProduct));
    }

    @Test
    public void ensureDoesNotHaveSubscription() {
        Product noSub = this.createProduct("p1", "p1", owner);
        assertFalse(productCurator.productHasSubscriptions(owner, noSub));
    }

    @Test
    public void testSaveOrUpdateProductNoDuplicateProdContent() {
        // TODO:
        // This test may have lost meaning after the various changes and addition of the product
        // manager

        Product p = createTestProduct();
        Content content = TestUtil.createContent("best-content");

        p.addContent(content, true);
        contentCurator.create(content);
        productCurator.create(p);

        // Technically the same product:
        Product p2 = createTestProduct();
        p2.setUuid(p.getUuid());

        // The content isn't quite the same. We just care about matching
        // product ids with content ids
        Content contentUpdate = TestUtil.createContent("best-content");
        contentUpdate.setUuid(content.getUuid());
        contentUpdate.setGpgUrl("different");

        p2.addContent(contentUpdate, true);
        productCurator.merge(p2);

        Product result = productCurator.get(p.getUuid());
        assertEquals(1, result.getProductContent().size());
    }

    @Test
    public void testGetHydratedProductsByUuid() {
        Product prod = TestUtil.createProduct("test-label-hydrated", "test-product-name-hydrated");
        productCurator.create(prod);
        prod.setAttribute("testattr", "testVal");

        Set<String> uuids = new HashSet<>();
        uuids.add(prod.getUuid());
        uuids.add(product.getUuid());

        Map<String, Product> products = productCurator.getHydratedProductsByUuid(uuids);
        assertEquals(2, products.size());
    }


    @Test
    public void testPoolProvidedProducts() {
        Set<String> uuids = productCurator.getPoolProvidedProductUuids(pool.getId());
        assertEquals(new HashSet<>(Arrays.asList(providedProduct.getUuid())), uuids);
    }

    @Test
    public void testDerivedPoolProvidedProducts() {
        Set<String> uuids = productCurator.getDerivedPoolProvidedProductUuids(pool.getId());
        assertEquals(new HashSet<>(Arrays.asList(derivedProvidedProduct.getUuid())), uuids);
    }
}
