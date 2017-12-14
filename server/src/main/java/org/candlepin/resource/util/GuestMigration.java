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

import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventSink;
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
import java.util.Set;

import javax.inject.Inject;

/** Used to make guest migrations atomic.  Since a guest migration involves a host, a guest, and possibly an
 * old host it is easy to run into race conditions if the migration is not handled atomically.
 */
public class GuestMigration {
    private static final Logger log = LoggerFactory.getLogger(GuestMigration.class);

    private ConsumerCurator consumerCurator;
    private EventFactory eventFactory;
    private EventSink sink;

    private boolean migrationPending;
    private MigrationManifest manifest;

    @Inject
    public GuestMigration(ConsumerCurator consumerCurator, EventFactory eventFactory, EventSink sink) {
        this.consumerCurator = consumerCurator;
        this.eventFactory = eventFactory;
        this.sink = sink;

        migrationPending = false;
    }

    public boolean isMigrationPending() {
        return migrationPending;
    }

    public void migrate() {
        if (!migrationPending) {
            throw new IllegalStateException("No migration is pending");
        }

        manifest.writeMigrationChanges();
        consumerCurator.bulkUpdate(manifest.asSet());
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
        List<GuestId> removedGuests = getRemovedGuestIds(existing, incoming);
        List<GuestId> addedGuests = getAddedGuestIds(existing, incoming);

        // remove guests that are missing.
        if (existingGuests != null) {
            for (GuestId guestId : removedGuests) {
                existingGuests.remove(guestId);
                log.debug("Guest ID removed: {}", guestId);
                sink.queueEvent(eventFactory.guestIdDeleted(guestId));
            }
        }

        // Check guests that are existing/added.
        for (GuestId guestId : incoming.getGuestIds()) {
            if (addedGuests.contains(guestId)) {
                manifest.addGuestId(guestId);
                Consumer guest = guestId.getConsumer();

                // If it's a brand new guest, there won't be a host to look up
                if (guest != null) {
                    // TODO is it possible for real guests to not resolve to a host? It occurs in unit tests.
                    Consumer oldHost = consumerCurator.getHost(guest);
                    if (oldHost != null) {
                        manifest.addOldMapping(oldHost, guest);
                    }
                }

                log.debug("New guest ID added: {}", guestId);
                sink.queueEvent(eventFactory.guestIdCreated(guestId));
            }
        }

        migrationPending = removedGuests.size() != 0 || addedGuests.size() != 0;
        return this;
    }

    private List<GuestId> getAddedGuestIds(Consumer existing, Consumer incoming) {
        return getDifferenceInGuestIds(incoming, existing);
    }

    private List<GuestId> getRemovedGuestIds(Consumer existing, Consumer incoming) {
        return getDifferenceInGuestIds(existing, incoming);
    }

    private List<GuestId> getDifferenceInGuestIds(Consumer c1, Consumer c2) {
        List<GuestId> ids1 = c1.getGuestIds() == null ?
            new ArrayList<GuestId>() : new ArrayList<GuestId>(c1.getGuestIds());
        List<GuestId> ids2 = c2.getGuestIds() == null ?
            new ArrayList<GuestId>() : new ArrayList<GuestId>(c2.getGuestIds());

        List<GuestId> removedGuests = new ArrayList<GuestId>(ids1);
        removedGuests.removeAll(ids2);
        return removedGuests;
    }

    public String toString() {
        return "GuestMigration[pending: " + migrationPending + "]";
    }

    /**
     * Simple data container meant to hold all consumers related to a guest migration.
     */
    public static class MigrationManifest {
        private Consumer newHost;
        private List<GuestId> newGuests = new ArrayList<GuestId>();
        private Map<Consumer, List<Consumer>> oldMappings = new HashMap<Consumer, List<Consumer>>();

        public MigrationManifest(Consumer newHost) {
            this.newHost = newHost;
        }

        public void addGuestId(GuestId guestId) {
            newGuests.add(guestId);
        }

        public void addOldMapping(Consumer host, Consumer guest) {
            if (!oldMappings.containsKey(host)) {
                oldMappings.put(host, new ArrayList<Consumer>());
            }

            oldMappings.get(host).add(guest);
        }

        /**
         * Return all consumers in the manifest as a set suitable for bulk updates.
         * @return A set containing all consumers in the migration manifest
         */
        public Set<Consumer> asSet() {
            Set<Consumer> modifiedConsumers = new HashSet<Consumer>();
            modifiedConsumers.add(newHost);
            modifiedConsumers.addAll(oldMappings.keySet());
            return modifiedConsumers;
        }

        /** Perform all necessary object updates necessary to resolve this manifest */
        public void writeMigrationChanges() {
            for (GuestId id : newGuests) {
                newHost.addGuestId(id);
            }

            for (Map.Entry<Consumer, List<Consumer>> entry : oldMappings.entrySet()) {
                Consumer oldHost = entry.getKey();
                List<Consumer> transferedGuests = entry.getValue();
                oldHost.getGuestIds().removeAll(transferedGuests);
            }
        }
    }
}
