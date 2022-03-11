/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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

import org.candlepin.config.Configuration;
import org.candlepin.model.Consumer;
import org.candlepin.pki.CertificateReader;
import org.candlepin.pki.PKIUtility;
import org.candlepin.pki.SubjectKeyIdentifierWriter;
import org.candlepin.pki.X509ByteExtensionWrapper;
import org.candlepin.pki.X509ExtensionWrapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyException;
import java.security.KeyPair;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Set;



/**
 * ProviderBasedPKIUtility is an abstract class implementing functionality in PKIUtility that only relies
 * on JCA classes and interfaces.  Any method that requires access to an underlying cryptographic provider
 * is declared abstract.  If we ever switch cryptographic providers again, it should just be a matter of
 * extending this class, implementing required methods with the new provider, and changing some Guice
 * bindings.
 */
public abstract class ProviderBasedPKIUtility implements PKIUtility {
    private static final Logger log = LoggerFactory.getLogger(ProviderBasedPKIUtility.class);

    protected CertificateReader reader;
    protected SubjectKeyIdentifierWriter subjectKeyWriter;
    protected Configuration config;

    public ProviderBasedPKIUtility(CertificateReader reader, SubjectKeyIdentifierWriter writer,
        Configuration config) {

        this.reader = reader;
        this.subjectKeyWriter = writer;
        this.config = config;
    }

    @Override
    public abstract X509Certificate createX509Certificate(String dn,
        Set<X509ExtensionWrapper> extensions, Set<X509ByteExtensionWrapper> byteExtensions,
        Date startDate, Date endDate, KeyPair clientKeyPair, BigInteger serialNumber, String alternateName)
        throws GeneralSecurityException, IOException;

    /**
     * Take an X509Certificate object and return a byte[] of the certificate, PEM encoded
     * @param cert
     * @return PEM-encoded bytes of the certificate
     * @throws IOException if there is i/o problem
     */
    @Override
    public abstract byte[] getPemEncoded(X509Certificate cert) throws IOException;

    /**
     * Compute a SHA256withRSA digital signature on an inputStream.  The digest is signed
     * with the CA key retrieved using CertificateReader.
     * @param input an input stream to sign
     * @return a byte array of the SHA256withRSA digital signature
     */
    @Override
    public byte[] getSHA256WithRSAHash(InputStream input) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(reader.getCaKey());

            updateSignature(input, signature);
            return signature.sign();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean verifySHA256WithRSAHashAgainstCACerts(File input, byte[] signedHash)
        throws CertificateException, IOException {

        log.debug("Verify against: {}", reader.getCACert().getSerialNumber());

        if (verifySHA256WithRSAHash(new FileInputStream(input), signedHash,
            reader.getCACert())) {
            return true;
        }

        for (X509Certificate cert : reader.getUpstreamCACerts()) {
            log.debug("Verify against: {}", cert.getSerialNumber());

            try (InputStream istream = new FileInputStream(input)) {
                if (verifySHA256WithRSAHash(istream, signedHash, cert)) {
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
    public boolean verifySHA256WithRSAHash(InputStream input, byte[] signedHash, Certificate certificate) {
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
            throw new RuntimeException(e);
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

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract KeyPair generateKeyPair() throws KeyException;

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract KeyPair getConsumerKeyPair(Consumer consumer) throws KeyException;
}
