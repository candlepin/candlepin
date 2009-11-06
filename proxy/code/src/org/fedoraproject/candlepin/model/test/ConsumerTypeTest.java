package org.fedoraproject.candlepin.model.test;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;

import org.fedoraproject.candlepin.model.ConsumerType;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConsumerTypeTest {

    @Test
    public void testSomething() {
        EntityManagerFactory emf = 
            Persistence.createEntityManagerFactory("test");
//        HibernateUtil.getSession();
        ConsumerType ct = new ConsumerType("standard-system");
        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = null;
        tx = em.getTransaction();
        tx.begin();
        
        em.persist(ct);
        tx.commit();
        
        List<EntityManager> results = em.createQuery("select ct from ConsumerType as ct")
            .getResultList();
        assertEquals(1, results.size());
        
    
    }
}
