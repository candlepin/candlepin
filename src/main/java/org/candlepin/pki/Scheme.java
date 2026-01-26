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

import java.security.PrivateKey;
import java.security.cert.X509Certificate;



/**
 * A cryptographic algorithm scheme used for signature operations.
 *
 * @param name
 *  the name of the signature scheme
 *
 * @param certificate
 *  the certificate to use for cryptographic operations
 *
 * @param privateKey
 *  the private key to use for cryptographic operations using this scheme; optional
 *
 * @param signatureAlgorithm
 *  the signature algorithm to use for cryptographic signing operations
 *
 * @param keyAlgorithm
 *  the algorithm used to generate key pairs under this scheme
 *
 * @param keySize
 *  the size of the keys generated using this scheme; optional
 *
 * @throws IllegalArgumentException
 *  if any of the required fields are null or empty: name, certificate, signatureAlgorithm, or keyAlgorithm
 */
public record Scheme(
    String name,

    X509Certificate certificate,
    PrivateKey privateKey,

    String signatureAlgorithm,
    String keyAlgorithm,
    Integer keySize
) {
    public Scheme {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is null or empty");
        }

        if (certificate == null) {
            throw new IllegalArgumentException("certificate is null");
        }

        if (signatureAlgorithm == null || signatureAlgorithm.isBlank()) {
            throw new IllegalArgumentException("signatureAlgorithm is null or empty");
        }

        if (keyAlgorithm == null || keyAlgorithm.isBlank()) {
            throw new IllegalArgumentException("keyAlgorithm is null or empty");
        }
    }

    /**
     * Simple builder class for helping constructing scheme records. This class performs no additional
     * validation on top of what is already performed by the scheme record itself.
     */
    public static class Builder {
        private String name;

        private X509Certificate certificate;
        private PrivateKey privateKey;

        private String signatureAlgorithm;
        private String keyAlgorithm;
        private Integer keySize;

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setCertificate(X509Certificate certificate) {
            this.certificate = certificate;
            return this;
        }

        public Builder setPrivateKey(PrivateKey privateKey) {
            this.privateKey = privateKey;
            return this;
        }

        public Builder setSignatureAlgorithm(String signatureAlgorithm) {
            this.signatureAlgorithm = signatureAlgorithm;
            return this;
        }

        public Builder setKeyAlgorithm(String keyAlgorithm) {
            this.keyAlgorithm = keyAlgorithm;
            return this;
        }

        public Builder setKeySize(Integer keySize) {
            this.keySize = keySize;
            return this;
        }

        public Scheme build() {
            return new Scheme(this.name, this.certificate, this.privateKey, this.signatureAlgorithm,
                this.keyAlgorithm, this.keySize);
        }

    }

    @Override
    public String toString() {
        PrivateKey pkey = this.privateKey();
        String keyInfo = pkey != null ? String.format("<%s key>", pkey.getAlgorithm()) : "-null-";

        X509Certificate cert = this.certificate();
        String certInfo = cert != null ? cert.getSerialNumber().toString() : "-null-";

        return String.format("Scheme [name: %s, pkey: %s, cert: %s, sigAlgo: %s, keyAlgo: %s, keySize: %s]",
            this.name(), keyInfo, certInfo, this.signatureAlgorithm(), this.keyAlgorithm(), this.keySize());
    }

}
