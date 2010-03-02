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

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.servlet.http.HttpServletRequest;

import org.fedoraproject.candlepin.CandlepinCommonTestingModule;
import org.fedoraproject.candlepin.CandlepinNonServletEnvironmentTestingModule;
import org.fedoraproject.candlepin.DateSource;
import org.fedoraproject.candlepin.model.AttributeCurator;
import org.fedoraproject.candlepin.model.CertificateCurator;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.ConsumerIdentityCertificateCurator;
import org.fedoraproject.candlepin.model.ConsumerTypeCurator;
import org.fedoraproject.candlepin.model.EntitlementCurator;
import org.fedoraproject.candlepin.model.PoolCurator;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.model.ProductCurator;
import org.fedoraproject.candlepin.model.RulesCurator;
import org.fedoraproject.candlepin.model.SpacewalkCertificateCurator;
import org.fedoraproject.candlepin.model.SubscriptionCurator;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.fedoraproject.candlepin.service.SubscriptionServiceAdapter;
import org.junit.Before;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.wideplay.warp.persist.PersistenceService;
import com.wideplay.warp.persist.UnitOfWork;
import com.wideplay.warp.persist.WorkManager;

/**
 * Test fixture for test classes requiring access to the database.
 */
public class DatabaseTestFixture {
    
    protected EntityManagerFactory emf;
    protected Injector injector;
    
    protected OwnerCurator ownerCurator;
    protected ProductCurator productCurator;
    protected ProductServiceAdapter productAdapter;
    protected SubscriptionServiceAdapter subAdapter;
    protected ConsumerCurator consumerCurator;
    protected ConsumerIdentityCertificateCurator consumerIdCertCurator;
    protected ConsumerTypeCurator consumerTypeCurator;
    protected CertificateCurator certificateCurator;
    protected PoolCurator poolCurator;
    protected DateSourceForTesting dateSource;
    protected SpacewalkCertificateCurator spacewalkCertCurator;
    protected EntitlementCurator entitlementCurator;
    protected AttributeCurator attributeCurator;
    protected RulesCurator rulesCurator;
    protected SubscriptionCurator subCurator;
    protected WorkManager unitOfWork;
    protected HttpServletRequest httpServletRequest;

    
    @Before
    public void init() {
        injector = Guice.createInjector(
                new CandlepinCommonTestingModule(),
                new CandlepinNonServletEnvironmentTestingModule(),
                PersistenceService.usingJpa()
                    .across(UnitOfWork.REQUEST)
                    .buildModule()
        );

        injector.getInstance(EntityManagerFactory.class); 
        emf = injector.getProvider(EntityManagerFactory.class).get();
        
        ownerCurator = injector.getInstance(OwnerCurator.class);
        productCurator = injector.getInstance(ProductCurator.class);
        consumerCurator = injector.getInstance(ConsumerCurator.class);
        consumerIdCertCurator = injector
            .getInstance(ConsumerIdentityCertificateCurator.class);
        consumerTypeCurator = injector.getInstance(ConsumerTypeCurator.class);
        certificateCurator = injector.getInstance(CertificateCurator.class);
        poolCurator = injector.getInstance(PoolCurator.class);
        spacewalkCertCurator = injector.getInstance(SpacewalkCertificateCurator.class);
        entitlementCurator = injector.getInstance(EntitlementCurator.class);
        attributeCurator = injector.getInstance(AttributeCurator.class);
        rulesCurator = injector.getInstance(RulesCurator.class);
        subCurator = injector.getInstance(SubscriptionCurator.class);
        unitOfWork = injector.getInstance(WorkManager.class);
        productAdapter = injector.getInstance(ProductServiceAdapter.class);
        subAdapter = injector.getInstance(SubscriptionServiceAdapter.class);
       
        dateSource = (DateSourceForTesting) injector.getInstance(DateSource.class);
        dateSource.currentDate(TestDateUtil.date(2010, 1, 1));
    }
        
    protected EntityManager entityManager() {
        return injector.getProvider(EntityManager.class).get();
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
