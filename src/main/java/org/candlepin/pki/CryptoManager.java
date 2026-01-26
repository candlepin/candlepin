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

import org.candlepin.model.Consumer;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import java.util.Set;



/**
 * The CryptoManager is the a centralized source of configured cryptographic schemes and certificates,
 * as well as acting as a provider for crypto-related service classes, such as the Signer, SignatureValdiator,
 * or X509CertificateBuilder.
 */
public interface CryptoManager {

    /**
     * Fetches the Java security provider backing all of the crypto operations of this CryptoManager instance
     * and any of the crypto utility objects it builds. This method will never return null.
     *
     * @return
     *  a Java security provider
     */
    java.security.Provider getSecurityProvider();

    /**
     * Gets a list of all known, configured cryptographic schemes defined in order of highest to lowest
     * priority. This method should never return a null, nor empty list; but the list returned may be
     * immutable or read-only. Callers should not expect to be able to perform transformative operations on
     * the output list.
     *
     * @return
     *  a list containing all known, configured cryptographic schemes defined in priority order.
     */
    List<Scheme> getCryptoSchemes();

    /**
     * Fetches the cryptographic scheme matching the given scheme name from the list of supported schemes,
     * case-insensitively. If a matching scheme could not be found in the scheme list, this method returns an
     * empty optional. If the provided scheme name is null, this method throws an exception.
     *
     * @param scheme
     *  the name of the scheme to fetch. Cannot be null
     *
     * @throws IllegalArgumentException
     *  if the given scheme name is null
     *
     * @return
     *  an optional containing the matching scheme if found, or an empty optional otherwise
     */
    Optional<Scheme> getCryptoScheme(String scheme);

    /**
     * Fetches the scheme appropriate for performing cryptographic operations for the given consumer,
     * performing scheme negotiation based on the consumer's known facts and system information. If a scheme
     * cannot be determined for this consumer, an empty optional is returned. If the provided consumer is
     * null, this method throws an exception.
     *
     * @param consumer
     *  the consumer for which to fetch an appropriate scheme
     *
     * @throws IllegalArgumentException
     *  if consumer is null
     *
     * @return
     *  an optional containing the negotiated scheme to use for cryptographic operations for the given
     *  consumer; or an empty optional if an appropriate scheme could not be determined
     */
    Optional<Scheme> getCryptoScheme(Consumer consumer);

    /**
     * Fetches the default scheme to use when scheme negotiation is not possible or otherwise cannot be
     * determined. This should typically be reserved for legacy behavior or communication with clients that
     * do not indicate the cryptographic scheme to use.
     *
     * @return
     *  the system default scheme
     */
    Scheme getDefaultCryptoScheme();

    /**
     * Fetches the set of "upstream" certificates, defined as those included in the Candlepin RPM, typically
     * aligned with the certificates used by hosted Candlepin. This method will never return null. If there
     * are no upstream certificates, this method returns an empty set.
     *
     * @throws CertificateException
     *  if an error occurs while reading the upstream certificates
     *
     * @return
     *  the set of upstream certificates
     */
    Set<X509Certificate> getUpstreamCertificates() throws CertificateException;

    /**
     * Checks if the given certificate is a trusted certificate, defined as the primary certificates currently
     * configured as a signer for one or more cryptographic schemes, it is one of the certificates in the
     * upstream certificate repository, or it has been signed by one of the aforementioned certificates.
     *
     * @param certificate
     *  the certificate to test
     *
     * @throws IllegalArgumentException
     *  if the given certificate is null
     *
     * @throws CertificateException
     *  if an error occurs while testing known certificates
     *
     * @return
     *  true if the certificate is considered trusted, false otherwise
     */
    boolean isTrustedCertificate(X509Certificate certificate) throws CertificateException;

    /**
     * Fetches a signer instance backed by the specified cryptographic scheme. If the given scheme is null or
     * does not contain a private key, this method throws an exception. This method will never return null.
     *
     * @param scheme
     *  the scheme for which to fetch a signer; cannot be null
     *
     * @throws IllegalArgumentException
     *  if the given scheme is null, or does not include a private key
     *
     * @return
     *  a signer instance using the specified cryptographic scheme
     */
    Signer getSigner(Scheme scheme);

    /**
     * Fetches a signature validator instance backed by the specified cryptographic scheme. If the given
     * scheme is null, this method throws an exception. This method will never return null.
     *
     * @param scheme
     *  the scheme for which to fetch a signature validator; cannot be null
     *
     * @throws IllegalArgumentException
     *  if the given scheme is null
     *
     * @return
     *  a signature validator instance using the specified cryptographic scheme
     */
    SignatureValidator getSignatureValidator(Scheme scheme);

    /**
     * Fetches a X509 certificate builder instance backed by the specified cryptographic scheme. If the given
     * scheme is null or does not contain a private key, this method throws an exception. This method will
     * never return null.
     *
     * @param scheme
     *  the scheme for which to fetch a certificate builder; cannot be null
     *
     * @throws IllegalArgumentException
     *  if the given scheme is null, or does not include a private key
     *
     * @return
     *  a X509 certificate builder instance using the specified cryptographic scheme
     */
    X509CertificateBuilder getCertificateBuilder(Scheme scheme);

    // /**
    //  * Fetches a key pair generator instance backed by the specified cryptographic scheme. If the given
    //  * scheme is null or does not contain a private key, this method throws an exception. This method will
    //  * never return null.
    //  *
    //  * @param scheme
    //  *  the scheme for the resultant keypair generator to use
    //  *
    //  * @throws IllegalArgumentException
    //  *  if the given scheme is null, or does not include a private key
    //  *
    //  * @return
    //  *  a keypair generator using the specified cryptographic scheme
    //  */
    // KeyPairGenerator getKeyPairGenerator(Scheme scheme);
}
