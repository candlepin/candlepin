package org.fedoraproject.candlepin.model;

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
        entityManager().persist(ct);
        
        commitTransaction();
        
        List<EntityManager> results = entityManager().createQuery("select ct from ConsumerType as ct")
            .getResultList();
        assertEquals(1, results.size());
    }
}
