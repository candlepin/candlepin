/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.async.JobManager;
import org.candlepin.audit.Event.Target;
import org.candlepin.audit.Event.Type;
import org.candlepin.audit.EventBuilder;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.auth.Access;
import org.candlepin.auth.AnonymousCloudConsumerPrincipal;
import org.candlepin.auth.ConsumerPrincipal;
import org.candlepin.auth.Principal;
import org.candlepin.auth.SubResource;
import org.candlepin.auth.TrustedUserPrincipal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.config.Configuration;
import org.candlepin.config.TestConfig;
import org.candlepin.controller.AutobindDisabledForOwnerException;
import org.candlepin.controller.AutobindHypervisorDisabledException;
import org.candlepin.controller.ContentAccessManager;
import org.candlepin.controller.ContentAccessMode;
import org.candlepin.controller.EntitlementCertificateService;
import org.candlepin.controller.Entitler;
import org.candlepin.controller.ManifestManager;
import org.candlepin.controller.PoolManager;
import org.candlepin.controller.PoolService;
import org.candlepin.controller.RefresherFactory;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.dto.api.server.v1.CertificateDTO;
import org.candlepin.dto.api.server.v1.CertificateSerialDTO;
import org.candlepin.dto.api.server.v1.ComplianceStatusDTO;
import org.candlepin.dto.api.server.v1.ConsumerDTO;
import org.candlepin.dto.api.server.v1.ConsumerDTOArrayElement;
import org.candlepin.dto.api.server.v1.ContentAccessDTO;
import org.candlepin.dto.api.server.v1.ContentOverrideDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.ExceptionMessage;
import org.candlepin.exceptions.GoneException;
import org.candlepin.exceptions.IseException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.exceptions.TooManyRequestsException;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.AnonymousCloudConsumer;
import org.candlepin.model.AnonymousCloudConsumerCurator;
import org.candlepin.model.AnonymousContentAccessCertificate;
import org.candlepin.model.AnonymousContentAccessCertificateCurator;
import org.candlepin.model.Cdn;
import org.candlepin.model.CdnCurator;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerContentOverride;
import org.candlepin.model.ConsumerContentOverrideCurator;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerCurator.ConsumerQueryArguments;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.ContentAccessPayload;
import org.candlepin.model.DeletedConsumer;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.model.DistributorVersionCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCertificate;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EnvironmentContentCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.SCACertificate;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.model.dto.Subscription;
import org.candlepin.paging.PageRequest;
import org.candlepin.pki.certs.AnonymousCertificateGenerator;
import org.candlepin.pki.certs.ConcurrentContentPayloadCreationException;
import org.candlepin.pki.certs.IdentityCertificateGenerator;
import org.candlepin.pki.certs.SCACertificateGenerator;
import org.candlepin.policy.SystemPurposeComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.policy.js.consumer.ConsumerRules;
import org.candlepin.resource.dto.AutobindData;
import org.candlepin.resource.dto.ContentAccessListing;
import org.candlepin.resource.util.CalculatedAttributesUtil;
import org.candlepin.resource.util.ConsumerBindUtil;
import org.candlepin.resource.util.ConsumerCloudDataBuilder;
import org.candlepin.resource.util.ConsumerEnricher;
import org.candlepin.resource.util.ConsumerTypeValidator;
import org.candlepin.resource.util.GuestMigration;
import org.candlepin.resource.validation.DTOValidator;
import org.candlepin.service.EntitlementCertServiceAdapter;
import org.candlepin.service.OwnerServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.service.exception.product.ProductServiceException;
import org.candlepin.service.exception.subscription.SubscriptionServiceException;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.ContentOverrideValidator;
import org.candlepin.util.FactValidator;
import org.candlepin.util.Util;

import org.apache.commons.lang3.RandomStringUtils;
import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.spi.HttpRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Provider;
import javax.persistence.OptimisticLockException;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ConsumerResourceTest {

    private static final int CONTENT_PAYLOAD_CREATION_EXCEPTION_RETRY_AFTER_TIME = 2;
    private static final String SINCE_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss z";

    private static Locale defaultLocale;

    private I18n i18n;
    private Provider<I18n> i18nProvider = () -> i18n;
    private Configuration config;
    private FactValidator factValidator;

    @Mock
    private ConsumerCurator consumerCurator;
    @Mock
    private OwnerCurator ownerCurator;
    @Mock
    private EntitlementCertServiceAdapter entitlementCertServiceAdapter;
    @Mock
    private SubscriptionServiceAdapter subscriptionServiceAdapter;
    @Mock
    private PoolManager poolManager;
    @Mock
    private RefresherFactory refresherFactory;
    @Mock
    private EntitlementCurator entitlementCurator;
    @Mock
    private ComplianceRules complianceRules;
    @Mock
    private SystemPurposeComplianceRules systemPurposeComplianceRules;
    @Mock
    private EventFactory eventFactory;
    @Mock
    private EventBuilder eventBuilder;
    @Mock
    private ConsumerBindUtil consumerBindUtil;
    @Mock
    private ConsumerEnricher consumerEnricher;
    @Mock
    private ConsumerTypeCurator consumerTypeCurator;
    @Mock
    private ContentAccessManager contentAccessManager;
    @Mock
    private EventSink sink;
    @Mock
    private EnvironmentCurator environmentCurator;
    @Mock
    private IdentityCertificateGenerator identityCertificateGenerator;
    @Mock
    private ActivationKeyCurator activationKeyCurator;
    @Mock
    private Entitler entitler;
    @Mock
    private ManifestManager manifestManager;
    @Mock
    private CdnCurator cdnCurator;
    @Mock
    private UserServiceAdapter userServiceAdapter;
    @Mock
    private DeletedConsumerCurator deletedConsumerCurator;
    @Mock
    private JobManager jobManager;
    @Mock
    private DTOValidator dtoValidator;
    @Mock
    private ConsumerRules consumerRules;
    @Mock
    private CalculatedAttributesUtil calculatedAttributesUtil;
    @Mock
    private DistributorVersionCurator distributorVersionCurator;
    @Mock
    private Provider<GuestMigration> guestMigrationProvider;
    @Mock
    private PrincipalProvider principalProvider;
    @Mock
    private ConsumerContentOverrideCurator consumerContentOverrideCurator;
    @Mock
    private ContentOverrideValidator contentOverrideValidator;
    @Mock
    private EnvironmentContentCurator environmentContentCurator;
    @Mock
    private EntitlementCertificateService entCertService;
    @Mock
    private PoolService poolService;
    @Mock
    private AnonymousCloudConsumerCurator anonymousConsumerCurator;
    @Mock
    private AnonymousContentAccessCertificateCurator anonymousCertCurator;
    @Mock
    private OwnerServiceAdapter ownerService;
    @Mock
    private SCACertificateGenerator scaCertificateGenerator;
    @Mock
    private AnonymousCertificateGenerator anonymousCertificateGenerator;
    @Mock
    private Principal principal;
    @Mock
    private ConsumerCloudDataBuilder consumerCloudDataBuilder;

    private ModelTranslator translator;
    private ConsumerResource consumerResource;
    private ConsumerResource mockedConsumerResource;

    @BeforeAll
    static void beforeAll() {
        defaultLocale = Locale.getDefault();
    }

    @BeforeEach
    public void setUp() {
        this.config = TestConfig.defaults();
        this.translator = new StandardTranslator(consumerTypeCurator,
            environmentCurator,
            ownerCurator);
        this.i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        when(eventBuilder.setEventData(any(Consumer.class))).thenReturn(eventBuilder);
        when(eventFactory.getEventBuilder(any(Target.class), any(Type.class))).thenReturn(eventBuilder);

        this.factValidator = new FactValidator(this.config, this.i18nProvider);

        this.consumerResource = this.buildConsumerResource();
        mockedConsumerResource = Mockito.spy(consumerResource);

        ResteasyContext.pushContext(Principal.class, principal);

        when(this.principal.canAccess(any(Object.class), any(SubResource.class), any(Access.class)))
            .thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        Locale.setDefault(defaultLocale);
    }

    private ConsumerResource buildConsumerResource() {
        return new ConsumerResource(
            this.consumerCurator,
            this.consumerTypeCurator,
            this.subscriptionServiceAdapter,
            this.entitlementCurator,
            this.identityCertificateGenerator,
            this.entitlementCertServiceAdapter,
            this.i18n,
            this.sink,
            this.eventFactory,
            this.userServiceAdapter,
            this.poolManager,
            this.refresherFactory,
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
            this.principalProvider,
            this.contentOverrideValidator,
            this.consumerContentOverrideCurator,
            this.entCertService,
            this.poolService,
            this.environmentContentCurator,
            this.anonymousConsumerCurator,
            this.anonymousCertCurator,
            this.ownerService,
            this.scaCertificateGenerator,
            this.anonymousCertificateGenerator,
            this.consumerCloudDataBuilder
        );
    }

    protected ConsumerType buildConsumerType() {
        int rnd = TestUtil.randomInt();

        ConsumerType type = new ConsumerType("consumer_type-" + rnd);
        type.setId("test-ctype-" + rnd);

        return this.mockConsumerType(type);
    }

    protected ConsumerType mockConsumerType(ConsumerType ctype) {
        if (ctype != null) {
            // Ensure the type has an ID
            if (ctype.getId() == null) {
                ctype.setId("test-ctype-" + ctype.getLabel() + "-" + TestUtil.randomInt());
            }

            when(consumerTypeCurator.getByLabel(ctype.getLabel())).thenReturn(ctype);
            when(consumerTypeCurator.getByLabel(eq(ctype.getLabel()), anyBoolean())).thenReturn(ctype);
            when(consumerTypeCurator.get(ctype.getId())).thenReturn(ctype);

            doAnswer(new Answer<ConsumerType>() {
                @Override
                public ConsumerType answer(InvocationOnMock invocation) {
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

            when(ownerCurator.getByKey(owner.getKey())).thenReturn(owner);
        }

        return owner;
    }

    /**
     * Creates SCA (Simple Content Access) owner with generated key.
     *
     * @return a newly created and mocked {@code Owner} instance
     */
    protected Owner createOwner() {
        return createOwner(null);
    }

    /**
     * Creates SCA (Simple Content Access) owner with the specified key or a generated key
     * if none is provided.
     *
     * @param key the key for the owner; if {@code null}, a random key will be generated
     * @return a newly created and mocked {@code Owner} instance
     */
    protected Owner createOwner(String key) {
        int rand = TestUtil.randomInt();
        if (key == null) {
            key = "test-owner-key-" + rand;
        }

        Owner owner = new Owner()
            .setId("test-owner-" + rand)
            .setKey(key)
            .setDisplayName("Test Owner " + rand);

        this.mockOwner(owner);

        return owner;
    }

    /**
     * Creates a non-SCA (Simple Content Access) owner with generated key.
     *
     * @return a newly created and mocked {@code Owner} instance
     */
    protected Owner createNonSCAOwner() {
        return createNonSCAOwner(null);
    }

    /**
     * Creates a non-SCA (Simple Content Access) owner with the specified key or
     * a generated key if none is provided.
     *
     * @param key the key for the owner; if {@code null}, a random key will be generated
     * @return a newly created and mocked {@code Owner} instance
     */
    protected Owner createNonSCAOwner(String key) {
        int rand = TestUtil.randomInt();
        if (key == null) {
            key = "test-owner-key-" + rand;
        }

        Owner owner = new Owner()
            .setId("test-owner-" + rand)
            .setKey(key)
            .setDisplayName("Test Owner " + rand)
            .setContentAccessMode(ContentAccessMode.ENTITLEMENT.toDatabaseValue());

        this.mockOwner(owner);

        return owner;
    }

    protected Consumer mockConsumer(Consumer consumer) {
        if (consumer != null) {
            consumer.ensureUUID();

            when(consumerCurator.verifyAndLookupConsumer(consumer.getUuid())).thenReturn(consumer);

            when(consumerCurator.verifyAndLookupConsumerWithEntitlements(consumer.getUuid()))
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
        Consumer consumer = this.mockConsumer(new Consumer()
            .setName("test-consumer")
            .setUsername("test-user")
            .setOwner(owner)
            .setType(ctype));

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

        Random random = new Random();
        List<Long> expectedEntSerials = List.of(
            random.nextLong(),
            random.nextLong(),
            random.nextLong());

        doReturn(expectedEntSerials).when(entitlementCertServiceAdapter).listEntitlementSerialIds(consumer);
        doReturn(List.of()).when(entitlementCurator).listByConsumer(consumer);

        long expectedSCASerialId = random.nextLong();
        CertificateSerial serial = new CertificateSerial();
        serial.setId(expectedSCASerialId);

        SCACertificate x509Certificate = new SCACertificate();
        x509Certificate.setSerial(serial);

        doReturn(x509Certificate).when(scaCertificateGenerator).getX509Certificate(consumer);

        List<CertificateSerialDTO> serials = consumerResource
            .getEntitlementCertificateSerials(consumer.getUuid());

        assertThat(serials)
            .isNotNull()
            .hasSize(expectedEntSerials.size() + 1) // The +1 is the SCA certificate serial
            .extracting(CertificateSerialDTO::getSerial)
            .containsAll(expectedEntSerials)
            .contains(expectedSCASerialId);
    }

    @Test
    public void testGetCertSerialsWithNoSCACertSerial() {
        Consumer consumer = createConsumer(createOwner());

        Random random = new Random();
        List<Long> expectedEntSerials = List.of(
            random.nextLong(),
            random.nextLong(),
            random.nextLong());

        doReturn(expectedEntSerials).when(entitlementCertServiceAdapter).listEntitlementSerialIds(consumer);
        doReturn(List.of()).when(entitlementCurator).listByConsumer(consumer);
        doReturn(null).when(scaCertificateGenerator).getX509Certificate(consumer);

        List<CertificateSerialDTO> serials = consumerResource
            .getEntitlementCertificateSerials(consumer.getUuid());

        assertThat(serials)
            .isNotNull()
            .hasSize(expectedEntSerials.size())
            .extracting(CertificateSerialDTO::getSerial)
            .containsExactlyInAnyOrderElementsOf(expectedEntSerials);
    }

    @Test
    public void testExceptionFromCertGen() throws Exception {
        Consumer consumer = createConsumer(createOwner());

        Entitlement e = Mockito.mock(Entitlement.class);
        Pool p = Mockito.mock(Pool.class);
        Subscription s = Mockito.mock(Subscription.class);
        when(e.getPool()).thenReturn(p);
        when(p.getSubscriptionId()).thenReturn("4444");

        doThrow(RuntimeException.class).when(entCertService)
            .regenerateCertificatesOf(any(Entitlement.class), anyBoolean());
        when(entitlementCurator.get("9999")).thenReturn(e);
        when(subscriptionServiceAdapter.getSubscription("4444")).thenReturn(s);
        when(entitlementCertServiceAdapter.generateEntitlementCert(
            any(Entitlement.class), any(Product.class)))
            .thenThrow(new IOException());

        ConsumerResource consumerResource = new ConsumerResource(
            this.consumerCurator,
            this.consumerTypeCurator,
            this.subscriptionServiceAdapter,
            this.entitlementCurator,
            this.identityCertificateGenerator,
            this.entitlementCertServiceAdapter,
            this.i18n,
            this.sink,
            this.eventFactory,
            this.userServiceAdapter,
            this.poolManager,
            this.refresherFactory,
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
            this.principalProvider,
            this.contentOverrideValidator,
            this.consumerContentOverrideCurator,
            this.entCertService,
            this.poolService,
            this.environmentContentCurator,
            this.anonymousConsumerCurator,
            this.anonymousCertCurator,
            this.ownerService,
            this.scaCertificateGenerator,
            this.anonymousCertificateGenerator,
            this.consumerCloudDataBuilder
        );

        // Fixme throw custom exception from generator instead of generic RuntimeException
        assertThrows(RuntimeException.class, () -> consumerResource
            .regenerateEntitlementCertificates(consumer.getUuid(), "9999", false, false));
    }

    private List<EntitlementCertificate> createEntitlementCertificates() {
        return Arrays.asList(createEntitlementCertificate("key1", "cert1"),
            createEntitlementCertificate("key2", "cert2"),
            createEntitlementCertificate("key3", "cert3"));
    }

    /**
     * Test just verifies that entitler is called only once and it doesn't need any other object to
     * execute.
     */
    @Test
    public void testRegenerateEntitlementCertificateWithValidConsumer() {
        Consumer consumer = createConsumer(createOwner());

        consumerResource.regenerateEntitlementCertificates(consumer.getUuid(), null, true, false);
        verify(entCertService, Mockito.times(1)).regenerateCertificatesOf(consumer, true);
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
            consumerResource.regenerateEntitlementCertificates("xyz", null, true, false)
        );
    }

    /*
     * Tests for entitlement revocation before regeneration in SCA mode
     *
     * The table of these tests is as follows:
     *
     * Org CA mode Consumer CA mode cleanupEntitlements expected behavior ==============
     * ================== ====================== ================= entitlement unset true retain sca
     * unset true > revoke entitlement entitlement true retain sca entitlement true > revoke entitlement
     * sca true retain sca sca true > revoke * false retain * null retain
     *
     * In SCA mode (either consumer or org) and the cleanupEntitlements is enabled, entitlements should
     * be revoked before regeneration. If the CA mode resolves to entitlement mode or the flag is either
     * set to "false" or not specified, entitlements should be retained.
     *
     * Note that the lazyRegen flag should have no affect on the above table.
     *
     * Unfortunately, this test suite is setup to heavily rely on narrow mocks, so we are verifying this
     * behavior using call verifications. This should be corrected at some point in the future.
     */
    public static Stream<Arguments> scaModeEntitlementCleanupProvider() {
        String entitlementMode = ContentAccessMode.ENTITLEMENT.toDatabaseValue();
        String scaMode = ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue();

        return Stream.of(
            Arguments.of(scaMode, null, false),
            Arguments.of(scaMode, null, true),
            Arguments.of(scaMode, null, null),
            Arguments.of(scaMode, entitlementMode, false),
            Arguments.of(scaMode, entitlementMode, true),
            Arguments.of(scaMode, entitlementMode, null),
            Arguments.of(scaMode, scaMode, false),
            Arguments.of(scaMode, scaMode, true),
            Arguments.of(scaMode, scaMode, null));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}, {1}, {2}")
    @MethodSource("scaModeEntitlementCleanupProvider")
    public void testRegenerateEntitlementCertificatesRevokesEntitlementsInSCAWithEntitlementCleanupEnabled(
        String orgCAMode, String consumerCAMode, Boolean lazyRegen) {

        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner);

        // Set up org and consumer content access modes
        owner.setContentAccessModeList(String.join(",", orgCAMode, consumerCAMode));
        owner.setContentAccessMode(orgCAMode);
        consumer.setContentAccessMode(consumerCAMode);

        ConsumerResource resource = this.buildConsumerResource();
        resource.regenerateEntitlementCertificates(consumer.getUuid(), null, lazyRegen, true);

        // The consumer is operating in SCA mode and we've specified entitlement cleanup, so we
        // expect that the revocation step is invoked.
        verify(this.poolService, Mockito.times(1)).revokeAllEntitlements(eq(consumer), anyBoolean());
    }

    public static Stream<Arguments> entitlementModeConfigurationProvider() {
        String entitlementMode = ContentAccessMode.ENTITLEMENT.toDatabaseValue();
        String scaMode = ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue();

        return Stream.of(
            Arguments.of(entitlementMode, null, true),
            Arguments.of(entitlementMode, null, false),
            Arguments.of(entitlementMode, null, null),
            Arguments.of(entitlementMode, scaMode, false),
            Arguments.of(entitlementMode, scaMode, true),
            Arguments.of(entitlementMode, scaMode, null),
            Arguments.of(entitlementMode, entitlementMode, true),
            Arguments.of(entitlementMode, entitlementMode, false),
            Arguments.of(entitlementMode, entitlementMode, null));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}, {1}, {2}")
    @MethodSource("entitlementModeConfigurationProvider")
    public void testRegenerateEntitlementCertificatesRetainsEntitlementsInEntitlementMode(
        String orgCAMode, String consumerCAMode, Boolean lazyRegen) {

        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner);

        // Set up org and consumer content access modes
        owner.setContentAccessModeList(String.join(",", orgCAMode, consumerCAMode));
        owner.setContentAccessMode(orgCAMode);
        consumer.setContentAccessMode(consumerCAMode);

        ConsumerResource resource = this.buildConsumerResource();
        resource.regenerateEntitlementCertificates(consumer.getUuid(), null, lazyRegen, true);

        // Since the consumer is operating in entitlement mode, entitlement revocation should not
        // be invoked, even though we've specified entitlement cleanup
        verify(this.poolService, Mockito.times(0)).revokeAllEntitlements(eq(consumer), anyBoolean());
    }

    public static Stream<Arguments> allCAModesConfigurationProvider() {
        String entitlementMode = ContentAccessMode.ENTITLEMENT.toDatabaseValue();
        String scaMode = ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue();

        List<String> orgCAModeList = List.of(entitlementMode, scaMode);
        List<String> consumerCAModeList = Arrays.asList(entitlementMode, scaMode, null);
        List<Boolean> lazyRegenList = Arrays.asList(true, false, null);

        List<Arguments> arguments = new ArrayList<>();

        for (String orgCAMode : orgCAModeList) {
            for (String consumerCAMode : consumerCAModeList) {
                for (Boolean lazyRegen : lazyRegenList) {
                    arguments.add(Arguments.of(orgCAMode, consumerCAMode, lazyRegen));
                }
            }
        }

        return arguments.stream();
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}, {1}, {2}")
    @MethodSource("allCAModesConfigurationProvider")
    public void testRegenerateEntitlementCertificatesRetainsEntitlementsWithSCAEntitlementCleanupDisabled(
        String orgCAMode, String consumerCAMode, Boolean lazyRegen) {

        Owner owner = this.createOwner();
        Consumer consumer = this.createConsumer(owner);

        // Set up org and consumer content access modes
        owner.setContentAccessModeList(String.join(",", orgCAMode, consumerCAMode));
        owner.setContentAccessMode(orgCAMode);
        consumer.setContentAccessMode(consumerCAMode);

        ConsumerResource resource = this.buildConsumerResource();
        resource.regenerateEntitlementCertificates(consumer.getUuid(), null, lazyRegen, false);

        // We're explicitly telling the regeneration op to not clean up entitlements, so we should not
        // be invoking the revocation logic no matter what other options we throw at it.
        verify(this.poolService, Mockito.times(0)).revokeAllEntitlements(eq(consumer), anyBoolean());
    }

    @Test
    public void testRegenerateIdCerts() {
        Consumer consumer = createConsumer(createOwner());
        consumer.setIdCert(createIdCert());
        IdentityCertificate ic = consumer.getIdCert();
        assertNotNull(ic);

        when(identityCertificateGenerator.regenerate(consumer)).thenReturn(createIdCert());

        ConsumerDTO fooc = consumerResource.regenerateIdentityCertificates(consumer.getUuid());

        assertNotNull(fooc);
        CertificateDTO ic1 = fooc.getIdCert();
        assertNotNull(ic1);
        assertNotEquals(ic1.getId(), ic.getId());
    }

    @Test
    public void expiredIdCertGetsRegenerated() {
        Consumer consumer = createConsumer(createOwner());
        ComplianceStatus status = new ComplianceStatus();
        when(complianceRules.getStatus(any(Consumer.class), any(Date.class), anyBoolean()))
            .thenReturn(status);
        // cert expires today which will trigger regen
        IdentityCertificate idCert = createIdCert();
        consumer.setIdCert(idCert);
        long origserial = consumer.getIdCert().getSerial().getSerial().longValue();
        when(identityCertificateGenerator.regenerate(consumer)).thenReturn(createIdCert());

        ConsumerDTO c = consumerResource.getConsumer(consumer.getUuid());

        assertNotEquals(c.getIdCert().getSerial().getSerial(), origserial);
    }

    @Test
    public void validIdCertDoesNotRegenerate() {
        Consumer consumer = createConsumer(createOwner());
        ComplianceStatus status = new ComplianceStatus();
        when(complianceRules.getStatus(any(Consumer.class), any(Date.class), anyBoolean()))
            .thenReturn(status);
        consumer.setIdCert(createIdCert(TestUtil.createDateOffset(1, 0, 0)));
        long origSerial = consumer.getIdCert().getSerial().getSerial().longValue();

        ConsumerDTO c = consumerResource.getConsumer(consumer.getUuid());

        assertEquals(origSerial, c.getIdCert().getSerial().getSerial());
    }

    @Test
    public void doesGenerateMissingIdCert() {
        Consumer consumer = createConsumer(createOwner());
        ComplianceStatus status = new ComplianceStatus();
        when(complianceRules.getStatus(any(Consumer.class), any(Date.class), anyBoolean()))
            .thenReturn(status);
        when(identityCertificateGenerator.regenerate(consumer)).thenReturn(createIdCert());

        ConsumerDTO c = consumerResource.getConsumer(consumer.getUuid());

        assertNotNull(c.getIdCert());
    }

    @Test
    public void testUnacceptedSubscriptionTerms() throws Exception {
        Owner o = createNonSCAOwner();
        Consumer c = createConsumer(o);
        String[] prodIds = {"notthere"};

        when(subscriptionServiceAdapter.hasUnacceptedSubscriptionTerms(o.getKey())).thenReturn(true);
        when(consumerCurator.verifyAndLookupConsumerWithEntitlements("fakeConsumer")).thenReturn(c);
        when(entitler.bindByProducts(any(AutobindData.class))).thenReturn(null);
        when(ownerCurator.findOwnerById(o.getId())).thenReturn(o);

        Response r = consumerResource.bind("fakeConsumer", null, Arrays.asList(prodIds),
            null, null, null, false, null, null);
        assertEquals(i18n.tr("You must first accept Red Hat''s Terms and conditions. Please visit {0}",
                "https://www.redhat.com/wapps/tnc/ackrequired?site=candlepin&event=attachSubscription"),
            ((ExceptionMessage) r.getEntity()).getDisplayMessage());
    }

    @Test
    public void testUnknownSubscriptionTerms() throws Exception {
        Owner o = createNonSCAOwner();
        Consumer c = createConsumer(o);
        String[] prodIds = {"notthere"};

        when(subscriptionServiceAdapter.hasUnacceptedSubscriptionTerms(o.getKey()))
            .thenThrow(new SubscriptionServiceException());
        when(consumerCurator.verifyAndLookupConsumerWithEntitlements("fakeConsumer")).thenReturn(c);
        when(entitler.bindByProducts(any(AutobindData.class))).thenReturn(null);
        when(ownerCurator.findOwnerById(o.getId())).thenReturn(o);

        assertNull(assertThrows(SubscriptionServiceException.class, () ->
            consumerResource.bind("fakeConsumer", null, Arrays.asList(prodIds), null, null, null, false,
                null, null))
            .getMessage());
    }

    @Test
    public void testUnknownProductRetrieval() throws Exception {
        Owner o = createNonSCAOwner();
        Consumer c = createConsumer(o);
        String[] prodIds = {"notthere"};

        when(subscriptionServiceAdapter.hasUnacceptedSubscriptionTerms(o.getKey())).thenReturn(false);
        when(consumerCurator.verifyAndLookupConsumerWithEntitlements("fakeConsumer")).thenReturn(c);
        when(entitler.bindByProducts(any(AutobindData.class)))
            .thenThrow(new ProductServiceException("notthere"));
        when(ownerCurator.findOwnerById(o.getId())).thenReturn(o);

        assertEquals("notthere",
            assertThrows(ProductServiceException.class, () -> consumerResource.bind("fakeConsumer", null,
                Arrays.asList(prodIds), null, null, null, false, null, null)).getProductId());
    }

    @Test
    public void testAutobindHypervisorDisabled() throws Exception {
        Owner o = createNonSCAOwner();
        Consumer c = createConsumer(o);
        String[] prodIds = {"notthere"};

        when(subscriptionServiceAdapter.hasUnacceptedSubscriptionTerms(o.getKey())).thenReturn(false);
        when(consumerCurator.verifyAndLookupConsumerWithEntitlements("fakeConsumer")).thenReturn(c);
        when(entitler.bindByProducts(any(AutobindData.class)))
            .thenThrow(AutobindHypervisorDisabledException.class);
        when(ownerCurator.findOwnerById(o.getId())).thenReturn(o);

        assertEquals(i18n.tr("Ignoring request to auto-attach. " +
                    "It is disabled for org \"{0}\" because of the hypervisor autobind setting.",
                o.getKey()),
            assertThrows(BadRequestException.class, () -> consumerResource.bind("fakeConsumer", null,
                Arrays.asList(prodIds), null, null, null, false, null, null)).getMessage());
    }

    @Test
    public void testAutobindDisabledForOwner() throws Exception {
        Owner o = createOwner();
        Consumer c = createConsumer(o);
        String[] prodIds = {"notthere"};

        when(subscriptionServiceAdapter.hasUnacceptedSubscriptionTerms(o.getKey())).thenReturn(false);
        when(consumerCurator.verifyAndLookupConsumerWithEntitlements("fakeConsumer")).thenReturn(c);
        when(entitler.bindByProducts(any(AutobindData.class)))
            .thenThrow(AutobindDisabledForOwnerException.class);
        when(ownerCurator.findOwnerById(o.getId())).thenReturn(o);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), consumerResource.bind("fakeConsumer", null,
            Arrays.asList(prodIds), null, null, null, false, null, null).getStatus());

        o.setContentAccessMode(ContentAccessMode.ENTITLEMENT.toDatabaseValue());
        assertEquals(i18n.tr("Ignoring request to auto-attach. " +
                "It is disabled for org \"{0}\".", o.getKey()),
            assertThrows(BadRequestException.class, () -> consumerResource.bind("fakeConsumer", null,
                Arrays.asList(prodIds), null, null, null, false, null, null)).getMessage());
    }

    @Test
    public void testProductNoPool() throws Exception {
        Owner o = createNonSCAOwner();
        Consumer c = createConsumer(o);
        String[] prodIds = {"notthere"};

        when(subscriptionServiceAdapter.hasUnacceptedSubscriptionTerms(o.getKey())).thenReturn(false);
        when(consumerCurator.verifyAndLookupConsumerWithEntitlements("fakeConsumer")).thenReturn(c);
        when(entitler.bindByProducts(any(AutobindData.class))).thenReturn(null);
        when(ownerCurator.findOwnerById(o.getId())).thenReturn(o);

        Response r = consumerResource.bind("fakeConsumer", null, Arrays.asList(prodIds),
            null, null, null, false, null, null);
        assertNull(r.getEntity());
    }

    @Test
    public void futureHealing() throws Exception {
        Owner owner = createNonSCAOwner();
        Consumer consumer = createConsumer(owner);
        ConsumerCurator cc = mock(ConsumerCurator.class);
        ConsumerInstalledProduct cip = mock(ConsumerInstalledProduct.class);
        Set<ConsumerInstalledProduct> products = new HashSet<>();
        products.add(cip);

        when(ownerCurator.findOwnerById(owner.getId())).thenReturn(owner);
        when(cip.getProductId()).thenReturn("product-foo");
        when(subscriptionServiceAdapter.hasUnacceptedSubscriptionTerms(owner.getKey())).thenReturn(false);
        when(cc.verifyAndLookupConsumerWithEntitlements(consumer.getUuid())).thenReturn(consumer);

        OffsetDateTime entitleDate = OffsetDateTime.now().minusYears(5);

        consumerResource.bind(consumer.getUuid(), null, null, null, null, null, false, entitleDate, null);
        AutobindData data = new AutobindData(consumer, owner)
            .on(Util.toDate(entitleDate));

        verify(entitler).bindByProducts(data);
    }

    @Test
    public void unbindByInvalidSerialShouldFail() {
        Consumer consumer = createConsumer(createOwner());
        ConsumerCurator consumerCurator = mock(ConsumerCurator.class);
        when(consumerCurator.verifyAndLookupConsumer("fake uuid")).thenReturn(consumer);
        when(entitlementCurator.get(any(Serializable.class))).thenReturn(null);

        assertThrows(NotFoundException.class, () -> consumerResource.unbindBySerial("fake uuid", 1234L));
    }

    /**
     * Basic test. If invalid id is given, should throw {@link NotFoundException}
     */
    @Test
    public void unbindByInvalidPoolIdShouldFail() {
        Consumer consumer = createConsumer(createOwner());
        ConsumerCurator consumerCurator = mock(ConsumerCurator.class);
        when(consumerCurator.verifyAndLookupConsumer("fake-uuid")).thenReturn(consumer);
        when(entitlementCurator.listByConsumerAndPoolId(eq(consumer), any(String.class)))
            .thenReturn(new ArrayList<>());

        assertThrows(NotFoundException.class,
            () -> consumerResource.unbindByPool("fake-uuid", "Run Forest!"));
    }

    @Test
    public void testBindMultipleParams() {
        Consumer c = createConsumer(createNonSCAOwner());
        when(consumerCurator.verifyAndLookupConsumerWithEntitlements(c.getUuid())).thenReturn(c);
        assertThrows(BadRequestException.class, () -> consumerResource.bind(c.getUuid(), "fake pool uuid",
            Arrays.asList("12232"), 1, null, null, false, null, null));
    }

    @Test
    public void testBindByPoolBadConsumerUuid() {
        Consumer c = createConsumer(createOwner());
        when(consumerCurator.verifyAndLookupConsumerWithEntitlements(c.getUuid()))
            .thenThrow(new NotFoundException(""));
        assertThrows(NotFoundException.class, () -> consumerResource.bind(c.getUuid(), "fake pool uuid", null,
            null, null, null, false, null, null));
    }

    protected EntitlementCertificate createEntitlementCertificate(String key, String cert) {
        EntitlementCertificate toReturn = new EntitlementCertificate();
        CertificateSerial certSerial = new CertificateSerial(new Random().nextLong(), new Date());
        toReturn.setKeyAsBytes(key.getBytes());
        toReturn.setCertAsBytes(cert.getBytes());
        toReturn.setSerial(certSerial);
        return toReturn;
    }

    private EntitlementCertificate createEntitlementCertificate(String key, String cert, long serialId) {
        EntitlementCertificate certificate = new EntitlementCertificate();
        CertificateSerial expectedSerial = new CertificateSerial(serialId, new Date());
        certificate.setKeyAsBytes(key.getBytes());
        certificate.setCertAsBytes(cert.getBytes());
        certificate.setSerial(expectedSerial);
        certificate.setUpdated(new Date());
        certificate.setUpdated(new Date());
        return certificate;
    }

    private SCACertificate createContentAccessCertificate(String key, String cert, long serialId) {
        CertificateSerial expectedSerial = new CertificateSerial(serialId, new Date());
        String certBody = """
            %s
            -----BEGIN ENTITLEMENT DATA-----
            """.formatted(cert);

        SCACertificate certificate = new SCACertificate();
        certificate.setKeyAsBytes(key.getBytes());
        certificate.setCertAsBytes(certBody.getBytes());
        certificate.setSerial(expectedSerial);
        return certificate;
    }

    private AnonymousContentAccessCertificate createAnonContentAccessCert(String key, String cert,
        long serial) {
        Date now = new Date();
        AnonymousContentAccessCertificate certificate = new AnonymousContentAccessCertificate();
        certificate.setCreated(now);
        certificate.setUpdated(now);
        CertificateSerial expectedSerial = new CertificateSerial(serial, new Date());
        certificate.setKeyAsBytes(key.getBytes());
        certificate.setCertAsBytes(cert.getBytes());
        certificate.setSerial(expectedSerial);

        return certificate;
    }

    @Test
    public void testNullPerson() {
        Owner owner = this.createOwner();
        ConsumerType ctype = this.mockConsumerType(new ConsumerType(ConsumerTypeEnum.PERSON));
        Consumer consumer = this.createConsumer(owner, ctype);
        ConsumerDTO consumerDto = this.translator.translate(consumer, ConsumerDTO.class);
        UserPrincipal up = mock(UserPrincipal.class);

        when(up.canAccess(owner, SubResource.CONSUMERS, Access.CREATE)).thenReturn(true);
        when(this.principalProvider.get()).thenReturn(up);
        // usa.findByLogin() will return null by default no need for a when
        assertThrows(NotFoundException.class,
            () -> consumerResource.createConsumer(consumerDto, null, owner.getKey(), null, true));
    }

    @Test
    public void testCreateConsumerShouldFailOnMaxLengthOfName() {
        Owner owner = this.createOwner();

        ConsumerType ctype = this.mockConsumerType(new ConsumerType(ConsumerTypeEnum.SYSTEM));

        Consumer consumer = this.createConsumer(owner, ctype);
        consumer.setName(RandomStringUtils.randomAlphanumeric(Consumer.MAX_LENGTH_OF_CONSUMER_NAME + 1));
        ConsumerDTO consumerDto = this.translator.translate(consumer, ConsumerDTO.class);

        UserPrincipal up = mock(UserPrincipal.class);

        when(up.canAccess(owner, SubResource.CONSUMERS, Access.CREATE)).thenReturn(true);
        when(this.principalProvider.get()).thenReturn(up);

        BadRequestException ex = assertThrows(BadRequestException.class,
            () -> consumerResource.createConsumer(consumerDto, null, owner.getKey(), null, false));
        assertEquals(String.format("Name of the consumer should be shorter than %d characters.",
            Consumer.MAX_LENGTH_OF_CONSUMER_NAME + 1), ex.getMessage());
    }

    @Test
    public void testUserNoOwnerSetup() {
        Owner owner = this.createOwner();
        UserPrincipal up = mock(UserPrincipal.class);
        when(up.getOwnerKeys()).thenReturn(new ArrayList<>());
        assertThrows(BadRequestException.class, () -> consumerResource.setupOwner(up, null));
    }

    @Test
    public void testUserOneOwnerSetup() {
        Owner owner = this.createOwner();
        UserPrincipal up = mock(UserPrincipal.class);
        when(up.getOwnerKeys()).thenReturn(List.of(owner.getKey()));
        when(up.canAccess(eq(owner), any(SubResource.class), any(Access.class))).thenReturn(true);
        assertEquals(owner, consumerResource.setupOwner(up, null));
    }

    @Test
    public void testUserManyOwnerNoPrimarySetup() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();
        UserPrincipal up = mock(UserPrincipal.class);
        when(up.getOwnerKeys()).thenReturn(List.of(owner1.getKey(), owner2.getKey(), owner3.getKey()));
        when(up.getPrimaryOwner()).thenReturn(null);
        when(up.canAccess(eq(owner2), any(SubResource.class), any(Access.class))).thenReturn(true);
        assertThrows(BadRequestException.class, () -> consumerResource.setupOwner(up, null));
    }

    @Test
    public void testUserManyOwnerPrimarySetup() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();
        UserPrincipal up = mock(UserPrincipal.class);
        when(up.getOwnerKeys()).thenReturn(List.of(owner1.getKey(), owner2.getKey(), owner3.getKey()));
        when(up.getPrimaryOwner()).thenReturn(owner2);
        when(up.canAccess(eq(owner2), any(SubResource.class), any(Access.class))).thenReturn(true);
        assertEquals(owner2, consumerResource.setupOwner(up, null));
    }

    @Test
    public void testTrustedUserShouldCreateMissingOwner() {
        TrustedUserPrincipal up = mock(TrustedUserPrincipal.class);
        String ownerKey = "new_owner";
        Map<String, Owner> owners = new HashMap<>();
        when(ownerCurator.create(any())).thenAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            Owner owner = (Owner) args[0];
            owners.put(owner.getKey(), owner);
            return owner;
        });
        when(ownerService.isOwnerKeyValidForCreation(ownerKey)).thenReturn(true);
        when(ownerCurator.getByKey(ownerKey)).thenAnswer(invocation -> owners.get(ownerKey));

        Owner newOwner = this.consumerResource.setupOwner(up, ownerKey);

        assertEquals(ownerKey, newOwner.getKey());
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
        when(consumerCurator.findByUuids(uuids)).thenReturn(consumers);

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
    public void testcheckForGuestsMigrationSerialList() {
        Consumer consumer = createConsumer(createOwner());
        List<EntitlementCertificate> certificates = createEntitlementCertificates();

        when(entitlementCertServiceAdapter.listForConsumer(consumer)).thenReturn(certificates);
        when(consumerCurator.verifyAndLookupConsumer(consumer.getUuid())).thenReturn(consumer);
        when(entitlementCurator.listByConsumer(consumer)).thenReturn(new ArrayList<>());

        mockedConsumerResource.getEntitlementCertificateSerials(consumer.getUuid());
        verify(mockedConsumerResource).revokeOnGuestMigration(consumer);
    }

    @Test
    public void testCheckForGuestsMigrationCertList() throws Exception {
        Consumer consumer = createConsumer(createOwner());
        Owner owner = new Owner();

        ConsumerPrincipal principal = new ConsumerPrincipal(consumer, owner);
        ResteasyContext.pushContext(Principal.class, principal);
        MockHttpRequest mockReq = MockHttpRequest.create("GET", "http://localhost/candlepin/fake")
            .header("accept", "application/json");
        ResteasyContext.pushContext(HttpRequest.class, mockReq);
        ResteasyContext.pushContext(HttpServletResponse.class, mock(HttpServletResponse.class));

        List<EntitlementCertificate> certificates = createEntitlementCertificates();

        when(entitlementCertServiceAdapter.listForConsumer(consumer)).thenReturn(certificates);
        when(consumerCurator.verifyAndLookupConsumer(consumer.getUuid())).thenReturn(consumer);
        when(entitlementCurator.listByConsumer(consumer)).thenReturn(new ArrayList<>());

        mockedConsumerResource.exportCertificates(consumer.getUuid(), "123");
        verify(mockedConsumerResource).revokeOnGuestMigration(consumer);
    }

    @Test
    public void testNoDryBindWhenAutobindDisabledForOwner() {
        Owner owner = createOwner();
        owner.setContentAccessMode("entitlement");
        owner.setId(TestUtil.randomString());
        Consumer consumer = createConsumer(owner);
        owner.setAutobindDisabled(true);
        when(consumerCurator.verifyAndLookupConsumer(consumer.getUuid())).thenReturn(consumer);
        when(ownerCurator.findOwnerById(owner.getId())).thenReturn(owner);

        try {
            consumerResource.dryBind(consumer.getUuid(), "some-sla");
            fail("Should have thrown a BadRequestException.");
        }
        catch (BadRequestException e) {
            assertEquals("Organization \"" + owner.getKey() + "\" has auto-attach disabled.", e.getMessage());
        }
    }

    @Test
    public void testAsyncExport() {
        Owner owner = this.createOwner();
        owner.setId(TestUtil.randomString());
        when(ownerCurator.findOwnerById(owner.getId())).thenReturn(owner);
        ConsumerType ctype = this.mockConsumerType(new ConsumerType(ConsumerType.ConsumerTypeEnum.CANDLEPIN));
        Consumer consumer = this.createConsumer(owner, ctype);

        Cdn cdn = new Cdn("cdn-label", "test", "url");

        when(cdnCurator.getByLabel(cdn.getLabel())).thenReturn(cdn);

        consumerResource.exportDataAsync(consumer.getUuid(), cdn.getLabel(),
            "prefix", cdn.getUrl());
        verify(manifestManager).generateManifestAsync(consumer.getUuid(), owner,
            cdn.getLabel(), "prefix", cdn.getUrl());
    }

    @Test
    public void deleteConsumerThrowsGoneExceptionIfConsumerDoesNotExistOnInitialLookup() {
        String targetConsumerUuid = "my-test-consumer";
        when(consumerCurator.findByUuid(targetConsumerUuid)).thenReturn(null);

        UserPrincipal uap = mock(UserPrincipal.class);
        when(uap.canAccess(any(Object.class), any(SubResource.class), any(Access.class)))
            .thenReturn(Boolean.TRUE);

        assertThrows(GoneException.class, () -> consumerResource.deleteConsumer(targetConsumerUuid));
    }

    @Test
    public void deleteConsumerThrowsGoneExceptionWhenLockAquisitionFailsDueToConsumerAlreadyDeleted() {
        Consumer consumer = createConsumer();
        when(consumerCurator.findByUuid(consumer.getUuid())).thenReturn(consumer);
        when(consumerCurator.lock(consumer)).thenThrow(OptimisticLockException.class);
        when(deletedConsumerCurator.findByConsumerUuid(consumer.getUuid()))
            .thenReturn(new DeletedConsumer());

        UserPrincipal uap = mock(UserPrincipal.class);
        when(uap.canAccess(any(Object.class), any(SubResource.class), any(Access.class)))
            .thenReturn(Boolean.TRUE);

        assertThrows(GoneException.class, () -> consumerResource.deleteConsumer(consumer.getUuid()));
    }

    @Test
    public void deleteConsumerReThrowsOLEWhenLockAquisitionFailsWithoutConsumerHavingBeenDeleted() {
        Consumer consumer = createConsumer();
        when(consumerCurator.findByUuid(consumer.getUuid())).thenReturn(consumer);
        when(consumerCurator.lock(consumer)).thenThrow(OptimisticLockException.class);
        when(deletedConsumerCurator.findByConsumerUuid(consumer.getUuid())).thenReturn(null);

        UserPrincipal uap = mock(UserPrincipal.class);
        when(uap.canAccess(any(Object.class), any(SubResource.class), any(Access.class)))
            .thenReturn(Boolean.TRUE);

        assertThrows(OptimisticLockException.class,
            () -> consumerResource.deleteConsumer(consumer.getUuid()));
    }

    @Test
    public void testExportCertificatesWithExistingSerialIdForEntitlementCertificate() throws Exception {
        Consumer consumer = createConsumer();
        Owner owner = new Owner();

        ConsumerPrincipal principal = new ConsumerPrincipal(consumer, owner);
        ResteasyContext.pushContext(Principal.class, principal);
        MockHttpRequest mockReq = MockHttpRequest.create("GET", "http://localhost/candlepin/fake")
            .header("accept", "application/json");
        ResteasyContext.pushContext(HttpRequest.class, mockReq);
        ResteasyContext.pushContext(HttpServletResponse.class, mock(HttpServletResponse.class));

        doReturn(consumer).when(consumerCurator).verifyAndLookupConsumer(consumer.getId());

        EntitlementCertificate expectedCertificate = createEntitlementCertificate("expected-key",
            "expected-cert", 18084729L);
        List<EntitlementCertificate> certificates = new ArrayList<>();
        certificates.add(createEntitlementCertificate("key-1", "cert-1"));
        certificates.add(createEntitlementCertificate("key-2", "cert-2"));
        certificates.add(expectedCertificate);
        doReturn(certificates).when(entitlementCertServiceAdapter).listForConsumer(any(Consumer.class));

        Object export = consumerResource.exportCertificates(consumer.getId(),
            Long.toString(expectedCertificate.getSerial().getId()));

        assertThat(export)
            .isInstanceOf(List.class);

        List<CertificateDTO> actual = (List<CertificateDTO>) export;
        assertEquals(1, actual.size());
        CertificateDTO actualCertificate = actual.get(0);
        assertEquals(expectedCertificate.getId(), actualCertificate.getId());
        assertEquals(expectedCertificate.getKey(), actualCertificate.getKey());
        assertEquals(expectedCertificate.getCert(), actualCertificate.getCert());
        assertEquals(Util.toDateTime(expectedCertificate.getCreated()), actualCertificate.getCreated());
        assertEquals(Util.toDateTime(expectedCertificate.getUpdated()), actualCertificate.getUpdated());
        assertEquals(expectedCertificate.getSerial().getId(), actualCertificate.getSerial().getId());
    }

    @Test
    public void testExportCertificatesWithExistingSerialIdForSimpleContentAccessCert()
        throws Exception {

        Consumer consumer = createConsumer();
        Owner owner = new Owner();

        ConsumerPrincipal principal = new ConsumerPrincipal(consumer, owner);
        ResteasyContext.pushContext(Principal.class, principal);
        MockHttpRequest mockReq = MockHttpRequest.create("GET", "http://localhost/candlepin/fake")
            .header("accept", "application/json");
        ResteasyContext.pushContext(HttpRequest.class, mockReq);
        ResteasyContext.pushContext(HttpServletResponse.class, mock(HttpServletResponse.class));

        doReturn(consumer).when(consumerCurator).verifyAndLookupConsumer(consumer.getId());

        List<EntitlementCertificate> certificates = new ArrayList<>();
        certificates.add(createEntitlementCertificate("key-1", "cert-1"));
        certificates.add(createEntitlementCertificate("key-2", "cert-2"));
        doReturn(certificates).when(entitlementCertServiceAdapter).listForConsumer(any(Consumer.class));

        SCACertificate expectedCertificate = createContentAccessCertificate("expected-key",
            "expected-cert", 18084729L);
        when(this.scaCertificateGenerator.generate(any(Consumer.class)))
            .thenReturn(expectedCertificate);

        Object export = consumerResource.exportCertificates(consumer.getId(),
            Long.toString(expectedCertificate.getSerial().getId()));

        assertThat(export)
            .isInstanceOf(List.class);

        List<CertificateDTO> actual = (List<CertificateDTO>) export;
        assertEquals(1, actual.size());
        CertificateDTO actualCertificate = actual.get(0);
        assertEquals(expectedCertificate.getId(), actualCertificate.getId());
        assertEquals(expectedCertificate.getKey(), actualCertificate.getKey());
        assertEquals(expectedCertificate.getCert(), actualCertificate.getCert());
        assertEquals(Util.toDateTime(expectedCertificate.getCreated()), actualCertificate.getCreated());
        assertEquals(Util.toDateTime(expectedCertificate.getUpdated()), actualCertificate.getUpdated());
        assertEquals(expectedCertificate.getSerial().getId(), actualCertificate.getSerial().getId());
    }

    @Test
    public void testExportCertificatesWithConcurrentContentPayloadCreationException()
        throws Exception {

        Consumer consumer = createConsumer();
        Owner owner = new Owner();

        ConsumerPrincipal principal = new ConsumerPrincipal(consumer, owner);
        ResteasyContext.pushContext(Principal.class, principal);
        MockHttpRequest mockReq = MockHttpRequest.create("GET", "http://localhost/candlepin/fake")
            .header("accept", "application/json");
        ResteasyContext.pushContext(HttpRequest.class, mockReq);
        ResteasyContext.pushContext(HttpServletResponse.class, mock(HttpServletResponse.class));

        doReturn(consumer)
            .when(consumerCurator)
            .verifyAndLookupConsumer(consumer.getId());

        doReturn(List.of(createEntitlementCertificate("key", "cert")))
            .when(entitlementCertServiceAdapter)
            .listForConsumer(any(Consumer.class));

        doThrow(ConcurrentContentPayloadCreationException.class)
            .when(scaCertificateGenerator)
            .generate(consumer);

        String consumerId = consumer.getId();
        TooManyRequestsException exception = assertThrows(TooManyRequestsException.class, () -> {
            consumerResource.exportCertificates(consumerId, "18084729");
        });

        assertThat(exception)
            .returns(CONTENT_PAYLOAD_CREATION_EXCEPTION_RETRY_AFTER_TIME,
                TooManyRequestsException::getRetryAfterTime);
    }

    @Test
    public void testExportCertificatesWithExistingAnonymousCloudConsumer() throws Exception {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer();
        consumer.setUuid("uuid");
        consumer.setProductIds(List.of("product-id"));

        AnonymousCloudConsumerPrincipal principal = new AnonymousCloudConsumerPrincipal(consumer);
        ResteasyContext.pushContext(Principal.class, principal);
        MockHttpRequest mockReq = MockHttpRequest.create("GET", "http://localhost/candlepin/fake")
            .header("accept", "application/json");
        ResteasyContext.pushContext(HttpRequest.class, mockReq);
        ResteasyContext.pushContext(HttpServletResponse.class, mock(HttpServletResponse.class));

        AnonymousContentAccessCertificate expectedCert = createAnonContentAccessCert("expected-key",
            "expected-cert", 18084729L);
        when(this.anonymousCertificateGenerator.generate(consumer)).thenReturn(expectedCert);

        Object export = consumerResource.exportCertificates(consumer.getUuid(), null);
        assertThat(export)
            .isInstanceOf(List.class);

        List<CertificateDTO> actual = (List<CertificateDTO>) export;
        assertEquals(1, actual.size());
        CertificateDTO actualCert = actual.get(0);
        assertEquals(expectedCert.getId(), actualCert.getId());
        assertEquals(expectedCert.getKey(), actualCert.getKey());
        assertEquals(expectedCert.getCert(), actualCert.getCert());
        assertEquals(expectedCert.getCreated(), Date.from(actualCert.getCreated().toInstant()));
        assertEquals(expectedCert.getUpdated(), Date.from(actualCert.getUpdated().toInstant()));
        assertEquals(expectedCert.getSerial().getId(), actualCert.getSerial().getId());
    }

    @Test
    public void testExportCertificatesWithFilteringExistingAnonymousCloudConsumer() throws Exception {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer();
        consumer.setUuid("uuid");
        consumer.setProductIds(List.of("product-id"));

        AnonymousCloudConsumerPrincipal principal = new AnonymousCloudConsumerPrincipal(consumer);
        ResteasyContext.pushContext(Principal.class, principal);

        AnonymousContentAccessCertificate expectedCertificate = createAnonContentAccessCert("expected-key",
            "expected-cert", 18084729L);
        when(this.anonymousCertificateGenerator.generate(consumer))
            .thenReturn(expectedCertificate);
        String serials = Long.toString(expectedCertificate.getSerial().getId());

        List<CertificateDTO> actual = (List<CertificateDTO>) consumerResource
            .exportCertificates(consumer.getUuid(), serials);

        assertEquals(0, actual.size());
    }

    @Test
    public void testExportCertificatesWithAnonymousConsumerAndUnableToGetCertificate()
        throws Exception {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer();
        consumer.setUuid("uuid");
        consumer.setProductIds(List.of("product-id"));
        AnonymousCloudConsumerPrincipal principal = new AnonymousCloudConsumerPrincipal(consumer);
        ResteasyContext.pushContext(Principal.class, principal);

        doThrow(new RuntimeException()).when(this.anonymousCertificateGenerator)
            .generate(any(AnonymousCloudConsumer.class));

        assertThrows(IseException.class, () -> consumerResource
            .exportCertificates(consumer.getUuid(), null));
    }

    @Test
    public void testExportCertificatesWithAnonymousConsumer() throws Exception {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer();
        consumer.setUuid("uuid");
        consumer.setProductIds(List.of("product-id"));

        AnonymousCloudConsumerPrincipal principal = new AnonymousCloudConsumerPrincipal(consumer);
        ResteasyContext.pushContext(Principal.class, principal);
        MockHttpRequest mockReq = MockHttpRequest.create("GET", "http://localhost/candlepin/fake");
        ResteasyContext.pushContext(HttpRequest.class, mockReq);
        ResteasyContext.pushContext(HttpServletResponse.class, mock(HttpServletResponse.class));

        assertThrows(BadRequestException.class, () -> consumerResource
            .exportCertificates(consumer.getUuid(), "1234L"));
    }

    @Test
    public void testExportCertificatesWithConsumer() throws Exception {
        Consumer consumer = createConsumer();
        Owner owner = new Owner();
        ConsumerPrincipal principal = new ConsumerPrincipal(consumer, owner);
        ResteasyContext.pushContext(Principal.class, principal);
        MockHttpRequest mockReq = MockHttpRequest.create("GET", "http://localhost/candlepin/fake");
        ResteasyContext.pushContext(HttpRequest.class, mockReq);
        ResteasyContext.pushContext(HttpServletResponse.class, mock(HttpServletResponse.class));

        Long serial = 123456L;
        File mockFile = mock(File.class);
        doReturn(mockFile).when(manifestManager).generateEntitlementArchive(consumer, Set.of(serial));

        Object actual = consumerResource.exportCertificates(consumer.getUuid(), Long.toString(serial));

        assertEquals(mockFile, actual);
    }

    @Test
    public void testExportCertificatesInZipFormatWithConcurrentContentPayloadCreationException()
        throws Exception {

        Consumer consumer = createConsumer();
        long serial = 123456L;

        doThrow(ConcurrentContentPayloadCreationException.class)
            .when(manifestManager)
            .generateEntitlementArchive(consumer, Set.of(serial));

        MockHttpRequest mockReq = MockHttpRequest.create("GET", "http://localhost/candlepin/fake")
            .header("accept", "application/zip");
        ResteasyContext.pushContext(HttpRequest.class, mockReq);
        ResteasyContext.pushContext(HttpServletResponse.class, mock(HttpServletResponse.class));

        TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
            () -> consumerResource.exportCertificates(consumer.getUuid(), Long.toString(serial)));

        assertThat(exception)
            .returns(CONTENT_PAYLOAD_CREATION_EXCEPTION_RETRY_AFTER_TIME,
                TooManyRequestsException::getRetryAfterTime);
    }

    @Test
    public void testExportCertificatesWithUnknownSerialId() throws Exception {
        Consumer consumer = createConsumer();

        ConsumerPrincipal principal = new ConsumerPrincipal(consumer, new Owner());
        ResteasyContext.pushContext(Principal.class, principal);
        MockHttpRequest mockReq = MockHttpRequest.create("GET", "http://localhost/candlepin/fake")
            .header("accept", "application/json");
        ResteasyContext.pushContext(HttpRequest.class, mockReq);
        ResteasyContext.pushContext(HttpServletResponse.class, mock(HttpServletResponse.class));

        doReturn(consumer).when(consumerCurator).verifyAndLookupConsumer(consumer.getId());
        List<EntitlementCertificate> certificates = createEntitlementCertificates();
        doReturn(certificates).when(entitlementCertServiceAdapter).listForConsumer(any(Consumer.class));
        SCACertificate certificate = createContentAccessCertificate(
            "key-1", "key-2", 18084729L);
        doReturn(certificate).when(this.scaCertificateGenerator).generate(any(Consumer.class));

        Object export = consumerResource.exportCertificates(consumer.getId(), "123456");
        assertThat(export)
            .isInstanceOf(List.class);

        List<CertificateDTO> actual = (List<CertificateDTO>) export;
        assertEquals(0, actual.size());
    }

    @Test
    public void shouldThrowWhenConsumerNotFound() {
        when(consumerCurator.verifyAndLookupConsumer(anyString()))
            .thenThrow(NotFoundException.class);

        Assertions.assertThrows(NotFoundException.class,
            () -> consumerResource.getContentAccessForConsumer("test_uuid"));
    }

    @Test
    public void usesDefaultWhenNoCAAvailable() {
        String expectedMode = ContentAccessMode.getDefault().toDatabaseValue();
        List<String> expectedModeList = List.of(
            ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue());
        Consumer consumer = createConsumer(createOwner());
        when(consumerCurator.verifyAndLookupConsumer(anyString()))
            .thenReturn(consumer);

        ContentAccessDTO contentAccess = consumerResource
            .getContentAccessForConsumer("test_consumer_uuid");

        assertEquals(expectedMode, contentAccess.getContentAccessMode());
        assertEquals(expectedModeList, contentAccess.getContentAccessModeList());
    }

    @Test
    public void usesConsumersCAModeWhenAvailable() {
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
    public void usesOwnersCAModeWhenConsumersCAModeNotAvailable() {
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
    public void usesOwnersCAModeListWhenAvailable() {
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

    @Test
    public void testGetContentAccessBodyWithEntitlementOwner() {
        Owner owner = createOwner()
            .setContentAccessMode(ContentAccessMode.ENTITLEMENT.name());
        doReturn(owner)
            .when(ownerCurator)
            .findOwnerById(owner.getOwnerId());

        Consumer consumer = createConsumer(owner);
        doReturn(consumer)
            .when(consumerCurator)
            .verifyAndLookupConsumer(anyString());

        String now = new SimpleDateFormat(SINCE_DATE_FORMAT)
            .format(new Date());

        assertThrows(BadRequestException.class, () -> {
            consumerResource.getContentAccessBody("test-uuid", now);
        });
    }

    @Test
    public void testGetContentAccessBodyWithNullX509Cert() throws Exception {
        Owner owner = createOwner();
        doReturn(owner)
            .when(ownerCurator)
            .findOwnerById(owner.getOwnerId());

        Consumer consumer = createConsumer(owner);
        doReturn(consumer)
            .when(consumerCurator)
            .verifyAndLookupConsumer(anyString());

        ContentAccessPayload payload = new ContentAccessPayload()
            .setTimestamp(new Date());
        doReturn(payload).when(scaCertificateGenerator).getContentPayload(consumer);

        String now = new SimpleDateFormat(SINCE_DATE_FORMAT)
            .format(new Date());

        assertThrows(BadRequestException.class, () -> {
            consumerResource.getContentAccessBody("test-uuid", now);
        });
    }

    @Test
    public void testGetContentAccessBodyWithNullContentAccessPayload() {
        Owner owner = createOwner();
        doReturn(owner)
            .when(ownerCurator)
            .findOwnerById(owner.getOwnerId());

        Consumer consumer = createConsumer(owner);
        consumer.getOwner()
            .setContentAccessModeList("owner-ca-mode-list");

        doReturn(consumer)
            .when(consumerCurator)
            .verifyAndLookupConsumer(anyString());

        SCACertificate expectedCertificate = new SCACertificate()
            .setUpdated(new Date());
        expectedCertificate.setCert("cert");
        expectedCertificate.setKey("key");
        expectedCertificate.setSerial(new CertificateSerial(1234567L));

        doReturn(expectedCertificate)
            .when(scaCertificateGenerator)
            .getX509Certificate(consumer);

        String now = new SimpleDateFormat(SINCE_DATE_FORMAT)
            .format(new Date());

        assertThrows(BadRequestException.class, () -> {
            consumerResource.getContentAccessBody("test-uuid", now);
        });
    }

    @Test
    public void testGetContentAccessBodyWithConcurrentContentPayloadCreationException() throws Exception {
        Owner owner = createOwner();
        doReturn(owner)
            .when(ownerCurator)
            .findOwnerById(owner.getOwnerId());

        Consumer consumer = createConsumer(owner);
        consumer.getOwner()
            .setContentAccessModeList("owner-ca-mode-list");

        doReturn(consumer)
            .when(consumerCurator)
            .verifyAndLookupConsumer(anyString());

        SCACertificate expectedCertificate = new SCACertificate()
            .setUpdated(new Date());
        expectedCertificate.setCert("cert");
        expectedCertificate.setKey("key");
        expectedCertificate.setSerial(new CertificateSerial(1234567L));

        doReturn(expectedCertificate)
            .when(scaCertificateGenerator)
            .getX509Certificate(consumer);

        doThrow(ConcurrentContentPayloadCreationException.class)
            .when(scaCertificateGenerator)
            .getContentPayload(consumer);

        String now = new SimpleDateFormat(SINCE_DATE_FORMAT)
            .format(new Date());

        TooManyRequestsException exception = assertThrows(TooManyRequestsException.class,
            () -> consumerResource.getContentAccessBody("test-uuid", now));

        assertThat(exception)
            .returns(CONTENT_PAYLOAD_CREATION_EXCEPTION_RETRY_AFTER_TIME,
                TooManyRequestsException::getRetryAfterTime);
    }

    @Test
    public void contentAccessNotModified() throws Exception {
        Owner owner = createOwner();
        doReturn(owner)
            .when(ownerCurator)
            .findOwnerById(owner.getOwnerId());

        Consumer consumer = createConsumer(owner);
        consumer.getOwner()
            .setContentAccessModeList("owner-ca-mode-list");

        doReturn(consumer)
            .when(consumerCurator)
            .verifyAndLookupConsumer(anyString());

        Date before = TestUtil.createDateOffset(0, 0, -5);
        SCACertificate expectedCertificate = new SCACertificate()
            .setUpdated(before);
        expectedCertificate.setCert("cert");
        expectedCertificate.setKey("key");
        expectedCertificate.setSerial(new CertificateSerial(1234567L));

        doReturn(expectedCertificate)
            .when(scaCertificateGenerator)
            .getX509Certificate(consumer);

        ContentAccessPayload payload = new ContentAccessPayload()
            .setTimestamp(before);
        doReturn(payload)
            .when(scaCertificateGenerator)
            .getContentPayload(consumer);

        String now = new SimpleDateFormat(SINCE_DATE_FORMAT)
            .format(new Date());

        Response contentAccess = consumerResource
            .getContentAccessBody("test-uuid", now);

        assertEquals("Not modified since date supplied.", contentAccess.getEntity());
    }

    @Test
    public void testGetContentAccessBodyWithNullCertAndContentPayloadUpdatedDates() throws Exception {
        Owner owner = createOwner();
        doReturn(owner)
            .when(ownerCurator)
            .findOwnerById(owner.getOwnerId());

        Consumer consumer = createConsumer(owner);
        doReturn(consumer)
            .when(consumerCurator)
            .verifyAndLookupConsumer(anyString());

        SCACertificate expectedCertificate = new SCACertificate();
        expectedCertificate.setCert("cert");
        expectedCertificate.setKey("key");
        expectedCertificate.setSerial(new CertificateSerial(1234567L));

        doReturn(expectedCertificate)
            .when(scaCertificateGenerator)
            .getX509Certificate(consumer);

        ContentAccessPayload payload = new ContentAccessPayload();
        doReturn(payload)
            .when(scaCertificateGenerator)
            .getContentPayload(consumer);

        // If the X509 cert and content access payload have a null updated date, then the current time should
        // be used as the updated date. Use a future 'since' date to verify this.
        String since = new SimpleDateFormat(SINCE_DATE_FORMAT)
            .format(TestUtil.createDateOffset(0, 0, 5));

        Response contentAccess = consumerResource
            .getContentAccessBody("test-uuid", since);

        assertEquals("Not modified since date supplied.", contentAccess.getEntity());
    }

    @Test
    public void testGetContentAccessBodyWithCertLastContentUpdateEqualToSinceDate() throws Exception {
        Owner owner = createOwner();
        doReturn(owner)
            .when(ownerCurator)
            .findOwnerById(owner.getOwnerId());

        Consumer consumer = createConsumer(owner);
        doReturn(consumer)
            .when(consumerCurator)
            .verifyAndLookupConsumer(anyString());

        SCACertificate expectedCertificate = createContentAccessCertificate(
            "expected-key", "expected-cert", new Random().nextLong());

        Date certLastUpdate = new Date();
        expectedCertificate.setUpdated(certLastUpdate);
        doReturn(expectedCertificate)
            .when(scaCertificateGenerator)
            .getX509Certificate(consumer);

        ContentAccessPayload payload = new ContentAccessPayload()
            .setTimestamp(certLastUpdate)
            .setPayload(TestUtil.randomString("payload-"));
        doReturn(payload).when(scaCertificateGenerator).getContentPayload(consumer);

        Response actual = consumerResource
            .getContentAccessBody("test-uuid", null);

        ContentAccessListing listing = (ContentAccessListing) actual.getEntity();
        assertThat(listing)
            .isNotNull()
            .returns(expectedCertificate.getUpdated(), ContentAccessListing::getLastUpdate);

        String since = new SimpleDateFormat(SINCE_DATE_FORMAT)
            .format(certLastUpdate);

        Response contentAccess = consumerResource
            .getContentAccessBody("test-uuid", since);

        assertEquals("Not modified since date supplied.", contentAccess.getEntity());
    }

    @Test
    public void testGetContentAccessBodyWithNullSinceDate() throws Exception {
        Owner owner = createOwner();
        doReturn(owner)
            .when(ownerCurator)
            .findOwnerById(owner.getOwnerId());

        Consumer consumer = createConsumer(owner);
        doReturn(consumer)
            .when(consumerCurator)
            .verifyAndLookupConsumer(anyString());

        SCACertificate expectedCertificate = createContentAccessCertificate(
            "expected-key", "expected-cert", new Random().nextLong());
        doReturn(expectedCertificate)
            .when(scaCertificateGenerator)
            .getX509Certificate(consumer);

        ContentAccessPayload payload = new ContentAccessPayload()
            .setTimestamp(new Date())
            .setPayload(TestUtil.randomString("payload-"));
        doReturn(payload).when(scaCertificateGenerator).getContentPayload(consumer);

        Response actual = consumerResource
            .getContentAccessBody("test-uuid", null);

        ContentAccessListing listing = (ContentAccessListing) actual.getEntity();
        Map<Long, List<String>> contentListing = listing.getContentListing();
        assertThat(contentListing)
            .isNotNull()
            .hasSize(1)
            .containsEntry(expectedCertificate.getSerial().getId(),
                List.of(expectedCertificate.getCert(), payload.getPayload()));
    }

    @Test
    public void testGetContentAccessBodyWithDifferentUpdateDates() throws Exception {
        Owner owner = createOwner();
        doReturn(owner)
            .when(ownerCurator)
            .findOwnerById(owner.getOwnerId());

        Consumer consumer = createConsumer(owner);
        doReturn(consumer)
            .when(consumerCurator)
            .verifyAndLookupConsumer(anyString());

        SCACertificate expectedCertificate = createContentAccessCertificate(
            "expected-key", "expected-cert", new Random().nextLong());

        // Make this updated date more recent than the content access payload timestamp.
        // This is the date that should be included in the ContentAccessListing
        expectedCertificate.setUpdated(TestUtil.createDateOffset(0, 0, -3));
        doReturn(expectedCertificate)
            .when(scaCertificateGenerator)
            .getX509Certificate(consumer);

        ContentAccessPayload payload = new ContentAccessPayload()
            .setTimestamp(TestUtil.createDateOffset(0, 0, -5))
            .setPayload(TestUtil.randomString("payload-"));
        doReturn(payload).when(scaCertificateGenerator).getContentPayload(consumer);

        Response actual = consumerResource
            .getContentAccessBody("test-uuid", null);

        ContentAccessListing listing = (ContentAccessListing) actual.getEntity();
        assertThat(listing)
            .isNotNull()
            .returns(expectedCertificate.getUpdated(), ContentAccessListing::getLastUpdate);

        Map<Long, List<String>> contentListing = listing.getContentListing();
        assertThat(contentListing)
            .isNotNull()
            .hasSize(1)
            .containsEntry(expectedCertificate.getSerial().getId(),
                List.of(expectedCertificate.getCert(), payload.getPayload()));
    }

    @Test
    public void contentAccessGetModified() throws Exception {
        Owner owner = createOwner();
        doReturn(owner)
            .when(ownerCurator)
            .findOwnerById(owner.getOwnerId());

        Consumer consumer = createConsumer(owner);
        doReturn(consumer)
            .when(consumerCurator)
            .verifyAndLookupConsumer(anyString());

        SCACertificate expectedCertificate = createContentAccessCertificate(
            "expected-key", "expected-cert", 18084729L);
        doReturn(expectedCertificate)
            .when(scaCertificateGenerator)
            .getX509Certificate(consumer);

        ContentAccessPayload payload = new ContentAccessPayload()
            .setTimestamp(TestUtil.createDateOffset(0, 0, -1))
            .setPayload(TestUtil.randomString("payload-"));
        doReturn(payload)
            .when(scaCertificateGenerator)
            .getContentPayload(consumer);

        Response actual = consumerResource
            .getContentAccessBody("test-uuid", "Fri, 06 Oct 2023 08:20:51 Z");

        ContentAccessListing listing = (ContentAccessListing) actual.getEntity();
        Map<Long, List<String>> contentListing = listing.getContentListing();
        assertThat(contentListing)
            .isNotNull()
            .hasSize(1)
            .containsEntry(expectedCertificate.getSerial().getId(),
                List.of(expectedCertificate.getCert(), payload.getPayload()));
    }

    @Test
    public void contentAccessIfModifiedSinceLocale() throws Exception {
        Locale.setDefault(Locale.FRANCE);
        Owner owner = createOwner();
        doReturn(owner)
            .when(ownerCurator)
            .findOwnerById(owner.getOwnerId());

        Consumer consumer = createConsumer(owner);
        doReturn(consumer)
            .when(consumerCurator)
            .verifyAndLookupConsumer(anyString());

        SCACertificate expectedCertificate = createContentAccessCertificate(
            "expected-key", "expected-cert", 18084729L);
        doReturn(expectedCertificate)
            .when(scaCertificateGenerator)
            .getX509Certificate(consumer);

        ContentAccessPayload payload = new ContentAccessPayload()
            .setTimestamp(new Date())
            .setPayload(TestUtil.randomString("payload-"));
        doReturn(payload)
            .when(scaCertificateGenerator)
            .getContentPayload(consumer);

        Response contentAccess = consumerResource
            .getContentAccessBody("test-uuid", "Fri, 06 Oct 2023 08:20:51 Z");

        ContentAccessListing listing = (ContentAccessListing) contentAccess.getEntity();
        Map<Long, List<String>> contentListing = listing.getContentListing();
        assertThat(contentListing)
            .isNotNull()
            .hasSize(1)
            .containsEntry(expectedCertificate.getSerial().getId(),
                List.of(expectedCertificate.getCert(), payload.getPayload()));
    }

    @Test
    public void testSearchConsumersRequiresNonNullSearchCriteria() {
        assertThrows(BadRequestException.class, () ->
            consumerResource.searchConsumers(null, null, null, null, null, null, null, null, null, null,
                null, null));
    }

    @Test
    public void testSearchConsumersRequiresOwnerAndEnvironmentToBeNonEmpty() {
        assertThrows(BadRequestException.class, () ->
            consumerResource.searchConsumers(null, null, null, null, null, null, null, "env", null, null,
                null, null));
    }

    @Test
    public void testSearchConsumersRequiresOwnerAndEnvironmentToBeEmpty() {
        assertThrows(BadRequestException.class, () ->
            consumerResource.searchConsumers(null, null, null, null, null, null, null, "", null, null,
                null, null));
    }

    @Test
    @SuppressWarnings("checkstyle:indentation")
    public void testSearchConsumersRequiresNonEmptySearchCriteria() {
        assertThrows(BadRequestException.class, () ->
            consumerResource.searchConsumers("", Collections.emptySet(), "", Collections.emptyList(),
                Collections.emptyList(), null, Collections.emptyList(), null, null, null, null, null));
    }

    @Test
    public void testSearchConsumersDoesNotRequirePagingForSmallResultSets() {
        ResteasyContext.pushContext(PageRequest.class, null);
        List<Consumer> expected = Stream.generate(this::createConsumer)
            .limit(5)
            .collect(Collectors.toList());

        doReturn(5L).when(this.consumerCurator).getConsumerCount(any(ConsumerQueryArguments.class));
        doReturn(expected).when(this.consumerCurator).findConsumers(any(ConsumerQueryArguments.class));

        Stream<ConsumerDTOArrayElement> result = this.consumerResource
            .searchConsumers("username", null, null, null, null, null, null, null, null, null, null, null);

        assertNotNull(result);
        assertEquals(expected.size(), result.count());
    }

    @Test
    public void testSearchConsumersRequiresPagingForLargeResultSets() {
        ResteasyContext.pushContext(PageRequest.class, null);
        doReturn(12000L).when(this.consumerCurator).getConsumerCount(any(ConsumerQueryArguments.class));

        assertThrows(BadRequestException.class, () -> this.consumerResource
            .searchConsumers("username", null, null, null, null, null, null, null, null, null, null, null));
    }

    @Test
    public void testFindConsumersByOwner() {
        Owner owner = this.createOwner("test_owner");

        List<Consumer> expected = Stream.generate(this::createConsumer)
            .limit(3)
            .collect(Collectors.toList());

        doReturn(expected).when(this.consumerCurator).findConsumers(any(ConsumerQueryArguments.class));

        ArgumentCaptor<ConsumerQueryArguments> captor = ArgumentCaptor.forClass(ConsumerQueryArguments.class);
        Stream<ConsumerDTOArrayElement> result = this.consumerResource.searchConsumers(null, null,
            owner.getKey(), null, null, null, null, null, null, null, null, null);

        // Verify the input passthrough is working properly
        verify(this.consumerCurator, times(1)).findConsumers(captor.capture());
        ConsumerQueryArguments builder = captor.getValue();

        assertNotNull(builder);
        assertSame(owner, builder.getOwner());
        assertNull(builder.getUsername());
        assertNull(builder.getUuids());
        assertNull(builder.getTypes());
        assertNull(builder.getHypervisorIds());
        assertNull(builder.getFacts());

        // Verify the output passthrough is working properly
        assertNotNull(result);
        assertEquals(expected.size(), result.count());

        // Impl note: the DTOs converted from mocks will have nothing in them, so there's no reason
        // to bother checking that we got the exact ones we're expecting.
    }

    @Test
    public void testFindConsumersByUsername() {
        String username = "test_user";

        List<Consumer> expected = Stream.generate(this::createConsumer)
            .limit(3)
            .collect(Collectors.toList());

        doReturn(expected).when(this.consumerCurator).findConsumers(any(ConsumerQueryArguments.class));

        ArgumentCaptor<ConsumerQueryArguments> captor = ArgumentCaptor.forClass(ConsumerQueryArguments.class);
        Stream<ConsumerDTOArrayElement> result = this.consumerResource.searchConsumers(username, null, null,
            null, null, null, null, null, null, null, null, null);

        // Verify the input passthrough is working properly
        verify(this.consumerCurator, times(1)).findConsumers(captor.capture());
        ConsumerQueryArguments builder = captor.getValue();

        assertNotNull(builder);
        assertNull(builder.getOwner());
        assertEquals(username, builder.getUsername());
        assertNull(builder.getUuids());
        assertNull(builder.getTypes());
        assertNull(builder.getHypervisorIds());
        assertNull(builder.getFacts());

        // Verify the output passthrough is working properly
        assertNotNull(result);
        assertEquals(expected.size(), result.count());

        // Impl note: the DTOs converted from mocks will have nothing in them, so there's no reason
        // to bother checking that we got the exact ones we're expecting.
    }

    @Test
    public void testFindConsumersByUuid() {
        List<String> uuids = Arrays.asList("uuid-1", "uuid-2", "uuid-3");

        List<Consumer> expected = Stream.generate(this::createConsumer)
            .limit(3)
            .collect(Collectors.toList());

        doReturn(expected).when(this.consumerCurator).findConsumers(any(ConsumerQueryArguments.class));

        ArgumentCaptor<ConsumerQueryArguments> captor = ArgumentCaptor.forClass(ConsumerQueryArguments.class);
        Stream<ConsumerDTOArrayElement> result = this.consumerResource.searchConsumers(null, null, null,
            uuids, null, null, null, null, null, null, null, null);

        // Verify the input passthrough is working properly
        verify(this.consumerCurator, times(1)).findConsumers(captor.capture());
        ConsumerQueryArguments builder = captor.getValue();

        assertNotNull(builder);
        assertNull(builder.getOwner());
        assertNull(builder.getUsername());
        assertEquals(uuids, builder.getUuids());
        assertNull(builder.getTypes());
        assertNull(builder.getHypervisorIds());
        assertNull(builder.getFacts());

        // Verify the output passthrough is working properly
        assertNotNull(result);
        assertEquals(expected.size(), result.count());

        // Impl note: the DTOs converted from mocks will have nothing in them, so there's no reason
        // to bother checking that we got the exact ones we're expecting.
    }

    @Test
    public void testFindConsumersByType() {
        List<ConsumerType> types = Stream.generate(this::buildConsumerType)
            .limit(3)
            .collect(Collectors.toList());

        Map<String, ConsumerType> typeMap = types.stream()
            .collect(Collectors.toMap(ConsumerType::getLabel, Function.identity()));

        doAnswer(new Answer<List<ConsumerType>>() {
            @Override
            public List<ConsumerType> answer(InvocationOnMock iom) {
                Set<String> labels = (Set<String>) iom.getArguments()[0];
                List<ConsumerType> output = new ArrayList<>();

                for (String label : labels) {
                    if (typeMap.containsKey(label)) {
                        output.add(typeMap.get(label));
                    }
                }

                return output;
            }
        }).when(this.consumerTypeCurator).getByLabels(anySet());

        List<Consumer> expected = Stream.generate(this::createConsumer)
            .limit(3)
            .collect(Collectors.toList());

        doReturn(expected).when(this.consumerCurator).findConsumers(any(ConsumerQueryArguments.class));

        ArgumentCaptor<ConsumerQueryArguments> captor = ArgumentCaptor.forClass(ConsumerQueryArguments.class);
        Stream<ConsumerDTOArrayElement> result = this.consumerResource.searchConsumers(null, typeMap.keySet(),
            null, null, null, null, null, null, null, null, null, null);

        // Verify the input passthrough is working properly
        verify(this.consumerCurator, times(1)).findConsumers(captor.capture());
        ConsumerQueryArguments builder = captor.getValue();

        assertNotNull(builder);
        assertNull(builder.getOwner());
        assertNull(builder.getUsername());
        assertNull(builder.getUuids());
        // We need an order-agnostic check here, and Hamcrest's matcher is busted, so we have to
        // do this one manually.
        assertTrue(Util.collectionsAreEqual(types, builder.getTypes()));
        assertNull(builder.getHypervisorIds());
        assertNull(builder.getFacts());

        // Verify the output passthrough is working properly
        assertNotNull(result);
        assertEquals(expected.size(), result.count());

        // Impl note: the DTOs converted from mocks will have nothing in them, so there's no reason
        // to bother checking that we got the exact ones we're expecting.
    }

    @Test
    public void testFindConsumersByHypervisorId() {
        List<String> hids = Arrays.asList("hypervisor-1", "hypervisor-2", "hypervisor-3");

        List<Consumer> expected = Stream.generate(this::createConsumer)
            .limit(3)
            .collect(Collectors.toList());

        doReturn(expected).when(this.consumerCurator).findConsumers(any(ConsumerQueryArguments.class));

        ArgumentCaptor<ConsumerQueryArguments> captor = ArgumentCaptor.forClass(ConsumerQueryArguments.class);
        Stream<ConsumerDTOArrayElement> result = this.consumerResource.searchConsumers(null, null, null,
            null, hids, null, null, null, null, null, null, null);

        // Verify the input passthrough is working properly
        verify(this.consumerCurator, times(1)).findConsumers(captor.capture());
        ConsumerQueryArguments builder = captor.getValue();

        assertNotNull(builder);
        assertNull(builder.getOwner());
        assertNull(builder.getUsername());
        assertNull(builder.getUuids());
        assertNull(builder.getTypes());
        assertEquals(hids, builder.getHypervisorIds());
        assertNull(builder.getFacts());

        // Verify the output passthrough is working properly
        assertNotNull(result);
        assertEquals(expected.size(), result.count());

        // Impl note: the DTOs converted from mocks will have nothing in them, so there's no reason
        // to bother checking that we got the exact ones we're expecting.
    }

    @Test
    public void testFindConsumersByFact() {
        List<String> factsParam = List.of(
            "fact-1:value-1a",
            "fact-1:value-1b",
            "fact-2:value-2",
            "fact-3:value-3");

        Map<String, Collection<String>> factsMap = Map.of(
            "fact-1", Set.of("value-1a", "value-1b"),
            "fact-2", Set.of("value-2"),
            "fact-3", Set.of("value-3"));

        List<Consumer> expected = Stream.generate(this::createConsumer)
            .limit(3)
            .collect(Collectors.toList());

        doReturn(expected).when(this.consumerCurator).findConsumers(any(ConsumerQueryArguments.class));

        ArgumentCaptor<ConsumerQueryArguments> captor = ArgumentCaptor.forClass(ConsumerQueryArguments.class);
        Stream<ConsumerDTOArrayElement> result = this.consumerResource.searchConsumers(null, null, null,
            null, null, null, factsParam, null, null, null, null, null);

        // Verify the input passthrough is working properly
        verify(this.consumerCurator, times(1)).findConsumers(captor.capture());
        ConsumerQueryArguments builder = captor.getValue();

        assertNotNull(builder);
        assertNull(builder.getOwner());
        assertNull(builder.getUsername());
        assertNull(builder.getUuids());
        assertNull(builder.getTypes());
        assertNull(builder.getHypervisorIds());
        assertEquals(factsMap, builder.getFacts());

        // Verify the output passthrough is working properly
        assertNotNull(result);
        assertEquals(expected.size(), result.count());

        // Impl note: the DTOs converted from mocks will have nothing in them, so there's no reason
        // to bother checking that we got the exact ones we're expecting.
    }

    private IdentityCertificate createIdCert() {
        IdentityCertificate idCert = TestUtil.createIdCert();
        CertificateSerial serial = idCert.getSerial();
        serial.setId(Util.generateUniqueLong());
        return idCert;
    }

    private IdentityCertificate createIdCert(Date expiration) {
        IdentityCertificate idCert = TestUtil.createIdCert(expiration);
        CertificateSerial serial = idCert.getSerial();
        serial.setId(Util.generateUniqueLong());
        return idCert;
    }

    @Nested
    @DisplayName("Consumer Content Overrides Tests")
    public class ConsumerContentOverridesTests extends DatabaseTestFixture {
        private ConsumerResource buildResource() {
            return this.injector.getInstance(ConsumerResource.class);
        }

        @Test
        public void testAddConsumerContentOverrides() {
            Owner owner = this.createOwner(TestUtil.randomString());
            Consumer c1 = this.createConsumer(owner);
            Consumer c2 = this.createConsumer(owner);

            List<ContentOverrideDTO> overridesToAdd = new ArrayList<>();

            for (int idx = 1; idx <= 3; ++idx) {
                String name = String.format("existing-c1-co-%d", idx);
                String label = String.format("existing-c1-label-%d", idx);

                // Create and persist some initial consumer 1 content overrides
                ConsumerContentOverride contentOverride = new ConsumerContentOverride()
                    .setConsumer(c1)
                    .setName(name)
                    .setContentLabel(label)
                    .setValue(TestUtil.randomString());

                this.consumerContentOverrideCurator.create(contentOverride);

                // Create and persist some initial consumer 2 content overrides
                ConsumerContentOverride c2ContentOverride = new ConsumerContentOverride()
                    .setConsumer(c2)
                    .setName(String.format("c2-co-%d", idx))
                    .setContentLabel(String.format("c2-label-%d", idx))
                    .setValue(TestUtil.randomString());

                this.consumerContentOverrideCurator.create(c2ContentOverride);

                // Add a content override to update the persisted ConsumerContentOverride
                ContentOverrideDTO contentOverrideUpdate = new ContentOverrideDTO();
                contentOverrideUpdate.setName(name);
                contentOverrideUpdate.setContentLabel(label);
                contentOverrideUpdate.setValue(TestUtil.randomString(label + "-modified-"));

                overridesToAdd.add(contentOverrideUpdate);

                // Add an unpersisted net new content overrides
                ContentOverrideDTO netNewContentOverride = new ContentOverrideDTO();
                netNewContentOverride.setName(String.format("new-co-%d", idx));
                netNewContentOverride.setContentLabel(String.format("new-co-label-%d", idx));
                netNewContentOverride.setValue(TestUtil.randomString());

                overridesToAdd.add(netNewContentOverride);
            }

            Stream<ContentOverrideDTO> actual = this.buildResource()
                .addConsumerContentOverrides(c1.getUuid(), overridesToAdd);

            assertThat(actual)
                .isNotNull()
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
                .containsExactlyInAnyOrderElementsOf(overridesToAdd);
        }
    }
}
