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

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.fedoraproject.candlepin.config.Config;
import org.junit.Test;

public class CandlepinConfigurationTest {

    @Test
    public void returnAllKeysWithAPrefixFromHead() {
        Config config = new CandlepinConfigurationForTesting(
                new HashMap<String, String>() {

                    {
                        put("a.b.a.b", "value");
                        put("a.b.c.d", "value");
                        put("a.b.e.f", "value");
                        put("a.c.a.a", "value");
                    }
                });

        Map<String, String> withPrefix = config.configurationWithPrefix("a.b");
        assertEquals(3, withPrefix.size());
        assertTrue(withPrefix.containsKey("a.b.a.b"));
        assertTrue(withPrefix.containsKey("a.b.c.d"));
        assertTrue(withPrefix.containsKey("a.b.e.f"));
    }

    @Test
    public void returnAllKeysWithAPrefixInTheMiddle() {
        Config config = new CandlepinConfigurationForTesting(
                new HashMap<String, String>() {

                    {
                        put("a.b.a.b", "value");
                        put("a.b.c.d", "value");
                        put("a.c.a.b", "value");
                        put("a.c.c.d", "value");
                        put("a.c.e.f", "value");
                        put("a.d.a.b", "value");
                    }
                });

        Map<String, String> withPrefix = config.configurationWithPrefix("a.c");
        assertEquals(3, withPrefix.size());
        assertTrue(withPrefix.containsKey("a.c.a.b"));
        assertTrue(withPrefix.containsKey("a.c.c.d"));
        assertTrue(withPrefix.containsKey("a.c.e.f"));
    }

    @Test
    public void returnAllKeysWithAPrefixFromTail() {
        Config config = new CandlepinConfigurationForTesting(
                new HashMap<String, String>() {

                    {
                        put("a.b.a.b", "value");
                        put("a.b.c.d", "value");
                        put("a.c.a.b", "value");
                        put("a.c.c.d", "value");
                        put("a.c.e.f", "value");
                    }
                });

        Map<String, String> withPrefix = config.configurationWithPrefix("a.c");
        assertEquals(3, withPrefix.size());
        assertTrue(withPrefix.containsKey("a.c.a.b"));
        assertTrue(withPrefix.containsKey("a.c.c.d"));
        assertTrue(withPrefix.containsKey("a.c.e.f"));
    }

    public static class CandlepinConfigurationForTesting extends Config {

        public CandlepinConfigurationForTesting(Map<String, String> inConfig) {
            configuration = new TreeMap<String, String>(inConfig);
        }
    }
}
