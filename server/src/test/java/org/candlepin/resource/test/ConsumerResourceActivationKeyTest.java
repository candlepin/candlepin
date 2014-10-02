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
package org.candlepin.resource.test;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
import org.candlepin.auth.Access;
import org.candlepin.auth.NoAuthPrincipal;
import org.candlepin.auth.Principal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.auth.permissions.OwnerPermission;
import org.candlepin.auth.permissions.Permission;
import org.candlepin.auth.permissions.PermissionFactory.PermissionType;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.Entitler;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerContentOverrideCurator;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.model.IdentityCertificate;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.PermissionBlueprint;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.Role;
import org.candlepin.model.User;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.candlepin.resource.ConsumerResource;
import org.candlepin.resource.dto.AutobindData;
import org.candlepin.resource.util.ConsumerBindUtil;
import org.candlepin.service.IdentityCertServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.test.TestUtil;
import org.candlepin.util.ServiceLevelValidator;

import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;


/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
/*
 * FIXME: this seems to only test creating
 * system consumers.
 */
public class ConsumerResourceActivationKeyTest {

    private static final String USER = "testuser";

    @Mock protected UserServiceAdapter userService;
    @Mock private IdentityCertServiceAdapter idCertService;
    @Mock private SubscriptionServiceAdapter subscriptionService;
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
    @Mock private Entitler entitler;

    private I18n i18n;

    private ConsumerResource resource;
    private ConsumerType system;
    protected Config config;
    protected Owner owner;
    protected Role role;
    private User user;

    @Before
    public void init() throws Exception {
        this.i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);

        this.config = initConfig();
        ConsumerBindUtil consumerBindUtil = new ConsumerBindUtil(entitler, i18n,
                consumerContentOverrideCurator, null, serviceLevelValidator);
        this.resource = new ConsumerResource(this.consumerCurator,
            this.consumerTypeCurator, null, this.subscriptionService, null,
            this.idCertService, null, this.i18n, this.sink, null, null, null,
            this.userService, null, null, null, this.ownerCurator,
            this.activationKeyCurator,
            null, this.complianceRules, this.deletedConsumerCurator,
            null, null, this.config, null, null, null, consumerBindUtil);

        this.system = initSystem();

        owner = new Owner("test_owner");
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
        when(consumerTypeCurator.lookupByLabel(system.getLabel())).thenReturn(system);
        when(userService.findByLogin(USER)).thenReturn(user);
        when(idCertService.generateIdentityCert(any(Consumer.class)))
                .thenReturn(new IdentityCertificate());
        when(ownerCurator.lookupByKey(owner.getKey())).thenReturn(owner);
        when(complianceRules.getStatus(any(Consumer.class),
                any(Date.class), any(Boolean.class), any(Boolean.class)))
                .thenReturn(new ComplianceStatus(new Date()));
    }

    public ConsumerType initSystem() {
        ConsumerType systemtype = new ConsumerType(ConsumerType.ConsumerTypeEnum.SYSTEM);
        return systemtype;
    }

    private static class ConfigForTesting extends Config {
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

    public Config initConfig() {
        Config config = new ConfigForTesting();
        return config;
    }


    protected Consumer createConsumer(String consumerName) {
        Collection<Permission> perms = new HashSet<Permission>();
        perms.add(new OwnerPermission(owner, Access.ALL));
        Principal principal = new UserPrincipal(USER, perms, false);

        List<String> empty = Collections.emptyList();
        return createConsumer(consumerName, principal, empty);
    }

    private Consumer createConsumer(String consumerName, Principal principal,
        List<String> activationKeys) {
        Consumer consumer = new Consumer(consumerName, null, null, system);
        return this.resource.create(consumer, principal, USER, owner.getKey(),
            createKeysString(activationKeys));
    }

    private List<String> mockActivationKeys() {
        ActivationKey key1 = new ActivationKey("key1", owner);
        when(activationKeyCurator.lookupForOwner("key1", owner)).thenReturn(key1);
        ActivationKey key2 = new ActivationKey("key2", owner);
        when(activationKeyCurator.lookupForOwner("key2", owner)).thenReturn(key2);
        ActivationKey key3 = new ActivationKey("key3", owner);
        when(activationKeyCurator.lookupForOwner("key3", owner)).thenReturn(key3);
        List<String> keys = new LinkedList<String>();
        keys.add(key1.getName());
        keys.add(key2.getName());
        keys.add(key3.getName());
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
    public void registerWithKeys() {
        // No auth should be required for registering with keys:
        Principal p = new NoAuthPrincipal();
        List<String> keys = mockActivationKeys();
        Consumer consumer = new Consumer("sys.example.com", null, null, system);
        resource.create(consumer, p, null, owner.getKey(), createKeysString(keys));
        for (String keyName : keys) {
            verify(activationKeyCurator).lookupForOwner(keyName, owner);
        }
    }

    @Test(expected = BadRequestException.class)
    public void orgRequiredWithActivationKeys() {
        Principal p = new NoAuthPrincipal();
        List<String> keys = mockActivationKeys();
        Consumer consumer = new Consumer("sys.example.com", null, null, system);
        resource.create(consumer, p, null, null, createKeysString(keys));
    }

    @Test(expected = BadRequestException.class)
    public void cannotMixUsernameWithActivationKeys() {
        Principal p = new NoAuthPrincipal();
        List<String> keys = mockActivationKeys();
        Consumer consumer = new Consumer("sys.example.com", null, null, system);
        resource.create(consumer, p, USER, owner.getKey(), createKeysString(keys));
    }

    @Test(expected = NotFoundException.class)
    public void failIfAnyActivationKeyDoesNotExistForOrg() {
        Principal p = new NoAuthPrincipal();
        List<String> keys = mockActivationKeys();
        keys.add("NoSuchKey");
        Consumer consumer = new Consumer("sys.example.com", null, null, system);
        resource.create(consumer, p, null, owner.getKey(), createKeysString(keys));
    }

    @Test
    public void registerWithKeyWithPoolAndInstalledProductsAutoAttach() {
        // No auth should be required for registering with keys:
        Principal p = new NoAuthPrincipal();

        Product prod = TestUtil.createProduct();
        String[] prodIds = new String[]{prod.getId()};

        Pool pool = TestUtil.createPool(owner, prod);
        pool.setId("id-string");
        List<String> poolIds = new ArrayList<String>();
        poolIds.add(pool.getId());

        List<String> keys = new ArrayList<String>();
        ActivationKey key1 = new ActivationKey("key1", owner);
        key1.addPool(pool, 0L);
        key1.setAutoAttach(true);
        keys.add(key1.getName());
        when(activationKeyCurator.lookupForOwner("key1", owner)).thenReturn(key1);

        Consumer consumer = new Consumer("sys.example.com", null, null, system);
        Set<ConsumerInstalledProduct> cips = new HashSet<ConsumerInstalledProduct>();
        ConsumerInstalledProduct cip = new ConsumerInstalledProduct(prod.getId(), prod.getName());
        cips.add(cip);
        consumer.setInstalledProducts(cips);

        AutobindData ad = new AutobindData(consumer).withPools(poolIds).forProducts(prodIds);

        resource.create(consumer, p, null, owner.getKey(), createKeysString(keys));
        verify(entitler).bindByProducts(eq(ad));
    }

    @Test
    public void registerWithKeyWithInstalledProductsAutoAttach() {
        // No auth should be required for registering with keys:
        Principal p = new NoAuthPrincipal();

        Product prod = TestUtil.createProduct();
        String[] prodIds = new String[]{prod.getId()};

        List<String> keys = new ArrayList<String>();
        ActivationKey key1 = new ActivationKey("key1", owner);
        key1.setAutoAttach(true);
        keys.add(key1.getName());
        when(activationKeyCurator.lookupForOwner("key1", owner)).thenReturn(key1);

        Consumer consumer = new Consumer("sys.example.com", null, null, system);
        Set<ConsumerInstalledProduct> cips = new HashSet<ConsumerInstalledProduct>();
        ConsumerInstalledProduct cip = new ConsumerInstalledProduct(prod.getId(), prod.getName());
        cips.add(cip);
        consumer.setInstalledProducts(cips);

        AutobindData ad = new AutobindData(consumer).forProducts(prodIds);

        resource.create(consumer, p, null, owner.getKey(), createKeysString(keys));
        verify(entitler).bindByProducts(eq(ad));
    }

    @Test
    public void registerWithKeyWithInstalledProductsPlusAutoAttach() {
        // No auth should be required for registering with keys:
        Principal p = new NoAuthPrincipal();

        // installed product
        Product prod1 = TestUtil.createProduct();
        // key product
        Product prod2 = TestUtil.createProduct();
        String[] prodIds = new String[]{prod1.getId(), prod2.getId()};

        List<String> keys = new ArrayList<String>();
        ActivationKey key1 = new ActivationKey("key1", owner);
        key1.addProduct(prod2);
        key1.setAutoAttach(true);
        keys.add(key1.getName());
        when(activationKeyCurator.lookupForOwner("key1", owner)).thenReturn(key1);

        Consumer consumer = new Consumer("sys.example.com", null, null, system);
        Set<ConsumerInstalledProduct> cips = new HashSet<ConsumerInstalledProduct>();
        ConsumerInstalledProduct cip = new ConsumerInstalledProduct(prod1.getId(), prod1.getName());
        cips.add(cip);
        consumer.setInstalledProducts(cips);

        AutobindData ad = new AutobindData(consumer).forProducts(prodIds);

        resource.create(consumer, p, null, owner.getKey(), createKeysString(keys));
        verify(entitler).bindByProducts(eq(ad));
    }
}
