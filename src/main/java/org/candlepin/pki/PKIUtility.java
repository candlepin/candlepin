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

import org.candlepin.model.Consumer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Set;



/**
 * Interface for lower level PKI operations that require access to a provider's internals.
 */
public interface PKIUtility {

    X509Certificate createX509Certificate(String dn, Set<X509ExtensionWrapper> extensions,
        Set<X509ByteExtensionWrapper> byteExtensions, Date startDate, Date endDate, KeyPair clientKeyPair,
        BigInteger serialNumber, String alternateName) throws GeneralSecurityException, IOException;

    /**
     * Take an X509Certificate object and return a byte[] of the certificate,
     * PEM encoded
     * @param cert
     * @return PEM-encoded bytes of the certificate
     * @throws IOException if there is i/o problem
     */
    byte[] getPemEncoded(X509Certificate cert) throws IOException;

    byte[] getPemEncoded(PrivateKey key) throws IOException;

    byte[] getSHA256WithRSAHash(InputStream input);

    boolean verifySHA256WithRSAHashAgainstCACerts(File input, byte[] signedHash)
        throws CertificateException, IOException;

    /**
     * Generates a new, unassociated key pair consisting of a public and private key.
     *
     * @return
     *  a KeyPair instance containing a new public and private key
     */
    KeyPair generateKeyPair() throws KeyException;

    /**
     * Fetches the key pair for the specified consumer, generating one if necessary. If the current
     * key pair is invalid, malformed, or unreadable, a new key pair will be generated instead.
     *
     * @param consumer
     *  the consumer for which to fetch a key pair
     *
     * @throws NoSuchAlgorithmException
     *
     * @return
     *  the KeyPair instance containing the public and private keys for the specified consumer
     */
    KeyPair getConsumerKeyPair(Consumer consumer) throws KeyException;
}
