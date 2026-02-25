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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.candlepin.test.CryptoUtil;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.security.Key;
import java.security.KeyPair;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;



/**
 * Base test suite for KeyPairGenerators. Subclasses are expected to provide a concrete KeyPairGenerators
 * implementation to test, as well as test for any implementation-specific functionality not covered by the
 * base KeyPairGenerators API/interface.
 */
public abstract class KeyPairGeneratorTest {

    // A list of known, supported schemes
    private static final List<Scheme> SUPPORTED_SCHEMES = CryptoUtil.generateSupportedSchemes().toList();

    protected static Stream<Arguments> schemeSource() {
        return SUPPORTED_SCHEMES.stream()
            .map(Arguments::of);
    }

    /**
     * Builds a new KeyPairGenerator instance to test. Each invocation of this method should return a new
     * instance to avoid unintended object state retention between tests.
     *
     * @return
     *  a new KeyPairGenerator instance to test
     */
    protected abstract KeyPairGenerator buildKeyPairGenerator(Scheme scheme);

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGetCryptoScheme(Scheme scheme) throws Exception {
        KeyPairGenerator generator = this.buildKeyPairGenerator(scheme);

        assertThat(generator.getCryptoScheme())
            .isNotNull()
            .returns(scheme.name(), Scheme::name)
            .returns(scheme.privateKey(), Scheme::privateKey)
            .returns(scheme.certificate(), Scheme::certificate)
            .returns(scheme.signatureAlgorithm(), Scheme::signatureAlgorithm)
            .returns(scheme.keyAlgorithm(), Scheme::keyAlgorithm)
            .returns(scheme.keySize(), Scheme::keySize);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGenerateKeyPair(Scheme scheme) throws Exception {
        KeyPairGenerator generator = this.buildKeyPairGenerator(scheme);

        KeyPair keypair = generator.generateKeyPair();
        assertNotNull(keypair);

        // Some supported algorithm names are actually mapped to whatever the crypto backend decides is the
        // "best" sub-algorithm for it. E.g. "ML-DSA" without a security level will result in the backend
        // choosing a security level for us.
        // Add more mappings here as necessary. Note that algorithms that are case-sensitive matches directly
        // do not need to be added here. Only add mappings that are different than the resultant algorithm
        // strings on the received keys. Keys and values should be expressed as upper-case.
        Map<String, Set<String>> algorithmMap = Map.of(
            "ML-DSA", Set.of("ML-DSA-44", "ML-DSA-65", "ML-DSA-87"));

        Consumer<String> algorithmCheck = algorithm -> {
            // If we later decide null algorithms on the pub/priv key are okay, we need to change this to
            // bail out of this check
            assertNotNull(algorithm);

            // If the algorithm string matches our scheme's algorithm exactly (case-insensitive), we're good.
            if (algorithm.equalsIgnoreCase(scheme.keyAlgorithm())) {
                return;
            }

            // Otherwise, we need it to map to one of our known mappable algorithm values.
            assertThat(algorithmMap.get(scheme.keyAlgorithm().toUpperCase()))
                .isNotNull()
                .contains(algorithm.toUpperCase());
        };

        // We should have both a public and private key, and the algorithms should align with the algorithm
        // from the scheme
        assertThat(keypair.getPublic())
            .isNotNull()
            .extracting(Key::getAlgorithm)
            .satisfies(algorithmCheck);

        assertThat(keypair.getPrivate())
            .isNotNull()
            .extracting(Key::getAlgorithm)
            .satisfies(algorithmCheck);
    }

    @ParameterizedTest
    @MethodSource("schemeSource")
    public void testGeneratedKeyPairsAllowExtraction(Scheme scheme) throws Exception {
        // This test verifies that the keys in the generated pairs support key encoding via .getEncoded().
        // Unfortunately, we can't do that generally, so all we can do is verify the output is not null.

        KeyPairGenerator generator = this.buildKeyPairGenerator(scheme);

        KeyPair keypair = generator.generateKeyPair();
        assertNotNull(keypair);

        assertThat(keypair.getPublic())
            .isNotNull()
            .extracting(Key::getEncoded)
            .isNotNull();

        assertThat(keypair.getPrivate())
            .isNotNull()
            .extracting(Key::getEncoded)
            .isNotNull();
    }

}
