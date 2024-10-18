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

    /**
     * Sets the environments of the {@link Consumer}s that correspond to the provided consumer UUIDs.
     * Consumers in this list will be removed environments they are currently in that are not in the list of 
     * provided environment IDs or not in the right priority. Consumers will be unchanged if they already
     * exist in the provided environments and are in the right priority. The ordering and index of the
     * provided environment IDs dictates the priority. All updated consumers will have their content access
     * certificate revoked.
     *
     * @param consumerUuids
     *  the UUIDs of the consumers that should have the environments set
     *
     * @param environmentIds
     *  the IDs of the {@link Environment}s that should be set for the consumers
     *
     * @return
     *  the UUIDs of the {@link Consumer}s that were updated and had their environments set
     */
    @Transactional
    public List<String> setConsumersEnvironments(Collection<String> consumerUuids,
        List<String> environmentIds) {

        if (consumerUuids == null || consumerUuids.isEmpty()) {
            return new ArrayList<>();
        }

        if (environmentIds == null || environmentIds.isEmpty()) {
            return new ArrayList<>();
        }

        log.info("setting {} consumers to {} environments",
            consumerUuids.size(), environmentIds.size());

        List<String> consumersToUpdate = envCurator
            .getConsumerUuidsNotExactlyInEnvs(consumerUuids, environmentIds);

        log.info("{} of the consumers are not currently in the target environments",
            consumersToUpdate.size());

        if (consumersToUpdate.isEmpty()) {
            return new ArrayList<>();
        }

        int added = envCurator.setConsumersEnvironments(consumersToUpdate, environmentIds);
        log.info("{} consumers have been set to the target environments", added);

        // Delete all of the content access certificates for consumers that had an environment change
        List<String> ids = caCertCurator.listCertSerialIdsByConsumerUuids(consumersToUpdate);
        int unlinked = consumerCurator.unlinkCaCertificates(ids);
        log.info("{} content access certs unlinked", unlinked);

        int certsRemoved = caCertCurator.deleteByIds(ids);
        log.info("{} content access certificates removed", certsRemoved);

        return consumersToUpdate;
    }
}
