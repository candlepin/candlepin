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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.config.ConfigurationException;
import org.candlepin.config.DevConfig;
import org.candlepin.test.CryptoUtil;
import org.candlepin.test.TestUtil;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.security.KeyException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;



public class SchemeReaderTest {

    // A list of known, supported schemes
    private static final List<Scheme> SUPPORTED_SCHEMES = CryptoUtil.generateSupportedSchemes().toList();

    private static Stream<Arguments> schemeSource() {
        return SUPPORTED_SCHEMES.stream()
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

        for (Scheme scheme : SUPPORTED_SCHEMES) {
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

        for (Scheme scheme : SUPPORTED_SCHEMES) {
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

        for (Scheme scheme : SUPPORTED_SCHEMES) {
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

        for (Scheme scheme : SUPPORTED_SCHEMES) {
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

    @Test
    @Disabled
    public void testReadLegacySchemeDefaultsToLegacyValues() throws Exception {
        // TODO: FIXME: CAN'T DO THIS PROPERLY UNTIL CERTIFICATE READER IS OVERHAULED AND MOCKABLE
    }

    @Test
    @Disabled
    public void testReadLegacySchemeDefaultsToLegacyConfigEntries() throws Exception {
        // TODO: FIXME: This cannot be done correctly until the certificate reader has been overhauled to
        // look more like the private key reader (e.g. becomes mockable)
    }

    // TODO: Add validation that the legacy scheme can be overridden on a value-by-value basis, falling back
    // to legacy defaults.

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

    @ParameterizedTest
    @NullAndEmptySource
    public void testReadSchemeWithInvalidConfigurationKeys(String invalidKey) throws Exception {
        DevConfig config = new DevConfig();
        SchemeReader reader = this.buildSchemeReader(config);

        // Invalid schema name
        assertThrows(IllegalArgumentException.class, () -> {
            reader.readScheme(invalidKey, "cert", "pk", "password", "sig-algo", "key-algo", "key-size");
        });

        // Invalid configuration key for the certificate
        assertThrows(IllegalArgumentException.class, () -> {
            reader.readScheme("name", invalidKey, "pk", "password", "sig-algo", "key-algo", "key-size");
        });

        // Invalid configuration key for the private key
        assertThrows(IllegalArgumentException.class, () -> {
            reader.readScheme("name", "cert", invalidKey, "password", "sig-algo", "key-algo", "key-size");
        });

        // Invalid configuration key for the signature algorithm
        assertThrows(IllegalArgumentException.class, () -> {
            reader.readScheme("name", "cert", "pk", "password", invalidKey, "key-algo", "key-size");
        });

        // Invalid configuration key for the key algorithm
        assertThrows(IllegalArgumentException.class, () -> {
            reader.readScheme("name", "cert", "pk", "password", "sig-algo", invalidKey, "key-size");
        });
    }

    @Test
    public void testReadSchemeUsingConfigurationKeys() throws Exception {
        DevConfig config = new DevConfig();
        Scheme scheme = CryptoUtil.generateRsaScheme();
        String expectedName = TestUtil.randomString("name-");
        String expectedPassword = TestUtil.randomString("password-");

        File certFile = writeCertToFile(scheme.certificate());
        File keyFile = writeKeyToFile(scheme.privateKey().get(), expectedPassword);

        config.setProperty(ConfigProperties.JWT_CRYPTO_SCHEME_CERT, certFile.getAbsolutePath());
        config.setProperty(ConfigProperties.JWT_CRYPTO_SCHEME_KEY, keyFile.getAbsolutePath());
        config.setProperty(ConfigProperties.JWT_CRYPTO_SCHEME_KEY_PASSWORD, expectedPassword);
        config.setProperty(ConfigProperties.JWT_CRYPTO_SCHEME_SIGNATURE_ALGORITHM,
            scheme.signatureAlgorithm());
        config.setProperty(ConfigProperties.JWT_CRYPTO_SCHEME_KEY_ALGORITHM, scheme.keyAlgorithm());
        config.setProperty(ConfigProperties.JWT_CRYPTO_SCHEME_KEY_SIZE,
            String.valueOf(scheme.keySize().get()));

        SchemeReader reader = this.buildSchemeReader(config);

        Scheme actual = reader.readScheme(expectedName,
            ConfigProperties.JWT_CRYPTO_SCHEME_CERT,
            ConfigProperties.JWT_CRYPTO_SCHEME_KEY,
            ConfigProperties.JWT_CRYPTO_SCHEME_KEY_PASSWORD,
            ConfigProperties.JWT_CRYPTO_SCHEME_SIGNATURE_ALGORITHM,
            ConfigProperties.JWT_CRYPTO_SCHEME_KEY_ALGORITHM,
            ConfigProperties.JWT_CRYPTO_SCHEME_KEY_SIZE);

        assertThat(actual)
            .isNotNull()
            .returns(expectedName, Scheme::name)
            .returns(scheme.certificate(), Scheme::certificate)
            .returns(scheme.privateKey(), Scheme::privateKey)
            .returns(scheme.signatureAlgorithm(), Scheme::signatureAlgorithm)
            .returns(scheme.keyAlgorithm(), Scheme::keyAlgorithm)
            .returns(scheme.keySize(), Scheme::keySize);
    }

    @Test
    public void testReadSchemeUsingConfigurationKeysWithNullPasswordKey() throws Exception {
        DevConfig config = new DevConfig();
        Scheme scheme = CryptoUtil.generateRsaScheme();
        String expectedName = TestUtil.randomString("name-");

        File certFile = writeCertToFile(scheme.certificate());
        File keyFile = writeKeyToFile(scheme.privateKey().get(), null);

        config.setProperty(ConfigProperties.JWT_CRYPTO_SCHEME_CERT, certFile.getAbsolutePath());
        config.setProperty(ConfigProperties.JWT_CRYPTO_SCHEME_KEY, keyFile.getAbsolutePath());
        config.clearProperty(ConfigProperties.JWT_CRYPTO_SCHEME_KEY_PASSWORD);
        config.setProperty(ConfigProperties.JWT_CRYPTO_SCHEME_SIGNATURE_ALGORITHM,
            scheme.signatureAlgorithm());
        config.setProperty(ConfigProperties.JWT_CRYPTO_SCHEME_KEY_ALGORITHM, scheme.keyAlgorithm());
        config.setProperty(ConfigProperties.JWT_CRYPTO_SCHEME_KEY_SIZE,
            String.valueOf(scheme.keySize().get()));

        SchemeReader reader = this.buildSchemeReader(config);

        Scheme actual = reader.readScheme(expectedName,
            ConfigProperties.JWT_CRYPTO_SCHEME_CERT,
            ConfigProperties.JWT_CRYPTO_SCHEME_KEY,
            null,
            ConfigProperties.JWT_CRYPTO_SCHEME_SIGNATURE_ALGORITHM,
            ConfigProperties.JWT_CRYPTO_SCHEME_KEY_ALGORITHM,
            ConfigProperties.JWT_CRYPTO_SCHEME_KEY_SIZE);

        assertThat(actual)
            .isNotNull()
            .returns(expectedName, Scheme::name)
            .returns(scheme.certificate(), Scheme::certificate)
            .returns(scheme.privateKey(), Scheme::privateKey)
            .returns(scheme.signatureAlgorithm(), Scheme::signatureAlgorithm)
            .returns(scheme.keyAlgorithm(), Scheme::keyAlgorithm)
            .returns(scheme.keySize(), Scheme::keySize);
    }

    @Test
    public void testReadSchemeUsingConfigurationKeysWithNullKeySizeKey() throws Exception {
        DevConfig config = new DevConfig();
        Scheme scheme = CryptoUtil.generateRsaScheme();
        String expectedName = TestUtil.randomString("name-");
        String expectedPassword = TestUtil.randomString("password-");

        File certFile = writeCertToFile(scheme.certificate());
        File keyFile = writeKeyToFile(scheme.privateKey().get(), expectedPassword);

        config.setProperty(ConfigProperties.JWT_CRYPTO_SCHEME_CERT, certFile.getAbsolutePath());
        config.setProperty(ConfigProperties.JWT_CRYPTO_SCHEME_KEY, keyFile.getAbsolutePath());
        config.setProperty(ConfigProperties.JWT_CRYPTO_SCHEME_KEY_PASSWORD, expectedPassword);
        config.setProperty(ConfigProperties.JWT_CRYPTO_SCHEME_SIGNATURE_ALGORITHM,
            scheme.signatureAlgorithm());
        config.setProperty(ConfigProperties.JWT_CRYPTO_SCHEME_KEY_ALGORITHM, scheme.keyAlgorithm());
        config.clearProperty(ConfigProperties.JWT_CRYPTO_SCHEME_KEY_SIZE);

        SchemeReader reader = this.buildSchemeReader(config);

        Scheme actual = reader.readScheme(expectedName,
            ConfigProperties.JWT_CRYPTO_SCHEME_CERT,
            ConfigProperties.JWT_CRYPTO_SCHEME_KEY,
            ConfigProperties.JWT_CRYPTO_SCHEME_KEY_PASSWORD,
            ConfigProperties.JWT_CRYPTO_SCHEME_SIGNATURE_ALGORITHM,
            ConfigProperties.JWT_CRYPTO_SCHEME_KEY_ALGORITHM,
            null);

        assertThat(actual)
            .isNotNull()
            .returns(expectedName, Scheme::name)
            .returns(scheme.certificate(), Scheme::certificate)
            .returns(scheme.privateKey(), Scheme::privateKey)
            .returns(scheme.signatureAlgorithm(), Scheme::signatureAlgorithm)
            .returns(scheme.keyAlgorithm(), Scheme::keyAlgorithm)
            .extracting(Scheme::keySize, as(InstanceOfAssertFactories.OPTIONAL))
            .isEmpty();
    }

    public static Stream<Arguments> requiredConfigurationKeysSource() {
        return Stream.of(
            Arguments.of(ConfigProperties.JWT_CRYPTO_SCHEME_CERT),
            Arguments.of(ConfigProperties.JWT_CRYPTO_SCHEME_KEY),
            Arguments.of(ConfigProperties.JWT_CRYPTO_SCHEME_SIGNATURE_ALGORITHM),
            Arguments.of(ConfigProperties.JWT_CRYPTO_SCHEME_KEY_ALGORITHM)
        );
    }

    @ParameterizedTest
    @MethodSource("requiredConfigurationKeysSource")
    public void testReadSchemeUsingConfigurationKeysWithMissingRequiredConfig(String key) throws Exception {
        DevConfig config = new DevConfig();
        Scheme scheme = CryptoUtil.generateRsaScheme();
        String expectedName = TestUtil.randomString("name-");
        String expectedPassword = TestUtil.randomString("password-");

        File certFile = writeCertToFile(scheme.certificate());
        File keyFile = writeKeyToFile(scheme.privateKey().get(), expectedPassword);

        config.setProperty(ConfigProperties.JWT_CRYPTO_SCHEME_CERT, certFile.getAbsolutePath());
        config.setProperty(ConfigProperties.JWT_CRYPTO_SCHEME_KEY, keyFile.getAbsolutePath());
        config.setProperty(ConfigProperties.JWT_CRYPTO_SCHEME_KEY_PASSWORD, expectedPassword);
        config.setProperty(ConfigProperties.JWT_CRYPTO_SCHEME_SIGNATURE_ALGORITHM,
            scheme.signatureAlgorithm());
        config.setProperty(ConfigProperties.JWT_CRYPTO_SCHEME_KEY_ALGORITHM, scheme.keyAlgorithm());
        config.setProperty(ConfigProperties.JWT_CRYPTO_SCHEME_KEY_SIZE,
            String.valueOf(scheme.keySize().get()));

        // Clear the required configuration property
        config.clearProperty(key);

        SchemeReader reader = this.buildSchemeReader(config);

        assertThrows(ConfigurationException.class, () -> {
            reader.readScheme(expectedName,
                ConfigProperties.JWT_CRYPTO_SCHEME_CERT,
                ConfigProperties.JWT_CRYPTO_SCHEME_KEY,
                ConfigProperties.JWT_CRYPTO_SCHEME_KEY_PASSWORD,
                ConfigProperties.JWT_CRYPTO_SCHEME_SIGNATURE_ALGORITHM,
                ConfigProperties.JWT_CRYPTO_SCHEME_KEY_ALGORITHM,
                ConfigProperties.JWT_CRYPTO_SCHEME_KEY_SIZE);
        });
    }

}
