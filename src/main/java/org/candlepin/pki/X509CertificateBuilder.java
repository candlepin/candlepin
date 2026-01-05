/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Collection;

/**
 * Builder that is responsible for creating {@link X509Certificate}s.
 */
public interface X509CertificateBuilder {

    /**
     * Returns the signature scheme used for signing X509 certificates.
     * This method should never return null.
     *
     * @return the signature scheme used for signing certificates
     */
    Scheme getSignatureScheme();

    /**
     * Sets the distinguished name used to construct a X509 certificate.
     *
     * @param dn
     *  the distinguished name to use when constructing a X509 certificate
     *
     * @return this instance of the X509CertificateBuilder
     */
    X509CertificateBuilder withDN(DistinguishedName dn);

    /**
     * Sets the subject alternative name used to construct a X509 certificate.
     *
     * @param subjectAltName
     *  the subject alternative name to use when constructing a X509 certificate
     *
     * @return this instance of the X509CertificateBuilder
     */
    X509CertificateBuilder withSubjectAltName(String subjectAltName);

    /**
     * Sets the validity period of the constructed X509 certificate.
     *
     * @param start
     *  the date before which the certificate is not valid
     *
     * @param end
     *  the date after which the certificate is not valid
     *
     * @return this instance of the X509CertificateBuilder
     */
    X509CertificateBuilder withValidity(Instant start, Instant end);

    /**
     * Sets the key pair used to construct a X509 certificate
     *
     * @param keyPair
     *  the key pair used to construct a X509 certificate
     *
     * @return this instance of the X509CertificateBuilder
     */
    X509CertificateBuilder withKeyPair(KeyPair keyPair);

    /**
     * Sets the serial number used to construct a X509 certificate.
     *
     * @param serial
     *  the serial number to use when constructing a X509 certificate
     *
     * @return this instance of the X509CertificateBuilder
     */
    X509CertificateBuilder withSerial(BigInteger serial);

    /**
     * Sets the serial number used to construct a X509 certificate.
     *
     * @param serial
     *  the serial number to use when constructing a X509 certificate
     *
     * @return this instance of the X509CertificateBuilder
     */
    X509CertificateBuilder withSerial(long serial);

    /**
     * Generates and sets a random serial number for the X509 certificate.
     *
     * @return this instance of the X509CertificateBuilder
     */
    X509CertificateBuilder withRandomSerial();

    /**
     * Sets the extensions to include in the X509 certificate.
     *
     * @param extensions
     *  the collection of extensions to include in the X509 certificate
     *
     * @return this instance of the X509CertificateBuilder
     */
    X509CertificateBuilder withExtensions(Collection<X509Extension> extensions);

    /**
     * Sets the extensions to include in the X509 certificate.
     *
     * @param extensions
     *  the extensions to include in the X509 certificate
     *
     * @return this instance of the X509CertificateBuilder
     */
    X509CertificateBuilder withExtensions(X509Extension... extensions);

    /**
     * @return a constructed {@link X509Certificate} using the configured properties
     */
    X509Certificate build();

}
