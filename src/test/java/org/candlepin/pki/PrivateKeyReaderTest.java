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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.test.CryptoUtil;
import org.candlepin.test.TestUtil;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.jcajce.JcePKCSPBEInputDecryptorProviderBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.security.KeyException;
import java.security.PrivateKey;
import java.util.List;
import java.util.stream.Stream;



/**
 * Base test suite for PrivateKeyReaders. Subclasses are expected to provide a concrete PrivateKeyReader
 * implementation to test, as well as test for any implementation-specific functionality not covered by the
 * base PrivateKeyReader API/interface.
 */
public abstract class PrivateKeyReaderTest {

    // A list of known, supported schemes
    private static final List<Scheme> SUPPORTED_SCHEMES = CryptoUtil.generateSupportedSchemes().toList();

    private static Stream<Arguments> schemeSource() {
        return SUPPORTED_SCHEMES.stream()
            .map(Arguments::of);
    }

    /**
     * Builds a new PrivateKeyReader instance to test. Each invocation of this method should return a new
     * instance to avoid unintended object state retention between tests.
     *
     * @return
     *  a new PrivateKeyReader instance to test
     */
    protected abstract PrivateKeyReader buildPrivateKeyReader();

    protected InputStream getResourceAsStream(String resource) {
        return this.getClass()
            .getClassLoader()
            .getResourceAsStream(resource);
    }

    private static File writeKeyToFile(PrivateKey key, String password) throws KeyException, IOException {
        File keyFile = File.createTempFile("cp_test_key", ".pem");
        keyFile.deleteOnExit();

        CryptoUtil.writePrivateKeyToFile(key, keyFile, password);
        return keyFile;
    }

    private static File writeKeyDataToFile(String keydata) throws IOException {
        File keyFile = File.createTempFile("cp_test_key", ".pem");
        keyFile.deleteOnExit();

        try (Writer writer = new FileWriter(keyFile)) {
            writer.write(keydata);
        }

        return keyFile;
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testReadUnencryptedPrivateKeyFromInputStreamNullPW(Scheme scheme) throws Exception {
        PrivateKeyReader pkreader = this.buildPrivateKeyReader();

        File file = writeKeyToFile(scheme.privateKey(), null);
        InputStream istream = new FileInputStream(file);

        PrivateKey output = pkreader.read(istream, null);
        assertEquals(scheme.privateKey(), output);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testReadUnencryptedPrivateKeyFromInputStreamEmptyPW(Scheme scheme) throws Exception {
        PrivateKeyReader pkreader = this.buildPrivateKeyReader();

        File file = writeKeyToFile(scheme.privateKey(), null);
        InputStream istream = new FileInputStream(file);

        PrivateKey output = pkreader.read(istream, "");
        assertEquals(scheme.privateKey(), output);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testReadUnencryptedPrivateKeyFromFileNullPW(Scheme scheme) throws Exception {
        PrivateKeyReader pkreader = this.buildPrivateKeyReader();

        File file = writeKeyToFile(scheme.privateKey(), null);

        PrivateKey output = pkreader.read(file, null);
        assertEquals(scheme.privateKey(), output);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testReadUnencryptedPrivateKeyFromFileEmptyPW(Scheme scheme) throws Exception {
        PrivateKeyReader pkreader = this.buildPrivateKeyReader();

        File file = writeKeyToFile(scheme.privateKey(), null);

        PrivateKey output = pkreader.read(file, "");
        assertEquals(scheme.privateKey(), output);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testReadUnencryptedPrivateKeyFromPathNullPW(Scheme scheme) throws Exception {
        PrivateKeyReader pkreader = this.buildPrivateKeyReader();

        File file = writeKeyToFile(scheme.privateKey(), null);

        PrivateKey output = pkreader.read(file.getAbsolutePath(), null);
        assertEquals(scheme.privateKey(), output);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testReadUnencryptedPrivateKeyFromPathEmptyPW(Scheme scheme) throws Exception {
        PrivateKeyReader pkreader = this.buildPrivateKeyReader();

        File file = writeKeyToFile(scheme.privateKey(), null);

        PrivateKey output = pkreader.read(file.getAbsolutePath(), "");
        assertEquals(scheme.privateKey(), output);
    }


    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testReadEncryptedPrivateKeyFromInputStream(Scheme scheme) throws Exception {
        PrivateKeyReader pkreader = this.buildPrivateKeyReader();

        String password = TestUtil.randomString(32, TestUtil.CHARSET_ALPHANUMERIC);
        File file = writeKeyToFile(scheme.privateKey(), password);
        InputStream istream = new FileInputStream(file);

        PrivateKey output = pkreader.read(istream, password);
        assertEquals(scheme.privateKey(), output);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testReadEncryptedPrivateKeyFromFile(Scheme scheme) throws Exception {
        PrivateKeyReader pkreader = this.buildPrivateKeyReader();

        String password = TestUtil.randomString(32, TestUtil.CHARSET_ALPHANUMERIC);
        File file = writeKeyToFile(scheme.privateKey(), password);

        PrivateKey output = pkreader.read(file, password);
        assertEquals(scheme.privateKey(), output);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testReadEncryptedPrivateKeyFromPath(Scheme scheme) throws Exception {
        PrivateKeyReader pkreader = this.buildPrivateKeyReader();

        String password = TestUtil.randomString(32, TestUtil.CHARSET_ALPHANUMERIC);
        File file = writeKeyToFile(scheme.privateKey(), password);

        PrivateKey output = pkreader.read(file.getAbsolutePath(), password);
        assertEquals(scheme.privateKey(), output);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testReadEncryptedPrivateKeyFromInputStreamRequiresPassword(Scheme scheme) throws Exception {
        PrivateKeyReader pkreader = this.buildPrivateKeyReader();

        String password = TestUtil.randomString(32, TestUtil.CHARSET_ALPHANUMERIC);
        File file = writeKeyToFile(scheme.privateKey(), password);

        assertThatThrownBy(() -> pkreader.read(new FileInputStream(file), null))
            .isInstanceOf(KeyException.class)
            .hasMessageContaining("encrypted")
            .hasMessageContaining("passphrase");

        assertThatThrownBy(() -> pkreader.read(new FileInputStream(file), ""))
            .isInstanceOf(KeyException.class)
            .hasMessageContaining("encrypted")
            .hasMessageContaining("passphrase");
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testReadEncryptedPrivateKeyFromFileRequiresPassword(Scheme scheme) throws Exception {
        PrivateKeyReader pkreader = this.buildPrivateKeyReader();

        String password = TestUtil.randomString(32, TestUtil.CHARSET_ALPHANUMERIC);
        File file = writeKeyToFile(scheme.privateKey(), password);

        assertThatThrownBy(() -> pkreader.read(file, null))
            .isInstanceOf(KeyException.class)
            .hasMessageContaining("encrypted")
            .hasMessageContaining("passphrase");

        assertThatThrownBy(() -> pkreader.read(file, ""))
            .isInstanceOf(KeyException.class)
            .hasMessageContaining("encrypted")
            .hasMessageContaining("passphrase");
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testReadEncryptedPrivateKeyFromPathRequiresPassword(Scheme scheme) throws Exception {
        PrivateKeyReader pkreader = this.buildPrivateKeyReader();

        String password = TestUtil.randomString(32, TestUtil.CHARSET_ALPHANUMERIC);
        File file = writeKeyToFile(scheme.privateKey(), password);

        assertThatThrownBy(() -> pkreader.read(file.getAbsolutePath(), null))
            .isInstanceOf(KeyException.class)
            .hasMessageContaining("encrypted")
            .hasMessageContaining("passphrase");

        assertThatThrownBy(() -> pkreader.read(file.getAbsolutePath(), ""))
            .isInstanceOf(KeyException.class)
            .hasMessageContaining("encrypted")
            .hasMessageContaining("passphrase");
    }

    @ParameterizedTest
    @ValueSource(strings = { "password" })
    @NullAndEmptySource
    public void testReadFromInputStreamRequiresInputStream(String password) {
        PrivateKeyReader pkreader = this.buildPrivateKeyReader();

        assertThrows(IllegalArgumentException.class, () -> pkreader.read((InputStream) null, password));
    }

    @ParameterizedTest
    @ValueSource(strings = { "password" })
    @NullAndEmptySource
    public void testReadFromFileRequiresInputStream(String password) {
        PrivateKeyReader pkreader = this.buildPrivateKeyReader();

        assertThrows(IllegalArgumentException.class, () -> pkreader.read((File) null, password));
    }

    @ParameterizedTest
    @ValueSource(strings = { "password" })
    @NullAndEmptySource
    public void testReadFromPathRequiresInputStream(String password) {
        PrivateKeyReader pkreader = this.buildPrivateKeyReader();

        assertThrows(IllegalArgumentException.class, () -> pkreader.read((String) null, password));
    }

    private static Stream<Arguments> malformedKeySource() {
        String empty = "";

        String garbage = TestUtil.randomString(128, TestUtil.CHARSET_ALPHANUMERIC);

        String noHeader = """
            MIIJKAIBAAKCAgEAudp7ZjjPyyfJZowD1OcAiG4cIz4azAhxPpp5Py0xb880xfa+
            4XMWeR4b32YU6rsBTpGqoCCPp08KYsqlOW0+Gj6Oi2I/MZbcrrnioUOyil1/Lo/1
            """;

        String noFooter = """
            -----BEGIN RSA PRIVATE KEY-----
            MIIJKAIBAAKCAgEAudp7ZjjPyyfJZowD1OcAiG4cIz4azAhxPpp5Py0xb880xfa+
            4XMWeR4b32YU6rsBTpGqoCCPp08KYsqlOW0+Gj6Oi2I/MZbcrrnioUOyil1/Lo/1
            """;

        String doublePemHeader = """
            -----BEGIN RSA PRIVATE KEY-----
            Proc-Type: 4,ENCRYPTED
            DEK-Info: AES-256-CBC,A4E76EB0315C87607649FB5E1FB975B1
            Proc-Type: 4,ENCRYPTED

            MIIJKAIBAAKCAgEAudp7ZjjPyyfJZowD1OcAiG4cIz4azAhxPpp5Py0xb880xfa+
            4XMWeR4b32YU6rsBTpGqoCCPp08KYsqlOW0+Gj6Oi2I/MZbcrrnioUOyil1/Lo/1
            -----END RSA PRIVATE KEY-----
            """;


        Stream<String> strings = Stream.of(
            empty,
            garbage,
            noHeader,
            noFooter,
            doublePemHeader
        );

        return strings.map(Arguments::of);
    }

    @ParameterizedTest(name = "{displayName}: {index}")
    @MethodSource("malformedKeySource")
    public void testThrowsExceptionOnMalformedKeyFromInputStream(String keydata) throws Exception {
        PrivateKeyReader pkreader = this.buildPrivateKeyReader();

        File file = writeKeyDataToFile(keydata);
        InputStream istream = new FileInputStream(file);

        assertThrows(KeyException.class, () -> pkreader.read(istream, null));
    }

    @ParameterizedTest(name = "{displayName}: {index}")
    @MethodSource("malformedKeySource")
    public void testThrowsExceptionOnMalformedKeyFromFile(String keydata) throws Exception {
        PrivateKeyReader pkreader = this.buildPrivateKeyReader();

        File file = writeKeyDataToFile(keydata);

        assertThrows(KeyException.class, () -> pkreader.read(file, null));
    }

    @ParameterizedTest(name = "{displayName}: {index}")
    @MethodSource("malformedKeySource")
    public void testThrowsExceptionOnMalformedKeyFromFilePath(String keydata) throws Exception {
        PrivateKeyReader pkreader = this.buildPrivateKeyReader();

        File file = writeKeyDataToFile(keydata);

        assertThrows(KeyException.class, () -> pkreader.read(file.getAbsolutePath(), null));
    }

    @ParameterizedTest(name = "{displayName}: {index}")
    @MethodSource("malformedKeySource")
    public void testThrowsExceptionOnMalformedKeyFromInputStreamWithPassword(String keydata)
        throws Exception {

        PrivateKeyReader pkreader = this.buildPrivateKeyReader();

        String password = TestUtil.randomString(32, TestUtil.CHARSET_ALPHANUMERIC);
        File file = writeKeyDataToFile(keydata);
        InputStream istream = new FileInputStream(file);

        assertThrows(KeyException.class, () -> pkreader.read(istream, password));
    }

    @ParameterizedTest(name = "{displayName}: {index}")
    @MethodSource("malformedKeySource")
    public void testThrowsExceptionOnMalformedKeyFromFileWithPassword(String keydata) throws Exception {
        PrivateKeyReader pkreader = this.buildPrivateKeyReader();

        String password = TestUtil.randomString(32, TestUtil.CHARSET_ALPHANUMERIC);
        File file = writeKeyDataToFile(keydata);

        assertThrows(KeyException.class, () -> pkreader.read(file, password));
    }

    @ParameterizedTest(name = "{displayName}: {index}")
    @MethodSource("malformedKeySource")
    public void testThrowsExceptionOnMalformedKeyFromFilePathWithPassword(String keydata) throws Exception {
        PrivateKeyReader pkreader = this.buildPrivateKeyReader();

        String password = TestUtil.randomString(32, TestUtil.CHARSET_ALPHANUMERIC);
        File file = writeKeyDataToFile(keydata);

        assertThrows(KeyException.class, () -> pkreader.read(file.getAbsolutePath(), password));
    }


    // Legacy tests
    // TODO: FIXME: I'm unsure what to do with these. They have value in that we still want to support these
    // key files (probably), but the way they go about testing is heavily coupled to the current crypto
    // provider due to how they manually load keys. As an amusing aside, the logic here is far less complex
    // than the logic in the PKR despite doing the same stuff.
    private static final char[] PASSWORD = "password".toCharArray();

    @Test
    public void testReadUnencryptedPKCS8() throws Exception {
        String keyFile = "keys/pkcs8-unencrypted.pem";
        try (
            InputStream keyStream = this.getResourceAsStream(keyFile);
            Reader expectedReader = new InputStreamReader(this.getResourceAsStream(keyFile));
        ) {
            PrivateKey actualKey = this.buildPrivateKeyReader().read(keyStream, null);
            PrivateKeyInfo expected = (PrivateKeyInfo) new PEMParser(expectedReader).readObject();
            PrivateKey expectedKey = new JcaPEMKeyConverter()
                .setProvider(CryptoUtil.getSecurityProvider().get())
                .getPrivateKey(expected);
            assertEquals(actualKey, expectedKey);
        }
    }

    @Test
    public void testReadEncryptedPKCS8() throws Exception {
        String keyFile = "keys/pkcs8-aes256-encrypted.pem";
        try (
            InputStream keyStream = this.getResourceAsStream(keyFile);
            Reader expectedReader = new InputStreamReader(this.getResourceAsStream(keyFile));
        ) {
            PrivateKey actualKey = this.buildPrivateKeyReader().read(keyStream, "password");

            PKCS8EncryptedPrivateKeyInfo expected =
                (PKCS8EncryptedPrivateKeyInfo) new PEMParser(expectedReader).readObject();

            // the PBE in JcePKCSPBEInputDecryptorProviderBuilder stands for "password based encryption"
            InputDecryptorProvider provider = new JcePKCSPBEInputDecryptorProviderBuilder()
                .setProvider(CryptoUtil.getSecurityProvider().get())
                .build(PASSWORD);
            PrivateKeyInfo decryptedInfo = expected.decryptPrivateKeyInfo(provider);
            PrivateKey expectedKey = new JcaPEMKeyConverter()
                .setProvider(CryptoUtil.getSecurityProvider().get())
                .getPrivateKey(decryptedInfo);
            assertEquals(actualKey, expectedKey);
        }
    }

    @Test
    public void testReadPKCS1() throws Exception {
        String keyFile = "keys/pkcs1-unencrypted.pem";
        try (
            InputStream keyStream = this.getResourceAsStream(keyFile);
            Reader expectedReader = new InputStreamReader(this.getResourceAsStream(keyFile));
        ) {
            PrivateKey actualKey = this.buildPrivateKeyReader().read(keyStream, null);
            PEMKeyPair expected = (PEMKeyPair) new PEMParser(expectedReader).readObject();
            PrivateKey expectedKey = new JcaPEMKeyConverter()
                .setProvider(CryptoUtil.getSecurityProvider().get())
                .getKeyPair(expected)
                .getPrivate();
            assertEquals(actualKey, expectedKey);
        }
    }

    @Test
    public void testReadEncryptedPKCS1() throws Exception {
        openPKCS1("keys/pkcs1-aes256-encrypted.pem", "password");
    }

    @Test
    public void testRead3DESEncryptedPKCS1() throws Exception {
        openPKCS1("keys/pkcs1-des-encrypted.pem", "password");
    }

    private void openPKCS1(String keyFile, String password) throws Exception {
        try (
            InputStream keyStream = this.getResourceAsStream(keyFile);
            Reader expectedReader = new InputStreamReader(this.getResourceAsStream(keyFile));
        ) {
            PrivateKey actualKey = this.buildPrivateKeyReader().read(keyStream, password);
            PEMEncryptedKeyPair expected = (PEMEncryptedKeyPair) new PEMParser(expectedReader).readObject();

            PEMDecryptorProvider provider = new JcePEMDecryptorProviderBuilder()
                .setProvider(CryptoUtil.getSecurityProvider().get())
                .build(PASSWORD);

            PEMKeyPair decryptedInfo = expected.decryptKeyPair(provider);
            PrivateKey expectedKey = new JcaPEMKeyConverter()
                .setProvider(CryptoUtil.getSecurityProvider().get())
                .getKeyPair(decryptedInfo)
                .getPrivate();
            assertEquals(actualKey, expectedKey);
        }
    }
}
