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
package org.candlepin.resource.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.CandlepinCommonTestingModule;
import org.candlepin.CandlepinNonServletEnvironmentTestingModule;
import org.candlepin.audit.Event;
import org.candlepin.audit.EventAdapter;
import org.candlepin.audit.EventAdapterImpl;
import org.candlepin.audit.Event.Target;
import org.candlepin.audit.Event.Type;
import org.candlepin.auth.PrincipalData;
import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.EventCurator;
import org.candlepin.resource.AtomFeedResource;

import org.jboss.resteasy.plugins.providers.atom.Feed;
import org.junit.Before;
import org.junit.Test;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.wideplay.warp.persist.PersistenceService;
import com.wideplay.warp.persist.UnitOfWork;

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
    private Injector injector;
    private I18n i18n;

    @Before
    public void setUp() {
        injector = Guice.createInjector(
            new CandlepinCommonTestingModule(),
            new CandlepinNonServletEnvironmentTestingModule(),
            PersistenceService.usingJpa()
                .across(UnitOfWork.REQUEST)
                .buildModule()
        );
        i18n = injector.getInstance(I18n.class);
        ec = mock(EventCurator.class);
        ea = new EventAdapterImpl(new ConfigForTesting(), i18n);
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
            Event e = new Event();
            e.setTarget(targets[i % targets.length]);
            e.setType(types[i % types.length]);
            e.setTimestamp(new Date());
            e.setPrincipal(new PrincipalData());
            list.add(e);
        }
        return list;
    }

    private static class ConfigForTesting extends Config {
        public ConfigForTesting() {
            super(ConfigProperties.DEFAULT_PROPERTIES);
        }
    }
}
