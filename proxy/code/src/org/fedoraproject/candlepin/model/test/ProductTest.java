package org.fedoraproject.candlepin.model.test;

import static org.junit.Assert.assertEquals;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;

import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.util.EntityManagerUtil;
import org.junit.Test;

public class ProductTest extends ModelTestFixture {
    
    
    @Test
    public void normalCreate() { 
    
        Product prod = new Product("cptest-label", "My Product");
        persistAndCommit(prod);
        
        List<Product> results = em.createQuery("select p from Product as p")
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
        em.getTransaction().begin();
        Product parent = new Product("parent-product", "Parent Product");
        Product child1 = new Product("child-product1", "Child Product 1");
        Product child2 = new Product("child-product2", "Child Product 2");
        
        parent.addChildProduct(child1);
        parent.addChildProduct(child2);
        em.persist(child1);
        em.persist(child2);
        em.persist(parent);
        em.getTransaction().commit();
        
        EntityManager em2 = EntityManagerUtil.createEntityManager();
        Product result = (Product)em2.createQuery(
                "select p from Product as p where name = :name")
                .setParameter("name", parent.getName())
                .getSingleResult();
        assertEquals(2, result.getChildProducts().size());
    }

    @Test(expected = PersistenceException.class)
    public void childHasSingleParentOnly() {
        em.getTransaction().begin();
        
        Product parent1 = new Product("parent-product1", "Parent Product 1");
        Product child1 = new Product("child-product1", "Child Product 1");
        Product parent2 = new Product("parent-product2", "Parent Product 2");
        
        List objects = em.createQuery("from Product p").getResultList();
        
        parent1.addChildProduct(child1);
        parent2.addChildProduct(child1); // should cause the failure
        
        em.persist(child1);
        em.persist(parent1);
        em.persist(parent2);
        em.getTransaction().commit();
    }

}
