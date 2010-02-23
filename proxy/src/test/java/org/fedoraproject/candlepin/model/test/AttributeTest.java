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
package org.fedoraproject.candlepin.model.test;

import static org.junit.Assert.*;

import org.fedoraproject.candlepin.model.Attribute;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;

import org.junit.Test;

import java.util.List;
import java.util.Map;

public class AttributeTest extends DatabaseTestFixture {

    @Test
    public void testLookup() {
        Attribute newAttr = new Attribute("OwesUsMoney_100", "100");
        assertFalse(newAttr.getContainsJson());
        attributeCurator.create(newAttr);

        Attribute foundAttr = attributeCurator.find(newAttr.getId());
        assertEquals(newAttr.getName(), foundAttr.getName());
        assertEquals(newAttr.getValue(), foundAttr.getValue());
    }

    @Test
    public void testJsonSimple() {
        String json = "{\"a\": \"1\", \"b\": \"2\"}";
        Attribute a = new Attribute("jsonattr", json);
        a.setContainsJson(true);
        attributeCurator.create(a);

        Attribute foundAttr = attributeCurator.find(a.getId());
        assertTrue(foundAttr.containsJson());
        Map data = foundAttr.getValueMap();
        assertEquals("1", data.get("a"));
        assertEquals("2", data.get("b"));
    }

    @Test
    public void testJsonComplex() {
        String json = "{\"a\": [\"1\"], \"b\": {\"c\": \"2\"}}";
        Attribute a = new Attribute("jsonattr", json);
        a.setContainsJson(true);
        attributeCurator.create(a);

        Attribute foundAttr = attributeCurator.find(a.getId());
        assertTrue(foundAttr.containsJson());
        Map data = foundAttr.getValueMap();
        List l = (List)data.get("a");
        assertEquals(1, l.size());
        assertEquals("1", l.get(0));

        Map subData = (Map)data.get("b");
        assertEquals("2", subData.get("c"));
    }

    @Test(expected = RuntimeException.class)
    public void testDoesNotContainJson() {
        String json = "{\"a\": [\"1\"], \"b\": {\"c\": \"2\"}}";
        Attribute a = new Attribute("jsonattr", json);
        a.getValueMap();
    }

    @Test(expected = RuntimeException.class)
    public void testBadJson() {
        String json = "{\"a\": [\"1\"], b\": {\"c\": \"2\"}}";
        Attribute a = new Attribute("jsonattr", json);
        a.setContainsJson(true);
        a.getValueMap();
    }
}
