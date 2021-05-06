/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;



/**
 * Test suite for the MapView class
 */
public class MapViewTest {

    protected Map source;
    protected MapView testobj;

    @Before
    public void init() {
        this.source = new HashMap();
        this.testobj = new MapView(this.source);
    }

    @Test
    public void testClear() {
        this.source.put("k1", "v1");
        this.source.put("k2", "v2");
        this.source.put("k3", "v3");

        assertEquals(3, this.testobj.size());

        this.testobj.clear();
        assertEquals(0, this.testobj.size());
    }

    @Test
    public void testContainsKey() {
        assertFalse(this.testobj.containsKey("k1"));
        assertFalse(this.testobj.containsKey("k2"));
        assertFalse(this.testobj.containsKey("k3"));
        assertFalse(this.testobj.containsKey("v1"));
        assertFalse(this.testobj.containsKey("v2"));
        assertFalse(this.testobj.containsKey("v3"));

        this.source.put("k1", "v1");
        assertTrue(this.testobj.containsKey("k1"));
        assertFalse(this.testobj.containsKey("k2"));
        assertFalse(this.testobj.containsKey("k3"));
        assertFalse(this.testobj.containsKey("v1"));
        assertFalse(this.testobj.containsKey("v2"));
        assertFalse(this.testobj.containsKey("v3"));

        this.source.put("k2", "v2");
        assertTrue(this.testobj.containsKey("k1"));
        assertTrue(this.testobj.containsKey("k2"));
        assertFalse(this.testobj.containsKey("k3"));
        assertFalse(this.testobj.containsKey("v1"));
        assertFalse(this.testobj.containsKey("v2"));
        assertFalse(this.testobj.containsKey("v3"));

        this.source.put("k3", "v3");
        assertTrue(this.testobj.containsKey("k1"));
        assertTrue(this.testobj.containsKey("k2"));
        assertTrue(this.testobj.containsKey("k3"));
        assertFalse(this.testobj.containsKey("v1"));
        assertFalse(this.testobj.containsKey("v2"));
        assertFalse(this.testobj.containsKey("v3"));
    }

    @Test
    public void testContainsValue() {
        assertFalse(this.testobj.containsValue("k1"));
        assertFalse(this.testobj.containsValue("k2"));
        assertFalse(this.testobj.containsValue("k3"));
        assertFalse(this.testobj.containsValue("v1"));
        assertFalse(this.testobj.containsValue("v2"));
        assertFalse(this.testobj.containsValue("v3"));

        this.source.put("k1", "v1");
        assertFalse(this.testobj.containsValue("k1"));
        assertFalse(this.testobj.containsValue("k2"));
        assertFalse(this.testobj.containsValue("k3"));
        assertTrue(this.testobj.containsValue("v1"));
        assertFalse(this.testobj.containsValue("v2"));
        assertFalse(this.testobj.containsValue("v3"));

        this.source.put("k2", "v2");
        assertFalse(this.testobj.containsValue("k1"));
        assertFalse(this.testobj.containsValue("k2"));
        assertFalse(this.testobj.containsValue("k3"));
        assertTrue(this.testobj.containsValue("v1"));
        assertTrue(this.testobj.containsValue("v2"));
        assertFalse(this.testobj.containsValue("v3"));

        this.source.put("k3", "v3");
        assertFalse(this.testobj.containsValue("k1"));
        assertFalse(this.testobj.containsValue("k2"));
        assertFalse(this.testobj.containsValue("k3"));
        assertTrue(this.testobj.containsValue("v1"));
        assertTrue(this.testobj.containsValue("v2"));
        assertTrue(this.testobj.containsValue("v3"));
    }

    @Test
    public void testEntrySet() {
        assertEquals(this.source.entrySet(), this.testobj.entrySet());

        this.source.put("k1", "v1");
        assertEquals(this.source.entrySet(), this.testobj.entrySet());

        this.source.put("k2", "v2");
        assertEquals(this.source.entrySet(), this.testobj.entrySet());

        this.source.put("k3", "v3");
        assertEquals(this.source.entrySet(), this.testobj.entrySet());

        this.source.clear();
        assertEquals(this.source.entrySet(), this.testobj.entrySet());
    }

    @Test
    public void testEquals() {
        Map comp = new HashMap();

        assertTrue(this.testobj.equals(comp));

        for (int i = 0; i < 5; ++i) {
            this.source.put("k" + i, "v" + i);
            assertFalse(this.testobj.equals(comp));

            comp.put("k" + i, "value");
            assertFalse(this.testobj.equals(comp));

            comp.put("k" + i, "v" + i);
            assertTrue(this.testobj.equals(comp));
        }

        this.source.clear();
        assertFalse(this.testobj.equals(comp));

        comp.clear();
        assertTrue(this.testobj.equals(comp));
    }

    @Test
    public void testGet() {
        assertNull(this.testobj.get("k1"));
        assertNull(this.testobj.get("k2"));
        assertNull(this.testobj.get("k3"));

        this.source.put("k1", "v1");
        assertEquals("v1", this.testobj.get("k1"));
        assertNull(this.testobj.get("k2"));
        assertNull(this.testobj.get("k3"));

        this.source.put("k2", "v2");
        assertEquals("v1", this.testobj.get("k1"));
        assertEquals("v2", this.testobj.get("k2"));
        assertNull(this.testobj.get("k3"));

        this.source.put("k3", "v3");
        assertEquals("v1", this.testobj.get("k1"));
        assertEquals("v2", this.testobj.get("k2"));
        assertEquals("v3", this.testobj.get("k3"));

        this.source.put("k2", "v2-b");
        assertEquals("v1", this.testobj.get("k1"));
        assertEquals("v2-b", this.testobj.get("k2"));
        assertEquals("v3", this.testobj.get("k3"));
    }

    @Test
    public void testHashCode() {
        Map comp = new HashMap();

        assertEquals(comp.hashCode(), this.testobj.hashCode());

        for (int i = 0; i < 5; ++i) {
            this.source.put("k" + i, "v" + i);
            assertNotEquals(comp.hashCode(), this.testobj.hashCode());

            comp.put("k" + i, "value");
            assertNotEquals(comp.hashCode(), this.testobj.hashCode());

            comp.put("k" + i, "v" + i);
            assertEquals(comp.hashCode(), this.testobj.hashCode());
        }

        this.source.clear();
        assertNotEquals(comp.hashCode(), this.testobj.hashCode());

        comp.clear();
        assertEquals(comp.hashCode(), this.testobj.hashCode());
    }

    @Test
    public void testIsEmpty() {
        assertTrue(this.testobj.isEmpty());

        this.source.put("k1", "v1");
        assertFalse(this.testobj.isEmpty());

        this.source.put("k2", "v2");
        assertFalse(this.testobj.isEmpty());

        this.source.put("k3", "v3");
        assertFalse(this.testobj.isEmpty());

        this.source.remove("k1");
        assertFalse(this.testobj.isEmpty());

        this.source.clear();
        assertTrue(this.testobj.isEmpty());
    }

    @Test
    public void testKeySet() {
        assertEquals(this.source.keySet(), this.testobj.keySet());

        this.source.put("k1", "v1");
        assertEquals(this.source.keySet(), this.testobj.keySet());

        this.source.put("k2", "v2");
        assertEquals(this.source.keySet(), this.testobj.keySet());

        this.source.put("k3", "v3");
        assertEquals(this.source.keySet(), this.testobj.keySet());

        this.source.clear();
        assertEquals(this.source.keySet(), this.testobj.keySet());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testPut() {
        this.testobj.put("k1", "v1");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testPutAll() {
        Map map = new HashMap();
        map.put("k1", "v1");
        map.put("k2", "v2");
        map.put("k3", "v3");

        this.testobj.putAll(map);
    }

    @Test
    public void testRemove() {
        assertNull(this.testobj.remove("k1"));
        assertNull(this.testobj.remove("k2"));
        assertNull(this.testobj.remove("k3"));

        this.source.put("k1", "v1");
        assertEquals(1, this.testobj.size());
        assertEquals("v1", this.testobj.remove("k1"));
        assertEquals(0, this.testobj.size());
        assertNull(this.testobj.remove("k2"));
        assertEquals(0, this.testobj.size());
        assertNull(this.testobj.remove("k3"));
        assertEquals(0, this.testobj.size());

        this.source.put("k1", "v1");
        this.source.put("k2", "v2");
        assertEquals(2, this.testobj.size());
        assertEquals("v1", this.testobj.remove("k1"));
        assertEquals(1, this.testobj.size());
        assertEquals("v2", this.testobj.remove("k2"));
        assertEquals(0, this.testobj.size());
        assertNull(this.testobj.remove("k3"));
        assertEquals(0, this.testobj.size());

        this.source.put("k1", "v1");
        this.source.put("k3", "v3");
        assertEquals(2, this.testobj.size());
        assertEquals("v1", this.testobj.remove("k1"));
        assertEquals(1, this.testobj.size());
        assertNull(this.testobj.remove("k2"));
        assertEquals(1, this.testobj.size());
        assertEquals("v3", this.testobj.remove("k3"));
        assertEquals(0, this.testobj.size());

        this.source.put("k1", "v1");
        this.source.put("k2", "v2");
        this.source.put("k3", "v3");
        assertEquals(3, this.testobj.size());
        assertEquals("v1", this.testobj.remove("k1"));
        assertEquals(2, this.testobj.size());
        assertEquals("v2", this.testobj.remove("k2"));
        assertEquals(1, this.testobj.size());
        assertEquals("v3", this.testobj.remove("k3"));
        assertEquals(0, this.testobj.size());
    }

    @Test
    public void testSize() {
        assertEquals(0, this.testobj.size());

        this.source.put("k1", "v1");
        assertEquals(1, this.testobj.size());

        this.source.put("k2", "v2");
        assertEquals(2, this.testobj.size());

        this.source.put("k2", "v2-b");
        assertEquals(2, this.testobj.size());

        this.source.put("k3", "v3");
        assertEquals(3, this.testobj.size());

        this.source.remove("k1");
        assertEquals(2, this.testobj.size());

        this.source.clear();
        assertEquals(0, this.testobj.size());
    }

    @Test
    public void testValues() {
        assertEquals(this.source.values(), this.testobj.values());

        this.source.put("k1", "v1");
        assertEquals(this.source.values(), this.testobj.values());

        this.source.put("k2", "v2");
        assertEquals(this.source.values(), this.testobj.values());

        this.source.put("k3", "v3");
        assertEquals(this.source.values(), this.testobj.values());

        this.source.clear();
        assertEquals(this.source.values(), this.testobj.values());
    }

}
