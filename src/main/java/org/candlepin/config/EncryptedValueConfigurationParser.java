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

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * EncryptedValueConfigurationParser
 */
public abstract class EncryptedValueConfigurationParser extends
    ConfigurationParser {
    private String passphrase = null;
    private static Logger log =
        LoggerFactory.getLogger(EncryptedValueConfigurationParser.class);

    public EncryptedValueConfigurationParser(Config config) {
        String secretFile = config
            .getString(ConfigProperties.PASSPHRASE_SECRET_FILE);

        log.debug("reading secret file: " +  secretFile);
        try {
            BufferedReader in = new BufferedReader(new FileReader(secretFile));
            String str;
            StringBuilder tmpPassphrase = new StringBuilder();
            while ((str = in.readLine()) != null) {
                log.debug("str passphrase: " + str);
                tmpPassphrase.append(str);
            }
            in.close();
            log.debug("tmpPassphrase: " + tmpPassphrase.toString());
            passphrase = tmpPassphrase.toString();
        }
        catch (FileNotFoundException e) {
            log.debug("File not found: " + secretFile);
            passphrase = null;
            // FIXME: log, complain, etc
        }
        catch (IOException e) {
            log.debug("IOException while reading: " + secretFile);
            passphrase = null;
        }

        log.debug("Using katello-passwd passphrase: " + passphrase);
    }

    /*
     * (non-Javadoc)
     * @see org.candlepin.config.ConfigurationParser#getPrefix()
     */
    public abstract String getPrefix();

    public Properties parseConfig(Map<String, String> inputConfiguration) {
        // pull out properties that we know might be crypted passwds
        // unencrypt them, and update the properties with the new versions
        // do this here so DbBasicAuthConfigParser and JPAConfigParser
        // will do it. Split it to a sub method so sub classes can
        // provide there own implementation of crypt/decrypt
        //
        Properties toReturn = new Properties();
        Properties toDecrypt = stripPrefixFromConfigKeys(inputConfiguration);
        toReturn.putAll(toDecrypt);

        if (encryptedConfigKeys() != null) {
            for (String encConfigKey : encryptedConfigKeys()) {
                String passwordString = toDecrypt.getProperty(encConfigKey);
                if (passwordString != null) {
                    String deCryptedValue = decryptValue(passwordString, getPassphrase());
                    toReturn.setProperty(encConfigKey, deCryptedValue);

                }
            }
        }
        return toReturn;
    }

    /*
     * returns a Set of config keys that should be decrypted if need be
     */
    public abstract Set<String> encryptedConfigKeys();

    /* encrypt config value, such as a password */
    public String encryptValue(String toEnc) {

        // FIXME: clearly this needs a real implementation
        return toEnc;
    }

    /* encrypt config valud, such as a password */
    public String decryptValue(String toDecrypt, String passphrase) {
        log.info("decrypt called");
        if (!toDecrypt.startsWith("$1$")) {
            // this is not an ecnrypted password, just return it
            log.debug("this is not an encrypted string");
            return toDecrypt;
        }

        // remove the magic string
        toDecrypt = toDecrypt.substring(3);


        try {
            Cipher cipher;
            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");

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

            log.info("gh10");
            // NOTE: the encrypted password is stored hex base64
            byte[] b64bytes = Base64.decodeBase64(toDecrypt);
            String plaintext = new String(cipher.doFinal(b64bytes));

            return plaintext;
        }
        catch (Exception e) {
            log.info("Failure trying to decrypt" + toDecrypt , e);
            throw new RuntimeException(e);
        }
    }

    /**
     * @return the passphrase
     */
    public String getPassphrase() {
        // read /etc/katello/secure/passphrase and use it's contents as
        // passpharse
        log.info("getPassphrase: " + passphrase);
        return passphrase;
    }

    /**
     * @param passphrase the passphrase to set
     */
    public void setPassphrase(String passphrase) {
        this.passphrase = passphrase;
    }
}
