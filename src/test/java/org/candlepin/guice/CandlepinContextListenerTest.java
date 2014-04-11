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
package org.candlepin.guice;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;

import org.candlepin.CandlepinCommonTestingModule;
import org.candlepin.CandlepinNonServletEnvironmentTestingModule;
import org.candlepin.audit.HornetqContextListener;
import org.candlepin.config.Config;
import org.candlepin.pinsetter.core.PinsetterContextListener;
import org.jboss.resteasy.spi.Registry;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;

/**
 * CandlepinContextListenerTest
 */
public class CandlepinContextListenerTest {
    private Config config;
    private CandlepinContextListener listener;
    private HornetqContextListener hqlistener;
    private PinsetterContextListener pinlistener;
    private ServletContextEvent evt;

    @Before
    public void init() {
        config = new Config(new HashMap<String, String>());
        hqlistener = mock(HornetqContextListener.class);
        pinlistener = mock(PinsetterContextListener.class);
        // for testing we override the getModules so we can
        // insert our mock versions of listeners to verify
        // they are getting invoked properly.
        listener = new CandlepinContextListener() {
            protected List<Module> getModules() {
                List<Module> modules = new LinkedList<Module>();
                // tried simply overriding CandlepinModule
                // but that caused it to read the local config
                // which means the test becomes non-deterministic.
                // so just load the items we need to verify the
                // functionality.
                modules.add(new CandlepinCommonTestingModule());
                modules.add(new CandlepinNonServletEnvironmentTestingModule());
                modules.add(new TestModule());
                return modules;
            }
        };
    }

    @Test
    public void contextInitialized() {
        prepareForInitialization();
        listener.contextInitialized(evt);
        verify(hqlistener).contextInitialized(any(Injector.class));
        verify(pinlistener).contextInitialized();
    }

    private void prepareForInitialization() {
        evt = mock(ServletContextEvent.class);
        ServletContext ctx = mock(ServletContext.class);
        Registry registry = mock(Registry.class);
        ResteasyProviderFactory rpfactory = mock(ResteasyProviderFactory.class);
        when(evt.getServletContext()).thenReturn(ctx);
        when(ctx.getAttribute(eq(
            Registry.class.getName()))).thenReturn(registry);
        when(ctx.getAttribute(eq(
            ResteasyProviderFactory.class.getName()))).thenReturn(rpfactory);
    }

    @Test
    public void contextDestroyed() {
        prepareForInitialization();
        listener.contextInitialized(evt);

        // we actually have to call contextInitialized before we
        // can call contextDestroyed, otherwise the listener's
        // member variables will be null. So all the above is simply
        // to setup the test to validate the destruction is doing the
        // proper thing.

        listener.contextDestroyed(evt);
        // make sure we only call it 4 times all from init code
        verify(evt, atMost(4)).getServletContext();
        verifyNoMoreInteractions(evt); // destroy shoudln't use it
        verify(hqlistener).contextDestroyed();
        verify(pinlistener).contextDestroyed();
    }

    public class TestModule extends AbstractModule {

        @Override
        protected void configure() {
            bind(PinsetterContextListener.class).toInstance(pinlistener);
            bind(HornetqContextListener.class).toInstance(hqlistener);
        }
    }
}
