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

import org.candlepin.pki.CertificateReader;
import org.candlepin.pki.Scheme;
import org.candlepin.pki.SignatureException;
import org.candlepin.pki.Signer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.security.Signature;
import java.util.Objects;

import javax.inject.Inject;


/**
 * Class meant for signing payloads and verification these signatures.
 */
public class JcaSigner implements Signer {
    private static final Logger log = LoggerFactory.getLogger(JcaSigner.class);

    // Size of the byte buffer to use to consume blocks of data from input streams
    private static final int BUFFER_SIZE = 4096;

    private static final String SIGNATURE_SCHEME_NAME = "rsa";
    private static final String KEY_ALGORITHM = "rsa";
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

    private final CertificateReader certificateAuthority;
    private final Scheme signatureScheme;

    // TODO: This constructor will be replaced when the CertificateAuthority is implemented

    @Inject
    public JcaSigner(CertificateReader reader) {
        this.certificateAuthority = Objects.requireNonNull(reader);

        this.signatureScheme = new Scheme.Builder()
            .setName(SIGNATURE_SCHEME_NAME)
            .setPrivateKey(this.certificateAuthority.getCaKey())
            .setCertificate(this.certificateAuthority.getCACert())
            .setSignatureAlgorithm(SIGNATURE_ALGORITHM)
            .setKeyAlgorithm(KEY_ALGORITHM)
            .setKeySize(4096)
            .build();
    }

    @Override
    public Scheme getSignatureScheme() {
        return this.signatureScheme;
    }

    /**
     * Compute a SHA256withRSA digital signature on an inputStream.  The digest is signed
     * with the CA key retrieved using CertificateReader.
     *
     * @param input an input stream to sign
     * @return a byte array of the SHA256withRSA digital signature
     */
    @Override
    public byte[] sign(InputStream input) {
        try {
            Signature signature = Signature.getInstance(this.signatureScheme.signatureAlgorithm());
            signature.initSign(this.certificateAuthority.getCaKey());

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
            Signature signature = Signature.getInstance(this.signatureScheme.signatureAlgorithm());
            signature.initSign(this.certificateAuthority.getCaKey());
            signature.update(data);

            return signature.sign();
        }
        catch (Exception e) {
            throw new SignatureException(e);
        }
    }

}
