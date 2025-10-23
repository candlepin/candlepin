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
package org.candlepin.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;

import org.candlepin.config.DatabaseConfigFactory;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class RhsmApiCompatCuratorTest extends DatabaseTestFixture {

    @ParameterizedTest(name = "{displayName} {index}: {0} {1}")
    @NullAndEmptySource
    public void testGetConsumerEntitlementCountsWithNullOrEmptyConsumerUuids(List<String> consumerUuids) {
        List<ConsumerEntitlementCount> actual = this.rhsmApiCompatCurator
            .getConsumerEntitlementCounts(null, consumerUuids);

        assertThat(actual)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testGetConsumerEntitlementCounts() {
        Owner owner = this.createOwner();

        Consumer consumer1 = this.createConsumer(owner);
        Consumer consumer2 = this.createConsumer(owner);
        Consumer consumer3 = this.createConsumer(owner);

        Content content1 = this.createContent();
        Content content2 = this.createContent();
        Content content3 = this.createContent();

        Product product1 = new Product()
            .setId(TestUtil.randomString())
            .setName(TestUtil.randomString());
        product1.addContent(content1, true);
        product1.addContent(content2, false);

        this.createProduct(product1);

        Product product2 = new Product()
            .setId(TestUtil.randomString())
            .setName(TestUtil.randomString());
        product2.addContent(content2, true);
        product2.addContent(content3, false);

        this.createProduct(product2);

        Pool pool1 = TestUtil.createPool(owner, product1)
            .setQuantity(10L);
        pool1 = poolCurator.create(pool1);

        Pool pool2 = TestUtil.createPool(owner, product2)
            .setQuantity(10L);
        pool2 = poolCurator.create(pool2);

        Pool pool3 = TestUtil.createPool(owner, product1)
            .setQuantity(10L)
            .setContractNumber(pool1.getContractNumber())
            .setSubscriptionId(pool1.getSubscriptionId());
        pool3 = poolCurator.create(pool3);

        Entitlement ent1 = createEntitlementWithQuantity(owner, consumer1, pool1, 1);
        Entitlement ent2 = createEntitlementWithQuantity(owner, consumer2, pool2, 2);
        Entitlement ent3 = createEntitlementWithQuantity(owner, consumer1, pool3, 3);

        createEntitlementWithQuantity(owner, consumer3, pool2, 4);

        List<String> consumerUuids = List.of(consumer1.getUuid());
        List<String> consumerIds = List.of(consumer2.getId());

        List<ConsumerEntitlementCount> actual = this.rhsmApiCompatCurator
            .getConsumerEntitlementCounts(consumerIds, consumerUuids);

        // Consumer 1's entitlement count for entitlement 1 and entitlement 3 should be the sum of the two
        // quantities because the pools are for the same contract number, subscription ID, and product.

        List<ConsumerEntitlementCount> expected = List.of(
            new ConsumerEntitlementCount(consumer1.getId(),
                consumer1.getUuid(),
                pool1.getContractNumber(),
                pool1.getSubscriptionId(),
                product1.getId(),
                product1.getName(),
                Long.valueOf(ent1.getQuantity() + ent3.getQuantity())),
            new ConsumerEntitlementCount(consumer2.getId(),
                consumer2.getUuid(),
                pool2.getContractNumber(),
                pool2.getSubscriptionId(),
                product2.getId(),
                product2.getName(),
                Long.valueOf(ent2.getQuantity())));

        assertThat(actual)
            .isNotNull()
            .hasSize(2)
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    public void testGetConsumerEntitlementCountsWithOnlyConsumerIDs() {
        Owner owner = this.createOwner();

        Consumer consumer1 = this.createConsumer(owner);
        Consumer consumer2 = this.createConsumer(owner);

        Content content = this.createContent();

        Product product = new Product()
            .setId(TestUtil.randomString())
            .setName(TestUtil.randomString());
        product.addContent(content, true);

        this.createProduct(product);

        Pool pool = TestUtil.createPool(owner, product)
            .setQuantity(10L);
        pool = poolCurator.create(pool);

        Entitlement ent = createEntitlementWithQuantity(owner, consumer1, pool, 1);
        createEntitlementWithQuantity(owner, consumer2, pool, 1);

        List<ConsumerEntitlementCount> actual = this.rhsmApiCompatCurator
            .getConsumerEntitlementCounts(List.of(consumer1.getId()), null);

        assertThat(actual)
            .isNotNull()
            .isNotNull()
            .singleElement()
            .returns(consumer1.getId(), ConsumerEntitlementCount::id)
            .returns(consumer1.getUuid(), ConsumerEntitlementCount::uuid)
            .returns(pool.getContractNumber(), ConsumerEntitlementCount::contractNumber)
            .returns(pool.getSubscriptionId(), ConsumerEntitlementCount::subscriptionId)
            .returns(product.getId(), ConsumerEntitlementCount::productId)
            .returns(product.getName(), ConsumerEntitlementCount::productName)
            .returns(Long.valueOf(ent.getQuantity()), ConsumerEntitlementCount::quantity);
    }

    @Test
    public void testGetConsumerEntitlementCountsWithPoolWithNoSubscriptionId() {
        Owner owner = this.createOwner();

        Consumer consumer = this.createConsumer(owner);

        Content content = this.createContent();
        Product product = new Product()
            .setId(TestUtil.randomString())
            .setName(TestUtil.randomString());
        product.addContent(content, true);

        this.createProduct(product);

        Pool pool = poolCurator.create(new Pool()
            .setOwner(owner)
            .setProduct(product)
            .setQuantity(10L)
            .setStartDate(Util.yesterday())
            .setEndDate(Util.tomorrow()));
        pool = poolCurator.create(pool);

        Entitlement ent = createEntitlementWithQuantity(owner, consumer, pool, 1);

        List<ConsumerEntitlementCount> actual = this.rhsmApiCompatCurator
            .getConsumerEntitlementCounts(null, List.of(consumer.getUuid()));

        assertThat(actual)
            .isNotNull()
            .singleElement()
            .returns(consumer.getId(), ConsumerEntitlementCount::id)
            .returns(consumer.getUuid(), ConsumerEntitlementCount::uuid)
            .returns(pool.getContractNumber(), ConsumerEntitlementCount::contractNumber)
            .returns(null, ConsumerEntitlementCount::subscriptionId)
            .returns(product.getId(), ConsumerEntitlementCount::productId)
            .returns(product.getName(), ConsumerEntitlementCount::productName)
            .returns(Long.valueOf(ent.getQuantity()), ConsumerEntitlementCount::quantity);
    }

    @Test
    public void testGetConsumerEntitlementCountsWithProductsWithTheSameName() {
        Owner owner = this.createOwner();

        Consumer consumer1 = this.createConsumer(owner);

        Content content1 = this.createContent();
        Content content2 = this.createContent();
        Content content3 = this.createContent();

        Product product1 = new Product()
            .setId(TestUtil.randomString())
            .setName(TestUtil.randomString());
        product1.addContent(content1, true);
        product1.addContent(content2, false);

        this.createProduct(product1);

        Product product2 = new Product()
            .setId(TestUtil.randomString())
            .setName(product1.getName());
        product2.addContent(content2, true);
        product2.addContent(content3, false);

        this.createProduct(product2);

        Pool pool1 = TestUtil.createPool(owner, product1)
            .setQuantity(10L);
        pool1 = poolCurator.create(pool1);

        Pool pool2 = TestUtil.createPool(owner, product2)
            .setQuantity(10L);
        pool2 = poolCurator.create(pool2);

        Entitlement ent1 = createEntitlementWithQuantity(owner, consumer1, pool1, 1);
        Entitlement ent2 = createEntitlementWithQuantity(owner, consumer1, pool2, 2);

        List<String> consumerIds = List.of(consumer1.getId());

        List<ConsumerEntitlementCount> actual = this.rhsmApiCompatCurator
            .getConsumerEntitlementCounts(consumerIds, null);

        List<ConsumerEntitlementCount> expected = List.of(
            new ConsumerEntitlementCount(consumer1.getId(),
                consumer1.getUuid(),
                pool1.getContractNumber(),
                pool1.getSubscriptionId(),
                product1.getId(),
                product1.getName(),
                Long.valueOf(ent1.getQuantity())),
            new ConsumerEntitlementCount(consumer1.getId(),
                consumer1.getUuid(),
                pool2.getContractNumber(),
                pool2.getSubscriptionId(),
                product2.getId(),
                product2.getName(),
                Long.valueOf(ent2.getQuantity())));

        assertThat(actual)
            .isNotNull()
            .hasSize(2)
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    public void testGetConsumerEntitlementCountsWithSameProductIdInDifferentNamespace() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Consumer consumer1 = this.createConsumer(owner1);
        Consumer consumer2 = this.createConsumer(owner2);

        Content content1 = this.createContent();
        Content content2 = this.createContent();
        Content content3 = this.createContent();

        Product product1 = new Product()
            .setId(TestUtil.randomString())
            .setName(TestUtil.randomString())
            .setNamespace(owner1);
        product1.addContent(content1, true);
        product1.addContent(content2, false);

        this.createProduct(product1);

        Product product2 = new Product()
            .setId(product1.getId())
            .setName(TestUtil.randomString())
            .setNamespace(owner2);
        product2.addContent(content2, true);
        product2.addContent(content3, false);

        this.createProduct(product2);

        Pool pool1 = TestUtil.createPool(owner1, product1)
            .setQuantity(10L);
        pool1 = poolCurator.create(pool1);

        Pool pool2 = TestUtil.createPool(owner2, product2)
            .setQuantity(10L);
        pool2 = poolCurator.create(pool2);

        Entitlement ent1 = createEntitlementWithQuantity(owner1, consumer1, pool1, 1);
        Entitlement ent2 = createEntitlementWithQuantity(owner2, consumer2, pool2, 1);

        List<String> consumerIds = List.of(consumer1.getId(), consumer2.getId());

        List<ConsumerEntitlementCount> actual = this.rhsmApiCompatCurator
            .getConsumerEntitlementCounts(consumerIds, null);

        List<ConsumerEntitlementCount> expected = List.of(
            new ConsumerEntitlementCount(consumer1.getId(),
                consumer1.getUuid(),
                pool1.getContractNumber(),
                pool1.getSubscriptionId(),
                product1.getId(),
                product1.getName(),
                Long.valueOf(ent1.getQuantity())),
            new ConsumerEntitlementCount(consumer2.getId(),
                consumer2.getUuid(),
                pool2.getContractNumber(),
                pool2.getSubscriptionId(),
                product2.getId(),
                product2.getName(),
                Long.valueOf(ent2.getQuantity())));

        assertThat(actual)
            .isNotNull()
            .hasSize(2)
            .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    public void testGetConsumerEntitlementCountsWithConsumerWithNoPools() {
        Owner owner = this.createOwner();

        Consumer consumer = this.createConsumer(owner);

        List<ConsumerEntitlementCount> actual = this.rhsmApiCompatCurator
            .getConsumerEntitlementCounts(null, List.of(consumer.getUuid()));

        assertThat(actual)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testGetConsumerEntitlementCountsWithExceedingInBlockSize() {
        int blockSize = 5;

        this.config.setProperty(DatabaseConfigFactory.IN_OPERATOR_BLOCK_SIZE, String.valueOf(blockSize));

        Owner owner = this.createOwner();

        Content content = this.createContent();
        Product product = new Product()
            .setId(TestUtil.randomString())
            .setName(TestUtil.randomString());
        product.addContent(content, true);

        this.createProduct(product);

        Pool pool = poolCurator.create(new Pool()
            .setOwner(owner)
            .setProduct(product)
            .setQuantity(1000L)
            .setStartDate(Util.yesterday())
            .setEndDate(Util.tomorrow()));
        pool = poolCurator.create(pool);

        List<String> consumerUuids = new ArrayList<>();
        List<String> consumerIds = new ArrayList<>();
        for (int i = 0; i < blockSize * 3; i++) {
            Consumer consumer = this.createConsumer(owner);
            createEntitlementWithQuantity(owner, consumer, pool, 1);
            consumerUuids.add(consumer.getUuid());
            consumerIds.add(consumer.getId());
        }

        List<ConsumerEntitlementCount> actual = this.rhsmApiCompatCurator
            .getConsumerEntitlementCounts(consumerIds, consumerUuids);

        assertThat(actual)
            .isNotNull()
            .hasSize(consumerUuids.size());
    }

    @Test
    public void testGetConsumerFeedsFilterByOwner() {
        Owner ownerA = createOwner();
        Owner ownerB = createOwner();
        Consumer consumerA1 = createConsumer(ownerA);
        Consumer consumerA2 = createConsumer(ownerA);
        Consumer consumerB1 = createConsumer(ownerB);

        List<ConsumerFeed> result = rhsmApiCompatCurator.getConsumerFeedQuery()
            .setOwner(ownerA)
            .getResultList();

        assertThat(result).extracting(ConsumerFeed::getId)
            .containsExactly(consumerA1.getId(), consumerA2.getId());
    }

    @Test
    public void testGetConsumerFeedsFilterByAfterId() {
        Owner owner = createOwner();
        Consumer c1 = createConsumer(owner);
        Consumer c2 = createConsumer(owner);
        Consumer c3 = createConsumer(owner);

        // Should only return consumers with id > id20
        List<ConsumerFeed> result =
            rhsmApiCompatCurator.getConsumerFeedQuery()
                .setOwner(owner)
                .setAfterId(c2.getId())
                .getResultList();

        assertThat(result)
            .extracting(ConsumerFeed::getId)
            .containsExactly(c3.getId());
    }

    @Test
    public void testGetConsumerFeedsFilterByAfterUuid() {
        Owner owner = createOwner();
        Consumer c1 = createConsumer(owner, "uuid1", null, null, null, null);
        Consumer c2 = createConsumer(owner, "uuid2", null, null, null, null);
        Consumer c3 = createConsumer(owner, "uuid3", null, null, null, null);

        // Should only return consumers with uuid1 > uuid2 (lexicographically)
        List<ConsumerFeed> result = rhsmApiCompatCurator.getConsumerFeedQuery()
            .setOwner(owner)
            .setAfterUuid(c2.getUuid())
            .getResultList();

        assertThat(result)
            .extracting(ConsumerFeed::getUuid)
            .containsExactly(c3.getUuid());
    }

    @Test
    public void testGetConsumerFeedsFilterByAfterCheckin() {
        Owner owner = createOwner();
        OffsetDateTime ts1 = OffsetDateTime.now().minusDays(3);
        OffsetDateTime ts2 = OffsetDateTime.now().minusDays(2);
        OffsetDateTime ts3 = OffsetDateTime.now().minusDays(1);
        Consumer c1 = createConsumer(owner, "uuidX", Date.from(ts1.toInstant()), null, null, null);
        Consumer c2 = createConsumer(owner, "uuidY", Date.from(ts2.toInstant()), null, null, null);
        Consumer c3 = createConsumer(owner, "uuidZ", Date.from(ts3.toInstant()), null, null, null);

        // Only return those after ts2 (should get c3 only)
        List<ConsumerFeed> result =
            rhsmApiCompatCurator.getConsumerFeedQuery()
                .setOwner(owner)
                .setAfterCheckin(ts2)
                .getResultList();

        assertThat(result)
            .extracting(ConsumerFeed::getId)
            .containsExactly(c3.getId());
    }

    @Test
    public void testGetConsumerFeedsFilterByAfterIdAndCheckin() {
        Owner owner = createOwner();
        OffsetDateTime ts1 = OffsetDateTime.now().minusDays(3);
        OffsetDateTime ts2 = OffsetDateTime.now().minusDays(2);
        OffsetDateTime ts3 = OffsetDateTime.now().minusDays(1);

        Consumer c1 = createConsumer(owner, "uuidX", Date.from(ts1.toInstant()), null, null, null);
        Consumer c2 = createConsumer(owner, "uuidY", Date.from(ts2.toInstant()), null, null, null);
        Consumer c3 = createConsumer(owner, "uuidZ", Date.from(ts3.toInstant()), null, null, null);

        // id > c1.getId() AND checkin > ts1 → c2 a c3
        List<ConsumerFeed> result = rhsmApiCompatCurator.getConsumerFeedQuery()
            .setOwner(owner)
            .setAfterId(c1.getId())
            .setAfterCheckin(ts1)
            .getResultList();

        assertThat(result)
            .extracting(ConsumerFeed::getId)
            .containsExactlyInAnyOrder(c2.getId(), c3.getId());
    }

    @Test
    public void testGetConsumerFeedsFilterByAfterIdAndUuid() {
        Owner owner = createOwner();
        Consumer c1 = createConsumer(owner, "uuid1", null, null, null, null);
        Consumer c2 = createConsumer(owner, "uuid2", null, null, null, null);
        Consumer c3 = createConsumer(owner, "uuid3", null, null, null, null);

        // id > c1.getId() AND uuid > "uuid1" → c2, c3
        List<ConsumerFeed> result = rhsmApiCompatCurator.getConsumerFeedQuery()
            .setOwner(owner)
            .setAfterId(c1.getId())
            .setAfterUuid(c1.getUuid())
            .getResultList();

        assertThat(result)
            .extracting(ConsumerFeed::getUuid)
            .containsExactlyInAnyOrder(c2.getUuid(), c3.getUuid());
    }

    @Test
    public void testGetConsumerFeedsFilterByAfterUuidAndCheckin() {
        Owner owner = createOwner();
        OffsetDateTime ts1 = OffsetDateTime.now().minusDays(3);
        OffsetDateTime ts2 = OffsetDateTime.now().minusDays(2);
        OffsetDateTime ts3 = OffsetDateTime.now().minusDays(1);

        Consumer c1 = createConsumer(owner, "uuidA", Date.from(ts1.toInstant()), null, null, null);
        Consumer c2 = createConsumer(owner, "uuidB", Date.from(ts2.toInstant()), null, null, null);
        Consumer c3 = createConsumer(owner, "uuidC", Date.from(ts3.toInstant()), null, null, null);

        // uuid > "uuidA" AND checkin > ts1 → c2, c3
        List<ConsumerFeed> result = rhsmApiCompatCurator.getConsumerFeedQuery()
            .setOwner(owner)
            .setAfterUuid(c1.getUuid())
            .setAfterCheckin(ts1)
            .getResultList();

        assertThat(result)
            .extracting(ConsumerFeed::getUuid)
            .containsExactlyInAnyOrder(c2.getUuid(), c3.getUuid());
    }

    @Test
    public void testGetConsumerFeedsFilterByAll() {
        Owner owner = createOwner();
        OffsetDateTime ts1 = OffsetDateTime.now().minusDays(5);
        OffsetDateTime ts2 = OffsetDateTime.now().minusDays(3);
        OffsetDateTime ts3 = OffsetDateTime.now().minusDays(1);
        Consumer c1 = createConsumer(owner, "uuidA", Date.from(ts1.toInstant()), null, null, null);
        Consumer c2 = createConsumer(owner, "uuidB", Date.from(ts2.toInstant()), null, null, null);
        Consumer c3 = createConsumer(owner, "uuidC", Date.from(ts3.toInstant()), null, null, null);

        List<ConsumerFeed> result = rhsmApiCompatCurator.getConsumerFeedQuery()
            .setOwner(owner)
            .setAfterId(c1.getId())
            .setAfterUuid(c1.getUuid())
            .setAfterCheckin(ts1)
            .getResultList();

        assertThat(result)
            .extracting(ConsumerFeed::getId)
            .containsExactlyInAnyOrder(c2.getId(), c3.getId());
    }

    @Test
    public void testGetConsumerFeedsPaging() {
        Owner owner = createOwner();
        int consumerCount = 20;
        List<String> uuids = new ArrayList<>();
        for (int i = 1; i <= consumerCount; i++) {
            String uuid = "uuid" + i;
            uuids.add(uuid);
            createConsumer(owner, uuid, null, null, null, null);
        }

        int pageSize = 5;
        int totalPages = (int) Math.ceil((double) consumerCount / pageSize);

        for (int page = 1; page <= totalPages; page++) {
            List<ConsumerFeed> result = rhsmApiCompatCurator.getConsumerFeedQuery()
                .setOwner(owner)
                .setPaging(page, pageSize)
                .getResultList();

            int expectedSize = Math.min(pageSize, consumerCount - (page - 1) * pageSize);

            assertThat(result)
                .hasSize(expectedSize);

            List<String> expectedUuids = uuids
                .subList((page - 1) * pageSize, Math.min(page * pageSize, consumerCount));

            assertThat(result)
                .extracting(ConsumerFeed::getUuid)
                .containsExactlyElementsOf(expectedUuids);
        }
    }

    @Test
    public void testGetConsumerFeedsPagingEmptyPage() {
        Owner owner = createOwner();
        int consumerCount = 5;
        for (int i = 1; i <= consumerCount; i++) {
            createConsumer(owner, "uuid" + i, null, null, null, null);
        }

        int page = 3;
        int pageSize = 3;
        List<ConsumerFeed> emptyPage = rhsmApiCompatCurator.getConsumerFeedQuery()
            .setOwner(owner)
            .setPaging(page, pageSize)
            .getResultList();

        assertThat(emptyPage)
            .isEmpty();
    }

    @Test
    public void testGetFactsByConsumerReturnsOnlyRelevantFacts() {
        Owner owner = createOwner();
        Map<String, String> facts = new HashMap<>();
        facts.put("cpu.cpu_socket(s)", "2");             // allowed
        facts.put("network.fqdn", "host.redhat.com");    // allowed
        facts.put("random.fact", "xxx");                 // not allowed
        Consumer consumer = createConsumer(owner, null, null, facts, null, null);

        List<ConsumerFeed> consumerFeeds = rhsmApiCompatCurator.getConsumerFeedQuery()
                .setOwner(owner)
                .getResultList();

        assertThat(consumerFeeds.get(0))
            .extracting(ConsumerFeed::getFacts)
            .satisfies(x -> {
                assertThat(x).containsEntry("cpu.cpu_socket(s)", "2");
                assertThat(x).containsEntry("network.fqdn", "host.redhat.com");
                assertThat(x).doesNotContainKey("random.fact");
            });
    }

    @Test
    public void testGetAddOnsByConsumerReturnsAllAddOns() {
        Owner owner = createOwner();
        HashSet<String> addOns = new HashSet<>();
        addOns.add("addon1");
        addOns.add("addon2");
        Consumer consumer = createConsumer(owner, null, null, null, addOns, null);

        List<ConsumerFeed> consumerFeed = rhsmApiCompatCurator.getConsumerFeedQuery()
            .setOwner(owner)
            .getResultList();

        assertThat(consumerFeed.get(0))
            .extracting(ConsumerFeed::getSyspurposeAddons)
            .satisfies(x -> {
                assertThat(x).containsExactlyInAnyOrder("addon1", "addon2");
            });
    }

    @Test
    public void testGetConsumerFeedsReturnsInstalledProducts() {
        Owner owner = createOwner();
        ConsumerInstalledProduct installedProd1 = createConsumerInstalledProduct();
        ConsumerInstalledProduct installedProd2 = createConsumerInstalledProduct();
        Consumer consumer = createConsumer(owner, null, null, null, null, List.of(installedProd1,
            installedProd2));

        List<ConsumerFeed> consumerFeed = rhsmApiCompatCurator.getConsumerFeedQuery()
            .setOwner(owner)
            .getResultList();

        Set<ConsumerFeedInstalledProduct> installedProducts = consumerFeed.get(0).getInstalledProducts();
        assertThat(installedProducts)
            .extracting(ConsumerFeedInstalledProduct::productId)
            .containsExactlyInAnyOrder(installedProd1.getProductId(), installedProd2.getProductId());
        assertThat(installedProducts)
            .extracting(ConsumerFeedInstalledProduct::productName)
            .contains(installedProd1.getProductName(), installedProd2.getProductName());
    }

    @Test
    public void testGetConsumerFeedsReturnsHypervisorGuestMapping() {
        Owner owner = createOwner();
        Consumer host = createConsumer(owner);
        String hostGuestVirtUuid = TestUtil.randomString("host-guest-virt-uuid-");
        createGuest(owner, hostGuestVirtUuid);
        linkHostToGuests(host, hostGuestVirtUuid);

        List<ConsumerFeed> consumerFeed = rhsmApiCompatCurator.getConsumerFeedQuery()
            .setOwner(owner)
            .getResultList();

        assertThat(consumerFeed)
            .isNotNull()
            .hasSize(2);

        List<ConsumerFeed> filteredResult = consumerFeed.stream()
            .filter(x -> x.getHypervisorUuid() != null)
            .toList();

        assertThat(filteredResult)
            .isNotNull()
            .hasSize(1)
            .extracting(
                ConsumerFeed::getHypervisorUuid,
                ConsumerFeed::getHypervisorName,
                ConsumerFeed::getGuestId
            )
            .containsExactly(
                tuple(host.getUuid(), host.getName(), hostGuestVirtUuid)
            );
    }

    @Test
    public void testCountConsumerFeedFilterByOwner() {
        Owner ownerA = createOwner();
        Owner ownerB = createOwner();
        createConsumer(ownerA);
        createConsumer(ownerA);
        createConsumer(ownerB);

        long count = rhsmApiCompatCurator.getConsumerFeedQuery()
            .setOwner(ownerA)
            .getResultCount();

        assertThat(count)
            .isEqualTo(2);
    }

    @Test
    public void testCountConsumerFeedFilterByAfterId() {
        Owner owner = createOwner();
        Consumer c1 = createConsumer(owner);
        Consumer c2 = createConsumer(owner);
        Consumer c3 = createConsumer(owner);

        long count = rhsmApiCompatCurator.getConsumerFeedQuery()
            .setOwner(owner)
            .setAfterId(c2.getId())
            .getResultCount();

        assertThat(count)
            .isEqualTo(1);
    }

    @Test
    public void testCountConsumerFeedFilterByAfterUuid() {
        Owner owner = createOwner();
        createConsumer(owner, "uuid1", null, null, null, null);
        Consumer consumer = createConsumer(owner, "uuid2", null, null, null, null);
        createConsumer(owner, "uuid3", null, null, null, null);

        long count = rhsmApiCompatCurator.getConsumerFeedQuery()
            .setOwner(owner)
            .setAfterUuid(consumer.getUuid())
            .getResultCount();

        assertThat(count)
            .isEqualTo(1);
    }

    @Test
    public void testCountConsumerFeedFilterByAfterCheckin() {
        Owner owner = createOwner();
        OffsetDateTime ts1 = OffsetDateTime.now().minusDays(3);
        OffsetDateTime ts2 = OffsetDateTime.now().minusDays(2);
        OffsetDateTime ts3 = OffsetDateTime.now().minusDays(1);

        createConsumer(owner, "uuidX", Date.from(ts1.toInstant()), null, null, null);
        createConsumer(owner, "uuidY", Date.from(ts2.toInstant()), null, null, null);
        createConsumer(owner, "uuidZ", Date.from(ts3.toInstant()), null, null, null);

        long count = rhsmApiCompatCurator.getConsumerFeedQuery()
            .setOwner(owner)
            .setAfterCheckin(ts2)
            .getResultCount();

        assertThat(count)
            .isEqualTo(1);
    }

    @Test
    public void testCountConsumerFeedFilterByAfterIdAndCheckin() {
        Owner owner = createOwner();
        OffsetDateTime ts1 = OffsetDateTime.now().minusDays(3);
        OffsetDateTime ts2 = OffsetDateTime.now().minusDays(2);
        OffsetDateTime ts3 = OffsetDateTime.now().minusDays(1);

        Consumer c1 = createConsumer(owner, "uuidX", Date.from(ts1.toInstant()), null, null, null);
        createConsumer(owner, "uuidY", Date.from(ts2.toInstant()), null, null, null);
        createConsumer(owner, "uuidZ", Date.from(ts3.toInstant()), null, null, null);

        long count = rhsmApiCompatCurator.getConsumerFeedQuery()
            .setOwner(owner)
            .setAfterId(c1.getId())
            .setAfterCheckin(ts1)
            .getResultCount();

        assertThat(count)
            .isEqualTo(2);
    }

    @Test
    public void testCountConsumerFeedFilterByAfterIdAndUuid() {
        Owner owner = createOwner();
        Consumer c1 = createConsumer(owner, "uuid1", null, null, null, null);
        createConsumer(owner, "uuid2", null, null, null, null);
        createConsumer(owner, "uuid3", null, null, null, null);

        long count = rhsmApiCompatCurator.getConsumerFeedQuery()
            .setOwner(owner)
            .setAfterId(c1.getId())
            .setAfterUuid(c1.getUuid())
            .getResultCount();

        assertThat(count)
            .isEqualTo(2);
    }

    private Consumer createConsumer(Owner owner, String uuid, Date lastCheckin, Map<String, String> facts,
        Set<String> addons, List<ConsumerInstalledProduct> installedProducts) {
        Consumer consumer = new Consumer()
            .setUuid(uuid)
            .setOwner(owner)
            .setName("test-consumer")
            .setType(this.createConsumerType())
            .setLastCheckin(lastCheckin)
            .setFacts(facts)
            .setAddOns(addons)
            .setInstalledProducts(installedProducts);

        return this.consumerCurator.create(consumer);
    }

    private Consumer createGuest(Owner owner, String virtUuid) {
        return this.createConsumer(owner, null, null, Map.of(Consumer.Facts.VIRT_UUID, virtUuid,
            Consumer.Facts.VIRT_IS_GUEST, "true", Consumer.Facts.ARCHITECTURE , "x86_64"), null, null);
    }

    private void linkHostToGuests(Consumer host, String... virtUuid) {
        List<GuestId> guestIds = Arrays.stream(virtUuid)
            .map(this::toGuestId)
            .collect(Collectors.toList());

        linkHostToGuests(host, guestIds);
    }

    private void linkHostToGuests(Consumer host, List<GuestId> guestIds) {
        consumerCurator.update(host.setGuestIds(guestIds));
    }

    private GuestId toGuestId(String guestId) {
        GuestId guest = new GuestId();
        guest.setGuestId(guestId);
        return guest;
    }

    private ConsumerInstalledProduct createConsumerInstalledProduct() {
        ConsumerInstalledProduct source = new ConsumerInstalledProduct();

        source.setProductId(TestUtil.randomString("test_product_id"));
        source.setProductName(TestUtil.randomString("test_product_name"));
        source.setVersion(TestUtil.randomString("test_version"));
        source.setArch(TestUtil.randomString("test_arch"));
        source.setStatus(TestUtil.randomString("test_status"));
        source.setStartDate(new Date());
        source.setEndDate(new Date());
        source.setCreated(new Date());
        source.setUpdated(new Date());

        return source;
    }

    private Entitlement createEntitlementWithQuantity(Owner owner, Consumer consumer, Pool pool,
        int quantity) {

        Entitlement entitlement = new Entitlement()
            .setId(Util.generateDbUUID())
            .setOwner(owner)
            .setQuantity(quantity)
            .setPool(pool)
            .setConsumer(consumer);

        this.entitlementCurator.create(entitlement);

        consumer.addEntitlement(entitlement);
        pool.getEntitlements().add(entitlement);

        return entitlement;
    }
}
