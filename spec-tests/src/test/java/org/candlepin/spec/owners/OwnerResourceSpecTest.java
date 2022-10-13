/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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
package org.candlepin.spec.owners;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertForbidden;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotFound;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.AttributeDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTOArrayElement;
import org.candlepin.dto.api.client.v1.ConsumerTypeDTO;
import org.candlepin.dto.api.client.v1.ContentAccessDTO;
import org.candlepin.dto.api.client.v1.NestedOwnerDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.ReleaseVerDTO;
import org.candlepin.dto.api.client.v1.SystemPurposeAttributesDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.spec.bootstrap.assertions.OnlyInHosted;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.client.api.Paging;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.DateUtil;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// TODO: FIXME: Several tests are named in ways that don't match convention, don't match the display
// name to the method name, or don't match with the test logic itself.

@SpecTest
class OwnerResourceSpecTest {

    private static ApiClient admin;
    private static OwnerClient owners;
    private static OwnerProductApi ownerProducts;

    @BeforeAll
    static void setUp() {
        admin = ApiClients.admin();
        owners = admin.owners();
        ownerProducts = admin.ownerProducts();
    }

    @Test
    void shouldCreateOwner() {
        OwnerDTO ownerDTO = Owners.random();
        OwnerDTO status = owners.createOwner(ownerDTO);

        assertThat(status.getId()).isNotNull();
        assertThat(status.getCreated()).isNotNull();
        assertThat(status.getUpdated()).isNotNull();
        assertThat(status.getContentAccessMode()).isNotNull();
        assertThat(status.getContentAccessModeList()).isNotNull();
        assertThat(status.getKey()).isEqualTo(ownerDTO.getKey());
        assertThat(status.getDisplayName()).isEqualTo(ownerDTO.getDisplayName());
    }

    @Test
    void shouldCreateScaOwner() throws Exception {
        OwnerDTO ownerDTO = Owners.randomSca();
        OwnerDTO status = owners.createOwner(ownerDTO);

        assertThat(status.getId()).isNotNull();
        assertThat(status.getCreated()).isNotNull();
        assertThat(status.getUpdated()).isNotNull();
        assertThat(status.getContentAccessMode()).isEqualTo(ownerDTO.getContentAccessMode());
        assertThat(status.getContentAccessModeList()).isEqualTo(ownerDTO.getContentAccessModeList());
        assertThat(status.getKey()).isEqualTo(ownerDTO.getKey());
        assertThat(status.getDisplayName()).isEqualTo(ownerDTO.getDisplayName());
    }

    @Test
    void shouldRetrieveOnlyConsumerTypesForOwner() {
        OwnerDTO newOwner = owners.createOwner(Owners.random());
        UserDTO user = UserUtil.createUser(admin, newOwner);
        ApiClient userClient = ApiClients.basic(user.getUsername(), user.getPassword());
        userClient.consumers().createConsumer(Consumers.random(newOwner));

        List<ConsumerDTOArrayElement> consumers = userClient.owners()
            .listOwnerConsumers(newOwner.getKey(), Set.of("system"));

        assertThat(consumers).hasSize(1);
    }

    @Test
    void shouldRetrieveOwnerContentAccess() {
        OwnerDTO newOwner = owners.createOwner(Owners.random());
        ContentAccessDTO ownerContentAccess = owners.getOwnerContentAccess(newOwner.getKey());

        assertThat(ownerContentAccess.getContentAccessModeList()).contains("org_environment", "entitlement");
        assertThat(ownerContentAccess.getContentAccessMode()).contains("entitlement");
    }

    @Test
    void shouldRetrieveOnlyOwnersConsumers() {
        OwnerDTO testOwner = owners.createOwner(Owners.random());
        UserDTO user = UserUtil.createUser(admin, testOwner);
        ApiClient userClient = ApiClients.basic(user.getUsername(), user.getPassword());
        userClient.consumers().createConsumer(Consumers.random(testOwner));

        List<ConsumerDTOArrayElement> consumers = userClient.owners()
            .listOwnerConsumers(testOwner.getKey());

        assertThat(consumers).hasSize(1);
    }

    @Test
    void shouldRetrieveConsumersServiceLevels() {
        OwnerDTO owner = owners.createOwner(Owners.random());
        UserDTO user = UserUtil.createUser(admin, owner);
        ApiClient userClient = ApiClients.basic(user.getUsername(), user.getPassword());

        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        ProductDTO product = createProduct(owner,
            ProductAttributes.SupportLevel.withValue("VIP")
        );
        owners.createPool(owner.getKey(), Pools.random(product));

        List<String> serviceLevels = consumerClient.owners()
            .ownerServiceLevels(owner.getKey(), "");

        assertThat(serviceLevels)
            .hasSize(1)
            .contains("VIP");
    }

    @Test
    void shouldRetrieveOnlyConsumersServiceLevels() {
        OwnerDTO testOwner = owners.createOwner(Owners.random());
        UserDTO user = UserUtil.createUser(admin, testOwner);
        ApiClient userClient = ApiClients.basic(user.getUsername(), user.getPassword());
        OwnerDTO testOwner2 = owners.createOwner(Owners.random());

        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(testOwner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        assertNotFound(() -> consumerClient.owners().ownerServiceLevels(testOwner2.getKey(), ""));
    }

    @Test
    void shouldAllowOwnerWithParent() {
        OwnerDTO parentOwner = owners.createOwner(Owners.random());
        OwnerDTO ownerWithParent = Owners.random()
            .parentOwner(Owners.toNested(parentOwner));

        OwnerDTO createdOwner = owners.createOwner(ownerWithParent);

        assertThat(createdOwner).isNotNull();
    }

    @Test
    void shouldFailToCreateOwnerWithInvalidParent() {
        OwnerDTO ownerDTO = Owners.random()
            .parentOwner(Owners.toNested(Owners.random()));

        assertNotFound(() -> owners.createOwner(ownerDTO));
    }

    @Test
    void shouldListOwnersPools() {
        OwnerDTO owner = owners.createOwner(Owners.random());
        ProductDTO product = createProduct(owner);
        owners.createPool(owner.getKey(), Pools.random(product));

        List<PoolDTO> ownerPools = owners
            .listOwnerPools(owner.getKey());

        assertThat(ownerPools).hasSize(1);
    }

    @Test
    void shouldListOwnersPoolsPaged() {
        OwnerDTO owner = owners.createOwner(Owners.random());
        ProductDTO product = createProduct(owner);

        for (int i = 0; i < 4; i++) {
            owners.createPool(owner.getKey(), Pools.random(product));
        }

        Set<String> pagedPoolIds = ApiClients.admin().owners()
            .listOwnerPools(owner.getKey(), Paging.withPage(1))
            .stream()
            .map(PoolDTO::getId)
            .collect(Collectors.toSet());

        assertThat(pagedPoolIds).hasSize(2);
    }

    @Test
    void shouldAllowOwnerToUpdateSubscription() {
        OwnerDTO owner = owners.createOwner(Owners.random());
        ProductDTO product = createProduct(owner);
        PoolDTO pool = owners.createPool(owner.getKey(), Pools.random(product));

        pool.startDate(DateUtil.tomorrow());

        owners.updatePool(owner.getKey(), pool);
        PoolDTO updatedPool = admin.pools().getPool(pool.getId(), null, null);

        assertThat(updatedPool.getStartDate())
            .isCloseTo(pool.getStartDate(), within(1, ChronoUnit.SECONDS));
    }

    @Test
    void shouldLetOwnersListPoolsPagedForConsumer() {
        OwnerDTO owner = owners.createOwner(Owners.random());
        UserDTO user = UserUtil.createUser(admin, owner);
        ApiClient userClient = ApiClients.basic(user.getUsername(), user.getPassword());
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ProductDTO product = createProduct(owner);

        for (int i = 0; i < 4; i++) {
            owners.createPool(owner.getKey(), Pools.random(product));
        }

        // Make sure there are 4 available pools
        List<PoolDTO> existingPools = owners.listOwnerPools(owner.getKey(), consumer.getUuid());
        assertThat(existingPools).hasSize(4);

        // Get page 2, per bz 1038273
        Set<String> pagedPoolIds = ApiClients.admin().owners()
            .listOwnerPools(owner.getKey(), consumer.getUuid(), Paging.withPage(1))
            .stream()
            .map(PoolDTO::getId)
            .collect(Collectors.toSet());

        assertThat(pagedPoolIds).hasSize(2);
    }

    @Test
    void shouldCreateOwnerDuringRefresh() {
        String ownerKey = StringUtil.random("owner");
        owners.refreshPools(ownerKey, true);

        OwnerDTO createdOwner = owners.getOwner(ownerKey);
        assertThat(createdOwner.getKey()).isEqualTo(ownerKey);

        List<PoolDTO> ownerPools = owners.listOwnerPools(ownerKey);
        assertThat(ownerPools).isEmpty();
    }

    @Test
    @OnlyInHosted
    void shouldRefreshUpdatesOwners() {
        OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        String ownerKey = StringUtil.random("owner");

        AsyncJobStatusDTO refreshJob = owners.refreshPools(ownerKey, true);
        refreshJob = admin.jobs().waitForJob(refreshJob);
        assertEquals("FINISHED", refreshJob.getState());

        OwnerDTO createdOwner = owners.getOwner(ownerKey);
        assertThat(createdOwner.getLastRefreshed())
            .isAfterOrEqualTo(now);
    }

    @Test
    void shouldAllowOnlySuperAdminToRefresh() {
        OwnerDTO owner = owners.createOwner(Owners.random());
        UserDTO readOnlyUser = UserUtil.createReadOnlyUser(admin, owner);
        UserDTO readWriteUser = UserUtil.createUser(admin, owner);
        UserDTO adminUser = UserUtil.createAdminUser(admin, owner);
        ApiClient readOnlyClient = ApiClients.basic(readOnlyUser.getUsername(), readOnlyUser.getPassword());
        ApiClient readWriteClient = ApiClients.basic(readWriteUser.getUsername(),
            readWriteUser.getPassword());
        ApiClient adminClient = ApiClients.basic(adminUser.getUsername(), adminUser.getPassword());

        assertForbidden(() -> readOnlyClient.owners().refreshPools(owner.getKey(), false));
        assertForbidden(() -> readWriteClient.owners().refreshPools(owner.getKey(), false));
        adminClient.owners().refreshPools(owner.getKey(), false);
    }

    @Test
    void shouldForbidReadOnlyClientToRegister() {
        OwnerDTO owner = owners.createOwner(Owners.random());
        UserDTO readOnlyUser = UserUtil.createReadOnlyUser(admin, owner);
        UserDTO readWriteUser = UserUtil.createUser(admin, owner);
        ApiClient readOnlyClient = ApiClients.basic(readOnlyUser.getUsername(), readOnlyUser.getPassword());
        ApiClient readWriteClient = ApiClients.basic(readWriteUser.getUsername(),
            readWriteUser.getPassword());

        assertForbidden(() -> readOnlyClient.consumers().createConsumer(Consumers.random(owner)));
        ConsumerDTO consumer = readWriteClient.consumers().createConsumer(Consumers.random(owner));
        assertThat(consumer).isNotNull();
    }

    @Test
    void shouldNotRegisterForPrincipalOwner() {
        OwnerDTO owner1 = owners.createOwner(Owners.random());
        OwnerDTO owner2 = owners.createOwner(Owners.random());
        UserDTO user = UserUtil.createReadOnlyUser(admin, owner1);
        ApiClient ownerClient = ApiClients.basic(user.getUsername(), user.getPassword());

        assertNotFound(() -> ownerClient.consumers().createConsumer(Consumers.random(owner2)));
    }

    @Test
    void shouldNotUpdateOwnerKey() {
        OwnerDTO owner = owners.createOwner(Owners.random());
        String originalKey = owner.getKey();
        String newKey = StringUtil.random("owner");
        owner.setKey(newKey);

        owners.updateOwner(originalKey, owner);

        assertNotFound(() -> owners.getOwner(newKey));
        OwnerDTO originalOwner = owners.getOwner(originalKey);
        assertThat(originalOwner).isNotNull();
    }

    @Test
    void shouldUpdateParentOwner() {
        OwnerDTO parent1 = owners.createOwner(Owners.random());
        OwnerDTO parent2 = owners.createOwner(Owners.random());
        OwnerDTO owner = owners.createOwner(Owners.random().parentOwner(Owners.toNested(parent1)));

        owners.updateOwner(owner.getKey(), owner.parentOwner(Owners.toNested(parent2)));

        OwnerDTO updatedOwner = owners.getOwner(owner.getKey());
        assertThat(updatedOwner.getParentOwner())
            .extracting(NestedOwnerDTO::getKey)
            .isEqualTo(parent2.getKey());
    }

    @Test
    void shouldAllowOnlyDefaultServiceLevelToUpdate() {
        OwnerDTO owner = owners.createOwner(Owners.random());
        ProductDTO product = createProduct(owner,
            ProductAttributes.SupportLevel.withValue("VIP"));
        owners.createPool(owner.getKey(), Pools.random(product));

        OwnerDTO ownerBefore = owners.getOwner(owner.getKey());
        assertThat(ownerBefore.getDefaultServiceLevel()).isNull();

        owners.updateOwner(ownerBefore.getKey(), ownerBefore.defaultServiceLevel("VIP"));

        OwnerDTO ownerAfter = owners.getOwner(owner.getKey());
        assertThat(ownerAfter.getDefaultServiceLevel())
            .isEqualTo("VIP");
    }

    @Test
    void shouldAllowOwnerToUpdateDefaultServiceLevel() {
        OwnerDTO owner = owners.createOwner(Owners.random());
        assertThat(owner.getDefaultServiceLevel()).isNull();
        ProductDTO product1 = createProduct(owner,
            ProductAttributes.SupportLevel.withValue("VIP")
        );
        ProductDTO product2 = createProduct(owner,
            ProductAttributes.SupportLevel.withValue("Layered"),
            ProductAttributes.SupportLevelExempt.withValue("true")
        );
        owners.createPool(owner.getKey(), Pools.random(product1));
        owners.createPool(owner.getKey(), Pools.random(product2));

        // Set an initial service level:
        owners.updateOwner(owner.getKey(), owner.defaultServiceLevel("VIP"));
        OwnerDTO updatedOwner = owners.getOwner(owner.getKey());
        assertThat(updatedOwner.getDefaultServiceLevel()).isEqualTo("VIP");

        // Try setting a service level not available in the org:
        assertBadRequest(() -> owners.updateOwner(owner.getKey(), owner.defaultServiceLevel("TooElite")));

        // Make sure we can 'unset' with empty string:
        owners.updateOwner(owner.getKey(), owner.defaultServiceLevel(""));
        OwnerDTO updatedOwner2 = owners.getOwner(owner.getKey());
        assertThat(updatedOwner2.getDefaultServiceLevel()).isNull();

        // Set an initial service level different casing:
        owners.updateOwner(owner.getKey(), owner.defaultServiceLevel("vip"));
        OwnerDTO updatedOwner3 = owners.getOwner(owner.getKey());
        assertThat(updatedOwner3.getDefaultServiceLevel()).isEqualTo("vip");

        // Cannot set exempt level:
        assertBadRequest(() -> owners.updateOwner(owner.getKey(), owner.defaultServiceLevel("Layered")));
    }

    @Test
    void shouldRaiseNotFoundForUnknownOwner() {
        assertNotFound(() -> owners.getOwner("unknown_owner"));
    }

    @Test
    void shouldAllowUnicodeOwners() {
        OwnerDTO createdOwner = owners
            .createOwner(Owners.random().key(StringUtil.random("Ακμή корпорация")));
        assertThat(createdOwner).isNotNull();
    }

    @Test
    void shouldShowServiceLevels() {
        OwnerDTO owner = owners.createOwner(Owners.random());
        assertThat(owner.getDefaultServiceLevel()).isNull();
        ProductDTO product1 = createProduct(owner,
            ProductAttributes.SupportLevel.withValue("Really High")
        );
        ProductDTO product2 = createProduct(owner,
            ProductAttributes.SupportLevel.withValue("Really Low")
        );
        ProductDTO product3 = createProduct(owner,
            ProductAttributes.SupportLevel.withValue("Really Low")
        );
        owners.createPool(owner.getKey(), Pools.random(product1));
        owners.createPool(owner.getKey(), Pools.random(product2));
        owners.createPool(owner.getKey(), Pools.random(product3));

        List<String> serviceLevels = owners.ownerServiceLevels(owner.getKey(), null);
        assertThat(serviceLevels)
            .containsExactly("Really High", "Really Low");
    }

    @Test
    void shouldExemptServiceLevelFiltering() {
        OwnerDTO owner = owners.createOwner(Owners.random());
        UserDTO user = UserUtil.createUser(admin, owner);
        ApiClient userClient = ApiClients.basic(user.getUsername(), user.getPassword());
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        ProductDTO product1 = createProduct(owner,
            ProductAttributes.SupportLevel.withValue("VIP")
        );
        ProductDTO product2 = createProduct(owner,
            ProductAttributes.SupportLevel.withValue("Layered"),
            ProductAttributes.SupportLevelExempt.withValue("true")
        );
        ProductDTO product3 = createProduct(owner,
            // The exempt attribute will cover here as well despite the casing
            ProductAttributes.SupportLevel.withValue("LAYered")
        );
        owners.createPool(owner.getKey(), Pools.random(product1));
        owners.createPool(owner.getKey(), Pools.random(product2));
        owners.createPool(owner.getKey(), Pools.random(product3));

        List<String> serviceLevels = consumerClient.owners()
            .ownerServiceLevels(owner.getKey(), "false");
        assertThat(serviceLevels)
            .containsExactly("VIP");
        List<String> exemptLevels = consumerClient.owners()
            .ownerServiceLevels(owner.getKey(), "true");
        assertThat(exemptLevels)
            .containsExactly("Layered");
    }

    @Test
    void shouldReturnCalculatedAttributes() {
        OwnerDTO owner = owners.createOwner(Owners.random());
        ProductDTO product = createProduct(owner, ProductAttributes.SupportLevel.withValue("VIP"));
        owners.createPool(owner.getKey(), Pools.random(product));
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(owner));

        List<PoolDTO> ownerPools = owners.listOwnerPools(owner.getKey(), consumer.getUuid());
        assertThat(ownerPools)
            .hasSize(1)
            .map(PoolDTO::getCalculatedAttributes)
            .first()
            .extracting("suggested_quantity").isEqualTo("1");
    }

    @Test
    void shouldAllowCustomFloatingPools() {
        OwnerDTO owner = owners.createOwner(Owners.random());
        ProductDTO provided1 = createProduct(owner);
        ProductDTO provided2 = createProduct(owner);
        ProductDTO derivedProvided = createProduct(owner);
        ProductDTO derivedProduct = ownerProducts
            .createProductByOwner(owner.getKey(), Products.random().addProvidedProductsItem(derivedProvided));
        ProductDTO product = ownerProducts.createProductByOwner(owner.getKey(), Products.random()
            .derivedProduct(derivedProduct)
            .addProvidedProductsItem(provided1)
            .addProvidedProductsItem(provided2));
        PoolDTO pool = owners.createPool(owner.getKey(), Pools.random(product));

        List<PoolDTO> ownerPools = owners.listOwnerPools(owner.getKey());
        PoolDTO listedPool = ownerPools.stream().findFirst().orElseThrow();
        assertThat(listedPool.getDerivedProductId()).isEqualTo(derivedProduct.getId());
        assertThat(listedPool.getProvidedProducts()).hasSize(2);
        assertThat(listedPool.getDerivedProvidedProducts()).hasSize(1);

        // Refresh should have no effect:
        owners.refreshPools(owner.getKey(), false);
        assertThat(owners.listOwnerPools(owner.getKey()))
            .hasSize(1);

        // Satellite will need to be able to clean it up as well:
        admin.pools().deletePool(pool.getId());
        assertThat(owners.listOwnerPools(owner.getKey()))
            .isEmpty();
    }

    @Test
    // BZ 988549
    void shouldNotDoubleBindWhenHealingOrg() {
        OwnerDTO owner = owners.createOwner(Owners.random());
        ProductDTO product = createProduct(owner,
            ProductAttributes.Usage.withValue("Development"),
            ProductAttributes.Roles.withValue("Server1,Server2"),
            ProductAttributes.Addons.withValue("addon1,addon2"),
            ProductAttributes.SupportLevel.withValue("mysla"),
            ProductAttributes.SupportType.withValue("test_support1")
        );
        owners.createPool(owner.getKey(), Pools.random(product));

        UserDTO user = UserUtil.createUser(admin, owner);
        ApiClient userClient = ApiClients.basic(user.getUsername(), user.getPassword());
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        consumer
            .releaseVer(new ReleaseVerDTO().releaseVer("1"))
            .installedProducts(Set.of(Products.toInstalled(product)));
        consumerClient.consumers().updateConsumer(consumer.getUuid(), consumer);

        AsyncJobStatusDTO healJob1 = userClient.owners().healEntire(owner.getKey());
        healJob1 = userClient.jobs().waitForJob(healJob1);
        assertEquals("FINISHED", healJob1.getState());

        ConsumerDTO updatedConsumer = admin.consumers().getConsumer(consumer.getUuid());
        assertThat(updatedConsumer.getEntitlementCount()).isEqualTo(1);

        owners.createPool(owner.getKey(), Pools.random(product));
        AsyncJobStatusDTO healJob2 = userClient.owners().healEntire(owner.getKey());
        healJob2 = userClient.jobs().waitForJob(healJob2);
        assertEquals("FINISHED", healJob2.getState());

        ConsumerDTO updatedConsumer2 = admin.consumers().getConsumer(consumer.getUuid());
        assertThat(updatedConsumer2.getEntitlementCount()).isEqualTo(1);
    }

    @Test
    void shouldSetOrgDebug() {
        OwnerDTO owner = owners.createOwner(Owners.random());

        owners.setLogLevel(owner.getKey(), null);
        OwnerDTO updatedOwner = owners.getOwner(owner.getKey());
        assertThat(updatedOwner.getLogLevel()).isEqualTo("DEBUG");

        owners.deleteLogLevel(owner.getKey());
        OwnerDTO updatedOwner2 = owners.getOwner(owner.getKey());
        assertThat(updatedOwner2.getLogLevel()).isNull();
    }

    @Test
    void shouldNotSetBadLogLevel() {
        OwnerDTO owner = owners.createOwner(Owners.random());

        assertBadRequest(() -> owners.setLogLevel(owner.getKey(), "THISLEVELISBAD"));
    }

    @Test
    void shouldLookupByConsumerType() {
        OwnerDTO owner = owners.createOwner(Owners.random());
        UserDTO user = UserUtil.createUser(admin, owner);
        ApiClient userClient = ApiClients.basic(user.getUsername(), user.getPassword());
        userClient.consumers().createConsumer(Consumers.random(owner));
        userClient.consumers().createConsumer(Consumers.random(owner));
        userClient.consumers().createConsumer(Consumers.random(owner, ConsumerTypes.Hypervisor));
        userClient.consumers().createConsumer(Consumers.random(owner, ConsumerTypes.Candlepin));

        List<ConsumerDTOArrayElement> systems = userClient.owners()
            .listOwnerConsumers(owner.getKey(), Set.of("system"));
        assertThat(systems)
            .hasSize(2)
            .map(ConsumerDTOArrayElement::getType)
            .map(ConsumerTypeDTO::getLabel)
            .containsOnly("system");

        List<ConsumerDTOArrayElement> hypervisors = userClient.owners()
            .listOwnerConsumers(owner.getKey(), Set.of("hypervisor"));
        assertThat(hypervisors)
            .hasSize(1)
            .map(ConsumerDTOArrayElement::getType)
            .map(ConsumerTypeDTO::getLabel)
            .containsOnly("hypervisor");

        List<ConsumerDTOArrayElement> distributors = userClient.owners()
            .listOwnerConsumers(owner.getKey(), Set.of("candlepin"));
        assertThat(distributors)
            .hasSize(1)
            .map(ConsumerDTOArrayElement::getType)
            .map(ConsumerTypeDTO::getLabel)
            .containsOnly("candlepin");

        List<ConsumerDTOArrayElement> consumers = userClient.owners()
            .listOwnerConsumers(owner.getKey(), Set.of("hypervisor", "candlepin"));
        assertThat(consumers)
            .hasSize(2)
            .map(ConsumerDTOArrayElement::getType)
            .map(ConsumerTypeDTO::getLabel)
            .containsOnly("candlepin", "hypervisor");
    }

    @Test
    void shouldUpdateAutoBindDisabled() {
        OwnerDTO owner = owners.createOwner(Owners.random());
        assertThat(owner.getAutobindDisabled()).isFalse();

        owner.autobindDisabled(true);
        owners.updateOwner(owner.getKey(), owner);
        OwnerDTO updatedOwner = owners.getOwner(owner.getKey());
        assertThat(updatedOwner.getAutobindDisabled()).isTrue();

        owner.autobindDisabled(false);
        owners.updateOwner(owner.getKey(), owner);
        OwnerDTO updatedOwner2 = owners.getOwner(owner.getKey());
        assertThat(updatedOwner2.getAutobindDisabled()).isFalse();
    }

    @Test
    void shouldIgnoreAutoBindDisabled() {
        OwnerDTO owner = owners.createOwner(Owners.random());
        assertThat(owner.getAutobindDisabled()).isFalse();

        owner.autobindDisabled(null);
        owners.updateOwner(owner.getKey(), owner);
        OwnerDTO updatedOwner2 = owners.getOwner(owner.getKey());

        assertThat(updatedOwner2.getAutobindDisabled()).isFalse();
    }

    @Test
    void shouldListSystemPurposeAttributes() {
        OwnerDTO owner = owners.createOwner(Owners.random());
        ProductDTO product1 = createProduct(owner,
            ProductAttributes.Usage.withValue("Development"),
            ProductAttributes.Roles.withValue("Server1,Server2"),
            ProductAttributes.Addons.withValue("addon1,addon2"),
            ProductAttributes.SupportLevel.withValue("mysla"),
            ProductAttributes.SupportType.withValue("test_support1"));
        ProductDTO product2 = createProduct(owner,
            ProductAttributes.Usage.withValue("Development"),
            ProductAttributes.Roles.withValue("Server1,Server2"),
            ProductAttributes.Addons.withValue("addon1,addon2"),
            ProductAttributes.SupportLevel.withValue("Layered"),
            ProductAttributes.SupportLevelExempt.withValue("true"),
            ProductAttributes.SupportType.withValue("test_support2")
        );
        ProductDTO product3 = createProduct(owner,
            ProductAttributes.Usage.withValue("Exp_Development"),
            ProductAttributes.Roles.withValue("Exp_Server"),
            ProductAttributes.Addons.withValue("Exp_addon"),
            ProductAttributes.SupportType.withValue("Exp_test_support")
        );
        createProduct(owner,
            ProductAttributes.Usage.withValue("No_Development"),
            ProductAttributes.Roles.withValue("No_Server"),
            ProductAttributes.Addons.withValue("No_addon"),
            ProductAttributes.SupportType.withValue("No_test_support")
        );
        owners.createPool(owner.getKey(), Pools.random(product1));
        owners.createPool(owner.getKey(), Pools.random(product2));
        owners.createPool(owner.getKey(), Pools.random(product3)
            .endDate(OffsetDateTime.now().minusYears(10)));

        SystemPurposeAttributesDTO systemPurpose = owners.getSyspurpose(owner.getKey());
        assertThat(systemPurpose.getSystemPurposeAttributes())
            .containsEntry("usage", Set.of("Development"))
            .containsEntry("roles", Set.of("Server1", "Server2"))
            .containsEntry("addons", Set.of("addon1", "addon2"))
            .containsEntry("support_level", Set.of("mysla"))
            .containsEntry("support_type", Set.of("test_support1", "test_support2"));
    }

    private ProductDTO createProduct(OwnerDTO owner, AttributeDTO... attributes) {
        return ownerProducts.createProductByOwner(owner.getKey(), Products.withAttributes(attributes));
    }
}
