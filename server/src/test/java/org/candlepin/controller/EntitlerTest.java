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
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.Content;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.dto.ContentData;
import org.candlepin.model.dto.ProductData;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.policy.ValidationError;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.js.entitlement.EntitlementRulesTranslator;
import org.candlepin.resource.dto.AutobindData;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.test.TestUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;



/**
 * EntitlerTest
 */
@RunWith(MockitoJUnitRunner.class)
public class EntitlerTest {
    private I18n i18n;
    private Entitler entitler;
    private EntitlementRulesTranslator translator;

    @Mock private PoolManager pm;
    @Mock private EventFactory ef;
    @Mock private EventSink sink;
    @Mock private Owner owner;
    @Mock private Consumer consumer;
    @Mock private ConsumerCurator cc;
    @Mock private EntitlementCurator entitlementCurator;
    @Mock private Configuration config;
    @Mock private OwnerProductCurator ownerProductCurator;
    @Mock private PoolCurator poolCurator;
    @Mock private ProductCurator productCurator;
    @Mock private ProductServiceAdapter productAdapter;
    @Mock private ProductManager productManager;
    @Mock private ContentManager contentManager;

    private ValidationResult fakeOutResult(String msg) {
        ValidationResult result = new ValidationResult();
        ValidationError err = new ValidationError(msg);
        result.addError(err);
        return result;
    }

    @Before
    public void init() {
        when(consumer.getOwner()).thenReturn(owner);

        i18n = I18nFactory.getI18n(
            getClass(),
            Locale.US,
            I18nFactory.READ_PROPERTIES | I18nFactory.FALLBACK
        );
        translator = new EntitlementRulesTranslator(i18n);

        entitler = new Entitler(pm, cc, i18n, ef, sink, translator, entitlementCurator, config,
            ownerProductCurator, poolCurator, productCurator, productManager, productAdapter, contentManager);
    }

    private void mockProducts(Owner owner, final Map<String, Product> products) {
        when(ownerProductCurator.getProductById(eq(owner), any(String.class)))
            .thenAnswer(new Answer<Product>() {
                @Override
                public Product answer(InvocationOnMock invocation) throws Throwable {
                    Object[] args = invocation.getArguments();
                    String pid = (String) args[1];

                    return products.get(pid);
                }
            });

        when(ownerProductCurator.getProductsByIds(eq(owner), any(Collection.class)))
            .thenAnswer(new Answer<Collection<Product>>() {
                @Override
                public Collection<Product> answer(InvocationOnMock invocation) throws Throwable {
                    Object[] args = invocation.getArguments();
                    Collection<String> pids = (Collection<String>) args[1];
                    Set<Product> output = new HashSet<Product>();

                    for (String pid : pids) {
                        Product product = products.get(pid);

                        if (product != null) {
                            output.add(product);
                        }
                    }

                    return output;
                }
            });
    }

    private void mockProducts(Owner owner, Product... products) {
        Map<String, Product> productMap = new HashMap<String, Product>();

        for (Product product : products) {
            productMap.put(product.getId(), product);
        }

        this.mockProducts(owner, productMap);
    }

    private void mockProductImport(Owner owner, final Map<String, Product> products) {
        when(productManager.importProducts(eq(owner), any(Map.class), any(Map.class)))
            .thenAnswer(new Answer<ImportResult<Product>>() {
                @Override
                public ImportResult<Product> answer(InvocationOnMock invocation) throws Throwable {
                    Object[] args = invocation.getArguments();
                    Map<String, ProductData> productData = (Map<String, ProductData>) args[1];
                    ImportResult<Product> importResult = new ImportResult<Product>();
                    Map<String, Product> output = importResult.getCreatedEntities();

                    if (productData != null) {
                        for (String pid : productData.keySet()) {
                            Product product = products.get(pid);

                            if (product != null) {
                                output.put(product.getId(), product);
                            }
                        }
                    }

                    return importResult;
                }
            });
    }

    private void mockProductImport(Owner owner, Product... products) {
        this.mockContentImport(owner, Collections.<String, Content>emptyMap());
        Map<String, Product> productMap = new HashMap<String, Product>();

        for (Product product : products) {
            productMap.put(product.getId(), product);
        }

        this.mockProductImport(owner, productMap);
    }

    private void mockContentImport(Owner owner, final Map<String, Content> contents) {
        when(contentManager.importContent(eq(owner), any(Map.class), any(Set.class)))
            .thenAnswer(new Answer<ImportResult<Content>>() {
                @Override
                public ImportResult<Content> answer(InvocationOnMock invocation) throws Throwable {
                    Object[] args = invocation.getArguments();
                    Map<String, ContentData> contentData = (Map<String, ContentData>) args[1];
                    ImportResult<Content> importResult = new ImportResult<Content>();
                    Map<String, Content> output = importResult.getCreatedEntities();

                    if (contentData != null) {
                        for (String pid : contentData.keySet()) {
                            Content content = contents.get(pid);

                            if (content != null) {
                                output.put(content.getId(), content);
                            }
                        }
                    }

                    return importResult;
                }
            });
    }

    private void mockContentImport(Owner owner, Content... contents) {
        Map<String, Content> contentMap = new HashMap<String, Content>();

        for (Content content : contents) {
            contentMap.put(content.getId(), content);
        }

        this.mockContentImport(owner, contentMap);
    }

    @Test
    public void bindByPoolString() throws EntitlementRefusedException {
        String poolid = "pool10";
        Entitlement ent = mock(Entitlement.class);
        List<Entitlement> eList = new ArrayList<Entitlement>();
        eList.add(ent);
        when(cc.findByUuid(eq("abcd1234"))).thenReturn(consumer);

        Map<String, Integer> pQs = new HashMap<String, Integer>();
        pQs.put(poolid, 1);

        when(pm.entitleByPools(eq(consumer), eq(pQs))).thenReturn(eList);

        List<Entitlement> ents = entitler.bindByPoolQuantities("abcd1234", pQs);
        assertNotNull(ents);
        assertEquals(ent, ents.get(0));
    }

    @Test
    public void bindByPool() throws EntitlementRefusedException {
        String poolid = "pool10";
        Pool pool = mock(Pool.class);
        Entitlement ent = mock(Entitlement.class);
        List<Entitlement> eList = new ArrayList<Entitlement>();
        eList.add(ent);

        when(pm.find(eq(poolid))).thenReturn(pool);
        Map<String, Integer> pQs = new HashMap<String, Integer>();
        pQs.put(poolid, 1);
        when(pm.entitleByPools(eq(consumer), eq(pQs))).thenReturn(eList);

        List<Entitlement> ents = entitler.bindByPoolQuantity(consumer, poolid, 1);
        assertNotNull(ents);
        assertEquals(ent, ents.get(0));
    }

    @Test
    public void bindByProductsString() throws Exception {
        String[] pids = {"prod1", "prod2", "prod3"};
        when(cc.findByUuid(eq("abcd1234"))).thenReturn(consumer);
        entitler.bindByProducts(pids, "abcd1234", null, null);
        AutobindData data = AutobindData.create(consumer).forProducts(pids);
        verify(pm).entitleByProducts(eq(data));
    }

    @Test
    public void bindByProducts() throws Exception  {
        String[] pids = {"prod1", "prod2", "prod3"};
        AutobindData data = AutobindData.create(consumer).forProducts(pids);
        entitler.bindByProducts(data);
        verify(pm).entitleByProducts(data);
    }

    @Test(expected = BadRequestException.class)
    public void nullPool() throws EntitlementRefusedException {
        String poolid = "foo";
        Consumer c = TestUtil.createConsumer(); // keeps me from casting null
        Map<String, Integer> pQs = new HashMap<String, Integer>();
        pQs.put(poolid, 1);
        when(cc.findByUuid(eq(c.getUuid()))).thenReturn(c);
        when(pm.entitleByPools(eq(c), eq(pQs))).thenThrow(new IllegalArgumentException());
        entitler.bindByPoolQuantities(c.getUuid(), pQs);
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
            Map<String, ValidationResult> fakeResult = new HashMap<String, ValidationResult>();
            fakeResult.put(poolid, fakeOutResult(msg));
            EntitlementRefusedException ere = new EntitlementRefusedException(fakeResult);

            when(pool.getId()).thenReturn(poolid);
            when(poolCurator.find(eq(poolid))).thenReturn(pool);
            Map<String, Integer> pQs = new HashMap<String, Integer>();
            pQs.put(poolid, 1);
            when(pm.entitleByPools(eq(consumer), eq(pQs))).thenThrow(ere);
            entitler.bindByPoolQuantity(consumer, poolid, 1);
        }
        catch (EntitlementRefusedException e) {
            fail(msg + ": threw unexpected error");
        }
    }

    @Test(expected = ForbiddenException.class)
    public void alreadyHasProduct() throws Exception {
        bindByProductErrorTest("rulefailed.consumer.already.has.product");
    }

    @Test(expected = ForbiddenException.class)
    public void noEntitlementsForProduct() throws Exception {
        bindByProductErrorTest("rulefailed.no.entitlements.available");
    }

    @Test(expected = ForbiddenException.class)
    public void mismatchByProduct() throws Exception {
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
    public void physicalOnly() throws Exception {
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
    public void allOtherErrors() throws Exception {
        bindByProductErrorTest("generic.error");
    }

    private void bindByProductErrorTest(String msg) throws Exception {
        try {
            String[] pids = {"prod1", "prod2", "prod3"};
            Map<String, ValidationResult> fakeResult = new HashMap<String, ValidationResult>();
            fakeResult.put("blah", fakeOutResult(msg));
            EntitlementRefusedException ere = new EntitlementRefusedException(fakeResult);
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

        Product product = TestUtil.createProduct();

        Pool p1 = TestUtil.createPool(owner1, product);

        p1.setAttribute(Pool.Attributes.UNMAPPED_GUESTS_ONLY, "true");

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

        CandlepinQuery cqmock = mock(CandlepinQuery.class);
        when(cqmock.iterator()).thenReturn(Arrays.asList(e1).iterator());
        when(entitlementCurator.findByPoolAttribute(eq(c), eq("unmapped_guests_only"), eq("true")))
            .thenReturn(cqmock);

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

        Product product1 = TestUtil.createProduct();
        Product product2 = TestUtil.createProduct();

        Pool p1 = TestUtil.createPool(owner1, product1);
        Pool p2 = TestUtil.createPool(owner2, product2);

        p1.setAttribute(Pool.Attributes.UNMAPPED_GUESTS_ONLY, "true");
        p2.setAttribute(Pool.Attributes.UNMAPPED_GUESTS_ONLY, "true");

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

        CandlepinQuery cqmock = mock(CandlepinQuery.class);

        when(cqmock.iterator()).thenReturn(Arrays.asList(e1,  e2).iterator());
        when(entitlementCurator.findByPoolAttribute(eq("unmapped_guests_only"), eq("true")))
            .thenReturn(cqmock);

        int total = entitler.revokeUnmappedGuestEntitlements();
        assertEquals(1, total);

        verify(pm).revokeEntitlement(e2);
    }

    @Test
    public void testDevPoolCreationAtBind() throws Exception {
        Owner owner = TestUtil.createOwner("o");
        List<ProductData> devProdDTOs = new ArrayList<ProductData>();
        Product p = TestUtil.createProduct("test-product", "Test Product");

        p.setAttribute(Product.Attributes.SUPPORT_LEVEL, "Premium");
        devProdDTOs.add(p.toDTO());
        Pool activePool = TestUtil.createPool(owner, p);
        List<Pool> activeList = new ArrayList<Pool>();
        activeList.add(activePool);
        Pool devPool = mock(Pool.class);

        Consumer devSystem = TestUtil.createConsumer(owner);
        devSystem.setFact("dev_sku", p.getId());

        when(config.getBoolean(eq(ConfigProperties.STANDALONE))).thenReturn(false);
        when(poolCurator.hasActiveEntitlementPools(eq(owner), any(Date.class))).thenReturn(true);
        when(productAdapter.getProductsByIds(eq(owner), any(List.class))).thenReturn(devProdDTOs);

        this.mockProducts(owner, p);
        this.mockProductImport(owner, p);
        this.mockContentImport(owner, Collections.<String, Content>emptyMap());

        when(pm.createPool(any(Pool.class))).thenReturn(devPool);
        when(devPool.getId()).thenReturn("test_pool_id");

        AutobindData ad = new AutobindData(devSystem);
        entitler.bindByProducts(ad);
        verify(pm).createPool(any(Pool.class));
    }

    @Test(expected = ForbiddenException.class)
    public void testDevPoolCreationAtBindFailStandalone() throws Exception {
        Owner owner = TestUtil.createOwner("o");
        List<ProductData> devProdDTOs = new ArrayList<ProductData>();
        Product p = TestUtil.createProduct("test-product", "Test Product");
        devProdDTOs.add(p.toDTO());

        Pool activePool = TestUtil.createPool(owner, p);
        List<Pool> activeList = new ArrayList<Pool>();
        activeList.add(activePool);

        Consumer devSystem = TestUtil.createConsumer(owner);
        devSystem.setFact("dev_sku", p.getId());
        devSystem.addInstalledProduct(new ConsumerInstalledProduct(p));

        when(config.getBoolean(eq(ConfigProperties.STANDALONE))).thenReturn(true);
        when(poolCurator.hasActiveEntitlementPools(eq(owner), any(Date.class))).thenReturn(true);
        when(productAdapter.getProductsByIds(any(Owner.class), any(List.class))).thenReturn(devProdDTOs);
        when(ownerProductCurator.getProductById(eq(owner), eq(p.getId()))).thenReturn(p);

        AutobindData ad = new AutobindData(devSystem);
        entitler.bindByProducts(ad);
    }

    @Test(expected = ForbiddenException.class)
    public void testDevPoolCreationAtBindFailNotActive() throws Exception {
        Owner owner = TestUtil.createOwner("o");
        List<ProductData> devProdDTOs = new ArrayList<ProductData>();
        Product p = TestUtil.createProduct("test-product", "Test Product");
        devProdDTOs.add(p.toDTO());

        Consumer devSystem = TestUtil.createConsumer(owner);
        devSystem.setFact("dev_sku", p.getId());
        devSystem.addInstalledProduct(new ConsumerInstalledProduct(p));

        when(config.getBoolean(eq(ConfigProperties.STANDALONE))).thenReturn(false);
        when(poolCurator.hasActiveEntitlementPools(eq(owner), any(Date.class))).thenReturn(false);
        when(productAdapter.getProductsByIds(any(Owner.class), any(List.class))).thenReturn(devProdDTOs);
        when(ownerProductCurator.getProductById(eq(owner), eq(p.getId()))).thenReturn(p);

        AutobindData ad = new AutobindData(devSystem);
        entitler.bindByProducts(ad);
    }

    private void mockUpdateProduct(final Product product, Owner owner) {
        when(productManager.updateProduct(any(ProductData.class), eq(owner), anyBoolean()))
            .thenAnswer(new Answer<Product>() {
                @Override
                public Product answer(InvocationOnMock invocation) throws Throwable {
                    Object[] args = invocation.getArguments();
                    ProductData pdata = (ProductData) args[0];

                    return (product.getId().equals(pdata.getId())) ? product : null;
                }
            });
    }

    @Test
    public void testDevPoolCreationAtBindFailNoSkuProduct() throws Exception {
        Owner owner = TestUtil.createOwner("o");
        List<ProductData> devProdDTOs = new ArrayList<ProductData>();
        Product p = TestUtil.createProduct("test-product", "Test Product");
        Product ip = TestUtil.createProduct("test-product-installed", "Installed Test Product");
        devProdDTOs.add(ip.toDTO());

        Pool activePool = TestUtil.createPool(owner, p);
        List<Pool> activeList = new ArrayList<Pool>();
        activeList.add(activePool);

        Consumer devSystem = TestUtil.createConsumer(owner);
        devSystem.setFact("dev_sku", p.getId());
        devSystem.addInstalledProduct(new ConsumerInstalledProduct(ip));

        when(config.getBoolean(eq(ConfigProperties.STANDALONE))).thenReturn(false);
        when(poolCurator.hasActiveEntitlementPools(eq(owner), any(Date.class))).thenReturn(true);
        when(productAdapter.getProductsByIds(any(Owner.class), any(List.class))).thenReturn(devProdDTOs);
        when(ownerProductCurator.getProductById(eq(owner), eq(p.getId()))).thenReturn(p);
        when(ownerProductCurator.getProductById(eq(owner), eq(ip.getId()))).thenReturn(ip);

        mockUpdateProduct(p, owner);
        mockUpdateProduct(ip, owner);
        mockProductImport(owner, p, ip);
        mockContentImport(owner, new Content[] {});

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
    public void testDevPoolCreationAtBindNoFailMissingInstalledProduct() throws Exception {
        Owner owner = TestUtil.createOwner("o");
        List<ProductData> devProdDTOs = new ArrayList<ProductData>();
        Product p = TestUtil.createProduct("test-product", "Test Product");
        Product ip1 = TestUtil.createProduct("test-product-installed-1", "Installed Test Product 1");
        Product ip2 = TestUtil.createProduct("test-product-installed-2", "Installed Test Product 2");
        devProdDTOs.add(p.toDTO());
        devProdDTOs.add(ip1.toDTO());

        Pool activePool = TestUtil.createPool(owner, p);
        List<Pool> activeList = new ArrayList<Pool>();
        activeList.add(activePool);

        Consumer devSystem = TestUtil.createConsumer(owner);
        devSystem.setFact("dev_sku", p.getId());
        devSystem.addInstalledProduct(new ConsumerInstalledProduct(ip1));
        devSystem.addInstalledProduct(new ConsumerInstalledProduct(ip2));

        when(config.getBoolean(eq(ConfigProperties.STANDALONE))).thenReturn(false);
        when(poolCurator.hasActiveEntitlementPools(eq(owner), any(Date.class))).thenReturn(true);
        when(productAdapter.getProductsByIds(any(Owner.class), any(List.class))).thenReturn(devProdDTOs);

        this.mockProducts(owner, p, ip1, ip2);
        this.mockProductImport(owner, p, ip1, ip2);
        this.mockContentImport(owner, Collections.<String, Content>emptyMap());

        Pool expectedPool = entitler.assembleDevPool(devSystem, p.getId());
        when(pm.createPool(any(Pool.class))).thenReturn(expectedPool);
        AutobindData ad = new AutobindData(devSystem);
        entitler.bindByProducts(ad);
    }

    @Test
    public void testCreatedDevPoolAttributes() {
        Owner owner = TestUtil.createOwner("o");
        List<ProductData> devProdDTOs = new ArrayList<ProductData>();
        Product p1 = TestUtil.createProduct("dev-product", "Dev Product");
        p1.setAttribute(Product.Attributes.SUPPORT_LEVEL, "Premium");
        p1.setAttribute("expires_after", "47");
        Product p2 = TestUtil.createProduct("provided-product1", "Provided Product 1");
        Product p3 = TestUtil.createProduct("provided-product2", "Provided Product 2");
        devProdDTOs.add(p1.toDTO());
        devProdDTOs.add(p2.toDTO());
        devProdDTOs.add(p3.toDTO());
        Consumer devSystem = TestUtil.createConsumer(owner);
        devSystem.setFact("dev_sku", p1.getId());
        devSystem.addInstalledProduct(new ConsumerInstalledProduct(p2));
        devSystem.addInstalledProduct(new ConsumerInstalledProduct(p3));
        when(productAdapter.getProductsByIds(eq(owner), any(List.class))).thenReturn(devProdDTOs);

        this.mockProducts(owner, p1, p2, p3);
        this.mockProductImport(owner, p1, p2, p3);
        this.mockContentImport(owner, Collections.<String, Content>emptyMap());

        Pool created = entitler.assembleDevPool(devSystem, devSystem.getFact("dev_sku"));
        Calendar cal = Calendar.getInstance();
        cal.setTime(created.getStartDate());
        cal.add(Calendar.DAY_OF_YEAR, 47);
        assertEquals(created.getEndDate(), cal.getTime());
        assertEquals("true", created.getAttributeValue(Pool.Attributes.DEVELOPMENT_POOL));
        assertEquals(devSystem.getUuid(), created.getAttributeValue(Pool.Attributes.REQUIRES_CONSUMER));
        assertEquals(p1.getId(), created.getProductId());
        assertEquals(2, created.getProvidedProducts().size());
        assertEquals("Premium", created.getProduct().getAttributeValue(Product.Attributes.SUPPORT_LEVEL));
        assertEquals(1L, created.getQuantity().longValue());
    }

    @Test
    public void testCreatedDevSkuWithNoSla() {
        Owner owner = TestUtil.createOwner("o");
        List<ProductData> devProdDTOs = new ArrayList<ProductData>();
        final Product p1 = TestUtil.createProduct("dev-product", "Dev Product");
        devProdDTOs.add(p1.toDTO());
        Consumer devSystem = TestUtil.createConsumer(owner);
        devSystem.setFact("dev_sku", p1.getId());

        when(productAdapter.getProductsByIds(eq(owner), any(List.class))).thenReturn(devProdDTOs);
        mockUpdateProduct(p1, owner);

        this.mockContentImport(owner, Collections.<String, Content>emptyMap());

        when(productManager.importProducts(eq(owner), any(Map.class), any(Map.class)))
            .thenAnswer(new Answer<ImportResult<Product>>() {
                @Override
                public ImportResult<Product> answer(InvocationOnMock invocation) throws Throwable {
                    Object[] args = invocation.getArguments();
                    Map<String, ProductData> productData = (Map<String, ProductData>) args[1];
                    ImportResult<Product> importResult = new ImportResult<Product>();
                    Map<String, Product> output = importResult.getCreatedEntities();

                    // We need to copy the attributes from the product data to the product to
                    // simulate a proper update.
                    for (ProductData pdata : productData.values()) {
                        if (pdata != null) {
                            if (p1.getId().equals(pdata.getId())) {
                                p1.clearAttributes();
                                if (pdata.getAttributes() != null) {
                                    p1.setAttributes(pdata.getAttributes());
                                }

                                output.put(p1.getId(), p1);
                            }
                            else {
                                Product product = new Product(pdata.getId(), pdata.getName());
                                // Do we care about this product? Probably not.
                                output.put(product.getId(), product);
                            }
                        }
                    }

                    return importResult;
                }
            });

        Pool created = entitler.assembleDevPool(devSystem, devSystem.getFact("dev_sku"));
        assertEquals(entitler.DEFAULT_DEV_SLA,
            created.getProduct().getAttributeValue(Product.Attributes.SUPPORT_LEVEL));
    }
}
