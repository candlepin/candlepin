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
package org.candlepin.pki.impl;

import org.candlepin.pki.CertificateReader;
import org.candlepin.pki.SignatureFailedException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Objects;

import javax.inject.Inject;


/**
 * Class meant for signing payloads and verification these signatures.
 */
public class Signer {
    private static final Logger log = LoggerFactory.getLogger(Signer.class);

    private final CertificateReader certificateAuthority;

    @Inject
    public Signer(CertificateReader reader) {
        this.certificateAuthority = Objects.requireNonNull(reader);
    }

    /**
     * Compute a MLDSA digital signature on an inputStream.  The digest is signed
     * with the CA key retrieved using CertificateReader.
     *
     * @param input an input stream to sign
     * @return a byte array of the MLDSA digital signature
     */
    public byte[] sign(InputStream input) {
        try {
            Signature signature = Signature.getInstance("MLDSA", "BC");
            signature.initSign(this.certificateAuthority.getCaKey());

            updateSignature(input, signature);
            return signature.sign();
        }
        catch (Exception e) {
            throw new SignatureFailedException("Failed to create signature!", e);
        }
    }

    public boolean verifySignature(File input, byte[] signedHash) throws IOException {
        log.debug("Verify against: {}", certificateAuthority.getCACert().getSerialNumber());

        try (InputStream inputStream = new FileInputStream(input)) {
            if (verifySHA256WithRSAHash(inputStream, signedHash, certificateAuthority.getCACert())) {
                return true;
            }
        }
        for (X509Certificate cert : certificateAuthority.getUpstreamCACerts()) {
            log.debug("Verify against: {}", cert.getSerialNumber());

            try (InputStream inputStream = new FileInputStream(input)) {
                if (verifySHA256WithRSAHash(inputStream, signedHash, cert)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Verify a digital signature.  The method calculates a digital signature using the SHA256withRSA
     * algorithm (and the public key from the certificate parameter) and then compares it with the signature
     * passed in through the signedHas parameter.
     * @param input input to verify
     * @param signedHash an existing signature to verify
     * @param certificate a certificate with the public key to use for verification
     * @return if the calculated signature matches the provided signature
     */
    private boolean verifySHA256WithRSAHash(InputStream input, byte[] signedHash, Certificate certificate) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(certificate);

            updateSignature(input, signature);
            return signature.verify(signedHash);
        }
        catch (SignatureException se) {
            return false;
        }
        catch (Exception e) {
            throw new SignatureFailedException("Failed to verify signature!", e);
        }
    }

    private void updateSignature(InputStream input, Signature signature)
        throws IOException, SignatureException {

        byte[] dataBytes = new byte[4096];
        int nread = 0;

        while ((nread = input.read(dataBytes)) != -1) {
            signature.update(dataBytes, 0, nread);
        }
    }

}
