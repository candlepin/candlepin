package org.fedoraproject.candlepin.model.test;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;

import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.util.EntityManagerUtil;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConsumerTypeTest extends DatabaseTestFixture {

    @Test
    public void testSomething() {
        beginTransaction();
        
        ConsumerType ct = new ConsumerType("standard-system");
        em.persist(ct);
        
        commitTransaction();
        
        List<EntityManager> results = em.createQuery("select ct from ConsumerType as ct")
            .getResultList();
        assertEquals(1, results.size());
    }
}
