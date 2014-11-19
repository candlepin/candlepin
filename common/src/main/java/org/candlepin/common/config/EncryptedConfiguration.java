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

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Properties;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * EncryptedValueConfigurationParser
 */
public class EncryptedConfiguration extends PropertiesFileConfiguration {
    private static Logger log = LoggerFactory.getLogger(EncryptedConfiguration.class);

    private String passphrase = null;

    public EncryptedConfiguration() {
        super();
    }

    public EncryptedConfiguration(String fileName) throws ConfigurationException {
        super(fileName);
    }

    public EncryptedConfiguration(String fileName, Charset encoding)
        throws ConfigurationException {
        super(fileName, encoding);
    }

    public EncryptedConfiguration(File file) throws ConfigurationException {
        super(file);
    }

    public EncryptedConfiguration(File file, Charset encoding)
        throws ConfigurationException {
        super(file, encoding);
    }

    public EncryptedConfiguration(InputStream inStream) throws ConfigurationException {
        super(inStream);
    }

    public EncryptedConfiguration(InputStream inStream, Charset encoding)
        throws ConfigurationException {
        super(inStream, encoding);
    }

    public EncryptedConfiguration(Properties properties) {
        super(properties);
    }

    public EncryptedConfiguration use(String passphraseProperty) throws ConfigurationException {
        passphrase = readPassphrase(passphraseProperty);
        return this;
    }

    public void toDecrypt(String ... encryptedProperties) throws ConfigurationException {
        for (String p : encryptedProperties) {
            toDecrypt(p);
        }
    }

    public void toDecrypt(String property) throws ConfigurationException {
        if (passphrase == null) {
            log.debug("Passphrase is null.  Skipping decrypt.");
            return;
        }

        if (!containsKey(property)) {
            log.debug("Can't decrypt missing property: {}", property);
            return;
        }

        String toDecrypt = getString(property);
        if (!toDecrypt.startsWith("$1$")) {
            // this is not an encrypted password, just return it
            log.debug("Value for {} is not an encrypted string", property);
            return;
        }

        // remove the magic string
        toDecrypt = toDecrypt.substring(3);

        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");

            // NOTE: we are creating a 64byte digest here,
            // but only the first 16 bytes are used as the iv
            String ivString = passphrase + passphrase;
            String iv = DigestUtils.sha256Hex(ivString);
            String passphraseDigest = DigestUtils.sha256Hex(passphrase);

            // FIXME: katello-password creates a 64 byte key, but somewhere
            // it gets truncated to 32 bytes, so we have to do that here.
            SecretKeySpec spec = new SecretKeySpec(Arrays.copyOfRange(
                passphraseDigest.getBytes(), 0, 32), "AES");

            cipher.init(Cipher.DECRYPT_MODE, spec,
                new IvParameterSpec(iv.getBytes(), 0, 16));

            // NOTE: the encrypted password is stored hex base64
            byte[] b64bytes = Base64.decodeBase64(toDecrypt);
            String decrypted = new String(cipher.doFinal(b64bytes));
            setProperty(property, decrypted);
        }
        catch (Exception e) {
            log.error("Failure trying to decrypt value of {}", property, e);
            throw new ConfigurationException(e);
        }

    }

    protected String readPassphrase(String passphraseProperty) throws ConfigurationException {
        if (!containsKey(passphraseProperty)) {
            log.info("No secret file provided.");
            return null;
        }

        String secretFile = getString(passphraseProperty);

        if (StringUtils.isEmpty(secretFile)) {
            log.warn("{} is present in the configuration but unset", passphraseProperty);
            return null;
        }

        log.debug("reading secret file: {}", secretFile);

        try {
            /* XXX Maybe it'd be better to use the charset the caller specifies during
             * construction?  But just because the config is in that charset doesn't mean
             * the password file is.  Stick with system default for now. */
            InputStream bs = new FileInputStream(secretFile);
            return IOUtils.toString(bs, Charset.defaultCharset().name());
        }
        catch (Exception e) {
            String msg = String.format("Could not read: %s", secretFile);
            log.error(msg);
            throw new ConfigurationException(msg, e);
        }
    }
}
