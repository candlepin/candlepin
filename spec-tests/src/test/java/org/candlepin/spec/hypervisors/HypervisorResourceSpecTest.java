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
package org.candlepin.spec.hypervisors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;

import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.GuestIdDTO;
import org.candlepin.dto.api.client.v1.HypervisorConsumerWithGuestDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Facts;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpecTest
public class HypervisorResourceSpecTest {

    private static final int MAX_CONSUMER_UUIDS = 1000;

    @Test
    public void shouldNotRetrieveHypervisorsAndGuestsWithExceedingTheConsumerUuidLimit() {
        ApiClient admin = ApiClients.admin();
        OwnerDTO owner = admin.owners().createOwner(Owners.random());

        List<String> consumerUuids = new ArrayList<>();
        for (int i = 0; i < MAX_CONSUMER_UUIDS + 1; i++) {
            consumerUuids.add(StringUtil.random("consumer-uuid-"));
        }

        assertBadRequest(() -> admin.hypervisors()
            .getHypervisorsAndGuests(owner.getKey(), consumerUuids));
    }

    @Test
    public void shouldRetrieveHypervisorsAndGuestsWithUnknownConsumerUuid() {
        ApiClient admin = ApiClients.admin();
        OwnerDTO owner = admin.owners().createOwner(Owners.random());

        ConsumerDTO host = admin.consumers().createConsumer(Consumers.random(owner));
        String guestVirtUuid = StringUtil.random("guest-virt-uuid-");
        ConsumerDTO guest = createGuest(admin, owner, guestVirtUuid);
        linkHostToGuests(admin, host, guestVirtUuid);

        HypervisorConsumerWithGuestDTO expected = new HypervisorConsumerWithGuestDTO()
            .hypervisorConsumerName(host.getName())
            .hypervisorConsumerUuid(host.getUuid())
            .guestId(guestVirtUuid)
            .guestUuid(guest.getUuid());

        List<HypervisorConsumerWithGuestDTO> actual = admin.hypervisors()
            .getHypervisorsAndGuests(owner.getKey(), List.of(guest.getUuid(), StringUtil.random("unknown-")));

        assertThat(actual)
            .isNotNull()
            .singleElement()
            .isEqualTo(expected);
    }

    @Test
    public void shouldNotRetrieveHypervisorsAndGuestsWithUnknownOwner() {
        ApiClient admin = ApiClients.admin();
        OwnerDTO owner1 = admin.owners().createOwner(Owners.random());
        OwnerDTO owner2 = admin.owners().createOwner(Owners.random());

        ConsumerDTO host = admin.consumers().createConsumer(Consumers.random(owner1));
        String guestVirtUuid = StringUtil.random("guest-virt-uuid-");
        ConsumerDTO guest = createGuest(admin, owner1, guestVirtUuid);
        linkHostToGuests(admin, host, guestVirtUuid);

        List<HypervisorConsumerWithGuestDTO> actual = admin.hypervisors()
            .getHypervisorsAndGuests(owner2.getKey(), List.of(guest.getUuid()));

        assertThat(actual)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void shouldIgnoreHostUuidWhenRetrievingHypervisorsAndGuests() {
        ApiClient admin = ApiClients.admin();
        OwnerDTO owner = admin.owners().createOwner(Owners.random());

        ConsumerDTO host = admin.consumers().createConsumer(Consumers.random(owner));
        String guestVirtUuid = StringUtil.random("guest-virt-uuid-");
        ConsumerDTO guest = createGuest(admin, owner, guestVirtUuid);
        linkHostToGuests(admin, host, guestVirtUuid);

        HypervisorConsumerWithGuestDTO expected = new HypervisorConsumerWithGuestDTO()
            .hypervisorConsumerName(host.getName())
            .hypervisorConsumerUuid(host.getUuid())
            .guestId(guestVirtUuid)
            .guestUuid(guest.getUuid());

        List<HypervisorConsumerWithGuestDTO> actual = admin.hypervisors()
            .getHypervisorsAndGuests(owner.getKey(), List.of(guest.getUuid(), host.getUuid()));

        assertThat(actual)
            .isNotNull()
            .singleElement()
            .isEqualTo(expected);
    }

    @Test
    public void shouldRetrieveHypervisorsAndGuestsWithUuidCaseDifferences() {
        ApiClient admin = ApiClients.admin();
        OwnerDTO owner = admin.owners().createOwner(Owners.random());

        ConsumerDTO host = admin.consumers().createConsumer(Consumers.random(owner));
        String guestVirtUuid = StringUtil.random("guest-virt-uuid-");
        ConsumerDTO guest = createGuest(admin, owner, guestVirtUuid);
        linkHostToGuests(admin, host, guestVirtUuid.toUpperCase());

        HypervisorConsumerWithGuestDTO expected = new HypervisorConsumerWithGuestDTO()
            .hypervisorConsumerName(host.getName())
            .hypervisorConsumerUuid(host.getUuid())
            .guestId(guestVirtUuid)
            .guestUuid(guest.getUuid());

        List<HypervisorConsumerWithGuestDTO> actual = admin.hypervisors()
            .getHypervisorsAndGuests(owner.getKey(), List.of(guest.getUuid()));

        assertThat(actual)
            .isNotNull()
            .singleElement()
            .isEqualTo(expected);
    }

    @Test
    public void shouldRetrieveHypervisorsAndGuestsWithReverseEndianness() {
        ApiClient admin = ApiClients.admin();
        OwnerDTO owner = admin.owners().createOwner(Owners.random());

        ConsumerDTO host = admin.consumers().createConsumer(Consumers.random(owner));
        String guestVirtUuid = "78d7e200-b7d6-4cfe-b7a9-5700e8094df3";
        ConsumerDTO guest = createGuest(admin, owner, guestVirtUuid);
        // virt-uuid has reversed-endianness in the first 3 sections
        linkHostToGuests(admin, host, "00e2d778-d6b7-fe4c-b7a9-5700e8094df3");

        HypervisorConsumerWithGuestDTO expected = new HypervisorConsumerWithGuestDTO()
            .hypervisorConsumerName(host.getName())
            .hypervisorConsumerUuid(host.getUuid())
            .guestId(guestVirtUuid)
            .guestUuid(guest.getUuid());

        List<HypervisorConsumerWithGuestDTO> actual = admin.hypervisors()
            .getHypervisorsAndGuests(owner.getKey(), List.of(guest.getUuid()));

        assertThat(actual)
            .isNotNull()
            .singleElement()
            .isEqualTo(expected);
    }

    @Test
    public void shouldRetrieveMostRecentHypervisorAndGuest() throws Exception {
        ApiClient admin = ApiClients.admin();
        OwnerDTO owner = admin.owners().createOwner(Owners.random());

        ConsumerDTO oldHost = admin.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO newHost = admin.consumers().createConsumer(Consumers.random(owner));

        String guestVirtUuid = StringUtil.random("guest-virt-uuid-");
        ConsumerDTO guest = createGuest(admin, owner, guestVirtUuid);
        linkHostToGuests(admin, oldHost, guestVirtUuid);

        Thread.sleep(1000);

        linkHostToGuests(admin, newHost, guestVirtUuid);

        HypervisorConsumerWithGuestDTO expected = new HypervisorConsumerWithGuestDTO()
            .hypervisorConsumerName(newHost.getName())
            .hypervisorConsumerUuid(newHost.getUuid())
            .guestId(guestVirtUuid)
            .guestUuid(guest.getUuid());

        List<HypervisorConsumerWithGuestDTO> actual = admin.hypervisors()
            .getHypervisorsAndGuests(owner.getKey(), List.of(guest.getUuid()));

        assertThat(actual)
            .isNotNull()
            .singleElement()
            .isEqualTo(expected);
    }

    @Test
    public void shouldRetrieveHypervisorsAndGuests() {
        ApiClient admin = ApiClients.admin();
        OwnerDTO owner = admin.owners().createOwner(Owners.random());

        ConsumerDTO host1 = admin.consumers().createConsumer(Consumers.random(owner));
        String host1Guest1VirtUuid = StringUtil.random("host1-guest1-virt-uuid-");
        String host1Guest2VirtUuid = StringUtil.random("host1-guest2-virt-uuid-");
        ConsumerDTO host1Guest1 = createGuest(admin, owner, host1Guest1VirtUuid);
        ConsumerDTO host1Guest2 = createGuest(admin, owner, host1Guest2VirtUuid);
        linkHostToGuests(admin, host1, host1Guest1VirtUuid, host1Guest2VirtUuid);

        ConsumerDTO host2 = admin.consumers().createConsumer(Consumers.random(owner));
        String host2Guest1VirtUuid = StringUtil.random("host2-guest1-virt-uuid-");
        String host2Guest2VirtUuid = StringUtil.random("host2-guest2-virt-uuid-");
        ConsumerDTO host2Guest1 = createGuest(admin, owner, host2Guest1VirtUuid);
        ConsumerDTO host2Guest2 = createGuest(admin, owner, host2Guest2VirtUuid);
        linkHostToGuests(admin, host2, host2Guest1VirtUuid, host2Guest2VirtUuid);

        HypervisorConsumerWithGuestDTO expected1 = new HypervisorConsumerWithGuestDTO()
            .hypervisorConsumerName(host1.getName())
            .hypervisorConsumerUuid(host1.getUuid())
            .guestId(host1Guest1VirtUuid)
            .guestUuid(host1Guest1.getUuid());
        HypervisorConsumerWithGuestDTO expected2 = new HypervisorConsumerWithGuestDTO()
            .hypervisorConsumerName(host1.getName())
            .hypervisorConsumerUuid(host1.getUuid())
            .guestId(host1Guest2VirtUuid)
            .guestUuid(host1Guest2.getUuid());
        HypervisorConsumerWithGuestDTO expected3 = new HypervisorConsumerWithGuestDTO()
            .hypervisorConsumerName(host2.getName())
            .hypervisorConsumerUuid(host2.getUuid())
            .guestId(host2Guest1VirtUuid)
            .guestUuid(host2Guest1.getUuid());
        HypervisorConsumerWithGuestDTO expected4 = new HypervisorConsumerWithGuestDTO()
            .hypervisorConsumerName(host2.getName())
            .hypervisorConsumerUuid(host2.getUuid())
            .guestId(host2Guest2VirtUuid)
            .guestUuid(host2Guest2.getUuid());

        // The hosts should be ignored.
        List<String> body =  List.of(host1Guest1.getUuid(),
            host1Guest2.getUuid(),
            host2Guest1.getUuid(),
            host2Guest2.getUuid());

        List<HypervisorConsumerWithGuestDTO> actual = admin.hypervisors()
            .getHypervisorsAndGuests(owner.getKey(), body);

        assertThat(actual)
            .isNotNull()
            .hasSize(4)
            .containsExactlyInAnyOrder(expected1, expected2, expected3, expected4);
    }

    private ConsumerDTO createGuest(ApiClient client, OwnerDTO owner, String virtUuid) {
        return client.consumers().createConsumer(Consumers.random(owner).facts(Map.ofEntries(
            Facts.VirtUuid.withValue(virtUuid),
            Facts.VirtIsGuest.withValue("true"),
            Facts.Arch.withValue("x86_64")
        )));
    }

    private void linkHostToGuests(ApiClient client, ConsumerDTO host, Collection<String> virtUuids) {
        List<GuestIdDTO> guestIds = virtUuids.stream()
            .map(this::toGuestId)
            .collect(Collectors.toList());

        linkHostToGuests(client, host, guestIds);
    }

    private void linkHostToGuests(ApiClient client, ConsumerDTO host, String... virtUuid) {
        List<GuestIdDTO> guestIds = Arrays.stream(virtUuid)
            .map(this::toGuestId)
            .collect(Collectors.toList());

        linkHostToGuests(client, host, guestIds);
    }

    private void linkHostToGuests(ApiClient client, ConsumerDTO host, List<GuestIdDTO> guestIds) {
        client.consumers()
            .updateConsumer(host.getUuid(), host.guestIds(guestIds));
    }

    private GuestIdDTO toGuestId(String guestId) {
        return new GuestIdDTO()
            .guestId(guestId);
    }

}
