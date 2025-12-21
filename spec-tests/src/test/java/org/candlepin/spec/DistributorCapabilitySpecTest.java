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
package org.candlepin.spec;

import static org.assertj.core.api.Assertions.assertThat;

import org.candlepin.dto.api.client.v1.CapabilityDTO;
import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.DistributorVersionCapabilityDTO;
import org.candlepin.dto.api.client.v1.DistributorVersionDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.ProductDTO;
import org.candlepin.resource.client.v1.OwnerProductApi;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.client.api.ConsumerClient;
import org.candlepin.spec.bootstrap.client.api.OwnerClient;
import org.candlepin.spec.bootstrap.data.builder.ConsumerTypes;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Pools;
import org.candlepin.spec.bootstrap.data.builder.ProductAttributes;
import org.candlepin.spec.bootstrap.data.builder.Products;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;


@SpecTest
public class DistributorCapabilitySpecTest {

    static ApiClient client;
    static OwnerClient ownerClient;
    static ConsumerClient consumerClient;
    static OwnerProductApi ownerProductApi;

    @BeforeAll
    static void beforeAll() {
        client = ApiClients.admin();
        ownerClient = client.owners();
        consumerClient = client.consumers();
        ownerProductApi = client.ownerProducts();
    }

    @Test
    public void shouldAllowDistributorVersionCreation() {
        int count = client.distributorVersions().getVersions("test-creation", null).size();
        DistributorVersionDTO distVersion = new DistributorVersionDTO()
            .name(StringUtil.random("test-creation"))
            .displayName(StringUtil.random("displayName"))
            .addCapabilitiesItem(new DistributorVersionCapabilityDTO().name("midas touch"))
            .addCapabilitiesItem(new DistributorVersionCapabilityDTO().name("telepathy"))
            .addCapabilitiesItem(new DistributorVersionCapabilityDTO().name("lightning speed"));

        OffsetDateTime init = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS);

        DistributorVersionDTO output = client.distributorVersions().create(distVersion);

        OffsetDateTime post = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS);

        assertThat(output.getId())
            .isNotNull()
            .isNotBlank();

        assertThat(output.getCreated())
            .isNotNull()
            .isAfterOrEqualTo(init)
            .isBeforeOrEqualTo(post);

        assertThat(output.getUpdated())
            .isNotNull()
            .isAfterOrEqualTo(init)
            .isAfterOrEqualTo(output.getCreated())
            .isBeforeOrEqualTo(post);

        assertThat(client.distributorVersions().getVersions("test-creation", null))
            .hasSize(count + 1);
        assertThat(output.getCapabilities()).hasSize(3);
    }

    @Test
    public void shouldAllowDistributorVersionUpdate() throws Exception {
        int count = client.distributorVersions().getVersions("test-update", null).size();
        DistributorVersionDTO distVersion = new DistributorVersionDTO()
            .name(StringUtil.random("test-update"))
            .displayName(StringUtil.random("displayName"))
            .addCapabilitiesItem(new DistributorVersionCapabilityDTO().name("midas touch"))
            .addCapabilitiesItem(new DistributorVersionCapabilityDTO().name("telepathy"))
            .addCapabilitiesItem(new DistributorVersionCapabilityDTO().name("lightning speed"));
        distVersion = client.distributorVersions().create(distVersion);
        String distVersionId = distVersion.getId();

        Thread.sleep(1100);

        DistributorVersionDTO updateDistVersion = new DistributorVersionDTO()
            .name(distVersion.getName())
            .displayName(distVersion.getDisplayName())
            .addCapabilitiesItem(new DistributorVersionCapabilityDTO().name("midas touch"))
            .addCapabilitiesItem(new DistributorVersionCapabilityDTO().name("lightning speed"));

        OffsetDateTime init = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS);

        DistributorVersionDTO output = client.distributorVersions().update(distVersionId, updateDistVersion);

        OffsetDateTime post = OffsetDateTime.now()
            .truncatedTo(ChronoUnit.SECONDS);

        assertThat(client.distributorVersions().getVersions("test-update", null))
            .hasSize(count + 1);

        assertThat(output)
            .returns(distVersionId, DistributorVersionDTO::getId)
            .extracting(DistributorVersionDTO::getCapabilities)
            .returns(2, Set::size);

        assertThat(output.getCreated())
            .isNotNull()
            .isEqualTo(distVersion.getCreated())
            .isBeforeOrEqualTo(init);

        assertThat(output.getUpdated())
            .isNotNull()
            .isAfter(output.getCreated())
            .isAfterOrEqualTo(init)
            .isBeforeOrEqualTo(post);
    }

    @Test
    public void shouldAssignConsumerCapabilitiesBasedOnDistributorVersionWhenCreating() {
        OwnerDTO owner = ownerClient.createOwner(Owners.random());
        String name = StringUtil.random("name");
        DistributorVersionDTO distVersion = new DistributorVersionDTO()
            .name(name)
            .displayName(StringUtil.random("displayName"))
            .addCapabilitiesItem(new DistributorVersionCapabilityDTO().name("midas touch"))
            .addCapabilitiesItem(new DistributorVersionCapabilityDTO().name("telepathy"))
            .addCapabilitiesItem(new DistributorVersionCapabilityDTO().name("lightning speed"));
        client.distributorVersions().create(distVersion);
        ConsumerDTO consumer = Consumers.random(owner)
            .type(ConsumerTypes.Candlepin.value())
            .facts(Map.of("distributor_version", name));
        consumer = consumerClient.createConsumer(consumer);
        assertThat(consumer.getCapabilities()).hasSize(3);
    }

    @Test
    public void shouldAssignConsumerCapabilitiesBasedOnCapabilityListWhenCreating() {
        OwnerDTO owner = ownerClient.createOwner(Owners.random());
        String name = StringUtil.random("name");
        DistributorVersionDTO distVersion = new DistributorVersionDTO()
            .name(name)
            .displayName(StringUtil.random("displayName"))
            .addCapabilitiesItem(new DistributorVersionCapabilityDTO().name("midas touch"))
            .addCapabilitiesItem(new DistributorVersionCapabilityDTO().name("telepathy"))
            .addCapabilitiesItem(new DistributorVersionCapabilityDTO().name("lightning speed"));
        client.distributorVersions().create(distVersion);

        ConsumerDTO consumer = Consumers.random(owner)
            .type(ConsumerTypes.Candlepin.value())
            .facts(Map.of("distributor_version", name))
            .addCapabilitiesItem(new CapabilityDTO().name("one"))
            .addCapabilitiesItem(new CapabilityDTO().name("two"));
        consumer = consumerClient.createConsumer(consumer);
        assertThat(consumer.getCapabilities()).hasSize(2);
    }

    @Test
    public void shouldUpdateConsumerCapabilitiesBasedOnChangedDistributorVersionWhenUpdatingConsumer() {
        OwnerDTO owner = ownerClient.createOwner(Owners.random());
        String name = StringUtil.random("name");
        DistributorVersionDTO distVersion = new DistributorVersionDTO()
            .name(name)
            .displayName(StringUtil.random("displayName"))
            .addCapabilitiesItem(new DistributorVersionCapabilityDTO().name("midas touch"))
            .addCapabilitiesItem(new DistributorVersionCapabilityDTO().name("telepathy"))
            .addCapabilitiesItem(new DistributorVersionCapabilityDTO().name("lightning speed"));
        client.distributorVersions().create(distVersion);

        ConsumerDTO consumer = Consumers.random(owner)
            .type(ConsumerTypes.Candlepin.value())
            .facts(Map.of("distributor_version", name));
        consumer = consumerClient.createConsumer(consumer);
        assertThat(consumer.getCapabilities()).hasSize(3);

        name = StringUtil.random("name");
        distVersion = new DistributorVersionDTO()
            .name(name)
            .displayName(StringUtil.random("displayName"))
            .addCapabilitiesItem(new DistributorVersionCapabilityDTO().name("midas touch"))
            .addCapabilitiesItem(new DistributorVersionCapabilityDTO().name("telekenesis"))
            .addCapabilitiesItem(new DistributorVersionCapabilityDTO().name("ludicrist speed"))
            .addCapabilitiesItem(new DistributorVersionCapabilityDTO().name("omlet maker"));
        client.distributorVersions().create(distVersion);

        // leave as superadmin so lastCheckin does not get updated
        client.consumers().updateConsumer(consumer.getUuid(), new ConsumerDTO().
            facts(Map.of("distributor_version", name)));
        consumer = client.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer)
            .returns(null, ConsumerDTO::getLastCheckin);
        assertThat(consumer.getCapabilities()).hasSize(4);
    }

    @Test
    public void shouldUpdateConsumerCapabilitiesFromCapabilityList() {
        OwnerDTO owner = ownerClient.createOwner(Owners.random());
        String name = StringUtil.random("name");
        DistributorVersionDTO distVersion = new DistributorVersionDTO()
            .name(name)
            .displayName(StringUtil.random("displayName"))
            .addCapabilitiesItem(new DistributorVersionCapabilityDTO().name("midas touch"))
            .addCapabilitiesItem(new DistributorVersionCapabilityDTO().name("telekenesis"))
            .addCapabilitiesItem(new DistributorVersionCapabilityDTO().name("ludicrist speed"))
            .addCapabilitiesItem(new DistributorVersionCapabilityDTO().name("omlet maker"));
        client.distributorVersions().create(distVersion);
        Map facts = Map.of("distributor_version", name);

        ConsumerDTO consumer = Consumers.random(owner)
            .type(ConsumerTypes.Candlepin.value())
            .facts(facts);
        consumer = consumerClient.createConsumer(consumer);
        assertThat(consumer.getCapabilities()).hasSize(4);

        // leave as superadmin so lastCheckin does not get updated
        client.consumers().updateConsumer(consumer.getUuid(), new ConsumerDTO().facts(facts));
        consumer = client.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer)
            .returns(null, ConsumerDTO::getLastCheckin);
        assertThat(consumer.getCapabilities()).hasSize(4);

        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumerClient.consumers().updateConsumer(consumer.getUuid(), new ConsumerDTO());
        consumer = client.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer)
            .doesNotReturn(null, ConsumerDTO::getLastCheckin);

        ConsumerDTO updateconsumer = new ConsumerDTO()
            .addCapabilitiesItem(new CapabilityDTO().name("midas touch"))
            .addCapabilitiesItem(new CapabilityDTO().name("telekenesis"))
            .addCapabilitiesItem(new CapabilityDTO().name("ludicrist speed"))
            .addCapabilitiesItem(new CapabilityDTO().name("omlet maker"))
            .addCapabilitiesItem(new CapabilityDTO().name("oragami"))
            .addCapabilitiesItem(new CapabilityDTO().name("heat vision"));
        consumerClient.consumers().updateConsumer(consumer.getUuid(), updateconsumer);
        consumer = client.consumers().getConsumer(consumer.getUuid());
        assertThat(consumer.getCapabilities()).hasSize(6);
    }

    // The unit tests examine the variations, this is a simple end-to-end test.
    // shows blocking for capability deficiency as well as showing
    // distributor consumers not needing cert version validation
    @Test
    public void shouldStopBindBasedOnConsumerCapabilities() {
        OwnerDTO owner = ownerClient.createOwner(Owners.random());
        ApiClient userClient = ApiClients.basic(UserUtil.createUser(client, owner));
        ProductDTO product = Products.randomEng()
            .addAttributesItem(ProductAttributes.Cores.withValue("8"));
        ownerProductApi.createProduct(owner.getKey(), product);
        ownerClient.createPool(owner.getKey(), Pools.random(product));

        ConsumerDTO consumer = Consumers.random(owner)
            .type(ConsumerTypes.Candlepin.value());
        consumer = consumerClient.createConsumer(consumer);
        JsonNode ents = userClient.consumers().bindProduct(consumer.getUuid(), product.getId());
        assertThat(ents).isEmpty();

        String name = StringUtil.random("name");
        DistributorVersionDTO distVersion = new DistributorVersionDTO()
            .name(name)
            .displayName(StringUtil.random("displayName"))
            .addCapabilitiesItem(new DistributorVersionCapabilityDTO().name("cores"));
        client.distributorVersions().create(distVersion);

        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumerClient.consumers().updateConsumer(consumer.getUuid(), new ConsumerDTO()
            .facts(Map.of("distributor_version", name)));
        consumer = client.consumers().getConsumer(consumer.getUuid());
        ents = client.consumers().bindProduct(consumer.getUuid(), product.getId());
        assertThat(ents).hasSize(1);
    }

    @Test
    public void shouldFilterDistributorVersionList() {
        String widget = StringUtil.random("Widget");
        String model = StringUtil.random("Model");
        String size = StringUtil.random("Size");
        String name1 = widget + model;
        String name2 = widget + size;
        String midas = StringUtil.random("midas");
        String telepathy = StringUtil.random("telepathy");
        String omletMaker = StringUtil.random("omlet maker");
        DistributorVersionDTO distVersion1 = new DistributorVersionDTO()
            .name(name1)
            .displayName(StringUtil.random("displayName"))
            .addCapabilitiesItem(new DistributorVersionCapabilityDTO().name(midas))
            .addCapabilitiesItem(new DistributorVersionCapabilityDTO().name(telepathy))
            .addCapabilitiesItem(new DistributorVersionCapabilityDTO().name("lightning speed"));
        client.distributorVersions().create(distVersion1);
        DistributorVersionDTO distVersion2 = new DistributorVersionDTO()
            .name(name2)
            .displayName(StringUtil.random("displayName"))
            .addCapabilitiesItem(new DistributorVersionCapabilityDTO().name(midas))
            .addCapabilitiesItem(new DistributorVersionCapabilityDTO().name("telekenesis"))
            .addCapabilitiesItem(new DistributorVersionCapabilityDTO().name("ludicrist speed"))
            .addCapabilitiesItem(new DistributorVersionCapabilityDTO().name(omletMaker));
        client.distributorVersions().create(distVersion2);

        assertThat(client.distributorVersions().getVersions(model, null))
            .singleElement()
            .returns(name1, DistributorVersionDTO::getName);

        assertThat(client.distributorVersions().getVersions(widget, null))
            .hasSize(2);

        assertThat(client.distributorVersions().getVersions(null, telepathy))
            .singleElement()
            .returns(name1, DistributorVersionDTO::getName);

        assertThat(client.distributorVersions().getVersions(null, omletMaker))
            .singleElement()
            .returns(name2, DistributorVersionDTO::getName);

        assertThat(client.distributorVersions().getVersions(null, midas))
            .hasSize(2);
    }
}
