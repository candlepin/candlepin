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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.hamcrest.Matchers;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    @Inject private ModifierTestDataGenerator modifierData;

    private Entitlement secondEntitlement;
    private Entitlement firstEntitlement;
    private EntitlementCertificate firstCertificate;
    private EntitlementCertificate secondCertificate;
    //Owner for modifying tests
    private Owner modifyOwner;
    private Owner owner;
    private Consumer consumer;
    private Environment environment;
    private Date futureDate;
    private Date pastDate;
    private Product parentProduct;
    private Product parentProduct2;
    private Product providedProduct1;
    private Product providedProduct2;
    private Product testProduct;

    @BeforeEach
    @Override
    public void init() throws Exception {
        super.init();

        modifyOwner = createOwner();
        modifierData.createTestData(modifyOwner);
        owner = createOwner();
        ownerCurator.create(owner);

        environment = new Environment("env1", "Env 1", owner);
        environmentCurator.create(environment);

        consumer = createConsumer(owner);
        consumer.setEnvironment(environment);
        consumerCurator.create(consumer);

        testProduct = TestUtil.createProduct();
        testProduct.setAttribute(Product.Attributes.VARIANT, "Starter Pack");
        productCurator.create(testProduct);

        Pool firstPool = createPool(owner, testProduct, 1L, dateSource.currentDate(),
            createFutureDate(1));
        firstPool.setAttribute("pool_attr_1", "attr1");
        poolCurator.merge(firstPool);

        firstCertificate = createEntitlementCertificate("key", "certificate");

        firstEntitlement = createEntitlement(owner, consumer, firstPool, firstCertificate);
        entitlementCurator.create(firstEntitlement);

        Product product1 = TestUtil.createProduct();
        product1.setAttribute(Pool.Attributes.ENABLED_CONSUMER_TYPES, "satellite");
        productCurator.create(product1);

        Pool secondPool = createPool(owner, product1, 1L, dateSource.currentDate(), createFutureDate(1));
        poolCurator.create(secondPool);

        secondCertificate = createEntitlementCertificate("key", "certificate");

        secondEntitlement = createEntitlement(owner, consumer, secondPool, secondCertificate);
        entitlementCurator.create(secondEntitlement);

        futureDate = createDate(2050, 1, 1);
        pastDate = createDate(1998, 1, 1);

        parentProduct = TestUtil.createProduct();
        parentProduct2 = TestUtil.createProduct();
        providedProduct1 = TestUtil.createProduct();
        providedProduct2 = TestUtil.createProduct();
        productCurator.create(parentProduct);
        productCurator.create(parentProduct2);
        productCurator.create(providedProduct1);
        productCurator.create(providedProduct2);
    }

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
        Product product = TestUtil.createProduct();
        productCurator.create(product);
        Pool p = createPool(owner, product, 2L, dateSource.currentDate(), createDate(2020, 1, 1));
        poolCurator.create(p);
        EntitlementCertificate cert = createEntitlementCertificate("keyx", "certificatex");
        Entitlement entitlement = createEntitlement(owner, c, p, cert);
        entitlementCurator.create(entitlement);
        commitTransaction();
        this.getEntityManager().clear();

        beginTransaction();
        c = consumerCurator.get(c.getId());
        c.setOwner(owner);
        for (Entitlement ent : c.getEntitlements()) {
            ent.getPool().getEntitlements().remove(ent);
            Hibernate.initialize(ent.getPool().getEntitlements());
        }
        try {
            consumerCurator.delete(c);
            consumerCurator.flush();
        }
        catch (Exception ex) {
            assertEquals(ex.getCause().getCause().getClass(), SQLIntegrityConstraintViolationException.class);
        }
        finally {
            rollbackTransaction();
        }
    }

    @Test
    public void testCompareTo() {
        Entitlement e1 = TestUtil.createEntitlement();
        Entitlement e2 = TestUtil.createEntitlement(e1.getOwner(), e1.getConsumer(), e1.getPool(), null);
        e2.getCertificates().addAll(e1.getCertificates());
        e2.setId(e1.getId());
        assertEquals(e2, e1);
        assertEquals(0, e1.compareTo(e2));
    }

    private Date createDate(int year, int month, int day) {
        return TestUtil.createDate(year, month, day);
    }

    private Date createFutureDate(int afterYears) {
        return TestUtil.createFutureDate(afterYears);
    }

    private Entitlement setupListProvidingEntitlement() {
        Date startDate = createDate(2000, 1, 1);
        Date endDate = createDate(2005, 1, 1);
        return setupListProvidingEntitlement(parentProduct, startDate, endDate);
    }

    private Entitlement setupListProvidingEntitlement(Product product, Date startDate, Date endDate) {

        product.setProvidedProducts(Arrays.asList(providedProduct1, providedProduct2));

        Pool testPool = createPool(owner, product, 1L,
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
        Pool pool = setupListProvidingEntitlement().getPool();
        Set<String> results = entitlementCurator.listEntitledProductIds(consumer, pool);
        assertEquals(3, results.size());
        assertTrue(results.contains(providedProduct1.getId()));
        assertTrue(results.contains(providedProduct2.getId()));
        assertTrue(results.contains(pool.getProduct().getId()));
    }

    private Pool newPoolUsingProducts(Pool pool, Date startDate, Date endDate) {
        Pool anotherPool = new Pool();
        anotherPool.setProduct(pool.getProduct());
        anotherPool.setProvidedProducts(pool.getProvidedProducts());
        anotherPool.setDerivedProduct(pool.getDerivedProduct());
        anotherPool.setDerivedProvidedProducts(pool.getDerivedProvidedProducts());
        anotherPool.setStartDate(startDate);
        anotherPool.setEndDate(endDate);
        return anotherPool;
    }

    @Test
    public void listEntitledProductIdsStartDateOverlap() {
        Pool existingEntPool = setupListProvidingEntitlement().getPool();
        Pool anotherPool = newPoolUsingProducts(existingEntPool,
            createDate(2002, 1, 1),
            createDate(2006, 1, 1));
        Set<String> results = entitlementCurator.listEntitledProductIds(consumer, anotherPool);
        assertEquals(3, results.size());
        assertTrue(results.contains(existingEntPool.getProductId()));
    }

    @Test
    public void listEntitledProductIdsEndDateOverlap() {
        Pool existingEntPool = setupListProvidingEntitlement().getPool();
        Pool anotherPool = newPoolUsingProducts(existingEntPool, pastDate, createDate(2002, 1, 1));

        Set<String> results = entitlementCurator.listEntitledProductIds(consumer, anotherPool);
        assertEquals(3, results.size());
        assertTrue(results.contains(existingEntPool.getProductId()));
    }

    @Test
    public void listEntitledProductIdsTotalOverlap() {
        Pool existingEntPool = setupListProvidingEntitlement().getPool();
        Pool anotherPool = newPoolUsingProducts(existingEntPool, pastDate, futureDate);
        Set<String> results = entitlementCurator.listEntitledProductIds(consumer, anotherPool);
        // Picks up suite pools as well:
        assertEquals(5, results.size());
        assertTrue(results.contains(existingEntPool.getProductId()));
    }

    @Test
    public void listEntitledProductIdsNoOverlap() {
        Pool existingEntPool = setupListProvidingEntitlement().getPool();
        Pool anotherPool = setupListProvidingEntitlement(parentProduct2, pastDate, pastDate).getPool();
        Set<String> results = entitlementCurator.listEntitledProductIds(consumer, anotherPool);
        assertEquals(3, results.size());
        assertFalse(results.contains(existingEntPool.getProductId()));
    }

    @Test
    public void shouldReturnCorrectCertificate() {
        Entitlement e = entitlementCurator
            .findByCertificateSerial(secondCertificate.getSerial().getId());
        assertEquals(secondEntitlement, e);
    }

    @Test
    public void shouldReturnInCorrectCertificate() {
        Entitlement e = entitlementCurator.findByCertificateSerial(firstCertificate.getSerial().getId());
        assertNotSame(secondEntitlement, e);
    }

    @Test
    public void listForConsumerOnDate() {
        List<Entitlement> ents = entitlementCurator
            .listByConsumerAndDate(consumer, createDate(2015, 1, 1))
            .list();

        assertEquals(2, ents.size());
    }

    @Test
    public void listByEnvironment() {
        List<Entitlement> ents = entitlementCurator.listByEnvironment(environment).list();
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
    public void listByConsumerExpired() {
        List<Entitlement> ents = entitlementCurator.listByConsumer(consumer);
        assertEquals(2, ents.size(), "Setup should add 2 entitlements:");

        Product product = TestUtil.createProduct();
        productCurator.create(product);
        // expired pool
        Pool pool = createPool(owner, product, 1L, createDate(2000, 1, 1), createDate(2000, 2, 2));
        poolCurator.create(pool);

        for (int i = 0; i < 2; i++) {
            EntitlementCertificate cert = createEntitlementCertificate("key", "certificate");
            Entitlement ent = createEntitlement(owner, consumer, pool, cert);
            entitlementCurator.create(ent);
        }

        ents = entitlementCurator.listByConsumer(consumer);
        assertEquals(2, ents.size(), "adding expired entitlements should not change results:");
    }

    @Test
    public void listByConsumerFilteringByProductAttribute() {
        EntitlementFilterBuilder filters = new EntitlementFilterBuilder();
        filters.addAttributeFilter(Product.Attributes.VARIANT, "Starter Pack");

        List<Entitlement> ents = entitlementCurator.listByConsumer(consumer, filters);
        assertEquals(1, ents.size(), "should match only one out of two entitlements:");

        Product p = ents.get(0).getPool().getProduct();
        assertTrue(p.hasAttribute(Product.Attributes.VARIANT),
            "Did not find ent by product attribute 'variant'");
        assertEquals("Starter Pack", p.getAttributeValue(Product.Attributes.VARIANT));
    }

    @Test
    public void listByConsumerFilterByMatches() {
        EntitlementFilterBuilder filters = new EntitlementFilterBuilder();
        filters.addMatchesFilter(testProduct.getName());

        List<Entitlement> ents = entitlementCurator.listByConsumer(consumer, filters);
        assertEquals(1, ents.size(), "should match only one out of two entitlements:");
        assertEquals(ents.get(0).getPool().getName(), testProduct.getName());
    }

    @Test
    public void listByConsumersFilteringByPoolAttribute() {
        EntitlementFilterBuilder filters = new EntitlementFilterBuilder();
        filters.addAttributeFilter("pool_attr_1", "attr1");

        List<Entitlement> ents = entitlementCurator.listByConsumer(consumer, filters);
        assertEquals(1, ents.size(), "should match only one out of two entitlements:");

        Pool p = ents.get(0).getPool();
        assertTrue(p.hasAttribute("pool_attr_1"), "Did not find ent by pool attribute 'pool_attr_1'");
        assertEquals(p.getAttributeValue("pool_attr_1"), "attr1");
    }

    @Test
    public void listAllByOwner() {
        PageRequest req = createPageRequest();

        EntitlementFilterBuilder filters = new EntitlementFilterBuilder();
        Page<List<Entitlement>> entitlementPages = entitlementCurator.listByOwner(owner, null, filters, req);
        List<Entitlement> entitlements = entitlementPages.getPageData();
        assertEquals(2, entitlements.size(), "should return all the entitlements:");
    }

    @Test
    public void listByOwnerWithPagingNoFiltering() {
        PageRequest req = createPageRequest();
        req.setPerPage(1);
        EntitlementFilterBuilder filters = new EntitlementFilterBuilder();
        Page<List<Entitlement>> entitlementPages = entitlementCurator.listByOwner(owner, null, filters, req);
        List<Entitlement> entitlements = entitlementPages.getPageData();
        assertEquals(1, entitlements.size(), "should return only single entitlement per page:");
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
        assertEquals(1, entitlements.size(), "should match only one out of two entitlements:");

        Pool p = entitlements.get(0).getPool();
        assertTrue(p.hasAttribute("pool_attr_1"), "Did not find ent by pool attribute 'pool_attr_1'");
        assertEquals(p.getAttributeValue("pool_attr_1"), "attr1");
    }

    @Test
    public void findByStackIdTest() {
        String stackingId = "test_stack_id";
        Product product = TestUtil.createProduct();
        product.setAttribute(Product.Attributes.STACKING_ID, stackingId);
        productCurator.create(product);

        Pool pool = createPool(owner, product, 1L, dateSource.currentDate(), createFutureDate(1));
        poolCurator.create(pool);
        Entitlement created = bind(consumer, pool);

        List<Entitlement> results = entitlementCurator.findByStackId(consumer, stackingId);
        assertEquals(1, results.size());
        assertTrue(results.contains(created));
    }

    @Test
    public void findByStackIdsTest() {
        Set<String> stackingIds = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            String stackingId = "test_stack_id" + i;
            if (i > 0) {
                stackingIds.add(stackingId);
            }

            Product product = TestUtil.createProduct();
            product.setAttribute(Product.Attributes.STACKING_ID, stackingId);
            productCurator.create(product);

            Pool pool = createPool(owner, product, 1L, dateSource.currentDate(), createFutureDate(1));
            poolCurator.create(pool);
            Entitlement created = bind(consumer, pool);
        }

        List<Entitlement> results = entitlementCurator.findByStackIds(consumer, stackingIds);
        assertEquals(3, results.size());
    }

    @Test
    public void findByStackIdMultiTest() {
        String stackingId = "test_stack_id";
        Product product = TestUtil.createProduct();
        product.setAttribute(Product.Attributes.STACKING_ID, stackingId);
        productCurator.create(product);

        int ents = 5;
        List<Entitlement> createdEntitlements = new LinkedList<>();
        for (int i = 0; i < ents; i++) {
            Pool pool = createPool(owner, product, 1L, dateSource.currentDate(), createFutureDate(1));
            poolCurator.create(pool);
            createdEntitlements.add(bind(consumer, pool));
        }

        List<Entitlement> results = entitlementCurator.findByStackId(consumer, stackingId);
        assertEquals(ents, results.size());
        assertTrue(results.containsAll(createdEntitlements) && createdEntitlements.containsAll(results));
    }

    @Test
    public void findByStackIdMultiTestWithDerived() {
        String stackingId = "test_stack_id";
        Product product = TestUtil.createProduct();
        product.setAttribute(Product.Attributes.STACKING_ID, stackingId);
        productCurator.create(product);

        Consumer otherConsumer = createConsumer(owner);
        otherConsumer.setEnvironment(environment);
        consumerCurator.create(otherConsumer);

        List<Entitlement> createdEntitlements = new LinkedList<>();
        for (int i = 0; i < 5; i++) {
            Pool pool = createPool(owner, product, 1L, dateSource.currentDate(), createFutureDate(1));

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
        Product product = TestUtil.createProduct();
        product.setAttribute(Product.Attributes.STACKING_ID, stackingId);
        productCurator.create(product);

        Pool futurePool = createPool(owner, product, 1L, createFutureDate(1), createFutureDate(2));
        poolCurator.create(futurePool);
        bind(consumer, futurePool);

        Pool currentPool = createPool(owner, product, 1L, dateSource.currentDate(), createFutureDate(1));
        poolCurator.create(currentPool);
        bind(consumer, currentPool);

        Pool anotherCurrentPool = createPool(owner, product, 1L,
            dateSource.currentDate(), createFutureDate(1));
        poolCurator.create(anotherCurrentPool);
        bind(consumer, anotherCurrentPool);

        // The future entitlement should have been omitted, and the eldest active
        // entitlement should have been selected:
        Entitlement result = entitlementCurator.findUpstreamEntitlementForStack(consumer, stackingId);
        assertNotNull(result);
        assertEquals(currentPool, result.getPool());
    }

    @Test
    public void findUpstreamEntitlementForStackNothingActive() {
        String stackingId = "test_stack_id";
        Product product = TestUtil.createProduct();
        product.setAttribute(Product.Attributes.STACKING_ID, stackingId);
        productCurator.create(product);

        Pool futurePool = createPool(owner, product, 1L,
            createFutureDate(1), createDate(2021, 1, 1));
        poolCurator.create(futurePool);
        bind(consumer, futurePool);

        // The future entitlement should have been omitted:
        Entitlement result = entitlementCurator.findUpstreamEntitlementForStack(consumer, stackingId);
        assertNull(result);
    }

    @Test
    public void findUpstreamEntitlementForStackNoResults() {
        String stackingId = "test_stack_id";
        Product product = TestUtil.createProduct();
        product.setAttribute(Product.Attributes.STACKING_ID, stackingId);
        productCurator.create(product);

        Entitlement result = entitlementCurator.findUpstreamEntitlementForStack(consumer, stackingId);
        assertNull(result);
    }

    @Test
    public void findEntitlementsByPoolAttributes() {
        Owner owner1 = createOwner();
        ownerCurator.create(owner1);

        Owner owner2 = createOwner();
        ownerCurator.create(owner2);

        Product product1 = this.createProduct(owner1);
        Product product2 = this.createProduct(owner2);

        Pool p1Attributes = TestUtil.createPool(owner1, product1);
        Pool p1NoAttributes = TestUtil.createPool(owner1, product1);

        Pool p2Attributes = TestUtil.createPool(owner2, product2);
        Pool p2NoAttributes = TestUtil.createPool(owner2, product2);
        Pool p2BadAttributes = TestUtil.createPool(owner2, product2);

        p1Attributes.setAttribute("x", "true");
        p2Attributes.setAttribute("x", "true");
        p2BadAttributes.setAttribute("x", "false");

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

        List<Entitlement> results = entitlementCurator.findByPoolAttribute("x", "true").list();

        assertThat(results, Matchers.hasItems(e1, e2));
    }

    private Entitlement bind(Consumer consumer, Pool pool) {
        EntitlementCertificate cert = createEntitlementCertificate("key", "certificate");
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

    @Test
    public void testListDirty() {
        Entitlement ent = entitlementCurator.listByConsumer(consumer).get(0);
        ent.setDirty(true);
        entitlementCurator.save(ent);
        List<Entitlement> ents = entitlementCurator.listDirty(consumer);
        assertNotNull(ents);
        assertEquals(1, ents.size());
        assertEquals(ent.getId(), ents.get(0).getId());
    }

    protected List<Product> createProducts(Owner owner, int count, String prefix) {
        List<Product> products = new LinkedList<>();

        for (int i = 0; i < count; ++i) {
            String id = String.format("%s-%d-%d", prefix, (i + 1), TestUtil.randomInt());
            products.add(this.createProduct(id, id, owner));
        }

        return products;
    }

    protected List<Product> createDependentProducts(Owner owner, int count, String prefix,
        Collection<Product> required) {

        List<Product> products = new LinkedList<>();

        for (int i = 0; i < count; ++i) {
            int rand = TestUtil.randomInt(10000);

            String cid = String.format("%s_content-%d-%d", prefix, (i + 1), rand);

            Content content = TestUtil.createContent(cid, cid);
            for (Product rprod : required) {
                content.addModifiedProductId(rprod.getId());
            }
            this.createContent(content, owner);

            String id = String.format("%s-%d-%d", prefix, (i + 1), rand);
            Product product = new Product(id, id);
            product.addContent(content, true);

            products.add(this.createProduct(product, owner));
        }

        return products;
    }

    protected Pool createPoolWithProducts(Owner owner, String sku, Collection<Product> provided) {
        Product skuProd = this.createProduct(sku, sku, owner);

        Pool pool = this.createPool(owner, skuProd, provided, 1000L, TestUtil.createDate(2000, 1, 1),
            TestUtil.createDate(2100, 1, 1));

        return pool;
    }

    protected void resetDirtyEntitlements(Entitlement... entitlements) {
        for (Entitlement entitlement : entitlements) {
            entitlement.setDirty(false);
            this.entitlementCurator.merge(entitlement);
        }

        this.entitlementCurator.flush();
    }

    @Test
    public void testMarkDependentEntitlementsDirty() {
        Owner owner = this.createOwner("test_owner");
        Consumer consumer = this.createConsumer(owner);

        List<Product> requiredProducts = this.createProducts(owner, 3, "test_req_prod");
        List<Product> dependentProduct1 = this.createDependentProducts(owner, 1, "test_dep_prod_a",
            requiredProducts.subList(0, 1));
        List<Product> dependentProduct2 = this.createDependentProducts(owner, 1, "test_dep_prod_b",
            requiredProducts.subList(1, 2));
        List<Product> dependentProduct3 = this.createDependentProducts(owner, 1, "test_dep_prod_c",
            requiredProducts.subList(2, 3));

        // reqPool1 includes requiredProducts 1 and 2
        // reqPool2 includes requiredProducts 2 and 3
        // reqPool3 includes requiredProducts 1, 2 and 3
        Pool requiredPool1 = this.createPoolWithProducts(owner, "reqPool1", requiredProducts.subList(0, 2));
        Pool requiredPool2 = this.createPoolWithProducts(owner, "reqPool2", requiredProducts.subList(1, 3));
        Pool requiredPool3 = this.createPoolWithProducts(owner, "reqPool3", requiredProducts.subList(0, 3));
        Pool dependentPool1 = this.createPoolWithProducts(owner, "depPool1", dependentProduct1);
        Pool dependentPool2 = this.createPoolWithProducts(owner, "depPool2", dependentProduct2);
        Pool dependentPool3 = this.createPoolWithProducts(owner, "depPool3", dependentProduct3);

        // Bind to requiredPool1
        Entitlement reqPool1Ent = this.bind(consumer, requiredPool1);

        // Consumer has no dependent entitlements (yet), so we should get an output of zero here
        int count = this.entitlementCurator.markDependentEntitlementsDirty(
            Arrays.asList(reqPool1Ent.getId()));
        assertEquals(0, count);

        // Bind to dependentPool3 (which requires reqProd3, that is not yet bound)
        Entitlement depPool3Ent = this.bind(consumer, dependentPool3);

        // We have a dependent entitlement now, but the entitlements are not related, so we should
        // still have zero entitlements affected
        count = this.entitlementCurator.markDependentEntitlementsDirty(Arrays.asList(reqPool1Ent.getId()));
        assertEquals(0, count);

        // Bind to depPool2, which requires reqProd2, which we have through reqPool1
        Entitlement depPool2Ent = this.bind(consumer, dependentPool2);

        // We should be marking our depPool2Ent dirty now...
        count = this.entitlementCurator.markDependentEntitlementsDirty(Arrays.asList(reqPool1Ent.getId()));
        assertEquals(1, count);

        this.entitlementCurator.refresh(reqPool1Ent, depPool2Ent, depPool3Ent);
        assertFalse(reqPool1Ent.isDirty());
        assertFalse(depPool3Ent.isDirty());
        assertTrue(depPool2Ent.isDirty());

        // Reset entitlements so we don't have any dirty flags already set
        this.resetDirtyEntitlements(reqPool1Ent, depPool3Ent, depPool2Ent);

        // Bind to requiredPool1
        Entitlement reqPool2Ent = this.bind(consumer, requiredPool2);

        // We should now hit both depPool2 and depPool3 since we have reqProd 1, 2 and 3 entitled
        count = this.entitlementCurator
            .markDependentEntitlementsDirty(Arrays.asList(reqPool1Ent.getId(), reqPool2Ent.getId()));
        assertEquals(2, count);

        this.entitlementCurator.refresh(reqPool1Ent, reqPool2Ent, depPool2Ent, depPool3Ent);
        assertFalse(reqPool1Ent.isDirty());
        assertFalse(reqPool2Ent.isDirty());
        assertTrue(depPool2Ent.isDirty());
        assertTrue(depPool3Ent.isDirty());

        this.resetDirtyEntitlements(reqPool1Ent, reqPool2Ent, depPool2Ent, depPool3Ent);

        // Unbind from reqPool1, which leaves us with reqProd 2 and 3.
        this.entitlementCurator.delete(reqPool1Ent);
        this.entitlementCurator.flush();

        // We should still hit both depPool2 and depPool3 since we still have reqProd 2 and 3 through
        // reqPool2
        count = this.entitlementCurator
            .markDependentEntitlementsDirty(Arrays.asList(reqPool1Ent.getId(), reqPool2Ent.getId()));
        assertEquals(2, count);

        this.entitlementCurator.refresh(reqPool2Ent, depPool2Ent, depPool3Ent);
        assertFalse(reqPool2Ent.isDirty());
        assertTrue(depPool2Ent.isDirty());
        assertTrue(depPool3Ent.isDirty());
    }

    @Test
    public void testMarkDependentEntitlementsDirtyDoesNotUpdateUnrelatedEntitlements() {
        Owner owner = this.createOwner("test_owner");
        Consumer consumer = this.createConsumer(owner);

        List<Product> requiredProducts = this.createProducts(owner, 3, "test_req_prod");
        List<Product> dependentProduct1 = this.createDependentProducts(owner, 1, "test_dep_prod_a",
            requiredProducts.subList(0, 1));
        List<Product> dependentProduct2 = this.createDependentProducts(owner, 1, "test_dep_prod_b",
            requiredProducts.subList(1, 2));
        List<Product> dependentProduct3 = this.createDependentProducts(owner, 1, "test_dep_prod_c",
            requiredProducts.subList(2, 3));

        Pool requiredPool1 = this.createPoolWithProducts(owner, "reqPool1", requiredProducts.subList(0, 1));
        Pool requiredPool2 = this.createPoolWithProducts(owner, "reqPool2", requiredProducts.subList(1, 2));
        Pool requiredPool3 = this.createPoolWithProducts(owner, "reqPool3", requiredProducts.subList(2, 3));
        Pool dependentPool1 = this.createPoolWithProducts(owner, "depPool1", dependentProduct1);
        Pool dependentPool2 = this.createPoolWithProducts(owner, "depPool2", dependentProduct2);
        Pool dependentPool3 = this.createPoolWithProducts(owner, "depPool3", dependentProduct3);

        Entitlement reqPool1Ent = this.bind(consumer, requiredPool1);
        Entitlement depPool1Ent = this.bind(consumer, dependentPool1);
        Entitlement depPool2Ent = this.bind(consumer, dependentPool2);
        Entitlement depPool3Ent = this.bind(consumer, dependentPool3);

        int count = this.entitlementCurator.markDependentEntitlementsDirty(
            Arrays.asList(reqPool1Ent.getId()));
        assertEquals(1, count);

        this.entitlementCurator.refresh(reqPool1Ent, depPool1Ent, depPool2Ent, depPool3Ent);
        assertFalse(reqPool1Ent.isDirty());
        assertTrue(depPool1Ent.isDirty());
        assertFalse(depPool2Ent.isDirty());
        assertFalse(depPool3Ent.isDirty());
    }

    @Test
    public void testMarkDependentEntitlementsDirtyDoesNotUpdateOtherConsumerEntitlements() {
        Owner owner = this.createOwner("test_owner");
        Consumer consumer1 = this.createConsumer(owner);
        Consumer consumer2 = this.createConsumer(owner);

        List<Product> requiredProducts = this.createProducts(owner, 3, "test_req_prod");
        List<Product> dependentProduct1 = this.createDependentProducts(owner, 1, "test_dep_prod_a",
            requiredProducts.subList(0, 1));
        List<Product> dependentProduct2 = this.createDependentProducts(owner, 1, "test_dep_prod_b",
            requiredProducts.subList(1, 2));
        List<Product> dependentProduct3 = this.createDependentProducts(owner, 1, "test_dep_prod_c",
            requiredProducts.subList(2, 3));

        Pool requiredPool1 = this.createPoolWithProducts(owner, "reqPool1", requiredProducts.subList(0, 1));
        Pool requiredPool2 = this.createPoolWithProducts(owner, "reqPool2", requiredProducts.subList(1, 2));
        Pool requiredPool3 = this.createPoolWithProducts(owner, "reqPool3", requiredProducts.subList(2, 3));
        Pool dependentPool1 = this.createPoolWithProducts(owner, "depPool1", dependentProduct1);
        Pool dependentPool2 = this.createPoolWithProducts(owner, "depPool2", dependentProduct2);
        Pool dependentPool3 = this.createPoolWithProducts(owner, "depPool3", dependentProduct3);

        Entitlement reqPool1Ent = this.bind(consumer1, requiredPool1);
        Entitlement depPool1EntA = this.bind(consumer1, dependentPool1);
        Entitlement depPool2EntA = this.bind(consumer1, dependentPool2);
        Entitlement depPool3EntA = this.bind(consumer1, dependentPool3);
        Entitlement depPool1EntB = this.bind(consumer2, dependentPool1);
        Entitlement depPool2EntB = this.bind(consumer2, dependentPool2);
        Entitlement depPool3EntB = this.bind(consumer2, dependentPool3);

        int count = this.entitlementCurator.markDependentEntitlementsDirty(
            Arrays.asList(reqPool1Ent.getId()));
        assertEquals(1, count);

        this.entitlementCurator.refresh(reqPool1Ent, depPool1EntA, depPool2EntA, depPool3EntA, depPool1EntB,
            depPool2EntB, depPool3EntB);

        assertFalse(reqPool1Ent.isDirty());
        assertTrue(depPool1EntA.isDirty());
        assertFalse(depPool2EntA.isDirty());
        assertFalse(depPool3EntA.isDirty());
        assertFalse(depPool1EntB.isDirty());
        assertFalse(depPool2EntB.isDirty());
        assertFalse(depPool3EntB.isDirty());
    }

    @Test
    public void testMarkDependentEntitlementsDirtyDoesNotUpdateOtherOwnerEntitlements() {
        Owner owner1 = this.createOwner("test_owner-1");
        Owner owner2 = this.createOwner("test_owner-2");
        Consumer consumer1 = this.createConsumer(owner1);
        Consumer consumer2 = this.createConsumer(owner2);

        List<Product> reqProducts1 = Arrays.asList(
            this.createProduct("req_prod_1", "req_prod_1", owner1),
            this.createProduct("req_prod_2", "req_prod_2", owner1));

        List<Product> reqProducts2 = Arrays.asList(
            this.createProduct("req_prod_1", "req_prod_1", owner2),
            this.createProduct("req_prod_2", "req_prod_2", owner2));

        List<Product> dependentProductA = this.createDependentProducts(owner1, 1, "test_dep_prod_a",
            reqProducts1.subList(0, 1));
        List<Product> dependentProductB = this.createDependentProducts(owner2, 1, "test_dep_prod_b",
            reqProducts2.subList(0, 1));

        Pool requiredPool1A = this.createPoolWithProducts(owner1, "reqPool1", reqProducts1.subList(0, 1));
        Pool requiredPool2A = this.createPoolWithProducts(owner1, "reqPool2", reqProducts1.subList(1, 2));
        Pool requiredPool1B = this.createPoolWithProducts(owner2, "reqPool1", reqProducts2.subList(0, 1));
        Pool requiredPool2B = this.createPoolWithProducts(owner2, "reqPool2", reqProducts2.subList(1, 2));
        Pool dependentPoolA = this.createPoolWithProducts(owner1, "depPool1", dependentProductA);
        Pool dependentPoolB = this.createPoolWithProducts(owner2, "depPool2", dependentProductB);

        Entitlement reqPool1AEnt = this.bind(consumer1, requiredPool1A);
        Entitlement reqPool2AEnt = this.bind(consumer1, requiredPool2A);
        Entitlement depPoolAEnt = this.bind(consumer1, dependentPoolA);
        Entitlement reqPool1BEnt = this.bind(consumer2, requiredPool1B);
        Entitlement reqPool2BEnt = this.bind(consumer2, requiredPool2B);
        Entitlement depPoolBEnt = this.bind(consumer2, dependentPoolB);

        int count = this.entitlementCurator.markDependentEntitlementsDirty(
            Arrays.asList(reqPool1AEnt.getId(), reqPool2AEnt.getId()));
        assertEquals(1, count);

        this.entitlementCurator.refresh(reqPool1AEnt, reqPool2AEnt, depPoolAEnt, reqPool1BEnt, reqPool2BEnt,
            depPoolBEnt);

        assertFalse(reqPool1AEnt.isDirty());
        assertFalse(reqPool2AEnt.isDirty());
        assertTrue(depPoolAEnt.isDirty());
        assertFalse(reqPool1BEnt.isDirty());
        assertFalse(reqPool2BEnt.isDirty());
        assertFalse(depPoolBEnt.isDirty());
    }

    @Test
    public void testMarkDependentEntitlementsDirtyUpdatesMultipleConsumers() {
        Owner owner1 = this.createOwner("test_owner-1");
        Owner owner2 = this.createOwner("test_owner-2");
        Consumer consumer1 = this.createConsumer(owner1);
        Consumer consumer2 = this.createConsumer(owner2);

        List<Product> reqProds1 = Arrays.asList(
            this.createProduct("req_prod_1", "req_prod_1", owner1),
            this.createProduct("req_prod_2", "req_prod_2", owner1));

        List<Product> reqProds2 = Arrays.asList(
            this.createProduct("req_prod_1", "req_prod_1", owner2),
            this.createProduct("req_prod_2", "req_prod_2", owner2));

        List<Product> dependentProductA = this.createDependentProducts(owner1, 1, "test_dep_prod_a",
            reqProds1.subList(0, 1));
        List<Product> dependentProductB = this.createDependentProducts(owner2, 1, "test_dep_prod_b",
            reqProds2.subList(0, 1));

        Pool requiredPool1A = this.createPoolWithProducts(owner1, "reqPool1", reqProds1.subList(0, 1));
        Pool requiredPool2A = this.createPoolWithProducts(owner1, "reqPool2", reqProds1.subList(1, 2));
        Pool requiredPool1B = this.createPoolWithProducts(owner2, "reqPool1", reqProds2.subList(0, 1));
        Pool requiredPool2B = this.createPoolWithProducts(owner2, "reqPool2", reqProds2.subList(1, 2));
        Pool dependentPoolA = this.createPoolWithProducts(owner1, "depPool1", dependentProductA);
        Pool dependentPoolB = this.createPoolWithProducts(owner2, "depPool2", dependentProductB);

        Entitlement reqPool1AEnt = this.bind(consumer1, requiredPool1A);
        Entitlement reqPool2AEnt = this.bind(consumer1, requiredPool2A);
        Entitlement depPoolAEnt = this.bind(consumer1, dependentPoolA);
        Entitlement reqPool1BEnt = this.bind(consumer2, requiredPool1B);
        Entitlement reqPool2BEnt = this.bind(consumer2, requiredPool2B);
        Entitlement depPoolBEnt = this.bind(consumer2, dependentPoolB);

        int count = this.entitlementCurator.markDependentEntitlementsDirty(
            Arrays.asList(reqPool1AEnt.getId(), reqPool2AEnt.getId(), reqPool1BEnt.getId(),
            reqPool2BEnt.getId()));
        assertEquals(2, count);

        this.entitlementCurator.refresh(reqPool1AEnt, reqPool2AEnt, depPoolAEnt, reqPool1BEnt, reqPool2BEnt,
            depPoolBEnt);

        assertFalse(reqPool1AEnt.isDirty());
        assertFalse(reqPool2AEnt.isDirty());
        assertTrue(depPoolAEnt.isDirty());
        assertFalse(reqPool1BEnt.isDirty());
        assertFalse(reqPool2BEnt.isDirty());
        assertTrue(depPoolBEnt.isDirty());
    }

    @Test
    public void testMarkDependentEntitlementsDirtyDoesNotRequireAllRequiredProducts() {
        // The "modified products" or "required products" collection on a content is a disjunction,
        // not a conjunction. The presence of any of those products should link two entitlements.

        Owner owner = this.createOwner("test_owner");
        Consumer consumer = this.createConsumer(owner);

        List<Product> requiredProducts = this.createProducts(owner, 3, "test_req_prod");
        List<Product> dependentProduct = this.createDependentProducts(owner, 1, "test_dep_prod_a",
            requiredProducts);

        Pool requiredPool1 = this.createPoolWithProducts(owner, "reqPool1", requiredProducts.subList(0, 1));
        Pool requiredPool2 = this.createPoolWithProducts(owner, "reqPool2", requiredProducts.subList(1, 2));
        Pool requiredPool3 = this.createPoolWithProducts(owner, "reqPool3", requiredProducts.subList(2, 3));
        Pool dependentPool = this.createPoolWithProducts(owner, "depPool1", dependentProduct);

        Entitlement reqPool1Ent = this.bind(consumer, requiredPool2);
        Entitlement depPool1Ent = this.bind(consumer, dependentPool);

        int count = this.entitlementCurator.markDependentEntitlementsDirty(
            Arrays.asList(reqPool1Ent.getId()));
        assertEquals(1, count);

        this.entitlementCurator.refresh(reqPool1Ent, depPool1Ent);
        assertFalse(reqPool1Ent.isDirty());
        assertTrue(depPool1Ent.isDirty());
    }

    @Test
    public void testMarkDependentEntitlementsDirtyDoesNotExamineSkuProducts() {
        Owner owner = this.createOwner("test_owner");
        Consumer consumer = this.createConsumer(owner);

        List<Product> requiredProducts = this.createProducts(owner, 3, "test_req_prod");
        List<Product> providedProducts = this.createProducts(owner, 3, "test_prov_prod");
        List<Product> dependentProduct = this.createDependentProducts(owner, 1, "test_dep_prod_a",
            requiredProducts);

        Pool requiredPool = this.createPool(owner, requiredProducts.get(0), providedProducts, 1000L,
            TestUtil.createDate(2000, 1, 1), TestUtil.createDate(2100, 1, 1));

        Pool dependentPool = this.createPoolWithProducts(owner, "depPool1", dependentProduct);

        Entitlement reqPool1Ent = this.bind(consumer, requiredPool);
        Entitlement depPool1Ent = this.bind(consumer, dependentPool);

        int count = this.entitlementCurator.markDependentEntitlementsDirty(
            Arrays.asList(reqPool1Ent.getId()));
        assertEquals(0, count);

        this.entitlementCurator.refresh(reqPool1Ent, depPool1Ent);
        assertFalse(reqPool1Ent.isDirty());
        assertFalse(depPool1Ent.isDirty());
    }

    @Test
    public void testMarkDependentEntitlementsDirtyDoesNotExamineDerivedProducts() {
        Owner owner = this.createOwner("test_owner");
        Consumer consumer = this.createConsumer(owner);

        List<Product> requiredProducts = this.createProducts(owner, 3, "test_req_prod");
        List<Product> providedProducts = this.createProducts(owner, 3, "test_prov_prod");
        List<Product> dependentProduct = this.createDependentProducts(owner, 1, "test_dep_prod_a",
            requiredProducts);

        Pool requiredPool = this.createPoolWithProducts(owner, "reqPool1", providedProducts);
        requiredPool.setDerivedProduct(requiredProducts.get(0));
        this.poolCurator.merge(requiredPool);

        Pool dependentPool = this.createPoolWithProducts(owner, "depPool1", dependentProduct);

        Entitlement reqPool1Ent = this.bind(consumer, requiredPool);
        Entitlement depPool1Ent = this.bind(consumer, dependentPool);

        int count = this.entitlementCurator.markDependentEntitlementsDirty(
            Arrays.asList(reqPool1Ent.getId()));
        assertEquals(0, count);

        this.entitlementCurator.refresh(reqPool1Ent, depPool1Ent);
        assertFalse(reqPool1Ent.isDirty());
        assertFalse(depPool1Ent.isDirty());
    }

    @Test
    public void testMarkDependentEntitlementsDirtyDoesNotExamineDerivedProvidedProducts() {
        Owner owner = this.createOwner("test_owner");
        Consumer consumer = this.createConsumer(owner);

        List<Product> requiredProducts = this.createProducts(owner, 3, "test_req_prod");
        List<Product> providedProducts = this.createProducts(owner, 3, "test_prov_prod");
        List<Product> dependentProduct = this.createDependentProducts(owner, 1, "test_dep_prod_a",
            requiredProducts);

        Pool requiredPool = this.createPoolWithProducts(owner, "reqPool1", providedProducts);
        requiredPool.setDerivedProvidedProducts(requiredProducts);
        this.poolCurator.merge(requiredPool);

        Pool dependentPool = this.createPoolWithProducts(owner, "depPool1", dependentProduct);

        Entitlement reqPool1Ent = this.bind(consumer, requiredPool);
        Entitlement depPool1Ent = this.bind(consumer, dependentPool);

        int count = this.entitlementCurator.markDependentEntitlementsDirty(
            Arrays.asList(reqPool1Ent.getId()));
        assertEquals(0, count);

        this.entitlementCurator.refresh(reqPool1Ent, depPool1Ent);
        assertFalse(reqPool1Ent.isDirty());
        assertFalse(depPool1Ent.isDirty());
    }

    @Test
    public void testFilterOutDistributorEntitlements() {
        Product product = TestUtil.createProduct();
        productCurator.create(product);

        Consumer distributor = createDistributor(owner);

        Pool pool = createPool(owner, product, 10L,
            dateSource.currentDate(), createFutureDate(1));
        poolCurator.create(pool);
        Entitlement distributorEnt1 = bind(distributor, pool);
        Entitlement consumerEnt1 = bind(consumer, pool);

        List<String> entsToFilter = new LinkedList<>();
        entsToFilter.add(distributorEnt1.getId());
        entsToFilter.add(consumerEnt1.getId());

        Set<String> filtered = entitlementCurator.filterDistributorEntitlementIds(entsToFilter);
        assertEquals(1, filtered.size());
        assertTrue(filtered.contains(distributorEnt1.getId()));
    }

    @Test
    public void testGetDependentEntitlementIds() {
        Owner owner = this.createOwner("test_owner");
        Consumer consumer = this.createConsumer(owner);
        Consumer distributor = this.createDistributor(owner);

        List<Product> requiredProducts = this.createProducts(owner, 3, "test_req_prod");
        List<Product> providedProducts = this.createProducts(owner, 3, "test_prov_prod");
        List<Product> dependentProducts = this.createDependentProducts(owner, 1, "test_dep_prod_a",
            requiredProducts);

        Pool requiredPool = this.createPoolWithProducts(owner, "reqPool1", providedProducts);
        requiredPool.addProvidedProduct(requiredProducts.get(0));
        this.poolCurator.merge(requiredPool);

        Pool dependentPool = this.createPoolWithProducts(owner, "depPool1", dependentProducts);

        // Bind the ents for the normal consumer
        assertNotNull(this.bind(consumer, requiredPool));
        Entitlement dependentEnt = this.bind(consumer, dependentPool);
        assertNotNull(dependentEnt);

        // A regular consumer should have dependent ents based on provided products.
        Collection<String> poolIds = entitlementCurator.getDependentEntitlementIdsForPools(consumer,
            Arrays.asList(requiredPool.getId()));
        assertEquals(1, poolIds.size());
        assertTrue(poolIds.contains(dependentEnt.getId()));

        // Bind the ents for the distributor consumer
        ConsumerType ctype = this.consumerTypeCurator.getConsumerType(distributor);
        assertNotNull(ctype);
        assertTrue(ctype.isManifest());

        assertNotNull(this.bind(distributor, requiredPool));
        Entitlement distributorDependentEnt = this.bind(distributor, dependentPool);
        assertNotNull(distributorDependentEnt);

        // A distributor should have the same dependent entitlement
        poolIds = entitlementCurator.getDependentEntitlementIdsForPools(distributor,
            Arrays.asList(requiredPool.getId()));
        assertEquals(1, poolIds.size());
        assertTrue(poolIds.contains(distributorDependentEnt.getId()));
    }

    @Test
    public void testGetDependentEntitlementIdsForPoolsMatchesOnDerivedProvidedProductsForDistributor() {
        Owner owner = this.createOwner("test_owner");
        Consumer consumer = this.createConsumer(owner);
        Consumer distributor = this.createDistributor(owner);

        List<Product> requiredProducts = this.createProducts(owner, 3, "test_req_prod");
        List<Product> providedProducts = this.createProducts(owner, 3, "test_prov_prod");
        List<Product> dependentProducts = this.createDependentProducts(owner, 1, "test_dep_prod_a",
            requiredProducts);

        Pool requiredPool = this.createPoolWithProducts(owner, "reqPool1", providedProducts);
        requiredPool.setDerivedProduct(providedProducts.get(0));
        requiredPool.addDerivedProvidedProduct(requiredProducts.get(0));
        this.poolCurator.merge(requiredPool);

        Pool dependentPool = this.createPoolWithProducts(owner, "depPool1", dependentProducts);

        // Bind the ents for the normal consumer
        assertNotNull(this.bind(consumer, requiredPool));
        Entitlement dependentEnt = this.bind(consumer, dependentPool);
        assertNotNull(dependentEnt);

        // A regular consumer should not have any dependent ents based on
        // derived products.
        Collection<String> poolIds = entitlementCurator.getDependentEntitlementIdsForPools(consumer,
            Arrays.asList(requiredPool.getId()));
        assertEquals(0, poolIds.size());

        // Bind the ents for the distributor consumer
        ConsumerType ctype = this.consumerTypeCurator.getConsumerType(distributor);
        assertNotNull(ctype);
        assertTrue(ctype.isManifest());

        assertNotNull(this.bind(distributor, requiredPool));
        Entitlement distributorDependentEnt = this.bind(distributor, dependentPool);
        assertNotNull(distributorDependentEnt);

        // A distributor should have a dependent ent due to derived product match.
        poolIds = entitlementCurator.getDependentEntitlementIdsForPools(distributor,
            Arrays.asList(requiredPool.getId()));
        assertEquals(1, poolIds.size());
        assertTrue(poolIds.contains(distributorDependentEnt.getId()));
    }

    @Test
    public void testBatchDeleteEntitlementsWithLargeDataSet() {
        // We're only expecting 10 or so total deletions, but the parameters we provide should not
        // exceed the limit in a single query. At the time of writing, a signed, two-byte value
        // seems to be used for parameter definitions, so we'll use 32k as our in-test minimum, but
        // use the larger of that and actual value of getParameterLimit as our base parameter limit

        int defaultLimit = 32000;
        int configLimit = this.entitlementCurator.getQueryParameterLimit();
        int testLimit = Math.max(defaultLimit, configLimit) * 2;

        int entitlementCount = 10;

        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner);
        Product product = this.createProduct(owner);
        Pool pool = this.createPool(owner, product);

        List<String> entIds = new LinkedList<>();

        // Pad the entIds collection with a ton of extra IDs so we can exceed the known parameter
        // limits
        for (int i = 0; i < testLimit; ++i) {
            entIds.add("test_id-" + i);
        }

        // Add our actual IDs to the end so we verify the query isn't just being truncated
        for (int i = 0; i < entitlementCount; ++i) {
            Entitlement entitlement = this.createEntitlement(owner, consumer, pool);
            entIds.add(entitlement.getId());
        }

        int deleted = this.entitlementCurator.batchDeleteByIds(entIds);

        assertEquals(entitlementCount, deleted);
    }

}
