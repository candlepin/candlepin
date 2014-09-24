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
import org.candlepin.gutterball.model.jpa.ComplianceSnapshot;
import org.candlepin.gutterball.model.jpa.ComplianceStatusSnapshot;
import org.candlepin.gutterball.model.jpa.ConsumerSnapshot;
import org.candlepin.gutterball.model.jpa.EntitlementSnapshot;
import org.candlepin.gutterball.model.jpa.Event;
import org.candlepin.gutterball.model.jpa.OwnerSnapshot;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
    private ObjectMapper mapper;

    @Inject
    public ComplianceHandler(ComplianceSnapshotCurator jpaCurator) {
        this.jpaCurator = jpaCurator;

        SimpleModule module = new SimpleModule("ComplianceSnapshotModule");
        module.addDeserializer(ComplianceSnapshot.class, new JsonDeserializer<ComplianceSnapshot>() {

            @Override
            public ComplianceSnapshot deserialize(JsonParser jp,
                    DeserializationContext context) throws IOException,
                    JsonProcessingException {

                JsonNode complianceEventJson = jp.getCodec().readTree(jp);
                JsonNode consumer = complianceEventJson.get("consumer");
                JsonNode owner = consumer.get("owner");
                JsonNode status = complianceEventJson.get("status");
                JsonNode entitlements = complianceEventJson.get("entitlements");

                Date statusDate = context.parseDate(status.get("date").asText());

                OwnerSnapshot ownerSnap = new OwnerSnapshot(owner.get("key").asText(),
                        owner.get("displayName").asText());

                ConsumerSnapshot consumerSnap = new ConsumerSnapshot(consumer.get("uuid").asText(),
                        ownerSnap);

                ComplianceStatusSnapshot statusSnap = new ComplianceStatusSnapshot(statusDate,
                        status.get("status").asText());

                ComplianceSnapshot snapshot = new ComplianceSnapshot(statusDate, consumerSnap, statusSnap);

                Iterator<JsonNode> entIter = entitlements.elements();
                while (entIter.hasNext()) {
                    JsonNode ent = entIter.next();
                    EntitlementSnapshot entSnap = new EntitlementSnapshot(ent.get("quanty").asInt());
                    snapshot.addEntitlementSnapshot(entSnap);
                }

                return snapshot;
            }
        });

        mapper = new ObjectMapper();
        mapper.registerModule(module);
    }

    @Override
    public void handleCreated(Event event) {
        ComplianceSnapshot snap;
        try {
            snap = mapper.readValue(event.getNewEntity(), ComplianceSnapshot.class);
            jpaCurator.create(snap);
        }
        catch (IOException e) {
            throw new RuntimeException("Could not deserialize compliance snapshot data.", e);
        }
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
