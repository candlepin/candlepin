package org.fedoraproject.candlepin.client.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class EntitlementHttpClientTest extends AbstractGuiceGrizzlyTest {

    private static final long MAX_MEMBERS_IN = new Long(10);
    private String CONSUMER_NAME = "consumer name";
    
    private Owner owner;
    private Consumer consumer;
    private ConsumerType consumerType;
    private Product product;
    private EntitlementPool entitlementPool;
    private Entitler entitler;
    private EntitlementPool exhaustedPool;
    private Product exhaustedPoolProduct;

    @Before
    public void setUp() {
        TestServletConfig.servletInjector = injector;
        startServer(TestServletConfig.class);
        
        consumerType = new ConsumerType("some-consumer-type");
        consumerTypeCurator.create(consumerType);
                
        owner = TestUtil.createOwner();
        ownerCurator.create(owner);
        
        consumer = new Consumer(CONSUMER_NAME, owner, consumerType);
        consumerCurator.create(consumer);

        product = TestUtil.createProduct();
        productCurator.create(product);
        
        entitlementPool = new EntitlementPool(owner, product, MAX_MEMBERS_IN, 
                TestDateUtil.date(2010, 1, 1), TestDateUtil.date(2020, 12, 31));
        entitlementPoolCurator.create(entitlementPool);
        
        exhaustedPoolProduct = TestUtil.createProduct();
        productCurator.create(exhaustedPoolProduct);

        exhaustedPool = new EntitlementPool(owner, exhaustedPoolProduct, new Long(0), 
                TestDateUtil.date(2010, 1, 1), TestDateUtil.date(2020, 12, 31));
        entitlementPoolCurator.create(exhaustedPool);
        
        entitler = injector.getInstance(Entitler.class);
    }
    
    @Test
    public void listEntitlements() {
        for (int i = 0; i < entitlementPool.getMaxMembers(); i++) {
            Consumer c = TestUtil.createConsumer(consumerType, owner);
            consumerCurator.create(c);
            entitler.entitle(owner, c, product);
        }
        
        WebResource r = resource().path("/entitlement/");
        List<Entitlement> returned = r.accept("application/json")
             .type("application/json")
             .get(new GenericType<List<Entitlement>>() {});
        
        assertTrue(10 == returned.size());
        assertTrue(10 == entitlementCurator.findAll().size());
    }
    
    @Test
    public void getSingleEntitlement() {
        Consumer c = TestUtil.createConsumer(consumerType, owner);
        consumerCurator.create(c);
        Entitlement entitlement = entitler.entitle(owner, c, product);
        
        WebResource r = resource().path("/entitlement/" + entitlement.getId());
        Entitlement returned = r.accept("application/json")
             .type("application/json")
             .get(Entitlement.class);
        
        assertEntitlementsAreSame(entitlement, returned);
        assertNotNull(entitlementCurator.find(entitlement.getId()));
    }
    
    @Test
    public void getSingleEntitlementWithInvalidIdShouldFail() {
        try {
            WebResource r = resource().path("/entitlement/1234");
            Entitlement returned = r.accept("application/json")
                 .type("application/json")
                 .get(Entitlement.class);
        } catch (UniformInterfaceException e) {
            assertEquals(404, e.getResponse().getStatus());
        }
    }
    
    @Test
    public void entitlementWithValidConsumerAndProduct() {
        
        unitOfWork.beginWork();
        assertTrue(entitlementCurator.findAll().size() == 0);
        assertEquals(new Long(0), 
                entitlementPoolCurator.listByOwnerAndProduct(owner, consumer, product)
                .get(0).getCurrentMembers());
        unitOfWork.endWork();

        unitOfWork.beginWork();
        WebResource r = resource()
            .path("/entitlement/consumer/" + consumer.getUuid() + "/product/" + product.getLabel());
        String s = r.accept("application/json")
             .type("application/json")
             .post(String.class);
        unitOfWork.endWork();
        
        assertEntitlementSucceeded();
    }
    
    @Test
    public void entitlementWithInvalidConsumerShouldFail() {
        try {
            WebResource r = resource()
                .path("/entitlement/consumer/1234-5678/product/" + product.getLabel());
            String s = r.accept("application/json")
                 .type("application/json")
                 .post(String.class);
            fail();
        } catch (UniformInterfaceException e) {
            assertHttpResponse(400, e.getResponse());
        }
    }
    
    @Test
    public void entitlementWithInvalidProductShouldFail() {
        try {
            WebResource r = resource()
                .path("/entitlement/consumer/" + consumer.getUuid() 
                    + "/product/" + exhaustedPoolProduct.getLabel());
            String s = r.accept("application/json")
                 .type("application/json")
                 .post(String.class);
            fail();
        } catch (UniformInterfaceException e) {
            assertHttpResponse(400, e.getResponse());
        }
    }
    
    @Test
    public void entitlementForExhaustedPoolShouldFail() {
        try {
            WebResource r = resource()
                .path("/entitlement/consumer/" + consumer.getUuid() + "/product/nonexistent-product");
            String s = r.accept("application/json")
                 .type("application/json")
                 .post(String.class);
            fail();
        } catch (UniformInterfaceException e) {
            assertHttpResponse(400, e.getResponse());
        }
    }
    
    @Test
    public void hasEntitlementWithEntitledProductShouldReturnTrue() {
        Entitlement entitlement = entitler.entitle(owner, consumer, product);
        assertNotNull(entitlementCurator.find(entitlement.getId()));        
        
        WebResource r = resource().path(
                "/entitlement/consumer/" + consumer.getUuid() + "/product/" + product.getLabel()
        );
        Entitlement returned = r.accept("application/json")
             .type("application/json")
             .get(Entitlement.class);
        
        assertEntitlementsAreSame(entitlement, returned);
    }
    
    @Test
    public void hasEntitlementWithoutEntitledProductShouldReturnFalse() {
        try {
            WebResource r = resource().path(
                    "/entitlement/consumer/" + consumer.getUuid() + "/product/" + product.getLabel()
            );
            r.accept("application/json")
                 .type("application/json")
                 .get(Entitlement.class);
            fail();
        } catch (UniformInterfaceException e) {
            assertHttpResponse(404, e.getResponse());
        }
    }
    
    @Test
    public void deleteEntitlementWithValidIdShouldPass() {
        unitOfWork.beginWork();
        Entitlement entitlement = entitler.entitle(owner, consumer, product);
        assertNotNull(entitlementCurator.find(entitlement.getId()));
        unitOfWork.endWork();
        
        unitOfWork.beginWork();
        WebResource r = resource().path(
                "/entitlement/" + entitlement.getId()
        );
        r.accept("application/json")
             .type("application/json")
             .delete();
        unitOfWork.endWork();
        
        assertNull(entitlementCurator.find(entitlement.getId()));
    }
    
    @Test
    public void deleteEntitlementWithInvalidIdShouldFail() {
        try {
            WebResource r = resource().path("/entitlement/1234");
            r.accept("application/json")
                 .type("application/json")
                 .delete();
        } catch (UniformInterfaceException e) {
            assertHttpResponse(404, e.getResponse());
        }
    }
    
    protected void assertHttpResponse(int code, ClientResponse response) {
        assertEquals(code, response.getStatus());
    }
    
    protected void assertEntitlementSucceeded() {
        assertEquals(new Long(1), new Long(entitlementCurator.findAll().size()));
        assertEquals(new Long(1),  
            entitlementPoolCurator.listByOwnerAndProduct(owner, consumer, product)
            .get(0).getCurrentMembers());
        assertEquals(1, consumerCurator.find(consumer.getId()).getConsumedProducts().size());
        assertEquals(product.getId(), consumerCurator.find(consumer.getId())
                .getConsumedProducts().iterator().next().getId());
        assertEquals(1, consumerCurator.find(consumer.getId()).getEntitlements().size());
    }
    

    private void assertEntitlementsAreSame(Entitlement entitlement, Entitlement returned) {
        assertEquals(entitlement.getId(), returned.getId());
        assertEquals(entitlement.getProduct(), returned.getProduct());
        assertEquals(entitlement.getStartDate(), returned.getStartDate());
        assertEquals(entitlement.getIsFree(), returned.getIsFree());
    }
}
