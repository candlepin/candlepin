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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.fedoraproject.candlepin.controller.Entitler;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.resource.EntitlementResource;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.TestDateUtil;
import org.fedoraproject.candlepin.test.TestUtil;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;


/**
 * ConsumerResourceTest
 */
public class EntitlementResourceTest extends DatabaseTestFixture {
    
    private Consumer consumer;
    private Product product;
    private EntitlementPool ep;
    private Owner owner;    
    private EntitlementResource eapi;
    private Entitler entitler;
    
    @Before
    public void createTestObjects() {
        owner = TestUtil.createOwner();
        ownerCurator.create(owner);
        
        ConsumerType type = new ConsumerType("some-consumer-type");
        consumerTypeCurator.create(type);
        
        consumer = TestUtil.createConsumer(type, owner);
        consumerCurator.create(consumer);
        
        product = TestUtil.createProduct();
        productCurator.create(product);
        
        ep = new EntitlementPool(owner, product, new Long(10), 
                TestDateUtil.date(2010, 1, 1), TestDateUtil.date(2020, 12, 31));
        entitlementPoolCurator.create(ep);

        entitler = injector.getInstance(Entitler.class);

        eapi = new EntitlementResource(entitlementPoolCurator, entitlementCurator, ownerCurator, consumerCurator, 
                productCurator, dateSource, entitler);
        
        dateSource.currentDate(TestDateUtil.date(2010, 1, 13));
    }
    
    @Test
    public void testEntitle() throws Exception {
        String cert = eapi.entitle(consumer.getUuid(), product.getLabel());
        
        assertNotNull(cert);
        
        consumer = consumerCurator.lookupByUuid(consumer.getUuid());
        assertEquals(1, consumer.getConsumedProducts().size());
        assertEquals(product.getId(), consumer.getConsumedProducts().iterator()
                .next().getId());
        assertEquals(1, consumer.getEntitlements().size());
        
        ep = entitlementPoolCurator.find(ep.getId());
        assertEquals(new Long(1), ep.getCurrentMembers());
    }
    
    @Test(expected=RuntimeException.class)
    public void testMaxMembership() {
        // 10 entitlements available, lets try to entitle 11 consumers.
        for (int i = 0; i < ep.getMaxMembers(); i++) {
            Consumer c = TestUtil.createConsumer(consumer.getType(), owner);
            consumerCurator.create(c);
            eapi.entitle(c.getUuid(), product.getLabel());
        }
        
        // Now for the 11th:
        Consumer c = TestUtil.createConsumer(consumer.getType(), owner);
        consumerCurator.create(c);
        eapi.entitle(c.getUuid(), product.getLabel());
    }
    
    @Test(expected=RuntimeException.class)
    public void testEntitlementsHaveExpired() {
        dateSource.currentDate(TestDateUtil.date(2030, 1, 13));
        eapi.entitle(consumer.getUuid(), product.getLabel());
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
    
    @Ignore
    public void testHasEntitlement() {
        
        eapi.entitle(consumer.getUuid(), product.getLabel());

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
   @Ignore
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
