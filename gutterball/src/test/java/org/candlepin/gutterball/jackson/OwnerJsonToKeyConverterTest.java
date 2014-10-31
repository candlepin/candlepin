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

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class OwnerJsonToKeyConverterTest {

    @Test
    public void testConversion() {
        Map<String, Object> ownerData = new HashMap<String, Object>();
        ownerData.put("key", "My Key");

        OwnerJsonToKeyConverter converter = new OwnerJsonToKeyConverter();
        assertEquals("My Key", converter.convert(ownerData));
    }

    @Test
    public void testConversionWithMissingKey() {
        OwnerJsonToKeyConverter converter = new OwnerJsonToKeyConverter();
        assertEquals("Unknown", converter.convert(new HashMap<String, Object>()));
    }

    @Test
    public void testConversionWithNullMap() {
        OwnerJsonToKeyConverter converter = new OwnerJsonToKeyConverter();
        assertEquals("Unknown", converter.convert(null));
    }
}
