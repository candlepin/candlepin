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

import static org.candlepin.test.TestUtil.*;
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
import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.controller.CandlepinPoolManager;
import org.candlepin.controller.Entitler;
import org.candlepin.controller.ManifestManager;
import org.candlepin.controller.OwnerManager;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Cdn;
import org.candlepin.model.CdnCurator;
import org.candlepin.model.Certificate;
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
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.model.dto.PoolIdAndQuantity;
import org.candlepin.model.dto.Subscription;
import org.candlepin.policy.js.activationkey.ActivationKeyRules;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.resource.dto.AutobindData;
import org.candlepin.resource.util.ConsumerBindUtil;
import org.candlepin.resource.util.ConsumerEnricher;
import org.candlepin.resource.util.ConsumerTypeValidator;
import org.candlepin.resource.util.ResourceDateParser;
import org.candlepin.resteasy.parameter.KeyValueParameter;
import org.candlepin.service.EntitlementCertServiceAdapter;
import org.candlepin.service.IdentityCertServiceAdapter;
import org.candlepin.service.OwnerServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.test.TestUtil;
import org.candlepin.util.FactValidator;
import org.candlepin.util.ServiceLevelValidator;

import org.apache.commons.lang.RandomStringUtils;
import org.hibernate.mapping.Collection;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
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

    @SuppressWarnings("checkstyle:visibilitymodifier")
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private I18n i18n;
    private Configuration config;
    private FactValidator factValidator;

    @Mock private ConsumerCurator mockedConsumerCurator;
    @Mock private OwnerCurator mockedOwnerCurator;
    @Mock private EntitlementCertServiceAdapter mockedEntitlementCertServiceAdapter;
    @Mock private OwnerServiceAdapter mockedOwnerServiceAdapter;
    @Mock private SubscriptionServiceAdapter mockedSubscriptionServiceAdapter;
    @Mock private PoolManager mockedPoolManager;
    @Mock private EntitlementCurator mockedEntitlementCurator;
    @Mock private ComplianceRules mockedComplianceRules;
    @Mock private ServiceLevelValidator mockedServiceLevelValidator;
    @Mock private ActivationKeyRules mockedActivationKeyRules;
    @Mock private EventFactory eventFactory;
    @Mock private EventBuilder eventBuilder;
    @Mock private ConsumerBindUtil consumerBindUtil;
    @Mock private OwnerManager mockOwnerManager;
    @Mock private ConsumerEnricher consumerEnricher;
    @Mock private ConsumerTypeCurator mockConsumerTypeCurator;

    @Before
    public void setUp() {
        this.config = new CandlepinCommonTestConfig();

        this.i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        when(eventBuilder.setOldEntity(any(Consumer.class))).thenReturn(eventBuilder);
        when(eventBuilder.setNewEntity(any(Consumer.class))).thenReturn(eventBuilder);
        when(eventFactory.getEventBuilder(any(Target.class), any(Type.class))).thenReturn(eventBuilder);

        this.factValidator = new FactValidator(this.config, this.i18n);
    }

    @Test
    public void testValidateShareConsumerRequiresRecipientFact() {
        ConsumerType share = new ConsumerType(ConsumerTypeEnum.SHARE);
        Consumer c = new Consumer("test-consumer", "test-user", new Owner(
            "Test Owner"), share);

        ConsumerResource consumerResource = new ConsumerResource(
            mockedConsumerCurator, mockConsumerTypeCurator, null, null, null, mockedEntitlementCurator, null,
            mockedEntitlementCertServiceAdapter, i18n, null, null, null, null,
            null, mockedPoolManager, null, mockedOwnerCurator, null, null, null,
            null, null, null, new CandlepinCommonTestConfig(), null, null, null,
            consumerBindUtil, null, null, factValidator, null, consumerEnricher);

        UserPrincipal uap = mock(UserPrincipal.class);
        when(uap.canAccess(any(Object.class), any(SubResource.class), any(Access.class))).thenReturn
            (Boolean.TRUE);

        Owner o = mock(Owner.class);
        when(mockedOwnerCurator.lookupByKey(any(String.class))).thenReturn(o);
        when(mockConsumerTypeCurator.lookupByLabel(any(String.class))).thenReturn(share);

        c.setFact("foo", "bar");

        thrown.expect(BadRequestException.class);
        thrown.expectMessage("must specify a recipient org");
        consumerResource.create(c, uap, "test-user", "test-owner", null, false);
    }

    @Test
    public void testValidateShareConsumerRequiresRecipientPermissions() {
        ConsumerType share = new ConsumerType(ConsumerTypeEnum.SHARE);
        Consumer c = new Consumer("test-consumer", "test-user", new Owner(
            "Test Owner"), share);

        ConsumerResource consumerResource = new ConsumerResource(
            mockedConsumerCurator, mockConsumerTypeCurator, null, null, null, mockedEntitlementCurator, null,
            mockedEntitlementCertServiceAdapter, i18n, null, null, null, null,
            null, mockedPoolManager, null, mockedOwnerCurator, null, null, null,
            null, null, null, new CandlepinCommonTestConfig(), null, null, null,
            consumerBindUtil, null, null, factValidator, null, consumerEnricher);

        UserPrincipal uap = mock(UserPrincipal.class);
        when(uap.canAccess(any(Object.class), any(SubResource.class), any(Access.class))).thenReturn
            (Boolean.TRUE);

        Owner o = mock(Owner.class);
        when(mockedOwnerCurator.lookupByKey(any(String.class))).thenReturn(o);
        when(mockConsumerTypeCurator.lookupByLabel(any(String.class))).thenReturn(share);

        Owner o2 = mock(Owner.class);
        c.setRecipientOwnerKey("o2");
        when(mockedOwnerCurator.lookupByKey(eq("o2"))).thenReturn(o2);

        when(uap.canAccess(eq(o2), eq(SubResource.ENTITLEMENTS), eq(Access.CREATE))).thenReturn(Boolean
            .FALSE);
        thrown.expect(NotFoundException.class);
        thrown.expectMessage("owner with key");
        consumerResource.create(c, uap, "test-user", "test-owner", null, false);
    }

    @Test
    public void testGetCertSerials() {
        Consumer consumer = createConsumer();
        List<EntitlementCertificate> certificates = createEntitlementCertificates();
        List<Long> serialIds = new ArrayList<Long>();
        for (EntitlementCertificate ec : certificates) {
            serialIds.add(ec.getSerial().getId());
        }

        when(mockedEntitlementCertServiceAdapter.listEntitlementSerialIds(consumer)).thenReturn(serialIds);
        when(mockedConsumerCurator.verifyAndLookupConsumer(consumer.getUuid())).thenReturn(consumer);
        when(mockedEntitlementCurator.listByConsumer(consumer)).thenReturn(new ArrayList<Entitlement>());

        ConsumerResource consumerResource = new ConsumerResource(
            mockedConsumerCurator, null, null, null, null, mockedEntitlementCurator, null,
            mockedEntitlementCertServiceAdapter, null, null, null, null, null, null, mockedPoolManager, null,
            null, null, null, null, null, null, null, this.config, null, null, null, consumerBindUtil,
            null, null, this.factValidator, null, consumerEnricher);

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
            null, null, null, this.config, null, null, mockedEntitlementCurator,
            mockedConsumerCurator, null, null, null, null, mockedActivationKeyRules, null, null,
            null, null, null, null, null, null, null
        );

        ConsumerResource consumerResource = new ConsumerResource(
            mockedConsumerCurator, null, null, null, null, mockedEntitlementCurator, null,
            mockedEntitlementCertServiceAdapter, null, null, null, null, null, null,
            poolManager, null, null, null, null, null, null, null, null,
            this.config, null, null, null, consumerBindUtil,
            null, null, this.factValidator, null, consumerEnricher);

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
            null, mockedSubscriptionServiceAdapter, this.mockedOwnerServiceAdapter, null, null, null, null,
            null, null, null, null, null, mgr, null, null, null, null, null, null, null, null,
            this.config, null, null, null, consumerBindUtil, null, null, this.factValidator,
            null, consumerEnricher);

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

        ConsumerResource cr = new ConsumerResource(mockedConsumerCurator, null, null,
            null, null, null, mockedIdSvc, null, null, sink, eventFactory, null, null,
            null, null, null, mockedOwnerCurator, null, null, null, null,
            null, null, this.config, null, null, null, consumerBindUtil,
            null, null, this.factValidator, null, consumerEnricher);

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
            null, ssa, this.mockedOwnerServiceAdapter, null, mockedIdSvc, null, null, sink, eventFactory,
            null, null, null, null, null, mockedOwnerCurator, null, null, rules, null,
            null, null, this.config, null, null, null, consumerBindUtil,
            null, null, this.factValidator, null, consumerEnricher);

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
            null, ssa, this.mockedOwnerServiceAdapter, null, null, null, null, null, null, null, null, null,
            null, null, mockedOwnerCurator, null, null, rules, null, null, null,
            this.config, null, null, null, consumerBindUtil,
            null, null, this.factValidator, null, consumerEnricher);

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

        ConsumerResource cr = new ConsumerResource(null, ctc, null,
            null, null, null, null, null, i18n, null, null, null, null,
            null, null, null, oc, akc, null, null, null, null,
            null, this.config, null, null, null, consumerBindUtil,
            null, null, this.factValidator, null, consumerEnricher);

        cr.create(c, nap, null, "testOwner", "testKey", true);
    }

    @Test
    public void testProductNoPool() throws Exception {
        Consumer c = createConsumer();
        SubscriptionServiceAdapter sa = mock(SubscriptionServiceAdapter.class);
        Entitler e = mock(Entitler.class);
        ConsumerCurator cc = mock(ConsumerCurator.class);
        String[] prodIds = {"notthere"};

        when(sa.hasUnacceptedSubscriptionTerms(eq(c.getOwner()))).thenReturn(false);
        when(cc.verifyAndLookupConsumerWithEntitlements(eq("fakeConsumer"))).thenReturn(c);
        when(e.bindByProducts(any(AutobindData.class))).thenReturn(null);

        ConsumerResource cr = new ConsumerResource(cc, null,
            null, sa, this.mockedOwnerServiceAdapter, null, null, null, i18n, null, null, null, null, null,
            null, null, null, null, e, null, null, null, null,
            this.config, null, null, null, consumerBindUtil,
            null, null, this.factValidator, null, consumerEnricher);


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

        ConsumerResource cr = new ConsumerResource(cc, null, null, sa, this.mockedOwnerServiceAdapter, null,
            null, null, i18n, null, null, null, null, null, pm, null, null, null, null, null, null, null,
            null, this.config, null, null, null, consumerBindUtil, null, null, this.factValidator,
            null, consumerEnricher);

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
        Consumer c = createConsumer();
        SubscriptionServiceAdapter sa = mock(SubscriptionServiceAdapter.class);
        Entitler e = mock(Entitler.class);
        ConsumerCurator cc = mock(ConsumerCurator.class);
        ConsumerInstalledProduct cip = mock(ConsumerInstalledProduct.class);
        Set<ConsumerInstalledProduct> products = new HashSet<ConsumerInstalledProduct>();
        products.add(cip);

        when(cip.getProductId()).thenReturn("product-foo");
        when(sa.hasUnacceptedSubscriptionTerms(eq(c.getOwner()))).thenReturn(false);
        when(cc.verifyAndLookupConsumerWithEntitlements(eq(c.getUuid()))).thenReturn(c);

        ConsumerResource cr = new ConsumerResource(cc, null, null, sa, this.mockedOwnerServiceAdapter,
            null, null, null, null, null, null, null, null, null, null,
            null, null, null, e, null, null, null, null,
            this.config, null, null, null, consumerBindUtil,
            null, null, this.factValidator, null, consumerEnricher);

        String dtStr = "2011-09-26T18:10:50.184081+00:00";
        Date dt = ResourceDateParser.parseDateString(dtStr);

        cr.bind(c.getUuid(), null, null, null, null, null, false, dtStr, null, null, null);

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
            null, null, null, entitlementCurator, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, this.config, null, null, null, consumerBindUtil,
            null, null, this.factValidator, null, consumerEnricher);

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
            null, null, null, entitlementCurator, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, this.config, null, null, null, consumerBindUtil,
            null, null, this.factValidator, null, consumerEnricher);

        consumerResource.unbindByPool("fake-uuid", "Run Forest!");
    }

    @Test(expected = BadRequestException.class)
    public void testBindMultipleParams() throws Exception {
        ConsumerCurator consumerCurator = mock(ConsumerCurator.class);
        ConsumerResource consumerResource = new ConsumerResource(consumerCurator, null,
            null, null, null, null, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, this.config, null, null, null, consumerBindUtil,
            null, null, this.factValidator, null, consumerEnricher);

        Consumer c = createConsumer();
        when(consumerCurator.verifyAndLookupConsumerWithEntitlements(eq(c.getUuid()))).thenReturn(c);
        consumerResource.bind(c.getUuid(), "fake pool uuid",
            new String[]{"12232"}, 1, null, null, false, null, null, null, null);
    }

    @Test(expected = BadRequestException.class)
    public void testBindMultipleParamsBodyAndProducts() throws Exception {
        ConsumerCurator consumerCurator = mock(ConsumerCurator.class);
        ConsumerResource consumerResource = new ConsumerResource(consumerCurator, null,
            null, null, null, null, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, this.config, null, null, null, consumerBindUtil,
            null, null, this.factValidator, null, consumerEnricher);

        PoolIdAndQuantity[] pools = new PoolIdAndQuantity[2];
        pools[0] = new PoolIdAndQuantity("first", 1);
        pools[1] = new PoolIdAndQuantity("second", 2);

        Consumer c = createConsumer();
        when(consumerCurator.verifyAndLookupConsumerWithEntitlements(eq(c.getUuid()))).thenReturn(c);
        consumerResource.bind(c.getUuid(), null,
            new String[]{"12232"}, null, null, null, false, null, null, pools, null);
    }

    @Test(expected = BadRequestException.class)
    public void testBindMultipleParamsBodyAndPoolString() throws Exception {
        ConsumerCurator consumerCurator = mock(ConsumerCurator.class);
        ConsumerResource consumerResource = new ConsumerResource(consumerCurator, null,
            null, null, null, null, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, this.config, null, null, null, consumerBindUtil,
            null, null, this.factValidator, null, consumerEnricher);

        PoolIdAndQuantity[] pools = new PoolIdAndQuantity[2];
        pools[0] = new PoolIdAndQuantity("first", 1);
        pools[1] = new PoolIdAndQuantity("second", 2);

        Consumer c = createConsumer();
        when(consumerCurator.verifyAndLookupConsumerWithEntitlements(eq(c.getUuid()))).thenReturn(c);
        consumerResource.bind(c.getUuid(), "assad",
            null, null, null, null, false, null, null, pools, null);
    }

    @Test(expected = BadRequestException.class)
    public void testBindMultipleParamsBodyAndAsync() throws Exception {
        ConsumerCurator consumerCurator = mock(ConsumerCurator.class);
        ConsumerResource consumerResource = new ConsumerResource(consumerCurator, null,
            null, null, null, null, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, this.config, null, null, null, consumerBindUtil,
            null, null, this.factValidator, null, consumerEnricher);

        PoolIdAndQuantity[] pools = new PoolIdAndQuantity[2];
        pools[0] = new PoolIdAndQuantity("first", 1);
        pools[1] = new PoolIdAndQuantity("second", 2);
        Consumer c = createConsumer();
        when(consumerCurator.verifyAndLookupConsumerWithEntitlements(eq(c.getUuid()))).thenReturn(c);
        consumerResource.bind(c.getUuid(), null,
            null, null, null, null, false, null, null, pools, null);
    }

    @Test(expected = BadRequestException.class)
    public void testBindMultipleParamsBodyAndQuantity() throws Exception {
        ConsumerCurator consumerCurator = mock(ConsumerCurator.class);
        ConsumerResource consumerResource = new ConsumerResource(consumerCurator, null,
            null, null, null, null, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, this.config, null, null, null, consumerBindUtil,
            null, null, this.factValidator, null, consumerEnricher);

        PoolIdAndQuantity[] pools = new PoolIdAndQuantity[2];
        pools[0] = new PoolIdAndQuantity("first", 1);
        pools[1] = new PoolIdAndQuantity("second", 2);

        Consumer c = createConsumer();
        when(consumerCurator.verifyAndLookupConsumerWithEntitlements(eq(c.getUuid()))).thenReturn(c);
        consumerResource.bind(c.getUuid(), null,
            null, 1, null, null, false, null, null, pools, null);
    }

    @Test(expected = NotFoundException.class)
    public void testBindByPoolBadConsumerUuid() throws Exception {
        ConsumerCurator consumerCurator = mock(ConsumerCurator.class);
        when(consumerCurator.verifyAndLookupConsumerWithEntitlements(any(String.class)))
            .thenThrow(new NotFoundException(""));
        ConsumerResource consumerResource = new ConsumerResource(consumerCurator, null,
            null, null, null, null, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, this.config, null, null, null, consumerBindUtil,
            null, null, this.factValidator, null, consumerEnricher);

        Consumer c = createConsumer();
        when(consumerCurator.verifyAndLookupConsumerWithEntitlements(eq(c.getUuid()))).thenReturn(c);
        consumerResource.bind(c.getUuid(), "fake pool uuid", null, null, null,
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
            null, null, null, null, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, this.config, null, null, null, consumerBindUtil,
            null, null, this.factValidator, null, consumerEnricher);

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

        ConsumerResource cr = new ConsumerResource(null, ctc, null,
            null, null, null, null, null, i18n, null, null, null, null,
            usa, null,  null, oc, null, null, null, null, null,
            null, this.config, null, null, null, consumerBindUtil,
            null, null, this.factValidator, null, consumerEnricher);

        cr.create(c, up, null, "testOwner", null, true);
    }

    @Test
    public void testCreateConsumerShouldFailOnMaxLengthOfName() {
        thrown.expect(BadRequestException.class);
        thrown.expectMessage(String.format("Name of the consumer " +
            "should be shorter than %d characters.", Consumer.MAX_LENGTH_OF_CONSUMER_NAME + 1));

        Consumer c = mock(Consumer.class);
        Owner o = mock(Owner.class);
        UserPrincipal up = mock(UserPrincipal.class);
        OwnerCurator oc = mock(OwnerCurator.class);
        ConsumerType cType = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        ConsumerResource consumerResource = createConsumerResource(oc);

        String ownerKey = "testOwner";
        when(oc.lookupByKey(eq(ownerKey))).thenReturn(o);
        when(o.getKey()).thenReturn(ownerKey);
        when(c.getType()).thenReturn(cType);
        String s = RandomStringUtils.randomAlphanumeric(Consumer.MAX_LENGTH_OF_CONSUMER_NAME + 1);
        when(c.getName()).thenReturn(s);
        when(up.canAccess(eq(o), eq(SubResource.CONSUMERS), eq(Access.CREATE))).
            thenReturn(true);

        consumerResource.create(c, up, null, ownerKey, null, false);
    }

    ConsumerResource createConsumerResource(OwnerCurator oc) {
        ConsumerResource consumerResource = new ConsumerResource(
            null, null, null, null, null, null, null, null, i18n, null, null, null, null, null, null, null,
            oc, null, null, null, null, null, null, this.config, null, null, null, null, null, null,
            this.factValidator, null, consumerEnricher);

        return consumerResource;
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

        CandlepinQuery cqmock = mock(CandlepinQuery.class);
        when(cqmock.list()).thenReturn(consumers);
        when(cqmock.iterator()).thenReturn(consumers.iterator());

        List<String> uuids = new ArrayList<String>();
        uuids.add("1");
        uuids.add("2");
        when(mockedConsumerCurator.findByUuids(eq(uuids))).thenReturn(cqmock);

        ComplianceStatus status = new ComplianceStatus();
        when(mockedComplianceRules.getStatus(any(Consumer.class), any(Date.class)))
            .thenReturn(status);

        ConsumerResource cr = new ConsumerResource(mockedConsumerCurator, null, null, null, null, null, null,
            null, i18n, null, null, null, null, null, null, null, null, null, null, mockedComplianceRules,
            null, null, null, this.config, null, null, null, consumerBindUtil, null, null,
            this.factValidator, null, consumerEnricher);

        Map<String, ComplianceStatus> results = cr.getComplianceStatusList(uuids);
        assertEquals(2, results.size());
        assertTrue(results.containsKey("1"));
        assertTrue(results.containsKey("2"));
    }

    @Test
    public void testConsumerExistsYes() {
        when(mockedConsumerCurator.doesConsumerExist(any(String.class))).thenReturn(true);
        ConsumerResource cr = new ConsumerResource(mockedConsumerCurator, null,
            null, null, null, null, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, mockedComplianceRules,
            null, null, null, this.config, null, null, null, consumerBindUtil,
            null, null, this.factValidator, null, consumerEnricher);

        cr.consumerExists("uuid");
    }

    @Test (expected = NotFoundException.class)
    public void testConsumerExistsNo() {
        when(mockedConsumerCurator.doesConsumerExist(any(String.class))).thenReturn(false);
        ConsumerResource cr = new ConsumerResource(mockedConsumerCurator, null, null, null, null, null, null,
            null, i18n, null, null, null, null, null, null, null, null, null, null, mockedComplianceRules,
            null, null, null, this.config, null, null, null, consumerBindUtil, null, null,
            this.factValidator, null, consumerEnricher);

        cr.consumerExists("uuid");
    }

    @Test(expected = BadRequestException.class)
    public void testFetchAllConsumers() {
        ConsumerResource cr = new ConsumerResource(
            null, null, null, null, null, null, null, null, i18n, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, this.config, null, null, null, null, null,
            null, this.factValidator, null, consumerEnricher);

        cr.list(null, null, null, null, null, null, null);
    }

    @Test
    public void testFetchAllConsumersForUser() {
        ConsumerResource cr = new ConsumerResource(
            mockedConsumerCurator, null, null, null, null, null, null, null, i18n, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null, this.config, null, null, null, null,
            null, null, this.factValidator, new ConsumerTypeValidator(null, null),
            consumerEnricher);

        ArrayList<Consumer> consumers = new ArrayList<Consumer>();

        CandlepinQuery cqmock = mock(CandlepinQuery.class);
        when(cqmock.list()).thenReturn(consumers);
        when(cqmock.iterator()).thenReturn(consumers.iterator());
        when(mockedConsumerCurator.searchOwnerConsumers(
            any(Owner.class), anyString(), (java.util.Collection<ConsumerType>) any(Collection.class),
            any(List.class), any(List.class), any(List.class), any(List.class), any(List.class),
            any(List.class))).thenReturn(cqmock);

        List<Consumer> result = cr.list("TaylorSwift", null, null, null, null, null, null).list();
        assertEquals(consumers, result);
    }

    public void testFetchAllConsumersForOwner() {
        ConsumerResource cr = new ConsumerResource(
            mockedConsumerCurator, null, null, null, null, null, null, null, i18n, null, null, null, null,
            null, null, null, mockedOwnerCurator, null, null, null, null, null, null, this.config, null, null,
            null, null, null, null, this.factValidator, null, consumerEnricher);

        ArrayList<Consumer> consumers = new ArrayList<Consumer>();
        CandlepinQuery cqmock = mock(CandlepinQuery.class);
        when(cqmock.list()).thenReturn(consumers);
        when(cqmock.iterator()).thenReturn(consumers.iterator());

        when(mockedOwnerCurator.lookupByKey(eq("taylorOwner"))).thenReturn(new Owner());
        when(mockedConsumerCurator.searchOwnerConsumers(
            any(Owner.class), anyString(), (java.util.Collection<ConsumerType>) any(Collection.class),
            any(List.class), any(List.class), any(List.class), any(List.class), any(List.class),
            any(List.class))).thenReturn(cqmock);

        List<Consumer> result = cr.list(null, null, "taylorOwner", null, null, null, null).list();
        assertEquals(consumers, result);
    }

    @Test(expected = BadRequestException.class)
    public void testFetchAllConsumersForEmptyUUIDs() {
        ConsumerResource cr = new ConsumerResource(
            mockedConsumerCurator, null, null, null, null, null, null, null, i18n, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null, this.config, null, null, null, null,
            null, null, this.factValidator, null, consumerEnricher);

        cr.list(null, null, null, new ArrayList<String>(), null, null, null);
    }

    @Test
    public void testFetchAllConsumersForSomeUUIDs() {
        ConsumerResource cr = new ConsumerResource(
            mockedConsumerCurator, null, null, null, null, null, null, null, i18n, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null, this.config, null, null, null, null,
            null, null, this.factValidator, new ConsumerTypeValidator(null, null),
            consumerEnricher);

        ArrayList<Consumer> consumers = new ArrayList<Consumer>();
        CandlepinQuery cqmock = mock(CandlepinQuery.class);
        when(cqmock.list()).thenReturn(consumers);
        when(cqmock.iterator()).thenReturn(consumers.iterator());

        when(mockedConsumerCurator.searchOwnerConsumers(
            any(Owner.class), anyString(), (java.util.Collection<ConsumerType>) any(Collection.class),
            any(List.class), any(List.class), any(List.class),
            any(List.class), any(List.class), any(List.class))).thenReturn(cqmock);

        List<String> uuids = new ArrayList<String>();
        uuids.add("swiftuuid");
        List<Consumer> result = cr.list(null, null, null, uuids, null, null, null).list();
        assertEquals(consumers, result);
    }

    @Test
    public void testcheckForGuestsMigrationSerialList() {
        Consumer consumer = createConsumer();
        List<EntitlementCertificate> certificates = createEntitlementCertificates();

        when(mockedEntitlementCertServiceAdapter.listForConsumer(consumer)) .thenReturn(certificates);
        when(mockedConsumerCurator.verifyAndLookupConsumer(consumer.getUuid())).thenReturn(consumer);
        when(mockedEntitlementCurator.listByConsumer(consumer)).thenReturn(new ArrayList<Entitlement>());

        ConsumerResource consumerResource = Mockito.spy(new ConsumerResource(
            mockedConsumerCurator, null, null, null, null, mockedEntitlementCurator, null,
            mockedEntitlementCertServiceAdapter, null, null, null, null, null, null, mockedPoolManager, null,
            null, null, null, null, null, null, null, this.config, null, null, null, consumerBindUtil,
            null, null, this.factValidator, null, consumerEnricher));

        List<CertificateSerialDto> serials = consumerResource
            .getEntitlementCertificateSerials(consumer.getUuid());
        verify(consumerResource).checkForGuestMigration(consumer);
    }

    @Test
    public void testCheckForGuestsMigrationCertList() {
        Consumer consumer = createConsumer();
        List<EntitlementCertificate> certificates = createEntitlementCertificates();

        when(mockedEntitlementCertServiceAdapter.listForConsumer(consumer)) .thenReturn(certificates);
        when(mockedConsumerCurator.verifyAndLookupConsumer(consumer.getUuid())).thenReturn(consumer);
        when(mockedEntitlementCurator.listByConsumer(consumer)).thenReturn(new ArrayList<Entitlement>());

        ConsumerResource consumerResource = Mockito.spy(new ConsumerResource(
            mockedConsumerCurator, null, null, null, null, mockedEntitlementCurator, null,
            mockedEntitlementCertServiceAdapter, null, null, null, null, null, null, mockedPoolManager, null,
            null, null, null, null, null, null, null, this.config, null, null, null, consumerBindUtil,
            null, null, this.factValidator, null, consumerEnricher));

        Set<Long> serials = new HashSet<Long>();
        List<Certificate> certs = consumerResource
            .getEntitlementCertificates(consumer.getUuid(), "123");
        verify(consumerResource).checkForGuestMigration(consumer);
    }

    @Test
    public void testNoDryBindWhenAutobindDisabledForOwner() throws Exception {
        Consumer consumer = createConsumer();
        consumer.getOwner().setAutobindDisabled(true);
        when(mockedConsumerCurator.verifyAndLookupConsumer(eq(consumer.getUuid()))).thenReturn(consumer);
        ManifestManager manifestManager = mock(ManifestManager.class);
        ConsumerResource consumerResource = new ConsumerResource(
            mockedConsumerCurator, null, null, null, null, null, null, null, i18n, null, null, null, null,
            null, null, null, mockedOwnerCurator, null, null, null, null, null, null, this.config, null, null,
            null, null, manifestManager, null, this.factValidator, null, consumerEnricher);

        try {
            consumerResource.dryBind(consumer.getUuid(), "some-sla");
            fail("Should have thrown a BadRequestException.");
        }
        catch (BadRequestException e) {
            assertEquals("Owner has autobind disabled.", e.getMessage());
        }
    }

    @Test
    public void testAsyncExport() {
        CdnCurator mockedCdnCurator = mock(CdnCurator.class);
        ManifestManager manifestManager = mock(ManifestManager.class);
        ConsumerResource cr = new ConsumerResource(
            mockedConsumerCurator, null, null, null, null, null, null, null, i18n, null, null, null, null,
            null, null, null, mockedOwnerCurator, null, null, null, null, null, null, this.config, null,
            mockedCdnCurator, null, null, manifestManager, null, this.factValidator, null,
            consumerEnricher);

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

}
