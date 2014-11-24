/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.gutterball.eventhandler;

import org.candlepin.gutterball.curator.ComplianceSnapshotCurator;
import org.candlepin.gutterball.curator.ConsumerStateCurator;
import org.candlepin.gutterball.model.Event;
import org.candlepin.gutterball.model.snapshot.Compliance;
import org.candlepin.gutterball.model.snapshot.ComplianceStatus;
import org.candlepin.gutterball.model.snapshot.Consumer;
import org.candlepin.gutterball.model.snapshot.Owner;
import org.candlepin.gutterball.model.ConsumerState;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Date;

/**
 * Handler for Compliance Events.  Currently we only send ComplianceCreated events.
 * they're not persisted in the candlepin database, so they're always recreated,
 * it's more of a bundle anyhow.
 */
@HandlerTarget("COMPLIANCE")
public class ComplianceHandler implements EventHandler {

    private static Logger log = LoggerFactory.getLogger(ComplianceHandler.class);

    private ObjectMapper mapper;
    private ComplianceSnapshotCurator complianceCurator;
    private ConsumerStateCurator consumerStateCurator;

    @Inject
    public ComplianceHandler(ObjectMapper mapper, ComplianceSnapshotCurator complianceCurator,
        ConsumerStateCurator consumerStateCurator) {

        this.mapper = mapper;
        this.complianceCurator = complianceCurator;
        this.consumerStateCurator = consumerStateCurator;
    }

    @Override
    public void handleCreated(Event event) {
        Compliance compliance;
        ComplianceStatus status;
        Consumer consumer;
        Owner owner;
        ConsumerState cstate;

        String uuid;
        String ownerKey;
        Date eventDate;

        try {
            compliance = mapper.readValue(event.getNewEntity(), Compliance.class);
        }
        catch (IOException e) {
            throw new RuntimeException("Could not deserialize compliance snapshot data.", e);
        }

        consumer = compliance.getConsumer();
        if (consumer == null) {
            throw new RuntimeException(
                "Unable to read consumer information from compliance status event."
            );
        }

        status = compliance.getStatus();
        if (status == null) {
            throw new RuntimeException(
                "Unable to read compliance status from compliance status event."
            );
        }

        owner = consumer.getOwner();
        if (owner == null) {
            throw new RuntimeException(
                "Unable to read owner information from compliance status event."
            );
        }

        // Inject the consumer state object...
        uuid = consumer.getUuid();
        eventDate = status.getDate();
        cstate = this.consumerStateCurator.findByUuid(uuid);

        if (cstate == null) {
            // At this point, we've not received a register event for this consumer, so we need
            // to create one from the information in this event.

            // TODO: Perhaps we should be validating that the uuid, key and date are properly
            // set as well...?
            ownerKey = owner.getKey();
            cstate = this.consumerStateCurator.create(new ConsumerState(uuid, ownerKey, eventDate));
        }

        // Not picked up from the event.
        consumer.setConsumerState(cstate);
        compliance.setDate(eventDate);

        complianceCurator.create(compliance);
    }

    @Override
    public void handleUpdated(Event event) {
        log.warn("Received a COMPLIANCE MODIFIED event, skipping");
    }

    @Override
    public void handleDeleted(Event event) {
        log.warn("Received a COMPLIANCE DELETED event, skipping");
    }
}
