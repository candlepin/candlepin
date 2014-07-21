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

import javax.inject.Inject;
import javax.inject.Singleton;

import org.candlepin.gutterball.TestUtils;
import org.candlepin.gutterball.config.Configuration;
import org.candlepin.gutterball.config.MapConfiguration;
import org.candlepin.gutterball.guice.MongoDBClientProvider;
import org.candlepin.gutterball.guice.MongoDBProvider;
import org.candlepin.gutterball.model.Event;
import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.mongodb.DB;
import com.mongodb.DBCursor;
import com.mongodb.MongoClient;

@RunWith(JukitoRunner.class)
public class EventCuratorTest {

    public static class EventCuratorTestModule extends JukitoModule {
        @Override
        protected void configureTest() {
            Configuration c = new MapConfiguration();
            c.setProperty("gutterball.mongodb.database", "test");
            bind(Configuration.class).toInstance(c);
            bind(MongoClient.class).toProvider(MongoDBClientProvider.class).in(Singleton.class);
            bind(DB.class).toProvider(MongoDBProvider.class).in(Singleton.class);
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

    @Test
    public void testFindById() {
        Event found = curator.findById(e1.getString("_id"));
        assertNotNull(found);
        assertEquals(e1.get("_id"), found.get("_id"));
        assertEquals(e1.getId(), found.getId());
        assertEquals(e1.get("type"), found.getType());
    }

    @Test
    public void testGetAll() {
        DBCursor results = curator.all();
        assertEquals(1, curator.count());
        Event result = (Event) results.next();
        assertEquals(e1.getId(), result.getId());
    }

}
