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

import org.candlepin.gutterball.curator.jpa.ComplianceSnapshotCurator;
import org.candlepin.gutterball.model.Event;
import org.candlepin.gutterball.model.jpa.ComplianceSnapshot;
import org.candlepin.gutterball.model.jpa.ComplianceStatusSnapshot;
import org.candlepin.gutterball.model.jpa.ConsumerSnapshot;
import org.candlepin.gutterball.model.jpa.EntitlementSnapshot;
import org.candlepin.gutterball.model.jpa.OwnerSnapshot;

import com.google.inject.Inject;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Iterator;

/**
 * Handler for Compliance Events.  Currently we only send ComplianceCreated events.
 * they're not persisted in the candlepin database, so they're always recreated,
 * it's more of a bundle anyhow.
 */
@HandlerTarget("COMPLIANCE")
public class ComplianceHandler implements EventHandler {

    private static Logger log = LoggerFactory.getLogger(ComplianceHandler.class);

    private ComplianceSnapshotCurator jpaCurator;

    @Inject
    public ComplianceHandler(ComplianceSnapshotCurator jpaCurator) {
        this.jpaCurator = jpaCurator;
    }

    @Override
    public void handleCreated(Event event) {
        BasicDBObject entity = (BasicDBObject) event.getNewEntity();
        DBObject status = (DBObject) entity.get("status");
        DBObject consumer = (DBObject) entity.get("consumer");
        DBObject owner = (DBObject) consumer.get("owner");

        Date statusDate = (Date) status.get("date");
        OwnerSnapshot ownerSnap =
                new OwnerSnapshot((String) owner.get("key"), (String) owner.get("displayName"));
        ConsumerSnapshot consumerSnap = new ConsumerSnapshot((String) consumer.get("uuid"), ownerSnap);
        ComplianceStatusSnapshot statusSnap = new ComplianceStatusSnapshot(statusDate,
                (String) status.get("status"));
        ComplianceSnapshot snap = new ComplianceSnapshot(statusDate, consumerSnap, statusSnap);

        BasicDBList ents = (BasicDBList) entity.get("entitlements");
        Iterator<Object> iter = ents.iterator();
        while (iter.hasNext()) {
            DBObject ent = (DBObject) iter.next();
            snap.addEntitlementSnapshot(new EntitlementSnapshot((Integer) ent.get("quantity")));
        }


        jpaCurator.create(snap);
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
