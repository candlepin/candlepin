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
package org.candlepin.model.test;

import static org.junit.Assert.*;

import org.candlepin.audit.Event;
import org.candlepin.audit.Event.Type;
import org.candlepin.audit.EventBuilder;
import org.candlepin.audit.EventFactory;
import org.candlepin.auth.Access;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Owner;
import org.candlepin.model.Rules;
import org.candlepin.test.DatabaseTestFixture;

import org.junit.Before;
import org.junit.Test;

import java.util.Date;
import java.util.List;


public class EventCuratorTest extends DatabaseTestFixture {

    private Owner owner;


    @Before
    public void setUp() {
        owner = new Owner("testOwner");
        ownerCurator.create(owner);
    }

    @Test
    public void testCreate() {
        Consumer newConsumer = new Consumer("consumername", "user", owner,
            new ConsumerType("system"));
        consumerTypeCurator.create(newConsumer.getType());
        consumerCurator.create(newConsumer);

        setupPrincipal(owner, Access.ALL);
        EventFactory eventFactory = injector.getInstance(EventFactory.class);
        Event event = eventFactory.consumerCreated(newConsumer);
        eventCurator.create(event);

        Event lookedUp = eventCurator.find(event.getId());
        assertNull(lookedUp.getOldEntity());
        assertEquals(Type.CREATED, lookedUp.getType());
        assertNotNull(lookedUp.getId());
    }

    @Test
    public void testSecondarySorting() {

        Consumer newConsumer = new Consumer("consumername", "user", owner,
                new ConsumerType("system"));
        consumerTypeCurator.create(newConsumer.getType());
        consumerCurator.create(newConsumer);

        setupPrincipal(owner, Access.ALL);
        EventFactory eventFactory = injector.getInstance(EventFactory.class);

        // Force all events to have exact same timestamp:
        Date forcedDate = new Date();

        EventBuilder builder = eventFactory.getEventBuilder(Event.Target.RULES,
                Event.Type.DELETED);
        Event rulesDeletedEvent = builder.setOldEntity(new Rules()).buildEvent();
        rulesDeletedEvent.setTimestamp(forcedDate);

        builder = eventFactory.getEventBuilder(Event.Target.CONSUMER,
                Event.Type.CREATED);
        Event consumerCreatedEvent = builder.setNewEntity(newConsumer).buildEvent();
        consumerCreatedEvent.setTimestamp(forcedDate);

        builder = eventFactory.getEventBuilder(Event.Target.CONSUMER,
                Event.Type.MODIFIED);
        Event consumerModifiedEvent = builder.setNewEntity(newConsumer).
                setOldEntity(newConsumer).buildEvent();
        consumerModifiedEvent.setTimestamp(forcedDate);

        eventCurator.create(rulesDeletedEvent);
        eventCurator.create(consumerCreatedEvent);
        eventCurator.create(consumerModifiedEvent);

        List<Event> mostRecent = eventCurator.listMostRecent(3);
        assertEquals(3, mostRecent.size());

        // We should see this sorted by timestamp (all the same), then entity, then type:
        assertEquals(consumerCreatedEvent.getId(), mostRecent.get(0).getId());
        assertEquals(consumerModifiedEvent.getId(), mostRecent.get(1).getId());
        assertEquals(rulesDeletedEvent.getId(), mostRecent.get(2).getId());
    }

}
