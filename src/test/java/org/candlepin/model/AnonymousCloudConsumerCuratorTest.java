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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.util.Util;

import com.google.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;



public class AnonymousCloudConsumerCuratorTest extends DatabaseTestFixture {

    @Inject
    private AnonymousCloudConsumerCurator anonymousCloudConsumerCurator;

    @Test
    public void testCreate() throws Exception {
        AnonymousCloudConsumer expected = new AnonymousCloudConsumer()
            .setCloudAccountId("cloudAccountId")
            .setCloudInstanceId("instanceId")
            .setProductId("productId")
            .setCloudProviderShortName("shortName");

        this.anonymousCloudConsumerCurator.create(expected);

        List<AnonymousCloudConsumer> actual = this.getAnonymousConsumersFromDB();
        assertThat(actual)
            .singleElement()
            .returns(expected.getId(), AnonymousCloudConsumer::getId)
            .returns(expected.getUuid(), AnonymousCloudConsumer::getUuid)
            .returns(expected.getCloudAccountId(), AnonymousCloudConsumer::getCloudAccountId)
            .returns(expected.getCloudInstanceId(), AnonymousCloudConsumer::getCloudInstanceId)
            .returns(expected.getCloudProviderShortName(), AnonymousCloudConsumer::getCloudProviderShortName)
            .returns(expected.getProductId(), AnonymousCloudConsumer::getProductId);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    @ValueSource(strings = { "  " })
    public void testGetByUuidWithInvalidUuid(String uuid) {
        AnonymousCloudConsumer expected = new AnonymousCloudConsumer()
            .setCloudAccountId("cloudAccountId")
            .setCloudInstanceId("instanceId")
            .setProductId("productId")
            .setCloudProviderShortName("shortName");

        this.anonymousCloudConsumerCurator.create(expected);

        AnonymousCloudConsumer actual = this.anonymousCloudConsumerCurator.getByUuid(uuid);

        assertNull(actual);
    }

    @Test
    public void testGetByUuidWithNonExistingUuid() {
        AnonymousCloudConsumer expected = new AnonymousCloudConsumer()
            .setCloudAccountId("cloudAccountId")
            .setCloudInstanceId("instanceId")
            .setProductId("productId")
            .setCloudProviderShortName("shortName");
        this.anonymousCloudConsumerCurator.create(expected);

        AnonymousCloudConsumer actual = this.anonymousCloudConsumerCurator.getByUuid(Util.generateUUID());

        assertNull(actual);
    }

    @Test
    public void testGetByUuidWithExistingUuid() {
        AnonymousCloudConsumer expected = new AnonymousCloudConsumer()
            .setCloudAccountId("cloudAccountId")
            .setCloudInstanceId("instanceId")
            .setProductId("productId")
            .setCloudProviderShortName("shortName");
        this.anonymousCloudConsumerCurator.create(expected);

        AnonymousCloudConsumer actual = this.anonymousCloudConsumerCurator.getByUuid(expected.getUuid());

        assertThat(actual)
            .isNotNull()
            .returns(expected.getId(), AnonymousCloudConsumer::getId)
            .returns(expected.getUuid(), AnonymousCloudConsumer::getUuid)
            .returns(expected.getCloudAccountId(), AnonymousCloudConsumer::getCloudAccountId)
            .returns(expected.getCloudInstanceId(), AnonymousCloudConsumer::getCloudInstanceId)
            .returns(expected.getCloudProviderShortName(), AnonymousCloudConsumer::getCloudProviderShortName)
            .returns(expected.getProductId(), AnonymousCloudConsumer::getProductId);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    @ValueSource(strings = { "  " })
    public void testGetByCloudInstanceIdWithInvalidInstanceId(String instanceId) {
        AnonymousCloudConsumer expected = new AnonymousCloudConsumer()
            .setCloudAccountId("cloudAccountId")
            .setCloudInstanceId("instanceId")
            .setProductId("productId")
            .setCloudProviderShortName("shortName");

        this.anonymousCloudConsumerCurator.create(expected);

        AnonymousCloudConsumer actual = this.anonymousCloudConsumerCurator
            .getByCloudInstanceId(instanceId);

        assertNull(actual);
    }

    @Test
    public void testGetByCloudInstanceIdWithExistingInstanceId() {
        AnonymousCloudConsumer expected = new AnonymousCloudConsumer()
            .setCloudAccountId("cloudAccountId")
            .setCloudInstanceId("instanceId")
            .setProductId("productId")
            .setCloudProviderShortName("shortName");
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
            .returns(expected.getProductId(), AnonymousCloudConsumer::getProductId);
    }

    @Test
    public void testGetByCloudInstanceIdWithNonExistingInstanceId() {
        AnonymousCloudConsumer expected = new AnonymousCloudConsumer()
            .setCloudAccountId("cloudAccountId")
            .setCloudInstanceId("instanceId")
            .setProductId("productId")
            .setCloudProviderShortName("shortName");
        this.anonymousCloudConsumerCurator.create(expected);

        AnonymousCloudConsumer actual = this.anonymousCloudConsumerCurator
            .getByCloudInstanceId(Util.generateUUID());

        assertNull(actual);
    }

    private List<AnonymousCloudConsumer> getAnonymousConsumersFromDB() {
        return this.getEntityManager()
            .createQuery("select c from AnonymousCloudConsumer c", AnonymousCloudConsumer.class)
            .getResultList();
    }

}
