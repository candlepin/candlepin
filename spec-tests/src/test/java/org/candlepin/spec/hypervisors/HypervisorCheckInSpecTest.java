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
package org.candlepin.spec.hypervisors;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotFound;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.dto.api.client.v1.AsyncJobStatusDTO;
import org.candlepin.dto.api.client.v1.AttributeDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTOArrayElement;
import org.candlepin.dto.api.client.v1.ConsumerInstalledProductDTO;
import org.candlepin.dto.api.client.v1.ContentDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.GuestIdDTO;
import org.candlepin.dto.api.client.v1.GuestIdDTOArrayElement;
import org.candlepin.dto.api.client.v1.HypervisorIdDTO;
import org.candlepin.dto.api.client.v1.HypervisorUpdateResultDTO;
import org.candlepin.dto.api.client.v1.NestedOwnerDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.ProvidedProductDTO;
import org.candlepin.dto.api.client.v1.StatusDTO;
import org.candlepin.dto.api.client.v1.SubscriptionDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.resource.HostedTestApi;
import org.candlepin.resource.client.v1.OwnerContentApi;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.spec.bootstrap.assertions.OnlyInHosted;
import org.candlepin.spec.bootstrap.assertions.OnlyInStandalone;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Contents;
import org.candlepin.spec.bootstrap.data.builder.HypervisorTestData;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.builder.Subscriptions;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


@SpecTest
public class HypervisorCheckInSpecTest {
    private static ApiClient client;
    private static OwnerClient ownerApi;
    private static OwnerProductApi ownerProductApi;
    private static OwnerContentApi ownerContentApi;
    private HostedTestApi hostedTestApi;
    private static String reporterId = "test_reporter";
    private OwnerDTO owner;
    private ApiClient userClient;


    @BeforeAll
    public static void beforeAll() throws Exception {
        client = ApiClients.admin();
        ownerApi = client.owners();
        ownerProductApi = client.ownerProducts();
        ownerContentApi = client.ownerContent();
    }

    private PoolDTO createVirtLimitProductPools(OwnerDTO owner, ConsumerDTO hostConsumer) {
        ProductDTO virtLimitProduct = Products.randomSKU()
            .addAttributesItem(new AttributeDTO().name("virt_limit").value("3"));
        virtLimitProduct = ownerProductApi.createProduct(owner.getKey(), virtLimitProduct);

        PoolDTO pool1 = Pools.random(virtLimitProduct);
        pool1 = ownerApi.createPool(owner.getKey(), pool1);
        PoolDTO pool2 = Pools.random(virtLimitProduct);
        ownerApi.createPool(owner.getKey(), pool2);

        ApiClient hostConsumerClient = ApiClients.ssl(hostConsumer);
        hostConsumerClient.consumers().bindPool(hostConsumer.getUuid(), pool1.getId(), 1);
        return getVirtLimitPool(ownerApi.listOwnerPools(owner.getKey()));
    }

    private void setupOwnerUserClient() {
        owner = ownerApi.createOwner(Owners.random());
        UserDTO user = UserUtil.createUser(client, owner);
        userClient = ApiClients.basic(user.getUsername(), user.getPassword());
    }

    @Test
    public void shouldAddConsumerToCreatedWhenNewHostIdAndNoGuestsReported() throws Exception {
        setupOwnerUserClient();
        HypervisorTestData data = new HypervisorTestData();

        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, userClient,
            data.getExpectedHostName(), data.getExpectedHostHypervisorId(), new ArrayList<>(), null,
            reporterId, true);
        // Should only  have a result entry for created.
        confirmResultDataCounts(resultData, 1, 0, 0, 0);
        assertEquals(data.getExpectedHostName(), resultData.getCreated().iterator().next().getName());
        NestedOwnerDTO resultOwner = resultData.getCreated().iterator().next().getOwner();
        assertEquals(owner.getKey(), resultOwner.getKey());

        //  verify our created consumer is correct.
        ConsumerDTO createdConsumer = userClient.consumers().getConsumer(
            resultData.getCreated().iterator().next().getUuid());
        assertEquals(data.getExpectedHostName(), createdConsumer.getName());
        assertNull(createdConsumer.getIdCert());

        // Test get_owner_hypervisors works, should return all
        assertEquals(1, ownerApi.getHypervisors(owner.getKey(), null).size());
        // Test lookup with hypervisor ids
        assertEquals(1, ownerApi.getHypervisors(owner.getKey(),
            List.of(data.getExpectedHostHypervisorId())).size());
        // Test lookup with nonexistant hypervisor id
        assertEquals(0, ownerApi.getHypervisors(
            owner.getKey(), List.of("non existent")).size());
        // verify last checkin time is updated
        assertNotNull(createdConsumer.getLastCheckin());
    }

    @Test
    public void shouldSupportPagingForGettingHypervisors() throws Exception {
        setupOwnerUserClient();
        HypervisorTestData data1 = new HypervisorTestData();
        HypervisorTestData data2 = new HypervisorTestData();

        hypervisorCheckin(owner, userClient,
            data1.getExpectedHostName(), data1.getExpectedHostHypervisorId(), new ArrayList<>(), null,
            reporterId, true);
        hypervisorCheckin(owner, userClient,
            data2.getExpectedHostName(), data2.getExpectedHostHypervisorId(), new ArrayList<>(), null,
            reporterId, true);

        // By owner
        assertHypervisors(ownerApi.getHypervisors(owner.getKey(), null, 1, 2, "asc", "hypervisorId"),
            data1.getExpectedHostHypervisorId(), data2.getExpectedHostHypervisorId());
        assertHypervisors(ownerApi.getHypervisors(owner.getKey(), null, 1, 2, "desc", "hypervisorId"),
            data2.getExpectedHostHypervisorId(), data1.getExpectedHostHypervisorId());
        assertHypervisors(ownerApi.getHypervisors(owner.getKey(), null, 1, 1, "asc", "hypervisorId"),
            data1.getExpectedHostHypervisorId());
        assertHypervisors(ownerApi.getHypervisors(owner.getKey(), null, 1, 1, "desc", "hypervisorId"),
            data2.getExpectedHostHypervisorId());

        assertHypervisors(ownerApi.getHypervisors(owner.getKey(), List.of(
                data1.getExpectedHostHypervisorId(),
                data2.getExpectedHostHypervisorId()
            ), 1, 2, "asc", "hypervisorId"),
            data1.getExpectedHostHypervisorId(), data2.getExpectedHostHypervisorId());
        assertHypervisors(ownerApi.getHypervisors(owner.getKey(), List.of(
                data1.getExpectedHostHypervisorId(),
                data2.getExpectedHostHypervisorId()
            ), 1, 2, "desc", "hypervisorId"),
            data2.getExpectedHostHypervisorId(), data1.getExpectedHostHypervisorId());
        assertHypervisors(ownerApi.getHypervisors(owner.getKey(), List.of(
                data1.getExpectedHostHypervisorId(),
                data2.getExpectedHostHypervisorId()
            ), 1, 1, "asc", "hypervisorId"),
            data1.getExpectedHostHypervisorId());
        assertHypervisors(ownerApi.getHypervisors(owner.getKey(), List.of(
                data1.getExpectedHostHypervisorId(),
                data2.getExpectedHostHypervisorId()
            ), 1, 1, "desc", "hypervisorId"),
            data2.getExpectedHostHypervisorId());
    }

    private static void assertHypervisors(List<ConsumerDTOArrayElement> hypervisors, String... expected) {
        assertThat(hypervisors)
            .hasSize(expected.length)
            .map(ConsumerDTOArrayElement::getHypervisorId)
            .map(HypervisorIdDTO::getHypervisorId)
            .containsExactlyElementsOf(Arrays.stream(expected).map(String::toLowerCase).toList());
    }

    @Test
    public void shouldAddConsumerToCreatedWhenNewHostIdAndGuestsWereReported() throws Exception {
        setupOwnerUserClient();
        HypervisorTestData data = new HypervisorTestData();

        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, userClient,
            data.getExpectedHostName(), data.getExpectedHostHypervisorId(), data.getExpectedGuestIds(),
            null, reporterId, true);
        // Should only  have a result entry for created.
        confirmResultDataCounts(resultData, 1, 0, 0, 0);
        assertEquals(data.getExpectedHostName(), resultData.getCreated().iterator().next().getName());
        NestedOwnerDTO resultOwner = resultData.getCreated().iterator().next().getOwner();
        assertEquals(owner.getKey(), resultOwner.getKey());

        // verify our created consumer is correct.
        ConsumerDTO createdConsumer = userClient.consumers().getConsumer(
            resultData.getCreated().iterator().next().getUuid());
        assertEquals(data.getExpectedHostName(), createdConsumer.getName());
        // verify last checkin time is updated
        assertNotNull(createdConsumer.getLastCheckin());
    }

    @Test
    public void shouldNotAddNewConsumerWhenCreateMissingIsFalse() throws Exception {
        setupOwnerUserClient();
        HypervisorTestData data = new HypervisorTestData();

        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, userClient,
            data.getExpectedHostName(), data.getExpectedHostHypervisorId(),  data.getExpectedGuestIds(),
            null, reporterId, false);
        // Should only have a result entry for failed.
        confirmResultDataCounts(resultData, 0, 0, 0, 1);
    }

    @Test
    public void shouldAddConsumerToUpdatedWhenGuestIdsAreUpdated() throws Exception {
        setupOwnerUserClient();
        HypervisorTestData data = new HypervisorTestData();

        ConsumerDTO hostConsumer = createHostConsumer(owner, userClient, data);
        hypervisorCheckin(owner, userClient, data.getExpectedHostName(),
            data.getExpectedHostHypervisorId(), data.getExpectedGuestIds(),
            Map.of("test_fact", "fact_value"), reporterId, true);
        ConsumerDTO oldCheckIn = userClient.consumers().getConsumer(hostConsumer.getUuid());
        //because of MySql not using milliseconds
        sleep(2000);

        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, userClient,
            data.getExpectedHostName(), data.getExpectedHostHypervisorId(),  List.of("g1", "g2"),
            Map.of("test_fact", "fact_value"), reporterId, true);
        // Should only  have a result entry for updated.
        confirmResultDataCounts(resultData, 0, 1, 0, 0);
        assertEquals(data.getExpectedHostName(), resultData.getUpdated().iterator().next().getName());
        NestedOwnerDTO resultOwner = resultData.getUpdated().iterator().next().getOwner();
        assertEquals(owner.getKey(), resultOwner.getKey());

        // verify our created consumer is correct.
        ConsumerDTO updatedConsumer = userClient.consumers().getConsumer(
            resultData.getUpdated().iterator().next().getUuid());
        assertEquals(data.getExpectedHostName(), updatedConsumer.getName());
        // verify last checkin time is updated
        assertNotNull(updatedConsumer.getLastCheckin());
        assertNotEquals(oldCheckIn.getLastCheckin(), updatedConsumer.getLastCheckin());
    }

    @Test
    public void shouldAddConsumerToUnchangedWhenSameGuestIdsAreSent() throws Exception {
        setupOwnerUserClient();
        HypervisorTestData data = new HypervisorTestData();

        ConsumerDTO hostConsumer = createHostConsumer(owner, userClient, data);
        hypervisorCheckin(owner, userClient, data.getExpectedHostName(),
            data.getExpectedHostHypervisorId(), data.getExpectedGuestIds(),
            Map.of("test_fact", "fact_value"), reporterId, true);
        ConsumerDTO oldCheckIn = userClient.consumers().getConsumer(hostConsumer.getUuid());
        //because of MySql not using milliseconds
        sleep(2000);

        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, userClient,
            data.getExpectedHostName(), data.getExpectedHostHypervisorId(), data.getExpectedGuestIds(),
            Map.of("test_fact", "fact_value"), reporterId, true);
        // Should only  have a result entry for unchanged.
        confirmResultDataCounts(resultData, 0, 0, 1, 0);
        assertEquals(data.getExpectedHostName(), resultData.getUnchanged().iterator().next().getName());
        NestedOwnerDTO resultOwner = resultData.getUnchanged().iterator().next().getOwner();
        assertEquals(owner.getKey(), resultOwner.getKey());

        // verify our created consumer is correct.
        ConsumerDTO unchangedConsumer = userClient.consumers().getConsumer(
            resultData.getUnchanged().iterator().next().getUuid());
        assertEquals(data.getExpectedHostName(), unchangedConsumer.getName());
        // verify last checkin time is updated
        assertNotNull(unchangedConsumer.getLastCheckin());
        assertNotEquals(oldCheckIn.getLastCheckin(), unchangedConsumer.getLastCheckin());
    }

    @Test
    public void shouldAddConsumerToUnchangedWhenComparingEmptyGuestIdLists() throws Exception {
        setupOwnerUserClient();
        String hostHypId = StringUtil.random("host");
        String hostHypName = StringUtil.random("name");
        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, userClient, hostHypName,
            hostHypId, new ArrayList<>(), null, reporterId, true);
        // Should only  have a result entry for created.
        confirmResultDataCounts(resultData, 1, 0, 0, 0);
        assertEquals(hostHypName, resultData.getCreated().iterator().next().getName());

        // Do the same update with [] and it should be considered unchanged.
        resultData = hypervisorCheckin(owner, userClient, hostHypName, hostHypId, new ArrayList<>(),
            null, reporterId, true);
        confirmResultDataCounts(resultData, 0, 0, 1, 0);
        assertEquals(hostHypName, resultData.getUnchanged().iterator().next().getName());
        NestedOwnerDTO resultOwner = resultData.getUnchanged().iterator().next().getOwner();
        assertEquals(owner.getKey(), resultOwner.getKey());

        // verify our unchanged consumer is correct.
        ConsumerDTO unchangedConsumer = userClient.consumers().getConsumer(
            resultData.getUnchanged().iterator().next().getUuid());
        assertEquals(hostHypName, unchangedConsumer.getName());
    }

    @Test
    public void shouldAddHostAndAssociateGuests() throws IOException {
        setupOwnerUserClient();
        HypervisorTestData data = new HypervisorTestData();

        ConsumerDTO hostConsumer = createHostConsumer(owner, userClient, data);
        hypervisorCheckin(owner, userClient, data.getExpectedHostName(),
            data.getExpectedHostHypervisorId(), data.getExpectedGuestIds(),
            Map.of("test_fact", "fact_value"), reporterId, true);
        ConsumerDTO consumer = userClient.consumers().getConsumer(hostConsumer.getUuid());
        checkHypervisorConsumer(consumer, data.getExpectedHostName(), data.getExpectedGuestIds(), reporterId);
    }

    @Test
    public void shouldUdateHostGuestIdsAsConsumer() throws Exception {
        setupOwnerUserClient();
        HypervisorTestData data = new HypervisorTestData();

        ConsumerDTO hostConsumer = createHostConsumer(owner, userClient, data);
        hypervisorCheckin(owner, userClient, data.getExpectedHostName(),
            data.getExpectedHostHypervisorId(), data.getExpectedGuestIds(),
            Map.of("test_fact", "fact_value"), reporterId, true);
        ApiClient hostConsumerClient = ApiClients.ssl(hostConsumer);
        asyncUpdateGuestIdsTest(owner, hostConsumerClient, hostConsumer, null);
    }

    @Test
    public void shouldPersistReportedIdOnHostGuestMappingsUpdate() throws Exception {
        setupOwnerUserClient();
        HypervisorTestData data = new HypervisorTestData();

        ConsumerDTO hostConsumer = createHostConsumer(owner, userClient, data);
        hypervisorCheckin(owner, userClient, data.getExpectedHostName(),
            data.getExpectedHostHypervisorId(), data.getExpectedGuestIds(),
            Map.of("test_fact", "fact_value"), reporterId, true);
        ApiClient hostConsumerClient = ApiClients.ssl(hostConsumer);
        asyncUpdateGuestIdsTest(owner, hostConsumerClient, hostConsumer, "Lois Lane");
    }

    @Test
    public void shouldUpdateHostGuestIdsAsUser() throws Exception {
        setupOwnerUserClient();
        HypervisorTestData data = new HypervisorTestData();

        ConsumerDTO hostConsumer = createHostConsumer(owner, userClient, data);
        hypervisorCheckin(owner, userClient, data.getExpectedHostName(),
            data.getExpectedHostHypervisorId(), data.getExpectedGuestIds(),
            Map.of("test_fact", "fact_value"), reporterId, true);
        ApiClient hostConsumerClient = ApiClients.ssl(hostConsumer);
        asyncUpdateGuestIdsTest(owner, userClient, hostConsumer, null);
    }

    @Test
    @OnlyInStandalone
    public void shouldNotRevokeGuestEntitlementsWhenGuestNoLongerMapped() throws Exception {
        setupOwnerUserClient();
        HypervisorTestData data = new HypervisorTestData();

        ConsumerDTO hostConsumer = createHostConsumer(owner, userClient, data);
        ConsumerDTO guestConsumer1 = createGuestConsumer(owner, userClient, data.getGuest1VirtUuid());
        ConsumerDTO guestConsumer2 = createGuestConsumer(owner, userClient, data.getGuest2VirtUuid());
        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, userClient,
            data.getExpectedHostName(), data.getExpectedHostHypervisorId(), data.getExpectedGuestIds(),
            Map.of("test_fact", "fact_value"), reporterId, true);
        confirmResultDataCounts(resultData, 0, 1, 0, 0);

        PoolDTO virtLimitPool = createVirtLimitProductPools(owner, hostConsumer);

        ApiClient guest1Client = ApiClients.ssl(guestConsumer1);
        guest1Client.consumers().bindPool(guestConsumer1.getUuid(), virtLimitPool.getId(), 1);
        assertEquals(1, guest1Client.consumers().listEntitlements(guestConsumer1.getUuid()).size());
        // Host stops reporting guest:
        resultData = hypervisorCheckin(owner, userClient, data.getExpectedHostName(),
            data.getExpectedHostHypervisorId(), List.of(data.getGuest2VirtUuid()),
            Map.of("test_fact", "fact_value"), reporterId, true);
        confirmResultDataCounts(resultData, 0, 1, 0, 0);
        assertEquals(data.getExpectedHostName(), resultData.getUpdated().iterator().next().getName());
        NestedOwnerDTO resultOwner = resultData.getUpdated().iterator().next().getOwner();
        assertEquals(owner.getKey(), resultOwner.getKey());

        // Entitlement should not be gone
        assertEquals(1, guest1Client.consumers().listEntitlements(guestConsumer1.getUuid()).size());
    }

    @Test
    public void shouldNotRevokeHostEntitlementsWhenGuestIdListIsEmpty() throws Exception {
        setupOwnerUserClient();
        HypervisorTestData data = new HypervisorTestData();

        ConsumerDTO hostConsumer = createHostConsumer(owner, userClient, data);
        createVirtLimitProductPools(owner, hostConsumer);
        ApiClient hostConsumerClient = ApiClients.ssl(hostConsumer);
        assertEquals(1, hostConsumerClient.consumers().listEntitlements(hostConsumer.getUuid()).size());
        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, userClient,
            data.getExpectedHostName(), data.getExpectedHostHypervisorId(), new ArrayList<>(),
            Map.of("test_fact", "fact_value"), reporterId, true);
        confirmResultDataCounts(resultData, 0, 0, 1, 0);
        assertEquals(data.getExpectedHostName(), resultData.getUnchanged().iterator().next().getName());
        NestedOwnerDTO resultOwner = resultData.getUnchanged().iterator().next().getOwner();
        assertEquals(owner.getKey(), resultOwner.getKey());

        // Entitlement should not be gone
        assertEquals(1, hostConsumerClient.consumers().listEntitlements(hostConsumer.getUuid()).size());
    }

    @Test
    @OnlyInStandalone
    public void shouldNotRevokeHostAndGuestEntitlementsWhenGuestIdListIsEmpty() throws Exception {
        setupOwnerUserClient();
        HypervisorTestData data = new HypervisorTestData();

        ConsumerDTO hostConsumer = createHostConsumer(owner, userClient, data);
        ApiClient hostConsumerClient = ApiClients.ssl(hostConsumer);
        ConsumerDTO guestConsumer1 = createGuestConsumer(owner, userClient, data.getGuest1VirtUuid());
        ApiClient guest1Client = ApiClients.ssl(guestConsumer1);
        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, userClient,
            data.getExpectedHostName(), data.getExpectedHostHypervisorId(), data.getExpectedGuestIds(),
            Map.of("test_fact", "fact_value"), reporterId, true);
        confirmResultDataCounts(resultData, 0, 1, 0, 0);

        PoolDTO virtLimitPool = createVirtLimitProductPools(owner, hostConsumer);
        guest1Client.consumers().bindPool(guestConsumer1.getUuid(), virtLimitPool.getId(), 1);
        assertEquals(1, guest1Client.consumers().listEntitlements(guestConsumer1.getUuid()).size());

        resultData = hypervisorCheckin(owner, userClient, data.getExpectedHostName(),
            data.getExpectedHostHypervisorId(), new ArrayList<>(),
            Map.of("test_fact", "fact_value"), reporterId, true);
        confirmResultDataCounts(resultData, 0, 1, 0, 0);
        assertEquals(data.getExpectedHostName(), resultData.getUpdated().iterator().next().getName());
        NestedOwnerDTO resultOwner = resultData.getUpdated().iterator().next().getOwner();
        assertEquals(owner.getKey(), resultOwner.getKey());

        // Entitlements should not be gone
        assertEquals(1, hostConsumerClient.consumers().listEntitlements(hostConsumer.getUuid()).size());
        assertEquals(1, guest1Client.consumers().listEntitlements(guestConsumer1.getUuid()).size());
    }

    @Test
    public void shouldInitializeGuestIdListIstoEmptyWhenCreatingNewHost() throws Exception {
        setupOwnerUserClient();
        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, userClient,
            StringUtil.random("name"), StringUtil.random("hyper"), new ArrayList<>(),
            Map.of("test_fact", "fact_value"), reporterId, true);
        confirmResultDataCounts(resultData, 1, 0, 0, 0);
        assertNotNull(client.guestIds().getGuestIds(resultData.getCreated().iterator().next().getUuid()));
    }

    @Test
    public void shouldSupportMultipleOrgsReportingTheSameCluster() throws Exception {
        OwnerDTO owner1 = ownerApi.createOwner(Owners.random());
        OwnerDTO owner2 = ownerApi.createOwner(Owners.random());
        UserDTO user1 = UserUtil.createUser(client, owner1);
        ApiClient userClient1 = ApiClients.basic(user1.getUsername(), user1.getPassword());
        UserDTO user2 = UserUtil.createUser(client, owner2);
        ApiClient userClient2 = ApiClients.basic(user2.getUsername(), user2.getPassword());
        ConsumerDTO consumer1 = Consumers.random(owner1);
        consumer1 = userClient1.consumers().createConsumer(consumer1);
        ApiClient consumerClient1 = ApiClients.ssl(consumer1);
        ConsumerDTO consumer2 = Consumers.random(owner2);
        consumer2 = userClient2.consumers().createConsumer(consumer2);
        ApiClient consumerClient2 = ApiClients.ssl(consumer2);
        String hostHypId = StringUtil.random("host");
        String hostName = StringUtil.random("name");
        List<String> firstGuestList = List.of("guest1", "guest2", "guest3");
        List<String> secondGuestList = List.of("guest1", "guest2", "guest3", "guest4");

        HypervisorUpdateResultDTO resultData1 = hypervisorCheckin(owner1, consumerClient1, hostName,
            hostHypId, firstGuestList, Map.of("test_fact", "fact_value"), reporterId, true);
        HypervisorUpdateResultDTO resultData2 = hypervisorCheckin(owner2, consumerClient2, hostName,
            hostHypId, firstGuestList, Map.of("test_fact", "fact_value"), reporterId, true);
        // check in each org
        confirmResultDataCounts(resultData1, 1, 0, 0, 0);
        confirmResultDataCounts(resultData2, 1, 0, 0, 0);

        // Now check in each org again
        resultData1 = hypervisorCheckin(owner1, consumerClient1, hostName, hostHypId, firstGuestList,
            Map.of("test_fact", "fact_value"), reporterId, true);
        resultData2 = hypervisorCheckin(owner2, consumerClient2, hostName, hostHypId, firstGuestList,
            Map.of("test_fact", "fact_value"), reporterId, true);
        // Nothing should have changed
        confirmResultDataCounts(resultData1, 0, 0, 1, 0);
        confirmResultDataCounts(resultData2, 0, 0, 1, 0);

        // Send modified data for owner 1, but it shouldn't impact owner 2 at all
        resultData1 = hypervisorCheckin(owner1, consumerClient1, hostName, hostHypId, secondGuestList,
            Map.of("test_fact", "fact_value"), reporterId, true);
        resultData2 = hypervisorCheckin(owner2, consumerClient2, hostName, hostHypId, firstGuestList,
            Map.of("test_fact", "fact_value"), reporterId, true);
        // Now owner 1 should have an update, but owner two should remain the same
        confirmResultDataCounts(resultData1, 0, 1, 0, 0);
        confirmResultDataCounts(resultData2, 0, 0, 1, 0);

    }

    @Test
    public void shouldAllowVirtWhoToUpdateMappings() throws Exception {
        setupOwnerUserClient();
        ApiClient virtClient = createVirtWhoClient(owner, userClient);
        HypervisorTestData data = new HypervisorTestData();

        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, virtClient,
            data.getExpectedHostName(), data.getExpectedHostHypervisorId(), data.getExpectedGuestIds(),
            null, reporterId, true);
        confirmResultDataCounts(resultData, 1, 0, 0, 0);
        assertEquals(data.getExpectedHostName(), resultData.getCreated().iterator().next().getName());
        NestedOwnerDTO resultOwner = resultData.getCreated().iterator().next().getOwner();
        assertEquals(owner.getKey(), resultOwner.getKey());

        resultData = hypervisorCheckin(owner, virtClient, data.getExpectedHostName(),
            data.getExpectedHostHypervisorId(), data.getExpectedGuestIds(), null, reporterId, true);
        confirmResultDataCounts(resultData, 0, 0, 1, 0);
        assertEquals(data.getExpectedHostName(), resultData.getUnchanged().iterator().next().getName());
        resultOwner = resultData.getUnchanged().iterator().next().getOwner();
        assertEquals(owner.getKey(), resultOwner.getKey());

    }

    @Test
    public void shouldBlockVirtWhoIfOwnerDoesNotMatchIdentityCert() throws Exception {
        setupOwnerUserClient();
        OwnerDTO owner2 = ownerApi.createOwner(Owners.random());
        ApiClient virtClient = createVirtWhoClient(owner, userClient);
        HypervisorTestData data = new HypervisorTestData();

        hypervisorCheckin(owner, virtClient, data.getExpectedHostName(),
            data.getExpectedHostHypervisorId(), data.getExpectedGuestIds(), null, reporterId, true);
        assertNotFound(() -> hypervisorCheckin(owner2, virtClient, data.getExpectedHostName(),
            data.getExpectedHostHypervisorId(), data.getExpectedGuestIds(), null, reporterId, true));
    }

    @Test
    public void shouldRaiseBadRequestExceptionIfMappingWasNotProvided() {
        setupOwnerUserClient();
        ApiClient virtClient = createVirtWhoClient(owner, userClient);
        assertBadRequest(() -> virtClient.hypervisors().hypervisorUpdateAsync(
            owner.getKey(), true, reporterId, null));
    }

    @Test
    public void shouldRaiseBadRequestExceptionIfInvalidMappingInputWasProvided() {
        setupOwnerUserClient();
        ApiClient virtClient = createVirtWhoClient(owner, userClient);
        assertBadRequest(() -> virtClient.hypervisors().hypervisorUpdateAsync(
            owner.getKey(), true, reporterId, "test invalid input"));
    }

    @Test
    public void shouldSeeTheCapabilityThatCorrespondsToTheAsyncMethod() {
        StatusDTO status = client.status().status();
        assertTrue(status.getManagerCapabilities().contains("hypervisors_async"));
    }

    @Test
    public void shouldIgnoreHypervisorIdsEqualToTheEmptyString() throws Exception {
        setupOwnerUserClient();
        ApiClient virtClient = createVirtWhoClient(owner, userClient);
        HypervisorTestData data = new HypervisorTestData();

        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, virtClient,
            data.getExpectedHostName(), "", data.getExpectedGuestIds(),
            null, reporterId, true);
        confirmResultDataCounts(resultData, 0, 0, 0, 0);
    }

    @Test
    public void shouldIgnoreGuestIdsEqualToTheEmptyString() throws Exception {
        setupOwnerUserClient();
        ApiClient virtClient = createVirtWhoClient(owner, userClient);
        HypervisorTestData data = new HypervisorTestData();

        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, virtClient,
            data.getExpectedHostName(), data.getExpectedHostHypervisorId(),
            List.of(data.getGuest1VirtUuid(), ""), null, reporterId, true);
        confirmResultDataCounts(resultData, 1, 0, 0, 0);
        String updatedConsumerUuid = resultData.getCreated().iterator().next().getUuid();
        List<GuestIdDTOArrayElement> guestIds =  client.guestIds().getGuestIds(updatedConsumerUuid);
        assertEquals(1, guestIds.size());
        assertEquals(data.getGuest1VirtUuid(), guestIds.get(0).getGuestId());
    }

    @Test
    @OnlyInStandalone
    public void shouldAllowASingleGuestToBeMigratedAndRevokeHostLimitedEnts() throws Exception {
        setupOwnerUserClient();
        HypervisorTestData data1 = new HypervisorTestData();
        HypervisorTestData data2 = new HypervisorTestData();
        String uuid1 = StringUtil.random("uuid");

        ConsumerDTO hostConsumer = createHostConsumer(owner, userClient, data1);
        ConsumerDTO newHostConsumer = createHostConsumer(owner, userClient, data2);
        ConsumerDTO guestConsumer = createGuestConsumer(owner, userClient, uuid1);

        // product and pool
        ProductDTO superAwesome = Products.random()
            .name(StringUtil.random("product"))
            .addAttributesItem(new AttributeDTO().name("virt_limit").value("10"))
            .addAttributesItem(new AttributeDTO().name("host_limited").value("true"));
        ownerProductApi.createProduct(owner.getKey(), superAwesome);
        PoolDTO pool = Pools.random(superAwesome);
        ownerApi.createPool(owner.getKey(), pool);

        ApiClient hostConsumerClient = ApiClients.ssl(hostConsumer);
        ApiClient newHostConsumerClient = ApiClients.ssl(newHostConsumer);
        ApiClient guestConsumerClient = ApiClients.ssl(guestConsumer);

        hypervisorCheckin(owner, userClient, data1.getExpectedHostName(),
            data1.getExpectedHostHypervisorId(), List.of(uuid1), null, reporterId, true);
        List<PoolDTO> pools = hostConsumerClient.pools().listPoolsByConsumer(hostConsumer.getUuid());
        assertEquals(1, pools.size());
        hostConsumerClient.consumers().bindPool(hostConsumer.getUuid(), pools.get(0).getId(), 1);
        newHostConsumerClient.consumers().bindPool(newHostConsumer.getUuid(), pools.get(0).getId(), 1);

        pools = guestConsumerClient.pools().listPoolsByConsumer(guestConsumer.getUuid());
        assertEquals(2, pools.size());
        // Get the guest pool
        PoolDTO guestPool = new PoolDTO();
        for (PoolDTO aPool : pools) {
            if (aPool.getSourceEntitlement() != null) {
                guestPool = aPool;
            }
        }
        List<AttributeDTO> hostAtts = guestPool.getAttributes().stream()
            .filter(x -> "requires_host".equals(x.getName()))
            .collect(Collectors.toList());
        assertEquals(1, hostAtts.size());
        assertEquals(hostConsumer.getUuid(), hostAtts.get(0).getValue());

        // Consume the host limited pool
        guestConsumerClient.consumers().bindPool(guestConsumer.getUuid(), guestPool.getId(), 1);
        //Should have a host with 1 registered guest
        List<ConsumerDTOArrayElement> guestList = client.consumers().getGuests(hostConsumer.getUuid());
        assertEquals(1, guestList.size());
        assertEquals(guestConsumer.getUuid(), guestList.get(0).getUuid());
        List<EntitlementDTO> ents = guestConsumerClient.consumers().listEntitlements(guestConsumer.getUuid());
        assertEquals(1, ents.size());

        // Updating to a new host should remove host specific entitlements
        // because MySql
        sleep(2000);
        hypervisorCheckin(owner, userClient, data2.getExpectedHostName(),
            data2.getExpectedHostHypervisorId(), List.of(uuid1), null, reporterId, true);
        // The guests host limited entitlement should be gone
        assertEquals(0, guestConsumerClient.consumers().listEntitlements(guestConsumer.getUuid(),
            null, true, null, null, null, null, null).size());
    }

    @Nested
    @OnlyInHosted
    public class HypervisorCheckInHostedSpecTest {
        private PoolDTO createVirtLimitProductSubAndBind(OwnerDTO owner, ConsumerDTO hostConsumer) {
            hostedTestApi = client.hosted();
            ProductDTO virtLimitProduct = Products.randomSKU()
                .addAttributesItem(new AttributeDTO().name("virt_limit").value("3"));
            virtLimitProduct = hostedTestApi.createProduct(virtLimitProduct);

            SubscriptionDTO sub1 = Subscriptions.random(owner, virtLimitProduct);
            sub1 = hostedTestApi.createSubscription(sub1);
            SubscriptionDTO sub2 = Subscriptions.random(owner, virtLimitProduct);
            hostedTestApi.createSubscription(sub2);
            AsyncJobStatusDTO refresh = ownerApi.refreshPools(owner.getKey(), false);
            if (refresh != null) {
                AsyncJobStatusDTO status = client.jobs().waitForJob(refresh.getId());
                assertEquals("FINISHED", status.getState());
            }

            final String sub1Id = sub1.getId();
            List<PoolDTO> pools = client.owners().listOwnerPools(owner.getKey());
            List<PoolDTO> sub1Pools = pools.stream()
                .filter(x -> sub1Id.equals(x.getSubscriptionId()) && "NORMAL".equals(x.getType()))
                .collect(Collectors.toList());
            ApiClient hostConsumerClient = ApiClients.ssl(hostConsumer);
            hostConsumerClient.consumers().bindPool(hostConsumer.getUuid(), sub1Pools.get(0).getId(), 1);
            return pools.stream()
                .filter(x -> sub1Id.equals(x.getSubscriptionId()) && "BONUS".equals(x.getType()))
                .collect(Collectors.toList()).get(0);

        }
        @Test
        public void shouldNotRevokeGuestEntitlementsWhenGuestNoLongerMapped() throws Exception {
            setupOwnerUserClient();
            HypervisorTestData data = new HypervisorTestData();

            ConsumerDTO hostConsumer = createHostConsumer(owner, userClient, data);
            ConsumerDTO guestConsumer1 = createGuestConsumer(owner, userClient, data.getGuest1VirtUuid());
            ConsumerDTO guestConsumer2 = createGuestConsumer(owner, userClient, data.getGuest2VirtUuid());
            HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, userClient,
                data.getExpectedHostName(), data.getExpectedHostHypervisorId(), data.getExpectedGuestIds(),
                Map.of("test_fact", "fact_value"), reporterId, true);
            confirmResultDataCounts(resultData, 0, 1, 0, 0);

            PoolDTO virtLimitPool = createVirtLimitProductSubAndBind(owner, hostConsumer);
            ApiClient guest1Client = ApiClients.ssl(guestConsumer1);
            guest1Client.consumers().bindPool(guestConsumer1.getUuid(), virtLimitPool.getId(), 1);
            assertEquals(1, guest1Client.consumers().listEntitlements(guestConsumer1.getUuid()).size());
            // Host stops reporting guest:
            hypervisorCheckin(owner, userClient, data.getExpectedHostName(),
                data.getExpectedHostHypervisorId(), List.of(data.getGuest2VirtUuid()),
                Map.of("test_fact", "fact_value"), reporterId, true);
            confirmResultDataCounts(resultData, 0, 1, 0, 0);
            assertEquals(data.getExpectedHostName(), resultData.getUpdated().iterator().next().getName());
            NestedOwnerDTO resultOwner = resultData.getUpdated().iterator().next().getOwner();
            assertEquals(owner.getKey(), resultOwner.getKey());

            // Entitlement should not be gone
            assertEquals(1, guest1Client.consumers().listEntitlements(guestConsumer1.getUuid()).size());
        }

        @Test
        public void shouldNotRevokeHostAndGuestEntitlementsWhenGuestIdListIsEmpty() throws Exception {
            setupOwnerUserClient();
            HypervisorTestData data = new HypervisorTestData();

            ConsumerDTO hostConsumer = createHostConsumer(owner, userClient, data);
            String hostUuid = hostConsumer.getUuid();
            ApiClient hostConsumerClient = ApiClients.ssl(hostConsumer);
            ConsumerDTO guestConsumer1 = createGuestConsumer(owner, userClient, data.getGuest1VirtUuid());

            PoolDTO virtLimitPool = createVirtLimitProductSubAndBind(owner, hostConsumer);
            ApiClient guest1Client = ApiClients.ssl(guestConsumer1);
            guest1Client.consumers().bindPool(guestConsumer1.getUuid(), virtLimitPool.getId(), 1);
            assertEquals(1, guest1Client.consumers().listEntitlements(guestConsumer1.getUuid()).size());

            HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, userClient,
                data.getExpectedHostName(), data.getExpectedHostHypervisorId(), new ArrayList<>(),
                Map.of("test_fact", "fact_value"), reporterId, true);
            confirmResultDataCounts(resultData, 0, 0, 1, 0);
            assertEquals(data.getExpectedHostName(), resultData.getUnchanged().iterator().next().getName());
            NestedOwnerDTO resultOwner = resultData.getUnchanged().iterator().next().getOwner();
            assertEquals(owner.getKey(), resultOwner.getKey());

            // Entitlements should not be gone
            assertEquals(1, hostConsumerClient.consumers().listEntitlements(hostUuid).size());
            assertEquals(1, guest1Client.consumers().listEntitlements(guestConsumer1.getUuid()).size());
        }

        @Test
        public void shouldAllowASingleGuestToBeMigratedAndRevokeHostLimitedEntsHosted() throws Exception {
            setupOwnerUserClient();
            HypervisorTestData data1 = new HypervisorTestData();
            HypervisorTestData data2 = new HypervisorTestData();
            String uuid1 = StringUtil.random("uuid");

            ConsumerDTO hostConsumer = createHostConsumer(owner, userClient, data1);
            ConsumerDTO newHostConsumer = createHostConsumer(owner, userClient, data2);
            ConsumerDTO guestConsumer = createGuestConsumer(owner, userClient, uuid1);

            // subscription and pool
            HostedTestApi hostedTestApi = client.hosted();
            ProductDTO superAwesome = Products.random()
                .name(StringUtil.random("product"))
                .addAttributesItem(new AttributeDTO().name("virt_limit").value("10"))
                .addAttributesItem(new AttributeDTO().name("host_limited").value("true"));
            hostedTestApi.createProduct(superAwesome);
            SubscriptionDTO sub = Subscriptions.random(owner, superAwesome);
            hostedTestApi.createSubscription(sub);
            AsyncJobStatusDTO refresh = ownerApi.refreshPools(owner.getKey(), false);
            if (refresh != null) {
                AsyncJobStatusDTO status = client.jobs().waitForJob(refresh.getId());
                assertEquals("FINISHED", status.getState());
            }

            ApiClient hostConsumerClient = ApiClients.ssl(hostConsumer);
            ApiClient newHostConsumerClient = ApiClients.ssl(newHostConsumer);
            ApiClient guestConsumerClient = ApiClients.ssl(guestConsumer);

            hypervisorCheckin(owner, userClient, data1.getExpectedHostName(),
                data1.getExpectedHostHypervisorId(), List.of(uuid1), null, reporterId, true);
            List<PoolDTO> pools = hostConsumerClient.pools().listPoolsByConsumer(hostConsumer.getUuid());
            assertEquals(1, pools.size());
            hostConsumerClient.consumers().bindPool(hostConsumer.getUuid(), pools.get(0).getId(), 1);
            newHostConsumerClient.consumers().bindPool(newHostConsumer.getUuid(), pools.get(0).getId(), 1);

            pools = guestConsumerClient.pools().listPoolsByConsumer(guestConsumer.getUuid());
            assertEquals(2, pools.size());
            // Get the guest pool
            PoolDTO guestPool = new PoolDTO();
            for (PoolDTO aPool : pools) {
                if (aPool.getSourceEntitlement() != null) {
                    guestPool = aPool;
                }
            }
            List<AttributeDTO> hostAtts = guestPool.getAttributes().stream()
                .filter(x -> "requires_host".equals(x.getName()))
                .collect(Collectors.toList());
            assertEquals(1, hostAtts.size());
            assertEquals(hostConsumer.getUuid(), hostAtts.get(0).getValue());

            // Consume the host limited pool
            guestConsumerClient.consumers().bindPool(guestConsumer.getUuid(), guestPool.getId(), 1);
            //Should have a host with 1 registered guest
            List<ConsumerDTOArrayElement> guestList = client.consumers().getGuests(hostConsumer.getUuid());
            assertEquals(1, guestList.size());
            assertEquals(guestConsumer.getUuid(), guestList.get(0).getUuid());
            assertEquals(1, guestConsumerClient.consumers().listEntitlements(
                guestConsumer.getUuid()).size());

            // Updating to a new host should remove host specific entitlements
            // because MySql
            sleep(2000);
            hypervisorCheckin(owner, userClient, data2.getExpectedHostName(),
                data2.getExpectedHostHypervisorId(), List.of(uuid1), null, reporterId, true);
            // The guests host limited entitlement should be gone
            assertEquals(0, guestConsumerClient.consumers().listEntitlements(guestConsumer.getUuid(),
                null, true, null, null, null, null, null).size());
        }
    }

    @Test
    public void shouldAllowExistingGuestToBeMigratedToAnExistingHost() {
        setupOwnerUserClient();
        String hypervisorId1 = StringUtil.random("hypervisor").toLowerCase();
        String hypervisorId2 = StringUtil.random("hypervisor").toLowerCase();
        ApiClient virtwho = createVirtWhoClient(owner, userClient);
        String hostName1 = StringUtil.random("host");
        String hostName2 = StringUtil.random("host");
        String guestUuid = StringUtil.random(("uuid"));
        String guestIdToMigrate = StringUtil.random("uuid");

        ObjectMapper om = ApiClient.MAPPER;
        ObjectNode beforeMigration = om.createObjectNode();
        ArrayNode hypervisors = beforeMigration.putArray("hypervisors");
        ObjectNode hypervisor = om.createObjectNode();
        hypervisor.put("name", hostName1);
        ObjectNode hypervisorId = hypervisor.putObject("hypervisorId");
        hypervisorId.put("hypervisorId", hypervisorId1);
        ArrayNode guestIds = hypervisor.putArray("guestIds");
        ObjectNode guestId = om.createObjectNode();
        guestId.put("guestId", guestUuid);
        guestIds.add(guestId);
        JsonNode factsNode = om.valueToTree(Map.of("test_fact", "test_value"));
        hypervisor.set("facts", factsNode);
        hypervisors.add(hypervisor);
        hypervisor = om.createObjectNode();
        hypervisor.put("name", hostName2);
        hypervisorId = hypervisor.putObject("hypervisorId");
        hypervisorId.put("hypervisorId", hypervisorId2);
        guestIds = hypervisor.putArray("guestIds");
        guestId = om.createObjectNode();
        guestId.put("guestId", guestIdToMigrate);
        guestIds.add(guestId);
        factsNode = om.valueToTree(Map.of("test_fact", "test_value"));
        hypervisor.set("facts", factsNode);
        hypervisors.add(hypervisor);

        AsyncJobStatusDTO job = virtwho.hypervisors().hypervisorUpdateAsync(
            owner.getKey(), true, reporterId, beforeMigration.toString());
        if (job != null) {
            AsyncJobStatusDTO status = client.jobs().waitForJob(job.getId());
            assertEquals("FINISHED", status.getState());
        }
        List<ConsumerDTOArrayElement> hypervisorList = ownerApi.getHypervisors(
            owner.getKey(), List.of(hypervisorId1));
        assertEquals(1, client.guestIds().getGuestIds(hypervisorList.get(0).getUuid()).size());
        hypervisorList = ownerApi.getHypervisors(owner.getKey(), List.of(hypervisorId2));
        assertEquals(1, client.guestIds().getGuestIds(hypervisorList.get(0).getUuid()).size());

        ObjectNode afterMigration = om.createObjectNode();
        hypervisors = afterMigration.putArray("hypervisors");
        hypervisor = om.createObjectNode();
        hypervisor.put("name", hostName1);
        hypervisorId = hypervisor.putObject("hypervisorId");
        hypervisorId.put("hypervisorId", hypervisorId1);
        guestIds = hypervisor.putArray("guestIds");
        guestId = om.createObjectNode();
        guestId.put("guestId", guestUuid);
        guestIds.add(guestId);
        guestId = om.createObjectNode();
        guestId.put("guestId", guestIdToMigrate);
        guestIds.add(guestId);
        factsNode = om.valueToTree(Map.of("test_fact", "test_value"));
        hypervisor.set("facts", factsNode);
        hypervisors.add(hypervisor);
        hypervisor = om.createObjectNode();
        hypervisor.put("name", hostName2);
        hypervisorId = hypervisor.putObject("hypervisorId");
        hypervisorId.put("hypervisorId", hypervisorId2);
        hypervisor.putArray("guestIds");
        factsNode = om.valueToTree(Map.of("test_fact", "test_value"));
        hypervisor.set("facts", factsNode);
        hypervisors.add(hypervisor);

        job = virtwho.hypervisors().hypervisorUpdateAsync(
            owner.getKey(), false, reporterId, afterMigration.toString());
        if (job != null) {
            AsyncJobStatusDTO status = client.jobs().waitForJob(job.getId());
            assertEquals("FINISHED", status.getState());
        }
        hypervisorList = ownerApi.getHypervisors(owner.getKey(), List.of(hypervisorId1));
        assertEquals(2, client.guestIds().getGuestIds(hypervisorList.get(0).getUuid()).size());
        hypervisorList = ownerApi.getHypervisors(owner.getKey(), List.of(hypervisorId2));
        assertEquals(0, client.guestIds().getGuestIds(hypervisorList.get(0).getUuid()).size());
    }

    @Test
    public void shouldCompleteSuccessfullyWhenAGuestWithHostSpecificEntitlementIsMigrated() throws Exception {
        setupOwnerUserClient();
        ProductDTO product = Products.randomEng()
            .name(StringUtil.random("name"))
            .attributes(List.of(new AttributeDTO().name("version").value("6.1")));
        product = ownerProductApi.createProduct(owner.getKey(), product);
        ProductDTO product1 = Products.random()
            .name(StringUtil.random("name"))
            .attributes(List.of(
                new AttributeDTO().name("stacking_id").value("ouch"),
                new AttributeDTO().name("virt_limit").value("1"),
                new AttributeDTO().name("sockets").value("1"),
                new AttributeDTO().name("instance_multiplier").value("2"),
                new AttributeDTO().name("multi-entitlement").value("yes"),
                new AttributeDTO().name("host_limited").value("true")))
            .providedProducts(Set.of(product));
        product1 = ownerProductApi.createProduct(owner.getKey(), product1);
        PoolDTO pool = Pools.random()
            .owner(new NestedOwnerDTO().key(owner.getKey()))
            .productId(product1.getId())
            .providedProducts(Set.of(new ProvidedProductDTO().productId(product.getId())));
        ownerApi.createPool(owner.getKey(), pool);

        Map guestFacts = Map.of("virt.is_guest", "true", "virt.uuid", "myGuestId",
            "system.certificate_version", "3.2");
        ConsumerDTO guest = Consumers.random(owner)
            .facts(guestFacts)
            .installedProducts(Set.of(new ConsumerInstalledProductDTO()
            .productId(product.getId()).productName(product.getName())));
        guest = userClient.consumers().createConsumer(guest);

        Map hypervisorFacts = Map.of("virt.is_guest", "false");
        String hypervisorId = StringUtil.random("hypervisorId");
        ConsumerDTO hypervisor = Consumers.random(owner)
            .type(ConsumerTypes.Hypervisor.value())
            .facts(hypervisorFacts)
            .hypervisorId(new HypervisorIdDTO().hypervisorId(hypervisorId))
            .guestIds(List.of(new GuestIdDTO().guestId("myGuestId")))
            .installedProducts(Set.of(new ConsumerInstalledProductDTO()
            .productId(product.getId()).productName(product.getName())));
        userClient.consumers().createConsumer(hypervisor);
        JsonNode bindResult = userClient.consumers().autoBind(guest.getUuid());
        assertThat(bindResult).hasSize(1);

        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, userClient, hypervisor.getName(),
            hypervisor.getHypervisorId().getHypervisorId(), List.of("blah"), hypervisorFacts, reporterId,
            true);
        confirmResultDataCounts(resultData, 0, 1, 0, 0);
    }

    @Test
    public void shouldMergeConsumerIntoHypervisorWithTheSameUuid() throws Exception {
        setupOwnerUserClient();
        ApiClient virtwho = createVirtWhoClient(owner, userClient);
        HypervisorTestData data = new HypervisorTestData();

        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, virtwho, data.getExpectedHostName(),
            data.getExpectedHostHypervisorId(), data.getExpectedGuestIds(),
            Map.of("dmi.system.uuid", data.getExpectedHostHypervisorId()), reporterId, true);
        confirmResultDataCounts(resultData, 1, 0, 0, 0);
        String hypervisorUuid = resultData.getCreated().iterator().next().getUuid();

        Map<String, String> facts = Map.of("dmi.system.uuid", data.getExpectedHostHypervisorId(),
            "virt.is_guest", "false");
        ConsumerDTO testHost = Consumers.random(owner).facts(facts);
        testHost = userClient.consumers().createConsumer(testHost);
        // virtwho consumer, and the merged test host
        assertThat(ownerApi.listOwnerConsumers(owner.getKey(), null)).hasSize(2);
        assertEquals("hypervisor", testHost.getType().getLabel());
        assertEquals(testHost.getUuid(), resultData.getCreated().iterator().next().getUuid());
        assertEquals(hypervisorUuid, testHost.getUuid());
    }

    @Test
    public void shouldMergeConsumerThatDoesNotSpecifyOwnerKeyIntoHypervisorWithTheSameUuid()
        throws Exception {
        setupOwnerUserClient();
        ApiClient virtwho = createVirtWhoClient(owner, userClient);
        HypervisorTestData data = new HypervisorTestData();

        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, virtwho, data.getExpectedHostName(),
            data.getExpectedHostHypervisorId(), data.getExpectedGuestIds(),
            Map.of("dmi.system.uuid", data.getExpectedHostHypervisorId()), reporterId, true);
        confirmResultDataCounts(resultData, 1, 0, 0, 0);

        // NOTE: we don't specify owner key during registration so that candlepin will have to resolve
        // the owner based on the user principal:
        Map<String, String> facts = Map.of("dmi.system.uuid", data.getExpectedHostHypervisorId(),
            "virt.is_guest", "false");
        ConsumerDTO testHost = Consumers.random(null).facts(facts);
        testHost = userClient.consumers().createConsumer(testHost, null, null, null, true);
        // virtwho consumer, and the merged test host
        assertThat(ownerApi.listOwnerConsumers(owner.getKey(), null)).hasSize(2);
        assertEquals("hypervisor", testHost.getType().getLabel());
        assertEquals(testHost.getUuid(), resultData.getCreated().iterator().next().getUuid());
    }

    @Test
    public void shouldMergeHypervisorIntoConsumerWithTheSameUuid() throws Exception {
        setupOwnerUserClient();
        ApiClient virtwho = createVirtWhoClient(owner, userClient);
        HypervisorTestData data = new HypervisorTestData();

        Map<String, String> facts = Map.of("dmi.system.uuid", data.getExpectedHostHypervisorId(),
            "virt.is_guest", "false");
        ConsumerDTO testHost = Consumers.random(owner).facts(facts);
        testHost = userClient.consumers().createConsumer(testHost);
        String testHostUuid = testHost.getUuid();

        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, virtwho, data.getExpectedHostName(),
            data.getExpectedHostHypervisorId(), data.getExpectedGuestIds(),
            Map.of("dmi.system.uuid", data.getExpectedHostHypervisorId()), reporterId, true);
        confirmResultDataCounts(resultData, 0, 1, 0, 0);
        assertEquals(testHostUuid, resultData.getUpdated().iterator().next().getUuid());

        // virtwho consumer, and the merged test host
        assertThat(ownerApi.listOwnerConsumers(owner.getKey(), null)).hasSize(2);
        testHost = userClient.consumers().getConsumer(testHost.getUuid());
        assertEquals("hypervisor", testHost.getType().getLabel());
        assertEquals(testHostUuid, testHost.getUuid());
    }

    @Test
    public void shouldMergeHypervisorIntoConsumerWithTheSameUuidIgnoreCasing() throws Exception {
        setupOwnerUserClient();
        ApiClient virtwho = createVirtWhoClient(owner, userClient);
        HypervisorTestData data = new HypervisorTestData();

        Map<String, String> facts = Map.of("dmi.system.uuid",
            data.getExpectedHostHypervisorId().toUpperCase(), "virt.is_guest", "false");
        ConsumerDTO testHost = Consumers.random(owner).facts(facts);
        testHost = userClient.consumers().createConsumer(testHost);
        String testHostUuid = testHost.getUuid();

        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, virtwho, data.getExpectedHostName(),
            data.getExpectedHostHypervisorId(), data.getExpectedGuestIds(),
            Map.of("dmi.system.uuid", data.getExpectedHostHypervisorId()), reporterId, true);
        confirmResultDataCounts(resultData, 0, 1, 0, 0);
        assertEquals(testHostUuid, resultData.getUpdated().iterator().next().getUuid());

        // virtwho consumer, and the merged test host
        assertThat(ownerApi.listOwnerConsumers(owner.getKey(), null)).hasSize(2);
        testHost = userClient.consumers().getConsumer(testHost.getUuid());
        assertEquals("hypervisor", testHost.getType().getLabel());
        assertEquals(testHostUuid, testHost.getUuid());
    }

    @Test
    public void shouldMergeHypervisorIntoConsumerWithTheSameUuidIgnoreCasingReverse() throws Exception {
        setupOwnerUserClient();
        ApiClient virtwho = createVirtWhoClient(owner, userClient);
        HypervisorTestData data = new HypervisorTestData();

        Map<String, String> facts = Map.of("dmi.system.uuid", data.getExpectedHostHypervisorId(),
            "virt.is_guest", "false");
        ConsumerDTO testHost = Consumers.random(owner).facts(facts);
        testHost = userClient.consumers().createConsumer(testHost);
        String testHostUuid = testHost.getUuid();

        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, virtwho, data.getExpectedHostName(),
            data.getExpectedHostHypervisorId(), data.getExpectedGuestIds(),
            Map.of("dmi.system.uuid", data.getExpectedHostHypervisorId().toUpperCase()), reporterId, true);
        confirmResultDataCounts(resultData, 0, 1, 0, 0);
        assertEquals(testHostUuid, resultData.getUpdated().iterator().next().getUuid());

        // virtwho consumer, and the merged test host
        assertThat(ownerApi.listOwnerConsumers(owner.getKey(), null)).hasSize(2);
        testHost = userClient.consumers().getConsumer(testHost.getUuid());
        assertEquals("hypervisor", testHost.getType().getLabel());
        assertEquals(testHostUuid, testHost.getUuid());
    }

    @Test
    public void shouldNotFailWhenFactsChangeButNotTheGuestList() throws Exception {
        setupOwnerUserClient();
        ApiClient virtwho = createVirtWhoClient(owner, userClient);
        HypervisorTestData data = new HypervisorTestData();

        ConsumerDTO testHost = createHostConsumer(owner, userClient, data);

        Map hostFacts = Map.of(
            "dmi.system.uuid", data.getExpectedHostHypervisorId(),
            "virt.is_guest", "false",
            "fact1", "one",
            "fact2", "two",
            "fact3", "three");

        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, virtwho, data.getExpectedHostName(),
            data.getExpectedHostHypervisorId(), data.getExpectedGuestIds(), hostFacts, reporterId, true);
        confirmResultDataCounts(resultData, 0, 1, 0, 0);
    }

    @Test
    public void shouldAllowTheHypervisorIdToBeChangedOnTheConsumer() throws Exception {
        setupOwnerUserClient();
        HypervisorTestData data = new HypervisorTestData();
        String hostHypId2 = StringUtil.random("hypervisor").toLowerCase();
        String hostSystemId = StringUtil.random("system");

        ConsumerDTO testHost = Consumers.random(owner)
            .name(data.getExpectedHostName())
            .type(ConsumerTypes.Hypervisor.value())
            .facts(Map.of("dmi.system.uuid", hostSystemId, "virt.is_guest", "false"))
            .hypervisorId(new HypervisorIdDTO().hypervisorId(data.getExpectedHostHypervisorId()))
            .guestIds(data.getGuestIdDTOs());
        testHost = userClient.consumers().createConsumer(testHost);
        assertEquals(data.getExpectedHostHypervisorId().toLowerCase(),
            testHost.getHypervisorId().getHypervisorId());

        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, userClient,
            data.getExpectedHostName(), hostHypId2, data.getExpectedGuestIds(),
            Map.of("dmi.system.uuid", hostSystemId), reporterId, true);
        confirmResultDataCounts(resultData, 0, 1, 0, 0);
        testHost = userClient.consumers().getConsumer(testHost.getUuid());
        assertEquals(hostHypId2, testHost.getHypervisorId().getHypervisorId());
    }

    @Test
    public void shouldAllowTheHypervisorIdUpdateOnTheConsumerWithNoExistingHypervisorId() throws Exception {
        setupOwnerUserClient();
        HypervisorTestData data = new HypervisorTestData();
        String hostSystemId = StringUtil.random("system");

        ConsumerDTO testHost = Consumers.random(owner)
            .name(data.getExpectedHostName())
            .type(ConsumerTypes.Hypervisor.value())
            .facts(Map.of("virt.is_guest", "false"))
            .guestIds(data.getGuestIdDTOs());
        testHost = userClient.consumers().createConsumer(testHost);
        ConsumerDTO toUpdate = new ConsumerDTO()
            .uuid(testHost.getUuid())
            .facts(Map.of("dmi.system.uuid", hostSystemId, "virt.is_guest", "false"));
        userClient.consumers().updateConsumer(testHost.getUuid(), toUpdate);
        testHost = userClient.consumers().getConsumer(testHost.getUuid());
        assertNull(testHost.getHypervisorId());

        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, userClient,
            data.getExpectedHostName(), data.getExpectedHostHypervisorId(), data.getExpectedGuestIds(),
            Map.of("dmi.system.uuid", hostSystemId), reporterId, true);
        confirmResultDataCounts(resultData, 0, 1, 0, 0);
        testHost = userClient.consumers().getConsumer(testHost.getUuid());
        assertEquals(data.getExpectedHostHypervisorId().toLowerCase(),
            testHost.getHypervisorId().getHypervisorId());
    }

    @Test
    public void shouldFailWhenJsonDoesNotHaveTheProperStructure() {
        setupOwnerUserClient();
        String uuid1 = StringUtil.random("uuid");
        String uuid2 = StringUtil.random("uuid");
        String hypervisorId1 = StringUtil.random("hypervisor").toLowerCase();

        // synchronous report structure should fail
        ObjectMapper om = ApiClient.MAPPER;
        ObjectNode syncStructure = om.createObjectNode();
        syncStructure.put("name", "")
            .put("uuid", uuid1);
        ObjectNode hyperIdNode = syncStructure.putObject("hypervisorId");
        hyperIdNode.put("hypervisorId", hypervisorId1);
        ArrayNode guestIds = syncStructure.putArray("guestIds");
        guestIds.addObject().put("guestId", uuid2);
        ObjectNode facts = syncStructure.putObject("facts");
        facts.put("test_fact", "test_value");
        ApiClient virtwho = createVirtWhoClient(owner, userClient);
        assertBadRequest(() -> virtwho.hypervisors().hypervisorUpdateAsync(
            owner.getKey(), true, reporterId, syncStructure.toString()));

        // empty json
        assertBadRequest(() -> virtwho.hypervisors().hypervisorUpdateAsync(
            owner.getKey(), true, reporterId, "{}"));
        // this is the correct version of an empty list of hypervisors
        AsyncJobStatusDTO job = virtwho.hypervisors().hypervisorUpdateAsync(
            owner.getKey(), true, reporterId, "{\"hypervisors\":[]}");
        if (job != null) {
            AsyncJobStatusDTO status = client.jobs().waitForJob(job.getId());
            assertEquals("FINISHED", status.getState());
        }
    }

    @Test
    public void shouldAllowTheHardwareIdToChangeWhileTheHypervisorIdStaysConstant() throws Exception {
        setupOwnerUserClient();
        String hostSystemId1 = StringUtil.random("system");
        String hostSystemId2 = StringUtil.random("system");
        HypervisorTestData data = new HypervisorTestData();
        ConsumerDTO testHost = Consumers.random(owner)
            .name(data.getExpectedHostName())
            .type(ConsumerTypes.Hypervisor.value())
            .hypervisorId(new HypervisorIdDTO().hypervisorId(data.getExpectedHostHypervisorId()))
            .facts(Map.of("virt.is_guest", "false", "dmi.system.uuid", hostSystemId1))
            .guestIds(data.getGuestIdDTOs());
        testHost = userClient.consumers().createConsumer(testHost);
        assertEquals(data.getExpectedHostHypervisorId().toLowerCase(),
            testHost.getHypervisorId().getHypervisorId());

        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, userClient,
            data.getExpectedHostName(), data.getExpectedHostHypervisorId(), data.getExpectedGuestIds(),
            Map.of("dmi.system.uuid", hostSystemId1), reporterId, true);
        confirmResultDataCounts(resultData, 0, 1, 0, 0);
        testHost = userClient.consumers().getConsumer(testHost.getUuid());
        assertEquals(data.getExpectedHostHypervisorId().toLowerCase(),
            testHost.getHypervisorId().getHypervisorId());

        resultData = hypervisorCheckin(owner, userClient, data.getExpectedHostName(),
            data.getExpectedHostHypervisorId(), data.getExpectedGuestIds(),
            Map.of("dmi.system.uuid", hostSystemId2), reporterId, true);
        confirmResultDataCounts(resultData, 0, 1, 0, 0);
        testHost = userClient.consumers().getConsumer(testHost.getUuid());
        assertEquals(data.getExpectedHostHypervisorId().toLowerCase(),
            testHost.getHypervisorId().getHypervisorId());
    }

    @Test
    public void shouldReconcileWhenHardwareIdsChangeAcrossKnownHypervisors() throws Exception {
        setupOwnerUserClient();
        String hostHypId1 = StringUtil.random("hypervisor").toLowerCase();
        String hostHypId2 = StringUtil.random("hypervisor").toLowerCase();
        String hostHypId3 = StringUtil.random("hypervisor").toLowerCase();
        String hostSystemId1 = StringUtil.random("system").toLowerCase();
        String hostSystemId2 = StringUtil.random("system").toLowerCase();
        String hostSystemId3 = StringUtil.random("system").toLowerCase();
        String hostSystemId4 = StringUtil.random("system").toLowerCase();
        List<GuestIdDTO> guestSet1 = List.of(new GuestIdDTO().guestId("g1"), new GuestIdDTO().guestId("g2"));
        List<String> guests1 = List.of("g1", "g2");
        List<GuestIdDTO> guestSet2 = List.of(new GuestIdDTO().guestId("g3"), new GuestIdDTO().guestId("g4"));
        List<String> guests2 = List.of("g3", "g4");
        List<GuestIdDTO> guestSet3 = List.of(new GuestIdDTO().guestId("g5"), new GuestIdDTO().guestId("g6"));
        List<String> guests3 = List.of("g5", "g6");

        ConsumerDTO testHost1 = Consumers.random(owner)
            .name(hostHypId1)
            .type(ConsumerTypes.Hypervisor.value())
            .hypervisorId(new HypervisorIdDTO().hypervisorId(hostHypId1))
            .facts(Map.of("virt.is_guest", "false", "dmi.system.uuid", hostSystemId1))
            .guestIds(guestSet1);
        testHost1 = userClient.consumers().createConsumer(testHost1);
        assertEquals(hostHypId1, testHost1.getHypervisorId().getHypervisorId());

        ConsumerDTO testHost2 = Consumers.random(owner)
            .name(hostHypId2)
            .type(ConsumerTypes.Hypervisor.value())
            .hypervisorId(new HypervisorIdDTO().hypervisorId(hostHypId2))
            .facts(Map.of("virt.is_guest", "false", "dmi.system.uuid", hostSystemId2))
            .guestIds(guestSet2);
        testHost2 = userClient.consumers().createConsumer(testHost2);
        assertEquals(hostHypId2, testHost2.getHypervisorId().getHypervisorId());

        ConsumerDTO testHost3 = Consumers.random(owner)
            .name(hostHypId3)
            .type(ConsumerTypes.Hypervisor.value())
            .hypervisorId(new HypervisorIdDTO().hypervisorId(hostHypId3))
            .facts(Map.of("virt.is_guest", "false", "dmi.system.uuid", hostSystemId3))
            .guestIds(guestSet3);
        testHost3 = userClient.consumers().createConsumer(testHost3);
        assertEquals(hostHypId3, testHost3.getHypervisorId().getHypervisorId());

        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, userClient, hostHypId1,
            hostHypId1, guests1, Map.of("dmi.system.uuid", hostSystemId2), reporterId, true);
        confirmResultDataCounts(resultData, 0, 1, 0, 0);
        testHost1 = userClient.consumers().getConsumer(testHost1.getUuid());
        assertEquals(hostHypId1, testHost1.getHypervisorId().getHypervisorId());
        assertEquals(hostSystemId2, testHost1.getFacts().get("dmi.system.uuid"));

        resultData = hypervisorCheckin(owner, userClient, hostHypId2,
            hostHypId2, guests2, Map.of("dmi.system.uuid", hostSystemId4), reporterId, true);
        confirmResultDataCounts(resultData, 0, 1, 0, 0);
        testHost2 = userClient.consumers().getConsumer(testHost2.getUuid());
        assertEquals(hostHypId2, testHost2.getHypervisorId().getHypervisorId());
        assertEquals(hostSystemId4, testHost2.getFacts().get("dmi.system.uuid"));

        resultData = hypervisorCheckin(owner, userClient, hostHypId3,
            hostHypId3, guests3, Map.of("dmi.system.uuid", hostSystemId1), reporterId, true);
        confirmResultDataCounts(resultData, 0, 1, 0, 0);
        testHost3 = userClient.consumers().getConsumer(testHost3.getUuid());
        assertEquals(hostHypId3, testHost3.getHypervisorId().getHypervisorId());
        assertEquals(hostSystemId1, testHost3.getFacts().get("dmi.system.uuid"));
    }

    @Test
    public void shouldUpdateTheConsumerNameFromTheHypervisorCheckin() throws Exception {
        setupOwnerUserClient();
        HypervisorTestData data = new HypervisorTestData();
        String hostSystemId1 = StringUtil.random("system").toLowerCase();
        String newName = StringUtil.random("name");

        ConsumerDTO testHost1 = Consumers.random(owner)
            .name(data.getExpectedHostName())
            .type(ConsumerTypes.Hypervisor.value())
            .hypervisorId(new HypervisorIdDTO().hypervisorId(data.getExpectedHostHypervisorId()))
            .facts(Map.of("virt.is_guest", "false", "dmi.system.uuid", hostSystemId1))
            .guestIds(data.getGuestIdDTOs());
        testHost1 = userClient.consumers().createConsumer(testHost1);
        assertEquals(data.getExpectedHostHypervisorId().toLowerCase(),
            testHost1.getHypervisorId().getHypervisorId());

        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, userClient, newName,
            data.getExpectedHostHypervisorId(), data.getExpectedGuestIds(),
            Map.of("dmi.system.uuid", hostSystemId1), reporterId, true);
        confirmResultDataCounts(resultData, 0, 1, 0, 0);
        testHost1 = userClient.consumers().getConsumer(testHost1.getUuid());
        assertEquals(newName, testHost1.getName());
    }

    @Test
    public void shouldNotUpdateTheNullConsumerNameFromTheHypervisorCheckin() throws Exception {
        setupOwnerUserClient();
        HypervisorTestData data = new HypervisorTestData();
        String hostSystemId1 = StringUtil.random("system").toLowerCase();
        String newName = StringUtil.random("name");

        ConsumerDTO testHost1 = Consumers.random(owner)
            .name(data.getExpectedHostName())
            .type(ConsumerTypes.Hypervisor.value())
            .hypervisorId(new HypervisorIdDTO().hypervisorId(data.getExpectedHostHypervisorId()))
            .facts(Map.of("virt.is_guest", "false", "dmi.system.uuid", hostSystemId1))
            .guestIds(data.getGuestIdDTOs());
        testHost1 = userClient.consumers().createConsumer(testHost1);
        assertEquals(data.getExpectedHostHypervisorId().toLowerCase(),
            testHost1.getHypervisorId().getHypervisorId());

        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, userClient, newName,
            data.getExpectedHostHypervisorId(), data.getExpectedGuestIds(),
            Map.of("dmi.system.uuid", hostSystemId1), reporterId, true);
        confirmResultDataCounts(resultData, 0, 1, 0, 0);
        testHost1 = userClient.consumers().getConsumer(testHost1.getUuid());
        assertEquals(newName, testHost1.getName());
        assertEquals(data.getExpectedHostHypervisorId().toLowerCase(),
            testHost1.getHypervisorId().getHypervisorId());

        resultData = hypervisorCheckin(owner, userClient, null,
            data.getExpectedHostHypervisorId(), data.getExpectedGuestIds(),
            Map.of("dmi.system.uuid", hostSystemId1), reporterId, true);
        confirmResultDataCounts(resultData, 0, 0, 1, 0);
        testHost1 = userClient.consumers().getConsumer(testHost1.getUuid());
        assertEquals(newName, testHost1.getName());
    }

    @Test
    public void shouldKeepConditionalContentForGuestAfterMigration() throws Exception {
        setupOwnerUserClient();
        ProductDTO rh00271EngProduct = createRH00271EngProduct(owner);
        ProductDTO rh00271Product = createRH00271Product(owner, rh00271EngProduct);
        ProductDTO rh00051EngProduct = createRH00051EngProduct(owner);
        ProductDTO rh00051Product = createRH00051Product(owner, rh00051EngProduct);
        ContentDTO rh00051Content = createRH00051Content(owner);
        // Content that has a required/modified product 'rh00051_eng_product' (this eng product needs
        // to be entitled to the consumer already, or otherwise this content will get filtered out
        // during entitlement cert generation)
        ContentDTO rh00271Content = createRH00271Content(owner, rh00051Product);
        ownerProductApi.addContentToProduct(owner.getKey(), rh00051EngProduct.getId(),
            rh00051Content.getId(), true);
        ownerProductApi.addContentToProduct(owner.getKey(), rh00271EngProduct.getId(),
            rh00271Content.getId(), true);
        // creating primary pool for the products
        PoolDTO rh00271Pool = createRH00271Pool(owner, rh00271Product, rh00271EngProduct);
        PoolDTO rh00051Pool = createRH00051Pool(owner, rh00051Product, rh00051EngProduct);

        // creating hypervisor 1
        HypervisorTestData data1 = new HypervisorTestData();
        ConsumerDTO hypervisor1 = createHostConsumer(owner, userClient, data1);
        ApiClient hypervisorClient1 = ApiClients.ssl(hypervisor1);
        // creating guest
        String guestUuid = StringUtil.random("uuid");
        ConsumerDTO guest = createGuestToMigrate(owner, userClient, guestUuid, rh00271EngProduct,
            rh00051EngProduct);
        ApiClient guestClient = ApiClients.ssl(guest);

        //  guest mapping (hypervisor1 => guest) is done by virtwho
        ApiClient virtwho = createVirtWhoClient(owner, userClient);
        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, virtwho,
            data1.getExpectedHostName(), data1.getExpectedHostHypervisorId(), List.of(guestUuid),
            null, reporterId, true);
        confirmResultDataCounts(resultData, 0, 1, 0, 0);

        // creating hypervisor 2
        HypervisorTestData data2 = new HypervisorTestData();
        ConsumerDTO hypervisor2 = createHostConsumer(owner, userClient, data2);
        ApiClient hypervisorClient2 = ApiClients.ssl(hypervisor2);

        // Hypervisor1 consumes the same pool
        hypervisorClient1.consumers().bindPool(hypervisor1.getUuid(), rh00271Pool.getId(), 1);
        PoolDTO stackDerivedPool = findDerivedPoolForHost(owner.getKey(), "STACK_DERIVED",
            rh00271Product.getId(), hypervisor1.getUuid());
        String hypervisor1StackDerivedPool1 = stackDerivedPool.getId();
        hypervisorClient1.consumers().bindPool(hypervisor1.getUuid(), rh00051Pool.getId(), 1);
        stackDerivedPool = findDerivedPoolForHost(owner.getKey(), "STACK_DERIVED",
            rh00051Product.getId(), hypervisor1.getUuid());
        String hypervisor1StackDerivedPool2 = stackDerivedPool.getId();
        // Hypervisor2 consumes the same pool
        hypervisorClient2.consumers().bindPool(hypervisor2.getUuid(), rh00271Pool.getId(), 1);
        stackDerivedPool = findDerivedPoolForHost(owner.getKey(), "STACK_DERIVED",
            rh00271Product.getId(), hypervisor1.getUuid());
        String hypervisor2StackDerivedPool1 = stackDerivedPool.getId();
        hypervisorClient2.consumers().bindPool(hypervisor2.getUuid(), rh00051Pool.getId(), 1);
        stackDerivedPool = findDerivedPoolForHost(owner.getKey(), "STACK_DERIVED",
            rh00051Product.getId(), hypervisor1.getUuid());
        String hypervisor2StackDerivedPool2 = stackDerivedPool.getId();

        JsonNode ent = guestClient.consumers()
            .bindPool(guest.getUuid(), hypervisor1StackDerivedPool2, 1).get(0);
        EntitlementDTO guestRH00051Entitlement = ApiClient.MAPPER.convertValue(ent, EntitlementDTO.class);
        assertEquals(hypervisor1StackDerivedPool2, guestRH00051Entitlement.getPool().getId());
        ent = guestClient.consumers().bindPool(guest.getUuid(), hypervisor1StackDerivedPool1, 1).get(0);
        EntitlementDTO guestRH00271Entitlement = ApiClient.MAPPER.convertValue(ent, EntitlementDTO.class);
        assertEquals(hypervisor1StackDerivedPool1, guestRH00271Entitlement.getPool().getId());

        // Verify guest's entitlement certs each contain the appropriate content set
        List<JsonNode> jsonNodes = guestClient.consumers().exportCertificates(guest.getUuid(), null);
        assertEquals(2, jsonNodes.size());
        JsonNode rh00051Cert = jsonNodes.stream().filter(x ->
            guestRH00051Entitlement.getPool().getId().equals(x.get("pool").get("id").asText()))
            .collect(Collectors.toList()).get(0);
        JsonNode rh00271Cert = jsonNodes.stream().filter(x ->
            guestRH00271Entitlement.getPool().getId().equals(x.get("pool").get("id").asText()))
            .collect(Collectors.toList()).get(0);
        assertNotNull(rh00051Cert);
        assertNotNull(rh00271Cert);

        assertEquals(1, rh00051Cert.get("products").get(0).get("content").size());
        assertEquals(rh00051Content.getId(),
            rh00051Cert.get("products").get(0).get("content").get(0).get("id").asText());
        assertEquals(1, rh00271Cert.get("products").get(0).get("content").size());
        assertEquals(rh00271Content.getId(),
            rh00271Cert.get("products").get(0).get("content").get(0).get("id").asText());

        executeMigration(owner, data1, data2, virtwho, guestUuid);
        //  At this point, the guest will have the entitlements from the old hypervisor revoked, and
        //  it will get the entitlements from the new hypervisor auto-attached
        List<JsonNode> newJsonNodes = guestClient.consumers().exportCertificates(guest.getUuid(), null);
        assertEquals(2, jsonNodes.size());
        // Verify guest's entitlement certs each contain the appropriate content set
        List<EntitlementDTO> newEnts = guestClient.consumers().listEntitlements(guest.getUuid());
        assertEquals(2, newEnts.size());

        EntitlementDTO updatedGuestRH00271Entitlement = newEnts.stream().filter(x ->
            guestRH00271Entitlement.getPool().getProductId().equals(x.getPool().getProductId()))
            .collect(Collectors.toList()).get(0);
        assertNotNull(updatedGuestRH00271Entitlement);
        EntitlementDTO updatedGuestRH00051Entitlement = newEnts.stream()
            .filter(x -> guestRH00051Entitlement.getPool().getProductId().equals(x.getPool().getProductId()))
            .collect(Collectors.toList()).get(0);
        assertNotNull(updatedGuestRH00051Entitlement);

        rh00051Cert = newJsonNodes.stream().filter(x ->
            updatedGuestRH00051Entitlement.getPool().getId().equals(x.get("pool").get("id").asText()))
            .collect(Collectors.toList()).get(0);
        rh00271Cert = newJsonNodes.stream().filter(x ->
            updatedGuestRH00271Entitlement.getPool().getId().equals(x.get("pool").get("id").asText()))
            .collect(Collectors.toList()).get(0);
        assertNotNull(rh00051Cert);
        assertNotNull(rh00271Cert);

        assertEquals(1, rh00051Cert.get("products").get(0).get("content").size());
        assertEquals(rh00051Content.getId(),
            rh00051Cert.get("products").get(0).get("content").get(0).get("id").asText());

        // rh00271Content (which depends on modified product id 69) should not have been filtered out,
        // because the engineering product 69 should already be covered by entitlement rh00051:
        assertEquals(1, rh00271Cert.get("products").get(0).get("content").size());
        assertEquals(rh00271Content.getId(),
            rh00271Cert.get("products").get(0).get("content").get(0).get("id").asText());
    }

    private ProductDTO createRH00271EngProduct(OwnerDTO owner) {
        ProductDTO rh00271EngProduct = Products.randomEng()
            .id("204")
            .name("Red Hat Enterprise Linux Server - Extended Life Cycle Support");
        return ownerProductApi.createProduct(owner.getKey(), rh00271EngProduct);
    }

    private ProductDTO createRH00271Product(OwnerDTO owner, ProductDTO rh00271EngProduct) {
        ProductDTO rh00271Product = Products.randomSKU()
            .id("RH00271")
            .name("Extended Life Cycle Support (Unlimited Guests)")
            .providedProducts(Set.of(rh00271EngProduct))
            .multiplier(1L)
            .attributes(List.of(new AttributeDTO().name("virt_limit").value("unlimited"),
                new AttributeDTO().name("stacking_id").value("RH00271"),
                new AttributeDTO().name("host_limited").value("true")));
        return ownerProductApi.createProduct(owner.getKey(), rh00271Product);
    }

    private ProductDTO createRH00051EngProduct(OwnerDTO owner) {
        ProductDTO rh00051EngProduct = Products.randomEng()
            .id("69")
            .name("Red Hat Enterprise Linux Server");
        return ownerProductApi.createProduct(owner.getKey(), rh00051EngProduct);
    }

    private ProductDTO createRH00051Product(OwnerDTO owner, ProductDTO rh00051EngProduct) {
        ProductDTO rh00051Product = Products.randomSKU()
            .id("RH00051")
            .name("Red Hat Enterprise Linux for Virtual Datacenters with Smart Management, Standard")
            .providedProducts(Set.of(rh00051EngProduct))
            .multiplier(1L)
            .attributes(List.of(new AttributeDTO().name("virt_limit").value("unlimited"),
                new AttributeDTO().name("stacking_id").value("RH00051"),
                new AttributeDTO().name("host_limited").value("true")));
        return ownerProductApi.createProduct(owner.getKey(), rh00051Product);
    }

    private ContentDTO createRH00051Content(OwnerDTO owner) {
        ContentDTO rh00051Content = Contents.random()
            .name("cname-c1")
            .id("test-content-c1")
            .label(StringUtil.random("clabel"))
            .type("ctype")
            .vendor("cvendor")
            .contentUrl("/this/is/the/path");
        return ownerContentApi.createContent(owner.getKey(), rh00051Content);
    }

    private ContentDTO createRH00271Content(OwnerDTO owner, ProductDTO rh00051Product) {
        ContentDTO rh00271Content = Contents.random()
            .name("cname-c2")
            .id("test-content-c2")
            .label(StringUtil.random("clabel"))
            .type("ctype")
            .vendor("cvendor")
            .contentUrl("/this/is/the/path")
            .modifiedProductIds(Set.of(rh00051Product.getId()));
        return ownerContentApi.createContent(owner.getKey(), rh00271Content);
    }

    private PoolDTO createRH00271Pool(OwnerDTO owner, ProductDTO rh00271Product,
        ProductDTO rh00271EngProduct) {
        PoolDTO rh00271Pool = Pools.random()
            .productId(rh00271Product.getId())
            .upstreamPoolId(StringUtil.random("id"))
            .providedProducts(Set.of(new ProvidedProductDTO()
            .productId(rh00271EngProduct.getId())
            .productName(rh00271EngProduct.getId())));

        return ownerApi.createPool(owner.getKey(), rh00271Pool);
    }

    private PoolDTO createRH00051Pool(OwnerDTO owner, ProductDTO rh00051Product,
        ProductDTO rh00051EngProduct) {
        PoolDTO rh00051Pool = Pools.random()
            .productId(rh00051Product.getId())
            .upstreamPoolId(StringUtil.random("id"))
            .providedProducts(Set.of(new ProvidedProductDTO()
            .productId(rh00051EngProduct.getId())
            .productName(rh00051EngProduct.getId())));

        return ownerApi.createPool(owner.getKey(), rh00051Pool);
    }

    private ConsumerDTO createGuestToMigrate(OwnerDTO owner, ApiClient userClient, String guestUuid,
        ProductDTO rh00271EngProduct, ProductDTO rh00051EngProduct) {
        ConsumerDTO guest = Consumers.random(owner)
            .installedProducts(Set.of(
                new ConsumerInstalledProductDTO()
            .productName(rh00271EngProduct.getName()).productId(rh00271EngProduct.getId()),
                new ConsumerInstalledProductDTO()
            .productName(rh00051EngProduct.getName()).productId(rh00051EngProduct.getId())))
            .facts(Map.of("virt.is_guest", "true", "virt.uuid", guestUuid,
            "system.certificate_version", "3.2"));
        return userClient.consumers().createConsumer(guest);
    }

    private void executeMigration(OwnerDTO owner,  HypervisorTestData data1, HypervisorTestData data2,
        ApiClient virtwho, String guestUuid) {
        ObjectMapper om = ApiClient.MAPPER;
        ObjectNode afterMigration = om.createObjectNode();
        ArrayNode hypervisors = afterMigration.putArray("hypervisors");
        ObjectNode hypervisor = om.createObjectNode();
        hypervisor.put("name", data1.getExpectedHostName());
        ObjectNode hypervisorId = hypervisor.putObject("hypervisorId");
        hypervisorId.put("hypervisorId", data1.getExpectedHostHypervisorId());
        ArrayNode guestIds = hypervisor.putArray("guestIds");
        JsonNode factsNode = om.valueToTree(Map.of("test_fact", "test_value"));
        hypervisor.set("facts", factsNode);
        hypervisors.add(hypervisor);
        hypervisor = om.createObjectNode();
        hypervisor.put("name", data2.getExpectedHostName());
        hypervisorId = hypervisor.putObject("hypervisorId");
        hypervisorId.put("hypervisorId", data2.getExpectedHostHypervisorId());
        ObjectNode guestId = om.createObjectNode();
        guestId.put("guestId", guestUuid);
        guestIds.add(guestId);
        factsNode = om.valueToTree(Map.of("test_fact", "test_value"));
        hypervisor.set("facts", factsNode);
        hypervisors.add(hypervisor);

        AsyncJobStatusDTO job = virtwho.hypervisors().hypervisorUpdateAsync(
            owner.getKey(), true, reporterId, afterMigration.toString());
        if (job != null) {
            AsyncJobStatusDTO status = client.jobs().waitForJob(job.getId());
            assertEquals("FINISHED", status.getState());
        }
    }

    /**
     * Utility methods
     */
    private HypervisorUpdateResultDTO hypervisorCheckin(OwnerDTO owner, ApiClient consumerClient,
        String hypervisorName, String hypervisorId, List<String> guestIds, Map<String, String> facts,
        String reporterId, boolean createMissing) throws ApiException, IOException {

        JsonNode hostGuestMapping = getAsyncHostGuestMapping(hypervisorName, hypervisorId, guestIds, facts);
        AsyncJobStatusDTO job = consumerClient.hypervisors()
            .hypervisorUpdateAsync(owner.getKey(), createMissing, reporterId, hostGuestMapping.toString());

        HypervisorUpdateResultDTO resultData = null;
        if (job != null) {
            AsyncJobStatusDTO status = client.jobs().waitForJob(job.getId());
            assertEquals("FINISHED", status.getState());
            resultData = getResultData(status);
        }

        return resultData;
    }

    private void asyncUpdateGuestIdsTest(OwnerDTO owner, ApiClient client, ConsumerDTO hostConsumer,
        String reporter) throws Exception {
        List<String> updatedGuestIds = List.of("Guest3", "Guest4");
        JsonNode mapping = getAsyncHostGuestMapping(
            hostConsumer.getName(), hostConsumer.getHypervisorId().getHypervisorId(), updatedGuestIds, null);
        AsyncJobStatusDTO job = client.hypervisors().hypervisorUpdateAsync(
            owner.getKey(), true, reporter, mapping.toString());
        HypervisorUpdateResultDTO resultData = null;
        if (job != null) {
            AsyncJobStatusDTO status = client.jobs().waitForJob(job.getId());
            assertEquals("FINISHED", status.getState());
            resultData = getResultData(status);
        }
        confirmResultDataCounts(resultData, 0, 1, 0, 0);
        ConsumerDTO consumer = client.consumers().getConsumer(hostConsumer.getUuid());
        checkHypervisorConsumer(consumer, hostConsumer.getName(), updatedGuestIds, reporter);
    }

    /**
     * The HypervisorUpdateResultDTO fromJson method will not parse the raw string from the
     *   AsyncJobStatusDTO
     *
     * @param status
     * @return HypervisorUpdateResultDTO
     */
    private HypervisorUpdateResultDTO getResultData(AsyncJobStatusDTO status) {
        return ApiClient.MAPPER.convertValue(status.getResultData(), HypervisorUpdateResultDTO.class);
    }

    private void confirmResultDataCounts(HypervisorUpdateResultDTO resultData, int created, int updated,
        int unchanged, int failedUpdate) {
        assertEquals(created, resultData.getCreated().size());
        assertEquals(updated, resultData.getUpdated().size());
        assertEquals(unchanged, resultData.getUnchanged().size());
        assertEquals(failedUpdate, resultData.getFailedUpdate().size());
    }

    private void checkHypervisorConsumer(ConsumerDTO consumer, String expectedHostName,
        List<String> expectedGuestIds, String reporterId) throws ApiException {
        assertEquals(expectedHostName, consumer.getName());
        List<GuestIdDTOArrayElement> guestIds = client.guestIds().getGuestIds(consumer.getUuid());
        assertEquals(expectedGuestIds.size(), guestIds.size());
        if (reporterId != null) {
            assertEquals(reporterId, consumer.getHypervisorId().getReporterId());
        }
        for (GuestIdDTOArrayElement element : guestIds) {
            assertTrue(expectedGuestIds.contains(element.getGuestId()));
        }
    }

    private JsonNode getAsyncHostGuestMapping(String name, String id, List<String> expectedGuestIds,
        Map<String, String> facts) {
        ObjectMapper om = ApiClient.MAPPER;
        ObjectNode root = om.createObjectNode();
        ArrayNode hypervisors = root.putArray("hypervisors");
        ObjectNode hypervisor = om.createObjectNode();
        hypervisor.put("name", name);
        ObjectNode hypervisorId = hypervisor.putObject("hypervisorId");
        hypervisorId.put("hypervisorId", id);
        ArrayNode guestIds = hypervisor.putArray("guestIds");
        for (String gid : expectedGuestIds) {
            ObjectNode guestId = om.createObjectNode();
            guestId.put("guestId", gid);
            guestIds.add(guestId);
        }
        JsonNode factsNode = om.valueToTree(facts);
        hypervisor.set("facts", factsNode);
        hypervisors.add(hypervisor);
        return root;
    }

    private ApiClient createVirtWhoClient(OwnerDTO owner, ApiClient userClient) throws ApiException {
        ConsumerDTO consumer = Consumers.random(owner, ConsumerTypes.System)
            .installedProducts(Set.of(
                new ConsumerInstalledProductDTO().productId("installedProd").productName("Installed")));
        consumer = userClient.consumers().createConsumer(consumer);
        return ApiClients.ssl(consumer);
    }

    private PoolDTO getVirtLimitPool(List<PoolDTO> pools) {
        for (PoolDTO pool : pools) {
            for (AttributeDTO attribute : pool.getAttributes()) {
                if (attribute.getName().equals("virt_only") && attribute.getValue().equals("true")) {
                    return pool;
                }
            }
        }
        return null;
    }

    ConsumerDTO createHostConsumer(OwnerDTO owner, ApiClient userClient, HypervisorTestData data) {
        ConsumerDTO hostConsumer = Consumers.random(owner)
            .name(data.getExpectedHostName())
            .type(ConsumerTypes.Hypervisor.value())
            .facts(Map.of("test_fact", "fact_value"))
            .hypervisorId(new HypervisorIdDTO().hypervisorId(data.getExpectedHostHypervisorId()));
        return userClient.consumers().createConsumer(hostConsumer);
    }

    ConsumerDTO createGuestConsumer(OwnerDTO owner, ApiClient userClient, String virtUuid) {
        ConsumerDTO guestConsumer1 = Consumers.random(owner)
            .name(StringUtil.random("guest"))
            .type(ConsumerTypes.System.value())
            .facts(Map.of("virt.uuid", virtUuid, "virt.is_guest",
            "true", "uname.machine", "x86_64"));
        return userClient.consumers().createConsumer(guestConsumer1);
    }

    private PoolDTO findDerivedPoolForHost(String ownerKey, String type, String productId,
        String hypervisorUuid) {
        List<PoolDTO> pools = ownerApi.listOwnerPools(ownerKey);
        List<PoolDTO> selected = pools.stream()
            .filter(x -> type.equals(x.getType()) && productId.equals(x.getProductId()))
            .collect(Collectors.toList());
        for (PoolDTO pool : selected) {
            int matchInt = pool.getAttributes().stream()
                .filter(x -> "requires_host".equals(x.getName()) && hypervisorUuid.equals(x.getValue()))
                .collect(Collectors.toList()).size();
            if (matchInt > 0) {
                return pool;
            }
        }
        return null;
    }
}
