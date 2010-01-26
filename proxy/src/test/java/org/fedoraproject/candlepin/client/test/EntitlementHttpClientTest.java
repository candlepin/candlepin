package org.fedoraproject.candlepin.client.test;

import static org.junit.Assert.*;

import java.util.List;

import org.fedoraproject.candlepin.client.test.ConsumerHttpClientTest.TestServletConfig;
import org.fedoraproject.candlepin.controller.Entitler;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.test.TestDateUtil;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.WebResource;

public class EntitlementHttpClientTest extends AbstractGuiceGrizzlyTest {

    private Owner owner;
    private Product product;
    private EntitlementPool entitlementPool;
    private ConsumerType consumerType;
    private Entitler entitler;

    @Before
    public void setUp() {
        TestServletConfig.servletInjector = injector;
        startServer(TestServletConfig.class);
        
        consumerType = new ConsumerType("some-consumer-type");
        consumerTypeCurator.create(consumerType);
        
        owner = TestUtil.createOwner();
        ownerCurator.create(owner);
        
        product = TestUtil.createProduct();
        productCurator.create(product);
        
        entitlementPool = new EntitlementPool(owner, product, new Long(10), 
                TestDateUtil.date(2010, 1, 1), TestDateUtil.date(2020, 12, 31));
        entitlementPoolCurator.create(entitlementPool);
        
        entitler = injector.getInstance(Entitler.class);
    }
    
    @Test
    public void listEntitlements() {
        for (int i = 0; i < entitlementPool.getMaxMembers(); i++) {
            Consumer c = TestUtil.createConsumer(consumerType, owner);
            consumerCurator.create(c);
            entitler.createEntitlement(owner, c, product);
        }
        
        WebResource r = resource().path("/entitlement/");
        List<Entitlement> returned = r.accept("application/json")
             .type("application/json")
             .get(new GenericType<List<Entitlement>>() {});
        
        assertTrue(10 == returned.size());
        assertTrue(10 == entitlementCurator.findAll().size());
    }
    
}
