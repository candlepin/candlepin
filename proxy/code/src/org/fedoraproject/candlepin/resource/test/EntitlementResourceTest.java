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
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.ObjectFactory;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.resource.EntitlementResource;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.Ignore;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.representation.Form;


/**
 * ConsumerResourceTest
 */
public class EntitlementResourceTest extends DatabaseTestFixture {
    
    private Consumer consumer;
    private Product product;
    private EntitlementPool ep;
    
    @Before
    public void createTestObjects() {
        
        beginTransaction();
        
        Owner o = TestUtil.createOwner();
        ConsumerType type = new ConsumerType("some-consumer-type");
        
        consumer = TestUtil.createConsumer(type, o);
        product = TestUtil.createProduct();
        
        entityManager().persist(o);
        entityManager().persist(type);
        entityManager().persist(consumer);
        entityManager().persist(product);
        commitTransaction();
        
        ep = new EntitlementPool();
        ep.setProduct(product);
        ep.setOwner(consumer.getOwner());
        ep.setMaxMembers(new Long(10));
        ep.setCurrentMembers(new Long(0));
        
        Date futuredate = new Date(System.currentTimeMillis() + 1000000000);
        ep.setEndDate(futuredate);
        ObjectFactory.get().store(ep);

    }
    
    @Ignore
    public void testEntitle() throws Exception {
        
        
        EntitlementResource eapi = new EntitlementResource();
        Form f = new Form();
        f.add("consumer_id", consumer.getId());
        f.add("product_id", product.getId());
        String cert = (String) eapi.entitle(consumer, product);
        
        assertNotNull(cert);
        assertNotNull(consumer.getConsumedProducts());
        assertNotNull(consumer.getEntitlements());
        
        ConsumerType type = new ConsumerType("some-consumer-type");
     
        // Test max membership
        boolean failed = false;
        for (int i = 0; i < ep.getMaxMembers() + 10; i++) {
            Consumer ci = TestUtil.createConsumer(type, consumer.getOwner());
            f.add("consumer_id", ci.getId());
            try {
                eapi.entitle(consumer, product);
            }
            catch (Exception e) {
                System.out.println("Failed: " + e);
                failed = true;
            }
        }
        assertTrue("we didnt hit max members", failed);

        // Test expiration
        Date pastdate = new Date(System.currentTimeMillis() - 1000000000);
        ep.setEndDate(pastdate);
        failed = false;
        try {
            eapi.entitle(consumer, product);
        }
        catch (Exception e) {
            System.out.println("expired:  ? " + e);
            failed = true;
        }
        assertTrue("we didnt expire", failed);
        

        
    }
    
    @Ignore
    public void testHasEntitlement() {
        EntitlementResource eapi = new EntitlementResource();
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
