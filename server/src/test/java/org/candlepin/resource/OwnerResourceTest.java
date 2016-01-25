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
package org.candlepin.resource;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.candlepin.audit.Event;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.auth.Access;
import org.candlepin.auth.ConsumerPrincipal;
import org.candlepin.auth.Principal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.auth.permissions.PermissionFactory.PermissionType;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.common.exceptions.IseException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.CandlepinPoolManager;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EntitlementCertificateCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EntitlementFilterBuilder;
import org.candlepin.model.EventCurator;
import org.candlepin.model.ImportRecord;
import org.candlepin.model.ImportRecordCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.PermissionBlueprint;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.Release;
import org.candlepin.model.Role;
import org.candlepin.model.RoleCurator;
import org.candlepin.model.UeberCertificateGenerator;
import org.candlepin.model.UpstreamConsumer;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.model.dto.Subscription;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.resteasy.parameter.CandlepinParam;
import org.candlepin.resteasy.parameter.CandlepinParameterUnmarshaller;
import org.candlepin.resteasy.parameter.KeyValueParameter;
import org.candlepin.service.impl.ImportSubscriptionServiceAdapter;
import org.candlepin.sync.ConflictOverrides;
import org.candlepin.sync.Importer;
import org.candlepin.sync.ImporterException;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.ContentOverrideValidator;
import org.candlepin.util.ServiceLevelValidator;
import org.candlepin.util.Util;

import org.hamcrest.core.IsEqual;
import org.jboss.resteasy.plugins.providers.atom.Entry;
import org.jboss.resteasy.plugins.providers.atom.Feed;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.jboss.resteasy.specimpl.MultivaluedMapImpl;
import org.jboss.resteasy.util.GenericType;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.xnap.commons.i18n.I18n;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MultivaluedMap;
/**
 * OwnerResourceTest
 */
public class OwnerResourceTest extends DatabaseTestFixture {
    private static final String OWNER_NAME = "Jar Jar Binks";

    @Inject private OwnerCurator ownerCurator;
    @Inject private ProductCurator productCurator;
    @Inject private PoolCurator poolCurator;
    @Inject private ConsumerCurator consumerCurator;
    @Inject private ConsumerTypeCurator consumerTypeCurator;
    @Inject private EntitlementCurator entitlementCurator;
    @Inject private EventCurator eventCurator;
    @Inject private RoleCurator roleCurator;
    @Inject private CandlepinPoolManager poolManager;
    @Inject private ServiceLevelValidator serviceLevelValidator;
    @Inject private I18n i18n;
    @Inject private OwnerResource ownerResource;
    @Inject private EventFactory eventFactory;
    @Inject private Configuration config;
    @Inject private ImportRecordCurator importRecordCurator;
    @Inject private ContentOverrideValidator contentOverrideValidator;

    private Owner owner;
    private List<Owner> owners;
    private Product product;

    @SuppressWarnings("checkstyle:visibilitymodifier")
    @Rule
    public ExpectedException ex = ExpectedException.none();

    @Before
    public void setUp() {
        owner = ownerCurator.create(new Owner(OWNER_NAME));
        owners = new ArrayList<Owner>();
        owners.add(owner);
        product = TestUtil.createProduct(owner);
        productCurator.create(product);
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
        Product prod = TestUtil.createProduct(owner);
        productCurator.create(prod);

        List<Subscription> subscriptions = new LinkedList<Subscription>();
        ImportSubscriptionServiceAdapter subAdapter
            = new ImportSubscriptionServiceAdapter(subscriptions);

        Subscription sub = new Subscription(owner, prod,
            new HashSet<Product>(), 2000L, TestUtil.createDate(2010, 2, 9),
            TestUtil.createDate(3000, 2, 9), TestUtil.createDate(2010, 2, 12));
        sub.setId(Util.generateDbUUID());
        subscriptions.add(sub);

        // Trigger the refresh:
        poolManager.getRefresher(subAdapter).add(owner).run();
        List<Pool> pools = poolCurator.listByOwnerAndProduct(owner, prod.getId());
        assertEquals(1, pools.size());
        Pool newPool = pools.get(0);

        assertEquals(sub.getId(), newPool.getSubscriptionId());
        assertEquals(sub.getQuantity(), newPool.getQuantity());
        assertEquals(sub.getStartDate(), newPool.getStartDate());
        assertEquals(sub.getEndDate(), newPool.getEndDate());
    }

    @Test
    public void testRefreshPoolsWithChangedSubscriptions() {
        Product prod = TestUtil.createProduct(owner);
        productCurator.create(prod);
        Pool pool = createPool(owner, prod, 1000L,
            TestUtil.createDate(2009, 11, 30),
            TestUtil.createDate(2015, 11, 30));
        Owner owner = pool.getOwner();

        List<Subscription> subscriptions = new LinkedList<Subscription>();
        ImportSubscriptionServiceAdapter subAdapter
            = new ImportSubscriptionServiceAdapter(subscriptions);

        Subscription sub = new Subscription(owner, prod,
            new HashSet<Product>(), 2000L, TestUtil.createDate(2010, 2, 9),
            TestUtil.createDate(3000, 2, 9), TestUtil.createDate(2010, 2, 12));
        sub.setId(Util.generateDbUUID());
        subscriptions.add(sub);

        assertTrue(pool.getQuantity() < sub.getQuantity());
        assertTrue(pool.getStartDate() != sub.getStartDate());
        assertTrue(pool.getEndDate() != sub.getEndDate());

        pool.getSourceSubscription().setSubscriptionId(sub.getId());
        poolCurator.merge(pool);

        poolManager.getRefresher(subAdapter).add(owner).run();

        pool = poolCurator.find(pool.getId());
        assertEquals(sub.getId(), pool.getSubscriptionId());
        assertEquals(sub.getQuantity(), pool.getQuantity());
        assertEquals(sub.getStartDate(), pool.getStartDate());
        assertEquals(sub.getEndDate(), pool.getEndDate());
    }

    @Test
    public void testRefreshPoolsWithRemovedSubscriptions() {
        Product prod = TestUtil.createProduct(owner);
        productCurator.create(prod);

        List<Subscription> subscriptions = new LinkedList<Subscription>();
        ImportSubscriptionServiceAdapter subAdapter
            = new ImportSubscriptionServiceAdapter(subscriptions);

        Subscription sub = new Subscription(owner, prod,
            new HashSet<Product>(), 2000L, TestUtil.createDate(2010, 2, 9),
            TestUtil.createDate(3000, 2, 9), TestUtil.createDate(2010, 2, 12));
        sub.setId(Util.generateDbUUID());
        subscriptions.add(sub);

        // Trigger the refresh:
        poolManager.getRefresher(subAdapter).add(owner).run();

        List<Pool> pools = poolCurator.listByOwnerAndProduct(owner,
            prod.getId());
        assertEquals(1, pools.size());
        Pool newPool = pools.get(0);
        String poolId = newPool.getId();

        // Now delete the subscription:
        subscriptions.remove(sub);

        // Trigger the refresh:
        poolManager.getRefresher(subAdapter).add(owner).run();
        assertNull("Pool not having subscription should have been deleted",
            poolCurator.find(poolId));
    }

    @Test
    public void testRefreshMultiplePools() {
        Product prod = TestUtil.createProduct(owner);
        productCurator.create(prod);
        Product prod2 = TestUtil.createProduct(owner);
        productCurator.create(prod2);

        List<Subscription> subscriptions = new LinkedList<Subscription>();
        ImportSubscriptionServiceAdapter subAdapter
            = new ImportSubscriptionServiceAdapter(subscriptions);

        Subscription sub = new Subscription(owner, prod,
            new HashSet<Product>(), 2000L, TestUtil.createDate(2010, 2, 9),
            TestUtil.createDate(3000, 2, 9), TestUtil.createDate(2010, 2, 12));
        sub.setId(Util.generateDbUUID());
        subscriptions.add(sub);

        Subscription sub2 = new Subscription(owner, prod2,
            new HashSet<Product>(), 800L, TestUtil.createDate(2010, 2, 9),
            TestUtil.createDate(3000, 2, 9), TestUtil.createDate(2010, 2, 12));
        sub2.setId(Util.generateDbUUID());
        subscriptions.add(sub2);

        // Trigger the refresh:
        poolManager.getRefresher(subAdapter).add(owner).run();

        List<Pool> pools = poolCurator.listByOwner(owner);
        assertEquals(2, pools.size());
    }

    // test covers scenario from bug 1012386
    @Test
    public void testRefreshPoolsWithRemovedMasterPool() {
        Product prod = TestUtil.createProduct(owner);
        prod.setAttribute("virt_limit", "4");
        productCurator.create(prod);
        config.setProperty(ConfigProperties.STANDALONE, "false");

        List<Subscription> subscriptions = new LinkedList<Subscription>();
        ImportSubscriptionServiceAdapter subAdapter
            = new ImportSubscriptionServiceAdapter(subscriptions);

        Subscription sub = new Subscription(owner, prod,
            new HashSet<Product>(), 2000L, TestUtil.createDate(2010, 2, 9),
            TestUtil.createDate(3000, 2, 9), TestUtil.createDate(2010, 2, 12));
        sub.setId(Util.generateDbUUID());
        subscriptions.add(sub);

        // Trigger the refresh:
        poolManager.getRefresher(subAdapter).add(owner).run();

        List<Pool> pools = poolCurator.lookupBySubscriptionId(sub.getId());
        assertEquals(2, pools.size());
        String bonusId =  "";
        String masterId = "";

        for (Pool p : pools) {
            if (p.getSourceSubscription().getSubscriptionSubKey().equals("master")) {
                poolCurator.delete(p);
                masterId = p.getId();
            }
            else {
                bonusId = p.getId();
            }
        }

        // Trigger the refresh:
        poolManager.getRefresher(subAdapter).add(owner).run();

        assertNull("Original Master Pool should be gone",
            poolCurator.find(masterId));
        assertNotNull("Bonus Pool should be the same",
            poolCurator.find(bonusId));
        // master pool should have been recreated
        pools = poolCurator.lookupBySubscriptionId(sub.getId());
        assertEquals(2, pools.size());
        boolean newMaster = false;
        for (Pool p : pools) {
            if (p.getSourceSubscription().getSubscriptionSubKey().equals("master")) {
                newMaster = true;
            }
        }
        assertTrue(newMaster);
    }

    // test covers a corollary scenario from bug 1012386
    @Test
    public void testRefreshPoolsWithRemovedBonusPool() {
        Product prod = TestUtil.createProduct(owner);
        prod.setAttribute("virt_limit", "4");
        productCurator.create(prod);
        config.setProperty(ConfigProperties.STANDALONE, "false");

        List<Subscription> subscriptions = new LinkedList<Subscription>();
        ImportSubscriptionServiceAdapter subAdapter
            = new ImportSubscriptionServiceAdapter(subscriptions);

        Subscription sub = new Subscription(owner, prod,
            new HashSet<Product>(), 2000L, TestUtil.createDate(2010, 2, 9),
            TestUtil.createDate(3000, 2, 9), TestUtil.createDate(2010, 2, 12));
        sub.setId(Util.generateDbUUID());
        subscriptions.add(sub);

        // Trigger the refresh:
        poolManager.getRefresher(subAdapter).add(owner).run();

        List<Pool> pools = poolCurator.lookupBySubscriptionId(sub.getId());
        assertEquals(2, pools.size());
        String bonusId =  "";
        String masterId = "";

        for (Pool p : pools) {
            if (p.getSourceSubscription().getSubscriptionSubKey().equals("derived")) {
                poolCurator.delete(p);
                bonusId = p.getId();
            }
            else {
                masterId = p.getId();
            }
        }

        // Trigger the refresh:
        poolManager.getRefresher(subAdapter).add(owner).run();

        assertNull("Original bonus pool should be gone",
            poolCurator.find(bonusId));
        assertNotNull("Master pool should be the same",
            poolCurator.find(masterId));
        // master pool should have been recreated
        pools = poolCurator.lookupBySubscriptionId(sub.getId());
        assertEquals(2, pools.size());
        boolean newBonus = false;
        for (Pool p : pools) {
            if (p.getSourceSubscription().getSubscriptionSubKey().equals("derived")) {
                newBonus = true;
            }
        }
        assertTrue(newBonus);
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

        ownerResource.listPools(owner.getKey(), null, null, null, false, null,
            null, new ArrayList<KeyValueParameter>(), principal, null);
    }

    @Test
    public void testUnmappedGuestConsumerCanListPoolsForFuture() {
        Consumer c = TestUtil.createConsumer(owner);
        consumerTypeCurator.create(c.getType());
        c.setFact("virt.is_guest", "true");
        c.setFact("virt.uuid", "system_uuid");
        consumerCurator.create(c);
        Principal principal = setupPrincipal(new ConsumerPrincipal(c));

        securityInterceptor.enable();

        Date now = new Date();
        Product p = TestUtil.createProduct(owner);
        productCurator.create(p);
        Pool pool1 = TestUtil.createPool(owner, p);
        pool1.setAttribute("virt_only", "true");
        pool1.setAttribute("pool_derived", "true");
        pool1.setAttribute("physical_only", "false");
        pool1.setAttribute("unmapped_guests_only", "true");
        pool1.setStartDate(now);
        pool1.setEndDate(new Date(now.getTime() + 1000L * 60 * 60 * 24 * 365));
        Pool pool2 = TestUtil.createPool(owner, p);
        pool2.setAttribute("virt_only", "true");
        pool2.setAttribute("pool_derived", "true");
        pool2.setAttribute("physical_only", "false");
        pool2.setAttribute("unmapped_guests_only", "true");
        pool2.setStartDate(new Date(now.getTime() + 2 * 1000L * 60 * 60 * 24 * 365));
        pool2.setEndDate(new Date(now.getTime() + 3 * 1000L * 60 * 60 * 24 * 365));
        poolCurator.create(pool1);
        poolCurator.create(pool2);

        List<Pool> nowList = ownerResource.listPools(owner.getKey(), c.getUuid(), null, null, false, null,
            null, new ArrayList<KeyValueParameter>(), principal, null);
        assertEquals(1, nowList.size());
        assert (nowList.get(0).getId().equals(pool1.getId()));

        Date activeOn = new Date(pool2.getStartDate().getTime() + 1000L * 60 * 60 * 24);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        List<Pool> futureList = ownerResource.listPools(owner.getKey(), c.getUuid(), null, null, false,
                sdf.format(activeOn), null, new ArrayList<KeyValueParameter>(), principal, null);
        assertEquals(1, futureList.size());
        assert (futureList.get(0).getId().equals(pool2.getId()));
    }

    @Test
    public void testOwnerAdminCanGetPools() {
        Principal principal = setupPrincipal(owner, Access.ALL);

        Product p = TestUtil.createProduct(owner);
        productCurator.create(p);
        Pool pool1 = TestUtil.createPool(owner, p);
        Pool pool2 = TestUtil.createPool(owner, p);
        poolCurator.create(pool1);
        poolCurator.create(pool2);

        List<Pool> pools = ownerResource.listPools(owner.getKey(),
            null, null, null, true, null, null,
            new ArrayList<KeyValueParameter>(), principal, null);
        assertEquals(2, pools.size());
    }

    @Test
    public void testCanFilterPoolsByAttribute() throws Exception {
        Principal principal = setupPrincipal(owner, Access.ALL);

        Product p = TestUtil.createProduct(owner);
        productCurator.create(p);
        Pool pool1 = TestUtil.createPool(owner, p);
        pool1.setAttribute("virt_only", "true");
        poolCurator.create(pool1);

        Product p2 = TestUtil.createProduct(owner);
        p2.setAttribute("cores", "12");
        productCurator.create(p2);
        Pool pool2 = TestUtil.createPool(owner, p2);
        poolCurator.create(pool2);

        List<KeyValueParameter> params = new ArrayList<KeyValueParameter>();
        params.add(createKeyValueParam("cores", "12"));

        List<Pool> pools = ownerResource.listPools(owner.getKey(), null,
            null, null, true, null, null, params, principal, null);
        assertEquals(1, pools.size());
        assertEquals(pool2, pools.get(0));

        params.clear();
        params.add(createKeyValueParam("virt_only", "true"));

        pools = ownerResource.listPools(owner.getKey(), null, null,
            null, true, null, null, params, principal, null);
        assertEquals(1, pools.size());
        assertEquals(pool1, pools.get(0));
    }


    @Test
    public void testCanFilterOutDevPoolsByAttribute() throws Exception {
        Principal principal = setupPrincipal(owner, Access.ALL);

        Product p = TestUtil.createProduct(owner);
        productCurator.create(p);
        Pool pool1 = TestUtil.createPool(owner, p);
        pool1.setAttribute(Pool.DEVELOPMENT_POOL_ATTRIBUTE, "true");
        poolCurator.create(pool1);

        Product p2 = TestUtil.createProduct(owner);
        productCurator.create(p2);
        Pool pool2 = TestUtil.createPool(owner, p2);
        poolCurator.create(pool2);

        List<KeyValueParameter> params = new ArrayList<KeyValueParameter>();
        List<Pool> pools = ownerResource.listPools(owner.getKey(), null,
            null, null, true, null, null, params, principal, null);
        assertEquals(2, pools.size());

        params = new ArrayList<KeyValueParameter>();
        params.add(createKeyValueParam(Pool.DEVELOPMENT_POOL_ATTRIBUTE, "!true"));
        pools = ownerResource.listPools(owner.getKey(), null,
            null, null, true, null, null, params, principal, null);
        assertEquals(1, pools.size());
        assertEquals(pool2, pools.get(0));
    }


    @Test(expected = NotFoundException.class)
    public void ownerAdminCannotAccessAnotherOwnersPools() {
        Owner evilOwner = new Owner("evilowner");
        ownerCurator.create(evilOwner);
        Principal principal = setupPrincipal(evilOwner, Access.ALL);

        Product p = TestUtil.createProduct(owner);
        productCurator.create(p);
        Pool pool1 = TestUtil.createPool(owner, p);
        Pool pool2 = TestUtil.createPool(owner, p);
        poolCurator.create(pool1);
        poolCurator.create(pool2);

        securityInterceptor.enable();

        // Filtering should just cause this to return no results:
        ownerResource.listPools(owner.getKey(), null, null, null, true, null,
            null, new ArrayList<KeyValueParameter>(), principal, null);
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
            new ArrayList<String>(), null, null, null, null, null, null);
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
            owner.getKey(), "username", types, uuids, null, null, null, null,
            null, new PageRequest());

        assertEquals(0, results.size());
    }


    @Test
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
                uuids, null, null, null, null, null, null).size());
    }

    /**
     * I'm generally not a fan of testing this way, but in this case
     * I want to check that the exception message that is returned
     * correctly concats the invalid type name.
     */
    @Test
    public void failWhenListingByBadConsumerType() {
        ex.expect(BadRequestException.class);
        ex.expectMessage(IsEqual.<String>equalTo("No such unit type(s): unknown"));

        Set<String> types = new HashSet<String>();
        types.add("unknown");
        ownerResource.listConsumers(owner.getKey(), null, types,
            new ArrayList<String>(), null, null, null, null, null, null);
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
            null, uuids, null, null, null, null, null, null);
        assertEquals(2, results.size());
    }

    @Test
    public void consumerListPoolsGetCalculatedAttributes() {
        Product p = TestUtil.createProduct(owner);
        productCurator.create(p);
        Pool pool1 = TestUtil.createPool(owner, p);
        poolCurator.create(pool1);

        Consumer c = TestUtil.createConsumer(owner);
        consumerTypeCurator.create(c.getType());
        consumerCurator.create(c);

        Principal principal = setupPrincipal(new ConsumerPrincipal(c));
        securityInterceptor.enable();

        List<Pool> pools = ownerResource.listPools(owner.getKey(), c.getUuid(), null,
            p.getId(), true, null, null, new ArrayList<KeyValueParameter>(), principal, null);
        assertEquals(1, pools.size());
        Pool returnedPool = pools.get(0);
        assertNotNull(returnedPool.getCalculatedAttributes());
    }


    @Test(expected = NotFoundException.class)
    public void testConsumerListPoolsCannotAccessOtherConsumer() {
        Product p = TestUtil.createProduct(owner);
        productCurator.create(p);
        Pool pool1 = TestUtil.createPool(owner, p);
        poolCurator.create(pool1);

        Consumer c = TestUtil.createConsumer(owner);
        consumerTypeCurator.create(c.getType());
        consumerCurator.create(c);

        securityInterceptor.enable();

        Owner owner2 = createOwner();
        ownerCurator.create(owner2);

        ownerResource.listPools(owner.getKey(), c.getUuid(), null,
            p.getUuid(), true, null, null,
            new ArrayList<KeyValueParameter>(), setupPrincipal(owner2, Access.NONE), null);
    }

    @Test
    public void testEntitlementsRevocationWithFifoOrder() throws Exception {
        Pool pool = doTestEntitlementsRevocationCommon(7, 4, 5, true);
        assertEquals(4L, this.poolCurator.find(pool.getId()).getConsumed().longValue());
    }

    @Test
    public void testEntitlementsRevocationWithLifoOrder() throws Exception {
        Pool pool = doTestEntitlementsRevocationCommon(7, 4, 5, false);
        assertEquals(5L, this.poolCurator.find(pool.getId()).getConsumed().longValue());
    }

    @Test
    public void testEntitlementsRevocationWithNoOverflow() throws Exception {
        Pool pool = doTestEntitlementsRevocationCommon(10, 4, 5, false);
        assertEquals(9L, this.poolCurator.find(pool.getId()).getConsumed().longValue());
    }

    @Test
    public void testActivationKeyCreateRead() {
        ActivationKey key = new ActivationKey();
        key.setName("dd");
        key.setReleaseVer(new Release("release1"));
        key = ownerResource.createActivationKey(owner.getKey(), key);
        assertNotNull(key.getId());
        assertEquals(key.getOwner().getId(), owner.getId());
        assertEquals(key.getReleaseVer().getReleaseVer(), "release1");
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

    @Test(expected = BadRequestException.class)
    public void testActivationKeyTooLongRelease() {
        ActivationKey key = new ActivationKey();
        Owner owner = createOwner();
        key.setOwner(owner);
        key.setReleaseVer(new Release(TestUtil.getStringOfSize(256)));
        key = ownerResource.createActivationKey(owner.getKey(), key);
    }

    private Pool doTestEntitlementsRevocationCommon(long subQ, int e1, int e2, boolean fifo)
        throws ParseException {

        Product prod = TestUtil.createProduct(owner);
        productCurator.create(prod);

        List<Subscription> subscriptions = new LinkedList<Subscription>();
        ImportSubscriptionServiceAdapter subAdapter
            = new ImportSubscriptionServiceAdapter(subscriptions);

        Subscription sub = new Subscription(owner, prod,
            new HashSet<Product>(), 1000L, TestUtil.createDate(2009, 11, 30),
            TestUtil.createDate(Calendar.getInstance().get(Calendar.YEAR) + 10, 10, 30),
            TestUtil.createDate(2015, 11, 30));
        sub.setId(Util.generateDbUUID());
        subscriptions.add(sub);

        List<Pool> pools = poolManager.createAndEnrichPools(sub);
        assertTrue(pools.size() > 0);
        Pool pool = pools.get(0);

        sub.setQuantity(subQ);

        Owner retrieved = pool.getOwner();
        Consumer consumer = createConsumer(retrieved);
        Consumer consumer1 = createConsumer(retrieved);

        pool = this.poolCurator.find(pool.getId());
        createEntitlementWithQ(pool, retrieved, consumer, e1, "01/02/2010");
        createEntitlementWithQ(pool, retrieved, consumer1, e2, "01/01/2010");
        assertEquals(pool.getConsumed(), Long.valueOf(e1 + e2));
        this.config.setProperty(
            ConfigProperties.REVOKE_ENTITLEMENT_IN_FIFO_ORDER, fifo ? "true" :
                "false");

        poolManager.getRefresher(subAdapter).add(retrieved).run();
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
        ownerResource.undoImports(owner1.getKey(), new UserPrincipal("JarjarBinks", null, true));
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

        OwnerResource or = new OwnerResource(
            oc,
            akc,
            null,
            null,
            i18n,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            contentOverrideValidator,
            serviceLevelValidator,
            null,
            null,
            null,
            null,
            null
        );
        or.createActivationKey("testOwner", ak);
    }

    @Test
    public void testUpdateOwner() {
        Owner owner = new Owner("Test Owner", "test");
        ownerCurator.create(owner);

        Product prod1 = TestUtil.createProduct(owner);
        prod1.setAttribute("support_level", "premium");
        productCurator.create(prod1);
        Product prod2 = TestUtil.createProduct(owner);
        prod2.setAttribute("support_level", "standard");
        productCurator.create(prod2);

        List<Subscription> subscriptions = new LinkedList<Subscription>();
        ImportSubscriptionServiceAdapter subAdapter
            = new ImportSubscriptionServiceAdapter(subscriptions);

        Subscription sub1 = new Subscription(owner, prod1,
            new HashSet<Product>(), 2000L, TestUtil.createDate(2010, 2, 9),
            TestUtil.createDate(3000, 2, 9), TestUtil.createDate(2010, 2, 12));
        sub1.setId(Util.generateDbUUID());
        subscriptions.add(sub1);

        Subscription sub2 = new Subscription(owner, prod2,
            new HashSet<Product>(), 2000L, TestUtil.createDate(2010, 2, 9),
            TestUtil.createDate(3000, 2, 9), TestUtil.createDate(2010, 2, 12));
        sub2.setId(Util.generateDbUUID());
        subscriptions.add(sub2);

        // Trigger the refresh:
        poolManager.getRefresher(subAdapter).add(owner).run();

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

        MultipartInput input = mock(MultipartInput.class);

        Map<String, Object> result = new HashMap<String, Object>();
        List<Subscription> subscriptions = new ArrayList<Subscription>();
        Subscription subscription = new Subscription();
        //expires tomorrow
        subscription.setEndDate(new Date((new Date()).getTime() + (1000 * 60 * 60 * 24)));
        subscriptions.add(subscription);
        result.put("subscriptions", subscriptions);
        EventSink es = mock(EventSink.class);
        OwnerResource thisOwnerResource = setUpSuccesfullImport(input, result, es);
        thisOwnerResource.importManifest(owner.getKey(), new String [] {}, input);
        List<ImportRecord> records = importRecordCurator.findRecords(owner);
        ImportRecord ir = records.get(0);
        assertEquals("test_file.zip", ir.getFileName());
        assertEquals(owner, ir.getOwner());
        assertEquals(ImportRecord.Status.SUCCESS, ir.getStatus());
    }

    @Test
    public void importManifestWithNoActiveSubscriptions()
        throws IOException, ImporterException {

        MultipartInput input = mock(MultipartInput.class);
        List<Subscription> subscriptions = new ArrayList<Subscription>();
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("subscriptions", subscriptions);
        EventSink es = mock(EventSink.class);
        OwnerResource thisOwnerResource = setUpSuccesfullImport(input, result, es);
        ImportRecord record = thisOwnerResource.importManifest(owner.getKey(), new String[0], input);
        assertEquals(ImportRecord.Status.SUCCESS_WITH_WARNING, record.getStatus());
        assertEquals(owner.getKey() + " file imported successfully." +
                "No active subscriptions found in the file.", record.getStatusMessage());
    }

    @Test
    public void importManifestWithSomeActiveSubscriptions()
        throws IOException, ImporterException {

        MultipartInput input = mock(MultipartInput.class);
        Map<String, Object> result = new HashMap<String, Object>();
        List<Subscription> subscriptions = new ArrayList<Subscription>();
        Subscription subscription = new Subscription();
        //expires tomorrow
        subscription.setEndDate(new Date((new Date()).getTime() + (1000 * 60 * 60 * 24)));
        subscriptions.add(subscription);
        subscription = new Subscription();
        //expires yesterday
        subscription.setEndDate(new Date((new Date()).getTime() - (1000 * 60 * 60 * 24)));
        subscriptions.add(subscription);
        result.put("subscriptions", subscriptions);

        EventSink es = mock(EventSink.class);
        OwnerResource thisOwnerResource = setUpSuccesfullImport(input, result, es);
        ImportRecord record = thisOwnerResource.importManifest(owner.getKey(), new String[0], input);
        assertEquals(ImportRecord.Status.SUCCESS_WITH_WARNING, record.getStatus());
        assertEquals(owner.getKey() + " file imported successfully." +
                "One or more inactive subscriptions found in the file.", record.getStatusMessage());
        verify(es).emitSubscriptionExpired(subscription);
    }

    private OwnerResource setUpSuccesfullImport(MultipartInput input, Map<String, Object> result,
            EventSink es)
        throws IOException, ImporterException {

        Importer importer = mock(Importer.class);
        OwnerResource thisOwnerResource = new OwnerResource(ownerCurator, null,
            null, null, i18n, es, null, null, null, importer, null, null,
            null, importRecordCurator, null, null, null, null,
            null, null, null, contentOverrideValidator,
            serviceLevelValidator, null, null, null, null, null);

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
            .thenReturn(result);
        return thisOwnerResource;

    }

    @Test
    public void testImportRecordFailureWithFilename()
        throws IOException, ImporterException {
        Importer importer = mock(Importer.class);
        EventSink es = mock(EventSink.class);
        OwnerResource thisOwnerResource = new OwnerResource(ownerCurator, null,
            null, null, i18n, es, null, null, null, importer, null, null,
            null, importRecordCurator, null, null, null, null,
            null, null, null, contentOverrideValidator,
            serviceLevelValidator, null, null, null, null, null);

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
            null, null, i18n, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null,
            contentOverrideValidator, serviceLevelValidator, null, null, null, null, null);

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

    @Test
    public void createSubscription() {
        Product p = TestUtil.createProduct(owner);
        productCurator.create(p);
        Subscription s = TestUtil.createSubscription(owner, p);
        s.setId("MADETHISUP");
        assertEquals(0, poolCurator.listByOwner(owner).size());
        ownerResource.createSubscription(owner.getKey(), s);
        assertEquals(1, poolCurator.listByOwner(owner).size());
    }

    @Test
    public void createPool() {
        Product prod = TestUtil.createProduct(owner);
        productCurator.create(prod);
        Pool pool = TestUtil.createPool(owner, prod);
        assertEquals(0, poolCurator.listByOwner(owner).size());
        ownerResource.createPool(owner.getKey(), pool);
        assertEquals(1, poolCurator.listByOwner(owner).size());
        assertNotNull(pool.getId());

    }

    @Test
    public void updatePool() {
        Product prod = TestUtil.createProduct(owner);
        productCurator.create(prod);
        Pool pool = TestUtil.createPool(owner, prod);
        ownerResource.createPool(owner.getKey(), pool);
        List<Pool> createdPools = poolCurator.listByOwner(owner);
        assertEquals(1, createdPools.size());
        assertEquals(pool.getQuantity(), createdPools.get(0).getQuantity());

        pool.setQuantity(10L);
        ownerResource.createPool(owner.getKey(), pool);
        List<Pool> updatedPools = poolCurator.listByOwner(owner);
        assertEquals(1, createdPools.size());
        assertEquals(10L, createdPools.get(0).getQuantity().longValue());
    }

    @Test
    public void createBonusPool() {
        Product prod = TestUtil.createProduct(owner);
        prod.setAttribute("virt_limit", "2");
        productCurator.create(prod);
        Pool pool = TestUtil.createPool(owner, prod);
        assertEquals(0, poolCurator.listByOwner(owner).size());
        ownerResource.createPool(owner.getKey(), pool);
        List<Pool> pools = poolCurator.listByOwner(owner);
        assertEquals(2, pools.size());
        assertTrue(pools.get(0).getSubscriptionSubKey().startsWith("master") ||
                pools.get(1).getSubscriptionSubKey().startsWith("master"));
        assertTrue(pools.get(0).getSubscriptionSubKey().equals("derived") ||
                pools.get(1).getSubscriptionSubKey().equals("derived"));
    }

    @Test
    public void createBonusPoolForUpdate() {
        Product prod = TestUtil.createProduct(owner);
        prod.setAttribute("virt_limit", "3");
        productCurator.create(prod);
        Pool pool = TestUtil.createPool(owner, prod);
        pool.setSubscriptionSubKey("master");
        ownerResource.createPool(owner.getKey(), pool);
        pool.setQuantity(100L);
        ownerResource.updatePool(owner.getKey(), pool);
        List<Pool> pools = poolCurator.listByOwner(owner);
        assertEquals(2, pools.size());
        assertTrue(pools.get(0).getSubscriptionSubKey().startsWith("master") ||
                pools.get(1).getSubscriptionSubKey().startsWith("master"));
        assertTrue(pools.get(0).getSubscriptionSubKey().equals("derived") ||
                pools.get(1).getSubscriptionSubKey().equals("derived"));
        assertEquals(100L, pools.get(0).getQuantity().longValue());
        assertEquals(300L, pools.get(1).getQuantity().longValue());
    }

    @Test
    public void removePoolsForExpiredUpdate() {
        Product prod = TestUtil.createProduct(owner);
        prod.setAttribute("virt_limit", "3");
        productCurator.create(prod);
        Pool pool = TestUtil.createPool(owner, prod);
        pool.setSubscriptionSubKey("master");
        ownerResource.createPool(owner.getKey(), pool);
        List<Pool> pools = poolCurator.listByOwner(owner);
        assertEquals(2, pools.size());
        pool.setStartDate(new Date(System.currentTimeMillis() - 5 * 24 * 60 * 60 * 1000));
        pool.setEndDate(new Date(System.currentTimeMillis() - 3 * 24 * 60 * 60 * 1000));
        ownerResource.updatePool(owner.getKey(), pool);
        pools = poolCurator.listByOwner(owner);
        assertEquals(0, pools.size());
    }

    @Test(expected = BadRequestException.class)
    public void cantUpdateBonusPool() {
        Product prod = TestUtil.createProduct(owner);
        prod.setAttribute("virt_limit", "3");
        productCurator.create(prod);
        Pool pool = TestUtil.createPool(owner, prod);
        pool.setSubscriptionSubKey("master");
        ownerResource.createPool(owner.getKey(), pool);
        List<Pool> pools = poolCurator.listByOwner(owner);

        Pool bonusPool = null;
        for (Pool p : pools) {
            if (p.getSubscriptionSubKey().contentEquals("derived")) {
                bonusPool = p;
            }
        }
        assertNotNull(bonusPool);
        ownerResource.updatePool(owner.getKey(), bonusPool);
    }

    @Test
    public void enrichPool() {
        Product prod = TestUtil.createProduct(owner);
        prod.setAttribute("virt_only", "true");
        prod.setMultiplier(2L);
        productCurator.create(prod);
        Pool pool = TestUtil.createPool(owner, prod);
        pool.setQuantity(100L);
        assertEquals(0, poolCurator.listByOwner(owner).size());
        ownerResource.createPool(owner.getKey(), pool);
        List<Pool> pools = poolCurator.listByOwner(owner);
        assertEquals(1, pools.size());
        assertTrue(Boolean.parseBoolean(pools.get(0).getAttributeValue("virt_only")));
        assertEquals(200L, pools.get(0).getQuantity().intValue());
    }

    @Test
    public void getAllEntitlementsForOwner() {
        PageRequest req = new PageRequest();
        req.setPage(1);
        req.setPerPage(10);

        Owner owner = TestUtil.createOwner();
        Consumer consumer = TestUtil.createConsumer(owner);
        Pool pool = TestUtil.createPool(owner, TestUtil.createProduct(owner));

        Entitlement e = TestUtil.createEntitlement(owner, consumer, pool, null);
        e.setId("getAllEntitlementsForOwner");
        List<Entitlement> entitlements = new ArrayList<Entitlement>();
        entitlements.add(e);
        Page<List<Entitlement>> page = new Page<List<Entitlement>>();
        page.setPageData(entitlements);

        OwnerCurator oc = mock(OwnerCurator.class);
        EntitlementCurator ec = mock(EntitlementCurator.class);
        OwnerResource ownerres = new OwnerResource(oc, null,
                null, null, i18n, null, null, null, null, null, null, null,
                null, null, null, null, null, ec, null, null, null,
                null, null, null, null, null, null, null);

        when(oc.lookupByKey(owner.getKey())).thenReturn(owner);
        when(
                ec.listByOwner(isA(Owner.class), anyString(), isA(EntitlementFilterBuilder.class),
                        isA(PageRequest.class))).thenReturn(page);

        List<Entitlement> result = ownerres.ownerEntitlements(owner.getKey(), null, null, null, req);

        assertEquals(1, result.size());
        assertEquals("getAllEntitlementsForOwner", result.get(0).getId());
    }

    @Test(expected = NotFoundException.class)
    public void getEntitlementsForNonExistantOwner() {
        PageRequest req = new PageRequest();
        req.setPage(1);
        req.setPerPage(10);

        OwnerCurator oc = mock(OwnerCurator.class);
        OwnerResource ownerres = new OwnerResource(oc, null,
                null, null, i18n, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);

        ownerres.ownerEntitlements("Taylor Swift", null, null, null, req);
    }

    @Test
    public void testCreateUeberCertificateFromScratch() {
        Principal principal = setupPrincipal(owner, Access.ALL);
        Owner owner = TestUtil.createOwner();
        Consumer consumer = TestUtil.createConsumer(owner);
        EntitlementCertificate entCert = mock(EntitlementCertificate.class);


        OwnerCurator oc = mock(OwnerCurator.class);
        ConsumerCurator cc = mock(ConsumerCurator.class);
        EntitlementCurator ec = mock(EntitlementCurator.class);
        CandlepinPoolManager cpm = mock(CandlepinPoolManager.class);
        EntitlementCertificateCurator ecc = mock(EntitlementCertificateCurator.class);
        UeberCertificateGenerator ucg = mock(UeberCertificateGenerator.class);


        OwnerResource resource = new OwnerResource(
            oc, null, cc, null, i18n, null, null, null, null, null, cpm, null, null, null, null,
            null, ecc, ec, ucg, null, null, null, null, null, null, null, null, null
        );

        try {
            when(oc.lookupByKey(eq("admin"))).thenReturn(owner);
            when(cc.findByName(eq(owner), eq(Consumer.UEBER_CERT_CONSUMER))).thenReturn(null);
            when(ucg.generate(eq(owner), eq(principal))).thenReturn(entCert);
        }
        catch (EntitlementRefusedException ere) {
            // ...
        }

        EntitlementCertificate result = resource.createUeberCertificate(principal, "admin");

        assertEquals(entCert, result);
    }

    @Test
    public void testCreateUeberCertificateRegenerate() {
        Principal principal = setupPrincipal(owner, Access.ALL);
        Owner owner = TestUtil.createOwner();
        Consumer consumer = TestUtil.createConsumer(owner);
        Entitlement ent = mock(Entitlement.class);
        List<Entitlement> entList = Arrays.asList(ent);
        EntitlementCertificate entCert = mock(EntitlementCertificate.class);
        List<EntitlementCertificate> ecList = Arrays.asList(entCert);

        OwnerCurator oc = mock(OwnerCurator.class);
        ConsumerCurator cc = mock(ConsumerCurator.class);
        EntitlementCurator ec = mock(EntitlementCurator.class);
        CandlepinPoolManager cpm = mock(CandlepinPoolManager.class);
        EntitlementCertificateCurator ecc = mock(EntitlementCertificateCurator.class);
        UeberCertificateGenerator ucg = mock(UeberCertificateGenerator.class);

        OwnerResource resource = new OwnerResource(
            oc, null, cc, null, i18n, null, null, null, null, null, cpm, null, null, null, null,
            null, ecc, ec, ucg, null, null, null, null, null, null, null, null, null
        );

        when(oc.lookupByKey(eq("admin"))).thenReturn(owner);
        when(cc.findByName(eq(owner), eq(Consumer.UEBER_CERT_CONSUMER))).thenReturn(consumer);
        when(ec.listByConsumer(eq(consumer))).thenReturn(entList);
        when(ecc.listForConsumer(eq(consumer))).thenReturn(ecList);

        EntitlementCertificate result = resource.createUeberCertificate(principal, "admin");

        assertEquals(entCert, result);
    }

    @Test
    public void testCreateUeberCertificateRegenerateWithNoEntitlement() {
        Principal principal = setupPrincipal(owner, Access.ALL);
        Owner owner = TestUtil.createOwner();
        Consumer consumer = TestUtil.createConsumer(owner);
        Entitlement ent = mock(Entitlement.class);
        List<Entitlement> entList = new LinkedList<Entitlement>();
        EntitlementCertificate entCert = mock(EntitlementCertificate.class);
        List<EntitlementCertificate> ecList = Arrays.asList(entCert);

        OwnerCurator oc = mock(OwnerCurator.class);
        ConsumerCurator cc = mock(ConsumerCurator.class);
        EntitlementCurator ec = mock(EntitlementCurator.class);
        CandlepinPoolManager cpm = mock(CandlepinPoolManager.class);
        EntitlementCertificateCurator ecc = mock(EntitlementCertificateCurator.class);
        UeberCertificateGenerator ucg = mock(UeberCertificateGenerator.class);

        OwnerResource resource = new OwnerResource(
            oc, null, cc, null, i18n, null, null, null, null, null, cpm, null, null, null, null,
            null, ecc, ec, ucg, null, null, null, null, null, null, null, null, null
        );

        try {
            when(oc.lookupByKey(eq("admin"))).thenReturn(owner);
            when(cc.findByName(eq(owner), eq(Consumer.UEBER_CERT_CONSUMER))).thenReturn(consumer);
            when(ec.listByConsumer(eq(consumer))).thenReturn(entList);
            when(ucg.generate(eq(owner), eq(principal))).thenReturn(entCert);
        }
        catch (EntitlementRefusedException ere) {
            // ...
        }

        EntitlementCertificate result = resource.createUeberCertificate(principal, "admin");

        assertEquals(entCert, result);
    }

    @Test(expected = NotFoundException.class)
    public void testCreateUeberCertificateInvalidOwner() {
        Principal principal = setupPrincipal(owner, Access.ALL);

        OwnerCurator oc = mock(OwnerCurator.class);
        ConsumerCurator cc = mock(ConsumerCurator.class);
        EntitlementCurator ec = mock(EntitlementCurator.class);
        CandlepinPoolManager cpm = mock(CandlepinPoolManager.class);
        EntitlementCertificateCurator ecc = mock(EntitlementCertificateCurator.class);
        UeberCertificateGenerator ucg = mock(UeberCertificateGenerator.class);

        OwnerResource resource = new OwnerResource(
            oc, null, cc, null, i18n, null, null, null, null, null, cpm, null, null, null, null,
            null, ecc, ec, ucg, null, null, null, null, null, null, null, null, null
        );

        EntitlementCertificate result = resource.createUeberCertificate(principal, "admin");
    }

}
