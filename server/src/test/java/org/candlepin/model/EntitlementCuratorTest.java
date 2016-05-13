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
package org.candlepin.model;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.hamcrest.Matchers;
import org.hibernate.Hibernate;
import org.junit.Before;
import org.junit.Test;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

/**
 * EntitlementCuratorTest
 */
public class EntitlementCuratorTest extends DatabaseTestFixture {
    @Inject private OwnerCurator ownerCurator;
    @Inject private ProductCurator productCurator;
    @Inject private PoolCurator poolCurator;
    @Inject private ConsumerCurator consumerCurator;
    @Inject private EntitlementCurator entitlementCurator;
    @Inject private EnvironmentCurator envCurator;

    private Entitlement ent1modif;
    private Entitlement ent2modif;
    private Entitlement secondEntitlement;
    private Entitlement firstEntitlement;
    private EntitlementCertificate firstCertificate;
    private EntitlementCertificate secondCertificate;
    private Owner owner;
    private Consumer consumer;
    private Environment environment;
    private Date futureDate;
    private Date pastDate;
    private Product parentProduct;
    private Product providedProduct1;
    private Product providedProduct2;
    private Product testProduct;

    /**
     * Reproducer of EXTRA Lazy problem. This test is here mainly to demonstrate
     * the problem. The problem is that Pool.entitlements is an extra lazy
     * collection and at the same time it is cascading CREATE.
     * The most important lines in this method are:
     *     (1) ent.getPool().getEntitlements().remove(ent);
     *     (2) Hibernate.initialize(ent.getPool().getEntitlements());
     * The problem is that remove() in (1) will not initialize the collection because
     * it is EXTRA LAZY. Then (2) will initialize without removed entitlement
     * Then when owning consumer c is being deleted, hibernate will recreate
     * the entitlements from the initialized collection Pool.entitlements and
     * subsequent delete of Consumer will cause foreign key exception.
     */
    @Test
    public void removeConsumerWithEntitlements() {
        beginTransaction();
        Consumer c = createConsumer(owner);
        consumerCurator.create(c);
        Product product = TestUtil.createProduct(owner);
        productCurator.create(product);
        Pool p = createPool(owner, product, 2L, dateSource.currentDate(), createDate(2020, 1, 1));
        poolCurator.create(p);
        EntitlementCertificate cert = createEntitlementCertificate("keyx", "certificatex");
        Entitlement entitlement = createEntitlement(owner, c, p, cert);
        entitlementCurator.create(entitlement);
        commitTransaction();
        entityManager().clear();

        beginTransaction();
        c = consumerCurator.find(c.getId());
        for (Entitlement ent : c.getEntitlements()) {
            ent.getPool().getEntitlements().remove(ent);
            Hibernate.initialize(ent.getPool().getEntitlements());
        }
        try {
            consumerCurator.delete(c);
            consumerCurator.flush();
        }
        catch (Exception ex) {
            assertEquals(ex.getCause().getCause().getClass(),
                SQLIntegrityConstraintViolationException.class);
        }
        finally {
            rollbackTransaction();
        }
    }

    @Before
    public void setUp() {
        owner = createOwner();
        ownerCurator.create(owner);

        environment = new Environment("env1", "Env 1", owner);
        envCurator.create(environment);

        consumer = createConsumer(owner);
        consumer.setEnvironment(environment);
        consumerCurator.create(consumer);

        testProduct = TestUtil.createProduct(owner);
        testProduct.setAttribute("variant", "Starter Pack");
        productCurator.create(testProduct);

        Pool firstPool = createPool(owner, testProduct, 1L,
            dateSource.currentDate(), createDate(2020, 1, 1));
        firstPool.setAttribute("pool_attr_1", "attr1");
        poolCurator.merge(firstPool);

        firstCertificate = createEntitlementCertificate("key", "certificate");

        firstEntitlement = createEntitlement(owner, consumer, firstPool,
            firstCertificate);
        entitlementCurator.create(firstEntitlement);

        Product product1 = TestUtil.createProduct(owner);
        product1.setAttribute("enabled_consumer_types", "satellite");
        productCurator.create(product1);

        Pool secondPool = createPool(owner, product1, 1L,
            dateSource.currentDate(), createDate(2020, 1, 1));
        poolCurator.create(secondPool);

        secondCertificate = createEntitlementCertificate("key", "certificate");

        secondEntitlement = createEntitlement(owner, consumer, secondPool,
            secondCertificate);
        entitlementCurator.create(secondEntitlement);

        futureDate = createDate(2050, 1, 1);
        pastDate = createDate(1998, 1, 1);

        parentProduct = TestUtil.createProduct(owner);
        providedProduct1 = TestUtil.createProduct(owner);
        providedProduct2 = TestUtil.createProduct(owner);
        productCurator.create(parentProduct);
        productCurator.create(providedProduct1);
        productCurator.create(providedProduct2);
    }

    @Test
    public void testCompareTo() {
        Entitlement e1 = TestUtil.createEntitlement();
        Entitlement e2 = TestUtil.createEntitlement(e1.getOwner(), e1.getConsumer(), e1.getPool(), null);
        e2.getCertificates().addAll(e1.getCertificates());

        assertTrue(e1.equals(e2));
        assertEquals(0, e1.compareTo(e2));
    }

    private Date createDate(int year, int month, int day) {
        return TestUtil.createDate(year, month, day);
    }

    @Test
    public void listModifyingExcludesEntitlementThatModifiesItself() {
        Date startDate = createDate(2010, 1, 1);
        Date endDate = createDate(2050, 1, 1);
        Pool testPool = createPool(owner, parentProduct, 100L,
            startDate, endDate);

        // Provided product 2 will modify 1, both will be on the pool:
        Content c = new Content(this.owner, "fakecontent", "fakecontent", "facecontent",
            "yum", "RH", "http://", "http://", "x86_64");
        Set<String> modifiedIds = new HashSet<String>();
        modifiedIds.add(providedProduct1.getId());
        c.setModifiedProductIds(modifiedIds);
        contentCurator.create(c);
        providedProduct2.addContent(c);

        // Add some provided products to this pool:
        testPool.addProvidedProduct(providedProduct1);
        testPool.addProvidedProduct(providedProduct2);
        poolCurator.create(testPool);

        EntitlementCertificate cert = createEntitlementCertificate("key", "certificate");

        Entitlement ent = createEntitlement(owner, consumer, testPool, cert);
        entitlementCurator.create(ent);

        EntitlementCertificate cert1 = createEntitlementCertificate("key", "certificate");

        Entitlement ent1 = createEntitlement(owner, consumer, testPool, cert);
        entitlementCurator.create(ent1);

        // The ent we just created should *not* be returned as modifying itself:
        Collection<String> entIds = entitlementCurator.listModifying(ent);
        assertEquals(1, entIds.size());
        String id = entIds.iterator().next();
        assertFalse(id.contentEquals(ent.getId()));
        assertTrue(id.contentEquals(ent1.getId()));
    }

    @Test
    public void listModifying() {
        Date startDate = createDate(2010, 1, 1);
        Date endDate = createDate(2050, 1, 1);
        Entitlement ent = setUpModifyingEntitlements(startDate, endDate, 4, "1");

        // The ent we just created should *not* be returned as modifying itself:
        Collection<String> entIds = entitlementCurator.listModifying(ent);
        assertEquals(4, entIds.size());
        assertThat(entIds, not(hasItem(ent.getId())));
    }

    @Test
    public void listModifyingBatch() {
        Date startDate = createDate(2010, 1, 1);
        Date endDate = createDate(2020, 1, 1);
        Entitlement ent = setUpModifyingEntitlements(startDate, endDate, 4, "1");

        Date startDate1 = createDate(2030, 1, 1);
        Date endDate1 = createDate(2040, 1, 1);
        Entitlement ent1 = setUpModifyingEntitlements(startDate1, endDate1, 2, "2");

        // The ent we just created should *not* be returned as modifying itself:
        Collection<String> entIds = entitlementCurator.listModifying(Arrays.asList(ent, ent1));
        assertEquals(6, entIds.size());

        assertThat(entIds, not(hasItem(ent.getId())));
        assertThat(entIds, not(hasItem(ent1.getId())));
    }

    @Test
    public void listOverlapModifyingBatch() {
        Date startDate = createDate(2010, 1, 1);
        Date endDate = createDate(2050, 1, 1);
        Entitlement ent = setUpModifyingEntitlements(startDate, endDate, 3, "1");

        Date startDate1 = createDate(2030, 1, 1);
        Date endDate1 = createDate(2040, 1, 1);
        Entitlement ent1 = setUpModifyingEntitlements(startDate1, endDate1, 2, "2");

        // The ent we just created should *not* be returned as modifying itself:
        Collection<String> entIds = entitlementCurator.listModifying(Arrays.asList(ent, ent1));
        assertEquals(5, entIds.size());
        assertThat(entIds, not(hasItems(ent.getId())));
        assertThat(entIds, not(hasItems(ent1.getId())));
    }

    @Test
    public void listModifyingBatchEnsureOnlyModifying() {
        Date startDate = createDate(2010, 1, 1);
        Date endDate = createDate(2050, 1, 1);
        Entitlement ent = setUpModifyingEntitlements(startDate, endDate, 3, "1");

        Date startDate1 = createDate(2010, 1, 1);
        Date endDate1 = createDate(2050, 1, 1);
        Entitlement ent1 = setUpModifyingEntitlements(startDate1, endDate1, 2, "2");

        Date startDate2 = createDate(2030, 1, 1);
        Date endDate2 = createDate(2040, 1, 1);
        Entitlement ent2 = setUpModifyingEntitlements(startDate2, endDate2, 2, "3");

        // The ent we just created should *not* be returned as modifying itself:
        Collection<String> entIds = entitlementCurator.listModifying(Arrays.asList(ent, ent1));
        assertEquals(5, entIds.size());
        assertThat(entIds, not(hasItems(ent.getId())));
        assertThat(entIds, not(hasItems(ent1.getId())));
        assertThat(entIds, not(hasItems(ent2.getId())));
    }

    @Test
    public void listModifyingBatchEnsureOnlyOverLapping() {
        Date startDate = createDate(2010, 1, 1);
        Date endDate = createDate(2050, 1, 1);
        Entitlement ent = setUpModifyingEntitlements(startDate, endDate, 3, "1");

        Pool pool = createPool(owner, ent.getPool().getProduct(), 100L, startDate, endDate);
        pool.setProvidedProducts(ent.getPool().getProvidedProducts());
        pool.setStartDate(createDate(2020, 1, 1));
        pool.setEndDate(createDate(2021, 1, 1));
        poolCurator.create(pool);
        EntitlementCertificate cert = createEntitlementCertificate("key", "certificate");
        Entitlement ent1 = createEntitlement(owner, consumer, pool, cert);
        entitlementCurator.create(ent1);

        Pool pool2 = createPool(owner, ent.getPool().getProduct(), 100L, startDate, endDate);
        pool2.setProvidedProducts(ent.getPool().getProvidedProducts());
        pool2.setStartDate(createDate(2090, 1, 1));
        pool2.setEndDate(createDate(2091, 1, 1));
        poolCurator.create(pool2);
        EntitlementCertificate cert2 = createEntitlementCertificate("key", "certificate");
        Entitlement ent2 = createEntitlement(owner, consumer, pool2, cert);
        entitlementCurator.create(ent2);

        // The ent we just created should *not* be returned as modifying itself:
        Collection<String> entIds = entitlementCurator.listModifying(Arrays.asList(ent));
        assertEquals(4, entIds.size());
        assertThat(entIds, not(hasItems(ent.getId())));
        assertThat(entIds, hasItems(ent1.getId()));
        assertThat(entIds, not(hasItems(ent2.getId())));
    }

    private Entitlement setUpModifyingEntitlements(Date startDate, Date endDate, Integer howMany,
        String contentId) {
        Product parentProductForTest = TestUtil.createProduct(owner);
        Product providedProduct1ForTest = TestUtil.createProduct(owner);
        Product providedProduct2ForTest = TestUtil.createProduct(owner);
        productCurator.create(parentProductForTest);
        productCurator.create(providedProduct1ForTest);
        productCurator.create(providedProduct2ForTest);

        Pool testPool = createPool(owner, parentProductForTest, 100L, startDate, endDate);

        // Provided product 2 will modify 1, both will be on the pool:
        Content c = new Content(this.owner, "fakecontent", contentId, contentId, "yum", "RH",
            "http://", "http://", "x86_64");
        Set<String> modifiedIds = new HashSet<String>();
        modifiedIds.add(providedProduct1ForTest.getId());
        c.setModifiedProductIds(modifiedIds);
        contentCurator.create(c);
        providedProduct2ForTest.addContent(c);

        assertTrue(providedProduct2ForTest.modifies(providedProduct1ForTest.getId()));

        // Add some provided products to this pool:
        testPool.addProvidedProduct(providedProduct1ForTest);
        testPool.addProvidedProduct(providedProduct2ForTest);
        poolCurator.create(testPool);

        EntitlementCertificate cert = createEntitlementCertificate("key", "certificate");

        Entitlement ent = createEntitlement(owner, consumer, testPool, cert);
        entitlementCurator.create(ent);

        for (Integer i = 0; i < howMany; i++) {
            EntitlementCertificate cert1 = createEntitlementCertificate("key", "certificate");
            Entitlement ent1 = createEntitlement(owner, consumer, testPool, cert);
            entitlementCurator.create(ent1);
        }

        return ent;
    }

    public void prepareEntitlementsForModifying() {
        Content contentPool1 = new Content(owner, "fakecontent", "fakecontent",
            "facecontent", "yum", "RH", "http://", "http://",
            "x86_64");
        Content contentPool2 = new Content(owner, "fakecontent2", "fakecontent2",
            "facecontent2", "yum", "RH", "http://",
            "http://", "x86_64");

        /**
         * Each of these products are provided by respective Entitlements
         */
        Product providedProductEnt1 = TestUtil.createProduct("ppent1", "ppent1", owner);
        Product providedProductEnt2 = TestUtil.createProduct("ppent2", "ppent2", owner);
        Product providedProductEnt3 = TestUtil.createProduct("ppent3", "ppent3", owner);
        Product providedProductEnt4 = TestUtil.createProduct("ppent4", "ppent4", owner);

        productCurator.create(providedProductEnt1);
        productCurator.create(providedProductEnt2);
        productCurator.create(providedProductEnt3);
        productCurator.create(providedProductEnt4);

        ent1modif = createPool("p1", createDate(1999, 1, 1), createDate(1999 + 50, 2, 1)
            , providedProductEnt1);
        ent2modif = createPool("p2", createDate(2000, 4, 4), createDate(2001 + 50, 3, 3)
            , providedProductEnt2);

        /**
         * Ent1 and Ent2 entitlements are being modified by contentPool1 and
         * contentPool2
         */
        Set<String> modifiedIds1 = new HashSet<String>();
        Set<String> modifiedIds2 = new HashSet<String>();
        modifiedIds1.add(providedProductEnt1.getId());
        modifiedIds2.add(ent2modif.getPool().getProductId());

        /**
         * ContentPool1 modifies Ent1 ContentPool2 modifies Ent2
         */
        contentPool1.setModifiedProductIds(modifiedIds1);
        contentPool2.setModifiedProductIds(modifiedIds2);

        contentCurator.create(contentPool1);
        contentCurator.create(contentPool2);

        /**
         * Ent3 has content 1 and Ent4 has content 2
         */
        providedProductEnt3.addContent(contentPool1);
        providedProductEnt4.addContent(contentPool2);

        createPool("p3", createDate(1998, 1, 1), createDate(2003 + 50, 2, 1),
            providedProductEnt3);
        createPool("p4", createDate(2001, 2, 30), createDate(2002 + 50, 1, 10),
            providedProductEnt4);

        createPool("p5", createDate(2000 + 50, 5, 5), createDate(2000 + 50, 5, 10), null);

        createPool("p6", createDate(1998, 1, 1), createDate(1998, 12, 31), null);
        createPool("p7", createDate(2003, 2, 2), createDate(2003 + 50, 3, 3), null);
    }

    private Entitlement createPool(String id, Date startDate, Date endDate, Product provided) {
        EntitlementCertificate cert = createEntitlementCertificate("key", "certificate");
        Product poolProd = TestUtil.createProduct("prod-" + id, "prod-" + id, owner);
        productCurator.create(poolProd);
        Pool p = TestUtil.createPool(owner, poolProd);

        if (provided != null) {
            p.addProvidedProduct(provided);
        }

        p.setStartDate(startDate);
        p.setEndDate(endDate);
        Entitlement e1 = createEntitlement(owner, consumer, p, cert);
        poolCurator.create(p);
        entitlementCurator.create(e1);

        return e1;
    }

    @Test
    public void batchListModifying() {
        prepareEntitlementsForModifying();

        Collection<String> entIds = entitlementCurator.batchListModifying(
            Arrays.asList(ent1modif, ent2modif)
        );

        assertEquals(2, entIds.size());

        for (String entId : entIds) {
            Entitlement ent = entitlementCurator.find(entId);

            assertNotNull(ent);
            assertTrue(ent.getPool().getProductId().equals("prod-p3") ||
                ent.getPool().getProductId().equals("prod-p4"));
        }
    }

    private Entitlement setupListProvidingEntitlement() {
        Date startDate = createDate(2000, 1, 1);
        Date endDate = createDate(2005, 1, 1);
        Pool testPool = createPool(owner, parentProduct, 1L,
            startDate, endDate);

        // Add some provided products to this pool:
        testPool.addProvidedProduct(providedProduct1);
        testPool.addProvidedProduct(providedProduct2);
        poolCurator.create(testPool);

        EntitlementCertificate cert = createEntitlementCertificate("key", "certificate");

        Entitlement ent = createEntitlement(owner, consumer, testPool, cert);
        entitlementCurator.create(ent);

        return ent;
    }

    @Test
    public void listEntitledProductIds() {
        Entitlement ent = setupListProvidingEntitlement();
        Set<String> results = entitlementCurator.listEntitledProductIds(consumer,
            ent.getStartDate(), ent.getEndDate());
        assertEquals(3, results.size());
        assertTrue(results.contains(providedProduct1.getId()));
        assertTrue(results.contains(providedProduct2.getId()));
        assertTrue(results.contains(ent.getPool().getProduct().getId()));
    }

    @Test
    public void listEntitledProductIdsStartDateOverlap() {
        Entitlement ent = setupListProvidingEntitlement();
        Set<String> results = entitlementCurator.listEntitledProductIds(consumer,
            createDate(2002, 1, 1), createDate(2006, 1, 1));
        assertEquals(3, results.size());
        assertTrue(results.contains(ent.getPool().getProductId()));
    }

    @Test
    public void listEntitledProductIdsEndDateOverlap() {
        Entitlement ent = setupListProvidingEntitlement();
        Set<String> results = entitlementCurator.listEntitledProductIds(consumer,
            pastDate, createDate(2002, 1, 1));
        assertEquals(3, results.size());
        assertTrue(results.contains(ent.getPool().getProductId()));
    }

    @Test
    public void listEntitledProductIdsTotalOverlap() {
        Entitlement ent = setupListProvidingEntitlement();
        Set<String> results = entitlementCurator.listEntitledProductIds(consumer,
            pastDate, futureDate);
        // Picks up suite pools as well:
        assertEquals(5, results.size());
        assertTrue(results.contains(ent.getPool().getProductId()));
    }

    @Test
    public void listEntitledProductIdsNoOverlap() {
        setupListProvidingEntitlement();
        Set<String> results = entitlementCurator.listEntitledProductIds(consumer,
            pastDate, pastDate);
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

    private PageRequest createPageRequest() {
        PageRequest req = new PageRequest();
        req.setPage(1);
        req.setPerPage(10);
        req.setOrder(PageRequest.Order.ASCENDING);
        req.setSortBy("id");
        return req;
    }

    @Test
    public void testListByConsumerAndProduct() {
        PageRequest req = createPageRequest();
        Product product = TestUtil.createProduct(owner);
        productCurator.create(product);

        Pool pool = createPool(owner, product, 1L,
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
        Product product = TestUtil.createProduct(owner);
        productCurator.create(product);

        Pool pool = createPool(owner, product, 1L,
            dateSource.currentDate(), createDate(2020, 1, 1));
        poolCurator.create(pool);

        for (int i = 0; i < 10; i++) {
            EntitlementCertificate cert =
                createEntitlementCertificate("key", "certificate");
            Entitlement ent = createEntitlement(owner, consumer, pool, cert);
            entitlementCurator.create(ent);
        }

        Product product2 = TestUtil.createProduct(owner);
        productCurator.create(product2);

        Pool pool2 = createPool(owner, product2, 1L,
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
        PageRequest req = createPageRequest();

        Product product = TestUtil.createProduct(owner);
        productCurator.create(product);

        Pool pool = createPool(owner, product, 1L,
            dateSource.currentDate(), createDate(2020, 1, 1));
        poolCurator.create(pool);

        for (int i = 0; i < 5; i++) {
            EntitlementCertificate cert =
                createEntitlementCertificate("key", "certificate");
            Entitlement ent = createEntitlement(owner, consumer, pool, cert);
            entitlementCurator.create(ent);
        }

        Product product2 = TestUtil.createProduct(owner);
        productCurator.create(product2);

        Pool pool2 = createPool(owner, product2, 1L,
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
        assertEquals("Setup should add 2 entitlements:", 2, ents.size());

        Product product = TestUtil.createProduct(owner);
        productCurator.create(product);
        // expired pool
        Pool pool = createPool(owner, product, 1L,
            createDate(2000, 1, 1), createDate(2000, 2, 2));
        poolCurator.create(pool);
        for (int i = 0; i < 2; i++) {
            EntitlementCertificate cert =
                createEntitlementCertificate("key", "certificate");
            Entitlement ent = createEntitlement(owner, consumer, pool, cert);
            entitlementCurator.create(ent);
        }

        ents = entitlementCurator.listByConsumer(consumer);
        assertEquals("adding expired entitlements should not change results:", 2, ents.size());
    }

    @Test
    public void listByConsumerFilteringByProductAttribute() {
        EntitlementFilterBuilder filters = new EntitlementFilterBuilder();
        filters.addAttributeFilter("variant", "Starter Pack");

        List<Entitlement> ents = entitlementCurator.listByConsumer(consumer, filters);
        assertEquals("should match only one out of two entitlements:", 1, ents.size());

        Product p = ents.get(0).getPool().getProduct();
        assertTrue("Did not find ent by product attribute 'variant'", p.hasAttribute("variant"));
        assertEquals(p.getAttributeValue("variant"), "Starter Pack");
    }

    @Test
    public void listByConsumerFilterByMatches() {
        EntitlementFilterBuilder filters = new EntitlementFilterBuilder();
        filters.addMatchesFilter(testProduct.getName());

        List<Entitlement> ents = entitlementCurator.listByConsumer(consumer, filters);
        assertEquals("should match only one out of two entitlements:", 1, ents.size());
        assertEquals(ents.get(0).getPool().getName(), testProduct.getName());
    }

    @Test
    public void listByConsumersFilteringByPoolAttribute() {
        EntitlementFilterBuilder filters = new EntitlementFilterBuilder();
        filters.addAttributeFilter("pool_attr_1", "attr1");

        List<Entitlement> ents = entitlementCurator.listByConsumer(consumer, filters);
        assertEquals("should match only one out of two entitlements:", 1, ents.size());

        Pool p = ents.get(0).getPool();
        assertTrue("Did not find ent by pool attribute 'pool_attr_1'", p.hasAttribute("pool_attr_1"));
        assertEquals(p.getAttributeValue("pool_attr_1"), "attr1");
    }

    @Test
    public void listAllByOwner() {
        PageRequest req = createPageRequest();

        EntitlementFilterBuilder filters = new EntitlementFilterBuilder();
        Page<List<Entitlement>> entitlementPages = entitlementCurator.listByOwner(owner, null, filters, req);
        List<Entitlement> entitlements = entitlementPages.getPageData();
        assertEquals("should return all the entitlements:", 2, entitlements.size());
    }

    @Test
    public void listByOwnerWithPagingNoFiltering() {
        PageRequest req = createPageRequest();
        req.setPerPage(1);
        EntitlementFilterBuilder filters = new EntitlementFilterBuilder();
        Page<List<Entitlement>> entitlementPages = entitlementCurator.listByOwner(owner, null, filters, req);
        List<Entitlement> entitlements = entitlementPages.getPageData();
        assertEquals("should return only single entitlement per page:", 1, entitlements.size());
    }

    /*
     * should be enough to test a single filtering criterion.
     * other tests are covered in consumer tests
     */
    @Test
    public void listByOwnerWithPagingAndFiltering() {
        PageRequest req = createPageRequest();

        EntitlementFilterBuilder filters = new EntitlementFilterBuilder();
        filters.addAttributeFilter("pool_attr_1", "attr1");
        Page<List<Entitlement>> entitlementPages = entitlementCurator.listByOwner(owner, null, filters, req);
        List<Entitlement> entitlements = entitlementPages.getPageData();
        assertEquals("should match only one out of two entitlements:", 1, entitlements.size());

        Pool p = entitlements.get(0).getPool();
        assertTrue("Did not find ent by pool attribute 'pool_attr_1'", p.hasAttribute("pool_attr_1"));
        assertEquals(p.getAttributeValue("pool_attr_1"), "attr1");
    }

    @Test
    public void findByStackIdTest() {
        String stackingId = "test_stack_id";
        Product product = TestUtil.createProduct(owner);
        product.setAttribute("stacking_id", stackingId);
        productCurator.create(product);

        Pool pool = createPool(owner, product, 1L,
            dateSource.currentDate(), createDate(2020, 1, 1));
        poolCurator.create(pool);
        Entitlement created = bind(consumer, pool);

        List<Entitlement> results = entitlementCurator.findByStackId(consumer, stackingId);
        assertEquals(1, results.size());
        assertTrue(results.contains(created));
    }

    @Test
    public void findByStackIdsTest() {
        Set<String> stackingIds = new HashSet<String>();
        for (Integer i = 0; i < 4; i++) {
            String stackingId = "test_stack_id" + i.toString();
            if (i > 0) {
                stackingIds.add(stackingId);
            }
            Product product = TestUtil.createProduct(owner);
            product.setAttribute("stacking_id", stackingId);
            productCurator.create(product);

            Pool pool = createPool(owner, product, 1L, dateSource.currentDate(), createDate(2020, 1, 1));
            poolCurator.create(pool);
            Entitlement created = bind(consumer, pool);
        }

        List<Entitlement> results = entitlementCurator.findByStackIds(consumer, stackingIds);
        assertEquals(3, results.size());
    }

    @Test
    public void findByStackIdMultiTest() {
        String stackingId = "test_stack_id";
        Product product = TestUtil.createProduct(owner);
        product.setAttribute("stacking_id", stackingId);
        productCurator.create(product);

        int ents = 5;
        List<Entitlement> createdEntitlements = new LinkedList<Entitlement>();
        for (int i = 0; i < ents; i++) {
            Pool pool = createPool(owner, product, 1L,
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
        Product product = TestUtil.createProduct(owner);
        product.setAttribute("stacking_id", stackingId);
        productCurator.create(product);

        Consumer otherConsumer = createConsumer(owner);
        otherConsumer.setEnvironment(environment);
        consumerCurator.create(otherConsumer);

        List<Entitlement> createdEntitlements = new LinkedList<Entitlement>();
        for (int i = 0; i < 5; i++) {
            Pool pool = createPool(owner, product, 1L,
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

    @Test
    public void findUpstreamEntitlementForStack() {
        String stackingId = "test_stack_id";
        Product product = TestUtil.createProduct(owner);
        product.setAttribute("stacking_id", stackingId);
        productCurator.create(product);

        Pool futurePool = createPool(owner, product, 1L,
            createDate(2020, 1, 1), createDate(2021, 1, 1));
        poolCurator.create(futurePool);
        bind(consumer, futurePool);

        Pool currentPool = createPool(owner, product, 1L,
            dateSource.currentDate(), createDate(2020, 1, 1));
        poolCurator.create(currentPool);
        bind(consumer, currentPool);

        Pool anotherCurrentPool = createPool(owner, product, 1L,
            dateSource.currentDate(), createDate(2020, 1, 1));
        poolCurator.create(anotherCurrentPool);
        bind(consumer, anotherCurrentPool);

        // The future entitlement should have been omitted, and the eldest active
        // entitlement should have been selected:
        Entitlement result = entitlementCurator.findUpstreamEntitlementForStack(
            consumer, stackingId);
        assertNotNull(result);
        assertEquals(currentPool, result.getPool());
    }

    @Test
    public void findUpstreamEntitlementForStackNothingActive() {
        String stackingId = "test_stack_id";
        Product product = TestUtil.createProduct(owner);
        product.setAttribute("stacking_id", stackingId);
        productCurator.create(product);

        Pool futurePool = createPool(owner, product, 1L,
            createDate(2020, 1, 1), createDate(2021, 1, 1));
        poolCurator.create(futurePool);
        bind(consumer, futurePool);

        // The future entitlement should have been omitted:
        Entitlement result = entitlementCurator.findUpstreamEntitlementForStack(
            consumer, stackingId);
        assertNull(result);
    }

    @Test
    public void findUpstreamEntitlementForStackNoResults() {
        String stackingId = "test_stack_id";
        Product product = TestUtil.createProduct(owner);
        product.setAttribute("stacking_id", stackingId);
        productCurator.create(product);

        Entitlement result = entitlementCurator.findUpstreamEntitlementForStack(
            consumer, stackingId);
        assertNull(result);
    }

    @Test
    public void findEntitlementsByPoolAttributes() {
        Owner owner1 = createOwner();
        ownerCurator.create(owner1);

        Owner owner2 = createOwner();
        ownerCurator.create(owner2);

        Product product1 = TestUtil.createProduct(owner1);
        Product product2 = TestUtil.createProduct(owner2);
        productCurator.create(product1);
        productCurator.create(product2);

        Pool p1Attributes = TestUtil.createPool(owner1, product1);
        Pool p1NoAttributes = TestUtil.createPool(owner1, product1);

        Pool p2Attributes = TestUtil.createPool(owner2, product2);
        Pool p2NoAttributes = TestUtil.createPool(owner2, product2);
        Pool p2BadAttributes = TestUtil.createPool(owner2, product2);

        p1Attributes.addAttribute(new PoolAttribute("x", "true"));
        p2Attributes.addAttribute(new PoolAttribute("x", "true"));
        p2BadAttributes.addAttribute(new PoolAttribute("x", "false"));

        poolCurator.create(p1Attributes);
        poolCurator.create(p1NoAttributes);
        poolCurator.create(p2Attributes);
        poolCurator.create(p2NoAttributes);
        poolCurator.create(p2BadAttributes);

        Entitlement e1 = createEntitlement(owner, consumer, p1Attributes,
            createEntitlementCertificate("key", "certificate"));
        entitlementCurator.create(e1);


        Entitlement e2 = createEntitlement(owner, consumer, p2Attributes,
            createEntitlementCertificate("key", "certificate"));
        entitlementCurator.create(e2);

        Entitlement eBadAttributes = createEntitlement(owner, consumer, p2NoAttributes,
            createEntitlementCertificate("key", "certificate"));
        entitlementCurator.create(eBadAttributes);

        Entitlement eNoAttributes = createEntitlement(owner, consumer, p2BadAttributes,
            createEntitlementCertificate("key", "certificate"));
        entitlementCurator.create(eNoAttributes);

        List<Entitlement> results = entitlementCurator.findByPoolAttribute("x", "true");

        assertThat(results, Matchers.hasItems(e1, e2));
    }

    private Entitlement bind(Consumer consumer, Pool pool) {
        EntitlementCertificate cert =
            createEntitlementCertificate("key", "certificate");
        Entitlement ent = createEntitlement(owner, consumer, pool, cert);
        return entitlementCurator.create(ent);
    }

    @Test
    public void testMarkEntitlementsDirty() {
        Entitlement ent1 = this.setupListProvidingEntitlement();
        Entitlement ent2 = this.setupListProvidingEntitlement();
        Entitlement ent3 = this.setupListProvidingEntitlement();

        assertFalse(ent1.isDirty());
        assertFalse(ent2.isDirty());
        assertFalse(ent3.isDirty());

        this.entitlementCurator.markEntitlementsDirty(Arrays.asList(ent1.getId(), ent2.getId()));

        this.entitlementCurator.refresh(ent1);
        this.entitlementCurator.refresh(ent2);
        this.entitlementCurator.refresh(ent3);

        assertTrue(ent1.isDirty());
        assertTrue(ent2.isDirty());
        assertFalse(ent3.isDirty());
    }
}
