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
import org.candlepin.gutterball.model.Event;
import org.candlepin.gutterball.model.Event.Status;
import org.candlepin.gutterball.model.snapshot.Compliance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Handler for Compliance Events.  Currently we only send ComplianceCreated events.
 * they're not persisted in the candlepin database, so they're always recreated,
 * it's more of a bundle anyhow.
 */
@HandlerTarget("COMPLIANCE")
public class ComplianceHandler extends EventHandler {

    private static Logger log = LoggerFactory.getLogger(ComplianceHandler.class);

    private ComplianceSnapshotCurator complianceCurator;
    private ObjectMapper mapper;

    @Inject
    public ComplianceHandler(ObjectMapper mapper, ComplianceSnapshotCurator complianceCurator) {
        this.complianceCurator = complianceCurator;
        this.mapper = mapper;
    }

    @Override
    public Status handleCreated(Event event) {
        Compliance snap;
        try {
            snap = mapper.readValue(event.getNewEntity(), Compliance.class);
            // Not picked up from the event.
            snap.setDate(snap.getStatus().getDate());
            complianceCurator.create(snap);
        }
        catch (IOException e) {
            throw new RuntimeException("Could not deserialize compliance snapshot data.", e);
        }
        return Status.PROCESSED;
    }
}
