/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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
package org.candlepin.service.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCloudData;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Date;
import java.util.List;


public class CloudCheckInEventTest {

    private static final String SYSTEM_UUID_KEY = "systemUuid";
    private static final String CHECK_IN_KEY = "checkIn";
    private static final String PROVIDER_KEY = "cloudProviderId";
    private static final String ACCOUNT_KEY = "cloudAccountId";
    private static final String CLOUD_OFFERINGS_KEY = "cloudOfferingIds";

    private ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testConstructorWithNullConsumerCloudData() {
        assertThrows(IllegalArgumentException.class, () -> {
            new CloudCheckInEvent(null, mapper);
        });
    }

    @Test
    public void testConstructorWithNullConsumer() {
        ConsumerCloudData cloudData = createCloudData()
            .setConsumer(null);

        assertThrows(IllegalArgumentException.class, () -> {
            new CloudCheckInEvent(cloudData, mapper);
        });
    }

    @Test
    public void testConstructorWithNullObjectMapper() {
        Consumer consumer = new Consumer()
            .setUuid(TestUtil.randomString())
            .setLastCheckin(new Date());

        ConsumerCloudData cloudData = createCloudData()
            .setConsumer(consumer);

        assertThrows(IllegalArgumentException.class, () -> {
            new CloudCheckInEvent(cloudData, null);
        });
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testConstructorWithInvalidConsumerUuid(String consumerUuid) {
        Consumer consumer = new Consumer()
            .setUuid(consumerUuid)
            .setLastCheckin(new Date());

        ConsumerCloudData cloudData = createCloudData()
            .setConsumer(consumer);

        assertThrows(IllegalStateException.class, () -> {
            new CloudCheckInEvent(cloudData, mapper);
        });
    }

    @Test
    public void testConstructorWithNullCheckIn() {
        Consumer consumer = new Consumer()
            .setUuid(TestUtil.randomString())
            .setLastCheckin(null);

        ConsumerCloudData cloudData = createCloudData()
            .setConsumer(consumer);

        assertThrows(IllegalStateException.class, () -> {
            new CloudCheckInEvent(cloudData, mapper);
        });
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testConstructorWithInvalidCloudProviderShortName(String shortName) {
        ConsumerCloudData cloudData = new ConsumerCloudData()
            .setCloudAccountId(TestUtil.randomString())
            .setCloudInstanceId(TestUtil.randomString())
            .setCloudOfferingIds(List.of(TestUtil.randomString(), TestUtil.randomString()))
            .setConsumer(createConsumer())
            .setCreated(new Date());

        assertThrows(IllegalStateException.class, () -> {
            new CloudCheckInEvent(cloudData, mapper);
        });
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testConstructorWithNullOrEmptyCloudAccountId(String accountId) {
        ConsumerCloudData cloudData = createCloudData()
            .setCloudAccountId(accountId);

        CloudCheckInEvent event = new CloudCheckInEvent(cloudData, mapper);

        // No exception is expected to be thrown
    }

    @Test
    public void testConstructor() throws Exception {
        ConsumerCloudData expected = createCloudData();

        CloudCheckInEvent actual = new CloudCheckInEvent(expected, mapper);

        assertThat(actual)
            .returns(expected.getConsumer().getUuid(), CloudCheckInEvent::getConsumerUuid)
            .returns(expected.getConsumer().getLastCheckin(), CloudCheckInEvent::getCheckIn)
            .returns(expected.getCloudProviderShortName(), CloudCheckInEvent::getCloudProviderId)
            .returns(expected.getCloudAccountId(), CloudCheckInEvent::getCloudAccountId)
            .returns(expected.getCloudOfferingIds(), CloudCheckInEvent::getCloudOfferingIds);
    }

    @Test
    public void testGetBody() throws Exception {
        ConsumerCloudData data = createCloudData();

        CloudCheckInEvent actual = new CloudCheckInEvent(data, mapper);

        String expected = getBody(actual);

        assertThat(actual)
            .returns(expected, CloudCheckInEvent::getBody);
    }

    @Test
    public void testGetSerializationType() throws Exception {
        ConsumerCloudData data = createCloudData();

        CloudCheckInEvent actual = new CloudCheckInEvent(data, mapper);

        assertThat(actual)
            .returns(SerializationType.JSON, CloudCheckInEvent::getSerializationType);
    }

    @Test
    public void testEqualsWithSameInstance() throws Exception {
        ConsumerCloudData data = createCloudData();
        CloudCheckInEvent event = new CloudCheckInEvent(data, mapper);

        assertTrue(event.equals(event));
    }

    @Test
    public void testEqualsWithDifferentClass() throws Exception {
        ConsumerCloudData data = createCloudData();
        CloudCheckInEvent event = new CloudCheckInEvent(data, mapper);

        assertFalse(event.equals(data));
    }

    @Test
    public void testEqualsWithDifferentConsumerUuid() throws Exception {
        Consumer consumer = new Consumer()
            .setUuid(TestUtil.randomString())
            .setLastCheckin(new Date());

        ConsumerCloudData cloudData = new ConsumerCloudData()
            .setCloudAccountId(TestUtil.randomString())
            .setCloudInstanceId(TestUtil.randomString())
            .setCloudOfferingIds(List.of(TestUtil.randomString(), TestUtil.randomString()))
            .setCloudProviderShortName(TestUtil.randomString())
            .setConsumer(consumer)
            .setCreated(new Date());

        CloudCheckInEvent event1 = new CloudCheckInEvent(cloudData, mapper);

        consumer.setUuid(TestUtil.randomString());
        cloudData.setConsumer(consumer);

        CloudCheckInEvent event2 = new CloudCheckInEvent(cloudData, mapper);

        assertFalse(event1.equals(event2));
    }

    @Test
    public void testEqualsWithDifferentCheckIn() throws Exception {
        Consumer consumer = new Consumer()
            .setUuid(TestUtil.randomString())
            .setLastCheckin(new Date());

        ConsumerCloudData cloudData = new ConsumerCloudData()
            .setCloudAccountId(TestUtil.randomString())
            .setCloudInstanceId(TestUtil.randomString())
            .setCloudOfferingIds(List.of(TestUtil.randomString(), TestUtil.randomString()))
            .setCloudProviderShortName(TestUtil.randomString())
            .setConsumer(consumer)
            .setCreated(new Date());

        CloudCheckInEvent event1 = new CloudCheckInEvent(cloudData, mapper);

        consumer.setLastCheckin(TestUtil.createDateOffset(0, 0, 1));
        cloudData.setConsumer(consumer);

        CloudCheckInEvent event2 = new CloudCheckInEvent(cloudData, mapper);

        assertFalse(event1.equals(event2));
    }

    @Test
    public void testEqualsWithDifferentCloudProviderId() throws Exception {
        ConsumerCloudData data = createCloudData();
        CloudCheckInEvent event1 = new CloudCheckInEvent(data, mapper);

        data.setCloudProviderShortName(TestUtil.randomString());
        CloudCheckInEvent event2 = new CloudCheckInEvent(data, mapper);

        assertFalse(event1.equals(event2));
    }

    @Test
    public void testEqualsWithDifferentCloudAccountId() throws Exception {
        ConsumerCloudData data = createCloudData();
        CloudCheckInEvent event1 = new CloudCheckInEvent(data, mapper);

        data.setCloudAccountId(TestUtil.randomString());
        CloudCheckInEvent event2 = new CloudCheckInEvent(data, mapper);

        assertFalse(event1.equals(event2));
    }

    @Test
    public void testEqualsWithDifferentCloudOfferingIds() throws Exception {
        ConsumerCloudData data = createCloudData();
        CloudCheckInEvent event1 = new CloudCheckInEvent(data, mapper);

        data.setCloudOfferingIds(List.of(TestUtil.randomString(), TestUtil.randomString()));
        CloudCheckInEvent event2 = new CloudCheckInEvent(data, mapper);

        assertFalse(event1.equals(event2));
    }

    @Test
    public void testHashcodeWithDifferentConsumerUuid() throws Exception {
        Consumer consumer = new Consumer()
            .setUuid(TestUtil.randomString())
            .setLastCheckin(new Date());

        ConsumerCloudData cloudData = new ConsumerCloudData()
            .setCloudAccountId(TestUtil.randomString())
            .setCloudInstanceId(TestUtil.randomString())
            .setCloudOfferingIds(List.of(TestUtil.randomString(), TestUtil.randomString()))
            .setCloudProviderShortName(TestUtil.randomString())
            .setConsumer(consumer)
            .setCreated(new Date());

        CloudCheckInEvent event1 = new CloudCheckInEvent(cloudData, mapper);

        consumer.setUuid(TestUtil.randomString());
        cloudData.setConsumer(consumer);

        CloudCheckInEvent event2 = new CloudCheckInEvent(cloudData, mapper);

        assertNotEquals(event1.hashCode(), event2.hashCode());
    }

    @Test
    public void testHashcodeWithDifferentCheckIn() throws Exception {
        Consumer consumer = new Consumer()
            .setUuid(TestUtil.randomString())
            .setLastCheckin(new Date());

        ConsumerCloudData cloudData = new ConsumerCloudData()
            .setCloudAccountId(TestUtil.randomString())
            .setCloudInstanceId(TestUtil.randomString())
            .setCloudOfferingIds(List.of(TestUtil.randomString(), TestUtil.randomString()))
            .setCloudProviderShortName(TestUtil.randomString())
            .setConsumer(consumer)
            .setCreated(new Date());

        CloudCheckInEvent event1 = new CloudCheckInEvent(cloudData, mapper);

        consumer.setLastCheckin(TestUtil.createDateOffset(0, 0, 1));
        cloudData.setConsumer(consumer);

        CloudCheckInEvent event2 = new CloudCheckInEvent(cloudData, mapper);

        assertNotEquals(event1.hashCode(), event2.hashCode());
    }

    @Test
    public void testHashcodeWithDifferentCloudProviderId() throws Exception {
        ConsumerCloudData data = createCloudData();
        CloudCheckInEvent event1 = new CloudCheckInEvent(data, mapper);

        data.setCloudProviderShortName(TestUtil.randomString());
        CloudCheckInEvent event2 = new CloudCheckInEvent(data, mapper);

        assertNotEquals(event1.hashCode(), event2.hashCode());
    }

    @Test
    public void testHashcodeWithDifferentCloudAccountId() throws Exception {
        ConsumerCloudData data = createCloudData();
        CloudCheckInEvent event1 = new CloudCheckInEvent(data, mapper);

        data.setCloudAccountId(TestUtil.randomString());
        CloudCheckInEvent event2 = new CloudCheckInEvent(data, mapper);

        assertNotEquals(event1.hashCode(), event2.hashCode());
    }

    @Test
    public void testHashcodeWithDifferentCloudOfferingIds() throws Exception {
        ConsumerCloudData data = createCloudData();
        CloudCheckInEvent event1 = new CloudCheckInEvent(data, mapper);

        data.setCloudOfferingIds(List.of(TestUtil.randomString(), TestUtil.randomString()));
        CloudCheckInEvent event2 = new CloudCheckInEvent(data, mapper);

        assertNotEquals(event1.hashCode(), event2.hashCode());
    }

    private Consumer createConsumer() {
        return new Consumer()
            .setUuid(TestUtil.randomString())
            .setLastCheckin(new Date());
    }

    private ConsumerCloudData createCloudData() {
        return new ConsumerCloudData()
            .setCloudAccountId(TestUtil.randomString())
            .setCloudInstanceId(TestUtil.randomString())
            .setCloudOfferingIds(List.of(TestUtil.randomString(), TestUtil.randomString()))
            .setCloudProviderShortName(TestUtil.randomString())
            .setConsumer(createConsumer())
            .setCreated(new Date());
    }

    private String getBody(CloudCheckInEvent event) {
        ObjectMapper mapper = new ObjectMapper();

        ArrayNode arrayNode = mapper.createArrayNode();
        for (String offer : event.getCloudOfferingIds()) {
            arrayNode.add(offer);
        }

        ObjectNode rootNode = mapper.createObjectNode()
            .put(SYSTEM_UUID_KEY, event.getConsumerUuid())
            .put(CHECK_IN_KEY, event.getCheckIn().toString())
            .put(PROVIDER_KEY, event.getCloudProviderId())
            .put(ACCOUNT_KEY, event.getCloudAccountId())
            .putPOJO(CLOUD_OFFERINGS_KEY, arrayNode);

        return mapper.writeValueAsString(rootNode);
    }
}
