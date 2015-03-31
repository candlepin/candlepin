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

package org.candlepin.gutterball.curator;

import static org.junit.Assert.*;

import org.candlepin.gutterball.DatabaseTestFixture;
import org.candlepin.gutterball.model.Event;

import org.junit.Before;
import org.junit.Test;

import java.util.Date;

public class EventCuratorTest extends DatabaseTestFixture {

    private EventCurator curator;
    private Event event;

    @Before
    public void setupTest() {
        curator = injector.getInstance(EventCurator.class);

        beginTransaction();
        event = createEvent("12345");
        curator.create(event);
        commitTransaction();
    }

    @Test
    public void testFindById() {
        assertNotNull(curator.find(event.getId()));
    }

    @Test
    public void testHasEventForMessage() {
        assertTrue(curator.hasEventForMessage(event.getMessageId()));
    }

    @Test
    public void testFindByIdReturnsNullWhenNotFound() {
        assertFalse(curator.hasEventForMessage("not-found"));
    }

    private Event createEvent(String messageId) {
        return new Event(messageId, "type", Event.Status.PROCESSED, "target", "targetName", "principal",
            "ownerId", "consumerId", "entityId", "oldEntity", "newEntity", "referenceId", "referenceType",
            new Date());
    }

}
