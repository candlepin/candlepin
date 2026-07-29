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
package org.candlepin.pki;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.test.CryptoUtil;

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
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;



/**
 * General test suite that all SignatureValidators must extend
 */
public abstract class SignatureValidatorTest {

    /**
     * Builds a new SignatureValidator instance to test, using the given crypto scheme. Each invocation of
     * this method should return a new instance to avoid unintended object state retention between tests.
     *
     * @param scheme
     *  the scheme with which to build the signature validator
     *
     * @return
     *  a new SignatureValidator instance to test
     */
    protected abstract SignatureValidator buildSignatureValidator(Scheme scheme);

    protected static Stream<Arguments> schemeSource() {
        return CryptoUtil.SUPPORTED_SCHEMES.values()
            .stream()
            .map(Arguments::of);
    }

    protected static File generateTempFile(String data) throws IOException {
        File tmp = File.createTempFile("cp_test", ".txt");
        tmp.deleteOnExit();

        try (FileWriter writer = new FileWriter(tmp)) {
            writer.write(data);
        }

        return tmp;
    }

    protected static byte[] signData(String algorithm, PrivateKey privateKey, InputStream istream)
        throws Exception {

        Signature signer = Signature.getInstance(algorithm, CryptoUtil.getSecurityProvider());
        signer.initSign(privateKey);

        byte[] buffer = new byte[4096];
        int read;

        while ((read = istream.read(buffer)) > 0) {
            signer.update(buffer, 0, read);
        }

        return signer.sign();
    }

    protected static byte[] signData(String algorithm, PrivateKey privateKey, byte[] data) throws Exception {
        try (InputStream istream = new ByteArrayInputStream(data)) {
            return signData(algorithm, privateKey, istream);
        }
    }

    protected static byte[] signData(String algorithm, PrivateKey privateKey, File file) throws Exception {
        try (InputStream istream = new FileInputStream(file)) {
            return signData(algorithm, privateKey, istream);
        }
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testConstructionRetainsScheme(Scheme scheme) throws Exception {
        SignatureValidator validator = this.buildSignatureValidator(scheme);

        // Ensure repeated calls also return the scheme, I guess
        for (int i = 0; i < 5; ++i) {
            assertEquals(validator.getCryptoScheme(), scheme);
        }
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testForSignatureRequiresSignature(Scheme scheme) throws Exception {
        SignatureValidator validator = this.buildSignatureValidator(scheme);

        assertThrows(IllegalArgumentException.class, () -> validator.forSignature(null));
        assertThrows(IllegalArgumentException.class, () -> validator.forSignature(new byte[0]));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testAdditionalCertificateSilentlyIgnoresNullCollections(Scheme scheme) throws Exception {
        SignatureValidator validator = this.buildSignatureValidator(scheme);
        validator.withAdditionalCertificates((Collection<X509Certificate>) null);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testAdditionalCertificateSilentlyIgnoresNullsWithinCollection(Scheme scheme)
        throws Exception {

        SignatureValidator validator = this.buildSignatureValidator(scheme);
        Collection<X509Certificate> certs = Arrays.asList(new X509Certificate[] { null });

        validator.withAdditionalCertificates(certs);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testAdditionalCertificateSilentlyIgnoresNullArrays(Scheme scheme) throws Exception {
        SignatureValidator validator = this.buildSignatureValidator(scheme);
        validator.withAdditionalCertificates((X509Certificate[]) null);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testAdditionalCertificateSilentlyIgnoresNullsWithinArray(Scheme scheme) throws Exception {
        SignatureValidator validator = this.buildSignatureValidator(scheme);
        X509Certificate[] certs = new X509Certificate[] { null };

        validator.withAdditionalCertificates(certs);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testValidateWithBytes(Scheme scheme) throws Exception {
        String data = "hello world";
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] signature = signData(scheme.signatureAlgorithm(), scheme.privateKey().get(), bytes);

        SignatureValidator validator = this.buildSignatureValidator(scheme)
            .forSignature(signature);

        assertTrue(validator.validate(bytes));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testValidateWithBytesAndAdditionalCertificatesAsArray(Scheme scheme) throws Exception {
        Scheme altScheme = CryptoUtil.generateSchemeFromScheme(scheme);

        List<X509Certificate> certificates = new ArrayList<>();
        certificates.add(altScheme.certificate());

        for (Scheme supportedScheme : CryptoUtil.SUPPORTED_SCHEMES.values()) {
            X509Certificate cert = CryptoUtil.generateX509Certificate(supportedScheme);
            certificates.add(cert);
        }

        X509Certificate[] certsArray = certificates.toArray(new X509Certificate[0]);

        String data = "hello world";
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] signature = signData(altScheme.signatureAlgorithm(), altScheme.privateKey().get(), bytes);

        SignatureValidator validator = this.buildSignatureValidator(scheme)
            .forSignature(signature)
            .withAdditionalCertificates(certsArray);

        assertTrue(validator.validate(bytes));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testValidateWithBytesAndAdditionalCertificatesAsCollection(Scheme scheme)
        throws Exception {

        Scheme altScheme = CryptoUtil.generateSchemeFromScheme(scheme);

        List<X509Certificate> certificates = new ArrayList<>();
        certificates.add(altScheme.certificate());

        for (Scheme supportedScheme : CryptoUtil.SUPPORTED_SCHEMES.values()) {
            X509Certificate cert = CryptoUtil.generateX509Certificate(supportedScheme);
            certificates.add(cert);
        }

        String data = "hello world";
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] signature = signData(altScheme.signatureAlgorithm(), altScheme.privateKey().get(), bytes);

        SignatureValidator validator = this.buildSignatureValidator(scheme)
            .forSignature(signature)
            .withAdditionalCertificates(certificates);

        assertTrue(validator.validate(bytes));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testValidateWithBytesFailsWithNoMatchingCertificates(Scheme scheme) throws Exception {
        Scheme altScheme = CryptoUtil.generateSchemeFromScheme(scheme);

        List<X509Certificate> certificates = new ArrayList<>();
        for (Scheme supportedScheme : CryptoUtil.SUPPORTED_SCHEMES.values()) {
            X509Certificate cert = CryptoUtil.generateX509Certificate(supportedScheme);
            certificates.add(cert);
        }

        String data = "hello world";
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] signature = signData(altScheme.signatureAlgorithm(), altScheme.privateKey().get(), bytes);

        SignatureValidator validator = this.buildSignatureValidator(scheme)
            .forSignature(signature)
            .withAdditionalCertificates(certificates);

        assertFalse(validator.validate(bytes));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testValidateWithBytesRequiresSignature(Scheme scheme) throws Exception {
        String data = "hello world";
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);

        SignatureValidator validator = this.buildSignatureValidator(scheme);

        assertThrows(IllegalStateException.class, () -> validator.validate(bytes));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testValidateWithBytesToleratesNullArrays(Scheme scheme) throws Exception {
        String data = "hello world";
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] signature = signData(scheme.signatureAlgorithm(), scheme.privateKey().get(), bytes);

        SignatureValidator validator = this.buildSignatureValidator(scheme)
            .forSignature(signature);

        assertFalse(validator.validate((byte[]) null));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testValidateWithFile(Scheme scheme) throws Exception {
        String data = "hello world";
        File file = generateTempFile(data);
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] signature = signData(scheme.signatureAlgorithm(), scheme.privateKey().get(), bytes);

        SignatureValidator validator = this.buildSignatureValidator(scheme)
            .forSignature(signature);

        assertTrue(validator.validate(file));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testValidateWithFileAndAdditionalCertificatesAsArray(Scheme scheme) throws Exception {
        Scheme altScheme = CryptoUtil.generateSchemeFromScheme(scheme);

        List<X509Certificate> certificates = new ArrayList<>();
        certificates.add(altScheme.certificate());

        for (Scheme supportedScheme : CryptoUtil.SUPPORTED_SCHEMES.values()) {
            X509Certificate cert = CryptoUtil.generateX509Certificate(supportedScheme);
            certificates.add(cert);
        }

        X509Certificate[] certsArray = certificates.toArray(new X509Certificate[0]);

        String data = "hello world";
        File file = generateTempFile(data);
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] signature = signData(altScheme.signatureAlgorithm(), altScheme.privateKey().get(), bytes);

        SignatureValidator validator = this.buildSignatureValidator(scheme)
            .forSignature(signature)
            .withAdditionalCertificates(certsArray);

        assertTrue(validator.validate(file));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testValidateWithFileAndAdditionalCertificatesAsCollection(Scheme scheme) throws Exception {
        Scheme altScheme = CryptoUtil.generateSchemeFromScheme(scheme);

        List<X509Certificate> certificates = new ArrayList<>();
        certificates.add(altScheme.certificate());

        for (Scheme supportedScheme : CryptoUtil.SUPPORTED_SCHEMES.values()) {
            X509Certificate cert = CryptoUtil.generateX509Certificate(supportedScheme);
            certificates.add(cert);
        }

        String data = "hello world";
        File file = generateTempFile(data);
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] signature = signData(altScheme.signatureAlgorithm(), altScheme.privateKey().get(), bytes);

        SignatureValidator validator = this.buildSignatureValidator(scheme)
            .forSignature(signature)
            .withAdditionalCertificates(certificates);

        assertTrue(validator.validate(file));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testValidateWithFileFailsWithNoMatchingCertificates(Scheme scheme) throws Exception {
        Scheme altScheme = CryptoUtil.generateSchemeFromScheme(scheme);

        List<X509Certificate> certificates = new ArrayList<>();
        for (Scheme supportedScheme : CryptoUtil.SUPPORTED_SCHEMES.values()) {
            X509Certificate cert = CryptoUtil.generateX509Certificate(supportedScheme);
            certificates.add(cert);
        }

        String data = "hello world";
        File file = generateTempFile(data);
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] signature = signData(altScheme.signatureAlgorithm(), altScheme.privateKey().get(), bytes);

        SignatureValidator validator = this.buildSignatureValidator(scheme)
            .forSignature(signature)
            .withAdditionalCertificates(certificates);

        assertFalse(validator.validate(file));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testValidateWithFileRequiresSignature(Scheme scheme) throws Exception {
        String data = "hello world";
        File file = generateTempFile(data);

        SignatureValidator validator = this.buildSignatureValidator(scheme);

        assertThrows(IllegalStateException.class, () -> validator.validate(file));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testValidateWithFileRejectsNullFileObjects(Scheme scheme) throws Exception {
        String data = "hello world";

        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] signature = signData(scheme.signatureAlgorithm(), scheme.privateKey().get(), bytes);

        SignatureValidator validator = this.buildSignatureValidator(scheme)
            .forSignature(signature);

        assertThrows(IllegalArgumentException.class, () -> validator.validate((File) null));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testValidateWithFileThrowsExceptionOnFileMissing(Scheme scheme) throws Exception {
        String data = "hello world";

        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] signature = signData(scheme.signatureAlgorithm(), scheme.privateKey().get(), bytes);

        SignatureValidator validator = this.buildSignatureValidator(scheme)
            .forSignature(signature);

        assertThrows(IOException.class, () -> validator.validate(new File("this_file_shouldnt_exist.pls")));
    }
}
