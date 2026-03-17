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
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public abstract class OidUtilTest {

    /**
     * An incomplete mapping of known, standardized Java key (pair) algorithm names to their expected OIDs.
     * More mappings can be added to this map to improve test coverage; but at a minimum, signature algorithms
     * in use in known Candlepin deployments should be listed to ensure correct functionality in those
     * environments.
     */
    private static final Map<String, String> KEY_ALGO_NAMES_TO_OIDS = Map.ofEntries(
        Map.entry("ML-DSA-44",              "2.16.840.1.101.3.4.3.17"),
        Map.entry("ML-DSA-65",              "2.16.840.1.101.3.4.3.18"),
        Map.entry("ML-DSA-87",              "2.16.840.1.101.3.4.3.19"),
        Map.entry("RSA",                    "1.2.840.113549.1.1.1")
    );

    /**
     * An incomplete mapping of known, standardized Java signature algorithm names to their expected OIDs.
     * More mappings can be added to this map to improve test coverage; but at a minimum, signature algorithms
     * in use in known Candlepin deployments should be listed to ensure correct functionality in those
     * environments.
     */
    private static final Map<String, String> SIG_ALGO_NAMES_TO_OIDS = Map.ofEntries(
        Map.entry("ML-DSA-44",              "2.16.840.1.101.3.4.3.17"),
        Map.entry("ML-DSA-65",              "2.16.840.1.101.3.4.3.18"),
        Map.entry("ML-DSA-87",              "2.16.840.1.101.3.4.3.19"),
        Map.entry("SHA224withRSA",          "1.2.840.113549.1.1.14"),
        Map.entry("SHA256withRSA",          "1.2.840.113549.1.1.11"),
        Map.entry("SHA384withRSA",          "1.2.840.113549.1.1.12"),
        Map.entry("SHA512withRSA",          "1.2.840.113549.1.1.13")
    );

    /**
     * Builds a new OidUtil instance to test. Each invocation of this method should return a new instance to
     * avoid unintended object state retention between tests.
     *
     * @return
     *  a new OidUtil instance to test
     */
    protected abstract OidUtil buildOidUtil();

    private static Stream<Arguments> kvpairSource(Map<String, String> source) {
        return source.entrySet().stream()
            .map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
    }

    private static Stream<Arguments> kmvmapSource(Map<String, String> source) {
        Map<String, Set<String>> reversed = source.entrySet().stream()
            .collect(Collectors.groupingBy(Map.Entry::getValue,
                Collectors.mapping(Map.Entry::getKey, Collectors.toSet())));

        return reversed.entrySet().stream()
            .map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
    }

    public static Stream<Arguments> keyNameToOidSource() {
        return kvpairSource(KEY_ALGO_NAMES_TO_OIDS);
    }

    public static Stream<Arguments> keyOidSource() {
        return KEY_ALGO_NAMES_TO_OIDS.values()
            .stream()
            .map(Arguments::of);
    }

    public static Stream<Arguments> keyOidToNameSource() {
        return kmvmapSource(KEY_ALGO_NAMES_TO_OIDS);
    }

    public static Stream<Arguments> signatureNameToOidSource() {
        return kvpairSource(SIG_ALGO_NAMES_TO_OIDS);
    }

    public static Stream<Arguments> signatureOidToNameSource() {
        return kmvmapSource(SIG_ALGO_NAMES_TO_OIDS);
    }

    public static Stream<Arguments> signatureOidSource() {
        return SIG_ALGO_NAMES_TO_OIDS.values()
            .stream()
            .map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("keyNameToOidSource")
    public void testKeyAlgorithmNameToOidTranslation(String algorithmName, String expected) {
        OidUtil oidUtil = this.buildOidUtil();

        assertThat(oidUtil.getKeyAlgorithmOid(algorithmName))
            .isNotNull()
            .isPresent()
            .hasValue(expected);
    }

    @ParameterizedTest
    @MethodSource("keyOidSource")
    public void testKeyAlgorithmOidToOidTranslation(String algorithmOid) {
        // This test validates that algorithm "names" that are OIDs map to themselves.
        OidUtil oidUtil = this.buildOidUtil();

        assertThat(oidUtil.getKeyAlgorithmOid(algorithmOid))
            .isNotNull()
            .isPresent()
            .hasValue(algorithmOid);
    }

    @Test
    public void testGetKeyAlgorithmOidReturnsEmptyOnUnknownAlgorithm() {
        OidUtil oidUtil = this.buildOidUtil();

        assertThat(oidUtil.getKeyAlgorithmOid("unknown_algorithm"))
            .isNotNull()
            .isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testGetKeyAlgorithmOidRejectsNullAndEmptyInputs(String input) {
        OidUtil oidUtil = this.buildOidUtil();

        assertThrows(IllegalArgumentException.class, () -> oidUtil.getKeyAlgorithmOid(input));
    }

    @ParameterizedTest
    @MethodSource("keyOidToNameSource")
    public void testKeyAlgorithmOidToNameTranslation(String algorithmOid, Set<String> accepted) {
        OidUtil oidUtil = this.buildOidUtil();

        assertThat(oidUtil.getKeyAlgorithmName(algorithmOid))
            .isNotNull()
            .isPresent()
            .hasValueSatisfying(value -> accepted.contains(value));
    }

    @Test
    public void testGetKeyAlgorithmNameReturnsEmptyOnUnknownAlgorithm() {
        OidUtil oidUtil = this.buildOidUtil();

        assertThat(oidUtil.getKeyAlgorithmName("1.2.3.4.5.6.7.8.9"))
            .isNotNull()
            .isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testGetKeyAlgorithmNameRejectsNullAndEmptyInputs(String input) {
        OidUtil oidUtil = this.buildOidUtil();

        assertThrows(IllegalArgumentException.class, () -> oidUtil.getKeyAlgorithmName(input));
    }

    @ParameterizedTest
    @MethodSource("signatureNameToOidSource")
    public void testSignatureAlgorithmNameToOidTranslation(String algorithmName, String expected) {
        OidUtil oidUtil = this.buildOidUtil();

        assertThat(oidUtil.getSignatureAlgorithmOid(algorithmName))
            .isNotNull()
            .isPresent()
            .hasValue(expected);
    }

    @ParameterizedTest
    @MethodSource("signatureOidSource")
    public void testSignatureAlgorithmOidToOidTranslation(String algorithmOid) {
        // This test validates that algorithm "names" that are OIDs map to themselves.
        OidUtil oidUtil = this.buildOidUtil();

        assertThat(oidUtil.getSignatureAlgorithmOid(algorithmOid))
            .isNotNull()
            .isPresent()
            .hasValue(algorithmOid);
    }

    @Test
    public void testGetSignatureAlgorithmOidReturnsEmptyOnUnknownAlgorithm() {
        OidUtil oidUtil = this.buildOidUtil();

        assertThat(oidUtil.getSignatureAlgorithmOid("unknown_algorithm"))
            .isNotNull()
            .isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testGetSignatureAlgorithmOidRejectsNullAndEmptyInputs(String input) {
        OidUtil oidUtil = this.buildOidUtil();

        assertThrows(IllegalArgumentException.class, () -> oidUtil.getSignatureAlgorithmOid(input));
    }

    @ParameterizedTest
    @MethodSource("signatureOidToNameSource")
    public void testSignatureAlgorithmOidToNameTranslation(String algorithmOid, Set<String> accepted) {
        OidUtil oidUtil = this.buildOidUtil();

        assertThat(oidUtil.getSignatureAlgorithmName(algorithmOid))
            .isNotNull()
            .isPresent()
            .hasValueSatisfying(value -> accepted.contains(value));
    }

    @Test
    public void testGetSignatureAlgorithmNameReturnsEmptyOnUnknownAlgorithm() {
        OidUtil oidUtil = this.buildOidUtil();

        assertThat(oidUtil.getSignatureAlgorithmName("1.2.3.4.5.6.7.8.9"))
            .isNotNull()
            .isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    public void testGetSignatureAlgorithmNameRejectsNullAndEmptyInputs(String input) {
        OidUtil oidUtil = this.buildOidUtil();

        assertThrows(IllegalArgumentException.class, () -> oidUtil.getSignatureAlgorithmName(input));
    }

    @ParameterizedTest
    @ValueSource(strings = { "1.2.3", "1.2.4", "1.2.5", "11.22.33.44.55" })
    public void testIsAlgorithmSupported(String algorithmOid) {
        OidUtil oidUtil = this.buildOidUtil();

        Set<String> supportedAlgorithmOids = Set.of("1.2.3", "1.2.4", "1.2.5", "11.22.33.44.55");

        assertThat(oidUtil.isAlgorithmSupported(supportedAlgorithmOids, algorithmOid))
            .isTrue();
    }

    @Test
    public void testAlgorithmIsNotSupported() {
        OidUtil oidUtil = this.buildOidUtil();

        Set<String> supportedAlgorithmOids = Set.of("1.2.3", "1.2.4", "1.2.5");
        String algorithmOid = "1.2.3.4";

        assertThat(oidUtil.isAlgorithmSupported(supportedAlgorithmOids, algorithmOid))
            .isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "1.2.3.4", "1.2.3.", "1.2", "1.2." })
    public void testIsAlgorithmSupportedDoesNotSupportOidHierarchies(String algorithmOid) {
        // Impl note: at the time of writing, no OID families or hierarchies are supported. If/when that
        // changes, this test will no longer be valid and will need to be removed and replaced with a test
        // that verifies support for known algorithm families or supported hierarchies

        OidUtil oidUtil = this.buildOidUtil();
        Set<String> supportedAlgorithmOids = Set.of("1.2.3", "1.2.4", "1.2.5");

        assertThat(oidUtil.isAlgorithmSupported(supportedAlgorithmOids, algorithmOid))
            .isFalse();
    }

    @Test
    public void testIsAlgorithmSupportedRequiresNonNullAlgorithmOidCollection() {
        OidUtil oidUtil = this.buildOidUtil();
        assertThrows(IllegalArgumentException.class, () -> oidUtil.isAlgorithmSupported(null, "1.2.3"));
    }

    @Test
    public void testIsAlgorithmSupportedAllowsEmptyAlgorithmOidCollection() {
        // If an empty collection of algorithm OIDs is provided, this method should still function, but it
        // should return false.

        OidUtil oidUtil = this.buildOidUtil();

        assertThat(oidUtil.isAlgorithmSupported(Set.of(), "1.2.3"))
            .isFalse();
    }

    @Test
    public void testIsAlgorithmSupportedRequiresNonNullTestAlgorithmOid() {
        OidUtil oidUtil = this.buildOidUtil();
        Set<String> supportedAlgorithmOids = Set.of("1.2.3", "1.2.4", "1.2.5");

        assertThrows(IllegalArgumentException.class, () ->
            oidUtil.isAlgorithmSupported(supportedAlgorithmOids, null));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " ", "\t" })
    public void testIsAlgorithmSupportedRejectsEmptyTestAlgorithmOid(String algorithmOid) {
        OidUtil oidUtil = this.buildOidUtil();
        Set<String> supportedAlgorithmOids = Set.of("1.2.3", "1.2.4", "1.2.5");

        assertThrows(IllegalArgumentException.class, () ->
            oidUtil.isAlgorithmSupported(supportedAlgorithmOids, algorithmOid));
    }

}
