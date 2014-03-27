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
package org.candlepin.model.test;

import static org.hamcrest.collection.IsCollectionContaining.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.Content;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.model.Subscription;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.PersistenceException;

public class ProductCuratorTest extends DatabaseTestFixture {

    private CandlepinCommonTestConfig config = null;

    private Product product;
    private Product derivedProduct;
    private Product providedProduct;
    private Product derivedProvidedProduct;

    private Subscription sub;

    @Before
    public void setUp() {
        config = (CandlepinCommonTestConfig) injector.getInstance(Config.class);
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

        Owner owner = createOwner();
        ownerCurator.create(owner);

        product = TestUtil.createProduct();
        productCurator.create(product);

        providedProduct = TestUtil.createProduct();
        productCurator.create(providedProduct);

        Set<Product> providedProducts = new HashSet<Product>();
        providedProducts.add(providedProduct);

        derivedProduct = TestUtil.createProduct();
        productCurator.create(derivedProduct);

        derivedProvidedProduct = TestUtil.createProduct();
        productCurator.create(derivedProvidedProduct);

        Set<Product> derivedProvidedProducts = new HashSet<Product>();
        derivedProvidedProducts.add(derivedProvidedProduct);

        sub = new Subscription(owner, product, providedProducts, 16L,
            TestUtil.createDate(2006, 10, 21), TestUtil.createDate(2020, 1, 1), new Date());
        sub.setDerivedProduct(derivedProduct);
        sub.setDerivedProvidedProducts(derivedProvidedProducts);
        subCurator.create(sub);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void normalCreate() {

        Product prod = new Product("cptest-label", "My Product");
        productCurator.create(prod);

        List<Product> results = entityManager().createQuery(
                "select p from Product as p").getResultList();
        assertEquals(5, results.size());
    }

    @Test(expected = PersistenceException.class)
    public void nameRequired() {

        Product prod = new Product("someproductlabel", null);
        productCurator.create(prod);

    }

    @Test(expected = PersistenceException.class)
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
        assertFalse(prod.getId().equals(prod2.getId()));
    }

    @Test(expected = PersistenceException.class)
    public void labelUnique() {

        Product prod = new Product("label1", "name");
        Product prod2 = new Product("label1", "name2");
        productCurator.create(prod);

        productCurator.create(prod2);
    }

    @Test
    public void testEquality() {
        assertEquals(new Product("label", "name"), new Product("label", "name"));
        assertFalse(new Product("label", "name").equals(null));
        assertFalse(new Product("label", "name").equals(new Product("label",
                "another_name")));
        assertFalse(new Product("label", "name").equals(new Product(
                "another_label", "name")));
    }

    @Test
    public void testWithSimpleJsonAttribute() throws Exception {
        Map<String, String> data = new HashMap<String, String>();
        data.put("a", "1");
        data.put("b", "2");
        ObjectMapper mapper = new ObjectMapper();
        String jsonData = mapper.writeValueAsString(data);

        Product prod = new Product("cptest-label", "My Product");
        ProductAttribute a = new ProductAttribute("content_sets", jsonData);
        prod.addAttribute(a);
        productCurator.create(prod);
        attributeCurator.create(a);

        Product lookedUp = productCurator.find(prod.getId());
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

        Product prod = new Product("cptest-label", "My Product");
        ProductAttribute a = new ProductAttribute("content_sets", jsonData);
        prod.addAttribute(a);
        productCurator.create(prod);
        attributeCurator.create(a);

        Product lookedUp = productCurator.find(prod.getId());
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
        Product prod = new Product("test-label", "test-product-name");
        productCurator.create(prod);

        assertNotNull(prod.getCreated());
    }

    @Test
    public void testInitialUpdate() {
        Product prod = new Product("test-label", "test-product-name");
        productCurator.create(prod);

        assertNotNull(prod.getUpdated());
    }

    @Test
    public void testDependentProducts() {
        Product prod = new Product("test-label", "test-product-name");
        HashSet<String> dependentProductIds = new HashSet<String>();
        dependentProductIds.add("ProductX");
        prod.setDependentProductIds(dependentProductIds);
        productCurator.create(prod);

        Product lookedUp = productCurator.find(prod.getId());
        assertThat(lookedUp.getDependentProductIds(), hasItem("ProductX"));

    }

    @Test
    public void testFullReliantProducts() {
        Product prod = new Product("test-label", "test-product-name");
        HashSet<String> reliantProductIds = new HashSet<String>();
        reliantProductIds.add("ProductX");
        reliantProductIds.add("ProductY");
        prod.setReliesOn(reliantProductIds);
        productCurator.create(prod);

        Product lookedUp = productCurator.find(prod.getId());
        assertTrue(lookedUp.getReliesOn().contains("ProductX"));
        assertTrue(lookedUp.getReliesOn().contains("ProductY"));
    }

    @Test
    public void testAddReliantProducts() {
        Product prod = new Product("test-label", "test-product-name");
        HashSet<String> reliantProductIds = new HashSet<String>();
        reliantProductIds.add("ProductX");
        prod.setReliesOn(reliantProductIds);
        productCurator.create(prod);
        Product lookedUp = productCurator.find(prod.getId());
        assertTrue(lookedUp.getReliesOn().contains("ProductX"));

        prod.addRely("ProductY");
        productCurator.merge(prod);
        lookedUp = productCurator.find(prod.getId());
        assertTrue(lookedUp.getReliesOn().contains("ProductX"));
        assertTrue(lookedUp.getReliesOn().contains("ProductY"));
    }

    @Test
    public void testRemoveReliantProducts() {
        Product prod = new Product("test-label", "test-product-name");
        HashSet<String> reliantProductIds = new HashSet<String>();
        reliantProductIds.add("ProductX");
        reliantProductIds.add("ProductY");
        reliantProductIds.add("ProductZ");
        prod.setReliesOn(reliantProductIds);
        productCurator.create(prod);
        Product lookedUp = productCurator.find(prod.getId());
        assertTrue(lookedUp.getReliesOn().contains("ProductX"));
        assertTrue(lookedUp.getReliesOn().contains("ProductY"));
        assertTrue(lookedUp.getReliesOn().contains("ProductZ"));

        prod.removeRely("ProductY");
        productCurator.merge(prod);
        lookedUp = productCurator.find(prod.getId());
        assertTrue(lookedUp.getReliesOn().contains("ProductX"));
        assertTrue(!lookedUp.getReliesOn().contains("ProductY"));
        assertTrue(lookedUp.getReliesOn().contains("ProductZ"));
    }

    @Test(expected = BadRequestException.class)
    public void testCircularReliantProductsOne() {
        Product prod = new Product("test-label", "test-product-name");
        HashSet<String> reliantProductIds = new HashSet<String>();
        reliantProductIds.add("ProductX");
        reliantProductIds.add("ProductY");
        reliantProductIds.add("test-label");
        prod.setReliesOn(reliantProductIds);
        productCurator.create(prod);
    }

    @Test(expected = BadRequestException.class)
    public void testCircularReliantProductsTwo() {
        Product prod1 = new Product("test-label-1", "test-product-name-1");
        HashSet<String> reliantProductIds1 = new HashSet<String>();
        reliantProductIds1.add("test-label-2");
        prod1.setReliesOn(reliantProductIds1);
        try {
            productCurator.create(prod1);
        }
        catch (BadRequestException bre) {
            fail();
        }

        Product prod2 = new Product("test-label-2", "test-product-name-2");
        HashSet<String> reliantProductIds2 = new HashSet<String>();
        reliantProductIds2.add("test-label-1");
        prod2.setReliesOn(reliantProductIds2);
        productCurator.create(prod2);
    }

    @Test(expected = BadRequestException.class)
    public void testCircularReliantProductsThree() {
        Product prod1 = new Product("test-label-1", "test-product-name-1");
        HashSet<String> reliantProductIds1 = new HashSet<String>();
        reliantProductIds1.add("test-label-2");
        prod1.setReliesOn(reliantProductIds1);
        try {
            productCurator.create(prod1);
        }
        catch (BadRequestException bre) {
            fail();
        }

        Product prod3 = new Product("test-label-3", "test-product-name-3");
        HashSet<String> reliantProductIds3 = new HashSet<String>();
        reliantProductIds3.add("test-label-1");
        prod3.setReliesOn(reliantProductIds3);
        try {
            productCurator.create(prod3);
        }
        catch (BadRequestException bre) {
            fail();
        }

        Product prod2 = new Product("test-label-2", "test-product-name-2");
        HashSet<String> reliantProductIds2 = new HashSet<String>();
        reliantProductIds2.add("test-label-3");
        prod2.setReliesOn(reliantProductIds2);
        productCurator.create(prod2);
    }

    @Test(expected = BadRequestException.class)
    public void testCircularReliantProductsUpdate() {
        Product prod1 = new Product("test-label-1", "test-product-name-1");
        try {
            productCurator.create(prod1);
            prod1.addRely("test-label-2");
            productCurator.merge(prod1);
        }
        catch (BadRequestException bre) {
            fail();
        }

        Product prod3 = new Product("test-label-3", "test-product-name-3");
        try {
            productCurator.create(prod3);
            prod3.addRely("test-label-1");
            productCurator.merge(prod3);
        }
        catch (BadRequestException bre) {
            fail();
        }

        Product prod2 = new Product("test-label-2", "test-product-name-2");
        try {
            productCurator.create(prod2);
        }
        catch (BadRequestException bre) {
            fail();
        }
        prod2.addRely("test-label-3");
        productCurator.merge(prod2);
    }

    /**
     * Test whether the product updation date is updated when merging.
     */
    @Test
    public void testSubsequentUpdate() {
        Product prod = new Product("test-label", "test-product-name");
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
        Product prod = new Product("cp_test-label", "variant",
                                   "version", "arch", "", "SVC");
        productCurator.create(prod);

        productCurator.find(prod.getId());
    }

    @Test
    public void setMultiplierBasic() {
        Product product = new Product("test", "Test Product");
        product.setMultiplier(4L);

        assertEquals(Long.valueOf(4), product.getMultiplier());
    }

    @Test
    public void setMultiplierNull() {
        Product product = new Product("test", "Test Product");
        product.setMultiplier(null);

        assertEquals(Long.valueOf(1), product.getMultiplier());
    }

    @Test
    public void setMultiplierNegative() {
        Product product = new Product("test", "Test Product");
        product.setMultiplier(-15L);

        assertEquals(Long.valueOf(1), product.getMultiplier());
    }

    private Product createTestProduct() {
        Product p = TestUtil.createProduct("testProductId", "Test Product");

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
        // Will have same ID, but we'll modify other data:
        Product modified = createTestProduct();
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
        productCurator.createOrUpdate(modified);

        Product lookedUp = productCurator.lookupById(original.getId());
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
        assertTrue(original.getId() != null);
    }

    @Test
    public void testProductAttributeValidationSuccessUpdate() {
        Product original = createTestProduct();
        productCurator.create(original);
        assertTrue(original.getId() != null);
        original.setAttribute("product.count", "134");
        original.setAttribute("product.pos_count", "333");
        original.setAttribute("product.long_multiplier",
            (new Long(Integer.MAX_VALUE * 100)).toString());
        original.setAttribute("product.long_pos_count", "10");
        original.setAttribute("product.bool_val_str", "false");
        original.setAttribute("product.bool_val_num", "1");
        productCurator.createOrUpdate(original);
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
        assertTrue(original.getId() != null);
        original.addAttribute(new ProductAttribute("product.count", "one"));
        productCurator.createOrUpdate(original);
    }

    @Test(expected = BadRequestException.class)
    public void testProductAttributeUpdateFailPosInt() {
        Product original = createTestProduct();
        productCurator.create(original);
        assertTrue(original.getId() != null);
        original.addAttribute(new ProductAttribute("product.pos_count", "-44"));
        productCurator.createOrUpdate(original);
    }

    @Test
    public void testProductAttributeUpdateSuccessZeroInt() {
        Product original = createTestProduct();
        productCurator.create(original);
        assertTrue(original.getId() != null);
        original.addAttribute(new ProductAttribute("product.pos_count", "0"));
        productCurator.createOrUpdate(original);
    }

    @Test(expected = BadRequestException.class)
    public void testProductAttributeUpdateFailLong() {
        Product original = createTestProduct();
        productCurator.create(original);
        assertTrue(original.getId() != null);
        original.addAttribute(new ProductAttribute("product.long_multiplier",
            "10^23"));
        productCurator.createOrUpdate(original);
    }

    @Test(expected = BadRequestException.class)
    public void testProductAttributeUpdateFailPosLong() {
        Product original = createTestProduct();
        productCurator.create(original);
        assertTrue(original.getId() != null);
        original.addAttribute(new ProductAttribute("product.long_pos_count",
            "-23"));
        productCurator.createOrUpdate(original);
    }

    @Test(expected = BadRequestException.class)
    public void testProductAttributeUpdateFailStringBool() {
        Product original = createTestProduct();
        productCurator.create(original);
        assertTrue(original.getId() != null);
        original.addAttribute(new ProductAttribute("product.bool_val_str", "flase"));
        productCurator.createOrUpdate(original);
    }

    @Test(expected = BadRequestException.class)
    public void testProductAttributeUpdateFailNumberBool() {
        Product original = createTestProduct();
        productCurator.create(original);
        assertTrue(original.getId() != null);
        original.addAttribute(new ProductAttribute("product.bool_val_num", "6"));
        productCurator.createOrUpdate(original);
    }

    @Test
    public void testSubstringConfigList() {
        Product original = createTestProduct();
        original.addAttribute(new ProductAttribute("product.pos", "-5"));
        productCurator.create(original);
    }

    @Test
    public void testRemoveProductContent() {
        Product p = createTestProduct();
        Content content = new Content("test-content", "test-content",
            "test-content", "yum", "us", "here", "here", "test-arch");
        p.addContent(content);
        contentCurator.create(content);
        productCurator.create(p);

        p = productCurator.find(p.getId());
        assertEquals(1, p.getProductContent().size());

        productCurator.removeProductContent(p, content);
        p = productCurator.find(p.getId());
        assertEquals(0, p.getProductContent().size());
    }

    @Test
    public void listByIds() {
        List<Product> products = new ArrayList<Product>();
        List<String> pids = new ArrayList<String>();
        for (int i = 0; i < 5; i++) {
            Product p = TestUtil.createProduct();
            productCurator.create(p);
            products.add(p);
            pids.add(p.getId());
        }

        // ok get first 3 items to lookup
        List<Product> returned = productCurator.listAllByIds(pids.subList(0, 3));
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
        Content content = new Content("best-content", "best-content",
            "best-content", "yum", "us", "here", "here", "test-arch");
        p.addContent(content);
        contentCurator.create(content);
        productCurator.create(p);

        List<String> contentIds = new LinkedList<String>();
        contentIds.add(content.getId());
        List<String> productIds = productCurator.getProductIdsWithContent(contentIds);
        assertEquals(1, productIds.size());
        assertEquals(p.getId(), productIds.get(0));
    }

    @Test
    public void ensureProductHasSubscription() {
        assertTrue(productCurator.productHasSubscriptions(product));
    }

    @Test
    public void ensureProvidedProductHasSubscription() {
        assertTrue(productCurator.productHasSubscriptions(providedProduct));
    }

    @Test
    public void ensureDerivedProductHasSubscription() {
        assertTrue(productCurator.productHasSubscriptions(derivedProduct));
    }

    @Test
    public void ensureDerivedProvidedProductHasSubscription() {
        assertTrue(productCurator.productHasSubscriptions(derivedProvidedProduct));
    }

    @Test
    public void ensureDoesNotHaveSubscription() {
        Product doesNotHave = TestUtil.createProduct();
        productCurator.create(doesNotHave);
        assertFalse(productCurator.productHasSubscriptions(doesNotHave));
    }

}
