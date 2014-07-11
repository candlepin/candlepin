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

import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;


public class PropertyConverterTest {
    @SuppressWarnings("checkstyle:visibilitymodifier")
    @Rule
    public ExpectedException ex = ExpectedException.none();

    private Object BAD_COMPARISON = StandardCharsets.UTF_8;
    private String expectedMessage(Object value, Class<?> clazz) {
        return String.format(PropertyConverter.ERROR_MESSAGE, value, clazz.getName());
    }

    @Test
    public void testToBoolean() {
        assertEquals(Boolean.TRUE, toBoolean("true"));
        assertEquals(Boolean.TRUE, toBoolean("yes"));
        assertEquals(Boolean.TRUE, toBoolean("on"));

        assertEquals(Boolean.FALSE, toBoolean("false"));
        assertEquals(Boolean.FALSE, toBoolean("no"));
        assertEquals(Boolean.FALSE, toBoolean("off"));
    }

    @Test
    public void testFailToBoolean() {
        ex.expect(ConversionException.class);
        ex.expectMessage(expectedMessage(BAD_COMPARISON, Boolean.class));
        toBoolean(BAD_COMPARISON);
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
        ex.expect(ConversionException.class);
        ex.expectMessage(expectedMessage(BAD_COMPARISON, Integer.class));
        toInteger(BAD_COMPARISON);
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
        ex.expect(ConversionException.class);
        ex.expectMessage(expectedMessage(BAD_COMPARISON, Long.class));
        toLong(BAD_COMPARISON);
    }

    @Test
    public void testToList() {
        assertEquals(Arrays.asList("Hello", "world", "how", "are you?"),
                toList("Hello, world,how,  are you?"));
    }

    @Test
    public void testFailToList() {
        ex.expect(ConversionException.class);
        ex.expectMessage(expectedMessage(BAD_COMPARISON, List.class));
        toList(BAD_COMPARISON);
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
        ex.expect(ConversionException.class);
        ex.expectMessage(expectedMessage(BAD_COMPARISON, BigInteger.class));
        toBigInteger(BAD_COMPARISON);
    }
}
