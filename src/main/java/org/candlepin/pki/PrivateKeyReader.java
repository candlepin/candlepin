/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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
package org.candlepin.pki;

import org.apache.commons.codec.binary.Base64InputStream;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPrivateKey;
import java.util.Map;

/**
 * Interface defining methods to read a private key from a PEM file.
 */
public interface PrivateKeyReader {

    RSAPrivateKey read(String caKeyPath, String caKeyPassword) throws IOException;

    RSAPrivateKey read(InputStream keyStream, String password) throws IOException;

    /**
     * Interface for various private key encoding types
     */
    interface PrivateKeyPemParser {
        default RSAPrivateKey decode(String pem, String password, Map<String, String> headers)
            throws IOException {
            try (
                InputStream derStream = new Base64InputStream(
                    new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
            ) {
                byte[] der = IOUtils.toByteArray(derStream);
                return decode(der, password, headers);
            }
        }

        RSAPrivateKey decode(byte[] der, String password, Map<String, String> headers) throws IOException;

        default char[] getPassword(String password) {
            return (password != null) ? password.toCharArray() : null;
        }
    }
}
