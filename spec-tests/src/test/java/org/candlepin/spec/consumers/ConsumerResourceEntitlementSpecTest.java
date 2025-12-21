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
package org.candlepin.spec.consumers;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.map;
import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotFound;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.AttributeDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerInstalledProductDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.PoolQuantityDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.SubscriptionDTO;
import org.candlepin.spec.bootstrap.assertions.OnlyInHosted;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.request.Request;
import org.candlepin.spec.bootstrap.client.request.Response;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.builder.Subscriptions;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;


@SpecTest
public class ConsumerResourceEntitlementSpecTest {
    private static final String LIST_ENTS_PATH = "/consumers/{consumer_uuid}/entitlements";

    private ApiClient adminClient;
    private OwnerDTO owner;

    @BeforeEach
    public void setup() {
        adminClient = ApiClients.admin();
        owner = adminClient.owners().createOwner(Owners.random());
    }

    @Test
    public void shouldReceivePagedDataBackWhenRequested() {
        String ownerKey = owner.getKey();
        ProductDTO prod1 = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ProductDTO prod2 = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ProductDTO prod3 = adminClient.ownerProducts().createProduct(ownerKey, Products.random());
        ProductDTO prod4 = adminClient.ownerProducts().createProduct(ownerKey, Products.random());

        adminClient.owners().createPool(ownerKey, Pools.random(prod1));
        adminClient.owners().createPool(ownerKey, Pools.random(prod2));
        adminClient.owners().createPool(ownerKey, Pools.random(prod3));
        adminClient.owners().createPool(ownerKey, Pools.random(prod4));

        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        adminClient.consumers().bindProduct(consumer.getUuid(), prod1.getId());
        adminClient.consumers().bindProduct(consumer.getUuid(), prod2.getId());
        adminClient.consumers().bindProduct(consumer.getUuid(), prod3.getId());
        adminClient.consumers().bindProduct(consumer.getUuid(), prod4.getId());

        Response response = Request.from(adminClient)
            .setPath(LIST_ENTS_PATH)
            .setPathParam("consumer_uuid", consumer.getUuid())
            .addQueryParam("page", "1")
            .addQueryParam("per_page", "2")
            .addQueryParam("sort_by", "id")
            .addQueryParam("order", "asc")
            .execute();
        assertNotNull(response);
        assertEquals(200, response.getCode());
        List<EntitlementDTO> ents = response.deserialize(new TypeReference<List<EntitlementDTO>>() {});
        assertThat(ents).hasSize(2);
        assertThat(ents.get(0).getId()).isLessThan(ents.get(1).getId());

        response = Request.from(adminClient)
            .setPath(LIST_ENTS_PATH)
            .setPathParam("consumer_uuid", consumer.getUuid())
            .addQueryParam("page", "2")
            .addQueryParam("per_page", "2")
            .addQueryParam("sort_by", "id")
            .addQueryParam("order", "asc")
            .execute();
        assertNotNull(response);
        assertEquals(200, response.getCode());
        ents = response.deserialize(new TypeReference<List<EntitlementDTO>>() {});
        assertThat(ents).hasSize(2);
        assertThat(ents.get(0).getId()).isLessThan(ents.get(1).getId());

        response = Request.from(adminClient)
            .setPath(LIST_ENTS_PATH)
            .setPathParam("consumer_uuid", consumer.getUuid())
            .addQueryParam("page", "3")
            .addQueryParam("per_page", "2")
            .addQueryParam("sort_by", "id")
            .addQueryParam("order", "asc")
            .execute();
        assertNotNull(response);
        assertEquals(200, response.getCode());
        ents = response.deserialize(new TypeReference<List<EntitlementDTO>>() {});
        assertThat(ents).isEmpty();
    }

    @Test
    public void shouldNotAllowAConsumerToViewEntitlementsFromADifferentConsumer() {
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        OwnerDTO owner2 = adminClient.owners().createOwner(Owners.random());
        ConsumerDTO consumer2 = adminClient.consumers().createConsumer(Consumers.random(owner2));
        ApiClient consumer2Client = ApiClients.ssl(consumer2);

        assertNotFound(() -> consumer2Client.consumers().listEntitlements(consumer.getUuid()));
    }

    @Test
    public void shouldPopulateGeneratedFieldsWhenFetchingConsumerEntitlements() {
        ProductDTO prod = adminClient.ownerProducts()
            .createProduct(owner.getKey(), Products.random());
        adminClient.owners().createPool(owner.getKey(), Pools.random(prod));
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        adminClient.consumers().bindProduct(consumer.getUuid(), prod.getId());

        List<EntitlementDTO> ents = consumerClient.consumers().listEntitlements(consumer.getUuid());
        assertThat(ents)
            .isNotNull()
            .hasSize(1);

        EntitlementDTO result = ents.get(0);
        assertNotNull(result.getCreated());
        assertNotNull(result.getUpdated());
    }

    @Test
    public void shouldNotRecalculateQuantityAttributesWhenFetchingEntitlements() {
        // TODO: FIXME: What is this test testing?

        ProductDTO prod = adminClient.ownerProducts()
            .createProduct(owner.getKey(), Products.random());
        adminClient.owners().createPool(owner.getKey(), Pools.random(prod));
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        adminClient.consumers().bindProduct(consumer.getUuid(), prod.getId());

        List<EntitlementDTO> ents = consumerClient.consumers().listEntitlements(consumer.getUuid());
        assertThat(ents)
            .singleElement()
            .extracting(EntitlementDTO::getPool)
            .isNotNull()
            .extracting(PoolDTO::getCalculatedAttributes, as(map(String.class, String.class)))
            .doesNotContainKeys("suggested_quantity", "quantity_increment")
            .containsKeys("compliance_type")
            .containsEntry("compliance_type", "Standard");
    }

    @Test
    @OnlyInHosted
    public void shouldNotRecalculateQuantityAttributesWhenFetchingEntitlementsHosted() {
        ProductDTO prod =  adminClient.hosted().createProduct(Products.random());
        adminClient.hosted().createSubscription(Subscriptions.random(owner, prod));
        AsyncJobStatusDTO job = adminClient.owners().refreshPools(this.owner.getKey(), false);
        job = adminClient.jobs().waitForJob(job.getId());
        assertThatJob(job).isFinished();
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        adminClient.consumers().bindProduct(consumer.getUuid(), prod.getId());

        List<EntitlementDTO> ents = consumerClient.consumers().listEntitlements(consumer.getUuid());
        assertThat(ents)
            .singleElement()
            .extracting(EntitlementDTO::getPool)
            .isNotNull()
            .extracting(PoolDTO::getCalculatedAttributes, as(map(String.class, String.class)))
            .doesNotContainKeys("suggested_quantity", "quantity_increment")
            .containsKeys("compliance_type")
            .containsEntry("compliance_type", "Standard");
    }

    @Test
    public void shouldBlockConsumersFromUsingOtherOrgsPools() {
        ProductDTO prod = adminClient.ownerProducts()
            .createProduct(owner.getKey(), Products.random());
        PoolDTO pool = adminClient.owners().createPool(owner.getKey(), Pools.random(prod));
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        adminClient.consumers().bindProduct(consumer.getUuid(), prod.getId());

        ConsumerDTO consumer2 = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient2 = ApiClients.ssl(consumer2);
        assertNotFound(() -> consumerClient2.consumers().bindPool(consumer.getUuid(), pool.getId(), 1));
    }

    @Test
    @OnlyInHosted
    public void shouldBlockConsumersFromUsingOtherOrgsPoolsHosted() {
        ProductDTO prod =  adminClient.hosted().createProduct(Products.random());
        SubscriptionDTO sub = adminClient.hosted().createSubscription(Subscriptions.random(owner, prod));
        AsyncJobStatusDTO job = adminClient.owners().refreshPools(this.owner.getKey(), false);
        job = adminClient.jobs().waitForJob(job.getId());
        assertThatJob(job).isFinished();
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        adminClient.consumers().bindProduct(consumer.getUuid(), prod.getId());

        ConsumerDTO consumer2 = adminClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient2 = ApiClients.ssl(consumer2);
        assertNotFound(() -> consumerClient2.consumers()
            .bindPool(consumer.getUuid(), sub.getUpstreamPoolId(), 1));
    }

    @Test
    public void shouldSetComplianceStatusAndUpdateComplianceStatus() {
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        assertThat(consumer).returns("valid", ConsumerDTO::getEntitlementStatus);
        ProductDTO prod = adminClient.ownerProducts()
            .createProduct(owner.getKey(), Products.random());

        consumer.installedProducts(Set.of(Products.toInstalled(prod)));
        adminClient.consumers().updateConsumer(consumer.getUuid(), consumer);
        consumer = adminClient.consumers().getConsumer(consumer.getUuid());
        PoolDTO pool = Pools.random(prod)
            .upstreamPoolId(StringUtil.random("pool-"))
            .subscriptionId(StringUtil.random("sub-"))
            .subscriptionSubKey(StringUtil.random("subKey-"));
        pool = adminClient.owners().createPool(owner.getKey(), pool);
        adminClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);

        consumer = adminClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer)
            .isNotNull()
            .returns("valid", ConsumerDTO::getEntitlementStatus);
    }

    @Test
    @OnlyInHosted
    public void shouldSetComplianceStatusAndUpdateComplianceStatusHosted() {
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        assertThat(consumer).returns("valid", ConsumerDTO::getEntitlementStatus);
        ProductDTO prod =  adminClient.hosted().createProduct(Products.random());

        consumer.installedProducts(Set.of(Products.toInstalled(prod)));
        adminClient.consumers().updateConsumer(consumer.getUuid(), consumer);
        consumer = adminClient.consumers().getConsumer(consumer.getUuid());
        adminClient.hosted().createSubscription(Subscriptions.random(owner, prod));
        AsyncJobStatusDTO job = adminClient.owners().refreshPools(this.owner.getKey(), false);
        job = adminClient.jobs().waitForJob(job.getId());
        assertThatJob(job).isFinished();
        List<PoolDTO> pools = adminClient.owners().listOwnerPools(owner.getKey());
        assertThat(pools).singleElement();

        adminClient.consumers().bindPool(consumer.getUuid(), pools.get(0).getId(), 1);
        consumer = adminClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer)
            .isNotNull()
            .returns("valid", ConsumerDTO::getEntitlementStatus);
    }

    @Test
    public void shouldAllowConsumerToBindToProductsSupportingMultipleArchitectures() {
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        ConsumerDTO consumer = Consumers.random(owner).facts(Map.of("uname.machine", "x86_64"));
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);
        ProductDTO product = Products.random()
            .addAttributesItem(ProductAttributes.Arch.withValue("i386, x86_64"));
        product = adminClient.ownerProducts().createProduct(owner.getKey(), product);
        PoolDTO pool = adminClient.owners().createPool(owner.getKey(), Pools.random(product));

        assertThat(consumerClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1)).singleElement();
    }

    @Test
    public void shouldUpdateConsumerUpdatedTimestampOnBind() throws Exception {
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        ConsumerDTO consumer = Consumers.random(owner);
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        ProductDTO product = Products.random();
        product = adminClient.ownerProducts().createProduct(owner.getKey(), product);
        PoolDTO pool = adminClient.owners().createPool(owner.getKey(), Pools.random(product));

        // Do a bind and make sure the updated timestamp changed:
        OffsetDateTime oldUpdated = userClient.consumers().getConsumer(consumer.getUuid()).getUpdated();

        // MySQL before 5.6.4 doesn't store fractional seconds on timestamps.
        sleep(1000);

        consumerClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);
        assertThat(userClient.consumers().getConsumer(consumer.getUuid()))
            .doesNotReturn(null, ConsumerDTO::getUpdated)
            .doesNotReturn(oldUpdated, ConsumerDTO::getUpdated);
    }

    @Test
    public void shouldConsumerCanAsyncBindByProductId() throws Exception {
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        ConsumerDTO consumer = Consumers.random(owner);
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        ProductDTO product = Products.random();
        product = adminClient.ownerProducts().createProduct(owner.getKey(), product);
        PoolDTO pool = adminClient.owners().createPool(owner.getKey(), Pools.random(product));

        AsyncJobStatusDTO job = AsyncJobStatusDTO.fromJson(userClient.consumers().bind(consumer.getUuid(),
            pool.getId(), null, 1, null, null, true, null, List.of()));
        job = adminClient.jobs().waitForJob(job);
        assertThatJob(job).isFinished();

        assertThat(consumerClient.consumers().listEntitlements(consumer.getUuid()))
            .singleElement()
            .returns(product.getId(), x -> x.getPool().getProductId());
    }

    @Test
    public void shouldAllowConsumerToBindToProudctsBasedOnProductSocketQuantityAcrossPools()
        throws Exception {
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        ConsumerDTO consumer = Consumers.random(owner)
            .facts(Map.of("cpu.cpu_socket(s)", "4"));
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        ProductDTO product1 = Products.random()
            .addAttributesItem(ProductAttributes.Sockets.withValue("2"))
            .addAttributesItem(ProductAttributes.MultiEntitlement.withValue("yes"))
            .addAttributesItem(ProductAttributes.StackingId.withValue("8888"));
        product1 = adminClient.ownerProducts().createProduct(owner.getKey(), product1);
        ProductDTO product2 = Products.random()
            .addAttributesItem(ProductAttributes.Sockets.withValue("2"))
            .addAttributesItem(ProductAttributes.MultiEntitlement.withValue("yes"))
            .addAttributesItem(ProductAttributes.StackingId.withValue("8888"));
        product2 = adminClient.ownerProducts().createProduct(owner.getKey(), product2);
        adminClient.owners().createPool(owner.getKey(), Pools.random(product1)
            .quantity(1L).startDate(OffsetDateTime.now().minusSeconds(3)));
        adminClient.owners().createPool(owner.getKey(), Pools.random(product1)
            .quantity(1L).startDate(OffsetDateTime.now().minusSeconds(3)));
        adminClient.owners().createPool(owner.getKey(), Pools.random(product2)
            .quantity(1L).startDate(OffsetDateTime.now().minusSeconds(3)));

        consumerClient.consumers().bindProduct(consumer.getUuid(), product1);
        assertThat(consumerClient.consumers().listEntitlements(consumer.getUuid()))
            .hasSize(2)
            .extracting(EntitlementDTO::getQuantity)
            .containsExactly(1, 1);
    }

    @Test
    public void shouldAllowTheInstalledProductsToBeEnrichedWthProductInformation() {
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        ConsumerDTO consumer = Consumers.random(owner)
            .facts(Map.of("arch", "test_arch1"));
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);

        ProductDTO product1 = Products.random()
            .addAttributesItem(ProductAttributes.Arch.withValue("ALL"))
            .addAttributesItem(ProductAttributes.Version.withValue("3.11"))
            .addAttributesItem(ProductAttributes.MultiEntitlement.withValue("yes"));
        product1 = adminClient.ownerProducts().createProduct(owner.getKey(), product1);
        Set<ConsumerInstalledProductDTO> installed = Set.of(new ConsumerInstalledProductDTO()
            .productId(product1.getId()).productName(product1.getName()));

        consumerClient.consumers().updateConsumer(consumer.getUuid(),
            new ConsumerDTO().installedProducts(installed));

        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
        adminClient.owners().createPool(owner.getKey(), Pools.random(product1)
            .startDate(start)
            .endDate(start.plusYears(1L)));

        final String consumerUuid = consumer.getUuid();
        adminClient.owners().listOwnerPools(owner.getKey()).forEach(entry ->
            consumerClient.consumers().bindPool(consumerUuid, entry.getId(), 1));

        consumer = userClient.consumers().getConsumer(consumerUuid);
        consumer.getInstalledProducts().forEach(entry -> assertThat(entry)
            .returns("ALL", ConsumerInstalledProductDTO::getArch)
            .returns("3.11", ConsumerInstalledProductDTO::getVersion)
            .returns("green", ConsumerInstalledProductDTO::getStatus)
            .returns(start, ConsumerInstalledProductDTO::getStartDate)
            .returns(start.plusYears(1L), ConsumerInstalledProductDTO::getEndDate));
    }

    @Test
    public void shouldAllowAConsumerWithoutExistingEntitlementsToDryRunAnAutoAttachOnSLAButNotFilterOnIt() {
        String serviceLevel1 = StringUtil.random("VIP");
        String serviceLevel2 = StringUtil.random("Ultra-VIP");
        ProductDTO product1 = Products.randomEng()
            .addAttributesItem(ProductAttributes.SupportLevel.withValue(serviceLevel1));
        product1 = adminClient.ownerProducts().createProduct(owner.getKey(), product1);
        ProductDTO product2 = Products.randomEng()
            .addAttributesItem(ProductAttributes.SupportLevel.withValue(serviceLevel2));
        product2 = adminClient.ownerProducts().createProduct(owner.getKey(), product2);
        PoolDTO pool1 = adminClient.owners().createPool(owner.getKey(), Pools.random(product1));
        PoolDTO pool2 = adminClient.owners().createPool(owner.getKey(), Pools.random(product2));

        Set<ConsumerInstalledProductDTO> installed = Set.of(
            new ConsumerInstalledProductDTO().productId(product1.getId()).productName(product1.getName()),
            new ConsumerInstalledProductDTO().productId(product2.getId()).productName(product2.getName()));

        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        ConsumerDTO consumer = Consumers.random(owner)
            .serviceLevel(serviceLevel1)
            .installedProducts(installed);
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumer = consumerClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer).returns(serviceLevel1, ConsumerDTO::getServiceLevel);

        // dry run against the set service level:
        // should return both pools because we no longer filter on consumer's sla
        // (unless the consumer has existing entitlements, and in this case we don't).
        List<PoolQuantityDTO> pools = adminClient.consumers().dryBind(consumer.getUuid(), null);
        assertThat(pools)
            .hasSize(2)
            .map(PoolQuantityDTO::getPool)
            .map(PoolDTO::getId)
            .containsExactly(pool1.getId(), pool2.getId());

        // dry run against the override service level:
        // should return both pools because we no longer filter on the SLA override
        // (unless the consumer has existing entitlements, and in this case we don't).
        pools = adminClient.consumers().dryBind(consumer.getUuid(), serviceLevel2);
        assertThat(pools)
            .hasSize(2)
            .map(PoolQuantityDTO::getPool)
            .map(PoolDTO::getId)
            .containsExactly(pool1.getId(), pool2.getId());

        // ensure the override use did not change the setting
        consumer = consumerClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer).returns(serviceLevel1, ConsumerDTO::getServiceLevel);

        // dry run against 1) no consumer SLA 2) no override SLA 3) with owner default SLA:
        // should return both pools because we no longer filter on the owner's default SLA.
        // (unless the consumer has existing entitlements, and in this case we don't).
        consumerClient.consumers().updateConsumer(consumer.getUuid(), new ConsumerDTO().serviceLevel(""));
        consumer = consumerClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer.getServiceLevel()).isEmpty();
        consumerClient.consumers().updateConsumer(consumer.getUuid(), new ConsumerDTO()
            .serviceLevel(serviceLevel1));

        // dry run against the override service level should be case-insensitive
        assertThat(serviceLevel2.equals(serviceLevel2.toLowerCase())).isFalse();
        pools = adminClient.consumers().dryBind(consumer.getUuid(), serviceLevel2.toLowerCase());
        assertThat(pools)
            .hasSize(2)
            .map(PoolQuantityDTO::getPool)
            .map(PoolDTO::getId)
            .containsExactly(pool1.getId(), pool2.getId());
    }

    @Test
    public void shouldAllowAConsumerDryRunAnAutoAttachBasedOnSLAButDoNotFilterOnTheirExistingEntSlas() {
        String serviceLevel1 = StringUtil.random("VIP");
        String serviceLevel2 = StringUtil.random("Ultra-VIP");
        ProductDTO product1 = Products.randomEng()
            .addAttributesItem(ProductAttributes.SupportLevel.withValue(serviceLevel1));
        product1 = adminClient.ownerProducts().createProduct(owner.getKey(), product1);
        ProductDTO product2 = Products.randomEng()
            .addAttributesItem(ProductAttributes.SupportLevel.withValue(serviceLevel2));
        product2 = adminClient.ownerProducts().createProduct(owner.getKey(), product2);
        ProductDTO product3 = Products.randomEng()
            .addAttributesItem(ProductAttributes.SupportLevel.withValue(serviceLevel2));
        product3 = adminClient.ownerProducts().createProduct(owner.getKey(), product3);

        PoolDTO pool1 = adminClient.owners().createPool(owner.getKey(), Pools.random(product1));
        PoolDTO pool2 = adminClient.owners().createPool(owner.getKey(), Pools.random(product2));
        PoolDTO pool3 = adminClient.owners().createPool(owner.getKey(), Pools.random(product3));

        Set<ConsumerInstalledProductDTO> installed = Set.of(
            new ConsumerInstalledProductDTO().productId(product1.getId()).productName(product1.getName()),
            new ConsumerInstalledProductDTO().productId(product2.getId()).productName(product2.getName()),
            new ConsumerInstalledProductDTO().productId(product3.getId()).productName(product3.getName()));

        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        ConsumerDTO consumer = Consumers.random(owner)
            .serviceLevel(serviceLevel1)
            .installedProducts(installed);
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumer = consumerClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer).returns(serviceLevel1, ConsumerDTO::getServiceLevel);

        // We explicitly attach to a pool with Ultra-VIP SLA
        consumerClient.consumers().bindPool(consumer.getUuid(), pool2.getId(), 1);

        // dry run against the set service level:
        // NOTE : we do NOT filter on the consumer's existing entitlements' SLA's, and in this case
        // the consumer has an existing entitlement whose SLA is 'Ultra-VIP',
        // so 'Ultra-VIP' & 'VIP' both pools are considered and eligible during auto attach.
        List<PoolQuantityDTO> pools = adminClient.consumers().dryBind(consumer.getUuid(), null);
        assertThat(pools).hasSize(2);
        assertThat(pools.get(0))
            .returns(pool1.getId(), x -> x.getPool().getId())
            .returns(serviceLevel1, x -> getProductAttributeValue(x.getPool(), "support_level"));
        assertThat(pools.get(1))
            .returns(pool3.getId(), x -> x.getPool().getId())
            .returns(serviceLevel2, x -> getProductAttributeValue(x.getPool(), "support_level"));
    }

    @Test
    public void shouldRecognizeSupportLevelExemptAttribute() {
        String serviceLevel1 = StringUtil.random("VIP");
        String serviceLevel2 = StringUtil.random("Ultra-VIP");
        ProductDTO product1 = Products.randomEng()
            .addAttributesItem(ProductAttributes.SupportLevel.withValue("Layered"))
            .addAttributesItem(ProductAttributes.SupportLevelExempt.withValue("true"));
        product1 = adminClient.ownerProducts().createProduct(owner.getKey(), product1);
        ProductDTO product2 = Products.randomEng()
            .addAttributesItem(ProductAttributes.SupportLevel.withValue(serviceLevel1));
        product2 = adminClient.ownerProducts().createProduct(owner.getKey(), product2);
        ProductDTO product3 = Products.randomEng()
            .addAttributesItem(ProductAttributes.SupportLevel.withValue(serviceLevel2));
        product3 = adminClient.ownerProducts().createProduct(owner.getKey(), product3);
        ProductDTO product4 = Products.randomEng()
            .addAttributesItem(ProductAttributes.SupportLevel.withValue("LAYered"));
        product4 = adminClient.ownerProducts().createProduct(owner.getKey(), product4);
        PoolDTO pool1 = adminClient.owners().createPool(owner.getKey(), Pools.random(product1));
        PoolDTO pool2 = adminClient.owners().createPool(owner.getKey(), Pools.random(product2));
        PoolDTO pool3 = adminClient.owners().createPool(owner.getKey(), Pools.random(product3));

        Set<ConsumerInstalledProductDTO> installed = Set.of(
            new ConsumerInstalledProductDTO().productId(product1.getId()).productName(product1.getName()),
            new ConsumerInstalledProductDTO().productId(product2.getId()).productName(product2.getName()),
            new ConsumerInstalledProductDTO().productId(product4.getId()).productName(product4.getName()));

        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        ConsumerDTO consumer = Consumers.random(owner)
            .serviceLevel(serviceLevel2)
            .installedProducts(installed);
        consumer = userClient.consumers().createConsumer(consumer);
        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumer = consumerClient.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer).returns(serviceLevel2, ConsumerDTO::getServiceLevel);

        // Dry run against the set service level:
        // Should get pools of both the exempt product (product1) and the other installed product (product2),
        // because we no longer filter on the consumer's sla match (unless the consumer has existing
        // entitlements), and exempt sla pools are always returned.
        List<PoolQuantityDTO> pools = adminClient.consumers().dryBind(consumer.getUuid(), null);
        assertThat(pools)
            .hasSize(2)
            .map(PoolQuantityDTO::getPool)
            .map(PoolDTO::getId)
            .containsExactly(pool1.getId(), pool2.getId());

        // This product should also get pulled, exempt overrides
        // based on name match
        adminClient.owners().createPool(owner.getKey(), Pools.random(product4)
            .startDate(OffsetDateTime.now().minusSeconds(3)));
        assertThat(adminClient.consumers().dryBind(consumer.getUuid(), null)).hasSize(3);

        // changing consumer's service level to one that matches installed
        // should have no effect because we are not filtering on it (unless the consumer has existing
        // entitlements), and exempt sla pools are always returned.
        consumerClient.consumers().updateConsumer(consumer.getUuid(), new ConsumerDTO()
            .serviceLevel(serviceLevel1));
        assertThat(adminClient.consumers().dryBind(consumer.getUuid(), null)).hasSize(3);
    }

    @Test
    public void shouldReturnEmptListForDryRunWhereAllPoolsAreBlockedBecauseOfConsumerType() {
        ProductDTO product1 = Products.randomEng()
            .addAttributesItem(ProductAttributes.RequiresConsumer.withValue("person"));
        product1 = adminClient.ownerProducts().createProduct(owner.getKey(), product1);
        ProductDTO product2 = Products.randomEng()
            .addAttributesItem(ProductAttributes.RequiresConsumer.withValue("person"));
        product2 = adminClient.ownerProducts().createProduct(owner.getKey(), product2);
        PoolDTO pool1 = adminClient.owners().createPool(owner.getKey(), Pools.random(product1));
        PoolDTO pool2 = adminClient.owners().createPool(owner.getKey(), Pools.random(product2));

        Set<ConsumerInstalledProductDTO> installed = Set.of(
            new ConsumerInstalledProductDTO().productId(product1.getId()).productName(product1.getName()),
            new ConsumerInstalledProductDTO().productId(product2.getId()).productName(product2.getName()));

        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        ConsumerDTO consumer = Consumers.random(owner)
            .installedProducts(installed);
        consumer = userClient.consumers().createConsumer(consumer);

        assertThat(adminClient.consumers().dryBind(consumer.getUuid(), null)).hasSize(0);
    }

    @Test
    public void shouldAllowAConsumerToUnregisterAndFreeUpThePool() {
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        ProductDTO product = adminClient.ownerProducts().createProduct(owner.getKey(),
            Products.random());
        adminClient.owners().createPool(owner.getKey(), Pools.random(product)
            .startDate(OffsetDateTime.now().minusSeconds(3)));

        PoolDTO pool =  consumerClient.pools().listPoolsByConsumer(consumer.getUuid()).get(0);
        assertThat(pool).
            returns(0L, PoolDTO::getConsumed);
        consumerClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 1);
        assertThat(adminClient.pools().getPool(pool.getId(), null, null))
            .returns(1L, PoolDTO::getConsumed);
        consumerClient.consumers().deleteConsumer(consumer.getUuid());
        assertThat(adminClient.pools().getPool(pool.getId(), null, null))
            .returns(0L, PoolDTO::getConsumed);
    }

    @Test
    public void shouldAllowAConsumerToUnregisterAndFreeUpThePoolsConsumedInABatch() {
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        ProductDTO product = adminClient.ownerProducts().createProduct(owner.getKey(),
            Products.random());
        adminClient.owners().createPool(owner.getKey(), Pools.random(product)
            .quantity(2L).startDate(OffsetDateTime.now().minusSeconds(3)));
        adminClient.owners().createPool(owner.getKey(), Pools.random(product)
            .quantity(2L).startDate(OffsetDateTime.now().minusSeconds(3)));
        adminClient.owners().createPool(owner.getKey(), Pools.random(product)
            .quantity(2L).startDate(OffsetDateTime.now().minusSeconds(3)));

        List<PoolDTO> pools = consumerClient.pools().listPoolsByConsumer(consumer.getUuid());
        assertThat(pools).hasSize(3);
        pools.forEach(entry ->
            consumerClient.consumers().bindPool(consumer.getUuid(), entry.getId(), 1));

        pools = consumerClient.pools().listPoolsByConsumer(consumer.getUuid());
        pools.forEach(entry ->
            assertThat(entry).returns(1L, PoolDTO::getConsumed));
        consumerClient.consumers().deleteConsumer(consumer.getUuid());
        pools.forEach(entry ->
            assertThat(entry).returns(0L, PoolDTO::getConsumed));
    }

    @Test
    public void shouldBindCorrectQuantityWhenNotSpecified() {
        // When no quantity is sent to the server, the suggested quantity should be attached
        Map<String, String> facts = Map.of("cpu.cpu_socket(s)", "4");
        ProductDTO product1 = Products.random()
            .addAttributesItem(ProductAttributes.Sockets.withValue("1"))
            .addAttributesItem(ProductAttributes.MultiEntitlement.withValue("yes"))
            .addAttributesItem(ProductAttributes.StackingId.withValue(StringUtil.random("stack")));
        product1 = adminClient.ownerProducts().createProduct(owner.getKey(), product1);
        PoolDTO pool = adminClient.owners().createPool(owner.getKey(), Pools.random(product1));

        Set<ConsumerInstalledProductDTO> installed = Set.of(
            new ConsumerInstalledProductDTO().productId(product1.getId()).productName(product1.getName()));
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(facts).installedProducts(installed));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        assertThat(consumerClient.consumers().bindPool(consumer.getUuid(), pool.getId(), null))
            .singleElement()
            .returns(4, x -> x.get("quantity").asInt());
    }

    @Test
    public void shouldBindQuantity1WhenSuggestedIs0AndNotSpecified() {
        // When no quantity is sent to the server, the suggested quantity should be attached
        Map<String, String> facts = Map.of("cpu.cpu_socket(s)", "4");
        ProductDTO product1 = Products.random()
            .addAttributesItem(ProductAttributes.Sockets.withValue("2"))
            .addAttributesItem(ProductAttributes.MultiEntitlement.withValue("yes"))
            .addAttributesItem(ProductAttributes.StackingId.withValue(StringUtil.random("stack")));
        product1 = adminClient.ownerProducts().createProduct(owner.getKey(), product1);
        PoolDTO pool = adminClient.owners().createPool(owner.getKey(), Pools.random(product1));

        Set<ConsumerInstalledProductDTO> installed = Set.of(
            new ConsumerInstalledProductDTO().productId(product1.getId()).productName(product1.getName()));
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(facts).installedProducts(installed));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        // Cover product with 2 2 socket ents, then suggested will be 0
        consumerClient.consumers().bindPool(consumer.getUuid(), pool.getId(), 2);
        assertThat(consumerClient.consumers().bindPool(consumer.getUuid(), pool.getId(), null))
            .singleElement()
            .returns(1, x -> x.get("quantity").asInt());
    }

    @Test
    public void shouldBindCorrectFuturQuantityWhenFullySubscribeToday() {
        Map<String, String> facts = Map.of("cpu.cpu_socket(s)", "4");
        ProductDTO product1 = Products.random()
            .addAttributesItem(ProductAttributes.Sockets.withValue("2"))
            .addAttributesItem(ProductAttributes.MultiEntitlement.withValue("yes"))
            .addAttributesItem(ProductAttributes.StackingId.withValue(StringUtil.random("stack")));
        product1 = adminClient.ownerProducts().createProduct(owner.getKey(), product1);
        PoolDTO currentPool = adminClient.owners().createPool(owner.getKey(), Pools.random(product1));
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC)
            .plusYears(11L).truncatedTo(ChronoUnit.SECONDS);
        PoolDTO futurePool = adminClient.owners().createPool(owner.getKey(), Pools.random(product1)
            .startDate(start));

        Set<ConsumerInstalledProductDTO> installed = Set.of(
            new ConsumerInstalledProductDTO().productId(product1.getId()).productName(product1.getName()));
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(facts).installedProducts(installed));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        // Fully cover the product1 for a year
        consumerClient.consumers().bindPool(consumer.getUuid(), currentPool.getId(), 2);

        assertThat(consumerClient.consumers().bindPool(consumer.getUuid(), futurePool.getId(), null))
            .singleElement()
            .returns(2, x -> x.get("quantity").asInt())
            .returns(start, x ->
            OffsetDateTime.parse(x.get("startDate").asText().substring(0, 20) + "00:00"));
    }

    @Test
    public void shouldUpdateConsumeEntitlementCountOnBindAndRevoke() {
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(adminClient, owner));
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        ProductDTO product1 = adminClient.ownerProducts().createProduct(owner.getKey(),
            Products.random());
        ProductDTO product2 = adminClient.ownerProducts().createProduct(owner.getKey(),
            Products.random());
        ProductDTO product3 = adminClient.ownerProducts().createProduct(owner.getKey(),
            Products.random());

        PoolDTO pool1 = adminClient.owners().createPool(owner.getKey(), Pools.random(product1));
        JsonNode ent1 = consumerClient.consumers().bindPool(consumer.getUuid(), pool1.getId(), 1).get(0);
        assertThat(consumerClient.consumers().getConsumer(consumer.getUuid()))
            .returns(1L, ConsumerDTO::getEntitlementCount);

        PoolDTO pool2 = adminClient.owners().createPool(owner.getKey(), Pools.random(product2));
        JsonNode ent2 = consumerClient.consumers().bindPool(consumer.getUuid(), pool2.getId(), 1).get(0);
        assertThat(consumerClient.consumers().getConsumer(consumer.getUuid()))
            .returns(2L, ConsumerDTO::getEntitlementCount);

        PoolDTO pool3 = adminClient.owners().createPool(owner.getKey(), Pools.random(product3));
        JsonNode ent3 = consumerClient.consumers().bindPool(consumer.getUuid(), pool3.getId(), 1).get(0);
        assertThat(consumerClient.consumers().getConsumer(consumer.getUuid()))
            .returns(3L, ConsumerDTO::getEntitlementCount);

        consumerClient.consumers().unbindByEntitlementId(consumer.getUuid(), ent1.get("id").asText());
        assertThat(consumerClient.consumers().getConsumer(consumer.getUuid()))
            .returns(2L, ConsumerDTO::getEntitlementCount);

        consumerClient.consumers().unbindByEntitlementId(consumer.getUuid(), ent2.get("id").asText());
        assertThat(consumerClient.consumers().getConsumer(consumer.getUuid()))
            .returns(1L, ConsumerDTO::getEntitlementCount);

        consumerClient.consumers().unbindByEntitlementId(consumer.getUuid(), ent3.get("id").asText());
        assertThat(consumerClient.consumers().getConsumer(consumer.getUuid()))
            .returns(0L, ConsumerDTO::getEntitlementCount);
    }

    private String getProductAttributeValue(PoolDTO pool, String name) {
        return pool.getProductAttributes().stream()
            .filter(y -> y.getName().equals(name))
            .findFirst()
            .map(AttributeDTO::getValue)
            .orElse(null);
    }
}
