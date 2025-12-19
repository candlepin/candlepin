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
package org.candlepin.spec.products;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotFound;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.NestedOwnerDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.SubscriptionDTO;
import org.candlepin.spec.bootstrap.assertions.CandlepinMode;
import org.candlepin.spec.bootstrap.assertions.OnlyInHosted;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.request.Request;
import org.candlepin.spec.bootstrap.client.request.Response;
import org.candlepin.spec.bootstrap.data.builder.ExportGenerator;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.builder.Subscriptions;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

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

import tools.jackson.core.type.TypeReference;

import java.io.File;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;


@SpecTest
public class ProductResourceSpecTest {

    private ProductDTO createProduct(ApiClient client, String productId, String ownerKey,
        ProductDTO derivedProduct, Collection<ProductDTO> providedProducts) {

        ProductDTO product = new ProductDTO()
            .id(productId)
            .name(productId)
            .derivedProduct(derivedProduct);

        if (providedProducts != null && !providedProducts.isEmpty()) {
            product.setProvidedProducts(new HashSet<>(providedProducts));
        }

        return client.ownerProducts()
            .createProduct(ownerKey, product);
    }

    private PoolDTO createPool(ApiClient client, String ownerKey, ProductDTO product) {
        OffsetDateTime now = Instant.now()
            .atOffset(ZoneOffset.UTC);

        PoolDTO pool = new PoolDTO()
            .productId(product.getId())
            .productName(product.getName())
            .quantity(10L)
            .startDate(now.minus(1, ChronoUnit.DAYS))
            .endDate(now.plus(10, ChronoUnit.DAYS));

        return client.owners()
            .createPool(ownerKey, pool);
    }

    /**
     * Creates test data for the refresh pools by products tests. Do not use this data for other tests
     *
     * @param client
     *  the API client to use for creating test data
     *
     * @return
     *  a map of product IDs, to owner keys, to product instances
     */
    private Map<String, Map<String, ProductDTO>> setupDataForRefreshProductPoolsTests(ApiClient client) {
        OwnerDTO owner1 = client.owners().createOwner(Owners.random());
        OwnerDTO owner2 = client.owners().createOwner(Owners.random());
        OwnerDTO owner3 = client.owners().createOwner(Owners.random());

        String p1ProductId = StringUtil.random("p1");
        String p2ProductId = StringUtil.random("p2");
        String p3ProductId = StringUtil.random("p3");
        String p4dProductId = StringUtil.random("p4d");
        String p4ProductId = StringUtil.random("p4");
        String p5dProductId = StringUtil.random("p5d");
        String p5ProductId = StringUtil.random("p5");
        String p6dProductId = StringUtil.random("p6d");
        String p6ProductId = StringUtil.random("p6");

        ProductDTO prod1o1 = this.createProduct(client, p1ProductId, owner1.getKey(), null, null);
        ProductDTO prod1o2 = this.createProduct(client, p1ProductId, owner2.getKey(), null, null);
        ProductDTO prod1o3 = this.createProduct(client, p1ProductId, owner3.getKey(), null, null);

        ProductDTO prod2o1 = this.createProduct(client, p2ProductId, owner1.getKey(), null, null);
        ProductDTO prod2o2 = this.createProduct(client, p2ProductId, owner2.getKey(), null, null);

        ProductDTO prod3o2 = this.createProduct(client, p3ProductId, owner2.getKey(), null, null);
        ProductDTO prod3o3 = this.createProduct(client, p3ProductId, owner3.getKey(), null, null);

        ProductDTO prod4d = this.createProduct(client, p4dProductId, owner1.getKey(), null, List.of(prod2o1));
        ProductDTO prod4 = this.createProduct(client, p4ProductId, owner1.getKey(), prod4d, List.of(prod1o1));

        ProductDTO prod5d = this.createProduct(client, p5dProductId, owner2.getKey(), null, List.of(prod3o2));
        ProductDTO prod5 = this.createProduct(client, p5ProductId, owner2.getKey(), prod5d,
            List.of(prod1o2, prod2o2));

        ProductDTO prod6d = this.createProduct(client, p6dProductId, owner3.getKey(), null, List.of(prod3o3));
        ProductDTO prod6 = this.createProduct(client, p6ProductId, owner3.getKey(), prod6d, List.of(prod1o3));

        this.createPool(client, owner1.getKey(), prod4);
        this.createPool(client, owner2.getKey(), prod5);
        this.createPool(client, owner3.getKey(), prod6);

        Map<String, ProductDTO> p1map = Map.of(
            owner1.getKey(), prod1o1,
            owner2.getKey(), prod1o2,
            owner3.getKey(), prod1o3);

        Map<String, ProductDTO> p2map = Map.of(
            owner1.getKey(), prod2o1,
            owner2.getKey(), prod2o2);

        Map<String, ProductDTO> p3map = Map.of(
            owner2.getKey(), prod3o2,
            owner3.getKey(), prod3o3);

        return Map.of(
            p1ProductId, p1map,
            p2ProductId, p2map,
            p3ProductId, p3map,
            p4dProductId, Map.of(owner1.getKey(), prod4d),
            p4ProductId, Map.of(owner1.getKey(), prod4),
            p5dProductId, Map.of(owner2.getKey(), prod5d),
            p5ProductId, Map.of(owner2.getKey(), prod5),
            p6dProductId, Map.of(owner3.getKey(), prod6d),
            p6ProductId, Map.of(owner3.getKey(), prod6));
    }

    @Test
    @OnlyInHosted
    public void shouldRefreshPoolsForOrgsOwningProducts() throws Exception {
        ApiClient client = ApiClients.admin();
        Map<String, Map<String, ProductDTO>> data = this.setupDataForRefreshProductPoolsTests(client);

        for (Map.Entry<String, Map<String, ProductDTO>> entry : data.entrySet()) {
            String pid = entry.getKey();
            Map<String, ProductDTO> orgProductMap = entry.getValue();

            List<AsyncJobStatusDTO> jobs = client.products()
                .refreshPoolsForProducts(List.of(pid), true);

            assertThat(jobs)
                .isNotNull()
                .hasSize(orgProductMap.size())
                .allSatisfy(job -> assertThatJob(job)
                    .isNotNull()
                    .isType("RefreshPoolsJob")
                    .terminates(client)
                    .isFinished());
        }
    }

    @Test
    @OnlyInHosted
    public void shouldRefreshPoolsForAllOrgsOwningMultipleProducts() throws Exception {
        ApiClient client = ApiClients.admin();
        Map<String, Map<String, ProductDTO>> data = this.setupDataForRefreshProductPoolsTests(client);

        // API generation fail here. We can't pass in arbitrary collections, so we have convert it to
        // a list first
        List<String> pids = new ArrayList<>(data.keySet());

        // We expect one job for every org that is found in the data set. At the time of writing, that
        // should be three.
        int expectedJobCount = (int) data.values()
            .stream()
            .flatMap(map -> map.keySet().stream())
            .distinct()
            .count();

        List<AsyncJobStatusDTO> jobs = client.products()
            .refreshPoolsForProducts(pids, true);

        assertThat(jobs)
            .isNotNull()
            .hasSize(expectedJobCount)
            .allSatisfy(job -> assertThatJob(job)
                .isNotNull()
                .isType("RefreshPoolsJob")
                .terminates(client)
                .isFinished());
    }

    @Test
    @OnlyInHosted
    public void shouldNotCreateRefreshPoolsJobForUnknownProducts() throws Exception {
        ApiClient client = ApiClients.admin();
        Map<String, Map<String, ProductDTO>> data = this.setupDataForRefreshProductPoolsTests(client);

        List<AsyncJobStatusDTO> jobs = client.products()
            .refreshPoolsForProducts(List.of("unknown_pid"), true);

        assertThat(jobs)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void shouldRaiseBadRequestOnRefreshWithNoProducts() {
        ApiClient client = ApiClients.admin();

        assertBadRequest(() -> client.products().refreshPoolsForProducts(List.of(), false));
    }

    @Test
    public void shouldRaiseNotFoundForNonExistingProducts() {
        ApiClient client = ApiClients.admin();

        assertNotFound(() -> client.products().getProductByUuid("unknown-product-id"));
    }

    @Nested
    @Isolated
    @TestInstance(Lifecycle.PER_CLASS)
    @Execution(ExecutionMode.SAME_THREAD)
    public class ProductQueryTests {

        private static final String QUERY_PATH = "/products";

        private static final String INCLUSION_INCLUDE = "include";
        private static final String INCLUSION_EXCLUDE = "exclude";
        private static final String INCLUSION_EXCLUSIVE = "exclusive";

        private static record ProductInfo(ProductDTO dto, OwnerDTO owner, Set<String> activeOwners) {
            public String id() {
                return this.dto() != null ? this.dto().getId() : null;
            }

            public boolean active() {
                return !this.activeOwners().isEmpty();
            }

            public boolean custom() {
                return this.owner() != null;
            }
        };

        private ApiClient adminClient;

        private List<OwnerDTO> owners;
        private Map<String, ProductInfo> productMap;

        /**
         * Verifies either the manifest generator extension or the hosted test extension is present as
         * required by the current operating mode.
         */
        private void checkRequiredExtensions() {
            if (CandlepinMode.isStandalone()) {
                assumeTrue(CandlepinMode::hasManifestGenTestExtension);
            }
            else {
                assumeTrue(CandlepinMode::hasHostedTestExtension);
            }
        }

        private OwnerDTO createOwner(String keyPrefix) {
            OwnerDTO owner = Owners.random()
                .key(StringUtil.random(keyPrefix + "-"));

            return this.adminClient.owners()
                .createOwner(owner);
        }

        private SubscriptionDTO createSubscription(OwnerDTO owner, ProductDTO product, boolean active) {
            OffsetDateTime now = Instant.now()
                .atOffset(ZoneOffset.UTC);

            // Impl note:
            // We create active subscriptions in the future to work around a limitation on manifests
            // disallowing and ignoring expired pools
            return Subscriptions.random(owner, product)
                .startDate(active ? now.minus(7, ChronoUnit.DAYS) : now.plus(7, ChronoUnit.DAYS))
                .endDate(now.plus(30, ChronoUnit.DAYS));
        }

        private PoolDTO createPool(OwnerDTO owner, ProductDTO product, boolean active) {
            OffsetDateTime now = Instant.now()
                .atOffset(ZoneOffset.UTC);

            PoolDTO pool = Pools.random(product)
                .startDate(now.minus(7, ChronoUnit.DAYS))
                .endDate(active ? now.plus(7, ChronoUnit.DAYS) : now.minus(3, ChronoUnit.DAYS));

            return this.adminClient.owners()
                .createPool(owner.getKey(), pool);
        }

        private void mapProductInfo(ProductDTO product, OwnerDTO owner, OwnerDTO activeOwner) {
            Set<String> activeOwnerKeys = new HashSet<>();
            if (activeOwner != null) {
                activeOwnerKeys.add(activeOwner.getKey());
            }

            ProductInfo existing = this.productMap.get(product.getId());
            if (existing != null) {
                activeOwnerKeys.addAll(existing.activeOwners());
            }

            ProductInfo pinfo = new ProductInfo(product, owner, activeOwnerKeys);
            this.productMap.put(product.getId(), pinfo);

        }

        private void commitGlobalSubscriptions(Collection<SubscriptionDTO> subscriptions) throws Exception {
            Map<String, List<SubscriptionDTO>> subscriptionMap = new HashMap<>();

            for (SubscriptionDTO subscription : subscriptions) {
                NestedOwnerDTO owner = subscription.getOwner();

                subscriptionMap.computeIfAbsent(owner.getKey(), key -> new ArrayList<>())
                    .add(subscription);
            }

            for (Map.Entry<String, List<SubscriptionDTO>> entry : subscriptionMap.entrySet()) {
                String ownerKey = entry.getKey();
                List<SubscriptionDTO> ownerSubs = entry.getValue();

                AsyncJobStatusDTO job;
                if (CandlepinMode.isStandalone()) {
                    File manifest = new ExportGenerator()
                        .addSubscriptions(ownerSubs)
                        .export();

                    job = this.adminClient.owners()
                        .importManifestAsync(ownerKey, List.of(), manifest);
                }
                else {
                    ownerSubs.forEach(subscription -> this.adminClient.hosted()
                        .createSubscription(subscription, true));

                    job = this.adminClient.owners()
                        .refreshPools(ownerKey, false);
                }

                assertThatJob(job)
                    .isNotNull()
                    .terminates(this.adminClient)
                    .isFinished();
            }
        }

        @BeforeAll
        public void setup() throws Exception {
            // Ensure we have our required test extensions or we'll be very broken...
            this.checkRequiredExtensions();

            this.adminClient = ApiClients.admin();

            this.owners = List.of(
                this.createOwner("owner1"),
                this.createOwner("owner2"),
                this.createOwner("owner3"));

            this.productMap = new HashMap<>();

            List<SubscriptionDTO> subscriptions = new ArrayList<>();

            // Dummy org we use for creating a bunch of future pools for our global products. We'll also
            // create per-org subscriptions for these products later. Also note that these will never
            // be fully resolved.
            OwnerDTO globalOwner = this.createOwner("global");

            List<ProductDTO> globalProducts = new ArrayList<>();
            for (int i = 1; i <= 3; ++i) {
                ProductDTO gprod = new ProductDTO()
                    .id("g-prod-" + i)
                    .name("global product " + i);

                subscriptions.add(this.createSubscription(globalOwner, gprod, false));
                globalProducts.add(gprod);
                this.mapProductInfo(gprod, null, null);
            }

            for (int oidx = 1; oidx <= owners.size(); ++oidx) {
                OwnerDTO owner = owners.get(oidx - 1);

                List<ProductDTO> ownerProducts = new ArrayList<>();
                for (int i = 1; i <= 2; ++i) {
                    ProductDTO cprod = new ProductDTO()
                        .id(String.format("o%d-prod-%d", oidx, i))
                        .name(String.format("%s product %d", owner.getKey(), i));

                    cprod = this.adminClient.ownerProducts()
                        .createProduct(owner.getKey(), cprod);

                    ownerProducts.add(cprod);
                    this.mapProductInfo(cprod, owner, null);
                }

                // Create an active and inactive global subscription for this org
                subscriptions.add(this.createSubscription(owner, globalProducts.get(0), true));
                subscriptions.add(this.createSubscription(owner, globalProducts.get(1), false));
                this.mapProductInfo(globalProducts.get(0), null, owner);

                // Create an active and inactive custom subscription
                this.createPool(owner, ownerProducts.get(0), true);
                this.createPool(owner, ownerProducts.get(1), false);
                this.mapProductInfo(ownerProducts.get(0), owner, owner);
            }

            this.commitGlobalSubscriptions(subscriptions);
        }

        // Impl note:
        // Since we cannot depend on the global state being prestine from run to run (or even test to
        // test), we cannot use exact matching on our output as extraneous products from previous test
        // runs or otherwise pre-existing data may show up in the result set. Instead, these tests will
        // verify our expected products are present, and unexpected products from the test data are not.

        private List<String> getUnexpectedIds(List<String> expectedIds) {
            return this.productMap.values()
                .stream()
                .map(ProductInfo::dto)
                .map(ProductDTO::getId)
                .filter(Predicate.not(expectedIds::contains))
                .toList();
        }

        @Test
        public void shouldAllowQueryingWithoutAnyFilters() {
            List<ProductDTO> output = this.adminClient.products()
                .getProducts(null, null, null, INCLUSION_INCLUDE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ProductDTO::getId)
                .containsAll(this.productMap.keySet());
        }

        @Test
        public void shouldDefaultToActiveExclusiveCustomInclusive() {
            List<String> expectedPids = this.productMap.values()
                .stream()
                .filter(ProductInfo::active)
                .map(ProductInfo::id)
                .toList();

            List<ProductDTO> output = this.adminClient.products()
                .getProducts(null, null, null, null, null);

            assertThat(output)
                .isNotNull()
                .map(ProductDTO::getId)
                .containsAll(expectedPids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedPids));
        }

        @Test
        public void shouldAllowFilteringOnOwnerKeys() {
            List<String> ownerKeys = List.of(
                this.owners.get(0).getKey(),
                this.owners.get(1).getKey());

            Predicate<ProductInfo> ownerMatchPredicate = pinfo -> pinfo.owner() == null ||
                ownerKeys.contains(pinfo.owner().getKey());

            List<String> expectedPids = this.productMap.values()
                .stream()
                .filter(ownerMatchPredicate)
                .map(ProductInfo::id)
                .toList();

            List<ProductDTO> output = this.adminClient.products()
                .getProducts(ownerKeys, null, null, INCLUSION_INCLUDE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ProductDTO::getId)
                .containsAll(expectedPids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedPids));
        }

        @ParameterizedTest
        @ValueSource(strings = { "invalid_owner" })
        @NullAndEmptySource
        public void shouldFailWithInvalidOwners(String ownerKey) {
            List<String> ownerKeys = Arrays.asList(ownerKey);

            assertNotFound(() -> this.adminClient.products().getProducts(ownerKeys, null, null, null, null));
        }

        @Test
        public void shouldAllowFilteringOnIDs() {
            // Randomly seeded for consistency; seed itself chosen randomly
            Random rand = new Random(58258);

            List<String> pids = this.productMap.values()
                .stream()
                .map(ProductInfo::id)
                .sorted()
                .filter(elem -> rand.nextBoolean())
                .toList();

            List<ProductDTO> output = this.adminClient.products()
                .getProducts(null, pids, null, INCLUSION_INCLUDE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ProductDTO::getId)
                .containsAll(pids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(pids));
        }

        @ParameterizedTest
        @ValueSource(strings = { "invalid_id" })
        @NullAndEmptySource
        public void shouldAllowFilteringOnInvalidIds(String pid) {
            // This test verifies that invalid IDs are "allowed", but they won't match on anything

            ProductDTO expected = this.productMap.values()
                .stream()
                .findAny()
                .map(ProductInfo::dto)
                .get();

            List<String> expectedPids = List.of(expected.getId());

            List<String> pids = Arrays.asList(expected.getId(), pid);

            List<ProductDTO> output = this.adminClient.products()
                .getProducts(null, pids, null, INCLUSION_INCLUDE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ProductDTO::getId)
                .containsAll(expectedPids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedPids));
        }

        @Test
        public void shouldAllowFilteringOnName() {
            Random rand = new Random(55851);

            List<String> names = this.productMap.values()
                .stream()
                .sorted(Comparator.comparing(ProductInfo::id))
                .filter(elem -> rand.nextBoolean())
                .map(pinfo -> pinfo.dto().getName())
                .toList();

            List<String> expectedPids = this.productMap.values()
                .stream()
                .filter(pinfo -> names.contains(pinfo.dto().getName()))
                .map(ProductInfo::id)
                .toList();

            List<ProductDTO> output = this.adminClient.products()
                .getProducts(null, null, names, INCLUSION_INCLUDE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ProductDTO::getId)
                .containsAll(expectedPids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedPids));
        }

        @ParameterizedTest
        @ValueSource(strings = { "invalid_name" })
        @NullAndEmptySource
        public void shouldAllowFilteringOnInvalidNames(String name) {
            // This test verifies that invalid IDs are "allowed", but they won't match on anything

            ProductDTO expected = this.productMap.values()
                .stream()
                .findAny()
                .map(ProductInfo::dto)
                .get();

            List<String> expectedPids = List.of(expected.getId());

            List<String> names = Arrays.asList(expected.getName(), name);

            List<ProductDTO> output = this.adminClient.products()
                .getProducts(null, null, names, INCLUSION_INCLUDE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ProductDTO::getId)
                .containsAll(expectedPids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedPids));
        }

        @Test
        public void shouldAllowFilteringWithActiveIncluded() {
            // This is effectively the same as no filter -- we expect everything back

            List<ProductDTO> output = this.adminClient.products()
                .getProducts(null, null, null, INCLUSION_INCLUDE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ProductDTO::getId)
                .containsAll(this.productMap.keySet());
        }

        @Test
        public void shouldAllowFilteringWithActiveExcluded() {
            List<String> expectedPids = this.productMap.values()
                .stream()
                .filter(pinfo -> !pinfo.active())
                .map(ProductInfo::id)
                .toList();

            List<ProductDTO> output = this.adminClient.products()
                .getProducts(null, null, null, INCLUSION_EXCLUDE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ProductDTO::getId)
                .containsAll(expectedPids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedPids));
        }

        @Test
        public void shouldAllowFilteringWithActiveExclusive() {
            List<String> expectedPids = this.productMap.values()
                .stream()
                .filter(ProductInfo::active)
                .map(ProductInfo::id)
                .toList();

            List<ProductDTO> output = this.adminClient.products()
                .getProducts(null, null, null, INCLUSION_EXCLUSIVE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ProductDTO::getId)
                .containsAll(expectedPids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedPids));
        }

        @Test
        public void shouldErrorWithInvalidActiveInclusion() {
            assertBadRequest(() -> this.adminClient.products()
                .getProducts(null, null, null, "invalid_type", INCLUSION_INCLUDE));
        }

        @Test
        public void shouldAllowFilteringWithCustomIncluded() {
            // This is effectively the same as no filter -- we expect everything back
            List<ProductDTO> output = this.adminClient.products()
                .getProducts(null, null, null, INCLUSION_INCLUDE, INCLUSION_INCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ProductDTO::getId)
                .containsAll(this.productMap.keySet());
        }

        @Test
        public void shouldAllowFilteringWithCustomExcluded() {
            List<String> expectedPids = this.productMap.values()
                .stream()
                .filter(pinfo -> !pinfo.custom())
                .map(ProductInfo::id)
                .toList();

            List<ProductDTO> output = this.adminClient.products()
                .getProducts(null, null, null, INCLUSION_INCLUDE, INCLUSION_EXCLUDE);

            assertThat(output)
                .isNotNull()
                .map(ProductDTO::getId)
                .containsAll(expectedPids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedPids));
        }

        @Test
        public void shouldAllowFilteringWithCustomExclusive() {
            List<String> expectedPids = this.productMap.values()
                .stream()
                .filter(ProductInfo::custom)
                .map(ProductInfo::id)
                .toList();

            List<ProductDTO> output = this.adminClient.products()
                .getProducts(null, null, null, INCLUSION_INCLUDE, INCLUSION_EXCLUSIVE);

            assertThat(output)
                .isNotNull()
                .map(ProductDTO::getId)
                .containsAll(expectedPids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedPids));
        }

        @Test
        public void shouldErrorWithInvalidCustomInclusion() {
            assertBadRequest(() -> this.adminClient.products()
                .getProducts(null, null, null, INCLUSION_INCLUDE, "invalid_type"));
        }

        @Test
        public void shouldAllowQueryingWithMultipleFilters() {
            Random rand = new Random(14023);

            // Hand pick a product to ensure that we have *something* that comes out of the filter, should
            // our random selection process not have any intersection.
            ProductInfo selected = this.productMap.values()
                .stream()
                .filter(ProductInfo::active)
                .filter(ProductInfo::custom)
                .findAny()
                .get();

            List<String> ownerKeys = this.owners.stream()
                .filter(elem -> rand.nextBoolean())
                .map(OwnerDTO::getKey)
                .collect(Collectors.toCollection(ArrayList::new));

            List<String> pids = this.productMap.values()
                .stream()
                .map(ProductInfo::id)
                .sorted()
                .filter(elem -> rand.nextBoolean())
                .collect(Collectors.toCollection(ArrayList::new));

            List<String> names = this.productMap.values()
                .stream()
                .sorted(Comparator.comparing(ProductInfo::id))
                .filter(elem -> rand.nextBoolean())
                .map(pinfo -> pinfo.dto().getName())
                .collect(Collectors.toCollection(ArrayList::new));

            ownerKeys.add(selected.owner().getKey());
            pids.add(selected.dto().getId());
            names.add(selected.dto().getName());

            List<String> expectedPids = this.productMap.values()
                .stream()
                .filter(ProductInfo::active)
                .filter(ProductInfo::custom)
                .filter(pinfo -> pinfo.activeOwners().stream().anyMatch(ownerKeys::contains))
                .filter(pinfo -> pids.contains(pinfo.dto().getId()))
                .filter(pinfo -> names.contains(pinfo.dto().getName()))
                .map(ProductInfo::id)
                .toList();

            assertThat(expectedPids)
                .isNotEmpty();

            List<ProductDTO> output = this.adminClient.products()
                .getProducts(ownerKeys, pids, names, INCLUSION_EXCLUSIVE, INCLUSION_EXCLUSIVE);

            assertThat(output)
                .isNotNull()
                .map(ProductDTO::getId)
                .containsAll(expectedPids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedPids));
        }

        @ParameterizedTest
        @ValueSource(ints = { 1, 2, 3, 6, 10, 1000 })
        public void shouldAllowQueryingInPages(int pageSize) {
            List<String> ownerKeys = this.owners.stream()
                .map(OwnerDTO::getKey)
                .toList();

            List<String> expectedPids = this.productMap.values()
                .stream()
                .filter(ProductInfo::custom)
                .map(ProductInfo::id)
                .toList();

            List<String> received = new ArrayList<>();

            int page = 0;
            while (true) {
                Response response = Request.from(this.adminClient)
                    .setPath(QUERY_PATH)
                    .addQueryParam("owner", ownerKeys)
                    .addQueryParam("page", String.valueOf(++page))
                    .addQueryParam("per_page", String.valueOf(pageSize))
                    .addQueryParam("active", "include")
                    .addQueryParam("custom", "exclusive")
                    .execute();

                assertEquals(200, response.getCode());

                List<ProductDTO> output = response.deserialize(new TypeReference<List<ProductDTO>>() {});
                assertNotNull(output);

                if (output.isEmpty()) {
                    break;
                }

                output.stream()
                    .map(ProductDTO::getId)
                    .sequential()
                    .forEach(received::add);
            }

            assertThat(received)
                .containsAll(expectedPids)
                .doesNotContainAnyElementsOf(this.getUnexpectedIds(expectedPids));
        }

        @ParameterizedTest
        @ValueSource(ints = { 0, -1, -100 })
        public void shouldFailWithInvalidPage(int page) {
            Response response = Request.from(this.adminClient)
                .setPath(QUERY_PATH)
                .addQueryParam("page", String.valueOf(page))
                .execute();

            assertEquals(400, response.getCode());
        }

        @ParameterizedTest
        @ValueSource(ints = { 0, -1, -100 })
        public void shouldFailWithInvalidPageSize(int pageSize) {
            Response response = Request.from(this.adminClient)
                .setPath(QUERY_PATH)
                .addQueryParam("per_page", String.valueOf(pageSize))
                .execute();

            assertEquals(400, response.getCode());
        }

        @ParameterizedTest
        @ValueSource(strings = { "id", "name", "uuid" })
        public void shouldAllowQueryingWithAscendingOrderedOutput(String field) {
            List<String> ownerKeys = this.owners.stream()
                .map(OwnerDTO::getKey)
                .toList();

            Map<String, Comparator<ProductDTO>> comparatorMap = Map.of(
                "id", Comparator.comparing(ProductDTO::getId),
                "name", Comparator.comparing(ProductDTO::getName),
                "uuid", Comparator.comparing(ProductDTO::getUuid));

            List<ProductDTO> products = this.adminClient.products()
                .getProducts(ownerKeys, null, null, INCLUSION_INCLUDE, INCLUSION_EXCLUSIVE);

            List<String> expectedPids = products.stream()
                .sorted(comparatorMap.get(field))
                .map(ProductDTO::getId)
                .toList();

            Response response = Request.from(this.adminClient)
                .setPath(QUERY_PATH)
                .addQueryParam("owner", ownerKeys)
                .addQueryParam("sort_by", field)
                .addQueryParam("order", "asc")
                .addQueryParam("active", INCLUSION_INCLUDE)
                .addQueryParam("custom", INCLUSION_EXCLUSIVE)
                .execute();

            assertEquals(200, response.getCode());

            List<ProductDTO> output = response.deserialize(new TypeReference<List<ProductDTO>>() {});

            assertThat(output)
                .isNotNull()
                .map(ProductDTO::getId)
                .containsExactlyElementsOf(expectedPids); // this must be an ordered check
        }

        @ParameterizedTest
        @ValueSource(strings = { "id", "name", "uuid" })
        public void shouldAllowQueryingWithDescendingOrderedOutput(String field) {
            List<String> ownerKeys = this.owners.stream()
                .map(OwnerDTO::getKey)
                .toList();

            Map<String, Comparator<ProductDTO>> comparatorMap = Map.of(
                "id", Comparator.comparing(ProductDTO::getId),
                "name", Comparator.comparing(ProductDTO::getName),
                "uuid", Comparator.comparing(ProductDTO::getUuid));

            List<ProductDTO> products = this.adminClient.products()
                .getProducts(ownerKeys, null, null, INCLUSION_INCLUDE, INCLUSION_EXCLUSIVE);

            List<String> expectedPids = products.stream()
                .sorted(comparatorMap.get(field).reversed())
                .map(ProductDTO::getId)
                .toList();

            Response response = Request.from(this.adminClient)
                .setPath(QUERY_PATH)
                .addQueryParam("owner", ownerKeys)
                .addQueryParam("sort_by", field)
                .addQueryParam("order", "desc")
                .addQueryParam("active", INCLUSION_INCLUDE)
                .addQueryParam("custom", INCLUSION_EXCLUSIVE)
                .execute();

            assertEquals(200, response.getCode());

            List<ProductDTO> output = response.deserialize(new TypeReference<List<ProductDTO>>() {});

            assertThat(output)
                .isNotNull()
                .map(ProductDTO::getId)
                .containsExactlyElementsOf(expectedPids); // this must be an ordered check
        }

        @Test
        public void shouldFailWithInvalidOrderField() {
            Response response = Request.from(this.adminClient)
                .setPath(QUERY_PATH)
                .addQueryParam("sort_by", "invalid field")
                .execute();

            assertEquals(400, response.getCode());
        }

        @Test
        public void shouldFailWithInvalidOrderDirection() {
            Response response = Request.from(this.adminClient)
                .setPath(QUERY_PATH)
                .addQueryParam("order", "invalid order")
                .execute();

            assertEquals(400, response.getCode());
        }

        // These tests verify the definition of "active" is properly implemented, ensuring "active" is
        // defined as a product which is attached to a pool which has started and has not expired, or
        // attached to another active product (recursively).
        //
        // This definition is recursive in nature, so the effect is that it should consider any product
        // that is a descendant of a product attached to a non-future pool that hasn't yet expired.

        @Test
        public void shouldOnlySelectActiveProductsFromActivePools() {
            // "active" only considers pools which have started but have not yet expired -- that is:
            // (start time < now() < end time)
            OwnerDTO owner = this.createOwner("test_org");

            ProductDTO prod1 = this.adminClient.ownerProducts()
                .createProduct(owner.getKey(), Products.random());
            ProductDTO prod2 = this.adminClient.ownerProducts()
                .createProduct(owner.getKey(), Products.random());
            ProductDTO prod3 = this.adminClient.ownerProducts()
                .createProduct(owner.getKey(), Products.random());

            OffsetDateTime now = Instant.now()
                .atOffset(ZoneOffset.UTC);

            // Create three pools: expired, current (active), future
            PoolDTO pool1 = Pools.random(prod1)
                .startDate(now.minus(7, ChronoUnit.DAYS))
                .endDate(now.minus(3, ChronoUnit.DAYS));
            PoolDTO pool2 = Pools.random(prod2)
                .startDate(now.minus(3, ChronoUnit.DAYS))
                .endDate(now.plus(3, ChronoUnit.DAYS));
            PoolDTO pool3 = Pools.random(prod3)
                .startDate(now.plus(3, ChronoUnit.DAYS))
                .endDate(now.plus(7, ChronoUnit.DAYS));

            this.adminClient.owners().createPool(owner.getKey(), pool1);
            this.adminClient.owners().createPool(owner.getKey(), pool2);
            this.adminClient.owners().createPool(owner.getKey(), pool3);

            // Active = exclusive should only find the active pool; future and expired pools should be omitted
            List<ProductDTO> output = this.adminClient.products()
                .getProducts(List.of(owner.getKey()), null, null, INCLUSION_EXCLUSIVE, INCLUSION_EXCLUSIVE);

            assertThat(output)
                .isNotNull()
                .map(ProductDTO::getId)
                .singleElement()
                .isEqualTo(prod2.getId());
        }

        @Test
        public void shouldAlsoIncludeDescendantProductsOfActiveProducts() {
            // "active" includes descendants of products attached to an active pool
            OwnerDTO owner = this.createOwner("test_org");

            List<ProductDTO> products = new ArrayList<>();
            for (int i = 0; i < 20; ++i) {
                ProductDTO product = new ProductDTO()
                    .id("p" + i)
                    .name("product " + i);

                products.add(product);
            }

            /*
            pool -> prod - p0
                        derived - p1
                            provided - p2
                            provided - p3
                                provided - p4
                        provided - p5
                        provided - p6

            pool -> prod - p7
                        derived - p8*
                        provided - p9

            pool -> prod - p8*
                        provided - p10
                            provided - p11
                        provided - p12
                            provided - p13

                    prod - p14
                        derived - p15
                            provided - p16

                    prod - p17
                        provided - p18

            pool -> prod - p19
                    prod - p20
            */

            products.get(0).setDerivedProduct(products.get(1));
            products.get(0).addProvidedProductsItem(products.get(5));
            products.get(0).addProvidedProductsItem(products.get(6));

            products.get(1).addProvidedProductsItem(products.get(2));
            products.get(1).addProvidedProductsItem(products.get(3));

            products.get(3).addProvidedProductsItem(products.get(4));

            products.get(7).setDerivedProduct(products.get(8));
            products.get(7).addProvidedProductsItem(products.get(9));

            products.get(8).addProvidedProductsItem(products.get(10));
            products.get(8).addProvidedProductsItem(products.get(12));

            products.get(10).addProvidedProductsItem(products.get(11));

            products.get(12).addProvidedProductsItem(products.get(13));

            products.get(14).setDerivedProduct(products.get(15));

            products.get(15).setDerivedProduct(products.get(16));

            products.get(17).setDerivedProduct(products.get(18));

            // persist the products in reverse order so we don't hit any linkage errors
            for (int i = products.size() - 1; i >= 0; --i) {
                this.adminClient.ownerProducts().createProduct(owner.getKey(), products.get(i));
            }

            // create some pools to link to our product tree
            this.adminClient.owners().createPool(owner.getKey(), Pools.random(products.get(0)));
            this.adminClient.owners().createPool(owner.getKey(), Pools.random(products.get(7)));
            this.adminClient.owners().createPool(owner.getKey(), Pools.random(products.get(8)));
            this.adminClient.owners().createPool(owner.getKey(), Pools.random(products.get(19)));

            List<String> expectedPids = List.of("p0", "p1", "p2", "p3", "p4", "p5", "p6", "p7", "p8", "p9",
                "p10", "p11", "p12", "p13", "p19");

            List<ProductDTO> output = this.adminClient.products()
                .getProducts(List.of(owner.getKey()), null, null, INCLUSION_EXCLUSIVE, INCLUSION_EXCLUSIVE);

            assertThat(output)
                .isNotNull()
                .map(ProductDTO::getId)
                .containsExactlyInAnyOrderElementsOf(expectedPids);
        }
    }
}
