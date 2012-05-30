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
package org.candlepin.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import org.junit.Before;
import org.junit.Test;

public class JPAConfigParserTest {

    // generated with katello-secure-passphrase and katell-passwd
    // we are going to try to be compatible with this if we can
    private String passphrase = "QwGhDv4FSnyTbFJf8O6gvWIsmQX7PZtE64ALMCXx4DcS48s5Sum7RkVcefD0vMe5";
    private String plainPassword = "testpassword";
    private String encPasswordAsStored = "$1$8dg00oV+ZhN74tvxG+kAhw==";
    private Config config;

    @Before
    public void init() {
        config = new Config();
    }

    @SuppressWarnings("serial")
    @Test
    public void shouldStripJPAConfigKeyPrefixes() {
        final String key1 = "key1";
        final String key2 = "key1.key2";

        Map<String, String> configuraton = new HashMap<String, String>() {

            {
                put(JPAConfigParser.JPA_CONFIG_PREFIX + "." + key1, "value");
                put(JPAConfigParser.JPA_CONFIG_PREFIX + "." + key2, "value");
            }
        };

        Properties stripped = new JPAConfigParser(config)
            .stripPrefixFromConfigKeys(configuraton);

        assertEquals(2, stripped.size());
        assertTrue(stripped.containsKey(key1));
        assertTrue(stripped.containsKey(key2));
    }

    @Test
    public void testDecryptValue() {
        JPAConfigParser jpac = new JPAConfigParser(config);
        String res = null;
        res = jpac.decryptValue(encPasswordAsStored, passphrase);
        assertEquals(plainPassword, res);
    }

    @Test
    public void testDecryptValueNotEncrypted() {
        JPAConfigParser jpac = new JPAConfigParser(config);
        String res = null;
        res = jpac.decryptValue("password", passphrase);
        assertEquals("password", res);
    }

    @Test
    public void testEncryptedConfigKeys() {
        JPAConfigParser jpac = new JPAConfigParser(config);
        Set<String> ecks = jpac.encryptedConfigKeys();
        assertTrue(ecks.contains("jpa.config.hibernate.connection.password"));
    }

    @SuppressWarnings("serial")
    @Test
    public void getStringMethods() {
        Config config = new Config(new HashMap<String, String>() {

            {
                // NOTE: we decrypt at read time, so the in memomory
                // config class is always in plain text
                put("jpa.config.hibernate.connection.password",
                    plainPassword);
            }
        });

        assertEquals(plainPassword, config.getString("jpa.config.hibernate.connection.password"));
        assertNull(config.getString("not.exist"));
        assertNull(config.getString("not.exist", null));
        assertNull(config.getStringArray("not.exist"));
        assertNull(config.getStringArray(null));

    }

    @Test
    public void containsKey() {
        TreeMap<String, String> testdata = new TreeMap<String, String>();
        testdata.put("jpa.config.hibernate.connection.password",
            encPasswordAsStored);
        Config config = new Config(testdata);
        assertFalse(config.containsKey("notthere"));
        assertTrue(config
            .containsKey("jpa.config.hibernate.connection.password"));
    }
}
