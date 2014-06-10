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
package org.candlepin.gutterball.config;

import static org.junit.Assert.*;

import org.candlepin.gutterball.config.Configuration;
import org.candlepin.gutterball.config.MapConfiguration;
import org.candlepin.gutterball.config.Configuration.TrimMode;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * MapConfigurationTest
 */
@RunWith(MockitoJUnitRunner.class)
public class MapConfigurationTest {
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
        assertEquals(Boolean.TRUE, config.getBoolean("x"));
    }

    @Test
    public void testGetMissingBoolean() {
        ex.expect(NoSuchElementException.class);
        ex.expectMessage(config.doesNotMapMessage("x"));
        config.getBoolean("x");
    }

    @Test
    public void testGetBooleanWithDefault() {
        assertEquals(Boolean.FALSE, config.getBoolean("missing", Boolean.FALSE));
    }

    @Test
    public void testGetInteger() {
        config.setProperty("x", "1");
        assertEquals(Integer.valueOf(1), config.getInteger("x"));
    }

    @Test
    public void testGetMissingInteger() {
        ex.expect(NoSuchElementException.class);
        ex.expectMessage(config.doesNotMapMessage("x"));
        config.getInteger("x");
    }

    @Test
    public void testGetIntegerWithDefault() {
        assertEquals(Long.valueOf("2"), config.getLong("missing", 2L));
    }

    @Test
    public void testGetLong() {
        config.setProperty("x", "1");
        assertEquals(Long.valueOf(1), config.getLong("x"));
    }

    @Test
    public void testGetMissingLong() {
        ex.expect(NoSuchElementException.class);
        ex.expectMessage(config.doesNotMapMessage("x"));
        config.getLong("x");
    }

    @Test
    public void testGetLongWithDefault() {
        assertEquals(Long.valueOf(1), config.getLong("x", 1L));
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
}
