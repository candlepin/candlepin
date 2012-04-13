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

import static org.candlepin.test.TestUtil.createIdCert;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Matchers.any;

import java.io.IOException;
import java.io.Serializable;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.auth.NoAuthPrincipal;
import org.candlepin.controller.CandlepinPoolManager;
import org.candlepin.controller.Entitler;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.ActivationKey;
import org.candlepin.model.ActivationKeyCurator;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialDto;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.resource.ConsumerResource;
import org.candlepin.resource.util.ResourceDateParser;
import org.candlepin.service.EntitlementCertServiceAdapter;
import org.candlepin.service.IdentityCertServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

/**
 * ConsumerResourceTest
 */
@RunWith(MockitoJUnitRunner.class)
public class ConsumerResourceTest {

    private I18n i18n;

    @Mock
    private ConsumerCurator mockedConsumerCurator;
    @Mock
    private OwnerCurator mockedOwnerCurator;
    @Mock
    private EntitlementCertServiceAdapter mockedEntitlementCertServiceAdapter;

    @Before
    public void setUp() {
        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
    }

    @Test
    public void testGetCertSerials() {
        Consumer consumer = createConsumer();
        List<EntitlementCertificate> certificates = createEntitlementCertificates();

        when(mockedEntitlementCertServiceAdapter.listForConsumer(consumer))
            .thenReturn(certificates);
        when(mockedConsumerCurator.findByUuid(consumer.getUuid())).thenReturn(
            consumer);

        ConsumerResource consumerResource = new ConsumerResource(
            mockedConsumerCurator, null, null, null, null, null,
            mockedEntitlementCertServiceAdapter, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null, null);

        List<CertificateSerialDto> serials = consumerResource
            .getEntitlementCertificateSerials(consumer.getUuid());

        verifyCertificateSerialNumbers(serials);
    }

    private void verifyCertificateSerialNumbers(
        List<CertificateSerialDto> serials) {
        assertEquals(3, serials.size());
        assertTrue(serials.get(0).getSerial() > 0);
    }

    private List<EntitlementCertificate> createEntitlementCertificates() {
        return Arrays.asList(new EntitlementCertificate[]{
            createEntitlementCertificate("key1", "cert1"),
            createEntitlementCertificate("key2", "cert2"),
            createEntitlementCertificate("key3", "cert3") });
    }


    /**
     * Test just verifies that entitler is called only once and it doesn't need
     * any other object to execute.
     */
    @Test
    public void testRegenerateEntitlementCertificateWithValidConsumer() {
        Consumer consumer = createConsumer();

        when(mockedConsumerCurator.findByUuid(consumer.getUuid())).thenReturn(
            consumer);

        CandlepinPoolManager mgr = mock(CandlepinPoolManager.class);
        ConsumerResource cr = new ConsumerResource(mockedConsumerCurator, null,
            null, null, null, null, null, null, null, null, null, null, null,
            null, mgr, null, null, null, null, null, null, null, null);
        cr.regenerateEntitlementCertificates(consumer.getUuid(), null);
        Mockito.verify(mgr, Mockito.times(1))
            .regenerateEntitlementCertificates(eq(consumer));
    }

    @Test
    public void testRegenerateIdCerts() throws GeneralSecurityException,
        IOException {

        // using lconsumer simply to avoid hiding consumer. This should
        // get renamed once we refactor this test suite.
        IdentityCertServiceAdapter mockedIdSvc = Mockito
            .mock(IdentityCertServiceAdapter.class);

        EventSink sink = Mockito.mock(EventSink.class);
        EventFactory factory = Mockito.mock(EventFactory.class);

        Consumer consumer = createConsumer();
        consumer.setIdCert(createIdCert());
        IdentityCertificate ic = consumer.getIdCert();
        assertNotNull(ic);

        when(mockedConsumerCurator.findByUuid(consumer.getUuid())).thenReturn(
            consumer);
        when(mockedIdSvc.regenerateIdentityCert(consumer)).thenReturn(
            createIdCert());

        ConsumerResource cr = new ConsumerResource(mockedConsumerCurator, null,
            null, null, null, mockedIdSvc, null, null, sink, factory, null, null,
            null, null, null, null, null, null, mockedOwnerCurator, null, null, null,
            null);

        Consumer fooc = cr.regenerateIdentityCertificates(consumer.getUuid());

        assertNotNull(fooc);
        IdentityCertificate ic1 = fooc.getIdCert();
        assertNotNull(ic1);
        assertFalse(ic.equals(ic1));
    }

    @Test(expected = BadRequestException.class)
    public void testCreatePersonConsumerWithActivationKey() {
        Consumer c = mock(Consumer.class);
        Owner o = mock(Owner.class);
        ActivationKey ak = mock(ActivationKey.class);
        NoAuthPrincipal nap = mock(NoAuthPrincipal.class);
        ActivationKeyCurator akc = mock(ActivationKeyCurator.class);
        OwnerCurator oc = mock(OwnerCurator.class);
        ConsumerTypeCurator ctc = mock(ConsumerTypeCurator.class);

        ConsumerType cType = new ConsumerType(ConsumerTypeEnum.PERSON);
        when(ak.getId()).thenReturn("testKey");
        when(o.getKey()).thenReturn("testOwner");
        when(akc.lookupForOwner(eq("testKey"), eq(o))).thenReturn(ak);
        when(oc.lookupByKey(eq("testOwner"))).thenReturn(o);
        when(c.getType()).thenReturn(cType);
        when(c.getName()).thenReturn("testConsumer");
        when(ctc.lookupByLabel(eq("person"))).thenReturn(cType);

        ConsumerResource cr = new ConsumerResource(null, ctc,
            null, null, null, null, null, i18n, null, null, null, null,
            null, null, null, null, null, null, oc, akc, null, null, null);
        cr.create(c, nap, null, "testOwner", "testKey");
    }

    @Test
    public void testProductNoPool() {
        try {
            Consumer c = mock(Consumer.class);
            Owner o = mock(Owner.class);
            SubscriptionServiceAdapter sa = mock(SubscriptionServiceAdapter.class);
            Entitler e = mock(Entitler.class);
            ConsumerCurator cc = mock(ConsumerCurator.class);
            String[] prodIds = {"notthere"};

            when(c.getOwner()).thenReturn(o);
            when(sa.hasUnacceptedSubscriptionTerms(eq(o))).thenReturn(false);
            when(cc.findByUuid(eq("fakeConsumer"))).thenReturn(c);
            when(e.bindByProducts(eq(prodIds), eq(c), eq((Date) null)))
                .thenThrow(new RuntimeException());

            ConsumerResource cr = new ConsumerResource(cc, null,
                null, sa, null, null, null, i18n, null, null, null, null,
                null, null, null, null, null, null, null, null, e, null, null);
            cr.bind("fakeConsumer", null, prodIds, 1, null, null, false, null);
        }
        catch (Throwable t) {
            fail("Runtime exception should be caught in ConsumerResource.bind");
        }
    }

    @Test
    public void futureHealing() {
        Consumer c = mock(Consumer.class);
        Owner o = mock(Owner.class);
        SubscriptionServiceAdapter sa = mock(SubscriptionServiceAdapter.class);
        Entitler e = mock(Entitler.class);
        ConsumerCurator cc = mock(ConsumerCurator.class);
        ConsumerInstalledProduct cip = mock(ConsumerInstalledProduct.class);
        Set<ConsumerInstalledProduct> products = new HashSet<ConsumerInstalledProduct>();
        products.add(cip);

        when(c.getOwner()).thenReturn(o);
        when(cip.getProductId()).thenReturn("product-foo");
        when(sa.hasUnacceptedSubscriptionTerms(eq(o))).thenReturn(false);
        when(cc.findByUuid(eq("fakeConsumer"))).thenReturn(c);

        ConsumerResource cr = new ConsumerResource(cc, null,
            null, sa, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, e, null, null);
        String dtStr = "2011-09-26T18:10:50.184081+00:00";
        Date dt = ResourceDateParser.parseDateString(dtStr);
        cr.bind("fakeConsumer", null, null, 1, null, null, false, dtStr);
        verify(e).bindByProducts(eq((String []) null), eq(c), eq(dt));
    }

    @Test(expected = NotFoundException.class)
    public void unbindByInvalidSerialShouldFail() {
        Consumer consumer = createConsumer();
        ConsumerCurator consumerCurator = mock(ConsumerCurator.class);
        when(consumerCurator.findByUuid(eq("fake uuid"))).thenReturn(consumer);

        EntitlementCurator entitlementCurator = mock(EntitlementCurator.class);
        when(entitlementCurator.find(any(Serializable.class))).thenReturn(null);

        ConsumerResource consumerResource = new ConsumerResource(consumerCurator, null,
            null, null, entitlementCurator, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, null, null, null, null, null);

        consumerResource.unbindBySerial("fake uuid",
            Long.valueOf(1234L));
    }

    @Test(expected = NotFoundException.class)
    public void unbindBySerialWithInvalidUuidShouldFail() {
        ConsumerCurator consumerCurator = mock(ConsumerCurator.class);
        when(consumerCurator.findByUuid(eq("fake uuid"))).thenReturn(null);

        ConsumerResource consumerResource = new ConsumerResource(consumerCurator, null,
            null, null, null, null, null, i18n, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null, null);

        consumerResource.unbindBySerial("fake uuid",
            Long.valueOf(1234L));
    }

    @Test(expected = BadRequestException.class)
    public void testBindMultipleParams() throws Exception {
        ConsumerCurator consumerCurator = mock(ConsumerCurator.class);
        ConsumerResource consumerResource = new ConsumerResource(consumerCurator, null,
            null, null, null, null, null, i18n, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null, null);

        consumerResource.bind("fake uuid", "fake pool uuid",
            new String[]{"12232"}, 1, null, null, false, null);
    }


    @Test(expected = NotFoundException.class)
    public void testBindByPoolBadConsumerUuid() throws Exception {
        ConsumerCurator consumerCurator = mock(ConsumerCurator.class);
        ConsumerResource consumerResource = new ConsumerResource(consumerCurator, null,
            null, null, null, null, null, i18n, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null, null);

        consumerResource.bind("notarealuuid", "fake pool uuid", null, null, null,
            null, false, null);
    }

    /**
     * Basic test. If invalid id is given, should throw
     * {@link NotFoundException}
     */
    @Test(expected = NotFoundException.class)
    public void testRegenerateEntitlementCertificatesWithInvalidConsumerId() {
        ConsumerCurator consumerCurator = mock(ConsumerCurator.class);
        ConsumerResource consumerResource = new ConsumerResource(consumerCurator, null,
            null, null, null, null, null, i18n, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null, null);

        consumerResource.regenerateEntitlementCertificates("xyz", null);
    }

    private Consumer createConsumer() {
        return new Consumer("test-consumer", "test-user", new Owner(
            "Test Owner"), new ConsumerType("test-consumer-type-"));
    }

    protected EntitlementCertificate createEntitlementCertificate(String key,
        String cert) {
        EntitlementCertificate toReturn = new EntitlementCertificate();
        CertificateSerial certSerial = new CertificateSerial(1L, new Date());
        toReturn.setKeyAsBytes(key.getBytes());
        toReturn.setCertAsBytes(cert.getBytes());
        toReturn.setSerial(certSerial);
        return toReturn;
    }
}
