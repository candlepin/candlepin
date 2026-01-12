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
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;



/**
 * The SignatureValidator interface defines a fluent-style API for validating data using a given signature,
 * optionally with additional fallback certificates.
 * <p>
 * An example data validation operation using this interface:
 * <pre>
 *  boolean valid = certificateAutority.getSignatureValidator(scheme)
 *      .withAdditionalCertificates(certificateAuthority.getUpstreamCertificates())
 *      .forSignature(signature)
 *      .validate(istream);
 * </pre>
 */
public interface SignatureValidator {

    /**
     * Fetches the scheme backing this signature validator. Signature validation operations performed by this
     * validator will use the algorithms and keys defined by this scheme.
     * <p>
     * Implementations of this method must never return null, and must always return the same scheme for a
     * given validator instance.
     *
     * @return
     *  the signature scheme used by this signature validator.
     */
    Scheme getSignatureScheme();

    /**
     * Adds one or more additional certificates (and their associated public key) to be used to validate
     * signatures as a fallback, should validation with the scheme-provided certificates fail. If any of the
     * provided certificates is null, this method throws an exception.
     *
     * @deprecated
     *  Additional certificates are only intended to be used with legacy logic that allows signatures to be
     *  signed by one of multiple certificates (keys), rather than verifying that the signature is valid with
     *  a given singular certificate and then verifying if that certificate is trusted. New code should not
     *  use the legacy form of signature validation and should move toward singular cert signing.
     *
     * @param certs
     *  a collection of additional certificates to use for signature validation. Null or empty collections are
     *  silently ignored.
     *
     * @return
     *  a signature validator configured with the provided additional certificates
     */
    @Deprecated
    SignatureValidator withAdditionalCertificates(Collection<X509Certificate> certs);

    /**
     * Adds one or more additional certificates (and their associated public key) to be used to validate
     * signatures as a fallback, should validation with the scheme-provided certificates fail. Null
     * certificates, or a null array of certificates will be silently ignored.
     *
     * @deprecated
     *  Additional certificates are only intended to be used with legacy logic that allows signatures to be
     *  signed by one of multiple certificates (keys), rather than verifying that the signature is valid with
     *  a given singular certificate and then verifying if that certificate is trusted. New code should not
     *  use the legacy form of signature validation and should move toward singular cert signing.
     *
     * @param certs
     *  an array of additional certificates to use for signature validation.
     *
     * @return
     *  a signature validator configured with the provided additional certificates
     */
    @Deprecated
    default SignatureValidator withAdditionalCertificates(X509Certificate... certs) {
        return this.withAdditionalCertificates(certs != null ? Arrays.asList(certs) : null);
    }

    /**
     * Sets the signature of the data to validate. If the signature is null or empty, this method throws an
     * exception.
     *
     * @param signature
     *  an array of bytes representing the raw signature to validate. Cannot be null or empty.
     *
     * @throws IllegalArgumentException
     *  if the given signature is null or empty
     *
     * @return
     *  a signature validator configured with the specified signature
     */
    SignatureValidator forSignature(byte[] signature);

    // TODO:
    // When additional certificate logic is removed and replaced with proper cert chaining and trusts, add
    // a validate overload for InputStream, and possibly remove/deprecate the validate for File

    /**
     * Attempts to validate the data within the given file against the configured signature, using the
     * scheme-provided certificates and keys, falling back to any additional certificates this validator may
     * be configured to use should validation with the scheme's certificate fail. If this validator has not
     * been configured with a signature, or the provided file is null, this method throws an
     * exception.
     *
     * @param file
     *  a file containing the data to validate against the configured signature. Cannot be null.
     *
     * @throws IllegalStateException
     *  if this validator has not been configured with a signature
     *
     * @throws IllegalArgumentException
     *  if the given file is null
     *
     * @throws IOException
     *  if an IOException occurs while reading the given file
     *
     * @return
     *  true if the signature of the data within the file matches the configured signature; false otherwise
     */
    boolean validate(File file) throws IOException;

    /**
     * Attempts to validate the given data against the configured signature, using the scheme-provided
     * certificates and keys, falling back to any additional certificates this validator may be configured to
     * use should validation with the scheme's certificate fail. If this validator has not been configured
     * with a signature, this method throws an exception. If the data array is null, it is treated the same
     * as an empty array.
     *
     * @param data
     *  the data to validate against the configured signature.
     *
     * @throws IllegalStateException
     *  if this validator has not been configured with a signature
     *
     * @return
     *  true if the signature of the data matches the configured signature; false otherwise
     */
    boolean validate(byte[] data);

}
