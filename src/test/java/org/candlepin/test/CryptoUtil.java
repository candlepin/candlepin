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
package org.candlepin.test;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.KeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;



/**
 * Provides utility functionality for generating cryptographic resources for testing. This class must not be
 * used in production code.
 */
public class CryptoUtil {

    private CryptoUtil() {
        throw new UnsupportedOperationException();
    }

    /**
     * Generates a key pair using the given algorithm and key size
     *
     * @param algorithm
     *  the key generation algorithm to use
     *
     * @param keySize
     *  the size or length of the key(s) to generate
     *
     * @return
     *  the generated key pair
     */
    public static KeyPair generateKeyPair(String algorithm, int keySize) throws KeyException {
        try {
            KeyPairGenerator keygen = KeyPairGenerator.getInstance(algorithm);
            keygen.initialize(keySize);

            return keygen.generateKeyPair();
        }
        catch (NoSuchAlgorithmException e) {
            throw new KeyException(e);
        }
    }

    /**
     * Generates a self-signed X509 certificate with the following properties:
     *
     * - Distinguished name: CN: cp.test_cert
     * - Serial number derived from milliseconds of generation time
     * - Valid from a week prior to generation
     * - Valid until a week after generation
     * - Usage: digital signature, key encipherment, data encipherment
     *
     * @param keypair
     *  the key pair to use to build the certificate
     *
     * @param signatureAlgorithm
     *  the algorithm to use to sign the certificate
     *
     * @return
     *  a self-signed X509 certificate
     */
    public static X509Certificate generateX509Certificate(KeyPair keypair, String signatureAlgorithm)
        throws CertificateException {

        try {
            // DN
            X500Name distinguishedName = new X500NameBuilder()
                .addRDN(BCStyle.CN, "cp.test_cert")
                .build();

            // Validity
            Instant now = Instant.now();
            Instant validAfter = now.minus(168, ChronoUnit.HOURS); // 1 week; WEEKS unit isn't supported here
            Instant validUntil = now.plus(168, ChronoUnit.HOURS);

            // Serial
            BigInteger serial = BigInteger.valueOf(now.toEpochMilli());

            // Key usage
            KeyUsage usage = new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment |
                KeyUsage.dataEncipherment);

            // aki/ski
            JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
            AuthorityKeyIdentifier aki = extUtils.createAuthorityKeyIdentifier(keypair.getPublic());
            SubjectKeyIdentifier ski = extUtils.createSubjectKeyIdentifier(keypair.getPublic());

            // assembly!
            X509v3CertificateBuilder certBuilder = new X509v3CertificateBuilder(
                distinguishedName,
                serial,
                Date.from(validAfter),
                Date.from(validUntil),
                distinguishedName,
                SubjectPublicKeyInfo.getInstance(keypair.getPublic().getEncoded()));

            // Add extensions
            certBuilder.addExtension(Extension.keyUsage, true, usage)
                .addExtension(Extension.authorityKeyIdentifier, false, aki)
                .addExtension(Extension.subjectKeyIdentifier, false, ski)
                .addExtension(Extension.basicConstraints, true, new BasicConstraints(true));

            // Signer and converter
            ContentSigner signer = new JcaContentSignerBuilder(signatureAlgorithm)
                .build(keypair.getPrivate());

            return new JcaX509CertificateConverter()
                .setProvider(new BouncyCastleProvider())
                .getCertificate(certBuilder.build(signer));
        }
        catch (CertIOException | NoSuchAlgorithmException | OperatorCreationException e) {
            throw new CertificateException(e);
        }
    }

}
