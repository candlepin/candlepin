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
package org.candlepin.gutterball.servlet;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.candlepin.common.config.Configuration;
import org.candlepin.common.config.ConfigurationException;
import org.candlepin.common.config.MapConfiguration;
import org.candlepin.gutterball.config.ConfigProperties;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;

import org.jboss.resteasy.spi.Registry;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jukito.JukitoRunner;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;

/**
 * GutterballContextListenerTest
 * @version $Rev$
 */
@RunWith(JukitoRunner.class)
public class GutterballContextListenerTest {

    private GutterballContextListener listener;

    @Before
    public void init() {
        listener = new GutterballContextListener() {
            protected Configuration readConfiguration(ServletContext context)
                throws ConfigurationException {

                return new MapConfiguration(
                    new HashMap<String, String>() {

                        private static final long serialVersionUID = 1L;
                        {
                            put(ConfigProperties.AMQP_KEYSTORE, "value");
                            put(ConfigProperties.AMQP_CONNECT_STRING, "value");
                            put(ConfigProperties.AMQP_CONNECT_STRING,
                                    "amqp://guest:guest@localhost/test");
                            put(ConfigProperties.AMQP_KEYSTORE_PASSWORD, "password");
                            put(ConfigProperties.AMQP_TRUSTSTORE,
                                    "/etc/gutterball/certs/amqp/gutterball.truststore");
                            put(ConfigProperties.AMQP_TRUSTSTORE_PASSWORD, "password");

                            // Mongo defaults
                            //this.put(ConfigProperties.MONGODB_HOST, "localhost");
                            //this.put(ConfigProperties.MONGODB_PORT, "27017");
                            //this.put(ConfigProperties.MONGODB_DATABASE, "gutterball");
                        }
                    });
            }
        };
    }

    @Test
    public void testGetModules(ServletContext ctx) {
        List<Module> modules = listener.getModules(ctx);
        verifyZeroInteractions(ctx);
        assertNotNull(modules);
        assertTrue(modules.size() > 1);
    }

    @Test
    public void processInjector(ServletContext ctx, Injector inj) {
        listener.processInjector(ctx, inj);
        verify(ctx).getAttribute(eq(Registry.class.getName()));
        verify(ctx).getAttribute(eq(ResteasyProviderFactory.class.getName()));
        // TODO: fix text
    }

    @Test
    public void getStage(ServletContext ctx) {
        assertEquals(Stage.PRODUCTION, listener.getStage(ctx));
        verifyZeroInteractions(ctx);
    }

    @Test
    public void readConfiguration(ServletContext ctx) throws Exception {
        Configuration config = listener.readConfiguration(ctx);
        verifyZeroInteractions(ctx);

    }

    @Test
    @Ignore
    public void initialized(ServletContextEvent sce, ServletContext ctx) {
        when(sce.getServletContext()).thenReturn(ctx);
        listener.contextInitialized(sce);
    }

    @Test
    public void getInjector() {
        List<Module> modules = new ArrayList<Module>();
        modules.add(new ContextListenerTestModule());
        Injector inj = listener.getInjector(Stage.DEVELOPMENT, modules);
        assertNotNull(inj);
        assertNotNull(inj.getInstance(Dummy.class));
    }

    public class ContextListenerTestModule extends AbstractModule {
        @Override
        protected void configure() {
            bind(Dummy.class);
        }
    }

    public static class Dummy {
        public Dummy() {
            // do nothing
        }
    }
}
