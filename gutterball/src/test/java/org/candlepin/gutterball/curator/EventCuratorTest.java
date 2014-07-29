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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.nio.charset.Charset;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.candlepin.common.config.Configuration;
import org.candlepin.common.config.ConfigurationException;
import org.candlepin.common.config.PropertiesFileConfiguration;
import org.candlepin.gutterball.TestUtils;
import org.candlepin.gutterball.guice.GutterballServletModule;
import org.candlepin.gutterball.guice.MongoDBClientProvider;
import org.candlepin.gutterball.guice.MongoDBProvider;
import org.candlepin.gutterball.model.Event;
import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.mongodb.DB;
import com.mongodb.DBCursor;
import com.mongodb.MongoClient;

@RunWith(JukitoRunner.class)
public class EventCuratorTest {

    // TODO: Can this be broken out into a reusable JukitoModule
    //       and used with @UseModules?
    public static class EventCuratorTestModule extends JukitoModule {
        @Override
        protected void configureTest() {
            bind(Configuration.class).toInstance(readIntegrationTestConfig());
            bind(MongoClient.class).toProvider(MongoDBClientProvider.class).in(Singleton.class);
            bind(DB.class).toProvider(MongoDBProvider.class).in(Singleton.class);
        }

        // NOTE: If tests are failing due to auth errors, be sure that you have
        //       a user added to your test database that match those set in the
        //       integration_test.properties file.
        private Configuration readIntegrationTestConfig() {
            Charset utf8 = Charset.forName("UTF-8");
            InputStream defaultStream = GutterballServletModule.class
                    .getClassLoader().getResourceAsStream("integration_test.properties");

            Configuration defaults = null;
            try {
                defaults = new PropertiesFileConfiguration(defaultStream, utf8);
            }
            catch (ConfigurationException e) {
                fail("Unable to read test config: " + e.getMessage());
            }
            return defaults;
        }

    }

    @Inject
    private DB database;

    @Inject
    private EventCurator curator;

    private Event e1;

    @Before
    public void setupData() {
        database.getCollection(EventCurator.COLLECTION).drop();
        e1 = TestUtils.createEvent("CREATE");
        curator.save(e1);
    }

    @Ignore
    @Test
    public void testFindById() {
        Event found = curator.findById(e1.getString("_id"));
        assertNotNull(found);
        assertEquals(e1.get("_id"), found.get("_id"));
        assertEquals(e1.getId(), found.getId());
        assertEquals(e1.get("type"), found.getType());
    }

    @Ignore
    @Test
    public void testGetAll() {
        DBCursor results = curator.all();
        assertEquals(1, curator.count());
        Event result = (Event) results.next();
        assertEquals(e1.getId(), result.getId());
    }

}
