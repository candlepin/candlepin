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
import org.candlepin.model.ContentAccessCertificateCurator;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.service.EventAdapter;
import org.candlepin.service.model.CloudCheckInEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.persist.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

/**
 * The ConsumerManager class is responsible for managing operations related
 * to the Consumer object.
 */
public class ConsumerManager {
    private static final Logger log = LoggerFactory.getLogger(ConsumerManager.class);

    private final ConsumerCurator consumerCurator;
    private final ContentAccessCertificateCurator caCertCurator;
    private final EnvironmentCurator envCurator;
    private final EventAdapter eventAdapter;
    private final ObjectMapper objectMapper;

    @Inject
    public ConsumerManager(ConsumerCurator consumerCurator,
        ContentAccessCertificateCurator caCertCurator,
        EnvironmentCurator envCurator,
        EventAdapter eventAdapter,
        ObjectMapper objectMapper) {
        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.caCertCurator = Objects.requireNonNull(caCertCurator);
        this.envCurator = Objects.requireNonNull(envCurator);
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

    // TODO: Java Doc
    @Transactional
    public List<String> setConsumersEnvironments(Collection<String> consumerUuids,
        List<String> environmentIds) {

        if (consumerUuids == null || consumerUuids.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> consumersToUpdate = envCurator
            .getConsumerUuidsNotExactlyInEnvs(consumerUuids, environmentIds);

        log.info("{} of the consumers are not currently in the target environments");

        if (consumersToUpdate.isEmpty()) {
            return new ArrayList<>();
        }

        int added = envCurator.setConsumersEnvironments(consumersToUpdate, environmentIds);
        log.info("{} consumers set to the target environments", added);

        // Delete all of the content access certificates for consumers that had an environment change
        List<String> ids = caCertCurator.listCertSerialIdsByConsumerUuids(consumersToUpdate);

        int unlinked = consumerCurator.unlinkCaCertificates(ids);
        log.info("{} content access certs unlinked", unlinked);

        int certsRemoved = caCertCurator.deleteByIds(ids);
        log.info("{} content access certificates removed", certsRemoved);

        return consumersToUpdate;
    }
}
