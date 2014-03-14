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
package org.candlepin.resource.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.audit.Event;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.auth.Access;
import org.candlepin.auth.ConsumerPrincipal;
import org.candlepin.auth.Principal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.auth.permissions.PermissionFactory.PermissionType;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.exceptions.IseException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Entitlement;
import org.candlepin.model.ExporterMetadata;
import org.candlepin.model.ExporterMetadataCurator;
import org.candlepin.model.ImportRecord;
import org.candlepin.model.ImportRecordCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.PermissionBlueprint;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.Role;
import org.candlepin.model.Subscription;
import org.candlepin.model.SubscriptionCurator;
import org.candlepin.model.UpstreamConsumer;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.paging.PageRequest;
import org.candlepin.resource.OwnerResource;
import org.candlepin.resteasy.parameter.CandlepinParam;
import org.candlepin.resteasy.parameter.CandlepinParameterUnmarshaller;
import org.candlepin.resteasy.parameter.KeyValueParameter;
import org.candlepin.sync.ConflictOverrides;
import org.candlepin.sync.Importer;
import org.candlepin.sync.ImporterException;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.ContentOverrideValidator;
import org.jboss.resteasy.plugins.providers.atom.Entry;
import org.jboss.resteasy.plugins.providers.atom.Feed;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.jboss.resteasy.specimpl.MultivaluedMapImpl;
import org.jboss.resteasy.util.GenericType;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MultivaluedMap;
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
    private ImportRecordCurator importRecordCurator;
    private ContentOverrideValidator contentOverrideValidator;

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
        importRecordCurator = injector.getInstance(ImportRecordCurator.class);
        contentOverrideValidator = injector.getInstance(ContentOverrideValidator.class);
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
        poolManager.getRefresher().add(owner).run();
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

        poolManager.getRefresher().add(owner).run();

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
        poolManager.getRefresher().add(owner).run();

        List<Pool> pools = poolCurator.listByOwnerAndProduct(owner,
            prod.getId());
        assertEquals(1, pools.size());
        Pool newPool = pools.get(0);
        String poolId = newPool.getId();
        // Now delete the subscription:
        subCurator.delete(sub);

        // Trigger the refresh:
        poolManager.getRefresher().add(owner).run();
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
        poolManager.getRefresher().add(owner).run();

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
        Principal principal = setupPrincipal(new ConsumerPrincipal(c));

        securityInterceptor.enable();

        ownerResource.listPools(owner.getKey(), null, null, false, null,
            new ArrayList<KeyValueParameter>(), principal, null);
    }

    @Test
    public void testOwnerAdminCanGetPools() {
        Principal principal = setupPrincipal(owner, Access.ALL);

        Product p = TestUtil.createProduct();
        productCurator.create(p);
        Pool pool1 = TestUtil.createPool(owner, p);
        Pool pool2 = TestUtil.createPool(owner, p);
        poolCurator.create(pool1);
        poolCurator.create(pool2);

        List<Pool> pools = ownerResource.listPools(owner.getKey(),
            null, null, true, null, new ArrayList<KeyValueParameter>(), principal, null);
        assertEquals(2, pools.size());
    }

    @Test
    public void testCanFilterPoolsByAttribute() throws Exception {
        Principal principal = setupPrincipal(owner, Access.ALL);

        Product p = TestUtil.createProduct();
        productCurator.create(p);
        Pool pool1 = TestUtil.createPool(owner, p);
        pool1.setAttribute("virt_only", "true");
        poolCurator.create(pool1);

        Product p2 = TestUtil.createProduct();
        p2.setAttribute("cores", "12");
        Pool pool2 = TestUtil.createPool(owner, p2);
        poolCurator.create(pool2);

        List<KeyValueParameter> params = new ArrayList<KeyValueParameter>();
        params.add(createKeyValueParam("cores", "12"));

        List<Pool> pools = ownerResource.listPools(owner.getKey(), null, null, true,
            null, params, principal, null);
        assertEquals(1, pools.size());
        assertEquals(pool2, pools.get(0));

        params.clear();
        params.add(createKeyValueParam("virt_only", "true"));

        pools = ownerResource.listPools(owner.getKey(), null, null, true,
            null, params, principal, null);
        assertEquals(1, pools.size());
        assertEquals(pool1, pools.get(0));
    }

    @Test(expected = NotFoundException.class)
    public void ownerAdminCannotAccessAnotherOwnersPools() {
        Owner evilOwner = new Owner("evilowner");
        ownerCurator.create(evilOwner);
        Principal principal = setupPrincipal(evilOwner, Access.ALL);

        Product p = TestUtil.createProduct();
        productCurator.create(p);
        Pool pool1 = TestUtil.createPool(owner, p);
        Pool pool2 = TestUtil.createPool(owner, p);
        poolCurator.create(pool1);
        poolCurator.create(pool2);

        securityInterceptor.enable();

        // Filtering should just cause this to return no results:
        ownerResource.listPools(owner.getKey(), null, null, true, null,
            new ArrayList<KeyValueParameter>(), principal, null);
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

    @Test(expected = NotFoundException.class)
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
    public void consumerCannotListAllConsumersInOwner() {
        Consumer c = TestUtil.createConsumer(owner);
        consumerTypeCurator.create(c.getType());
        consumerCurator.create(c);
        setupPrincipal(new ConsumerPrincipal(c));

        securityInterceptor.enable();

        ownerResource.listConsumers(owner.getKey(), null, null,
            new ArrayList<String>(), null, null, null);
    }

    @Test
    public void consumerCanListConsumersByIdWhenOtherParametersPresent() {
        Consumer c = TestUtil.createConsumer(owner);
        consumerTypeCurator.create(c.getType());
        consumerCurator.create(c);

        List<String> uuids = new ArrayList<String>();
        uuids.add(c.getUuid());

        setupPrincipal(owner, Access.ALL);
        securityInterceptor.enable();

        Set<String> types = new HashSet<String>();
        types.add("type");
        consumerTypeCurator.create(new ConsumerType("type"));

        List<Consumer> results = ownerResource.listConsumers(
            owner.getKey(), "username", types, uuids, null, null, new PageRequest());

        assertEquals(0, results.size());
    }

    public void consumerCannotListConsumersFromAnotherOwner() {
        Consumer c = TestUtil.createConsumer(owner);
        consumerTypeCurator.create(c.getType());
        consumerCurator.create(c);

        Owner owner2 = ownerCurator.create(new Owner("Owner2"));
        Consumer c2 = TestUtil.createConsumer(owner2);
        consumerTypeCurator.create(c2.getType());
        consumerCurator.create(c2);

        List<String> uuids = new ArrayList<String>();
        uuids.add(c.getUuid());
        uuids.add(c2.getUuid());

        setupPrincipal(owner, Access.ALL);
        securityInterceptor.enable();

        assertEquals(1,
            ownerResource.listConsumers(owner.getKey(), null, null,
                uuids, null, null, null).size());
    }

    /**
     * I'm generally not a fan of testing this way, but in this case
     * I want to check that the exception message that is returned
     * correctly concats the invalid type name.
     */
    @Test
    public void failWhenListingByBadConsumerType() {
        Set<String> types = new HashSet<String>();
        types.add("unknown");
        try {
            ownerResource.listConsumers(owner.getKey(), null, types,
                new ArrayList<String>(), null, null, null);
            fail("Should have thrown a BadRequestException.");
        }
        catch (BadRequestException bre) {
            assertEquals("No such unit type(s): unknown",
                bre.getMessage());
        }
    }

    @Test
    public void consumerCanListMultipleConsumers() {
        Consumer c = TestUtil.createConsumer(owner);
        consumerTypeCurator.create(c.getType());
        consumerCurator.create(c);

        Consumer c2 = TestUtil.createConsumer(owner);
        consumerTypeCurator.create(c2.getType());
        consumerCurator.create(c2);

        List<String> uuids = new ArrayList<String>();
        uuids.add(c.getUuid());
        uuids.add(c2.getUuid());

        setupPrincipal(owner, Access.ALL);
        securityInterceptor.enable();

        List<Consumer> results = ownerResource.listConsumers(owner.getKey(), null,
            null, uuids, null, null, null);
        assertEquals(2, results.size());
    }

    @Test
    public void consumerListPoolsGetCalculatedAttributes() {
        Product p = TestUtil.createProduct();
        productCurator.create(p);
        Pool pool1 = TestUtil.createPool(owner, p);
        poolCurator.create(pool1);

        Consumer c = TestUtil.createConsumer(owner);
        consumerTypeCurator.create(c.getType());
        consumerCurator.create(c);

        Principal principal = setupPrincipal(new ConsumerPrincipal(c));
        securityInterceptor.enable();

        List<Pool> pools = ownerResource.listPools(owner.getKey(), c.getUuid(),
            p.getId(), true, null, new ArrayList<KeyValueParameter>(), principal, null);
        assertEquals(1, pools.size());
        Pool returnedPool = pools.get(0);
        assertNotNull(returnedPool.getCalculatedAttributes());
    }

    @Test(expected = NotFoundException.class)
    public void testConsumerListPoolsCannotAccessOtherConsumer() {
        Product p = TestUtil.createProduct();
        productCurator.create(p);
        Pool pool1 = TestUtil.createPool(owner, p);
        poolCurator.create(pool1);

        Consumer c = TestUtil.createConsumer(owner);
        consumerTypeCurator.create(c.getType());
        consumerCurator.create(c);

        securityInterceptor.enable();

        Owner owner2 = createOwner();
        ownerCurator.create(owner2);

        ownerResource.listPools(owner.getKey(), c.getUuid(),
            p.getId(), true, null, new ArrayList<KeyValueParameter>(),
            setupPrincipal(owner2, Access.NONE), null);
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
        poolManager.getRefresher().add(owner).run();
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
        PermissionBlueprint p = new PermissionBlueprint(PermissionType.OWNER, owner,
            Access.ALL);
        Role r = new Role("rolename");
        r.addPermission(p);
        roleCurator.create(r);
        ownerResource.deleteOwner(owner.getKey(), false);
    }

    @Test(expected = NotFoundException.class)
    public void undoImportforOwnerWithNoImports() {
        Owner owner1 = new Owner("owner-with-no-imports", "foo");
        ownerResource.createOwner(owner1);
        ownerResource.undoImports(owner1.getKey(),
            new UserPrincipal("JarjarBinks", null, true));
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

        OwnerResource or = new OwnerResource(oc,
            null, akc, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, contentOverrideValidator);
        or.createActivationKey("testOwner", ak);
    }

    @Test
    public void testUpdateOwner() {
        Owner owner = new Owner("Test Owner", "test");
        ownerCurator.create(owner);

        Product prod1 = TestUtil.createProduct();
        prod1.setAttribute("support_level", "premium");
        productCurator.create(prod1);
        Product prod2 = TestUtil.createProduct();
        prod2.setAttribute("support_level", "standard");
        productCurator.create(prod2);

        Subscription sub1 = new Subscription(owner, prod1,
            new HashSet<Product>(), 2000L, TestUtil.createDate(2010, 2, 9),
            TestUtil.createDate(3000, 2, 9), TestUtil.createDate(2010, 2, 12));
        subCurator.create(sub1);
        Subscription sub2 = new Subscription(owner, prod2,
            new HashSet<Product>(), 2000L, TestUtil.createDate(2010, 2, 9),
            TestUtil.createDate(3000, 2, 9), TestUtil.createDate(2010, 2, 12));
        subCurator.create(sub2);

        // Trigger the refresh:
        poolManager.getRefresher().add(owner).run();

        owner.setDefaultServiceLevel("premium");
        Owner parentOwner1 = new Owner("Paren Owner 1", "parentTest1");
        ownerResource.createOwner(parentOwner1);
        Owner parentOwner2 = new Owner("Paren Owner 2", "parentTest2");
        ownerResource.createOwner(parentOwner2);
        owner.setParentOwner(parentOwner1);
        ownerResource.createOwner(owner);

        // Update with Display Name Only
        Owner upOwner1 = mock(Owner.class);
        when(upOwner1.getDisplayName()).thenReturn("New Name");
        ownerResource.updateOwner(owner.getKey(), upOwner1);
        assertEquals("New Name", owner.getDisplayName());
        assertEquals(parentOwner1, owner.getParentOwner());
        assertEquals("premium", owner.getDefaultServiceLevel());

        // Update with Default Service Level only
        Owner upOwner2 = mock(Owner.class);
        when(upOwner2.getDefaultServiceLevel()).thenReturn("standard");
        ownerResource.updateOwner(owner.getKey(), upOwner2);
        assertEquals("standard", owner.getDefaultServiceLevel());
        assertEquals("New Name", owner.getDisplayName());
        assertEquals(parentOwner1, owner.getParentOwner());

        // Update with Parent Owner only
        Owner upOwner3 = mock(Owner.class);
        when(upOwner3.getParentOwner()).thenReturn(parentOwner2);
        ownerResource.updateOwner(owner.getKey(), upOwner3);
        assertEquals(parentOwner2, owner.getParentOwner());
        assertEquals("standard", owner.getDefaultServiceLevel());
        assertEquals("New Name", owner.getDisplayName());

        // Update with empty Service Level only
        Owner upOwner4 = mock(Owner.class);
        when(upOwner4.getDefaultServiceLevel()).thenReturn("");
        ownerResource.updateOwner(owner.getKey(), upOwner4);
        assertNull(owner.getDefaultServiceLevel());
        assertEquals("New Name", owner.getDisplayName());
        assertEquals(parentOwner2, owner.getParentOwner());
    }

    @Test
    public void testImportRecordSuccessWithFilename()
        throws IOException, ImporterException {
        Importer importer = mock(Importer.class);
        EventSink es = mock(EventSink.class);
        OwnerResource thisOwnerResource = new OwnerResource(ownerCurator, null,
            null, null, null, i18n, es, null, null, null, importer, null, null,
            null, importRecordCurator, null, null, null, null, null,
            null, null, null, contentOverrideValidator);

        MultipartInput input = mock(MultipartInput.class);
        InputPart part = mock(InputPart.class);
        File archive = mock(File.class);
        List<InputPart> parts = new ArrayList<InputPart>();
        parts.add(part);
        MultivaluedMap<String, String> mm = new MultivaluedMapImpl<String, String>();
        List<String> contDis = new ArrayList<String>();
        contDis.add("form-data; name=\"upload\"; filename=\"test_file.zip\"");
        mm.put("Content-Disposition", contDis);

        when(input.getParts()).thenReturn(parts);
        when(part.getHeaders()).thenReturn(mm);
        when(part.getBody(any(GenericType.class))).thenReturn(archive);
        when(importer.loadExport(eq(owner), any(File.class), any(ConflictOverrides.class)))
            .thenReturn(new HashMap<String, Object>());

        thisOwnerResource.importManifest(owner.getKey(), new String [] {}, input);
        List<ImportRecord> records = importRecordCurator.findRecords(owner);
        ImportRecord ir = records.get(0);
        assertEquals("test_file.zip", ir.getFileName());
        assertEquals(owner, ir.getOwner());
        assertEquals(ImportRecord.Status.SUCCESS, ir.getStatus());
    }

    @Test
    public void testImportRecordDeleteWithLogging()
        throws IOException, ImporterException {
        EventSink es = mock(EventSink.class);
        ExporterMetadataCurator ec = mock(ExporterMetadataCurator.class);
        SubscriptionCurator sc = mock(SubscriptionCurator.class);
        OwnerResource thisOwnerResource = new OwnerResource(ownerCurator, sc,
            null, null, null, i18n, es, null, null, null, null, null, ec,
            null, importRecordCurator, null, null, null, null, null,
            null, null, null, contentOverrideValidator);

        ExporterMetadata metadata = new ExporterMetadata();
        when(ec.lookupByTypeAndOwner(ExporterMetadata.TYPE_PER_USER, owner))
            .thenReturn(metadata);
        when(sc.listByOwner(owner)).thenReturn(new ArrayList<Subscription>());

        thisOwnerResource.undoImports(owner.getKey(),
            new UserPrincipal("JarJarBinks", null, true));
        List<ImportRecord> records = importRecordCurator.findRecords(owner);
        assertTrue(records.size() == 1);
        ImportRecord ir = records.get(0);
        assertTrue(ir.getStatus() == ImportRecord.Status.DELETE);
    }

    @Test
    public void testImportRecordFailureWithFilename()
        throws IOException, ImporterException {
        Importer importer = mock(Importer.class);
        EventSink es = mock(EventSink.class);
        OwnerResource thisOwnerResource = new OwnerResource(ownerCurator, null,
            null, null, null, i18n, es, null, null, null, importer, null, null,
            null, importRecordCurator, null, null, null, null, null,
            null, null, null, contentOverrideValidator);

        MultipartInput input = mock(MultipartInput.class);
        InputPart part = mock(InputPart.class);
        File archive = mock(File.class);
        List<InputPart> parts = new ArrayList<InputPart>();
        parts.add(part);
        MultivaluedMap<String, String> mm = new MultivaluedMapImpl<String, String>();
        List<String> contDis = new ArrayList<String>();
        contDis.add("form-data; name=\"upload\"; filename=\"test_file.zip\"");
        mm.put("Content-Disposition", contDis);

        when(input.getParts()).thenReturn(parts);
        when(part.getHeaders()).thenReturn(mm);
        when(part.getBody(any(GenericType.class))).thenReturn(archive);
        when(importer.loadExport(eq(owner), any(File.class), any(ConflictOverrides.class)))
            .thenThrow(new ImporterException("Bad import"));

        try {
            thisOwnerResource.importManifest(owner.getKey(), new String [] {}, input);
        }
        catch (IseException ise) {
            // expected, so we catch and go on.
        }
        List<ImportRecord> records = importRecordCurator.findRecords(owner);
        ImportRecord ir = records.get(0);
        assertEquals("test_file.zip", ir.getFileName());
        assertEquals(owner, ir.getOwner());
        assertEquals(ImportRecord.Status.FAILURE, ir.getStatus());
        assertEquals("Bad import", ir.getStatusMessage());
    }

    @Test
    public void upstreamConsumers() {
        Principal p = mock(Principal.class);
        OwnerCurator oc = mock(OwnerCurator.class);
        UpstreamConsumer upstream = mock(UpstreamConsumer.class);
        Owner owner = mock(Owner.class);
        OwnerResource ownerres = new OwnerResource(oc, null,
            null, null, null, i18n, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            contentOverrideValidator);

        when(oc.lookupByKey(eq("admin"))).thenReturn(owner);
        when(owner.getUpstreamConsumer()).thenReturn(upstream);

        List<UpstreamConsumer> results = ownerres.getUpstreamConsumers(p, "admin");
        assertNotNull(results);
        assertEquals(1, results.size());
    }

    @Test
    public void testSetAndDeleteOwnerLogLevel() {
        Owner owner = new Owner("Test Owner", "test");
        ownerCurator.create(owner);
        ownerResource.setLogLevel(owner.getKey(), "ALL");

        owner = ownerCurator.lookupByKey(owner.getKey());
        assertEquals(owner.getLogLevel(), "ALL");

        ownerResource.deleteLogLevel(owner.getKey());
        owner = ownerCurator.lookupByKey(owner.getKey());
        assertNull(owner.getLogLevel());
    }

    @Test(expected = BadRequestException.class)
    public void testSetBadLogLevel() {
        Owner owner = new Owner("Test Owner", "test");
        ownerCurator.create(owner);
        ownerResource.setLogLevel(owner.getKey(), "THISLEVELISBAD");
    }

    @QueryParam("test-attr") @CandlepinParam(type = KeyValueParameter.class)
    private KeyValueParameter createKeyValueParam(String key, String val) throws Exception {
        // Can't create the KeyValueParam directly as the parse method
        // is package protected -- create one via the unmarshaller so we don't have to
        // change the visibility of the parse method.
        Annotation[] annotations = this.getClass()
            .getDeclaredMethod("createKeyValueParam", String.class, String.class)
            .getAnnotations();
        CandlepinParameterUnmarshaller unmarshaller =
            new CandlepinParameterUnmarshaller();
        unmarshaller.setAnnotations(annotations);
        return (KeyValueParameter) unmarshaller.fromString(key + ":" + val);
    }
}
