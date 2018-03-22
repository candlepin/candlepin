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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
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
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.dto.api.v1.CertificateDTO;
import org.candlepin.dto.api.v1.ConsumerDTO;
import org.candlepin.dto.api.v1.ConsumerTypeDTO;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.model.CandlepinQuery;
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
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.model.dto.Subscription;
import org.candlepin.policy.js.activationkey.ActivationKeyRules;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.resource.dto.AutobindData;
import org.candlepin.resource.util.ConsumerBindUtil;
import org.candlepin.resource.util.ConsumerEnricher;
import org.candlepin.resource.util.ConsumerTypeValidator;
import org.candlepin.resource.util.GuestMigration;
import org.candlepin.resource.util.ResourceDateParser;
import org.candlepin.resteasy.parameter.KeyValueParameter;
import org.candlepin.service.EntitlementCertServiceAdapter;
import org.candlepin.service.IdentityCertServiceAdapter;
import org.candlepin.service.OwnerServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.service.impl.DefaultContentAccessCertServiceAdapter;
import org.candlepin.test.TestUtil;
import org.candlepin.util.FactValidator;
import org.candlepin.util.ServiceLevelValidator;

import com.google.inject.util.Providers;

import org.apache.commons.lang.RandomStringUtils;
import org.hibernate.mapping.Collection;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
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

import javax.inject.Provider;
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

    @Mock private ConsumerCurator mockConsumerCurator;
    @Mock private OwnerCurator mockOwnerCurator;
    @Mock private EntitlementCertServiceAdapter mockEntitlementCertServiceAdapter;
    @Mock private OwnerServiceAdapter mockOwnerServiceAdapter;
    @Mock private SubscriptionServiceAdapter mockSubscriptionServiceAdapter;
    @Mock private PoolManager mockPoolManager;
    @Mock private EntitlementCurator mockEntitlementCurator;
    @Mock private ComplianceRules mockComplianceRules;
    @Mock private ServiceLevelValidator mockServiceLevelValidator;
    @Mock private ActivationKeyRules mockActivationKeyRules;
    @Mock private EventFactory eventFactory;
    @Mock private EventBuilder eventBuilder;
    @Mock private ConsumerBindUtil consumerBindUtil;
    @Mock private OwnerManager mockOwnerManager;
    @Mock private ConsumerEnricher consumerEnricher;
    @Mock private ConsumerTypeCurator mockConsumerTypeCurator;
    @Mock private DefaultContentAccessCertServiceAdapter mockContentAccessCertService;
    @Mock private EventSink sink;

    private GuestMigration testMigration;
    private Provider<GuestMigration> migrationProvider;
    private ModelTranslator translator;


    @Before
    public void setUp() {
        this.config = new CandlepinCommonTestConfig();
        this.translator = new StandardTranslator(mockConsumerTypeCurator);
        this.i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        when(eventBuilder.setEventData(any(Consumer.class))).thenReturn(eventBuilder);
        when(eventFactory.getEventBuilder(any(Target.class), any(Type.class))).thenReturn(eventBuilder);

        this.factValidator = new FactValidator(this.config, this.i18n);

        testMigration = new GuestMigration(mockConsumerCurator);
        migrationProvider = Providers.of(testMigration);
    }

    protected ConsumerType mockConsumerType(ConsumerType ctype) {
        // Ensure the type has an ID
        if (ctype != null) {
            if (ctype.getId() == null) {
                ctype.setId("test-ctype-" + ctype.getLabel() + "-" + TestUtil.randomInt());
            }

            when(mockConsumerTypeCurator.lookupByLabel(eq(ctype.getLabel()))).thenReturn(ctype);
            when(mockConsumerTypeCurator.find(eq(ctype.getId()))).thenReturn(ctype);

            doAnswer(new Answer<ConsumerType>() {
                @Override
                public ConsumerType answer(InvocationOnMock invocation) throws Throwable {
                    Object[] args = invocation.getArguments();
                    Consumer consumer = (Consumer) args[0];
                    ConsumerTypeCurator curator = (ConsumerTypeCurator) invocation.getMock();

                    ConsumerType ctype = null;

                    if (consumer != null && consumer.getTypeId() != null) {
                        ctype = curator.find(consumer.getTypeId());

                        if (ctype == null) {
                            throw new IllegalStateException("No such consumer type: " + consumer.getTypeId());
                        }
                    }

                    return ctype;
                }
            }).when(mockConsumerTypeCurator).getConsumerType(any(Consumer.class));
        }

        return ctype;
    }

    protected Owner mockOwner(Owner owner) {
        if (owner != null) {
            int rand = TestUtil.randomInt();

            if (owner.getId() == null) {
                owner.setId("test-owner-" + rand);
            }

            if (owner.getKey() == null) {
                owner.setKey("test-owner-key-" + rand);
            }

            when(mockOwnerCurator.lookupByKey(eq(owner.getKey()))).thenReturn(owner);
        }

        return owner;
    }

    protected Owner createOwner() {
        int rand = TestUtil.randomInt();
        Owner owner = new Owner("test-owner-key-" + rand, "Test Owner " + rand);
        owner.setId("test-owner-" + rand);

        this.mockOwner(owner);

        return owner;
    }

    protected Consumer mockConsumer(Consumer consumer) {
        if (consumer != null) {
            consumer.ensureUUID();

            when(mockConsumerCurator.verifyAndLookupConsumer(eq(consumer.getUuid()))).thenReturn(consumer);

            when(mockConsumerCurator.verifyAndLookupConsumerWithEntitlements(eq(consumer.getUuid())))
                .thenReturn(consumer);
        }

        return consumer;
    }

    protected Consumer createConsumer(Owner owner, ConsumerType ctype) {
        if (ctype == null) {
            ctype = new ConsumerType("test-ctype-" + TestUtil.randomInt());
        }

        if (owner == null) {
            owner = this.createOwner();
        }

        this.mockConsumerType(ctype);
        Consumer consumer = this.mockConsumer(new Consumer("test-consumer", "test-user", owner, ctype));

        return consumer;
    }

    protected Consumer createConsumer(Owner owner) {
        return this.createConsumer(owner, null);
    }

    protected Consumer createConsumer() {
        return this.createConsumer(null, null);
    }

    @Test
    public void testValidateShareConsumerRequiresRecipientFact() {
        ConsumerType share = this.mockConsumerType(new ConsumerType(ConsumerTypeEnum.SHARE));
        ConsumerTypeDTO shareDto = this.translator.translate(share, ConsumerTypeDTO.class);

        ConsumerDTO c = createConsumerDTO("test-consumer", "test-user", new OwnerDTO("Test Owner"), shareDto);

        ConsumerResource consumerResource = new ConsumerResource(
            mockConsumerCurator, mockConsumerTypeCurator, null, null, null, mockEntitlementCurator, null,
            mockEntitlementCertServiceAdapter, i18n, null, null, null, null,
            null, mockPoolManager, null, mockOwnerCurator, null, null, null,
            null, null, null, new CandlepinCommonTestConfig(), null, null, null,
            consumerBindUtil, null, null, factValidator,
            null, consumerEnricher, migrationProvider, translator);

        UserPrincipal uap = mock(UserPrincipal.class);
        when(uap.canAccess(any(Object.class), any(SubResource.class), any(Access.class)))
            .thenReturn(Boolean.TRUE);

        Owner o = mock(Owner.class);
        when(mockOwnerCurator.lookupByKey(any(String.class))).thenReturn(o);

        c.setFact("foo", "bar");

        thrown.expect(BadRequestException.class);
        thrown.expectMessage("must specify a recipient org");
        consumerResource.create(c, uap, "test-user", "test-owner", null, false);
    }

    @Test
    public void testValidateShareConsumerRequiresRecipientPermissions() {
        ConsumerType share = this.mockConsumerType(new ConsumerType(ConsumerTypeEnum.SHARE));
        ConsumerTypeDTO shareDto = this.translator.translate(share, ConsumerTypeDTO.class);
        ConsumerDTO c = createConsumerDTO("test-consumer", "test-user", new OwnerDTO("Test Owner"), shareDto);

        ConsumerResource consumerResource = new ConsumerResource(
            mockConsumerCurator, mockConsumerTypeCurator, null, null, null, mockEntitlementCurator, null,
            mockEntitlementCertServiceAdapter, i18n, null, null, null, null,
            null, mockPoolManager, null, mockOwnerCurator, null, null, null,
            null, null, null, new CandlepinCommonTestConfig(), null, null, null,
            consumerBindUtil, null, null, factValidator,
            null, consumerEnricher, migrationProvider, translator);

        UserPrincipal uap = mock(UserPrincipal.class);
        when(uap.canAccess(any(Object.class), any(SubResource.class), any(Access.class)))
            .thenReturn(Boolean.TRUE);

        Owner o = mock(Owner.class);
        when(mockOwnerCurator.lookupByKey(any(String.class))).thenReturn(o);

        Owner o2 = mock(Owner.class);
        c.setRecipientOwnerKey("o2");
        when(mockOwnerCurator.lookupByKey(eq("o2"))).thenReturn(o2);

        when(uap.canAccess(eq(o2), eq(SubResource.ENTITLEMENTS), eq(Access.CREATE)))
            .thenReturn(Boolean.FALSE);

        thrown.expect(NotFoundException.class);
        thrown.expectMessage("owner with key");
        consumerResource.create(c, uap, "test-user", "test-owner", null, false);
    }

    @Test
    public void testGetCertSerials() {
        Consumer consumer = createConsumer();

        List<EntitlementCertificate> certificates = createEntitlementCertificates();
        List<Long> serialIds = new ArrayList<>();
        for (EntitlementCertificate ec : certificates) {
            serialIds.add(ec.getSerial().getId());
        }

        when(mockEntitlementCertServiceAdapter.listEntitlementSerialIds(consumer)).thenReturn(serialIds);
        when(mockEntitlementCurator.listByConsumer(consumer)).thenReturn(new ArrayList<>());

        ConsumerResource consumerResource = new ConsumerResource(
            mockConsumerCurator, mockConsumerTypeCurator, null, null, null, mockEntitlementCurator, null,
            mockEntitlementCertServiceAdapter, null, null, null, null, null, null, mockPoolManager, null,
            null, null, null, null, null, null, null, this.config, null, null, null, consumerBindUtil,
            null, mockContentAccessCertService, this.factValidator, null, consumerEnricher,
            migrationProvider, translator);

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

        when(mockEntitlementCurator.find(eq("9999"))).thenReturn(e);
        when(mockSubscriptionServiceAdapter.getSubscription(eq("4444"))).thenReturn(s);

        when(mockEntitlementCertServiceAdapter.generateEntitlementCert(
            any(Entitlement.class), any(Product.class)))
            .thenThrow(new IOException());

        CandlepinPoolManager poolManager = new CandlepinPoolManager(
            null, null, null, this.config, null, null, mockEntitlementCurator,
            mockConsumerCurator, mockConsumerTypeCurator, null, null, null, null, mockActivationKeyRules,
            null, null, null, null, null, null, null, null, null, null
        );

        ConsumerResource consumerResource = new ConsumerResource(
            mockConsumerCurator, mockConsumerTypeCurator, null, null, null, mockEntitlementCurator, null,
            mockEntitlementCertServiceAdapter, null, null, null, null, null, null,
            poolManager, null, null, null, null, null, null, null, null,
            this.config, null, null, null, consumerBindUtil,
            null, null, this.factValidator, null, consumerEnricher, migrationProvider, translator);

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

        CandlepinPoolManager mgr = mock(CandlepinPoolManager.class);
        ConsumerResource cr = new ConsumerResource(mockConsumerCurator, mockConsumerTypeCurator,
            null, mockSubscriptionServiceAdapter, this.mockOwnerServiceAdapter, null, null, null, null,
            null, null, null, null, null, mgr, null, null, null, null, null, null, null, null,
            this.config, null, null, null, consumerBindUtil, null, null, this.factValidator,
            null, consumerEnricher, migrationProvider, translator);

        cr.regenerateEntitlementCertificates(consumer.getUuid(), null, true);
        Mockito.verify(mgr, Mockito.times(1)).regenerateCertificatesOf(eq(consumer), eq(true));
    }

    @Test
    public void testRegenerateIdCerts() throws GeneralSecurityException, IOException {
        // using lconsumer simply to avoid hiding consumer. This should
        // get renamed once we refactor this test suite.
        IdentityCertServiceAdapter mockIdSvc = Mockito.mock(IdentityCertServiceAdapter.class);

        EventSink sink = Mockito.mock(EventSinkImpl.class);

        Consumer consumer = createConsumer();
        consumer.setIdCert(createIdCert());
        IdentityCertificate ic = consumer.getIdCert();
        assertNotNull(ic);

        when(mockIdSvc.regenerateIdentityCert(consumer)).thenReturn(createIdCert());

        ConsumerResource cr = new ConsumerResource(mockConsumerCurator, mockConsumerTypeCurator, null,
            null, null, null, mockIdSvc, null, null, sink, eventFactory, null, null,
            null, null, null, mockOwnerCurator, null, null, null, null,
            null, null, this.config, null, null, null, consumerBindUtil,
            null, null, this.factValidator, null, consumerEnricher, migrationProvider, translator);

        ConsumerDTO fooc = cr.regenerateIdentityCertificates(consumer.getUuid());

        assertNotNull(fooc);
        CertificateDTO ic1 = fooc.getIdCert();
        assertNotNull(ic1);
        assertFalse(ic.getId().equals(ic1.getId()));
    }

    @Test
    public void testIdCertGetsRegenerated() throws Exception {
        // using lconsumer simply to avoid hiding consumer. This should
        // get renamed once we refactor this test suite.
        IdentityCertServiceAdapter mockIdSvc = Mockito.mock(IdentityCertServiceAdapter.class);

        EventSink sink = Mockito.mock(EventSinkImpl.class);

        SubscriptionServiceAdapter ssa = Mockito.mock(SubscriptionServiceAdapter.class);
        ComplianceRules rules = Mockito.mock(ComplianceRules.class);

        Consumer consumer = createConsumer();
        ComplianceStatus status = new ComplianceStatus();
        when(rules.getStatus(any(Consumer.class), any(Date.class), anyBoolean())).thenReturn(status);
        // cert expires today which will trigger regen
        consumer.setIdCert(createIdCert());
        BigInteger origserial = consumer.getIdCert().getSerial().getSerial();

        when(mockIdSvc.regenerateIdentityCert(consumer)).thenReturn(createIdCert());

        ConsumerResource cr = new ConsumerResource(mockConsumerCurator, mockConsumerTypeCurator,
            null, ssa, this.mockOwnerServiceAdapter, null, mockIdSvc, null, null, sink, eventFactory,
            null, null, null, null, null, mockOwnerCurator, null, null, rules, null,
            null, null, this.config, null, null, null, consumerBindUtil,
            null, null, this.factValidator, null, consumerEnricher, migrationProvider, translator);

        ConsumerDTO c = cr.getConsumer(consumer.getUuid());

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

        ConsumerResource cr = new ConsumerResource(mockConsumerCurator, mockConsumerTypeCurator,
            null, ssa, this.mockOwnerServiceAdapter, null, null, null, null, null, null, null, null, null,
            null, null, mockOwnerCurator, null, null, rules, null, null, null,
            this.config, null, null, null, consumerBindUtil,
            null, null, this.factValidator, null, consumerEnricher, migrationProvider, translator);

        ConsumerDTO c = cr.getConsumer(consumer.getUuid());

        assertEquals(origserial, c.getIdCert().getSerial().getSerial());
    }

    @Test(expected = BadRequestException.class)
    public void testCreatePersonConsumerWithActivationKey() {
        ConsumerType ctype = this.mockConsumerType(new ConsumerType(ConsumerTypeEnum.PERSON));
        ConsumerTypeDTO ctypeDto = this.translator.translate(ctype, ConsumerTypeDTO.class);

        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner, ctype);
        ConsumerDTO consumerDto = this.translator.translate(consumer, ConsumerDTO.class);

        ActivationKey ak = mock(ActivationKey.class);
        NoAuthPrincipal nap = mock(NoAuthPrincipal.class);
        ActivationKeyCurator akc = mock(ActivationKeyCurator.class);
        ConsumerContentOverrideCurator ccoc = mock(ConsumerContentOverrideCurator.class);

        when(ak.getId()).thenReturn("testKey");
        when(akc.lookupForOwner(eq(owner.getKey()), eq(owner))).thenReturn(ak);

        ConsumerResource cr = new ConsumerResource(null, mockConsumerTypeCurator, null,
            null, null, null, null, null, i18n, null, null, null, null,
            null, null, null, mockOwnerCurator, akc, null, null, null, null,
            null, this.config, null, null, null, consumerBindUtil,
            null, null, this.factValidator, null, consumerEnricher, migrationProvider, translator);

        cr.create(consumerDto, nap, null, owner.getKey(), "testKey", true);
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

        ConsumerResource cr = new ConsumerResource(cc, mockConsumerTypeCurator,
            null, sa, this.mockOwnerServiceAdapter, null, null, null, i18n, null, null, null, null, null,
            null, null, null, null, e, null, null, null, null,
            this.config, null, null, null, consumerBindUtil,
            null, null, this.factValidator, null, consumerEnricher, migrationProvider, translator);

        Response r = cr.bind("fakeConsumer", null, prodIds, null, null, null, false, null, null);
        assertEquals(null, r.getEntity());
    }

    @Test
    public void futureHealing() throws Exception {
        Consumer c = createConsumer();
        SubscriptionServiceAdapter sa = mock(SubscriptionServiceAdapter.class);
        Entitler e = mock(Entitler.class);
        ConsumerCurator cc = mock(ConsumerCurator.class);
        ConsumerInstalledProduct cip = mock(ConsumerInstalledProduct.class);
        Set<ConsumerInstalledProduct> products = new HashSet<>();
        products.add(cip);

        when(cip.getProductId()).thenReturn("product-foo");
        when(sa.hasUnacceptedSubscriptionTerms(eq(c.getOwner()))).thenReturn(false);
        when(cc.verifyAndLookupConsumerWithEntitlements(eq(c.getUuid()))).thenReturn(c);

        ConsumerResource cr = new ConsumerResource(cc, mockConsumerTypeCurator, null, sa,
            this.mockOwnerServiceAdapter, null, null, null, null, null, null, null, null, null, null,
            null, null, null, e, null, null, null, null,
            this.config, null, null, null, consumerBindUtil,
            null, null, this.factValidator, null, consumerEnricher, migrationProvider, translator);

        String dtStr = "2011-09-26T18:10:50.184081+00:00";
        Date dt = ResourceDateParser.parseDateString(dtStr);

        cr.bind(c.getUuid(), null, null, null, null, null, false, dtStr, null);
        AutobindData data = AutobindData.create(c).on(dt);
        verify(e).bindByProducts(eq(data));
    }

    @Test(expected = NotFoundException.class)
    public void unbindByInvalidSerialShouldFail() {
        Consumer consumer = createConsumer();

        when(mockEntitlementCurator.find(any(Serializable.class))).thenReturn(null);

        ConsumerResource consumerResource = new ConsumerResource(mockConsumerCurator, mockConsumerTypeCurator,
            null, null, null, mockEntitlementCurator, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, this.config, null, null, null, consumerBindUtil,
            null, null, this.factValidator, null, consumerEnricher, migrationProvider, translator);

        consumerResource.unbindBySerial("fake uuid", Long.valueOf(1234L));
    }

    /**
     * Basic test. If invalid id is given, should throw
     * {@link NotFoundException}
     */
    @Test(expected = NotFoundException.class)
    public void unbindByInvalidPoolIdShouldFail() {
        Consumer consumer = createConsumer();

        when(mockEntitlementCurator.listByConsumerAndPoolId(eq(consumer), any(String.class)))
            .thenReturn(new ArrayList<>());

        ConsumerResource consumerResource = new ConsumerResource(mockConsumerCurator, mockConsumerTypeCurator,
            null, null, null, mockEntitlementCurator, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, this.config, null, null, null, consumerBindUtil,
            null, null, this.factValidator, null, consumerEnricher, migrationProvider, translator);

        consumerResource.unbindByPool("fake-uuid", "Run Forest!");
    }

    @Test(expected = BadRequestException.class)
    public void testBindMultipleParams() throws Exception {
        ConsumerResource consumerResource = new ConsumerResource(mockConsumerCurator, mockConsumerTypeCurator,
            null, null, null, null, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, this.config, null, null, null, consumerBindUtil,
            null, null, this.factValidator, null, consumerEnricher, migrationProvider, translator);

        Consumer c = createConsumer();
        consumerResource.bind(c.getUuid(), "fake pool uuid",
            new String[]{"12232"}, 1, null, null, false, null, null);
    }

    @Test(expected = NotFoundException.class)
    public void testBindByPoolBadConsumerUuid() throws Exception {
        when(mockConsumerCurator.verifyAndLookupConsumerWithEntitlements(any(String.class)))
            .thenThrow(new NotFoundException(""));

        ConsumerResource consumerResource = new ConsumerResource(mockConsumerCurator, mockConsumerTypeCurator,
            null, null, null, null, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, this.config, null, null, null, consumerBindUtil,
            null, null, this.factValidator, null, consumerEnricher, migrationProvider, translator);

        Consumer c = createConsumer();
        consumerResource.bind(c.getUuid(), "fake pool uuid", null, null, null, null, false, null, null);
    }

    /**
     * Basic test. If invalid id is given, should throw
     * {@link NotFoundException}
     */
    @Test(expected = NotFoundException.class)
    public void testRegenerateEntitlementCertificatesWithInvalidConsumerId() {
        when(mockConsumerCurator.verifyAndLookupConsumer(any(String.class)))
            .thenThrow(new NotFoundException(""));

        ConsumerResource consumerResource = new ConsumerResource(mockConsumerCurator, mockConsumerTypeCurator,
            null, null, null, null, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, null, null, null,
            null, this.config, null, null, null, consumerBindUtil,
            null, null, this.factValidator, null, consumerEnricher, migrationProvider, translator);

        consumerResource.regenerateEntitlementCertificates("xyz", null, true);
    }

    protected EntitlementCertificate createEntitlementCertificate(String key, String cert) {
        EntitlementCertificate toReturn = new EntitlementCertificate();
        CertificateSerial certSerial = new CertificateSerial(1L, new Date());
        toReturn.setKeyAsBytes(key.getBytes());
        toReturn.setCertAsBytes(cert.getBytes());
        toReturn.setSerial(certSerial);
        return toReturn;
    }

    @Test(expected = NotFoundException.class)
    public void testNullPerson() {
        Owner owner = this.createOwner();

        ConsumerType ctype = this.mockConsumerType(new ConsumerType(ConsumerTypeEnum.PERSON));
        ConsumerTypeDTO ctypeDto = this.translator.translate(ctype, ConsumerTypeDTO.class);

        Consumer consumer = this.createConsumer(owner, ctype);
        ConsumerDTO consumerDto = this.translator.translate(consumer, ConsumerDTO.class);

        UserServiceAdapter usa = mock(UserServiceAdapter.class);
        UserPrincipal up = mock(UserPrincipal.class);

        when(up.canAccess(eq(owner), eq(SubResource.CONSUMERS), eq(Access.CREATE))).thenReturn(true);

        // usa.findByLogin() will return null by default no need for a when

        ConsumerResource cr = new ConsumerResource(mockConsumerCurator, mockConsumerTypeCurator, null,
            null, null, null, null, null, i18n, null, null, null, null,
            usa, null,  null, mockOwnerCurator, null, null, null, null, null,
            null, this.config, null, null, null, consumerBindUtil,
            null, null, this.factValidator, null, consumerEnricher, migrationProvider, translator);

        cr.create(consumerDto, up, null, owner.getKey(), null, true);
    }

    @Test
    public void testCreateConsumerShouldFailOnMaxLengthOfName() {
        thrown.expect(BadRequestException.class);
        thrown.expectMessage(String.format("Name of the consumer " +
            "should be shorter than %d characters.", Consumer.MAX_LENGTH_OF_CONSUMER_NAME + 1));

        Owner owner = this.createOwner();

        ConsumerType ctype = this.mockConsumerType(new ConsumerType(ConsumerTypeEnum.SYSTEM));
        ConsumerTypeDTO ctypeDto = this.translator.translate(ctype, ConsumerTypeDTO.class);

        Consumer consumer = this.createConsumer(owner, ctype);
        consumer.setName(RandomStringUtils.randomAlphanumeric(Consumer.MAX_LENGTH_OF_CONSUMER_NAME + 1));
        ConsumerDTO consumerDto = this.translator.translate(consumer, ConsumerDTO.class);

        UserPrincipal up = mock(UserPrincipal.class);

        ConsumerResource consumerResource = createConsumerResource(mockOwnerCurator);

        when(up.canAccess(eq(owner), eq(SubResource.CONSUMERS), eq(Access.CREATE))).thenReturn(true);

        consumerResource.create(consumerDto, up, null, owner.getKey(), null, false);
    }

    ConsumerResource createConsumerResource(OwnerCurator oc) {
        ConsumerResource consumerResource = new ConsumerResource(
            null, mockConsumerTypeCurator, null, null, null, null, null, null, i18n, null, null, null,
            null, null, null, null, oc, null, null, null, null, null, null, this.config, null, null, null,
            null, null, null, this.factValidator, null, consumerEnricher, migrationProvider, translator);

        return consumerResource;
    }

    @Test
    public void testGetComplianceStatusList() {
        Owner owner = this.createOwner();

        Consumer c = this.createConsumer(owner);
        Consumer c2 = this.createConsumer(owner);

        List<Consumer> consumers = new ArrayList<>();
        consumers.add(c);
        consumers.add(c2);

        CandlepinQuery cqmock = mock(CandlepinQuery.class);
        when(cqmock.list()).thenReturn(consumers);
        when(cqmock.iterator()).thenReturn(consumers.iterator());

        List<String> uuids = new ArrayList<>();
        uuids.add(c.getUuid());
        uuids.add(c2.getUuid());
        when(mockConsumerCurator.findByUuids(eq(uuids))).thenReturn(cqmock);

        ComplianceStatus status = new ComplianceStatus();
        when(mockComplianceRules.getStatus(any(Consumer.class), any(Date.class)))
            .thenReturn(status);

        ConsumerResource cr = new ConsumerResource(mockConsumerCurator, mockConsumerTypeCurator, null,
            null, null, null, null,
            null, i18n, null, null, null, null, null, null, null, null, null, null, mockComplianceRules,
            null, null, null, this.config, null, null, null, consumerBindUtil, null, null,
            this.factValidator, null, consumerEnricher, migrationProvider, translator);

        Map<String, ComplianceStatus> results = cr.getComplianceStatusList(uuids);
        assertEquals(2, results.size());
        assertTrue(results.containsKey(c.getUuid()));
        assertTrue(results.containsKey(c2.getUuid()));
    }

    @Test
    public void testConsumerExistsYes() {
        when(mockConsumerCurator.doesConsumerExist(any(String.class))).thenReturn(true);
        ConsumerResource cr = new ConsumerResource(mockConsumerCurator, mockConsumerTypeCurator,
            null, null, null, null, null, null, i18n, null, null, null,
            null, null, null, null, null, null, null, mockComplianceRules,
            null, null, null, this.config, null, null, null, consumerBindUtil,
            null, null, this.factValidator, null, consumerEnricher, migrationProvider, translator);

        cr.consumerExists("uuid");
    }

    @Test (expected = NotFoundException.class)
    public void testConsumerExistsNo() {
        when(mockConsumerCurator.doesConsumerExist(any(String.class))).thenReturn(false);
        ConsumerResource cr = new ConsumerResource(mockConsumerCurator, mockConsumerTypeCurator,
            null, null, null, null, null,
            null, i18n, null, null, null, null, null, null, null, null, null, null, mockComplianceRules,
            null, null, null, this.config, null, null, null, consumerBindUtil, null, null,
            this.factValidator, null, consumerEnricher, migrationProvider, translator);

        cr.consumerExists("uuid");
    }

    @Test(expected = BadRequestException.class)
    public void testFetchAllConsumers() {
        ConsumerResource cr = new ConsumerResource(
            null, mockConsumerTypeCurator, null, null, null, null, null, null, i18n, null, null, null, null,
            null, null, null,
            null, null, null, null, null, null, null, this.config, null, null, null, null, null,
            null, this.factValidator, null, consumerEnricher, migrationProvider, translator);

        cr.list(null, null, null, null, null, null, null);
    }

    @Test
    public void testFetchAllConsumersForUser() {
        ModelTranslator mockTranslator = mock(ModelTranslator.class);
        ConsumerResource cr = new ConsumerResource(
            mockConsumerCurator, mockConsumerTypeCurator, null, null, null, null, null, null, i18n, null,
            null, null, null,
            null, null, null, null, null, null, null, null, null, null, this.config, null, null, null, null,
            null, null, this.factValidator, new ConsumerTypeValidator(null, null),
            consumerEnricher, migrationProvider, mockTranslator);

        ArrayList<Consumer> consumers = new ArrayList<>();

        CandlepinQuery cqmock = mock(CandlepinQuery.class);
        when(cqmock.list()).thenReturn(consumers);
        when(cqmock.iterator()).thenReturn(consumers.iterator());
        when(mockConsumerCurator.searchOwnerConsumers(
            any(Owner.class), anyString(), (java.util.Collection<ConsumerType>) any(Collection.class),
            any(List.class), any(List.class), any(List.class), any(List.class), any(List.class),
            any(List.class))).thenReturn(cqmock);
        when(mockTranslator.translateQuery(eq(cqmock), eq(ConsumerDTO.class))).thenReturn(cqmock);

        List<ConsumerDTO> result = cr.list("TaylorSwift", null, null, null, null, null, null).list();
        assertEquals(consumers, result);
    }

    public void testFetchAllConsumersForOwner() {
        ConsumerResource cr = new ConsumerResource(
            mockConsumerCurator, mockConsumerTypeCurator, null, null, null, null, null, null, i18n, null,
            null, null, null,
            null, null, null, mockOwnerCurator, null, null, null, null, null, null, this.config, null, null,
            null, null, null, null, this.factValidator,
            null, consumerEnricher, migrationProvider, translator);

        ArrayList<Consumer> consumers = new ArrayList<>();
        CandlepinQuery cqmock = mock(CandlepinQuery.class);
        when(cqmock.list()).thenReturn(consumers);
        when(cqmock.iterator()).thenReturn(consumers.iterator());

        when(mockOwnerCurator.lookupByKey(eq("taylorOwner"))).thenReturn(new Owner());
        when(mockConsumerCurator.searchOwnerConsumers(
            any(Owner.class), anyString(), (java.util.Collection<ConsumerType>) any(Collection.class),
            any(List.class), any(List.class), any(List.class), any(List.class), any(List.class),
            any(List.class))).thenReturn(cqmock);

        List<ConsumerDTO> result = cr.list(null, null, "taylorOwner", null, null, null, null).list();
        assertEquals(consumers, result);
    }

    @Test(expected = BadRequestException.class)
    public void testFetchAllConsumersForEmptyUUIDs() {
        ConsumerResource cr = new ConsumerResource(
            mockConsumerCurator, mockConsumerTypeCurator, null, null, null, null, null, null, i18n, null,
            null, null, null,
            null, null, null, null, null, null, null, null, null, null, this.config, null, null, null, null,
            null, null, this.factValidator, null, consumerEnricher, migrationProvider, translator);

        cr.list(null, null, null, new ArrayList<>(), null, null, null);
    }

    @Test
    public void testFetchAllConsumersForSomeUUIDs() {
        ModelTranslator mockTranslator = mock(ModelTranslator.class);
        ConsumerResource cr = new ConsumerResource(
            mockConsumerCurator, mockConsumerTypeCurator, null, null, null, null, null, null, i18n, null,
            null, null, null,
            null, null, null, null, null, null, null, null, null, null, this.config, null, null, null, null,
            null, null, this.factValidator, new ConsumerTypeValidator(null, null),
            consumerEnricher, migrationProvider, mockTranslator);

        ArrayList<Consumer> consumers = new ArrayList<>();
        CandlepinQuery cqmock = mock(CandlepinQuery.class);
        when(cqmock.list()).thenReturn(consumers);
        when(cqmock.iterator()).thenReturn(consumers.iterator());

        when(mockConsumerCurator.searchOwnerConsumers(
            any(Owner.class), anyString(), (java.util.Collection<ConsumerType>) any(Collection.class),
            any(List.class), any(List.class), any(List.class),
            any(List.class), any(List.class), any(List.class))).thenReturn(cqmock);
        when(mockTranslator.translateQuery(eq(cqmock), eq(ConsumerDTO.class))).thenReturn(cqmock);

        List<String> uuids = new ArrayList<>();
        uuids.add("swiftuuid");
        List<ConsumerDTO> result = cr.list(null, null, null, uuids, null, null, null).list();
        assertEquals(consumers, result);
    }

    @Test
    public void testcheckForGuestsMigrationSerialList() {
        Consumer consumer = createConsumer();
        List<EntitlementCertificate> certificates = createEntitlementCertificates();

        when(mockEntitlementCertServiceAdapter.listForConsumer(consumer)) .thenReturn(certificates);
        when(mockConsumerCurator.verifyAndLookupConsumer(consumer.getUuid())).thenReturn(consumer);
        when(mockEntitlementCurator.listByConsumer(consumer)).thenReturn(new ArrayList<>());

        ConsumerResource consumerResource = Mockito.spy(new ConsumerResource(
            mockConsumerCurator, mockConsumerTypeCurator, null, null, null, mockEntitlementCurator, null,
            mockEntitlementCertServiceAdapter, null, null, null, null, null, null, mockPoolManager, null,
            null, null, null, null, null, null, null, this.config, null, null, null, consumerBindUtil,
            null, mockContentAccessCertService, this.factValidator, null, consumerEnricher,
            migrationProvider, translator));

        List<CertificateSerialDto> serials = consumerResource
            .getEntitlementCertificateSerials(consumer.getUuid());
        verify(consumerResource).revokeOnGuestMigration(consumer);
    }

    @Test
    public void testCheckForGuestsMigrationCertList() {
        Consumer consumer = createConsumer();
        List<EntitlementCertificate> certificates = createEntitlementCertificates();

        when(mockEntitlementCertServiceAdapter.listForConsumer(consumer)) .thenReturn(certificates);
        when(mockConsumerCurator.verifyAndLookupConsumer(consumer.getUuid())).thenReturn(consumer);
        when(mockEntitlementCurator.listByConsumer(consumer)).thenReturn(new ArrayList<>());

        GuestMigration migrationSpy = Mockito.spy(testMigration);
        migrationProvider = Providers.of(migrationSpy);

        ConsumerResource consumerResource = Mockito.spy(new ConsumerResource(
            mockConsumerCurator, mockConsumerTypeCurator, null, null, null, mockEntitlementCurator, null,
            mockEntitlementCertServiceAdapter, null, null, null, null, null, null, mockPoolManager, null,
            null, null, null, null, null, null, null, this.config, null, null, null, consumerBindUtil,
            null, mockContentAccessCertService, this.factValidator, null, consumerEnricher,
            migrationProvider, translator));

        Set<Long> serials = new HashSet<>();
        List<CertificateDTO> certs = consumerResource.getEntitlementCertificates(consumer.getUuid(), "123");
        verify(consumerResource).revokeOnGuestMigration(consumer);
    }

    @Test
    public void testNoDryBindWhenAutobindDisabledForOwner() throws Exception {
        Consumer consumer = createConsumer();
        consumer.getOwner().setAutobindDisabled(true);
        ManifestManager manifestManager = mock(ManifestManager.class);
        ConsumerResource consumerResource = new ConsumerResource(
            mockConsumerCurator, mockConsumerTypeCurator, null, null, null, null, null, null, i18n, null,
            null, null, null,
            null, null, null, mockOwnerCurator, null, null, null, null, null, null, this.config, null, null,
            null, null, manifestManager, null, this.factValidator, null, consumerEnricher,
            migrationProvider, translator);

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
        CdnCurator mockCdnCurator = mock(CdnCurator.class);
        ManifestManager manifestManager = mock(ManifestManager.class);
        ConsumerResource cr = new ConsumerResource(
            mockConsumerCurator, mockConsumerTypeCurator, null, null, null, null, null, null, i18n, null,
            null, null, null,
            null, null, null, mockOwnerCurator, null, null, null, null, null, null, this.config, null,
            mockCdnCurator, null, null, manifestManager, null, this.factValidator, null,
            consumerEnricher, migrationProvider, translator);

        List<KeyValueParameter> extParams = new ArrayList<>();

        Owner owner = this.createOwner();
        ConsumerType ctype = this.mockConsumerType(new ConsumerType(ConsumerType.ConsumerTypeEnum.CANDLEPIN));
        Consumer consumer = this.createConsumer(owner, ctype);

        Cdn cdn = new Cdn("cdn-label", "test", "url");

        when(mockCdnCurator.lookupByLabel(eq(cdn.getLabel()))).thenReturn(cdn);

        cr.exportDataAsync(null, consumer.getUuid(), cdn.getLabel(), "prefix", cdn.getUrl(), extParams);
        verify(manifestManager).generateManifestAsync(eq(consumer.getUuid()), eq(cdn.getLabel()),
            eq("prefix"), eq(cdn.getUrl()), any(Map.class));
    }

}
