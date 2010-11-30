/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.config;

import static org.junit.Assert.assertEquals;

import java.util.TreeMap;

import org.junit.Test;

/**
 * ConfigTest
 */
public class ConfigTest {
    
    @Test
    public void testTrimSpaces() {
        Config config = new Config();
        TreeMap<String, String> testData = new TreeMap<String, String>();
        testData.put("good", "good");
        testData.put("bad", "    bad    ");
        testData = config.trimSpaces(testData);
        assertEquals("good", testData.get("good"));
        assertEquals("bad", testData.get("bad"));        
    }
}
