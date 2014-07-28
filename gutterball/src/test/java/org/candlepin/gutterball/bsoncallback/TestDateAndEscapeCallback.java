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
package org.candlepin.gutterball.bsoncallback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.mongodb.DBObject;
import com.mongodb.util.JSON;

import org.junit.Test;

import java.util.Date;

public class TestDateAndEscapeCallback {

    private static final String VALID_BSON = "{\"id\": \"1\", \"value\": {\"id\": \"2\"}}";
    private static final String INVALID_BSON = "{\"id\": \"1\", " +
            "\"some.key\": \"$val.can.have.these\", \"$other.val\": {\"id\": \"2\"}}";
    private static final String FROM_DB = "{\"id\": \"1\", \"foo_dot_bar\": \"2\"}";

    @Test
    public void testJsonParseNoChange() {
        DBObject dbo = (DBObject) JSON.parse(VALID_BSON, new DateAndEscapeCallback());
        assertEquals(2, dbo.keySet().size());
        assertEquals("1", dbo.get("id"));
        assertTrue(dbo.get("value") instanceof DBObject);
        assertEquals("2", ((DBObject) dbo.get("value")).get("id"));
    }

    @Test
    public void testJsonParseDots() {
        DBObject dbo = (DBObject) JSON.parse(INVALID_BSON, new DateAndEscapeCallback());
        assertEquals(3, dbo.keySet().size());
        assertEquals("1", dbo.get("id"));
        // Test that we escape both object keys and var/string keys
        assertEquals("$val.can.have.these", dbo.get("some_dot_key"));
        assertTrue(dbo.get("_dolla_other_dot_val") instanceof DBObject);
        assertEquals("2", ((DBObject) dbo.get("_dolla_other_dot_val")).get("id"));
    }

    @Test
    public void testReverseEscape() {
        DBObject dbo = (DBObject) JSON.parse(FROM_DB, new DateAndEscapeCallback(false));
        assertEquals(2, dbo.keySet().size());
        assertEquals("1", dbo.get("id"));
        assertEquals("2", dbo.get("foo.bar"));
    }

    @Test
    public void testJsonParseDateNotLong() {
        Date date = new Date();
        String datedJson = "{\"id\": \"1\", \"created\": " + date.getTime() + "}";
        // If this was a different callback, 'created' would be set to the long value
        DBObject dbo = (DBObject) JSON.parse(datedJson, new DateAndEscapeCallback());
        assertEquals(2, dbo.keySet().size());
        assertEquals("1", dbo.get("id"));
        assertTrue(dbo.get("created") instanceof Date);
    }
}
