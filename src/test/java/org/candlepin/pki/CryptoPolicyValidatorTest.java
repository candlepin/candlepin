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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.config.ConfigurationException;
import org.candlepin.sync.SchemeFile;
import org.candlepin.test.CryptoUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;

public class CryptoPolicyValidatorTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "\t" })
    public void testNoPolicyMeansNoRestrictions(String policy) {
        CryptoPolicyValidator validator = new CryptoPolicyValidator(policy);

        assertThat(validator.getDisabledAlgorithms()).isEmpty();
        assertThat(validator.checkAlgorithms("SHA256withRSA", "RSA", 4096)).isEmpty();
    }

    @Test
    public void testParsesPlainDisabledAlgorithm() {
        CryptoPolicyValidator validator = new CryptoPolicyValidator("MD2, MD5");

        assertThat(validator.getDisabledAlgorithms()).hasSize(2)
            .extracting(CryptoPolicyValidator.DisabledAlgorithm::name)
            .containsExactlyInAnyOrder("MD2", "MD5");
    }

    @Test
    public void testParsesKeySizeConstraintLessThan() {
        CryptoPolicyValidator validator = new CryptoPolicyValidator("RSA keySize < 2048");

        assertThat(validator.getDisabledAlgorithms()).singleElement()
            .extracting(CryptoPolicyValidator.DisabledAlgorithm::name,
                entry -> entry.keySizeConstraint().operator(), entry -> entry.keySizeConstraint().value())
            .containsExactly("RSA", "<", 2048);
    }

    @Test
    public void testParsesKeySizeConstraintLessThanOrEqual() {
        CryptoPolicyValidator validator = new CryptoPolicyValidator("RSA keySize <= 1024");

        assertThat(validator.getDisabledAlgorithms()).singleElement()
            .extracting(entry -> entry.keySizeConstraint().operator(),
                entry -> entry.keySizeConstraint().value()).containsExactly("<=", 1024);
    }

    @Test
    public void testParsesMultipleEntriesIncludingKeySizeConstraints() {
        CryptoPolicyValidator validator = new CryptoPolicyValidator(
            "MD2, RSA keySize < 2048, DSA keySize < 2048, EC keySize < 224");

        assertThat(validator.getDisabledAlgorithms()).hasSize(4);

        assertThat(validator.getDisabledAlgorithms()).element(0).satisfies(entry -> {
            assertThat(entry.name()).isEqualTo("MD2");
            assertThat(entry.keySizeConstraint()).isNull();
        });

        assertThat(validator.getDisabledAlgorithms()).element(1).satisfies(entry -> {
            assertThat(entry.name()).isEqualTo("RSA");
            assertThat(entry.keySizeConstraint()).isNotNull();
            assertThat(entry.keySizeConstraint().value()).isEqualTo(2048);
        });
    }

    @Test
    public void testSkipsJdkCAConstraint() {
        CryptoPolicyValidator validator = new CryptoPolicyValidator("SHA1 jdkCA & usage TLSServer, MD2");

        assertThat(validator.getDisabledAlgorithms()).singleElement()
            .extracting(CryptoPolicyValidator.DisabledAlgorithm::name).isEqualTo("MD2");
    }

    @Test
    public void testSkipsDenyAfterConstraint() {
        CryptoPolicyValidator validator = new CryptoPolicyValidator("SHA1 denyAfter 2020-01-01, MD5");

        assertThat(validator.getDisabledAlgorithms()).singleElement()
            .extracting(CryptoPolicyValidator.DisabledAlgorithm::name).isEqualTo("MD5");
    }

    @Test
    public void testSkipsUsageConstraint() {
        CryptoPolicyValidator validator = new CryptoPolicyValidator(
            "SHA1 usage TLSServer, RSA keySize < 2048");

        assertThat(validator.getDisabledAlgorithms()).singleElement()
            .extracting(CryptoPolicyValidator.DisabledAlgorithm::name).isEqualTo("RSA");
    }

    @Test
    public void testDetectsDisabledKeyAlgorithm() {
        CryptoPolicyValidator validator = new CryptoPolicyValidator("DSA");

        List<String> violations = validator.checkAlgorithms("SHA256withDSA", "DSA", 2048);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.contains("key algorithm") && v.contains("disabled"));
    }

    @Test
    public void testDetectsDisabledSignatureSubElement() {
        CryptoPolicyValidator validator = new CryptoPolicyValidator("MD5");

        List<String> violations = validator.checkAlgorithms("MD5withRSA", "RSA", 4096);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.contains("signature algorithm") && v.contains("MD5"));
    }

    @Test
    public void testDetectsDisabledSignatureSubElementInSigningPart() {
        CryptoPolicyValidator validator = new CryptoPolicyValidator("DSA");

        List<String> violations = validator.checkAlgorithms("SHA256withDSA", "DSA", 2048);
        assertThat(violations).anyMatch(v -> v.contains("signature algorithm") && v.contains("DSA"));
    }

    @Test
    public void testDoesNotFalsePositiveOnSubstring() {
        CryptoPolicyValidator validator = new CryptoPolicyValidator("DSA");

        List<String> violations = validator.checkAlgorithms("SHA256withECDSA", "EC", 256);
        assertThat(violations).isEmpty();
    }

    @Test
    public void testAllowsAlgorithmNotInDisabledList() {
        CryptoPolicyValidator validator = new CryptoPolicyValidator("MD2, MD5, DSA");

        List<String> violations = validator.checkAlgorithms("SHA256withRSA", "RSA", 4096);
        assertThat(violations).isEmpty();
    }

    @Test
    public void testCaseInsensitiveMatching() {
        CryptoPolicyValidator validator = new CryptoPolicyValidator("rsa");

        List<String> violations = validator.checkAlgorithms("SHA256withRSA", "RSA", 4096);
        assertThat(violations).isNotEmpty();
    }

    // --- Validation: key size constraints ---

    @Test
    public void testKeySizeConstraintViolated() {
        CryptoPolicyValidator validator = new CryptoPolicyValidator("RSA keySize < 2048");

        List<String> violations = validator.checkAlgorithms("SHA256withRSA", "RSA", 1024);
        assertThat(violations).singleElement().asString().contains("key size 1024");
    }

    @Test
    public void testKeySizeConstraintNotViolated() {
        CryptoPolicyValidator validator = new CryptoPolicyValidator("RSA keySize < 2048");

        assertThat(validator.checkAlgorithms("SHA256withRSA", "RSA", 4096)).isEmpty();
    }

    @Test
    public void testKeySizeConstraintBoundary() {
        CryptoPolicyValidator validator = new CryptoPolicyValidator("RSA keySize < 2048");

        assertThat(validator.checkAlgorithms("SHA256withRSA", "RSA", 2048)).isEmpty();
        assertThat(validator.checkAlgorithms("SHA256withRSA", "RSA", 2047)).hasSize(1);
    }

    @Test
    public void testKeySizeConstraintLessThanOrEqualBoundary() {
        CryptoPolicyValidator validator = new CryptoPolicyValidator("RSA keySize <= 1024");

        assertThat(validator.checkAlgorithms("SHA256withRSA", "RSA", 1024)).hasSize(1);
        assertThat(validator.checkAlgorithms("SHA256withRSA", "RSA", 1025)).isEmpty();
    }

    @Test
    public void testKeySizeConstraintDoesNotApplyToOtherAlgorithms() {
        CryptoPolicyValidator validator = new CryptoPolicyValidator("RSA keySize < 2048");

        assertThat(validator.checkAlgorithms("SHA256withECDSA", "EC", 256)).isEmpty();
    }

    @Test
    public void testKeySizeConstraintSkippedWhenKeySizeNull() {
        CryptoPolicyValidator validator = new CryptoPolicyValidator("RSA keySize < 2048");

        assertThat(validator.checkAlgorithms("SHA256withRSA", "RSA", null)).isEmpty();
    }

    @Test
    public void testValidateSchemePassesForCompliantScheme() {
        CryptoPolicyValidator validator = new CryptoPolicyValidator("MD2, DSA keySize < 1024");

        for (Scheme scheme : CryptoUtil.SUPPORTED_SCHEMES.values()) {
            assertDoesNotThrow(() -> validator.validateScheme(scheme));
        }
    }

    @Test
    public void testValidateSchemeThrowsForDisabledKeyAlgorithm() {
        Scheme scheme = CryptoUtil.SUPPORTED_SCHEMES.values().iterator().next();
        String keyAlgorithm = scheme.keyAlgorithm();

        CryptoPolicyValidator validator = new CryptoPolicyValidator(keyAlgorithm);

        assertThatThrownBy(() -> validator.validateScheme(scheme)).isInstanceOf(ConfigurationException.class)
            .hasMessageContaining(scheme.name()).hasMessageContaining("violates the system crypto policy");
    }

    @Test
    public void testValidateSchemeThrowsForKeySizeViolation() {
        Scheme rsaScheme = CryptoUtil.SUPPORTED_SCHEMES.values().stream()
            .filter(s -> s.keyAlgorithm().equalsIgnoreCase("RSA")).findFirst().orElse(null);

        if (rsaScheme == null) {
            return;
        }

        // Disallow RSA keys smaller than a size larger than our test scheme's key
        int disallowBelow = rsaScheme.keySize().orElse(0) + 1;
        CryptoPolicyValidator validator = new CryptoPolicyValidator("RSA keySize < " + disallowBelow);

        assertThatThrownBy(() -> validator.validateScheme(rsaScheme)).isInstanceOf(
            ConfigurationException.class).hasMessageContaining("violates the system crypto policy");
    }

    @Test
    public void testValidateSchemeThrowsForNullScheme() {
        CryptoPolicyValidator validator = new CryptoPolicyValidator((String) null);

        assertThrows(IllegalArgumentException.class, () -> validator.validateScheme(null));
    }

    @Test
    public void testValidateSchemeFilePassesForCompliantScheme() throws Exception {
        CryptoPolicyValidator validator = new CryptoPolicyValidator("MD2, DSA keySize < 1024");

        SchemeFile schemeFile = createSchemeFile("SHA256withRSA", "RSA");

        assertDoesNotThrow(() -> validator.validateSchemeFile(schemeFile));
    }

    @Test
    public void testValidateSchemeFileThrowsForDisabledAlgorithm() throws Exception {
        CryptoPolicyValidator validator = new CryptoPolicyValidator("RSA");

        SchemeFile schemeFile = createSchemeFile("SHA256withRSA", "RSA");
        assertThatThrownBy(() -> validator.validateSchemeFile(schemeFile)).isInstanceOf(
            ConfigurationException.class).hasMessageContaining("Manifest scheme");
    }

    @Test
    public void testValidateSchemeFileThrowsForNullSchemeFile() {
        CryptoPolicyValidator validator = new CryptoPolicyValidator(null);

        assertThrows(IllegalArgumentException.class, () -> validator.validateSchemeFile(null));
    }

    @Test
    public void testRealisticDefaultPolicy() {
        String policy = "MD2, MD5, SHA1 jdkCA & usage TLSServer, RSA keySize < 2048, " +
            "DSA keySize < 2048, EC keySize < 224";

        CryptoPolicyValidator validator = new CryptoPolicyValidator(policy);

        assertThat(validator.checkAlgorithms("SHA256withRSA", "RSA", 4096)).isEmpty();
        assertThat(validator.checkAlgorithms("MD5withRSA", "RSA", 4096)).isNotEmpty();
        assertThat(validator.checkAlgorithms("SHA256withRSA", "RSA", 1024)).isNotEmpty();
        assertThat(validator.checkAlgorithms("ML-DSA-65", "ML-DSA-65", null)).isEmpty();
        assertThat(validator.checkAlgorithms("SHA1withRSA", "RSA", 4096)).isEmpty();
    }

    private SchemeFile createSchemeFile(String signatureAlgorithm, String keyAlgorithm) throws Exception {

        KeyPair keyPair = CryptoUtil.generateKeyPair(keyAlgorithm, 4096);
        X509Certificate cert = CryptoUtil.generateX509Certificate(keyPair, signatureAlgorithm);

        return new SchemeFile("test_scheme", Base64.getEncoder().encodeToString(cert.getEncoded()),
            signatureAlgorithm, keyAlgorithm);
    }
}
