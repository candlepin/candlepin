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

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;

import org.candlepin.dto.api.client.v1.AttributeDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTOArrayElement;
import org.candlepin.dto.api.client.v1.ConsumerInstalledProductDTO;
import org.candlepin.dto.api.client.v1.EntitlementDTO;
import org.candlepin.dto.api.client.v1.GuestIdDTO;
import org.candlepin.dto.api.client.v1.GuestIdDTOArrayElement;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PoolDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;


@SpecTest
public class ConsumerResourceHostGuestSpecTest {

    private static ApiClient client;
    private static OwnerClient ownerApi;
    private static OwnerProductApi ownerProductApi;

    @BeforeAll
    public static void beforeAll() {
        client = ApiClients.admin();
        ownerApi = client.owners();
        ownerProductApi = client.ownerProducts();
    }

    @Test
    public void shouldAllowAddingGuestIdsToHostConsumerOnUpdate() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        List<GuestIdDTO> guests = List.of(new GuestIdDTO().guestId("guest1"));
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));
        assertThat(consumer)
            .isNotNull()
            .returns(List.of(), ConsumerDTO::getGuestIds);

        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumerClient.consumers().updateConsumer(consumer.getUuid(), new ConsumerDTO().guestIds(guests));
        assertThat(consumerClient.guestIds().getGuestIds(consumer.getUuid()))
            .singleElement()
            .returns("guest1", GuestIdDTOArrayElement::getGuestId);
    }

    @Test
    public void shouldAllowUpdatingGuestIdsFromHostConsumerOnUpdate() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        List<GuestIdDTO> guests = List.of(new GuestIdDTO().guestId("guest1"),
            new GuestIdDTO().guestId("guest2"));
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));

        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumerClient.consumers().updateConsumer(consumer.getUuid(), new ConsumerDTO().guestIds(guests));
        assertThat(consumerClient.guestIds().getGuestIds(consumer.getUuid()))
            .hasSize(2);

        consumerClient.consumers().updateConsumer(consumer.getUuid(),
            new ConsumerDTO().guestIds(guests.subList(1, 2)));
        assertThat(consumerClient.guestIds().getGuestIds(consumer.getUuid()))
            .singleElement()
            .returns("guest2", GuestIdDTOArrayElement::getGuestId);
    }

    @Test
    public void shouldNotModifyGuestIdListIfGuestIdsisNullOnUpdate() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        List<GuestIdDTO> guests = List.of(new GuestIdDTO().guestId("guest1"));
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));

        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumerClient.consumers().updateConsumer(consumer.getUuid(), new ConsumerDTO().guestIds(guests));
        assertThat(consumerClient.guestIds().getGuestIds(consumer.getUuid()))
            .singleElement();

        consumerClient.consumers().updateConsumer(consumer.getUuid(), new ConsumerDTO());
        assertThat(consumerClient.guestIds().getGuestIds(consumer.getUuid()))
            .singleElement()
            .returns("guest1", GuestIdDTOArrayElement::getGuestId);
    }

    @Test
    public void shouldClearGuestIdsWhenEmptyListIsProvidedOnUpdate() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        List<GuestIdDTO> guests = List.of(new GuestIdDTO().guestId("guest1"));
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ConsumerDTO consumer = userClient.consumers().createConsumer(Consumers.random(owner));

        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumerClient.consumers().updateConsumer(consumer.getUuid(), new ConsumerDTO().guestIds(guests));
        assertThat(consumerClient.guestIds().getGuestIds(consumer.getUuid()))
            .singleElement();

        consumerClient.consumers().updateConsumer(consumer.getUuid(), new ConsumerDTO().guestIds(List.of()));
        assertThat(consumerClient.guestIds().getGuestIds(consumer.getUuid()))
            .isEmpty();
    }

    @Test
    public void shouldAllowAHostToListGuests() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        String uuid1 = StringUtil.random("uuid");
        String uuid2 = StringUtil.random("uuid");
        List<GuestIdDTO> guests = List.of(new GuestIdDTO().guestId(uuid1), new GuestIdDTO().guestId(uuid2));

        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ConsumerDTO hostConsumer = userClient.consumers().createConsumer(Consumers.random(owner));
        userClient.consumers().createConsumer(Consumers.random(owner).facts(Map.of("virt.uuid", uuid1)));
        userClient.consumers().createConsumer(Consumers.random(owner).facts(Map.of("virt.uuid", uuid2)));

        ApiClient hostConsumerClient = ApiClients.ssl(hostConsumer);
        hostConsumerClient.consumers().updateConsumer(hostConsumer.getUuid(), new ConsumerDTO()
            .guestIds(guests));

        assertThat(hostConsumerClient.consumers().getGuests(hostConsumer.getUuid()))
            .hasSize(2);
    }

    @Test
    public void shouldAllowAHostToListGuestsWhenGuestIdsReportedWithReverseEndianness() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        String uuid1 = "78d7e200-b7d6-4cfe-b7a9-5700e8094df3";
        String uuid2 = StringUtil.random("uuid");
        List<GuestIdDTO> guests = List.of(new GuestIdDTO().guestId(uuid1), new GuestIdDTO()
            .guestId(uuid2));

        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ConsumerDTO hostConsumer = userClient.consumers().createConsumer(Consumers.random(owner));
        // virt-uuid has reversed-endianness in the first 3 sections
        ConsumerDTO guestConsumer1 = userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("virt.uuid", "00e2d778-d6b7-fe4c-b7a9-5700e8094df3")));
        ConsumerDTO guestConsumer2 = userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("virt.uuid", uuid2)));

        ApiClient hostConsumerClient = ApiClients.ssl(hostConsumer);
        hostConsumerClient.consumers().updateConsumer(hostConsumer.getUuid(), new ConsumerDTO()
            .guestIds(guests));

        assertThat(hostConsumerClient.consumers().getGuests(hostConsumer.getUuid()))
            .hasSize(2);
        assertThat(userClient.consumers().getHost(guestConsumer1.getUuid()))
            .returns(hostConsumer.getUuid(), ConsumerDTO::getUuid);
        assertThat(userClient.consumers().getHost(guestConsumer2.getUuid()))
            .returns(hostConsumer.getUuid(), ConsumerDTO::getUuid);
    }

    @Test
    public void shouldNotAllowAHostToListGuestThatAnotherHostHasClaimed() throws InterruptedException {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        String uuid1 = StringUtil.random("uuid");
        String uuid2 = StringUtil.random("uuid");
        List<GuestIdDTO> guests1 = List.of(new GuestIdDTO().guestId(uuid1), new GuestIdDTO()
            .guestId(uuid2));
        List<GuestIdDTO> guests2 = List.of(new GuestIdDTO().guestId(uuid2));

        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ConsumerDTO hostConsumer1 = userClient.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO hostConsumer2 = userClient.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO guestConsumer1 = userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("virt.uuid", uuid1)));
        ConsumerDTO guestConsumer2 = userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("virt.uuid", uuid2)));

        ApiClient hostConsumerClient = ApiClients.ssl(hostConsumer1);
        hostConsumerClient.consumers().updateConsumer(hostConsumer1.getUuid(), new ConsumerDTO()
            .guestIds(guests1));

        // MySQL before 5.6.4 doesn't store fractional seconds on timestamps
        // and getHost() method in ConsumerCurator (which is what tells us which
        // host a guest is associated with) sorts results by updated time.
        sleep(1000);

        ApiClient hostConsumerClient2 = ApiClients.ssl(hostConsumer2);
        hostConsumerClient2.consumers().updateConsumer(hostConsumer2.getUuid(), new ConsumerDTO()
            .guestIds(guests2));


        assertThat(hostConsumerClient.consumers().getGuests(hostConsumer1.getUuid()))
            .singleElement()
            .returns(guestConsumer1.getUuid(), ConsumerDTOArrayElement::getUuid);
    }

    @Test
    public void shouldGuestListMostCurrentHost() throws InterruptedException {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        String uuid1 = StringUtil.random("uuid");
        String uuid2 = StringUtil.random("uuid");
        List<GuestIdDTO> guests1 = List.of(new GuestIdDTO().guestId(uuid1), new GuestIdDTO()
            .guestId(uuid2));
        List<GuestIdDTO> guests2 = List.of(new GuestIdDTO().guestId(uuid2));

        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ConsumerDTO hostConsumer1 = userClient.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO hostConsumer2 = userClient.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO guestConsumer1 = userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("virt.uuid", uuid1)));
        ConsumerDTO guestConsumer2 = userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("virt.uuid", uuid2)));

        ApiClient hostConsumerClient = ApiClients.ssl(hostConsumer1);
        hostConsumerClient.consumers().updateConsumer(hostConsumer1.getUuid(), new ConsumerDTO()
            .guestIds(guests1));

        // MySQL before 5.6.4 doesn't store fractional seconds on timestamps
        // and getHost() method in ConsumerCurator (which is what tells us which
        // host a guest is associated with) sorts results by updated time.
        sleep(1000);

        ApiClient hostConsumerClient2 = ApiClients.ssl(hostConsumer2);
        hostConsumerClient2.consumers().updateConsumer(hostConsumer2.getUuid(), new ConsumerDTO()
            .guestIds(guests2));

        assertThat(userClient.consumers().getHost(guestConsumer1.getUuid()))
            .returns(hostConsumer1.getUuid(), ConsumerDTO::getUuid);
        assertThat(userClient.consumers().getHost(guestConsumer2.getUuid()))
            .returns(hostConsumer2.getUuid(), ConsumerDTO::getUuid);
    }

    @Test
    public void shouldIgnoreDuplicateGuestIds() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        GuestIdDTO guestId1 = new GuestIdDTO().guestId(StringUtil.random("uuid"));
        GuestIdDTO guestId2 = new GuestIdDTO().guestId(StringUtil.random("uuid"));
        List<GuestIdDTO> guests = List.of(guestId1, guestId1, guestId2, guestId2);

        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ConsumerDTO hostConsumer = userClient.consumers().createConsumer(Consumers.random(owner));
        ConsumerDTO guestConsumer1 = userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("virt.uuid", guestId1.getGuestId())));
        ConsumerDTO guestConsumer2 = userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("virt.uuid", guestId2.getGuestId())));

        ApiClient hostConsumerClient = ApiClients.ssl(hostConsumer);
        hostConsumerClient.consumers().updateConsumer(hostConsumer.getUuid(), new ConsumerDTO()
            .guestIds(guests));

        assertThat(userClient.consumers().getGuests(hostConsumer.getUuid()))
            .hasSize(2)
            .map(ConsumerDTOArrayElement::getUuid)
            .containsExactlyInAnyOrder(guestConsumer1.getUuid(), guestConsumer2.getUuid());
    }

    @Test
    public void shouldNotGuestImposeSlaOnHostAutoAttach() {
        OwnerDTO owner = ownerApi.createOwner(Owners.random());
        String uuid1 = StringUtil.random("uuid");
        String uuid2 = StringUtil.random("uuid");
        String uuid3 = StringUtil.random("uuid");
        List<GuestIdDTO> guests = List.of(new GuestIdDTO().guestId(uuid1), new GuestIdDTO().guestId(uuid2),
            new GuestIdDTO().guestId(uuid3));

        ProductDTO providedProduct = ownerProductApi.createProductByOwner(owner.getKey(),
            Products.randomEng());
        ProductDTO vipProduct = Products.random()
            .addAttributesItem(ProductAttributes.SupportLevel.withValue("VIP"))
            .addAttributesItem(ProductAttributes.VirtualLimit.withValue("5"))
            .addAttributesItem(ProductAttributes.HostLimited.withValue("true"))
            .providedProducts(Set.of(providedProduct));
        vipProduct = ownerProductApi.createProductByOwner(owner.getKey(), vipProduct);
        ProductDTO standardProduct = Products.random()
            .addAttributesItem(ProductAttributes.SupportLevel.withValue("Standard"))
            .addAttributesItem(ProductAttributes.VirtualLimit.withValue("5"))
            .addAttributesItem(ProductAttributes.HostLimited.withValue("true"))
            .providedProducts(Set.of(providedProduct));
        standardProduct = ownerProductApi.createProductByOwner(owner.getKey(), standardProduct);

        ownerApi.createPool(owner.getKey(), Pools.random(vipProduct)
            .subscriptionSubKey(StringUtil.random("source_sub"))
            .subscriptionId(StringUtil.random("sub_id"))
            .upstreamPoolId(StringUtil.random("upstream")));
        ownerApi.createPool(owner.getKey(), Pools.random(standardProduct)
            .subscriptionSubKey(StringUtil.random("source_sub"))
            .subscriptionId(StringUtil.random("sub_id"))
            .upstreamPoolId(StringUtil.random("upstream")));

        Set<ConsumerInstalledProductDTO> installed = Set.of(new ConsumerInstalledProductDTO()
            .productId(providedProduct.getId())
            .productName(providedProduct.getName()));

        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ConsumerDTO hostConsumer = userClient.consumers().createConsumer(Consumers.random(owner));
        assertThat(hostConsumer)
            .returns("", ConsumerDTO::getServiceLevel);


        ConsumerDTO guestConsumer1 = userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("virt.uuid", uuid1, "virt.is_guest", "true")));
        ConsumerDTO guestConsumer2 = userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("virt.uuid", uuid2, "virt.is_guest", "true")));
        ConsumerDTO guestConsumer3 = userClient.consumers().createConsumer(Consumers.random(owner)
            .facts(Map.of("virt.uuid", uuid3, "virt.is_guest", "true")));

        ApiClient hostConsumerClient = ApiClients.ssl(hostConsumer);
        hostConsumerClient.consumers().updateConsumer(hostConsumer.getUuid(), new ConsumerDTO()
            .guestIds(guests));
        ApiClient guestClient1 = ApiClients.ssl(guestConsumer1);
        guestClient1.consumers().updateConsumer(guestConsumer1.getUuid(), new ConsumerDTO()
            .serviceLevel("VIP").installedProducts(installed));
        ApiClient guestClient2 = ApiClients.ssl(guestConsumer2);
        guestClient2.consumers().updateConsumer(guestConsumer2.getUuid(), new ConsumerDTO()
            .serviceLevel("VIP").installedProducts(installed));
        ApiClient guestClient3 = ApiClients.ssl(guestConsumer3);
        guestClient3.consumers().updateConsumer(guestConsumer3.getUuid(), new ConsumerDTO()
            .serviceLevel("Standard").installedProducts(installed));

        // first guest causes host to attach to pool
        // We no longer filter based on consumer/pool SLA match, but we highly prioritize,
        // so the VIP SLA pool is attached.
        guestClient1.consumers().autoBind(guestConsumer1.getUuid());
        assertThat(guestClient1.consumers().listEntitlements(guestConsumer1.getUuid()))
            .singleElement()
            .extracting(EntitlementDTO::getPool)
            .returns(hostConsumer.getUuid(), x -> getAttributeValue(x, "requires_host"));

        assertThat(hostConsumerClient.consumers().listEntitlements(hostConsumer.getUuid()))
            .singleElement()
            .extracting(EntitlementDTO::getPool)
            .returns("VIP", x -> getProductAttributeValue(x, "support_level"));
        assertThat(hostConsumerClient.consumers().getConsumer(hostConsumer.getUuid()))
            .returns("", ConsumerDTO::getServiceLevel);

        // second guest grabs the VIP pool because it is already available
        guestClient2.consumers().autoBind(guestConsumer2.getUuid());
        assertThat(guestClient2.consumers().listEntitlements(guestConsumer2.getUuid()))
            .singleElement()
            .extracting(EntitlementDTO::getPool)
            .returns(hostConsumer.getUuid(), x -> getAttributeValue(x, "requires_host"));

        assertThat(hostConsumerClient.consumers().listEntitlements(hostConsumer.getUuid()))
            .singleElement()
            .extracting(EntitlementDTO::getPool)
            .returns("VIP", x -> getProductAttributeValue(x, "support_level"));
        assertThat(hostConsumerClient.consumers().getConsumer(hostConsumer.getUuid()))
            .returns("", ConsumerDTO::getServiceLevel);

        // third guest, even though has a Standard SLA, will not attach to the Standard pool
        // instead will attach to the available VIP pool, since we no longer match on SLA.
        guestClient3.consumers().autoBind(guestConsumer3.getUuid());
        assertThat(guestClient3.consumers().listEntitlements(guestConsumer3.getUuid()))
            .singleElement()
            .extracting(EntitlementDTO::getPool)
            .returns(hostConsumer.getUuid(), x -> getAttributeValue(x, "requires_host"))
            .returns("VIP", x -> getProductAttributeValue(x, "support_level"));

        assertThat(hostConsumerClient.consumers().getConsumer(hostConsumer.getUuid()))
            .returns("", ConsumerDTO::getServiceLevel);
        assertThat(hostConsumerClient.consumers().listEntitlements(hostConsumer.getUuid()))
            .singleElement()
            .extracting(EntitlementDTO::getPool)
            .returns("VIP", x -> getProductAttributeValue(x, "support_level"));

    }

    private String getAttributeValue(PoolDTO pool, String name) {
        return pool.getAttributes().stream()
            .filter(y -> y.getName().equals(name))
            .findFirst()
            .map(AttributeDTO::getValue)
            .orElse(null);
    }

    private String getProductAttributeValue(PoolDTO pool, String name) {
        return pool.getProductAttributes().stream()
            .filter(y -> y.getName().equals(name))
            .findFirst()
            .map(AttributeDTO::getValue)
            .orElse(null);
    }
}
