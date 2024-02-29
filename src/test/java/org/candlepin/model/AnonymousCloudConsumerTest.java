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

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.collection;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import javax.validation.ConstraintViolationException;



public class AnonymousCloudConsumerTest extends DatabaseTestFixture {

    @Test
    public void testUuidGeneration() throws Exception {
        String expectedCloudAccountId = "cloud-account-id";
        String expectedInstanceId = "instance-id";
        String expectedOfferId = "offer-id";
        String expectedProductId = "product-id";
        String expectedCloudProviderShortName = TestUtil.randomString();
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setCloudAccountId(expectedCloudAccountId)
            .setCloudInstanceId(expectedInstanceId)
            .setCloudOfferingId(expectedOfferId)
            .setProductIds(List.of(expectedProductId))
            .setCloudProviderShortName(expectedCloudProviderShortName);

        AnonymousCloudConsumer actual = anonymousCloudConsumerCurator.create(consumer);

        assertThat(actual)
            .isNotNull()
            .doesNotReturn(null, AnonymousCloudConsumer::getId)
            .doesNotReturn(null, AnonymousCloudConsumer::getUuid)
            .returns(expectedCloudAccountId, AnonymousCloudConsumer::getCloudAccountId)
            .returns(expectedInstanceId, AnonymousCloudConsumer::getCloudInstanceId)
            .returns(expectedCloudProviderShortName, AnonymousCloudConsumer::getCloudProviderShortName)
            .returns(expectedOfferId, AnonymousCloudConsumer::getCloudOfferingId)
            .extracting(AnonymousCloudConsumer::getProductIds, as(collection(String.class)))
            .containsExactly(expectedProductId);
    }

    @Test
    public void testCloudAccountIdFieldRequired() throws Exception {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setProductIds(List.of("product-id"))
            .setCloudInstanceId("instance-id")
            .setCloudOfferingId("offer-id")
            .setCloudProviderShortName(TestUtil.randomString());

        assertThrows(ConstraintViolationException.class,
            () -> anonymousCloudConsumerCurator.create(consumer));
    }

    @Test
    public void testCloudInstanceIdFieldRequired() throws Exception {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setCloudAccountId("cloud-account-id")
            .setProductIds(List.of("product-id"))
            .setCloudOfferingId("offer-id")
            .setCloudProviderShortName(TestUtil.randomString());

        assertThrows(ConstraintViolationException.class,
            () -> anonymousCloudConsumerCurator.create(consumer));
    }

    @Test
    public void testCloudOfferingIdFieldRequired() throws Exception {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setCloudAccountId("cloud-account-id")
            .setProductIds(List.of("product-id"))
            .setCloudInstanceId("instance-id")
            .setCloudProviderShortName(TestUtil.randomString());

        assertThrows(ConstraintViolationException.class,
            () -> anonymousCloudConsumerCurator.create(consumer));
    }

    @Test
    public void testProductIdsFieldRequired() throws Exception {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setCloudAccountId("cloud-account-id")
            .setCloudInstanceId("instance-id")
            .setCloudOfferingId("offer-id")
            .setCloudProviderShortName(TestUtil.randomString());

        assertThrows(ConstraintViolationException.class,
            () -> anonymousCloudConsumerCurator.create(consumer));
    }

    @Test
    public void testCloudProviderShortNameFieldRequired() throws Exception {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setCloudAccountId("cloud-account-id")
            .setCloudInstanceId("instance-id")
            .setCloudOfferingId("offer-id")
            .setProductIds(List.of("product-id"));

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
        String expectedCloudProviderShortName = TestUtil.randomString();
        String expectedInstanceId = "instance-id";
        String expectedOfferId = "offer-id";
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setCloudAccountId(expectedCloudAccountId)
            .setCloudInstanceId(expectedInstanceId)
            .setCloudOfferingId(expectedOfferId)
            .setProductIds(List.of(expectedProductId))
            .setCloudProviderShortName(expectedCloudProviderShortName);

        consumer = anonymousCloudConsumerCurator.create(consumer);
        String expectedUuid = "updated-uuid";
        consumer.setUuid(expectedUuid);

        AnonymousCloudConsumer actual = anonymousCloudConsumerCurator.merge(consumer);

        assertThat(actual)
            .isNotNull()
            .doesNotReturn(null, AnonymousCloudConsumer::getId)
            .returns(expectedUuid, AnonymousCloudConsumer::getUuid)
            .returns(expectedCloudAccountId, AnonymousCloudConsumer::getCloudAccountId)
            .returns(expectedInstanceId, AnonymousCloudConsumer::getCloudInstanceId)
            .returns(expectedOfferId, AnonymousCloudConsumer::getCloudOfferingId)
            .returns(expectedCloudProviderShortName, AnonymousCloudConsumer::getCloudProviderShortName)
            .extracting(AnonymousCloudConsumer::getProductIds, as(collection(String.class)))
            .containsExactly(expectedProductId);
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
        String expectedOfferId = "offer-id";
        String expectedCloudProviderShortName = TestUtil.randomString();
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setCloudAccountId("initial-cloud-account-id")
            .setCloudInstanceId("instance-id")
            .setCloudOfferingId(expectedOfferId)
            .setProductIds(List.of(expectedProductId))
            .setCloudProviderShortName(expectedCloudProviderShortName);

        consumer = anonymousCloudConsumerCurator.create(consumer);
        String expectedCloudAccountId = "cloud-account-id";
        consumer.setCloudAccountId(expectedCloudAccountId);

        AnonymousCloudConsumer actual = anonymousCloudConsumerCurator.merge(consumer);

        assertThat(actual)
            .isNotNull()
            .doesNotReturn(null, AnonymousCloudConsumer::getId)
            .returns(consumer.getUuid(), AnonymousCloudConsumer::getUuid)
            .returns(expectedCloudAccountId, AnonymousCloudConsumer::getCloudAccountId)
            .returns(expectedInstanceId, AnonymousCloudConsumer::getCloudInstanceId)
            .returns(expectedOfferId, AnonymousCloudConsumer::getCloudOfferingId)
            .returns(expectedCloudProviderShortName, AnonymousCloudConsumer::getCloudProviderShortName)
            .extracting(AnonymousCloudConsumer::getProductIds, as(collection(String.class)))
            .containsExactly(expectedProductId);
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
        String expectedOfferId = "offer-id";
        String expectedProductId = "product-id";
        String expectedCloudProviderShortName = TestUtil.randomString();
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setCloudAccountId(expectedCloudAccountId)
            .setCloudInstanceId("init-instance-id")
            .setCloudOfferingId(expectedOfferId)
            .setProductIds(List.of(expectedProductId))
            .setCloudProviderShortName(expectedCloudProviderShortName);

        consumer = anonymousCloudConsumerCurator.create(consumer);
        String expectedInstanceId = "updated-instance-id";
        consumer.setCloudInstanceId(expectedInstanceId);

        AnonymousCloudConsumer actual = anonymousCloudConsumerCurator.merge(consumer);

        assertThat(actual)
            .isNotNull()
            .doesNotReturn(null, AnonymousCloudConsumer::getId)
            .returns(consumer.getUuid(), AnonymousCloudConsumer::getUuid)
            .returns(expectedCloudAccountId, AnonymousCloudConsumer::getCloudAccountId)
            .returns(expectedInstanceId, AnonymousCloudConsumer::getCloudInstanceId)
            .returns(expectedOfferId, AnonymousCloudConsumer::getCloudOfferingId)
            .returns(expectedCloudProviderShortName, AnonymousCloudConsumer::getCloudProviderShortName)
            .extracting(AnonymousCloudConsumer::getProductIds, as(collection(String.class)))
            .containsExactly(expectedProductId);
    }

    @Test
    public void testSetCloudOfferingIdWithNull() {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer();

        assertThrows(IllegalArgumentException.class, () -> consumer.setCloudOfferingId(null));
    }

    @Test
    public void testSetCloudOfferingIdWithGreaterThanMaxLength() {
        String offerId = generateString(AnonymousCloudConsumer.CLOUD_OFFERING_ID_MAX_LENGTH + 1);
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer();

        assertThrows(IllegalArgumentException.class, () -> consumer.setCloudOfferingId(offerId));
    }

    @Test
    public void testUpdateToCloudOfferingId() {
        String expectedCloudAccountId = "cloud-account-id";
        String expectedProductId = "product-id";
        String expectedInstanceId = "instance-id";
        String expectedCloudProviderShortName = TestUtil.randomString();
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setCloudAccountId(expectedCloudAccountId)
            .setCloudInstanceId(expectedInstanceId)
            .setCloudOfferingId("initial-offer-id")
            .setProductIds(List.of(expectedProductId))
            .setCloudProviderShortName(expectedCloudProviderShortName);

        consumer = anonymousCloudConsumerCurator.create(consumer);
        String expectedOfferId = "updated-offer-id";
        consumer.setCloudOfferingId(expectedOfferId);

        AnonymousCloudConsumer actual = anonymousCloudConsumerCurator.merge(consumer);

        assertThat(actual)
            .isNotNull()
            .doesNotReturn(null, AnonymousCloudConsumer::getId)
            .returns(consumer.getUuid(), AnonymousCloudConsumer::getUuid)
            .returns(expectedCloudAccountId, AnonymousCloudConsumer::getCloudAccountId)
            .returns(expectedInstanceId, AnonymousCloudConsumer::getCloudInstanceId)
            .returns(expectedOfferId, AnonymousCloudConsumer::getCloudOfferingId)
            .returns(expectedCloudProviderShortName, AnonymousCloudConsumer::getCloudProviderShortName)
            .extracting(AnonymousCloudConsumer::getProductIds, as(collection(String.class)))
            .containsExactly(expectedProductId);
    }


    @Test
    public void testSetProductIdsWithNull() {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer();

        assertThrows(IllegalArgumentException.class, () -> consumer.setProductIds(null));
    }

    @Test
    public void testSetProductIdsWithEmptyList() {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer();

        assertThrows(IllegalArgumentException.class, () -> consumer.setProductIds(List.of()));
    }

    @Test
    public void testSetProductIdsWithNullAndBlankValues() {
        String expectedProdId = "prod-id";
        List<String> productIds = new ArrayList<>();
        productIds.add(expectedProdId);
        productIds.add(null);
        productIds.add("");
        productIds.add("  ");

        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setCloudAccountId("account-id")
            .setCloudInstanceId("instance-id")
            .setCloudOfferingId("offer-id")
            .setProductIds(productIds)
            .setCloudProviderShortName(TestUtil.randomString());

        consumer = anonymousCloudConsumerCurator.create(consumer);

        assertThat(consumer)
            .isNotNull()
            .extracting(AnonymousCloudConsumer::getProductIds, as(collection(String.class)))
            .containsExactly(expectedProdId);
    }

    @Test
    public void testUpdateToProductIds() {
        String expectedCloudAccountId = "cloud-account-id";
        String expectedInstanceId = "instance-id";
        String expectedOfferId = "offer-id";
        String expectedCloudProviderShortName = TestUtil.randomString();
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setCloudAccountId(expectedCloudAccountId)
            .setCloudInstanceId("instance-id")
            .setCloudOfferingId(expectedOfferId)
            .setProductIds(List.of("init-prod-id"))
            .setCloudProviderShortName(expectedCloudProviderShortName);

        consumer = anonymousCloudConsumerCurator.create(consumer);
        String expectedProductId = "product-id";
        consumer.setProductIds(List.of(expectedProductId));

        AnonymousCloudConsumer actual = anonymousCloudConsumerCurator.merge(consumer);

        assertThat(actual)
            .isNotNull()
            .doesNotReturn(null, AnonymousCloudConsumer::getId)
            .returns(consumer.getUuid(), AnonymousCloudConsumer::getUuid)
            .returns(expectedCloudAccountId, AnonymousCloudConsumer::getCloudAccountId)
            .returns(expectedInstanceId, AnonymousCloudConsumer::getCloudInstanceId)
            .returns(expectedOfferId, AnonymousCloudConsumer::getCloudOfferingId)
            .returns(expectedCloudProviderShortName, AnonymousCloudConsumer::getCloudProviderShortName)
            .extracting(AnonymousCloudConsumer::getProductIds, as(collection(String.class)))
            .containsExactly(expectedProductId);
    }

    @Test
    public void testSetCloudProviderShortNameWithNull() {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer();

        assertThrows(IllegalArgumentException.class,
            () -> consumer.setCloudProviderShortName(null));
    }

    @Test
    public void testUpdateToCloudProviderShortName() {
        String expectedCloudAccountId = "cloud-account-id";
        String expectedInstanceId = "instance-id";
        String expectedOfferId = "offer-id";
        String expectedProductId = "product-id";
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setCloudAccountId(expectedCloudAccountId)
            .setCloudInstanceId(expectedInstanceId)
            .setCloudOfferingId(expectedOfferId)
            .setProductIds(List.of(expectedProductId))
            .setCloudProviderShortName(TestUtil.randomString())
            .setProductIds(List.of(expectedProductId));

        consumer = anonymousCloudConsumerCurator.create(consumer);
        String expectedCloudProviderShortName = TestUtil.randomString();
        consumer.setCloudProviderShortName(expectedCloudProviderShortName);

        AnonymousCloudConsumer actual = anonymousCloudConsumerCurator.merge(consumer);

        assertThat(actual)
            .isNotNull()
            .doesNotReturn(null, AnonymousCloudConsumer::getId)
            .returns(consumer.getUuid(), AnonymousCloudConsumer::getUuid)
            .returns(expectedCloudAccountId, AnonymousCloudConsumer::getCloudAccountId)
            .returns(expectedInstanceId, AnonymousCloudConsumer::getCloudInstanceId)
            .returns(expectedOfferId, AnonymousCloudConsumer::getCloudOfferingId)
            .returns(expectedCloudProviderShortName, AnonymousCloudConsumer::getCloudProviderShortName)
            .extracting(AnonymousCloudConsumer::getProductIds, as(collection(String.class)))
            .containsExactly(expectedProductId);
    }

    @Test
    public void testUpdateToAnonymousContentAccessCertificate() {
        CertificateSerial serial = new CertificateSerial();
        this.certSerialCurator.create(serial);
        AnonymousContentAccessCertificate certificate = new AnonymousContentAccessCertificate();
        certificate.setCert("cert-1");
        certificate.setKey("key-1");
        certificate.setSerial(serial);

        certificate = anonymousContentAccessCertCurator.create(certificate);

        String expectedCloudAccountId = "cloud-account-id";
        String expectedInstanceId = "instance-id";
        String expectedOfferId = "offer-id";
        String expectedProductId = "product-id";
        String expectedCloudProviderShortName = TestUtil.randomString();
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setCloudAccountId(expectedCloudAccountId)
            .setCloudInstanceId(expectedInstanceId)
            .setCloudOfferingId(expectedOfferId)
            .setProductIds(List.of(expectedProductId))
            .setCloudProviderShortName(expectedCloudProviderShortName)
            .setContentAccessCert(certificate);

        consumer = anonymousCloudConsumerCurator.create(consumer);

        CertificateSerial expectedSerial = new CertificateSerial();
        this.certSerialCurator.create(expectedSerial);
        AnonymousContentAccessCertificate expectedCert = new AnonymousContentAccessCertificate();
        expectedCert.setCert("cert-2");
        expectedCert.setKey("key-2");
        expectedCert.setSerial(expectedSerial);
        expectedCert = anonymousContentAccessCertCurator.create(expectedCert);
        consumer.setContentAccessCert(expectedCert);

        AnonymousCloudConsumer actual = anonymousCloudConsumerCurator.merge(consumer);

        assertThat(actual)
            .isNotNull()
            .doesNotReturn(null, AnonymousCloudConsumer::getId)
            .returns(consumer.getUuid(), AnonymousCloudConsumer::getUuid)
            .returns(expectedCloudAccountId, AnonymousCloudConsumer::getCloudAccountId)
            .returns(expectedInstanceId, AnonymousCloudConsumer::getCloudInstanceId)
            .returns(expectedOfferId, AnonymousCloudConsumer::getCloudOfferingId)
            .returns(expectedCloudProviderShortName, AnonymousCloudConsumer::getCloudProviderShortName)
            .returns(expectedCert, AnonymousCloudConsumer::getContentAccessCert)
            .extracting(AnonymousCloudConsumer::getProductIds, as(collection(String.class)))
            .containsExactly(expectedProductId);
    }

    private String generateString(int length) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            builder.append("i");
        }

        return builder.toString();
    }

}
