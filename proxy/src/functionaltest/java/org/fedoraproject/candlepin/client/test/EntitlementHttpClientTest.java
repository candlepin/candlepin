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
package org.fedoraproject.candlepin.client.test;

import static org.junit.Assert.*;

import java.util.List;

import org.fedoraproject.candlepin.controller.Entitler;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.test.TestDateUtil;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;

public class EntitlementHttpClientTest extends AbstractGuiceGrizzlyTest {

    private static final long MAX_MEMBERS_IN = new Long(10);
    private String CONSUMER_NAME = "consumer name";

    private Owner owner;
    private Consumer consumer;
    private ConsumerType consumerType;
    private Product product;
    private Pool entitlementPool;
    private Entitler entitler;
    private Pool exhaustedPool;
    private Product exhaustedPoolProduct;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        consumerType = new ConsumerType("some-consumer-type");
        consumerTypeCurator.create(consumerType);

        owner = TestUtil.createOwner();
        ownerCurator.create(owner);

        consumer = new Consumer(CONSUMER_NAME, owner, consumerType);
        consumerCurator.create(consumer);

        product = TestUtil.createProduct();
        productCurator.create(product);

        entitlementPool = new Pool(owner, product.getId(),
                MAX_MEMBERS_IN, TestDateUtil.date(2010, 1, 1), TestDateUtil
                        .date(2020, 12, 31));
        poolCurator.create(entitlementPool);

        exhaustedPoolProduct = TestUtil.createProduct();
        productCurator.create(exhaustedPoolProduct);

        exhaustedPool = new Pool(owner,
                exhaustedPoolProduct.getId(), new Long(0), TestDateUtil.date(
                        2010, 1, 1), TestDateUtil.date(2020, 12, 31));
        poolCurator.create(exhaustedPool);

        entitler = injector.getInstance(Entitler.class);
    }

    @Test
    public void listEntitlements() {
        for (int i = 0; i < entitlementPool.getQuantity(); i++) {
            Consumer c = TestUtil.createConsumer(consumerType, owner);
            consumerCurator.create(c);
            entitler.entitle(c, product);
        }

        WebResource r = resource().path("/entitlements/");
        List<Entitlement> returned = r.accept("application/json").type(
                "application/json").get(new GenericType<List<Entitlement>>() {
                });

        assertTrue(10 == returned.size());
        assertTrue(10 == entitlementCurator.findAll().size());
    }

    @Test
    public void getSingleEntitlement() {
        Consumer c = TestUtil.createConsumer(consumerType, owner);
        consumerCurator.create(c);
        Entitlement entitlement = entitler.entitle(c, product);

        WebResource r = resource().path("/entitlements/" + entitlement.getId());
        Entitlement returned = r.accept("application/json").type(
                "application/json").get(Entitlement.class);

        assertEntitlementsAreSame(entitlement, returned);
        assertNotNull(entitlementCurator.find(entitlement.getId()));
    }

    @Test
    public void getSingleEntitlementWithInvalidIdShouldFail() {
        try {
            WebResource r = resource().path("/entitlements/1234");
            Entitlement returned = r.accept("application/json").type(
                    "application/json").get(Entitlement.class);
        }
        catch (UniformInterfaceException e) {
            assertEquals(404, e.getResponse().getStatus());
        }
    }

    @Test
    public void entitlementWithValidConsumerAndProduct() {

        unitOfWork.beginWork();
        assertTrue(entitlementCurator.findAll().size() == 0);
        assertEquals(new Long(0), poolCurator.listByOwnerAndProduct(
                owner, product).get(0).getConsumed());
        unitOfWork.endWork();

        unitOfWork.beginWork();
        WebResource r = resource().path(
                "/consumers/" + consumer.getUuid() + "/entitlements").
                        queryParam("product", product.getLabel());
        r.accept("application/json").type("application/json").post(
                String.class);
        unitOfWork.endWork();

        assertEntitlementSucceeded();
    }

    @Test
    public void entitlementWithInvalidConsumerShouldFail() {
        try {
            WebResource r = resource().path(
                    "/consumers/1234-5678/entitlements").
                    queryParam("product", product.getLabel());
            r.accept("application/json").type("application/json")
                    .post(String.class);
            fail();
        }
        catch (UniformInterfaceException e) {
            assertHttpResponse(400, e.getResponse());
        }
    }

    @Test
    public void entitlementWithInvalidProductShouldFail() {
        try {
            WebResource r = resource().path(
                    "/consumers/" + consumer.getUuid() + "/entitlements").
                    queryParam("product", exhaustedPoolProduct.getLabel());
            r.accept("application/json").type("application/json")
                    .post(String.class);
            fail();
        }
        catch (UniformInterfaceException e) {
            assertHttpResponse(400, e.getResponse());
        }
    }

    @Test
    public void entitlementForExhaustedPoolShouldFail() {
        try {
            WebResource r = resource().path(
                    "/consumers/" + consumer.getUuid() +
                            "/entitlements").
                            queryParam("product", "nonexistent-product");
            r.accept("application/json").type("application/json")
                    .post(String.class);
            fail();
        }
        catch (UniformInterfaceException e) {
            assertHttpResponse(400, e.getResponse());
        }
    }

    @Test
    public void entitlementForConsumerNoProductShouldFail() {
        try {
            WebResource r = resource().path(
                    "/consumers/" + consumer.getUuid() +
                            "/entitlements").queryParam("token", "1234567");
            r.accept("application/json").type("application/json")
                    .post(String.class);
            fail();
        }
        catch (UniformInterfaceException e) {
            assertHttpResponse(400, e.getResponse());
        }
    }

    @Test
    public void entitlementForRegNumberShouldFail() {
        try {
            WebResource r = resource().path(
                    "/consumers/" + consumer.getUuid() +
                            "/entitlements").queryParam("token", "1234567");
            r.accept("application/json").type("application/json").post(String.class);
            fail();
        }
        catch (UniformInterfaceException e) {
            assertHttpResponse(400, e.getResponse());
        }
    }

//    @Test
//    public void hasEntitlementWithEntitledProductShouldReturnTrue() {
//        Entitlement entitlement = entitler.entitle(consumer, product);
//        assertNotNull(entitlementCurator.find(entitlement.getId()));
//
//        WebResource r = resource().path(
//                "/consumers/" + consumer.getUuid() + "/entitlements").
//                queryParam("product", product.getId());
//        Entitlement returned = r.accept("application/json").type(
//                "application/json").get(Entitlement.class);
//
//        assertEntitlementsAreSame(entitlement, returned);
//    }
//
//    @Test
//    public void hasEntitlementWithoutEntitledProductShouldReturnFalse() {
//        try {
//            WebResource r = resource().path(
//                    "/consumers/" + consumer.getUuid() + "/entitlements").
//                    queryParam("product", product.getLabel());
//            r.accept("application/json").type("application/json").get(
//                    Entitlement.class);
//            fail();
//        }
//        catch (UniformInterfaceException e) {
//            assertHttpResponse(404, e.getResponse());
//        }
//    }

    @Test
    public void deleteEntitlementWithValidIdShouldPass() {
        unitOfWork.beginWork();
        Entitlement entitlement = entitler.entitle(consumer, product);
        assertNotNull(entitlementCurator.find(entitlement.getId()));
        unitOfWork.endWork();

        unitOfWork.beginWork();
        WebResource r = resource().path("/entitlements/" + entitlement.getId());
        r.accept("application/json").type("application/json").delete();
        unitOfWork.endWork();

        assertNull(entitlementCurator.find(entitlement.getId()));
    }

    @Test
    public void deleteEntitlementWithInvalidIdShouldFail() {
        try {
            WebResource r = resource().path("/entitlements/1234");
            r.accept("application/json").type("application/json").delete();
        }
        catch (UniformInterfaceException e) {
            assertHttpResponse(404, e.getResponse());
        }
    }

    protected void assertHttpResponse(int code, ClientResponse response) {
        assertEquals(code, response.getStatus());
    }

    protected void assertEntitlementSucceeded() {
        assertEquals(new Long(1), new Long(entitlementCurator.findAll().size()));
        assertEquals(new Long(1), poolCurator.listByOwnerAndProduct(
                owner, product).get(0).getConsumed());
        assertEquals(1, consumerCurator.find(consumer.getId())
                .getEntitlements().size());
    }

    private void assertEntitlementsAreSame(Entitlement entitlement,
            Entitlement returned) {
        assertEquals(entitlement.getId(), returned.getId());
        assertEquals(entitlement.getProductId(), returned.getProductId());
        assertEquals(entitlement.getStartDate(), returned.getStartDate());
        assertEquals(entitlement.getIsFree(), returned.getIsFree());
    }
}
