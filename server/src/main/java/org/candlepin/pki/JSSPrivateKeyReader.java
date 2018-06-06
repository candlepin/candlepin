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

import org.mozilla.jss.netscape.security.util.DerInputStream;
import org.mozilla.jss.netscape.security.util.DerValue;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Implementation of ProviderBasedPrivateKeyReader using JSS as the crypto provider.
 */
public class JSSPrivateKeyReader extends ProviderBasedPrivateKeyReader {
    @Override
    protected PrivateKeyPemParser pkcs1EncryptedPrivateKeyPemParser() {
        return new PKCS1EncryptedPrivateKeyPemParser();
    }

    @Override
    protected PrivateKeyPemParser pkcs1PrivateKeyPemParser() {
        return new PKCS1PrivateKeyPemParser();
    }

    /**
     * Read an OpenSSL created RSA private key.  For unencrypted RSA keys, OpenSSL uses the PKCS1 format
     * defined in RFC 8017.  This method is operating on RSA private keys with the additional
     * parameters used in Chinese remainder theorem optimizations.
     */
    private static class PKCS1PrivateKeyPemParser implements PrivateKeyPemParser {
        @Override
        public RSAPrivateKey decode(byte[] der, String password, Map<String, String> headers)
            throws IOException {

            DerValue[] seqItems = new DerInputStream(der).getSequence(9);

            BigInteger version = seqItems[0].getInteger().toBigInteger();
            if (version.intValue() != 0 && version.intValue() != 1) {
                throw new IllegalArgumentException("wronger version for RSA private key");
            }

            // See RFC 8017 Appendix A.1.2
            BigInteger modulus = seqItems[1].getInteger().toBigInteger();
            BigInteger publicExponent = seqItems[2].getInteger().toBigInteger();
            BigInteger privateExponent = seqItems[3].getInteger().toBigInteger();
            BigInteger primeP = seqItems[4].getInteger().toBigInteger();
            BigInteger primeQ = seqItems[5].getInteger().toBigInteger();
            BigInteger primeExponentP = seqItems[6].getInteger().toBigInteger();
            BigInteger primeExponentQ = seqItems[7].getInteger().toBigInteger();
            BigInteger coefficient = seqItems[8].getInteger().toBigInteger();

            if (seqItems.length > 9) {
                /* Near as I can tell, multi-prime RSA keys are uncommon.  I can't even figure out how to
                 * generate them via the OpenSSL CLI.  If we did want to support them, we'd need to do some
                 * additional parsing and use the RSAMultiPrimePrivateCrtKeySpec class.
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
     * This class is not very generous in what is accepts.  It will handle 3DES and AES both only in CBC mode.
     * OpenSSL does give options to use other symmetric ciphers to encrypt a key: Camellia, Aria, DES, and
     * IDEA but those should not be common choices and those ciphers are not supported in JCA (See
     * https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#Cipher).  I don't
     * see any way at all to specify the cipher mode, but the BouncyCastle PEMUtilities class that this
     * class borrows from supports ECB and OFB.  I'm going to omit those modes since I have no way to
     * generate encrypted keys to test them.  If necessary, however, OFB uses NoPadding and ECB uses
     * PKCS5Padding.
     */
    private static class PKCS1EncryptedPrivateKeyPemParser extends
        AbstractPKCS1EncryptedPrivateKeyPemParser {
        @Override
        public RSAPrivateKey decode(byte[] der, String password, Map<String, String> headers)
            throws IOException {
            readHeaders(headers);  // prime the algoName and iv class fields

            if (algoName == null || iv == null) {
                throw new IOException("Could not read key headers");
            }

            int keyLength;
            String cipherAlgorithm;
            String paddingType;
            String blockMode;
            byte[] salt = iv;

            if (algoName.endsWith("-CBC")) {
                blockMode = "CBC";
                paddingType = "PKCS5Padding";
            }
            else {
                throw new UnsupportedOperationException("Unsupported block cipher mode for private key");
            }

            if (algoName.startsWith("DES-EDE3")) {
                keyLength = 24 * 8;
                cipherAlgorithm = "DESede";
            }
            else if (algoName.startsWith("AES-")) {
                cipherAlgorithm = "AES";
                if (salt.length > 8) {
                    salt = new byte[8];
                    System.arraycopy(iv, 0, salt, 0, 8);
                }

                if (algoName.startsWith("AES-128-")) {
                    keyLength = 128;
                }
                else if (algoName.startsWith("AES-192-")) {
                    keyLength = 192;
                }
                else if (algoName.startsWith("AES-256-")) {
                    keyLength = 256;
                }
                else {
                    throw new IOException("Unknown AES encryption type for private key");
                }
            }
            else {
                throw new UnsupportedOperationException("Unsupported cipher: " + algoName +
                    ". Only AES and 3DES are supported");
            }

            String cipherSpec = cipherAlgorithm + "/" + blockMode + "/" + paddingType;
            byte[] decrypted;

            try {
                byte[] key = generateDerivedKey(
                    password.getBytes(StandardCharsets.UTF_8),
                    salt,
                    keyLength / 8
                );
                SecretKey secret = new SecretKeySpec(key, cipherAlgorithm);
                Cipher cipher = Cipher.getInstance(cipherSpec);
                cipher.init(Cipher.DECRYPT_MODE, secret, new IvParameterSpec(iv));
                decrypted = cipher.doFinal(der);
            }
            catch (NoSuchAlgorithmException | NoSuchPaddingException |
                InvalidKeyException | IllegalBlockSizeException | BadPaddingException |
                InvalidAlgorithmParameterException e) {
                throw new IOException("Could not decrypt private key", e);
            }

            return new PKCS1PrivateKeyPemParser().decode(decrypted, null, null);
        }

        /**
         * OpenSSL has a custom key derivation function that we need to replicate to determine the actual
         * secret key we need to decrypt the private key.  See
         * https://en.wikipedia.org/wiki/Key_derivation_function and
         * https://www.openssl.org/docs/manmaster/man3/EVP_BytesToKey.html
         *
         * This implementation is adapted from BouncyCastle's OpenSSLPBEParametersGenerator class.
         *
         * @param password the password used
         * @param iv the initialization vector
         * @param bytesNeeded the size of the derived key
         * @return a byte array we can use to decrypt the private key.
         */
        private byte[] generateDerivedKey(byte[] password, byte[] iv, int bytesNeeded) throws
            NoSuchAlgorithmException {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] buf = new byte[md5.getDigestLength()];
            byte[] key = new byte[bytesNeeded];
            int offset = 0;

            for (;;) {
                md5.update(password);
                md5.update(iv);

                buf = md5.digest();

                int len = (bytesNeeded > buf.length) ? buf.length : bytesNeeded;
                System.arraycopy(buf, 0, key, offset, len);
                offset += len;

                // check if we need any more
                bytesNeeded -= len;
                if (bytesNeeded == 0)
                {
                    break;
                }

                // do another round
                md5.reset();
                md5.update(buf);
            }

            return key;
        }
    }
}
