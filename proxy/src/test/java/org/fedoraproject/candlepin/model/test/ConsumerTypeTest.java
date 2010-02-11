package org.fedoraproject.candlepin.model.test;

import static org.junit.Assert.assertEquals;

import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;

import org.junit.Test;

import java.util.List;

import javax.persistence.EntityManager;

public class ConsumerTypeTest extends DatabaseTestFixture {

    @Test
    public void testSomething() {
        beginTransaction();
        
        ConsumerType ct = new ConsumerType("standard-system");
        entityManager().persist(ct);
        
        commitTransaction();
        
        List<EntityManager> results = entityManager().createQuery("select ct from ConsumerType as ct")
            .getResultList();
        assertEquals(1, results.size());
    }
}
