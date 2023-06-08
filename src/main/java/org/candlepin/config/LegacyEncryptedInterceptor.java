/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import static io.smallrye.config.SecretKeys.doLocked;

import io.smallrye.config.ConfigSourceInterceptor;
import io.smallrye.config.ConfigSourceInterceptorContext;
import io.smallrye.config.ConfigValue;
import io.smallrye.config.Priorities;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import jakarta.annotation.Priority;


@Priority(Priorities.LIBRARY + 1000)
public class LegacyEncryptedInterceptor implements ConfigSourceInterceptor {
    private static final Logger log = LoggerFactory.getLogger(LegacyEncryptedInterceptor.class);

    private final Set<String> encryptedProperties;
    private String passphrase;

    public LegacyEncryptedInterceptor(String[] encryptedProperties) {
        this(new HashSet<>(Arrays.asList(encryptedProperties)));
    }

    public LegacyEncryptedInterceptor(Set<String> encryptedProperties) {
        this.encryptedProperties = encryptedProperties;
    }

    public ConfigValue getValue(final ConfigSourceInterceptorContext context, final String property) {
        ConfigValue configValue = doLocked(() -> context.proceed(property));
        if (configValue == null) {
            log.debug("Can't decrypt missing property: {}", property);
            return null;
        }

        if (!encryptedProperties.contains(configValue.getName())) {
            log.debug("Skipping unencrypted property: {}", property);
            return configValue;
        }

        if (passphrase == null) {
            passphrase = readPassphrase(context);
        }

        if (StringUtils.isEmpty(passphrase)) {
            log.debug("Passphrase is empty. Skipping decrypt.");
            return configValue;
        }

        String toDecrypt = configValue.getRawValue();
        if (!toDecrypt.startsWith("$1$")) {
            // this is not an encrypted password, just return it
            log.debug("Value for {} is not an encrypted string", property);
            return configValue;
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
            SecretKeySpec spec = new SecretKeySpec(
                Arrays.copyOfRange(passphraseDigest.getBytes(), 0, 32), "AES");

            cipher.init(Cipher.DECRYPT_MODE, spec, new IvParameterSpec(iv.getBytes(), 0, 16));

            // NOTE: the encrypted password is stored hex base64
            byte[] b64bytes = Base64.decodeBase64(toDecrypt);
            String decrypted = new String(cipher.doFinal(b64bytes));
            return configValue.withValue(decrypted);
        }
        catch (Exception e) {
            log.error("Failure trying to decrypt value of {}", property, e);
        }
        return configValue;
    }

    private String readPassphrase(final ConfigSourceInterceptorContext context) {
        ConfigValue configValue = doLocked(() -> context.proceed(ConfigProperties.PASSPHRASE_SECRET_FILE));
        String passFilePath = configValue.getValue();
        if (StringUtils.isEmpty(passFilePath)) {
            log.info("No secret file provided.");
            return null;
        }

        Path path = Paths.get(passFilePath);
        if (!Files.exists(path)) {
            log.warn("{} is present in the configuration but the file does not exist",
                ConfigProperties.PASSPHRASE_SECRET_FILE);
            return null;
        }

        log.debug("reading secret file: {}", passFilePath);
        try (InputStream bs = new FileInputStream(passFilePath)) {
            /* XXX Maybe it'd be better to use the charset the caller specifies during
             * construction?  But just because the config is in that charset doesn't mean
             * the password file is.  Stick with system default for now. */
            return IOUtils.toString(bs, Charset.defaultCharset()).trim();
        }
        catch (Exception e) {
            throw new RuntimeException("Could not read: %s".formatted(passFilePath), e);
        }
    }
}
