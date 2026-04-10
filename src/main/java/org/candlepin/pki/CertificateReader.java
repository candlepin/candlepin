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
package org.candlepin.pki;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;



/**
 * Interface defining methods to read a X.509 from an input stream or file.
 */
public interface CertificateReader {

    /**
     * Attempts to read the data from the given input stream as an X.509 certificate. If a valid certificate
     * cannot be read from the stream, this method throws an exception. This method never returns null.
     * <p>
     * Note that callers are responsible for maintaining the lifecycle of the given input stream. That is, the
     * stream will be consumed by this method, but it will not be marked, rewound, nor closed.
     *
     * @param istream
     *  the stream from which to read a X.509 certificate
     *
     * @throws IllegalArgumentException
     *  if the provided InputStream is null
     *
     * @throws CertificateException
     *  if an exception occurs while reading the certificate
     *
     * @return
     *  the X509Certificate read from the specified input stream.
     */
    X509Certificate read(InputStream istream) throws CertificateException;

    /**
     * Attempts to read the data from the given file as an X.509 certificate. If a valid certificate cannot be
     * read from the stream, this method throws an exception. This method never returns null.
     *
     * @param file
     *  the file from which to read a X.509 certificate
     *
     * @throws IllegalArgumentException
     *  if the provided file is null
     *
     * @throws CertificateException
     *  if an exception occurs while reading the certificate
     *
     * @return
     *  the X509Certificate read from the specified file.
     */
    default X509Certificate read(File file) throws CertificateException {
        if (file == null) {
            throw new IllegalArgumentException("file is null");
        }

        try (FileInputStream istream = new FileInputStream(file)) {
            return this.read(istream);
        }
        catch (IOException e) {
            throw new CertificateException(e);
        }
    }

    /**
     * Attempts to read the data from the given file path as an X.509 certificate. If a valid certificate
     * cannot be  read from the stream, this method throws an exception. This method never returns null.
     *
     * @param path
     *  a path to the file from which to read a X.509 certificate
     *
     * @throws IllegalArgumentException
     *  if the provided file path is null
     *
     * @throws CertificateException
     *  if an exception occurs while reading the certificate
     *
     * @return
     *  the X509Certificate read from the specified path.
     */
    default X509Certificate read(String path) throws CertificateException {
        if (path == null) {
            throw new IllegalArgumentException("path is null");
        }

        return this.read(new File(path));
    }

}
