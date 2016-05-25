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

import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.config.ConfigProperties;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
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

    @Inject private OwnerCurator ownerCurator;
    @Inject private ProductCurator productCurator;
    @Inject private ProductAttributeCurator attributeCurator;
    @Inject private PoolCurator poolCurator;
    @Inject private ContentCurator contentCurator;
    @Inject private Configuration config;

    private Owner owner;
    private Product product;
    private Product derivedProduct;
    private Product providedProduct;
    private Product derivedProvidedProduct;

    private Pool pool;

    @Before
    public void setUp() {
        config.setProperty(ConfigProperties.INTEGER_ATTRIBUTES,
            "product.count, product.multiplier");
        config.setProperty(ConfigProperties.NON_NEG_INTEGER_ATTRIBUTES,
            "product.pos_count");
        config.setProperty(ConfigProperties.LONG_ATTRIBUTES,
            "product.long_count, product.long_multiplier");
        config.setProperty(ConfigProperties.NON_NEG_LONG_ATTRIBUTES,
            "product.long_pos_count");
        config.setProperty(ConfigProperties.BOOLEAN_ATTRIBUTES,
            "product.bool_val_str, product.bool_val_num");

        this.owner = createOwner();
        ownerCurator.create(owner);

        product = TestUtil.createProduct(owner);
        productCurator.create(product);

        providedProduct = TestUtil.createProduct(owner);
        productCurator.create(providedProduct);

        Set<Product> providedProducts = new HashSet<Product>();
        providedProducts.add(providedProduct);

        derivedProduct = TestUtil.createProduct(owner);
        productCurator.create(derivedProduct);

        derivedProvidedProduct = TestUtil.createProduct(owner);
        productCurator.create(derivedProvidedProduct);

        Set<Product> derivedProvidedProducts = new HashSet<Product>();
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

        Product prod = new Product("cptest-label", "My Product", owner);
        productCurator.create(prod);

        List<Product> results = entityManager().createQuery(
            "select p from Product as p").getResultList();
        assertEquals(5, results.size());
    }

    @Test(expected = PersistenceException.class)
    public void nameRequired() {

        Product prod = new Product("someproductlabel", null, owner);
        productCurator.create(prod);

    }

    @Test(expected = ConstraintViolationException.class)
    public void labelRequired() {

        Product prod = new Product(null, "My Product Name", owner);
        productCurator.create(prod);
    }

    @Test
    public void nameNonUnique() {

        Product prod = new Product("label1", "name", owner);
        productCurator.create(prod);

        Product prod2 = new Product("label2", "name", owner);
        productCurator.create(prod2);

        assertEquals(prod.getName(), prod2.getName());
        assertFalse(prod.getUuid().equals(prod2.getUuid()));
    }

    @Test
    public void testEquality() {
        assertEquals(new Product("label", "name", owner), new Product("label", "name", owner));
        assertFalse(new Product("label", "name", owner).equals(null));
        assertFalse(new Product("label", "name", owner).equals(new Product("label",
            "another_name", owner)));
        assertFalse(new Product("label", "name", owner).equals(new Product(
            "another_label", "name", owner)));
    }

    @Test
    public void testWithSimpleJsonAttribute() throws Exception {
        Map<String, String> data = new HashMap<String, String>();
        data.put("a", "1");
        data.put("b", "2");
        ObjectMapper mapper = new ObjectMapper();
        String jsonData = mapper.writeValueAsString(data);

        Product prod = new Product("cptest-label", "My Product", owner);
        ProductAttribute a = new ProductAttribute("content_sets", jsonData);
        prod.addAttribute(a);
        productCurator.create(prod);
        attributeCurator.create(a);

        Product lookedUp = productCurator.find(prod.getUuid());
        assertEquals(jsonData, lookedUp.getAttribute("content_sets").getValue());

        data = mapper.readValue(lookedUp.getAttribute("content_sets").getValue(),
            new TypeReference<Map<String, String>>(){});
        assertEquals("1", data.get("a"));
        assertEquals("2", data.get("b"));
    }

    @Test
    public void testJsonListOfHashes() throws Exception {
        List<Map<String, String>> data = new LinkedList<Map<String, String>>();
        Map<String, String> contentSet1 = new HashMap<String, String>();
        contentSet1.put("name", "cs1");
        contentSet1.put("url", "url");

        Map<String, String> contentSet2 = new HashMap<String, String>();
        contentSet2.put("name", "cs2");
        contentSet2.put("url", "url2");

        data.add(contentSet1);
        data.add(contentSet2);

        ObjectMapper mapper = new ObjectMapper();
        String jsonData = mapper.writeValueAsString(data);

        Product prod = new Product("cptest-label", "My Product", owner);
        ProductAttribute a = new ProductAttribute("content_sets", jsonData);
        prod.addAttribute(a);
        productCurator.create(prod);
        attributeCurator.create(a);

        Product lookedUp = productCurator.find(prod.getUuid());
        assertEquals(jsonData, lookedUp.getAttribute("content_sets").getValue());

        data = mapper.readValue(lookedUp.getAttribute("content_sets").getValue(),
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
        Product prod = new Product("test-label", "test-product-name", owner);
        productCurator.create(prod);

        assertNotNull(prod.getCreated());
    }

    @Test
    public void testInitialUpdate() {
        Product prod = new Product("test-label", "test-product-name", owner);
        productCurator.create(prod);

        assertNotNull(prod.getUpdated());
    }

    @Test
    public void testDependentProducts() {
        Product prod = new Product("test-label", "test-product-name", owner);
        HashSet<String> dependentProductIds = new HashSet<String>();
        dependentProductIds.add("ProductX");
        prod.setDependentProductIds(dependentProductIds);
        productCurator.create(prod);

        Product lookedUp = productCurator.find(prod.getUuid());
        assertThat(lookedUp.getDependentProductIds(), hasItem("ProductX"));
    }

    /**
     * Test whether the product updation date is updated when merging.
     */
    @Test
    public void testSubsequentUpdate() {
        Product prod = new Product("test-label", "test-product-name", owner);
        productCurator.create(prod);

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -2);
        prod.setUpdated(calendar.getTime());

        long updated = prod.getUpdated().getTime();

        prod.setName("test-changed-name");
        prod = this.productCurator.merge(prod);
        assertTrue(prod.getUpdated().getTime() > updated);
    }


    @Test
    public void testProductFullConstructor() {
        Product prod = new Product("cp_test-label", "variant", owner, "version", "arch", "", "SVC");
        productCurator.create(prod);

        productCurator.find(prod.getUuid());
    }

    @Test
    public void setMultiplierBasic() {
        Product product = new Product("test", "Test Product", owner);
        product.setMultiplier(4L);

        assertEquals(Long.valueOf(4), product.getMultiplier());
    }

    @Test
    public void setMultiplierNull() {
        Product product = new Product("test", "Test Product", owner);
        product.setMultiplier(null);

        assertEquals(Long.valueOf(1), product.getMultiplier());
    }

    @Test
    public void setMultiplierNegative() {
        Product product = new Product("test", "Test Product", owner);
        product.setMultiplier(-15L);

        assertEquals(Long.valueOf(1), product.getMultiplier());
    }

    private Product createTestProduct() {
        Product p = TestUtil.createProduct("testProductId", "Test Product", owner);

        ProductAttribute a1 = new ProductAttribute("a1", "a1");
        p.addAttribute(a1);

        ProductAttribute a2 = new ProductAttribute("a2", "a2");
        p.addAttribute(a2);

        ProductAttribute a3 = new ProductAttribute("a3", "a3");
        p.addAttribute(a3);

        p.setMultiplier(1L);
        return p;
    }

    @Test
    public void testUpdateProduct() {
        Product original = createTestProduct();
        productCurator.create(original);

        Product modified = productCurator.lookupById(owner, original.getId());
        String newName = "new name";
        modified.setName(newName);

        // Hack up the attributes, keep a1, skip a2, modify a3, add a4:
        Set<ProductAttribute> newAttributes = new HashSet<ProductAttribute>();
        newAttributes.add(modified.getAttribute("a1"));
        ProductAttribute a3 = modified.getAttribute("a3");
        a3.setValue("a3-modified");
        a3.setProduct(modified);
        newAttributes.add(a3);
        ProductAttribute a4 = new ProductAttribute("a4", "a4");
        a4.setProduct(modified);
        newAttributes.add(a4);
        modified.setAttributes(newAttributes);

        int initialAttrCount = attributeCurator.listAll().size();
        productCurator.merge(modified);

        Product lookedUp = productCurator.lookupById(owner, original.getId());
        assertEquals(newName, lookedUp.getName());
        assertEquals(3, lookedUp.getAttributes().size());
        assertEquals("a1", lookedUp.getAttributeValue("a1"));
        assertEquals("a3-modified", lookedUp.getAttributeValue("a3"));
        assertEquals("a4", lookedUp.getAttributeValue("a4"));

        // TODO: test content merging

        // Old attributes should get cleaned up:
        assertEquals(initialAttrCount, attributeCurator.listAll().size());
    }

    @Test
    public void testProductAttributeValidationSuccessCreate() {
        Product original = createTestProduct();
        original.addAttribute(new ProductAttribute("product.count", "1"));
        original.addAttribute(new ProductAttribute("product.pos_count", "5"));
        original.addAttribute(new ProductAttribute("product.long_multiplier",
            (new Long(Integer.MAX_VALUE * 1000)).toString()));
        original.addAttribute(new ProductAttribute("product.long_pos_count", "23"));
        original.addAttribute(new ProductAttribute("product.bool_val_str", "true"));
        original.addAttribute(new ProductAttribute("product.bool_val_num", "0"));
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
        original.setAttribute("product.long_multiplier",
            (new Long(Integer.MAX_VALUE * 100)).toString());
        original.setAttribute("product.long_pos_count", "10");
        original.setAttribute("product.bool_val_str", "false");
        original.setAttribute("product.bool_val_num", "1");
        productCurator.merge(original);
    }

    @Test(expected = BadRequestException.class)
    public void testProductAttributeCreationFailBadInt() {
        Product original = createTestProduct();
        original.addAttribute(new ProductAttribute("product.count", "1.0"));
        productCurator.create(original);
    }

    @Test
    public void testProductAttributeCreationSuccessZeroInt() {
        Product original = createTestProduct();
        original.addAttribute(new ProductAttribute("product.pos_count", "0"));
        productCurator.create(original);
    }

    @Test(expected = BadRequestException.class)
    public void testProductAttributeCreationFailBadPosInt() {
        Product original = createTestProduct();
        original.addAttribute(new ProductAttribute("product.pos_count", "-5"));
        productCurator.create(original);
    }

    @Test(expected = BadRequestException.class)
    public void testProductAttributeCreationFailBadLong() {
        Product original = createTestProduct();
        original.addAttribute(new ProductAttribute("product.long_multiplier",
            "ZZ"));
        productCurator.create(original);
    }

    @Test(expected = BadRequestException.class)
    public void testProductAttributeCreationFailBadPosLong() {
        Product original = createTestProduct();
        original.addAttribute(new ProductAttribute("product.long_pos_count",
            "-1"));
        productCurator.create(original);
    }

    @Test(expected = BadRequestException.class)
    public void testProductAttributeCreationFailBadStringBool() {
        Product original = createTestProduct();
        original.addAttribute(new ProductAttribute("product.bool_val_str", "yes"));
        productCurator.create(original);
    }

    @Test(expected = BadRequestException.class)
    public void testProductAttributeCreationFailNumberBool() {
        Product original = createTestProduct();
        original.addAttribute(new ProductAttribute("product.bool_val_num", "2"));
        productCurator.create(original);
    }

    @Test(expected = BadRequestException.class)
    public void testProductAttributeUpdateFailInt() {
        Product original = createTestProduct();
        productCurator.create(original);
        assertTrue(original.getUuid() != null);
        original.addAttribute(new ProductAttribute("product.count", "one"));
        productCurator.merge(original);
    }

    @Test(expected = BadRequestException.class)
    public void testProductAttributeUpdateFailPosInt() {
        Product original = createTestProduct();
        productCurator.create(original);
        assertTrue(original.getUuid() != null);
        original.addAttribute(new ProductAttribute("product.pos_count", "-44"));
        productCurator.merge(original);
    }

    @Test
    public void testProductAttributeUpdateSuccessZeroInt() {
        Product original = createTestProduct();
        productCurator.create(original);
        assertTrue(original.getUuid() != null);
        original.addAttribute(new ProductAttribute("product.pos_count", "0"));
        productCurator.merge(original);
    }

    @Test(expected = BadRequestException.class)
    public void testProductAttributeUpdateFailLong() {
        Product original = createTestProduct();
        productCurator.create(original);
        assertTrue(original.getUuid() != null);
        original.addAttribute(new ProductAttribute("product.long_multiplier",
            "10^23"));
        productCurator.merge(original);
    }

    @Test(expected = BadRequestException.class)
    public void testProductAttributeUpdateFailPosLong() {
        Product original = createTestProduct();
        productCurator.create(original);
        assertTrue(original.getUuid() != null);
        original.addAttribute(new ProductAttribute("product.long_pos_count",
            "-23"));
        productCurator.merge(original);
    }

    @Test(expected = BadRequestException.class)
    public void testProductAttributeUpdateFailStringBool() {
        Product original = createTestProduct();
        productCurator.create(original);
        assertTrue(original.getUuid() != null);
        original.addAttribute(new ProductAttribute("product.bool_val_str", "flase"));
        productCurator.merge(original);
    }

    @Test(expected = BadRequestException.class)
    public void testProductAttributeUpdateFailNumberBool() {
        Product original = createTestProduct();
        productCurator.create(original);
        assertTrue(original.getUuid() != null);
        original.addAttribute(new ProductAttribute("product.bool_val_num", "6"));
        productCurator.merge(original);
    }

    @Test
    public void testSubstringConfigList() {
        Product original = createTestProduct();
        original.addAttribute(new ProductAttribute("product.pos", "-5"));
        productCurator.create(original);
    }

    @Test
    public void listByIds() {
        List<Product> products = new ArrayList<Product>();
        List<String> pids = new ArrayList<String>();
        for (int i = 0; i < 5; i++) {
            Product p = TestUtil.createProduct(owner);
            productCurator.create(p);
            products.add(p);
            pids.add(p.getId());
        }

        // ok get first 3 items to lookup
        List<Product> returned = productCurator.listAllByIds(owner, pids.subList(0, 3));
        assertEquals(3, returned.size());

        // verify the first 3 were actually returned, and only those 3.
        assertTrue(returned.contains(products.get(0)));
        assertTrue(returned.contains(products.get(1)));
        assertTrue(returned.contains(products.get(2)));
        assertFalse(returned.contains(products.get(3)));
        assertFalse(returned.contains(products.get(4)));
    }

    @Test
    public void testGetProductIdFromContentId() {
        Product p = createTestProduct();
        Content content = new Content(this.owner, "best-content", "best-content",
            "best-content", "yum", "us", "here", "here", "test-arch");
        p.addContent(content);
        contentCurator.create(content);
        productCurator.create(p);

        List<String> contentIds = new LinkedList<String>();
        contentIds.add(content.getId());
        List<Product> products = productCurator.getProductsWithContent(owner, contentIds);
        assertEquals(1, products.size());
        assertEquals(p, products.get(0));
    }

    @Test
    public void testGetProductIdFromContentUuid() {
        Product p = createTestProduct();
        Content content = new Content(this.owner, "best-content", "best-content",
            "best-content", "yum", "us", "here", "here", "test-arch");
        p.addContent(content);
        contentCurator.create(content);
        productCurator.create(p);

        List<String> contentUuids = new LinkedList<String>();
        contentUuids.add(content.getUuid());
        List<Product> products = productCurator.getProductsWithContent(contentUuids);
        assertEquals(1, products.size());
        assertEquals(p, products.get(0));
    }

    @Test
    public void ensureProductHasSubscription() {
        assertTrue(productCurator.productHasSubscriptions(product, owner));
    }

    @Test
    public void ensureProvidedProductHasSubscription() {
        assertTrue(productCurator.productHasSubscriptions(providedProduct, owner));
    }

    @Test
    public void ensureDerivedProductHasSubscription() {
        assertTrue(productCurator.productHasSubscriptions(derivedProduct, owner));
    }

    @Test
    public void ensureDerivedProvidedProductHasSubscription() {
        assertTrue(productCurator.productHasSubscriptions(derivedProvidedProduct, owner));
    }

    @Test
    public void ensureDoesNotHaveSubscription() {
        Product doesNotHave = TestUtil.createProduct(owner);
        productCurator.create(doesNotHave);
        assertFalse(productCurator.productHasSubscriptions(doesNotHave, owner));
    }

    @Test
    public void testSaveOrUpdateProductNoDuplicateProdContent() {
        Product p = createTestProduct();
        Content content = new Content(this.owner, "best-content", "best-content",
            "best-content", "yum", "us", "here", "here", "test-arch"
        );

        p.addContent(content);
        contentCurator.create(content);
        productCurator.create(p);

        // Technically the same product:
        Product p2 = createTestProduct();
        p2.setUuid(p.getUuid());

        // The content isn't quite the same. We just care about matching
        // product ids with content ids
        Content contentUpdate = new Content(this.owner, "best-content", "best-content",
            "best-content", "yum", "us", "here", "differnet", "test-arch"
        );

        contentUpdate.setUuid(content.getUuid());
        this.contentCurator.create(content);

        p2.addContent(contentUpdate);
        productCurator.merge(p2);

        Product result = productCurator.find(p.getUuid());
        assertEquals(1, result.getProductContent().size());
    }
}
