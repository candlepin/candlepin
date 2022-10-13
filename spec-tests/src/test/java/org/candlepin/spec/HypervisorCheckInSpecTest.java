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
package org.candlepin.spec;

import static java.lang.Thread.sleep;
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
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.GuestIdDTOArrayElement;
import org.candlepin.dto.api.client.v1.HypervisorIdDTO;
import org.candlepin.dto.api.client.v1.HypervisorUpdateResultDTO;
import org.candlepin.dto.api.client.v1.NestedOwnerDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.StatusDTO;
import org.candlepin.dto.api.client.v1.SubscriptionDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.invoker.client.ApiException;
import org.candlepin.resource.HostedTestApi;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.spec.bootstrap.assertions.OnlyInHosted;
import org.candlepin.spec.bootstrap.assertions.OnlyInStandalone;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.ConsumerClient;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.builder.Subscriptions;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@SpecTest
public class HypervisorCheckInSpecTest {
    private static ApiClient client;
    private static OwnerClient ownerApi;
    private static OwnerProductApi ownerProductApi;
    private static ConsumerClient consumerApi;
    private HostedTestApi hostedTestApi;
    private OwnerDTO owner;
    private UserDTO user;
    private ApiClient userClient;
    private ApiClient hostConsumerClient;
    private String expectedHostHypervisorId;
    private String expectedHostName;
    private List<String> expectedGuestIds;
    private String guest1VirtUuid;
    private String guest2VirtUuid;
    private ConsumerDTO hostConsumer;
    private ConsumerDTO guestConsumer1;
    private ConsumerDTO guestConsumer2;
    private ProductDTO virtLimitProduct;
    private PoolDTO virtLimitPool;
    private String hostUuid;
    private String reporterId;

    @BeforeAll
    public static void beforeAll() throws Exception {
        client = ApiClients.admin();
        ownerApi = client.owners();
        consumerApi = client.consumers();
        ownerProductApi = client.ownerProducts();
    }

    @BeforeEach
    public void beforeEach() throws ApiException, IOException {
        owner = ownerApi.createOwner(Owners.random());
        user = UserUtil.createUser(client, owner);
        userClient = ApiClients.basic(user.getUsername(), user.getPassword());

        guest1VirtUuid = StringUtil.random("uuid");
        guest2VirtUuid = StringUtil.random("uuid");
        expectedHostHypervisorId = StringUtil.random("host");
        expectedHostName = StringUtil.random("name");
        expectedGuestIds = List.of(guest1VirtUuid, guest2VirtUuid);

        hostConsumer = Consumers.random(owner)
            .name(expectedHostName)
            .type(ConsumerTypes.Hypervisor.value())
            .facts(Map.of("test_fact", "fact_value"))
            .hypervisorId(new HypervisorIdDTO().hypervisorId(expectedHostHypervisorId));
        hostConsumer = consumerApi.createConsumer(hostConsumer);
        hostUuid = hostConsumer.getUuid();
        hostConsumerClient = ApiClients.ssl(hostConsumer);

        reporterId = StringUtil.random("reporter");
        hypervisorCheckin(owner, userClient, expectedHostName,
            expectedHostHypervisorId, expectedGuestIds,
            Map.of("test_fact", "fact_value"), reporterId, true);
    }

    private void initConsumersAndPools() {
        virtLimitProduct = Products.randomSKU()
            .addAttributesItem(new AttributeDTO().name("virt_limit").value("3"));
        virtLimitProduct = ownerProductApi.createProductByOwner(owner.getKey(), virtLimitProduct);

        PoolDTO pool1 = Pools.random(virtLimitProduct);
        pool1 = ownerApi.createPool(owner.getKey(), pool1);
        PoolDTO pool2 = Pools.random(virtLimitProduct);
        ownerApi.createPool(owner.getKey(), pool2);

        hostConsumerClient.consumers().bindPool(hostConsumer.getUuid(), pool1.getId(), 1);
        virtLimitPool = getVirtLimitPool(ownerApi.listOwnerPools(owner.getKey()));

        guestConsumer1 = Consumers.random(owner)
            .name(StringUtil.random("guest"))
            .type(ConsumerTypes.System.value())
            .facts(Map.of("virt.uuid", guest1VirtUuid, "virt.is_guest",
                "true", "uname.machine", "x86_64"));
        guestConsumer1 = consumerApi.createConsumer(guestConsumer1);
        guestConsumer2 = Consumers.random(owner)
            .name(StringUtil.random("guest"))
            .type(ConsumerTypes.System.value())
            .facts(Map.of("virt.uuid", guest2VirtUuid, "virt.is_guest",
                "true", "uname.machine", "x86_64"));
        guestConsumer2 = consumerApi.createConsumer(guestConsumer2);
    }

    @Test
    public void shouldAddConsumerToCreatedWhenNewHostIdAndNoGuestsReported() throws Exception {
        String hostHypId = StringUtil.random("host");
        String hostHypName = StringUtil.random("name");
        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, userClient, hostHypName,
            hostHypId, new ArrayList<>(), null, reporterId, true);
        // Should only  have a result entry for created.
        confirmResultDataCounts(resultData, 1, 0, 0, 0);
        assertEquals(hostHypName, resultData.getCreated().iterator().next().getName());
        NestedOwnerDTO resultOwner = resultData.getCreated().iterator().next().getOwner();
        assertEquals(owner.getKey(), resultOwner.getKey());

        //  verify our created consumer is correct.
        ConsumerDTO createdConsumer = consumerApi.getConsumer(
            resultData.getCreated().iterator().next().getUuid());
        assertEquals(hostHypName, createdConsumer.getName());
        assertNull(createdConsumer.getIdCert());

        // Test get_owner_hypervisors works, should return all
        assertEquals(2, ownerApi.getHypervisors(owner.getKey(), null).size());
        // Test lookup with hypervisor ids
        assertEquals(1, ownerApi.getHypervisors(owner.getKey(), List.of(hostHypId)).size());
        // Test lookup with nonexistant hypervisor id
        assertEquals(0, ownerApi.getHypervisors(owner.getKey(), List.of("non existent")).size());
        // verify last checkin time is updated
        assertNotNull(createdConsumer.getLastCheckin());
    }

    @Test
    public void shouldAddConsumerToCreatedWhenNewHostIdAndGuestsWereReported() throws Exception {
        String hostHypId = StringUtil.random("host");
        String hostHypName = StringUtil.random("name");
        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, userClient, hostHypName, hostHypId,
            List.of("g1"), null, reporterId, true);
        // Should only  have a result entry for created.
        confirmResultDataCounts(resultData, 1, 0, 0, 0);
        assertEquals(hostHypName, resultData.getCreated().iterator().next().getName());
        NestedOwnerDTO resultOwner = resultData.getCreated().iterator().next().getOwner();
        assertEquals(owner.getKey(), resultOwner.getKey());

        // verify our created consumer is correct.
        ConsumerDTO createdConsumer = consumerApi.getConsumer(
            resultData.getCreated().iterator().next().getUuid());
        assertEquals(hostHypName, createdConsumer.getName());
        // verify last checkin time is updated
        assertNotNull(createdConsumer.getLastCheckin());
    }

    @Test
    public void shouldNotAddNewConsumerWhenCreateMissingIsFalse() throws Exception {
        String hostHypId = StringUtil.random("host");
        String hostHypName = StringUtil.random("name");
        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, userClient, hostHypName,
            hostHypId,  List.of("g1"), null, reporterId, false);
        // Should only have a result entry for failed.
        confirmResultDataCounts(resultData, 0, 0, 0, 1);
    }

    @Test
    public void shouldAddConsumerToUpdatedWhenGuestIdsAreUpdated() throws Exception {
        ConsumerDTO oldCheckIn = consumerApi.getConsumer(hostUuid);
        //because of MySql not using milliseconds
        sleep(2000);

        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, userClient, expectedHostName,
            expectedHostHypervisorId,  List.of("g1", "g2"),
            Map.of("test_fact", "fact_value"), reporterId, true);
        // Should only  have a result entry for updated.
        confirmResultDataCounts(resultData, 0, 1, 0, 0);
        assertEquals(expectedHostName, resultData.getUpdated().iterator().next().getName());
        NestedOwnerDTO resultOwner = resultData.getUpdated().iterator().next().getOwner();
        assertEquals(owner.getKey(), resultOwner.getKey());

        // verify our created consumer is correct.
        ConsumerDTO updatedConsumer = consumerApi.getConsumer(
            resultData.getUpdated().iterator().next().getUuid());
        assertEquals(expectedHostName, updatedConsumer.getName());
        // verify last checkin time is updated
        assertNotNull(updatedConsumer.getLastCheckin());
        assertNotEquals(oldCheckIn.getLastCheckin(), updatedConsumer.getLastCheckin());
    }

    @Test
    public void shouldAddConsumerToUnchangedWhenSameGuestIdsAreSent() throws Exception {
        ConsumerDTO oldCheckIn = consumerApi.getConsumer(hostUuid);
        //because of MySql not using milliseconds
        sleep(2000);

        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, userClient, expectedHostName,
            expectedHostHypervisorId, expectedGuestIds,
            Map.of("test_fact", "fact_value"), reporterId, true);
        // Should only  have a result entry for unchanged.
        confirmResultDataCounts(resultData, 0, 0, 1, 0);
        assertEquals(expectedHostName, resultData.getUnchanged().iterator().next().getName());
        NestedOwnerDTO resultOwner = resultData.getUnchanged().iterator().next().getOwner();
        assertEquals(owner.getKey(), resultOwner.getKey());

        // verify our created consumer is correct.
        ConsumerDTO unchangedConsumer = consumerApi.getConsumer(
            resultData.getUnchanged().iterator().next().getUuid());
        assertEquals(expectedHostName, unchangedConsumer.getName());
        // verify last checkin time is updated
        assertNotNull(unchangedConsumer.getLastCheckin());
        assertNotEquals(oldCheckIn.getLastCheckin(), unchangedConsumer.getLastCheckin());
    }

    @Test
    public void shouldAddConsumerToUnchangedWhenComparingEmptyGuestIdLists() throws Exception {
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
        ConsumerDTO unchangedConsumer = consumerApi.getConsumer(
            resultData.getUnchanged().iterator().next().getUuid());
        assertEquals(hostHypName, unchangedConsumer.getName());
    }

    @Test
    public void shouldAddHostAndAssociateGuests() {
        ConsumerDTO consumer = consumerApi.getConsumer(hostUuid);
        checkHypervisorConsumer(consumer, expectedHostName, expectedGuestIds, reporterId);
    }

    @Test
    public void shouldUdateHostGuestIdsAsConsumer() throws Exception {
        asyncUpdateGuestIdsTest(hostConsumerClient, null);
    }

    @Test
    public void shouldPersistReportedIdOnHostGuestMappingsUpdate() throws Exception {
        asyncUpdateGuestIdsTest(hostConsumerClient, "Lois Lane");
    }

    @Test
    public void shouldUpdateHostGuestIdsAsUser() throws Exception {
        asyncUpdateGuestIdsTest(userClient, null);
    }

    @Test
    @OnlyInStandalone
    public void shouldNotRevokeGuestEntitlementsWhenGuestNoLongerMapped() throws Exception {
        initConsumersAndPools();
        ApiClient guest1Client = ApiClients.ssl(guestConsumer1);
        guest1Client.consumers().bindPool(guestConsumer1.getUuid(), virtLimitPool.getId(), 1);
        assertEquals(1, guest1Client.consumers().listEntitlements(guestConsumer1.getUuid()).size());
        // Host stops reporting guest:
        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, userClient, expectedHostName,
            expectedHostHypervisorId, List.of(guest2VirtUuid),
            Map.of("test_fact", "fact_value"), reporterId, true);
        confirmResultDataCounts(resultData, 0, 1, 0, 0);
        assertEquals(expectedHostName, resultData.getUpdated().iterator().next().getName());
        NestedOwnerDTO resultOwner = resultData.getUpdated().iterator().next().getOwner();
        assertEquals(owner.getKey(), resultOwner.getKey());

        // Entitlement should not be gone
        assertEquals(1, guest1Client.consumers().listEntitlements(guestConsumer1.getUuid()).size());
    }

    @Test
    public void shouldNotRevokeHostEntitlementsWhenGuestIdListIsEmpty() throws Exception {
        initConsumersAndPools();
        assertEquals(1, hostConsumerClient.consumers().listEntitlements(hostUuid).size());
        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, userClient, expectedHostName,
            expectedHostHypervisorId, new ArrayList<>(),
            Map.of("test_fact", "fact_value"), reporterId, true);
        confirmResultDataCounts(resultData, 0, 1, 0, 0);
        assertEquals(expectedHostName, resultData.getUpdated().iterator().next().getName());
        NestedOwnerDTO resultOwner = resultData.getUpdated().iterator().next().getOwner();
        assertEquals(owner.getKey(), resultOwner.getKey());

        // Entitlement should not be gone
        assertEquals(1, hostConsumerClient.consumers().listEntitlements(hostUuid).size());
    }

    @Test
    @OnlyInStandalone
    public void shouldNotRevokeHostAndGuestEntitlementsWhenGuestIdListIsEmpty() throws Exception {
        initConsumersAndPools();
        ApiClient guest1Client = ApiClients.ssl(guestConsumer1);
        guest1Client.consumers().bindPool(guestConsumer1.getUuid(), virtLimitPool.getId(), 1);
        assertEquals(1, guest1Client.consumers().listEntitlements(guestConsumer1.getUuid()).size());

        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, userClient, expectedHostName,
            expectedHostHypervisorId, new ArrayList<>(),
            Map.of("test_fact", "fact_value"), reporterId, true);
        confirmResultDataCounts(resultData, 0, 1, 0, 0);
        assertEquals(expectedHostName, resultData.getUpdated().iterator().next().getName());
        NestedOwnerDTO resultOwner = resultData.getUpdated().iterator().next().getOwner();
        assertEquals(owner.getKey(), resultOwner.getKey());

        // Entitlements should not be gone
        assertEquals(1, hostConsumerClient.consumers().listEntitlements(hostUuid).size());
        assertEquals(1, guest1Client.consumers().listEntitlements(guestConsumer1.getUuid()).size());
    }

    @Test
    public void shouldInitializeGuestIdListIstoEmptyWhenCreatingNewHost() throws Exception {
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
        UserDTO user2 = UserUtil.createUser(client, owner2);
        ApiClient userClient1 = ApiClients.basic(user1.getUsername(), user1.getPassword());
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
        ApiClient virtClient = createVirtwhoClient(userClient);
        String hostHypId = StringUtil.random("host");
        String hostHypName = StringUtil.random("name");
        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, virtClient, hostHypName,
            hostHypId, List.of("g1", "g2"), null, reporterId, true);
        confirmResultDataCounts(resultData, 1, 0, 0, 0);
        assertEquals(hostHypName, resultData.getCreated().iterator().next().getName());
        NestedOwnerDTO resultOwner = resultData.getCreated().iterator().next().getOwner();
        assertEquals(owner.getKey(), resultOwner.getKey());

        resultData = hypervisorCheckin(owner, virtClient, hostHypName,
            hostHypId, List.of("g1", "g2"), null, reporterId, true);
        confirmResultDataCounts(resultData, 0, 0, 1, 0);
        assertEquals(hostHypName, resultData.getUnchanged().iterator().next().getName());
        resultOwner = resultData.getUnchanged().iterator().next().getOwner();
        assertEquals(owner.getKey(), resultOwner.getKey());

    }

    @Test
    public void shouldBlockVirtWhoIfOwnerDoesNotMatchIdentityCert() throws Exception {
        OwnerDTO owner2 = ownerApi.createOwner(Owners.random());
        ApiClient virtClient = createVirtwhoClient(userClient);
        String hostHypId = StringUtil.random("host");
        String hostHypName = StringUtil.random("name");
        hypervisorCheckin(owner, virtClient, hostHypName,
            hostHypId, List.of("g1", "g2"), null, reporterId, true);
        assertNotFound(() -> hypervisorCheckin(owner2, virtClient, hostHypName,
            hostHypId, List.of("g1", "g2"), null, reporterId, true));
    }

    @Test
    public void shouldRaiseBadRequestExceptionIfMappingWasNotProvided() {
        ApiClient virtClient = createVirtwhoClient(userClient);
        assertBadRequest(() -> virtClient.hypervisors().hypervisorUpdateAsync(
            owner.getKey(), true, reporterId, null));
    }

    @Test
    public void shouldRaiseBadRequestExceptionIfInvalidMappingInputWasProvided() {
        ApiClient virtClient = createVirtwhoClient(userClient);
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
        ApiClient virtClient = createVirtwhoClient(userClient);
        String hostHypId = "";
        String hostHypName = StringUtil.random("name");
        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, virtClient, hostHypName,
            hostHypId, List.of("g1", "g2"), null, reporterId, true);
        confirmResultDataCounts(resultData, 0, 0, 0, 0);
    }

    @Test
    public void shouldIgnoreGuestIdsEqualToTheEmptyString() throws Exception {
        ApiClient virtClient = createVirtwhoClient(userClient);
        String hostHypName = StringUtil.random("name");
        String expectedGuestId = StringUtil.random("guest");
        List<String> guests = List.of(expectedGuestId, "");

        HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, virtClient, hostHypName,
            expectedHostHypervisorId, guests, null, reporterId, true);
        confirmResultDataCounts(resultData, 0, 1, 0, 0);
        String updatedConsumerUuid = resultData.getUpdated().iterator().next().getUuid();
        List<GuestIdDTOArrayElement> guestIds =  client.guestIds().getGuestIds(updatedConsumerUuid);
        assertEquals(1, guestIds.size());
        assertEquals(expectedGuestId, guestIds.get(0).getGuestId());
    }

    @Test
    @OnlyInStandalone
    public void shouldAllowASingleGuestToBeMigratedAndRevokeHostLimitedEnts() throws Exception {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        UserDTO user = UserUtil.createUser(client, owner);
        ApiClient userClient = ApiClients.basic(user.getUsername(), user.getPassword());
        String hypervisorId1 = StringUtil.random("hypervisor").toLowerCase();
        String hypervisorId2 = StringUtil.random("hypervisor").toLowerCase();
        String uuid1 = StringUtil.random("uuid");

        ConsumerDTO hostConsumer = Consumers.random(owner)
            .name(StringUtil.random("host"))
            .type(ConsumerTypes.Hypervisor.value())
            .hypervisorId(new HypervisorIdDTO().hypervisorId(hypervisorId1));
        hostConsumer = userClient.consumers().createConsumer(hostConsumer);
        ConsumerDTO newHostConsumer = Consumers.random(owner)
            .name(StringUtil.random("host"))
            .type(ConsumerTypes.Hypervisor.value())
            .hypervisorId(new HypervisorIdDTO().hypervisorId(hypervisorId2));
        newHostConsumer = userClient.consumers().createConsumer(newHostConsumer);
        ConsumerDTO guestConsumer = Consumers.random(owner)
            .name(StringUtil.random("guest"))
            .type(ConsumerTypes.System.value())
            .facts(Map.of("virt.uuid", uuid1, "virt.is_guest", "true"));
        guestConsumer = userClient.consumers().createConsumer(guestConsumer);

        // product and pool
        ProductDTO superAwesome = Products.random()
            .name(StringUtil.random("product"))
            .addAttributesItem(new AttributeDTO().name("virt_limit").value("10"))
            .addAttributesItem(new AttributeDTO().name("host_limited").value("true"));
        ownerProductApi.createProductByOwner(owner.getKey(), superAwesome);
        PoolDTO pool = Pools.random(superAwesome);
        ownerApi.createPool(owner.getKey(), pool);

        ApiClient hostConsumerClient = ApiClients.ssl(hostConsumer);
        ApiClient newHostConsumerClient = ApiClients.ssl(newHostConsumer);
        ApiClient guestConsumerClient = ApiClients.ssl(guestConsumer);

        hypervisorCheckin(owner, userClient, "tester",
            hypervisorId1, List.of(uuid1), null, reporterId, true);
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
        hypervisorCheckin(owner, userClient, "tester",
            hypervisorId2, List.of(uuid1), null, reporterId, true);
        // The guests host limited entitlement should be gone
        assertEquals(0, guestConsumerClient.consumers().listEntitlements(guestConsumer.getUuid(),
            null, true, null, null, null, null, null).size());
    }

    @Nested
    @OnlyInHosted
    public class HypervisorCheckInHostedSpecTest {
        @BeforeEach
        public void beforeEach() throws Exception {
            hostedTestApi = client.hosted();
            owner = ownerApi.createOwner(Owners.random());
            user = UserUtil.createUser(client, owner);
            userClient = ApiClients.basic(user.getUsername(), user.getPassword());

            hostConsumer = Consumers.random(owner)
                .name(expectedHostName)
                .type(ConsumerTypes.Hypervisor.value())
                .facts(Map.of("test_fact", "fact_value"))
                .hypervisorId(new HypervisorIdDTO().hypervisorId(expectedHostHypervisorId));
            hostConsumer = consumerApi.createConsumer(hostConsumer);
            hostUuid = hostConsumer.getUuid();
            hostConsumerClient = ApiClients.ssl(hostConsumer);

            hypervisorCheckin(owner, userClient, expectedHostName,
                expectedHostHypervisorId, expectedGuestIds,
                Map.of("test_fact", "fact_value"), reporterId, true);
        }

        private void initConsumersAndSubs() {
            virtLimitProduct = Products.randomSKU()
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
            List<PoolDTO> pools = userClient.owners().listOwnerPools(owner.getKey());
            List<PoolDTO> sub1Pools = pools.stream()
                .filter(x -> sub1Id.equals(x.getSubscriptionId()) && "NORMAL".equals(x.getType()))
                .collect(Collectors.toList());
            hostConsumerClient.consumers().bindPool(hostConsumer.getUuid(), sub1Pools.get(0).getId(), 1);
            virtLimitPool = pools.stream()
                .filter(x -> sub1Id.equals(x.getSubscriptionId()) && "BONUS".equals(x.getType()))
                .collect(Collectors.toList()).get(0);

            guestConsumer1 = Consumers.random(owner)
                .name(StringUtil.random("guest"))
                .type(ConsumerTypes.System.value())
                .facts(Map.of("virt.uuid", guest1VirtUuid, "virt.is_guest", "true",
                    "uname.machine", "x86_64"));
            guestConsumer1 = consumerApi.createConsumer(guestConsumer1);
            guestConsumer2 = Consumers.random(owner)
                .name(StringUtil.random("guest"))
                .type(ConsumerTypes.System.value())
                .facts(Map.of("virt.uuid", guest2VirtUuid, "virt.is_guest", "true",
                    "uname.machine", "x86_64"));
            guestConsumer2 = consumerApi.createConsumer(guestConsumer2);
        }
        @Test
        public void shouldNotRevokeGuestEntitlementsWhenGuestNoLongerMapped() throws Exception {
            initConsumersAndSubs();
            ApiClient guest1Client = ApiClients.ssl(guestConsumer1);
            guest1Client.consumers().bindPool(guestConsumer1.getUuid(), virtLimitPool.getId(), 1);
            assertEquals(1, guest1Client.consumers().listEntitlements(guestConsumer1.getUuid()).size());
            // Host stops reporting guest:
            HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, userClient, expectedHostName,
                expectedHostHypervisorId, List.of(guest2VirtUuid),
                Map.of("test_fact", "fact_value"), reporterId, true);
            confirmResultDataCounts(resultData, 0, 1, 0, 0);
            assertEquals(expectedHostName, resultData.getUpdated().iterator().next().getName());
            NestedOwnerDTO resultOwner = resultData.getUpdated().iterator().next().getOwner();
            assertEquals(owner.getKey(), resultOwner.getKey());

            // Entitlement should not be gone
            assertEquals(1, guest1Client.consumers().listEntitlements(guestConsumer1.getUuid()).size());
        }

        @Test
        public void shouldNotRevokeHostAndGuestEntitlementsWhenGuestIdListIsEmpty() throws Exception {
            initConsumersAndSubs();
            ApiClient guest1Client = ApiClients.ssl(guestConsumer1);
            guest1Client.consumers().bindPool(guestConsumer1.getUuid(), virtLimitPool.getId(), 1);
            assertEquals(1, guest1Client.consumers().listEntitlements(guestConsumer1.getUuid()).size());

            HypervisorUpdateResultDTO resultData = hypervisorCheckin(owner, userClient, expectedHostName,
                expectedHostHypervisorId, new ArrayList<>(),
                Map.of("test_fact", "fact_value"), reporterId, true);
            confirmResultDataCounts(resultData, 0, 1, 0, 0);
            assertEquals(expectedHostName, resultData.getUpdated().iterator().next().getName());
            NestedOwnerDTO resultOwner = resultData.getUpdated().iterator().next().getOwner();
            assertEquals(owner.getKey(), resultOwner.getKey());

            // Entitlements should not be gone
            assertEquals(1, hostConsumerClient.consumers().listEntitlements(hostUuid).size());
            assertEquals(1, guest1Client.consumers().listEntitlements(guestConsumer1.getUuid()).size());
        }

        @Test
        public void shouldAllowASingleGuestToBeMigratedAndRevokeHostLimitedEntsHosted() throws Exception {
            OwnerDTO owner = ownerApi.createOwner(Owners.random());
            UserDTO user = UserUtil.createUser(client, owner);
            ApiClient userClient = ApiClients.basic(user.getUsername(), user.getPassword());
            String hypervisorId1 = StringUtil.random("hypervisor").toLowerCase();
            String hypervisorId2 = StringUtil.random("hypervisor").toLowerCase();
            String uuid1 = StringUtil.random("uuid");

            ConsumerDTO hostConsumer = Consumers.random(owner)
                .name(StringUtil.random("host"))
                .type(ConsumerTypes.Hypervisor.value())
                .hypervisorId(new HypervisorIdDTO().hypervisorId(hypervisorId1));
            hostConsumer = userClient.consumers().createConsumer(hostConsumer);
            ConsumerDTO newHostConsumer = Consumers.random(owner)
                .name(StringUtil.random("host"))
                .type(ConsumerTypes.Hypervisor.value())
                .hypervisorId(new HypervisorIdDTO().hypervisorId(hypervisorId2));
            newHostConsumer = userClient.consumers().createConsumer(newHostConsumer);
            ConsumerDTO guestConsumer = Consumers.random(owner)
                .name(StringUtil.random("guest"))
                .type(ConsumerTypes.System.value())
                .facts(Map.of("virt.uuid", uuid1, "virt.is_guest", "true"));
            guestConsumer = userClient.consumers().createConsumer(guestConsumer);

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

            hypervisorCheckin(owner, userClient, "tester",
                hypervisorId1, List.of(uuid1), null, reporterId, true);
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
            hypervisorCheckin(owner, userClient, "tester",
                hypervisorId2, List.of(uuid1), null, reporterId, true);
            // The guests host limited entitlement should be gone
            assertEquals(0, guestConsumerClient.consumers().listEntitlements(guestConsumer.getUuid(),
                null, true, null, null, null, null, null).size());
        }
    }

    private HypervisorUpdateResultDTO hypervisorCheckin(OwnerDTO owner, ApiClient consumerClient,
        String hypervisorName, String hypervisorId, List<String> guestIds, Map<String, String> facts,
        String reporterId, boolean createMissing) throws ApiException, IOException {
        JsonNode hostGuestMapping = getAsyncHostGuestMapping(hypervisorName, hypervisorId, guestIds, facts);
        AsyncJobStatusDTO job = consumerClient.hypervisors().hypervisorUpdateAsync(
            owner.getKey(), createMissing, reporterId, hostGuestMapping.toString());
        HypervisorUpdateResultDTO resultData = null;
        if (job != null) {
            AsyncJobStatusDTO status = client.jobs().waitForJob(job.getId());
            assertEquals("FINISHED", status.getState());
            resultData = getResultData(status);
        }
        return resultData;
    }

    private void asyncUpdateGuestIdsTest(ApiClient client, String reporter) throws Exception {
        List<String> updatedGuestIds = List.of(guest2VirtUuid, "Guest3");
        JsonNode mapping = getAsyncHostGuestMapping(
            expectedHostName, expectedHostHypervisorId, updatedGuestIds, null);
        AsyncJobStatusDTO job = client.hypervisors().hypervisorUpdateAsync(
            owner.getKey(), true, reporter, mapping.toString());
        HypervisorUpdateResultDTO resultData = null;
        if (job != null) {
            AsyncJobStatusDTO status = client.jobs().waitForJob(job.getId());
            assertEquals("FINISHED", status.getState());
            resultData = getResultData(status);
        }
        confirmResultDataCounts(resultData, 0, 1, 0, 0);
        ConsumerDTO consumer = consumerApi.getConsumer(hostUuid);
        checkHypervisorConsumer(consumer, expectedHostName, updatedGuestIds, reporter);
    }

    /**
     * The HypervisorUpdateResultDTO fromJson method will not parse the raw string from the
     *   AsyncJobStatusDTO
     *
     * @param status
     * @return HypervisorUpdateResultDTO
     * @throws IOException
     */
    private HypervisorUpdateResultDTO getResultData(AsyncJobStatusDTO status) throws IOException {
        Map resultData = (Map) status.getResultData();
        Gson gson = new GsonBuilder().create();
        String json = gson.toJson(resultData);
        return HypervisorUpdateResultDTO.fromJson(json);
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
        Map facts) {
        ObjectMapper om = new ObjectMapper();
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

    private ApiClient createVirtwhoClient(ApiClient userClient) throws ApiException {
        ConsumerDTO consumer = Consumers.random(owner, ConsumerTypes.System)
            .installedProducts(Set.of(
                new ConsumerInstalledProductDTO().productId("installedProd").productName("Installed")));
        consumer = userClient.consumers().createConsumer(consumer);
        return ApiClients.ssl(consumer);
    }

    PoolDTO getVirtLimitPool(List<PoolDTO> pools) {
        for (PoolDTO pool : pools) {
            for (AttributeDTO attribute : pool.getAttributes()) {
                if (attribute.getName().equals("virt_only") && attribute.getValue().equals("true")) {
                    return pool;
                }
            }
        }
        return null;
    }

}
