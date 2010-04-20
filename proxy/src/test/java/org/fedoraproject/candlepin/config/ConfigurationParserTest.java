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

import org.junit.Test;

import java.util.HashMap;
import java.util.Properties;
import static org.junit.Assert.*;


/**
 * ConfigurationParserTest
 */
public class ConfigurationParserTest {

    @SuppressWarnings("serial")
    @Test
    public void testParserConfig() {
        ConfigurationParser cp = new ConfigurationParser() {
            public String getPrefix() {
                return "a.b";
            }
        };
        
        Properties props = cp.parseConfig(new HashMap<String, String>() {
            {
                put("a.b.a.b", "value");
                put("a.b.c.d", "value");
                put("a.c.a.b", "value");
                put("a.d.a.b", "value");
            }
        });
        
        assertNotNull(cp);
        assertEquals("a.b", cp.getPrefix());
        assertNotNull(props);
        assertFalse(props.containsKey("a.b.a.b"));
        assertTrue(props.containsKey("a.b"));
        assertTrue(props.containsKey("c.d"));
        assertTrue(props.containsKey("a.c.a.b"));
        assertTrue(props.containsKey("a.d.a.b"));
    }
}
