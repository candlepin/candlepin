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

import static org.assertj.core.api.Assertions.assertThat;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertBadRequest;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotFound;

import org.candlepin.dto.api.client.v1.ConsumerDTO;
import org.candlepin.dto.api.client.v1.ContentOverrideDTO;
import org.candlepin.dto.api.client.v1.EnvironmentDTO;
import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Consumers;
import org.candlepin.spec.bootstrap.data.builder.ContentOverrides;
import org.candlepin.spec.bootstrap.data.builder.Environments;
import org.candlepin.spec.bootstrap.data.builder.Owners;

import org.assertj.core.api.ListAssert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

@SpecTest
public class ConsumerResourceContentOverrideSpecTest {

    private static final String CONTENT_LABEL = "content.label";
    private static ApiClient admin;
    private OwnerDTO owner;

    @BeforeAll
    public static void beforeAll() {
        admin = ApiClients.admin();
    }

    @BeforeEach
    void setUp() {
        this.owner = admin.owners().createOwner(Owners.random());
    }

    private ListAssert<ContentOverrideDTO> assertConsumerOverrides(ApiClient client, ConsumerDTO consumer) {
        List<ContentOverrideDTO> returnedOverrides = client.consumers()
            .listConsumerContentOverrides(consumer.getUuid());
        return assertThat(returnedOverrides);
    }

    public ContentOverrideDTO createOverride(String name, String value) {
        return new ContentOverrideDTO()
            .name(name)
            .value(value)
            .contentLabel(CONTENT_LABEL);
    }

    @Test
    public void shouldAllowContentValueOverridesPerConsumer() {
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(this.owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        List<ContentOverrideDTO> overrides = List.of(
            ContentOverrides.random(),
            ContentOverrides.random()
        );

        List<ContentOverrideDTO> createdOverrides = consumerClient.consumers()
            .addConsumerContentOverrides(consumer.getUuid(), overrides);

        assertConsumerOverrides(consumerClient, consumer)
            .hasSize(2)
            .containsExactlyInAnyOrderElementsOf(createdOverrides);
    }

    @Test
    public void shouldAllowContentValueOverridesUpdates() {
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(this.owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        ContentOverrideDTO override1 = ContentOverrides.random();
        ContentOverrideDTO override2 = ContentOverrides.random();
        List<ContentOverrideDTO> overrides = List.of(override1, override2);
        consumerClient.consumers().addConsumerContentOverrides(consumer.getUuid(), overrides);

        String expectedValue = "consumer1_update";
        consumerClient.consumers()
            .addConsumerContentOverrides(consumer.getUuid(), List.of(override1.value(expectedValue)));

        assertConsumerOverrides(consumerClient, consumer)
            .hasSize(2)
            .map(ContentOverrideDTO::getValue)
            .containsExactlyInAnyOrder(expectedValue, override2.getValue());
    }

    @Test
    public void shouldKeepContentOverridesSeparateAcrossConsumers() {
        ConsumerDTO consumer1 = admin.consumers().createConsumer(Consumers.random(this.owner));
        ConsumerDTO consumer2 = admin.consumers().createConsumer(Consumers.random(this.owner));
        ApiClient consumerClient1 = ApiClients.ssl(consumer1);
        ApiClient consumerClient2 = ApiClients.ssl(consumer2);
        List<ContentOverrideDTO> overrides1 = List.of(
            ContentOverrides.withValue(consumer1),
            ContentOverrides.withValue(consumer1),
            ContentOverrides.withValue(consumer1)
        );
        List<ContentOverrideDTO> overrides2 = List.of(
            ContentOverrides.withValue(consumer2),
            ContentOverrides.withValue(consumer2)
        );

        consumerClient1.consumers().addConsumerContentOverrides(consumer1.getUuid(), overrides1);
        consumerClient2.consumers().addConsumerContentOverrides(consumer2.getUuid(), overrides2);

        assertConsumerOverrides(consumerClient1, consumer1)
            .hasSize(3)
            .map(ContentOverrideDTO::getValue)
            .containsOnly(consumer1.getUuid());

        assertConsumerOverrides(consumerClient2, consumer2)
            .hasSize(2)
            .map(ContentOverrideDTO::getValue)
            .containsOnly(consumer2.getUuid());
    }

    @Test
    public void shouldAllowContentValueOverrideDeletion() {
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(this.owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        ContentOverrideDTO override = ContentOverrides.withValue(consumer);
        List<ContentOverrideDTO> overrides = List.of(
            override,
            ContentOverrides.withValue(consumer),
            ContentOverrides.withValue(consumer),
            ContentOverrides.withValue(consumer),
            ContentOverrides.withValue(consumer)
        );
        consumerClient.consumers().addConsumerContentOverrides(consumer.getUuid(), overrides);

        consumerClient.consumers().deleteConsumerContentOverrides(consumer.getUuid(), List.of(override));

        assertConsumerOverrides(consumerClient, consumer)
            .hasSize(4)
            .map(ContentOverrideDTO::getName)
            .doesNotContain(override.getName());
    }

    @Test
    public void shouldKeepContentOverrideDeletesSeparateAcrossConsumers() {
        ConsumerDTO consumer1 = admin.consumers().createConsumer(Consumers.random(this.owner));
        ConsumerDTO consumer2 = admin.consumers().createConsumer(Consumers.random(this.owner));
        ApiClient consumerClient1 = ApiClients.ssl(consumer1);
        ApiClient consumerClient2 = ApiClients.ssl(consumer2);
        ContentOverrideDTO override = ContentOverrides.random();
        List<ContentOverrideDTO> overrides = List.of(
            override,
            ContentOverrides.random(),
            ContentOverrides.random(),
            ContentOverrides.random(),
            ContentOverrides.random()
        );
        consumerClient1.consumers().addConsumerContentOverrides(consumer1.getUuid(), overrides);
        consumerClient2.consumers().addConsumerContentOverrides(consumer2.getUuid(), overrides);

        consumerClient1.consumers().deleteConsumerContentOverrides(consumer1.getUuid(), List.of(override));

        assertConsumerOverrides(consumerClient1, consumer1)
            .hasSize(4)
            .map(ContentOverrideDTO::getName)
            .doesNotContain(override.getName());
        assertConsumerOverrides(consumerClient2, consumer2)
            .hasSize(5)
            .map(ContentOverrideDTO::getName)
            .contains(override.getName());
    }

    @Test
    public void shouldRejectChangesForBlocklistedAttributes() {
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(this.owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        List<ContentOverrideDTO> overrides = List.of(ContentOverrides.random().name("label"));

        assertBadRequest(() -> consumerClient.consumers()
            .addConsumerContentOverrides(consumer.getUuid(), overrides));
    }

    @Test
    public void shouldRejectChangesForBlocklistedAttributesRegardlessOfCase() {
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(this.owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        List<ContentOverrideDTO> overrides = List.of(ContentOverrides.random().name("LABEL"));

        assertBadRequest(() -> consumerClient.consumers()
            .addConsumerContentOverrides(consumer.getUuid(), overrides));
    }

    @Test
    public void shouldRejectAllChangesIfAnyBlocklistedAttributesExist() {
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(this.owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        List<ContentOverrideDTO> overrides = List.of(
            ContentOverrides.random(),
            ContentOverrides.random().name("LABEL")
        );

        assertBadRequest(() -> consumerClient.consumers()
            .addConsumerContentOverrides(consumer.getUuid(), overrides));
        assertConsumerOverrides(consumerClient, consumer)
            .isEmpty();
    }

    @Test
    public void shouldNotCreateNewOverrideForPropertyWithSameNameButDifferentCase() {
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(this.owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        consumerClient.consumers().addConsumerContentOverrides(consumer.getUuid(),
            List.of(createOverride("test-field", "some_value")));
        assertConsumerOverrides(consumerClient, consumer)
            .hasSize(1);

        consumerClient.consumers().addConsumerContentOverrides(consumer.getUuid(),
            List.of(createOverride("TEST-FIELD", "some_other_value")));

        assertConsumerOverrides(consumerClient, consumer)
            .singleElement()
            .returns("test-field", ContentOverrideDTO::getName);
    }

    @Test
    public void shouldUseLastPropertyWhenOverridesWithDuplicateNamesAreSpecified() {
        ConsumerDTO consumer = admin.consumers().createConsumer(Consumers.random(this.owner));
        ApiClient consumerClient = ApiClients.ssl(consumer);
        ContentOverrideDTO override1 = createOverride("my-field", "some_value");
        ContentOverrideDTO override2 = createOverride("my-field", "some_other_value");
        consumerClient.consumers().addConsumerContentOverrides(consumer.getUuid(), List.of(override1));
        consumerClient.consumers().addConsumerContentOverrides(consumer.getUuid(), List.of(override2));

        assertConsumerOverrides(consumerClient, consumer)
            .singleElement()
            .returns("some_other_value", ContentOverrideDTO::getValue);
    }

    @Test
    public void shouldResultInNotFoundIfDifferentConsumerAttemptsToGetOverrides() {
        ConsumerDTO consumer1 = admin.consumers().createConsumer(Consumers.random(this.owner));
        ConsumerDTO consumer2 = admin.consumers().createConsumer(Consumers.random(this.owner));
        ApiClient consumerClient1 = ApiClients.ssl(consumer1);
        ApiClient consumerClient2 = ApiClients.ssl(consumer2);
        ContentOverrideDTO override = ContentOverrides.random();
        consumerClient1.consumers().addConsumerContentOverrides(consumer1.getUuid(), List.of(override));

        assertConsumerOverrides(consumerClient1, consumer1)
            .hasSize(1);

        assertNotFound(() -> consumerClient2.consumers()
            .listConsumerContentOverrides(consumer1.getUuid()));
    }

    @Test
    public void shouldResultInNotFoundIfDifferentConsumerAttemptsToAddOverrides() {
        ConsumerDTO consumer1 = admin.consumers().createConsumer(Consumers.random(this.owner));
        ConsumerDTO consumer2 = admin.consumers().createConsumer(Consumers.random(this.owner));
        ApiClient consumerClient1 = ApiClients.ssl(consumer1);
        ApiClient consumerClient2 = ApiClients.ssl(consumer2);
        ContentOverrideDTO override = ContentOverrides.random();

        assertConsumerOverrides(consumerClient1, consumer1)
            .isEmpty();

        assertNotFound(() -> consumerClient2.consumers()
            .addConsumerContentOverrides(consumer1.getUuid(), List.of(override)));
    }

    @Test
    public void shouldResultInNotFoundIfDifferentConsumerAttemptsToDeleteOverrides() {
        ConsumerDTO consumer1 = admin.consumers().createConsumer(Consumers.random(this.owner));
        ConsumerDTO consumer2 = admin.consumers().createConsumer(Consumers.random(this.owner));
        ApiClient consumerClient1 = ApiClients.ssl(consumer1);
        ApiClient consumerClient2 = ApiClients.ssl(consumer2);
        ContentOverrideDTO override = ContentOverrides.random();

        consumerClient1.consumers().addConsumerContentOverrides(consumer1.getUuid(), List.of(override));
        assertConsumerOverrides(consumerClient1, consumer1)
            .hasSize(1);

        assertNotFound(() -> consumerClient2.consumers()
            .deleteConsumerContentOverrides(consumer1.getUuid(), List.of(override)));
    }

    @Test
    public void shouldLayerContentOverridesForConsumer() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        EnvironmentDTO environment1 = adminClient.owners().createEnv(owner.getKey(), Environments.random());
        EnvironmentDTO environment2 = adminClient.owners().createEnv(owner.getKey(), Environments.random());
        ConsumerDTO consumer = adminClient.consumers().createConsumer(Consumers.random(owner)
            .addEnvironmentsItem(environment1)
            .addEnvironmentsItem(environment2));

        ContentOverrideDTO env1or1 = new ContentOverrideDTO()
            .contentLabel("label1")
            .name("attrib-a")
            .value("env1or1");
        ContentOverrideDTO env1or2 = new ContentOverrideDTO()
            .contentLabel("label1")
            .name("attrib-b")
            .value("env1or2");
        ContentOverrideDTO env1or3 = new ContentOverrideDTO()
            .contentLabel("label2")
            .name("attrib-a")
            .value("env1or3");

        ContentOverrideDTO env2or1 = new ContentOverrideDTO()
            .contentLabel("label3")
            .name("attrib-a")
            .value("env2or1");
        ContentOverrideDTO env2or2 = new ContentOverrideDTO()
            .contentLabel("label3")
            .name("attrib-b")
            .value("env2or2");
        ContentOverrideDTO env2or3 = new ContentOverrideDTO()
            .contentLabel("label2")
            .name("attrib-a")
            .value("env2or3");

        ContentOverrideDTO consumerOr1 = new ContentOverrideDTO()
            .contentLabel("label1")
            .name("attrib-b")
            .value("consumerOr1");
        ContentOverrideDTO consumerOr2 = new ContentOverrideDTO()
            .contentLabel("label2")
            .name("attrib-b")
            .value("consumerOr2");
        ContentOverrideDTO consumerOr3 = new ContentOverrideDTO()
            .contentLabel("label3")
            .name("attrib-b")
            .value("consumerOr3");

        // label1
        //     a - env1or1
        //     b - consumerOr1

        // label2
        //     a - env1or3
        //     b - consumerOr2

        // label3
        //     a - env2or1
        //     b - consumerOr3

        List<ContentOverrideDTO> overrides1 = adminClient.environments()
            .putEnvironmentContentOverrides(environment1.getId(), List.of(env1or1, env1or2, env1or3));

        List<ContentOverrideDTO> overrides2 = adminClient.environments()
            .putEnvironmentContentOverrides(environment2.getId(), List.of(env2or1, env2or2, env2or3));

        List<ContentOverrideDTO> overrides3 = adminClient.consumers()
            .addConsumerContentOverrides(consumer.getUuid(), List.of(consumerOr1, consumerOr2, consumerOr3));

        assertThat(overrides1)
            .hasSize(3)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(env1or1, env1or2, env1or3));

        assertThat(overrides2)
            .hasSize(3)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(env2or1, env2or2, env2or3));

        assertThat(overrides3)
            .hasSize(3)
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(List.of(consumerOr1, consumerOr2, consumerOr3));

        // We have a few conflicts here, and conflict resolution following highest prio wins should
        // give us the following in no particular order:
        List<ContentOverrideDTO> expected = List.of(env1or1, consumerOr1, env1or3, consumerOr2, env2or1,
            consumerOr3);

        List<ContentOverrideDTO> output = adminClient.consumers()
            .listConsumerContentOverrides(consumer.getUuid());

        assertThat(output)
            .hasSize(expected.size())
            .usingRecursiveFieldByFieldElementComparatorIgnoringFields("created", "updated")
            .containsExactlyInAnyOrderElementsOf(expected);
    }

}
