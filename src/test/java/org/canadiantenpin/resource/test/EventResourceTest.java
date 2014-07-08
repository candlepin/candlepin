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
package org.canadianTenPin.resource.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.inject.Guice;
import com.google.inject.Injector;

import org.canadianTenPin.CanadianTenPinCommonTestingModule;
import org.canadianTenPin.CanadianTenPinNonServletEnvironmentTestingModule;
import org.canadianTenPin.audit.Event;
import org.canadianTenPin.audit.EventAdapter;
import org.canadianTenPin.auth.PrincipalData;
import org.canadianTenPin.exceptions.NotFoundException;
import org.canadianTenPin.model.EventCurator;
import org.canadianTenPin.resource.EventResource;
import org.junit.Before;
import org.junit.Test;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.List;



/**
 * EventResourceTest
 */
public class EventResourceTest {
    protected Injector injector;
    private EventCurator ec;

    @Before
    public void init() {
        ec = mock(EventCurator.class);
        injector = Guice.createInjector(
            new CanadianTenPinCommonTestingModule(),
            new CanadianTenPinNonServletEnvironmentTestingModule()
        );
    }

    @Test
    public void getevent() {
        Event e = getEvent();
        when(ec.find(eq("8aba"))).thenReturn(e);
        EventResource er = new EventResource(ec, null,
            injector.getInstance(EventAdapter.class));
        assertEquals(e, er.getEvent("8aba"));
    }

    @Test(expected = NotFoundException.class)
    public void notfound() {
        when(ec.find(anyString())).thenReturn(null);
        EventResource er = new EventResource(ec,
            injector.getInstance(I18n.class),
            injector.getInstance(EventAdapter.class));
        er.getEvent("foo");
    }

    @Test
    public void listevents() {
        when(ec.listAll()).thenReturn(null);
        EventResource er = new EventResource(ec, null,
            injector.getInstance(EventAdapter.class));
        assertNull(er.listEvents());

        List<Event> events = new ArrayList<Event>();
        events.add(getEvent());
        when(ec.listAll()).thenReturn(events);
        assertEquals(events, er.listEvents());
    }

    protected Event getEvent() {
        Event e = new Event();
        e.setTarget(Event.Target.CONSUMER);
        e.setType(Event.Type.CREATED);
        e.setPrincipal(new PrincipalData());
        return e;
    }
}
