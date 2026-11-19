/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
package org.candlepin.spec.activationkey;

import static org.assertj.core.api.Assertions.assertThat;

import org.candlepin.dto.api.client.v1.ActivationKeyDTO;
import org.candlepin.dto.api.client.v1.ActivationKeyProductDTO;
import org.candlepin.dto.api.client.v1.ContentOverrideDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.ActivationKeys;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Products;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Performance spec tests for the GET owners/{owner_key}/activation_keys endpoint.
 *
 * <p>Context: CANDLEPIN-1267 made ActivationKeyCurator.listByOwner eagerly fetch the
 * activation key element collections to avoid an N+1 problem during DTO translation.
 * These tests measure the endpoint against a dataset modeled on production Hosted data
 * and are used to verify the performance of that change.</p>
 *
 * <p>The dataset consists of activation keys for a single owner, where 50 keys carry
 * 200 to 5200 content overrides (incremental), a different 50 keys carry 20 to 1000
 * product IDs (incremental), and the remaining keys carry a small number of each.</p>
 *
 * <p>These tests are excluded from the standard spec suite via the "performance" tag
 * and are run with the dedicated gradle task: {@code ./gradlew perf}. Seeding the
 * dataset takes several minutes; the measured operation is read-only, so the data is
 * seeded once and shared by the tests in the nested class. The dataset size can be
 * scaled down for smoke runs via the {@code perf.test.total.keys} and
 * {@code perf.test.large.keys} system properties.</p>
 */
@SpecTest
@Tag("performance")
public class ActivationKeyPerformanceSpecTest {

    private static final int TOTAL_KEYS = intProperty("perf.test.total.keys", 3000);
    private static final int LARGE_KEY_COUNT = intProperty("perf.test.large.keys", 50);
    private static final int MIN_CONTENT_OVERRIDES = 200;
    private static final int MAX_CONTENT_OVERRIDES = 5200;
    private static final int MIN_PRODUCT_IDS = 20;
    private static final int MAX_PRODUCT_IDS = 1000;
    private static final int SMALL_COLLECTION_SIZE = 5;
    private static final int WARMUP_RUNS = 2;
    private static final int MEASURED_RUNS = 5;

    private static final String OVERRIDE_KEY_PREFIX = "perf-act-key-overrides-";
    private static final String PRODUCT_KEY_PREFIX = "perf-act-key-products-";
    private static final String SMALL_KEY_PREFIX = "perf-act-key-small-";

    private static int intProperty(String key, int defaultValue) {
        String value = System.getProperty(key);
        return value == null ? defaultValue : Integer.parseInt(value);
    }

    /**
     * The collection size for the activation key at the provided index within the set of
     * large keys, linearly scaled between the provided bounds.
     */
    private static int scaledSize(int index, int min, int max) {
        if (LARGE_KEY_COUNT <= 1) {
            return max;
        }

        return min + (max - min) * index / (LARGE_KEY_COUNT - 1);
    }

    @Nested
    @TestInstance(Lifecycle.PER_CLASS)
    class OwnerActivationKeyListPerformance {

        private final Logger log = LoggerFactory.getLogger(OwnerActivationKeyListPerformance.class);

        private final ApiClient adminClient = ApiClients.admin();
        private OwnerDTO owner;

        /**
         * Seeds the activation key dataset for a fresh owner. Seeding happens once; the
         * tests only read the data afterwards.
         */
        @BeforeAll
        void seedActivationKeys() {
            this.owner = this.adminClient.owners().createOwner(Owners.random());
            List<String> productIds = this.createProducts(MAX_PRODUCT_IDS);

            int seeded = 0;
            long startNanos = System.nanoTime();

            for (int i = 0; i < LARGE_KEY_COUNT; i++) {
                int overrideCount = scaledSize(i, MIN_CONTENT_OVERRIDES, MAX_CONTENT_OVERRIDES);
                this.createKey(OVERRIDE_KEY_PREFIX + i, overrideCount, SMALL_COLLECTION_SIZE, productIds);
                this.logProgress(++seeded, startNanos);
            }

            for (int i = 0; i < LARGE_KEY_COUNT; i++) {
                int productCount = scaledSize(i, MIN_PRODUCT_IDS, MAX_PRODUCT_IDS);
                this.createKey(PRODUCT_KEY_PREFIX + i, SMALL_COLLECTION_SIZE, productCount, productIds);
                this.logProgress(++seeded, startNanos);
            }

            int remainingKeys = TOTAL_KEYS - 2 * LARGE_KEY_COUNT;
            for (int i = 0; i < remainingKeys; i++) {
                this.createKey(SMALL_KEY_PREFIX + i, SMALL_COLLECTION_SIZE, SMALL_COLLECTION_SIZE, productIds);
                this.logProgress(++seeded, startNanos);
            }

            this.log.info("Seeding complete: {} activation keys for owner {} in {} s", seeded,
                this.owner.getKey(), TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startNanos));
        }

        private void logProgress(int seeded, long startNanos) {
            if (seeded % 100 == 0 || seeded == TOTAL_KEYS) {
                this.log.info("Seeded {} of {} activation keys ({} s elapsed)", seeded, TOTAL_KEYS,
                    TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startNanos));
            }
        }

        private List<String> createProducts(int count) {
            List<String> productIds = new ArrayList<>(count);

            for (int i = 0; i < count; i++) {
                ProductDTO product = this.adminClient.ownerProducts()
                    .createProduct(this.owner.getKey(), Products.random());
                productIds.add(product.getId());
            }

            return productIds;
        }

        private void createKey(String name, int overrideCount, int productCount, List<String> productIds) {
            ActivationKeyDTO dto = ActivationKeys.random(this.owner)
                .name(name);

            for (int i = 0; i < overrideCount; i++) {
                dto.addContentOverridesItem(new ContentOverrideDTO()
                    .contentLabel("perf-content-label-" + i)
                    .name("perf-override-name-" + i)
                    .value("1"));
            }

            for (int i = 0; i < productCount; i++) {
                dto.addProductsItem(new ActivationKeyProductDTO()
                    .productId(productIds.get(i)));
            }

            this.adminClient.owners().createActivationKey(this.owner.getKey(), dto);
        }

        @Test
        void ownerActivationKeysWithMixedCollectionSizes() {
            List<Long> timingsMillis = new ArrayList<>(MEASURED_RUNS);
            List<ActivationKeyDTO> keys = List.of();

            for (int i = 0; i < WARMUP_RUNS; i++) {
                keys = this.adminClient.owners().ownerActivationKeys(this.owner.getKey(), null);
                assertThat(keys).hasSize(TOTAL_KEYS);
            }

            for (int i = 0; i < MEASURED_RUNS; i++) {
                long startNanos = System.nanoTime();
                keys = this.adminClient.owners().ownerActivationKeys(this.owner.getKey(), null);
                timingsMillis.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
            }

            this.assertSeededData(keys);
            this.logSummary(timingsMillis);
        }

        private void assertSeededData(List<ActivationKeyDTO> keys) {
            assertThat(keys).hasSize(TOTAL_KEYS);

            int lastLargeKeyIndex = LARGE_KEY_COUNT - 1;
            String expectedMaxOverrideKeyName = OVERRIDE_KEY_PREFIX + lastLargeKeyIndex;
            String expectedMaxProductKeyName = PRODUCT_KEY_PREFIX + lastLargeKeyIndex;

            ActivationKeyDTO maxOverrideKey = keys.stream()
                .filter(key -> expectedMaxOverrideKeyName.equals(key.getName()))
                .findFirst()
                .orElseThrow();
            assertThat(maxOverrideKey.getContentOverrides())
                .hasSize(scaledSize(lastLargeKeyIndex, MIN_CONTENT_OVERRIDES, MAX_CONTENT_OVERRIDES));

            ActivationKeyDTO maxProductKey = keys.stream()
                .filter(key -> expectedMaxProductKeyName.equals(key.getName()))
                .findFirst()
                .orElseThrow();
            assertThat(maxProductKey.getProducts())
                .hasSize(scaledSize(lastLargeKeyIndex, MIN_PRODUCT_IDS, MAX_PRODUCT_IDS));
        }

        private void logSummary(List<Long> timingsMillis) {
            List<Long> sorted = new ArrayList<>(timingsMillis);
            Collections.sort(sorted);

            long min = sorted.get(0);
            long median = sorted.get(sorted.size() / 2);
            long max = sorted.get(sorted.size() - 1);
            double mean = timingsMillis.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);

            this.log.info("PERF RESULT GET owners/{}/activation_keys: runs={} min={}ms median={}ms " +
                "mean={}ms max={}ms keys={}", this.owner.getKey(), timingsMillis.size(), min, median,
                String.format("%.1f", mean), max, TOTAL_KEYS);
        }
    }
}
