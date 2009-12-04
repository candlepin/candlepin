package org.fedoraproject.candlepin.model.test;

import static org.junit.Assert.*;

import java.util.List;

import javax.persistence.EntityManager;

import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.junit.Test;

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
