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

import org.candlepin.gutterball.EmbeddedMongoRule;
import org.candlepin.gutterball.MongoCollectionCleanupRule;
import org.candlepin.gutterball.TestUtils;
import org.candlepin.gutterball.model.Event;
import org.candlepin.gutterball.mongodb.MongoConnection;

import com.mongodb.DBCursor;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class EventCuratorTest {

    @SuppressWarnings("checkstyle:visibilitymodifier")
    @ClassRule
    public static EmbeddedMongoRule serverRule = new EmbeddedMongoRule();

    @SuppressWarnings("checkstyle:visibilitymodifier")
    @Rule
    public MongoCollectionCleanupRule mongoTest = new MongoCollectionCleanupRule(serverRule,
            EventCurator.COLLECTION);

    private MongoConnection mongo;
    private EventCurator curator;
    private Event e1;

    @Before
    public void setupData() {
        mongo = serverRule.getMongoConnection();
        curator = new EventCurator(mongo);

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
