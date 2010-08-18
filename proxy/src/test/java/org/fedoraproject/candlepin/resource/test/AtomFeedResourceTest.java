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
package org.fedoraproject.candlepin.resource.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.fedoraproject.candlepin.audit.Event;
import org.fedoraproject.candlepin.audit.EventAdapter;
import org.fedoraproject.candlepin.audit.EventAdapterImpl;
import org.fedoraproject.candlepin.audit.Event.Target;
import org.fedoraproject.candlepin.audit.Event.Type;
import org.fedoraproject.candlepin.model.EventCurator;
import org.fedoraproject.candlepin.resource.AtomFeedResource;

import org.jboss.resteasy.plugins.providers.atom.Feed;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
/**
 * AtomFeedResourceTest
 */
public class AtomFeedResourceTest {

    private EventCurator ec;
    private EventAdapter ea;
    private AtomFeedResource afr;
    
    @Before
    public void setUp() {
        ec = mock(EventCurator.class);
        ea = new EventAdapterImpl();
        afr = new AtomFeedResource(ec, ea);
    }
    
    @Test
    public void getFeed() {
        List<Event> events = getEvents(10);
        when(ec.listMostRecent(eq(1000))).thenReturn(events);
        Feed f = afr.getFeed();
        assertNotNull(f);
        assertEquals(10, f.getEntries().size());
    }
    
    @Test
    public void getEmptyFeed() {
        when(ec.listMostRecent(eq(1000))).thenReturn(new ArrayList<Event>());
        Feed f = afr.getFeed();
        assertNotNull(f);
        assertTrue(f.getEntries().isEmpty());
    }
    
    private List<Event> getEvents(int count) {
        List<Event> list = new ArrayList<Event>(count);
        Target[] targets = Target.values();
        Type[] types = Type.values();
        for (int i = 0; i < count; i++) {
            Event e = mock(Event.class);
            when(e.getTarget()).thenReturn(targets[i % targets.length]);
            when(e.getType()).thenReturn(types[i % types.length]);
            when(e.getTimestamp()).thenReturn(new Date());
            list.add(e);
        }
        return list;
    }
}
