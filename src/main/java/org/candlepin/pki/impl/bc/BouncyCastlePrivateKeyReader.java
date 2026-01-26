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
package org.candlepin.pki.impl.bc;

import org.candlepin.pki.impl.AbstractPrivateKeyReader;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMException;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.PKCSException;
import org.bouncycastle.pkcs.jcajce.JcePKCSPBEInputDecryptorProviderBuilder;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.Enumeration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;



/**
 * Bouncy Castle implementation of the abstract private key reader; supporting both PKCS1 and PKCS8 key
 * formats.
 */
public class BouncyCastlePrivateKeyReader extends AbstractPrivateKeyReader {

    private static final String HEADER_DEK_INFO = "DEK-Info";

    private static final Pattern REGEX_DEK_INFO = Pattern.compile("^(.+?)\\s*,\\s*([a-fA-F0-9]+)$");
    private static final int REGEX_GROUP_DEK_ALGO = 1;
    private static final int REGEX_GROUP_DEK_IV = 2;

    private final BouncyCastleProvider securityProvider;

    @Inject
    public BouncyCastlePrivateKeyReader(BouncyCastleProvider securityProvider) {
        this.securityProvider = Objects.requireNonNull(securityProvider);
    }

    private byte[] decryptPkcs1(byte[] buffer, Map<String, String> headers, String password)
        throws KeyException {

        if (headers == null || headers.isEmpty() || !headers.containsKey(HEADER_DEK_INFO)) {
            return buffer;
        }

        String dekinfo = headers.get(HEADER_DEK_INFO);
        Matcher matcher = REGEX_DEK_INFO.matcher(dekinfo);
        if (!matcher.matches()) {
            throw new KeyException("malformed data encryption key in private key headers: " + dekinfo);
        }

        if (password == null || password.isEmpty()) {
            throw new KeyException("Cannot read private key: no passphrase provided for encrypted key");
        }

        try {
            String algorithm = matcher.group(REGEX_GROUP_DEK_ALGO);
            byte[] initvec = Hex.decodeHex(matcher.group(REGEX_GROUP_DEK_IV).toCharArray());

            PEMDecryptorProvider decryptorProvider = new JcePEMDecryptorProviderBuilder()
                .setProvider(this.securityProvider)
                .build(password.toCharArray());

            return decryptorProvider.get(algorithm)
                .decrypt(buffer, initvec);
        }
        catch (DecoderException e) {
            throw new KeyException("Cannot read private key: unable to decode initialization vector", e);
        }
        catch (OperatorCreationException e) {
            throw new KeyException("Cannot read private key: no provider for decryption algorithm", e);
        }
        catch (PEMException e) {
            throw new KeyException(e);
        }
    }

    @Override
    protected PrivateKey decodePkcs1(byte[] buffer, Map<String, String> headers, String password)
        throws KeyException {

        // Check if we need to decrypt the buffer first
        buffer = decryptPkcs1(buffer, headers, password);

        ASN1Sequence seq = ASN1Sequence.getInstance(buffer);
        Enumeration asn1 = seq.getObjects();

        BigInteger version = ((ASN1Integer) asn1.nextElement()).getValue();
        if (version.intValue() != 0 && version.intValue() != 1) {
            throw new KeyException("Cannot read private key: wrong version for RSA private key: " + version);
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
            throw new KeyException("Cannot read private key: RSA multi-prime keys are not supported");
        }

        try {
            RSAPrivateCrtKeySpec spec = new RSAPrivateCrtKeySpec(modulus, publicExponent, privateExponent,
                primeP, primeQ, primeExponentP, primeExponentQ, coefficient);

            return KeyFactory.getInstance("RSA", this.securityProvider)
                .generatePrivate(spec);
        }
        catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new KeyException("Cannot read private key", e);
        }
    }

    @Override
    protected PrivateKey decodePkcs8(byte[] buffer, Optional<Modifiers> modifier, String password)
        throws KeyException {

        try {
            PrivateKeyInfo pkinfo;

            if (modifier.orElse(null) == Modifiers.ENCRYPTED) {
                if (password == null || password.isEmpty()) {
                    throw new KeyException(
                        "Cannot read private key: no passphrase provided for encrypted key");
                }

                InputDecryptorProvider decryptor = new JcePKCSPBEInputDecryptorProviderBuilder()
                    .setProvider(this.securityProvider)
                    .build(password.toCharArray());

                pkinfo = new PKCS8EncryptedPrivateKeyInfo(buffer)
                    .decryptPrivateKeyInfo(decryptor);
            }
            else {
                pkinfo = PrivateKeyInfo.getInstance(buffer);
            }

            return new JcaPEMKeyConverter()
                .setProvider(this.securityProvider)
                .getPrivateKey(pkinfo);
        }
        catch (IOException | PKCSException e) {
            throw new KeyException(e);
        }
    }

}
