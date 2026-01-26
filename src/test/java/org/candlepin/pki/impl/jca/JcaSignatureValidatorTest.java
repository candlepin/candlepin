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
package org.candlepin.pki.impl.jca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.pki.Scheme;
import org.candlepin.pki.SignatureValidator;
import org.candlepin.test.CryptoUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

// TODO: Make this test suite generic, and the specific implementation provider a subclass thing. Whatever.
// Basically, like the PrivateKeyReaderTest.

// TODO: Update this test suite to no longer use the ExtScheme wrapper. It was written before Scheme had the
// private key field and is no longer necessary.

public class JcaSignatureValidatorTest {

    /**
     * An extended scheme that includes the keypair as an explicit field
     */
    private static record ExtScheme(
        String name,
        KeyPair keypair,
        X509Certificate certificate,
        String signatureAlgorithm,
        String keyAlgorithm,
        int keySize
    ) {
        /**
         * Convenience method to make the test output less crazy
         *
         * @return string representation of this scheme
         */
        @Override
        public String toString() {
            return String.format("ExtScheme [name: %s]", this.name);
        }
    }

    private static Map<String, ExtScheme> schemeMap = new HashMap<>();

    private static ExtScheme generateRsaScheme() throws Exception {
        final String signatureAlgorithm = "SHA256WithRSA";
        final String keyAlgorithm = "rsa";
        final int keySize = 4096;

        KeyPair keypair = CryptoUtil.generateKeyPair(keyAlgorithm, keySize);
        X509Certificate certificate = CryptoUtil.generateX509Certificate(keypair, signatureAlgorithm);

        return new ExtScheme("rsa", keypair, certificate, signatureAlgorithm, keyAlgorithm, keySize);
    }

    private static ExtScheme generateFromScheme(ExtScheme scheme) throws Exception {
        final String signatureAlgorithm = scheme.signatureAlgorithm();
        final String keyAlgorithm = scheme.keyAlgorithm();
        final int keySize = scheme.keySize();

        KeyPair keypair = CryptoUtil.generateKeyPair(keyAlgorithm, keySize);
        X509Certificate certificate = CryptoUtil.generateX509Certificate(keypair, signatureAlgorithm);

        return new ExtScheme(scheme.name(), keypair, certificate, signatureAlgorithm, keyAlgorithm, keySize);
    }

    private static Scheme toStandardScheme(ExtScheme scheme) {
        return new Scheme.Builder()
            .setName(scheme.name())
            .setCertificate(scheme.certificate())
            .setSignatureAlgorithm(scheme.signatureAlgorithm())
            .setKeyAlgorithm(scheme.keyAlgorithm())
            .build();
    }

    @BeforeAll
    public static void initSchemes() throws Exception {
        List<ExtScheme> schemes = List.of(generateRsaScheme());
        // TODO: Add more schemes here as they're required/supported

        for (ExtScheme scheme : schemes) {
            schemeMap.put(scheme.name(), scheme);
        }
    }

    private static Stream<Arguments> schemeSource() {
        return schemeMap.values()
            .stream()
            .map(Arguments::of);
    }

    private static JcaSignatureValidator buildValidator(ExtScheme scheme) {
        return new JcaSignatureValidator(CryptoUtil.getSecurityProvider(), toStandardScheme(scheme));
    }

    private static File generateTempFile(String data) throws IOException {
        File tmp = File.createTempFile("cp_test", ".txt");
        tmp.deleteOnExit();

        try (FileWriter writer = new FileWriter(tmp)) {
            writer.write(data);
        }

        return tmp;
    }

    private byte[] signData(String algorithm, PrivateKey privateKey, InputStream istream) throws Exception {
        Signature jcaSignature = Signature.getInstance(algorithm);
        jcaSignature.initSign(privateKey);

        byte[] buffer = new byte[4096];
        int read;

        while ((read = istream.read(buffer)) != -1) {
            jcaSignature.update(buffer, 0, read);
        }

        return jcaSignature.sign();
    }

    private byte[] signData(String algorithm, PrivateKey privateKey, byte[] data) throws Exception {
        return signData(algorithm, privateKey, new ByteArrayInputStream(data));
    }

    private byte[] signData(String algorithm, PrivateKey privateKey, File file) throws Exception {
        return signData(algorithm, privateKey, new FileInputStream(file));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testConstructionRetainsScheme(ExtScheme scheme) throws Exception {
        Scheme expected = toStandardScheme(scheme);
        SignatureValidator validator = new JcaSignatureValidator(CryptoUtil.getSecurityProvider(), expected);

        assertEquals(validator.getCryptoScheme(), expected);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testConstructionRequiresSecurityProvider(ExtScheme extscheme) throws Exception {
        Scheme scheme = toStandardScheme(extscheme);

        // This could be a NPE or IllegalArgumentException depending on the underlying implementation.
        assertThrows(NullPointerException.class, () -> new JcaSignatureValidator(null, scheme));
    }

    @Test
    public void testConstructionRequiresScheme() throws Exception {
        java.security.Provider provider = CryptoUtil.getSecurityProvider();

        // This could be a NPE or IllegalArgumentException depending on the underlying implementation.
        assertThrows(NullPointerException.class, () -> new JcaSignatureValidator(provider, null));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testForSignatureRequiresSignature(ExtScheme scheme) throws Exception {
        SignatureValidator validator = buildValidator(scheme);

        assertThrows(IllegalArgumentException.class, () -> validator.forSignature(null));
        assertThrows(IllegalArgumentException.class, () -> validator.forSignature(new byte[0]));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testAdditionalCertificateIgnoresNullCollections(ExtScheme scheme) throws Exception {
        SignatureValidator validator = buildValidator(scheme);
        validator.withAdditionalCertificates((Collection<X509Certificate>) null);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testAdditionalCertificateIgnoresNullsWithinCollection(ExtScheme scheme) throws Exception {
        SignatureValidator validator = buildValidator(scheme);
        Collection<X509Certificate> certs = Arrays.asList(new X509Certificate[] { null });

        validator.withAdditionalCertificates(certs);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testAdditionalCertificateIgnoresNullArrays(ExtScheme scheme) throws Exception {
        SignatureValidator validator = buildValidator(scheme);
        validator.withAdditionalCertificates((X509Certificate[]) null);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testAdditionalCertificateIgnoresNullsWithinArray(ExtScheme scheme) throws Exception {
        SignatureValidator validator = buildValidator(scheme);
        X509Certificate[] certs = new X509Certificate[] { null };

        validator.withAdditionalCertificates(certs);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testValidateWithBytes(ExtScheme scheme) throws Exception {
        String data = "hello world";
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] signature = this.signData(scheme.signatureAlgorithm(), scheme.keypair().getPrivate(), bytes);

        SignatureValidator validator = buildValidator(scheme)
            .forSignature(signature);

        assertTrue(validator.validate(bytes));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testValidateWithBytesAndAdditionalCertificatesAsArray(ExtScheme scheme) throws Exception {
        ExtScheme alt1 = generateFromScheme(scheme);
        ExtScheme alt2 = generateFromScheme(scheme);

        String data = "hello world";
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] signature = this.signData(alt2.signatureAlgorithm(), alt2.keypair().getPrivate(), bytes);

        SignatureValidator validator = buildValidator(scheme)
            .forSignature(signature)
            .withAdditionalCertificates(alt1.certificate(), alt2.certificate());

        assertTrue(validator.validate(bytes));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testValidateWithBytesAndAdditionalCertificatesAsCollection(ExtScheme scheme)
        throws Exception {

        ExtScheme alt1 = generateFromScheme(scheme);
        ExtScheme alt2 = generateFromScheme(scheme);

        String data = "hello world";
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] signature = this.signData(alt2.signatureAlgorithm(), alt2.keypair().getPrivate(), bytes);

        SignatureValidator validator = buildValidator(scheme)
            .forSignature(signature)
            .withAdditionalCertificates(List.of(alt1.certificate(), alt2.certificate()));

        assertTrue(validator.validate(bytes));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testValidateWithBytesFailsWithNoMatchingCertificates(ExtScheme scheme) throws Exception {
        ExtScheme alt1 = generateFromScheme(scheme);
        ExtScheme alt2 = generateFromScheme(scheme);
        ExtScheme alt3 = generateFromScheme(scheme);

        String data = "hello world";
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] signature = this.signData(alt3.signatureAlgorithm(), alt3.keypair().getPrivate(), bytes);

        SignatureValidator validator = buildValidator(scheme)
            .forSignature(signature)
            .withAdditionalCertificates(alt1.certificate(), alt2.certificate());

        assertFalse(validator.validate(bytes));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testValidateWithBytesRequiresSignature(ExtScheme scheme) throws Exception {
        String data = "hello world";
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);

        SignatureValidator validator = buildValidator(scheme);

        assertThrows(IllegalStateException.class, () -> validator.validate(bytes));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testValidateWithBytesToleratesNullArrays(ExtScheme scheme) throws Exception {
        String data = "hello world";
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] signature = this.signData(scheme.signatureAlgorithm(), scheme.keypair().getPrivate(), bytes);

        SignatureValidator validator = buildValidator(scheme)
            .forSignature(signature);

        assertFalse(validator.validate((byte[]) null));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testValidateWithFile(ExtScheme scheme) throws Exception {
        String data = "hello world";
        File file = generateTempFile(data);
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] signature = this.signData(scheme.signatureAlgorithm(), scheme.keypair().getPrivate(), bytes);

        SignatureValidator validator = buildValidator(scheme)
            .forSignature(signature);

        assertTrue(validator.validate(file));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testValidateWithFileAndAdditionalCertificatesAsArray(ExtScheme scheme) throws Exception {
        ExtScheme alt1 = generateFromScheme(scheme);
        ExtScheme alt2 = generateFromScheme(scheme);

        String data = "hello world";
        File file = generateTempFile(data);
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] signature = this.signData(alt2.signatureAlgorithm(), alt2.keypair().getPrivate(), bytes);

        SignatureValidator validator = buildValidator(scheme)
            .forSignature(signature)
            .withAdditionalCertificates(alt1.certificate(), alt2.certificate());

        assertTrue(validator.validate(file));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testValidateWithFileAndAdditionalCertificatesAsCollection(ExtScheme scheme) throws Exception {
        ExtScheme alt1 = generateFromScheme(scheme);
        ExtScheme alt2 = generateFromScheme(scheme);

        String data = "hello world";
        File file = generateTempFile(data);
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] signature = this.signData(alt2.signatureAlgorithm(), alt2.keypair().getPrivate(), bytes);

        SignatureValidator validator = buildValidator(scheme)
            .forSignature(signature)
            .withAdditionalCertificates(List.of(alt1.certificate(), alt2.certificate()));

        assertTrue(validator.validate(file));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testValidateWithFileFailsWithNoMatchingCertificates(ExtScheme scheme) throws Exception {
        ExtScheme alt1 = generateFromScheme(scheme);
        ExtScheme alt2 = generateFromScheme(scheme);
        ExtScheme alt3 = generateFromScheme(scheme);

        String data = "hello world";
        File file = generateTempFile(data);
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] signature = this.signData(alt3.signatureAlgorithm(), alt3.keypair().getPrivate(), bytes);

        SignatureValidator validator = buildValidator(scheme)
            .forSignature(signature)
            .withAdditionalCertificates(alt1.certificate(), alt2.certificate());

        assertFalse(validator.validate(file));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testValidateWithFileRequiresSignature(ExtScheme scheme) throws Exception {
        String data = "hello world";
        File file = generateTempFile(data);
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);

        SignatureValidator validator = buildValidator(scheme);

        assertThrows(IllegalStateException.class, () -> validator.validate(file));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testValidateWithFileRejectsNullFileObjects(ExtScheme scheme) throws Exception {
        String data = "hello world";

        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] signature = this.signData(scheme.signatureAlgorithm(), scheme.keypair().getPrivate(), bytes);

        SignatureValidator validator = buildValidator(scheme)
            .forSignature(signature);

        assertThrows(IllegalArgumentException.class, () -> validator.validate((File) null));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testValidateWithFileThrowsExceptionOnFileMissing(ExtScheme scheme) throws Exception {
        String data = "hello world";

        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] signature = this.signData(scheme.signatureAlgorithm(), scheme.keypair().getPrivate(), bytes);

        SignatureValidator validator = buildValidator(scheme)
            .forSignature(signature);

        assertThrows(IOException.class, () -> validator.validate(new File("this_file_shouldnt_exist.pls")));
    }
}
