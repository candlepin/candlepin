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

import java.util.ArrayList;
import java.util.List;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.TestDateUtil;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;


/**
 * ConsumerResourceTest
 */
public class EntitlementResourceTest extends DatabaseTestFixture {
    
    private Consumer consumer;
    private Product product;
    private Pool ep;
    private Owner owner;    
    //private EntitlementResource eapi;
    //private Entitler entitler;
    
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
        
        ep = new Pool(owner, product.getId(), new Long(10), 
                TestDateUtil.date(2010, 1, 1), TestDateUtil.date(2020, 12, 31));
        poolCurator.create(ep);

        //entitler = injector.getInstance(Entitler.class);

        //eapi = new EntitlementResource(
        //        poolCurator, entitlementCurator,
        //        consumerCurator, productAdapter, subAdapter, entitler);
        
        dateSource.currentDate(TestDateUtil.date(2010, 1, 13));
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
    
//    @Ignore
//    public void testHasEntitlement() {
//        
//        eapi.entitleByProduct(consumer.getUuid(), product.getLabel());
//
//        // TODO: Disabling this test, boils into ObjectFactory things that need
//        // to be fixed before we can do this check! Sorry! :) - dgoodwin
////        assertTrue(eapi.hasEntitlement(consumer.getUuid(), product.getUuid()));
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
        List<Object> aparams = new ArrayList<Object>();
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
