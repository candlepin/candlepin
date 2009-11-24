package org.fedoraproject.candlepin.model.test;

import static org.junit.Assert.assertEquals;

import java.util.List;

import javax.persistence.PersistenceException;

import org.fedoraproject.candlepin.model.Product;
import org.junit.Test;

public class ProductTest extends ModelTestFixture {
    
    
    @Test
    public void normalCreate() { 
    
        Product prod = new Product("myproductlabel", "My Product");
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
    
}
