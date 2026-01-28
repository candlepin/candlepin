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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

public class AnonymousCloudConsumerCuratorTest extends DatabaseTestFixture {

    @Test
    public void testCreate() throws Exception {
        String expectedProductId = "productId";
        AnonymousCloudConsumer expected = new AnonymousCloudConsumer()
            .setCloudAccountId("cloudAccountId")
            .setCloudInstanceId("instanceId")
            .setCloudOfferingId("offeringId")
            .setProductIds(List.of(expectedProductId))
            .setCloudProviderShortName(TestUtil.randomString());

        this.anonymousCloudConsumerCurator.create(expected);

        List<AnonymousCloudConsumer> actual = this.getAnonymousConsumersFromDB();
        assertThat(actual)
            .singleElement()
            .returns(expected.getId(), AnonymousCloudConsumer::getId)
            .returns(expected.getUuid(), AnonymousCloudConsumer::getUuid)
            .returns(expected.getCloudAccountId(), AnonymousCloudConsumer::getCloudAccountId)
            .returns(expected.getCloudInstanceId(), AnonymousCloudConsumer::getCloudInstanceId)
            .returns(expected.getCloudProviderShortName(), AnonymousCloudConsumer::getCloudProviderShortName)
            .extracting(AnonymousCloudConsumer::getProductIds, as(collection(String.class)))
            .containsExactly(expectedProductId);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    @ValueSource(strings = { "  " })
    public void testGetByUuidWithInvalidUuid(String uuid) {
        AnonymousCloudConsumer expected = new AnonymousCloudConsumer()
            .setCloudAccountId("cloudAccountId")
            .setCloudInstanceId("instanceId")
            .setCloudOfferingId("offeringId")
            .setProductIds(List.of("productId"))
            .setCloudProviderShortName(TestUtil.randomString());

        this.anonymousCloudConsumerCurator.create(expected);

        AnonymousCloudConsumer actual = this.anonymousCloudConsumerCurator.getByUuid(uuid);

        assertNull(actual);
    }

    @Test
    public void testGetByUuidWithNonExistingUuid() {
        AnonymousCloudConsumer expected = new AnonymousCloudConsumer()
            .setCloudAccountId("cloudAccountId")
            .setCloudInstanceId("instanceId")
            .setCloudOfferingId("offeringId")
            .setProductIds(List.of("productId"))
            .setCloudProviderShortName(TestUtil.randomString());
        this.anonymousCloudConsumerCurator.create(expected);

        AnonymousCloudConsumer actual = this.anonymousCloudConsumerCurator.getByUuid(Util.generateUUID());

        assertNull(actual);
    }

    @Test
    public void testGetByUuidWithExistingUuid() {
        String expectedProductId = "productId";
        AnonymousCloudConsumer expected = new AnonymousCloudConsumer()
            .setCloudAccountId("cloudAccountId")
            .setCloudInstanceId("instanceId")
            .setCloudOfferingId("offeringId")
            .setProductIds(List.of(expectedProductId))
            .setCloudProviderShortName(TestUtil.randomString());
        this.anonymousCloudConsumerCurator.create(expected);

        AnonymousCloudConsumer actual = this.anonymousCloudConsumerCurator.getByUuid(expected.getUuid());

        assertThat(actual)
            .isNotNull()
            .returns(expected.getId(), AnonymousCloudConsumer::getId)
            .returns(expected.getUuid(), AnonymousCloudConsumer::getUuid)
            .returns(expected.getCloudAccountId(), AnonymousCloudConsumer::getCloudAccountId)
            .returns(expected.getCloudInstanceId(), AnonymousCloudConsumer::getCloudInstanceId)
            .returns(expected.getCloudProviderShortName(), AnonymousCloudConsumer::getCloudProviderShortName)
            .extracting(AnonymousCloudConsumer::getProductIds, as(collection(String.class)))
            .containsExactly(expectedProductId);
    }

    @Test
    public void testGetByUuidsWithNullUuids() {
        List<AnonymousCloudConsumer> actual = this.anonymousCloudConsumerCurator.getByUuids(null);

        assertThat(actual)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testGetByUuidsWithEmptyUuids() {
        List<AnonymousCloudConsumer> actual = this.anonymousCloudConsumerCurator.getByUuids(List.of());

        assertThat(actual)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testGetByUuidsWithExistingAnonymousCloudConsumer() {
        String expectedProductId = "productId";
        AnonymousCloudConsumer expected = new AnonymousCloudConsumer()
            .setCloudAccountId("cloudAccountId")
            .setCloudInstanceId("instanceId")
            .setCloudOfferingId("offeringId")
            .setProductIds(List.of(expectedProductId))
            .setProductIds(List.of("productId"))
            .setCloudProviderShortName(TestUtil.randomString());
        expected = this.anonymousCloudConsumerCurator.create(expected);

        AnonymousCloudConsumer other = new AnonymousCloudConsumer()
            .setCloudAccountId("otherCloudAccountId")
            .setCloudInstanceId("otherInstanceId")
            .setCloudOfferingId("otherOfferingId")
            .setProductIds(List.of("otherProductId"))
            .setProductIds(List.of("productId"))
            .setCloudProviderShortName(TestUtil.randomString());
        other = this.anonymousCloudConsumerCurator.create(other);

        List<AnonymousCloudConsumer> actual = this.anonymousCloudConsumerCurator
            .getByUuids(List.of(expected.getUuid()));

        assertThat(actual)
            .singleElement()
            .returns(expected.getId(), AnonymousCloudConsumer::getId)
            .returns(expected.getUuid(), AnonymousCloudConsumer::getUuid)
            .returns(expected.getCloudAccountId(), AnonymousCloudConsumer::getCloudAccountId)
            .returns(expected.getCloudInstanceId(), AnonymousCloudConsumer::getCloudInstanceId)
            .returns(expected.getCloudOfferingId(), AnonymousCloudConsumer::getCloudOfferingId)
            .returns(expected.getCloudProviderShortName(), AnonymousCloudConsumer::getCloudProviderShortName)
            .extracting(AnonymousCloudConsumer::getProductIds, as(collection(String.class)))
            .containsExactly(expectedProductId);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    @ValueSource(strings = { "  " })
    public void testGetByCloudInstanceIdWithInvalidInstanceId(String instanceId) {
        AnonymousCloudConsumer expected = new AnonymousCloudConsumer()
            .setCloudAccountId("cloudAccountId")
            .setCloudInstanceId("instanceId")
            .setCloudOfferingId("offeringId")
            .setProductIds(List.of("productId"))
            .setCloudProviderShortName(TestUtil.randomString());

        this.anonymousCloudConsumerCurator.create(expected);

        AnonymousCloudConsumer actual = this.anonymousCloudConsumerCurator
            .getByCloudInstanceId(instanceId);

        assertNull(actual);
    }

    @Test
    public void testGetByCloudInstanceIdWithExistingInstanceId() {
        String expectedProductId = "productId";
        AnonymousCloudConsumer expected = new AnonymousCloudConsumer()
            .setCloudAccountId("cloudAccountId")
            .setCloudInstanceId("instanceId")
            .setCloudOfferingId("offeringId")
            .setProductIds(List.of(expectedProductId))
            .setCloudProviderShortName(TestUtil.randomString());
        this.anonymousCloudConsumerCurator.create(expected);

        AnonymousCloudConsumer actual = this.anonymousCloudConsumerCurator
            .getByCloudInstanceId(expected.getCloudInstanceId());

        assertThat(actual)
            .isNotNull()
            .returns(expected.getId(), AnonymousCloudConsumer::getId)
            .returns(expected.getUuid(), AnonymousCloudConsumer::getUuid)
            .returns(expected.getCloudAccountId(), AnonymousCloudConsumer::getCloudAccountId)
            .returns(expected.getCloudInstanceId(), AnonymousCloudConsumer::getCloudInstanceId)
            .returns(expected.getCloudProviderShortName(), AnonymousCloudConsumer::getCloudProviderShortName)
            .extracting(AnonymousCloudConsumer::getProductIds, as(collection(String.class)))
            .containsExactly(expectedProductId);
    }

    @Test
    public void testGetByCloudInstanceIdWithNonExistingInstanceId() {
        AnonymousCloudConsumer expected = new AnonymousCloudConsumer()
            .setCloudAccountId("cloudAccountId")
            .setCloudInstanceId("instanceId")
            .setCloudOfferingId("offeringId")
            .setProductIds(List.of("productId"))
            .setCloudProviderShortName(TestUtil.randomString());
        this.anonymousCloudConsumerCurator.create(expected);

        AnonymousCloudConsumer actual = this.anonymousCloudConsumerCurator
            .getByCloudInstanceId(Util.generateUUID());

        assertNull(actual);
    }

    @Test
    public void testGetByAccountIdWithExistingAnonymousCloudConsumer() {
        String otherProductId = "other-product-Id";
        AnonymousCloudConsumer expected = new AnonymousCloudConsumer()
            .setCloudAccountId("cloudAccountId")
            .setCloudInstanceId("instanceId")
            .setCloudOfferingId("offeringId")
            .setProductIds(List.of("productId"))
            .setCloudProviderShortName(TestUtil.randomString());
        expected = this.anonymousCloudConsumerCurator.create(expected);

        AnonymousCloudConsumer other = new AnonymousCloudConsumer()
            .setCloudAccountId("otherCloudAccountId")
            .setCloudInstanceId("otherInstanceId")
            .setCloudOfferingId("otherOfferingId")
            .setProductIds(List.of(otherProductId))
            .setCloudProviderShortName(TestUtil.randomString());
        other = this.anonymousCloudConsumerCurator.create(other);

        List<AnonymousCloudConsumer> actual = this.anonymousCloudConsumerCurator
            .getByCloudAccountId("otherCloudAccountId");

        assertThat(actual)
            .singleElement()
            .returns(other.getId(), AnonymousCloudConsumer::getId)
            .returns(other.getUuid(), AnonymousCloudConsumer::getUuid)
            .returns(other.getCloudAccountId(), AnonymousCloudConsumer::getCloudAccountId)
            .returns(other.getCloudInstanceId(), AnonymousCloudConsumer::getCloudInstanceId)
            .returns(other.getCloudOfferingId(), AnonymousCloudConsumer::getCloudOfferingId)
            .returns(other.getCloudProviderShortName(), AnonymousCloudConsumer::getCloudProviderShortName)
            .extracting(AnonymousCloudConsumer::getProductIds, as(collection(String.class)))
            .containsExactly(otherProductId);
    }

    private List<AnonymousCloudConsumer> getAnonymousConsumersFromDB() {
        return this.getEntityManager()
            .createQuery("select c from AnonymousCloudConsumer c", AnonymousCloudConsumer.class)
            .getResultList();
    }

    @Test
    public void unlinksAnonymousContentAccessCerts() {
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setCloudAccountId("cloudAccount1")
            .setCloudInstanceId("cloudInstance1")
            .setCloudOfferingId("cloudOffering1")
            .setCloudProviderShortName("GCP")
            .setProductIds(Set.of("SKU1"));
        AnonymousCloudConsumer consumer2 = new AnonymousCloudConsumer()
            .setCloudAccountId("cloudAccount2")
            .setCloudInstanceId("cloudInstance2")
            .setCloudOfferingId("cloudOffering2")
            .setCloudProviderShortName("GCP")
            .setProductIds(Set.of("SKU2"));

        AnonymousContentAccessCertificate caCert1 = createExpiredAnonymousContentAccessCert();
        AnonymousContentAccessCertificate caCert2 = createExpiredAnonymousContentAccessCert();
        consumer.setContentAccessCert(caCert1);
        consumer2.setContentAccessCert(caCert2);
        this.anonymousCloudConsumerCurator.create(consumer);
        this.anonymousCloudConsumerCurator.create(consumer2);

        int unlinkedConsumers = this.anonymousCloudConsumerCurator.unlinkAnonymousCertificates(List.of(
            caCert1.getId(),
            caCert2.getId()));
        this.anonymousCloudConsumerCurator.flush();
        this.anonymousCloudConsumerCurator.clear();

        assertEquals(2, unlinkedConsumers);
        for (AnonymousCloudConsumer c : this.anonymousCloudConsumerCurator.listAll()) {
            assertNull(c.getContentAccessCert());
        }
    }

    @Test
    public void noAnonymousContentAccessCertsToUnlink() {
        assertEquals(0, anonymousCloudConsumerCurator.unlinkAnonymousCertificates(null));
        assertEquals(0, anonymousCloudConsumerCurator.unlinkAnonymousCertificates(List.of()));
        assertEquals(0, anonymousCloudConsumerCurator.unlinkAnonymousCertificates(List.of("UnknownId")));
    }

    @Test
    public void testGetInactiveAnonymousCloudConsumerIdsWithNullRetentionDate() {
        assertThrows(IllegalArgumentException.class, () -> {
            this.anonymousCloudConsumerCurator.getInactiveAnonymousCloudConsumerIds(null);
        });
    }

    @Test
    public void testGetInactiveAnonymousCloudConsumerIds() throws Exception {
        AnonymousCloudConsumer inactive1 = new AnonymousCloudConsumer()
            .setCloudAccountId(TestUtil.randomString())
            .setCloudInstanceId(TestUtil.randomString())
            .setCloudOfferingId(TestUtil.randomString())
            .setProductIds(List.of(TestUtil.randomString()))
            .setCloudProviderShortName("AWS");
        inactive1 = this.anonymousCloudConsumerCurator.create(inactive1);

        AnonymousCloudConsumer inactive2 = new AnonymousCloudConsumer()
            .setCloudAccountId(TestUtil.randomString())
            .setCloudInstanceId(TestUtil.randomString())
            .setCloudOfferingId(TestUtil.randomString())
            .setProductIds(List.of(TestUtil.randomString()))
            .setCloudProviderShortName("AWS");
        inactive2 = this.anonymousCloudConsumerCurator.create(inactive2);

        // active1 has an updated date that is before the retention date, but has an active content access
        // certificate and should not be considered inactive

        AnonymousCloudConsumer active1 = new AnonymousCloudConsumer()
            .setCloudAccountId(TestUtil.randomString())
            .setCloudInstanceId(TestUtil.randomString())
            .setCloudOfferingId(TestUtil.randomString())
            .setProductIds(List.of(TestUtil.randomString()))
            .setCloudProviderShortName("AWS")
            .setContentAccessCert(createAnonymousContentAccessCert(Util.tomorrow()));
        active1 = this.anonymousCloudConsumerCurator.create(active1);

        Thread.sleep(1000);
        Instant retentionDate = Instant.now();
        Thread.sleep(1000);

        // active2 has an updated date after the retention date and no content access certificate

        AnonymousCloudConsumer active2 = new AnonymousCloudConsumer()
            .setCloudAccountId(TestUtil.randomString())
            .setCloudInstanceId(TestUtil.randomString())
            .setCloudOfferingId(TestUtil.randomString())
            .setProductIds(List.of(TestUtil.randomString()))
            .setCloudProviderShortName("AWS");
        active2 = this.anonymousCloudConsumerCurator.create(active2);

        List<String> actual = this.anonymousCloudConsumerCurator
            .getInactiveAnonymousCloudConsumerIds(retentionDate);

        assertThat(actual)
            .isNotNull()
            .containsExactlyInAnyOrder(inactive1.getId(), inactive2.getId());
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testDeleteAnonymousCloudConsumersWithNullAndEmptyIds(List<String> ids) {
        int actual = this.anonymousCloudConsumerCurator.deleteAnonymousCloudConsumers(ids);

        assertEquals(0, actual);
    }

    @Test
    public void testDeleteAnonymousCloudConsumers() {
        AnonymousCloudConsumer c1 = new AnonymousCloudConsumer()
            .setCloudAccountId(TestUtil.randomString())
            .setCloudInstanceId(TestUtil.randomString())
            .setCloudOfferingId(TestUtil.randomString())
            .setProductIds(List.of(TestUtil.randomString()))
            .setCloudProviderShortName("AWS");
        c1 = this.anonymousCloudConsumerCurator.create(c1);

        AnonymousCloudConsumer c2 = new AnonymousCloudConsumer()
            .setCloudAccountId(TestUtil.randomString())
            .setCloudInstanceId(TestUtil.randomString())
            .setCloudOfferingId(TestUtil.randomString())
            .setProductIds(List.of(TestUtil.randomString()))
            .setCloudProviderShortName("AWS");
        c2 = this.anonymousCloudConsumerCurator.create(c2);

        AnonymousCloudConsumer c3 = new AnonymousCloudConsumer()
            .setCloudAccountId(TestUtil.randomString())
            .setCloudInstanceId(TestUtil.randomString())
            .setCloudOfferingId(TestUtil.randomString())
            .setProductIds(List.of(TestUtil.randomString()))
            .setCloudProviderShortName("AWS");
        c3 = this.anonymousCloudConsumerCurator.create(c3);

        int deleted = this.anonymousCloudConsumerCurator
            .deleteAnonymousCloudConsumers(List.of(c1.getId(), c2.getId()));
        assertEquals(2, deleted);

        this.anonymousCloudConsumerCurator.flush();
        this.anonymousCloudConsumerCurator.clear();

        AnonymousCloudConsumer actual = this.anonymousCloudConsumerCurator.get(c1.getId());
        assertNull(actual);

        actual = this.anonymousCloudConsumerCurator.get(c2.getId());
        assertNull(actual);

        actual = this.anonymousCloudConsumerCurator.get(c3.getId());
        assertThat(actual)
            .isNotNull()
            .isEqualTo(c3);
    }

    private AnonymousContentAccessCertificate createExpiredAnonymousContentAccessCert() {
        return createAnonymousContentAccessCert(Util.yesterday());
    }

    private AnonymousContentAccessCertificate createAnonymousContentAccessCert(Date date) {
        CertificateSerial serial = certSerialCurator.create(new CertificateSerial(date));

        AnonymousContentAccessCertificate certificate = new AnonymousContentAccessCertificate();
        certificate.setKey(TestUtil.randomString("key-"));
        certificate.setSerial(serial);
        certificate.setCert(TestUtil.randomString("cert-"));

        return anonymousContentAccessCertCurator.create(certificate);
    }

}
