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

import static org.candlepin.test.TestUtil.createIdCert;
import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.candlepin.audit.Event.Target;
import org.candlepin.audit.Event.Type;
import org.candlepin.audit.EventBuilder;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.audit.EventSinkImpl;
import org.candlepin.auth.Access;
import org.candlepin.auth.NoAuthPrincipal;
import org.candlepin.auth.SubResource;
import org.candlepin.auth.TrustedUserPrincipal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.controller.CandlepinPoolManager;
import org.candlepin.controller.Entitler;
import org.candlepin.controller.ManifestManager;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.Cdn;
import org.candlepin.model.CdnCurator;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.CertificateSerialDto;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerContentOverrideCurator;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.GuestId;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.VirtConsumerMap;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.model.dto.PoolIdAndQuantity;
import org.candlepin.model.dto.Subscription;
import org.candlepin.policy.js.activationkey.ActivationKeyRules;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.resource.dto.AutobindData;
import org.candlepin.resource.util.ConsumerBindUtil;
import org.candlepin.resource.util.ResourceDateParser;
import org.candlepin.resteasy.parameter.KeyValueParameter;
import org.candlepin.service.EntitlementCertServiceAdapter;
import org.candlepin.service.IdentityCertServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.test.TestUtil;
import org.candlepin.util.ServiceLevelValidator;

import org.hibernate.mapping.Collection;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.quartz.JobDetail;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.Response;

/**
 * ConsumerResourceTest
 */
@RunWith(MockitoJUnitRunner.class)
public class ConsumerResourceTest {

    private I18n i18n;

    @Mock private ConsumerCurator mockedConsumerCurator;
    @Mock private OwnerCurator mockedOwnerCurator;
    @Mock private EntitlementCertServiceAdapter mockedEntitlementCertServiceAdapter;
    @Mock private SubscriptionServiceAdapter mockedSubscriptionServiceAdapter;
    @Mock private PoolManager mockedPoolManager;
    @Mock private EntitlementCurator mockedEntitlementCurator;
    @Mock private ComplianceRules mockedComplianceRules;
    @Mock private ServiceLevelValidator mockedServiceLevelValidator;
    @Mock private ActivationKeyRules mockedActivationKeyRules;
    @Mock private EventFactory eventFactory;
    @Mock private EventBuilder eventBuilder;
    @Mock private ConsumerBindUtil consumerBindUtil;
    @Mock private ProductCurator productCurator;

    @Before
    public void setUp() {
        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        when(eventBuilder.setOldEntity(any(Consumer.class))).thenReturn(eventBuilder);
        when(eventBuilder.setNewEntity(any(Consumer.class))).thenReturn(eventBuilder);
        when(eventFactory.getEventBuilder(any(Target.class), any(Type.class))).thenReturn(eventBuilder);
    }

    @Test
    public void testGetCertSerials() {
        Consumer consumer = createConsumer();
        List<EntitlementCertificate> certificates = createEntitlementCertificates();

        when(mockedEntitlementCertServiceAdapter.listForConsumer(consumer)) .thenReturn(certificates);
        when(mockedConsumerCurator.verifyAndLookupConsumer(consumer.getUuid())).thenReturn(consumer);
        when(mockedEntitlementCurator.listByConsumer(consumer)).thenReturn(new ArrayList<Entitlement>());

        ConsumerResource consumerResource = new ConsumerResource(
            mockedConsumerCurator, null, null, null, mockedEntitlementCurator, null,
            mockedEntitlementCertServiceAdapter, null, null, null, null, null,
            null, mockedPoolManager, null, null, null, null, null,
            null, null, null, new CandlepinCommonTestConfig(), null, null, null,
            consumerBindUtil, productCurator, null);

        List<CertificateSerialDto> serials = consumerResource
            .getEntitlementCertificateSerials(consumer.getUuid());

        verifyCertificateSerialNumbers(serials);
    }

    @Test (expected = RuntimeException.class)
    public void testExceptionFromCertGen() throws Exception {
        Consumer consumer = createConsumer();

        Entitlement e = Mockito.mock(Entitlement.class);
        Pool p = Mockito.mock(Pool.class);
        Subscription s = Mockito.mock(Subscription.class);
        when(e.getPool()).thenReturn(p);
        when(p.getSubscriptionId()).thenReturn("4444");

        when(mockedConsumerCurator.verifyAndLookupConsumer(consumer.getUuid())).thenReturn(consumer);
        when(mockedEntitlementCurator.find(eq("9999"))).thenReturn(e);
        when(mockedSubscriptionServiceAdapter.getSubscription(eq("4444"))).thenReturn(s);

        when(mockedEntitlementCertServiceAdapter.generateEntitlementCert(
            any(Entitlement.class), any(Product.class)))
            .thenThrow(new IOException());

        CandlepinPoolManager poolManager = new CandlepinPoolManager(
            null, null, null, new CandlepinCommonTestConfig(), null, null, mockedEntitlementCurator,
            mockedConsumerCurator, null, null, null, null, mockedActivationKeyRules, null, null,
            null, null, null, null, null, null
        );

        ConsumerResource consumerResource = new ConsumerResource(
            mockedConsumerCurator, null, null, null, mockedEntitlementCurator, null,
            mockedEntitlementCertServiceAdapter, null, null, null, null, null, null,
            poolManager, null, null, null, null, null, null, null, null,
            new CandlepinCommonTestConfig(), null, null, null, consumerBindUtil, productCurator, null);
        consumerResource.regenerateEntitlementCertificates(consumer.getUuid(), "9999", false);
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

        when(mockedConsumerCurator.verifyAndLookupConsumer(consumer.getUuid())).thenReturn(consumer);

        CandlepinPoolManager mgr = mock(CandlepinPoolManager.class);
        ConsumerResource cr = new ConsumerResource(mockedConsumerCurator, null,
            null, mockedSubscriptionServiceAdapter, null, null, null, null, null, null, null, null, null,
            mgr, null, null, null, null, null, null, null, null,
            new CandlepinCommonTestConfig(), null, null, null, consumerBindUtil, productCurator, null);

        cr.regenerateEntitlementCertificates(consumer.getUuid(), null, true);
        Mockito.verify(mgr, Mockito.times(1)).regenerateCertificatesOf(eq(consumer), eq(true));
    }

    @Test
    public void testRegenerateIdCerts() throws GeneralSecurityException,
        IOException {

        // using lconsumer simply to avoid hiding consumer. This should
        // get renamed once we refactor this test suite.
        IdentityCertServiceAdapter mockedIdSvc = Mockito
            .mock(IdentityCertServiceAdapter.class);

        EventSink sink = Mockito.mock(EventSinkImpl.class);

        Consumer consumer = createConsumer();
        consumer.setIdCert(createIdCert());
        IdentityCertificate ic = consumer.getIdCert();
        assertNotNull(ic);

        when(mockedConsumerCurator.verifyAndLookupConsumer(consumer.getUuid())).thenReturn(consumer);
        when(mockedIdSvc.regenerateIdentityCert(consumer)).thenReturn(createIdCert());

        ConsumerResource cr = new ConsumerResource(mockedConsumerCurator, null,
            null, null, null, mockedIdSvc, null, null, sink, eventFactory, null, null,
            null, null, null, mockedOwnerCurator, null, null, null, null,
            null, null, new CandlepinCommonTestConfig(), null, null, null, consumerBindUtil, productCurator,
            null);

        Consumer fooc = cr.regenerateIdentityCertificates(consumer.getUuid());

        assertNotNull(fooc);
        IdentityCertificate ic1 = fooc.getIdCert();
        assertNotNull(ic1);
        assertFalse(ic.equals(ic1));
    }

    @Test
    public void testIdCertGetsRegenerated() throws Exception {
        // using lconsumer simply to avoid hiding consumer. This should
        // get renamed once we refactor this test suite.
        IdentityCertServiceAdapter mockedIdSvc = Mockito.mock(IdentityCertServiceAdapter.class);

        EventSink sink = Mockito.mock(EventSinkImpl.class);

        SubscriptionServiceAdapter ssa = Mockito.mock(SubscriptionServiceAdapter.class);
        ComplianceRules rules = Mockito.mock(ComplianceRules.class);

        Consumer consumer = createConsumer();
        ComplianceStatus status = new ComplianceStatus();
        when(rules.getStatus(any(Consumer.class), any(Date.class), anyBoolean())).thenReturn(status);
        // cert expires today which will trigger regen
        consumer.setIdCert(createIdCert());
        BigInteger origserial = consumer.getIdCert().getSerial().getSerial();

        when(mockedConsumerCurator.verifyAndLookupConsumer(consumer.getUuid())).thenReturn(consumer);
        when(mockedIdSvc.regenerateIdentityCert(consumer)).thenReturn(createIdCert());

        ConsumerResource cr = new ConsumerResource(mockedConsumerCurator, null,
            null, ssa, null, mockedIdSvc, null, null, sink, eventFactory, null, null,
            null, null, null, mockedOwnerCurator, null, null, rules, null,
            null, null, new CandlepinCommonTestConfig(), null, null, null, consumerBindUtil, productCurator,
            null);
        Consumer c = cr.getConsumer(consumer.getUuid());

        assertFalse(origserial.equals(c.getIdCert().getSerial().getSerial()));
    }

    @Test
    public void testIdCertDoesNotRegenerate() throws Exception {
        SubscriptionServiceAdapter ssa = Mockito.mock(SubscriptionServiceAdapter.class);
        ComplianceRules rules = Mockito.mock(ComplianceRules.class);

        Consumer consumer = createConsumer();
        ComplianceStatus status = new ComplianceStatus();
        when(rules.getStatus(any(Consumer.class), any(Date.class), anyBoolean())).thenReturn(status);
        consumer.setIdCert(createIdCert(TestUtil.createDate(2025, 6, 9)));
        BigInteger origserial = consumer.getIdCert().getSerial().getSerial();

        when(mockedConsumerCurator.verifyAndLookupConsumer(consumer.getUuid())).thenReturn(consumer);

        ConsumerResource cr = new ConsumerResource(mockedConsumerCurator, null,
            null, ssa, null, null, null, null, null, null, null, null, null, null,
            null, mockedOwnerCurator, null, null, rules, null, null, null,
            new CandlepinCommonTestConfig(), null, null, null, consumerBindUtil, productCurator, null);

        Consumer c = cr.getConsumer(consumer.getUuid());

        assertEquals(origserial, c.getIdCert().getSerial().getSerial());
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
        ConsumerContentOverrideCurator ccoc = mock(ConsumerContentOverrideCurator.class);

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
            null, null, null, oc, akc, null, null, null, null,
            null, new CandlepinCommonTestConfig(), null, null, null, consumerBindUtil, productCurator, null);
        cr.create(c, nap, null, "testOwner", "testKey", true);
    }

    @Test
    public void testProductNoPool() throws Exception {
        Consumer c = mock(Consumer.class);
        Owner o = mock(Owner.class);
        SubscriptionServiceAdapter sa = mock(SubscriptionServiceAdapter.class);
        Entitler e = mock(Entitler.class);
        ConsumerCurator cc = mock(ConsumerCurator.class);
        String[] prodIds = {"notthere"};

        when(c.getOwner()).thenReturn(o);
        when(sa.hasUnacceptedSubscriptionTerms(eq(o))).thenReturn(false);
        when(cc.verifyAndLookupConsumerWithEntitlements(eq("fakeConsumer"))).thenReturn(c);
        when(e.bindByProducts(any(AutobindData.class))).thenReturn(null);

        ConsumerResource cr = new ConsumerResource(cc, null,
            null, sa, null, null, null, i18n, null, null, null, null, null,
            null, null, null, null, e, null, null, null, null,
            new CandlepinCommonTestConfig(), null, null, null, consumerBindUtil, productCurator, null);
        Response r = cr.bind(
            "fakeConsumer", null, prodIds, null, null, null, false, null, null, null, null);
        assertEquals(null, r.getEntity());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBindByPools() throws Exception {
        PoolIdAndQuantity[] pools = new PoolIdAndQuantity[2];
        pools[0] = new PoolIdAndQuantity("first", 1);
        pools[1] = new PoolIdAndQuantity("second", 2);
        ConsumerCurator cc = mock(ConsumerCurator.class);
        SubscriptionServiceAdapter sa = mock(SubscriptionServiceAdapter.class);
        PoolManager pm = mock(PoolManager.class);
        Owner owner = TestUtil.createOwner();
        Consumer consumer = TestUtil.createConsumer(owner);

        when(cc.verifyAndLookupConsumerWithEntitlements(eq("fakeConsumer"))).thenReturn(consumer);
        when(sa.hasUnacceptedSubscriptionTerms(any(Owner.class))).thenReturn(false);

        ConsumerResource cr = new ConsumerResource(cc, null, null, sa, null, null, null, i18n, null,
            null, null, null, null, pm, null, null, null, null, null, null, null, null,
            new CandlepinCommonTestConfig(), null, null, null, consumerBindUtil, productCurator, null);

        Response rsp = cr.bind("fakeConsumer", null, null, null, null, null, true, null,
            null, pools, new TrustedUserPrincipal("TaylorSwift"));

        JobDetail detail = (JobDetail) rsp.getEntity();
        PoolIdAndQuantity[] pQs = (PoolIdAndQuantity[]) detail.getJobDataMap().get("pool_and_quantities");
        boolean firstFound = false;
        boolean secondFound = false;
        for (PoolIdAndQuantity pq : pQs) {
            if (pq.getPoolId().contentEquals("first")) {
                firstFound = true;
                assertEquals(1, pq.getQuantity().intValue());
            }
            if (pq.getPoolId().contentEquals("second")) {
                secondFound = true;
                assertEquals(2, pq.getQuantity().intValue());
            }
        }
        assertTrue(firstFound);
        assertTrue(secondFound);
    }

    @Test
    public void futureHealing() throws Exception {
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
        when(cc.verifyAndLookupConsumerWithEntitlements(eq("fakeConsumer"))).thenReturn(c);

        ConsumerResource cr = new ConsumerResource(cc, null, null, sa,
            null, null, null, null, null, null, null, null, null, null,
            null, null, null, e, null, null, null, null,
            new CandlepinCommonTestConfig(), null, null, null, consumerBindUtil, productCurator, null);

        String dtStr = "2011-09-26T18:10:50.184081+00:00";
        Date dt = ResourceDateParser.parseDateString(dtStr);
        cr.bind("fakeConsumer", null, null, null, null, null, false, dtStr, null, null, null);
        AutobindData data = AutobindData.create(c).on(dt);
        verify(e).bindByProducts(eq(data));
    }

    @Test(expected = NotFoundException.class)
    public void unbindByInvalidSerialShouldFail() {
        Consumer consumer = createConsumer();
        ConsumerCurator consumerCurator = mock(ConsumerCurator.class);
        when(consumerCurator.verifyAndLookupConsumer(eq("fake uuid"))).thenReturn(consumer);

        EntitlementCurator entitlementCurator = mock(EntitlementCurator.class);
        when(entitlementCurator.find(any(Serializable.class))).thenReturn(null);

        ConsumerResource consumerResource = new ConsumerResource(consumerCurator, null,
            null, null, entitlementCurator, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, new CandlepinCommonTestConfig(), null, null, null, consumerBindUtil, productCurator, null);

        consumerResource.unbindBySerial("fake uuid",
            Long.valueOf(1234L));
    }

    /**
     * Basic test. If invalid id is given, should throw
     * {@link NotFoundException}
     */
    @Test(expected = NotFoundException.class)
    public void unbindByInvalidPoolIdShouldFail() {
        Consumer consumer = createConsumer();
        ConsumerCurator consumerCurator = mock(ConsumerCurator.class);
        when(consumerCurator.verifyAndLookupConsumer(eq("fake-uuid"))).thenReturn(consumer);
        EntitlementCurator entitlementCurator = mock(EntitlementCurator.class);

        when(entitlementCurator.listByConsumerAndPoolId(eq(consumer), any(String.class)))
            .thenReturn(new ArrayList<Entitlement>());

        ConsumerResource consumerResource = new ConsumerResource(consumerCurator, null,
            null, null, entitlementCurator, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, new CandlepinCommonTestConfig(), null, null, null, consumerBindUtil, productCurator, null);
        consumerResource.unbindByPool("fake-uuid", "Run Forest!");
    }

    @Test(expected = BadRequestException.class)
    public void testBindMultipleParams() throws Exception {
        ConsumerCurator consumerCurator = mock(ConsumerCurator.class);
        ConsumerResource consumerResource = new ConsumerResource(consumerCurator, null,
            null, null, null, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, new CandlepinCommonTestConfig(), null, null, null, consumerBindUtil, productCurator, null);

        consumerResource.bind("fake uuid", "fake pool uuid",
            new String[]{"12232"}, 1, null, null, false, null, null, null, null);
    }

    @Test(expected = BadRequestException.class)
    public void testBindMultipleParamsBodyAndProducts() throws Exception {
        ConsumerCurator consumerCurator = mock(ConsumerCurator.class);
        ConsumerResource consumerResource = new ConsumerResource(consumerCurator, null,
            null, null, null, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, new CandlepinCommonTestConfig(), null, null, null, consumerBindUtil, productCurator, null);

        PoolIdAndQuantity[] pools = new PoolIdAndQuantity[2];
        pools[0] = new PoolIdAndQuantity("first", 1);
        pools[1] = new PoolIdAndQuantity("second", 2);

        consumerResource.bind("fake uuid", null,
            new String[]{"12232"}, null, null, null, false, null, null, pools, null);
    }

    @Test(expected = BadRequestException.class)
    public void testBindMultipleParamsBodyAndPoolString() throws Exception {
        ConsumerCurator consumerCurator = mock(ConsumerCurator.class);
        ConsumerResource consumerResource = new ConsumerResource(consumerCurator, null,
            null, null, null, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, new CandlepinCommonTestConfig(), null, null, null, consumerBindUtil, productCurator, null);

        PoolIdAndQuantity[] pools = new PoolIdAndQuantity[2];
        pools[0] = new PoolIdAndQuantity("first", 1);
        pools[1] = new PoolIdAndQuantity("second", 2);

        consumerResource.bind("fake uuid", "assad",
            null, null, null, null, false, null, null, pools, null);
    }

    @Test(expected = BadRequestException.class)
    public void testBindMultipleParamsBodyAndAsync() throws Exception {
        ConsumerCurator consumerCurator = mock(ConsumerCurator.class);
        ConsumerResource consumerResource = new ConsumerResource(consumerCurator, null,
            null, null, null, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, new CandlepinCommonTestConfig(), null, null, null, consumerBindUtil, productCurator, null);

        PoolIdAndQuantity[] pools = new PoolIdAndQuantity[2];
        pools[0] = new PoolIdAndQuantity("first", 1);
        pools[1] = new PoolIdAndQuantity("second", 2);

        consumerResource.bind("fake uuid", null,
            null, null, null, null, false, null, null, pools, null);
    }

    @Test(expected = BadRequestException.class)
    public void testBindMultipleParamsBodyAndQuantity() throws Exception {
        ConsumerCurator consumerCurator = mock(ConsumerCurator.class);
        ConsumerResource consumerResource = new ConsumerResource(consumerCurator, null,
            null, null, null, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, new CandlepinCommonTestConfig(), null, null, null, consumerBindUtil,  productCurator, null);

        PoolIdAndQuantity[] pools = new PoolIdAndQuantity[2];
        pools[0] = new PoolIdAndQuantity("first", 1);
        pools[1] = new PoolIdAndQuantity("second", 2);

        consumerResource.bind("fake uuid", null,
            null, 1, null, null, false, null, null, pools, null);
    }

    @Test(expected = NotFoundException.class)
    public void testBindByPoolBadConsumerUuid() throws Exception {
        ConsumerCurator consumerCurator = mock(ConsumerCurator.class);
        when(consumerCurator.verifyAndLookupConsumerWithEntitlements(any(String.class)))
            .thenThrow(new NotFoundException(""));
        ConsumerResource consumerResource = new ConsumerResource(consumerCurator, null,
            null, null, null, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, new CandlepinCommonTestConfig(), null, null, null, consumerBindUtil, productCurator, null);

        consumerResource.bind("notarealuuid", "fake pool uuid", null, null, null,
            null, false, null, null, null, null);
    }

    /**
     * Basic test. If invalid id is given, should throw
     * {@link NotFoundException}
     */
    @Test(expected = NotFoundException.class)
    public void testRegenerateEntitlementCertificatesWithInvalidConsumerId() {
        ConsumerCurator consumerCurator = mock(ConsumerCurator.class);
        when(consumerCurator.verifyAndLookupConsumer(any(String.class)))
            .thenThrow(new NotFoundException(""));
        ConsumerResource consumerResource = new ConsumerResource(consumerCurator, null,
            null, null, null, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, new CandlepinCommonTestConfig(), null, null, null, consumerBindUtil, productCurator, null);

        consumerResource.regenerateEntitlementCertificates("xyz", null, true);
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

    @Test(expected = NotFoundException.class)
    public void testNullPerson() {
        Consumer c = mock(Consumer.class);
        Owner o = mock(Owner.class);
        UserServiceAdapter usa = mock(UserServiceAdapter.class);
        UserPrincipal up = mock(UserPrincipal.class);
        OwnerCurator oc = mock(OwnerCurator.class);
        ConsumerTypeCurator ctc = mock(ConsumerTypeCurator.class);
        ConsumerType cType = new ConsumerType(ConsumerTypeEnum.PERSON);

        when(o.getKey()).thenReturn("testOwner");
        when(oc.lookupByKey(eq("testOwner"))).thenReturn(o);
        when(c.getType()).thenReturn(cType);
        when(c.getName()).thenReturn("testConsumer");
        when(ctc.lookupByLabel(eq("person"))).thenReturn(cType);
        when(up.canAccess(eq(o), eq(SubResource.CONSUMERS), eq(Access.CREATE))).
            thenReturn(true);
        // usa.findByLogin() will return null by default no need for a when

        ConsumerResource cr = new ConsumerResource(null, ctc,
            null, null, null, null, null, i18n, null, null, null, null,
            usa, null,  null, oc, null, null, null, null, null,
            null, new CandlepinCommonTestConfig(), null, null, null, consumerBindUtil, productCurator, null);
        cr.create(c, up, null, "testOwner", null, true);
    }

    @Test
    public void testGetComplianceStatusList() {
        Consumer c = mock(Consumer.class);
        Consumer c2 = mock(Consumer.class);
        when(c.getUuid()).thenReturn("1");
        when(c2.getUuid()).thenReturn("2");

        List<Consumer> consumers = new ArrayList<Consumer>();
        consumers.add(c);
        consumers.add(c2);

        List<String> uuids = new ArrayList<String>();
        uuids.add("1");
        uuids.add("2");
        when(mockedConsumerCurator.findByUuids(eq(uuids))).thenReturn(consumers);

        ComplianceStatus status = new ComplianceStatus();
        when(mockedComplianceRules.getStatus(any(Consumer.class), any(Date.class)))
            .thenReturn(status);

        ConsumerResource cr = new ConsumerResource(mockedConsumerCurator, null,
            null, null, null, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, mockedComplianceRules,
            null, null, null, new CandlepinCommonTestConfig(), null, null, null,
            consumerBindUtil, productCurator, null);

        Map<String, ComplianceStatus> results = cr.getComplianceStatusList(uuids);
        assertEquals(2, results.size());
        assertTrue(results.containsKey("1"));
        assertTrue(results.containsKey("2"));
    }

    @Test
    public void testConsumerExistsYes() {
        when(mockedConsumerCurator.doesConsumerExist(any(String.class))).thenReturn(true);
        ConsumerResource cr = new ConsumerResource(mockedConsumerCurator, null,
            null, null, null, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, mockedComplianceRules,
            null, null, null, new CandlepinCommonTestConfig(),
            null, null, null, consumerBindUtil, productCurator, null);
        cr.consumerExists("uuid");
    }

    @Test (expected = NotFoundException.class)
    public void testConsumerExistsNo() {
        when(mockedConsumerCurator.doesConsumerExist(any(String.class))).thenReturn(false);
        ConsumerResource cr = new ConsumerResource(mockedConsumerCurator, null,
            null, null, null, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, mockedComplianceRules,
            null, null, null, new CandlepinCommonTestConfig(),
            null, null, null, consumerBindUtil, productCurator, null);
        cr.consumerExists("uuid");
    }

    @Test(expected = BadRequestException.class)
    public void testFetchAllConsumers() {
        ConsumerResource cr = new ConsumerResource(null, null,
            null, null, null, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, null,
            null, null, null, new CandlepinCommonTestConfig(),
            null, null, null, null, productCurator, null);
        cr.list(null, null, null, null, null, null, null);
    }

    @Test
    public void testFetchAllConsumersForUser() {
        ConsumerResource cr = new ConsumerResource(mockedConsumerCurator, null,
            null, null, null, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, null,
            null, null, null, new CandlepinCommonTestConfig(),
            null, null, null, null,  productCurator, null);

        Page<List<Consumer>> page = new Page<List<Consumer>>();
        ArrayList<Consumer> consumers = new ArrayList<Consumer>();
        page.setPageData(consumers);
        when(mockedConsumerCurator.searchOwnerConsumers(
            any(Owner.class), anyString(), (java.util.Collection<ConsumerType>) any(Collection.class),
            any(List.class), any(List.class), any(List.class), any(List.class), any(List.class),
            any(List.class), any(PageRequest.class))).thenReturn(page);

        List<Consumer> result = cr.list("TaylorSwift", null, null, null, null, null, null);
        assertEquals(consumers, result);
    }

    public void testFetchAllConsumersForOwner() {
        ConsumerResource cr = new ConsumerResource(mockedConsumerCurator, null,
            null, null, null, null, null, i18n, null, null, null,
            null, null, null, null, mockedOwnerCurator, null, null, null,
            null, null, null, new CandlepinCommonTestConfig(),
            null, null, null, null, productCurator, null);

        Page<List<Consumer>> page = new Page<List<Consumer>>();
        ArrayList<Consumer> consumers = new ArrayList<Consumer>();
        page.setPageData(consumers);

        when(mockedOwnerCurator.lookupByKey(eq("taylorOwner"))).thenReturn(new Owner());
        when(mockedConsumerCurator.searchOwnerConsumers(
            any(Owner.class), anyString(), (java.util.Collection<ConsumerType>) any(Collection.class),
            any(List.class), any(List.class), any(List.class), any(List.class), any(List.class),
            any(List.class), any(PageRequest.class))).thenReturn(page);
        List<Consumer> result = cr.list(null, null, "taylorOwner", null, null, null, null);
        assertEquals(consumers, result);
    }

    @Test(expected = BadRequestException.class)
    public void testFetchAllConsumersForEmptyUUIDs() {
        ConsumerResource cr = new ConsumerResource(mockedConsumerCurator, null,
            null, null, null, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, null,
            null, null, null, new CandlepinCommonTestConfig(),
            null, null, null, null, productCurator, null);
        cr.list(null, null, null, new ArrayList<String>(), null, null, null);
    }

    @Test
    public void testFetchAllConsumersForSomeUUIDs() {
        ConsumerResource cr = new ConsumerResource(mockedConsumerCurator, null,
            null, null, null, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, null,
            null, null, null, new CandlepinCommonTestConfig(),
            null, null, null, null, productCurator, null);

        Page<List<Consumer>> page = new Page<List<Consumer>>();
        ArrayList<Consumer> consumers = new ArrayList<Consumer>();
        page.setPageData(consumers);
        when(mockedConsumerCurator.searchOwnerConsumers(
                any(Owner.class), anyString(), (java.util.Collection<ConsumerType>) any(Collection.class),
                any(List.class), any(List.class), any(List.class),
                any(List.class), any(List.class), any(List.class),
                any(PageRequest.class))).thenReturn(page);
        List<String> uuids = new ArrayList<String>();
        uuids.add("swiftuuid");
        List<Consumer> result = cr.list(null, null, null, uuids, null, null, null);
        assertEquals(consumers, result);
    }

    @Test
    public void testcheckForGuestsMigration() {
        ConsumerResource cr = Mockito.spy(new ConsumerResource(mockedConsumerCurator, null,
            null, null, null, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, null,
            null, null, null, new CandlepinCommonTestConfig(),
            null, null, null, null, productCurator, null));

        List<GuestId> startGuests  = new ArrayList<GuestId>();
        List<GuestId> updatedGuests  = new ArrayList<GuestId>();
        VirtConsumerMap guestConsumerMap = new VirtConsumerMap();
        Owner o = mock(Owner.class);
        Consumer host = new Consumer();

        Consumer cOne = new Consumer();
        cOne.setFact("virt.is_guest", "true");
        cOne.setFact("virt.uuid", cOne.getUuid() + "-vuuid");
        cOne.setOwner(o);
        GuestId one = new GuestId(cOne.getFact("virt.uuid"));
        startGuests.add(one);
        guestConsumerMap.add(cOne.getFact("virt.uuid"), cOne);

        Consumer cTwo = new Consumer();
        cTwo.setFact("virt.is_guest", "true");
        cTwo.setFact("virt.uuid", cTwo.getUuid() + "-vuuid");
        cTwo.setOwner(o);
        GuestId two = new GuestId(cTwo.getFact("virt.uuid"));
        startGuests.add(two);
        updatedGuests.add(two);
        guestConsumerMap.add(cTwo.getFact("virt.uuid"), cTwo);

        Consumer cThree = new Consumer();
        cThree.setFact("virt.is_guest", "true");
        cThree.setFact("virt.uuid", cThree.getUuid() + "-vuuid");
        cThree.setOwner(o);
        GuestId three = new GuestId(cThree.getFact("virt.uuid"));
        updatedGuests.add(three);
        guestConsumerMap.add(cThree.getFact("virt.uuid"), cThree);

        cr.checkForGuestsMigration(host, startGuests, updatedGuests, guestConsumerMap);
        verify(cr).checkForGuestMigration(host, cOne);
        verify(cr).checkForGuestMigration(host, cTwo);
        verify(cr).checkForGuestMigration(host, cThree);
    }

    @Test
    public void testAsyncExport() {
        CdnCurator mockedCdnCurator = mock(CdnCurator.class);
        ManifestManager manifestManager = mock(ManifestManager.class);
        ConsumerResource cr = new ConsumerResource(mockedConsumerCurator, null,
            null, null, null, null, null, i18n, null, null, null,
            null, null, null, null, mockedOwnerCurator, null, null, null,
            null, null, null, new CandlepinCommonTestConfig(),
            null, mockedCdnCurator, null, null,  productCurator, manifestManager);

        List<KeyValueParameter> extParams = new ArrayList<KeyValueParameter>();
        Owner owner = TestUtil.createOwner();
        Consumer consumer = TestUtil.createConsumer(
            new ConsumerType(ConsumerType.ConsumerTypeEnum.CANDLEPIN), owner);
        Cdn cdn = new Cdn("cdn-label", "test", "url");

        when(mockedConsumerCurator.verifyAndLookupConsumer(eq(consumer.getUuid()))).thenReturn(consumer);
        when(mockedCdnCurator.lookupByLabel(eq(cdn.getLabel()))).thenReturn(cdn);

        cr.exportDataAsync(null, consumer.getUuid(), cdn.getLabel(), "prefix", cdn.getUrl(), extParams);
        verify(manifestManager).generateManifestAsync(eq(consumer.getUuid()), eq(cdn.getLabel()),
            eq("prefix"), eq(cdn.getUrl()), any(Map.class));
    }

    public void testNoDryBindWhenAutobindDisabledForOwner() throws Exception {
        Consumer consumer = createConsumer();
        consumer.getOwner().setAutobindDisabled(true);
        ConsumerCurator consumerCurator = mock(ConsumerCurator.class);
        when(consumerCurator.verifyAndLookupConsumer(eq(consumer.getUuid()))).thenReturn(consumer);

        ConsumerResource consumerResource = new ConsumerResource(consumerCurator, null,
            null, null, null, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, new CandlepinCommonTestConfig(), null, null, null, consumerBindUtil, productCurator, null);

        try {
            consumerResource.dryBind(consumer.getUuid(), "some-sla");
            fail("Should have thrown a BadRequestException.");
        }
        catch (BadRequestException e) {
            assertEquals("Owner has autobind disabled.", e.getMessage());
        }
    }

}
