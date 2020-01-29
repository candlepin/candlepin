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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyMapOf;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.candlepin.audit.Event;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Consumer;
import org.candlepin.model.Content;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EntitlementCertificateCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.SourceSubscription;
import org.candlepin.service.EntitlementCertServiceAdapter;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;



/**
 * PoolManagerTest
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class EntitlementCertificateGeneratorTest {

    @Mock private ContentAccessManager mockContentAccessManager;
    @Mock private EntitlementCertServiceAdapter mockEntCertAdapter;
    @Mock private EntitlementCertificateCurator mockEntCertCurator;
    @Mock private EntitlementCurator mockEntitlementCurator;
    @Mock private PoolCurator mockPoolCurator;
    @Mock private EventSink mockEventSink;
    @Mock private EventFactory mockEventFactory;
    @Mock private ProductCurator mockProductCurator;
    @Mock private OwnerCurator mockOwnerCurator;

    @Captor private ArgumentCaptor<Map<String, Entitlement>> entMapCaptor;
    @Captor private ArgumentCaptor<Map<String, Product>> productMapCaptor;
    @Captor private ArgumentCaptor<Map<String, PoolQuantity>> poolQuantityMapCaptor;

    private EntitlementCertificateGenerator ecGenerator;

    @BeforeEach
    public void init() throws Exception {
        this.ecGenerator = new EntitlementCertificateGenerator(
            this.mockEntCertCurator, this.mockEntCertAdapter, this.mockEntitlementCurator,
            this.mockPoolCurator, this.mockEventSink, this.mockEventFactory, this.mockProductCurator,
            this.mockContentAccessManager, this.mockOwnerCurator);
    }

    @Test
    public void testGenerateEntitlementCertificate() throws GeneralSecurityException, IOException {
        this.ecGenerator = new EntitlementCertificateGenerator(this.mockEntCertCurator,
                this.mockEntCertAdapter, this.mockEntitlementCurator, this.mockPoolCurator,
                this.mockEventSink, this.mockEventFactory, this.mockProductCurator,
                this.mockContentAccessManager, this.mockOwnerCurator);

        Consumer consumer = mock(Consumer.class);
        Pool pool = mock(Pool.class);
        Product product = mock(Product.class);
        when(pool.getId()).thenReturn("Swift");
        when(pool.getProduct()).thenReturn(product);
        Entitlement entitlement = mock(Entitlement.class);
        when(entitlement.getConsumer()).thenReturn(consumer);
        Map<String, Product> expectedProducts = new HashMap<>();
        expectedProducts.put("Swift", product);
        Map<String, Entitlement> expected = new HashMap<>();
        expected.put("Swift", entitlement);
        Map<String, PoolQuantity> poolQuantityMap = new HashMap<>();
        poolQuantityMap.put("Swift", new PoolQuantity(pool, 0));
        ecGenerator.generateEntitlementCertificate(pool, entitlement);
        verify(mockEntCertAdapter).generateEntitlementCerts(eq(consumer), eq(poolQuantityMap), eq(expected),
            eq(expectedProducts), eq(true));
    }

    @Test
    public void testGenerateEntitlementCertificates() throws GeneralSecurityException, IOException {
        this.ecGenerator = new EntitlementCertificateGenerator(this.mockEntCertCurator,
            this.mockEntCertAdapter, this.mockEntitlementCurator, this.mockPoolCurator,
            this.mockEventSink, this.mockEventFactory, this.mockProductCurator,
                this.mockContentAccessManager, this.mockOwnerCurator);
        Consumer consumer = mock(Consumer.class);
        Product product = mock(Product.class);
        Entitlement entitlement = mock(Entitlement.class);
        Pool pool = mock(Pool.class);
        Map<String, Product> products = new HashMap<>();
        products.put("Taylor", product);
        Map<String, Entitlement> entitlements = new HashMap<>();
        entitlements.put("Taylor", entitlement);
        Map<String, PoolQuantity> poolQuantities = new HashMap<>();
        poolQuantities.put("Taylor", new PoolQuantity(pool, 1));
        ecGenerator.generateEntitlementCertificates(consumer, products, poolQuantities, entitlements, true);
        verify(mockEntCertAdapter).generateEntitlementCerts(eq(consumer), eq(poolQuantities), eq
            (entitlements), eq(products), anyBoolean());
    }

    @Test
    public void testLazyRegenerateForEntitlement() {
        Entitlement entitlement = new Entitlement();
        this.ecGenerator.regenerateCertificatesOf(entitlement, true);
        assertTrue(entitlement.isDirty());
        verifyZeroInteractions(this.mockEntCertAdapter);
    }

    @Test
    public void testLazyRegenerateForEntitlementCollection() {
        List<Entitlement> entitlements = Arrays.asList(
            new Entitlement(), new Entitlement(), new Entitlement()
        );

        this.ecGenerator.regenerateCertificatesOf(entitlements, true);

        for (Entitlement entitlement : entitlements) {
            assertTrue(entitlement.isDirty());
        }

        verifyZeroInteractions(this.mockEntCertAdapter);
    }

    /**
     * Generates some entitlements and the necessary objects for testing entitlement regeneration
     *
     * @return
     *  A list of entitlements
     */
    private List<Entitlement> generateEntitlements() {
        Owner owner = TestUtil.createOwner("test-owner", "Test Owner");

        Content c1 = TestUtil.createContent("c1");
        Content c2 = TestUtil.createContent("c2");
        Content c3 = TestUtil.createContent("c3");

        Product prod1 = TestUtil.createProduct();
        Product prod2 = TestUtil.createProduct();
        Product prod3 = TestUtil.createProduct();
        Product pprod1 = TestUtil.createProduct();
        Product pprod2 = TestUtil.createProduct();
        Product pprod3 = TestUtil.createProduct();

        prod1.setProvidedProducts(Collections.singleton(pprod1));
        prod2.setProvidedProducts(Collections.singleton(pprod2));
        prod3.setProvidedProducts(Collections.singleton(pprod3));

        prod1.addContent(c1, true);
        pprod2.addContent(c2, true);
        prod3.addContent(c3, true);

        Pool pool1 = createPool(owner, prod1, Collections.singleton(pprod1), 1);
        Pool pool2 = createPool(owner, prod2, Collections.singleton(pprod2), 1);
        Pool pool3 = createPool(owner, prod3, Collections.singleton(pprod3), 1);

        Consumer consumer = TestUtil.createConsumer(owner);

        Entitlement ent1 = TestUtil.createEntitlement(owner, consumer, pool1, null);
        Entitlement ent2 = TestUtil.createEntitlement(owner, consumer, pool2, null);
        Entitlement ent3 = TestUtil.createEntitlement(owner, consumer, pool3, null);
        ent1.setId("ent1");
        ent2.setId("ent2");
        ent3.setId("ent3");

        return Arrays.asList(ent1, ent2, ent3);
    }

    private static int lastPoolId = 1;

    /**
     * This method creates pool for testing without in-memory database. The provided
     * products are 'cached' in mocked product curator
     */
    private Pool createPool(Owner owner, Product prod, Set<Product> providedProd, int q) {
        Pool p = TestUtil.createPool(owner, prod, providedProd, q);

        p.setId("" + lastPoolId++);
        System.out.println("Caching providedProducts for Pool:" + p.getId());
        when(mockProductCurator.getPoolProvidedProductsCached(p.getId())).
            thenReturn(providedProd);
        return p;
    }


    @Test
    public void testLazyRegnerateForEnvironmentContent() {
        String environmentId = "env_id_1";
        List<Entitlement> entitlements = this.generateEntitlements();
        CandlepinQuery cqmock = mock(CandlepinQuery.class);
        when(cqmock.iterator()).thenReturn(entitlements.iterator());
        when(this.mockEntitlementCurator.listByEnvironment(environmentId)).thenReturn(cqmock);

        this.ecGenerator.regenerateCertificatesOf(environmentId, Arrays.asList("c1", "c2", "c4"), true);

        assertTrue(entitlements.get(0).isDirty());
        assertTrue(entitlements.get(1).isDirty());
        assertFalse(entitlements.get(2).isDirty());

        verifyZeroInteractions(this.mockEntCertAdapter);
    }

    @Test
    public void testNonLazyRegnerateForEnvironmentContent() throws Exception {
        String environmentId = "env_id_1";
        List<Entitlement> entitlements = this.generateEntitlements();

        HashMap<String, EntitlementCertificate> ecMap = new HashMap<>();
        for (Entitlement entitlement : entitlements) {
            ecMap.put(entitlement.getPool().getId(), new EntitlementCertificate());
        }

        CandlepinQuery<Entitlement> cqmock = mock(CandlepinQuery.class);
        when(cqmock.iterator()).thenReturn(entitlements.iterator());
        when(this.mockEntitlementCurator.listByEnvironment(environmentId)).thenReturn(cqmock);
        when(this.mockEntCertAdapter.generateEntitlementCerts(any(Consumer.class), any(Map.class),
            any(Map.class), any(Map.class), anyBoolean())).thenReturn(ecMap);
        when(mockEventFactory.entitlementChanged(any(Entitlement.class))).thenReturn(mock(Event.class));
        this.ecGenerator.regenerateCertificatesOf(environmentId, Arrays.asList("c1", "c2", "c4"), false);

        assertFalse(entitlements.get(0).isDirty());
        assertFalse(entitlements.get(1).isDirty());
        assertFalse(entitlements.get(2).isDirty());

        verify(this.mockEntCertAdapter, times(2)).generateEntitlementCerts(any(Consumer.class),
            this.poolQuantityMapCaptor.capture(), this.entMapCaptor.capture(), this.productMapCaptor
            .capture(), eq(false));

        verify(this.mockEventSink, times(2)).queueEvent(any(Event.class));
    }

    @Test
    public void testLazyRegenerationForProductById() throws Exception {
        Owner owner = TestUtil.createOwner("test-owner", "Test Owner");
        Consumer consumer = TestUtil.createConsumer(owner);
        Product product = TestUtil.createProduct();
        Pool pool = TestUtil.createPool(owner, product);
        Entitlement entitlement = TestUtil.createEntitlement(owner, consumer, pool, null);
        Set<Entitlement> entitlements = new HashSet<>();
        entitlements.add(entitlement);
        pool.setEntitlements(entitlements);

        when(this.mockPoolCurator.listAvailableEntitlementPools(isNull(), eq(owner),
            eq(product.getId()), any(Date.class))).thenReturn(Arrays.asList(pool));
        when(mockEventFactory.entitlementChanged(any(Entitlement.class))).thenReturn(mock(Event.class));
        this.ecGenerator.regenerateCertificatesOf(owner, product.getId(), true);

        assertTrue(entitlement.isDirty());

        verifyZeroInteractions(this.mockEntCertAdapter);
    }

    @Test
    public void testNonLazyRegenerationForProductById() throws Exception {
        Owner owner = TestUtil.createOwner("test-owner", "Test Owner");
        Consumer consumer = TestUtil.createConsumer(owner);
        Product product = TestUtil.createProduct();
        Pool pool = TestUtil.createPool(owner, product);
        Entitlement entitlement = TestUtil.createEntitlement(owner, consumer, pool, null);
        Set<Entitlement> entitlements = new HashSet<>();
        entitlements.add(entitlement);
        pool.setEntitlements(entitlements);

        HashMap<String, EntitlementCertificate> ecMap = new HashMap<>();
        ecMap.put(pool.getId(), new EntitlementCertificate());

        when(this.mockPoolCurator.listAvailableEntitlementPools(isNull(), eq(owner),
            eq(product.getId()), any(Date.class))).thenReturn(Arrays.asList(pool));
        when(this.mockEntCertAdapter.generateEntitlementCerts(any(Consumer.class), any(Map.class),
            any(Map.class), any(Map.class), anyBoolean())).thenReturn(ecMap);

        when(mockEventFactory.entitlementChanged(any(Entitlement.class))).thenReturn(mock(Event.class));
        this.ecGenerator.regenerateCertificatesOf(owner, product.getId(), false);

        assertFalse(entitlement.isDirty());

        verify(this.mockEntCertAdapter, times(1)).generateEntitlementCerts(any(Consumer.class),
            this.poolQuantityMapCaptor.capture(), this.entMapCaptor.capture(),
            this.productMapCaptor.capture(), eq(false));

        verify(this.mockEventSink, times(1)).queueEvent(any(Event.class));
    }

    @Test
    public void testLazyRegenerateForConsumer() {
        Entitlement entitlement = new Entitlement();
        Consumer consumer = new Consumer();
        Owner owner = new Owner();
        owner.setId("test-owner");
        consumer.setOwner(owner);
        consumer.addEntitlement(entitlement);

        when(mockOwnerCurator.findOwnerById(eq("test-owner"))).thenReturn(owner);
        this.ecGenerator.regenerateCertificatesOf(consumer, true);

        assertTrue(entitlement.isDirty());
        verifyZeroInteractions(this.mockEntCertAdapter);
    }

    @Test
    public void testNonLazyRegenerate() throws Exception {
        Owner owner = TestUtil.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct();

        Pool pool = TestUtil.createPool(owner, product);
        pool.setSourceSubscription(new SourceSubscription("source-sub-id", "master"));

        Map<String, EntitlementCertificate> entCerts = new HashMap<>();
        entCerts.put(pool.getId(), new EntitlementCertificate());

        when(this.mockEntCertAdapter.generateEntitlementCerts(
            any(Consumer.class), anyMapOf(String.class, PoolQuantity.class),
            anyMapOf(String.class, Entitlement.class),
            anyMapOf(String.class, Product.class), anyBoolean())).thenReturn(entCerts);
        when(mockEventFactory.entitlementChanged(any(Entitlement.class))).thenReturn(mock(Event.class));
        Consumer consumer = TestUtil.createConsumer(owner);
        Entitlement entitlement = new Entitlement(pool, consumer, owner, 1);
        entitlement.setDirty(true);

        this.ecGenerator.regenerateCertificatesOf(entitlement, false);
        assertFalse(entitlement.isDirty());

        verify(this.mockEntCertAdapter).generateEntitlementCerts(eq(consumer),
            this.poolQuantityMapCaptor.capture(), this.entMapCaptor.capture(), this.productMapCaptor
            .capture(), eq(false));

        assertEquals(entitlement, this.entMapCaptor.getValue().get(pool.getId()));
        assertEquals(product, this.productMapCaptor.getValue().get(pool.getId()));

        verify(this.mockEventSink, times(1)).queueEvent(any(Event.class));
    }


    @Test
    public void testLazyRegenerationByEntitlementId() throws Exception {
        Owner owner = TestUtil.createOwner("test-owner", "Test Owner");
        Consumer consumer = TestUtil.createConsumer(owner);
        Product product = TestUtil.createProduct();
        Pool pool = TestUtil.createPool(owner, product);
        Entitlement entitlement = TestUtil.createEntitlement(owner, consumer, pool, null);
        entitlement.setId("lazy-ent-id");
        Collection<String> entitlements = Arrays.asList(entitlement.getId());

        this.ecGenerator.regenerateCertificatesByEntitlementIds(entitlements, true);

        // We're expecting the DB to update the state, and since we're mocking all of the curators, we can't
        // do a flush to verify this. We're, instead, relying on the backing DB functionality to be working
        // correctly.
        // assertTrue(entitlement.isDirty());

        verify(this.mockEntitlementCurator, times(1)).markEntitlementsDirty(eq(entitlements));

        verifyZeroInteractions(this.mockEntCertAdapter);
    }

    @Test
    public void testNonLazyRegenerationByEntitlementId() throws Exception {
        Owner owner = TestUtil.createOwner("test-owner", "Test Owner");
        Consumer consumer = TestUtil.createConsumer(owner);
        Product product = TestUtil.createProduct();
        Pool pool = TestUtil.createPool(owner, product);
        Entitlement entitlement = TestUtil.createEntitlement(owner, consumer, pool, null);
        entitlement.setId("test-ent-id");
        Collection<String> entitlements = Arrays.asList(entitlement.getId());
        pool.setEntitlements(new HashSet(Arrays.asList(entitlement)));

        HashMap<String, EntitlementCertificate> ecMap = new HashMap<>();
        ecMap.put(pool.getId(), new EntitlementCertificate());

        when(this.mockEntitlementCurator.get(eq(entitlement.getId()))).thenReturn(entitlement);
        when(this.mockEntCertAdapter.generateEntitlementCerts(any(Consumer.class), any(Map.class),
            any(Map.class), any(Map.class), anyBoolean())).thenReturn(ecMap);
        when(mockEventFactory.entitlementChanged(any(Entitlement.class))).thenReturn(mock(Event.class));
        this.ecGenerator.regenerateCertificatesByEntitlementIds(entitlements, false);

        assertFalse(entitlement.isDirty());

        verify(this.mockEntCertAdapter, times(1)).generateEntitlementCerts(any(Consumer.class),
            this.poolQuantityMapCaptor.capture(), this.entMapCaptor.capture(), this.productMapCaptor
            .capture(), eq(false));

        verify(this.mockEventSink, times(1)).queueEvent(any(Event.class));
    }

}
