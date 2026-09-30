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
package org.candlepin.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.config.DatabaseConfigFactory;
import org.candlepin.dto.api.server.v1.ActivationKeyDTO;
import org.candlepin.dto.api.server.v1.ActivationKeyPoolDTO;
import org.candlepin.dto.api.server.v1.ActivationKeyProductDTO;
import org.candlepin.dto.api.server.v1.ContentOverrideDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Release;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyContentOverride;
import org.candlepin.test.DatabaseTestFixture;

import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;


/**
 * Tests activation key listing and the number of queries needed to build its response.
 */
public class OwnerActivationKeyListingTest extends DatabaseTestFixture {
    private static final Logger log = LoggerFactory.getLogger(OwnerActivationKeyListingTest.class);

    @ParameterizedTest
    @CsvSource({"1, 15000, 6", "25, 15000, 6", "2711, 15000, 6", "25, 10, 14"})
    public void testListKeysWithBoundedQueries(int keyCount, int blockSize, long maxStatements) {
        this.config.setProperty(DatabaseConfigFactory.IN_OPERATOR_BLOCK_SIZE, Integer.toString(blockSize));
        Owner owner = this.createOwner();
        for (int i = 0; i < keyCount; ++i) {
            this.activationKeyCurator.create(new ActivationKey("key-" + i, owner), false);
        }

        List<ActivationKeyDTO> keys = this.listKeys(owner.getKey(), null, maxStatements);

        assertThat(keys).hasSize(keyCount);
        assertThat(keys).extracting(ActivationKeyDTO::getId).doesNotHaveDuplicates();
        assertThat(keys).allSatisfy(key -> {
            assertThat(key.getOwner().getKey()).isEqualTo(owner.getKey());
            assertThat(key.getProducts()).isEmpty();
            assertThat(key.getAddOns()).isEmpty();
            assertThat(key.getPools()).isEmpty();
            assertThat(key.getContentOverrides()).isEmpty();
        });
    }

    @Test
    public void testListKeysPreservesCollectionsAndOwnerIsolation() {
        Owner owner = this.createOwner();
        Pool firstPool = this.createPool(owner, this.createProduct());
        Pool secondPool = this.createPool(owner, this.createProduct());
        ActivationKey populated = new ActivationKey("populated", owner)
            .setDescription("Activation key with all collections")
            .setReleaseVer(new Release("9.4"))
            .setServiceLevel("Premium")
            .setUsage("Production")
            .setRole("Server")
            .setAutoAttach(false)
            .setProductIds(Set.of("product-1", "product-2"))
            .setAddOns(Set.of("addon-1", "addon-2"))
            .addPool(firstPool, 2L)
            .addPool(secondPool, 3L)
            .addContentOverride(new ActivationKeyContentOverride()
                .setContentLabel("repo-1")
                .setName("enabled")
                .setValue("1"))
            .addContentOverride(new ActivationKeyContentOverride()
                .setContentLabel("repo-2")
                .setName("enabled")
                .setValue("0"));
        this.activationKeyCurator.create(populated);
        this.activationKeyCurator.create(new ActivationKey("empty", owner));
        this.activationKeyCurator.create(new ActivationKey("populated", this.createOwner())
            .setProductIds(Set.of("foreign-product")));

        List<ActivationKeyDTO> keys = this.listKeys(owner.getKey(), null, 6);

        assertThat(keys).extracting(ActivationKeyDTO::getName).containsExactlyInAnyOrder("populated", "empty");
        ActivationKeyDTO actual = keys.stream()
            .filter(key -> "populated".equals(key.getName()))
            .findFirst()
            .orElseThrow();
        assertThat(actual.getId()).isEqualTo(populated.getId());
        assertThat(actual.getOwner().getKey()).isEqualTo(owner.getKey());
        assertThat(actual.getDescription()).isEqualTo("Activation key with all collections");
        assertThat(actual.getReleaseVer().getReleaseVer()).isEqualTo("9.4");
        assertThat(actual.getServiceLevel()).isEqualTo("Premium");
        assertThat(actual.getUsage()).isEqualTo("Production");
        assertThat(actual.getRole()).isEqualTo("Server");
        assertThat(actual.getAutoAttach()).isFalse();
        assertThat(actual.getCreated()).isNotNull();
        assertThat(actual.getUpdated()).isNotNull();
        assertThat(actual.getProducts()).extracting(ActivationKeyProductDTO::getProductId)
            .containsExactlyInAnyOrder("product-1", "product-2");
        assertThat(actual.getAddOns()).containsExactlyInAnyOrder("addon-1", "addon-2");
        assertThat(actual.getPools()).extracting(ActivationKeyPoolDTO::getPoolId, ActivationKeyPoolDTO::getQuantity)
            .containsExactlyInAnyOrder(tuple(firstPool.getId(), 2L), tuple(secondPool.getId(), 3L));
        assertThat(actual.getContentOverrides()).extracting(ContentOverrideDTO::getContentLabel,
            ContentOverrideDTO::getName, ContentOverrideDTO::getValue)
            .containsExactlyInAnyOrder(tuple("repo-1", "enabled", "1"), tuple("repo-2", "enabled", "0"));
        assertThat(keys.stream()
            .filter(key -> "empty".equals(key.getName()))
            .findFirst()
            .orElseThrow())
            .satisfies(key -> {
                assertThat(key.getProducts()).isEmpty();
                assertThat(key.getAddOns()).isEmpty();
                assertThat(key.getPools()).isEmpty();
                assertThat(key.getContentOverrides()).isEmpty();
            });
    }

    @Test
    public void testListKeysFiltersByExactNameWithinOwner() {
        Owner owner = this.createOwner();
        this.activationKeyCurator.create(new ActivationKey("match", owner)
            .setProductIds(Set.of("matching-product")));
        this.activationKeyCurator.create(new ActivationKey("match-extra", owner));
        this.activationKeyCurator.create(new ActivationKey("match", this.createOwner())
            .setProductIds(Set.of("foreign-product")));

        List<ActivationKeyDTO> keys = this.listKeys(owner.getKey(), "match", 6);

        assertThat(keys).singleElement().satisfies(key -> {
            assertThat(key.getName()).isEqualTo("match");
            assertThat(key.getOwner().getKey()).isEqualTo(owner.getKey());
            assertThat(key.getProducts()).extracting(ActivationKeyProductDTO::getProductId)
                .containsExactly("matching-product");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", ""})
    public void testListKeysReturnsEmptyForUnmatchedName(String name) {
        Owner owner = this.createOwner();
        this.activationKeyCurator.create(new ActivationKey("existing", owner));

        assertThat(this.listKeys(owner.getKey(), name, 2)).isEmpty();
    }

    @Test
    public void testListKeysReturnsEmptyForOwnerWithoutKeys() {
        Owner owner = this.createOwner();

        assertThat(this.listKeys(owner.getKey(), null, 2)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    public void testListKeysRejectsInvalidOwnerKey(String ownerKey) {
        OwnerResource resource = this.injector.getInstance(OwnerResource.class);

        assertThrows(BadRequestException.class, () -> resource.ownerActivationKeys(ownerKey, null).toList());
    }

    @Test
    public void testListKeysRejectsUnknownOwner() {
        OwnerResource resource = this.injector.getInstance(OwnerResource.class);

        assertThrows(NotFoundException.class, () -> resource.ownerActivationKeys("missing-owner", null).toList());
    }

    private List<ActivationKeyDTO> listKeys(String ownerKey, String name, long maxStatements) {
        this.getEntityManager().flush();
        this.getEntityManager().clear();
        OwnerResource resource = this.injector.getInstance(OwnerResource.class);
        Statistics statistics = this.getCurrentSession()
            .getSessionFactory()
            .getStatistics();
        boolean enabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        try {
            long start = System.nanoTime();
            List<ActivationKeyDTO> keys = resource.ownerActivationKeys(ownerKey, name).toList();
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;
            long statements = statistics.getPrepareStatementCount();
            log.info("Listed {} activation keys using {} SQL statements in {} ms",
                keys.size(), statements, elapsedMillis);
            assertThat(statements).as("SQL statements for %s activation keys", keys.size())
                .isLessThanOrEqualTo(maxStatements);
            return keys;
        }
        finally {
            statistics.setStatisticsEnabled(enabled);
        }
    }
}
