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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCloudData;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.service.EventAdapter;
import org.candlepin.service.model.CloudCheckInEvent;
import org.candlepin.test.TestUtil;
import org.candlepin.util.ObjectMapperFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

@ExtendWith(MockitoExtension.class)
public class ConsumerManagerTest {

    @Mock
    private ConsumerCurator consumerCurator;
    @Mock
    private EventAdapter eventAdapter;

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

    private ConsumerManager buildConsumerManager() {
        return new ConsumerManager(consumerCurator, eventAdapter, objectMapper);
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
