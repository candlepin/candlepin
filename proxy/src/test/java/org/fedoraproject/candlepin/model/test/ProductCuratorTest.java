/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.model.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.PersistenceException;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductAttribute;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.junit.Test;

public class ProductCuratorTest extends DatabaseTestFixture {

    @Test
    @SuppressWarnings("unchecked")
    public void normalCreate() {

        Product prod = new Product("cptest-label", "My Product");
        productCurator.create(prod);

        List<Product> results = entityManager().createQuery(
                "select p from Product as p").getResultList();
        assertEquals(1, results.size());
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

    @Test(expected = PersistenceException.class)
    public void nameUnique() {

        Product prod = new Product("label1", "name");
        productCurator.create(prod);

        Product prod2 = new Product("label2", "name");
        productCurator.create(prod2);
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

        assertEquals(new Long(4), product.getMultiplier());
    }

    @Test
    public void setMultiplierNull() {
        Product product = new Product("test", "Test Product");
        product.setMultiplier(null);

        assertEquals(new Long(1), product.getMultiplier());
    }

    @Test
    public void setMultiplierNegative() {
        Product product = new Product("test", "Test Product");
        product.setMultiplier(-15L);

        assertEquals(new Long(1), product.getMultiplier());
    }

    private Product createTestProduct() {
        Product p = new Product("testProductId", "Test Product");

        ProductAttribute a1 = new ProductAttribute("a1", "a1");
        p.addAttribute(a1);

        ProductAttribute a2 = new ProductAttribute("a2", "a2");
        p.addAttribute(a2);

        ProductAttribute a3 = new ProductAttribute("a3", "a3");
        p.addAttribute(a3);

        p.setMultiplier(new Long(1));
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
        newAttributes.add(a3);
        ProductAttribute a4 = new ProductAttribute("a4", "a4");
        newAttributes.add(a4);
        modified.setAttributes(newAttributes);

        productCurator.createOrUpdate(modified);

        Product lookedUp = productCurator.lookupById(original.getId());
        assertEquals(newName, lookedUp.getName());
        assertEquals(3, lookedUp.getAttributes().size());
        assertEquals("a1", lookedUp.getAttributeValue("a1"));
        assertEquals("a3-modified", lookedUp.getAttributeValue("a3"));
        assertEquals("a4", lookedUp.getAttributeValue("a4"));

        // TODO: test content merging

        // TODO: test attribute cleanup:
        List<ProductAttribute> all = attributeCurator.listAll();
        for (ProductAttribute a : all) {
            System.out.println(a);
        }
        // Old attributes should get cleaned up:
        assertEquals(3, all.size());
    }
}
