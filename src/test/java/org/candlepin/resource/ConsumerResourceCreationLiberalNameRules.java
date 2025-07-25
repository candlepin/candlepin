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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import org.candlepin.async.JobManager;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.auth.permissions.OwnerPermission;
import org.candlepin.auth.permissions.Permission;
import org.candlepin.auth.permissions.PermissionFactory;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.controller.ContentAccessManager;
import org.candlepin.controller.EntitlementCertificateService;
import org.candlepin.controller.Entitler;
import org.candlepin.controller.ManifestManager;
import org.candlepin.controller.PoolManager;
import org.candlepin.controller.PoolService;
import org.candlepin.controller.RefresherFactory;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.dto.api.server.v1.ConsumerDTO;
import org.candlepin.dto.api.server.v1.ConsumerTypeDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.AnonymousCloudConsumerCurator;
import org.candlepin.model.AnonymousContentAccessCertificateCurator;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerContentOverrideCurator;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.model.DistributorVersionCurator;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.EnvironmentContentCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.PermissionBlueprint;
import org.candlepin.model.Role;
import org.candlepin.model.User;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.pki.certs.AnonymousCertificateGenerator;
import org.candlepin.pki.certs.IdentityCertificateGenerator;
import org.candlepin.pki.certs.SCACertificateGenerator;
import org.candlepin.policy.SystemPurposeComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.policy.js.consumer.ConsumerRules;
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
import org.candlepin.test.TestUtil;
import org.candlepin.util.ContentOverrideValidator;
import org.candlepin.util.FactValidator;

import com.google.inject.util.Providers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;


@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ConsumerResourceCreationLiberalNameRules {

    private static final Logger log = LoggerFactory.getLogger(ConsumerResourceCreationTest.class);

    private static final String USER = "testuser";

    @Mock
    protected UserServiceAdapter userService;
    @Mock
    protected IdentityCertificateGenerator idCertGenerator;
    @Mock
    protected SubscriptionServiceAdapter subscriptionService;
    @Mock
    protected ConsumerCurator consumerCurator;
    @Mock
    protected ConsumerTypeCurator consumerTypeCurator;
    @Mock
    protected OwnerCurator ownerCurator;
    @Mock
    private EntitlementCurator entitlementCurator;
    @Mock
    private EntitlementCertServiceAdapter entitlementCertServiceAdapter;
    @Mock
    protected EventSink sink;
    @Mock
    protected ActivationKeyCurator activationKeyCurator;
    @Mock
    protected ComplianceRules complianceRules;
    @Mock
    protected SystemPurposeComplianceRules systemPurposeComplianceRules;
    @Mock
    protected DeletedConsumerCurator deletedConsumerCurator;
    @Mock
    protected ConsumerContentOverrideCurator consumerContentOverrideCurator;
    @Mock
    protected ConsumerBindUtil consumerBindUtil;
    @Mock
    protected ConsumerEnricher consumerEnricher;
    @Mock
    protected EnvironmentCurator environmentCurator;
    @Mock
    protected JobManager jobManager;
    @Mock
    protected DTOValidator dtoValidator;
    @Mock
    private PoolManager poolManager;
    @Mock
    private RefresherFactory refresherFactory;
    @Mock
    private EventFactory eventFactory;
    @Mock
    private ContentAccessManager contentAccessManager;
    @Mock
    private Entitler entitler;
    @Mock
    private ManifestManager manifestManager;
    @Mock
    private ConsumerRules consumerRules;
    @Mock
    private CalculatedAttributesUtil calculatedAttributesUtil;
    @Mock
    private DistributorVersionCurator distributorVersionCurator;
    @Mock
    private PrincipalProvider principalProvider;
    @Mock
    private ContentOverrideValidator contentOverrideValidator;
    @Mock
    private EnvironmentContentCurator environmentContentCurator;
    @Mock
    private EntitlementCertificateService entCertService;
    @Mock
    private PoolService poolService;
    @Mock
    private SCACertificateGenerator scaCertificateGenerator;
    @Mock
    private AnonymousCertificateGenerator anonymousCertificateGenerator;
    @Mock
    private AnonymousCloudConsumerCurator anonymousConsumerCurator;
    @Mock
    private AnonymousContentAccessCertificateCurator anonymousCertCurator;
    @Mock
    private OwnerServiceAdapter ownerService;
    @Mock
    private ConsumerCloudDataBuilder consumerCloudDataBuilder;
    protected ModelTranslator modelTranslator;

    private I18n i18n;

    private ConsumerResource resource;
    private ConsumerTypeDTO systemDto;
    private ConsumerType system;
    protected DevConfig config;
    protected Owner owner;
    protected Role role;
    private User user;

    @BeforeEach
    public void init() throws Exception {
        this.i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);

        this.modelTranslator = new StandardTranslator(this.consumerTypeCurator, this.environmentCurator,
            this.ownerCurator);

        this.config = initConfig();
        this.resource = new ConsumerResource(
            this.consumerCurator,
            this.consumerTypeCurator,
            this.subscriptionService,
            this.entitlementCurator,
            this.idCertGenerator,
            this.entitlementCertServiceAdapter,
            this.i18n,
            this.sink,
            this.eventFactory,
            this.userService,
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
            new FactValidator(this.config, () -> this.i18n),
            new ConsumerTypeValidator(consumerTypeCurator, i18n),
            this.consumerEnricher,
            Providers.of(new GuestMigration(consumerCurator)),
            this.modelTranslator,
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

        this.system = this.initConsumerType();
        this.mockConsumerType(this.system);
        this.systemDto = this.modelTranslator.translate(this.system, ConsumerTypeDTO.class);

        this.owner = new Owner()
            .setId(TestUtil.randomString())
            .setKey("test_owner")
            .setDisplayName("test_owner");

        user = new User(USER, "");
        PermissionBlueprint p = new PermissionBlueprint(PermissionFactory.PermissionType.OWNER, owner,
            Access.ALL);
        role = new Role();
        role.addPermission(p);
        role.addUser(user);

        when(consumerCurator.create(any(Consumer.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        when(consumerCurator.create(any(Consumer.class), any(Boolean.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        when(consumerCurator.update(any(Consumer.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        when(userService.findByLogin(USER)).thenReturn(user);
        IdentityCertificate cert = new IdentityCertificate();
        cert.setKey("testKey");
        cert.setCert("testCert");
        cert.setId("testId");
        cert.setSerial(new CertificateSerial(new Date()));
        when(idCertGenerator.generate(any(Consumer.class))).thenReturn(cert);
        when(ownerCurator.getByKey(owner.getKey())).thenReturn(owner);
        when(complianceRules.getStatus(
            any(Consumer.class), any(Date.class), any(Boolean.class), any(Boolean.class)))
            .thenReturn(new ComplianceStatus(new Date()));
    }

    public ConsumerType initConsumerType() {
        return new ConsumerType(ConsumerType.ConsumerTypeEnum.SYSTEM);
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

            doAnswer((Answer<ConsumerType>) invocation -> {
                Object[] args = invocation.getArguments();
                Consumer consumer = (Consumer) args[0];
                ConsumerTypeCurator curator = (ConsumerTypeCurator) invocation.getMock();
                ConsumerType ctype1;

                if (consumer == null || consumer.getTypeId() == null) {
                    throw new IllegalArgumentException("consumer is null or lacks a type ID");
                }

                ctype1 = curator.get(consumer.getTypeId());
                if (ctype1 == null) {
                    throw new IllegalStateException("No such consumer type: " + consumer.getTypeId());
                }

                return ctype1;
            }).when(consumerTypeCurator).getConsumerType(any(Consumer.class));
        }

        return ctype;
    }

    public DevConfig initConfig() {
        DevConfig config = TestConfig.defaults();

        config.setProperty(ConfigProperties.CONSUMER_SYSTEM_NAME_PATTERN, ".+");
        config.setProperty(ConfigProperties.CONSUMER_PERSON_NAME_PATTERN, ".+");
        config.setProperty(ConfigProperties.USE_SYSTEM_UUID_FOR_MATCHING, "true");

        return config;
    }

    protected ConsumerDTO createConsumer(String consumerName) {
        Collection<Permission> perms = new HashSet<>();
        perms.add(new OwnerPermission(owner, Access.ALL));
        Principal principal = new UserPrincipal(USER, perms, false);

        return createConsumer(consumerName, principal);
    }

    private ConsumerDTO createConsumer(String consumerName, Principal principal) {
        when(this.principalProvider.get()).thenReturn(principal);
        ConsumerDTO consumer = TestUtil.createConsumerDTO(consumerName, null, null, systemDto);
        return this.resource.createConsumer(consumer, USER, owner.getKey(), null, true);
    }

    // These overridden tests should receive an error with the default regex (from superclass),
    // but should be okay here

    @Test
    public void allowNameThatContainsMultibyteKorean() {
        assertNotNull(createConsumer("서브스크립션 "));
    }

    @Test
    public void allowNameThatContainsMultibyteOriya() {
        assertNotNull(createConsumer("ପରିବେଶ"));
    }

    @Test
    public void allowNameThatContainsBadCharacter() {
        createConsumer("bar$%camp");
    }

    @Test
    public void allowNameThatContainsWhitespace() {
        createConsumer("abc123 456");
    }

    @Test
    public void allowNameThatStartsWithBadCharacter() {
        createConsumer("<foo");
    }

    /*
     * This is a special case. We should never allow a name starting with pound,
     * regardless of the regex config.
     */
    @Test
    public void disallowNameThatStartsWithPound() {
        assertThrows(BadRequestException.class,
            () -> createConsumer("#pound"));
    }

    @Test
    public void allowNameThatContainsJustAboutEverything() {
        createConsumer("0123456789-=~!@#$%^&*()_+{}[]|\\:;\"'<>,.?/");
    }

    @Test
    public void allowNameThatContainsRockDots() {
        createConsumer("áàéíñó€áâæÀÁåàüÄÆùúÆÔÓû");
    }

    @Test
    public void allowNameThatContainsFunUnicode() {
        // this is roughly: skull and crossbones,
        // snowman, neigher less than nor greater than,
        // intterobang
        createConsumer("\u2620 \u2603 \u2278 \u203D");
    }
}
