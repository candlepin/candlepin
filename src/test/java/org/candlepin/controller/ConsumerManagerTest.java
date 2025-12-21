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

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCloudData;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ContentAccessCertificateCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.Owner;
import org.candlepin.service.EventAdapter;
import org.candlepin.service.model.CloudCheckInEvent;
import org.candlepin.test.TestUtil;
import org.candlepin.util.NonNullLinkedHashSet;
import org.candlepin.util.ObjectMapperFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collection;
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
    private ContentAccessCertificateCurator contentAccessCertificateCurator;
    @Mock
    private EventAdapter eventAdapter;
    @Mock
    private EnvironmentCurator envCurator;

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
        NonNullLinkedHashSet<String> envIds = new NonNullLinkedHashSet<>();
        envIds.add("env-id");

        Set<String> actual = consumerManager
            .setConsumersEnvironments(owner, consumerUuids, envIds);

        assertThat(actual)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testSetConsumersEnvironmentsWithNullEnvironmentIds() {
        Owner owner = new Owner()
            .setKey("owner");
        ConsumerManager consumerManager = buildConsumerManager();

        Set<String> actual = consumerManager.setConsumersEnvironments(owner, List.of("consumer-uuid"), null);
        assertThat(actual)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testSetConsumersEnvironmentsWithNullOwner() {
        ConsumerManager consumerManager = buildConsumerManager();
        NonNullLinkedHashSet<String> envIds = new NonNullLinkedHashSet<>();
        envIds.add("env-id");

        assertThrows(IllegalArgumentException.class, () -> {
            consumerManager.setConsumersEnvironments(null, List.of("consumer-uuid"), envIds);
        });
    }

    @Test
    public void testSetConsumersEnvironmentsWithAllConsumersAlreadyInEnvironments() {
        Owner owner = new Owner()
            .setKey("owner");

        List<String> consumerUuids = List.of("consumer-1", "consumer-2");
        List<String> envIds = new ArrayList<>();
        envIds.add("env-1");
        envIds.add("env-2");
        NonNullLinkedHashSet<String> envIdsHashSet = new NonNullLinkedHashSet<>();
        envIdsHashSet.addAll(envIds);

        Map<String, List<String>> consumerUuidToEnvs = new HashMap<>();
        consumerUuidToEnvs.put(consumerUuids.get(0), envIds);
        consumerUuidToEnvs.put(consumerUuids.get(1), envIds);

        doReturn(consumerUuidToEnvs).when(envCurator).findEnvironmentsOf(consumerUuids);

        ConsumerManager consumerManager = buildConsumerManager();
        Set<String> actual = consumerManager.setConsumersEnvironments(owner, consumerUuids, envIdsHashSet);

        verify(consumerCurator, never()).merge(any(Consumer.class));
        verify(contentAccessCertificateCurator, never()).deleteForConsumers(any(Collection.class));

        assertThat(actual)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testSetConsumersEnvironmentsWithDifferentPriorityOrder() {
        Owner owner = new Owner()
            .setKey("owner");

        String consumerUuidWithDifferentPriority = "consumer-uuid-1";
        String consumerUuidWithSamePriority = "consumer-uuid-2";
        Set<String> consumerUuids = Set.of(consumerUuidWithDifferentPriority, consumerUuidWithSamePriority);
        NonNullLinkedHashSet<String> envIds = new NonNullLinkedHashSet<>();
        envIds.add("env-1");
        envIds.add("env-2");

        Map<String, List<String>> consumerUuidToEnvs = new HashMap<>();
        consumerUuidToEnvs.put(consumerUuidWithDifferentPriority, List.of("env-2", "env-1"));
        consumerUuidToEnvs.put(consumerUuidWithSamePriority, List.of("env-1", "env-2"));

        doReturn(consumerUuidToEnvs).when(envCurator).findEnvironmentsOf(any(Collection.class));

        ConsumerManager consumerManager = buildConsumerManager();
        Set<String> actual = consumerManager.setConsumersEnvironments(owner, consumerUuids, envIds);

        verify(envCurator).setConsumersEnvironments(Set.of(consumerUuidWithDifferentPriority), envIds);
        verify(contentAccessCertificateCurator).deleteForConsumers(Set.of(consumerUuidWithDifferentPriority));

        assertThat(actual)
            .isNotNull()
            .singleElement()
            .isEqualTo(consumerUuidWithDifferentPriority);
    }

    @Test
    public void testSetConsumersEnvironmentsWithEmptyEnvironmentIds() {
        Owner owner = new Owner()
            .setKey("owner");

        String consumer1Uuid = "consumer-1-uuid";
        String consumer2Uuid = "consumer-2-uuid";
        List<String> consumerUuids = List.of(consumer1Uuid, consumer2Uuid);
        NonNullLinkedHashSet<String> envIds = new NonNullLinkedHashSet<>();

        ConsumerManager consumerManager = buildConsumerManager();
        Set<String> actual = consumerManager.setConsumersEnvironments(owner, consumerUuids, envIds);

        assertThat(actual)
            .isNotNull()
            .containsAll(consumerUuids);

        verify(envCurator).setConsumersEnvironments(consumerUuids, envIds);
    }

    @Test
    public void testSetConsumersEnvironments() {
        Owner owner = new Owner()
            .setKey("owner");

        String consumer1Uuid = "consumer-1-uuid";
        String consumer2Uuid = "consumer-2-uuid";
        Set<String> consumerUuids = Set.of(consumer1Uuid, consumer2Uuid);
        NonNullLinkedHashSet<String> envIds = new NonNullLinkedHashSet<>();
        envIds.add("env-1");
        envIds.add("env-2");

        Map<String, List<String>> consumerUuidToEnvs = new HashMap<>();
        consumerUuidToEnvs.put(consumer1Uuid, List.of("env-1"));

        doReturn(consumerUuidToEnvs).when(envCurator).findEnvironmentsOf(any(Collection.class));

        ConsumerManager consumerManager = buildConsumerManager();
        Set<String> actual = consumerManager.setConsumersEnvironments(owner, consumerUuids, envIds);

        verify(envCurator).setConsumersEnvironments(consumerUuids, envIds);
        verify(contentAccessCertificateCurator).deleteForConsumers(consumerUuids);

        assertThat(actual)
            .isNotNull()
            .containsAll(consumerUuids);
    }

    private ConsumerManager buildConsumerManager() {
        return new ConsumerManager(consumerCurator, contentAccessCertificateCurator, envCurator, eventAdapter,
            objectMapper);
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
