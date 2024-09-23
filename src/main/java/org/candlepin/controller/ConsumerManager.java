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

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.service.EventAdapter;
import org.candlepin.service.model.CloudCheckInEvent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.persist.Transactional;

import java.util.Date;
import java.util.Objects;

import javax.inject.Inject;

/**
 * The ConsumerManager class is responsible for managing operations related
 * to the Consumer object.
 */
public class ConsumerManager {

    private final ConsumerCurator consumerCurator;
    private final EventAdapter eventAdapter;
    private final ObjectMapper objectMapper;

    @Inject
    public ConsumerManager(ConsumerCurator consumerCurator,
        EventAdapter eventAdapter,
        ObjectMapper objectMapper) {
        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.eventAdapter = Objects.requireNonNull(eventAdapter);
        this.objectMapper = Objects.requireNonNull(objectMapper);

    }

    /**
     * Updates the last check-in of the consumer and, if cloud data is present,
     * publishes a cloud check-in event.
     *
     * @param consumer The Consumer object to be updated.
     */
    @Transactional
    public void updateLastCheckIn(Consumer consumer) {
        if (consumer == null) {
            throw new IllegalArgumentException("Consumer cannot be null");
        }

        consumer.setLastCheckin(new Date());
        consumer = consumerCurator.merge(consumer);

        if (consumer.getConsumerCloudData() != null) {
            CloudCheckInEvent cloudCheckInEvent =
                new CloudCheckInEvent(consumer.getConsumerCloudData(), objectMapper);
            eventAdapter.publish(cloudCheckInEvent);
        }
    }
}
