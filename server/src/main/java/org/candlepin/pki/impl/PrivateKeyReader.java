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
package org.candlepin.pki.impl;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64InputStream;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMDecryptor;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.operator.OperatorCreationException;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Class used to read a private key from a PKCS1 or PKCS8 file.  Inspired by the PemReader
 * and PEMParser classes in BouncyCastle (which is licensed under an equivalent to the MIT license).
 */
public class PrivateKeyReader {
    private static final String BEGIN = "-----BEGIN ";
    private static final String END = "-----END ";

    public PrivateKey read(String caKeyPath, String caKeyPassword) throws IOException {
        try (
            FileInputStream fis = new FileInputStream(caKeyPath)
        ) {
            return read(fis, caKeyPassword);
        }
    }

    public PrivateKey read(InputStream keyStream, String password) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(keyStream));
        String line = reader.readLine();

        while (line != null && !line.startsWith(BEGIN)) {
            line = reader.readLine();
        }

        if (line != null) {
            // "-----BEGIN RSA PRIVATE KEY-----" becomes "RSA PRIVATE KEY-----"
            line = line.substring(BEGIN.length());
            // Find the first occurrence of a hyphen
            int nextHyphen = line.indexOf('-');
            // Now we have something like "RSA PRIVATE KEY"
            String type = line.substring(0, nextHyphen);

            if (nextHyphen > 0) {
                return readPem(type, reader, password);
            }
        }

        throw new IOException("Could not read key");
    }

    /**
     * Read a PEM blob based on the type declared in the BEGIN block.  We have to deal with several different
     * formats: PCKS8 (both encrypted and unencrypted) and the non-standard PKCS1 format OpenSSL uses.  For an
     * unencrypted key, OpenSSL uses a normal PKCS1, but for an encrypted key it adds headers prior to the
     * PEM.  For example:
     *
     *-----BEGIN RSA PRIVATE KEY-----
     * Proc-Type: 4,ENCRYPTED
     * DEK-Info: AES-256-CBC,A4E76EB0315C87607649FB5E1FB975B1
     *
     * This non-standard format is described in OpenSSL's man page for "PEM_read_bio_PrivateKey".  But for
     * reference:
     *
     * The line beginning with Proc-Type contains the version and the protection on the encapsulated data.
     * The line beginning DEK-Info contains two comma separated values: the encryption algorithm name
     * and an initialization vector used by the cipher encoded as a set of hexadecimal digits.
     *
     * @param type the key type.  One of "RSA PRIVATE KEY", "PRIVATE KEY", or "ENCRYPTED PRIVATE KEY"
     * @param reader a reader for the PEM
     * @param password a password to use for decrypting if necessary
     * @return the PrivateKey from the PEM file
     * @throws IOException if anything goes wrong
     */
    protected PrivateKey readPem(String type, BufferedReader reader, String password) throws IOException {
        String line;
        String endMarker = END + type;
        StringBuilder buf = new StringBuilder();
        Map<String, String> headers = new HashMap<>();

        while ((line = reader.readLine()) != null) {
            if (line.indexOf(':') >= 0) {
                int index = line.indexOf(':');
                String header = line.substring(0, index);

                if (headers.containsKey(header)) {
                    // Near as I can tell, a header shouldn't be defined multiple times, but if it is we'll
                    // abort
                    throw new IOException("The header \"" + header + "\" appears multiple times in this key");
                }

                String value = line.substring(index + 1).trim();
                headers.put(header, value);
                continue;
            }

            if (line.contains(endMarker)) {
                break;
            }
            buf.append(line.trim());
        }

        if (line == null) {
            throw new IOException(endMarker + " not found");
        }

        String pem = buf.toString();
        switch (type) {
            case "RSA PRIVATE KEY":
                if (headers.isEmpty()) {
                    return new PKCS1PrivateKeyPemParser().decode(pem, null, null);
                }
                else {
                    return new PKCS1EncryptedPrivateKeyPemParser().decode(pem, password, headers);
                }
            case "PRIVATE KEY":
                return new PKCS8PrivateKeyPemParser().decode(pem, null, null);
            case "ENCRYPTED PRIVATE KEY":
                return new PKCS8EncryptedPrivateKeyPemParser().decode(pem, password, null);
            default:
                throw new IOException("Unrecognized type: " + type);
        }
    }

    /**
     * Interface for various private key encoding types
     */
    interface PrivateKeyPemParser {
        default PrivateKey decode(String pem, String password, Map<String, String> headers)
            throws IOException {
            try (
                InputStream derStream = new Base64InputStream(
                    new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
            ) {
                byte[] der = IOUtils.toByteArray(derStream);
                return decode(der, password, headers);
            }
        }

        PrivateKey decode(byte[] der, String password, Map<String, String> headers) throws IOException;

        default char[] getPassword(String password) {
            return (password != null) ? password.toCharArray() : null;
        }
    }

    /**
     * Read an encrypted PKCS8.  This does not work currently due to
     * https://bugs.openjdk.java.net/browse/JDK-8076999
     */
    private static class PKCS8EncryptedPrivateKeyPemParser implements PrivateKeyPemParser {
        @Override
        public PrivateKey decode(byte[] der, String password, Map<String, String> headers)
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
                return kf.generatePrivate(pkcsSpec);
            }
            catch (NoSuchAlgorithmException | InvalidKeySpecException | InvalidKeyException e) {
                throw new IOException("Could not read key", e);
            }
        }
    }

    private static class PKCS8PrivateKeyPemParser implements PrivateKeyPemParser {
        @Override
        public PrivateKey decode(byte[] der, String password, Map<String, String> headers)
            throws IOException {
            try {
                PKCS8EncodedKeySpec kspec = new PKCS8EncodedKeySpec(der);
                KeyFactory kf = KeyFactory.getInstance("RSA");
                return kf.generatePrivate(kspec);
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
        public PrivateKey decode(byte[] der, String password, Map<String, String> headers)
            throws IOException {
            ASN1Sequence seq = ASN1Sequence.getInstance(der);
            Enumeration asn1 = seq.getObjects();

            BigInteger v = ((ASN1Integer) asn1.nextElement()).getValue();
            if (v.intValue() != 0 && v.intValue() != 1)
            {
                throw new IllegalArgumentException("wrong version for RSA private key");
            }

            // See RFC 8017 A.1.2
            BigInteger version = v;
            BigInteger modulus = ((ASN1Integer) asn1.nextElement()).getValue();
            BigInteger publicExponent = ((ASN1Integer) asn1.nextElement()).getValue();
            BigInteger privateExponent = ((ASN1Integer) asn1.nextElement()).getValue();
            BigInteger primeP = ((ASN1Integer) asn1.nextElement()).getValue();
            BigInteger primeQ = ((ASN1Integer) asn1.nextElement()).getValue();
            BigInteger primeExponentP = ((ASN1Integer) asn1.nextElement()).getValue();
            BigInteger primeExponentQ = ((ASN1Integer) asn1.nextElement()).getValue();
            BigInteger coefficient = ((ASN1Integer) asn1.nextElement()).getValue();

            if (asn1.hasMoreElements()) {
                ASN1Sequence otherPrimeInfos = (ASN1Sequence) asn1.nextElement();
            }

            RSAPrivateCrtKeySpec spec = new RSAPrivateCrtKeySpec(modulus, publicExponent, privateExponent,
                primeP, primeQ, primeExponentP, primeExponentQ, coefficient);
            try {
                KeyFactory kf = KeyFactory.getInstance("RSA");
                return kf.generatePrivate(spec);
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
        public PrivateKey decode(byte[] data, String password, Map<String, String> headers)
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
