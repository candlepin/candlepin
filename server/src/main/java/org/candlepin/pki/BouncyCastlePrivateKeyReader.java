/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMDecryptor;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.operator.OperatorCreationException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.Enumeration;
import java.util.Map;

import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Implementation of PrivateKeyReader using BouncyCastle as the crypto provider.
 */
public class BouncyCastlePrivateKeyReader extends PrivateKeyReader {
    @Override
    protected PrivateKeyPemParser pkcS8EncryptedPrivateKeyPemParser() {
        return new PKCS8EncryptedPrivateKeyPemParser();
    }

    @Override
    protected PrivateKeyPemParser pkcS8PrivateKeyPemParser() {
        return new PKCS8PrivateKeyPemParser();
    }

    @Override
    protected PrivateKeyPemParser pkcs1EncryptedPrivateKeyPemParser() {
        return new PKCS1EncryptedPrivateKeyPemParser();
    }

    @Override
    protected PrivateKeyPemParser pkcs1PrivateKeyPemParser() {
        return new PKCS1PrivateKeyPemParser();
    }

    /**
     * Read an encrypted PKCS8.  This does not work currently due to
     * https://bugs.openjdk.java.net/browse/JDK-8076999
     */
    private static class PKCS8EncryptedPrivateKeyPemParser implements PrivateKeyPemParser {
        @Override
        public RSAPrivateKey decode(byte[] der, String password, Map<String, String> headers)
            throws IOException {
            try {
                FileOutputStream fos = new FileOutputStream(new File("/tmp/xyz.der"));
                fos.write(der);

                // PBE stands for password based encryption
                PBEKeySpec pbeKeySpec = new PBEKeySpec(getPassword(password));
                EncryptedPrivateKeyInfo encryptedInfo = new EncryptedPrivateKeyInfo(der);
                SecretKeyFactory skf = SecretKeyFactory.getInstance(encryptedInfo.getAlgName());
                Key secret = skf.generateSecret(pbeKeySpec);
                PKCS8EncodedKeySpec pkcsSpec = encryptedInfo.getKeySpec(secret);
                KeyFactory kf = KeyFactory.getInstance("RSA");
                return (RSAPrivateKey) kf.generatePrivate(pkcsSpec);
            }
            catch (NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException e) {
                throw new IOException("Could not read key", e);
            }
        }
    }

    private static class PKCS8PrivateKeyPemParser implements PrivateKeyPemParser {
        @Override
        public RSAPrivateKey decode(byte[] der, String password, Map<String, String> headers)
            throws IOException {
            try {
                PKCS8EncodedKeySpec kspec = new PKCS8EncodedKeySpec(der);
                KeyFactory kf = KeyFactory.getInstance("RSA");
                return (RSAPrivateKey) kf.generatePrivate(kspec);
            }
            catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                throw new IOException("Could not read key", e);
            }
        }
    }

    /**
     * Read an OpenSSL created RSA key.  For unencrypted RSA keys, OpenSSL uses the PKCS1 format defined in
     * RFC 8017.  Currently this uses BouncyCastle to parse the ASN1, but if we switch providers, using their
     * ASN1 parsing classes should be straight-forward.
     */
    private static class PKCS1PrivateKeyPemParser implements PrivateKeyPemParser {
        @Override
        public RSAPrivateKey decode(byte[] der, String password, Map<String, String> headers)
            throws IOException {
            ASN1Sequence seq = ASN1Sequence.getInstance(der);
            Enumeration asn1 = seq.getObjects();

            BigInteger version = ((ASN1Integer) asn1.nextElement()).getValue();
            if (version.intValue() != 0 && version.intValue() != 1)
            {
                throw new IllegalArgumentException("wrong version for RSA private key");
            }

            // See RFC 8017 Appendix A.1.2
            BigInteger modulus = ((ASN1Integer) asn1.nextElement()).getValue();
            BigInteger publicExponent = ((ASN1Integer) asn1.nextElement()).getValue();
            BigInteger privateExponent = ((ASN1Integer) asn1.nextElement()).getValue();
            BigInteger primeP = ((ASN1Integer) asn1.nextElement()).getValue();
            BigInteger primeQ = ((ASN1Integer) asn1.nextElement()).getValue();
            BigInteger primeExponentP = ((ASN1Integer) asn1.nextElement()).getValue();
            BigInteger primeExponentQ = ((ASN1Integer) asn1.nextElement()).getValue();
            BigInteger coefficient = ((ASN1Integer) asn1.nextElement()).getValue();

            if (asn1.hasMoreElements()) {
                /* Near as I can tell, multi-prime RSA keys are uncommon.  I can't even figure out how to
                 * generate them via the OpenSSL CLI.  If we did want to support them, we'd need to do
                 * something like:
                 *
                 * ASN1Sequence otherPrimeInfos = (ASN1Sequence) asn1.nextElement();
                 * RSAOtherPrimeInfo[] otherPrimes = new RSAOtherPrimeInfo[otherPrimeInfos.size()];
                 * for (ASN1Encodable e : otherPrimeInfos.toArray()) {
                 * // Create RSAOtherPrimeInfo object here
                 *  }
                 * RSAMultiPrimePrivateCrtKeySpec spec = new RSAMultiPrimePrivateCrtKeySpec(modulus,
                 *  publicExponent, privateExponent, primeP, primeQ, primeExponentP, primeExponentQ,
                 *  coefficient, otherPrimeInfos.);
                 */
                throw new IOException("RSA multi-prime keys are not supported");
            }

            RSAPrivateCrtKeySpec spec = new RSAPrivateCrtKeySpec(modulus, publicExponent, privateExponent,
                primeP, primeQ, primeExponentP, primeExponentQ, coefficient);
            try {
                KeyFactory kf = KeyFactory.getInstance("RSA");
                return (RSAPrivateKey) kf.generatePrivate(spec);
            }
            catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                throw new IOException("Could not read key", e);
            }
        }
    }

    /**
     * Read an OpenSSL created and encrypted private key.  OpenSSL encrypts RSA keys in a non-standard
     * format that uses plaintext headers to define the encryption algorithm and the initialization vector
     * for that algorithm.  This class parses those headers, decrypts the base64 encoded data (which is not
     * in an ASN1 format) and then sends it on to PKCS1PrivateKeyPemParser.
     *
     * Note this class uses BouncyCastle to do the decryption and should be replaced if we switch crypto
     * providers.
     */
    private static class PKCS1EncryptedPrivateKeyPemParser implements PrivateKeyPemParser {
        @Override
        public RSAPrivateKey decode(byte[] data, String password, Map<String, String> headers)
            throws IOException {

            String algoName = null;
            byte[] iv = null;

            for (Map.Entry<String, String> header : headers.entrySet()) {
                if (header.getKey().equals("DEK-Info")) {
                    int index = header.getValue().indexOf(',');
                    algoName = header.getValue().substring(0, index);
                    try {
                        iv = Hex.decodeHex(header.getValue().substring(index + 1).toCharArray());
                    }
                    catch (DecoderException e) {
                        throw new IOException("Could not read key", e);
                    }
                }
            }

            if (algoName == null || iv == null) {
                throw new IOException("Could not read key headers");
            }

            PEMDecryptorProvider provider = new JcePEMDecryptorProviderBuilder()
                .setProvider(new BouncyCastleProvider())
                .build(getPassword(password));

            try {
                PEMDecryptor keyDecryptor = provider.get(algoName);
                byte[] decrypted = keyDecryptor.decrypt(data, iv);
                return new PKCS1PrivateKeyPemParser().decode(decrypted, null, null);
            }
            catch (OperatorCreationException e) {
                throw new IOException("Could not read key", e);
            }
        }
    }
}
