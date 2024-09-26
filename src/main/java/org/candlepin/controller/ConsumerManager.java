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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

/**
 * The ConsumerManager class is responsible for managing operations related
 * to the Consumer object.
 */
public class ConsumerManager {
    private static final Logger log = LoggerFactory.getLogger(ConsumerManager.class);

    private final ConsumerCurator consumerCurator;
    private final EventAdapter eventAdapter;
    private final ObjectMapper objectMapper;
    private final EnvironmentCurator envCurator;
    private final ContentAccessCertificateCurator caCertCurator;

    @Inject
    public ConsumerManager(ConsumerCurator consumerCurator,
        EventAdapter eventAdapter,
        ObjectMapper objectMapper,
        EnvironmentCurator envCurator,
        ContentAccessCertificateCurator caCertCurator) {
        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.eventAdapter = Objects.requireNonNull(eventAdapter);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.envCurator = Objects.requireNonNull(envCurator);
        this.caCertCurator = caCertCurator;
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

    @Transactional
    public Set<String> addConsumersToEnvironments(Collection<String> consumerUuids,
        Collection<String> environmentIds, String ownerId) {

        if (consumerUuids == null || consumerUuids.isEmpty()) {
            return new HashSet<>();
        }

        long startTime = new Date().getTime();

        Collection<Consumer> consumers  = consumerCurator.findByUuidsAndOwner(consumerUuids, ownerId);
        consumerCurator.lock(consumers);

        Set<String> consumersToClearEnvs = new HashSet<>();
        Map<String, List<String>> addConsumerToEnvs = new HashMap<>();
        for (Consumer consumer : consumers) {
            List<String> currentEnvironments = consumer.getEnvironmentIds();
            String consumerUuid = consumer.getUuid();

            // Consumer is not in any environment
            if (currentEnvironments == null) {
                addConsumerToEnvs.put(consumerUuid, new ArrayList<>(environmentIds));
                continue;
            }

            // Check if the consumer needs to be added to the environments
            List<String> addEnvironments = environmentIds.stream()
                .filter(e -> !currentEnvironments.contains(e))
                .collect(Collectors.toList());

            if (addEnvironments.size() > 0) {
                addConsumerToEnvs.put(consumerUuid, addEnvironments);
            }

            // Check if the consumer needs to be removed from any environments
            long removeEnvironments = currentEnvironments.stream()
                .filter(e -> !environmentIds.contains(e))
                .count();

            if (removeEnvironments > 0) {
                consumersToClearEnvs.add(consumerUuid);
            }
        }

        int removed = envCurator.removeConsumerFromOtherEnvironments(consumersToClearEnvs, environmentIds);
        log.info("{} consumers removed from different environments", removed);

        int added = envCurator.addConsumersToEnvironments(addConsumerToEnvs);
        log.info("{} consumers added to the target environments", added);

        // Union the two sets together to get all updated consumers
        consumersToClearEnvs.addAll(addConsumerToEnvs.keySet());

        // Delete all of the content access certificates for consumers that had an environment change
        List<String> ids = caCertCurator.listCertSerialIdsByConsumerUuids(consumersToClearEnvs);

        int unlinked = consumerCurator.unlinkCaCertificates(ids);
        log.info("{} content access certs unlinked", unlinked);

        int certsRemoved = caCertCurator.deleteByIds(ids);
        log.info("{} content access certificates removed", certsRemoved);

        long endTime = new Date().getTime();
        long duration = endTime - startTime;

        log.info("ConsumerManager.addConsumersToEnvironments duration: " + duration + " ms");

        return consumersToClearEnvs;
    }

}
