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
package org.fedoraproject.candlepin.configuration;

import static org.junit.Assert.*;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.fedoraproject.candlepin.config.JPAConfiguration;
import org.junit.Test;

public class JPAConfigurationTest {

    @Test
    public void shouldStripJPAConfigKeyPrefixes() {
        final String key1 = "key1";
        final String key2 = "key1.key2";

        Map<String, String> configuraton = new HashMap<String, String>() {

            {
                put(JPAConfiguration.JPA_CONFIG_PREFIX + "." + key1, "value");
                put(JPAConfiguration.JPA_CONFIG_PREFIX + "." + key2, "value");
            }
        };

        Properties stripped = new JPAConfiguration()
                .stripPrefixFromConfigKeys(configuraton);

        assertEquals(2, stripped.size());
        assertTrue(stripped.containsKey(key1));
        assertTrue(stripped.containsKey(key2));
    }

    @Test
    public void shouldReturnPropertyElementsOfConfigurationFile()
        throws Exception {
        Properties loaded = new JPAConfiguration()
                .loadDefaultConfigurationSettings("testing", new File(
                        getClass().getResource("./persistence_for_testing.xml")
                                .toURI()));

        assertEquals(3, loaded.size());
        assertEquals("first", (String) loaded.get("first"));
        assertEquals("second", (String) loaded.get("second"));
        assertEquals("third", (String) loaded.get("third"));
    }
}
