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

import static org.hamcrest.MatcherAssert.*;
import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.common.config.Configuration.TrimMode;

import org.hamcrest.core.IsInstanceOf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

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
@ExtendWith(MockitoExtension.class)
public class MapConfigurationTest {

    private MapConfiguration config;

    @BeforeEach
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
        assertEquals(new HashSet<>(Arrays.asList("x.1", "x.2", "x.3")), keySet);
    }


    public void testNullInHashMapProhibited() {
        HashMap<String, String> m = new HashMap<>();
        m.put(null, "x");
        m.put("hello", "world");
        assertTrue(m.containsKey(null));

        Throwable t = assertThrows(RuntimeException.class, () -> new MapConfiguration(m));
        assertThat(t, IsInstanceOf.instanceOf(ConfigurationException.class));
    }

    @Test
    public void testStrippedSubset() {
        config.setProperty("a.b.a.b", "value");
        config.setProperty("a.b.c.d", "value");
        config.setProperty("a.c.a.b", "value");
        config.setProperty("a.d.a.b", "value");
        Configuration stripped = config.strippedSubset("a.b.");

        assertFalse(stripped.containsKey("a.b.a.b"));
        assertTrue(stripped.containsKey("a.b"));
        assertTrue(stripped.containsKey("c.d"));
        assertFalse(stripped.containsKey("a.c.a.b"));
        assertFalse(stripped.containsKey("a.d.a.b"));
    }

    @Test
    public void testMerge() {
        config.setProperty("x.1", "y");
        config.setProperty("x.2", "y");

        Configuration config2 = new MapConfiguration();
        config2.setProperty("a.1", "b");
        // Add a conflicting property; config will trump config2
        config2.setProperty("x.1", "b");

        Configuration mergedConfig = MapConfiguration.merge(config, config2);
        assertNotSame(config, mergedConfig);

        Set<String> keySet = (Set<String>) mergedConfig.getKeys();
        assertEquals(new HashSet<>(Arrays.asList("x.1", "x.2", "a.1")), keySet);

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
        assertEquals(new HashSet<>(Arrays.asList("x", "a")), keySet);
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
        assertFalse(config.getBoolean("no"));
    }

    @Test
    public void testGetMissingBoolean() {
        Throwable t = assertThrows(NoSuchElementException.class, () -> config.getBoolean("x"));
        assertEquals(config.doesNotMapMessage("x"), t.getMessage());
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
        Throwable t = assertThrows(NoSuchElementException.class, () -> config.getInt("x"));
        assertEquals(config.doesNotMapMessage("x"), t.getMessage());
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
        Throwable t = assertThrows(NoSuchElementException.class, () -> config.getLong("x"));
        assertEquals(config.doesNotMapMessage("x"), t.getMessage());
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
        Throwable t = assertThrows(NoSuchElementException.class, () -> config.getString("x"));
        assertEquals(config.doesNotMapMessage("x"), t.getMessage());
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
        Throwable t = assertThrows(NoSuchElementException.class, () -> config.getList("x"));
        assertEquals(config.doesNotMapMessage("x"), t.getMessage());
    }

    @Test
    public void testGetListWithDefault() {
        assertEquals(null, config.getList("x", null));
    }

    @Test
    public void testGetMissingProperty() {
        Throwable t = assertThrows(NoSuchElementException.class, () -> config.getProperty("x"));
        assertEquals(config.doesNotMapMessage("x"), t.getMessage());
    }

    @Test
    public void testGetSet() {
        config.setProperty("x", "  a  , b  ,  c  ");
        Set<String> expected = new HashSet<>(Arrays.asList("a", "b", "c"));
        assertEquals(expected, config.getSet("x"));
    }

    @Test
    public void testGetPropertyWithDefault() {
        assertEquals("z", config.getProperty("x", "z"));
    }

    @SuppressWarnings("serial")
    @Test
    public void toPropertiesWithDefaults() {
        Map<String, String> defaults = new HashMap<>();
        defaults.put("a", "defaultvalue");
        defaults.put("z", "should have a value");

        config.setProperty("a", "value1");
        config.setProperty("b", "value2");
        config.setProperty("c", "value3");
        config.setProperty("d", "value4");

        Properties p = config.toProperties(defaults);
        assertEquals(5, p.size());
        assertTrue(p.containsKey("a"));
        assertTrue(p.containsKey("b"));
        assertTrue(p.containsKey("c"));
        assertTrue(p.containsKey("d"));
        assertEquals("value1", p.getProperty("a"));
        assertEquals("should have a value", p.getProperty("z"));
    }

    @SuppressWarnings("serial")
    @Test
    public void returnAllKeysWithAPrefixFromTail() {
        config.setProperty("a.b.a.b", "value");
        config.setProperty("a.b.c.d", "value");
        config.setProperty("a.c.a.b", "value");
        config.setProperty("a.c.c.d", "value");
        config.setProperty("a.c.e.f", "value");


        Map<String, String> withPrefix = config.subsetMap("a.c");
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


        Map<String, String> withPrefix = config.subsetMap("a.c");
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


        Map<String, String> withPrefix = config.subsetMap("a.b");
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
