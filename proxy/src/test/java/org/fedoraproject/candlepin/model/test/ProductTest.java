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

//import org.codehaus.jackson.map.ObjectMapper;
//import org.codehaus.jackson.type.TypeReference;
import org.fedoraproject.candlepin.model.Attribute;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;

import org.junit.Test;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.persistence.PersistenceException;

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

        // EntityManager em2 = EntityManagerUtil.createEntityManager(); XXX not
        // sure why we need to ask for a new EntityManager here
        Product result = (Product) entityManager().createQuery(
                "select p from Product as p where name = :name").setParameter(
                "name", parent.getName()).getSingleResult();
        assertEquals(2, result.getChildProducts().size());
    }

    @Test(expected = PersistenceException.class)
    public void childHasSingleParentOnly() {
        beginTransaction();

        Product parent1 = new Product("parent-product1", "Parent Product 1");
        Product child1 = new Product("child-product1", "Child Product 1");
        Product parent2 = new Product("parent-product2", "Parent Product 2");

        parent1.addChildProduct(child1);
        parent2.addChildProduct(child1); // should cause the failure

        entityManager().persist(child1);
        entityManager().persist(parent1);
        entityManager().persist(parent2);
        commitTransaction();
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

        // EntityManager em2 = EntityManagerUtil.createEntityManager();
        Product result = (Product) entityManager().createQuery(
                "select p from Product as p where name = :name").setParameter(
                "name", child1.getName()).getSingleResult();
        assertNotNull(result);

        beginTransaction();
        entityManager().remove(parent1);
        commitTransaction();

        // em2 = EntityManagerUtil.createEntityManager();
        List<Product> results = entityManager().createQuery(
                "select p from Product as p where name = :name").setParameter(
                "name", child1.getName()).getResultList();
        assertEquals(0, results.size());
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
    
//    @Test
//    public void testWithSimpleJsonAttribute() throws Exception {
//        Map<String, String> data = new HashMap<String, String>();
//        data.put("a", "1");
//        data.put("b", "2");
//        ObjectMapper mapper = new ObjectMapper();
//        String jsonData = mapper.writeValueAsString(data);
//        
//        Product prod = new Product("cptest-label", "My Product");
//        Attribute a = new Attribute("content_sets", jsonData);
//        prod.addAttribute(a);
//        attributeCurator.create(a);
//        productCurator.create(prod);
//        
//        Product lookedUp = productCurator.find(prod.getId());
//        assertEquals(jsonData, lookedUp.getAttribute("content_sets"));
//        
//        data = mapper.readValue(lookedUp.getAttribute("content_sets"), 
//            new TypeReference<Map<String, String>>(){});
//        assertEquals("1", data.get("a"));
//        assertEquals("2", data.get("b"));
//    }

//    @Test
//    public void testJsonListOfHashes() throws Exception {
//        List<Map<String, String>> data = new LinkedList<Map<String,String>>();
//        Map<String, String> contentSet1 = new HashMap<String, String>();
//        contentSet1.put("name", "cs1");
//        contentSet1.put("url", "url");
//        
//        Map<String, String> contentSet2 = new HashMap<String, String>();
//        contentSet2.put("name", "cs2");
//        contentSet2.put("url", "url2");
//        
//        data.add(contentSet1);
//        data.add(contentSet2);
//
//        ObjectMapper mapper = new ObjectMapper();
//        String jsonData = mapper.writeValueAsString(data);
//        System.out.println(jsonData);
//        
//        Product prod = new Product("cptest-label", "My Product");
//        Attribute a = new Attribute("content_sets", jsonData);
//        prod.addAttribute(a);
//        attributeCurator.create(a);
//        productCurator.create(prod);
//        
//        Product lookedUp = productCurator.find(prod.getId());
//        assertEquals(jsonData, lookedUp.getAttribute("content_sets"));
//        
//        data = mapper.readValue(lookedUp.getAttribute("content_sets"), 
//            new TypeReference<List<Map<String, String>>>(){});
//        Map<String, String> cs1 = data.get(0);
//        assertEquals("cs1", cs1.get("name"));
//        
//        Map<String, String> cs2 = data.get(1);
//        assertEquals("cs2", cs2.get("name"));
//    }

}
