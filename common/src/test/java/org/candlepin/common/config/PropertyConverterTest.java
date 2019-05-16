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

import static org.candlepin.common.config.PropertyConverter.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

public class PropertyConverterTest {
    private Object BAD_COMPARISON = new Object();
    private String expectedMessage(Object value, Class<?> clazz) {
        return String.format(PropertyConverter.ERROR_MESSAGE, value, clazz.getName());
    }

    @Test
    public void testToBoolean() {
        assertTrue(toBoolean("true"));
        assertTrue(toBoolean("yes"));
        assertTrue(toBoolean("on"));

        assertFalse(toBoolean("false"));
        assertFalse(toBoolean("no"));
        assertFalse(toBoolean("off"));

        assertTrue(toBoolean("1"));
        assertTrue(toBoolean("y"));
        assertFalse(toBoolean("n"));
    }

    @Test
    public void testFailToBoolean() {
        Throwable t = assertThrows(ConversionException.class, () -> toBoolean(BAD_COMPARISON));
        assertEquals(expectedMessage(BAD_COMPARISON, Boolean.class), t.getMessage());
    }

    @Test
    public void testToInteger() {
        assertEquals(Integer.valueOf(1), toInteger("1"));
        assertEquals(Integer.valueOf(1), toInteger(1L));
        assertEquals(Integer.valueOf(255), toInteger("0xFF"));
        assertEquals(Integer.valueOf(1), toInteger("0b1"));
    }

    @Test
    public void testFailToInteger() {
        Throwable t = assertThrows(ConversionException.class, () -> toInteger(BAD_COMPARISON));
        assertEquals(expectedMessage(BAD_COMPARISON, Integer.class), t.getMessage());
    }

    @Test
    public void testToLong() {
        assertEquals(Long.valueOf(1), toLong("1"));
        assertEquals(Long.valueOf(1), toLong(1));
        assertEquals(Long.valueOf(1), toLong(Short.valueOf("1")));
        assertEquals(Long.valueOf(255), toLong("0xFF"));
        assertEquals(Long.valueOf(1), toLong("0b1"));
    }

    @Test
    public void testFailToLong() {
        Throwable t = assertThrows(ConversionException.class, () -> toLong(BAD_COMPARISON));
        assertEquals(expectedMessage(BAD_COMPARISON, Long.class), t.getMessage());
    }

    @Test
    public void testToList() {
        assertEquals(Arrays.asList("Hello", "world", "how", "are you?"),
            toList("Hello, world,how,  are you?"));
    }

    @Test
    public void testFailToList() {
        Throwable t = assertThrows(ConversionException.class, () -> toList(BAD_COMPARISON));
        assertEquals(expectedMessage(BAD_COMPARISON, List.class), t.getMessage());
    }

    @Test
    public void testToBigInteger() {
        assertEquals(new BigInteger("1") , toBigInteger("1"));
        assertEquals(new BigInteger("1") , toBigInteger(1));
        assertEquals(new BigInteger("255"), toBigInteger("0xFF"));
        assertEquals(new BigInteger("1"), toBigInteger("0b1"));
    }

    @Test
    public void testFailToBigInteger() {
        Throwable t = assertThrows(ConversionException.class, () -> toBigInteger(BAD_COMPARISON));
        assertEquals(expectedMessage(BAD_COMPARISON, BigInteger.class), t.getMessage());
    }
}
