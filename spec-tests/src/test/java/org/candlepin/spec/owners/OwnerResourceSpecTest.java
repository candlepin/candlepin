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
package org.candlepin.spec.owners;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.InstanceOfAssertFactories.collection;
import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertForbidden;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotFound;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.candlepin.dto.api.client.v1.ActivationKeyDTO;
import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.AttributeDTO;
import org.candlepin.dto.api.client.v1.CertificateDTO;
import org.candlepin.dto.api.client.v1.ClaimantOwner;
import org.candlepin.dto.api.client.v1.CloudAuthenticationResultDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTOArrayElement;
import org.candlepin.dto.api.client.v1.ConsumerTypeDTO;
import org.candlepin.dto.api.client.v1.ContentAccessDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.ContentToPromoteDTO;
import org.candlepin.dto.api.client.v1.EnvironmentDTO;
import org.candlepin.dto.api.client.v1.NestedOwnerDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.ReleaseVerDTO;
import org.candlepin.dto.api.client.v1.RoleDTO;
import org.candlepin.dto.api.client.v1.SetConsumerEnvironmentsDTO;
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
import org.candlepin.spec.bootstrap.data.builder.ActivationKeys;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Contents;
import org.candlepin.spec.bootstrap.data.builder.Environments;
import org.candlepin.spec.bootstrap.data.builder.Facts;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Permissions;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.builder.Subscriptions;
import org.candlepin.spec.bootstrap.data.util.CertificateUtil;
import org.candlepin.spec.bootstrap.data.util.DateUtil;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


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
        OwnerDTO actual = owners.createOwner(ownerDTO);

        assertThat(actual.getId()).isNotNull();
        assertThat(actual.getCreated()).isNotNull();
        assertThat(actual.getUpdated()).isNotNull();
        assertThat(actual.getContentAccessMode()).isNotNull();
        assertThat(actual.getContentAccessModeList()).isNotNull();
        assertThat(actual.getKey()).isEqualTo(ownerDTO.getKey());
        assertThat(actual.getDisplayName()).isEqualTo(ownerDTO.getDisplayName());
    }

    @Test
    public void shouldCreateScaOwner() {
        OwnerDTO ownerDTO = Owners.randomSca();
        OwnerDTO actual = owners.createOwner(ownerDTO);

        assertThat(actual.getId()).isNotNull();
        assertThat(actual.getCreated()).isNotNull();
        assertThat(actual.getUpdated()).isNotNull();
        assertThat(actual.getContentAccessMode()).isEqualTo(ownerDTO.getContentAccessMode());
        assertThat(actual.getContentAccessModeList()).isEqualTo(ownerDTO.getContentAccessModeList());
        assertThat(actual.getKey()).isEqualTo(ownerDTO.getKey());
        assertThat(actual.getDisplayName()).isEqualTo(ownerDTO.getDisplayName());
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
    public void shouldPopulateGeneratedFieldsWhenCreatingOwners() {
        OffsetDateTime init = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS);

        OwnerDTO output = this.owners.createOwner(Owners.random());

        OffsetDateTime post = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS);

        assertThat(output.getId())
            .isNotNull()
            .isNotBlank();

        assertThat(output.getCreated())
            .isNotNull()
            .isAfterOrEqualTo(init)
            .isBeforeOrEqualTo(post);

        assertThat(output.getUpdated())
            .isNotNull()
            .isAfterOrEqualTo(init)
            .isAfterOrEqualTo(output.getCreated())
            .isBeforeOrEqualTo(post);
    }

    @Test
    public void shouldUpdateGeneratedFieldsWhenUpdatingOwners() throws Exception {
        OwnerDTO entity = this.owners.createOwner(Owners.random());

        Thread.sleep(1100);

        OffsetDateTime init = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS);

        entity.setDisplayName(entity.getDisplayName() + "-update");
        OwnerDTO output = this.owners.updateOwner(entity.getKey(), entity);

        OffsetDateTime post = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS);

        assertThat(output.getCreated())
            .isNotNull()
            .isEqualTo(entity.getCreated())
            .isBeforeOrEqualTo(init);

        assertThat(output.getUpdated())
            .isNotNull()
            .isAfter(output.getCreated())
            .isAfterOrEqualTo(init)
            .isBeforeOrEqualTo(post);
    }

    @Test
    public void shouldPopulateGeneratedFieldsWhenCreatingOwnerPools() {
        OwnerDTO owner = this.owners.createOwner(Owners.random());
        ProductDTO product = this.createProduct(owner);

        OffsetDateTime init = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS);

        PoolDTO output = this.owners.createPool(owner.getKey(), Pools.random(product));

        OffsetDateTime post = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS);

        assertThat(output.getId())
            .isNotNull()
            .isNotBlank();

        assertThat(output.getCreated())
            .isNotNull()
            .isAfterOrEqualTo(init)
            .isBeforeOrEqualTo(post);

        assertThat(output.getUpdated())
            .isNotNull()
            .isAfterOrEqualTo(init)
            .isAfterOrEqualTo(output.getCreated())
            .isBeforeOrEqualTo(post);
    }

    // Techincally this test isn't necessary, since we know that by starting a second request to actually
    // fetch the results, a new transaction has started and the previous one has been committed. But we're
    // leaving this here for the sake of completion
    @Test
    public void shouldUpdateGeneratedFieldsWhenUpdatingOwnerPools() throws Exception {
        OwnerDTO owner = this.owners.createOwner(Owners.random());
        ProductDTO product = this.createProduct(owner);
        PoolDTO entity = this.owners.createPool(owner.getKey(), Pools.random(product));

        Thread.sleep(1100);

        OffsetDateTime init = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS);

        entity.setStartDate(entity.getStartDate().plusYears(1));
        this.owners.updatePool(owner.getKey(), entity);

        PoolDTO output = this.admin.pools().getPool(entity.getId(), null, null);

        OffsetDateTime post = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS);

        assertThat(output.getCreated())
            .isNotNull()
            .isEqualTo(entity.getCreated())
            .isBeforeOrEqualTo(init);

        assertThat(output.getUpdated())
            .isNotNull()
            .isAfter(output.getCreated())
            .isAfterOrEqualTo(init)
            .isBeforeOrEqualTo(post);
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

    @Nested
    @TestInstance(Lifecycle.PER_CLASS)
    public class ListOwnerPoolsPagingTests {
        private OwnerDTO owner;
        private String ownerKey;

        private int numberOfPools = 20;
        private List<PoolDTO> pools = new ArrayList<>();

        private Map<String, Comparator<PoolDTO>> comparatorMap = Map.of(
            "id", Comparator.comparing(PoolDTO::getId),
            "quantity", Comparator.comparing(PoolDTO::getQuantity));

        @BeforeAll
        public void setup() {
            owner = owners.createOwner(Owners.randomSca());
            ownerKey = owner.getKey();

            Random random = new Random();
            for (int i = 0; i < numberOfPools; i++) {
                ProductDTO product = createProduct(owner);
                PoolDTO pool = Pools.random(product)
                    .quantity(random.nextLong(100L, 10000L));

                pool = owners.createPool(ownerKey, pool);
                pools.add(pool);
            }
        }

        @Test
        public void shouldPageOwnersPools() {
            int pageSize = 5;
            List<String> actualPoolsIds = new ArrayList<>();
            for (int pageIndex = 1; pageIndex * pageSize <= numberOfPools; pageIndex++) {
                Paging paging = new Paging(pageIndex, pageSize, "id", "asc");

                List<String> poolIds = owners.listOwnerPools(ownerKey, paging).stream()
                    .map(PoolDTO::getId)
                    .collect(Collectors.toList());

                actualPoolsIds.addAll(poolIds);
            }

            List<String> expectedPoolIds = pools.stream()
                .map(PoolDTO::getId)
                .toList();

            assertThat(actualPoolsIds)
                .containsExactlyElementsOf(expectedPoolIds);
        }

        @Test
        public void shouldPageOwnersPoolsWithOrderByNotSpecified() {
            int pageSize = 5;
            List<String> actualPoolsIds = new ArrayList<>();
            for (int pageIndex = 1; pageIndex * pageSize <= numberOfPools; pageIndex++) {
                Paging paging = new Paging(pageIndex, pageSize, null, "asc");

                List<String> poolIds = owners.listOwnerPools(ownerKey, paging).stream()
                    .map(PoolDTO::getId)
                    .collect(Collectors.toList());

                actualPoolsIds.addAll(poolIds);
            }

            List<String> expectedPoolIds = pools.stream()
                .map(PoolDTO::getId)
                .toList();

            assertThat(actualPoolsIds)
                .containsExactlyElementsOf(expectedPoolIds);
        }

        @Test
        public void shouldPageOwnersPoolsInDescendingOrderWithOrderNotSpecified() {
            int pageSize = 5;
            List<String> actualPoolsIds = new ArrayList<>();
            for (int pageIndex = 1; pageIndex * pageSize <= numberOfPools; pageIndex++) {
                Paging paging = new Paging(pageIndex, pageSize, "id", null);

                List<String> poolIds = owners.listOwnerPools(ownerKey, paging).stream()
                    .map(PoolDTO::getId)
                    .collect(Collectors.toList());

                actualPoolsIds.addAll(poolIds);
            }

            List<String> expectedPoolIds = pools.stream()
                .sorted(comparatorMap.get("id").reversed())
                .map(PoolDTO::getId)
                .toList();

            assertThat(actualPoolsIds)
                .containsExactlyElementsOf(expectedPoolIds);
        }

        @ParameterizedTest
        @ValueSource(ints = { 0, -1, -100 })
        public void shouldFailWithInvalidPage(int page) {
            Paging paging = new Paging(page, 5, "id", "asc");

            assertBadRequest(() -> owners.listOwnerPools(ownerKey, paging));
        }

        @ParameterizedTest
        @ValueSource(ints = { 0, -1, -100 })
        public void shouldFailWithInvalidPageSize(int pageSize) {
            Paging paging = new Paging(1, pageSize, "id", "asc");

            assertBadRequest(() -> owners.listOwnerPools(ownerKey, paging));
        }

        @Test
        public void shouldFailWithInvalidOrderByField() {
            Paging paging = new Paging(1, numberOfPools, StringUtil.random(""), "asc");

            assertBadRequest(() -> owners.listOwnerPools(ownerKey, paging));
        }

        @Test
        public void shouldFailWithInvalidOrderDirection() {
            Paging paging = new Paging(1, numberOfPools, "id", StringUtil.random(""));

            assertBadRequest(() -> owners.listOwnerPools(ownerKey, paging));
        }

        @ParameterizedTest
        @ValueSource(strings = { "id", "quantity" })
        public void shouldOrderInAscendingOrder(String field) {
            List<String> expectedPoolIds = pools.stream()
                .sorted(comparatorMap.get(field))
                .map(PoolDTO::getId)
                .toList();

            Paging paging = new Paging(1, numberOfPools, field, "asc");
            List<PoolDTO> actual = owners.listOwnerPools(ownerKey, paging);

            assertThat(actual)
                .isNotNull()
                .map(PoolDTO::getId)
                .containsExactlyElementsOf(expectedPoolIds);
        }

        @ParameterizedTest
        @ValueSource(strings = { "id", "quantity" })
        public void shouldOrderInDescendingOrder(String field) {
            List<String> expectedPoolIds = pools.stream()
                .sorted(comparatorMap.get(field).reversed())
                .map(PoolDTO::getId)
                .toList();

            Paging paging = new Paging(1, numberOfPools, field, "desc");
            List<PoolDTO> actual = owners.listOwnerPools(ownerKey, paging);

            assertThat(actual)
                .isNotNull()
                .map(PoolDTO::getId)
                .containsExactlyElementsOf(expectedPoolIds);
        }

        @Test
        public void shouldLetOwnersListPoolsPagedForConsumer() {
            UserDTO user = UserUtil.createUser(admin, owner);
            ApiClient userClient = ApiClients.basic(user.getUsername(), user.getPassword());
            ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));

            List<String> expectedPoolIds = pools.stream()
                .sorted(Comparator.comparing(PoolDTO::getId))
                .map(PoolDTO::getId)
                .toList();

            Paging paging = new Paging(1, 10, "id", "asc");
            List<String> actualPage1 = ApiClients.admin().owners()
                .listOwnerPools(ownerKey, consumer.getUuid(), paging)
                .stream()
                .map(PoolDTO::getId)
                .collect(Collectors.toList());

            assertThat(actualPage1)
                .isNotNull()
                .hasSize(10)
                .containsExactlyElementsOf(expectedPoolIds.subList(0, 10));

            // Get page 2, per bz 1038273
            paging = new Paging(2, 10, "id", "asc");
            List<String> actualPage2 = ApiClients.admin().owners()
                .listOwnerPools(ownerKey, consumer.getUuid(), paging)
                .stream()
                .map(PoolDTO::getId)
                .collect(Collectors.toList());

            assertThat(actualPage2)
                .isNotNull()
                .hasSize(10)
                .containsExactlyElementsOf(expectedPoolIds.subList(10, 20));
        }
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
    public void shouldListOwnerPoolsAndIgnoreWarnings() {
        OwnerDTO owner = owners.createOwner(Owners.random());

        // Create a consumer that has more cores than the product allows. This will generate a warning and
        // will filter the pool when calling listOwnerPools unless the "listall" query parameter is true.

        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.ofEntries(Facts.CpuSockets.withValue("2"),
                Facts.CoresPerSocket.withValue("12"))));

        ProductDTO product = admin.ownerProducts().createProduct(owner.getKey(), Products.random()
            .attributes(List.of(new AttributeDTO().name("cores").value("10"))));

        PoolDTO expected = owners.createPool(owner.getKey(), Pools.random(product));

        List<PoolDTO> actual = admin.owners().listOwnerPools(owner.getKey(), consumer.getUuid(),
            null, null, null, true, null, null, null, null, null,
            null, null, null, null, null, null);

        assertThat(actual)
            .isNotNull()
            .singleElement()
            .returns(expected.getId(), PoolDTO::getId);
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

    @Test
    public void shouldRevokeEntitlementsAndRemoveActivationKeyPoolsWithChangeToSCA()
        throws InterruptedException {
        ApiClient admin = ApiClients.admin();
        OwnerDTO owner = admin.owners().createOwner(Owners.random());

        ProductDTO prod = admin.ownerProducts().createProduct(owner.getKey(), Products.random());
        PoolDTO pool1 = admin.owners().createPool(owner.getKey(), Pools.random(prod));
        PoolDTO pool2 = admin.owners().createPool(owner.getKey(), Pools.random(prod));

        ConsumerDTO consumer1 = admin.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO consumer2 = admin.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO consumer3 = admin.consumers().createConsumer(Consumers.random(owner));
        admin.consumers().bindPool(consumer1.getUuid(), pool1.getId(), 1);
        admin.consumers().bindPool(consumer2.getUuid(), pool2.getId(), 1);
        admin.consumers().bindPool(consumer3.getUuid(), pool2.getId(), 1);

        ActivationKeyDTO key1 = admin.owners()
            .createActivationKey(owner.getKey(), ActivationKeys.random(owner));
        ActivationKeyDTO key2 = admin.owners()
            .createActivationKey(owner.getKey(), ActivationKeys.random(owner));

        key1 = admin.activationKeys().addPoolToKey(key1.getId(), pool1.getId(), 1L);
        key2 = admin.activationKeys().addPoolToKey(key2.getId(), pool2.getId(), 1L);

        assertThat(admin.entitlements().listAllForConsumer(consumer1.getUuid()))
            .isNotNull()
            .singleElement();

        assertThat(admin.entitlements().listAllForConsumer(consumer2.getUuid()))
            .isNotNull()
            .singleElement();

        assertThat(admin.entitlements().listAllForConsumer(consumer3.getUuid()))
            .isNotNull()
            .singleElement();

        assertThat(admin.activationKeys().getActivationKeyPools(key1.getId()))
            .isNotNull()
            .singleElement()
            .returns(pool1.getId(), PoolDTO::getId);

        assertThat(admin.activationKeys().getActivationKeyPools(key2.getId()))
            .isNotNull()
            .singleElement()
            .returns(pool2.getId(), PoolDTO::getId);

        OffsetDateTime timeBeforeUpdate = OffsetDateTime.now();
        // Because MySQL/Mariadb versions before 5.6.4 truncate milliseconds, lets remove a couple seconds
        // to ensure we will not miss the job we need in the job query results later on.
        timeBeforeUpdate = timeBeforeUpdate.minusSeconds(2);

        // Update owner to SCA mode
        owner.contentAccessMode(Owners.SCA_ACCESS_MODE);
        owner = admin.owners().updateOwner(owner.getKey(), owner);

        // Wait for the entitlement revoke job to finish
        List<AsyncJobStatusDTO> jobs = admin.jobs().listJobStatuses(null,
            Set.of("EntitlementRevokingJob"), null, Set.of(owner.getKey()), null, null, null,
            timeBeforeUpdate, null, null, null, null, null);
        jobs.forEach(job -> {
            job = admin.jobs().waitForJob(job);
            assertThatJob(job)
                .isFinished()
                // Number of revoked entitlements
                .contains("3")
                // Number of removed activation key pools
                .contains("2");
        });

        assertThat(admin.entitlements().listAllForConsumer(consumer1.getUuid()))
            .isNotNull()
            .isEmpty();

        assertThat(admin.entitlements().listAllForConsumer(consumer2.getUuid()))
            .isNotNull()
            .isEmpty();

        assertThat(admin.entitlements().listAllForConsumer(consumer3.getUuid()))
            .isNotNull()
            .isEmpty();

        assertThat(admin.activationKeys().getActivationKeyPools(key1.getId()))
            .isNotNull()
            .isEmpty();

        assertThat(admin.activationKeys().getActivationKeyPools(key2.getId()))
            .isNotNull()
            .isEmpty();
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

    @Nested
    @Isolated
    @Execution(ExecutionMode.SAME_THREAD)
    class GetPages {
        @Test
        public void shouldListOwnersPagedAndSorted() {
            ApiClient adminClient = ApiClients.admin();

            // Ensure that there is data for this test.
            // Most likely, there will already be data left from other test runs that will show up
            IntStream.range(0, 5).forEach(entry -> {
                adminClient.owners().createOwner(Owners.random());
                // for timestamp separation
                try {
                    sleep(1000);
                }
                catch (InterruptedException ie) {
                    throw new RuntimeException("Unable to sleep as expected");
                }
            });

            List<OwnerDTO> owners = adminClient.owners().listOwners(null, 1, 4, "asc", "created");
            assertThat(owners)
                .isNotNull()
                .hasSize(4);
            assertThat(owners.get(0).getCreated().compareTo(owners.get(1).getCreated()))
                .isNotPositive();
            assertThat(owners.get(1).getCreated().compareTo(owners.get(2).getCreated()))
                .isNotPositive();
            assertThat(owners.get(2).getCreated().compareTo(owners.get(3).getCreated()))
                .isNotPositive();
        }
    }

    @ParameterizedTest(name = "{displayName} {index}: {0} {1}")
    @NullAndEmptySource
    public void shouldThrowBadRequestWhenBulkSetConsumerEnvsWithNullOrEmptyConsumerUuids(List<String> uuids) {
        OwnerDTO owner = admin.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        SetConsumerEnvironmentsDTO req = new SetConsumerEnvironmentsDTO();
        req.setConsumerUuids(uuids);
        req.setEnvironmentIds(List.of(StringUtil.random("env-"), StringUtil.random("env-")));

        assertBadRequest(() -> admin.owners().setConsumersToEnvironments(ownerKey, req));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0} {1}")
    @NullAndEmptySource
    public void shouldThrowBadRequestWhenBulkSetConsumerEnvsWithNullAndEmptyEnvIds(List<String> envIds) {
        OwnerDTO owner = admin.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        SetConsumerEnvironmentsDTO req = new SetConsumerEnvironmentsDTO();
        req.setConsumerUuids(List.of(StringUtil.random("c-"), StringUtil.random("c-")));
        req.setEnvironmentIds(envIds);

        assertBadRequest(() -> admin.owners().setConsumersToEnvironments(ownerKey, req));
    }

    @Test
    public void shouldThrowNotFoundWhenBulkSetConsumerEnvsWithUnknownOwnerKey() {
        OwnerDTO owner = admin.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();
        EnvironmentDTO targetEnv = admin.owners().createEnvironment(ownerKey, Environments.random()
            .id(StringUtil.random("env-")));
        ConsumerDTO consumer = Consumers.random(owner)
            .environments(List.of(targetEnv));
        consumer = admin.consumers().createConsumer(consumer);

        SetConsumerEnvironmentsDTO req = new SetConsumerEnvironmentsDTO();
        req.setConsumerUuids(List.of(consumer.getUuid(), StringUtil.random("unknown-")));
        req.setEnvironmentIds(List.of(targetEnv.getId()));

        assertNotFound(() -> admin.owners()
            .setConsumersToEnvironments(StringUtil.random("unknown-"), req));
    }

    @Test
    public void shouldThrowBadRequestWhenBulkSetConsumerEnvsWithOwnerInEntitlementMode() {
        OwnerDTO owner = admin.owners().createOwner(Owners.random());
        String ownerKey = owner.getKey();
        EnvironmentDTO targetEnv = admin.owners().createEnvironment(ownerKey, Environments.random()
            .id(StringUtil.random("env-")));
        ConsumerDTO consumer = Consumers.random(owner)
            .environments(List.of(targetEnv));
        consumer = admin.consumers().createConsumer(consumer);

        SetConsumerEnvironmentsDTO req = new SetConsumerEnvironmentsDTO();
        req.setConsumerUuids(List.of(consumer.getUuid(), StringUtil.random("unknown-")));
        req.setEnvironmentIds(List.of(targetEnv.getId()));

        assertBadRequest(() -> admin.owners().setConsumersToEnvironments(ownerKey, req));
    }

    @Test
    public void shouldThrowBadRequestWhenBulkSetConsumerEnvsWithUnknownConsumerUuid() {
        OwnerDTO owner = admin.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();
        EnvironmentDTO targetEnv = admin.owners().createEnvironment(ownerKey, Environments.random()
            .id(StringUtil.random("env-")));
        ConsumerDTO consumer = Consumers.random(owner)
            .environments(List.of(targetEnv));
        consumer = admin.consumers().createConsumer(consumer);

        SetConsumerEnvironmentsDTO req = new SetConsumerEnvironmentsDTO();
        req.setConsumerUuids(List.of(consumer.getUuid(), StringUtil.random("unknown-")));
        req.setEnvironmentIds(List.of(targetEnv.getId()));

        assertBadRequest(() -> admin.owners().setConsumersToEnvironments(ownerKey, req));
    }

    @Test
    public void shouldThrowBadRequestWhenBulkSetConsumerEnvsWithUnknownEnvironmentId() {
        OwnerDTO owner = admin.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();
        EnvironmentDTO targetEnv = admin.owners().createEnvironment(ownerKey, Environments.random()
            .id(StringUtil.random("env-")));
        ConsumerDTO consumer = Consumers.random(owner)
            .environments(List.of(targetEnv));
        consumer = admin.consumers().createConsumer(consumer);

        SetConsumerEnvironmentsDTO req = new SetConsumerEnvironmentsDTO();
        req.setConsumerUuids(List.of(consumer.getUuid()));
        req.setEnvironmentIds(List.of(targetEnv.getId(), StringUtil.random("unknown-")));

        assertBadRequest(() -> admin.owners().setConsumersToEnvironments(ownerKey, req));
    }

    @Test
    public void shouldThrowBadRequestWhenBulkSetConsumerEnvsWithEnvironmentIdFromOtherOwner() {
        OwnerDTO owner1 = admin.owners().createOwner(Owners.randomSca());
        String owner1Key = owner1.getKey();
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(owner1));
        EnvironmentDTO owner1Env = admin.owners().createEnvironment(owner1Key, Environments.random()
            .id(StringUtil.random("env-")));

        OwnerDTO owner2 = admin.owners().createOwner(Owners.randomSca());
        EnvironmentDTO owner2Env = admin.owners().createEnvironment(owner2.getKey(), Environments.random()
            .id(StringUtil.random("env-")));

        SetConsumerEnvironmentsDTO request = new SetConsumerEnvironmentsDTO();
        request.setConsumerUuids(List.of(consumer.getUuid()));
        request.setEnvironmentIds(List.of(owner1Env.getId(), owner2Env.getId()));

        assertBadRequest(() ->  admin.owners().setConsumersToEnvironments(owner1Key, request));
    }

    @Test
    public void shouldSetConsumerEnvironmentsWithDuplicateConsumerUuids() {
        OwnerDTO owner = admin.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(owner));
        EnvironmentDTO targetEnv = admin.owners().createEnvironment(ownerKey, Environments.random()
            .id(StringUtil.random("env-")));

        SetConsumerEnvironmentsDTO request = new SetConsumerEnvironmentsDTO();
        request.setConsumerUuids(List.of(consumer.getUuid(), consumer.getUuid()));
        request.setEnvironmentIds(List.of(targetEnv.getId()));

        admin.owners().setConsumersToEnvironments(ownerKey, request);

        ConsumerDTO actual = admin.consumers().getConsumer(consumer.getUuid());
        assertThat(actual)
            .isNotNull()
            .extracting(ConsumerDTO::getEnvironments, as(collection(EnvironmentDTO.class)))
            .containsOnly(targetEnv);
    }

    @Test
    public void shouldThrowBadRequestWhenBulkSetConsumerEnvsWithDuplicateEnvironmentIds() {
        OwnerDTO owner = admin.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(owner));
        EnvironmentDTO targetEnv = admin.owners().createEnvironment(ownerKey, Environments.random()
            .id(StringUtil.random("env-")));

        SetConsumerEnvironmentsDTO request = new SetConsumerEnvironmentsDTO();
        request.setConsumerUuids(List.of(consumer.getUuid()));
        request.setEnvironmentIds(List.of(targetEnv.getId(), targetEnv.getId()));

        assertBadRequest(() -> admin.owners().setConsumersToEnvironments(ownerKey, request));
    }

    @Test
    public void shouldThrowBadRequestWhenBulkSetConsumerEnvsWithConsumerFromOtherOwner() {
        OwnerDTO owner1 = admin.owners().createOwner(Owners.randomSca());
        String owner1Key = owner1.getKey();
        EnvironmentDTO env1 = admin.owners().createEnvironment(owner1Key, Environments.random()
            .id(StringUtil.random("env-1-")));

        OwnerDTO owner2 = admin.owners().createOwner(Owners.randomSca());
        String owner2Key = owner1.getKey();
        EnvironmentDTO env2 = admin.owners().createEnvironment(owner2Key, Environments.random()
            .id(StringUtil.random("env-2-")));

        ConsumerDTO consumer = Consumers.random(owner2)
            .environments(List.of(env2));
        consumer = admin.consumers().createConsumer(consumer);

        SetConsumerEnvironmentsDTO req = new SetConsumerEnvironmentsDTO();
        req.setConsumerUuids(List.of(consumer.getUuid()));
        req.setEnvironmentIds(List.of(env1.getId()));

        assertBadRequest(() -> admin.owners().setConsumersToEnvironments(owner1Key, req));
    }

    @Test
    public void shouldSetConsumerEnvironmentsWithConsumerWithDifferentEnvironment() {
        OwnerDTO owner = admin.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        EnvironmentDTO targetEnv1 = admin.owners().createEnvironment(ownerKey, Environments.random()
            .id(StringUtil.random("target-1-")));
        EnvironmentDTO targetEnv2 = admin.owners().createEnvironment(ownerKey, Environments.random()
            .id(StringUtil.random("target-2-")));
        EnvironmentDTO otherEnv = admin.owners().createEnvironment(ownerKey, Environments.random()
            .id(StringUtil.random("other-")));

        ConsumerDTO consumer = Consumers.random(owner)
            .environments(List.of(targetEnv1, targetEnv2, otherEnv));

        consumer = admin.consumers().createConsumer(consumer);

        SetConsumerEnvironmentsDTO req = new SetConsumerEnvironmentsDTO();
        req.setConsumerUuids(List.of(consumer.getUuid()));
        req.setEnvironmentIds(List.of(targetEnv1.getId(), targetEnv2.getId()));

        admin.owners().setConsumersToEnvironments(ownerKey, req);

        ConsumerDTO actual = admin.consumers().getConsumer(consumer.getUuid());
        assertThat(actual)
            .isNotNull()
            .extracting(ConsumerDTO::getEnvironments, as(collection(EnvironmentDTO.class)))
            .isNotNull()
            .hasSize(2)
            .containsExactly(targetEnv1, targetEnv2);
    }

    @Test
    public void shouldSetConsumerEnvironmentsWithConsumerWithMissingEnvironment() {
        OwnerDTO owner = admin.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        EnvironmentDTO targetEnv1 = admin.owners().createEnvironment(ownerKey, Environments.random()
            .id(StringUtil.random("target-1-")));
        EnvironmentDTO targetEnv2 = admin.owners().createEnvironment(ownerKey, Environments.random()
            .id(StringUtil.random("target-2-")));

        ConsumerDTO consumer = Consumers.random(owner)
            .environments(List.of(targetEnv1));
        consumer = admin.consumers().createConsumer(consumer);

        SetConsumerEnvironmentsDTO req = new SetConsumerEnvironmentsDTO();
        req.setConsumerUuids(List.of(consumer.getUuid()));
        req.setEnvironmentIds(List.of(targetEnv1.getId(), targetEnv2.getId()));

        admin.owners().setConsumersToEnvironments(ownerKey, req);

        ConsumerDTO actual = admin.consumers().getConsumer(consumer.getUuid());
        assertThat(actual)
            .isNotNull()
            .extracting(ConsumerDTO::getEnvironments, as(collection(EnvironmentDTO.class)))
            .isNotNull()
            .hasSize(2)
            .containsExactly(targetEnv1, targetEnv2);
    }

    @Test
    public void shouldSetConsumerEnvironmentsWithConsumerWithWrongPriority() {
        OwnerDTO owner = admin.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        EnvironmentDTO targetEnv1 = admin.owners().createEnvironment(ownerKey, Environments.random()
            .id(StringUtil.random("target-1-")));
        EnvironmentDTO targetEnv2 = admin.owners().createEnvironment(ownerKey, Environments.random()
            .id(StringUtil.random("target-2-")));

        ConsumerDTO consumer = Consumers.random(owner)
            .environments(List.of(targetEnv2, targetEnv1));
        consumer = admin.consumers().createConsumer(consumer);

        SetConsumerEnvironmentsDTO req = new SetConsumerEnvironmentsDTO();
        req.setConsumerUuids(List.of(consumer.getUuid()));
        req.setEnvironmentIds(List.of(targetEnv1.getId(), targetEnv2.getId()));

        admin.owners().setConsumersToEnvironments(ownerKey, req);

        ConsumerDTO actual = admin.consumers().getConsumer(consumer.getUuid());
        assertThat(actual)
            .isNotNull()
            .extracting(ConsumerDTO::getEnvironments, as(collection(EnvironmentDTO.class)))
            .isNotNull()
            .hasSize(2)
            .containsExactly(targetEnv1, targetEnv2);
    }

    @Test
    public void shouldSetConsumerEnvironmentsWithConsumerAlreadyExactlyInEnvironments() {
        OwnerDTO owner = admin.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        EnvironmentDTO targetEnv1 = admin.owners().createEnvironment(ownerKey, Environments.random()
            .id(StringUtil.random("target-1-")));
        EnvironmentDTO targetEnv2 = admin.owners().createEnvironment(ownerKey, Environments.random()
            .id(StringUtil.random("target-2-")));

        ConsumerDTO consumer = Consumers.random(owner)
            .environments(List.of(targetEnv1, targetEnv2));
        consumer = admin.consumers().createConsumer(consumer);

        SetConsumerEnvironmentsDTO req = new SetConsumerEnvironmentsDTO();
        req.setConsumerUuids(List.of(consumer.getUuid()));
        req.setEnvironmentIds(List.of(targetEnv1.getId(), targetEnv2.getId()));

        admin.owners().setConsumersToEnvironments(ownerKey, req);

        ConsumerDTO actual = admin.consumers().getConsumer(consumer.getUuid());
        assertThat(actual)
            .isNotNull()
            .extracting(ConsumerDTO::getEnvironments, as(collection(EnvironmentDTO.class)))
            .isNotNull()
            .hasSize(2)
            .containsExactly(targetEnv1, targetEnv2);
    }

    @Test
    public void shouldRemoveConsumersFromAllEnvironmentsWithEmptyEnvList() {
        OwnerDTO owner = admin.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        EnvironmentDTO env1 = admin.owners().createEnvironment(ownerKey, Environments.random()
            .id(StringUtil.random("env-1-")));
        EnvironmentDTO env2 = admin.owners().createEnvironment(ownerKey, Environments.random()
            .id(StringUtil.random("env-2-")));
        EnvironmentDTO env3 = admin.owners().createEnvironment(ownerKey, Environments.random()
            .id(StringUtil.random("env-3-")));

        ConsumerDTO consumer1 = Consumers.random(owner)
            .environments(List.of(env1));
        consumer1 = admin.consumers().createConsumer(consumer1);
        ConsumerDTO consumer2 = Consumers.random(owner)
            .environments(List.of(env2));
        consumer2 = admin.consumers().createConsumer(consumer2);
        ConsumerDTO consumer3 = Consumers.random(owner)
            .environments(List.of(env3));
        consumer3 = admin.consumers().createConsumer(consumer3);

        SetConsumerEnvironmentsDTO req = new SetConsumerEnvironmentsDTO();
        req.setConsumerUuids(List.of(consumer1.getUuid(), consumer2.getUuid(), consumer3.getUuid()));
        req.setEnvironmentIds(List.of());

        admin.owners().setConsumersToEnvironments(ownerKey, req);

        assertThat(admin.consumers().getConsumer(consumer1.getUuid()))
            .isNotNull()
            .returns(null, ConsumerDTO::getEnvironments);

        assertThat(admin.consumers().getConsumer(consumer2.getUuid()))
            .isNotNull()
            .returns(null, ConsumerDTO::getEnvironments);

        assertThat(admin.consumers().getConsumer(consumer3.getUuid()))
            .isNotNull()
            .returns(null, ConsumerDTO::getEnvironments);
    }

    @Test
    public void shouldSetConsumerEnvironmentsWithMultipleConsumers() {
        OwnerDTO owner = admin.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        EnvironmentDTO targetEnv1 = admin.owners().createEnvironment(ownerKey, Environments.random()
            .id(StringUtil.random("target-1-")));
        EnvironmentDTO targetEnv2 = admin.owners().createEnvironment(ownerKey, Environments.random()
            .id(StringUtil.random("target-2-")));
        admin.owners().createEnvironment(ownerKey, Environments.random()
            .id(StringUtil.random("env-3-")));

        admin.consumers().createConsumer(Consumers.random(owner));

        ConsumerDTO consumer1 = Consumers.random(owner)
            .environments(List.of(targetEnv1));
        consumer1 = admin.consumers().createConsumer(consumer1);

        ConsumerDTO consumer2 = Consumers.random(owner)
            .environments(List.of(targetEnv2));
        consumer2 = admin.consumers().createConsumer(consumer2);

        SetConsumerEnvironmentsDTO req = new SetConsumerEnvironmentsDTO();
        req.setConsumerUuids(List.of(consumer1.getUuid(), consumer2.getUuid()));
        req.setEnvironmentIds(List.of(targetEnv1.getId(), targetEnv2.getId()));

        admin.owners().setConsumersToEnvironments(ownerKey, req);

        ConsumerDTO actual = admin.consumers().getConsumer(consumer1.getUuid());
        assertThat(actual)
            .isNotNull()
            .extracting(ConsumerDTO::getEnvironments, as(collection(EnvironmentDTO.class)))
            .isNotNull()
            .hasSize(2)
            .containsExactly(targetEnv1, targetEnv2);

        actual = admin.consumers().getConsumer(consumer2.getUuid());
        assertThat(actual)
            .isNotNull()
            .extracting(ConsumerDTO::getEnvironments, as(collection(EnvironmentDTO.class)))
            .isNotNull()
            .hasSize(2)
            .containsExactly(targetEnv1, targetEnv2);
    }

    @Test
    public void shouldUpdateContentAccessCertificateWhenSettingConsumerEnvironments() {
        OwnerDTO owner = admin.owners().createOwner(Owners.randomSca());
        String ownerKey = owner.getKey();

        ContentDTO content1 = admin.ownerContent().createContent(ownerKey, Contents.random());
        ProductDTO prod1 = admin.ownerProducts().createProduct(ownerKey, Products.random());
        admin.ownerProducts().addContentToProduct(ownerKey, prod1.getId(), content1.getId(), true);
        admin.owners().createPool(ownerKey, Pools.random(prod1));

        ContentDTO content2 = admin.ownerContent().createContent(ownerKey, Contents.random());
        ProductDTO prod2 = admin.ownerProducts().createProduct(ownerKey, Products.random());
        admin.ownerProducts().addContentToProduct(ownerKey, prod2.getId(), content2.getId(), true);
        admin.owners().createPool(ownerKey, Pools.random(prod2));

        EnvironmentDTO env1 = admin.owners().createEnvironment(ownerKey, Environments.random()
            .id(StringUtil.random("env-1-")));
        EnvironmentDTO env2 = admin.owners().createEnvironment(ownerKey, Environments.random()
            .id(StringUtil.random("env-2-")));

        ContentToPromoteDTO promote = new ContentToPromoteDTO()
            .environmentId(env1.getId())
            .contentId(content1.getId())
            .enabled(true);
        admin.environments().promoteContent(env1.getId(), List.of(promote), true);
        promote = new ContentToPromoteDTO()
            .environmentId(env2.getId())
            .contentId(content2.getId())
            .enabled(true);
        admin.environments().promoteContent(env2.getId(), List.of(promote), true);

        ConsumerDTO consumer = Consumers.random(owner)
            .environments(List.of(env1));
        consumer = admin.consumers().createConsumer(consumer);

        List<JsonNode> certs = admin.consumers().exportCertificates(consumer.getUuid(), null);
        Map<String, List<String>> prodIdToContentIds = CertificateUtil.toProductContentIdMap(certs.get(0));
        assertThat(prodIdToContentIds)
            .hasSize(1)
            .extractingByKey("content_access", as(collection(String.class)))
            .singleElement()
            .isEqualTo(content1.getId());

        SetConsumerEnvironmentsDTO req = new SetConsumerEnvironmentsDTO();
        req.setConsumerUuids(List.of(consumer.getUuid(), consumer.getUuid()));
        req.setEnvironmentIds(List.of(env2.getId()));

        admin.owners().setConsumersToEnvironments(ownerKey, req);

        // Verify that the certificate content was updated
        certs = admin.consumers().exportCertificates(consumer.getUuid(), null);
        assertThat(certs).singleElement();
        prodIdToContentIds = CertificateUtil.toProductContentIdMap(certs.get(0));
        assertThat(prodIdToContentIds)
            .hasSize(1)
            .extractingByKey("content_access", as(collection(String.class)))
            .singleElement()
            .isEqualTo(content2.getId());
    }

}
