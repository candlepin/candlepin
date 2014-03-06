/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.model.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.Environment;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProvidedProduct;
import org.candlepin.model.SourceStack;
import org.candlepin.paging.Page;
import org.candlepin.paging.PageRequest;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * EntitlementCuratorTest
 */
public class EntitlementCuratorTest extends DatabaseTestFixture {
    private Entitlement secondEntitlement;
    private Entitlement firstEntitlement;
    private EntitlementCertificate firstCertificate;
    private EntitlementCertificate secondCertificate;
    private Owner owner;
    private Consumer consumer;
    private Environment environment;
    private Date overlappingDate;
    private Date futureDate;
    private Date pastDate;
    private Product parentProduct;
    private Product providedProduct1;
    private Product providedProduct2;

    @Before
    public void setUp() {
        owner = createOwner();
        ownerCurator.create(owner);

        environment = new Environment("env1", "Env 1", owner);
        envCurator.create(environment);

        consumer = createConsumer(owner);
        consumer.setEnvironment(environment);
        consumerCurator.create(consumer);

        Product product = TestUtil.createProduct();
        productCurator.create(product);

        Pool firstPool = createPoolAndSub(owner, product, 1L,
            dateSource.currentDate(), createDate(2020, 1, 1));
        poolCurator.create(firstPool);

        firstCertificate = createEntitlementCertificate("key", "certificate");

        firstEntitlement = createEntitlement(owner, consumer, firstPool,
            firstCertificate);
        entitlementCurator.create(firstEntitlement);

        Product product1 = TestUtil.createProduct();
        productCurator.create(product1);

        Pool secondPool = createPoolAndSub(owner, product1, 1L,
            dateSource.currentDate(), createDate(2020, 1, 1));
        poolCurator.create(secondPool);

        secondCertificate = createEntitlementCertificate("key", "certificate");

        secondEntitlement = createEntitlement(owner, consumer, secondPool,
            secondCertificate);
        entitlementCurator.create(secondEntitlement);

        overlappingDate = createDate(2010, 2, 1);
        futureDate = createDate(2050, 1, 1);
        pastDate = createDate(1998, 1, 1);

        parentProduct = TestUtil.createProduct();
        providedProduct1 = TestUtil.createProduct();
        providedProduct2 = TestUtil.createProduct();
        productCurator.create(parentProduct);
        productCurator.create(providedProduct1);
        productCurator.create(providedProduct2);
    }

    private Date createDate(int year, int month, int day) {
        return TestUtil.createDate(year, month, day);
    }

    private Entitlement setupListProvidingEntitlement() {
        Date startDate = createDate(2010, 1, 1);
        Date endDate = createDate(2011, 1, 1);
        Pool testPool = createPoolAndSub(owner, parentProduct, 1L,
            startDate, endDate);

        // Add some provided products to this pool:
        ProvidedProduct p1 = new ProvidedProduct(providedProduct1.getId(),
            providedProduct1.getName());
        ProvidedProduct p2 = new ProvidedProduct(providedProduct2.getId(),
            providedProduct2.getName());
        testPool.addProvidedProduct(p1);
        testPool.addProvidedProduct(p2);
        poolCurator.create(testPool);

        EntitlementCertificate cert = createEntitlementCertificate("key", "certificate");

        Entitlement ent = createEntitlement(owner, consumer, testPool, cert);
        entitlementCurator.create(ent);

        return ent;
    }

    @Test
    public void listProviding() {
        Entitlement ent = setupListProvidingEntitlement();
        // Test a successful query:
        Set<Entitlement> results = entitlementCurator.listProviding(consumer,
                ent.getPool().getProductId(), ent.getStartDate(), ent.getEndDate());
        assertEquals(1, results.size());
    }

    @Test
    public void listProvidingProvidedProduct() {
        Entitlement ent = setupListProvidingEntitlement();
        // Test a successful query:
        Set<Entitlement> results = entitlementCurator.listProviding(consumer,
                providedProduct1.getId(), ent.getStartDate(), ent.getEndDate());
        assertEquals(1, results.size());
    }


    @Test
    public void listProvidingNoResults() {
        Entitlement ent = setupListProvidingEntitlement();
        Set<Entitlement> results = entitlementCurator.listProviding(consumer,
            "nosuchproductid", ent.getStartDate(), ent.getEndDate());
        assertEquals(0, results.size());
    }

    @Test
    public void listProvidingStartDateOverlap() {
        Entitlement ent = setupListProvidingEntitlement();
        Set<Entitlement> results = entitlementCurator.listProviding(consumer,
            ent.getPool().getProductId(), overlappingDate, futureDate);
        assertEquals(1, results.size());

    }

    @Test
    public void listProvidingEndDateOverlap() {
        Entitlement ent = setupListProvidingEntitlement();
        Set<Entitlement> results = entitlementCurator.listProviding(consumer,
            ent.getPool().getProductId(), pastDate, overlappingDate);
        assertEquals(1, results.size());
    }

    @Test
    public void listProvidingTotalOverlap() {
        Entitlement ent = setupListProvidingEntitlement();
        Set<Entitlement> results = entitlementCurator.listProviding(consumer,
            ent.getPool().getProductId(), pastDate, futureDate);
        assertEquals(1, results.size());
    }

    @Test
    public void listProvidingNoOverlap() {
        Entitlement ent = setupListProvidingEntitlement();
        Set<Entitlement> results = entitlementCurator.listProviding(consumer,
            ent.getPool().getProductId(), pastDate, pastDate);
        assertEquals(0, results.size());
    }

    @Test
    public void shouldReturnCorrectCertificate() {
        Entitlement e = entitlementCurator
            .findByCertificateSerial(secondCertificate.getSerial().getId());
        assertEquals(secondEntitlement, e);
    }

    @Test
    public void shouldReturnInCorrectCertificate() {
        Entitlement e = entitlementCurator
            .findByCertificateSerial(firstCertificate.getSerial().getId());
        assertNotSame(secondEntitlement, e);
    }

    @Test
    public void listForConsumerOnDate() {
        List<Entitlement> ents = entitlementCurator.listByConsumerAndDate(
            consumer, createDate(2015, 1, 1));
        assertEquals(2, ents.size());
    }

    @Test
    public void listByEnvironment() {
        List<Entitlement> ents = entitlementCurator.listByEnvironment(
            environment);
        assertEquals(2, ents.size());
    }

    @Test
    public void testListByConsumerAndProduct() {
        PageRequest req = new PageRequest();
        req.setPage(1);
        req.setPerPage(10);
        req.setOrder(PageRequest.Order.ASCENDING);
        req.setSortBy("id");

        Product product = TestUtil.createProduct();
        productCurator.create(product);

        Pool pool = createPoolAndSub(owner, product, 1L,
            dateSource.currentDate(), createDate(2020, 1, 1));
        poolCurator.create(pool);

        for (int i = 0; i < 10; i++) {
            EntitlementCertificate cert =
                createEntitlementCertificate("key", "certificate");
            Entitlement ent = createEntitlement(owner, consumer, pool, cert);
            entitlementCurator.create(ent);
        }

        Page<List<Entitlement>> page =
            entitlementCurator.listByConsumerAndProduct(consumer, product.getId(), req);
        assertEquals(Integer.valueOf(10), page.getMaxRecords());

        List<Entitlement> ents = page.getPageData();
        assertEquals(10, ents.size());

        // Make sure we have the real PageRequest, not the dummy one we send in
        // with the order and sortBy fields.
        assertEquals(req, page.getPageRequest());

        // Check that we've sorted ascending on the id
        for (int i = 0; i < ents.size(); i++) {
            if (i < ents.size() - 1) {
                assertTrue(ents.get(i).getId().compareTo(ents.get(i + 1).getId()) < 1);
            }
        }
    }

    @Test
    public void testListByConsumerAndProductWithoutPaging() {
        Product product = TestUtil.createProduct();
        productCurator.create(product);

        Pool pool = createPoolAndSub(owner, product, 1L,
            dateSource.currentDate(), createDate(2020, 1, 1));
        poolCurator.create(pool);

        for (int i = 0; i < 10; i++) {
            EntitlementCertificate cert =
                createEntitlementCertificate("key", "certificate");
            Entitlement ent = createEntitlement(owner, consumer, pool, cert);
            entitlementCurator.create(ent);
        }

        Product product2 = TestUtil.createProduct();
        productCurator.create(product2);

        Pool pool2 = createPoolAndSub(owner, product2, 1L,
            dateSource.currentDate(), createDate(2020, 1, 1));
        poolCurator.create(pool2);

        for (int i = 0; i < 10; i++) {
            EntitlementCertificate cert =
                createEntitlementCertificate("key", "certificate");
            Entitlement ent2 = createEntitlement(owner, consumer, pool2, cert);
            entitlementCurator.create(ent2);
        }

        Page<List<Entitlement>> page =
            entitlementCurator.listByConsumerAndProduct(consumer, product.getId(), null);
        assertEquals(Integer.valueOf(10), page.getMaxRecords());

        List<Entitlement> ents = page.getPageData();
        assertEquals(10, ents.size());

        assertNull(page.getPageRequest());
    }

    @Test
    public void testListByConsumerAndProductFiltered() {
        PageRequest req = new PageRequest();
        req.setPage(1);
        req.setPerPage(10);

        Product product = TestUtil.createProduct();
        productCurator.create(product);

        Pool pool = createPoolAndSub(owner, product, 1L,
            dateSource.currentDate(), createDate(2020, 1, 1));
        poolCurator.create(pool);

        for (int i = 0; i < 5; i++) {
            EntitlementCertificate cert =
                createEntitlementCertificate("key", "certificate");
            Entitlement ent = createEntitlement(owner, consumer, pool, cert);
            entitlementCurator.create(ent);
        }

        Product product2 = TestUtil.createProduct();
        productCurator.create(product2);

        Pool pool2 = createPoolAndSub(owner, product2, 1L,
            dateSource.currentDate(), createDate(2020, 1, 1));
        poolCurator.create(pool2);

        for (int i = 0; i < 5; i++) {
            EntitlementCertificate cert =
                createEntitlementCertificate("key", "certificate");
            Entitlement ent = createEntitlement(owner, consumer, pool2, cert);
            entitlementCurator.create(ent);
        }

        Page<List<Entitlement>> page =
            entitlementCurator.listByConsumerAndProduct(consumer, product.getId(), req);
        assertEquals(Integer.valueOf(5), page.getMaxRecords());
        assertEquals(5, page.getPageData().size());
    }

    @Test
    public void listByConsumerExpired() {
        List<Entitlement> ents = entitlementCurator.listByConsumer(consumer);
        // Should be 2 entitlements already
        assertEquals(2, ents.size());

        Product product = TestUtil.createProduct();
        productCurator.create(product);
        // expired pool
        Pool pool = createPoolAndSub(owner, product, 1L,
            createDate(2000, 1, 1), createDate(2000, 2, 2));
        poolCurator.create(pool);
        for (int i = 0; i < 2; i++) {
            EntitlementCertificate cert =
                createEntitlementCertificate("key", "certificate");
            Entitlement ent = createEntitlement(owner, consumer, pool, cert);
            entitlementCurator.create(ent);
        }

        // Do not show the expired entitlements, size should be the same as before
        assertEquals(2, ents.size());
    }

    @Test
    public void findByStackIdTest() {
        String stackingId = "test_stack_id";
        Product product = TestUtil.createProduct();
        product.setAttribute("stacking_id", stackingId);
        productCurator.create(product);

        Pool pool = createPoolAndSub(owner, product, 1L,
            dateSource.currentDate(), createDate(2020, 1, 1));
        poolCurator.create(pool);
        Entitlement created = bind(consumer, pool);

        List<Entitlement> results = entitlementCurator.findByStackId(consumer, stackingId);
        assertEquals(1, results.size());
        assertTrue(results.contains(created));
    }

    @Test
    public void findByStackIdMultiTest() {
        String stackingId = "test_stack_id";
        Product product = TestUtil.createProduct();
        product.setAttribute("stacking_id", stackingId);
        productCurator.create(product);

        int ents = 5;
        List<Entitlement> createdEntitlements = new LinkedList<Entitlement>();
        for (int i = 0; i < ents; i++) {
            Pool pool = createPoolAndSub(owner, product, 1L,
                dateSource.currentDate(), createDate(2020, 1, 1));
            poolCurator.create(pool);
            createdEntitlements.add(bind(consumer, pool));
        }

        List<Entitlement> results = entitlementCurator.findByStackId(consumer, stackingId);
        assertEquals(ents, results.size());
        assertTrue(results.containsAll(createdEntitlements) &&
            createdEntitlements.containsAll(results));
    }

    @Test
    public void findByStackIdMultiTestWithDerived() {
        String stackingId = "test_stack_id";
        Product product = TestUtil.createProduct();
        product.setAttribute("stacking_id", stackingId);
        productCurator.create(product);

        Consumer otherConsumer = createConsumer(owner);
        otherConsumer.setEnvironment(environment);
        consumerCurator.create(otherConsumer);

        List<Entitlement> createdEntitlements = new LinkedList<Entitlement>();
        for (int i = 0; i < 5; i++) {
            Pool pool = createPoolAndSub(owner, product, 1L,
                dateSource.currentDate(), createDate(2020, 1, 1));
            if (i < 2) {
                pool.setSourceStack(new SourceStack(otherConsumer, "otherstackid" + i));
            }
            else if (i < 4) {
                pool.setSourceEntitlement(createdEntitlements.get(0));
            }
            poolCurator.create(pool);
            createdEntitlements.add(bind(consumer, pool));
        }

        List<Entitlement> results = entitlementCurator.findByStackId(consumer, stackingId);
        assertEquals(1, results.size());
        assertEquals(createdEntitlements.get(4), results.get(0));
    }

    private Entitlement bind(Consumer consumer, Pool pool) {
        EntitlementCertificate cert =
            createEntitlementCertificate("key", "certificate");
        Entitlement ent = createEntitlement(owner, consumer, pool, cert);
        return entitlementCurator.create(ent);
    }
}
