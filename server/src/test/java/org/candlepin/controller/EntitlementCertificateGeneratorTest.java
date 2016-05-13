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
import org.candlepin.model.Consumer;
import org.candlepin.model.Content;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EntitlementCertificateCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.model.SourceSubscription;
import org.candlepin.service.EntitlementCertServiceAdapter;
import org.candlepin.test.TestUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;



/**
 * PoolManagerTest
 */
@RunWith(MockitoJUnitRunner.class)
public class EntitlementCertificateGeneratorTest {

    @Mock private EntitlementCertServiceAdapter mockEntCertAdapter;
    @Mock private EntitlementCertificateCurator mockEntCertCurator;
    @Mock private EntitlementCurator mockEntitlementCurator;
    @Mock private PoolCurator mockPoolCurator;
    @Mock private EventSink mockEventSink;
    @Mock private EventFactory mockEventFactory;

    @Captor private ArgumentCaptor<Map<String, Entitlement>> entMapCaptor;
    @Captor private ArgumentCaptor<Map<String, Product>> productMapCaptor;

    private EntitlementCertificateGenerator ecGenerator;

    @Before
    public void init() throws Exception {
        this.ecGenerator = new EntitlementCertificateGenerator(
            this.mockEntCertCurator, this.mockEntCertAdapter, this.mockEntitlementCurator,
            this.mockPoolCurator, this.mockEventSink, this.mockEventFactory
        );
    }

    @Test
    public void testGenerateEntitlementCertificate() throws GeneralSecurityException, IOException {
        this.ecGenerator = new EntitlementCertificateGenerator(this.mockEntCertCurator,
                this.mockEntCertAdapter, this.mockEntitlementCurator, this.mockPoolCurator,
                this.mockEventSink, this.mockEventFactory);
        Consumer consumer = mock(Consumer.class);
        Pool pool = mock(Pool.class);
        Product product = mock(Product.class);
        when(pool.getId()).thenReturn("Swift");
        when(pool.getProduct()).thenReturn(product);
        Entitlement entitlement = mock(Entitlement.class);
        when(entitlement.getConsumer()).thenReturn(consumer);
        EntitlementCertificate entitlementCert = mock(EntitlementCertificate.class);
        Map<String, EntitlementCertificate> expected = new HashMap<String, EntitlementCertificate>();
        expected.put("Swift", entitlementCert);
        when(mockEntCertAdapter.generateUeberCerts(eq(consumer), entMapCaptor.capture(),
                        productMapCaptor.capture())).thenReturn(expected);
        EntitlementCertificate result = ecGenerator.generateEntitlementCertificate(pool, entitlement, true);
        assertEquals(entitlementCert, result);
    }

    @Test
    public void testGenerateEntitlementCertificatesUber() throws GeneralSecurityException, IOException {
        this.ecGenerator = new EntitlementCertificateGenerator(this.mockEntCertCurator,
                this.mockEntCertAdapter, this.mockEntitlementCurator, this.mockPoolCurator,
                this.mockEventSink, this.mockEventFactory);
        Consumer consumer = mock(Consumer.class);
        Product product = mock(Product.class);
        Entitlement entitlement = mock(Entitlement.class);
        Map<String, Product> products = new HashMap<String, Product>();
        products.put("Taylor", product);
        Map<String, Entitlement> entitlements = new HashMap<String, Entitlement>();
        entitlements.put("Taylor", entitlement);
        ecGenerator.generateEntitlementCertificates(consumer, products, entitlements, true);
        verify(mockEntCertAdapter).generateUeberCerts(eq(consumer), eq(entitlements), eq(products));
    }

    @Test
    public void testGenerateEntitlementCertificates() throws GeneralSecurityException, IOException {
        this.ecGenerator = new EntitlementCertificateGenerator(this.mockEntCertCurator,
            this.mockEntCertAdapter, this.mockEntitlementCurator, this.mockPoolCurator,
            this.mockEventSink, this.mockEventFactory);
        Consumer consumer = mock(Consumer.class);
        Product product = mock(Product.class);
        Entitlement entitlement = mock(Entitlement.class);
        Map<String, Product> products = new HashMap<String, Product>();
        products.put("Taylor", product);
        Map<String, Entitlement> entitlements = new HashMap<String, Entitlement>();
        entitlements.put("Taylor", entitlement);
        ecGenerator.generateEntitlementCertificates(consumer, products, entitlements, false);
        verify(mockEntCertAdapter).generateEntitlementCerts(eq(consumer), eq(entitlements), eq(products));
    }

    @Test
    public void testLazyRegenerateForEntitlement() {
        Entitlement entitlement = new Entitlement();
        this.ecGenerator.regenerateCertificatesOf(entitlement, false, true);
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

        Content c1 = TestUtil.createContent(owner, "c1");
        Content c2 = TestUtil.createContent(owner, "c2");
        Content c3 = TestUtil.createContent(owner, "c3");

        Product prod1 = TestUtil.createProduct(owner);
        Product prod2 = TestUtil.createProduct(owner);
        Product prod3 = TestUtil.createProduct(owner);
        Product pprod1 = TestUtil.createProduct(owner);
        Product pprod2 = TestUtil.createProduct(owner);
        Product pprod3 = TestUtil.createProduct(owner);

        prod1.addContent(c1);
        pprod2.addContent(c2);
        prod3.addContent(c3);

        Pool pool1 = TestUtil.createPool(owner, prod1, Arrays.asList(pprod1), 1);
        Pool pool2 = TestUtil.createPool(owner, prod2, Arrays.asList(pprod2), 1);
        Pool pool3 = TestUtil.createPool(owner, prod3, Arrays.asList(pprod3), 1);

        Consumer consumer = TestUtil.createConsumer(owner);

        Entitlement ent1 = TestUtil.createEntitlement(owner, consumer, pool1, null);
        Entitlement ent2 = TestUtil.createEntitlement(owner, consumer, pool2, null);
        Entitlement ent3 = TestUtil.createEntitlement(owner, consumer, pool3, null);
        ent1.setId("ent1");
        ent2.setId("ent2");
        ent3.setId("ent3");

        return Arrays.asList(ent1, ent2, ent3);
    }

    @Test
    public void testLazyRegnerateForEnvironmentContent() {
        Environment environment = new Environment();
        List<Entitlement> entitlements = this.generateEntitlements();
        when(this.mockEntitlementCurator.listByEnvironment(environment)).thenReturn(entitlements);

        this.ecGenerator.regenerateCertificatesOf(environment, Arrays.asList("c1", "c2", "c4"), true);

        assertTrue(entitlements.get(0).isDirty());
        assertTrue(entitlements.get(1).isDirty());
        assertFalse(entitlements.get(2).isDirty());

        verifyZeroInteractions(this.mockEntCertAdapter);
    }

    @Test
    public void testNonLazyRegnerateForEnvironmentContent() throws Exception {
        Environment environment = new Environment();
        List<Entitlement> entitlements = this.generateEntitlements();

        HashMap<String, EntitlementCertificate> ecMap = new HashMap<String, EntitlementCertificate>();
        for (Entitlement entitlement : entitlements) {
            ecMap.put(entitlement.getPool().getId(), new EntitlementCertificate());
        }

        when(this.mockEntitlementCurator.listByEnvironment(environment)).thenReturn(entitlements);
        when(this.mockEntCertAdapter.generateEntitlementCerts(any(Consumer.class), any(Map.class),
            any(Map.class))).thenReturn(ecMap);

        this.ecGenerator.regenerateCertificatesOf(environment, Arrays.asList("c1", "c2", "c4"), false);

        assertFalse(entitlements.get(0).isDirty());
        assertFalse(entitlements.get(1).isDirty());
        assertFalse(entitlements.get(2).isDirty());

        verify(this.mockEntCertAdapter, times(2)).generateEntitlementCerts(any(Consumer.class),
            this.entMapCaptor.capture(), this.productMapCaptor.capture());

        verify(this.mockEventSink, times(2)).queueEvent(any(Event.class));
    }

    @Test
    public void testLazyRegenerationForProductById() throws Exception {
        Owner owner = TestUtil.createOwner("test-owner", "Test Owner");
        Consumer consumer = TestUtil.createConsumer(owner);
        Product product = TestUtil.createProduct(owner);
        Pool pool = TestUtil.createPool(owner, product);
        Entitlement entitlement = TestUtil.createEntitlement(owner, consumer, pool, null);
        Set<Entitlement> entitlements = new HashSet<Entitlement>();
        entitlements.add(entitlement);
        pool.setEntitlements(entitlements);

        when(this.mockPoolCurator.listAvailableEntitlementPools(any(Consumer.class), eq(owner),
            eq(product.getId()), any(Date.class), anyBoolean())).thenReturn(Arrays.asList(pool));

        this.ecGenerator.regenerateCertificatesOf(owner, product.getId(), true);

        assertTrue(entitlement.isDirty());

        verifyZeroInteractions(this.mockEntCertAdapter);
    }

    @Test
    public void testNonLazyRegenerationForProductById() throws Exception {
        Owner owner = TestUtil.createOwner("test-owner", "Test Owner");
        Consumer consumer = TestUtil.createConsumer(owner);
        Product product = TestUtil.createProduct(owner);
        Pool pool = TestUtil.createPool(owner, product);
        Entitlement entitlement = TestUtil.createEntitlement(owner, consumer, pool, null);
        Set<Entitlement> entitlements = new HashSet<Entitlement>();
        entitlements.add(entitlement);
        pool.setEntitlements(entitlements);

        HashMap<String, EntitlementCertificate> ecMap = new HashMap<String, EntitlementCertificate>();
        ecMap.put(pool.getId(), new EntitlementCertificate());

        when(this.mockPoolCurator.listAvailableEntitlementPools(any(Consumer.class), eq(owner),
            eq(product.getId()), any(Date.class), anyBoolean())).thenReturn(Arrays.asList(pool));
        when(this.mockEntCertAdapter.generateEntitlementCerts(any(Consumer.class), any(Map.class),
            any(Map.class))).thenReturn(ecMap);

        this.ecGenerator.regenerateCertificatesOf(owner, product.getId(), false);

        assertFalse(entitlement.isDirty());

        verify(this.mockEntCertAdapter, times(1)).generateEntitlementCerts(any(Consumer.class),
            this.entMapCaptor.capture(), this.productMapCaptor.capture());

        verify(this.mockEventSink, times(1)).queueEvent(any(Event.class));
    }

    @Test
    public void testLazyRegenerateForConsumer() {
        Entitlement entitlement = new Entitlement();
        Consumer consumer = new Consumer();
        consumer.addEntitlement(entitlement);

        this.ecGenerator.regenerateCertificatesOf(consumer, true);

        assertTrue(entitlement.isDirty());
        verifyZeroInteractions(this.mockEntCertAdapter);
    }

    @Test
    public void testNonLazyRegenerate() throws Exception {
        Owner owner = TestUtil.createOwner("test-owner", "Test Owner");
        Product product = TestUtil.createProduct(owner);

        Pool pool = TestUtil.createPool(owner, product);
        pool.setSourceSubscription(new SourceSubscription("source-sub-id", "master"));

        Map<String, EntitlementCertificate> entCerts = new HashMap<String, EntitlementCertificate>();
        entCerts.put(pool.getId(), new EntitlementCertificate());

        when(this.mockEntCertAdapter.generateEntitlementCerts(
            any(Consumer.class), anyMapOf(String.class, Entitlement.class),
            anyMapOf(String.class, Product.class))).thenReturn(entCerts);

        Consumer consumer = TestUtil.createConsumer(owner);
        Entitlement entitlement = new Entitlement(pool, consumer, 1);
        entitlement.setDirty(true);

        this.ecGenerator.regenerateCertificatesOf(entitlement, false, false);
        assertFalse(entitlement.isDirty());

        verify(this.mockEntCertAdapter).generateEntitlementCerts(eq(consumer),
            this.entMapCaptor.capture(), this.productMapCaptor.capture());

        assertEquals(entitlement, this.entMapCaptor.getValue().get(pool.getId()));
        assertEquals(product, this.productMapCaptor.getValue().get(pool.getId()));

        verify(this.mockEventSink, times(1)).queueEvent(any(Event.class));
    }


    @Test
    public void testLazyRegenerationByEntitlementId() throws Exception {
        Owner owner = TestUtil.createOwner("test-owner", "Test Owner");
        Consumer consumer = TestUtil.createConsumer(owner);
        Product product = TestUtil.createProduct(owner);
        Pool pool = TestUtil.createPool(owner, product);
        Entitlement entitlement = TestUtil.createEntitlement(owner, consumer, pool, null);
        entitlement.setId("lazy-ent-id");
        Collection<String> entitlements = Arrays.asList(entitlement.getId());

        this.ecGenerator.regenerateCertificatesByEntitlementIds(entitlements, false, true);

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
        Product product = TestUtil.createProduct(owner);
        Pool pool = TestUtil.createPool(owner, product);
        Entitlement entitlement = TestUtil.createEntitlement(owner, consumer, pool, null);
        entitlement.setId("test-ent-id");
        Collection<String> entitlements = Arrays.asList(entitlement.getId());
        pool.setEntitlements(new HashSet(Arrays.asList(entitlement)));

        HashMap<String, EntitlementCertificate> ecMap = new HashMap<String, EntitlementCertificate>();
        ecMap.put(pool.getId(), new EntitlementCertificate());

        when(this.mockEntitlementCurator.find(eq(entitlement.getId()))).thenReturn(entitlement);
        when(this.mockEntCertAdapter.generateEntitlementCerts(any(Consumer.class), any(Map.class),
            any(Map.class))).thenReturn(ecMap);

        this.ecGenerator.regenerateCertificatesByEntitlementIds(entitlements, false, false);

        assertFalse(entitlement.isDirty());

        verify(this.mockEntCertAdapter, times(1)).generateEntitlementCerts(any(Consumer.class),
            this.entMapCaptor.capture(), this.productMapCaptor.capture());

        verify(this.mockEventSink, times(1)).queueEvent(any(Event.class));
    }




}
