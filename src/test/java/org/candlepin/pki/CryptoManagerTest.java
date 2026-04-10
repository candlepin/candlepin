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
import org.candlepin.config.ConfigurationException;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.model.AnonymousCloudConsumer;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
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
import java.security.KeyPair;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;



/**
 * Base test suite for crypt managers. Subclasses are expected to provide a concrete CryptoManager
 * implementation to test, as well as test for any implementation-specific functionality not covered by the
 * base CryptoManager API/interface.
 */
public abstract class CryptoManagerTest {

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

    private static Stream<Arguments> schemeSource() {
        return CryptoUtil.SUPPORTED_SCHEMES.values()
            .stream()
            .map(Arguments::of);
    }

    private CryptoManager buildCryptoManager() {
        return this.buildCryptoManager(TestConfig.defaults());
    }

    private static DevConfig addSchemeConfig(DevConfig config, List<Scheme> schemes)
        throws KeyException, IOException {

        for (Scheme scheme : schemes) {
            CryptoUtil.generateSchemeConfiguration(config, scheme, null);
        }

        List<String> schemeNames = schemes.stream()
            .map(Scheme::name)
            .toList();

        config.setProperty(ConfigProperties.CRYPTO_SCHEMES,
            String.join(",", schemeNames));

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

    private static String reverseOid(String oid) {
        String[] chunks = oid.split("\\.");

        for (int i = 0; i < chunks.length / 2; ++i) {
            String tmp = chunks[i];
            chunks[i] = chunks[chunks.length - i - 1];
            chunks[chunks.length - i - 1] = tmp;
        }

        return String.join(".", chunks);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testCryptoManagerThrowsExceptionOnMalformedKeyAlgorithm(Scheme scheme) throws Exception {
        // This test validates that the crypto manager immediately fails when a scheme is fully configured,
        // but has an invalid key algorithm/size

        String signatureAlgorithm = scheme.signatureAlgorithm();
        String keyAlgorithm = scheme.keyAlgorithm();
        Integer keySize = scheme.keySize().orElse(null);

        KeyPair keypair = keySize != null ?
            CryptoUtil.generateKeyPair(keyAlgorithm, keySize) :
            CryptoUtil.generateKeyPair(keyAlgorithm, null);

        X509Certificate certificate = CryptoUtil.generateX509Certificate(keypair, signatureAlgorithm);

        Scheme malformed = new Scheme.Builder()
            .setName(scheme.name() + "_malformed")
            .setPrivateKey(keypair.getPrivate())
            .setCertificate(certificate)
            .setSignatureAlgorithm(signatureAlgorithm)
            .setKeyAlgorithm("malformed_key_algo")
            .setKeySize(8675309)
            .build();

        DevConfig config = addSchemeConfig(TestConfig.defaults(), List.of(scheme, malformed));

        assertThrows(ConfigurationException.class, () -> this.buildCryptoManager(config));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testCryptoManagerThrowsExceptionOnMalformedSignatureAlgorithm(Scheme scheme)
        throws Exception {

        // This test validates that the crypto manager immediately fails when a scheme is fully configured,
        // but has an invalid signature algorithms.

        String signatureAlgorithm = scheme.signatureAlgorithm();
        String keyAlgorithm = scheme.keyAlgorithm();
        Integer keySize = scheme.keySize().orElse(null);

        KeyPair keypair = keySize != null ?
            CryptoUtil.generateKeyPair(keyAlgorithm, keySize) :
            CryptoUtil.generateKeyPair(keyAlgorithm, null);

        X509Certificate certificate = CryptoUtil.generateX509Certificate(keypair, signatureAlgorithm);

        Scheme malformed = new Scheme.Builder()
            .setName(scheme.name() + "_malformed")
            .setPrivateKey(keypair.getPrivate())
            .setCertificate(certificate)
            .setSignatureAlgorithm("malformed_sig_algo")
            .setKeyAlgorithm(keyAlgorithm)
            .setKeySize(keySize)
            .build();

        DevConfig config = addSchemeConfig(TestConfig.defaults(), List.of(scheme, malformed));

        assertThrows(ConfigurationException.class, () -> this.buildCryptoManager(config));
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
        List<Scheme> expected = new ArrayList<>(CryptoUtil.SUPPORTED_SCHEMES.values());
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
        List<Scheme> supportedSchemes = new ArrayList<>(CryptoUtil.SUPPORTED_SCHEMES.values());
        DevConfig config = addSchemeConfig(TestConfig.defaults(), supportedSchemes);

        CryptoManager cryptoManager = this.buildCryptoManager(config);

        for (String schemeName : CryptoUtil.SUPPORTED_SCHEMES.keySet()) {
            Scheme expected = CryptoUtil.SUPPORTED_SCHEMES.get(schemeName);
            Optional<Scheme> actual = cryptoManager.getCryptoScheme(schemeName);

            assertThat(actual)
                .isNotNull()
                .hasValue(expected);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "   ", "\t", "does_not_exist" })
    public void testGetCryptoSchemeWhenNotPresent(String schemeName) throws Exception {
        List<Scheme> schemes = new ArrayList<>(CryptoUtil.SUPPORTED_SCHEMES.values());
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

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGetConsumerCryptoScheme(Scheme scheme) throws Exception {
        OidUtil oidUtil = CryptoUtil.getOidUtil();

        String keyAlgoOid = oidUtil.getKeyAlgorithmOid(scheme.keyAlgorithm())
            .orElseThrow(() -> new RuntimeException("scheme key algorithm does not map to an OID"));

        String sigAlgoOid = oidUtil.getSignatureAlgorithmOid(scheme.signatureAlgorithm())
            .orElseThrow(() -> new RuntimeException("scheme signature algorithm does not map to an OID"));

        Consumer consumer = new Consumer()
            .setSupportedKeyAlgorithmOids(Set.of(keyAlgoOid))
            .setSupportedSignatureAlgorithmOids(Set.of(sigAlgoOid));

        // Build a configuration that definitely contains and lists the scheme under test
        DevConfig config = TestConfig.defaults();
        CryptoUtil.generateSchemeConfiguration(config, scheme, null);
        config.setProperty(ConfigProperties.CRYPTO_SCHEMES,
            String.join(",", CryptoUtil.SUPPORTED_SCHEMES.keySet()));

        CryptoManager cryptoManager = this.buildCryptoManager(config);

        assertThat(cryptoManager.getCryptoScheme(consumer))
            .isEqualTo(scheme);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGetConsumerCryptoSchemeRespectsSchemeConfigPriorityOrder(Scheme target) throws Exception {
        DevConfig config = TestConfig.defaults();
        OidUtil oidUtil = CryptoUtil.getOidUtil();

        Set<String> keyAlgoOids = new HashSet<>();
        Set<String> sigAlgoOids = new HashSet<>();

        LinkedHashSet<Scheme> orderedSchemes = new LinkedHashSet<>();
        orderedSchemes.add(target);
        orderedSchemes.addAll(CryptoUtil.SUPPORTED_SCHEMES.values());

        for (Scheme scheme : orderedSchemes) {
            CryptoUtil.generateSchemeConfiguration(config, scheme, null);

            String keyAlgoOid = oidUtil.getKeyAlgorithmOid(scheme.keyAlgorithm())
                .orElseThrow(() -> new RuntimeException("scheme key algorithm does not map to an OID"));

            String sigAlgoOid = oidUtil.getSignatureAlgorithmOid(scheme.signatureAlgorithm())
                .orElseThrow(() -> new RuntimeException("scheme signature algorithm does not map to an OID"));

            keyAlgoOids.add(keyAlgoOid);
            sigAlgoOids.add(sigAlgoOid);
        }

        Consumer consumer = new Consumer()
            .setSupportedKeyAlgorithmOids(keyAlgoOids)
            .setSupportedSignatureAlgorithmOids(sigAlgoOids);

        // Set the configuration such that it definitely contains and lists the scheme under test
        List<String> schemesList = orderedSchemes.stream()
            .map(Scheme::name)
            .toList();

        config.setProperty(ConfigProperties.CRYPTO_SCHEMES, String.join(",", schemesList));

        CryptoManager cryptoManager = this.buildCryptoManager(config);

        assertThat(cryptoManager.getCryptoScheme(consumer))
            .isEqualTo(target);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGetConsumerCryptoSchemeAllowsIndicatingOnlyKeyCapabilities(Scheme scheme)
        throws Exception {

        OidUtil oidUtil = CryptoUtil.getOidUtil();

        String keyAlgoOid = oidUtil.getKeyAlgorithmOid(scheme.keyAlgorithm())
            .orElseThrow(() -> new RuntimeException("scheme key algorithm does not map to an OID"));

        // Setup the consumer such that it only indicates which key algorithms it supports, allowing us to
        // select the "best" signature algorithm of our choosing so long as the scheme uses a supported key
        // algorithm
        Consumer consumer = new Consumer()
            .setSupportedKeyAlgorithmOids(Set.of(keyAlgoOid))
            .setSupportedSignatureAlgorithmOids(null);

        // Build a configuration that definitely contains and lists the scheme under test
        DevConfig config = TestConfig.defaults();
        CryptoUtil.generateSchemeConfiguration(config, scheme, null);
        config.setProperty(ConfigProperties.CRYPTO_SCHEMES,
            String.join(",", CryptoUtil.SUPPORTED_SCHEMES.keySet()));

        CryptoManager cryptoManager = this.buildCryptoManager(config);

        assertThat(cryptoManager.getCryptoScheme(consumer))
            .isEqualTo(scheme);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGetConsumerCryptoSchemeAllowsIndicatingOnlySignatureCapabilities(Scheme scheme)
        throws Exception {

        OidUtil oidUtil = CryptoUtil.getOidUtil();

        String sigAlgoOid = oidUtil.getSignatureAlgorithmOid(scheme.signatureAlgorithm())
            .orElseThrow(() -> new RuntimeException("scheme signature algorithm does not map to an OID"));

        // Setup the consumer such that it only indicates which signature algorithms it supports, allowing us
        // to select the "best" key algorithm of our choosing so long as the scheme uses a supported signature
        // algorithm
        Consumer consumer = new Consumer()
            .setSupportedKeyAlgorithmOids(null)
            .setSupportedSignatureAlgorithmOids(Set.of(sigAlgoOid));

        // Build a configuration that definitely contains and lists the scheme under test
        DevConfig config = TestConfig.defaults();
        CryptoUtil.generateSchemeConfiguration(config, scheme, null);
        config.setProperty(ConfigProperties.CRYPTO_SCHEMES,
            String.join(",", CryptoUtil.SUPPORTED_SCHEMES.keySet()));

        CryptoManager cryptoManager = this.buildCryptoManager(config);

        assertThat(cryptoManager.getCryptoScheme(consumer))
            .isEqualTo(scheme);
    }

    @Test
    public void testGetConsumerCryptoSchemeReturnsDefaultSchemeWhenNoCapabilitiesAreDefined()
        throws Exception {

        Consumer consumer = new Consumer()
            .setSupportedKeyAlgorithmOids(null)
            .setSupportedSignatureAlgorithmOids(null);

        CryptoManager cryptoManager = this.buildCryptoManager();

        assertThat(cryptoManager.getCryptoScheme(consumer))
            .isEqualTo(cryptoManager.getDefaultCryptoScheme());
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGetConsumerCryptoSchemeThrowsExceptionWhenKeyAlgorithmIsNotSupported(Scheme scheme)
        throws Exception {

        OidUtil oidUtil = CryptoUtil.getOidUtil();

        String keyAlgoOid = oidUtil.getKeyAlgorithmOid(scheme.keyAlgorithm())
            .orElseThrow(() -> new RuntimeException("scheme key algorithm does not map to an OID"));

        String sigAlgoOid = oidUtil.getSignatureAlgorithmOid(scheme.signatureAlgorithm())
            .orElseThrow(() -> new RuntimeException("scheme signature algorithm does not map to an OID"));

        // Reverse the key algorithm OID to guarantee it doesn't match our scheme's key algo OID
        Consumer consumer = new Consumer()
            .setSupportedKeyAlgorithmOids(Set.of(reverseOid(keyAlgoOid)))
            .setSupportedSignatureAlgorithmOids(Set.of(sigAlgoOid));

        // Build a configuration that definitely contains and lists the scheme under test
        DevConfig config = TestConfig.defaults();
        CryptoUtil.generateSchemeConfiguration(config, scheme, null);
        config.setProperty(ConfigProperties.CRYPTO_SCHEMES,
            String.join(",", CryptoUtil.SUPPORTED_SCHEMES.keySet()));

        CryptoManager cryptoManager = this.buildCryptoManager(config);

        assertThrows(CryptoCapabilitiesException.class, () -> cryptoManager.getCryptoScheme(consumer));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGetConsumerCryptoSchemeThrowsExceptionWhenSignatureAlgorithmIsNotSupported(Scheme scheme)
        throws Exception {

        OidUtil oidUtil = CryptoUtil.getOidUtil();

        String keyAlgoOid = oidUtil.getKeyAlgorithmOid(scheme.keyAlgorithm())
            .orElseThrow(() -> new RuntimeException("scheme key algorithm does not map to an OID"));

        String sigAlgoOid = oidUtil.getSignatureAlgorithmOid(scheme.signatureAlgorithm())
            .orElseThrow(() -> new RuntimeException("scheme signature algorithm does not map to an OID"));

        // Reverse the key algorithm OID to guarantee it doesn't match our scheme's key algo OID
        Consumer consumer = new Consumer()
            .setSupportedKeyAlgorithmOids(Set.of(keyAlgoOid))
            .setSupportedSignatureAlgorithmOids(Set.of(reverseOid(sigAlgoOid)));

        // Build a configuration that definitely contains and lists the scheme under test
        DevConfig config = TestConfig.defaults();
        CryptoUtil.generateSchemeConfiguration(config, scheme, null);
        config.setProperty(ConfigProperties.CRYPTO_SCHEMES,
            String.join(",", CryptoUtil.SUPPORTED_SCHEMES.keySet()));

        CryptoManager cryptoManager = this.buildCryptoManager(config);

        assertThrows(CryptoCapabilitiesException.class, () -> cryptoManager.getCryptoScheme(consumer));
    }

    @Test
    public void testGetConsumerCryptoSchemeThrowsExceptionOnNullConsumer() throws Exception {
        CryptoManager cryptoManager = this.buildCryptoManager();

        assertThrows(IllegalArgumentException.class, () -> cryptoManager.getCryptoScheme((Consumer) null));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testIsConsumerUsingDefaultSchemeReturnsFalseWhenCapabilitiesProvided(Scheme scheme)
        throws Exception {

        OidUtil oidUtil = CryptoUtil.getOidUtil();

        String keyAlgoOid = oidUtil.getKeyAlgorithmOid(scheme.keyAlgorithm())
            .orElseThrow(() -> new RuntimeException("scheme key algorithm does not map to an OID"));

        String sigAlgoOid = oidUtil.getSignatureAlgorithmOid(scheme.signatureAlgorithm())
            .orElseThrow(() -> new RuntimeException("scheme signature algorithm does not map to an OID"));

        Consumer consumer = new Consumer()
            .setSupportedKeyAlgorithmOids(Set.of(keyAlgoOid))
            .setSupportedSignatureAlgorithmOids(Set.of(sigAlgoOid));

        // Build a configuration that definitely contains and lists the scheme under test
        DevConfig config = TestConfig.defaults();
        CryptoUtil.generateSchemeConfiguration(config, scheme, null);
        config.setProperty(ConfigProperties.CRYPTO_SCHEMES,
            String.join(",", CryptoUtil.SUPPORTED_SCHEMES.keySet()));

        CryptoManager cryptoManager = this.buildCryptoManager(config);

        assertThat(cryptoManager.isUsingDefaultCryptoScheme(consumer))
            .isEqualTo(false);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testIsConsumerUsingDefaultSchemeReturnsFalseWhenKeyAlgorithmIsNotSupported(Scheme scheme)
        throws Exception {

        // This test verifies that the isUsingDefaultCryptoScheme check returns false even in the case that
        // the algorithms they provide are not supported.
        OidUtil oidUtil = CryptoUtil.getOidUtil();

        String keyAlgoOid = oidUtil.getKeyAlgorithmOid(scheme.keyAlgorithm())
            .orElseThrow(() -> new RuntimeException("scheme key algorithm does not map to an OID"));

        String sigAlgoOid = oidUtil.getSignatureAlgorithmOid(scheme.signatureAlgorithm())
            .orElseThrow(() -> new RuntimeException("scheme signature algorithm does not map to an OID"));

        // Reverse the key algorithm OID to guarantee it doesn't match our scheme's key algo OID
        Consumer consumer = new Consumer()
            .setSupportedKeyAlgorithmOids(Set.of(reverseOid(keyAlgoOid)))
            .setSupportedSignatureAlgorithmOids(Set.of(sigAlgoOid));

        // Build a configuration that definitely contains and lists the scheme under test
        DevConfig config = TestConfig.defaults();
        CryptoUtil.generateSchemeConfiguration(config, scheme, null);
        config.setProperty(ConfigProperties.CRYPTO_SCHEMES,
            String.join(",", CryptoUtil.SUPPORTED_SCHEMES.keySet()));

        CryptoManager cryptoManager = this.buildCryptoManager(config);

        assertThat(cryptoManager.isUsingDefaultCryptoScheme(consumer))
            .isEqualTo(false);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testIsConsumerUsingDefaultSchemeReturnsFalseWhenSignatureAlgorithmIsNotSupported(
        Scheme scheme) throws Exception {

        // This test verifies that the isUsingDefaultCryptoScheme check returns false even in the case that
        // the algorithms they provide are not supported.
        OidUtil oidUtil = CryptoUtil.getOidUtil();

        String keyAlgoOid = oidUtil.getKeyAlgorithmOid(scheme.keyAlgorithm())
            .orElseThrow(() -> new RuntimeException("scheme key algorithm does not map to an OID"));

        String sigAlgoOid = oidUtil.getSignatureAlgorithmOid(scheme.signatureAlgorithm())
            .orElseThrow(() -> new RuntimeException("scheme signature algorithm does not map to an OID"));

        // Reverse the key algorithm OID to guarantee it doesn't match our scheme's key algo OID
        Consumer consumer = new Consumer()
            .setSupportedKeyAlgorithmOids(Set.of(keyAlgoOid))
            .setSupportedSignatureAlgorithmOids(Set.of(reverseOid(sigAlgoOid)));

        // Build a configuration that definitely contains and lists the scheme under test
        DevConfig config = TestConfig.defaults();
        CryptoUtil.generateSchemeConfiguration(config, scheme, null);
        config.setProperty(ConfigProperties.CRYPTO_SCHEMES,
            String.join(",", CryptoUtil.SUPPORTED_SCHEMES.keySet()));

        CryptoManager cryptoManager = this.buildCryptoManager(config);

        assertThat(cryptoManager.isUsingDefaultCryptoScheme(consumer))
            .isEqualTo(false);
    }

    @Test
    public void testIsConsumerUsingDefaultSchemeReturnsTrueWhenNoCapabilitiesAreDefined()
        throws Exception {

        Consumer consumer = new Consumer()
            .setSupportedKeyAlgorithmOids(null)
            .setSupportedSignatureAlgorithmOids(null);

        CryptoManager cryptoManager = this.buildCryptoManager();

        assertThat(cryptoManager.isUsingDefaultCryptoScheme(consumer))
            .isEqualTo(true);
    }

    @Test
    public void testIsConsumerUsingDefaultSchemeThrowsExceptionOnNullConsumer() throws Exception {
        CryptoManager cryptoManager = this.buildCryptoManager();

        assertThrows(IllegalArgumentException.class,
            () -> cryptoManager.isUsingDefaultCryptoScheme((Consumer) null));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGetAnonCloudConsumerCryptoScheme(Scheme scheme) throws Exception {
        OidUtil oidUtil = CryptoUtil.getOidUtil();

        String keyAlgoOid = oidUtil.getKeyAlgorithmOid(scheme.keyAlgorithm())
            .orElseThrow(() -> new RuntimeException("scheme key algorithm does not map to an OID"));

        String sigAlgoOid = oidUtil.getSignatureAlgorithmOid(scheme.signatureAlgorithm())
            .orElseThrow(() -> new RuntimeException("scheme signature algorithm does not map to an OID"));

        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setSupportedKeyAlgorithmOids(Set.of(keyAlgoOid))
            .setSupportedSignatureAlgorithmOids(Set.of(sigAlgoOid));

        // Build a configuration that definitely contains and lists the scheme under test
        DevConfig config = TestConfig.defaults();
        CryptoUtil.generateSchemeConfiguration(config, scheme, null);
        config.setProperty(ConfigProperties.CRYPTO_SCHEMES,
            String.join(",", CryptoUtil.SUPPORTED_SCHEMES.keySet()));

        CryptoManager cryptoManager = this.buildCryptoManager(config);

        assertThat(cryptoManager.getCryptoScheme(consumer))
            .isEqualTo(scheme);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGetAnonCloudConsumerCryptoSchemeRespectsSchemeConfigPriorityOrder(Scheme target)
        throws Exception {

        DevConfig config = TestConfig.defaults();
        OidUtil oidUtil = CryptoUtil.getOidUtil();

        Set<String> keyAlgoOids = new HashSet<>();
        Set<String> sigAlgoOids = new HashSet<>();

        LinkedHashSet<Scheme> orderedSchemes = new LinkedHashSet<>();
        orderedSchemes.add(target);
        orderedSchemes.addAll(CryptoUtil.SUPPORTED_SCHEMES.values());

        for (Scheme scheme : orderedSchemes) {
            CryptoUtil.generateSchemeConfiguration(config, scheme, null);

            String keyAlgoOid = oidUtil.getKeyAlgorithmOid(scheme.keyAlgorithm())
                .orElseThrow(() -> new RuntimeException("scheme key algorithm does not map to an OID"));

            String sigAlgoOid = oidUtil.getSignatureAlgorithmOid(scheme.signatureAlgorithm())
                .orElseThrow(() -> new RuntimeException("scheme signature algorithm does not map to an OID"));

            keyAlgoOids.add(keyAlgoOid);
            sigAlgoOids.add(sigAlgoOid);
        }

        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setSupportedKeyAlgorithmOids(keyAlgoOids)
            .setSupportedSignatureAlgorithmOids(sigAlgoOids);

        // Set the configuration such that it definitely contains and lists the scheme under test
        List<String> schemesList = orderedSchemes.stream()
            .map(Scheme::name)
            .toList();

        config.setProperty(ConfigProperties.CRYPTO_SCHEMES, String.join(",", schemesList));

        CryptoManager cryptoManager = this.buildCryptoManager(config);

        assertThat(cryptoManager.getCryptoScheme(consumer))
            .isEqualTo(target);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGetAnonCloudConsumerCryptoSchemeAllowsIndicatingOnlyKeyCapabilities(Scheme scheme)
        throws Exception {

        OidUtil oidUtil = CryptoUtil.getOidUtil();

        String keyAlgoOid = oidUtil.getKeyAlgorithmOid(scheme.keyAlgorithm())
            .orElseThrow(() -> new RuntimeException("scheme key algorithm does not map to an OID"));

        // Setup the consumer such that it only indicates which key algorithms it supports, allowing us to
        // select the "best" signature algorithm of our choosing so long as the scheme uses a supported key
        // algorithm
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setSupportedKeyAlgorithmOids(Set.of(keyAlgoOid))
            .setSupportedSignatureAlgorithmOids(null);

        // Build a configuration that definitely contains and lists the scheme under test
        DevConfig config = TestConfig.defaults();
        CryptoUtil.generateSchemeConfiguration(config, scheme, null);
        config.setProperty(ConfigProperties.CRYPTO_SCHEMES,
            String.join(",", CryptoUtil.SUPPORTED_SCHEMES.keySet()));

        CryptoManager cryptoManager = this.buildCryptoManager(config);

        assertThat(cryptoManager.getCryptoScheme(consumer))
            .isEqualTo(scheme);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGetAnonCloudConsumerCryptoSchemeAllowsIndicatingOnlySignatureCapabilities(Scheme scheme)
        throws Exception {

        OidUtil oidUtil = CryptoUtil.getOidUtil();

        String sigAlgoOid = oidUtil.getSignatureAlgorithmOid(scheme.signatureAlgorithm())
            .orElseThrow(() -> new RuntimeException("scheme signature algorithm does not map to an OID"));

        // Setup the consumer such that it only indicates which signature algorithms it supports, allowing us
        // to select the "best" key algorithm of our choosing so long as the scheme uses a supported signature
        // algorithm
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setSupportedKeyAlgorithmOids(null)
            .setSupportedSignatureAlgorithmOids(Set.of(sigAlgoOid));

        // Build a configuration that definitely contains and lists the scheme under test
        DevConfig config = TestConfig.defaults();
        CryptoUtil.generateSchemeConfiguration(config, scheme, null);
        config.setProperty(ConfigProperties.CRYPTO_SCHEMES,
            String.join(",", CryptoUtil.SUPPORTED_SCHEMES.keySet()));

        CryptoManager cryptoManager = this.buildCryptoManager(config);

        assertThat(cryptoManager.getCryptoScheme(consumer))
            .isEqualTo(scheme);
    }

    @Test
    public void testGetAnonCloudConsumerCryptoSchemeReturnsDefaultSchemeWhenNoCapabilitiesAreDefined()
        throws Exception {

        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setSupportedKeyAlgorithmOids(null)
            .setSupportedSignatureAlgorithmOids(null);

        CryptoManager cryptoManager = this.buildCryptoManager();

        assertThat(cryptoManager.getCryptoScheme(consumer))
            .isEqualTo(cryptoManager.getDefaultCryptoScheme());
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGetAnonCloudConsumerCryptoSchemeThrowsExceptionWhenKeyAlgorithmIsNotSupported(
        Scheme scheme) throws Exception {

        OidUtil oidUtil = CryptoUtil.getOidUtil();

        String keyAlgoOid = oidUtil.getKeyAlgorithmOid(scheme.keyAlgorithm())
            .orElseThrow(() -> new RuntimeException("scheme key algorithm does not map to an OID"));

        String sigAlgoOid = oidUtil.getSignatureAlgorithmOid(scheme.signatureAlgorithm())
            .orElseThrow(() -> new RuntimeException("scheme signature algorithm does not map to an OID"));

        // Reverse the key algorithm OID to guarantee it doesn't match our scheme's key algo OID
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setSupportedKeyAlgorithmOids(Set.of(reverseOid(keyAlgoOid)))
            .setSupportedSignatureAlgorithmOids(Set.of(sigAlgoOid));

        // Build a configuration that definitely contains and lists the scheme under test
        DevConfig config = TestConfig.defaults();
        CryptoUtil.generateSchemeConfiguration(config, scheme, null);
        config.setProperty(ConfigProperties.CRYPTO_SCHEMES,
            String.join(",", CryptoUtil.SUPPORTED_SCHEMES.keySet()));

        CryptoManager cryptoManager = this.buildCryptoManager(config);

        assertThrows(CryptoCapabilitiesException.class, () -> cryptoManager.getCryptoScheme(consumer));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGetAnonCloudConsumerCryptoSchemeThrowsExceptionWhenSignatureAlgorithmIsNotSupported(
        Scheme scheme) throws Exception {

        OidUtil oidUtil = CryptoUtil.getOidUtil();

        String keyAlgoOid = oidUtil.getKeyAlgorithmOid(scheme.keyAlgorithm())
            .orElseThrow(() -> new RuntimeException("scheme key algorithm does not map to an OID"));

        String sigAlgoOid = oidUtil.getSignatureAlgorithmOid(scheme.signatureAlgorithm())
            .orElseThrow(() -> new RuntimeException("scheme signature algorithm does not map to an OID"));

        // Reverse the key algorithm OID to guarantee it doesn't match our scheme's key algo OID
        AnonymousCloudConsumer consumer = new AnonymousCloudConsumer()
            .setSupportedKeyAlgorithmOids(Set.of(keyAlgoOid))
            .setSupportedSignatureAlgorithmOids(Set.of(reverseOid(sigAlgoOid)));

        // Build a configuration that definitely contains and lists the scheme under test
        DevConfig config = TestConfig.defaults();
        CryptoUtil.generateSchemeConfiguration(config, scheme, null);
        config.setProperty(ConfigProperties.CRYPTO_SCHEMES,
            String.join(",", CryptoUtil.SUPPORTED_SCHEMES.keySet()));

        CryptoManager cryptoManager = this.buildCryptoManager(config);

        assertThrows(CryptoCapabilitiesException.class, () -> cryptoManager.getCryptoScheme(consumer));
    }

    @Test
    public void testGetAnonCloudConsumerCryptoSchemeThrowsExceptionOnNullConsumer() throws Exception {
        CryptoManager cryptoManager = this.buildCryptoManager();

        assertThrows(IllegalArgumentException.class,
            () -> cryptoManager.getCryptoScheme((AnonymousCloudConsumer) null));
    }

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
        List<Scheme> supportedSchemes = new ArrayList<>(CryptoUtil.SUPPORTED_SCHEMES.values());
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
        for (Scheme scheme : CryptoUtil.SUPPORTED_SCHEMES.values()) {
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
        for (Scheme scheme : CryptoUtil.SUPPORTED_SCHEMES.values()) {
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

            // Ensure that our supported schemes are present in the config
            CryptoManagerTest.addSchemeConfig(config, List.copyOf(CryptoUtil.SUPPORTED_SCHEMES.values()));

            String schemes = String.join(",", CryptoUtil.SUPPORTED_SCHEMES.keySet());
            config.setProperty(ConfigProperties.CRYPTO_SCHEMES, schemes);

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
            return CryptoUtil.SUPPORTED_SCHEMES.values()
                .stream()
                .map(Arguments::of);
        }

        @ParameterizedTest
        @MethodSource("schemeSource")
        public void testIsTrustedCertificateRejectsUnknownSelfSignedCertificate(Scheme scheme)
            throws Exception {

            // Ensure the scheme from which we generate our cert is disconnected from those in our config
            Scheme alt = CryptoUtil.generateSchemeFromScheme(scheme);
            X509Certificate testCert = CryptoUtil.generateX509Certificate(alt);

            boolean output = this.cryptoManager.isTrustedCertificate(testCert);
            assertFalse(output);
        }

        @ParameterizedTest
        @MethodSource("schemeSource")
        public void testIsTrustedCertificateRejectsUnknownSignedCertificate(Scheme scheme) throws Exception {
            // Ensure the scheme from which we generate our cert is disconnected from those in our config
            Scheme alt = CryptoUtil.generateSchemeFromScheme(scheme);
            X509Certificate testCert = CryptoUtil.generateSignedX509Certificate(alt);

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
    public void testGetSignatureValidatorAllowsSchemeWithoutPrivateKey(Scheme scheme) {
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

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGetKeyPairGenerator(Scheme scheme) {
        CryptoManager cryptoManager = this.buildCryptoManager();

        KeyPairGenerator output = cryptoManager.getKeyPairGenerator(scheme);
        assertNotNull(output);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGetKeyPairGeneratorAllowsSchemeWithoutPrivateKey(Scheme scheme) {
        CryptoManager cryptoManager = this.buildCryptoManager();

        Scheme keyless = new Scheme.Builder()
            .setName(scheme.name())
            .setCertificate(scheme.certificate())
            .setSignatureAlgorithm(scheme.signatureAlgorithm())
            .setKeyAlgorithm(scheme.keyAlgorithm())
            .setKeySize(scheme.keySize().orElse(null))
            .build();

        KeyPairGenerator output = cryptoManager.getKeyPairGenerator(keyless);
        assertNotNull(output);
    }

    @Test
    public void testGetKeyPairGeneratorThrowsExceptionOnNullInput() {
        CryptoManager cryptoManager = this.buildCryptoManager();
        assertThrows(IllegalArgumentException.class, () -> cryptoManager.getKeyPairGenerator(null));
    }

    // TODO: FIXME: Temporary logic testing. Remove once the feature flag is removed.
    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGetConsumerCryptoSchemeReturnsDefaultWhenCapsSpecifiedIfNegotiationDisabled(
        Scheme scheme) throws Exception {

        OidUtil oidUtil = CryptoUtil.getOidUtil();

        String keyAlgoOid = oidUtil.getKeyAlgorithmOid(scheme.keyAlgorithm())
            .orElseThrow(() -> new RuntimeException("scheme key algorithm does not map to an OID"));

        String sigAlgoOid = oidUtil.getSignatureAlgorithmOid(scheme.signatureAlgorithm())
            .orElseThrow(() -> new RuntimeException("scheme signature algorithm does not map to an OID"));

        Consumer consumer = new Consumer()
            .setSupportedKeyAlgorithmOids(Set.of(keyAlgoOid))
            .setSupportedSignatureAlgorithmOids(Set.of(sigAlgoOid));

        // Build a configuration that definitely contains and lists the scheme under test
        DevConfig config = TestConfig.defaults();
        CryptoUtil.generateSchemeConfiguration(config, scheme, null);
        config.setProperty(ConfigProperties.CRYPTO_SCHEMES,
            String.join(",", CryptoUtil.SUPPORTED_SCHEMES.keySet()));

        // Disable client negotiation to ensure they always get the default no matter what we set
        config.setProperty(ConfigProperties.CRYPTO_CLIENT_NEGOTIATION_ENABLED, "false");

        CryptoManager cryptoManager = this.buildCryptoManager(config);

        assertThat(cryptoManager.getCryptoScheme(consumer))
            .isEqualTo(cryptoManager.getDefaultCryptoScheme());
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGetConsumerCryptoSchemeReturnsExpectedWhenCapsSpecifiedIfNegotiationDisabledForManifest(
        Scheme scheme) throws Exception {
        // This test verifies that manifest consumers bypass the scheme negotiation feature flag

        OidUtil oidUtil = CryptoUtil.getOidUtil();

        String keyAlgoOid = oidUtil.getKeyAlgorithmOid(scheme.keyAlgorithm())
            .orElseThrow(() -> new RuntimeException("scheme key algorithm does not map to an OID"));

        String sigAlgoOid = oidUtil.getSignatureAlgorithmOid(scheme.signatureAlgorithm())
            .orElseThrow(() -> new RuntimeException("scheme signature algorithm does not map to an OID"));

        ConsumerType ctype = new ConsumerType()
            .setId("type_id")
            .setManifest(true);

        Consumer consumer = new Consumer()
            .setType(ctype)
            .setSupportedKeyAlgorithmOids(Set.of(keyAlgoOid))
            .setSupportedSignatureAlgorithmOids(Set.of(sigAlgoOid));

        // Build a configuration that definitely contains and lists the scheme under test
        DevConfig config = TestConfig.defaults();
        CryptoUtil.generateSchemeConfiguration(config, scheme, null);
        config.setProperty(ConfigProperties.CRYPTO_SCHEMES,
            String.join(",", CryptoUtil.SUPPORTED_SCHEMES.keySet()));

        // Disable client scheme negotiation to ensure that our manifest consumer can still negoate even
        // though regular clients cannot
        config.setProperty(ConfigProperties.CRYPTO_CLIENT_NEGOTIATION_ENABLED, "false");

        CryptoManager cryptoManager = this.buildCryptoManager(config);

        assertThat(cryptoManager.getCryptoScheme(consumer))
            .isEqualTo(scheme);
    }

    // end temporary tests

}
