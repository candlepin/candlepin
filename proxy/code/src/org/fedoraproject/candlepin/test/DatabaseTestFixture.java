package org.fedoraproject.candlepin.test;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.util.EntityManagerUtil;
import org.junit.After;
import org.junit.Before;

/**
 * Test fixture for test classes requiring access to the database.
 */
public class DatabaseTestFixture {
    
    protected EntityManager em;
    
    @Before
    public void setUp() {
        em = EntityManagerUtil.createEntityManager();
    }
    
    /*
     * Cleans out everything in the database. Currently we test against an 
     * in-memory hsqldb, so this should be ok.
     * 
     *  WARNING: Don't flip the persistence unit to point to an actual live 
     *  DB or you'll lose everything.
     *  
     *  WARNING: If you're creating objects in tables that have static 
     *  pre-populated content, we may not want to wipe those tables here.
     *  This situation hasn't arisen yet.
     */
    @After
    public void cleanDb() {
        if (!em.getTransaction().isActive()) {
            em.getTransaction().begin();
        }
        
        // TODO: Would rather be doing this, but such a bulk delete does not seem to respect
        // the cascade to child products and thus fails.
        // em.createQuery("delete from Product").executeUpdate();
        List<Product> products = em.createQuery("from Product p").getResultList();
        for (Product p : products) {
            em.remove(p);
        }
        
        List<Owner> owners = em.createQuery("from Owner o").getResultList();
        for (Owner o : owners) {
            em.remove(o);
        }
        
        List<Consumer> consumers = em.createQuery("from Consumer c").getResultList();
        for (Consumer c : consumers) {
            em.remove(c);
        }

        // TODO: Is this right? Or should we pre-populate default defined types, and always
        // reference these in the tests instead of creating them everywhere?
        List<ConsumerType> consumerTypes = em.createQuery("from ConsumerType c").
            getResultList();
        for (ConsumerType c : consumerTypes) {
            em.remove(c);
        }

        em.getTransaction().commit();
        em.close();
    }
    
    /**
     * Begin a transaction, persist the given entity, and commit.
     * 
     * @param storeMe Entity to persist.
     */
    protected void persistAndCommit(Object storeMe) {
        EntityTransaction tx = null;
        
        try {
            tx = em.getTransaction();
            tx.begin();
            em.persist(storeMe);
            tx.commit();
        }
        catch (RuntimeException e) {
            if (tx != null && tx.isActive()) {
                tx.rollback();
            }
            throw e; // or display error message
        }
    }


}
