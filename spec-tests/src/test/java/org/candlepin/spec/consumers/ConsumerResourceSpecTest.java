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

import static org.assertj.core.api.Assertions.assertThat;

import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTOArrayElement;
import org.candlepin.dto.api.client.v1.GuestIdDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.ReleaseVerDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.request.Request;
import org.candlepin.spec.bootstrap.client.request.Response;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

@SpecTest
public class ConsumerResourceSpecTest {
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
}
