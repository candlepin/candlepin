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
package org.candlepin.common.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.candlepin.common.config.Configuration.TrimMode;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Set;

/**
 * MapConfigurationTest
 */
@RunWith(MockitoJUnitRunner.class)
public class MapConfigurationTest {

    @SuppressWarnings("checkstyle:visibilitymodifier")
    @Rule
    public ExpectedException ex = ExpectedException.none();

    private MapConfiguration config;

    @Before
    public void init() {
        config = new MapConfiguration();
    }

    @Test
    public void testSubset() {
        config.setProperty("x.1", "y");
        config.setProperty("x.2", "y");
        config.setProperty("x.3", "y");
        config.setProperty("a.1", "b");
        config.setProperty("a.2", "b");
        config.setProperty("a.3", "b");

        Configuration newConfig = config.subset("x");
        config.clear();

        Set<String> keySet = (Set<String>) newConfig.getKeys();
        assertEquals(new HashSet<String>(Arrays.asList("x.1", "x.2", "x.3")), keySet);
    }

    @Test
    public void testMerge() {
        config.setProperty("x.1", "y");
        config.setProperty("x.2", "y");

        Configuration config2 = new MapConfiguration();
        config2.setProperty("a.1", "b");
        // Add a conflicting property; config will trump config2
        config2.setProperty("x.1", "b");

        Configuration mergedConfig = config.merge(config2);
        assertNotSame(config, mergedConfig);

        Set<String> keySet = (Set<String>) mergedConfig.getKeys();
        assertEquals(new HashSet<String>(Arrays.asList("x.1", "x.2", "a.1")), keySet);

        assertEquals("y", mergedConfig.getProperty("x.1"));
    }

    @Test
    public void testIsEmpty() {
        assertTrue(config.isEmpty());
        config.setProperty("x", "y");
        assertFalse(config.isEmpty());
    }

    @Test
    public void testContainsKey() {
        assertFalse(config.containsKey("x"));
        config.setProperty("x", "y");
        assertTrue(config.containsKey("x"));
    }

    @Test
    public void testSetProperty() {
        config.setProperty("x", "y");
        assertTrue(config.containsKey("x"));
        assertEquals("y", config.getProperty("x"));
    }

    @Test
    public void testClear() {
        config.setProperty("x", "y");
        config.setProperty("a", "b");
        assertTrue(config.containsKey("x"));
        assertTrue(config.containsKey("a"));
        config.clear();
        assertFalse(config.containsKey("x"));
        assertFalse(config.containsKey("a"));
    }

    @Test
    public void testClearProperty() {
        config.setProperty("x", "y");
        config.setProperty("a", "b");
        assertTrue(config.containsKey("x"));
        assertTrue(config.containsKey("a"));
        config.clearProperty("x");
        assertFalse(config.containsKey("x"));
        assertTrue(config.containsKey("a"));
    }

    @Test
    public void testGetKeys() {
        config.setProperty("x", "y");
        config.setProperty("a", "b");
        Set<String> keySet = (Set<String>) config.getKeys();
        assertEquals(new HashSet<String>(Arrays.asList("x", "a")), keySet);
    }

    @Test
    public void testGetBoolean() {
        config.setProperty("x", "true");
        config.setProperty("bar", "false");
        config.setProperty("bar1", "1");
        config.setProperty("bar2", "yes");
        config.setProperty("no", "n");
        assertTrue(config.getBoolean("x"));
        assertTrue(config.getBoolean("bar1"));
        assertTrue(config.getBoolean("bar2"));
        assertFalse(config.getBoolean("bar"));
        //assertFalse(config.getBoolean(null));
        assertFalse(config.getBoolean("no"));
    }

    @Test
    public void testGetMissingBoolean() {
        ex.expect(NoSuchElementException.class);
        ex.expectMessage(config.doesNotMapMessage("x"));
        config.getBoolean("x");
    }

    @Test
    public void testGetBooleanWithDefault() {
        assertFalse(config.getBoolean("missing", Boolean.FALSE));
    }

    @Test
    public void testGetInteger() {
        config.setProperty("x", "1");
        assertEquals(1, config.getInt("x"));
    }

    @Test
    public void testGetMissingInteger() {
        ex.expect(NoSuchElementException.class);
        ex.expectMessage(config.doesNotMapMessage("x"));
        config.getInt("x");
    }

    @Test
    public void testGetIntWithDefault() {
        config.setProperty("threshold", "10");
        assertEquals(5, config.getInt("nothere", 5));
        assertEquals(10, config.getInt("threshold", 5));
    }

    @Test
    public void testGetLong() {
        config.setProperty("x", "1");
        assertEquals(1L, config.getLong("x"));
    }

    @Test
    public void testGetMissingLong() {
        ex.expect(NoSuchElementException.class);
        ex.expectMessage(config.doesNotMapMessage("x"));
        config.getLong("x");
    }

    @Test
    public void testGetLongWithDefault() {
        assertEquals(1L, config.getLong("x", 1L));
    }

    @Test
    public void testGetString() {
        config.setProperty("x", "y");
        assertEquals("y", config.getString("x"));
    }

    @Test
    public void testGetMissingString() {
        ex.expect(NoSuchElementException.class);
        ex.expectMessage(config.doesNotMapMessage("x"));
        config.getString("x");
    }

    @Test
    public void testGetStringWithDefault() {
        assertEquals("y", config.getString("x", "y"));
    }

    @Test
    public void testGetStringTrimsByDefault() {
        config.setProperty("x", "\t y \t");
        assertEquals("y", config.getString("x"));
    }

    @Test
    public void testGetStringTrims() {
        config.setProperty("x", "\t y \t");
        assertEquals("y", config.getString("x", null, TrimMode.TRIM));
    }

    @Test
    public void testGetStringWithNoTrim() {
        config.setProperty("x", "\t y \t");
        assertEquals("\t y \t", config.getString("x", null, TrimMode.NO_TRIM));
    }

    @Test
    public void testGetList() {
        config.setProperty("x", "a, b, c");
        assertEquals(Arrays.asList("a", "b", "c"), config.getList("x"));
    }

    @Test
    public void testGetMissingList() {
        ex.expect(NoSuchElementException.class);
        ex.expectMessage(config.doesNotMapMessage("x"));
        config.getList("x");
    }

    @Test
    public void testGetListWithDefault() {
        assertEquals(null, config.getList("x", null));
    }

    @Test
    public void testGetProperty() {
        config.setProperty("x", new HashSet<String>(Arrays.asList("y")));
        assertEquals(new HashSet<String>(Arrays.asList("y")), config.getProperty("x"));
    }

    @Test
    public void testGetMissingProperty() {
        ex.expect(NoSuchElementException.class);
        ex.expectMessage(config.doesNotMapMessage("x"));
        config.getProperty("x");
    }

    @Test
    public void testGetPropertyWithDefault() {
        assertEquals("z", config.getProperty("x", "z"));
    }

    @SuppressWarnings("serial")
    @Test
    public void namespaceWithNull() {
        Map<String, Object> defaults = new HashMap<String, Object>();
        defaults.put(null, null);

        config.setProperty("a.c.a.b", "value3");
        config.setProperty("a.c.c.d", "value4");
        config.setProperty("a.c.e.f", "value5");

        try {
            Properties withPrefix = config.getNamespaceProperties("a.c", defaults);
            assertEquals(3, withPrefix.size());
            assertTrue(withPrefix.containsKey("a.c.a.b"));
            assertTrue(withPrefix.containsKey("a.c.c.d"));
            assertTrue(withPrefix.containsKey("a.c.e.f"));
            assertEquals("value3", withPrefix.getProperty("a.c.a.b"));

            withPrefix = config.getNamespaceProperties("a.c", null);
            assertEquals(3, withPrefix.size());
            assertTrue(withPrefix.containsKey("a.c.a.b"));
            assertTrue(withPrefix.containsKey("a.c.c.d"));
            assertTrue(withPrefix.containsKey("a.c.e.f"));
            assertEquals("value3", withPrefix.getProperty("a.c.a.b"));
        }
        catch (NullPointerException npe) {
            fail("getNamespaceProperties didn't check for null");
        }

    }

    @SuppressWarnings("serial")
    @Test
    public void returnNamespacePropsWithDefaults() {
        Map<String, Object> defaults = new HashMap<String, Object>();
        defaults.put("a.c.a.b", "defaultvalue");
        defaults.put("a.c.not.e", "should have a value");
        defaults.put("not.here", "is.ignored");

        config.setProperty("a.b.a.b", "value1");
        config.setProperty("a.b.c.d", "value2");
        config.setProperty("a.c.a.b", "value3");
        config.setProperty("a.c.c.d", "value4");
        config.setProperty("a.c.e.f", "value5");

        Properties withPrefix = config.getNamespaceProperties("a.c", defaults);
        assertEquals(4, withPrefix.size());
        assertTrue(withPrefix.containsKey("a.c.a.b"));
        assertTrue(withPrefix.containsKey("a.c.c.d"));
        assertTrue(withPrefix.containsKey("a.c.e.f"));
        assertTrue(withPrefix.containsKey("a.c.not.e"));
        assertEquals("value3", withPrefix.getProperty("a.c.a.b"));
        assertEquals("should have a value", withPrefix.getProperty("a.c.not.e"));
        assertFalse(withPrefix.containsKey("not.here"));
    }

    @SuppressWarnings("serial")
    @Test
    public void returnNamespaceProperties() {
        config.setProperty("a.b.a.b", "value1");
        config.setProperty("a.b.c.d", "value2");
        config.setProperty("a.c.a.b", "value3");
        config.setProperty("a.c.c.d", "value4");
        config.setProperty("a.c.e.f", "value5");

        Properties withPrefix = config.getNamespaceProperties("a.c");
        assertEquals(3, withPrefix.size());
        assertTrue(withPrefix.containsKey("a.c.a.b"));
        assertTrue(withPrefix.containsKey("a.c.c.d"));
        assertTrue(withPrefix.containsKey("a.c.e.f"));
    }

    @SuppressWarnings("serial")
    @Test
    public void returnAllKeysWithAPrefixFromTail() {
        config.setProperty("a.b.a.b", "value");
        config.setProperty("a.b.c.d", "value");
        config.setProperty("a.c.a.b", "value");
        config.setProperty("a.c.c.d", "value");
        config.setProperty("a.c.e.f", "value");


        Map<String, Object> withPrefix = config.subsetMap("a.c");
        assertEquals(3, withPrefix.size());
        assertTrue(withPrefix.containsKey("a.c.a.b"));
        assertTrue(withPrefix.containsKey("a.c.c.d"));
        assertTrue(withPrefix.containsKey("a.c.e.f"));
    }

    @SuppressWarnings("serial")
    @Test
    public void returnAllKeysWithAPrefixInTheMiddle() {
        config.setProperty("a.b.a.b", "value");
        config.setProperty("a.b.c.d", "value");
        config.setProperty("a.c.a.b", "value");
        config.setProperty("a.c.c.d", "value");
        config.setProperty("a.c.e.f", "value");
        config.setProperty("a.d.a.b", "value");


        Map<String, Object> withPrefix = config.subsetMap("a.c");
        assertEquals(3, withPrefix.size());
        assertTrue(withPrefix.containsKey("a.c.a.b"));
        assertTrue(withPrefix.containsKey("a.c.c.d"));
        assertTrue(withPrefix.containsKey("a.c.e.f"));
    }

    @Test
    public void returnAllKeysWithAPrefixFromHead() {
        config.setProperty("a.b.a.b", "value");
        config.setProperty("a.b.c.d", "value");
        config.setProperty("a.b.e.f", "value");
        config.setProperty("a.c.a.a", "value");


        Map<String, Object> withPrefix = config.subsetMap("a.b");
        assertEquals(3, withPrefix.size());
        assertTrue(withPrefix.containsKey("a.b.a.b"));
        assertTrue(withPrefix.containsKey("a.b.c.d"));
        assertTrue(withPrefix.containsKey("a.b.e.f"));
    }

    @Test
    public void testTrimSpaces() {
        config.setProperty("good", "good");
        config.setProperty("bad", "    bad    ");
        assertEquals("good", config.getString("good"));
        assertEquals("bad", config.getString("bad"));
    }
}
