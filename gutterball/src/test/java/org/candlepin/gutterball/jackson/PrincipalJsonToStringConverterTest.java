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

package org.candlepin.gutterball.jackson;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class PrincipalJsonToStringConverterTest {

    @Test
    public void testConversion() {
        Map<String, Object> principalData = new HashMap<String, Object>();
        principalData.put("type", "consumer");
        principalData.put("name", "1234-12332-223");

        PrincipalJsonToStringConverter converter = new PrincipalJsonToStringConverter();
        assertEquals("consumer@1234-12332-223", converter.convert(principalData));
    }

    @Test
    public void testConversionWithMissingKey() {
        PrincipalJsonToStringConverter converter = new PrincipalJsonToStringConverter();
        assertEquals("Unknown", converter.convert(new HashMap<String, Object>()));
    }

    @Test
    public void testConversionWithNullMap() {
        PrincipalJsonToStringConverter converter = new PrincipalJsonToStringConverter();
        assertEquals("Unknown", converter.convert(null));
    }
}
