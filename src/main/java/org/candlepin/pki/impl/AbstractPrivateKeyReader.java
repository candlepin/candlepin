/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
package org.candlepin.pki.impl;

import org.candlepin.pki.PrivateKeyReader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyException;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



/**
 * A generic abstract implementation of the PrivateKeyReader which parses PKCS1 and PKCS8 PEM files,
 * offloading the processing of the actual key data to a provider-specific implementation. Subclasses need
 * only implement processing of the binary key data, rather than the PEM boundary and header processing.
 */
public abstract class AbstractPrivateKeyReader implements PrivateKeyReader {
    private static final String BOUNDARY_BEGIN = "BEGIN";
    private static final String BOUNDARY_END = "END";

    private static final String MODIFIER_ENCRYPTED = Modifiers.ENCRYPTED.toString();
    private static final String MODIFIER_RSA = Modifiers.RSA.toString();

    /**
     * Regex for a PEM header/footer, resolves to something like:
     *  ^\s*-+(BEGIN|END)\s(?:(ENCRYPTED|RSA)\s)?PRIVATE KEY-+\s*$
     */
    private static final Pattern REGEX_PEM_BOUNDARY = Pattern.compile("^\\s*-+(" +
        String.join("|", BOUNDARY_BEGIN, BOUNDARY_END) +
        ")\\s(?:(" +
        String.join("|", MODIFIER_ENCRYPTED, MODIFIER_RSA) +
        ")\\s)?PRIVATE KEY-+\\s*$");

    // Group numbers, related to the REGEX_PEM_BOUNDARY expression
    private static final int REGEX_GROUP_PEM_BOUNDARY = 1;
    private static final int REGEX_GROUP_PEM_MODIFIER = 2;

    /**
     * The Modifiers enum contains supported private key "modifiers" found in the header of private key
     * PEM declarations. That is, the optional indicators that occur between the "BEGIN" and "PRIVATE KEY"
     * of the header which provide hints at the format and encoding of the private key.
     *
     * For example, for the header "----BEGIN RSA PRIVATE KEY----", "RSA" would be the modifier and would
     * map to the equally named modifier enum value.
     */
    public enum Modifiers {
        /** Modifier indicating a PKCS1 RSA private key */
        RSA,

        /** Modifier indicating an encrypted PKCS8 private key */
        ENCRYPTED;

        /**
         * Null-safe, case-insensitive conversion of a string to modifier enum. If the given modifier is null
         * or empty, this method returns an empty Optional. Otherwise, it performs a case-insensitive
         * conversion of the given string to a modifier enum, wrapped in an Optional. If the modifier name
         * does not exist, this method throws an exception. This method never returns null.
         *
         * @param modifier
         *  the modifier string to convert to a modifier enum
         *
         * @throws IllegalArgumentException
         *  if the modifier name does not match a known modifier enum
         *
         * @return
         *  an optional containing a modifier enum, or an empty optional if the given modifier name is null
         *  or empty
         */
        public static Optional<Modifiers> fromString(String modifier) {
            if (modifier == null || modifier.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(Modifiers.valueOf(modifier.toUpperCase()));
        }
    }

    /**
     * Parses lines of text from the given reader until a PEM private key header is found, or end of stream
     * occurs. Returns an optional containing any header modifier found (e.g. RSA or ENCRYPTED). If EOF occurs
     * before a header is found, this method throws an exception.
     *
     * @param reader
     *  the reader to process
     *
     * @return
     *  any header modifier found
     */
    private Optional<Modifiers> findPemHeader(BufferedReader reader) throws IOException, KeyException {
        String line;
        while ((line = reader.readLine()) != null) {
            // Only invoke the regex engine if the line begins with our prefix
            if (!line.startsWith("-")) {
                continue;
            }

            Matcher matcher = REGEX_PEM_BOUNDARY.matcher(line);
            if (matcher.matches() && BOUNDARY_BEGIN.equals(matcher.group(REGEX_GROUP_PEM_BOUNDARY))) {
                return Modifiers.fromString(matcher.group(REGEX_GROUP_PEM_MODIFIER));
            }
        }

        throw new KeyException("No private key declaration found in the source stream");
    }

    /**
     * Reads lines of base64-encoded data until the matching PEM footer is found, or end-of-file is reached.
     * In the event the key appears to be a PKCS1/RSA key, this method will attempt to parse any headers
     * present in the key declaration and store them in the given map of headers. If the footer is not found
     * before hitting EOF, this method throws an exception.
     *
     * @param reader
     *  a BufferedReader instance from which to read private key data
     *
     * @param modifier
     *  an optional containing any modifier on the PEM key header (RSA, ENCRYPTED, etc.)
     *
     * @param pembuilder
     *  a StringBuilder to receive the base64-encoded private key data
     *
     * @param pkcs1Headers
     *  a map to receive the PEM headers, if present
     *
     * @throws KeyException
     *  if the key data is malformed or otherwise cannot be parsed, or a given PEM header is declared multiple
     *  times
     */
    private void readPem(BufferedReader reader, Optional<Modifiers> modifier,
        Map<String, String> pkcs1Headers, StringBuilder pembuilder) throws IOException, KeyException {

        String line;

        // Attempt to read the headers if it looks like an RSA key
        if (modifier.orElse(null) == Modifiers.RSA) {
            while ((line = reader.readLine()) != null) {
                int idx = line.indexOf(':');
                if (idx == -1) {
                    // Ensure we save the non-matching line, instead of accidentally discarding it. It likely
                    // contains the first line of the b64 (no headers), or an empty line (headers present).
                    pembuilder.append(line.trim());
                    break;
                }

                String key = line.substring(0, idx).trim();
                String value = line.substring(idx + 1).trim();

                if (pkcs1Headers.containsKey(key)) {
                    // Near as I can tell, a header shouldn't be defined multiple times, but if it is we'll
                    // abort
                    throw new KeyException("The header \"" + key + "\" appears multiple times in this key");
                }

                pkcs1Headers.put(key, value);
            }
        }

        // Read until we find our footer, or EOF into failure
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("-")) {
                Matcher matcher = REGEX_PEM_BOUNDARY.matcher(line);

                boolean match = matcher.matches() &&
                    BOUNDARY_END.equals(matcher.group(REGEX_GROUP_PEM_BOUNDARY)) &&
                    modifier.equals(Modifiers.fromString(matcher.group(REGEX_GROUP_PEM_MODIFIER)));

                if (match) {
                    return;
                }
            }

            pembuilder.append(line.trim());
        }

        throw new KeyException("EOF reached before terminating key");
    }

    /**
     * Decodes the given StringBuilder containing base64-encoded data. If decoding fails for any reason, this
     * method throws an exception.
     *
     * @param pembuilder
     *  the StringBuilder instance containing the base64 data to decode
     *
     * @throws KeyException
     *  if the data cannot be decoded
     *
     * @return
     *  a byte array containing the decoded data
     */
    private byte[] decodeBase64(StringBuilder pembuilder) throws KeyException {
        try {
            return Base64.getDecoder().decode(pembuilder.toString());
        }
        catch (IllegalArgumentException e) {
            throw new KeyException("Cannot read private key: malformed base64 data section", e);
        }
    }

    /**
     * Decodes the specified buffer to a PrivateKey instance. The buffer will always be the binary
     * representation of a PKCS1 key immediately after base64 decoding, but before any decryption is applied.
     * If the private key cannot be decoded for any reason, this method throws an exception. This method never
     * returns null.
     * <p>
     * Note that this method is not intended to be called directly. Any invocation of this method from outside
     * the AbstractPrivateKeyReader should be considered erroneous.
     *
     * @param buffer
     *  a buffer containing the private key to decode in binary form; will never be null
     *
     * @param headers
     *  any additional headers defined with the private key; will never be null, but may be empty
     *
     * @param password
     *  the password to use to decrypt a protected private key; may be null or empty if no password has been
     *  provided
     *
     * @throws KeyException
     *  if the private key cannot be decoded
     *
     * @return
     *  the decoded private key
     */
    protected abstract PrivateKey decodePkcs1(byte[] buffer, Map<String, String> headers, String password)
        throws KeyException;

    /**
     * Decodes the specified buffer to a PrivateKey instance. The buffer will always be the binary
     * representation of a PKCS8 key immediately after base64 decoding, but before any decryption is applied.
     * If the private key cannot be decoded for any reason, this method throws an exception. This method never
     * returns null.
     * <p>
     * Note that this method is not intended to be called directly. Any invocation of this method from outside
     * the AbstractPrivateKeyReader should be considered erroneous.
     *
     * @param buffer
     *  a buffer containing the private key to decode in binary form; will never be null
     *
     * @param modifier
     *  an optional containing any modifier observed while parsing the originating PEM source; will never be
     *  null, but may be empty
     *
     * @param password
     *  the password to use to decrypt a protected private key; may be null or empty if no password has been
     *  provided
     *
     * @throws KeyException
     *  if the private key cannot be decoded
     *
     * @return
     *  the decoded private key
     */
    protected abstract PrivateKey decodePkcs8(byte[] buffer, Optional<Modifiers> modifier, String password)
        throws KeyException;

    @Override
    public PrivateKey read(InputStream istream, String password) throws KeyException {
        if (istream == null) {
            throw new IllegalArgumentException("istream is null");
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(istream))) {
            Optional<Modifiers> modifier = this.findPemHeader(reader);

            Map<String, String> pkcs1Headers = new HashMap<>();
            StringBuilder pembuilder = new StringBuilder();

            // This reads both the headers and the b64 encoded pem
            this.readPem(reader, modifier, pkcs1Headers, pembuilder);
            byte[] der = this.decodeBase64(pembuilder);

            return modifier.orElse(null) == Modifiers.RSA ?
                this.decodePkcs1(der, pkcs1Headers, password) :
                this.decodePkcs8(der, modifier, password);
        }
        catch (IOException e) {
            throw new KeyException(e);
        }
    }

}

