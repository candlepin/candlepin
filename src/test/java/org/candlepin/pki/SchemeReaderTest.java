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

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.config.ConfigurationException;
import org.candlepin.config.DevConfig;
import org.candlepin.test.CryptoUtil;
import org.candlepin.test.TestUtil;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;



public class SchemeReaderTest {

    private static Stream<Arguments> schemeSource() {
        return CryptoUtil.SUPPORTED_SCHEMES.values()
            .stream()
            .map(Arguments::of);
    }

    private static File writeKeyToFile(PrivateKey key, String password) throws KeyException, IOException {
        File keyFile = File.createTempFile("cp_test_key", ".pem");
        keyFile.deleteOnExit();

        CryptoUtil.writePrivateKeyToFile(key, keyFile, password);
        return keyFile;
    }

    private static File writeCertToFile(X509Certificate certificate) throws IOException {
        File certFile = File.createTempFile("cp_test_cert", ".pem");
        certFile.deleteOnExit();

        CryptoUtil.writeCertificateToFile(certificate, certFile);
        return certFile;
    }

    private static void writeSchemeConfig(DevConfig config, Scheme scheme, String schemeName,
        String keyPassword) throws KeyException, IOException {

        String prefix = ConfigProperties.schemePrefix(schemeName);

        File keyFile = writeKeyToFile(scheme.privateKey().get(), keyPassword);
        File certFile = writeCertToFile(scheme.certificate());

        config.setProperty(prefix + ConfigProperties.CRYPTO_SCHEME_CERT, certFile.getCanonicalPath());
        config.setProperty(prefix + ConfigProperties.CRYPTO_SCHEME_KEY, keyFile.getCanonicalPath());

        if (keyPassword != null) {
            config.setProperty(prefix + ConfigProperties.CRYPTO_SCHEME_KEY_PASSWORD, keyPassword);
        }

        config.setProperty(prefix + ConfigProperties.CRYPTO_SCHEME_SIGNATURE_ALGORITHM,
            scheme.signatureAlgorithm());
        config.setProperty(prefix + ConfigProperties.CRYPTO_SCHEME_KEY_ALGORITHM, scheme.keyAlgorithm());

        scheme.keySize().ifPresent(keySize ->
            config.setProperty(prefix + ConfigProperties.CRYPTO_SCHEME_KEY_SIZE, keySize.toString()));
    }

    private static void writeSchemeConfig(DevConfig config, Scheme scheme, String keyPassword)
        throws KeyException, IOException {

        writeSchemeConfig(config, scheme, scheme.name(), keyPassword);
    }

    private void assertSchemesAreEqual(Scheme actual, Scheme expected) {
        if (expected == null) {
            assertNull(actual);
            return;
        }

        assertThat(actual)
            .returns(expected.name(), Scheme::name)
            .returns(expected.privateKey(), Scheme::privateKey)
            .returns(expected.certificate(), Scheme::certificate)
            .returns(expected.signatureAlgorithm(), Scheme::signatureAlgorithm)
            .returns(expected.keyAlgorithm(), Scheme::keyAlgorithm)
            .returns(expected.keySize(), Scheme::keySize);
    }

    private CertificateReader mockCertificateReader(String path, X509Certificate certificate)
        throws CertificateException {

        CertificateReader mockCertificateReader = mock(CertificateReader.class);

        doReturn(certificate)
            .when(mockCertificateReader)
            .read(any(InputStream.class));

        doReturn(certificate)
            .when(mockCertificateReader)
            .read(eq(new File(path)));

        doReturn(certificate)
            .when(mockCertificateReader)
            .read(eq(path));

        return mockCertificateReader;
    }

    private PrivateKeyReader mockPrivateKeyReader(String path, PrivateKey privateKey) throws KeyException {
        PrivateKeyReader mockPrivateKeyReader = mock(PrivateKeyReader.class);

        doReturn(privateKey)
            .when(mockPrivateKeyReader)
            .read(any(InputStream.class), any());

        doReturn(privateKey)
            .when(mockPrivateKeyReader)
            .read(eq(new File(path)), any());

        doReturn(privateKey)
            .when(mockPrivateKeyReader)
            .read(eq(path), any());

        return mockPrivateKeyReader;
    }

    private SchemeReader buildSchemeReader(Configuration config) throws CertificateException {
        return new SchemeReader(config, CryptoUtil.getPrivateKeyReader(), CryptoUtil.getCertificateReader());
    }


    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testReadSchemesWithIndividualSchemeNoKeyPassword(Scheme scheme) throws Exception {
        DevConfig config = new DevConfig();

        writeSchemeConfig(config, scheme, null);
        config.setProperty(ConfigProperties.CRYPTO_SCHEMES, scheme.name());

        SchemeReader reader = this.buildSchemeReader(config);
        List<Scheme> output = reader.readSchemes();

        assertThat(output)
            .isNotNull()
            .singleElement()
            .satisfies(actual -> assertSchemesAreEqual(actual, scheme));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testReadSchemesWithIndividualSchemeWithKeyPassword(Scheme scheme) throws Exception {
        DevConfig config = new DevConfig();

        writeSchemeConfig(config, scheme, "cp_test_key_passWORD123!@#");
        config.setProperty(ConfigProperties.CRYPTO_SCHEMES, scheme.name());

        SchemeReader reader = this.buildSchemeReader(config);
        List<Scheme> output = reader.readSchemes();

        assertThat(output)
            .isNotNull()
            .singleElement()
            .satisfies(actual -> assertSchemesAreEqual(actual, scheme));
    }

    @Test
    public void testReadSchemeWithAllSupportedSchemes() throws Exception {
        DevConfig config = new DevConfig();
        Map<String, Scheme> schemeMap = new HashMap<>();

        for (Scheme scheme : CryptoUtil.SUPPORTED_SCHEMES.values()) {
            schemeMap.put(scheme.name(), scheme);
            writeSchemeConfig(config, scheme, null);
        }

        config.setProperty(ConfigProperties.CRYPTO_SCHEMES, String.join(", ", schemeMap.keySet()));

        SchemeReader reader = this.buildSchemeReader(config);

        List<Scheme> output = reader.readSchemes();

        assertThat(output)
            .isNotNull()
            .hasSize(schemeMap.size());

        for (Scheme actual : output) {
            Scheme expected = schemeMap.get(actual.name());
            assertNotNull(expected);

            assertSchemesAreEqual(actual, expected);
        }
    }

    @Test
    public void testReadSchemesThrowsExceptionOnMissingSchemesList() throws Exception {
        DevConfig config = new DevConfig();

        for (Scheme scheme : CryptoUtil.SUPPORTED_SCHEMES.values()) {
            writeSchemeConfig(config, scheme, null);
        }

        SchemeReader reader = this.buildSchemeReader(config);

        assertThatThrownBy(() -> reader.readSchemes())
            .isInstanceOf(ConfigurationException.class)
            .hasMessageContaining("No crypto schemes defined");
    }

    @ParameterizedTest
    @ValueSource(strings = { "abc!@#*$)", "-hyphen_first", "uni©ode" })
    public void testReadSchemesThrowsExceptionOnInvalidSchemeNameInList(String schemeName) throws Exception {
        DevConfig config = new DevConfig();
        List<String> schemeNames = new ArrayList<>();
        schemeNames.add(schemeName);

        for (Scheme scheme : CryptoUtil.SUPPORTED_SCHEMES.values()) {
            schemeNames.add(scheme.name());
            writeSchemeConfig(config, scheme, null);
        }

        config.setProperty(ConfigProperties.CRYPTO_SCHEMES, String.join(", ", schemeNames));

        SchemeReader reader = this.buildSchemeReader(config);

        assertThatThrownBy(() -> reader.readSchemes())
            .isInstanceOf(ConfigurationException.class)
            .hasMessageContaining("Malformed crypto schemes declaration");
    }

    @Test
    public void testReadSchemesThrowsExceptionOnMissingSchemeNameInList() throws Exception {
        DevConfig config = new DevConfig();
        List<String> schemeNames = new ArrayList<>();
        schemeNames.add("missing_scheme");

        for (Scheme scheme : CryptoUtil.SUPPORTED_SCHEMES.values()) {
            schemeNames.add(scheme.name());
            writeSchemeConfig(config, scheme, null);
        }

        config.setProperty(ConfigProperties.CRYPTO_SCHEMES, String.join(", ", schemeNames));

        SchemeReader reader = this.buildSchemeReader(config);

        assertThatThrownBy(() -> reader.readSchemes())
            .isInstanceOf(ConfigurationException.class)
            .hasMessageContaining("Unable to read scheme");
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testReadSchemesThrowsExceptionOnUnreadableKey(Scheme scheme) throws Exception {
        DevConfig config = new DevConfig();

        writeSchemeConfig(config, scheme, null);
        config.setProperty(ConfigProperties.CRYPTO_SCHEMES, scheme.name());

        // set the key property to some non-existent file
        config.setProperty(ConfigProperties.schemeConfig(scheme.name(), ConfigProperties.CRYPTO_SCHEME_KEY),
            "path_does_not_exist");

        SchemeReader reader = this.buildSchemeReader(config);

        assertThatThrownBy(() -> reader.readSchemes())
            .isInstanceOf(ConfigurationException.class)
            .hasMessageContaining("Unable to read private key");
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testReadSchemesThrowsExceptionOnKeyWithWrongPassword(Scheme scheme) throws Exception {
        DevConfig config = new DevConfig();

        writeSchemeConfig(config, scheme, "password");
        config.setProperty(ConfigProperties.CRYPTO_SCHEMES, scheme.name());
        config.setProperty(ConfigProperties.schemeConfig(scheme.name(),
            ConfigProperties.CRYPTO_SCHEME_KEY_PASSWORD), "wrong_password");

        SchemeReader reader = this.buildSchemeReader(config);

        assertThatThrownBy(() -> reader.readSchemes())
            .isInstanceOf(ConfigurationException.class)
            .hasMessageContaining("Unable to read private key");
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testReadSchemesThrowsExceptionOnKeyWithNoPassword(Scheme scheme) throws Exception {
        DevConfig config = new DevConfig();

        writeSchemeConfig(config, scheme, "password");
        config.setProperty(ConfigProperties.CRYPTO_SCHEMES, scheme.name());
        config.clearProperty(ConfigProperties.schemeConfig(scheme.name(),
            ConfigProperties.CRYPTO_SCHEME_KEY_PASSWORD));

        SchemeReader reader = this.buildSchemeReader(config);

        assertThatThrownBy(() -> reader.readSchemes())
            .isInstanceOf(ConfigurationException.class)
            .hasMessageContaining("Unable to read private key");
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testReadSchemesThrowsExceptionOnUnreadableCert(Scheme scheme) throws Exception {
        DevConfig config = new DevConfig();

        writeSchemeConfig(config, scheme, null);
        config.setProperty(ConfigProperties.CRYPTO_SCHEMES, scheme.name());

        // set the key property to some non-existent file
        config.setProperty(ConfigProperties.schemeConfig(scheme.name(), ConfigProperties.CRYPTO_SCHEME_CERT),
            "path_does_not_exist");

        SchemeReader reader = this.buildSchemeReader(config);

        assertThatThrownBy(() -> reader.readSchemes())
            .isInstanceOf(ConfigurationException.class)
            .hasMessageContaining("Unable to read certificate");
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testReadDefaultScheme(Scheme scheme) throws Exception {
        DevConfig config = new DevConfig();

        writeSchemeConfig(config, scheme, null);
        config.setProperty(ConfigProperties.CRYPTO_DEFAULT_SCHEME, scheme.name());

        SchemeReader reader = this.buildSchemeReader(config);
        Scheme output = reader.readDefaultScheme();

        assertSchemesAreEqual(output, scheme);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testReadDefaultSchemeWithEncryptedKey(Scheme scheme) throws Exception {
        DevConfig config = new DevConfig();
        String password = TestUtil.randomString(16, TestUtil.CHARSET_ALPHANUMERIC);

        writeSchemeConfig(config, scheme, password);
        config.setProperty(ConfigProperties.CRYPTO_DEFAULT_SCHEME, scheme.name());

        SchemeReader reader = this.buildSchemeReader(config);
        Scheme output = reader.readDefaultScheme();

        assertSchemesAreEqual(output, scheme);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testReadDefaultSchemeDefaultsToLegacyScheme(Scheme scheme) throws Exception {
        DevConfig config = new DevConfig();

        writeSchemeConfig(config, scheme, SchemeReader.LEGACY_SCHEME, null);

        // Ensure we have no property defined for the default
        config.clearProperty(ConfigProperties.CRYPTO_DEFAULT_SCHEME);

        SchemeReader reader = this.buildSchemeReader(config);
        Scheme output = reader.readDefaultScheme();

        assertThat(output)
            .returns(SchemeReader.LEGACY_SCHEME, Scheme::name)
            .returns(scheme.privateKey(), Scheme::privateKey)
            .returns(scheme.certificate(), Scheme::certificate)
            .returns(scheme.signatureAlgorithm(), Scheme::signatureAlgorithm)
            .returns(scheme.keyAlgorithm(), Scheme::keyAlgorithm)
            .returns(scheme.keySize(), Scheme::keySize);
    }

    @Test
    public void testReadDefaultSchemeThrowsExceptionOnMissingScheme() throws Exception {
        DevConfig config = new DevConfig();

        config.setProperty(ConfigProperties.CRYPTO_DEFAULT_SCHEME, "missing_scheme");

        SchemeReader reader = this.buildSchemeReader(config);

        assertThatThrownBy(() -> reader.readDefaultScheme())
            .isInstanceOf(ConfigurationException.class)
            .hasMessageContaining("Unable to read scheme");
    }

    @ParameterizedTest
    @ValueSource(strings = { "abc!@#*$)", "-hyphen_first", "uni©ode" })
    public void testReadDefaultSchemeThrowsExceptionOnInvalidScheme(String schemeName) throws Exception {
        DevConfig config = new DevConfig();

        config.setProperty(ConfigProperties.CRYPTO_DEFAULT_SCHEME, schemeName);

        SchemeReader reader = this.buildSchemeReader(config);

        assertThatThrownBy(() -> reader.readDefaultScheme())
            .isInstanceOf(ConfigurationException.class)
            .hasMessageContaining("Malformed default crypto scheme declaration");
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testReadDefaultSchemeThrowsExceptionOnUnreadableKey(Scheme scheme) throws Exception {
        DevConfig config = new DevConfig();

        writeSchemeConfig(config, scheme, null);
        config.setProperty(ConfigProperties.CRYPTO_DEFAULT_SCHEME, scheme.name());

        // set the key property to some non-existent file
        config.setProperty(ConfigProperties.schemeConfig(scheme.name(), ConfigProperties.CRYPTO_SCHEME_KEY),
            "path_does_not_exist");

        SchemeReader reader = this.buildSchemeReader(config);

        assertThatThrownBy(() -> reader.readDefaultScheme())
            .isInstanceOf(ConfigurationException.class)
            .hasMessageContaining("Unable to read private key");
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testReadDefaultSchemeThrowsExceptionOnEncryptedKeyWithWrongPassword(Scheme scheme)
        throws Exception {

        DevConfig config = new DevConfig();

        writeSchemeConfig(config, scheme, "password");
        config.setProperty(ConfigProperties.CRYPTO_DEFAULT_SCHEME, scheme.name());
        config.setProperty(ConfigProperties.schemeConfig(scheme.name(),
            ConfigProperties.CRYPTO_SCHEME_KEY_PASSWORD), "wrong_password");

        SchemeReader reader = this.buildSchemeReader(config);

        assertThatThrownBy(() -> reader.readDefaultScheme())
            .isInstanceOf(ConfigurationException.class)
            .hasMessageContaining("Unable to read private key");
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testReadDefaultSchemeThrowsExceptionOnEncryptedKeyWithNoPassword(Scheme scheme)
        throws Exception {

        DevConfig config = new DevConfig();

        writeSchemeConfig(config, scheme, "password");
        config.setProperty(ConfigProperties.CRYPTO_DEFAULT_SCHEME, scheme.name());
        config.clearProperty(ConfigProperties.schemeConfig(scheme.name(),
            ConfigProperties.CRYPTO_SCHEME_KEY_PASSWORD));

        SchemeReader reader = this.buildSchemeReader(config);

        assertThatThrownBy(() -> reader.readDefaultScheme())
            .isInstanceOf(ConfigurationException.class)
            .hasMessageContaining("Unable to read private key");
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testReadDefaultSchemeThrowsExceptionOnUnreadableCert(Scheme scheme) throws Exception {
        DevConfig config = new DevConfig();

        writeSchemeConfig(config, scheme, null);
        config.setProperty(ConfigProperties.CRYPTO_DEFAULT_SCHEME, scheme.name());

        // set the key property to some non-existent file
        config.setProperty(ConfigProperties.schemeConfig(scheme.name(), ConfigProperties.CRYPTO_SCHEME_CERT),
            "path_does_not_exist");

        SchemeReader reader = this.buildSchemeReader(config);

        assertThatThrownBy(() -> reader.readDefaultScheme())
            .isInstanceOf(ConfigurationException.class)
            .hasMessageContaining("Unable to read certificate");
    }

    // Impl note: currently the "read legacy" tests all use readDefaultScheme with a configuration that points
    // the default scheme to the legacy scheme. In the future if readScheme is made public, these tests should
    // be updated to directly target it instead of fetching it indirectly.

    @Test
    public void testReadLegacySchemeDefaultsToLegacyConfigEntries() throws Exception {
        // At the time of writing, our legacy scheme is RSA
        Scheme legacyScheme = CryptoUtil.generateRsaScheme();

        // Write the cert and key so our readers can load them without needing mocks
        File certificateFile = CryptoUtil.writeCertificateToFile(legacyScheme.certificate());
        File privateKeyFile = CryptoUtil.writePrivateKeyToFile(legacyScheme.privateKey().get(), null);

        // Intentionally define a mostly empty config so we're guaranteed to fall back to the legacy configs
        // where they exist
        DevConfig config = new DevConfig();
        config.setProperty(ConfigProperties.LEGACY_CA_CERT, certificateFile.getCanonicalPath());
        config.setProperty(ConfigProperties.LEGACY_CA_KEY, privateKeyFile.getCanonicalPath());

        // Ensure we're only loading the legacy scheme
        config.setProperty(ConfigProperties.CRYPTO_SCHEMES, SchemeReader.LEGACY_SCHEME);
        config.setProperty(ConfigProperties.CRYPTO_DEFAULT_SCHEME, SchemeReader.LEGACY_SCHEME);

        SchemeReader schemeReader = this.buildSchemeReader(config);
        Scheme defaultScheme = schemeReader.readDefaultScheme();

        assertThat(defaultScheme)
            .isNotNull()
            .returns(legacyScheme.certificate(), Scheme::certificate)
            .returns(legacyScheme.privateKey(), Scheme::privateKey)
            .returns(SchemeReader.LEGACY_SCHEME_DEFAULT_SIGNATURE_ALGORITHM, Scheme::signatureAlgorithm)
            .returns(SchemeReader.LEGACY_SCHEME_DEFAULT_KEY_ALGORITHM, Scheme::keyAlgorithm)
            .returns(Optional.of(SchemeReader.LEGACY_SCHEME_DEFAULT_KEY_SIZE), Scheme::keySize);
    }

    @Test
    public void testReadLegacySchemeDefaultsToLegacyValues() throws Exception {
        // At the time of writing, our legacy scheme is RSA
        Scheme legacyScheme = CryptoUtil.generateRsaScheme();

        // Intentionally define an empty config so we're guaranteed to fall back to the legacy defaults
        DevConfig config = new DevConfig();
        config.setProperty(ConfigProperties.CRYPTO_SCHEMES, SchemeReader.LEGACY_SCHEME);
        config.setProperty(ConfigProperties.CRYPTO_DEFAULT_SCHEME, SchemeReader.LEGACY_SCHEME);

        // Impl note: We have to use mocks here so we can load our test assets from "real" filenames. This
        // may requre updates later if the SchemeReader stops using the reader interfaces. :(
        CertificateReader mockCertificateReader = mockCertificateReader(
            SchemeReader.LEGACY_SCHEME_DEFAULT_CERT, legacyScheme.certificate());

        PrivateKeyReader mockPrivateKeyReader = mockPrivateKeyReader(
            SchemeReader.LEGACY_SCHEME_DEFAULT_KEY, legacyScheme.privateKey().get());

        SchemeReader schemeReader = new SchemeReader(config, mockPrivateKeyReader, mockCertificateReader);
        Scheme defaultScheme = schemeReader.readDefaultScheme();

        assertThat(defaultScheme)
            .isNotNull()
            .returns(legacyScheme.certificate(), Scheme::certificate)
            .returns(legacyScheme.privateKey(), Scheme::privateKey)
            .returns(SchemeReader.LEGACY_SCHEME_DEFAULT_SIGNATURE_ALGORITHM, Scheme::signatureAlgorithm)
            .returns(SchemeReader.LEGACY_SCHEME_DEFAULT_KEY_ALGORITHM, Scheme::keyAlgorithm)
            .returns(Optional.of(SchemeReader.LEGACY_SCHEME_DEFAULT_KEY_SIZE), Scheme::keySize);

        // We don't care *which* methods were called, just that we expect that our mocks were hit once each
        assertEquals(1, mockingDetails(mockPrivateKeyReader).getInvocations().size());
        assertEquals(1, mockingDetails(mockCertificateReader).getInvocations().size());
    }

    @Test
    public void testReadLegacySchemeAllowsOverrideOfCertificate() throws Exception {
        Scheme legacyScheme = CryptoUtil.generateRsaScheme();

        File certificateFile = CryptoUtil.writeCertificateToFile(legacyScheme.certificate());

        DevConfig config = new DevConfig();
        String certConfigKey = ConfigProperties.schemeConfig(SchemeReader.LEGACY_SCHEME,
            ConfigProperties.CRYPTO_SCHEME_CERT);

        config.setProperty(certConfigKey, certificateFile.getCanonicalPath());

        PrivateKeyReader mockPrivateKeyReader = mockPrivateKeyReader(
            SchemeReader.LEGACY_SCHEME_DEFAULT_KEY, legacyScheme.privateKey().get());

        SchemeReader schemeReader = new SchemeReader(config, mockPrivateKeyReader,
            CryptoUtil.getCertificateReader());
        Scheme defaultScheme = schemeReader.readDefaultScheme();

        assertThat(defaultScheme)
            .isNotNull()
            .returns(legacyScheme.certificate(), Scheme::certificate)
            .returns(legacyScheme.privateKey(), Scheme::privateKey)
            .returns(SchemeReader.LEGACY_SCHEME_DEFAULT_SIGNATURE_ALGORITHM, Scheme::signatureAlgorithm)
            .returns(SchemeReader.LEGACY_SCHEME_DEFAULT_KEY_ALGORITHM, Scheme::keyAlgorithm)
            .returns(Optional.of(SchemeReader.LEGACY_SCHEME_DEFAULT_KEY_SIZE), Scheme::keySize);
    }

    @Test
    public void testReadLegacySchemeAllowsOverrideOfPrivateKey() throws Exception {
        Scheme legacyScheme = CryptoUtil.generateRsaScheme();

        File privateKeyFile = CryptoUtil.writePrivateKeyToFile(legacyScheme.privateKey().get(), null);

        DevConfig config = new DevConfig();
        String privKeyConfigKey = ConfigProperties.schemeConfig(SchemeReader.LEGACY_SCHEME,
            ConfigProperties.CRYPTO_SCHEME_KEY);

        config.setProperty(privKeyConfigKey, privateKeyFile.getCanonicalPath());

        CertificateReader mockCertificateReader = mockCertificateReader(
            SchemeReader.LEGACY_SCHEME_DEFAULT_CERT, legacyScheme.certificate());

        SchemeReader schemeReader = new SchemeReader(config, CryptoUtil.getPrivateKeyReader(),
            mockCertificateReader);
        Scheme defaultScheme = schemeReader.readDefaultScheme();

        assertThat(defaultScheme)
            .isNotNull()
            .returns(legacyScheme.certificate(), Scheme::certificate)
            .returns(legacyScheme.privateKey(), Scheme::privateKey)
            .returns(SchemeReader.LEGACY_SCHEME_DEFAULT_SIGNATURE_ALGORITHM, Scheme::signatureAlgorithm)
            .returns(SchemeReader.LEGACY_SCHEME_DEFAULT_KEY_ALGORITHM, Scheme::keyAlgorithm)
            .returns(Optional.of(SchemeReader.LEGACY_SCHEME_DEFAULT_KEY_SIZE), Scheme::keySize);
    }

    @Test
    public void testReadLegacySchemeAllowsOverrideOfSignatureAlgorithm() throws Exception {
        Scheme legacyScheme = CryptoUtil.generateRsaScheme();

        DevConfig config = new DevConfig();
        String sigAlgoConfigKey = ConfigProperties.schemeConfig(SchemeReader.LEGACY_SCHEME,
            ConfigProperties.CRYPTO_SCHEME_SIGNATURE_ALGORITHM);
        String expectedValue = "test_sig_algorithm";

        config.setProperty(sigAlgoConfigKey, expectedValue);

        CertificateReader mockCertificateReader = mockCertificateReader(
            SchemeReader.LEGACY_SCHEME_DEFAULT_CERT, legacyScheme.certificate());

        PrivateKeyReader mockPrivateKeyReader = mockPrivateKeyReader(
            SchemeReader.LEGACY_SCHEME_DEFAULT_KEY, legacyScheme.privateKey().get());

        SchemeReader schemeReader = new SchemeReader(config, mockPrivateKeyReader, mockCertificateReader);
        Scheme defaultScheme = schemeReader.readDefaultScheme();

        assertThat(defaultScheme)
            .isNotNull()
            .returns(legacyScheme.certificate(), Scheme::certificate)
            .returns(legacyScheme.privateKey(), Scheme::privateKey)
            .returns(expectedValue, Scheme::signatureAlgorithm)
            .returns(SchemeReader.LEGACY_SCHEME_DEFAULT_KEY_ALGORITHM, Scheme::keyAlgorithm)
            .returns(Optional.of(SchemeReader.LEGACY_SCHEME_DEFAULT_KEY_SIZE), Scheme::keySize);
    }

    @Test
    public void testReadLegacySchemeAllowsOverrideOfKeyAlgorithm() throws Exception {
        Scheme legacyScheme = CryptoUtil.generateRsaScheme();

        DevConfig config = new DevConfig();
        String keyAlgoConfigKey = ConfigProperties.schemeConfig(SchemeReader.LEGACY_SCHEME,
            ConfigProperties.CRYPTO_SCHEME_KEY_ALGORITHM);
        String expectedValue = "test_key_algorithm";

        config.setProperty(keyAlgoConfigKey, expectedValue);

        CertificateReader mockCertificateReader = mockCertificateReader(
            SchemeReader.LEGACY_SCHEME_DEFAULT_CERT, legacyScheme.certificate());

        PrivateKeyReader mockPrivateKeyReader = mockPrivateKeyReader(
            SchemeReader.LEGACY_SCHEME_DEFAULT_KEY, legacyScheme.privateKey().get());

        SchemeReader schemeReader = new SchemeReader(config, mockPrivateKeyReader, mockCertificateReader);
        Scheme defaultScheme = schemeReader.readDefaultScheme();

        assertThat(defaultScheme)
            .isNotNull()
            .returns(legacyScheme.certificate(), Scheme::certificate)
            .returns(legacyScheme.privateKey(), Scheme::privateKey)
            .returns(SchemeReader.LEGACY_SCHEME_DEFAULT_SIGNATURE_ALGORITHM, Scheme::signatureAlgorithm)
            .returns(expectedValue, Scheme::keyAlgorithm)
            // If we have a different algorithm than the legacy algorithm, our default value for key size
            // changes to none. This behavior is tested explicitly elsewhere.
            .returns(Optional.empty(), Scheme::keySize);
    }

    @Test
    public void testReadLegacySchemeAllowsOverrideOfKeySize() throws Exception {
        Scheme legacyScheme = CryptoUtil.generateRsaScheme();

        DevConfig config = new DevConfig();
        String keySizeConfigKey = ConfigProperties.schemeConfig(SchemeReader.LEGACY_SCHEME,
            ConfigProperties.CRYPTO_SCHEME_KEY_SIZE);
        int expectedValue = 12345;

        config.setProperty(keySizeConfigKey, String.valueOf(expectedValue));

        CertificateReader mockCertificateReader = mockCertificateReader(
            SchemeReader.LEGACY_SCHEME_DEFAULT_CERT, legacyScheme.certificate());

        PrivateKeyReader mockPrivateKeyReader = mockPrivateKeyReader(
            SchemeReader.LEGACY_SCHEME_DEFAULT_KEY, legacyScheme.privateKey().get());

        SchemeReader schemeReader = new SchemeReader(config, mockPrivateKeyReader, mockCertificateReader);
        Scheme defaultScheme = schemeReader.readDefaultScheme();

        assertThat(defaultScheme)
            .isNotNull()
            .returns(legacyScheme.certificate(), Scheme::certificate)
            .returns(legacyScheme.privateKey(), Scheme::privateKey)
            .returns(SchemeReader.LEGACY_SCHEME_DEFAULT_SIGNATURE_ALGORITHM, Scheme::signatureAlgorithm)
            .returns(SchemeReader.LEGACY_SCHEME_DEFAULT_KEY_ALGORITHM, Scheme::keyAlgorithm)
            .returns(Optional.of(expectedValue), Scheme::keySize);
    }

    @ParameterizedTest
    @ValueSource(strings = { "ec", "ml-ds", "random_key_algo" })
    public void testLegacySchemeDoesntUseLegacyKeySizeWithoutLegacyKeyAlgorithm(String keyAlgorithm)
        throws Exception {
        // This test verifies the behavior that the legacy scheme doesn't fallback to the legacy key size
        // of 4096 when the key algorithm is not set to "RSA" (or whatever we've deemed to be the legacy
        // value).

        DevConfig config = new DevConfig();
        Scheme scheme = CryptoUtil.generateRsaScheme();

        writeSchemeConfig(config, scheme, SchemeReader.LEGACY_SCHEME, null);
        config.setProperty(ConfigProperties.schemeConfig(SchemeReader.LEGACY_SCHEME,
            ConfigProperties.CRYPTO_SCHEME_KEY_ALGORITHM), keyAlgorithm);
        config.clearProperty(ConfigProperties.schemeConfig(SchemeReader.LEGACY_SCHEME,
            ConfigProperties.CRYPTO_SCHEME_KEY_SIZE));

        SchemeReader reader = this.buildSchemeReader(config);
        Scheme output = reader.readDefaultScheme();

        assertThat(output)
            .returns(SchemeReader.LEGACY_SCHEME, Scheme::name)
            .returns(scheme.privateKey(), Scheme::privateKey)
            .returns(scheme.certificate(), Scheme::certificate)
            .returns(keyAlgorithm, Scheme::keyAlgorithm)
            .extracting(Scheme::keySize, as(InstanceOfAssertFactories.OPTIONAL))
            .isEmpty();
    }

    @Test
    public void testLegacySchemeUsesLegacyKeySizeWhenSpecifyingLegacyKeyAlgorithm() throws Exception {
        // This test verifies the behavior that the legacy scheme falls back to the legacy key size of 4096
        // when the key algorithm is set to "RSA" (or whatever we've deemed to be the legacy value).

        DevConfig config = new DevConfig();
        Scheme scheme = CryptoUtil.generateRsaScheme();

        writeSchemeConfig(config, scheme, SchemeReader.LEGACY_SCHEME, null);
        config.setProperty(ConfigProperties.schemeConfig(SchemeReader.LEGACY_SCHEME,
            ConfigProperties.CRYPTO_SCHEME_KEY_ALGORITHM),
            SchemeReader.LEGACY_SCHEME_DEFAULT_KEY_ALGORITHM);
        config.clearProperty(ConfigProperties.schemeConfig(SchemeReader.LEGACY_SCHEME,
            ConfigProperties.CRYPTO_SCHEME_KEY_SIZE));

        SchemeReader reader = this.buildSchemeReader(config);
        Scheme output = reader.readDefaultScheme();

        assertThat(output)
            .returns(SchemeReader.LEGACY_SCHEME, Scheme::name)
            .returns(scheme.privateKey(), Scheme::privateKey)
            .returns(scheme.certificate(), Scheme::certificate)
            .returns(SchemeReader.LEGACY_SCHEME_DEFAULT_KEY_ALGORITHM, Scheme::keyAlgorithm)
            .extracting(Scheme::keySize, as(InstanceOfAssertFactories.OPTIONAL))
            .hasValue(SchemeReader.LEGACY_SCHEME_DEFAULT_KEY_SIZE);
    }

    @Test
    public void testLegacySchemeUsesLegacyKeySizeWhenFallingBackToLegacyKeyAlgorithm() throws Exception {
        // This test verifies the behavior that the legacy scheme falls back to the legacy key size of 4096
        // when the key algorithm is omitted (defaulting to the legacy default).

        DevConfig config = new DevConfig();
        Scheme scheme = CryptoUtil.generateRsaScheme();

        writeSchemeConfig(config, scheme, SchemeReader.LEGACY_SCHEME, null);
        config.clearProperty(ConfigProperties.schemeConfig(SchemeReader.LEGACY_SCHEME,
            ConfigProperties.CRYPTO_SCHEME_KEY_ALGORITHM));
        config.clearProperty(ConfigProperties.schemeConfig(SchemeReader.LEGACY_SCHEME,
            ConfigProperties.CRYPTO_SCHEME_KEY_SIZE));

        SchemeReader reader = this.buildSchemeReader(config);
        Scheme output = reader.readDefaultScheme();

        assertThat(output)
            .returns(SchemeReader.LEGACY_SCHEME, Scheme::name)
            .returns(scheme.privateKey(), Scheme::privateKey)
            .returns(scheme.certificate(), Scheme::certificate)
            .returns(SchemeReader.LEGACY_SCHEME_DEFAULT_KEY_ALGORITHM, Scheme::keyAlgorithm)
            .extracting(Scheme::keySize, as(InstanceOfAssertFactories.OPTIONAL))
            .hasValue(SchemeReader.LEGACY_SCHEME_DEFAULT_KEY_SIZE);
    }

    @Test
    public void testLegacySchemeUsesLegacySchemeKeyPassword() throws Exception {
        DevConfig config = new DevConfig();
        Scheme scheme = CryptoUtil.generateRsaScheme();

        writeSchemeConfig(config, scheme, SchemeReader.LEGACY_SCHEME, "legacy-scheme-password");
        config.setProperty(ConfigProperties.LEGACY_CA_KEY, "some-path");
        config.setProperty(ConfigProperties.LEGACY_CA_KEY_PASSWORD, "legacy-ca-password");

        SchemeReader reader = this.buildSchemeReader(config);
        Scheme output = reader.readDefaultScheme();

        assertThat(output)
            .returns(SchemeReader.LEGACY_SCHEME, Scheme::name)
            .returns(scheme.privateKey(), Scheme::privateKey)
            .returns(scheme.certificate(), Scheme::certificate)
            .returns(SchemeReader.LEGACY_SCHEME_DEFAULT_KEY_ALGORITHM, Scheme::keyAlgorithm)
            .extracting(Scheme::keySize, as(InstanceOfAssertFactories.OPTIONAL))
            .hasValue(SchemeReader.LEGACY_SCHEME_DEFAULT_KEY_SIZE);
    }

    @Test
    public void testLegacySchemeShouldFallBackToLegacyKeyPathAndPassword() throws Exception {
        DevConfig config = new DevConfig();
        Scheme scheme = CryptoUtil.generateRsaScheme();

        writeSchemeConfig(config, scheme, SchemeReader.LEGACY_SCHEME, "password");
        config.clearProperty(ConfigProperties.schemeConfig(SchemeReader.LEGACY_SCHEME,
            ConfigProperties.CRYPTO_SCHEME_KEY));

        PrivateKey expectedPrivateKey = CryptoUtil.generateKeyPair(scheme)
            .getPrivate();
        String actualPassword = TestUtil.randomString();
        File privateKeyFile = CryptoUtil.writePrivateKeyToFile(expectedPrivateKey, actualPassword);

        config.setProperty(ConfigProperties.LEGACY_CA_KEY, privateKeyFile.getCanonicalPath());
        config.setProperty(ConfigProperties.LEGACY_CA_KEY_PASSWORD, actualPassword);

        SchemeReader reader = this.buildSchemeReader(config);
        Scheme output = reader.readDefaultScheme();

        assertThat(output)
            .returns(SchemeReader.LEGACY_SCHEME, Scheme::name)
            .returns(Optional.of(expectedPrivateKey), Scheme::privateKey)
            .returns(scheme.certificate(), Scheme::certificate)
            .returns(SchemeReader.LEGACY_SCHEME_DEFAULT_KEY_ALGORITHM, Scheme::keyAlgorithm)
            .extracting(Scheme::keySize, as(InstanceOfAssertFactories.OPTIONAL))
            .hasValue(SchemeReader.LEGACY_SCHEME_DEFAULT_KEY_SIZE);
    }

}
