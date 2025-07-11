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

import org.candlepin.config.DatabaseConfigFactory;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.ArrayList;
import java.util.List;

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
