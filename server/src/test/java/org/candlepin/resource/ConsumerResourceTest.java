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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.async.JobManager;
import org.candlepin.audit.Event.Target;
import org.candlepin.audit.Event.Type;
import org.candlepin.audit.EventBuilder;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.auth.Access;
import org.candlepin.auth.NoAuthPrincipal;
import org.candlepin.auth.SubResource;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.GoneException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.controller.CandlepinPoolManager;
import org.candlepin.controller.ContentAccessManager;
import org.candlepin.controller.Entitler;
import org.candlepin.controller.ManifestManager;
import org.candlepin.controller.PoolManager;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.dto.api.v1.CertificateDTO;
import org.candlepin.dto.api.v1.CertificateSerialDTO;
import org.candlepin.dto.api.v1.ComplianceStatusDTO;
import org.candlepin.dto.api.v1.ConsumerDTO;
import org.candlepin.dto.api.v1.ConsumerDTOArrayElement;
import org.candlepin.dto.api.v1.ContentAccessDTO;
import org.candlepin.dto.api.v1.KeyValueParamDTO;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Cdn;
import org.candlepin.model.CdnCurator;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerContentOverrideCurator;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.DeletedConsumer;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.model.DistributorVersionCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.GuestIdCurator;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.model.dto.Subscription;
import org.candlepin.policy.SystemPurposeComplianceRules;
import org.candlepin.policy.activationkey.ActivationKeyRules;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.policy.js.consumer.ConsumerRules;
import org.candlepin.resource.dto.AutobindData;
import org.candlepin.resource.util.CalculatedAttributesUtil;
import org.candlepin.resource.util.ConsumerBindUtil;
import org.candlepin.resource.util.ConsumerEnricher;
import org.candlepin.resource.util.ConsumerTypeValidator;
import org.candlepin.resource.util.GuestMigration;
import org.candlepin.resource.util.ResourceDateParser;
import org.candlepin.resource.validation.DTOValidator;
import org.candlepin.service.EntitlementCertServiceAdapter;
import org.candlepin.service.IdentityCertServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.test.TestUtil;
import org.candlepin.util.ContentOverrideValidator;
import org.candlepin.util.ElementTransformer;
import org.candlepin.util.FactValidator;
import org.candlepin.util.ServiceLevelValidator;
import org.candlepin.util.Util;

import com.google.inject.util.Providers;

import org.apache.commons.lang.RandomStringUtils;
import org.hibernate.mapping.Collection;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.inject.Provider;
import javax.persistence.OptimisticLockException;
import javax.ws.rs.core.Response;



/**
 * ConsumerResourceTest
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ConsumerResourceTest {

    private I18n i18n;
    private Provider<I18n> i18nProvider = () -> i18n;
    private Configuration config;
    private FactValidator factValidator;

    @Mock private ConsumerCurator consumerCurator;
    @Mock private OwnerCurator ownerCurator;
    @Mock private EntitlementCertServiceAdapter entitlementCertServiceAdapter;
    @Mock private SubscriptionServiceAdapter subscriptionServiceAdapter;
    @Mock private PoolManager poolManager;
    @Mock private EntitlementCurator entitlementCurator;
    @Mock private ComplianceRules complianceRules;
    @Mock private SystemPurposeComplianceRules systemPurposeComplianceRules;
    @Mock private ServiceLevelValidator serviceLevelValidator;
    @Mock private ActivationKeyRules activationKeyRules;
    @Mock private EventFactory eventFactory;
    @Mock private EventBuilder eventBuilder;
    @Mock private ConsumerBindUtil consumerBindUtil;
    @Mock private ConsumerEnricher consumerEnricher;
    @Mock private ConsumerTypeCurator consumerTypeCurator;
    @Mock private ContentAccessManager contentAccessManager;
    @Mock private EventSink sink;
    @Mock private EnvironmentCurator environmentCurator;
    @Mock private IdentityCertServiceAdapter identityCertServiceAdapter;
    @Mock private ActivationKeyCurator activationKeyCurator;
    @Mock private Entitler entitler;
    @Mock private ManifestManager manifestManager;
    @Mock private CdnCurator cdnCurator;
    @Mock private UserServiceAdapter userServiceAdapter;
    @Mock private DeletedConsumerCurator deletedConsumerCurator;
    @Mock private JobManager jobManager;
    @Mock private DTOValidator dtoValidator;
    @Mock private ConsumerRules consumerRules;
    @Mock private CalculatedAttributesUtil calculatedAttributesUtil;
    @Mock private DistributorVersionCurator distributorVersionCurator;
    @Mock private GuestIdCurator guestIdCurator;
    @Mock private Provider<GuestMigration> guestMigrationProvider;
    @Mock private PrincipalProvider principalProvider;
    @Mock private ConsumerContentOverrideCurator consumerContentOverrideCurator;
    @Mock private ContentOverrideValidator contentOverrideValidator;
    @Mock private ProductCurator productCurator;

    private GuestMigration testMigration;
    private Provider<GuestMigration> migrationProvider;
    private ModelTranslator translator;
    private ConsumerResource consumerResource;
    private ConsumerResource mockedConsumerResource;


    @BeforeEach
    public void setUp() {
        this.config = new CandlepinCommonTestConfig();
        this.translator = new StandardTranslator(consumerTypeCurator,
            environmentCurator,
            ownerCurator,
            productCurator);
        this.i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        when(eventBuilder.setEventData(any(Consumer.class))).thenReturn(eventBuilder);
        when(eventFactory.getEventBuilder(any(Target.class), any(Type.class))).thenReturn(eventBuilder);

        this.factValidator = new FactValidator(this.config, this.i18nProvider);

        testMigration = new GuestMigration(consumerCurator);
        migrationProvider = Providers.of(testMigration);

        this.consumerResource = new ConsumerResource(
            this.consumerCurator,
            this.consumerTypeCurator,
            this.subscriptionServiceAdapter,
            this.entitlementCurator,
            this.identityCertServiceAdapter,
            this.entitlementCertServiceAdapter,
            this.i18n,
            this.sink,
            this.eventFactory,
            this.userServiceAdapter,
            this.poolManager,
            this.consumerRules,
            this.ownerCurator,
            this.activationKeyCurator,
            this.entitler,
            this.complianceRules,
            this.systemPurposeComplianceRules,
            this.deletedConsumerCurator,
            this.environmentCurator,
            this.distributorVersionCurator,
            this.config,
            this.calculatedAttributesUtil,
            this.consumerBindUtil,
            this.manifestManager,
            this.contentAccessManager,
            this.factValidator,
            new ConsumerTypeValidator(consumerTypeCurator, i18n),
            this.consumerEnricher,
            this.guestMigrationProvider,
            this.translator,
            this.jobManager,
            this.dtoValidator,
            this.guestIdCurator,
            this.principalProvider,
            this.contentOverrideValidator,
            this.consumerContentOverrideCurator
        );

        mockedConsumerResource = Mockito.spy(consumerResource);
    }

    protected ConsumerType mockConsumerType(ConsumerType ctype) {
        if (ctype != null) {
            // Ensure the type has an ID
            if (ctype.getId() == null) {
                ctype.setId("test-ctype-" + ctype.getLabel() + "-" + TestUtil.randomInt());
            }

            when(consumerTypeCurator.getByLabel(eq(ctype.getLabel()))).thenReturn(ctype);
            when(consumerTypeCurator.getByLabel(eq(ctype.getLabel()), anyBoolean())).thenReturn(ctype);
            when(consumerTypeCurator.get(eq(ctype.getId()))).thenReturn(ctype);

            doAnswer(new Answer<ConsumerType>() {
                @Override
                public ConsumerType answer(InvocationOnMock invocation) throws Throwable {
                    Object[] args = invocation.getArguments();
                    Consumer consumer = (Consumer) args[0];
                    ConsumerTypeCurator curator = (ConsumerTypeCurator) invocation.getMock();
                    ConsumerType ctype = null;

                    if (consumer == null || consumer.getTypeId() == null) {
                        throw new IllegalArgumentException("consumer is null or lacks a type ID");
                    }

                    ctype = curator.get(consumer.getTypeId());
                    if (ctype == null) {
                        throw new IllegalStateException("No such consumer type: " + consumer.getTypeId());
                    }

                    return ctype;
                }
            }).when(consumerTypeCurator).getConsumerType(any(Consumer.class));
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

            when(ownerCurator.getByKey(eq(owner.getKey()))).thenReturn(owner);
        }

        return owner;
    }

    protected Owner createOwner() {
        return createOwner(null);
    }

    protected Owner createOwner(String key) {
        int rand = TestUtil.randomInt();
        if (key == null) {
            key = "test-owner-key-" + rand;
        }
        Owner owner = new Owner(key, "Test Owner " + rand);
        owner.setId("test-owner-" + rand);

        this.mockOwner(owner);

        return owner;
    }

    protected OwnerDTO createOwnerDTO(String key) {
        return translator.translate(createOwner(key), OwnerDTO.class);
    }

    protected Consumer mockConsumer(Consumer consumer) {
        if (consumer != null) {
            consumer.ensureUUID();

            when(consumerCurator.verifyAndLookupConsumer(eq(consumer.getUuid()))).thenReturn(consumer);

            when(consumerCurator.verifyAndLookupConsumerWithEntitlements(eq(consumer.getUuid())))
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
    public void testGetCertSerials() {
        Consumer consumer = createConsumer(createOwner());
        List<EntitlementCertificate> certificates = createEntitlementCertificates();
        List<Long> serialIds = new ArrayList<>();
        for (EntitlementCertificate ec : certificates) {
            serialIds.add(ec.getSerial().getId());
        }

        when(entitlementCertServiceAdapter.listEntitlementSerialIds(consumer)).thenReturn(serialIds);
        when(entitlementCurator.listByConsumer(consumer)).thenReturn(new ArrayList<>());

        List<CertificateSerialDTO> serials = consumerResource
            .getEntitlementCertificateSerials(consumer.getUuid());

        verifyCertificateSerialNumbers(serials);
    }

    @Test
    public void testExceptionFromCertGen() throws Exception {
        Consumer consumer = createConsumer(createOwner());

        Entitlement e = Mockito.mock(Entitlement.class);
        Pool p = Mockito.mock(Pool.class);
        Subscription s = Mockito.mock(Subscription.class);
        when(e.getPool()).thenReturn(p);
        when(p.getSubscriptionId()).thenReturn("4444");

        when(entitlementCurator.get(eq("9999"))).thenReturn(e);
        when(subscriptionServiceAdapter.getSubscription(eq("4444"))).thenReturn(s);

        when(entitlementCertServiceAdapter.generateEntitlementCert(
            any(Entitlement.class), any(Product.class)))
            .thenThrow(new IOException());

        CandlepinPoolManager poolManager = new CandlepinPoolManager(
            null, null, null, this.config, null, null, entitlementCurator,
            consumerCurator, consumerTypeCurator, null, null, null, null, null,
            activationKeyRules, null, null, null, null, null, null, null, null, null, null
        );

        ConsumerResource consumerResource = new ConsumerResource(
            this.consumerCurator,
            this.consumerTypeCurator,
            this.subscriptionServiceAdapter,
            this.entitlementCurator,
            this.identityCertServiceAdapter,
            this.entitlementCertServiceAdapter,
            this.i18n,
            this.sink,
            this.eventFactory,
            this.userServiceAdapter,
            poolManager,
            this.consumerRules,
            this.ownerCurator,
            this.activationKeyCurator,
            this.entitler,
            this.complianceRules,
            this.systemPurposeComplianceRules,
            this.deletedConsumerCurator,
            this.environmentCurator,
            this.distributorVersionCurator,
            this.config,
            this.calculatedAttributesUtil,
            this.consumerBindUtil,
            this.manifestManager,
            this.contentAccessManager,
            this.factValidator,
            new ConsumerTypeValidator(consumerTypeCurator, i18n),
            this.consumerEnricher,
            this.guestMigrationProvider,
            this.translator,
            this.jobManager,
            this.dtoValidator,
            this.guestIdCurator,
            this.principalProvider,
            this.contentOverrideValidator,
            this.consumerContentOverrideCurator
        );

        assertThrows(RuntimeException.class, () ->
            consumerResource.regenerateEntitlementCertificates(consumer.getUuid(), "9999", false)
        );
    }

    private void verifyCertificateSerialNumbers(
        List<CertificateSerialDTO> serials) {
        assertEquals(3, serials.size());
        assertTrue(serials.get(0).getSerial() > 0);
    }

    private List<EntitlementCertificate> createEntitlementCertificates() {
        return Arrays.asList(createEntitlementCertificate("key1", "cert1"),
            createEntitlementCertificate("key2", "cert2"),
            createEntitlementCertificate("key3", "cert3"));
    }


    /**
     * Test just verifies that entitler is called only once and it doesn't need
     * any other object to execute.
     */
    @Test
    public void testRegenerateEntitlementCertificateWithValidConsumer() {
        Consumer consumer = createConsumer(createOwner());

        consumerResource.regenerateEntitlementCertificates(consumer.getUuid(), null, true);
        Mockito.verify(poolManager, Mockito.times(1)).regenerateCertificatesOf(eq(consumer), eq(true));
    }

    @Test
    public void testRegenerateIdCerts() throws GeneralSecurityException, IOException {
        Consumer consumer = createConsumer(createOwner());
        consumer.setIdCert(createIdCert());
        IdentityCertificate ic = consumer.getIdCert();
        assertNotNull(ic);

        when(identityCertServiceAdapter.regenerateIdentityCert(consumer)).thenReturn(createIdCert());

        ConsumerDTO fooc = consumerResource.regenerateIdentityCertificates(consumer.getUuid());

        assertNotNull(fooc);
        CertificateDTO ic1 = fooc.getIdCert();
        assertNotNull(ic1);
        assertNotEquals(ic1.getId(), ic.getId());
    }

    @Test
    public void testIdCertGetsRegenerated() throws Exception {
        // using lconsumer simply to avoid hiding consumer. This should
        // get renamed once we refactor this test suite.
        Consumer consumer = createConsumer(createOwner());
        ComplianceStatus status = new ComplianceStatus();
        when(complianceRules.getStatus(any(Consumer.class), any(Date.class), anyBoolean()))
            .thenReturn(status);
        // cert expires today which will trigger regen
        IdentityCertificate idCert = createIdCert();
        idCert.getSerial().setId(Util.generateUniqueLong());
        consumer.setIdCert(idCert);
        BigInteger origserial = consumer.getIdCert().getSerial().getSerial();
        when(identityCertServiceAdapter.regenerateIdentityCert(consumer)).thenReturn(createIdCert());

        ConsumerDTO c = consumerResource.getConsumer(consumer.getUuid());

        assertNotEquals(c.getIdCert().getSerial().getSerial(), origserial);
    }

    @Test
    public void testIdCertDoesNotRegenerate() {
        Consumer consumer = createConsumer(createOwner());
        ComplianceStatus status = new ComplianceStatus();
        when(complianceRules.getStatus(any(Consumer.class), any(Date.class), anyBoolean()))
            .thenReturn(status);
        consumer.setIdCert(createIdCert(TestUtil.createDate(2025, 6, 9)));
        BigInteger origserial = consumer.getIdCert().getSerial().getSerial();

        ConsumerDTO c = consumerResource.getConsumer(consumer.getUuid());

        assertEquals(origserial, c.getIdCert().getSerial().getSerial());
    }

    @Test
    public void testCreatePersonConsumerWithActivationKey() {
        ConsumerType ctype = this.mockConsumerType(new ConsumerType(ConsumerTypeEnum.PERSON));

        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner, ctype);
        ConsumerDTO consumerDto = this.translator.translate(consumer, ConsumerDTO.class);

        ActivationKey ak = mock(ActivationKey.class);
        NoAuthPrincipal nap = mock(NoAuthPrincipal.class);

        when(ak.getId()).thenReturn("testKey");
        when(activationKeyCurator.getByKeyName(eq(owner), eq(owner.getKey()))).thenReturn(ak);

        assertThrows(BadRequestException.class, () ->
            consumerResource.create(consumerDto, nap, null, owner.getKey(), "testKey", true)
        );
    }

    @Test
    public void testProductNoPool() throws Exception {
        Owner o = createOwner();
        Consumer c = createConsumer(o);
        String[] prodIds = {"notthere"};

        when(subscriptionServiceAdapter.hasUnacceptedSubscriptionTerms(eq(o.getKey()))).thenReturn(false);
        when(consumerCurator.verifyAndLookupConsumerWithEntitlements(eq("fakeConsumer"))).thenReturn(c);
        when(entitler.bindByProducts(any(AutobindData.class))).thenReturn(null);
        when(ownerCurator.findOwnerById(eq(o.getId()))).thenReturn(o);

        Response r = consumerResource.bind("fakeConsumer", null, prodIds,
            null, null, null, false, null, null);
        assertEquals(null, r.getEntity());
    }

    @Test
    public void futureHealing() throws Exception {
        Owner o = createOwner();
        Consumer c = createConsumer(o);
        ConsumerCurator cc = mock(ConsumerCurator.class);
        ConsumerInstalledProduct cip = mock(ConsumerInstalledProduct.class);
        Set<ConsumerInstalledProduct> products = new HashSet<>();
        products.add(cip);

        when(ownerCurator.findOwnerById(eq(o.getId()))).thenReturn(o);
        when(cip.getProductId()).thenReturn("product-foo");
        when(subscriptionServiceAdapter.hasUnacceptedSubscriptionTerms(eq(o.getKey()))).thenReturn(false);
        when(cc.verifyAndLookupConsumerWithEntitlements(eq(c.getUuid()))).thenReturn(c);

        String dtStr = "2011-09-26T18:10:50.184081+00:00";
        Date dt = ResourceDateParser.parseDateString(dtStr);

        consumerResource.bind(c.getUuid(), null, null, null, null, null, false, dtStr, null);
        AutobindData data = AutobindData.create(c, o).on(dt);
        verify(entitler).bindByProducts(eq(data));
    }

    @Test
    public void unbindByInvalidSerialShouldFail() {
        Consumer consumer = createConsumer(createOwner());
        ConsumerCurator consumerCurator = mock(ConsumerCurator.class);
        when(consumerCurator.verifyAndLookupConsumer(eq("fake uuid"))).thenReturn(consumer);
        when(entitlementCurator.get(any(Serializable.class))).thenReturn(null);

        assertThrows(NotFoundException.class, () ->
            consumerResource.unbindBySerial("fake uuid", 1234L)
        );
    }

    /**
     * Basic test. If invalid id is given, should throw
     * {@link NotFoundException}
     */
    @Test
    public void unbindByInvalidPoolIdShouldFail() {
        Consumer consumer = createConsumer(createOwner());
        ConsumerCurator consumerCurator = mock(ConsumerCurator.class);
        when(consumerCurator.verifyAndLookupConsumer(eq("fake-uuid"))).thenReturn(consumer);
        when(entitlementCurator.listByConsumerAndPoolId(eq(consumer), any(String.class)))
            .thenReturn(new ArrayList<>());

        assertThrows(NotFoundException.class, () ->
            consumerResource.unbindByPool("fake-uuid", "Run Forest!")
        );
    }

    @Test
    public void testBindMultipleParams() throws Exception {
        Consumer c = createConsumer(createOwner());
        when(consumerCurator.verifyAndLookupConsumerWithEntitlements(eq(c.getUuid()))).thenReturn(c);
        assertThrows(BadRequestException.class, () -> consumerResource.bind(c.getUuid(), "fake pool uuid",
            new String[]{"12232"}, 1, null, null, false, null, null)
        );
    }

    @Test
    public void testBindByPoolBadConsumerUuid() throws Exception {
        Consumer c = createConsumer(createOwner());
        when(consumerCurator.verifyAndLookupConsumerWithEntitlements(eq(c.getUuid())))
            .thenThrow(new NotFoundException(""));
        assertThrows(NotFoundException.class, () -> consumerResource.bind(c.getUuid(), "fake pool uuid", null,
            null, null, null, false, null, null)
        );
    }

    /**
     * Basic test. If invalid id is given, should throw
     * {@link NotFoundException}
     */
    @Test
    public void testRegenerateEntitlementCertificatesWithInvalidConsumerId() {
        when(consumerCurator.verifyAndLookupConsumer(any(String.class)))
            .thenThrow(new NotFoundException(""));

        assertThrows(NotFoundException.class, () ->
            consumerResource.regenerateEntitlementCertificates("xyz", null, true)
        );
    }

    protected EntitlementCertificate createEntitlementCertificate(String key, String cert) {
        EntitlementCertificate toReturn = new EntitlementCertificate();
        CertificateSerial certSerial = new CertificateSerial(1L, new Date());
        toReturn.setKeyAsBytes(key.getBytes());
        toReturn.setCertAsBytes(cert.getBytes());
        toReturn.setSerial(certSerial);
        return toReturn;
    }

    @Test
    public void testNullPerson() {
        Owner owner = this.createOwner();
        ConsumerType ctype = this.mockConsumerType(new ConsumerType(ConsumerTypeEnum.PERSON));
        Consumer consumer = this.createConsumer(owner, ctype);
        ConsumerDTO consumerDto = this.translator.translate(consumer, ConsumerDTO.class);
        UserPrincipal up = mock(UserPrincipal.class);

        when(up.canAccess(eq(owner), eq(SubResource.CONSUMERS), eq(Access.CREATE))).thenReturn(true);

        // usa.findByLogin() will return null by default no need for a when
        assertThrows(NotFoundException.class, () ->
            consumerResource.create(consumerDto, up, null, owner.getKey(), null, true)
        );
    }

    @Test
    public void testCreateConsumerShouldFailOnMaxLengthOfName() {
        Owner owner = this.createOwner();

        ConsumerType ctype = this.mockConsumerType(new ConsumerType(ConsumerTypeEnum.SYSTEM));

        Consumer consumer = this.createConsumer(owner, ctype);
        consumer.setName(RandomStringUtils.randomAlphanumeric(Consumer.MAX_LENGTH_OF_CONSUMER_NAME + 1));
        ConsumerDTO consumerDto = this.translator.translate(consumer, ConsumerDTO.class);

        UserPrincipal up = mock(UserPrincipal.class);

        when(up.canAccess(eq(owner), eq(SubResource.CONSUMERS), eq(Access.CREATE))).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
            consumerResource.create(consumerDto, up, null, owner.getKey(), null, false)
        );
        assertEquals(String.format("Name of the consumer should be shorter than %d characters.",
            Consumer.MAX_LENGTH_OF_CONSUMER_NAME + 1), ex.getMessage());
    }

    @Test
    public void testGetComplianceStatusList() {
        Owner owner = this.createOwner();
        Consumer c = this.createConsumer(owner);
        Consumer c2 = this.createConsumer(owner);
        List<Consumer> consumers = new ArrayList<>();
        consumers.add(c);
        consumers.add(c2);

        List<String> uuids = new ArrayList<>();
        uuids.add(c.getUuid());
        uuids.add(c2.getUuid());
        when(consumerCurator.findByUuids(eq(uuids))).thenReturn(consumers);

        ComplianceStatus status = new ComplianceStatus();
        when(complianceRules.getStatus(any(Consumer.class), any(Date.class)))
            .thenReturn(status);

        Map<String, ComplianceStatusDTO> results = consumerResource.getComplianceStatusList(uuids);
        assertEquals(2, results.size());
        assertTrue(results.containsKey(c.getUuid()));
        assertTrue(results.containsKey(c2.getUuid()));
    }

    @Test
    public void testConsumerExistsYes() {
        when(consumerCurator.doesConsumerExist(any(String.class))).thenReturn(true);
        consumerResource.consumerExists("uuid");
    }

    @Test
    public void testConsumerExistsNo() {
        when(consumerCurator.doesConsumerExist(any(String.class))).thenReturn(false);
        assertThrows(NotFoundException.class, () -> consumerResource.consumerExists("uuid"));
    }

    @Test
    public void testFetchAllConsumers() {
        assertThrows(BadRequestException.class, () ->
            consumerResource.list(null, null, null, null, null, null, null)
        );
    }

    @Test
    public void testFetchAllConsumersForUser() {
        ArrayList<Consumer> consumers = new ArrayList<>();

        CandlepinQuery cqmock = mock(CandlepinQuery.class);
        when(cqmock.list()).thenReturn(consumers);
        when(cqmock.iterator()).thenReturn(consumers.iterator());
        when(consumerCurator.searchOwnerConsumers(
            nullable(Owner.class), anyString(),
            (java.util.Collection<ConsumerType>) nullable(Collection.class),
            nullable(List.class), nullable(List.class), nullable(List.class), anyList(),
            anyList(), anyList())).thenReturn(cqmock);
        when(cqmock.transform(any(ElementTransformer.class))).thenReturn(cqmock);

        List<ConsumerDTOArrayElement> result = consumerResource
            .list("TaylorSwift", null, null, null, null, null, null)
            .list();
        assertEquals(consumers, result);
    }

    public void testFetchAllConsumersForOwner() {
        ArrayList<Consumer> consumers = new ArrayList<>();
        CandlepinQuery cqmock = mock(CandlepinQuery.class);
        when(cqmock.list()).thenReturn(consumers);
        when(cqmock.iterator()).thenReturn(consumers.iterator());

        when(ownerCurator.getByKey(eq("taylorOwner"))).thenReturn(new Owner());
        when(consumerCurator.searchOwnerConsumers(
            any(Owner.class), anyString(), (java.util.Collection<ConsumerType>) any(Collection.class),
            anyList(), anyList(), anyList(), anyList(), anyList(),
            anyList())).thenReturn(cqmock);

        List<ConsumerDTOArrayElement> result = consumerResource.list(null, null, "taylorOwner",
            null, null, null, null).list();
        assertEquals(consumers, result);
    }

    @Test
    public void testFetchAllConsumersForEmptyUUIDs() {
        assertThrows(BadRequestException.class, () ->
            consumerResource.list(null, null, null, new ArrayList<>(), null, null, null)
        );
    }

    @Test
    public void testFetchAllConsumersForSomeUUIDs() {
        ArrayList<Consumer> consumers = new ArrayList<>();
        CandlepinQuery cqmock = mock(CandlepinQuery.class);
        when(cqmock.list()).thenReturn(consumers);
        when(cqmock.iterator()).thenReturn(consumers.iterator());

        when(consumerCurator.searchOwnerConsumers(
            nullable(Owner.class),
            nullable(String.class),
            (java.util.Collection<ConsumerType>) nullable(Collection.class),
            anyList(),
            nullable(List.class),
            nullable(List.class),
            anyList(),
            anyList(),
            anyList())
        ).thenReturn(cqmock);
        when(cqmock.transform(any(ElementTransformer.class))).thenReturn(cqmock);

        List<String> uuids = new ArrayList<>();
        uuids.add("swiftuuid");
        List<ConsumerDTOArrayElement> result = consumerResource.list(null, null,
            null, uuids, null, null, null).list();
        assertEquals(consumers, result);
    }

    @Test
    public void testcheckForGuestsMigrationSerialList() {
        Consumer consumer = createConsumer(createOwner());
        List<EntitlementCertificate> certificates = createEntitlementCertificates();

        when(entitlementCertServiceAdapter.listForConsumer(consumer)) .thenReturn(certificates);
        when(consumerCurator.verifyAndLookupConsumer(consumer.getUuid())).thenReturn(consumer);
        when(entitlementCurator.listByConsumer(consumer)).thenReturn(new ArrayList<>());

        mockedConsumerResource.getEntitlementCertificateSerials(consumer.getUuid());
        verify(mockedConsumerResource).revokeOnGuestMigration(consumer);
    }

    @Test
    public void testCheckForGuestsMigrationCertList() {
        Consumer consumer = createConsumer(createOwner());
        List<EntitlementCertificate> certificates = createEntitlementCertificates();

        when(entitlementCertServiceAdapter.listForConsumer(consumer)) .thenReturn(certificates);
        when(consumerCurator.verifyAndLookupConsumer(consumer.getUuid())).thenReturn(consumer);
        when(entitlementCurator.listByConsumer(consumer)).thenReturn(new ArrayList<>());

        GuestMigration migrationSpy = Mockito.spy(testMigration);
        migrationProvider = Providers.of(migrationSpy);

        mockedConsumerResource.getEntitlementCertificates(consumer.getUuid(), "123");
        verify(mockedConsumerResource).revokeOnGuestMigration(consumer);
    }

    @Test
    public void testNoDryBindWhenAutobindDisabledForOwner() {
        Owner owner = createOwner();
        owner.setId(TestUtil.randomString());
        Consumer consumer = createConsumer(owner);
        owner.setAutobindDisabled(true);
        when(consumerCurator.verifyAndLookupConsumer(eq(consumer.getUuid()))).thenReturn(consumer);
        when(ownerCurator.findOwnerById(eq(owner.getId()))).thenReturn(owner);

        try {
            consumerResource.dryBind(consumer.getUuid(), "some-sla");
            fail("Should have thrown a BadRequestException.");
        }
        catch (BadRequestException e) {
            assertEquals("Organization \"" + owner.getKey() + "\" has auto-attach disabled.", e.getMessage());
        }
    }

    @Test
    public void testAsyncExport() throws Exception {
        List<KeyValueParamDTO> extParams = new ArrayList<>();
        Owner owner = this.createOwner();
        owner.setId(TestUtil.randomString());
        when(ownerCurator.findOwnerById(eq(owner.getId()))).thenReturn(owner);
        ConsumerType ctype = this.mockConsumerType(new ConsumerType(ConsumerType.ConsumerTypeEnum.CANDLEPIN));
        Consumer consumer = this.createConsumer(owner, ctype);

        Cdn cdn = new Cdn("cdn-label", "test", "url");

        when(cdnCurator.getByLabel(eq(cdn.getLabel()))).thenReturn(cdn);

        consumerResource.exportDataAsync(null, consumer.getUuid(), cdn.getLabel(),
            "prefix", cdn.getUrl(), extParams);
        verify(manifestManager).generateManifestAsync(eq(consumer.getUuid()), eq(owner),
            eq(cdn.getLabel()), eq("prefix"), eq(cdn.getUrl()), anyMap());
    }

    @Test
    public void deleteConsumerThrowsGoneExceptionIfConsumerDoesNotExistOnInitialLookup() {
        String targetConsumerUuid = "my-test-consumer";
        when(consumerCurator.findByUuid(eq(targetConsumerUuid))).thenReturn(null);

        UserPrincipal uap = mock(UserPrincipal.class);
        when(uap.canAccess(any(Object.class), any(SubResource.class), any(Access.class)))
            .thenReturn(Boolean.TRUE);

        assertThrows(GoneException.class, () -> consumerResource.deleteConsumer(targetConsumerUuid, uap));
    }

    @Test
    public void deleteConsuemrThrowsGoneExceptionWhenLockAquisitionFailsDueToConsumerAlreadyDeleted() {
        Consumer consumer = createConsumer();
        when(consumerCurator.findByUuid(eq(consumer.getUuid()))).thenReturn(consumer);
        when(consumerCurator.lock(eq(consumer))).thenThrow(OptimisticLockException.class);
        when(deletedConsumerCurator.findByConsumerUuid(eq(consumer.getUuid())))
            .thenReturn(new DeletedConsumer());

        UserPrincipal uap = mock(UserPrincipal.class);
        when(uap.canAccess(any(Object.class), any(SubResource.class), any(Access.class)))
            .thenReturn(Boolean.TRUE);

        assertThrows(GoneException.class, () -> consumerResource.deleteConsumer(consumer.getUuid(), uap));
    }

    @Test
    public void deleteConsuemrReThrowsOLEWhenLockAquisitionFailsWithoutConsumerHavingBeenDeleted() {
        Consumer consumer = createConsumer();
        when(consumerCurator.findByUuid(eq(consumer.getUuid()))).thenReturn(consumer);
        when(consumerCurator.lock(eq(consumer))).thenThrow(OptimisticLockException.class);
        when(deletedConsumerCurator.findByConsumerUuid(eq(consumer.getUuid()))).thenReturn(null);

        UserPrincipal uap = mock(UserPrincipal.class);
        when(uap.canAccess(any(Object.class), any(SubResource.class), any(Access.class)))
            .thenReturn(Boolean.TRUE);

        assertThrows(OptimisticLockException.class, () ->
            consumerResource.deleteConsumer(consumer.getUuid(), uap)
        );
    }


    @Test
    void shouldThrowWhenConsumerNotFound() {
        when(consumerCurator.verifyAndLookupConsumer(anyString()))
            .thenThrow(NotFoundException.class);

        Assertions.assertThrows(NotFoundException.class,
            () -> consumerResource.getContentAccessForConsumer("test_uuid"));
    }

    @Test
    void usesDefaultWhenNoCAAvailable() {
        String expectedMode = ContentAccessManager.ContentAccessMode.getDefault().toDatabaseValue();
        List<String> expectedModeList = Collections.singletonList(expectedMode);
        Consumer consumer = createConsumer(createOwner());
        when(consumerCurator.verifyAndLookupConsumer(anyString()))
            .thenReturn(consumer);

        ContentAccessDTO contentAccess = consumerResource
            .getContentAccessForConsumer("test_consumer_uuid");

        assertEquals(expectedMode, contentAccess.getContentAccessMode());
        assertEquals(expectedModeList, contentAccess.getContentAccessModeList());
    }

    @Test
    void usesConsumersCAModeWhenAvailable() {
        String expectedMode = "consumer-ca-mode";
        Consumer consumer = createConsumer(createOwner());
        consumer.setContentAccessMode(expectedMode);
        when(consumerCurator.verifyAndLookupConsumer(anyString()))
            .thenReturn(consumer);

        ContentAccessDTO contentAccess = consumerResource
            .getContentAccessForConsumer("test-uuid");

        assertEquals(expectedMode, contentAccess.getContentAccessMode());
    }

    @Test
    void usesOwnersCAModeWhenConsumersCAModeNotAvailable() {
        String expectedMode = "owner-ca-mode";
        Consumer consumer = createConsumer(createOwner());
        consumer.setContentAccessMode(null);
        consumer.getOwner().setContentAccessMode(expectedMode);
        when(consumerCurator.verifyAndLookupConsumer(anyString()))
            .thenReturn(consumer);

        ContentAccessDTO contentAccess = consumerResource
            .getContentAccessForConsumer("test-uuid");

        assertEquals(expectedMode, contentAccess.getContentAccessMode());
    }

    @Test
    void usesOwnersCAModeListWhenAvailable() {
        String expectedMode = "owner-ca-mode-list";
        List<String> expectedModeList = Collections.singletonList(expectedMode);
        Consumer consumer = createConsumer(createOwner());
        consumer.getOwner().setContentAccessModeList(expectedMode);
        when(consumerCurator.verifyAndLookupConsumer(anyString()))
            .thenReturn(consumer);

        ContentAccessDTO contentAccess = consumerResource
            .getContentAccessForConsumer("test-uuid");

        assertEquals(expectedModeList, contentAccess.getContentAccessModeList());
    }

}
