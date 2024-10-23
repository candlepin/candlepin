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

package org.candlepin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCloudData;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ContentAccessCertificateCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.Owner;
import org.candlepin.service.EventAdapter;
import org.candlepin.service.model.CloudCheckInEvent;
import org.candlepin.test.TestUtil;
import org.candlepin.util.ObjectMapperFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xnap.commons.i18n.I18n;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
public class ConsumerManagerTest {

    @Mock
    private ConsumerCurator consumerCurator;
    @Mock
    private EventAdapter eventAdapter;
    @Mock
    private ContentAccessCertificateCurator caCertCurator;
    @Mock
    private EnvironmentCurator envCurator;
    @Mock
    private I18n i18n;

    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        this.objectMapper = ObjectMapperFactory.getObjectMapper();
    }

    @Test
    public void testUpdateLastCheckInWithNullArgument() {
        ConsumerManager consumerManager = buildConsumerManager();

        assertThrows(IllegalArgumentException.class, () -> consumerManager.updateLastCheckIn(null));
    }

    @Test
    public void testUpdateLastCheckInWithoutCloudData() {
        ConsumerManager consumerManager = buildConsumerManager();
        Consumer consumer = new Consumer();
        doReturn(consumer).when(consumerCurator).merge(any(Consumer.class));

        consumerManager.updateLastCheckIn(consumer);

        verify(consumerCurator).merge(consumer);
        verify(eventAdapter, never()).publish(any());
    }

    @Test
    public void testUpdateLastCheckInWithCloudData() {
        ConsumerManager consumerManager = buildConsumerManager();
        Consumer consumer = createConsumer();
        ConsumerCloudData consumerCloudData = createConsumerCloudData();
        consumerCloudData.setConsumer(consumer);
        consumer.setConsumerCloudData(consumerCloudData);

        doReturn(consumer).when(consumerCurator).merge(any(Consumer.class));

        consumerManager.updateLastCheckIn(consumer);

        verify(consumerCurator).merge(consumer);
        verify(eventAdapter).publish(any(CloudCheckInEvent.class));
    }

    @Test
    public void testPublishCloudCheckInEvent() {
        ConsumerManager consumerManager = buildConsumerManager();
        Consumer consumer = createConsumer();
        ConsumerCloudData consumerCloudData = createConsumerCloudData();
        consumerCloudData.setConsumer(consumer);
        consumer.setConsumerCloudData(consumerCloudData);

        doReturn(consumer).when(consumerCurator).merge(any(Consumer.class));

        consumerManager.updateLastCheckIn(consumer);

        verify(consumerCurator).merge(consumer);
        verify(eventAdapter).publish(argThat(event ->
            event instanceof CloudCheckInEvent &&
                ((CloudCheckInEvent) event).getConsumerUuid()
                    .equals(consumerCloudData.getConsumer().getUuid())
        ));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0} {1}")
    @NullAndEmptySource
    public void testSetConsumersEnvironmentsWithNullOrEmptyConsumerUuids(List<String> consumerUuids) {
        Owner owner = new Owner()
            .setKey("owner");

        ConsumerManager consumerManager = buildConsumerManager();

        Set<String> actual = consumerManager
            .setConsumersEnvironments(consumerUuids, List.of("env-id"), owner);

        assertThat(actual)
            .isNotNull()
            .isEmpty();
    }

    @ParameterizedTest(name = "{displayName} {index}: {0} {1}")
    @NullAndEmptySource
    public void testSetConsumersEnvironmentsWithNullOrEmptyEnvironmentIds(List<String> envIds) {
        Owner owner = new Owner()
            .setKey("owner");
        ConsumerManager consumerManager = buildConsumerManager();

        Set<String> actual = consumerManager
            .setConsumersEnvironments(List.of("consumer-uuid"), envIds, owner);

        assertThat(actual)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testSetConsumersEnvironmentsWithNullOwner() {
        ConsumerManager consumerManager = buildConsumerManager();

        assertThrows(IllegalArgumentException.class, () -> {
            consumerManager.setConsumersEnvironments(List.of("consumer-uuid"), List.of("env-id"), null);
        });
    }

    @Test
    public void testSetConsumersEnvironmentsWithOwnerInEntitlementMode() {
        Owner owner = new Owner()
            .setKey("owner")
            .setContentAccessMode(ContentAccessMode.ENTITLEMENT.toDatabaseValue());

        ConsumerManager consumerManager = buildConsumerManager();

        assertThrows(BadRequestException.class, () -> {
            consumerManager.setConsumersEnvironments(List.of("consumer-uuid"), List.of("env-id"), owner);
        });
    }

    @Test
    public void testSetConsumersEnvironmentsWithUnknownConsumerUuids() {
        Owner owner = new Owner()
            .setKey("owner")
            .setContentAccessMode(ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue());

        ConsumerManager consumerManager = buildConsumerManager();
        List<String> consumerUuids = List.of("c1", "c2");
        List<String> environmentIds = List.of("e1", "e2");

        doReturn(Set.of("c1")).when(consumerCurator)
            .getNonExistentConsumerUuids(consumerUuids, owner.getKey());

        assertThrows(BadRequestException.class, () -> {
            consumerManager.setConsumersEnvironments(consumerUuids, environmentIds, owner);
        });
    }

    @Test
    public void testSetConsumersEnvironmentsWithUnknownEnvironmentIds() {
        Owner owner = new Owner()
            .setKey("owner")
            .setContentAccessMode(ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue());

        ConsumerManager consumerManager = buildConsumerManager();
        List<String> consumerUuids = List.of("c1", "c2");
        List<String> environmentIds = List.of("e1", "e2");

        doReturn(Set.of()).when(consumerCurator)
            .getNonExistentConsumerUuids(consumerUuids, owner.getKey());
        doReturn(Set.of("e1")).when(envCurator)
            .getNonExistentEnvironmentIds(environmentIds, owner);

        assertThrows(BadRequestException.class, () -> {
            consumerManager.setConsumersEnvironments(consumerUuids, environmentIds, owner);
        });
    }

    @Test
    public void testSetConsumersEnvironmentsWithDuplicateEnvironmentIds() {
        Owner owner = new Owner()
            .setKey("owner")
            .setContentAccessMode(ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue());

        ConsumerManager consumerManager = buildConsumerManager();
        List<String> consumerUuids = List.of("c1", "c2");
        List<String> environmentIds = List.of("e1", "e1", "e2");

        doReturn(Set.of()).when(consumerCurator)
            .getNonExistentConsumerUuids(consumerUuids, owner.getKey());
        doReturn(Set.of()).when(envCurator)
            .getNonExistentEnvironmentIds(environmentIds, owner);

        assertThrows(BadRequestException.class, () -> {
            consumerManager.setConsumersEnvironments(consumerUuids, environmentIds, owner);
        });
    }

    @Test
    public void testSetConsumersEnvironmentsWithAllConsumersAlreadyInEnvironments() {
        Owner owner = new Owner()
            .setKey("owner");

        List<String> consumerUuids = List.of("consumer-1", "consumer-2");
        List<String> envIds = List.of("env-1", "env-2");

        doReturn(Set.of()).when(consumerCurator)
            .getNonExistentConsumerUuids(consumerUuids, owner.getKey());
        doReturn(Set.of()).when(envCurator)
            .getNonExistentEnvironmentIds(envIds, owner);

        Map<String, List<String>> consumerUuidToEnvs = new HashMap<>();
        consumerUuidToEnvs.put(consumerUuids.get(0), envIds);
        consumerUuidToEnvs.put(consumerUuids.get(1), envIds);

        doReturn(consumerUuidToEnvs).when(envCurator).findEnvironmentsOf(consumerUuids);

        ConsumerManager consumerManager = buildConsumerManager();
        Set<String> actual = consumerManager.setConsumersEnvironments(consumerUuids, envIds, owner);

        assertThat(actual)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testSetConsumersEnvironments() {
        Owner owner = new Owner()
            .setKey("owner");

        String consumer1Uuid = "consumer-1-uuid";
        String consumer2Uuid = "consumer-2-uuid";
        List<String> consumerUuids = List.of(consumer1Uuid, consumer2Uuid);
        List<String> envIds = List.of("env-1", "env-2");
        List<String> serialIds = List.of("serial-1", "serial-2");

        doReturn(Set.of()).when(consumerCurator)
            .getNonExistentConsumerUuids(consumerUuids, owner.getKey());
        doReturn(Set.of()).when(envCurator)
            .getNonExistentEnvironmentIds(envIds, owner);

        Map<String, List<String>> consumerUuidToEnvs = new HashMap<>();
        consumerUuidToEnvs.put(consumerUuids.get(0), List.of(envIds.get(0)));

        doReturn(consumerUuidToEnvs).when(envCurator).findEnvironmentsOf(consumerUuids);
        doReturn(serialIds).when(caCertCurator).getIdsForConsumers(Set.of(consumer1Uuid, consumer2Uuid));

        ConsumerManager consumerManager = buildConsumerManager();
        Set<String> actual = consumerManager.setConsumersEnvironments(consumerUuids, envIds, owner);

        verify(envCurator).setConsumersEnvironments(Set.of(consumer1Uuid, consumer2Uuid), envIds);
        verify(caCertCurator).getIdsForConsumers(Set.of(consumer1Uuid, consumer2Uuid));
        verify(consumerCurator).unlinkCaCertificates(serialIds);
        verify(caCertCurator).deleteByIds(serialIds);

        assertThat(actual)
            .isNotNull()
            .containsAll(consumerUuids);
    }

    private ConsumerManager buildConsumerManager() {
        return new ConsumerManager(consumerCurator, caCertCurator, envCurator, eventAdapter, objectMapper,
            i18n);
    }

    private Consumer createConsumer() {
        return new Consumer()
            .setUuid(TestUtil.randomString("uuid"))
            .setLastCheckin(new Date());
    }

    private ConsumerCloudData createConsumerCloudData() {
        return new ConsumerCloudData()
            .setCloudAccountId(TestUtil.randomString("cloudAccountId"))
            .setCloudProviderShortName(TestUtil.randomString("AWS"));
    }
}
