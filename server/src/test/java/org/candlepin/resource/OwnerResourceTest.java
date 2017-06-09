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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.candlepin.common.exceptions.ConflictException;
import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.common.exceptions.IseException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.CandlepinPoolManager;
import org.candlepin.controller.ContentManager;
import org.candlepin.controller.ManifestManager;
import org.candlepin.controller.OwnerManager;
import org.candlepin.controller.ProductManager;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificateCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EntitlementFilterBuilder;
import org.candlepin.model.ImportRecord;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.PermissionBlueprint;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.Release;
import org.candlepin.model.Role;
import org.candlepin.model.UeberCertificate;
import org.candlepin.model.UeberCertificateCurator;
import org.candlepin.model.UeberCertificateGenerator;
import org.candlepin.model.UpstreamConsumer;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.model.dto.Subscription;
import org.candlepin.resteasy.parameter.CandlepinParam;
import org.candlepin.resteasy.parameter.CandlepinParameterUnmarshaller;
import org.candlepin.resteasy.parameter.KeyValueParameter;
import org.candlepin.service.OwnerServiceAdapter;
import org.candlepin.service.impl.DefaultOwnerServiceAdapter;
import org.candlepin.service.impl.ImportSubscriptionServiceAdapter;
import org.candlepin.sync.ConflictOverrides;
import org.candlepin.sync.ImporterException;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.ContentOverrideValidator;
import org.candlepin.util.ServiceLevelValidator;
import org.candlepin.util.Util;

import org.hamcrest.core.IsEqual;
import org.hibernate.exception.ConstraintViolationException;
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
import org.mockito.Mockito;
import org.quartz.JobDetail;
import org.xnap.commons.i18n.I18n;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.persistence.PersistenceException;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MultivaluedMap;


/**
 * OwnerResourceTest
 */
public class OwnerResourceTest extends DatabaseTestFixture {
    private static final String OWNER_NAME = "Jar Jar Binks";

    @Inject private CandlepinPoolManager poolManager;
    @Inject private ServiceLevelValidator serviceLevelValidator;
    @Inject private I18n i18n;
    @Inject private OwnerResource ownerResource;
    @Inject private EventFactory eventFactory;
    @Inject private Configuration config;
    @Inject private ContentOverrideValidator contentOverrideValidator;
    @Inject private ProductManager productManager;
    @Inject private ContentManager contentManager;
    @Inject private UeberCertificateGenerator ueberCertGenerator;
    @Inject private UeberCertificateCurator ueberCertCurator;

    private Owner owner;
    private List<Owner> owners;
    private Product product;
    private Set<String> typeLabels;
    private List<String> skus;
    private List<String> subscriptionIds;
    private List<String> contracts;

    @SuppressWarnings("checkstyle:visibilitymodifier")
    @Rule
    public ExpectedException ex = ExpectedException.none();

    @Before
    public void setUp() {
        owner = ownerCurator.create(new Owner(OWNER_NAME));
        owners = new ArrayList<Owner>();
        owners.add(owner);
        product = this.createProduct(owner);
        typeLabels = null;
        skus = null;
        subscriptionIds = null;
        contracts = null;
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
        ownerResource.deleteOwner(owner.getKey(), true, false);
        owner = ownerCurator.find(id);
        assertNull(owner);
    }

    @Test
    public void testRefreshPoolsWithNewSubscriptions() {
        Product prod = this.createProduct(owner);

        List<Subscription> subscriptions = new LinkedList<Subscription>();
        ImportSubscriptionServiceAdapter subAdapter = new ImportSubscriptionServiceAdapter(subscriptions);
        OwnerServiceAdapter ownerAdapter = new DefaultOwnerServiceAdapter(this.ownerCurator, this.i18n);

        Subscription sub = TestUtil.createSubscription(owner, prod, new HashSet<Product>());
        sub.setId(Util.generateDbUUID());
        sub.setQuantity(2000L);
        sub.setStartDate(TestUtil.createDate(2010, 2, 9));
        sub.setEndDate(TestUtil.createDate(3000, 2, 9));
        sub.setModified(TestUtil.createDate(2010, 2, 12));
        subscriptions.add(sub);

        // Trigger the refresh:
        poolManager.getRefresher(subAdapter, ownerAdapter).add(owner).run();
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
        Product prod = this.createProduct(owner);
        Pool pool = createPool(owner, prod, 1000L,
            TestUtil.createDate(2009, 11, 30),
            TestUtil.createDate(2015, 11, 30));

        List<Subscription> subscriptions = new LinkedList<Subscription>();
        ImportSubscriptionServiceAdapter subAdapter = new ImportSubscriptionServiceAdapter(subscriptions);
        OwnerServiceAdapter ownerAdapter = new DefaultOwnerServiceAdapter(this.ownerCurator, this.i18n);

        Subscription sub = TestUtil.createSubscription(owner, prod, new HashSet<Product>());
        sub.setId(Util.generateDbUUID());
        sub.setQuantity(2000L);
        sub.setStartDate(TestUtil.createDate(2010, 2, 9));
        sub.setEndDate(TestUtil.createDate(3000, 2, 9));
        sub.setModified(TestUtil.createDate(2010, 2, 12));
        subscriptions.add(sub);

        assertTrue(pool.getQuantity() < sub.getQuantity());
        assertTrue(pool.getStartDate() != sub.getStartDate());
        assertTrue(pool.getEndDate() != sub.getEndDate());

        pool.getSourceSubscription().setSubscriptionId(sub.getId());
        poolCurator.merge(pool);

        poolManager.getRefresher(subAdapter, ownerAdapter).add(owner).run();

        pool = poolCurator.find(pool.getId());
        assertEquals(sub.getId(), pool.getSubscriptionId());
        assertEquals(sub.getQuantity(), pool.getQuantity());
        assertEquals(sub.getStartDate(), pool.getStartDate());
        assertEquals(sub.getEndDate(), pool.getEndDate());
    }

    @Test
    public void testRefreshPoolsWithRemovedSubscriptions() {
        Product prod = this.createProduct(owner);

        List<Subscription> subscriptions = new LinkedList<Subscription>();
        ImportSubscriptionServiceAdapter subAdapter = new ImportSubscriptionServiceAdapter(subscriptions);
        OwnerServiceAdapter ownerAdapter = new DefaultOwnerServiceAdapter(this.ownerCurator, this.i18n);

        Subscription sub = TestUtil.createSubscription(owner, prod, new HashSet<Product>());
        sub.setId(Util.generateDbUUID());
        sub.setQuantity(2000L);
        sub.setStartDate(TestUtil.createDate(2010, 2, 9));
        sub.setEndDate(TestUtil.createDate(3000, 2, 9));
        sub.setModified(TestUtil.createDate(2010, 2, 12));

        // This line is only present as a result of a (temporary?) fix for BZ 1452694. Once a
        // better fix has been implemented, the upstream pool ID can be removed.
        sub.setUpstreamPoolId("upstream_pool_id");

        subscriptions.add(sub);

        // Trigger the refresh:
        poolManager.getRefresher(subAdapter, ownerAdapter).add(owner).run();

        List<Pool> pools = poolCurator.listByOwnerAndProduct(owner, prod.getId());
        assertEquals(1, pools.size());
        Pool newPool = pools.get(0);
        String poolId = newPool.getId();

        // Now delete the subscription:
        subscriptions.remove(sub);

        // Trigger the refresh:
        poolManager.getRefresher(subAdapter, ownerAdapter).add(owner).run();
        assertNull("Pool not having subscription should have been deleted", poolCurator.find(poolId));
    }

    @Test
    public void testRefreshMultiplePools() {
        Product prod = this.createProduct(owner);
        Product prod2 = this.createProduct(owner);

        List<Subscription> subscriptions = new LinkedList<Subscription>();
        ImportSubscriptionServiceAdapter subAdapter = new ImportSubscriptionServiceAdapter(subscriptions);
        OwnerServiceAdapter ownerAdapter = new DefaultOwnerServiceAdapter(this.ownerCurator, this.i18n);

        Subscription sub = TestUtil.createSubscription(owner, prod, new HashSet<Product>());
        sub.setId(Util.generateDbUUID());
        sub.setQuantity(2000L);
        sub.setStartDate(TestUtil.createDate(2010, 2, 9));
        sub.setEndDate(TestUtil.createDate(3000, 2, 9));
        sub.setModified(TestUtil.createDate(2010, 2, 12));
        subscriptions.add(sub);

        Subscription sub2 = TestUtil.createSubscription(owner, prod2, new HashSet<Product>());
        sub2.setId(Util.generateDbUUID());
        sub2.setQuantity(800L);
        sub2.setStartDate(TestUtil.createDate(2010, 2, 9));
        sub2.setEndDate(TestUtil.createDate(3000, 2, 9));
        sub2.setModified(TestUtil.createDate(2010, 2, 12));
        subscriptions.add(sub2);

        // Trigger the refresh:
        poolManager.getRefresher(subAdapter, ownerAdapter).add(owner).run();

        List<Pool> pools = poolCurator.listByOwner(owner).list();
        assertEquals(2, pools.size());
    }

    // test covers scenario from bug 1012386
    @Test
    public void testRefreshPoolsWithRemovedMasterPool() {
        Product prod = this.createProduct(owner);
        prod.setAttribute(Product.Attributes.VIRT_LIMIT, "4");
        productCurator.merge(prod);
        config.setProperty(ConfigProperties.STANDALONE, "false");

        List<Subscription> subscriptions = new LinkedList<Subscription>();
        ImportSubscriptionServiceAdapter subAdapter = new ImportSubscriptionServiceAdapter(subscriptions);
        OwnerServiceAdapter ownerAdapter = new DefaultOwnerServiceAdapter(this.ownerCurator, this.i18n);

        Subscription sub = TestUtil.createSubscription(owner, prod, new HashSet<Product>());
        sub.setId(Util.generateDbUUID());
        sub.setQuantity(2000L);
        sub.setStartDate(TestUtil.createDate(2010, 2, 9));
        sub.setEndDate(TestUtil.createDate(3000, 2, 9));
        sub.setModified(TestUtil.createDate(2010, 2, 12));
        subscriptions.add(sub);

        // Trigger the refresh:
        poolManager.getRefresher(subAdapter, ownerAdapter).add(owner).run();

        List<Pool> pools = poolCurator.lookupBySubscriptionId(owner, sub.getId());
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
        poolManager.getRefresher(subAdapter, ownerAdapter).add(owner).run();

        assertNull("Original Master Pool should be gone", poolCurator.find(masterId));
        assertNotNull("Bonus Pool should be the same", poolCurator.find(bonusId));
        // master pool should have been recreated
        pools = poolCurator.lookupBySubscriptionId(owner, sub.getId());
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
        Product prod = this.createProduct(owner);
        prod.setAttribute(Product.Attributes.VIRT_LIMIT, "4");
        productCurator.merge(prod);
        config.setProperty(ConfigProperties.STANDALONE, "false");

        List<Subscription> subscriptions = new LinkedList<Subscription>();
        ImportSubscriptionServiceAdapter subAdapter = new ImportSubscriptionServiceAdapter(subscriptions);
        OwnerServiceAdapter ownerAdapter = new DefaultOwnerServiceAdapter(this.ownerCurator, this.i18n);

        Subscription sub = TestUtil.createSubscription(owner, prod, new HashSet<Product>());
        sub.setId(Util.generateDbUUID());
        sub.setQuantity(2000L);
        sub.setStartDate(TestUtil.createDate(2010, 2, 9));
        sub.setEndDate(TestUtil.createDate(3000, 2, 9));
        sub.setModified(TestUtil.createDate(2010, 2, 12));
        subscriptions.add(sub);

        // Trigger the refresh:
        poolManager.getRefresher(subAdapter, ownerAdapter).add(owner).run();

        List<Pool> pools = poolCurator.lookupBySubscriptionId(owner, sub.getId());
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
        poolManager.getRefresher(subAdapter, ownerAdapter).add(owner).run();

        assertNull("Original bonus pool should be gone", poolCurator.find(bonusId));
        assertNotNull("Master pool should be the same", poolCurator.find(masterId));
        // master pool should have been recreated
        pools = poolCurator.lookupBySubscriptionId(owner, sub.getId());
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
        Consumer c1 = createConsumer(owner);
        Consumer c2 = createConsumer(owner);

        // Create a pool for this owner:
        Pool pool = TestUtil.createPool(owner, product);
        poolCurator.create(pool);

        // Give those consumers entitlements:
        Map<String, Integer> pQs = new HashMap<String, Integer>();
        pQs.put(pool.getId(), 1);
        poolManager.entitleByPools(c1, pQs);
        assertEquals(2, consumerCurator.listByOwner(owner).list().size());
        assertEquals(1, poolCurator.listByOwner(owner).list().size());
        assertEquals(1, entitlementCurator.listByOwner(owner).list().size());

        // Generate an ueber certificate for the Owner. This will need to
        // be cleaned up along with the owner deletion.
        UeberCertificate uCert = ueberCertGenerator.generate(owner.getKey(), setupAdminPrincipal("test"));
        assertNotNull(uCert);

        ownerResource.deleteOwner(owner.getKey(), true, false);

        assertEquals(0, consumerCurator.listByOwner(owner).list().size());
        assertNull(consumerCurator.findByUuid(c1.getUuid()));
        assertNull(consumerCurator.findByUuid(c2.getUuid()));
        assertEquals(0, poolCurator.listByOwner(owner).list().size());
        assertEquals(0, entitlementCurator.listByOwner(owner).list().size());
        assertNull(ueberCertCurator.findForOwner(owner));
    }


    @Test(expected = ForbiddenException.class)
    public void testConsumerRoleCannotGetOwner() {
        Consumer c = createConsumer(owner);
        setupPrincipal(new ConsumerPrincipal(c));

        securityInterceptor.enable();

        ownerResource.getOwner(owner.getKey());
    }

    @Test
    public void testConsumerCanListPools() {
        Consumer c = createConsumer(owner);
        Principal principal = setupPrincipal(new ConsumerPrincipal(c));

        securityInterceptor.enable();

        ownerResource.listPools(owner.getKey(), null, null, null, null, false, null,
            null, new ArrayList<KeyValueParameter>(), false, false, principal, null);
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
        Product p = this.createProduct(owner);
        Pool pool1 = TestUtil.createPool(owner, p);
        pool1.setAttribute(Pool.Attributes.VIRT_ONLY, "true");
        pool1.setAttribute(Pool.Attributes.DERIVED_POOL, "true");
        pool1.setAttribute(Pool.Attributes.PHYSICAL_ONLY, "false");
        pool1.setAttribute(Pool.Attributes.UNMAPPED_GUESTS_ONLY, "true");
        pool1.setStartDate(now);
        pool1.setEndDate(new Date(now.getTime() + 1000L * 60 * 60 * 24 * 365));
        Pool pool2 = TestUtil.createPool(owner, p);
        pool2.setAttribute(Pool.Attributes.VIRT_ONLY, "true");
        pool2.setAttribute(Pool.Attributes.DERIVED_POOL, "true");
        pool2.setAttribute(Pool.Attributes.PHYSICAL_ONLY, "false");
        pool2.setAttribute(Pool.Attributes.UNMAPPED_GUESTS_ONLY, "true");
        pool2.setStartDate(new Date(now.getTime() + 2 * 1000L * 60 * 60 * 24 * 365));
        pool2.setEndDate(new Date(now.getTime() + 3 * 1000L * 60 * 60 * 24 * 365));
        poolCurator.create(pool1);
        poolCurator.create(pool2);

        List<Pool> nowList = ownerResource.listPools(owner.getKey(), c.getUuid(), null, null, null, false,
            new Date(), null, new ArrayList<KeyValueParameter>(), false, false, principal, null);

        assertEquals(1, nowList.size());
        assert (nowList.get(0).getId().equals(pool1.getId()));

        Date activeOn = new Date(pool2.getStartDate().getTime() + 1000L * 60 * 60 * 24);
        List<Pool> futureList = ownerResource.listPools(owner.getKey(), c.getUuid(), null, null, null,
            false, activeOn, null, new ArrayList<KeyValueParameter>(), false, false,
            principal, null);
        assertEquals(1, futureList.size());
        assert (futureList.get(0).getId().equals(pool2.getId()));
    }

    @Test
    public void testOwnerAdminCanGetPools() {
        Principal principal = setupPrincipal(owner, Access.ALL);

        Product p = this.createProduct(owner);
        Pool pool1 = TestUtil.createPool(owner, p);
        Pool pool2 = TestUtil.createPool(owner, p);
        poolCurator.create(pool1);
        poolCurator.create(pool2);

        List<Pool> pools = ownerResource.listPools(owner.getKey(),
            null, null, null, null, true, null, null,
            new ArrayList<KeyValueParameter>(), false, false, principal, null);
        assertEquals(2, pools.size());
    }

    @Test
    public void testCanFilterPoolsByAttribute() throws Exception {
        Principal principal = setupPrincipal(owner, Access.ALL);

        Product p = this.createProduct(owner);
        Pool pool1 = TestUtil.createPool(owner, p);
        pool1.setAttribute(Product.Attributes.VIRT_ONLY, "true");
        poolCurator.create(pool1);

        Product p2 = this.createProduct(owner);
        p2.setAttribute(Product.Attributes.CORES, "12");
        productCurator.merge(p2);
        Pool pool2 = TestUtil.createPool(owner, p2);
        poolCurator.create(pool2);

        List<KeyValueParameter> params = new ArrayList<KeyValueParameter>();
        params.add(createKeyValueParam("cores", "12"));

        List<Pool> pools = ownerResource.listPools(owner.getKey(), null,
            null, null, null, true, null, null, params, false, false, principal, null);
        assertEquals(1, pools.size());
        assertEquals(pool2, pools.get(0));

        params.clear();
        params.add(createKeyValueParam("virt_only", "true"));

        pools = ownerResource.listPools(owner.getKey(), null, null,
            null, null, true, null, null, params, false, false, principal, null);
        assertEquals(1, pools.size());
        assertEquals(pool1, pools.get(0));
    }


    @Test
    public void testCanFilterOutDevPoolsByAttribute() throws Exception {
        Principal principal = setupPrincipal(owner, Access.ALL);

        Product p = this.createProduct(owner);

        Pool pool1 = TestUtil.createPool(owner, p);
        pool1.setAttribute(Pool.Attributes.DEVELOPMENT_POOL, "true");
        poolCurator.create(pool1);

        Product p2 = this.createProduct(owner);
        Pool pool2 = TestUtil.createPool(owner, p2);
        poolCurator.create(pool2);

        List<KeyValueParameter> params = new ArrayList<KeyValueParameter>();
        List<Pool> pools = ownerResource.listPools(owner.getKey(), null,
            null, null, null, true, null, null, params, false, false, principal, null);
        assertEquals(2, pools.size());

        params = new ArrayList<KeyValueParameter>();
        params.add(createKeyValueParam(Pool.Attributes.DEVELOPMENT_POOL, "!true"));
        pools = ownerResource.listPools(owner.getKey(), null,
            null, null, null, true, null, null, params, false, false, principal, null);
        assertEquals(1, pools.size());
        assertEquals(pool2, pools.get(0));
    }


    @Test(expected = NotFoundException.class)
    public void ownerAdminCannotAccessAnotherOwnersPools() {
        Owner evilOwner = new Owner("evilowner");
        ownerCurator.create(evilOwner);
        Principal principal = setupPrincipal(evilOwner, Access.ALL);

        Product p = this.createProduct(owner);
        Pool pool1 = TestUtil.createPool(owner, p);
        Pool pool2 = TestUtil.createPool(owner, p);
        poolCurator.create(pool1);
        poolCurator.create(pool2);

        securityInterceptor.enable();

        // Filtering should just cause this to return no results:
        ownerResource.listPools(owner.getKey(), null, null, null, null, true, null,
            null, new ArrayList<KeyValueParameter>(), false, false, principal, null);
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
        ownerResource.deleteOwner(owner.getKey(), true, false);
    }

    private Event createConsumerCreatedEvent(Owner o) {
        // Rather than run through an entire call to ConsumerResource, we'll
        // fake the
        // events in the db:
        setupPrincipal(o, Access.ALL);
        Consumer consumer = createConsumer(o);
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
        Consumer c = createConsumer(owner);
        setupPrincipal(new ConsumerPrincipal(c));

        securityInterceptor.enable();

        ownerResource.getOwnerAtomFeed(owner.getKey());
    }

    @Test(expected = ForbiddenException.class)
    public void consumerCannotListAllConsumersInOwner() {
        Consumer c = createConsumer(owner);
        setupPrincipal(new ConsumerPrincipal(c));

        securityInterceptor.enable();

        ownerResource.listConsumers(owner.getKey(), null, null,
            new ArrayList<String>(), null, null, null, null, null, null);
    }

    @Test
    public void consumerCanListConsumersByIdWhenOtherParametersPresent() {
        Consumer c = createConsumer(owner);
        List<String> uuids = new ArrayList<String>();
        uuids.add(c.getUuid());

        setupPrincipal(owner, Access.ALL);
        securityInterceptor.enable();

        Set<String> types = new HashSet<String>();
        types.add("type");
        consumerTypeCurator.create(new ConsumerType("type"));

        CandlepinQuery<Consumer> result = ownerResource.listConsumers(
            owner.getKey(), "username", types, uuids, null, null, null, null, null, new PageRequest());

        assertNotNull(result);
        List<Consumer> consumers = result.list();

        assertEquals(0, consumers.size());
    }

    @Test
    public void consumerCannotListConsumersFromAnotherOwner() {
        Consumer c = createConsumer(owner);

        Owner owner2 = ownerCurator.create(new Owner("Owner2"));
        Consumer c2 = createConsumer(owner2);

        List<String> uuids = new ArrayList<String>();
        uuids.add(c.getUuid());
        uuids.add(c2.getUuid());

        setupPrincipal(owner, Access.ALL);
        securityInterceptor.enable();

        CandlepinQuery<Consumer> result = ownerResource.listConsumers(
            owner.getKey(), null, null, uuids, null, null, null, null, null, null);

        assertNotNull(result);
        List<Consumer> consumers = result.list();

        assertEquals(1, consumers.size());
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
        Consumer c = createConsumer(owner);
        Consumer c2 = createConsumer(owner);

        List<String> uuids = new ArrayList<String>();
        uuids.add(c.getUuid());
        uuids.add(c2.getUuid());

        setupPrincipal(owner, Access.ALL);
        securityInterceptor.enable();

        CandlepinQuery<Consumer> result = ownerResource.listConsumers(
            owner.getKey(), null, null, uuids, null, null, null, null, null, null);

        assertNotNull(result);
        List<Consumer> consumers = result.list();

        assertEquals(2, consumers.size());
    }

    //copied from consumerCannotListAllConsumersInOwner
    @Test(expected = ForbiddenException.class)
    public void consumerCannotCountAllConsumersInOwner() {
        Consumer c = createConsumer(owner);
        setupPrincipal(new ConsumerPrincipal(c));
        securityInterceptor.enable();

        ownerResource.countConsumers(owner.getKey(), typeLabels,
            skus, subscriptionIds, contracts);
    }

    //copied from failWhenListingByBadConsumerType
    @Test
    public void failWhenCountingByBadConsumerType() {
        ex.expect(BadRequestException.class);
        ex.expectMessage(IsEqual.<String>equalTo("No such unit type(s): unknown"));

        Set<String> types = new HashSet<String>();
        types.add("unknown");

        ownerResource.countConsumers(owner.getKey(), types,
            skus, subscriptionIds, contracts);
    }

    @Test
    public void countShouldThrowExceptionIfUnknownOwner() throws Exception {
        String key = "unknown";
        ex.expect(NotFoundException.class);
        ex.expectMessage(i18n.tr("owner with key: {0} was not found", key));
        createConsumer(owner);

        ownerResource.countConsumers(key, typeLabels,
            skus, subscriptionIds, contracts);
    }

    @Test
    public void consumerListPoolsGetCalculatedAttributes() {
        Product p = this.createProduct(owner);
        Pool pool1 = TestUtil.createPool(owner, p);
        poolCurator.create(pool1);

        Consumer c = TestUtil.createConsumer(owner);
        consumerTypeCurator.create(c.getType());
        consumerCurator.create(c);

        Principal principal = setupPrincipal(new ConsumerPrincipal(c));
        securityInterceptor.enable();

        List<Pool> pools = ownerResource.listPools(owner.getKey(), c.getUuid(), null,
            p.getId(), null, true, null, null, new ArrayList<KeyValueParameter>(), false, false,
            principal, null);
        assertEquals(1, pools.size());
        Pool returnedPool = pools.get(0);
        assertNotNull(returnedPool.getCalculatedAttributes());
    }


    @Test(expected = NotFoundException.class)
    public void testConsumerListPoolsCannotAccessOtherConsumer() {
        Product p = this.createProduct(owner);
        Pool pool1 = TestUtil.createPool(owner, p);
        poolCurator.create(pool1);

        Consumer c = createConsumer(owner);

        securityInterceptor.enable();

        Owner owner2 = createOwner();
        ownerCurator.create(owner2);

        ownerResource.listPools(owner.getKey(), c.getUuid(), null, p.getUuid(),  null, true, null, null,
            new ArrayList<KeyValueParameter>(), false, false, setupPrincipal(owner2, Access.NONE), null);
    }

    @Test
    public void testEntitlementsRevocationWithLifoOrder() throws Exception {
        Pool pool = doTestEntitlementsRevocationCommon(7, 4, 5);
        assertEquals(5L, this.poolCurator.find(pool.getId()).getConsumed().longValue());
    }

    @Test
    public void testEntitlementsRevocationWithNoOverflow() throws Exception {
        Pool pool = doTestEntitlementsRevocationCommon(10, 4, 5);
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
        List<ActivationKey> keys = ownerResource.ownerActivationKeys(owner.getKey(), null);
        assertEquals(1, keys.size());
    }

    @Test
    public void testSearchActivationsKeysByName() {
        ActivationKey key = new ActivationKey();
        key.setName("dd");
        key.setReleaseVer(new Release("release1"));
        key = ownerResource.createActivationKey(owner.getKey(), key);
        assertNotNull(key.getId());
        assertEquals(key.getOwner().getId(), owner.getId());
        assertEquals(key.getReleaseVer().getReleaseVer(), "release1");

        key = new ActivationKey();
        key.setName("blah");
        key.setReleaseVer(new Release("release2"));
        key = ownerResource.createActivationKey(owner.getKey(), key);
        assertNotNull(key.getId());
        assertEquals(key.getOwner().getId(), owner.getId());
        assertEquals(key.getReleaseVer().getReleaseVer(), "release2");

        List<ActivationKey> keys = ownerResource.ownerActivationKeys(owner.getKey(), "dd");
        assertEquals(1, keys.size());

        keys = ownerResource.ownerActivationKeys(owner.getKey(), null);
        assertEquals(2, keys.size());
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

    private Pool doTestEntitlementsRevocationCommon(long subQ, int e1, int e2)
        throws ParseException {

        Product prod = this.createProduct(owner);

        List<Subscription> subscriptions = new LinkedList<Subscription>();
        ImportSubscriptionServiceAdapter subAdapter = new ImportSubscriptionServiceAdapter(subscriptions);
        OwnerServiceAdapter ownerAdapter = new DefaultOwnerServiceAdapter(this.ownerCurator, this.i18n);

        Subscription sub = TestUtil.createSubscription(owner, prod, new HashSet<Product>());
        sub.setId(Util.generateDbUUID());
        sub.setQuantity(1000L);
        sub.setStartDate(TestUtil.createDate(2009, 11, 30));
        sub.setEndDate(TestUtil.createDate(Calendar.getInstance().get(Calendar.YEAR) + 10, 10, 30));
        sub.setModified(TestUtil.createDate(2015, 11, 30));
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

        poolManager.getRefresher(subAdapter, ownerAdapter).add(retrieved).run();
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
        e1.getPool().setConsumed(e1.getPool().getConsumed() + quantity);
        this.poolCurator.merge(e1.getPool());

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
        PermissionBlueprint p = new PermissionBlueprint(PermissionType.OWNER, owner, Access.ALL);
        Role r = new Role("rolename");
        r.addPermission(p);
        roleCurator.create(r);
        ownerResource.deleteOwner(owner.getKey(), false, false);
    }

    @Test(expected = NotFoundException.class)
    public void undoImportforOwnerWithNoImports() {
        Owner owner1 = new Owner("owner-with-no-imports", "foo");
        ownerResource.createOwner(owner1);
        ownerResource.undoImports(owner1.getKey(), new UserPrincipal("JarjarBinks", null, true));
    }

    @Test(expected = ConflictException.class)
    public void testConflictOnDelete() {

        Owner o = mock(Owner.class);
        OwnerCurator oc = mock(OwnerCurator.class);
        OwnerManager ownerManager = mock(OwnerManager.class);
        EventFactory eventFactory = mock(EventFactory.class);
        OwnerResource or = new OwnerResource(
            oc, null, null, i18n, null, eventFactory, null, null, null, poolManager, ownerManager,  null,
            null, null, null, null, null, null, null, null, null, contentOverrideValidator,
            serviceLevelValidator, null, null, null, productManager, contentManager, null
        );

        when(oc.lookupByKey(eq("testOwner"))).thenReturn(o);
        ConstraintViolationException ce = new ConstraintViolationException(null, null, null);
        PersistenceException pe = new PersistenceException(ce);
        Mockito.doThrow(pe).when(ownerManager).cleanupAndDelete(eq(o), eq(poolManager), eq(true));
        or.deleteOwner("testOwner", true, true);
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

        OwnerResource ownerres = new OwnerResource(
            oc, akc, null, i18n, null, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, productManager, contentManager, null
        );

        ownerres.createActivationKey("testOwner", ak);
    }

    @Test
    public void testUpdateOwner() {
        config.setProperty(ConfigProperties.STANDALONE, "false");
        Owner owner = new Owner("Test Owner", "test");
        ownerCurator.create(owner);

        Product prod1 = this.createProduct(owner);
        prod1.setAttribute(Product.Attributes.SUPPORT_LEVEL, "premium");
        productCurator.merge(prod1);
        Product prod2 = this.createProduct(owner);
        prod2.setAttribute(Product.Attributes.SUPPORT_LEVEL, "standard");
        productCurator.merge(prod2);

        List<Subscription> subscriptions = new LinkedList<Subscription>();
        ImportSubscriptionServiceAdapter subAdapter = new ImportSubscriptionServiceAdapter(subscriptions);
        OwnerServiceAdapter ownerAdapter = new DefaultOwnerServiceAdapter(this.ownerCurator, this.i18n);

        Subscription sub1 = TestUtil.createSubscription(owner, prod1, new HashSet<Product>());
        sub1.setId(Util.generateDbUUID());
        sub1.setQuantity(2000L);
        sub1.setStartDate(TestUtil.createDate(2010, 2, 9));
        sub1.setEndDate(TestUtil.createDate(3000, 2, 9));
        sub1.setModified(TestUtil.createDate(2010, 2, 12));
        subscriptions.add(sub1);

        Subscription sub2 = TestUtil.createSubscription(owner, prod2, new HashSet<Product>());
        sub2.setId(Util.generateDbUUID());
        sub2.setQuantity(2000L);
        sub2.setStartDate(TestUtil.createDate(2010, 2, 9));
        sub2.setEndDate(TestUtil.createDate(3000, 2, 9));
        sub2.setModified(TestUtil.createDate(2010, 2, 12));
        subscriptions.add(sub2);

        // Trigger the refresh:
        poolManager.getRefresher(subAdapter, ownerAdapter).add(owner).run();

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
        assertFalse(owner.getAutobindDisabled());

        // Update with Default Service Level only
        Owner upOwner2 = mock(Owner.class);
        when(upOwner2.getDefaultServiceLevel()).thenReturn("standard");
        ownerResource.updateOwner(owner.getKey(), upOwner2);
        assertEquals("standard", owner.getDefaultServiceLevel());
        assertEquals("New Name", owner.getDisplayName());
        assertEquals(parentOwner1, owner.getParentOwner());
        assertFalse(owner.getAutobindDisabled());

        // Update with Parent Owner only
        Owner upOwner3 = mock(Owner.class);
        when(upOwner3.getParentOwner()).thenReturn(parentOwner2);
        ownerResource.updateOwner(owner.getKey(), upOwner3);
        assertEquals(parentOwner2, owner.getParentOwner());
        assertEquals("standard", owner.getDefaultServiceLevel());
        assertEquals("New Name", owner.getDisplayName());
        assertFalse(owner.getAutobindDisabled());

        // Update with empty Service Level only
        Owner upOwner4 = mock(Owner.class);
        when(upOwner4.getDefaultServiceLevel()).thenReturn("");
        ownerResource.updateOwner(owner.getKey(), upOwner4);
        assertNull(owner.getDefaultServiceLevel());
        assertEquals("New Name", owner.getDisplayName());
        assertEquals(parentOwner2, owner.getParentOwner());
        assertFalse(owner.getAutobindDisabled());

        // Update autobind with disabled value.
        Owner upOwner5 = mock(Owner.class);
        when(upOwner5.getAutobindDisabled()).thenReturn(Boolean.TRUE);
        ownerResource.updateOwner(owner.getKey(), upOwner5);
        assertNull(owner.getDefaultServiceLevel());
        assertEquals("New Name", owner.getDisplayName());
        assertEquals(parentOwner2, owner.getParentOwner());
        assertTrue(owner.getAutobindDisabled());
        assertTrue(owner.autobindDisabled());

        // Update autobind with enabled value.
        Owner upOwner6 = mock(Owner.class);
        when(upOwner6.getAutobindDisabled()).thenReturn(Boolean.FALSE);
        ownerResource.updateOwner(owner.getKey(), upOwner6);
        assertNull(owner.getDefaultServiceLevel());
        assertEquals("New Name", owner.getDisplayName());
        assertEquals(parentOwner2, owner.getParentOwner());
        assertFalse(owner.getAutobindDisabled());
        assertFalse(owner.autobindDisabled());

        // Unset autobindDisabled results in no update.
        Owner upOwner7 = mock(Owner.class);
        when(upOwner7.getAutobindDisabled()).thenReturn(null);
        ownerResource.updateOwner(owner.getKey(), upOwner7);
        assertNull(owner.getDefaultServiceLevel());
        assertEquals("New Name", owner.getDisplayName());
        assertEquals(parentOwner2, owner.getParentOwner());
        assertFalse(owner.getAutobindDisabled());
        assertFalse(owner.autobindDisabled());
    }

    public void testImportManifestSynchronousSuccess() throws IOException, ImporterException {
        ManifestManager manifestManager = mock(ManifestManager.class);
        EventSink es = mock(EventSink.class);
        OwnerResource thisOwnerResource = new OwnerResource(
            ownerCurator, null, null, i18n, es, eventFactory, null, null, manifestManager, null, null, null,
            null, importRecordCurator, null, null, null, null, null, null, null, contentOverrideValidator,
            serviceLevelValidator, null, null, null, productManager, contentManager, null
        );

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

        ImportRecord ir = new ImportRecord(owner);
        when(manifestManager.importManifest(eq(owner), any(File.class), eq("test_file.zip"),
            any(ConflictOverrides.class))).thenReturn(ir);

        ImportRecord response = thisOwnerResource.importManifest(owner.getKey(), new String [] {}, input);
        assertNotNull(response);
        assertEquals(ir, response);
    }

    @Test
    public void testImportManifestAsyncSuccess() throws IOException, ImporterException {
        ManifestManager manifestManager = mock(ManifestManager.class);
        EventSink es = mock(EventSink.class);
        OwnerResource thisOwnerResource = new OwnerResource(
            ownerCurator, null, null, i18n, es, eventFactory, null, null, manifestManager, null, null, null,
            null, importRecordCurator, null, null, null, null, null, null, null, contentOverrideValidator,
            serviceLevelValidator, null, null, null, productManager, contentManager, null
        );

        MultipartInput input = mock(MultipartInput.class);
        InputPart part = mock(InputPart.class);
        File archive = mock(File.class);
        List<InputPart> parts = new ArrayList<InputPart>();
        parts.add(part);
        MultivaluedMap<String, String> mm = new MultivaluedMapImpl<String, String>();
        List<String> contDis = new ArrayList<String>();
        contDis.add("form-data; name=\"upload\"; filename=\"test_file.zip\"");
        mm.put("Content-Disposition", contDis);

        JobDetail job = mock(JobDetail.class);

        when(input.getParts()).thenReturn(parts);
        when(part.getHeaders()).thenReturn(mm);
        when(part.getBody(any(GenericType.class))).thenReturn(archive);
        when(manifestManager.importManifestAsync(eq(owner), any(File.class), eq("test_file.zip"),
                any(ConflictOverrides.class))).thenReturn(job);

        JobDetail response = thisOwnerResource.importManifestAsync(owner.getKey(), new String [] {}, input);
        assertNotNull(response);
        assertEquals(job, response);

        verify(manifestManager, never()).importManifest(eq(owner), any(File.class), any(String.class),
            any(ConflictOverrides.class));
    }

    @Test
    public void testImportManifestFailure() throws IOException, ImporterException {
        ManifestManager manifestManager = mock(ManifestManager.class);
        EventSink es = mock(EventSink.class);
        OwnerResource thisOwnerResource = new OwnerResource(
            ownerCurator, null, null, i18n, es, eventFactory, null, null, manifestManager, null, null, null,
            null, importRecordCurator, null, null, null, null, null, null, null, contentOverrideValidator,
            serviceLevelValidator, null, null, null, productManager, contentManager, null
        );

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

        ImporterException expectedException = new ImporterException("Bad import");
        when(manifestManager.importManifest(eq(owner), any(File.class), any(String.class),
            any(ConflictOverrides.class))).thenThrow(expectedException);

        try {
            thisOwnerResource.importManifest(owner.getKey(), new String [] {}, input);
            fail("Expected IseException was not thrown");
        }
        catch (IseException ise) {
            // expected, so we catch and go on.
        }

        verify(manifestManager).recordImportFailure(eq(owner), eq(expectedException), eq("test_file.zip"));
    }

    @Test
    public void upstreamConsumers() {
        Principal p = mock(Principal.class);
        OwnerCurator oc = mock(OwnerCurator.class);
        UpstreamConsumer upstream = mock(UpstreamConsumer.class);
        Owner owner = mock(Owner.class);
        OwnerResource ownerres = new OwnerResource(
            oc, null, null, i18n, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, contentOverrideValidator, serviceLevelValidator, null,
            null, null, productManager, contentManager, null
        );

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
        Product p = this.createProduct(owner);
        Subscription s = TestUtil.createSubscription(owner, p);
        s.setId("MADETHISUP");
        assertEquals(0, poolCurator.listByOwner(owner).list().size());
        ownerResource.createSubscription(owner.getKey(), s);
        assertEquals(1, poolCurator.listByOwner(owner).list().size());
    }

    @Test
    public void createPool() {
        Product prod = this.createProduct(owner);
        Pool pool = TestUtil.createPool(owner, prod);
        assertEquals(0, poolCurator.listByOwner(owner).list().size());
        ownerResource.createPool(owner.getKey(), pool);
        assertEquals(1, poolCurator.listByOwner(owner).list().size());
        assertNotNull(pool.getId());

    }

    @Test
    public void updatePool() {
        Product prod = this.createProduct(owner);
        Pool pool = TestUtil.createPool(owner, prod);
        ownerResource.createPool(owner.getKey(), pool);
        List<Pool> createdPools = poolCurator.listByOwner(owner).list();
        assertEquals(1, createdPools.size());
        assertEquals(pool.getQuantity(), createdPools.get(0).getQuantity());

        pool.setQuantity(10L);
        ownerResource.createPool(owner.getKey(), pool);
        List<Pool> updatedPools = poolCurator.listByOwner(owner).list();
        assertEquals(1, createdPools.size());
        assertEquals(10L, createdPools.get(0).getQuantity().longValue());
    }

    @Test
    public void createBonusPool() {
        Product prod = this.createProduct(owner);
        prod.setAttribute(Product.Attributes.VIRT_LIMIT, "2");
        productCurator.merge(prod);
        Pool pool = TestUtil.createPool(owner, prod);
        assertEquals(0, poolCurator.listByOwner(owner).list().size());
        ownerResource.createPool(owner.getKey(), pool);
        List<Pool> pools = poolCurator.listByOwner(owner).list();
        assertEquals(2, pools.size());
        assertTrue(pools.get(0).getSubscriptionSubKey().startsWith("master") ||
            pools.get(1).getSubscriptionSubKey().startsWith("master"));
        assertTrue(pools.get(0).getSubscriptionSubKey().equals("derived") ||
            pools.get(1).getSubscriptionSubKey().equals("derived"));
    }

    @Test
    public void createBonusPoolForUpdate() {
        Product prod = this.createProduct(owner);
        prod.setAttribute(Product.Attributes.VIRT_LIMIT, "3");
        productCurator.merge(prod);
        Pool pool = TestUtil.createPool(owner, prod);
        pool.setSubscriptionSubKey("master");
        ownerResource.createPool(owner.getKey(), pool);
        pool.setQuantity(100L);
        ownerResource.updatePool(owner.getKey(), pool);
        List<Pool> pools = poolCurator.listByOwner(owner).list();
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
        Product prod = this.createProduct(owner);
        prod.setAttribute(Product.Attributes.VIRT_LIMIT, "3");
        productCurator.merge(prod);
        Pool pool = TestUtil.createPool(owner, prod);
        pool.setSubscriptionSubKey("master");
        ownerResource.createPool(owner.getKey(), pool);
        List<Pool> pools = poolCurator.listByOwner(owner).list();
        assertEquals(2, pools.size());
        pool.setStartDate(new Date(System.currentTimeMillis() - 5 * 24 * 60 * 60 * 1000));
        pool.setEndDate(new Date(System.currentTimeMillis() - 3 * 24 * 60 * 60 * 1000));
        ownerResource.updatePool(owner.getKey(), pool);
        pools = poolCurator.listByOwner(owner).list();
        assertEquals(0, pools.size());
    }

    @Test(expected = BadRequestException.class)
    public void cantUpdateBonusPool() {
        Product prod = this.createProduct(owner);
        prod.setAttribute(Product.Attributes.VIRT_LIMIT, "3");
        productCurator.merge(prod);
        Pool pool = TestUtil.createPool(owner, prod);
        pool.setSubscriptionSubKey("master");
        ownerResource.createPool(owner.getKey(), pool);
        List<Pool> pools = poolCurator.listByOwner(owner).list();

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
        Product prod = this.createProduct(owner);
        prod.setAttribute(Product.Attributes.VIRT_ONLY, "true");
        prod.setMultiplier(2L);
        productCurator.merge(prod);
        Pool pool = TestUtil.createPool(owner, prod);
        pool.setQuantity(100L);
        assertEquals(0, poolCurator.listByOwner(owner).list().size());
        ownerResource.createPool(owner.getKey(), pool);
        List<Pool> pools = poolCurator.listByOwner(owner).list();
        assertEquals(1, pools.size());
        assertTrue(Boolean.parseBoolean(pools.get(0).getAttributeValue(Product.Attributes.VIRT_ONLY)));
        assertEquals(200L, pools.get(0).getQuantity().intValue());
    }

    @Test
    public void getAllEntitlementsForOwner() {
        PageRequest req = new PageRequest();
        req.setPage(1);
        req.setPerPage(10);

        Owner owner = TestUtil.createOwner();
        Consumer consumer = TestUtil.createConsumer(owner);
        Pool pool = TestUtil.createPool(owner, TestUtil.createProduct());

        Entitlement e = TestUtil.createEntitlement(owner, consumer, pool, null);
        e.setId("getAllEntitlementsForOwner");
        List<Entitlement> entitlements = new ArrayList<Entitlement>();
        entitlements.add(e);
        Page<List<Entitlement>> page = new Page<List<Entitlement>>();
        page.setPageData(entitlements);

        OwnerCurator oc = mock(OwnerCurator.class);
        EntitlementCurator ec = mock(EntitlementCurator.class);
        OwnerResource ownerres = new OwnerResource(
            oc, null, null, i18n, null, null, null, null, null, null, null, null, null, null, null, null, ec,
            null, null, null, null, null, null, null, null, null, productManager, contentManager, null
        );

        when(oc.lookupByKey(owner.getKey())).thenReturn(owner);
        when(ec.listByOwner(isA(Owner.class), anyString(), isA(EntitlementFilterBuilder.class),
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
        OwnerResource ownerres = new OwnerResource(
            oc, null, null, i18n, null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null, productManager, contentManager, null
        );
        ownerres.ownerEntitlements("Taylor Swift", null, null, null, req);
    }

    @Test
    public void testCreateUeberCertificateFromScratch() {
        Principal principal = setupPrincipal(owner, Access.ALL);
        Owner owner = TestUtil.createOwner();
        UeberCertificate entCert = mock(UeberCertificate.class);

        OwnerCurator oc = mock(OwnerCurator.class);
        ConsumerCurator cc = mock(ConsumerCurator.class);
        EntitlementCurator ec = mock(EntitlementCurator.class);
        CandlepinPoolManager cpm = mock(CandlepinPoolManager.class);
        EntitlementCertificateCurator ecc = mock(EntitlementCertificateCurator.class);
        UeberCertificateCurator uc = mock(UeberCertificateCurator.class);
        UeberCertificateGenerator ucg = mock(UeberCertificateGenerator.class);

        OwnerResource resource = new OwnerResource(
            oc, null, cc, i18n, null, null, null, null, null, cpm, null, null, null, null, null, ecc, ec,
            uc, ucg, null, null, null, null, null, null, null, productManager, contentManager, null
        );

        when(oc.lookupByKey(eq("admin"))).thenReturn(owner);
        when(ucg.generate(eq(owner.getKey()), eq(principal))).thenReturn(entCert);

        UeberCertificate result = resource.createUeberCertificate(principal, owner.getKey());
        assertEquals(entCert, result);
    }

    @Test
    public void testCreateUeberCertificateRegenerate() {
        Principal principal = setupPrincipal(owner, Access.ALL);
        Owner owner = TestUtil.createOwner();
        UeberCertificate entCert = mock(UeberCertificate.class);

        OwnerCurator oc = mock(OwnerCurator.class);
        ConsumerCurator cc = mock(ConsumerCurator.class);
        EntitlementCurator ec = mock(EntitlementCurator.class);
        CandlepinPoolManager cpm = mock(CandlepinPoolManager.class);
        EntitlementCertificateCurator ecc = mock(EntitlementCertificateCurator.class);
        UeberCertificateCurator uc = mock(UeberCertificateCurator.class);
        UeberCertificateGenerator ucg = mock(UeberCertificateGenerator.class);

        OwnerResource resource = new OwnerResource(
            oc, null, cc, i18n, null, null, null, null, null, cpm, null, null, null, null, null, ecc, ec,
            uc, ucg, null, null, null, null, null, null, null, productManager, contentManager, null
        );

        when(ucg.generate(eq(owner.getKey()), eq(principal))).thenReturn(entCert);
        UeberCertificate result = resource.createUeberCertificate(principal, owner.getKey());

        assertEquals(entCert, result);
    }

}
