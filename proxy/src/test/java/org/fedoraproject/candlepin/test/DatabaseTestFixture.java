/**
 * Copyright (c) 2009 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.fedoraproject.candlepin.test;

import org.fedoraproject.candlepin.CandlepinTestingModule;
import org.fedoraproject.candlepin.DateSource;
import org.fedoraproject.candlepin.model.Attribute;
import org.fedoraproject.candlepin.model.AttributeCurator;
import org.fedoraproject.candlepin.model.Certificate;
import org.fedoraproject.candlepin.model.CertificateCurator;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.ConsumerTypeCurator;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCurator;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.EntitlementPoolCurator;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductCurator;
import org.fedoraproject.candlepin.model.RulesCurator;
import org.fedoraproject.candlepin.model.SpacewalkCertificateCurator;
import org.fedoraproject.candlepin.model.SubscriptionCurator;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.junit.Before;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.wideplay.warp.persist.PersistenceService;
import com.wideplay.warp.persist.UnitOfWork;
import com.wideplay.warp.persist.WorkManager;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;

/**
 * Test fixture for test classes requiring access to the database.
 */
public class DatabaseTestFixture {
    
    protected EntityManagerFactory emf;
    protected Injector injector;
    
    protected OwnerCurator ownerCurator;
    protected ProductCurator productCurator;
    protected ProductServiceAdapter productAdapter ;
    protected ConsumerCurator consumerCurator;
    protected ConsumerTypeCurator consumerTypeCurator;
    protected CertificateCurator certificateCurator;
    protected EntitlementPoolCurator entitlementPoolCurator;
    protected DateSourceForTesting dateSource;
    protected SpacewalkCertificateCurator spacewalkCertificateCurator;
    protected EntitlementCurator entitlementCurator;
    protected AttributeCurator attributeCurator;
    protected RulesCurator rulesCurator;
    protected SubscriptionCurator subCurator;
    protected WorkManager unitOfWork;

    
    @Before
    public void init() {
        injector = Guice.createInjector(
                new CandlepinTestingModule(), 
                PersistenceService.usingJpa()
                    .across(UnitOfWork.REQUEST)
                    .buildModule()
        );

        injector.getInstance(EntityManagerFactory.class); 
        emf = injector.getProvider(EntityManagerFactory.class).get();
        
        ownerCurator = injector.getInstance(OwnerCurator.class);
        productCurator = injector.getInstance(ProductCurator.class);
        consumerCurator = injector.getInstance(ConsumerCurator.class);
        consumerTypeCurator = injector.getInstance(ConsumerTypeCurator.class);
        certificateCurator = injector.getInstance(CertificateCurator.class);
        entitlementPoolCurator = injector.getInstance(EntitlementPoolCurator.class);
        spacewalkCertificateCurator = injector.getInstance(SpacewalkCertificateCurator.class);
        entitlementCurator = injector.getInstance(EntitlementCurator.class);
        attributeCurator = injector.getInstance(AttributeCurator.class);
        rulesCurator = injector.getInstance(RulesCurator.class);
        subCurator = injector.getInstance(SubscriptionCurator.class);
        unitOfWork = injector.getInstance(WorkManager.class);
        productAdapter = injector.getInstance(ProductServiceAdapter.class) ;
       
        dateSource = (DateSourceForTesting) injector.getInstance(DateSource.class);
        dateSource.currentDate(TestDateUtil.date(2010, 1, 1));
    }
    
    /*
     * As long as we are using in-memory db we don't need to clean it out; 
     * a new instance will be created for each test. cleanDb() is *not* being currently invoked.
     * 
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
    @SuppressWarnings("unchecked")
    public void cleanDb() {
        EntityManager em = entityManager();
        if (!em.getTransaction().isActive()) {
            beginTransaction();
        }
        
        List<Entitlement> ents = em.createQuery("from Entitlement e").
            getResultList();
        for (Entitlement e : ents) {
            em.remove(e);
        }

        List<EntitlementPool> pools = em.createQuery("from EntitlementPool p").getResultList();
        for (EntitlementPool p : pools) {
            em.remove(p);
        }

        // TODO: Would rather be doing this, but such a bulk delete does not seem to respect
        // the cascade to child products and thus fails.
        // em.createQuery("delete from Product").executeUpdate();
        List<Product> products = em.createQuery("from Product p").getResultList();
        for (Product p : products) {
            em.remove(p);
        }
        
        List<Consumer> consumers = em.createQuery("from Consumer c").getResultList();
        for (Consumer c : consumers) {
            em.remove(c);
        }

        List<Certificate> certificates = em.createQuery("from Certificate c").getResultList();
        for (Certificate c : certificates){
            em.remove(c);
        }
        
        List<Attribute> attributes = em.createQuery("from Attribute c").getResultList();
        for (Attribute a : attributes){
            em.remove(a);
        }
        
        
        List<Owner> owners = em.createQuery("from Owner o").getResultList();
        for (Owner o : owners) {
            em.remove(o);
        }

//        List<ConsumerInfo> consumerInfos = em.createQuery("from ConsumerInfo c").getResultList();
//        for (ConsumerInfo c : consumerInfos) {
//            em.remove(c);
//        }

        // TODO: Is this right? Or should we pre-populate default defined types, and always
        // reference these in the tests instead of creating them everywhere?
        List<ConsumerType> consumerTypes = em.createQuery("from ConsumerType c").
            getResultList();
        for (ConsumerType c : consumerTypes) {
            em.remove(c);
        }
        
 
        
        commitTransaction();
        em.close();
    }
    
    protected EntityManager entityManager() {
        return injector.getProvider(EntityManager.class).get();
    }
    
    /**
     * Begin a transaction, persist the given entity, and commit.
     * 
     * @param storeMe Entity to persist.
     */
    protected void persistAndCommit(Object storeMe) {
        EntityTransaction tx = null;
        
        try {
            tx = entityManager().getTransaction();
            tx.begin();
            entityManager().persist(storeMe);
            tx.commit();
        }
        catch (RuntimeException e) {
            if (tx != null && tx.isActive()) {
                tx.rollback();
            }
            throw e; // or display error message
        }
    }

    /**
     * Helper to open a new db transaction. Pretty simple for now, but may 
     * require additional logic and error handling down the road.
     */
    protected void beginTransaction() {
        entityManager().getTransaction().begin();
    }

    /**
     * Helper to commit the current db transaction. Pretty simple for now, but may 
     * require additional logic and error handling down the road.
     */
    protected void commitTransaction() {
        entityManager().getTransaction().commit();
    }
}
