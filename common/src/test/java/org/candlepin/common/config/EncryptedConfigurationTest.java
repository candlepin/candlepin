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

import static org.candlepin.common.config.ConfigurationPrefixes.JPA_CONFIG_PREFIX;
import static org.junit.Assert.assertEquals;

import org.hamcrest.core.IsInstanceOf;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.Writer;
import java.util.Properties;

import javax.crypto.BadPaddingException;

public class EncryptedConfigurationTest {
    // generated with katello-secure-passphrase and katell-passwd
    // we are going to try to be compatible with this if we can
    private String passphrase = "QwGhDv4FSnyTbFJf8O6gvWIsmQX7PZtE64ALMCXx4DcS48s5Sum7RkVcefD0vMe5";
    private String plainPassword = "testpassword";
    private String encPasswordAsStored = "$1$8dg00oV+ZhN74tvxG+kAhw==";

    @SuppressWarnings("visibilitymodifier")
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @SuppressWarnings("checkstyle:visibilitymodifier")
    @Rule
    public ExpectedException ex = ExpectedException.none();

    private Properties props;
    private final String key1 = JPA_CONFIG_PREFIX + "hibernate.connection.password";
    private final String key2 = JPA_CONFIG_PREFIX + "x";

    @Before
    public void init() {
        props = new Properties();
        props.setProperty(key1, encPasswordAsStored);
        props.setProperty(key2, "y");
    }

    @Test
    public void testDecrypt() throws Exception {
        File passphraseFile = temp.newFile("passphrase.txt");
        Writer w = new FileWriter(passphraseFile);
        w.write(passphrase);
        w.close();

        props.setProperty("passphrase_file", passphraseFile.getAbsolutePath());

        EncryptedConfiguration c = new EncryptedConfiguration(props);
        c.use("passphrase_file").toDecrypt(key1, key2);
        assertEquals(plainPassword, c.getString(key1));
        assertEquals("y", c.getString(key2));
    }

    @Test
    public void testDecryptWithEmptyPassphraseFile() throws Exception {
        props.setProperty("passphrase_file", "");

        EncryptedConfiguration c = new EncryptedConfiguration(props);
        c.use("passphrase_file").toDecrypt(key1, key2);
        assertEquals(encPasswordAsStored, c.getString(key1));
        assertEquals("y", c.getString(key2));
    }

    @Test
    public void testDecryptWithNoPassphraseFile() throws Exception {
        EncryptedConfiguration c = new EncryptedConfiguration(props);
        c.use("passphrase_file").toDecrypt(key1, key2);
        assertEquals(encPasswordAsStored, c.getString(key1));
        assertEquals("y", c.getString(key2));
    }

    @Test
    public void testDecryptWithBadPassphraseFile() throws Exception {
        ex.expect(ConfigurationException.class);
        ex.expectCause(IsInstanceOf.<Throwable>instanceOf(FileNotFoundException.class));

        props.setProperty("passphrase_file", "/does/not/exist");

        EncryptedConfiguration c = new EncryptedConfiguration(props);
        c.use("passphrase_file").toDecrypt(key1, key2);
    }

    @Test
    public void testDecryptWithWrongPassphrase() throws Exception {
        ex.expect(ConfigurationException.class);
        ex.expectCause(IsInstanceOf.<Throwable>instanceOf(BadPaddingException.class));

        File passphraseFile = temp.newFile("passphrase.txt");
        Writer w = new FileWriter(passphraseFile);
        w.write("wrong");
        w.close();

        props.setProperty("passphrase_file", passphraseFile.getAbsolutePath());

        EncryptedConfiguration c = new EncryptedConfiguration(props);
        c.use("passphrase_file").toDecrypt(key1, key2);
    }

    @Test
    public void testUnusualPassword() throws Exception {
        String expected = "Hello\nWorld\n";
        File passphraseFile = temp.newFile("passphrase.txt");
        Writer w = new FileWriter(passphraseFile);
        w.write(expected);
        w.close();

        props.setProperty("passphrase_file", passphraseFile.getAbsolutePath());
        EncryptedConfiguration c = new EncryptedConfiguration(props);
        assertEquals(expected, c.readPassphrase("passphrase_file"));
    }
}
