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
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

import org.candlepin.gutterball.mongodb.MongoConnection;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;

import org.mockito.runners.MockitoJUnitRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.util.LinkedList;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class MongoDBCuratorTest {

    @Mock
    private MongoConnection conn;
    @Mock
    private DB db;
    @Mock
    private DBCollection collection;

    private DBObject obj1;
    private DBObject obj2;
    private List<DBObject> objlist;
    private MongoDBCurator<BasicDBObject> curator;

    @Before
    public void setUp() {
        when(conn.getDB()).thenReturn(db);
        when(db.getCollection(any(String.class))).thenReturn(collection);

        objlist = new LinkedList<DBObject>();

        obj1 = new BasicDBObject("key", "value1");
        obj1.put("otherkey", "otherval1");
        obj1.put("objkey", new BasicDBObject("innerkey", "innerval1"));
        obj1.put("objkey2", new BasicDBObject("innerobjkey", new BasicDBObject("3rd_level_key", "val1")));
        objlist.add(obj1);

        obj2 = new BasicDBObject("key", "value2");
        obj2.put("otherkey", "otherval2");
        obj2.put("objkey", new BasicDBObject("innerkey", "innerval2"));
        obj2.put("obj2key", new BasicDBObject("obj2subkey", "only_result"));
        obj2.put("objkey2", new BasicDBObject("innerobjkey", new BasicDBObject("3rd_level_key", "val2")));
        objlist.add(obj2);

        curator = new TestingMongoDBCurator(conn);
    }

    @Test
    public void testGetValuesByKeyFirstLevel() {
        List<String> results = curator.getValuesByKey("key", objlist);
        assertEquals(2, results.size());
        assertTrue(results.contains("value1"));
        assertTrue(results.contains("value2"));
    }

    @Test
    public void testGetValuesByKeyFirstLevelNotExists() {
        List<String> results = curator.getValuesByKey("not_a_key", objlist);
        assertEquals(0, results.size());
    }

    @Test
    public void testGetValuesByKeySecondLevel() {
        List<String> results = curator.getValuesByKey("objkey.innerkey", objlist);
        assertEquals(2, results.size());
        assertTrue(results.contains("innerval1"));
        assertTrue(results.contains("innerval2"));
    }

    @Test
    public void testGetValuesByKeyThirdLevel() {
        List<String> results = curator.getValuesByKey("objkey2.innerobjkey.3rd_level_key", objlist);
        assertEquals(2, results.size());
        assertTrue(results.contains("val1"));
        assertTrue(results.contains("val2"));
    }

    @Test
    public void testGetValuesByKeyThirdLevelMiddleDoesntExist() {
        List<String> results = curator.getValuesByKey("objkey2.not_a_key.3rd_level_key", objlist);
        assertEquals(0, results.size());
    }

    @Test
    public void testGetValuesByKeyThirdLevelLastDoesntExist() {
        List<String> results = curator.getValuesByKey("objkey2.innerobjkey.not_a_key", objlist);
        assertEquals(0, results.size());
    }

    @Test
    public void testGetValuesByKeyOnlyExistsInSome() {
        List<String> results = curator.getValuesByKey("obj2key.obj2subkey", objlist);
        assertEquals(1, results.size());
        assertTrue(results.contains("only_result"));
    }

    private class TestingMongoDBCurator extends MongoDBCurator<BasicDBObject> {

        public TestingMongoDBCurator(MongoConnection connection) {
            super(BasicDBObject.class, connection);
        }

        @Override
        public String getCollectionName() {
            return "test";
        }
    }
}
