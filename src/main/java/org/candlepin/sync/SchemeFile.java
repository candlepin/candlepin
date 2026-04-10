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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Base64.Encoder;

/**
 * Serializable class for manifest export scheme file.
 *
 * @param name
 *  the name of the scheme; cannot be null
 *
 * @param certificate
 *  the certificate used by this scheme; cannot be null
 *
 * @param signatureAlgorithm
 *  the algorithm used to sign the signature; cannot be null
 *
 * @param keyAlgorithm
 *  the key algorithm used by this scheme; cannot be null
 */
public record SchemeFile(
    @JsonProperty(required = true) String name,
    @JsonProperty(required = true) String certificate,
    @JsonProperty(value = "signature_algorithm", required = true) String signatureAlgorithm,
    @JsonProperty(value = "key_algorithm", required = true) String keyAlgorithm
) {
    public static final String FILENAME = "scheme.json";

    public SchemeFile {
        if (name == null) {
            throw new IllegalArgumentException("name is null");
        }

        if (certificate == null) {
            throw new IllegalArgumentException("certificate is null");
        }

        if (signatureAlgorithm == null) {
            throw new IllegalArgumentException("signature algorithm is null");
        }

        if (keyAlgorithm == null) {
            throw new IllegalArgumentException("key algorithm is null");
        }
    }

    /**
     * Creates a scheme file instance using the provided cryptographic algorithm scheme.
     *
     * @param scheme
     *  the cryptographic algorithm scheme used to populate the scheme file
     *
     * @throws IllegalArgumentException
     *  if the scheme is null
     *
     * @throws CertificateEncodingException
     *  if unable to get encoded X.509 certificate
     *
     * @return a signature file instance
     */
    public static SchemeFile from(Scheme scheme) throws CertificateEncodingException {
        if (scheme == null) {
            throw new IllegalArgumentException("scheme is null");
        }

        Encoder encoder = Base64.getEncoder();
        X509Certificate certificate = scheme.certificate();
        String encodedCert = encoder.encodeToString(certificate.getEncoded());

        return new SchemeFile(
            scheme.name(),
            encodedCert,
            scheme.signatureAlgorithm(),
            scheme.keyAlgorithm());
    }

    /**
     * Create a {@link Scheme} using the provided scheme file.
     *
     * @param schemeFile
     *  the scheme file used to create a scheme instance
     *
     * @throws IllegalArgumentException
     *  if the provided scheme file is null or the certificate in the scheme file is not in a valid Base64
     *  scheme
     *
     * @throws CertificateException
     *  if unable to create a X.509 certificate
     *
     * @return the created scheme
     */
    public static Scheme toScheme(SchemeFile schemeFile) throws CertificateException {
        if (schemeFile == null) {
            throw new IllegalArgumentException("scheme file is null");
        }

        byte[] decoded = Base64.getDecoder().decode(schemeFile.certificate());
        try (ByteArrayInputStream is = new ByteArrayInputStream(decoded)) {
            X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(is);

            return new Scheme.Builder()
                .setName(schemeFile.name())
                .setCertificate(certificate)
                .setKeyAlgorithm(schemeFile.keyAlgorithm())
                .setSignatureAlgorithm(schemeFile.signatureAlgorithm())
                .build();
        }
        catch (IOException e) {
            throw new CertificateException(e);
        }
    }

}
