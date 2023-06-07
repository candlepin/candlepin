/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anySet;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
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
import org.candlepin.auth.SubResource;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.config.Configuration;
import org.candlepin.config.TestConfig;
import org.candlepin.controller.ContentAccessManager;
import org.candlepin.controller.ContentAccessManager.ContentAccessMode;
import org.candlepin.controller.EntitlementCertificateGenerator;
import org.candlepin.controller.Entitler;
import org.candlepin.controller.ManifestManager;
import org.candlepin.controller.PoolManager;
import org.candlepin.controller.RefresherFactory;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.dto.api.server.v1.CertificateDTO;
import org.candlepin.dto.api.server.v1.CertificateSerialDTO;
import org.candlepin.dto.api.server.v1.ComplianceStatusDTO;
import org.candlepin.dto.api.server.v1.ConsumerDTO;
import org.candlepin.dto.api.server.v1.ConsumerDTOArrayElement;
import org.candlepin.dto.api.server.v1.ContentAccessDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.GoneException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.Cdn;
import org.candlepin.model.CdnCurator;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerContentOverrideCurator;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerCurator.ConsumerQueryArguments;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.ContentAccessCertificate;
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
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.model.dto.Subscription;
import org.candlepin.paging.PageRequest;
import org.candlepin.policy.SystemPurposeComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.policy.js.consumer.ConsumerRules;
import org.candlepin.resource.dto.AutobindData;
import org.candlepin.resource.util.CalculatedAttributesUtil;
import org.candlepin.resource.util.ConsumerBindUtil;
import org.candlepin.resource.util.ConsumerEnricher;
import org.candlepin.resource.util.ConsumerTypeValidator;
import org.candlepin.resource.util.GuestMigration;
import org.candlepin.resource.validation.DTOValidator;
import org.candlepin.service.EntitlementCertServiceAdapter;
import org.candlepin.service.IdentityCertServiceAdapter;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.test.TestUtil;
import org.candlepin.util.ContentOverrideValidator;
import org.candlepin.util.FactValidator;
import org.candlepin.util.Util;

import org.apache.commons.lang3.RandomStringUtils;
import org.jboss.resteasy.core.ResteasyContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
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

import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    @Mock private ProductServiceAdapter mockProductServiceAdapter;
    @Mock private PoolManager poolManager;
    @Mock private RefresherFactory refresherFactory;
    @Mock private EntitlementCurator entitlementCurator;
    @Mock private ComplianceRules complianceRules;
    @Mock private SystemPurposeComplianceRules systemPurposeComplianceRules;
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
    @Mock private Provider<GuestMigration> guestMigrationProvider;
    @Mock private PrincipalProvider principalProvider;
    @Mock private ConsumerContentOverrideCurator consumerContentOverrideCurator;
    @Mock private ContentOverrideValidator contentOverrideValidator;
    @Mock private EnvironmentContentCurator environmentContentCurator;
    @Mock private EntitlementCertificateGenerator entCertGenerator;

    private ModelTranslator translator;
    private ConsumerResource consumerResource;
    private ConsumerResource mockedConsumerResource;


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
    }

    private ConsumerResource buildConsumerResource() {
        return new ConsumerResource(
            this.consumerCurator,
            this.consumerTypeCurator,
            this.subscriptionServiceAdapter,
            this.mockProductServiceAdapter,
            this.entitlementCurator,
            this.identityCertServiceAdapter,
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
            this.entCertGenerator,
            this.environmentContentCurator
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

        doThrow(RuntimeException.class).when(entCertGenerator)
            .regenerateCertificatesOf(any(Entitlement.class), anyBoolean());
        when(entitlementCurator.get(eq("9999"))).thenReturn(e);
        when(subscriptionServiceAdapter.getSubscription(eq("4444"))).thenReturn(s);
        when(entitlementCertServiceAdapter.generateEntitlementCert(
            any(Entitlement.class), any(Product.class)))
            .thenThrow(new IOException());

        ConsumerResource consumerResource = new ConsumerResource(
            this.consumerCurator,
            this.consumerTypeCurator,
            this.subscriptionServiceAdapter,
            this.mockProductServiceAdapter,
            this.entitlementCurator,
            this.identityCertServiceAdapter,
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
            this.entCertGenerator,
            this.environmentContentCurator
        );

        // Fixme throw custom exception from generator instead of generic RuntimeException
        assertThrows(RuntimeException.class, () ->
            consumerResource.regenerateEntitlementCertificates(consumer.getUuid(), "9999", false, false)
        );
    }

    private void verifyCertificateSerialNumbers(
        List<CertificateSerialDTO> serials) {
        assertEquals(3, serials.size());
        assertEquals(1L, serials.get(0).getSerial());
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

        consumerResource.regenerateEntitlementCertificates(consumer.getUuid(), null, true, false);
        Mockito.verify(entCertGenerator, Mockito.times(1)).regenerateCertificatesOf(consumer, true);
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
        Tests for entitlement revocation before regeneration in SCA mode

        The table of these tests is as follows:

        Org CA mode     Consumer CA mode    cleanupEntitlements     expected behavior
        ==============  ==================  ======================  =================
        entitlement     unset               true                    retain
        sca             unset               true                    > revoke
        entitlement     entitlement         true                    retain
        sca             entitlement         true                    > revoke
        entitlement     sca                 true                    retain
        sca             sca                 true                    > revoke
        *               *                   false                   retain
        *               *                   null                    retain

        In SCA mode (either consumer or org) and the cleanupEntitlements is enabled, entitlements
        should be revoked before regeneration. If the CA mode resolves to entitlement mode or the
        flag is either set to "false" or not specified, entitlements should be retained.

        Note that the lazyRegen flag should have no affect on the above table.

        Unfortunately, this test suite is setup to heavily rely on narrow mocks, so we are verifying
        this behavior using call verifications. This should be corrected at some point in the future.
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
        Mockito.verify(this.poolManager, Mockito.times(1)).revokeAllEntitlements(eq(consumer),
            Mockito.anyBoolean());
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
        Mockito.verify(this.poolManager, Mockito.times(0)).revokeAllEntitlements(eq(consumer),
            Mockito.anyBoolean());
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
        Mockito.verify(this.poolManager, Mockito.times(0)).revokeAllEntitlements(eq(consumer),
            Mockito.anyBoolean());
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
    public void expiredIdCertGetsRegenerated() throws Exception {
        Consumer consumer = createConsumer(createOwner());
        ComplianceStatus status = new ComplianceStatus();
        when(complianceRules.getStatus(any(Consumer.class), any(Date.class), anyBoolean()))
            .thenReturn(status);
        // cert expires today which will trigger regen
        IdentityCertificate idCert = createIdCert();
        consumer.setIdCert(idCert);
        BigInteger origserial = consumer.getIdCert().getSerial().getSerial();
        when(identityCertServiceAdapter.regenerateIdentityCert(consumer)).thenReturn(createIdCert());

        ConsumerDTO c = consumerResource.getConsumer(consumer.getUuid());

        assertNotEquals(c.getIdCert().getSerial().getSerial(), origserial);
    }

    @Test
    public void validIdCertDoesNotRegenerate() {
        Consumer consumer = createConsumer(createOwner());
        ComplianceStatus status = new ComplianceStatus();
        when(complianceRules.getStatus(any(Consumer.class), any(Date.class), anyBoolean()))
            .thenReturn(status);
        consumer.setIdCert(createIdCert(TestUtil.createDate(2025, 6, 9)));
        long origSerial = consumer.getIdCert().getSerial().getSerial().longValue();

        ConsumerDTO c = consumerResource.getConsumer(consumer.getUuid());

        assertEquals(origSerial, c.getIdCert().getSerial().getSerial());
    }

    @Test
    public void doesNotGeneratesMissingIdCert() throws Exception {
        Consumer consumer = createConsumer(createOwner());
        ComplianceStatus status = new ComplianceStatus();
        when(complianceRules.getStatus(any(Consumer.class), any(Date.class), anyBoolean()))
            .thenReturn(status);
        when(identityCertServiceAdapter.regenerateIdentityCert(consumer)).thenReturn(createIdCert());

        ConsumerDTO c = consumerResource.getConsumer(consumer.getUuid());

        assertNull(c.getIdCert());
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

        Response r = consumerResource.bind("fakeConsumer", null, Arrays.asList(prodIds),
            null, null, null, false, null, null);
        assertNull(r.getEntity());
    }

    @Test
    public void futureHealing() throws Exception {
        Owner owner = createOwner();
        Consumer consumer = createConsumer(owner);
        ConsumerCurator cc = mock(ConsumerCurator.class);
        ConsumerInstalledProduct cip = mock(ConsumerInstalledProduct.class);
        Set<ConsumerInstalledProduct> products = new HashSet<>();
        products.add(cip);

        when(ownerCurator.findOwnerById(eq(owner.getId()))).thenReturn(owner);
        when(cip.getProductId()).thenReturn("product-foo");
        when(subscriptionServiceAdapter.hasUnacceptedSubscriptionTerms(eq(owner.getKey()))).thenReturn(false);
        when(cc.verifyAndLookupConsumerWithEntitlements(eq(consumer.getUuid()))).thenReturn(consumer);

        OffsetDateTime entitleDate = OffsetDateTime.now().minusYears(5);

        consumerResource.bind(consumer.getUuid(), null, null, null, null, null, false, entitleDate, null);
        AutobindData data = new AutobindData(consumer, owner)
            .on(Util.toDate(entitleDate));

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
            Arrays.asList("12232"), 1, null, null, false, null, null)
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

    protected EntitlementCertificate createEntitlementCertificate(String key, String cert) {
        EntitlementCertificate toReturn = new EntitlementCertificate();
        CertificateSerial certSerial = new CertificateSerial(1L, new Date());
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
        return certificate;
    }

    private ContentAccessCertificate createContentAccessCertificate(String key, String cert, long serialId) {
        ContentAccessCertificate certificate = new ContentAccessCertificate();
        CertificateSerial expectedSerial = new CertificateSerial(serialId, new Date());
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

        when(up.canAccess(eq(owner), eq(SubResource.CONSUMERS), eq(Access.CREATE))).thenReturn(true);
        when(this.principalProvider.get()).thenReturn(up);
        // usa.findByLogin() will return null by default no need for a when
        assertThrows(NotFoundException.class, () ->
            consumerResource.createConsumer(consumerDto, null, owner.getKey(), null, true)
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
        when(this.principalProvider.get()).thenReturn(up);

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
            consumerResource.createConsumer(consumerDto, null, owner.getKey(), null, false)
        );
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

        mockedConsumerResource.getEntitlementCertificates(consumer.getUuid(), "123");
        verify(mockedConsumerResource).revokeOnGuestMigration(consumer);
    }

    @Test
    public void testNoDryBindWhenAutobindDisabledForOwner() {
        Owner owner = createOwner();
        owner.setContentAccessMode("entitlement");
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
        Owner owner = this.createOwner();
        owner.setId(TestUtil.randomString());
        when(ownerCurator.findOwnerById(eq(owner.getId()))).thenReturn(owner);
        ConsumerType ctype = this.mockConsumerType(new ConsumerType(ConsumerType.ConsumerTypeEnum.CANDLEPIN));
        Consumer consumer = this.createConsumer(owner, ctype);

        Cdn cdn = new Cdn("cdn-label", "test", "url");

        when(cdnCurator.getByLabel(eq(cdn.getLabel()))).thenReturn(cdn);

        consumerResource.exportDataAsync(consumer.getUuid(), cdn.getLabel(),
            "prefix", cdn.getUrl());
        verify(manifestManager).generateManifestAsync(eq(consumer.getUuid()), eq(owner),
            eq(cdn.getLabel()), eq("prefix"), eq(cdn.getUrl()));
    }

    @Test
    public void deleteConsumerThrowsGoneExceptionIfConsumerDoesNotExistOnInitialLookup() {
        String targetConsumerUuid = "my-test-consumer";
        when(consumerCurator.findByUuid(eq(targetConsumerUuid))).thenReturn(null);

        UserPrincipal uap = mock(UserPrincipal.class);
        when(uap.canAccess(any(Object.class), any(SubResource.class), any(Access.class)))
            .thenReturn(Boolean.TRUE);

        assertThrows(GoneException.class, () -> consumerResource.deleteConsumer(targetConsumerUuid));
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

        assertThrows(GoneException.class, () -> consumerResource.deleteConsumer(consumer.getUuid()));
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
            consumerResource.deleteConsumer(consumer.getUuid())
        );
    }

    @Test
    public void testGetEntitlementCertificatesWithExistingSerialIdForEntitlementCertificate() {
        Consumer consumer = createConsumer();
        doReturn(consumer).when(consumerCurator).verifyAndLookupConsumer(consumer.getId());

        EntitlementCertificate expectedCertificate = createEntitlementCertificate("expected-key",
            "expected-cert", 18084729L);
        List<EntitlementCertificate> certificates = new ArrayList<>();
        certificates.add(createEntitlementCertificate("key-1", "cert-1"));
        certificates.add(createEntitlementCertificate("key-2", "cert-2"));
        certificates.add(expectedCertificate);
        doReturn(certificates).when(entitlementCertServiceAdapter).listForConsumer(any(Consumer.class));

        List<CertificateDTO> actual = consumerResource.getEntitlementCertificates(consumer.getId(),
            Long.toString(expectedCertificate.getSerial().getId()));

        assertEquals(1, actual.size());
        CertificateDTO actualCertificate = actual.get(0);
        assertEquals(expectedCertificate.getId(), actualCertificate.getId());
        assertEquals(expectedCertificate.getKey(), actualCertificate.getKey());
        assertEquals(expectedCertificate.getCert(), actualCertificate.getCert());
        assertEquals(expectedCertificate.getCreated(), actualCertificate.getCreated());
        assertEquals(expectedCertificate.getUpdated(), actualCertificate.getUpdated());

        assertEquals(expectedCertificate.getSerial().getId(), actualCertificate.getSerial().getId());
    }

    @Test
    public void testGetEntitlementCertificatesWithExistingSerialIdForSimpleContentAccessCert() {
        Consumer consumer = createConsumer();
        doReturn(consumer).when(consumerCurator).verifyAndLookupConsumer(consumer.getId());

        List<EntitlementCertificate> certificates = new ArrayList<>();
        certificates.add(createEntitlementCertificate("key-1", "cert-1"));
        certificates.add(createEntitlementCertificate("key-2", "cert-2"));
        doReturn(certificates).when(entitlementCertServiceAdapter).listForConsumer(any(Consumer.class));

        ContentAccessCertificate expectedCertificate = createContentAccessCertificate("expected-key",
            "expected-cert", 18084729L);
        doReturn(expectedCertificate).when(contentAccessManager).getCertificate(any(Consumer.class));

        List<CertificateDTO> actual = consumerResource.getEntitlementCertificates(consumer.getId(),
            Long.toString(expectedCertificate.getSerial().getId()));

        assertEquals(1, actual.size());
        CertificateDTO actualCertificate = actual.get(0);
        assertEquals(expectedCertificate.getId(), actualCertificate.getId());
        assertEquals(expectedCertificate.getKey(), actualCertificate.getKey());
        assertEquals(expectedCertificate.getCert(), actualCertificate.getCert());
        assertEquals(expectedCertificate.getCreated(), actualCertificate.getCreated());
        assertEquals(expectedCertificate.getUpdated(), actualCertificate.getUpdated());

        assertEquals(expectedCertificate.getSerial().getId(), actualCertificate.getSerial().getId());
    }

    @Test
    public void testGetEntitlementCertificatesWithUnknownSerialId() {
        Consumer consumer = createConsumer();
        doReturn(consumer).when(consumerCurator).verifyAndLookupConsumer(consumer.getId());
        List<EntitlementCertificate> certificates = createEntitlementCertificates();
        doReturn(certificates).when(entitlementCertServiceAdapter).listForConsumer(any(Consumer.class));
        ContentAccessCertificate certificate = createContentAccessCertificate(
            "key-1", "key-2", 18084729L);
        doReturn(certificate).when(contentAccessManager).getCertificate(any(Consumer.class));

        List<CertificateDTO> actual = consumerResource.getEntitlementCertificates(consumer.getId(), "123456");

        assertEquals(0, actual.size());
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
        List<String> expectedModeList = Arrays.asList(
            ContentAccessManager.ContentAccessMode.ENTITLEMENT.toDatabaseValue(),
            ContentAccessManager.ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue());
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

    @Test
    public void testSearchConsumersRequiresNonNullSearchCriteria() {
        assertThrows(BadRequestException.class, () ->
            consumerResource.searchConsumers(null, null, null, null, null, null, null, null, null, null));
    }

    @Test
    @SuppressWarnings("checkstyle:indentation")
    public void testSearchConsumersRequiresNonEmptySearchCriteria() {
        assertThrows(BadRequestException.class, () ->
            consumerResource.searchConsumers("", Collections.emptySet(), "", Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), null, null, null, null));
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
            .searchConsumers("username", null, null, null, null, null, null, null, null, null);

        assertNotNull(result);
        assertEquals(expected.size(), result.count());
    }

    @Test
    public void testSearchConsumersRequiresPagingForLargeResultSets() {
        ResteasyContext.pushContext(PageRequest.class, null);
        doReturn(5000L).when(this.consumerCurator).getConsumerCount(any(ConsumerQueryArguments.class));

        assertThrows(BadRequestException.class, () -> this.consumerResource
            .searchConsumers("username", null, null, null, null, null, null, null, null, null));
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
            owner.getKey(), null, null, null, null, null, null, null);

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
            null, null, null, null, null, null, null);

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
            uuids, null, null, null, null, null, null);

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
                public List<ConsumerType> answer(InvocationOnMock iom) throws Throwable {
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
            null, null, null, null, null, null, null, null);

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
            null, hids, null, null, null, null, null);

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
            null, null, factsParam, null, null, null, null);

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

}
