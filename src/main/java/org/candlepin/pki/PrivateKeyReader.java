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
package org.candlepin.pki;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyException;
import java.security.PrivateKey;



/**
 * Interface defining methods to read a PEM-encoded private key from an input stream or file. Implementations
 * may choose whether or not to support a given format or encoding, such as PKCS1 or PKCS8 keys.
 */
public interface PrivateKeyReader {

    /**
     * Attempts to read the data from the given input stream as a PEM-encoded private key. If the password
     * argument is non-null, and non-empty, this method will attempt decryption or decoding with the password
     * using standard password-based encoding (PBE) algorithms. This method never returns null.
     * <p>
     * Note that callers are responsible for maintaining the lifecycle of the given input stream. That is, the
     * stream will be consumed by this method, but it will not be marked, rewound, nor closed.
     *
     * @param istream
     *  the stream from which to read a PEM-encoded private key
     *
     * @param password
     *  the password or passphrase to use while reading the key. If null or empty, no decryption step will be
     *  performed.
     *
     * @throws IllegalArgumentException
     *  if the provided InputStream is null
     *
     * @throws KeyException
     *  if an exception occurs while reading the key
     *
     * @return
     *  the PrivateKey read from the provided input stream.
     */
    PrivateKey read(InputStream istream, String password) throws KeyException;

    /**
     * Attempts to read the data from the given file as a PEM-encoded private key. If the password argument is
     * non-null, and non-empty, this method will attempt decryption or decoding with the password using
     * standard password-based encoding (PBE) algorithms. This method never returns null.
     *
     * @param file
     *  the file from which to read a PEM-encoded private key
     *
     * @param password
     *  the password or passphrase to use while reading the key. If null or empty, no decryption step will be
     *  performed.
     *
     * @throws IllegalArgumentException
     *  if the provided file is null
     *
     * @throws KeyException
     *  if an exception occurs while reading the key
     *
     * @return
     *  the PrivateKey read from the provided file
     */
    default PrivateKey read(File file, String password) throws KeyException {
        if (file == null) {
            throw new IllegalArgumentException("file is null");
        }

        try (FileInputStream istream = new FileInputStream(file)) {
            return this.read(istream, password);
        }
        catch (IOException e) {
            throw new KeyException(e);
        }
    }

    /**
     * Attempts to read the data from the given file path as a PEM-encoded private key. If the password
     * argument is non-null, and non-empty, this method will attempt decryption or decoding with the password
     * using standard password-based encoding (PBE) algorithms. This method never returns null.
     *
     * @param path
     *  the file path from which to read a PEM-encoded private key
     *
     * @param password
     *  the password or passphrase to use while reading the key. If null or empty, no decryption step will be
     *  performed.
     *
     * @throws IllegalArgumentException
     *  if the provided path is null
     *
     * @throws KeyException
     *  if an exception occurs while reading the key
     *
     * @return
     *  the PrivateKey read from the provided file path
     */
    default PrivateKey read(String path, String password) throws KeyException {
        if (path == null) {
            throw new IllegalArgumentException("path is null");
        }

        return this.read(new File(path), password);
    }

}
