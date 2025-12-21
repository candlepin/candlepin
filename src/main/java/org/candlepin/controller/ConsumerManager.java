/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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
import org.candlepin.model.ConsumerCloudData;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ContentAccessCertificateCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.Owner;
import org.candlepin.service.EventAdapter;
import org.candlepin.service.model.CloudCheckInEvent;
import org.candlepin.util.NonNullLinkedHashSet;

import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.inject.Inject;


/**
 * The ConsumerManager class is responsible for managing operations related
 * to the Consumer object.
 */
public class ConsumerManager {
    private static final Logger log = LoggerFactory.getLogger(ConsumerManager.class);

    private final ConsumerCurator consumerCurator;
    private final ContentAccessCertificateCurator caCertificateCurator;
    private final EnvironmentCurator envCurator;
    private final EventAdapter eventAdapter;
    private final ObjectMapper objectMapper;

    @Inject
    public ConsumerManager(ConsumerCurator consumerCurator,
        ContentAccessCertificateCurator caCertificateCurator,
        EnvironmentCurator envCurator,
        EventAdapter eventAdapter,
        ObjectMapper objectMapper) {
        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.caCertificateCurator = Objects.requireNonNull(caCertificateCurator);
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

        ConsumerCloudData cloudData = consumer.getConsumerCloudData();
        if (cloudData != null) {
            CloudCheckInEvent cloudCheckInEvent = new CloudCheckInEvent(cloudData, objectMapper);
            eventAdapter.publish(cloudCheckInEvent);
        }
    }

    /**
     * Sets the {@link Environment}s of the {@link Consumer}s that correspond to the provided consumer UUIDs.
     * Consumers will be unchanged if they already exist in the provided environments and are in the right
     * priority. Other consumers will be removed from their existing environments and set to the provided
     * environments. The ordering of the provided environment IDs dictates the priority. The first environment
     * ID in the list being the top priority and the last environment ID in the list being the least priority.
     * All updated consumers will have their content access certificate revoked.
     *
     * @param owner
     *  the owner of the consumers
     *
     * @param consumerUuids
     *  the UUIDs of the consumers that should have the environments set
     *
     * @param targetEnvIds
     *  the IDs of the {@link Environment}s that should be set for the consumers
     *
     * @throws IllegalArgumentException
     *  if the owner is null
     *
     * @return
     *  the UUIDs of the {@link Consumer}s that were updated and had their environments set
     */
    public Set<String> setConsumersEnvironments(Owner owner, Collection<String> consumerUuids,
        NonNullLinkedHashSet<String> targetEnvIds) {

        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        if (consumerUuids == null || consumerUuids.isEmpty() || targetEnvIds == null) {
            return new HashSet<>();
        }

        log.info("setting {} consumers to {} environments",
            consumerUuids.size(), targetEnvIds.size());

        // If there are no target environments, then all the consumers should be removed from all of their
        // current environments.
        if (targetEnvIds.isEmpty()) {
            int updated = envCurator.setConsumersEnvironments(consumerUuids, targetEnvIds);
            log.info("{} consumers removed from all environments", updated);

            return new HashSet<>(consumerUuids);
        }

        Set<String> consumersToUpdate = new HashSet<>();
        Map<String, List<String>> consumerUuidToEnvs = envCurator.findEnvironmentsOf(consumerUuids);
        for (String consumerUuid : consumerUuids) {
            List<String> currentEnvs = consumerUuidToEnvs.get(consumerUuid);
            if (currentEnvs == null || !hasSameElementsInOrder(targetEnvIds, currentEnvs)) {
                consumersToUpdate.add(consumerUuid);
            }
        }

        log.info("{} of the provided consumers are not in the target environments currently",
            consumersToUpdate.size());

        if (consumersToUpdate.isEmpty()) {
            return new HashSet<>();
        }

        int updated = envCurator.setConsumersEnvironments(consumersToUpdate, targetEnvIds);
        log.info("{} consumers have been set to the target environments", updated);

        int deletedCerts = caCertificateCurator.deleteForConsumers(consumersToUpdate);
        log.info("{} content access certs unlinked and removed", deletedCerts);

        return consumersToUpdate;
    }

    private boolean hasSameElementsInOrder(NonNullLinkedHashSet<String> list1, Collection<String> list2) {
        if (list1.size() != list2.size()) {
            return false;
        }

        Iterator<String> list1Iterator = list1.iterator();
        Iterator<String> list2Iterator = list2.iterator();
        while (list1Iterator.hasNext()) {
            if (!list1Iterator.next().equals(list2Iterator.next())) {
                return false;
            }
        }

        return true;
    }

}
