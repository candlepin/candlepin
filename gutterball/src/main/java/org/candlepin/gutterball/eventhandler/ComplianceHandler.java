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

import org.candlepin.gutterball.curator.ComplianceDataCurator;
import org.candlepin.gutterball.model.Event;

import com.google.inject.Inject;

/**
 * Handler for Compliance Events.  Currently we only send ComplianceCreated events.
 * they're not persisted in the candlepin database, so they're always recreated,
 * it's more of a bundle anyhow.
 *
 * TODO: Should we throw an exception if we get a compliance update/deleted event?
 */
@HandlerTarget("COMPLIANCE")
public class ComplianceHandler implements EventHandler {

    private ComplianceDataCurator curator;

    @Inject
    public ComplianceHandler(ComplianceDataCurator curator) {
        this.curator = curator;
    }

    @Override
    public void handleCreated(Event event) {
        curator.insert(event.getNewEntity());
    }

    @Override
    public void handleUpdated(Event event) {
        // NO-OP
    }

    @Override
    public void handleDeleted(Event event) {
        // NO-OP
    }
}
