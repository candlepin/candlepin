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
package org.candlepin.jackson;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

public class StringTrimmingConverterTest {
    private StringTrimmingConverter converter;

    @Before
    public void setUp() {
        converter = new StringTrimmingConverter();
    }

    @Test
    public void testRemovesNullBytes() {
        String in = "Hello world\0";
        String out = converter.convert(in);
        assertEquals("Hello world", out);
    }

    @Test
    public void testLeavesOtherStringsAlone() {
        String in = "Hello world";
        String out = converter.convert(in);
        assertEquals(in, out);
    }

    @Test
    public void testStripsOtherWhiteSpace() {
        String in = "Hello world   ";
        String out = converter.convert(in);
        assertEquals("Hello world", out);
    }
}
