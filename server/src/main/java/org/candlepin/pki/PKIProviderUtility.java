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

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Interface for lower level PKI operations that require access to a provider's internals.
 */
public interface PKIProviderUtility {
    String SIGNATURE_ALGO = "SHA256WithRSA";

    X509Certificate createX509Certificate(String dn, Set<X509ExtensionWrapper> extensions,
        Set<X509ByteExtensionWrapper> byteExtensions, Date startDate, Date endDate, KeyPair clientKeyPair,
        BigInteger serialNumber, String alternateName) throws GeneralSecurityException, IOException;

    /**
     * Generate CRL.
     *
     * @param entries the entries
     * @return the x509 CRL
     */
    X509CRL createX509CRL(List<X509CRLEntryWrapper> entries, BigInteger crlNumber);

    /**
     * Take an X509Certificate object and return a byte[] of the certificate,
     * PEM encoded
     * @param cert
     * @return PEM-encoded bytes of the certificate
     * @throws IOException if there is i/o problem
     */
    byte[] getPemEncoded(X509Certificate cert) throws IOException;

    byte[] getPemEncoded(Key key) throws IOException;

    byte[] getPemEncoded(X509CRL crl) throws IOException;

    /**
     * Writes the specified certificate to the given output stream in PEM encoding.
     *
     * @param cert
     *  The certificate to encode
     *
     * @param out
     *  The output stream to which the certificate should be written
     *
     * @throws IOException
     *  If an IOException occurs while writing the certificate
     */
    void writePemEncoded(X509Certificate cert, OutputStream out) throws IOException;

    /**
     * Writes the specified key to the given output stream in PEM encoding.
     *
     * @param key
     *  The key to encode
     *
     * @param out
     *  The output stream to which the key should be written
     *
     * @throws IOException
     *  If an IOException occurs while writing the key
     */
    void writePemEncoded(Key key, OutputStream out) throws IOException;

    /**
     * Writes the specified certificate revocation list to the given output stream in PEM encoding.
     *
     * @param crl
     *  The certificate revocation list to encode
     *
     * @param out
     *  The output stream to which the certificate revocation list should be written
     *
     * @throws IOException
     *  If an IOException occurs while writing the certificate revocation list
     */
    void writePemEncoded(X509CRL crl, OutputStream out) throws IOException;

    String decodeDERValue(byte[] value);
}
