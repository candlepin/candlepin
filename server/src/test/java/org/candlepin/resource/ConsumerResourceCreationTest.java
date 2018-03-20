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

import static org.candlepin.test.TestUtil.createConsumerDTO;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.auth.Access;
import org.candlepin.auth.NoAuthPrincipal;
import org.candlepin.auth.Principal;
import org.candlepin.auth.TrustedUserPrincipal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.auth.permissions.OwnerPermission;
import org.candlepin.auth.permissions.Permission;
import org.candlepin.auth.permissions.PermissionFactory.PermissionType;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.config.MapConfiguration;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.config.ConfigProperties;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.StandardTranslator;
import org.candlepin.dto.api.v1.ConsumerDTO;
import org.candlepin.dto.api.v1.ConsumerTypeDTO;
import org.candlepin.model.CertificateSerial;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerContentOverrideCurator;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.PermissionBlueprint;
import org.candlepin.model.Role;
import org.candlepin.model.User;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.resource.util.ConsumerBindUtil;
import org.candlepin.resource.util.ConsumerEnricher;
import org.candlepin.resource.util.GuestMigration;
import org.candlepin.service.IdentityCertServiceAdapter;
import org.candlepin.service.OwnerServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.test.TestUtil;
import org.candlepin.util.FactValidator;
import org.candlepin.util.ServiceLevelValidator;

import com.google.inject.util.Providers;

import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import javax.inject.Provider;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
/*
 * FIXME: this seems to only test creating
 * system consumers.
 */
public class ConsumerResourceCreationTest {

    private static final String USER = "testuser";

    @Mock protected UserServiceAdapter userService;
    @Mock private IdentityCertServiceAdapter idCertService;
    @Mock private SubscriptionServiceAdapter subscriptionService;
    @Mock private OwnerServiceAdapter ownerService;
    @Mock private ConsumerCurator consumerCurator;
    @Mock private ConsumerTypeCurator consumerTypeCurator;
    @Mock private OwnerCurator ownerCurator;
    @Mock private EventSink sink;
    @Mock private EventFactory factory;
    @Mock private ActivationKeyCurator activationKeyCurator;
    @Mock private ComplianceRules complianceRules;
    @Mock private DeletedConsumerCurator deletedConsumerCurator;
    @Mock private ConsumerContentOverrideCurator consumerContentOverrideCurator;
    @Mock private ServiceLevelValidator serviceLevelValidator;
    @Mock private ConsumerBindUtil consumerBindUtil;
    @Mock private ConsumerEnricher consumerEnricher;
    @Inject protected ModelTranslator modelTranslator;

    private I18n i18n;

    private ConsumerResource resource;
    private ConsumerTypeDTO systemDto;
    private ConsumerType system;
    protected Configuration config;
    protected Owner owner;
    protected Role role;
    private User user;
    private GuestMigration testMigration;
    private Provider<GuestMigration> migrationProvider;

    @Before
    public void init() throws Exception {
        this.i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);

        this.modelTranslator = new StandardTranslator();

        testMigration = new GuestMigration(consumerCurator);
        migrationProvider = Providers.of(testMigration);

        this.config = initConfig();
        this.resource = new ConsumerResource(
            this.consumerCurator, this.consumerTypeCurator, null, this.subscriptionService, this.ownerService,
            null, this.idCertService, null, this.i18n, this.sink, null, null, null, this.userService, null,
            null, this.ownerCurator, this.activationKeyCurator, null, this.complianceRules,
            this.deletedConsumerCurator, null, null, this.config, null, null, null, this.consumerBindUtil,
            null, null, new FactValidator(this.config, this.i18n),
            null, consumerEnricher, migrationProvider, modelTranslator);

        this.system = initSystem();
        this.systemDto = initSystemDto();

        owner = new Owner("test_owner");
        owner.setId(TestUtil.randomString());
        user = new User(USER, "");
        PermissionBlueprint p = new PermissionBlueprint(PermissionType.OWNER, owner,
            Access.ALL);
        role = new Role();
        role.addPermission(p);
        role.addUser(user);

        when(consumerCurator.create(any(Consumer.class))).thenAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return invocation.getArguments()[0];
            }
        });

        when(consumerCurator.create(any(Consumer.class), any(Boolean.class))).thenAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return invocation.getArguments()[0];
            }
        });

        when(consumerTypeCurator.lookupByLabel(system.getLabel())).thenReturn(system);
        when(userService.findByLogin(USER)).thenReturn(user);
        IdentityCertificate cert = new IdentityCertificate();
        cert.setKey("testKey");
        cert.setCert("testCert");
        cert.setId("testId");
        cert.setSerial(new CertificateSerial(new Date()));
        when(idCertService.generateIdentityCert(any(Consumer.class)))
                .thenReturn(cert);
        when(ownerCurator.lookupByKey(owner.getKey())).thenReturn(owner);
        when(complianceRules.getStatus(any(Consumer.class),
                any(Date.class), any(Boolean.class), any(Boolean.class)))
                .thenReturn(new ComplianceStatus(new Date()));
    }

    public ConsumerTypeDTO initSystemDto() {
        ConsumerTypeDTO systemtype = new ConsumerTypeDTO(ConsumerType.ConsumerTypeEnum.SYSTEM);
        return systemtype;
    }

    public ConsumerType initSystem() {
        ConsumerType system = new ConsumerType(ConsumerType.ConsumerTypeEnum.SYSTEM);
        return system;
    }


    private static class ConfigForTesting extends MapConfiguration {
        @SuppressWarnings("serial")
        public ConfigForTesting() {
            super(new HashMap<String, String>() {
                {
                    this.put(ConfigProperties.CONSUMER_SYSTEM_NAME_PATTERN,
                        "[\\#\\?\\'\\`\\!@{}()\\[\\]\\?&\\w-\\.]+");
                    this.put(ConfigProperties.CONSUMER_PERSON_NAME_PATTERN,
                        "[\\#\\?\\'\\`\\!@{}()\\[\\]\\?&\\w-\\.]+");
                }
            });
        }
    }

    public Configuration initConfig() {
        Configuration config = new ConfigForTesting();
        return config;
    }


    protected ConsumerDTO createConsumer(String consumerName) {
        Collection<Permission> perms = new HashSet<>();
        perms.add(new OwnerPermission(owner, Access.ALL));
        Principal principal = new UserPrincipal(USER, perms, false);

        List<String> empty = Collections.emptyList();
        return createConsumer(consumerName, principal, empty);
    }

    private ConsumerDTO createConsumer(String consumerName, Principal principal,
        List<String> activationKeys) {
        ConsumerDTO consumer = createConsumerDTO(consumerName, null, null, systemDto);
        return this.resource.create(consumer, principal, USER, owner.getKey(),
            null, true);
    }

    @Test
    public void acceptedConsumerName() {
        Assert.assertNotNull(createConsumer("test_user"));
    }

    @Test
    public void camelCaseName() {
        Assert.assertNotNull(createConsumer("ConsumerTest32953"));
    }

    @Test
    public void startsWithUnderscore() {
        Assert.assertNotNull(createConsumer("__init__"));
    }

    @Test
    public void startsWithDash() {
        Assert.assertNotNull(createConsumer("-dash"));
    }

    @Test
    public void containsNumbers() {
        Assert.assertNotNull(createConsumer("testmachine99"));
    }

    @Test
    public void startsWithNumbers() {
        Assert.assertNotNull(createConsumer("001test7"));
    }

    @Test
    public void containsPeriods() {
        Assert.assertNotNull(createConsumer("test-system.resource.net"));
    }

    @Test
    public void containsUserServiceChars() {
        Assert.assertNotNull(createConsumer("{bob}'s_b!g_#boi.`?uestlove!x"));
    }

    // These fail with the default consumer name pattern
    @Test(expected = BadRequestException.class)
    public void containsMultibyteKorean() {
        createConsumer("서브스크립션 ");
    }

    @Test(expected = BadRequestException.class)
    public void containsMultibyteOriya() {
        createConsumer("ପରିବେଶ");
    }

    @Test(expected = BadRequestException.class)
    public void startsWithPound() {
        createConsumer("#pound");
    }

    @Test(expected = BadRequestException.class)
    public void emptyConsumerName() {
        createConsumer("");
    }

    @Test(expected = BadRequestException.class)
    public void nullConsumerName() {
        createConsumer(null);
    }

    @Test(expected = BadRequestException.class)
    public void startsWithBadCharacter() {
        createConsumer("#foo");
    }

    @Test(expected = BadRequestException.class)
    public void containsBadCharacter() {
        createConsumer("bar$%camp");
    }

    @Test(expected = ForbiddenException.class)
    public void authRequired() {
        Principal p = new NoAuthPrincipal();
        List<String> empty = Collections.emptyList();
        createConsumer("sys.example.com", p, empty);
    }

    private List<String> mockActivationKeys() {
        ActivationKey key1 = new ActivationKey("key1", owner);
        when(activationKeyCurator.lookupForOwner("key1", owner)).thenReturn(key1);
        List<String> keys = new LinkedList<>();
        keys.add(key1.getName());
        return keys;
    }

    private String createKeysString(List<String> activationKeys) {
        // Allow empty string through because we accept it for ",foo" etc.
        if (!activationKeys.isEmpty()) {
            return StringUtils.join(activationKeys, ',');
        }
        return null;
    }

    @Test
    public void oauthRegistrationSupported() {
        // Should be able to register successfully with as a trusted user principal:
        Principal p = new TrustedUserPrincipal("anyuser");
        ConsumerDTO consumer = createConsumerDTO("sys.example.com", null, null, systemDto);
        resource.create(consumer, p, null, owner.getKey(), null, true);
    }

    @Test
    public void registerWithKeys() {
        // No auth should be required for registering with keys:
        Principal p = new NoAuthPrincipal();
        List<String> keys = mockActivationKeys();
        ConsumerDTO consumer = createConsumerDTO("sys.example.com", null, null, systemDto);
        resource.create(consumer, p, null, owner.getKey(), createKeysString(keys), true);
        for (String keyName : keys) {
            verify(activationKeyCurator).lookupForOwner(keyName, owner);
        }
    }

    @Test
    public void registerFailsWithKeyWhenAutobindOnKeyAndDisabledOnOwner() {
        ConsumerType consumerType = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        when(consumerTypeCurator.lookupByLabel(systemDto.getLabel())).thenReturn(consumerType);

        // Disable autobind for the owner.
        owner.setAutobindDisabled(true);

        // Create a key that has autobind disabled.
        ActivationKey key = new ActivationKey("autobind-disabled-key", owner);
        key.setAutoAttach(true);
        when(activationKeyCurator.lookupForOwner(key.getName(), owner)).thenReturn(key);

        // No auth should be required for registering with keys:
        Principal p = new NoAuthPrincipal();
        ConsumerDTO consumer = createConsumerDTO("sys.example.com", null, null, systemDto);
        resource.create(consumer, p, null, owner.getKey(), key.getName(), true);
    }

    @Test(expected = BadRequestException.class)
    public void orgRequiredWithActivationKeys() {
        Principal p = new NoAuthPrincipal();
        List<String> keys = mockActivationKeys();
        ConsumerDTO consumer = createConsumerDTO("sys.example.com", null, null, systemDto);
        resource.create(consumer, p, null, null, createKeysString(keys), true);
    }

    @Test(expected = BadRequestException.class)
    public void cannotMixUsernameWithActivationKeys() {
        Principal p = new NoAuthPrincipal();
        List<String> keys = mockActivationKeys();
        ConsumerDTO consumer = createConsumerDTO("sys.example.com", null, null, systemDto);
        resource.create(consumer, p, USER, owner.getKey(), createKeysString(keys), true);
    }

    @Test(expected = BadRequestException.class)
    public void failIfOnlyActivationKeyDoesNotExistForOrg() {
        Principal p = new NoAuthPrincipal();
        List<String> keys = new ArrayList<>();
        keys.add("NoSuchKey");
        ConsumerDTO consumer = createConsumerDTO("sys.example.com", null, null, systemDto);
        resource.create(consumer, p, null, owner.getKey(), createKeysString(keys), true);
    }

    @Test
    public void passIfOnlyOneActivationKeyDoesNotExistForOrg() {
        Principal p = new NoAuthPrincipal();
        List<String> keys = mockActivationKeys();
        keys.add("NoSuchKey");
        ConsumerDTO consumer = createConsumerDTO("sys.example.com", null, null, systemDto);
        resource.create(consumer, p, null, owner.getKey(), createKeysString(keys), true);
    }

    @Test
    public void registerWithNoInstalledProducts() {
        Principal p = new TrustedUserPrincipal("anyuser");
        ConsumerDTO consumer = createConsumerDTO("consumerName", null, null, systemDto);
        resource.create(consumer, p, USER, owner.getKey(), null, true);
    }

    @Test
    public void registerWithNullReleaseVer() {
        Principal p = new TrustedUserPrincipal("anyuser");
        ConsumerDTO consumer = createConsumerDTO("consumername", null, null, systemDto);
        consumer.setReleaseVersion(null);
        resource.create(consumer, p, USER, owner.getKey(), null, true);

    }

    @Test
    public void registerWithEmptyReleaseVer() {
        Principal p = new TrustedUserPrincipal("anyuser");
        ConsumerDTO consumer = createConsumerDTO("consumername", null, null, systemDto);
        consumer.setReleaseVersion("");
        resource.create(consumer, p, USER, owner.getKey(), null, true);
    }

    @Test
    public void registerWithNoReleaseVer() {
        Principal p = new TrustedUserPrincipal("anyuser");
        ConsumerDTO consumer = createConsumerDTO("consumername", null, null, systemDto);
        resource.create(consumer, p, USER, owner.getKey(), null, true);
    }

    @Test
    public void setStatusOnCreate() {
        Principal p = new TrustedUserPrincipal("anyuser");
        ConsumerDTO consumer = createConsumerDTO("consumername", null, null, systemDto);
        resource.create(consumer, p, USER, owner.getKey(), null, true);
        // Should be called with the consumer, null date (now),
        // no compliantUntil, and not update the consumer record
        verify(complianceRules).getStatus(any(Consumer.class), eq((Date) null), eq(false), eq(false));
    }
}
