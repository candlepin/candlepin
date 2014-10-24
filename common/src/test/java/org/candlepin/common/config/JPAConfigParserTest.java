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

import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

public class JPAConfigParserTest {

    // generated with katello-secure-passphrase and katell-passwd
    // we are going to try to be compatible with this if we can
    private String passphrase =
        "QwGhDv4FSnyTbFJf8O6gvWIsmQX7PZtE64ALMCXx4DcS48s5Sum7RkVcefD0vMe5";
    private String plainPassword = "testpassword";
    private String encPasswordAsStored = "$1$8dg00oV+ZhN74tvxG+kAhw==";

    @SuppressWarnings("visibilitymodifier")
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void testParseConfig() throws Exception {
        final File passphraseFile = temp.newFile("passphrase.txt");
        Writer w = new FileWriter(passphraseFile);
        w.write(passphrase);
        w.close();

        final String prefix = JPAConfigParser.JPA_CONFIG_PREFIX + ".";
        final String key1 = "hibernate.connection.password";
        final String key2 = "x";

        Map<String, String> configuration = new HashMap<String, String>() {
            {
                put(JPAConfigParser.PASSPHRASE_PROPERTY, passphraseFile.getAbsolutePath());
                put(prefix + key1, encPasswordAsStored);
                put(prefix + key2, "y");
            }
        };

        Properties results = new JPAConfigParser().parseConfig(configuration);
        assertEquals(plainPassword, results.get(key1));
        assertEquals("y", results.get(key2));
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

        Properties stripped = new JPAConfigParser()
            .stripPrefixFromConfigKeys(configuraton);

        assertEquals(2, stripped.size());
        assertTrue(stripped.containsKey(key1));
        assertTrue(stripped.containsKey(key2));
    }

    @Test
    public void testDecryptValue() {
        JPAConfigParser jpac = new JPAConfigParser();
        String res = jpac.decryptValue(encPasswordAsStored, passphrase);
        assertEquals(plainPassword, res);
    }

    @Test
    public void testDecryptValueNotEncrypted() {
        JPAConfigParser jpac = new JPAConfigParser();
        String res = null;
        res = jpac.decryptValue("password", passphrase);
        assertEquals("password", res);
    }

    @Test
    public void testEncryptedConfigKeys() {
        JPAConfigParser jpac = new JPAConfigParser();
        Set<String> ecks = jpac.getEncryptedConfigKeys();
        assertTrue(ecks.contains("hibernate.connection.password"));
    }

    @Test
    public void getStringMethods() {
        Configuration localconf = new MapConfiguration(new HashMap<String, String>() {
            {
                // NOTE: we decrypt at read time, so the in mem
                // config class is always in plain text
                put("jpa.config.hibernate.connection.password",
                    plainPassword);
            }
        });

        assertEquals(plainPassword,
            localconf.getString("jpa.config.hibernate.connection.password"));
        assertNull(localconf.getString("not.exist", null));
    }

    @Test
    public void containsKey() {
        TreeMap<String, String> testdata = new TreeMap<String, String>();
        testdata.put("jpa.config.hibernate.connection.password",
            encPasswordAsStored);
        Configuration localconf = new MapConfiguration(testdata);
        assertFalse(localconf.containsKey("notthere"));
        assertTrue(localconf
            .containsKey("jpa.config.hibernate.connection.password"));
    }
}
