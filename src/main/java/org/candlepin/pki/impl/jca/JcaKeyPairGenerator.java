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

import org.candlepin.pki.KeyPairGenerator;
import org.candlepin.pki.Scheme;

import java.security.InvalidParameterException;
import java.security.KeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;



/**
 * JCA implementation of the KeyPairGenerator
 */
public class JcaKeyPairGenerator implements KeyPairGenerator {

    private final java.security.Provider securityProvider;
    private final Scheme scheme;

    /**
     * Creates a new key pair generator backed by the given security provider and configured to use the
     * specified scheme.
     *
     * @param securityProvider
     *  the security provider to use when generating key pairs; cannot be null
     *
     * @param scheme
     *  the cryptographic scheme to use for generating key pairs; cannot be null
     */
    public JcaKeyPairGenerator(java.security.Provider securityProvider, Scheme scheme) {
        this.securityProvider = Objects.requireNonNull(securityProvider);
        this.scheme = Objects.requireNonNull(scheme);
    }

    private KeyException buildKeyException(String msg, Exception cause) {
        String keyAlgoString = this.scheme.keySize()
            .map(size -> String.format("%s, key size: %d", this.scheme.keyAlgorithm(), size))
            .orElse(this.scheme.keyAlgorithm());

        String exceptionMessage = String.format("Key pair generation failed: %s (algorithm: %s)",
            msg, keyAlgoString);

        return new KeyException(exceptionMessage, cause);
    }

    @Override
    public Scheme getCryptoScheme() {
        return this.scheme;
    }

    @Override
    public KeyPair generateKeyPair() throws KeyException {
        try {
            java.security.KeyPairGenerator generator = java.security.KeyPairGenerator.getInstance(
                this.scheme.keyAlgorithm(), this.securityProvider);

            this.scheme.keySize()
                .ifPresent(generator::initialize);

            return generator.generateKeyPair();
        }
        catch (InvalidParameterException e) {
            throw this.buildKeyException("unsupported key size", e);
        }
        catch (IllegalArgumentException e) {
            // The spec is a bit of a mess here. KeyPairGenerator.initialize is supposed to throw an
            // InvalidParameterException if the key size isn't supported, but BouncyCastle throws an
            // IllegalArgumentException instead. While InvalidParameterException IS an IAE, BouncyCastle does
            // not use it even while using more modern algorithms such as ML-DSA. And, unfortunately, the
            // KeyPairGenerator.getInstance method also throws an IAE if the security provider is null, and a
            // NPE if the algorithm is null. This means we don't have a ton of clarity here when BouncyCastle
            // is in the mix. If we get an IAE, we'll throw a generic message, even if it's probably a key
            // size issue.
            throw this.buildKeyException("invalid algorithm configuration", e);
        }
        catch (NoSuchAlgorithmException e) {
            throw this.buildKeyException("no such algorithm", e);
        }
    }

}

