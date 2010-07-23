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
package org.fedoraproject.candlepin.audit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import org.jboss.resteasy.plugins.providers.atom.Entry;
import org.jboss.resteasy.plugins.providers.atom.Feed;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;


/**
 * EventAdapterTest
 */
public class EventAdapterTest {

    @Test
    public void toFeed() {
        EventAdapter ea = new EventAdapterImpl();
        List<Event> events = new LinkedList<Event>();
        events.add(mockEvent(Event.Target.CONSUMER, Event.Type.CREATED));
        events.add(mockEvent(Event.Target.ENTITLEMENT, Event.Type.DELETED));
        Feed f = ea.toFeed(events);
        assertNotNull(f);
        assertNotNull(f.getEntries());
        assertFalse(f.getEntries().isEmpty());
        assertEquals(2, f.getEntries().size());
        Entry e = f.getEntries().get(0);
        assertNotNull(e);
        assertEquals("CONSUMER CREATED", e.getTitle());
        assertEquals(events.get(0).getTimestamp(), f.getUpdated());
    }

    private Event mockEvent(Event.Target tgt, Event.Type type) {
        Event e = Mockito.mock(Event.class);
        when(e.getTarget()).thenReturn(tgt);
        when(e.getType()).thenReturn(type);
        Date d = new Date(); // have to make it a var to avoid it changing
        when(e.getTimestamp()).thenReturn(d);
        return e;
    }

    @Test
    public void nullList() {
        EventAdapter ea = new EventAdapterImpl();
        Feed f = ea.toFeed(null);
        assertNotNull(f);
        assertNotNull(f.getEntries());
        assertTrue(f.getEntries().isEmpty());
    }

    @Test
    public void emptyList() {
        EventAdapter ea = new EventAdapterImpl();
        Feed f = ea.toFeed(new LinkedList<Event>());
        assertNotNull(f);
        assertNotNull(f.getEntries());
        assertTrue(f.getEntries().isEmpty());
    }
}
