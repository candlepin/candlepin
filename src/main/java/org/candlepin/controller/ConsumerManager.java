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

import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ContentAccessCertificateCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.Owner;
import org.candlepin.service.EventAdapter;
import org.candlepin.service.model.CloudCheckInEvent;
import org.candlepin.util.Util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.Date;
import java.util.HashSet;
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
    private final ContentAccessCertificateCurator caCertCurator;
    private final EnvironmentCurator envCurator;
    private final EventAdapter eventAdapter;
    private final ObjectMapper objectMapper;
    private final I18n i18n;

    @Inject
    public ConsumerManager(ConsumerCurator consumerCurator,
        ContentAccessCertificateCurator caCertCurator,
        EnvironmentCurator envCurator,
        EventAdapter eventAdapter,
        ObjectMapper objectMapper,
        I18n i18n) {
        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.caCertCurator = Objects.requireNonNull(caCertCurator);
        this.envCurator = Objects.requireNonNull(envCurator);
        this.eventAdapter = Objects.requireNonNull(eventAdapter);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.i18n = Objects.requireNonNull(i18n);
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
     * Sets the {@link Environment}s of the {@link Consumer}s that correspond to the provided consumer UUIDs.
     * Consumers will be unchanged if they already exist in the provided environments and are in the right
     * priority. Other consumers will be removed from their existing environments and set to the provided
     * environments. The ordering of the provided environment IDs dictates the priority. The first environment
     * ID in the list being the top priority and the last environment ID in the list being the least priority.
     * All updated consumers will have their content access certificate revoked.
     *
     * @param consumerUuids
     *  the UUIDs of the consumers that should have the environments set
     *
     * @param targetEnvIds
     *  the IDs of the {@link Environment}s that should be set for the consumers
     *
     * @param owner
     *  the owner of the consumers
     *
     * @throws IllegalArgumentException
     *  if the owner is null
     *
     * @throws BadRequestException
     *  if the owner is not in SCA content access mode, if one or more of provided consumer UUIDs or
     *  environment IDs is unknown to the owner, or if the target environments contains null or a duplicate
     *  value.
     *
     * @return
     *  the UUIDs of the {@link Consumer}s that were updated and had their environments set
     */
    public Set<String> setConsumersEnvironments(List<String> consumerUuids, List<String> targetEnvIds,
        Owner owner) {

        if (owner == null) {
            throw new IllegalArgumentException("owner is null");
        }

        if (consumerUuids == null || consumerUuids.isEmpty() || targetEnvIds == null ||
            targetEnvIds.isEmpty()) {
            return new HashSet<>();
        }

        if (!ContentAccessMode.ORG_ENVIRONMENT.toDatabaseValue().equals(owner.getContentAccessMode())) {
            throw new BadRequestException(i18n.tr("Owner is not in SCA content access mode"));
        }

        Set<String> unknownConsumerUuids = consumerCurator
            .getNonExistentConsumerUuids(consumerUuids, owner.getKey());
        if (!unknownConsumerUuids.isEmpty()) {
            throw new BadRequestException(i18n.tr("Unkown consumer UUIDs: {0}",
                unknownConsumerUuids));
        }

        Set<String> unknownEnvironmentIds = envCurator
            .getNonExistentEnvironmentIds(targetEnvIds, owner);
        if (!unknownEnvironmentIds.isEmpty()) {
            throw new BadRequestException(i18n.tr("Unknown environment IDs: {0}",
                unknownEnvironmentIds));
        }

        if (Util.containsDuplicateOrNull(targetEnvIds)) {
            throw new BadRequestException(i18n.tr("Environment IDs contains duplicates or null value"));
        }

        log.info("setting {} consumers to {} environments",
            consumerUuids.size(), targetEnvIds.size());

        Set<String> consumersToUpdate = new HashSet<>();
        Map<String, List<String>> consumerUuidToEnvs = envCurator.findEnvironmentsOf(consumerUuids);
        for (String consumerUuid : consumerUuids) {
            List<String> currentEnvs = consumerUuidToEnvs.get(consumerUuid);
            if (currentEnvs == null || !targetEnvIds.equals(currentEnvs)) {
                consumersToUpdate.add(consumerUuid);
            }
        }

        log.info("{} of the provided consumers are not in the target environments currently",
            consumersToUpdate.size());

        if (consumersToUpdate.isEmpty()) {
            return new HashSet<>();
        }

        int added = envCurator.setConsumersEnvironments(consumersToUpdate, targetEnvIds);
        log.info("{} consumers have been set to the target environments", added);

        // Delete all of the content access certificates for consumers that had an environment change
        List<String> ids = caCertCurator.getIdsForConsumers(consumersToUpdate);
        int unlinked = consumerCurator.unlinkCaCertificates(ids);
        log.info("{} content access certs unlinked", unlinked);

        int certsRemoved = caCertCurator.deleteByIds(ids);
        log.info("{} content access certificates removed", certsRemoved);

        return consumersToUpdate;
    }
}
