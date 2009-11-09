package org.fedoraproject.candlepin.model.test;

import static org.junit.Assert.assertEquals;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.PersistenceException;

import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.util.EntityManagerUtil;
import org.junit.Before;
import org.junit.Test;

public class ProductTest {
    
    private EntityManager em;
    
    @Before
    public void setUp() {
        em = EntityManagerUtil.createEntityManager();
    }
    
    @Test
    public void normalCreate() { 
    
        Product prod = new Product("myproductlabel", "My Product");
        storeObject(prod);
        
        List<Product> results = em.createQuery("select p from Product as p")
            .getResultList();
        assertEquals(1, results.size());
    }
    
    @Test(expected = PersistenceException.class)
    public void nameRequired() { 
    
        Product prod = new Product();
        prod.setLabel("someproductlabel");
        storeObject(prod);
        
    }
    
    @Test(expected = PersistenceException.class)
    public void labelRequired() { 
    
        Product prod = new Product();
        prod.setName("My Product Name");
        storeObject(prod);
        
    }
    
    public void storeObject(Object storeMe) {
        EntityTransaction tx = null;
        tx = em.getTransaction();
        tx.begin();
        
        em.persist(storeMe);
        tx.commit();
    }

}
