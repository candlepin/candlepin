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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

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
import org.fedoraproject.candlepin.model.Attribute;
import org.fedoraproject.candlepin.model.Content;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.junit.Test;

public class ProductTest extends DatabaseTestFixture {

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
    public void addChildProducts() {
        beginTransaction();
        Product parent = new Product("parent-product", "Parent Product");
        Product child1 = new Product("child-product1", "Child Product 1");
        Product child2 = new Product("child-product2", "Child Product 2");

        parent.addChildProduct(child1);
        parent.addChildProduct(child2);
        entityManager().persist(parent);
        commitTransaction();

        Product result = (Product) entityManager().createQuery(
                "select p from Product as p where name = :name").setParameter(
                "name", parent.getName()).getSingleResult();
        assertEquals(2, result.getChildProducts().size());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCascading() {
        beginTransaction();

        Product parent1 = new Product("parent-product1", "Parent Product 1");
        Product child1 = new Product("child-product1", "Child Product 1");
        parent1.addChildProduct(child1);
        entityManager().persist(parent1);
        commitTransaction();

        Product result = (Product) entityManager().createQuery(
                "select p from Product as p where name = :name").setParameter(
                "name", child1.getName()).getSingleResult();
        assertNotNull(result);

        beginTransaction();
        entityManager().remove(parent1);
        commitTransaction();

        List<Product> results = entityManager().createQuery(
                "select p from Product as p where name = :name").setParameter(
                "name", child1.getName()).getResultList();
        assertEquals(0, results.size());
    }
    
    @Test
    public void testUpdate() {
        Product product = new Product("test-product", "Test Product");
        Product updatedProduct = productCurator.update(product);
     
        assertEquals(product.getId(), updatedProduct.getId());
        assertEquals(product.getName(), updatedProduct.getName());
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
        Attribute a = new Attribute("content_sets", jsonData);
        prod.addAttribute(a);
        attributeCurator.create(a);
        productCurator.create(prod);
        
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
        System.out.println(jsonData);
        
        Product prod = new Product("cptest-label", "My Product");
        Attribute a = new Attribute("content_sets", jsonData);
        prod.addAttribute(a);
        attributeCurator.create(a);
        productCurator.create(prod);
        
        Product lookedUp = productCurator.find(prod.getId());
        assertEquals(jsonData, lookedUp.getAttribute("content_sets").getValue());
        
        data = mapper.readValue(lookedUp.getAttribute("content_sets").getValue(),
            new TypeReference<List<Map<String, String>>>(){});
        Map<String, String> cs1 = data.get(0);
        assertEquals("cs1", cs1.get("name"));
        
        Map<String, String> cs2 = data.get(1);
        assertEquals("cs2", cs2.get("name"));
    }

    @Test
    public void testProductWithContentSets() {
        // NOTE: Not using value on the Attributes which have children, but you
        // easily could, perhaps a string list of the children labels or
        // what not.
        Attribute contentSets = new Attribute("content_sets", "");
        for (int i = 0; i < 5; i++) {
            // assume family label as attribute name:
            Attribute channelFamily = new Attribute("channelfamilylabel" + i, "");
            channelFamily.addChildAttribute("family_id", "some family id");
            channelFamily.addChildAttribute("family_name", "some family name");
            channelFamily.addChildAttribute("flex_quantity", "5");
            channelFamily.addChildAttribute("physical_quantity", "10");

            // Now add the channels as a child of the channel family:
            Attribute channels = new Attribute("channels", "");
            for (int j = 0; j < 3; j++) {
                Attribute channel = new Attribute("channel" + j, ""); // assume channel ID?
                channel.addChildAttribute("channel_name", "chan name");
                channel.addChildAttribute("channel_desc", "description");
                channel.addChildAttribute("channel_basedir", "basedir");
                channels.addChildAttribute(channel);
            }

            // Finish the mapping:
            channelFamily.addChildAttribute(channels);
            contentSets.addChildAttribute(channelFamily);
        }

        Product prod = new Product("cptest-label", "My Product");
        prod.addAttribute(contentSets);
        productCurator.create(prod);

        Product lookedUp = productCurator.find(prod.getId());
        Attribute testing = lookedUp.getAttribute("content_sets");
        assertEquals(5, testing.getChildAttributes().size());
        testing = testing.getChildAttribute("channelfamilylabel0");
        testing = testing.getChildAttribute("channels");
        assertEquals(3, testing.getChildAttributes().size());
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
        Set<Product> products = new HashSet<Product>();
        Set<Content> content = new HashSet<Content>();
        Product prod = new Product("cp_test-label", "Test Product",
                                   "variant", "version", "arch",
                                   new Long(1111111), "SVC", products, content);
        productCurator.create(prod);
       
        Product lookedUp = productCurator.find(prod.getId());
    }
    
    @Test
    public void testProductChildProducts() {
        Set<Product> childProducts = new HashSet<Product>();
        Set<Product> products = new HashSet<Product>();
        Set<Content> content = new HashSet<Content>();
        
        String parentLabel = "cp_test_parent_product";
        String childLabel = "cp_test_child_product";

        Product childProd = new Product(childLabel, "Test Child Product",
                        "variant", "version", "arch",       
                        Math.abs(Long.valueOf(parentLabel.hashCode())), 
                        "SVC", products, content);
        
        childProducts.add(childProd);
        Product parentProd = new Product(parentLabel, "Test Parent Product",
                        "variant", "version", "arch",   
                        Math.abs(Long.valueOf(childLabel.hashCode())),
                        "MKT", childProducts, content);
      
        productCurator.create(parentProd);
        
        Set<Product> testProducts = new HashSet<Product>();
        Product lookedUp = productCurator.find(parentProd.getId());
        assertEquals(parentProd.getChildProducts(), lookedUp.getChildProducts());
        assertEquals(parentProd.getAllChildProducts(testProducts),
                    lookedUp.getAllChildProducts(testProducts));
    }
    
    @Test
    public void testBlkUpdate() {
        Set<Product> childProducts = new HashSet<Product>();
        Set<Product> products = new HashSet<Product>();
        Set<Content> content = new HashSet<Content>();
        
        String parentLabel = "cp_test_parent_product";
        String childLabel = "cp_test_child_product";

        Product childProd = new Product(childLabel, "Test Child Product",
            "variant", "version", "arch", Math.abs(Long.valueOf(parentLabel
                .hashCode())), "SVC", products, content);

        childProducts.add(childProd);
        Product parentProd = new Product(parentLabel, "Test Parent Product",
            "variant", "version", "arch", Math.abs(Long.valueOf(childLabel
                .hashCode())), "MKT", null, content);
        
        productCurator.create(parentProd);
        parentProd.setChildProducts(childProducts);
        productCurator.update(parentProd);
        
        Set<Product> testProducts = new HashSet<Product>();
        Product lookedUp = productCurator.find(parentProd.getId());
        assertEquals(parentProd.getChildProducts(), lookedUp.getChildProducts());
        assertEquals(parentProd.getAllChildProducts(testProducts), lookedUp
            .getAllChildProducts(testProducts));
    }
}
