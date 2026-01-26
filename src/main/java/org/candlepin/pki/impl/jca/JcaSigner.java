/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
import org.candlepin.pki.SignatureException;
import org.candlepin.pki.Signer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.security.Signature;
import java.util.Objects;



/**
 * Class meant for signing payloads and verification these signatures.
 */
public class JcaSigner implements Signer {
    private static final Logger log = LoggerFactory.getLogger(JcaSigner.class);

    // Size of the byte buffer to use to consume blocks of data from input streams
    private static final int BUFFER_SIZE = 4096;

    private final java.security.Provider securityProvider;
    private final Scheme scheme;

    public JcaSigner(java.security.Provider securityProvider, Scheme scheme) {
        this.securityProvider = Objects.requireNonNull(securityProvider);
        this.scheme = Objects.requireNonNull(scheme);

        if (this.scheme.privateKey().isEmpty()) {
            throw new IllegalStateException("scheme does not include a private key");
        }
    }

    @Override
    public Scheme getCryptoScheme() {
        return this.scheme;
    }

    @Override
    public byte[] sign(InputStream input) {
        try {
            Signature signature = Signature.getInstance(this.scheme.signatureAlgorithm(),
                this.securityProvider);
            signature.initSign(this.scheme.privateKey().get());

            byte[] bytes = new byte[BUFFER_SIZE];
            int read;

            while ((read = input.read(bytes)) != -1) {
                signature.update(bytes, 0, read);
            }

            return signature.sign();
        }
        catch (Exception e) {
            throw new SignatureException(e);
        }
    }

    @Override
    public byte[] sign(byte[] data) {
        try {
            Signature signature = Signature.getInstance(this.scheme.signatureAlgorithm(),
                this.securityProvider);
            signature.initSign(this.scheme.privateKey().get());

            signature.update(data);

            return signature.sign();
        }
        catch (Exception e) {
            throw new SignatureException(e);
        }
    }

}
