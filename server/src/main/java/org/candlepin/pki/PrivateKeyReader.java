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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.interfaces.RSAPrivateKey;
import java.util.HashMap;
import java.util.Map;

/**
 * Class used to read a private key from a PKCS1 or PKCS8 file.  Inspired by the PemReader
 * and PEMParser classes in BouncyCastle (which is licensed under an equivalent to the MIT license).
 */
public abstract class PrivateKeyReader implements ProviderBasedPrivateKeyReader {
    private static final String BEGIN = "-----BEGIN ";
    private static final String END = "-----END ";

    @Override
    public RSAPrivateKey read(String caKeyPath, String caKeyPassword) throws IOException {
        try (
            FileInputStream fis = new FileInputStream(caKeyPath)
        ) {
            return read(fis, caKeyPassword);
        }
    }

    @Override
    public RSAPrivateKey read(InputStream keyStream, String password) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(keyStream));
        String line = reader.readLine();

        while (line != null && !line.startsWith(BEGIN)) {
            line = reader.readLine();
        }

        if (line != null) {
            // "-----BEGIN RSA PRIVATE KEY-----" becomes "RSA PRIVATE KEY-----"
            line = line.substring(BEGIN.length());
            // Find the first occurrence of a hyphen
            int nextHyphen = line.indexOf('-');
            // Now we have something like "RSA PRIVATE KEY"
            String type = line.substring(0, nextHyphen);

            if (nextHyphen > 0) {
                return readPem(type, reader, password);
            }
        }

        throw new IOException("Could not read key");
    }

    /**
     * Read a PEM blob based on the type declared in the BEGIN block.  We have to deal with several different
     * formats: PCKS8 (both encrypted and unencrypted) and the non-standard PKCS1 format OpenSSL uses.  For an
     * unencrypted key, OpenSSL uses a normal PKCS1, but for an encrypted key it adds headers prior to the
     * PEM.  For example:
     *
     *-----BEGIN RSA PRIVATE KEY-----
     * Proc-Type: 4,ENCRYPTED
     * DEK-Info: AES-256-CBC,A4E76EB0315C87607649FB5E1FB975B1
     *
     * This non-standard format is described in OpenSSL's man page for "PEM_read_bio_PrivateKey".  But for
     * reference:
     *
     * The line beginning with Proc-Type contains the version and the protection on the encapsulated data.
     * The line beginning DEK-Info contains two comma separated values: the encryption algorithm name
     * and an initialization vector used by the cipher encoded as a set of hexadecimal digits.
     *
     * @param type the key type.  One of "RSA PRIVATE KEY", "PRIVATE KEY", or "ENCRYPTED PRIVATE KEY"
     * @param reader a reader for the PEM
     * @param password a password to use for decrypting if necessary
     * @return the PrivateKey from the PEM file
     * @throws IOException if anything goes wrong
     */
    protected RSAPrivateKey readPem(String type, BufferedReader reader, String password) throws IOException {
        String line;
        String endMarker = END + type;
        StringBuilder buf = new StringBuilder();
        Map<String, String> headers = new HashMap<>();

        while ((line = reader.readLine()) != null) {
            if (line.indexOf(':') >= 0) {
                int index = line.indexOf(':');
                String header = line.substring(0, index);

                if (headers.containsKey(header)) {
                    // Near as I can tell, a header shouldn't be defined multiple times, but if it is we'll
                    // abort
                    throw new IOException("The header \"" + header + "\" appears multiple times in this key");
                }

                String value = line.substring(index + 1).trim();
                headers.put(header, value);
                continue;
            }

            if (line.contains(endMarker)) {
                break;
            }
            buf.append(line.trim());
        }

        if (line == null) {
            throw new IOException(endMarker + " not found");
        }

        String pem = buf.toString();
        switch (type) {
            case "RSA PRIVATE KEY":
                if (headers.isEmpty()) {
                    return pkcs1PrivateKeyPemParser().decode(pem, null, null);
                }
                else {
                    return pkcs1EncryptedPrivateKeyPemParser().decode(pem, password, headers);
                }
            case "PRIVATE KEY":
                return pkcS8PrivateKeyPemParser().decode(pem, null, null);
            case "ENCRYPTED PRIVATE KEY":
                return pkcS8EncryptedPrivateKeyPemParser().decode(pem, password, null);
            default:
                throw new IOException("Unrecognized type: " + type);
        }
    }

    protected abstract PrivateKeyPemParser pkcS8EncryptedPrivateKeyPemParser();
    protected abstract PrivateKeyPemParser pkcS8PrivateKeyPemParser();
    protected abstract PrivateKeyPemParser pkcs1EncryptedPrivateKeyPemParser();
    protected abstract PrivateKeyPemParser pkcs1PrivateKeyPemParser();
}
