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
package org.candlepin.spec.consumers;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.collection;
import static org.assertj.core.api.InstanceOfAssertFactories.map;
import static org.candlepin.spec.bootstrap.assertions.JobStatusAssert.assertThatJob;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertGone;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotFound;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.CertificateDTO;
import org.candlepin.dto.api.client.v1.ComplianceStatusDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTOArrayElement;
import org.candlepin.dto.api.client.v1.ContentAccessDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.EnvironmentDTO;
import org.candlepin.dto.api.client.v1.GuestIdDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.ReleaseVerDTO;
import org.candlepin.dto.api.client.v1.RoleDTO;
import org.candlepin.dto.api.client.v1.SubscriptionDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.spec.bootstrap.assertions.OnlyInHosted;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.request.Request;
import org.candlepin.spec.bootstrap.client.request.Response;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Environment;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Permissions;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.builder.Roles;
import org.candlepin.spec.bootstrap.data.builder.Subscriptions;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Set;

@SpecTest
public class ConsumerResourceSpecTest {
    private static final String LIST_CONSUMERS_PATH = "/owners/{owner_key}/consumers";
    private static final String LIST_ENTS_PATH = "/consumers/{consumer_uuid}/entitlements";

    private ApiClient adminClient;
    private OwnerDTO owner;

    @BeforeEach
    public void setup() {
        adminClient = ApiClients.admin();
        owner = adminClient.owners().createOwner(Owners.random());
    }

    @Test
    public void shouldCreateGuestWhenUpdatingConsumerWithGuestIdObject() throws Exception {
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        String expectedGuestId = StringUtil.random("guestId-");
        Map<String, String> expectedAttributes = Map.of(StringUtil.random(5), StringUtil.random(5),
            StringUtil.random(5), StringUtil.random(5));
        GuestIdDTO guestId = new GuestIdDTO()
            .guestId(expectedGuestId)
            .attributes(expectedAttributes);
        consumer.setGuestIds(List.of(guestId));
        ReleaseVerDTO releaseVer = new ReleaseVerDTO()
            .releaseVer("");
        consumer.setReleaseVer(releaseVer);
        adminClient.consumers().updateConsumer(consumer.getUuid(), consumer);

        GuestIdDTO actual = adminClient.guestIds().getGuestId(consumer.getUuid(), expectedGuestId);
        assertThat(actual)
            .isNotNull()
            .returns(expectedGuestId, GuestIdDTO::getGuestId)
            .returns(expectedAttributes, GuestIdDTO::getAttributes);
    }

    @Test
    public void shouldCreateGuestWhenUpdatingConsumerWithGuestIdString() throws Exception {
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        ReleaseVerDTO releaseVer = new ReleaseVerDTO()
            .releaseVer("");
        consumer.setReleaseVer(releaseVer);
        JsonNode consumerRoot = ApiClient.MAPPER.valueToTree(consumer);
        ObjectNode objectNode = (ObjectNode) consumerRoot;

        ArrayNode arrayNode = ApiClient.MAPPER.createArrayNode();
        String expectedGuestId1 = StringUtil.random("guest-");
        String expectedGuestId2 = StringUtil.random("guest-");
        arrayNode.add(expectedGuestId1);
        arrayNode.add(expectedGuestId2);
        objectNode.putPOJO("guestIds", arrayNode);

        // ObjectMapper.valueToTree converts the OffsetDateTimes to epoch values which
        // is not acceped by the server, so this is cleaning up the json to be accepted.
        objectNode.put("created", consumer.getCreated().toString());
        objectNode.put("updated", consumer.getUpdated().toString());
        ObjectNode nullNode = null;
        objectNode.set("lastCheckin", nullNode);
        objectNode.set("idCert", nullNode);
        objectNode.set("serial", nullNode);

        Response response = Request.from(adminClient)
            .setPath("/consumers/{consumer_uuid}")
            .setMethod("PUT")
            .setPathParam("consumer_uuid", consumer.getUuid())
            .setBody(objectNode.toString().getBytes())
            .execute();

        assertThat(response)
            .isNotNull()
            .returns(204, Response::getCode);

        GuestIdDTO actual = adminClient.guestIds().getGuestId(consumer.getUuid(), expectedGuestId1);
        assertThat(actual)
            .isNotNull()
            .returns(expectedGuestId1, GuestIdDTO::getGuestId);

        actual = adminClient.guestIds().getGuestId(consumer.getUuid(), expectedGuestId2);
        assertThat(actual)
            .isNotNull()
            .returns(expectedGuestId2, GuestIdDTO::getGuestId);
    }

    @Test
    public void shouldFetchConsumersWithFacts() {
        this.adminClient.consumers().createConsumer(Consumers.random(this.owner));

        ConsumerDTO target = Consumers.random(this.owner)
            .putFactsItem("fact1", "value1");

        target = this.adminClient.consumers().createConsumer(target);

        ConsumerDTO decoy = Consumers.random(this.owner)
            .putFactsItem("fact2", "value2");

        this.adminClient.consumers().createConsumer(decoy);


        List<String> facts = List.of("fact1:value1");

        List<ConsumerDTOArrayElement> output = this.adminClient.consumers()
            .searchConsumers(null, null, this.owner.getKey(), null, null, facts, null, null, null, null);

        assertThat(output)
            .isNotNull()
            .singleElement()
            .isNotNull()
            .returns(target.getUuid(), ConsumerDTOArrayElement::getUuid);
    }

    @Test
    public void shouldNotAllowSettingEntitlementCountOnRegister() {
        ConsumerDTO consumer = adminClient.consumers()
            .createConsumer(Consumers.random(owner).entitlementCount(3L));

        assertThat(consumer)
            .isNotNull()
            .returns(0L, ConsumerDTO::getEntitlementCount);
    }

    @Test
    public void shouldNotAllowCopyingIdCertToOtherConsumers() {
        ConsumerDTO initialConsumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        CertificateDTO expectedIdCert = initialConsumer.getIdCert();

        ConsumerDTO newConsumer = adminClient.consumers()
            .createConsumer(Consumers.random(owner).idCert(expectedIdCert));

        assertThat(newConsumer)
            .isNotNull()
            .doesNotReturn(expectedIdCert, ConsumerDTO::getIdCert);
    }

    @Test
    public void shouldGetConsumerContentAccess() {
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));

        ContentAccessDTO contAccess = adminClient.consumers().getContentAccessForConsumer(consumer.getUuid());

        assertThat(contAccess)
            .returns(Owners.ENTITLEMENT_ACCESS_MODE, ContentAccessDTO::getContentAccessMode)
            .extracting(ContentAccessDTO::getContentAccessModeList, as(collection(String.class)))
            .containsExactlyInAnyOrder(Owners.ENTITLEMENT_ACCESS_MODE, Owners.SCA_ACCESS_MODE);
    }

    @Test
    public void shouldReceivePagedDataBackWhenRequested() {
        String ownerKey = owner.getKey();
        ProductDTO prod1 = adminClient.ownerProducts().createProductByOwner(ownerKey, Products.random());
        ProductDTO prod2 = adminClient.ownerProducts().createProductByOwner(ownerKey, Products.random());
        ProductDTO prod3 = adminClient.ownerProducts().createProductByOwner(ownerKey, Products.random());
        ProductDTO prod4 = adminClient.ownerProducts().createProductByOwner(ownerKey, Products.random());

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
    public void shouldNotRecalculateQuantityAttributesWhenFetchingEntitlements() {
        ProductDTO prod = adminClient.ownerProducts()
            .createProductByOwner(owner.getKey(), Products.random());
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
            .createProductByOwner(owner.getKey(), Products.random());
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
    public void shouldReceivePagedConsumersBackWhenRequested() {
        ConsumerDTO consumer1 = adminClient.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO consumer2 = adminClient.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO consumer3 = adminClient.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO consumer4 = adminClient.consumers().createConsumer(Consumers.random(owner));

        Response response = Request.from(adminClient)
            .setPath(LIST_CONSUMERS_PATH)
            .setPathParam("owner_key", owner.getKey())
            .addQueryParam("page", "1")
            .addQueryParam("per_page", "1")
            .addQueryParam("order", "asc")
            .execute();
        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertThat(response.deserialize(new TypeReference<List<ConsumerDTOArrayElement>>() {}))
            .singleElement()
            .returns(consumer1.getId(), ConsumerDTOArrayElement::getId);

        response = Request.from(adminClient)
            .setPath(LIST_CONSUMERS_PATH)
            .setPathParam("owner_key", owner.getKey())
            .addQueryParam("page", "2")
            .addQueryParam("per_page", "1")
            .addQueryParam("order", "asc")
            .execute();
        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertThat(response.deserialize(new TypeReference<List<ConsumerDTOArrayElement>>() {}))
            .singleElement()
            .returns(consumer2.getId(), ConsumerDTOArrayElement::getId);

        response = Request.from(adminClient)
            .setPath(LIST_CONSUMERS_PATH)
            .setPathParam("owner_key", owner.getKey())
            .addQueryParam("page", "3")
            .addQueryParam("per_page", "1")
            .addQueryParam("order", "asc")
            .execute();
        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertThat(response.deserialize(new TypeReference<List<ConsumerDTOArrayElement>>() {}))
            .singleElement()
            .returns(consumer3.getId(), ConsumerDTOArrayElement::getId);

        response = Request.from(adminClient)
            .setPath(LIST_CONSUMERS_PATH)
            .setPathParam("owner_key", owner.getKey())
            .addQueryParam("page", "4")
            .addQueryParam("per_page", "1")
            .addQueryParam("order", "asc")
            .execute();
        assertNotNull(response);
        assertEquals(200, response.getCode());
        assertThat(response.deserialize(new TypeReference<List<ConsumerDTOArrayElement>>() {}))
            .singleElement()
            .returns(consumer4.getId(), ConsumerDTOArrayElement::getId);
    }

    @Test
    public void shouldSetComplianceStatusAndUpdateComplianceStatus() {
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));
        assertThat(consumer).returns("valid", ConsumerDTO::getEntitlementStatus);
        ProductDTO prod = adminClient.ownerProducts()
            .createProductByOwner(owner.getKey(), Products.random());

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
    public void shouldListCompliances() {
        UserDTO user1 = createUserTypeAllAccess(adminClient, owner);
        ApiClient user1Client = ApiClients.basic(user1);
        ConsumerDTO consumer1 = user1Client.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO consumer2 = user1Client.consumers().createConsumer(Consumers.random(owner));

        Map<String, ComplianceStatusDTO> compliance = user1Client.consumers()
            .getComplianceStatusList(List.of(consumer1.getUuid(), consumer2.getUuid()));

        assertThat(compliance)
            .hasSize(2)
            .containsKeys(consumer1.getUuid(), consumer2.getUuid());
    }

    @Test
    public void shouldFilterCompliancesTheUserDoesNotOwn() {
        UserDTO user1 = createUserTypeAllAccess(adminClient, owner);
        ApiClient user1Client = ApiClients.basic(user1);
        ConsumerDTO consumer1 = user1Client.consumers().createConsumer(Consumers.random(owner));

        OwnerDTO owner2 = adminClient.owners().createOwner(Owners.random());
        ConsumerDTO consumer2 = adminClient.consumers().createConsumer(Consumers.random(owner2));

        Map<String, ComplianceStatusDTO> compliance = user1Client.consumers()
            .getComplianceStatusList(List.of(consumer1.getUuid(), consumer2.getUuid()));

        assertThat(compliance)
            .hasSize(1)
            .containsKeys(consumer1.getUuid());
    }

    @Test
    public void shouldNotLetConsumerUpdateEnvironmentWithIncorrectEnvName() {
        UserDTO user = createUserTypeAllAccess(adminClient, owner);
        ApiClient userClient = ApiClients.basic(user);
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        assertNotFound(() -> consumerClient.consumers()
            .updateConsumer(StringUtil.random("uuid-"), new ConsumerDTO()
            .environment(new EnvironmentDTO().name(StringUtil.random("name-")))));
    }

    @Test
    public void shouldLetConsumerUpdateEnvironmentWithValidEnvNameOnly() {
        UserDTO user = createUserTypeAllAccess(adminClient, owner);
        ApiClient userClient = ApiClients.basic(user);
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        EnvironmentDTO env = adminClient.owners().createEnv(owner.getKey(), Environment.random());
        assertNull(consumer.getEnvironment());

        consumerClient.consumers().updateConsumer(consumer.getUuid(), new ConsumerDTO()
            .environments(List.of(new EnvironmentDTO().name(env.getName()))));
        consumer = adminClient.consumers().getConsumer(consumer.getUuid());

        assertThat(consumer.getEnvironments())
            .singleElement()
            .returns(env.getId(), EnvironmentDTO::getId);
    }

    @Test
    public void shouldLetConsumerUpdateEnvironmentWithValidEnvIdOnly() {
        UserDTO user = createUserTypeAllAccess(adminClient, owner);
        ApiClient userClient = ApiClients.basic(user);
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        EnvironmentDTO env = adminClient.owners().createEnv(owner.getKey(), Environment.random());
        assertNull(consumer.getEnvironment());

        consumerClient.consumers().updateConsumer(consumer.getUuid(), new ConsumerDTO()
            .environments(List.of(new EnvironmentDTO().id(env.getId()))));
        consumer = adminClient.consumers().getConsumer(consumer.getUuid());

        assertThat(consumer.getEnvironments())
            .singleElement()
            .returns(env.getId(), EnvironmentDTO::getId);
    }

    @Test
    public void shouldLetNotConsumerUpdateEnvironmentWithIncorrectEnvId() {
        UserDTO user = createUserTypeAllAccess(adminClient, owner);
        ApiClient userClient = ApiClients.basic(user);
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        adminClient.owners().createEnv(owner.getKey(), Environment.random());
        assertNull(consumer.getEnvironment());

        assertNotFound(() -> consumerClient.consumers().updateConsumer(consumer.getUuid(), new ConsumerDTO()
            .environments(List.of(new EnvironmentDTO().id(StringUtil.random("invalid-"))))));
    }

    @Test
    public void shouldReturnGoneStatusCodeForDeletedConsumers() {
        UserDTO user = createUserTypeAllAccess(adminClient, owner);
        ApiClient userClient = ApiClients.basic(user);
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        consumerClient.consumers().deleteConsumer(consumer.getUuid());

        assertGone(() -> adminClient.consumers().getConsumer(consumer.getUuid()));
    }

    @Test
    public void shouldReturnAGoneStatusForAConsumerWithAnInvalidIdentityCert() {
        UserDTO user = createUserTypeAllAccess(adminClient, owner);
        ApiClient userClient = ApiClients.basic(user);
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);

        consumerClient.consumers().deleteConsumer(consumer.getUuid());

        assertGone(() -> consumerClient.consumers().deleteConsumer(consumer.getUuid()));
    }

    @Test
    public void shouldAllowSuperAdminsToSeeAllConsumers() {
        ConsumerDTO consumer1 = adminClient.consumers().createConsumer(Consumers.random(owner));
        OwnerDTO owner2 = adminClient.owners().createOwner(Owners.random());
        ConsumerDTO consumer2 = adminClient.consumers().createConsumer(Consumers.random(owner2));

        List<ConsumerDTOArrayElement> consumers = adminClient.consumers()
            .searchConsumers(null, Set.of("system"), null, null, null, null, null, null, null, null);

        assertThat(consumers)
            .extracting(ConsumerDTOArrayElement::getUuid)
            .contains(consumer1.getUuid(), consumer2.getUuid());
    }

    @Test
    public void shouldAllowSuperAdminsToQueryConsumersById() {
        ConsumerDTO consumer1 = adminClient.consumers().createConsumer(Consumers.random(owner));
        OwnerDTO owner2 = adminClient.owners().createOwner(Owners.random());
        ConsumerDTO consumer2 = adminClient.consumers().createConsumer(Consumers.random(owner2));
        ConsumerDTO consumer3 = adminClient.consumers().createConsumer(Consumers.random(owner));

        List<ConsumerDTOArrayElement> consumers = adminClient.consumers()
            .searchConsumers(null, null, null, List.of(consumer1.getUuid(), consumer2.getUuid()), null, null,
            null, null, null, null);

        assertThat(consumers)
            .extracting(ConsumerDTOArrayElement::getUuid)
            .contains(consumer1.getUuid(), consumer2.getUuid())
            .doesNotContain(consumer3.getUuid());
    }

    @Test
    public void shouldLetASuperAdminFilterConsumerByOwner() {
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner));

        List<ConsumerDTOArrayElement> consumers = adminClient.consumers()
            .searchConsumers(null, null, owner.getKey(), null, null, null, null, null, null, null);

        assertThat(consumers)
            .singleElement()
            .returns(consumer.getUuid(), ConsumerDTOArrayElement::getUuid);
    }

    @Test
    public void shouldLetASuperAdminSeeAPersonConsumerWithAGivenUsername() {
        UserDTO user = createUserTypeAllAccess(adminClient, owner);
        RoleDTO role = adminClient.roles().createRole(Roles.ownerAll(owner));
        adminClient.roles().addUserToRole(role.getName(), user.getUsername());
        ApiClient userClient = ApiClients.basic(user);
        ConsumerDTO consumer = userClient.consumers().createPersonConsumer(Consumers.random(owner)
            .type(ConsumerTypes.Person.value()), user.getUsername());

        List<ConsumerDTOArrayElement> consumers = adminClient.consumers()
            .searchConsumers(user.getUsername(), Set.of("person"), null, null, null, null, null, null,
            null, null);

        assertThat(consumers)
            .singleElement()
            .returns(consumer.getUuid(), ConsumerDTOArrayElement::getUuid);
    }

    @Test
    public void shouldLetASuperAdminCreatePersonConsumerForAnotherUser() {
        OwnerDTO owner2 = adminClient.owners().createOwner(Owners.random());
        UserDTO user = createUserTypeAllAccess(adminClient, owner2);
        RoleDTO role = adminClient.roles().createRole(Roles.ownerAll(owner2));
        adminClient.roles().addUserToRole(role.getName(), user.getUsername());
        ApiClient userClient = ApiClients.basic(user);
        ConsumerDTO consumer = userClient.consumers().createPersonConsumer(Consumers.random(owner2)
            .type(ConsumerTypes.Person.value()), user.getUsername());

        List<ConsumerDTOArrayElement> consumers = adminClient.consumers()
            .searchConsumers(user.getUsername(), Set.of("person"), owner2.getKey(), null, null, null,
            null, null, null, null);

        assertThat(consumers)
            .singleElement()
            .returns(consumer.getUuid(), ConsumerDTOArrayElement::getUuid);
    }

    @Test
    public void shouldNotAllowAnOwnerAdminCreatePersonConsumerForAnotherOwner() {
        UserDTO user = createUserTypeAllAccess(adminClient, owner);
        RoleDTO role = adminClient.roles().createRole(Roles.ownerAll(owner));
        adminClient.roles().addUserToRole(role.getName(), user.getUsername());
        ApiClient userClient = ApiClients.basic(user);
        userClient.consumers().createPersonConsumer(Consumers.random(owner)
            .type(ConsumerTypes.Person.value()), user.getUsername());

        OwnerDTO owner2 = adminClient.owners().createOwner(Owners.random());
        UserDTO user2 = createUserTypeAllAccess(adminClient, owner2);
        RoleDTO role2 = adminClient.roles().createRole(Roles.ownerAll(owner2));
        adminClient.roles().addUserToRole(role2.getName(), user2.getUsername());
        adminClient.consumers().createConsumer(Consumers.random(owner2));
        ApiClient user2Client = ApiClients.basic(user2);

        assertNotFound(() -> user2Client.consumers().createPersonConsumer(Consumers.random(owner)
            .type(ConsumerTypes.Person.value()), user.getUsername()));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = {"", "#something", "bar$%camp", "문자열이 아님"})
    public void shouldNotAllowSuperAdminToCreateConsumerWithInvalidName(String name) {
        assertBadRequest(() -> adminClient.consumers()
            .createConsumer(Consumers.random(owner).name(name)));
    }

    @Test
    public void shouldReturnNotFoundForANonExistentConsumer() {
        adminClient.consumers().createConsumer(Consumers.random(owner));

        assertNotFound(() -> adminClient.consumers().getConsumer(StringUtil.random("unknown")));
    }

    @Test
    public void shouldReturnNotFoundWhenCheckingIfANonExistentConsumerExists() {
        adminClient.consumers().createConsumer(Consumers.random(owner));

        assertNotFound(() -> adminClient.consumers().consumerExists(StringUtil.random("unknown")));
    }

    private UserDTO createUserTypeAllAccess(ApiClient client, OwnerDTO owner) {
        return UserUtil.createWith(client,
            Permissions.USERNAME_CONSUMERS.all(owner),
            Permissions.OWNER_POOLS.all(owner),
            Permissions.ATTACH.all(owner));
    }
}
