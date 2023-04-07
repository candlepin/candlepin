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

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertForbidden;

import org.candlepin.dto.api.client.v1.AttributeDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTOArrayElement;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.GuestIdDTO;
import org.candlepin.dto.api.client.v1.GuestIdDTOArrayElement;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpecTest
public class GuestsIdResourceSpecTest {

    private ApiClient admin;
    private OwnerDTO owner;
    ApiClient userClient;

    @BeforeEach
    public void beforeEach() {
        admin = ApiClients.admin();
        owner = admin.owners().createOwner(Owners.random());
        UserDTO user = UserUtil.createUser(admin, owner);
        userClient = ApiClients.basic(user);
    }

    @Test
    public void shouldAllowAddingGuestIdsToHostConsumer() {
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));

        assertThat(consumer)
            .isNotNull()
            .extracting(ConsumerDTO::getGuestIds)
            .satisfies(ids -> assertThat(ids).isEmpty());

        ApiClient consumerClient = ApiClients.ssl(consumer);
        linkHostToGuests(consumerClient, consumer, "guest1");

        List<GuestIdDTOArrayElement> guestIds = consumerClient.guestIds().getGuestIds(consumer.getUuid());
        assertThat(guestIds)
            .singleElement()
            .returns("guest1", GuestIdDTOArrayElement::getGuestId);

    }

    @Test
    public void shouldAllowUpdatingGuestIdsFromHostConsumer() {
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));

        assertThat(consumer)
            .isNotNull()
            .extracting(ConsumerDTO::getGuestIds)
            .satisfies(ids -> assertThat(ids).isEmpty());

        ApiClient consumerClient = ApiClients.ssl(consumer);
        linkHostToGuests(consumerClient, consumer, "guest1", "guest2");

        List<GuestIdDTOArrayElement> guestIds = consumerClient.guestIds().getGuestIds(consumer.getUuid());
        assertThat(guestIds)
            .hasSize(2);

        linkHostToGuests(consumerClient, consumer, "guest1");
        guestIds = consumerClient.guestIds().getGuestIds(consumer.getUuid());
        assertThat(guestIds)
            .singleElement()
            .returns("guest1", GuestIdDTOArrayElement::getGuestId);

    }

    @Test
    public void shouldClearGuestIdsWhenEmptyListIsProvided() {
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));

        assertThat(consumer)
            .isNotNull()
            .extracting(ConsumerDTO::getGuestIds)
            .satisfies(ids -> assertThat(ids).isEmpty());

        ApiClient consumerClient = ApiClients.ssl(consumer);
        linkHostToGuests(consumerClient, consumer, "guest1");

        List<GuestIdDTOArrayElement> guestIds = consumerClient.guestIds().getGuestIds(consumer.getUuid());
        assertThat(guestIds)
            .singleElement()
            .returns("guest1", GuestIdDTOArrayElement::getGuestId);

        linkHostToGuests(consumerClient, consumer);
        guestIds = consumerClient.guestIds().getGuestIds(consumer.getUuid());
        assertThat(guestIds)
            .isEmpty();

    }

    @Test
    public void shouldAllowHostToListGuests() {
        ConsumerDTO hostConsumer = userClient.consumers().createConsumer(Consumers.random(owner));
        String uuid1 = StringUtil.random("system.uuid");
        userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("virt.uuid", uuid1)));
        String uuid2 = StringUtil.random("system.uuid");
        userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("virt.uuid", uuid2)));

        ApiClient consumerClient = ApiClients.ssl(hostConsumer);
        linkHostToGuests(consumerClient, hostConsumer, uuid1, uuid2);

        List<ConsumerDTOArrayElement> guests = admin.consumers().getGuests(hostConsumer.getUuid());
        assertThat(guests)
            .hasSize(2);
    }

    @Test
    public void shouldNotAllowHostToListGuestsThatAnotherHostHasClaimed() throws InterruptedException {
        ConsumerDTO hostConsumer1 = userClient.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO hostConsumer2 = userClient.consumers().createConsumer(Consumers.random(owner));
        String uuid1 = StringUtil.random("system.uuid");
        ConsumerDTO guestConsumer1 = userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("virt.uuid", uuid1)));
        String uuid2 = StringUtil.random("system.uuid");
        userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("virt.uuid", uuid2)));

        ApiClient consumerClient1 = ApiClients.ssl(hostConsumer1);
        linkHostToGuests(consumerClient1, hostConsumer1, uuid1, uuid2);

        // MySQL before 5.6.4 doesn't store fractional seconds on timestamps
        // and getHost() method in ConsumerCurator (which is what tells us which
        // host a guest is associated with) sorts results by updated time.
        Thread.sleep(1000);

        ApiClient consumerClient2 = ApiClients.ssl(hostConsumer2);
        linkHostToGuests(consumerClient2, hostConsumer2, uuid2);

        admin.consumers().listEntitlements(
            hostConsumer1.getUuid(), null, true, null, null, null, null, null);

        List<ConsumerDTOArrayElement> guests = admin.consumers().getGuests(hostConsumer1.getUuid());
        assertThat(guests)
            .singleElement()
            .returns(guestConsumer1.getUuid(), ConsumerDTOArrayElement::getUuid);
    }

    @Test
    public void shouldAllowSingleGuestToBeDeleted() {
        ConsumerDTO hostConsumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(hostConsumer);
        String uuid1 = StringUtil.random("system.uuid");

        linkHostToGuests(consumerClient, hostConsumer, uuid1);

        List<GuestIdDTOArrayElement> guestIds = admin.guestIds().getGuestIds(hostConsumer.getUuid());
        assertThat(guestIds)
            .hasSize(1);
        admin.guestIds().deleteGuest(hostConsumer.getUuid(), uuid1, false);
        guestIds = admin.guestIds().getGuestIds(hostConsumer.getUuid());
        assertThat(guestIds)
            .isEmpty();
    }

    @Test
    @SuppressWarnings("checkstyle:indentation")
    public void shouldAllowSingleGuestToBeUpdatedAndRevokesHostLimitedEnts() {
        ConsumerDTO hostConsumer1 = userClient.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO hostConsumer2 = userClient.consumers().createConsumer(Consumers.random(owner));
        String uuid1 = StringUtil.random("system.uuid");
        ConsumerDTO guestConsumer = userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("virt.uuid", uuid1, "virt.is_guest", "true")));

        // Create a product/subscription
        ProductDTO product = admin.ownerProducts().createProductByOwner(owner.getKey(),
            Products.withAttributes(ProductAttributes.VirtualLimit.withValue("10"),
                ProductAttributes.HostLimited.withValue("true")));
        admin.owners().createPool(owner.getKey(), Pools.random(product));
        ApiClient consumerClient1 = ApiClients.ssl(hostConsumer1);
        ApiClient consumerClient2 = ApiClients.ssl(hostConsumer2);
        ApiClient guestClient = ApiClients.ssl(guestConsumer);
        linkHostToGuests(consumerClient1, hostConsumer1, uuid1);
        List<PoolDTO> pools = admin.pools().listPools(
            null, hostConsumer1.getUuid(), null, null, null, null, null, null, null);
        assertThat(pools)
            .hasSize(1);
        consumerClient1.consumers().bind(
            hostConsumer1.getUuid(), pools.get(0).getId(), null, 1, null, null, null, null, null);

        admin.owners().refreshPools(owner.getKey(), false);
        List<PoolDTO> guestPools = admin.pools().listPools(
            null, guestConsumer.getUuid(), null, null, null, null, null, null, null);

        // The original pool and the new host limited pool should be available
        assertThat(guestPools)
            .hasSize(2);
        // Get the guest pool
        PoolDTO guestPool = findGuestPool(guestClient, guestConsumer);

        // Make sure the required host is actually the host
        assertRequiredHost(guestPool, hostConsumer1);

        // Consume the host limited pool
        guestClient.consumers().bindPool(guestConsumer.getUuid(), guestPool.getId(), 1);

        // Should have a host with 1 registered guest
        List<ConsumerDTOArrayElement> guestsList = admin.consumers().getGuests(hostConsumer1.getUuid());
        assertThat(guestsList)
            .singleElement()
            .returns(guestConsumer.getUuid(), ConsumerDTOArrayElement::getUuid);

        List<EntitlementDTO> entitlementList = guestClient.consumers()
            .listEntitlements(guestConsumer.getUuid());
        assertThat(entitlementList)
            .hasSize(1);

        // Updating to a new host should remove host specific entitlements
        consumerClient2.guestIds().updateGuest(
            hostConsumer2.getUuid(), uuid1, new GuestIdDTO().guestId(uuid1));
        List<GuestIdDTOArrayElement> guestIdsHost1 =
            consumerClient1.guestIds().getGuestIds(hostConsumer1.getUuid());
        List<GuestIdDTOArrayElement> guestIdsHost2 =
            consumerClient2.guestIds().getGuestIds(hostConsumer2.getUuid());

        assertThat(guestIdsHost1)
            .isEmpty();
        assertThat(guestIdsHost2)
            .hasSize(1);

        // The guests host limited entitlement should be gone
        entitlementList = guestClient.consumers().listEntitlements(
            guestConsumer.getUuid(), null, true, null, null, null, null, null);
        assertThat(entitlementList)
            .isEmpty();

    }

    @Test
    @SuppressWarnings("checkstyle:indentation")
    public void shouldAllowSingleGuestToBeUpdatedAndNotRevokeHostLimitedEnts() {
        ConsumerDTO hostConsumer = userClient.consumers().createConsumer(Consumers.random(owner));
        String uuid1 = StringUtil.random("system.uuid");
        ConsumerDTO guestConsumer = userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("virt.uuid", uuid1, "virt.is_guest", "true")));

        // Create a product/subscription
        ProductDTO product = admin.ownerProducts().createProductByOwner(owner.getKey(),
            Products.withAttributes(ProductAttributes.VirtualLimit.withValue("10"),
                ProductAttributes.HostLimited.withValue("true")));
        admin.owners().createPool(owner.getKey(), Pools.random(product));
        ApiClient consumerClient = ApiClients.ssl(hostConsumer);
        ApiClient guestClient = ApiClients.ssl(guestConsumer);
        linkHostToGuests(consumerClient, hostConsumer, uuid1);
        List<PoolDTO> pools = admin.pools().listPools(
            null, hostConsumer.getUuid(), null, null, null, null, null, null, null);
        assertThat(pools)
            .hasSize(1);
        consumerClient.consumers().bind(
            hostConsumer.getUuid(), pools.get(0).getId(), null, 1, null, null, null, null, null);

        admin.owners().refreshPools(owner.getKey(), false);
        List<PoolDTO> guestPools = admin.pools().listPools(
            null, guestConsumer.getUuid(), null, null, null, null, null, null, null);

        // The original pool and the new host limited pool should be available
        assertThat(guestPools)
            .hasSize(2);
        // Get the guest pool
        PoolDTO guestPool = findGuestPool(guestClient, guestConsumer);

        // Make sure the required host is actually the host
        assertRequiredHost(guestPool, hostConsumer);

        // Consume the host limited pool
        guestClient.consumers().bindPool(guestConsumer.getUuid(), guestPool.getId(), 1);

        // Should have a host with 1 registered guest
        List<ConsumerDTOArrayElement> guestsList = admin.consumers().getGuests(hostConsumer.getUuid());
        assertThat(guestsList)
            .singleElement()
            .returns(guestConsumer.getUuid(), ConsumerDTOArrayElement::getUuid);

        List<EntitlementDTO> entitlementList = guestClient.consumers()
            .listEntitlements(guestConsumer.getUuid());
        assertThat(entitlementList)
            .hasSize(1);

        // Updating to the same host shouldn't revoke entitlements
        consumerClient.guestIds().updateGuest(hostConsumer.getUuid(), uuid1,
            new GuestIdDTO().guestId(uuid1).attributes(Map.of("some attr", "crazy new value")));

        List<GuestIdDTOArrayElement> guestIdsHost =
            consumerClient.guestIds().getGuestIds(hostConsumer.getUuid());
        entitlementList = guestClient.consumers().listEntitlements(
            guestConsumer.getUuid(), null, true, null, null, null, null, null);

        assertThat(guestIdsHost)
            .hasSize(1);
        assertThat(entitlementList)
            .hasSize(1);
    }

    @Test
    public void shouldNotAllowConsumerToUnregisterAnotherConsumerDuringGuestDeletion() {
        ConsumerDTO hostConsumer = userClient.consumers().createConsumer(Consumers.random(owner));
        String uuid1 = StringUtil.random("system.uuid");
        ConsumerDTO guestConsumer = userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("virt.uuid", uuid1, "virt.is_guest", "true")));

        ApiClient consumerClient = ApiClients.ssl(hostConsumer);
        linkHostToGuests(consumerClient, hostConsumer, uuid1);

        // Should have a host with 1 registered guest
        List<ConsumerDTOArrayElement> guestsList = admin.consumers().getGuests(hostConsumer.getUuid());
        assertThat(guestsList)
            .singleElement()
            .returns(guestConsumer.getUuid(), ConsumerDTOArrayElement::getUuid);

        // Should have a host with 1 registered guest
        assertForbidden(() -> consumerClient.guestIds().deleteGuest(hostConsumer.getUuid(), uuid1, true));
    }

    @Test
    public void shouldAllowUpdatingSingleGuestsAttributes() {
        String uuid1 = StringUtil.random("system.uuid");
        ConsumerDTO hostConsumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(hostConsumer);
        consumerClient.guestIds().updateGuests(hostConsumer.getUuid(),
            List.of(new GuestIdDTO().guestId(uuid1).attributes(Map.of("test", "hello"))));

        consumerClient.guestIds().updateGuests(hostConsumer.getUuid(),
            List.of(new GuestIdDTO().guestId(uuid1).attributes(Map.of("some_attr", "some_value"))));

        GuestIdDTO guestId = consumerClient.guestIds().getGuestId(hostConsumer.getUuid(), uuid1);
        assertThat(guestId)
            .isNotNull()
            .extracting(GuestIdDTO::getAttributes)
            .hasFieldOrPropertyWithValue("some_attr", "some_value");
    }

    @Test
    public void shouldAllowCreationOfNewGuestViaUpdate() {
        ConsumerDTO hostConsumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(hostConsumer);

        String uuid1 = StringUtil.random("guestid");
        consumerClient.guestIds().updateGuests(hostConsumer.getUuid(),
            List.of(new GuestIdDTO().guestId(uuid1).attributes(Map.of("some_attr", "some_value"))));

        GuestIdDTO guestId = consumerClient.guestIds().getGuestId(hostConsumer.getUuid(), uuid1);
        assertThat(guestId)
            .isNotNull()
            .extracting(GuestIdDTO::getAttributes)
            .hasFieldOrPropertyWithValue("some_attr", "some_value");
    }

    @Test
    public void shouldNotRewriteExistingGuestIdsOnHostConsumer() {
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        String guestId1 = StringUtil.random("guestid1");
        String guestId2 = StringUtil.random("guestid2");

        linkHostToGuests(consumerClient, consumer, guestId1, guestId2);
        List<GuestIdDTOArrayElement> guestIds = consumerClient.guestIds().getGuestIds(consumer.getUuid());
        assertThat(guestIds)
            .hasSize(2);

        OffsetDateTime updated = null;
        for (GuestIdDTOArrayElement guest: guestIds) {
            if (guest.getGuestId().equals(guestId2)) {
                updated = guest.getUpdated();
            }
        }
        assertThat(updated)
            .isNotNull();

        linkHostToGuests(consumerClient, consumer, guestId2);
        guestIds = consumerClient.guestIds().getGuestIds(consumer.getUuid());
        assertThat(guestIds)
            .singleElement()
            .returns(guestId2, GuestIdDTOArrayElement::getGuestId)
            .returns(updated, GuestIdDTOArrayElement::getUpdated);
    }

    private void assertRequiredHost(PoolDTO pool, ConsumerDTO host) {
        Map<String, String> collect = pool.getAttributes().stream()
            .collect(Collectors.toMap(AttributeDTO::getName, AttributeDTO::getValue));
        assertThat(collect).containsEntry("requires_host", host.getUuid());
    }

    private void linkHostToGuests(ApiClient hostClient, ConsumerDTO host, String... guestUuids) {
        List<GuestIdDTO> guestIds = Arrays.stream(guestUuids)
            .map(this::toGuestId)
            .collect(Collectors.toList());
        linkHostToGuests(hostClient, host, guestIds);
    }

    private void linkHostToGuests(ApiClient hostClient, ConsumerDTO host, List<GuestIdDTO> guestIds) {
        hostClient.guestIds().updateGuests(host.getUuid(), guestIds);
    }

    private GuestIdDTO toGuestId(String guestId) {
        return new GuestIdDTO().guestId(guestId);
    }

    private PoolDTO findGuestPool(ApiClient guestClient, ConsumerDTO guest) {
        List<PoolDTO> guestPools = guestClient.pools().listPoolsByConsumer(guest.getUuid());
        return findGuestPool(guestPools);
    }

    private PoolDTO findGuestPool(List<PoolDTO> pools) {
        return pools.stream()
            .filter(poolDTO -> poolDTO.getSourceEntitlement() != null)
            .findFirst()
            .orElseThrow();
    }

}
