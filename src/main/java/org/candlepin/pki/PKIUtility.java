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
package org.candlepin.pki;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Date;
import java.util.List;
import java.util.Set;


import org.apache.log4j.Logger;

/**
 * PKIUtility
 */
public abstract class PKIUtility {
    private static Logger log = Logger.getLogger(PKIUtility.class);

    // TODO : configurable?
    private static final int RSA_KEY_SIZE = 2048;
    protected static final String SIGNATURE_ALGO = "SHA1WITHRSA";
    protected PKIReader reader;
    protected SubjectKeyIdentifierWriter subjectKeyWriter;

    public PKIUtility(PKIReader reader, SubjectKeyIdentifierWriter subjectKeyWriter) {
        this.reader = reader;
        this.subjectKeyWriter = subjectKeyWriter;
    }

    public abstract X509Certificate createX509Certificate(String dn,
        Set<X509ExtensionWrapper> extensions, Set<X509ByteExtensionWrapper> byteExtensions,
        Date startDate, Date endDate,
        KeyPair clientKeyPair, BigInteger serialNumber, String alternateName)
        throws GeneralSecurityException, IOException;

    /**
     * Generate crl.
     *
     * @param entries the entries
     * @return the x509 crl
     */
    public abstract X509CRL createX509CRL(List<X509CRLEntryWrapper> entries,
        BigInteger crlNumber);

    public KeyPair decodeKeys(byte[] privKeyBits, byte[] pubKeyBits)
        throws InvalidKeySpecException, NoSuchAlgorithmException {

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        // build the private key
        PrivateKey privKey = keyFactory
            .generatePrivate(new PKCS8EncodedKeySpec(privKeyBits));
        // build the public key
        PublicKey pubKey = keyFactory.generatePublic(new X509EncodedKeySpec(
            pubKeyBits));
        // make them a key pair
        return new KeyPair(pubKey, privKey);
    }

    public KeyPair generateNewKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(RSA_KEY_SIZE);
        return generator.generateKeyPair();
    }

    /**
     * Take an X509Certificate object and return a byte[] of the certificate,
     * PEM encoded
     * @param cert
     * @return PEM-encoded bytes of the certificate
     * @throws IOException if there is i/o problem
     */
    public abstract byte[] getPemEncoded(X509Certificate cert) throws IOException;

    public abstract byte[] getPemEncoded(Key key) throws IOException;

    public abstract byte[] getPemEncoded(X509CRL crl) throws IOException;

    public static X509Certificate createCert(byte[] certData) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X509");
            X509Certificate cert = (X509Certificate) cf
            .generateCertificate(new ByteArrayInputStream(certData));
            return cert;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

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

    public boolean verifySHA256WithRSAHashWithUpstreamCACert(
        InputStream input, byte[] signedHash) throws CertificateException, IOException {
        return verifySHA256WithRSAHash(input, signedHash, reader.getUpstreamCACert());
    }

    public boolean verifySHA256WithRSAHash(
            InputStream input, byte[] signedHash, Certificate certificate) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(certificate.getPublicKey());

            updateSignature(input, signature);
            return signature.verify(signedHash);
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


    public abstract String decodeDERValue(byte[] value);
}
