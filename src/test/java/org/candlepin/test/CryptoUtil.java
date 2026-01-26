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

import org.candlepin.pki.PrivateKeyReader;
import org.candlepin.pki.Scheme;
import org.candlepin.pki.impl.bc.BouncyCastlePrivateKeyReader;
import org.candlepin.pki.impl.bc.BouncyCastleSecurityProvider;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
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
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcaPKCS8Generator;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8EncryptorBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.OutputEncryptor;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.AlgorithmParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.stream.Stream;

import javax.inject.Provider;



/**
 * Provides utility functionality for generating cryptographic resources for testing. Primarily, this class
 * aims to centralize provider-specific crypto functionality in one place such that another security provider
 * change requires only minimal changes to test suites outside of this class.
 * <p>
 * It should go without saying, but this class and the functionality it provides should not be used in
 * production code.
 */
public class CryptoUtil {
    private static final BouncyCastleSecurityProvider SECURITY_PROVIDER = new BouncyCastleSecurityProvider();

    private static final String RSA_SIGNATURE_ALGORITHM = "SHA256WithRSA";
    private static final String RSA_KEY_ALGORITHM = "RSA";
    private static final int RSA_KEY_SIZE = 4096;

    private static final String MLDSA_SIGNATURE_ALGORITHM = "ML-DSA";
    private static final String MLDSA_KEY_ALGORITHM = "ML-DSA";

    private CryptoUtil() {
        throw new UnsupportedOperationException();
    }

    /**
     * Fetches the security provider provider backing the operations in this util class.
     *
     * @return
     *  a SecurityProvider instance
     */
    public static Provider<? extends java.security.Provider> getSecurityProvider() {
        return SECURITY_PROVIDER;
    }

    /**
     * Fetches a private key reader implemented using a supported crypto security provider. Each call to this
     * method may return a new instance, but it will never return null.
     * <p>
     * This method exists to fetch a private key reader without needing an entire injection ecosystem in the
     * calling test methods, and all the configuration that requires.
     *
     * @return
     *  a PrivateKeyReader implementation
     */
    public static PrivateKeyReader getPrivateKeyReader() {
        // TODO: FIXME: This is likely temporary. I imagine a certificate authority would be able to provide
        // such a thing. Or maybe it should be injected. idk

        return new BouncyCastlePrivateKeyReader(SECURITY_PROVIDER);
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
            KeyPairGenerator keygen = KeyPairGenerator.getInstance(algorithm, SECURITY_PROVIDER.get());
            keygen.initialize(keySize);

            return keygen.generateKeyPair();
        }
        catch (NoSuchAlgorithmException e) {
            throw new KeyException(e);
        }
    }

    /**
     * Generates a key pair using the given algorithm and algorithm parameters. If the algorithm parameters
     * are null, they will be silently ignored.
     *
     * @param algorithm
     *  the key generation algorithm to use
     *
     * @param params
     *  An AlgorithmParameterSpec to pass through to the underlying security provider to influence key
     *  generation
     *
     * @return
     *  the generated key pair
     */
    public static KeyPair generateKeyPair(String algorithm, AlgorithmParameterSpec params)
        throws KeyException {

        try {
            KeyPairGenerator keygen = KeyPairGenerator.getInstance(algorithm, SECURITY_PROVIDER.get());
            if (params != null) {
                keygen.initialize(params);
            }

            return keygen.generateKeyPair();
        }
        catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException e) {
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
                .setProvider(SECURITY_PROVIDER.get())
                .build(keypair.getPrivate());

            return new JcaX509CertificateConverter()
                .setProvider(SECURITY_PROVIDER.get())
                .getCertificate(certBuilder.build(signer));
        }
        catch (CertIOException | NoSuchAlgorithmException | OperatorCreationException e) {
            throw new CertificateException(e);
        }
    }

    /**
     * Writes the given certificate to a file in PEM format.
     *
     * @param certificate
     *  the certificate to write to a file
     *
     * @param file
     *  the destination file to write
     *
     * @throws IOException
     *  if an IO exception occurs while writing the file
     */
    public static void writeCertificateToFile(X509Certificate certificate, File file) throws IOException {
        if (certificate == null) {
            throw new IllegalArgumentException("certificate is null");
        }

        if (file == null) {
            throw new IllegalArgumentException("file is null");
        }

        try (JcaPEMWriter writer = new JcaPEMWriter(new FileWriter(file))) {
            writer.writeObject(certificate);
        }
    }

    /**
     * Writes the given private key to a file in PEM format, optionally with the given password. If the
     * password is null or empty, the key will be written in plain text.
     *
     * @param key
     *  the private key to write to a file
     *
     * @param file
     *  the destination file to write
     *
     * @param password
     *  the password to use to protect the key
     *
     * @throws KeyException
     *  if an exception occurs while writing the key to the given file
     */
    public static void writePrivateKeyToFile(PrivateKey key, File file, String password) throws KeyException {
        if (key == null) {
            throw new IllegalArgumentException("key is null");
        }

        if (file == null) {
            throw new IllegalArgumentException("file is null");
        }

        try (JcaPEMWriter writer = new JcaPEMWriter(new FileWriter(file))) {
            if (password != null && !password.isBlank()) {
                // Not sure exactly *which* algorithm we should use here. Perhaps it'd be best to test *all*
                // of them, but given we've never specified which password types we support... hrmm. Perhaps
                // in the future we should specify that and then ensure all specified password algos are
                // supported.
                ASN1ObjectIdentifier algorithm = JcaPKCS8Generator.AES_256_CBC;

                OutputEncryptor encryptor = new JceOpenSSLPKCS8EncryptorBuilder(algorithm)
                    .setProvider(SECURITY_PROVIDER.get())
                    .setPassword(password.toCharArray())
                    .setIterationCount(1024) // not very strong, but this is testing so whatever.
                    .build();

                JcaPKCS8Generator generator = new JcaPKCS8Generator(key, encryptor);

                writer.writeObject(generator);
            }
            else {
                // No password given, write the key in plain-ish text-ish
                writer.writeObject(key);
            }
        }
        catch (IOException | OperatorCreationException e) {
            throw new KeyException(e);
        }
    }

    /**
     * Generates a scheme configured with Candlepin's legacy RSA algorithms with generated keys and
     * certificates using them.
     *
     * @throws KeyException
     *  if an exception occurs while generating the key pair
     *
     * @throws CertificateException
     *  if an exception occurs while generating the certificate
     *
     * @return
     *  a scheme with generated keys and certs using the Candlepin legacy RSA scheme
     */
    public static Scheme generateRsaScheme() throws KeyException, CertificateException {
        KeyPair keypair = generateKeyPair(RSA_KEY_ALGORITHM, RSA_KEY_SIZE);
        X509Certificate certificate = generateX509Certificate(keypair, RSA_SIGNATURE_ALGORITHM);

        return new Scheme.Builder()
            .setName("rsa")
            .setPrivateKey(keypair.getPrivate())
            .setCertificate(certificate)
            .setSignatureAlgorithm(RSA_SIGNATURE_ALGORITHM)
            .setKeyAlgorithm(RSA_KEY_ALGORITHM)
            .setKeySize(RSA_KEY_SIZE)
            .build();
    }

    /**
     * Generates a scheme configured to use ML-DSA for the key and signature algorithms with generated keys
     * and certificates using them. The scheme generated defines generic "ML-DSA" with no sizing specified
     * (e.g. ML-DSA-87).
     *
     * @throws KeyException
     *  if an exception occurs while generating the key pair
     *
     * @throws CertificateException
     *  if an exception occurs while generating the certificate
     *
     * @return
     *  a scheme with generated keys and certs using ML-DSA
     */
    public static Scheme generateMldsaScheme() throws KeyException, CertificateException {
        KeyPair keypair = generateKeyPair(MLDSA_KEY_ALGORITHM, null);
        X509Certificate certificate = generateX509Certificate(keypair, MLDSA_SIGNATURE_ALGORITHM);

        return new Scheme.Builder()
            .setName("ml-dsa")
            .setPrivateKey(keypair.getPrivate())
            .setCertificate(certificate)
            .setSignatureAlgorithm(MLDSA_SIGNATURE_ALGORITHM)
            .setKeyAlgorithm(MLDSA_KEY_ALGORITHM)
            .setKeySize(null)
            .build();
    }

    /**
     * Returns a stream containing one of each known crypto scheme explicitly supported by Candlepin. Test
     * suites looking to test all known schemes should make use of this function instead of defining their
     * own aggregator.
     *
     * @return
     *  a stream of all known supported crypto schemes.
     */
    public static Stream<Scheme> generateSupportedSchemes() {
        try {
            return Stream.of(
                generateRsaScheme(),
                generateMldsaScheme());
        }
        catch (KeyException | CertificateException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Generates a new scheme from the given scheme, generating a new keypair and certificate, but retaining
     * the same algorithms and key size. The newly generated scheme will contain a private key even if the
     * source scheme does not. The certificate in the new scheme will not be signed or otherwise derived from
     * the certificate in the source certificate.
     *
     * @param scheme
     *  the source scheme from which to generate a new scheme
     *
     * @throws KeyException
     *  if an exception occurs while generating the key pair
     *
     * @throws CertificateException
     *  if an exception occurs while generating the certificate
     *
     * @return
     *  a scheme with generated keys and certs using the algorithms from the given scheme
     */
    public static Scheme generateSchemeFromScheme(Scheme scheme) throws KeyException, CertificateException {
        String signatureAlgorithm = scheme.signatureAlgorithm();
        String keyAlgorithm = scheme.keyAlgorithm();
        Integer keySize = scheme.keySize();

        KeyPair keypair = keySize != null ?
            generateKeyPair(keyAlgorithm, keySize) :
            generateKeyPair(keyAlgorithm, null);

        X509Certificate certificate = generateX509Certificate(keypair, signatureAlgorithm);

        return new Scheme.Builder()
            .setName(scheme.name())
            .setPrivateKey(keypair.getPrivate())
            .setCertificate(certificate)
            .setSignatureAlgorithm(signatureAlgorithm)
            .setKeyAlgorithm(keyAlgorithm)
            .setKeySize(keySize)
            .build();
    }

}
