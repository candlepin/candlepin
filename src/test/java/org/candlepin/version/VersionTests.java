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
package org.candlepin.version;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * VersionTests
 */
public class VersionTests {

    @Test
    public void testDefaultsOnNonNumericsInVersion() {
        String versionString = "x.x.x";
        Version v = new Version(versionString);
        assertEquals("0.0.0", v.toString());
    }

    @Test
    public void testToString() {
        assertEquals("1.2.3", new Version("1.2.3").toString());
        assertEquals("1.2.0", new Version("1.2").toString());
        assertEquals("1.0.0", new Version("1").toString());
    }

    @Test
    public void ensureCompareToLessThan() {
        Version v1 = new Version("3.2.0");
        assertEquals(-1, v1.compareTo(new Version("4.2.3")));
        assertEquals(-1, v1.compareTo(new Version("3.3.0")));
        assertEquals(-1, v1.compareTo(new Version("3.2.1")));
    }

    @Test
    public void ensureCompareToEquality() {
        Version v1 = new Version("3.2.0");
        assertEquals(0, v1.compareTo(new Version("3.2.0")));
        assertEquals(0, v1.compareTo(new Version("3.2")));
    }

    @Test
    public void ensureCompareToGreaterThan() {
        Version v1 = new Version("3.2.5");
        assertEquals(1, v1.compareTo(new Version("2.2.5")));
        assertEquals(1, v1.compareTo(new Version("3.1.5")));
        assertEquals(1, v1.compareTo(new Version("3.2.4")));
    }

    @Test
    public void ensureEquality() {
        Version v1 = new Version("1.2.3");
        assertTrue(v1.equals(new Version("1.2.3")));
    }

    @Test
    public void ensureNotEqualWhenMajorDifferent() {
        Version v1 = new Version("1.0.0");
        assertFalse(v1.equals(new Version("2.0.0")));
        assertFalse(v1.equals(new Version("0.0.0")));
    }

    @Test
    public void ensureNotEqualWhenMinorDifferent() {
        Version v1 = new Version("0.1.0");
        assertFalse(v1.equals(new Version("0.0.0")));
        assertFalse(v1.equals(new Version("0.2.0")));
    }

    @Test
    public void ensureNotEqualWhenRevisionDifferent() {
        Version v1 = new Version("0.0.1");
        assertFalse(v1.equals(new Version("0.0.0")));
        assertFalse(v1.equals(new Version("0.0.2")));
    }

    @Test
    public void ensureNotEqualWhenArgNotInstanceOfVersion() {
        Version v1 = new Version("1.2.3");
        assertFalse(v1.equals("1.2.3"));
    }

}
