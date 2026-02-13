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
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;



/**
 * A JCA-based implementation of the SignatureValidator; providing signature and data validation functionality
 * independent of any individual cryptographic provider.
 */
public class JcaSignatureValidator implements SignatureValidator {
    private static final Logger log = LoggerFactory.getLogger(JcaSignatureValidator.class);

    // Size of the byte buffer to use to consume blocks of data from input streams
    private static final int BUFFER_SIZE = 4096;

    private final java.security.Provider securityProvider;
    private final Scheme scheme;

    private byte[] signature;
    private Set<X509Certificate> additionalCerts;

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

        this.signature = null;
        this.additionalCerts = new HashSet<>();
    }

    @Override
    public Scheme getCryptoScheme() {
        return this.scheme;
    }

    @Override
    public SignatureValidator withAdditionalCertificates(Collection<X509Certificate> certs) {
        if (certs == null || certs.isEmpty()) {
            return this;
        }

        certs.stream()
            .filter(Objects::nonNull)
            .forEach(this.additionalCerts::add);

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

    @Override
    public boolean validate(File file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("file is null");
        }

        if (this.signature == null || this.signature.length < 1) {
            throw new IllegalStateException("signature has not yet been configured");
        }

        CheckedPredicate<X509Certificate, IOException> predicate = certificate -> {
            try (InputStream istream = new FileInputStream(file)) {
                Signature jcaSignature = Signature.getInstance(this.scheme.signatureAlgorithm(),
                    this.securityProvider);
                jcaSignature.initVerify(certificate);

                byte[] buffer = new byte[BUFFER_SIZE];
                int read;

                while ((read = istream.read(buffer)) != -1) {
                    jcaSignature.update(buffer, 0, read);
                }

                return jcaSignature.verify(this.signature);
            }
            catch (java.security.SignatureException | NoSuchAlgorithmException | InvalidKeyException e) {
                throw new org.candlepin.pki.SignatureException(
                    "Unexpected exception occurred while verifying signature", e);
            }
        };

        return Stream.concat(Stream.of(this.scheme.certificate()), this.additionalCerts.stream())
            .filter(CheckedPredicate.rethrow(predicate))
            .findFirst()
            .isPresent();
    }

    @Override
    public boolean validate(byte[] data) {
        if (this.signature == null || this.signature.length < 1) {
            throw new IllegalStateException("signature has not yet been configured");
        }

        Predicate<X509Certificate> predicate = certificate -> {
            try {
                Signature jcaSignature = Signature.getInstance(this.scheme.signatureAlgorithm(),
                    this.securityProvider);
                jcaSignature.initVerify(certificate);

                if (data != null) {
                    jcaSignature.update(data);
                }

                return jcaSignature.verify(this.signature);
            }
            catch (java.security.SignatureException | NoSuchAlgorithmException | InvalidKeyException e) {
                throw new org.candlepin.pki.SignatureException(
                    "Unexpected exception occurred while verifying signature", e);
            }
        };

        return Stream.concat(Stream.of(this.scheme.certificate()), this.additionalCerts.stream())
            .filter(predicate)
            .findFirst()
            .isPresent();
    }

}
