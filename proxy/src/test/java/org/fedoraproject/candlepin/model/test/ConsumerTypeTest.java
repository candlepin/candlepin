package org.fedoraproject.candlepin.model.test;

import static org.junit.Assert.assertEquals;

import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.util.EntityManagerUtil;

import org.junit.Test;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

public class ConsumerTypeTest {

    @Test
    public void testSomething() {
        EntityManager em = EntityManagerUtil.createEntityManager();
        EntityTransaction tx = null;
        tx = em.getTransaction();
        tx.begin();
        
        ConsumerType ct = new ConsumerType("standard-system");
        em.persist(ct);
        
        tx.commit();
        
        List<EntityManager> results = em.createQuery("select ct from ConsumerType as ct")
            .getResultList();
        assertEquals(1, results.size());
        
    
    }
}
