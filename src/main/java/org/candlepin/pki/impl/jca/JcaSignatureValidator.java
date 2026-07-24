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
package org.candlepin.pki.impl.jca;

import org.candlepin.pki.Scheme;
import org.candlepin.pki.SignatureValidator;
import org.candlepin.util.function.CheckedPredicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;



/**
 * A JCA-based implementation of the SignatureValidator; providing signature and data validation functionality
 * independent of any individual cryptographic provider.
 */
public class JcaSignatureValidator implements SignatureValidator {
    private static final Logger log = LoggerFactory.getLogger(JcaSignatureValidator.class);

    // Size of the byte buffer to use to consume blocks of data from input streams
    private static final int BUFFER_SIZE = 4096;

    // Bit in the X.509 key usage extension that declares the cert as usable for digital signatures
    private static final int DIGITAL_SIGNATURE_USAGE_BIT = 0;

    private final java.security.Provider securityProvider;
    private final Scheme scheme;
    private final String signatureAlgorithm;

    private Set<X509Certificate> certificates;
    private byte[] signature;

    /**
     * A simple functional interface to assist in abstracting distinct logic away from the generalized
     * validation routine. Allows for generically declaring an exception to be rethrown, while still throwing
     * the java.security.SignatureException all Signature.update operations are expected to throw.
     */
    @FunctionalInterface
    private interface SignatureUpdater<E extends Exception> {
        /**
         * Updates the given Signature instance with the data whose signature is to be verified.
         * Implementations must call Signature.update at least once as part of this operation.
         *
         * @param verifier
         *  the Signature instance to be updated with data for calculating the signature via the
         *  Signature.update method; cannot be null
         *
         * @throws SignatureException
         *  if a SignatureException occurs while updating the signature
         *
         * @throws E
         *  if the targeted exception occurs as a result of the implementation of this method
         */
        void update(Signature verifier) throws SignatureException, E;
    }

    /**
     * Creates a new signature validator for the given cryptographic scheme.
     *
     * @param securityProvider
     *  the security provider to use for all crypto operations; cannot be null
     *
     * @param scheme
     *  the scheme to use for validating signatures; cannot be null
     */
    public JcaSignatureValidator(java.security.Provider securityProvider, Scheme scheme) {
        this.securityProvider = Objects.requireNonNull(securityProvider);
        this.scheme = Objects.requireNonNull(scheme);
        this.signatureAlgorithm = scheme.signatureAlgorithm();

        this.certificates = new HashSet<>();
        this.signature = null;

        this.certificates.add(scheme.certificate());
    }

    /**
     * Creates a new signature validator using the specified signature algorithm. One or more certificates
     * will need to be provided via the .withAdditionalCertificate method before validation can be performed.
     *
     * @deprecated
     * This constructor should not be used outside of extreme circumstances where the algorithm is known, but
     * a scheme is not available (e.g. legacy manifest import). The algorithm name is not validated at
     * construction time, and will not be checked until beginning the signature validation operation.
     *
     * Additionally, signature validators created in this way will violate the interface specification with
     * respect to the .getCryptoScheme method -- as there is no scheme provided, it will return null in cases
     * where callers may be expecting a valid scheme.
     *
     * @param securityProvider
     *  the security provider to use for all crypto operations; cannot be null
     *
     * @param signatureAlgorithm
     *  the algorithm to use for performing signature validation
     */
    @Deprecated
    public JcaSignatureValidator(java.security.Provider securityProvider, String signatureAlgorithm) {
        this.securityProvider = Objects.requireNonNull(securityProvider);
        this.scheme = null;
        this.signatureAlgorithm = Objects.requireNonNull(signatureAlgorithm);

        this.certificates = new HashSet<>();
        this.signature = null;
    }

    @Override
    public Scheme getCryptoScheme() {
        // Impl note: this will violate the interface spec if the validator was created manually without a
        // scheme.
        return this.scheme;
    }

    @Override
    public SignatureValidator withAdditionalCertificates(Collection<X509Certificate> certs) {
        if (certs == null || certs.isEmpty()) {
            return this;
        }

        certs.stream()
            .filter(Objects::nonNull)
            .sequential()
            .forEach(this.certificates::add);

        return this;
    }

    @Override
    public SignatureValidator forSignature(byte[] signature) {
        if (signature == null || signature.length == 0) {
            throw new IllegalArgumentException("signature is null or empty");
        }

        this.signature = signature;
        return this;
    }

    private <E extends Exception> boolean performValidation(SignatureUpdater<E> updater) throws E {
        if (this.signature == null || this.signature.length < 1) {
            throw new IllegalStateException("signature has not yet been configured");
        }

        CheckedPredicate<X509Certificate, E> predicate = certificate -> {
            try {
                // Verify this cert can be used for digital signatures. If the cert doesn't define key usage
                // or this method otherwise returns null, treat it as permissive.
                boolean[] usages = certificate.getKeyUsage();
                if (usages != null && !usages[DIGITAL_SIGNATURE_USAGE_BIT]) {
                    return false;
                }

                Signature verifier = Signature.getInstance(this.signatureAlgorithm, this.securityProvider);
                verifier.initVerify(certificate);

                updater.update(verifier);

                return verifier.verify(this.signature);
            }
            catch (InvalidKeyException e) {
                // Key and cert don't match signature scheme. Move on to next cert
                log.debug("Certificate not usable for signature validation with algorithm: <cert: {}>, {}",
                    certificate.getSerialNumber(), this.signatureAlgorithm);
                return false;
            }
            catch (java.security.SignatureException | NoSuchAlgorithmException e) {
                throw new org.candlepin.pki.SignatureException(
                    "Unexpected exception occurred while verifying signature", e);
            }
        };

        return this.certificates.stream()
            .filter(CheckedPredicate.rethrow(predicate))
            .findFirst()
            .isPresent();
    }

    @Override
    public boolean validate(File file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("file is null");
        }

        SignatureUpdater<IOException> updater = verifier -> {
            try (InputStream istream = new FileInputStream(file)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;

                while ((read = istream.read(buffer)) != -1) {
                    verifier.update(buffer, 0, read);
                }
            }
        };

        return this.performValidation(updater);
    }

    @Override
    public boolean validate(byte[] data) {
        SignatureUpdater<RuntimeException> updater = verifier -> {
            if (data != null) {
                verifier.update(data);
            }
        };

        return this.performValidation(updater);
    }

}
