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
package org.candlepin.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.junit.Before;
import org.junit.Test;

/**
 * DbBasicAuthCOnfigTest
 */
public class DbBasicAuthConfigTest {
    private String passphrase  =
        "QwGhDv4FSnyTbFJf8O6gvWIsmQX7PZtE64ALMCXx4DcS48s5Sum7RkVcefD0vMe5";
    private String plainPassword = "testpassword";
    private String encPasswordAsStored = "$1$8dg00oV+ZhN74tvxG+kAhw==";
    private Config config;

    @Before
    public void init() {
        config = new Config();
    }

    @Test
    public void testDecryptValue() {
       DbBasicAuthConfigParser dbac = new DbBasicAuthConfigParser(config);
       String res = null;
       res = dbac.decryptValue(encPasswordAsStored, passphrase);
       assertEquals(plainPassword, res);
    }

    @Test
    public void testDecryptValueNotEncrypted() {
        DbBasicAuthConfigParser dbac = new DbBasicAuthConfigParser(config);
        String res = null;
        res = dbac.decryptValue("password", passphrase);
        assertEquals("password", res);
    }

    @Test
    public void testEncryptedConfigKeys() {
        DbBasicAuthConfigParser dbac = new DbBasicAuthConfigParser(config);
        Set<String> ecks = dbac.encryptedConfigKeys();
        assertTrue(ecks.contains("database.connection.password"));
    }
}
