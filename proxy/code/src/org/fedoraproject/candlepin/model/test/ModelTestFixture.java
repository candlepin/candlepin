package org.fedoraproject.candlepin.model.test;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

import org.fedoraproject.candlepin.util.EntityManagerUtil;
import org.junit.Before;

public class ModelTestFixture {
    
    protected EntityManager em;
    
    @Before
    public void setUp() {
        em = EntityManagerUtil.createEntityManager();
    }
    
    /**
     * Begin a transaction, persist the given entity, and commit.
     * 
     * @param storeMe Entity to persist.
     */
    protected void persistAndCommit(Object storeMe) {
        EntityTransaction tx = null;
        tx = em.getTransaction();
        tx.begin();
        
        em.persist(storeMe);
        try {
            tx.commit();
        }
        catch (Exception e) {
            tx.rollback();
        }
    }


}
