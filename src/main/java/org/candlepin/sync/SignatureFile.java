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
package org.candlepin.sync;

import org.candlepin.pki.Scheme;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.security.cert.CertificateEncodingException;
import java.util.Base64;
import java.util.Base64.Encoder;

/**
 * Serializable class for manifest export signature file.
 *
 * @param scheme
 *  the scheme information to be included in the signature file; cannot be null
 *
 * @param signature
 *  the signature to be included in the signature file; cannot be null
 */
public record SignatureFile(
    SignatureScheme scheme,
    @JsonProperty(required = true) String signature
) {

    public SignatureFile {
        if (scheme == null) {
            throw new IllegalArgumentException("scheme is null");
        }

        if (signature == null) {
            throw new IllegalArgumentException("signature is null");
        }
    }

    /**
     * Creates a signature file instance using the provided cryptographic algorithm scheme and signature.
     *
     * @param scheme
     *  the cryptographic algorithm scheme used to populate the signature file
     *
     * @param signature
     *  the signature used to populate the signature file
     *
     * @throws IllegalArgumentException
     *  if the scheme or the signature is null
     *
     * @throws CertificateEncodingException
     *  if unable to get encoded X.509 certificate
     *
     * @return a signature file instance
     */
    public static SignatureFile from(Scheme scheme, byte[] signature)
        throws CertificateEncodingException {

        if (scheme == null) {
            throw new IllegalArgumentException("scheme is null");
        }

        if (signature == null) {
            throw new IllegalArgumentException("signature is null");
        }

        Encoder encoder = Base64.getEncoder();
        String certificate = encoder.encodeToString(scheme.certificate().getEncoded());
        SignatureScheme schemePayload = new SignatureScheme(
            scheme.name(),
            certificate,
            scheme.signatureAlgorithm(),
            scheme.keyAlgorithm());

        return new SignatureFile(schemePayload, encoder.encodeToString(signature));
    }

    public record SignatureScheme(
        @JsonProperty(required = true) String name,
        @JsonProperty(required = true) String certificate,
        @JsonProperty(value = "signature_algorithm", required = true) String signatureAlgorithm,
        @JsonProperty(value = "key_algorithm", required = true) String keyAlgorithm) {
    }
}
