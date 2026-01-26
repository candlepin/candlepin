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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.test.CryptoUtil;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.KeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;



/**
 * Base test suite for crypt managers. Subclasses are expected to provide a concrete CryptoManager
 * implementation to test, as well as test for any implementation-specific functionality not covered by the
 * base CryptoManager API/interface.
 */
public abstract class CryptoManagerTest {

    // A mapping of known, supported schemes
    private static final Map<String, Scheme> SUPPORTED_SCHEMES = CryptoUtil.generateSupportedSchemes()
        .collect(Collectors.toMap(Scheme::name, Function.identity()));

    /**
     * Builds a new CryptoManager instance to test, using the given system configuration. Each invocation of
     * this method should return a new instance to avoid unintended object state retention between tests.
     *
     * @param config
     *  the configuration to use for the crypto manager
     *
     * @return
     *  a new CryptoManager instance to test
     */
    protected abstract CryptoManager buildCryptoManager(Configuration config);

    private CryptoManager buildCryptoManager() {
        return this.buildCryptoManager(TestConfig.defaults());
    }

    private static DevConfig addSchemeConfig(DevConfig config, List<Scheme> schemes)
        throws KeyException, IOException {

        for (Scheme scheme : schemes) {
            CryptoUtil.generateSchemeConfiguration(config, scheme, null);
        }

        config.setProperty(ConfigProperties.CRYPTO_SCHEMES, String.join(",", SUPPORTED_SCHEMES.keySet()));

        return config;
    }

    private static DevConfig addUpstreamCertConfig(DevConfig config, Collection<X509Certificate> certificates)
        throws IOException {

        File tempDir = Files.createTempDirectory("cp_upstream_certs").toFile();
        tempDir.deleteOnExit();

        config.setProperty(ConfigProperties.CRYPTO_UPSTREAM_CERT_REPO, tempDir.getCanonicalPath());
        config.setProperty(ConfigProperties.LEGACY_CA_CERT_UPSTREAM, tempDir.getCanonicalPath());

        for (X509Certificate certificate : certificates) {
            File certFile = new File(tempDir, certificate.getSerialNumber().toString() + ".crt");
            certFile.deleteOnExit();

            CryptoUtil.writeCertificateToFile(certificate, certFile);
        }

        return config;
    }

    private static Stream<Arguments> schemeSource() {
        return SUPPORTED_SCHEMES.values()
            .stream()
            .map(Arguments::of);
    }

    @Test
    public void testGetSecurityProvider() {
        CryptoManager cryptoManager = this.buildCryptoManager();
        java.security.Provider securityProvider = cryptoManager.getSecurityProvider();

        assertNotNull(securityProvider);

        // This should not change from call to call. Verify that we get the same instance on repeated
        // invocations.
        for (int i = 0; i < 3; ++i) {
            java.security.Provider output = cryptoManager.getSecurityProvider();
            assertSame(securityProvider, output);
        }
    }

    @Test
    public void testGetCryptoSchemes() throws Exception {
        // We won't know exactly *what* order we'll get here; but we can guarantee that from this point on,
        // the schemes are deduplicated and in a reproducible order.
        List<Scheme> expected = new ArrayList<>(SUPPORTED_SCHEMES.values());
        DevConfig config = addSchemeConfig(TestConfig.defaults(), expected);

        CryptoManager cryptoManager = this.buildCryptoManager(config);
        List<Scheme> schemes = cryptoManager.getCryptoSchemes();

        assertThat(schemes)
            .isNotNull()
            .containsExactlyElementsOf(expected);

        // This should not change from call to call. The exact list instance may change, depending on the
        // underlying intpretation, but the exact schemes and order of those schemes should not change.
        // Verify that we get equal lists on repeated invocations.
        for (int i = 0; i < 3; ++i) {
            List<Scheme> output = cryptoManager.getCryptoSchemes();
            assertEquals(schemes, output);
        }
    }

    @Test
    public void testGetCryptoScheme() throws Exception {
        List<Scheme> supportedSchemes = new ArrayList<>(SUPPORTED_SCHEMES.values());
        DevConfig config = addSchemeConfig(TestConfig.defaults(), supportedSchemes);

        CryptoManager cryptoManager = this.buildCryptoManager(config);

        for (String schemeName : SUPPORTED_SCHEMES.keySet()) {
            Scheme expected = SUPPORTED_SCHEMES.get(schemeName);
            Optional<Scheme> actual = cryptoManager.getCryptoScheme(schemeName);

            assertThat(actual)
                .isNotNull()
                .hasValue(expected);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "   ", "\t", "does_not_exist" })
    public void testGetCryptoSchemeWhenNotPresent(String schemeName) throws Exception {
        List<Scheme> schemes = new ArrayList<>(SUPPORTED_SCHEMES.values());
        DevConfig config = addSchemeConfig(TestConfig.defaults(), schemes);

        CryptoManager cryptoManager = this.buildCryptoManager(config);

        Optional<Scheme> output = cryptoManager.getCryptoScheme(schemeName);
        assertThat(output)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testGetCryptoSchemeDoesNotReturnSchemeNotInSchemesList() throws Exception {
        // This test validates that CryptoManager::getCryptoScheme requires that returned schemes are in the
        // schemes list.

        Scheme rsaScheme = CryptoUtil.generateRsaScheme();
        Scheme mldsaScheme = CryptoUtil.generateMldsaScheme();

        DevConfig config = TestConfig.defaults();
        CryptoUtil.generateSchemeConfiguration(config, rsaScheme, null);
        CryptoUtil.generateSchemeConfiguration(config, mldsaScheme, null);

        // Only define the RSA scheme in the primary scheme list
        config.setProperty(ConfigProperties.CRYPTO_SCHEMES, rsaScheme.name());

        CryptoManager cryptoManager = this.buildCryptoManager(config);

        // Fetching RSA should work
        Optional<Scheme> rsaOutput = cryptoManager.getCryptoScheme(rsaScheme.name());
        assertThat(rsaOutput)
            .isNotNull()
            .hasValue(rsaScheme);

        // Fetching MLDSA should not
        Optional<Scheme> mldsaOutput = cryptoManager.getCryptoScheme(mldsaScheme.name());
        assertThat(mldsaOutput)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testGetCryptoRejectsNullSchemesNames() {
        CryptoManager cryptoManager = this.buildCryptoManager();
        assertThrows(IllegalArgumentException.class, () -> cryptoManager.getCryptoScheme((String) null));
    }

    // TODO: Add tests for getCryptoSchemes(Consumer) once it is implemented
    // - fetches scheme defined in consumer object
    // - returns empty optional if consumer does not define a scheme
    // - returns empty optional if consumer defines scheme not present in scheme list
    // - throws exception on null consumer

    @Test
    public void testGetDefaultCryptoScheme() throws Exception {
        CryptoManager cryptoManager = this.buildCryptoManager();

        Scheme defaultScheme = cryptoManager.getDefaultCryptoScheme();
        assertNotNull(defaultScheme);

        // This should not change from call to call. The exact instance *shouldn't* change, but it wouoldn't
        // break things if it does. We'll just verify that it's equal on each invocation.
        for (int i = 0; i < 3; ++i) {
            Scheme output = cryptoManager.getDefaultCryptoScheme();
            assertEquals(defaultScheme, output);
        }
    }

    @Test
    public void testGetDefaultCryptoSchemeRespectsConfiguration() throws Exception {
        List<Scheme> supportedSchemes = new ArrayList<>(SUPPORTED_SCHEMES.values());
        DevConfig config = addSchemeConfig(TestConfig.defaults(), supportedSchemes);

        for (Scheme expected : supportedSchemes) {
            config.setProperty(ConfigProperties.CRYPTO_DEFAULT_SCHEME, expected.name());
            CryptoManager cryptoManager = this.buildCryptoManager(config);

            Scheme actual = cryptoManager.getDefaultCryptoScheme();
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testGetDefaultCryptoSchemeDoesNotRequirePresenceInSchemeList() throws Exception {
        // This test validates that CryptoManager::getDefaultCryptoScheme does not require that the scheme
        // is present in the configured scheme list
        Scheme rsaScheme = CryptoUtil.generateRsaScheme();
        Scheme mldsaScheme = CryptoUtil.generateMldsaScheme();

        DevConfig config = TestConfig.defaults();
        CryptoUtil.generateSchemeConfiguration(config, rsaScheme, null);
        CryptoUtil.generateSchemeConfiguration(config, mldsaScheme, null);

        // Only define the RSA scheme in the primary scheme list, and set the default scheme to MLDSA
        config.setProperty(ConfigProperties.CRYPTO_SCHEMES, rsaScheme.name());
        config.setProperty(ConfigProperties.CRYPTO_DEFAULT_SCHEME, mldsaScheme.name());

        CryptoManager cryptoManager = this.buildCryptoManager(config);

        Scheme output = cryptoManager.getDefaultCryptoScheme();
        assertEquals(mldsaScheme, output);
    }

    @Test
    public void testGetUpstreamCertificates() throws Exception {
        Set<X509Certificate> upstreamCerts = new HashSet<>();
        for (Scheme scheme : SUPPORTED_SCHEMES.values()) {
            upstreamCerts.add(CryptoUtil.generateX509Certificate(scheme));
            upstreamCerts.add(CryptoUtil.generateX509Certificate(scheme));
        }

        DevConfig config = addUpstreamCertConfig(TestConfig.defaults(), upstreamCerts);

        CryptoManager cryptoManager = this.buildCryptoManager(config);

        Set<X509Certificate> output = cryptoManager.getUpstreamCertificates();
        assertThat(output)
            .isNotNull()
            .containsExactlyInAnyOrderElementsOf(upstreamCerts);
    }

    @Test
    public void testGetUpstreamCertificatesReturnsEmptySetWhenNoUpstreamCertsFound() throws Exception {
        Set<X509Certificate> upstreamCerts = new HashSet<>();
        DevConfig config = addUpstreamCertConfig(TestConfig.defaults(), upstreamCerts);

        CryptoManager cryptoManager = this.buildCryptoManager(config);

        Set<X509Certificate> output = cryptoManager.getUpstreamCertificates();
        assertThat(output)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testGetUpstreamCertificatesFallsBackToLegacyConfig() throws Exception {
        Set<X509Certificate> upstreamCerts = new HashSet<>();
        for (Scheme scheme : SUPPORTED_SCHEMES.values()) {
            upstreamCerts.add(CryptoUtil.generateX509Certificate(scheme));
            upstreamCerts.add(CryptoUtil.generateX509Certificate(scheme));
        }

        DevConfig config = addUpstreamCertConfig(TestConfig.defaults(), upstreamCerts);

        // Clear the "modern" configuration to ensure the legacy config is all that remains
        config.clearProperty(ConfigProperties.CRYPTO_UPSTREAM_CERT_REPO);

        CryptoManager cryptoManager = this.buildCryptoManager(config);

        Set<X509Certificate> output = cryptoManager.getUpstreamCertificates();
        assertThat(output)
            .isNotNull()
            .containsExactlyInAnyOrderElementsOf(upstreamCerts);
    }

    @Test
    public void testGetUpstreamCertificatesReturnsEmptySetWhenDirectoryIsMissing() throws Exception {
        DevConfig config = TestConfig.defaults();
        config.setProperty(ConfigProperties.CRYPTO_UPSTREAM_CERT_REPO, "/some/missing/directory");

        CryptoManager cryptoManager = this.buildCryptoManager(config);

        Set<X509Certificate> output = cryptoManager.getUpstreamCertificates();
        assertThat(output)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testGetUpstreamCertificatesThrowsExceptionWhenDirectoryIsReplacedByFile() throws Exception {
        // Impl note: Remove/replace this test if we ever add PKCS12 repo support

        File tempDir = Files.createTempDirectory("cp_upstream_certs").toFile();

        DevConfig config = TestConfig.defaults();
        config.setProperty(ConfigProperties.CRYPTO_UPSTREAM_CERT_REPO, tempDir.getCanonicalPath());

        CryptoManager cryptoManager = this.buildCryptoManager(config);

        Set<X509Certificate> output = cryptoManager.getUpstreamCertificates();
        assertNotNull(output);

        // Delete the directory and replace it with a file so it'll still exist, but will be busted
        tempDir.delete();
        tempDir.createNewFile();
        tempDir.deleteOnExit();

        assertThrows(CertificateException.class, () -> cryptoManager.getUpstreamCertificates());
    }

    @Nested
    @TestInstance(Lifecycle.PER_CLASS)
    public class TrustedCertificateTests {
        private CryptoManager cryptoManager;
        private List<Scheme> upstreamCertSchemes;

        @BeforeAll
        public void init() throws Exception {
            DevConfig config = TestConfig.defaults();

            // Generate a default scheme that's separate from the primary schemes
            Scheme defaultScheme = CryptoUtil.generateRsaScheme("default_scheme");
            CryptoUtil.generateSchemeConfiguration(config, defaultScheme, null);

            config.setProperty(ConfigProperties.CRYPTO_DEFAULT_SCHEME, defaultScheme.name());

            // Generate some "upstream" certificates from all of our supported crypto schemes
            this.upstreamCertSchemes = CryptoUtil.generateSupportedSchemes()
                .toList();

            List<X509Certificate> certs = this.upstreamCertSchemes
                .stream() // pls java, give us a tee stream >:(
                .map(Scheme::certificate)
                .toList();

            addUpstreamCertConfig(config, certs);

            this.cryptoManager = CryptoManagerTest.this.buildCryptoManager(config);
            this.validateTestState();
        }

        private void validateTestState() throws Exception {
            // Ensure we have separation of all of our cert sources
            List<Scheme> schemes = cryptoManager.getCryptoSchemes();
            Scheme defaultScheme = cryptoManager.getDefaultCryptoScheme();
            Set<X509Certificate> upstreamCertificates = cryptoManager.getUpstreamCertificates();

            // Verify the default scheme is not one of the schemes in the primary scheme list to ensure proper
            // separation and that we aren't accidentally re-validating the same cert from the primary scheme
            // list.
            assertThat(schemes).doesNotContain(defaultScheme);

            // Verify that the upstream certs aren't used by any of the schemes
            Stream.concat(schemes.stream(), Stream.of(defaultScheme))
                .map(Scheme::certificate)
                .forEach(cert -> assertThat(upstreamCertificates).doesNotContain(cert));
        }

        @Test
        public void testIsTrustedCertificateVerifiesCertFromSchemeInSchemeList() throws Exception {
            List<Scheme> schemes = this.cryptoManager.getCryptoSchemes();

            for (Scheme scheme : schemes) {
                X509Certificate testCert = scheme.certificate();

                boolean output = this.cryptoManager.isTrustedCertificate(testCert);
                assertTrue(output);
            }
        }

        @Test
        public void testIsTrustedCertificateVerifiesCertSignedByCertFromSchemeInSchemeList()
            throws Exception {

            List<Scheme> schemes = this.cryptoManager.getCryptoSchemes();

            for (Scheme scheme : schemes) {
                X509Certificate testCert = CryptoUtil.generateSignedX509Certificate(scheme);

                boolean output = this.cryptoManager.isTrustedCertificate(testCert);
                assertTrue(output);
            }
        }

        @Test
        public void testIsTrustedCertificateVerifiesCertFromDefaultScheme() throws Exception {
            Scheme defaultScheme = this.cryptoManager.getDefaultCryptoScheme();
            X509Certificate testCert = defaultScheme.certificate();

            boolean output = this.cryptoManager.isTrustedCertificate(testCert);
            assertTrue(output);
        }

        @Test
        public void testIsTrustedCertificateVerifiesCertSignedByCertFromDefaultScheme() throws Exception {
            Scheme defaultScheme = this.cryptoManager.getDefaultCryptoScheme();
            X509Certificate testCert = CryptoUtil.generateSignedX509Certificate(defaultScheme);

            boolean output = cryptoManager.isTrustedCertificate(testCert);
            assertTrue(output);
        }

        @Test
        public void testIsTrustedCertificateVerifiesCertFromUpstreamCertRepo() throws Exception {
            for (Scheme scheme : this.upstreamCertSchemes) {
                X509Certificate testCert = scheme.certificate();

                boolean output = this.cryptoManager.isTrustedCertificate(testCert);
                assertTrue(output);
            }
        }

        @Test
        public void testIsTrustedCertificateVerifiesCertSignedByCertFromUpstreamCertRepo() throws Exception {
            for (Scheme scheme : this.upstreamCertSchemes) {
                X509Certificate testCert = CryptoUtil.generateSignedX509Certificate(scheme);

                boolean output = this.cryptoManager.isTrustedCertificate(testCert);
                assertTrue(output);
            }
        }

        private static Stream<Arguments> schemeSource() {
            return CryptoManagerTest.SUPPORTED_SCHEMES.values()
                .stream()
                .map(Arguments::of);
        }

        @ParameterizedTest
        @MethodSource("schemeSource")
        public void testIsTrustedCertificateRejectsUnknownSelfSignedCertificate(Scheme scheme)
            throws Exception {

            X509Certificate testCert = CryptoUtil.generateX509Certificate(scheme);

            boolean output = this.cryptoManager.isTrustedCertificate(testCert);
            assertFalse(output);
        }

        @ParameterizedTest
        @MethodSource("schemeSource")
        public void testIsTrustedCertificateRejectsUnknownSignedCertificate(Scheme scheme) throws Exception {
            X509Certificate testCert = CryptoUtil.generateSignedX509Certificate(scheme);

            boolean output = this.cryptoManager.isTrustedCertificate(testCert);
            assertFalse(output);
        }
    }

    @Test
    public void testIsTrustedCertificateThrowsExceptionOnNullInput() throws Exception {
        CryptoManager cryptoManager = this.buildCryptoManager();
        assertThrows(IllegalArgumentException.class, () -> cryptoManager.isTrustedCertificate(null));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGetSigner(Scheme scheme) {
        CryptoManager cryptoManager = this.buildCryptoManager();

        Signer output = cryptoManager.getSigner(scheme);
        assertNotNull(output);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGetSignerRequiresSchemeWithPrivateKey(Scheme scheme) {
        CryptoManager cryptoManager = this.buildCryptoManager();

        Scheme keyless = new Scheme.Builder()
            .setName(scheme.name())
            .setCertificate(scheme.certificate())
            .setSignatureAlgorithm(scheme.signatureAlgorithm())
            .setKeyAlgorithm(scheme.keyAlgorithm())
            .setKeySize(scheme.keySize().orElse(null))
            .build();

        assertThrows(IllegalArgumentException.class, () -> cryptoManager.getSigner(keyless));
    }

    @Test
    public void testGetSignerThrowsExceptionOnNullInput() {
        CryptoManager cryptoManager = this.buildCryptoManager();
        assertThrows(IllegalArgumentException.class, () -> cryptoManager.getSigner(null));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGetSignatureValidator(Scheme scheme) {
        CryptoManager cryptoManager = this.buildCryptoManager();

        SignatureValidator output = cryptoManager.getSignatureValidator(scheme);
        assertNotNull(output);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGetSignatureValidatorAllowsSchemeWithPrivateKey(Scheme scheme) {
        CryptoManager cryptoManager = this.buildCryptoManager();

        Scheme keyless = new Scheme.Builder()
            .setName(scheme.name())
            .setCertificate(scheme.certificate())
            .setSignatureAlgorithm(scheme.signatureAlgorithm())
            .setKeyAlgorithm(scheme.keyAlgorithm())
            .setKeySize(scheme.keySize().orElse(null))
            .build();

        SignatureValidator output = cryptoManager.getSignatureValidator(keyless);
        assertNotNull(output);
    }

    @Test
    public void testGetSignatureValidatorThrowsExceptionOnNullInput() {
        CryptoManager cryptoManager = this.buildCryptoManager();
        assertThrows(IllegalArgumentException.class, () -> cryptoManager.getSignatureValidator(null));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGetCertificateBuilder(Scheme scheme) {
        CryptoManager cryptoManager = this.buildCryptoManager();

        X509CertificateBuilder output = cryptoManager.getCertificateBuilder(scheme);
        assertNotNull(output);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGetCertificateBuilderRequiresSchemeWithPrivateKey(Scheme scheme) {
        CryptoManager cryptoManager = this.buildCryptoManager();

        Scheme keyless = new Scheme.Builder()
            .setName(scheme.name())
            .setCertificate(scheme.certificate())
            .setSignatureAlgorithm(scheme.signatureAlgorithm())
            .setKeyAlgorithm(scheme.keyAlgorithm())
            .setKeySize(scheme.keySize().orElse(null))
            .build();

        assertThrows(IllegalArgumentException.class, () -> cryptoManager.getCertificateBuilder(keyless));
    }

    @Test
    public void testGetCertificateBuilderThrowsExceptionOnNullInput() {
        CryptoManager cryptoManager = this.buildCryptoManager();
        assertThrows(IllegalArgumentException.class, () -> cryptoManager.getCertificateBuilder(null));
    }

    // TODO: Add tests for getKeyPairGenerator(Scheme) once it is implemented
    // - gets an object every time
    // - requires a scheme (non-null input)
    // - does not require a scheme with a private key

}
