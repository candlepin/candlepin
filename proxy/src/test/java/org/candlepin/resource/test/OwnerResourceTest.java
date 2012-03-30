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
package org.candlepin.resource.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.audit.Event;
import org.candlepin.audit.EventFactory;
import org.candlepin.auth.Access;
import org.candlepin.auth.ConsumerPrincipal;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.ActivationKey;
import org.candlepin.model.ActivationKeyCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.OwnerPermission;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.Role;
import org.candlepin.model.Subscription;
import org.candlepin.resource.OwnerResource;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.jboss.resteasy.plugins.providers.atom.Entry;
import org.jboss.resteasy.plugins.providers.atom.Feed;
import org.junit.Before;
import org.junit.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
/**
 * OwnerResourceTest
 */
public class OwnerResourceTest extends DatabaseTestFixture {

    private static final String OWNER_NAME = "Jar Jar Binks";

    private OwnerResource ownerResource;
    private Owner owner;
    private List<Owner> owners;
    private Product product;
    private EventFactory eventFactory;
    private CandlepinCommonTestConfig config;

    @Before
    public void setUp() {
        this.ownerResource = injector.getInstance(OwnerResource.class);

        owner = ownerCurator.create(new Owner(OWNER_NAME));
        owners = new ArrayList<Owner>();
        owners.add(owner);
        product = TestUtil.createProduct();
        productCurator.create(product);
        eventFactory = injector.getInstance(EventFactory.class);
        this.config = (CandlepinCommonTestConfig) injector
            .getInstance(Config.class);
    }

    @Test
    public void testCreateOwner() {
        assertNotNull(owner);
        assertNotNull(ownerCurator.find(owner.getId()));
        assertTrue(owner.getPools().isEmpty());
    }

    @Test
    public void testSimpleDeleteOwner() {
        String id = owner.getId();
        ownerResource.deleteOwner(owner.getKey(), true);
        owner = ownerCurator.find(id);
        assertNull(owner);
    }

    @Test
    public void testRefreshPoolsWithNewSubscriptions() {
        Product prod = TestUtil.createProduct();
        productCurator.create(prod);

        Subscription sub = new Subscription(owner, prod,
            new HashSet<Product>(), 2000L, TestUtil.createDate(2010, 2, 9),
            TestUtil.createDate(3000, 2, 9), TestUtil.createDate(2010, 2, 12));
        subCurator.create(sub);

        // Trigger the refresh:
        poolManager.refreshPools(owner);
        List<Pool> pools = poolCurator.listByOwnerAndProduct(owner,
            prod.getId());
        assertEquals(1, pools.size());
        Pool newPool = pools.get(0);

        assertEquals(sub.getId(), newPool.getSubscriptionId());
        assertEquals(sub.getQuantity(), newPool.getQuantity());
        assertEquals(sub.getStartDate(), newPool.getStartDate());
        assertEquals(sub.getEndDate(), newPool.getEndDate());
    }

    @Test
    public void testRefreshPoolsWithChangedSubscriptions() {
        Product prod = TestUtil.createProduct();
        productCurator.create(prod);
        Pool pool = createPoolAndSub(createOwner(), prod, 1000L,
            TestUtil.createDate(2009, 11, 30),
            TestUtil.createDate(2015, 11, 30));
        Owner owner = pool.getOwner();

        Subscription sub = new Subscription(owner, prod,
            new HashSet<Product>(), 2000L, TestUtil.createDate(2010, 2, 9),
            TestUtil.createDate(3000, 2, 9), TestUtil.createDate(2010, 2, 12));
        subCurator.create(sub);
        assertTrue(pool.getQuantity() < sub.getQuantity());
        assertTrue(pool.getStartDate() != sub.getStartDate());
        assertTrue(pool.getEndDate() != sub.getEndDate());

        pool.setSubscriptionId(sub.getId());
        poolCurator.merge(pool);

        poolManager.refreshPools(owner);

        pool = poolCurator.find(pool.getId());
        assertEquals(sub.getId(), pool.getSubscriptionId());
        assertEquals(sub.getQuantity(), pool.getQuantity());
        assertEquals(sub.getStartDate(), pool.getStartDate());
        assertEquals(sub.getEndDate(), pool.getEndDate());
    }

    @Test
    public void testRefreshPoolsWithRemovedSubscriptions() {
        Product prod = TestUtil.createProduct();
        productCurator.create(prod);

        Subscription sub = new Subscription(owner, prod,
            new HashSet<Product>(), 2000L, TestUtil.createDate(2010, 2, 9),
            TestUtil.createDate(3000, 2, 9), TestUtil.createDate(2010, 2, 12));
        subCurator.create(sub);

        // Trigger the refresh:
        poolManager.refreshPools(owner);

        List<Pool> pools = poolCurator.listByOwnerAndProduct(owner,
            prod.getId());
        assertEquals(1, pools.size());
        Pool newPool = pools.get(0);
        String poolId = newPool.getId();
        // Now delete the subscription:
        subCurator.delete(sub);

        // Trigger the refresh:
        poolManager.refreshPools(owner);
        assertNull("Pool not having subscription should have been deleted",
            poolCurator.find(poolId));
    }

    @Test
    public void testRefreshMultiplePools() {
        Product prod = TestUtil.createProduct();
        productCurator.create(prod);
        Product prod2 = TestUtil.createProduct();
        productCurator.create(prod2);

        Subscription sub = new Subscription(owner, prod,
            new HashSet<Product>(), 2000L, TestUtil.createDate(2010, 2, 9),
            TestUtil.createDate(3000, 2, 9), TestUtil.createDate(2010, 2, 12));
        subCurator.create(sub);

        Subscription sub2 = new Subscription(owner, prod2,
            new HashSet<Product>(), 800L, TestUtil.createDate(2010, 2, 9),
            TestUtil.createDate(3000, 2, 9), TestUtil.createDate(2010, 2, 12));
        subCurator.create(sub2);

        // Trigger the refresh:
        poolManager.refreshPools(owner);

        List<Pool> pools = poolCurator.listByOwner(owner);
        assertEquals(2, pools.size());
    }

    @Test
    public void testComplexDeleteOwner() throws Exception {

        // Create some consumers:
        Consumer c1 = TestUtil.createConsumer(owner);
        consumerTypeCurator.create(c1.getType());
        consumerCurator.create(c1);
        Consumer c2 = TestUtil.createConsumer(owner);
        consumerTypeCurator.create(c2.getType());
        consumerCurator.create(c2);

        // Create a pool for this owner:
        Pool pool = TestUtil.createPool(owner, product);
        poolCurator.create(pool);

        // Give those consumers entitlements:
        poolManager.entitleByPool(c1, pool, 1);

        assertEquals(2, consumerCurator.listByOwner(owner).size());
        assertEquals(1, poolCurator.listByOwner(owner).size());
        assertEquals(1, entitlementCurator.listByOwner(owner).size());

        ownerResource.deleteOwner(owner.getKey(), true);

        assertEquals(0, consumerCurator.listByOwner(owner).size());
        assertNull(consumerCurator.findByUuid(c1.getUuid()));
        assertNull(consumerCurator.findByUuid(c2.getUuid()));
        assertEquals(0, poolCurator.listByOwner(owner).size());
        assertEquals(0, entitlementCurator.listByOwner(owner).size());
    }

    @Test(expected = ForbiddenException.class)
    public void testConsumerRoleCannotGetOwner() {
        Consumer c = TestUtil.createConsumer(owner);
        consumerTypeCurator.create(c.getType());
        consumerCurator.create(c);
        setupPrincipal(new ConsumerPrincipal(c));

        securityInterceptor.enable();

        ownerResource.getOwner(owner.getKey());
    }

    @Test
    public void testConsumerCanListPools() {
        Consumer c = TestUtil.createConsumer(owner);
        consumerTypeCurator.create(c.getType());
        consumerCurator.create(c);
        setupPrincipal(new ConsumerPrincipal(c));

        securityInterceptor.enable();

        ownerResource.getPools(owner.getKey(), null, null, false, null);
    }

    @Test
    public void testOwnerAdminCanGetPools() {
        setupPrincipal(owner, Access.ALL);

        Product p = TestUtil.createProduct();
        productCurator.create(p);
        Pool pool1 = TestUtil.createPool(owner, p);
        Pool pool2 = TestUtil.createPool(owner, p);
        poolCurator.create(pool1);
        poolCurator.create(pool2);

        List<Pool> pools = ownerResource.getPools(owner.getKey(),
            null, null, true, null);
        assertEquals(2, pools.size());
    }

    @Test(expected = ForbiddenException.class)
    public void ownerAdminCannotAccessAnotherOwnersPools() {
        Owner evilOwner = new Owner("evilowner");
        ownerCurator.create(evilOwner);
        setupPrincipal(evilOwner, Access.ALL);

        Product p = TestUtil.createProduct();
        productCurator.create(p);
        Pool pool1 = TestUtil.createPool(owner, p);
        Pool pool2 = TestUtil.createPool(owner, p);
        poolCurator.create(pool1);
        poolCurator.create(pool2);

        securityInterceptor.enable();

        // Filtering should just cause this to return no results:
        ownerResource.getPools(owner.getKey(), null, null, true, null);
    }

    @Test(expected = ForbiddenException.class)
    public void testOwnerAdminCannotListAllOwners() {
        setupPrincipal(owner, Access.ALL);

        securityInterceptor.enable();

        ownerResource.list(null);
    }

    @Test(expected = ForbiddenException.class)
    public void testOwnerAdminCannotDelete() {
        setupPrincipal(owner, Access.ALL);
        securityInterceptor.enable();
        ownerResource.deleteOwner(owner.getKey(), true);
    }

    private Event createConsumerCreatedEvent(Owner o) {
        // Rather than run through an entire call to ConsumerResource, we'll
        // fake the
        // events in the db:
        setupPrincipal(o, Access.ALL);
        Consumer consumer = TestUtil.createConsumer(o);
        consumerTypeCurator.create(consumer.getType());
        consumerCurator.create(consumer);
        Event e1 = eventFactory.consumerCreated(consumer);
        eventCurator.create(e1);
        return e1;
    }

    @Test
    public void ownersAtomFeed() {
        Owner owner2 = new Owner("anotherOwner");
        ownerCurator.create(owner2);

        Event e1 = createConsumerCreatedEvent(owner);
        // Make an event from another owner:
        createConsumerCreatedEvent(owner2);

        // Make sure we're acting as the correct owner admin:
        setupPrincipal(owner, Access.ALL);

        securityInterceptor.enable();

        Feed feed = ownerResource.getOwnerAtomFeed(owner.getKey());
        assertEquals(1, feed.getEntries().size());
        Entry entry = feed.getEntries().get(0);
        assertEquals(e1.getTimestamp(), entry.getPublished());
    }

    @Test(expected = ForbiddenException.class)
    public void ownerCannotAccessAnotherOwnersAtomFeed() {
        Owner owner2 = new Owner("anotherOwner");
        ownerCurator.create(owner2);

        // Or more specifically, gets no results, the call will not error out
        // because he has the correct role.
        createConsumerCreatedEvent(owner);

        setupPrincipal(owner2, Access.ALL);

        securityInterceptor.enable();

        ownerResource.getOwnerAtomFeed(owner.getKey());
    }

    @Test(expected = ForbiddenException.class)
    public void testConsumerRoleCannotAccessOwnerAtomFeed() {
        Consumer c = TestUtil.createConsumer(owner);
        consumerTypeCurator.create(c.getType());
        consumerCurator.create(c);
        setupPrincipal(new ConsumerPrincipal(c));

        securityInterceptor.enable();

        ownerResource.getOwnerAtomFeed(owner.getKey());
    }

    @Test(expected = ForbiddenException.class)
    public void consumerCannotAccessConsumerAtomFeed() {
        Consumer c = TestUtil.createConsumer(owner);
        consumerTypeCurator.create(c.getType());
        consumerCurator.create(c);
        setupPrincipal(new ConsumerPrincipal(c));

        securityInterceptor.enable();

        ownerResource.getConsumerAtomFeed(owner.getKey(), c.getUuid());
    }

    @Test(expected = ForbiddenException.class)
    public void consumerCannotListAllConsumersInOwner() {
        Consumer c = TestUtil.createConsumer(owner);
        consumerTypeCurator.create(c.getType());
        consumerCurator.create(c);
        setupPrincipal(new ConsumerPrincipal(c));

        securityInterceptor.enable();

        ownerResource.ownerConsumers(owner.getKey(), null, null);
    }

    public void consumerCanListPools() {
        Consumer c = TestUtil.createConsumer(owner);
        consumerTypeCurator.create(c.getType());
        consumerCurator.create(c);
        setupPrincipal(new ConsumerPrincipal(c));

        securityInterceptor.enable();

        ownerResource.getPools(owner.getKey(), null, null, false, null);
    }

    @Test(expected = ForbiddenException.class)
    public void ownerCannotAccessAnotherOwnersConsumerAtomFeed() {
        Owner owner2 = new Owner("anotherOwner");
        ownerCurator.create(owner2);

        securityInterceptor.enable();

        Event e1 = createConsumerCreatedEvent(owner);
        Consumer c = consumerCurator.find(e1.getEntityId());

        // Should see no results:
        setupPrincipal(owner2, Access.ALL);
        ownerResource.getConsumerAtomFeed(owner.getKey(), c.getUuid());
    }

    @Test
    public void consumersAtomFeed() {
        Owner owner2 = new Owner("anotherOwner");
        ownerCurator.create(owner2);

        // Make a consumer, we'll look for this creation event:
        Event e1 = createConsumerCreatedEvent(owner);
        Consumer c = consumerCurator.find(e1.getEntityId());

        // Make another consumer in this org, we do *not* want to see this in
        // the results:
        createConsumerCreatedEvent(owner);

        // Create another consumer in a different org, again do not want to see
        // this:
        setupPrincipal(owner2, Access.ALL);
        // leaving the interceptors off here because consumer types are created
        // by the test harness, which will fail if the interceptors are turned on
        createConsumerCreatedEvent(owner2);

        // Make sure we're acting as the correct owner admin:
        setupPrincipal(owner, Access.ALL);
        securityInterceptor.enable();

        Feed feed = ownerResource.getConsumerAtomFeed(owner.getKey(), c.getUuid());
        assertEquals(1, feed.getEntries().size());
        Entry entry = feed.getEntries().get(0);
        assertEquals(e1.getTimestamp(), entry.getPublished());
    }

    @Test
    public void testEntitlementsRevocationWithFifoOrder() throws Exception {
        Pool pool = doTestEntitlementsRevocationCommon(7, 4, 5, true);
        assertEquals(4L, this.poolCurator.find(pool.getId()).getConsumed()
            .longValue());
    }

    @Test
    public void testEntitlementsRevocationWithLifoOrder() throws Exception {
        Pool pool = doTestEntitlementsRevocationCommon(7, 4, 5, false);
        assertEquals(5L, this.poolCurator.find(pool.getId()).getConsumed()
            .longValue());
    }

    @Test
    public void testEntitlementsRevocationWithNoOverflow() throws Exception {
        Pool pool = doTestEntitlementsRevocationCommon(10, 4, 5, false);
        assertTrue(this.poolCurator.find(pool.getId()).getConsumed() == 9);
    }

    @Test
    public void testActivationKeyCreateRead() {
        ActivationKey key = new ActivationKey();
        key.setName("dd");
        key = ownerResource.createActivationKey(owner.getKey(), key);
        assertNotNull(key.getId());
        assertEquals(key.getOwner().getId(), owner.getId());
        List<ActivationKey> keys = ownerResource.ownerActivationKeys(owner.getKey());
        assertEquals(1, keys.size());
    }

    @Test(expected = BadRequestException.class)
    public void testActivationKeyRequiresName() {
        ActivationKey key = new ActivationKey();
        Owner owner = createOwner();
        key.setOwner(owner);
        key = ownerResource.createActivationKey(owner.getKey(), key);
    }

    private Pool doTestEntitlementsRevocationCommon(long subQ, int e1, int e2,
        boolean fifo) throws ParseException {
        Product prod = TestUtil.createProduct();
        productCurator.create(prod);
        Pool pool = createPoolAndSub(createOwner(), prod, 1000L,
            TestUtil.createDate(2009, 11, 30),
            TestUtil.createDate(2015, 11, 30));
        Owner owner = pool.getOwner();
        Consumer consumer = createConsumer(owner);
        Consumer consumer1 = createConsumer(owner);
        Subscription sub = this.subCurator.find(pool.getSubscriptionId());
        sub.setQuantity(subQ);
        this.subCurator.merge(sub);
        // this.ownerResource.refreshEntitlementPools(owner.getKey(), false);
        pool = this.poolCurator.find(pool.getId());
        createEntitlementWithQ(pool, owner, consumer, e1, "01/02/2010");
        createEntitlementWithQ(pool, owner, consumer1, e2, "01/01/2010");
        assertEquals(pool.getConsumed(), Long.valueOf(e1 + e2));
        this.config.setProperty(
            ConfigProperties.REVOKE_ENTITLEMENT_IN_FIFO_ORDER, fifo ? "true" :
                "false");
        poolManager.refreshPools(owner);
        pool = poolCurator.find(pool.getId());
        return pool;
    }

    /**
     * @param pool
     * @param owner
     * @param consumer
     * @return
     */
    private Entitlement createEntitlementWithQ(Pool pool, Owner owner,
        Consumer consumer, int quantity, String date) throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
        Entitlement e1 = createEntitlement(owner, consumer, pool, null);
        e1.setQuantity(quantity);
        pool.getEntitlements().add(e1);

        this.entitlementCurator.create(e1);
        this.poolCurator.merge(e1.getPool());
        this.poolCurator.refresh(pool);

        e1.setCreated(dateFormat.parse(date));
        this.entitlementCurator.merge(e1);

        return e1;
    }

    @Test
    public void ownerWithParentOwnerCanBeCreated() {
        Owner child = new Owner("name", "name1");
        child.setParentOwner(this.owner);
        this.ownerResource.createOwner(child);
        assertNotNull(ownerCurator.find(child.getId()));
        assertNotNull(child.getParentOwner());
        assertEquals(this.owner.getId(), child.getParentOwner().getId());
    }

    @Test(expected = BadRequestException.class)
    public void ownerWithInvalidParentCannotBeCreated() {
        Owner child = new Owner("name", "name1");
        Owner owner1 = new Owner("name2", "name3");
        owner1.setId("xyz");
        child.setParentOwner(owner1);
        this.ownerResource.createOwner(child);
        throw new RuntimeException(
            "OwnerResource should have thrown BadRequestException");
    }

    @Test(expected = BadRequestException.class)
    public void ownerWithInvalidParentWhoseIdIsNullCannotBeCreated() {
        Owner child = new Owner("name", "name1");
        Owner owner1 = new Owner("name2", "name3");
        child.setParentOwner(owner1);
        this.ownerResource.createOwner(child);
        throw new RuntimeException(
            "OwnerResource should have thrown BadRequestException");
    }

    @Test
    public void cleanupWithOutstandingPermissions() {
        OwnerPermission p = new OwnerPermission(owner, Access.ALL);
        Role r = new Role("rolename");
        r.addPermission(p);
        roleCurator.create(r);
        ownerResource.deleteOwner(owner.getKey(), false);
    }

    @Test(expected = NotFoundException.class)
    public void undoImportforOwnerWithNoImports() {
        Owner owner1 = new Owner("owner-with-no-imports", "foo");
        ownerResource.createOwner(owner1);
        ownerResource.undoImports(owner1.getKey());
    }

    @Test(expected = BadRequestException.class)
    public void testActivationKeyNameUnique() {
        ActivationKey ak = mock(ActivationKey.class);
        ActivationKey akOld = mock(ActivationKey.class);
        ActivationKeyCurator akc = mock(ActivationKeyCurator.class);
        Owner o = mock(Owner.class);
        OwnerCurator oc = mock(OwnerCurator.class);

        when(ak.getName()).thenReturn("testKey");
        when(akc.lookupForOwner(eq("testKey"), eq(o))).thenReturn(akOld);
        when(oc.lookupByKey(eq("testOwner"))).thenReturn(o);

        OwnerResource or = new OwnerResource(oc, null,
            null, akc, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null);
        or.createActivationKey("testOwner", ak);
    }
}
