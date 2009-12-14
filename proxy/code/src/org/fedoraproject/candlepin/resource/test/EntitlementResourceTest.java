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
package org.fedoraproject.candlepin.resource.test;

import static org.junit.Assert.*;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.ConsumerTypeCurator;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.EntitlementPoolCurator;
import org.fedoraproject.candlepin.model.ObjectFactory;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductCurator;
import org.fedoraproject.candlepin.resource.EntitlementResource;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

import sun.nio.ch.EPollSelectorProvider;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.representation.Form;
import com.wideplay.warp.persist.PersistenceService;
import com.wideplay.warp.persist.UnitOfWork;


/**
 * ConsumerResourceTest
 */
public class EntitlementResourceTest extends DatabaseTestFixture {
    
    private Consumer consumer;
    private Product product;
    private EntitlementPool ep;
    private Owner owner;
    private OwnerCurator ownerCurator;
    private EntitlementPoolCurator epCurator;
    private ConsumerCurator consumerCurator;
    private ProductCurator productCurator;
    private ConsumerTypeCurator consumerTypeCurator;
    
    private EntitlementResource eapi;
    
    @Before
    public void createTestObjects() {
        
        
        Injector injector = Guice.createInjector(
                new CandlePingTestingModule(), 
                PersistenceService.usingJpa()
                    .across(UnitOfWork.TRANSACTION)
                    .buildModule()
        );
        
        epCurator = injector.getInstance(EntitlementPoolCurator.class);
        ownerCurator = injector.getInstance(OwnerCurator.class);
        consumerCurator = injector.getInstance(ConsumerCurator.class);
        productCurator = injector.getInstance(ProductCurator.class);
        consumerTypeCurator = injector.getInstance(ConsumerTypeCurator.class);

        owner = TestUtil.createOwner();
        ownerCurator.create(owner);
        
        ConsumerType type = new ConsumerType("some-consumer-type");
        consumerTypeCurator.create(type);
        
        consumer = TestUtil.createConsumer(type, owner);
        consumerCurator.create(consumer);
        
        product = TestUtil.createProduct();
        productCurator.create(product);
        
        Date pastDate = new Date(System.currentTimeMillis() - 10000000);
        Date futuredate = new Date(System.currentTimeMillis() + 1000000000);
        ep = new EntitlementPool(owner, product, new Long(10), pastDate, futuredate);
        epCurator.create(ep);
        
        eapi = new EntitlementResource(epCurator, ownerCurator);

    }
    
    @Test
    public void testEntitle() throws Exception {
        
//        Form f = new Form();
//        f.add("consumer_id", consumer.getId());
//        f.add("product_id", product.getId());
        String cert = (String) eapi.entitle(consumer, product);
        
        assertNotNull(cert);
        
        assertEquals(1, consumer.getConsumedProducts().size());
        assertEquals(product.getId(), consumer.getConsumedProducts().iterator()
                .next().getId());
        assertEquals(1, consumer.getEntitlements().size());
        
        ep = epCurator.find(ep.getId());
        assertEquals(new Long(1), ep.getCurrentMembers());
    }
    
    @Test
    public void testMaxMembership() {
        
        // 10 entitlements available, lets try to entitle 11 consumers.
        for (int i = 0; i < ep.getMaxMembers(); i++) {
            Consumer c = TestUtil.createConsumer(consumer.getType(), owner);
            eapi.entitle(c, product);
        }
        
        // Now for the 11th:
        try {
            Consumer c = TestUtil.createConsumer(consumer.getType(), owner);
            eapi.entitle(c, product);
            fail();
        }
        catch (RuntimeException e) {
            // expected
        }
        
    }
    
    @Test
    public void testEntitlementsHaveExpired() {
        Product myProduct = TestUtil.createProduct();
        productCurator.create(myProduct);
        Date pastDate = new Date(System.currentTimeMillis() - 10000000);
        Date notSoPastDate = new Date(System.currentTimeMillis() - 100000);
        EntitlementPool anotherPool = new EntitlementPool(owner, myProduct, new Long(10), 
                pastDate, notSoPastDate);
        epCurator.create(anotherPool);
        
        try {
            eapi.entitle(consumer, myProduct);
            fail();
        }
        catch (RuntimeException e) {
            // expected
        }
    }
    
    @Test
    public void testEntitleOwnerHasNoEntitlements() {
        // TODO
    }
    
    @Test
    public void testEntitleOwnerHasNoAviailableEntitlements() {
        // TODO
    }
    
    @Test
    public void testEntitleConsumerAlreadyEntitledForProduct() {
        // TODO
    }
    
    @Test
    public void testHasEntitlement() {
        
        eapi.entitle(consumer, product);

        // TODO: Disabling this test, boils into ObjectFactory things that need
        // to be fixed before we can do this check! Sorry! :) - dgoodwin
//        assertTrue(eapi.hasEntitlement(consumer.getUuid(), product.getUuid()));
    }

    // TODO: Re-enable once ObjectFactory is Hibernatized or removed.
//    @Test
//    public void testListAvailableEntitlements() {
//        EntitlementResource eapi = new EntitlementResource();
////        consumer.setType(new ConsumerType("standard-system"));
//        Form f = new Form();
//        f.add("consumer_id", consumer.getId());
//        
//        List<EntitlementPool> avail = eapi.listAvailableEntitlements(consumer.getId());
//        assertNotNull(avail);
//        assertTrue(avail.size() > 0);
//    }
    
    @Test
    public void testJson() {
        ClientConfig cc = new DefaultClientConfig();
        Client c = Client.create(cc);
        
        // WebResource getresource = c.resource("http://localhost:8080/candlepin/entitle/");
        

        Object[] params = new Object[2];
        params[0] = consumer;
        params[1] = product;
        List aparams = new ArrayList();
        aparams.add(consumer);
        aparams.add(product);
        
        WebResource postresource = 
            c.resource("http://localhost:8080/candlepin/entitlement/foo/");
        postresource.accept("application/json").type("application/json").post(consumer);
        
        // System.out.println(jto.getName());
        // jto = getresource.accept("application/json").get(JsonTestObject.class);
        // assertEquals("testname", jto.getName());
        // assertEquals("AEF", jto.getUuid());
    }

    
}
