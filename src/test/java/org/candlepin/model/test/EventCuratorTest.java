/**
 * Copyright (c) 2009 Red Hat, Inc.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

import org.candlepin.audit.Event;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.Event.Type;
import org.candlepin.auth.Access;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Owner;
import org.candlepin.test.DatabaseTestFixture;

import org.junit.Before;
import org.junit.Test;


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

}
