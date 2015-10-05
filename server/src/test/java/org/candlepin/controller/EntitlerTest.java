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
package org.candlepin.controller;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.candlepin.audit.Event;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.Content;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolAttribute;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.policy.ValidationError;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.js.entitlement.EntitlementRulesTranslator;
import org.candlepin.resource.dto.AutobindData;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.test.TestUtil;

import org.junit.Before;
import org.junit.Test;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
/**
 * EntitlerTest
 */
public class EntitlerTest {
    private PoolManager pm;
    private EventFactory ef;
    private EventSink sink;
    private I18n i18n;
    private Entitler entitler;
    private Consumer consumer;
    private ConsumerCurator cc;
    private EntitlementRulesTranslator translator;
    private SubscriptionServiceAdapter subAdapter;
    private EntitlementCurator entitlementCurator;
    private Configuration config;
    private PoolCurator poolCurator;
    private ProductCurator productCurator;
    private ProductServiceAdapter productAdapter;


    private ValidationResult fakeOutResult(String msg) {
        ValidationResult result = new ValidationResult();
        ValidationError err = new ValidationError(msg);
        result.addError(err);
        return result;
    }

    @Before
    public void init() {
        pm = mock(PoolManager.class);
        ef = mock(EventFactory.class);
        sink = mock(EventSink.class);
        cc = mock(ConsumerCurator.class);
        consumer = mock(Consumer.class);
        entitlementCurator = mock(EntitlementCurator.class);
        i18n = I18nFactory.getI18n(
            getClass(),
            Locale.US,
            I18nFactory.READ_PROPERTIES | I18nFactory.FALLBACK
        );
        translator = new EntitlementRulesTranslator(i18n);
        subAdapter = mock(SubscriptionServiceAdapter.class);
        config = mock(Configuration.class);
        poolCurator = mock(PoolCurator.class);
        productCurator = mock(ProductCurator.class);
        productAdapter = mock(ProductServiceAdapter.class);

        entitler = new Entitler(pm, cc, i18n, ef, sink, translator, entitlementCurator, config,
                poolCurator, productCurator, productAdapter);
    }

    @Test
    public void bindByPoolString() throws EntitlementRefusedException {
        String poolid = "pool10";
        Pool pool = mock(Pool.class);
        Entitlement ent = mock(Entitlement.class);

        when(cc.findByUuid(eq("abcd1234"))).thenReturn(consumer);
        when(pm.find(eq(poolid))).thenReturn(pool);
        when(pm.entitleByPool(eq(consumer), eq(pool), eq(1))).thenReturn(ent);

        List<Entitlement> ents = entitler.bindByPool(poolid, "abcd1234", 1);
        assertNotNull(ents);
        assertEquals(ent, ents.get(0));
    }

    @Test
    public void bindByPool() throws EntitlementRefusedException {
        String poolid = "pool10";
        Pool pool = mock(Pool.class);
        Entitlement ent = mock(Entitlement.class);

        when(pm.find(eq(poolid))).thenReturn(pool);
        when(pm.entitleByPool(eq(consumer), eq(pool), eq(1))).thenReturn(ent);

        List<Entitlement> ents = entitler.bindByPool(poolid, consumer, 1);
        assertNotNull(ents);
        assertEquals(ent, ents.get(0));
    }

    @Test
    public void bindByProductsString() throws EntitlementRefusedException {
        String[] pids = {"prod1", "prod2", "prod3"};
        when(cc.findByUuid(eq("abcd1234"))).thenReturn(consumer);
        entitler.bindByProducts(pids, "abcd1234", null, null);
        AutobindData data = AutobindData.create(consumer).forProducts(pids);
        verify(pm).entitleByProducts(eq(data));
    }

    @Test
    public void bindByProducts() throws EntitlementRefusedException {
        String[] pids = {"prod1", "prod2", "prod3"};
        AutobindData data = AutobindData.create(consumer).forProducts(pids);
        entitler.bindByProducts(data);
        verify(pm).entitleByProducts(data);
    }

    @Test(expected = BadRequestException.class)
    public void nullPool() {
        String poolid = "foo";
        Consumer c = null; // keeps me from casting null
        when(pm.find(eq(poolid))).thenReturn(null);
        entitler.bindByPool(poolid, c, 10);
    }

    @Test(expected = ForbiddenException.class)
    public void someOtherErrorPool() {
        bindByPoolErrorTest("do.not.match");
    }

    @Test(expected = ForbiddenException.class)
    public void consumerTypeMismatchPool() {
        bindByPoolErrorTest("rulefailed.consumer.type.mismatch");
    }

    @Test(expected = ForbiddenException.class)
    public void alreadyHasProductPool() {
        bindByPoolErrorTest("rulefailed.consumer.already.has.product");
    }

    @Test(expected = ForbiddenException.class)
    public void noEntitlementsAvailable() {
        bindByPoolErrorTest("rulefailed.no.entitlements.available");
    }

    @Test
    public void consumerDoesntSupportInstanceBased() {
        String expected = "Unit does not support instance based " +
            "calculation required by pool 'pool10'";
        try {
            bindByPoolErrorTest("rulefailed.instance.unsupported.by.consumer");
            fail();
        }
        catch (ForbiddenException e) {
            assertEquals(expected, e.getMessage());
        }
    }

    @Test
    public void consumerDoesntSupportCores() {
        String expected = "Unit does not support core " +
            "calculation required by pool 'pool10'";
        try {
            bindByPoolErrorTest("rulefailed.cores.unsupported.by.consumer");
            fail();
        }
        catch (ForbiddenException e) {
            assertEquals(expected, e.getMessage());
        }
    }

    @Test
    public void consumerDoesntSupportRam() {
        String expected = "Unit does not support RAM " +
            "calculation required by pool 'pool10'";
        try {
            bindByPoolErrorTest("rulefailed.ram.unsupported.by.consumer");
            fail();
        }
        catch (ForbiddenException e) {
            assertEquals(expected, e.getMessage());
        }
    }

    @Test
    public void consumerDoesntSupportDerived() {
        String expected = "Unit does not support derived products " +
            "data required by pool 'pool10'";
        try {
            bindByPoolErrorTest("rulefailed.derivedproduct.unsupported.by.consumer");
            fail();
        }
        catch (ForbiddenException e) {
            assertEquals(expected, e.getMessage());
        }
    }

    private void bindByPoolErrorTest(String msg) {
        try {
            String poolid = "pool10";
            Pool pool = mock(Pool.class);
            EntitlementRefusedException ere = new EntitlementRefusedException(
                fakeOutResult(msg));

            when(pool.getId()).thenReturn(poolid);
            when(pm.find(eq(poolid))).thenReturn(pool);
            when(pm.entitleByPool(eq(consumer), eq(pool), eq(1))).thenThrow(ere);
            entitler.bindByPool(poolid, consumer, 1);
        }
        catch (EntitlementRefusedException e) {
            fail(msg + ": threw unexpected error");
        }
    }

    @Test(expected = ForbiddenException.class)
    public void alreadyHasProduct() {
        bindByProductErrorTest("rulefailed.consumer.already.has.product");
    }

    @Test(expected = ForbiddenException.class)
    public void noEntitlementsForProduct() {
        bindByProductErrorTest("rulefailed.no.entitlements.available");
    }

    @Test(expected = ForbiddenException.class)
    public void mismatchByProduct() {
        bindByProductErrorTest("rulefailed.consumer.type.mismatch");
    }

    @Test
    public void virtOnly() {
        String expected = "Pool is restricted to virtual guests: 'pool10'.";
        try {
            bindByPoolErrorTest("rulefailed.virt.only");
            fail();
        }
        catch (ForbiddenException e) {
            assertEquals(expected, e.getMessage());
        }
    }

    @Test
    public void physicalOnly() {
        String expected = "Pool is restricted to physical systems: 'pool10'.";
        try {
            bindByPoolErrorTest("rulefailed.physical.only");
            fail();
        }
        catch (ForbiddenException e) {
            assertEquals(expected, e.getMessage());
        }
    }

    @Test(expected = ForbiddenException.class)
    public void allOtherErrors() {
        bindByProductErrorTest("generic.error");
    }

    private void bindByProductErrorTest(String msg) {
        try {
            String[] pids = {"prod1", "prod2", "prod3"};
            EntitlementRefusedException ere = new EntitlementRefusedException(
                fakeOutResult(msg));
            AutobindData data = AutobindData.create(consumer).forProducts(pids);
            when(pm.entitleByProducts(data)).thenThrow(ere);
            entitler.bindByProducts(data);
        }
        catch (EntitlementRefusedException e) {
            fail(msg + ": threw unexpected error");
        }
    }

    @Test
    public void events() {
        List<Entitlement> ents = new ArrayList<Entitlement>();
        ents.add(mock(Entitlement.class));
        ents.add(mock(Entitlement.class));

        Event evt1 = mock(Event.class);
        Event evt2 = mock(Event.class);
        when(ef.entitlementCreated(any(Entitlement.class)))
            .thenReturn(evt1)
            .thenReturn(evt2);
        entitler.sendEvents(ents);

        verify(sink).queueEvent(eq(evt1));
        verify(sink).queueEvent(eq(evt2));
    }

    @Test
    public void noEventsWhenEntitlementsNull() {
        entitler.sendEvents(null);
        verify(sink, never()).queueEvent(any(Event.class));
    }

    @Test
    public void noEventsWhenListEmpty() {
        List<Entitlement> ents = new ArrayList<Entitlement>();
        entitler.sendEvents(ents);
        verify(sink, never()).queueEvent(any(Event.class));
    }

    @Test
    public void testRevokesLapsedUnmappedGuestEntitlementsOnAutoHeal() throws Exception {
        Owner owner1 = new Owner("o1");

        Product product = TestUtil.createProduct(owner1);

        Pool p1 = TestUtil.createPool(owner1, product);

        p1.addAttribute(new PoolAttribute("unmapped_guests_only", "true"));

        Date thirtySixHoursAgo = new Date(new Date().getTime() - 36L * 60L * 60L * 1000L);
        Date twelveHoursAgo = new Date(new Date().getTime() - 12L * 60L * 60L * 1000L);

        Consumer c;

        c = TestUtil.createConsumer(owner1);
        c.setCreated(thirtySixHoursAgo);
        c.setFact("virt.uuid", "1");

        Entitlement e1 = TestUtil.createEntitlement(owner1, c, p1, null);
        e1.setEndDateOverride(twelveHoursAgo);
        Set<Entitlement> entitlementSet1 = new HashSet<Entitlement>();
        entitlementSet1.add(e1);

        p1.setEntitlements(entitlementSet1);

        when(entitlementCurator.findByPoolAttribute(eq(c), eq("unmapped_guests_only"), eq("true")))
            .thenReturn(Arrays.asList(new Entitlement[] {e1}));

        String[] pids = {product.getId(), "prod2"};
        when(cc.findByUuid(eq("abcd1234"))).thenReturn(c);
        entitler.bindByProducts(pids, "abcd1234", null, null);
        AutobindData data = AutobindData.create(c).forProducts(pids);
        verify(pm).entitleByProducts(eq(data));
        verify(pm).revokeEntitlement(e1);
    }

    @Test
    public void testUnmappedGuestRevocation() throws Exception {
        Owner owner1 = new Owner("o1");
        Owner owner2 = new Owner("o2");

        Product product1 = TestUtil.createProduct(owner1);
        Product product2 = TestUtil.createProduct(owner2);

        Pool p1 = TestUtil.createPool(owner1, product1);
        Pool p2 = TestUtil.createPool(owner2, product2);

        p1.addAttribute(new PoolAttribute("unmapped_guests_only", "true"));
        p2.addAttribute(new PoolAttribute("unmapped_guests_only", "true"));

        Date thirtySixHoursAgo = new Date(new Date().getTime() - 36L * 60L * 60L * 1000L);
        Date twelveHoursAgo =  new Date(new Date().getTime() - 12L * 60L * 60L * 1000L);

        Consumer c;

        c = TestUtil.createConsumer(owner1);
        c.setCreated(twelveHoursAgo);

        Entitlement e1 = TestUtil.createEntitlement(owner1, c, p1, null);
        e1.setEndDateOverride(new Date(new Date().getTime() + 1L * 60L * 60L * 1000L));
        Set<Entitlement> entitlementSet1 = new HashSet<Entitlement>();
        entitlementSet1.add(e1);

        p1.setEntitlements(entitlementSet1);

        c = TestUtil.createConsumer(owner2);
        c.setCreated(twelveHoursAgo);

        Entitlement e2 = TestUtil.createEntitlement(owner2, c, p2, null);
        e2.setEndDateOverride(thirtySixHoursAgo);
        Set<Entitlement> entitlementSet2 = new HashSet<Entitlement>();
        entitlementSet2.add(e2);

        p2.setEntitlements(entitlementSet2);

        when(entitlementCurator.findByPoolAttribute(eq("unmapped_guests_only"), eq("true")))
            .thenReturn(Arrays.asList(new Entitlement[] {e1,  e2}));

        int total = entitler.revokeUnmappedGuestEntitlements();
        assertEquals(1, total);

        verify(pm).revokeEntitlement(e1);
    }

    @Test
    public void testDevPoolCreationAtBind() throws EntitlementRefusedException {
        Owner owner = new Owner("o");
        List<Product> devProds = new ArrayList<Product>();
        Product p = new Product("test-product", "Test Product", owner);
        p.setAttribute("support_level", "Premium");
        devProds.add(p);
        Pool activePool = TestUtil.createPool(owner, p);
        List<Pool> activeList = new ArrayList<Pool>();
        activeList.add(activePool);
        Pool devPool = mock(Pool.class);

        Consumer devSystem = TestUtil.createConsumer(owner);
        devSystem.setFact("dev_sku", p.getId());

        when(config.getBoolean(eq(ConfigProperties.STANDALONE))).thenReturn(false);
        when(poolCurator.hasActiveEntitlementPools(eq(owner), any(Date.class))).thenReturn(true);
        when(productAdapter.getProductsByIds(any(Owner.class), any(List.class))).thenReturn(devProds);
        when(productCurator.createOrUpdate(eq(p))).thenReturn(p);
        when(pm.createPool(any(Pool.class))).thenReturn(devPool);
        when(devPool.getId()).thenReturn("test_pool_id");

        AutobindData ad = new AutobindData(devSystem);
        entitler.bindByProducts(ad);
        verify(pm).createPool(any(Pool.class));
    }

    @Test(expected = ForbiddenException.class)
    public void testDevPoolCreationAtBindFailStandalone() throws EntitlementRefusedException {
        Owner owner = new Owner("o");
        List<Product> devProds = new ArrayList<Product>();
        Product p = new Product("test-product", "Test Product", owner);
        devProds.add(p);
        Pool activePool = TestUtil.createPool(owner, p);
        List<Pool> activeList = new ArrayList<Pool>();
        activeList.add(activePool);

        Consumer devSystem = TestUtil.createConsumer(owner);
        devSystem.setFact("dev_sku", p.getId());
        devSystem.addInstalledProduct(new ConsumerInstalledProduct(p));

        when(config.getBoolean(eq(ConfigProperties.STANDALONE))).thenReturn(true);
        when(poolCurator.hasActiveEntitlementPools(eq(owner), any(Date.class))).thenReturn(true);
        when(productAdapter.getProductsByIds(any(Owner.class), any(List.class))).thenReturn(devProds);

        AutobindData ad = new AutobindData(devSystem);
        entitler.bindByProducts(ad);
    }

    @Test(expected = ForbiddenException.class)
    public void testDevPoolCreationAtBindFailNotActive() throws EntitlementRefusedException {
        Owner owner = new Owner("o");
        List<Product> devProds = new ArrayList<Product>();
        Product p = new Product("test-product", "Test Product", owner);
        devProds.add(p);
        List<Pool> activeList = new ArrayList<Pool>();

        Consumer devSystem = TestUtil.createConsumer(owner);
        devSystem.setFact("dev_sku", p.getId());
        devSystem.addInstalledProduct(new ConsumerInstalledProduct(p));

        when(config.getBoolean(eq(ConfigProperties.STANDALONE))).thenReturn(false);
        when(poolCurator.hasActiveEntitlementPools(eq(owner), any(Date.class))).thenReturn(false);
        when(productAdapter.getProductsByIds(any(Owner.class), any(List.class))).thenReturn(devProds);

        AutobindData ad = new AutobindData(devSystem);
        entitler.bindByProducts(ad);
    }

    @Test
    public void testDevPoolCreationAtBindFailNoSkuProduct() throws EntitlementRefusedException {
        Owner owner = new Owner("o");
        List<Product> devProds = new ArrayList<Product>();
        Product p = new Product("test-product", "Test Product", owner);
        Product ip = new Product("test-product-installed", "Installed Test Product", owner);
        devProds.add(ip);
        Pool activePool = TestUtil.createPool(owner, p);
        List<Pool> activeList = new ArrayList<Pool>();
        activeList.add(activePool);

        Consumer devSystem = TestUtil.createConsumer(owner);
        devSystem.setFact("dev_sku", p.getId());
        devSystem.addInstalledProduct(new ConsumerInstalledProduct(ip));

        when(config.getBoolean(eq(ConfigProperties.STANDALONE))).thenReturn(false);
        when(poolCurator.hasActiveEntitlementPools(eq(owner), any(Date.class))).thenReturn(true);
        when(productAdapter.getProductsByIds(any(Owner.class), any(List.class))).thenReturn(devProds);
        AutobindData ad = new AutobindData(devSystem);
        try {
            entitler.bindByProducts(ad);
        }
        catch (ForbiddenException fe) {
            assertEquals(i18n.tr("SKU product not available to this development unit: ''{0}''",
                    p.getId()), fe.getMessage());
        }
    }

    @Test
    public void testDevPoolCreationAtBindNoFailMissingInstalledProduct()
            throws EntitlementRefusedException {
        Owner owner = new Owner("o");
        List<Product> devProds = new ArrayList<Product>();
        Product p = new Product("test-product", "Test Product", owner);
        Product ip1 = new Product("test-product-installed-1", "Installed Test Product 1", owner);
        Product ip2 = new Product("test-product-installed-2", "Installed Test Product 2", owner);
        devProds.add(p);
        devProds.add(ip1);
        Pool activePool = TestUtil.createPool(owner, p);
        List<Pool> activeList = new ArrayList<Pool>();
        activeList.add(activePool);

        Consumer devSystem = TestUtil.createConsumer(owner);
        devSystem.setFact("dev_sku", p.getId());
        devSystem.addInstalledProduct(new ConsumerInstalledProduct(ip1));
        devSystem.addInstalledProduct(new ConsumerInstalledProduct(ip2));

        when(config.getBoolean(eq(ConfigProperties.STANDALONE))).thenReturn(false);
        when(poolCurator.hasActiveEntitlementPools(eq(owner), any(Date.class))).thenReturn(true);
        when(productAdapter.getProductsByIds(any(Owner.class), any(List.class))).thenReturn(devProds);
        Pool expectedPool = entitler.assembleDevPool(devSystem, p.getId());
        when(pm.createPool(any(Pool.class))).thenReturn(expectedPool);
        AutobindData ad = new AutobindData(devSystem);
        List<Entitlement> ents = entitler.bindByProducts(ad);
    }

    @Test
    public void testCreatedDevPoolAttributes() {
        Owner owner = new Owner("o");
        List<Product> devProds = new ArrayList<Product>();
        Product p1 = new Product("dev-product", "Dev Product", owner);
        p1.setAttribute("support_level", "Premium");
        p1.setAttribute("expired_after", "47");
        Product p2 = new Product("provided-product1", "Provided Product 1", owner);
        Product p3 = new Product("provided-product2", "Provided Product 2", owner);
        devProds.add(p1);
        devProds.add(p2);
        devProds.add(p3);
        Consumer devSystem = TestUtil.createConsumer(owner);
        devSystem.setFact("dev_sku", p1.getId());
        devSystem.addInstalledProduct(new ConsumerInstalledProduct(p2));
        devSystem.addInstalledProduct(new ConsumerInstalledProduct(p3));
        when(productAdapter.getProductsByIds(any(Owner.class), any(List.class))).thenReturn(devProds);
        when(productCurator.createOrUpdate(eq(p1))).thenReturn(p1);
        when(productCurator.createOrUpdate(eq(p2))).thenReturn(p2);
        when(productCurator.createOrUpdate(eq(p3))).thenReturn(p3);

        Pool created = entitler.assembleDevPool(devSystem, devSystem.getFact("dev_sku"));
        long intervalMillis = created.getEndDate().getTime() - created.getStartDate().getTime();
        assertEquals(intervalMillis, 1000 * 60 * 60 * 24 * Long.parseLong("47"));
        assertEquals("true", created.getAttributeValue(Pool.DEVELOPMENT_POOL_ATTRIBUTE));
        assertEquals(devSystem.getUuid(), created.getAttributeValue(Pool.REQUIRES_CONSUMER_ATTRIBUTE));
        assertEquals(p1.getId(), created.getProductId());
        assertEquals(2, created.getProvidedProducts().size());
        assertEquals("Premium", created.getProduct().getAttributeValue("support_level"));
        assertEquals(1L, created.getQuantity().longValue());
    }

    @Test
    public void testCreatedDevSkuWithNoSla() {
        Owner owner = new Owner("o");
        List<Product> devProds = new ArrayList<Product>();
        Product p1 = new Product("dev-product", "Dev Product", null);
        devProds.add(p1);
        Consumer devSystem = TestUtil.createConsumer(owner);
        devSystem.setFact("dev_sku", p1.getId());
        when(productAdapter.getProductsByIds(any(Owner.class), any(List.class))).thenReturn(devProds);
        when(productCurator.createOrUpdate(eq(p1))).thenReturn(p1);

        Pool created = entitler.assembleDevPool(devSystem, devSystem.getFact("dev_sku"));
        assertEquals(entitler.DEFAULT_DEV_SLA, created.getProduct().getAttributeValue("support_level"));
    }

    @Test
    public void testEnsureOwnerOnDevProduct() {
        Owner owner = new Owner("o");
        List<Product> devProds = new ArrayList<Product>();
        Product p1 = new Product("dev-product-1", "Dev Product 1", null);
        Content c1 = new Content();
        p1.addContent(c1);
        devProds.add(p1);
        Product p2 = new Product("dev-product-2", "Dev Product 2", null);
        Content c2 = new Content();
        p2.addContent(c2);
        devProds.add(p2);
        Consumer devSystem = TestUtil.createConsumer(owner);
        devSystem.setFact("dev_sku", p1.getId());
        devSystem.addInstalledProduct(new ConsumerInstalledProduct(p2));

        when(productAdapter.getProductsByIds(any(Owner.class), any(List.class))).thenReturn(devProds);
        when(productCurator.createOrUpdate(eq(p1))).thenReturn(p1);
        when(productCurator.createOrUpdate(eq(p2))).thenReturn(p2);

        Pool created = entitler.assembleDevPool(devSystem, devSystem.getFact("dev_sku"));

        assertEquals(owner, created.getProduct().getOwner());
        for (Product p : created.getProvidedProducts()) {
            assertEquals(owner, p.getOwner());
        }
    }
}
