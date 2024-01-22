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
package org.candlepin.spec.owners;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertForbidden;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotFound;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.AttributeDTO;
import org.candlepin.dto.api.client.v1.CertificateDTO;
import org.candlepin.dto.api.client.v1.ClaimantOwner;
import org.candlepin.dto.api.client.v1.CloudAuthenticationResultDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTOArrayElement;
import org.candlepin.dto.api.client.v1.ConsumerTypeDTO;
import org.candlepin.dto.api.client.v1.ContentAccessDTO;
import org.candlepin.dto.api.client.v1.NestedOwnerDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.ReleaseVerDTO;
import org.candlepin.dto.api.client.v1.RoleDTO;
import org.candlepin.dto.api.client.v1.SystemPurposeAttributesDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.invoker.client.ApiException;
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
import org.candlepin.spec.bootstrap.data.builder.Permissions;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.builder.Subscriptions;
import org.candlepin.spec.bootstrap.data.util.DateUtil;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@SpecTest
public class OwnerResourceSpecTest {

    private static ApiClient admin;
    private static OwnerClient owners;
    private static OwnerProductApi ownerProducts;

    @BeforeAll
    public static void setUp() {
        admin = ApiClients.admin();
        owners = admin.owners();
        ownerProducts = admin.ownerProducts();
    }

    private ProductDTO createProduct(OwnerDTO owner, AttributeDTO... attributes) throws ApiException {
        return ownerProducts.createProduct(owner.getKey(), Products.withAttributes(attributes));
    }

    @Test
    public void shouldCreateOwner() {
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
    public void shouldCreateScaOwner() {
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
    public void shouldCreateAnonymousOwner() {
        OwnerDTO expectedOwner = Owners.random();
        expectedOwner.anonymous(true);
        owners.createOwner(expectedOwner);

        OwnerDTO actual = owners.getOwner(expectedOwner.getKey());

        assertThat(actual)
            .returns(expectedOwner.getAnonymous(), OwnerDTO::getAnonymous);
    }

    @Test
    public void shouldCreateClaimedOwner() {
        OwnerDTO expectedOwner = Owners.random();
        expectedOwner.claimed(true);
        expectedOwner = owners.createOwner(expectedOwner);

        OwnerDTO actual = owners.getOwner(expectedOwner.getKey());

        assertThat(actual)
            .returns(expectedOwner.getClaimed(), OwnerDTO::getClaimed);
    }

    @Test
    public void shouldRetrieveOnlyConsumerTypesForOwner() {
        OwnerDTO newOwner = owners.createOwner(Owners.random());
        UserDTO user = UserUtil.createUser(admin, newOwner);
        ApiClient userClient = ApiClients.basic(user.getUsername(), user.getPassword());
        userClient.consumers().createConsumer(Consumers.random(newOwner));

        List<ConsumerDTOArrayElement> consumers = userClient.owners()
            .listOwnerConsumers(newOwner.getKey(), Set.of("system"));

        assertThat(consumers).hasSize(1);
    }

    @Test
    public void shouldRetrieveOwnerContentAccess() {
        OwnerDTO newOwner = owners.createOwner(Owners.random());
        ContentAccessDTO ownerContentAccess = owners.getOwnerContentAccess(newOwner.getKey());

        assertThat(ownerContentAccess.getContentAccessModeList()).contains("org_environment", "entitlement");
        assertThat(ownerContentAccess.getContentAccessMode()).contains("entitlement");
    }

    @Test
    public void shouldRetrieveOnlyOwnersConsumers() {
        OwnerDTO testOwner = owners.createOwner(Owners.random());
        UserDTO user = UserUtil.createUser(admin, testOwner);
        ApiClient userClient = ApiClients.basic(user.getUsername(), user.getPassword());
        userClient.consumers().createConsumer(Consumers.random(testOwner));

        List<ConsumerDTOArrayElement> consumers = userClient.owners()
            .listOwnerConsumers(testOwner.getKey());

        assertThat(consumers).hasSize(1);
    }

    @Test
    public void shouldRetrieveConsumersServiceLevels() {
        OwnerDTO owner = owners.createOwner(Owners.random());
        UserDTO user = UserUtil.createUser(admin, owner);
        ApiClient userClient = ApiClients.basic(user.getUsername(), user.getPassword());

        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        ProductDTO product = createProduct(owner, ProductAttributes.SupportLevel.withValue("VIP"));
        owners.createPool(owner.getKey(), Pools.random(product));

        List<String> serviceLevels = consumerClient.owners()
            .ownerServiceLevels(owner.getKey(), "");

        assertThat(serviceLevels)
            .hasSize(1)
            .contains("VIP");
    }

    @Test
    public void shouldRetrieveOnlyConsumersServiceLevels() {
        OwnerDTO testOwner = owners.createOwner(Owners.random());
        UserDTO user = UserUtil.createUser(admin, testOwner);
        ApiClient userClient = ApiClients.basic(user.getUsername(), user.getPassword());
        OwnerDTO testOwner2 = owners.createOwner(Owners.random());

        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(testOwner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        assertNotFound(() -> consumerClient.owners().ownerServiceLevels(testOwner2.getKey(), ""));
    }

    @Test
    public void shouldAllowOwnerWithParent() {
        OwnerDTO parentOwner = owners.createOwner(Owners.random());
        OwnerDTO ownerWithParent = Owners.random()
            .parentOwner(Owners.toNested(parentOwner));

        OwnerDTO createdOwner = owners.createOwner(ownerWithParent);

        assertThat(createdOwner).isNotNull();
    }

    @Test
    public void shouldFailToCreateOwnerWithInvalidParent() {
        OwnerDTO ownerDTO = Owners.random()
            .parentOwner(Owners.toNested(Owners.random()));

        assertNotFound(() -> owners.createOwner(ownerDTO));
    }

    @Test
    public void shouldListOwnersPools() {
        OwnerDTO owner = owners.createOwner(Owners.random());
        ProductDTO product = createProduct(owner);
        owners.createPool(owner.getKey(), Pools.random(product));

        List<PoolDTO> ownerPools = owners
            .listOwnerPools(owner.getKey());

        assertThat(ownerPools).hasSize(1);
    }

    @Test
    public void shouldListOwnersPoolsPaged() {
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
    public void shouldAllowOwnerToUpdateSubscription() {
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
    public void shouldLetOwnersListPoolsPagedForConsumer() {
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
    public void shouldCreateOwnerDuringRefresh() {
        String ownerKey = StringUtil.random("owner");
        owners.refreshPools(ownerKey, true);

        OwnerDTO createdOwner = owners.getOwner(ownerKey);
        assertThat(createdOwner.getKey()).isEqualTo(ownerKey);

        List<PoolDTO> ownerPools = owners.listOwnerPools(ownerKey);
        assertThat(ownerPools).isEmpty();
    }

    @Test
    @OnlyInHosted
    public void shouldRefreshUpdatesOwners() {
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
    public void shouldAllowOnlySuperAdminToRefresh() {
        OwnerDTO owner = owners.createOwner(Owners.random());
        UserDTO readOnlyUser = UserUtil.createReadOnlyUser(admin, owner);
        UserDTO readWriteUser = UserUtil.createUser(admin, owner);
        UserDTO adminUser = UserUtil.createAdminUser(admin, owner);
        ApiClient readOnlyClient = ApiClients.basic(readOnlyUser.getUsername(), readOnlyUser.getPassword());
        ApiClient readWriteClient = ApiClients.basic(readWriteUser.getUsername(),
            readWriteUser.getPassword());
        ApiClient adminClient = ApiClients.basic(adminUser.getUsername(), adminUser.getPassword());

        ProductDTO product = Products.random();

        admin.ownerProducts().createProduct(owner.getKey(), product);
        owners.createPool(owner.getKey(), Pools.random(product));

        assertForbidden(() -> readOnlyClient.owners().refreshPools(owner.getKey(), false));
        assertForbidden(() -> readWriteClient.owners().refreshPools(owner.getKey(), false));
        adminClient.owners().refreshPools(owner.getKey(), false);
    }

    @Test
    public void shouldForbidReadOnlyClientToRegister() {
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
    public void shouldNotRegisterForPrincipalOwner() {
        OwnerDTO owner1 = owners.createOwner(Owners.random());
        OwnerDTO owner2 = owners.createOwner(Owners.random());
        UserDTO user = UserUtil.createReadOnlyUser(admin, owner1);
        ApiClient ownerClient = ApiClients.basic(user.getUsername(), user.getPassword());

        assertNotFound(() -> ownerClient.consumers().createConsumer(Consumers.random(owner2)));
    }

    @Test
    public void shouldNotUpdateOwnerKey() {
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
    public void shouldUpdateParentOwner() {
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
    public void shouldAllowOnlyDefaultServiceLevelToUpdate() {
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
    public void shouldAllowOwnerToUpdateDefaultServiceLevel() {
        OwnerDTO owner = owners.createOwner(Owners.random());
        assertThat(owner.getDefaultServiceLevel()).isNull();
        ProductDTO product1 = createProduct(owner,
            ProductAttributes.SupportLevel.withValue("VIP"));
        ProductDTO product2 = createProduct(owner,
            ProductAttributes.SupportLevel.withValue("Layered"),
            ProductAttributes.SupportLevelExempt.withValue("true"));
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
    public void shouldRegenOrgEntitlementsWhenContentPrefixChanges() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        // Create a pool to bind
        ProductDTO product = adminClient.ownerProducts().createProduct(owner.getKey(), Products.random());
        PoolDTO pool = adminClient.owners().createPool(owner.getKey(), Pools.random(product));

        // Create a consumer and bind the pool
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        adminClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);

        // Fetch the cert for the consumer so we can check if it's been regenerated later
        List<CertificateDTO> initCerts = adminClient.consumers().fetchCertificates(consumer.getUuid());
        assertThat(initCerts)
            .isNotNull()
            .hasSize(1);

        CertificateDTO initCert = initCerts.get(0);
        assertNotNull(initCert);

        // Update the content prefix on the org, which should trigger a regen of the cert when we
        // fetch it later
        owner.setContentPrefix("content_prefix");
        adminClient.owners().updateOwner(owner.getKey(), owner);

        // Fetch the certs again, verify we still only have the one but it has a new serial,
        // indicating it was regenerated.
        List<CertificateDTO> regenCerts = adminClient.consumers().fetchCertificates(consumer.getUuid());
        assertThat(regenCerts)
            .isNotNull()
            .hasSize(1);

        CertificateDTO regenCert = regenCerts.get(0);
        assertThat(regenCert)
            .isNotNull()
            .extracting(CertificateDTO::getSerial)
            .isNotEqualTo(initCert.getSerial());
    }

    @Test
    public void shouldRaiseNotFoundForUnknownOwner() {
        assertNotFound(() -> owners.getOwner("unknown_owner"));
    }

    @Test
    public void shouldAllowUnicodeOwners() {
        OwnerDTO createdOwner = owners
            .createOwner(Owners.random().key(StringUtil.random("Ακμή корпорация")));
        assertThat(createdOwner).isNotNull();
    }

    @Test
    public void shouldShowServiceLevels() {
        OwnerDTO owner = owners.createOwner(Owners.random());
        assertThat(owner.getDefaultServiceLevel()).isNull();
        ProductDTO product1 = createProduct(owner,
            ProductAttributes.SupportLevel.withValue("Really High"));
        ProductDTO product2 = createProduct(owner,
            ProductAttributes.SupportLevel.withValue("Really Low"));
        ProductDTO product3 = createProduct(owner,
            ProductAttributes.SupportLevel.withValue("Really Low"));
        owners.createPool(owner.getKey(), Pools.random(product1));
        owners.createPool(owner.getKey(), Pools.random(product2));
        owners.createPool(owner.getKey(), Pools.random(product3));

        List<String> serviceLevels = owners.ownerServiceLevels(owner.getKey(), null);
        assertThat(serviceLevels)
            .containsExactly("Really High", "Really Low");
    }

    @Test
    public void shouldExemptServiceLevelFiltering() {
        OwnerDTO owner = owners.createOwner(Owners.random());
        UserDTO user = UserUtil.createUser(admin, owner);
        ApiClient userClient = ApiClients.basic(user.getUsername(), user.getPassword());
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        ProductDTO product1 = createProduct(owner,
            ProductAttributes.SupportLevel.withValue("VIP"));
        ProductDTO product2 = createProduct(owner,
            ProductAttributes.SupportLevel.withValue("Layered"),
            ProductAttributes.SupportLevelExempt.withValue("true"));
        ProductDTO product3 = createProduct(owner,
            // The exempt attribute will cover here as well despite the casing
            ProductAttributes.SupportLevel.withValue("LAYered"));
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
    public void shouldReturnCalculatedAttributes() {
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
    public void shouldAllowCustomFloatingPools() {
        OwnerDTO owner = owners.createOwner(Owners.random());
        ProductDTO provided1 = createProduct(owner);
        ProductDTO provided2 = createProduct(owner);
        ProductDTO derivedProvided = createProduct(owner);
        ProductDTO derivedProduct = ownerProducts
            .createProduct(owner.getKey(), Products.random().addProvidedProductsItem(derivedProvided));
        ProductDTO product = ownerProducts.createProduct(owner.getKey(), Products.random()
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
    public void shouldNotDoubleBindWhenHealingOrg() {
        OwnerDTO owner = owners.createOwner(Owners.random());
        ProductDTO product = createProduct(owner,
            ProductAttributes.Usage.withValue("Development"),
            ProductAttributes.Roles.withValue("Server1,Server2"),
            ProductAttributes.Addons.withValue("addon1,addon2"),
            ProductAttributes.SupportLevel.withValue("mysla"),
            ProductAttributes.SupportType.withValue("test_support1"));
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
    public void shouldSetOrgDebug() {
        OwnerDTO owner = owners.createOwner(Owners.random());

        owners.setLogLevel(owner.getKey(), null);
        OwnerDTO updatedOwner = owners.getOwner(owner.getKey());
        assertThat(updatedOwner.getLogLevel()).isEqualTo("DEBUG");

        owners.deleteLogLevel(owner.getKey());
        OwnerDTO updatedOwner2 = owners.getOwner(owner.getKey());
        assertThat(updatedOwner2.getLogLevel()).isNull();
    }

    @Test
    public void shouldNotSetBadLogLevel() {
        OwnerDTO owner = owners.createOwner(Owners.random());

        assertBadRequest(() -> owners.setLogLevel(owner.getKey(), "THISLEVELISBAD"));
    }

    @Test
    public void shouldLookupByConsumerType() {
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
    public void shouldUpdateAutoBindDisabled() {
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
    public void shouldIgnoreAutoBindDisabled() {
        OwnerDTO owner = owners.createOwner(Owners.random());
        assertThat(owner.getAutobindDisabled()).isFalse();

        owner.autobindDisabled(null);
        owners.updateOwner(owner.getKey(), owner);
        OwnerDTO updatedOwner2 = owners.getOwner(owner.getKey());

        assertThat(updatedOwner2.getAutobindDisabled()).isFalse();
    }

    @Test
    public void shouldListSystemPurposeAttributes() {
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
            ProductAttributes.SupportType.withValue("test_support2"));
        ProductDTO product3 = createProduct(owner,
            ProductAttributes.Usage.withValue("Exp_Development"),
            ProductAttributes.Roles.withValue("Exp_Server"),
            ProductAttributes.Addons.withValue("Exp_addon"),
            ProductAttributes.SupportType.withValue("Exp_test_support"));
        createProduct(owner,
            ProductAttributes.Usage.withValue("No_Development"),
            ProductAttributes.Roles.withValue("No_Server"),
            ProductAttributes.Addons.withValue("No_addon"),
            ProductAttributes.SupportType.withValue("No_test_support"));
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

    @Test
    public void shouldListsSystemPurposeAttributesOfItsConsumers() throws ApiException {
        OwnerDTO owner = owners.createOwner(Owners.random());
        UserDTO user = UserUtil.createUser(admin, owner);
        ApiClient userClient = ApiClients.basic(user.getUsername(), user.getPassword());

        userClient.consumers().createConsumer(Consumers.random(owner)
            .usage("usage1")
            .addOns(Set.of("addon1"))
            .serviceLevel("sla1")
            .role("common_role")
            .serviceType("test_service-type1"));
        userClient.consumers().createConsumer(Consumers.random(owner)
            .usage("usage2")
            .addOns(Set.of("addon2"))
            .serviceLevel("sla2")
            .role("common_role")
            .serviceType("test_service-type2"));
        userClient.consumers().createConsumer(Consumers.random(owner)
            .usage("usage3")
            .addOns(null)
            .serviceLevel(null)
            .role(null)
            .serviceType("test_service-type3"));
        userClient.consumers().createConsumer(Consumers.random(owner)
            .usage("usage4")
            .addOns(Set.of(""))
            .serviceLevel(null)
            .role("")
            .serviceType("test_service-type4"));

        SystemPurposeAttributesDTO attributes = userClient.owners().getConsumersSyspurpose(owner.getKey());
        assertEquals(attributes.getOwner().getKey(), owner.getKey());

        assertThat(attributes.getSystemPurposeAttributes())
            .containsEntry("usage", Set.of("usage1", "usage2", "usage3", "usage4"))
            .containsEntry("roles", Set.of("common_role"))
            .containsEntry("addons", Set.of("addon1", "addon2"))
            .containsEntry("support_level", Set.of("sla1", "sla2"))
            .containsEntry("support_type", Set.of("test_service-type1", "test_service-type2",
                "test_service-type3", "test_service-type4"))
            // Make sure to filter null & empty addons.
            .doesNotContainEntry("addons", Set.of(""))
            .doesNotContainEntry("addons", null)
            // Empty serviceLevel means no serviceLevel, so we have to make sure those are filtered those out.
            .doesNotContainEntry("support_level", Set.of(""))
            // Make sure to filter null & empty roles.
            .doesNotContainEntry("roles", Set.of(""))
            .doesNotContainEntry("roles", null);

        // Even though 2 consumers have both specified the 'common_role', output should be deduplicated
        // and only include one instance of each unique value.
        assertEquals(attributes.getSystemPurposeAttributes().get("roles").size(), 1);
    }

    @Test
    public void shouldAllowUserWithOwnerPoolsPermissionCanSeeSystemPurposeOfTheOwnerProducts()
        throws ApiException {
        OwnerDTO owner = owners.createOwner(Owners.random());
        UserDTO user = UserUtil.createWith(admin, Permissions.OWNER_POOLS.all(owner));
        ApiClient userClient = ApiClients.basic(user.getUsername(), user.getPassword());

        // Make sure we see the role
        List<RoleDTO> roles = userClient.users().getUserRoles(user.getUsername());
        assertEquals(1, roles.size());

        // The user should have access to the owner's system purpose attributes (expecting this will not
        // return an error)
        SystemPurposeAttributesDTO attributes = userClient.owners().getSyspurpose(owner.getKey());
        assertEquals(owner.getKey(), attributes.getOwner().getKey());
    }

    @Test
    @OnlyInHosted
    public void shouldMigrateAllConsumersOfAnonymousOwnerToTargetOwner() {
        String accountId = StringUtil.random("cloud-account-id-");
        String instanceId = StringUtil.random("cloud-instance-id-");
        String offerId = StringUtil.random("cloud-offer-");
        OwnerDTO anonOwner = owners.createOwner(Owners.randomSca().anonymous(true));
        OwnerDTO destOwner = owners.createOwner(Owners.randomSca());
        admin.hosted().createOwner(anonOwner);
        ProductDTO prod = admin.ownerProducts().createProduct(anonOwner.getKey(), Products.random());
        admin.hosted().createProduct(prod);
        owners.createPool(anonOwner.getKey(), Pools.random(prod));
        admin.hosted().createSubscription(Subscriptions.random(anonOwner, prod));

        admin.hosted().associateProductIdsToCloudOffer(offerId, List.of(prod.getId()));
        admin.hosted().associateOwnerToCloudAccount(accountId, anonOwner.getKey());
        CloudAuthenticationResultDTO result = ApiClients.noAuth().cloudAuthorization()
            .cloudAuthorizeV2(accountId, instanceId, offerId, "test-type", "");

        ApiClient tokenClient = ApiClients.bearerToken(result.getToken());
        List<ConsumerDTO> consumers = List.of(
            tokenClient.consumers().createConsumerWithoutOwner(Consumers.randomNoOwner()),
            tokenClient.consumers().createConsumerWithoutOwner(Consumers.randomNoOwner()),
            tokenClient.consumers().createConsumerWithoutOwner(Consumers.randomNoOwner())
        );
        List<CertificateDTO> oldIdCerts = consumers.stream().map(ConsumerDTO::getIdCert).toList();
        List<CertificateDTO> oldScaCerts = fetchCertsOf(consumers, admin);

        AsyncJobStatusDTO job = owners.claim(anonOwner.getKey(),
            new ClaimantOwner().claimantOwnerKey(destOwner.getKey()));

        job = admin.jobs().waitForJob(job);
        assertThatJob(job).isFinished();

        // Anon owner should be left with no consumers
        List<ConsumerDTOArrayElement> anonConsumers = owners.listOwnerConsumers(anonOwner.getKey());
        assertThat(anonConsumers).isEmpty();

        // All consumers should be migrated to destination owner
        List<ConsumerDTOArrayElement> migratedConsumers = owners.listOwnerConsumers(destOwner.getKey());
        assertThat(migratedConsumers).hasSize(3);

        // Anon owner should be claimed
        OwnerDTO updatedOwner = owners.getOwner(anonOwner.getKey());
        assertThat(updatedOwner)
            .returns(true, OwnerDTO::getClaimed)
            .returns(destOwner.getKey(), OwnerDTO::getClaimantOwner);

        // Consumers should have regenerated identity certificates
        List<ConsumerDTO> updatedConsumers = fetchConsumers(consumers, admin);
        List<CertificateDTO> newIdCerts = updatedConsumers.stream().map(ConsumerDTO::getIdCert).toList();
        assertThat(newIdCerts)
            .hasSize(3)
            .doesNotContainAnyElementsOf(oldIdCerts);

        // Consumers should have regenerated content access certificates
        List<CertificateDTO> newScaCerts = fetchCertsOf(consumers, admin);
        assertThat(newScaCerts)
            .hasSize(3)
            .doesNotContainAnyElementsOf(oldScaCerts);
    }

    private List<ConsumerDTO> fetchConsumers(List<ConsumerDTO> consumers, ApiClient client) {
        return consumers.stream()
            .map(consumer -> client.consumers().getConsumer(consumer.getUuid()))
            .toList();
    }

    private List<CertificateDTO> fetchCertsOf(List<ConsumerDTO> consumers, ApiClient client) {
        return consumers.stream()
            .map(consumer -> client.consumers().fetchCertificates(consumer.getUuid(), null))
            .flatMap(Collection::stream)
            .toList();
    }

}
