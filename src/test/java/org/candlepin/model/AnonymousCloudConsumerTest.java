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
package org.candlepin.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.test.DatabaseTestFixture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.validation.ConstraintViolationException;



public class AnonymousCloudConsumerTest extends DatabaseTestFixture {

    @Test
    public void testUuidGeneration() throws Exception {
        String expectedCloudAccountId = "cloud-account-id";
        String expectedInstanceId = "instance-id";
        String expectedProductId = "product-id";
        String expectedCloudProviderShortName = "AWS";
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setCloudAccountId(expectedCloudAccountId)
            .setCloudInstanceId(expectedInstanceId)
            .setProductId(expectedProductId)
            .setCloudProviderShortName(expectedCloudProviderShortName);

        AnonymousCloudConsumer actual = anonymousCloudConsumerCurator.create(consumer);

        assertNotNull(actual.getId());
        assertNotNull(actual.getUuid());
        assertEquals(expectedCloudAccountId, actual.getCloudAccountId());
        assertEquals(expectedInstanceId, actual.getCloudInstanceId());
        assertEquals(expectedProductId, actual.getProductId());
        assertEquals(expectedCloudProviderShortName, actual.getCloudProviderShortName());
    }

    @Test
    public void testCloudAccountIdFieldRequired() throws Exception {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setProductId("product-id")
            .setCloudInstanceId("instance-id")
            .setCloudProviderShortName("AWS");

        assertThrows(ConstraintViolationException.class,
            () -> anonymousCloudConsumerCurator.create(consumer));
    }

    @Test
    public void testCloudInstanceIdFieldRequired() throws Exception {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setCloudAccountId("cloud-account-id")
            .setProductId("product-id")
            .setCloudProviderShortName("AWS");

        assertThrows(ConstraintViolationException.class,
            () -> anonymousCloudConsumerCurator.create(consumer));
    }

    @Test
    public void testProductIdFieldRequired() throws Exception {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setCloudAccountId("cloud-account-id")
            .setCloudInstanceId("instance-id")
            .setCloudProviderShortName("AWS");

        assertThrows(ConstraintViolationException.class,
            () -> anonymousCloudConsumerCurator.create(consumer));
    }

    @Test
    public void testCloudProviderShortNameFieldRequired() throws Exception {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setCloudAccountId("cloud-account-id")
            .setCloudInstanceId("instance-id")
            .setProductId("product-id");

        assertThrows(ConstraintViolationException.class,
            () -> anonymousCloudConsumerCurator.create(consumer));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    @ValueSource(strings = { "  " })
    public void testSetIdWithInvalidValue(String id) {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer();

        assertThrows(IllegalArgumentException.class, () -> consumer.setId(id));
    }

    @Test
    public void testSetIdWithGreaterThanMaxLength() {
        String id = generateString(AnonymousCloudConsumer.ID_MAX_LENGTH + 1);
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer();

        assertThrows(IllegalArgumentException.class, () -> consumer.setId(id));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    @ValueSource(strings = { "  " })
    public void testSetUuidWithInvalidValue(String uuid) {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer();

        assertThrows(IllegalArgumentException.class, () -> consumer.setUuid(uuid));
    }

    @Test
    public void testSetUuidWithGreaterThanMaxLength() {
        String uuid = generateString(AnonymousCloudConsumer.UUID_MAX_LENGTH + 1);
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer();

        assertThrows(IllegalArgumentException.class, () -> consumer.setUuid(uuid));
    }

    @Test
    public void testUpdateToUuid() {
        String expectedCloudAccountId = "cloud-account-id";
        String expectedProductId = "product-id";
        String expectedCloudProviderShortName = "GCP";
        String expectedInstanceId = "instance-id";
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setCloudAccountId(expectedCloudAccountId)
            .setCloudInstanceId(expectedInstanceId)
            .setProductId(expectedProductId)
            .setCloudProviderShortName(expectedCloudProviderShortName);

        consumer = anonymousCloudConsumerCurator.create(consumer);
        String expectedUuid = "updated-uuid";
        consumer.setUuid(expectedUuid);

        AnonymousCloudConsumer actual = anonymousCloudConsumerCurator.merge(consumer);

        assertNotNull(actual.getId());
        assertEquals(expectedUuid, actual.getUuid());
        assertEquals(expectedCloudAccountId, actual.getCloudAccountId());
        assertEquals(expectedInstanceId, actual.getCloudInstanceId());
        assertEquals(expectedProductId, actual.getProductId());
        assertEquals(expectedCloudProviderShortName, actual.getCloudProviderShortName());
    }

    @Test
    public void testSetCloudAccountIdWithNullValue() {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer();

        assertThrows(IllegalArgumentException.class, () -> consumer.setCloudAccountId(null));
    }

    @Test
    public void testSetCloudAccountIdWithGreaterThanMaxLength() {
        String cloudAccountId = generateString(AnonymousCloudConsumer.CLOUD_ACCOUNT_ID_MAX_LENGTH + 1);
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer();

        assertThrows(IllegalArgumentException.class, () -> consumer.setCloudAccountId(cloudAccountId));
    }

    @Test
    public void testUpdateToCloudAccountId() {
        String expectedInstanceId = "instance-id";
        String expectedProductId = "product-id";
        String expectedCloudProviderShortName = "GCP";
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setCloudAccountId("initial-cloud-account-id")
            .setCloudInstanceId("instance-id")
            .setProductId(expectedProductId)
            .setCloudProviderShortName(expectedCloudProviderShortName);

        consumer = anonymousCloudConsumerCurator.create(consumer);
        String expectedCloudAccountId = "cloud-account-id";
        consumer.setCloudAccountId(expectedCloudAccountId);

        AnonymousCloudConsumer actual = anonymousCloudConsumerCurator.merge(consumer);

        assertNotNull(actual.getId());
        assertEquals(consumer.getUuid(), actual.getUuid());
        assertEquals(expectedCloudAccountId, actual.getCloudAccountId());
        assertEquals(expectedInstanceId, actual.getCloudInstanceId());
        assertEquals(expectedProductId, actual.getProductId());
        assertEquals(expectedCloudProviderShortName, actual.getCloudProviderShortName());
    }

    @Test
    public void testSetCloudInstanceIdWithNull() {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer();

        assertThrows(IllegalArgumentException.class, () -> consumer.setCloudInstanceId(null));
    }

    @Test
    public void testSetCloudInstanceIdWithGreaterThanMaxLength() {
        String instanceId = generateString(AnonymousCloudConsumer.CLOUD_INSTANCE_ID_MAX_LENGTH + 1);
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer();

        assertThrows(IllegalArgumentException.class, () -> consumer.setCloudInstanceId(instanceId));
    }

    @Test
    public void testUpdateToCloudInstanceId() {
        String expectedCloudAccountId = "cloud-account-id";
        String expectedProductId = "product-id";
        String expectedCloudProviderShortName = "GCP";
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setCloudAccountId(expectedCloudAccountId)
            .setCloudInstanceId("init-instance-id")
            .setProductId(expectedProductId)
            .setCloudProviderShortName(expectedCloudProviderShortName);

        consumer = anonymousCloudConsumerCurator.create(consumer);
        String expectedInstanceId = "updated-instance-id";
        consumer.setCloudInstanceId(expectedInstanceId);

        AnonymousCloudConsumer actual = anonymousCloudConsumerCurator.merge(consumer);

        assertNotNull(actual.getId());
        assertEquals(consumer.getUuid(), actual.getUuid());
        assertEquals(expectedCloudAccountId, actual.getCloudAccountId());
        assertEquals(expectedInstanceId, actual.getCloudInstanceId());
        assertEquals(expectedProductId, actual.getProductId());
        assertEquals(expectedCloudProviderShortName, actual.getCloudProviderShortName());
    }

    @Test
    public void testSetProductIdWithNull() {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer();

        assertThrows(IllegalArgumentException.class, () -> consumer.setProductId(null));
    }

    @Test
    public void testSetProductIdWithGreaterThanMaxLength() {
        String productId = generateString(AnonymousCloudConsumer.PRODUCT_ID_MAX_LENGTH + 1);
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer();

        assertThrows(IllegalArgumentException.class, () -> consumer.setProductId(productId));
    }

    @Test
    public void testUpdateToProductId() {
        String expectedCloudAccountId = "cloud-account-id";
        String expectedInstanceId = "instance-id";
        String expectedCloudProviderShortName = "AWS";
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setCloudAccountId(expectedCloudAccountId)
            .setCloudInstanceId("instance-id")
            .setProductId("init-prod-id")
            .setCloudProviderShortName(expectedCloudProviderShortName);

        consumer = anonymousCloudConsumerCurator.create(consumer);
        String expectedProductId = "product-id";
        consumer.setProductId(expectedProductId);

        AnonymousCloudConsumer actual = anonymousCloudConsumerCurator.merge(consumer);

        assertNotNull(actual.getId());
        assertEquals(consumer.getUuid(), actual.getUuid());
        assertEquals(expectedCloudAccountId, actual.getCloudAccountId());
        assertEquals(expectedInstanceId, actual.getCloudInstanceId());
        assertEquals(expectedProductId, actual.getProductId());
        assertEquals(expectedCloudProviderShortName, actual.getCloudProviderShortName());
    }

    @Test
    public void testSetCloudProviderShortNameWithNull() {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer();

        assertThrows(IllegalArgumentException.class,
            () -> consumer.setCloudProviderShortName(null));
    }

    @Test
    public void testSetCloudProviderShortNameWithGreaterThanMaxLength() {
        String shortName = generateString(AnonymousCloudConsumer.CLOUD_PROVIDER_SHORT_NAME_MAX_LENGTH + 1);
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer();

        assertThrows(IllegalArgumentException.class, () -> consumer.setCloudProviderShortName(shortName));
    }

    @Test
    public void testUpdateToCloudProviderShortName() {
        String expectedCloudAccountId = "cloud-account-id";
        String expectedInstanceId = "instance-id";
        String expectedProductId = "product-id";
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setCloudAccountId(expectedCloudAccountId)
            .setCloudInstanceId(expectedInstanceId)
            .setProductId(expectedProductId)
            .setCloudProviderShortName("AWS");

        consumer = anonymousCloudConsumerCurator.create(consumer);
        String expectedCloudProviderShortName = "GCP";
        consumer.setCloudProviderShortName(expectedCloudProviderShortName);

        AnonymousCloudConsumer actual = anonymousCloudConsumerCurator.merge(consumer);

        assertNotNull(actual.getId());
        assertEquals(consumer.getUuid(), actual.getUuid());
        assertEquals(expectedCloudAccountId, actual.getCloudAccountId());
        assertEquals(expectedInstanceId, actual.getCloudInstanceId());
        assertEquals(expectedProductId, actual.getProductId());
        assertEquals(expectedCloudProviderShortName, actual.getCloudProviderShortName());
    }

    private String generateString(int length) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            builder.append("i");
        }

        return builder.toString();
    }

}
