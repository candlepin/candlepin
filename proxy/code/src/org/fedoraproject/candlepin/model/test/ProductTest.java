package org.fedoraproject.candlepin.model.test;

import static org.junit.Assert.*;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;

import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.util.EntityManagerUtil;
import org.junit.Test;

public class ProductTest extends DatabaseTestFixture {
    
    
    @Test
    public void normalCreate() { 
    
        Product prod = new Product("cptest-label", "My Product");
        persistAndCommit(prod);
        
        List<Product> results = entityManager().createQuery("select p from Product as p")
            .getResultList();
        assertEquals(1, results.size());
    }
    
    @Test(expected = PersistenceException.class)
    public void nameRequired() { 
    
        Product prod = new Product();
        prod.setLabel("someproductlabel");
        persistAndCommit(prod);
        
    }
    
    @Test(expected = PersistenceException.class)
    public void labelRequired() { 
    
        Product prod = new Product();
        prod.setName("My Product Name");
        persistAndCommit(prod);
    }
    
    @Test(expected = PersistenceException.class)
    public void nameUnique() { 
    
        Product prod = new Product("label1", "name");
        persistAndCommit(prod);
        
        Product prod2 = new Product("label2", "name");
        persistAndCommit(prod2);
    }
    
    @Test(expected = PersistenceException.class)
    public void labelUnique() { 

        Product prod = new Product("label1", "name");
        Product prod2 = new Product("label1", "name2");
        persistAndCommit(prod);
        
        persistAndCommit(prod2);
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
        
        //EntityManager em2 = EntityManagerUtil.createEntityManager(); XXX not sure why we need to ask for a new EntityManager here
        Product result = (Product)entityManager().createQuery(
                "select p from Product as p where name = :name")
                .setParameter("name", parent.getName())
                .getSingleResult();
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
    public void testCascading() {
        beginTransaction();
        
        Product parent1 = new Product("parent-product1", "Parent Product 1");
        Product child1 = new Product("child-product1", "Child Product 1");
        parent1.addChildProduct(child1);
        entityManager().persist(parent1);
        commitTransaction();
        
        //EntityManager em2 = EntityManagerUtil.createEntityManager();
        Product result = (Product)entityManager().createQuery(
                "select p from Product as p where name = :name")
                .setParameter("name", child1.getName())
                .getSingleResult();
        assertNotNull(result);
        
        beginTransaction();
        entityManager().remove(parent1);
        commitTransaction();

        //em2 = EntityManagerUtil.createEntityManager();
        List<Product> results = entityManager().createQuery(
                "select p from Product as p where name = :name")
                .setParameter("name", child1.getName())
                .getResultList();
        assertEquals(0, results.size());
    }

}
