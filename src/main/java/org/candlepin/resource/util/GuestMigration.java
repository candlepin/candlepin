/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.resource.util;

import org.candlepin.dto.api.v1.ConsumerDTO;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.GuestId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

/** Used to make guest migrations atomic.  Since a guest migration involves a host, a guest, and possibly an
 * old host it is easy to run into race conditions if the migration is not handled atomically.
 */
public class GuestMigration {
    private static final Logger log = LoggerFactory.getLogger(GuestMigration.class);

    private ConsumerCurator consumerCurator;

    private boolean migrationPending;
    private MigrationManifest manifest;

    @Inject
    public GuestMigration(ConsumerCurator consumerCurator) {
        this.consumerCurator = consumerCurator;

        migrationPending = false;
    }

    public boolean isMigrationPending() {
        return migrationPending;
    }

    public void migrate() {
        migrate(true);
    }

    public void migrate(boolean flush) {
        if (!migrationPending) {
            // we can accept this and continue
            log.debug("Guest migration found no consumers.");
            return;
        }

        manifest.writeMigrationChanges();
        consumerCurator.bulkUpdate(manifest.asSet(), flush);
        migrationPending = false;
    }

    /**
     * Build a manifest detailing any guest migrations occurring due to a host consumer update.
     *
     * If a consumer's guest was already reported by another host consumer, record that host in the
     * manifest as well so that the migration can be made atomically.
     *
     * @param incoming incoming consumer in DTO form
     * @param existing existing consumer in model form
     * @return guestMigration object that contains all information related to the migration
     */
    public GuestMigration buildMigrationManifest(ConsumerDTO incoming, Consumer existing) {
        if (incoming.getGuestIds() == null) {
            log.debug("Guests not included in this consumer update, skipping update.");
            migrationPending = false;
            return this;
        }

        manifest = new MigrationManifest(existing);

        log.debug("Updating {} guest IDs.", incoming.getGuestIds().size());
        List<GuestId> existingGuests = existing.getGuestIds();
        // Transform incoming GuestIdDTOs to GuestIds
        List<GuestId> incomingGuestIds = incoming.getGuestIds().stream().filter(Objects::nonNull).distinct()
            .map(guestIdDTO -> new GuestId(guestIdDTO.getGuestId(), existing, guestIdDTO.getAttributes()))
            .collect(Collectors.toList());

        List<GuestId> removedGuests = getRemovedGuestIds(existing, incomingGuestIds);
        List<GuestId> addedGuests = getAddedGuestIds(existing, incomingGuestIds);

        // remove guests that are missing.
        if (existingGuests != null) {
            for (GuestId guestId : removedGuests) {
                existingGuests.remove(guestId);
                log.debug("Guest ID removed: {}", guestId);
            }
        }

        // Check guests that are existing/added.
        for (GuestId guestId : incomingGuestIds) {
            if (addedGuests.contains(guestId)) {
                manifest.addGuestId(guestId);
                log.debug("New guest ID added: {}", guestId);
            }
        }

        migrationPending = removedGuests.size() != 0 || addedGuests.size() != 0;
        return this;
    }

    /**
     * Build a manifest detailing any guest migrations occurring due to a host consumer update.
     *
     * If a consumer's guest was already reported by another host consumer, record that host in the
     * manifest as well so that the migration can be made atomically.
     *
     * @param incoming incoming consumer
     * @param existing existing consumer
     * @return guestMigration object that contains all information related to the migration
     */
    public GuestMigration buildMigrationManifest(Consumer incoming, Consumer existing) {
        if (incoming.getGuestIds() == null) {
            log.debug("Guests not included in this consumer update, skipping update.");
            migrationPending = false;
            return this;
        }

        manifest = new MigrationManifest(existing);

        log.debug("Updating {} guest IDs.", incoming.getGuestIds().size());
        List<GuestId> existingGuests = existing.getGuestIds();
        List<GuestId> removedGuests = getRemovedGuestIds(existing, incoming.getGuestIds());
        List<GuestId> addedGuests = getAddedGuestIds(existing, incoming.getGuestIds());

        // remove guests that are missing.
        if (existingGuests != null) {
            for (GuestId guestId : removedGuests) {
                existingGuests.remove(guestId);
                log.debug("Guest ID removed: {}", guestId);
            }
        }

        // Check guests that are existing/added.
        for (GuestId guestId : incoming.getGuestIds()) {
            if (addedGuests.contains(guestId)) {
                manifest.addGuestId(guestId);
                log.debug("New guest ID added: {}", guestId);
            }
        }

        migrationPending = removedGuests.size() != 0 || addedGuests.size() != 0;
        return this;
    }

    private List<GuestId> getRemovedGuestIds(Consumer existingConsumer, List<GuestId> incomingIds) {
        List<GuestId> existingIds = (existingConsumer.getGuestIds() == null) ? new ArrayList<>() :
            new ArrayList<>(existingConsumer.getGuestIds());

        if (incomingIds == null) {
            incomingIds = new ArrayList<>();
        }

        // The incomingId list is expected to be a *complete* list of guest IDs.  Therefore, any id on our
        // consumer that is not on the list of incoming IDs is an ID that needs to be removed.
        List<GuestId> removedGuests = new ArrayList<>(existingIds);
        removedGuests.removeAll(incomingIds);
        return removedGuests;
    }

    private List<GuestId> getAddedGuestIds(Consumer existingConsumer, List<GuestId> incomingIds) {
        List<GuestId> existingIds = (existingConsumer.getGuestIds() == null) ? new ArrayList<>() :
            new ArrayList<>(existingConsumer.getGuestIds());

        if (incomingIds == null) {
            incomingIds = new ArrayList<>();
        }

        // Any id on our list of incoming IDs that's not one of the current IDs should be considered new
        List<GuestId> addedGuests = new ArrayList<>(incomingIds);
        addedGuests.removeAll(existingIds);
        return addedGuests;
    }

    public String toString() {
        return "GuestMigration[pending: " + migrationPending + "]";
    }

    /**
     * Simple data container meant to hold all consumers related to a guest migration.
     */
    public static class MigrationManifest {
        private Consumer newHost;
        private List<GuestId> newGuests = new ArrayList<>();
        private Map<Consumer, List<GuestId>> oldMappings = new HashMap<>();

        public MigrationManifest(Consumer newHost) {
            this.newHost = newHost;
        }

        public void addGuestId(GuestId guestId) {
            newGuests.add(guestId);
        }

        public void addOldMapping(Consumer host, GuestId guest) {
            if (!oldMappings.containsKey(host)) {
                oldMappings.put(host, new ArrayList<>());
            }

            oldMappings.get(host).add(guest);
        }

        /**
         * Return all consumers in the manifest as a set suitable for bulk updates.
         * @return A set containing all consumers in the migration manifest
         */
        public Set<Consumer> asSet() {
            Set<Consumer> modifiedConsumers = new HashSet<>();
            modifiedConsumers.add(newHost);
            modifiedConsumers.addAll(oldMappings.keySet());
            return modifiedConsumers;
        }

        /** Perform all necessary object updates necessary to resolve this manifest */
        public void writeMigrationChanges() {
            for (GuestId id : newGuests) {
                newHost.addGuestId(id);
            }

            for (Map.Entry<Consumer, List<GuestId>> entry : oldMappings.entrySet()) {
                Consumer oldHost = entry.getKey();
                List<GuestId> transferedGuests = entry.getValue();
                oldHost.getGuestIds().removeAll(transferedGuests);
            }
        }
    }
}
