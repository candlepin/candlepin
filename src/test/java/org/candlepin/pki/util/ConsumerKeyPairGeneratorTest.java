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
package org.candlepin.pki.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.model.Consumer;
import org.candlepin.model.KeyPairData;
import org.candlepin.model.KeyPairDataCurator;
import org.candlepin.pki.CryptoManager;
import org.candlepin.pki.Scheme;
import org.candlepin.test.CryptoUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyException;
import java.security.KeyPair;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;



public class ConsumerKeyPairGeneratorTest {

    // A list of known, supported schemes
    private static final List<Scheme> SUPPORTED_SCHEMES = CryptoUtil.generateSupportedSchemes().toList();

    // Some supported algorithm names are actually mapped to whatever the crypto backend decides is the
    // "best" sub-algorithm for it. E.g. "ML-DSA" without a security level will result in the backend
    // choosing a security level for us.
    // Add more mappings here as necessary. Note that algorithms that are case-sensitive matches directly
    // do not need to be added here. Only add mappings that are different than the resultant algorithm
    // strings on the received keys. Keys and values should be expressed as upper-case.
    private static final Map<String, Set<String>> ALGORITHM_MAP = Map.of(
        "ML-DSA", Set.of("ML-DSA-44", "ML-DSA-65", "ML-DSA-87"));

    private static Stream<Arguments> schemeSource() {
        return SUPPORTED_SCHEMES.stream()
            .map(Arguments::of);
    }

    private DevConfig buildConfiguration() throws KeyException, IOException {
        DevConfig config = TestConfig.defaults();

        for (Scheme scheme : SUPPORTED_SCHEMES) {
            CryptoUtil.generateSchemeConfiguration(config, scheme, null);
        }

        String schemesList = SUPPORTED_SCHEMES.stream()
            .map(Scheme::name)
            .collect(Collectors.joining(","));

        config.setProperty(ConfigProperties.CRYPTO_SCHEMES, schemesList);

        return config;
    }

    private DevConfig ensureSchemeInSchemeList(DevConfig config, Scheme scheme) {
        String schemeList = config.getString(ConfigProperties.CRYPTO_SCHEMES);

        // We're assuming this is well-formed in this test suite
        List<String> schemes = List.of(schemeList.split("\\s*,\\s*"));

        if (!schemes.contains(scheme.name())) {
            schemes.add(scheme.name());

            config.setProperty(ConfigProperties.CRYPTO_SCHEMES, String.join(",", schemes));
        }

        return config;
    }

    private String getKeyAlgorithmString(Scheme scheme) {
        return scheme.keySize()
            .map(size -> scheme.keyAlgorithm() + ":" + size)
            .orElse(scheme.keyAlgorithm());
    }

    private KeyPairData generateKeyPairData(Scheme scheme) throws KeyException {
        KeyPair keypair = CryptoUtil.generateKeyPair(scheme);

        return new KeyPairData()
            .setPublicKeyData(keypair.getPublic().getEncoded())
            .setPrivateKeyData(keypair.getPrivate().getEncoded())
            .setAlgorithm(this.getKeyAlgorithmString(scheme));
    }

    private KeyPairDataCurator mockKeyPairDataCurator() {
        KeyPairDataCurator keyPairDataCurator = mock(KeyPairDataCurator.class);

        doAnswer(returnsFirstArg()).when(keyPairDataCurator).merge(any());
        doAnswer(returnsFirstArg()).when(keyPairDataCurator).create(any());
        doAnswer(returnsFirstArg()).when(keyPairDataCurator).create(any(), anyBoolean());

        return keyPairDataCurator;
    }

    private ConsumerKeyPairGenerator buildKeyPairGenerator(CryptoManager cryptoManager) {
        return new ConsumerKeyPairGenerator(cryptoManager, this.mockKeyPairDataCurator());
    }

    private ConsumerKeyPairGenerator buildKeyPairGenerator(Configuration config) {
        return this.buildKeyPairGenerator(CryptoUtil.getCryptoManager(config));
    }

    private void validateKey(Scheme scheme, Key key) {
        // Ensure the key provides its encoding
        assertThat(key)
            .isNotNull()
            .doesNotReturn(null, Key::getEncoded);

        // Ensure the algorithm is what we expect
        String algorithm = key.getAlgorithm();
        assertNotNull(algorithm);

        // If the algorithm string matches our scheme's algorithm exactly (case-insensitive), we're good.
        if (algorithm.equalsIgnoreCase(scheme.keyAlgorithm())) {
            return;
        }

        // Otherwise, we need it to map to one of our known mappable algorithm values.
        assertThat(ALGORITHM_MAP.get(scheme.keyAlgorithm().toUpperCase()))
            .isNotNull()
            .contains(algorithm.toUpperCase());
    }

    @Test
    public void testConsumerKeyPairGeneratorRequiresConsumer() throws Exception {
        DevConfig config = this.buildConfiguration();
        ConsumerKeyPairGenerator generator = this.buildKeyPairGenerator(config);

        assertThrows(IllegalArgumentException.class, () -> generator.getConsumerKeyPair(null));
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGetConsumerKeyPairGeneratesWhenConsumerCacheIsNull(Scheme scheme) throws Exception {
        DevConfig config = this.buildConfiguration();

        Consumer consumer = CryptoUtil.configureConsumerForSchemes(new Consumer(), scheme)
            .setKeyPairData(null);

        ConsumerKeyPairGenerator generator = this.buildKeyPairGenerator(config);
        KeyPair output = generator.getConsumerKeyPair(consumer);

        assertNotNull(output);
        assertThat(output.getPublic())
            .satisfies(key -> this.validateKey(scheme, key));
        assertThat(output.getPrivate())
            .satisfies(key -> this.validateKey(scheme, key));

        // We also expect the consumer's cache on getKeyPairData has been updated accordingly
        assertThat(consumer.getKeyPairData())
            .isNotNull()
            .returns(output.getPublic().getEncoded(), KeyPairData::getPublicKeyData)
            .returns(output.getPrivate().getEncoded(), KeyPairData::getPrivateKeyData);
    }

    @Test
    public void testGetConsumerKeyPairGeneratesWhenConsumerCacheIsNullUsingDefaultScheme()
        throws Exception {

        DevConfig config = this.buildConfiguration();
        CryptoManager cryptoManager = CryptoUtil.getCryptoManager(config);
        Scheme scheme = cryptoManager.getDefaultCryptoScheme();

        Consumer consumer = CryptoUtil.configureConsumerForDefaultScheme(new Consumer())
            .setKeyPairData(null);

        ConsumerKeyPairGenerator generator = this.buildKeyPairGenerator(cryptoManager);
        KeyPair output = generator.getConsumerKeyPair(consumer);

        assertNotNull(output);
        assertThat(output.getPublic())
            .satisfies(key -> this.validateKey(scheme, key));
        assertThat(output.getPrivate())
            .satisfies(key -> this.validateKey(scheme, key));

        // We also expect the consumer's cache on getKeyPairData has been updated accordingly
        assertThat(consumer.getKeyPairData())
            .isNotNull()
            .returns(output.getPublic().getEncoded(), KeyPairData::getPublicKeyData)
            .returns(output.getPrivate().getEncoded(), KeyPairData::getPrivateKeyData);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGetConsumerKeyPairReturnsExistingWhenConsumerCacheIsValid(Scheme scheme)
        throws Exception {
        // Cache must be non-null *and* of the same keypair as the given scheme.

        DevConfig config = this.buildConfiguration();

        KeyPairData kpdata = this.generateKeyPairData(scheme);

        Consumer consumer = CryptoUtil.configureConsumerForSchemes(new Consumer(), scheme)
            .setKeyPairData(kpdata);

        ConsumerKeyPairGenerator generator = this.buildKeyPairGenerator(config);
        KeyPair output = generator.getConsumerKeyPair(consumer);

        assertNotNull(output);
        assertThat(output.getPublic())
            .returns(kpdata.getPublicKeyData(), Key::getEncoded)
            .satisfies(key -> this.validateKey(scheme, key));
        assertThat(output.getPrivate())
            .returns(kpdata.getPrivateKeyData(), Key::getEncoded)
            .satisfies(key -> this.validateKey(scheme, key));

        assertThat(consumer.getKeyPairData())
            .isNotNull()
            .returns(kpdata.getPublicKeyData(), KeyPairData::getPublicKeyData)
            .returns(kpdata.getPrivateKeyData(), KeyPairData::getPrivateKeyData);
    }

    @Test
    public void testGetConsumerKeyPairReturnsExistingWhenConsumerCacheIsValidUsingDefaultScheme()
        throws Exception {
        // Cache must be non-null *and* of the same keypair as the given scheme.

        DevConfig config = this.buildConfiguration();
        CryptoManager cryptoManager = CryptoUtil.getCryptoManager(config);
        Scheme scheme = cryptoManager.getDefaultCryptoScheme();

        KeyPairData kpdata = this.generateKeyPairData(scheme);

        Consumer consumer = CryptoUtil.configureConsumerForDefaultScheme(new Consumer())
            .setKeyPairData(kpdata);

        ConsumerKeyPairGenerator generator = this.buildKeyPairGenerator(cryptoManager);
        KeyPair output = generator.getConsumerKeyPair(consumer);

        assertNotNull(output);
        assertThat(output.getPublic())
            .returns(kpdata.getPublicKeyData(), Key::getEncoded)
            .satisfies(key -> this.validateKey(scheme, key));
        assertThat(output.getPrivate())
            .returns(kpdata.getPrivateKeyData(), Key::getEncoded)
            .satisfies(key -> this.validateKey(scheme, key));

        assertThat(consumer.getKeyPairData())
            .isNotNull()
            .returns(kpdata.getPublicKeyData(), KeyPairData::getPublicKeyData)
            .returns(kpdata.getPrivateKeyData(), KeyPairData::getPrivateKeyData);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGetConsumerKeyPairGeneratesNewWhenConsumerCacheIsWrongScheme(Scheme scheme)
        throws Exception {
        // Cache must be non-null and generated with a different scheme than current

        DevConfig config = this.buildConfiguration();

        byte[] previousPubKey = "fake_public_key".getBytes(StandardCharsets.UTF_8);
        byte[] previousPrivKey = "fake_private_key".getBytes(StandardCharsets.UTF_8);

        KeyPairData kpdata = new KeyPairData()
            .setPublicKeyData(previousPubKey)
            .setPrivateKeyData(previousPrivKey)
            .setAlgorithm("fake_scheme");

        Consumer consumer = CryptoUtil.configureConsumerForSchemes(new Consumer(), scheme)
            .setKeyPairData(kpdata);

        ConsumerKeyPairGenerator generator = this.buildKeyPairGenerator(config);
        KeyPair output = generator.getConsumerKeyPair(consumer);

        assertNotNull(output);
        assertThat(output.getPublic())
            .doesNotReturn(previousPubKey, Key::getEncoded)
            .satisfies(key -> this.validateKey(scheme, key));
        assertThat(output.getPrivate())
            .doesNotReturn(previousPrivKey, Key::getEncoded)
            .satisfies(key -> this.validateKey(scheme, key));

        assertThat(consumer.getKeyPairData())
            .isNotNull()
            .doesNotReturn(previousPubKey, KeyPairData::getPublicKeyData)
            .doesNotReturn(previousPrivKey, KeyPairData::getPrivateKeyData);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGetConsumerKeyPairGeneratesNewWhenConsumerCacheIsMalformed(Scheme scheme)
        throws Exception {
        // Cache must be non-null and generated with a different scheme than current

        DevConfig config = this.buildConfiguration();

        byte[] previousPubKey = "malformed_public_key".getBytes(StandardCharsets.UTF_8);
        byte[] previousPrivKey = "malformed_private_key".getBytes(StandardCharsets.UTF_8);

        String keyAlgorithmString = this.getKeyAlgorithmString(scheme);
        KeyPairData kpdata = new KeyPairData()
            .setPublicKeyData(previousPubKey)
            .setPrivateKeyData(previousPrivKey)
            .setAlgorithm(keyAlgorithmString);

        Consumer consumer = CryptoUtil.configureConsumerForSchemes(new Consumer(), scheme)
            .setKeyPairData(kpdata);

        ConsumerKeyPairGenerator generator = this.buildKeyPairGenerator(config);
        KeyPair output = generator.getConsumerKeyPair(consumer);

        assertNotNull(output);
        assertThat(output.getPublic())
            .doesNotReturn(previousPubKey, Key::getEncoded)
            .satisfies(key -> this.validateKey(scheme, key));
        assertThat(output.getPrivate())
            .doesNotReturn(previousPrivKey, Key::getEncoded)
            .satisfies(key -> this.validateKey(scheme, key));

        assertThat(consumer.getKeyPairData())
            .isNotNull()
            .returns(keyAlgorithmString, KeyPairData::getAlgorithm)
            .doesNotReturn(previousPubKey, KeyPairData::getPublicKeyData)
            .doesNotReturn(previousPrivKey, KeyPairData::getPrivateKeyData);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGetConsumerKeyPairGeneratesNewWhenConsumerCacheHasNullKeyData(Scheme scheme)
        throws Exception {
        // Cache must be non-null and generated with a different scheme than current

        DevConfig config = this.buildConfiguration();

        String keyAlgorithmString = this.getKeyAlgorithmString(scheme);
        KeyPairData kpdata = new KeyPairData()
            .setPublicKeyData(null)
            .setPrivateKeyData(null)
            .setAlgorithm(keyAlgorithmString);

        Consumer consumer = CryptoUtil.configureConsumerForSchemes(new Consumer(), scheme)
            .setKeyPairData(kpdata);

        ConsumerKeyPairGenerator generator = this.buildKeyPairGenerator(config);
        KeyPair output = generator.getConsumerKeyPair(consumer);

        assertNotNull(output);
        assertThat(output.getPublic())
            .doesNotReturn(null, Key::getEncoded)
            .satisfies(key -> this.validateKey(scheme, key));
        assertThat(output.getPrivate())
            .doesNotReturn(null, Key::getEncoded)
            .satisfies(key -> this.validateKey(scheme, key));

        assertThat(consumer.getKeyPairData())
            .isNotNull()
            .returns(keyAlgorithmString, KeyPairData::getAlgorithm)
            .returns(output.getPublic().getEncoded(), KeyPairData::getPublicKeyData)
            .returns(output.getPrivate().getEncoded(), KeyPairData::getPrivateKeyData);
    }

    @Test
    public void testGetConsumerKeyPairHandlesLegacyCacheEntriesWhenMatchingCurrentScheme() throws Exception {
        // At the time of writing, the legacy scheme was RSA
        Scheme legacyScheme = CryptoUtil.generateRsaScheme();
        String keyAlgorithmString = this.getKeyAlgorithmString(legacyScheme);

        DevConfig config = this.buildConfiguration();
        CryptoUtil.generateSchemeConfiguration(config, legacyScheme, null);
        this.ensureSchemeInSchemeList(config, legacyScheme);

        KeyPair keypair = CryptoUtil.generateKeyPair(legacyScheme);
        KeyPairData kpdata = new KeyPairData()
            .setPublicKeyData(keypair.getPublic().getEncoded())
            .setPrivateKeyData(keypair.getPrivate().getEncoded())
            .setAlgorithm(null);

        Consumer consumer = CryptoUtil.configureConsumerForSchemes(new Consumer(), legacyScheme)
            .setKeyPairData(kpdata);

        ConsumerKeyPairGenerator generator = this.buildKeyPairGenerator(config);
        KeyPair output = generator.getConsumerKeyPair(consumer);

        // In this case, even though the
        assertNotNull(output);
        assertThat(output.getPublic())
            .isNotNull()
            .satisfies(key -> this.validateKey(legacyScheme, key));
        assertThat(output.getPrivate())
            .isNotNull()
            .satisfies(key -> this.validateKey(legacyScheme, key));

        assertThat(consumer.getKeyPairData())
            .isNotNull()
            .returns(keyAlgorithmString, KeyPairData::getAlgorithm)
            .returns(keypair.getPublic().getEncoded(), KeyPairData::getPublicKeyData)
            .returns(keypair.getPrivate().getEncoded(), KeyPairData::getPrivateKeyData);
    }

    @Test
    public void testGetConsumerKeyPairHandlesLegacyCacheEntriesWhenNotMatchingCurrentScheme()
        throws Exception {

        // At the time of writing, the legacy scheme was RSA
        Scheme legacyScheme = CryptoUtil.generateRsaScheme();
        String legacyKeyAlgoString = this.getKeyAlgorithmString(legacyScheme);

        Scheme modernScheme = CryptoUtil.generateMldsaScheme();
        String modernKeyAlgoString = this.getKeyAlgorithmString(modernScheme);

        DevConfig config = this.buildConfiguration();
        CryptoUtil.generateSchemeConfiguration(config, legacyScheme, null);
        CryptoUtil.generateSchemeConfiguration(config, modernScheme, null);
        this.ensureSchemeInSchemeList(config, legacyScheme);

        KeyPair keypair = CryptoUtil.generateKeyPair(legacyScheme);
        KeyPairData kpdata = new KeyPairData()
            .setPublicKeyData(keypair.getPublic().getEncoded())
            .setPrivateKeyData(keypair.getPrivate().getEncoded())
            .setAlgorithm(null);

        Consumer consumer = CryptoUtil.configureConsumerForSchemes(new Consumer(), modernScheme)
            .setKeyPairData(kpdata);

        ConsumerKeyPairGenerator generator = this.buildKeyPairGenerator(config);
        KeyPair output = generator.getConsumerKeyPair(consumer);

        // In this case, even though the
        assertNotNull(output);
        assertThat(output.getPublic())
            .isNotNull()
            .satisfies(key -> this.validateKey(modernScheme, key));
        assertThat(output.getPrivate())
            .isNotNull()
            .satisfies(key -> this.validateKey(modernScheme, key));

        assertThat(consumer.getKeyPairData())
            .isNotNull()
            .returns(modernKeyAlgoString, KeyPairData::getAlgorithm)
            .returns(output.getPublic().getEncoded(), KeyPairData::getPublicKeyData)
            .returns(output.getPrivate().getEncoded(), KeyPairData::getPrivateKeyData);
    }


}
