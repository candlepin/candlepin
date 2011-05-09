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

import org.fedoraproject.candlepin.CandlepinCommonTestingModule;
import org.fedoraproject.candlepin.CandlepinNonServletEnvironmentTestingModule;
import org.fedoraproject.candlepin.auth.PrincipalData;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.config.ConfigProperties;
import org.jboss.resteasy.plugins.providers.atom.Entry;
import org.jboss.resteasy.plugins.providers.atom.Feed;
import org.junit.Before;
import org.junit.Test;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.wideplay.warp.persist.PersistenceService;
import com.wideplay.warp.persist.UnitOfWork;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;


/**
 * EventAdapterTest
 */
public class EventAdapterTest {

    private Injector injector;
    private I18n i18n;
    
    @Before
    public void init() {
        injector = Guice.createInjector(
            new CandlepinCommonTestingModule(),
            new CandlepinNonServletEnvironmentTestingModule(),
            PersistenceService.usingJpa()
                .across(UnitOfWork.REQUEST)
                .buildModule()
        );
        i18n = injector.getInstance(I18n.class);
    }
    
    @Test
    public void toFeed() {
        EventAdapter ea = new EventAdapterImpl(new ConfigForTesting(), i18n);
        List<Event> events = new LinkedList<Event>();
        events.add(mockEvent(Event.Target.CONSUMER, Event.Type.CREATED));
        events.add(mockEvent(Event.Target.ENTITLEMENT, Event.Type.DELETED));
        Feed f = ea.toFeed(events, "/test/path");
        assertNotNull(f);
        assertNotNull(f.getEntries());
        assertFalse(f.getEntries().isEmpty());
        assertEquals(2, f.getEntries().size());
        Entry e = f.getEntries().get(0);
        assertNotNull(e);
        assertNotNull(e.getTitle());
        assertTrue(e.getTitle().contains("consumer"));
        assertTrue(e.getTitle().contains("created"));        
        assertEquals(events.get(0).getTimestamp(), f.getUpdated());
    }

    private Event mockEvent(Event.Target tgt, Event.Type type) {
        Event e = new Event();
        e.setTarget(tgt);
        e.setType(type);
        e.setPrincipal(new PrincipalData());
        e.setTimestamp(new Date());
        return e;
    }

    @Test
    public void nullList() {
        EventAdapter ea = new EventAdapterImpl(new ConfigForTesting(), i18n);
        Feed f = ea.toFeed(null, null);
        assertNotNull(f);
        assertNotNull(f.getEntries());
        assertTrue(f.getEntries().isEmpty());
    }

    @Test
    public void emptyList() {
        EventAdapter ea = new EventAdapterImpl(new ConfigForTesting(), i18n);
        Feed f = ea.toFeed(new LinkedList<Event>(), "/some/path");
        assertNotNull(f);
        assertNotNull(f.getEntries());
        assertTrue(f.getEntries().isEmpty());
    }
    
    private static class ConfigForTesting extends Config {
        public ConfigForTesting() {
            super(ConfigProperties.DEFAULT_PROPERTIES);
        }
    }
}
