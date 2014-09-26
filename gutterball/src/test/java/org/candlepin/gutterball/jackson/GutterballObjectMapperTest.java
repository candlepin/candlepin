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

package org.candlepin.gutterball.jackson;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.candlepin.gutterball.model.ConsumerState;
import org.candlepin.gutterball.model.Event;
import org.candlepin.gutterball.model.snapshot.Compliance;
import org.candlepin.gutterball.model.snapshot.ComplianceStatus;
import org.candlepin.gutterball.model.snapshot.Consumer;
import org.candlepin.gutterball.model.snapshot.Entitlement;
import org.candlepin.gutterball.model.snapshot.Owner;

import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.util.Set;

/**
 * These tests are not really meant to test that every single property on the
 * deserialized Objects was set. It is mostly in place to make sure that the
 * main properties are set and that the sub-objects are being created/initialized.
 */
public class GutterballObjectMapperTest {

    private static final String BASE_PATH = "org/candlepin/gutterball/jackson/";
    private static final String COMPLIANCE_CREATED_FILE = BASE_PATH + "compliance-created.json";
    private static final String CONSUMER_CREATED_FILE = BASE_PATH + "consumer-created.json";
    private static final String CONSUMER_DELETED_FILE = BASE_PATH + "consumer-deleted.json";

    // Create only once
    private GutterballObjectMapper mapper = new GutterballObjectMapper();

    @Test
    public void deserializeEvent() throws Exception {
        String data = loadJsonFile(COMPLIANCE_CREATED_FILE);
        assertNotNull(data);
        Event event = mapper.readValue(data, Event.class);
        assertNotNull(event);
        assertEquals("CREATED", event.getType());
        assertEquals("COMPLIANCE", event.getTarget());
        assertEquals("consumer@23cfbf0f-be71-4c74-8906-5e4c685af5fd", event.getPrincipal());
        assertNotNull(event.getNewEntity());
        assertFalse(event.getNewEntity().isEmpty());
        assertNull(event.getOldEntity());
    }

    @Test
    public void deserializeComplianceSnapshotFromCreatedEvent() throws Exception {
        String data = loadJsonFile(COMPLIANCE_CREATED_FILE);
        assertNotNull(data);
        Event event = mapper.readValue(data, Event.class);
        assertNotNull(event);
        assertEquals("CREATED", event.getType());
        assertEquals("COMPLIANCE", event.getTarget());

        Compliance cs = mapper.readValue(event.getNewEntity(), Compliance.class);
        assertNotNull(cs);

        Consumer consumer = cs.getConsumer();
        assertNotNull(consumer);
        assertEquals("23cfbf0f-be71-4c74-8906-5e4c685af5fd", consumer.getUuid());

        Owner owner = consumer.getOwner();
        assertNotNull(owner);
        assertEquals("admin", owner.getKey());
        assertEquals("Admin Owner", owner.getDisplayName());

        ComplianceStatus status = cs.getStatus();
        assertNotNull(status);
        assertEquals("valid", status.getStatus());

        Set<Entitlement> entitlements = cs.getEntitlements();
        assertNotNull(entitlements);
        assertFalse(entitlements.isEmpty());
        assertEquals(1, entitlements.size());

        Entitlement ent = entitlements.iterator().next();
        assertEquals(1, ent.getQuantity());
    }

    @Test
    public void deserializeConsumerStateFromCreatedEvent() throws Exception {
        String data = loadJsonFile(CONSUMER_CREATED_FILE);
        assertNotNull(data);
        Event event = mapper.readValue(data, Event.class);
        assertNotNull(event);
        assertEquals("CREATED", event.getType());
        assertEquals("CONSUMER", event.getTarget());
        assertNotNull(event.getNewEntity());
        assertNull(event.getOldEntity());

        ConsumerState state = mapper.readValue(event.getNewEntity(), ConsumerState.class);
        assertNotNull(state);
        assertNotNull(state.getCreated());
        assertNull(state.getDeleted());
        assertEquals("admin", state.getOwnerKey());
        assertEquals("4a0734c1-32cd-4471-a5d3-648d238c7fc2", state.getUuid());
    }

    @Test
    public void deserializeConsumerStateFromDeletedEvent() throws Exception {
        String data = loadJsonFile(CONSUMER_DELETED_FILE);
        assertNotNull(data);
        Event event = mapper.readValue(data, Event.class);
        assertNotNull(event);
        assertEquals("DELETED", event.getType());
        assertEquals("CONSUMER", event.getTarget());
        assertNull(event.getNewEntity());
        assertNotNull(event.getOldEntity());

        // For the most part, old consumer state will be the same when the entity was deleted.
        // Really we only care about the UUID.
        System.err.println(event.getOldEntity());
        ConsumerState state = mapper.readValue(event.getOldEntity(), ConsumerState.class);
        assertNotNull(state);
        assertNotNull(state.getCreated());
        // Deleted is a date set by gutterball
        assertNull(state.getDeleted());
        assertEquals("admin", state.getOwnerKey());
        assertEquals("4a0734c1-32cd-4471-a5d3-648d238c7fc2", state.getUuid());
    }

    private String loadJsonFile(String testFile) throws Exception {
        URL fileUrl = Thread.currentThread().getContextClassLoader().getResource(testFile);
        assertNotNull(fileUrl);
        File f = new File(fileUrl.toURI());
        assertTrue(f.exists());
        return new String(Files.readAllBytes(f.toPath()));
    }

}
