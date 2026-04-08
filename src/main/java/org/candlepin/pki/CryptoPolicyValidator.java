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

import org.candlepin.config.ConfigurationException;
import org.candlepin.sync.SchemeFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;



/**
 * Validates cryptographic schemes against the system's crypto policy as defined by the JVM security
 * property {@code jdk.certpath.disabledAlgorithms}.
 *
 * <p>RHEL crypto policy restrictions are loaded into JVM security properties but are not enforced by
 * Java during primitive cryptographic operations (key/cert generation) or when using BouncyCastle as
 * the TLS provider. This class reads those restrictions and enforces them for Candlepin's configured
 * schemes.
 *
 * <p>The parser handles the JDK disabled algorithms format:
 * <ul>
 *   <li>Plain algorithm name: {@code MD2} (fully disabled)</li>
 *   <li>Key size constraint: {@code RSA keySize < 2048}</li>
 *   <li>Conditional constraints ({@code jdkCA}, {@code denyAfter}, {@code usage}): ignored, as they
 *       are certpath/TLS-context-specific and not relevant to primitive operations</li>
 *   <li>{@code include <property>}: resolved recursively</li>
 * </ul>
 */
@Singleton
public class CryptoPolicyValidator {
    private static final Logger log = LoggerFactory.getLogger(CryptoPolicyValidator.class);

    static final String CERTPATH_DISABLED_ALGORITHMS_PROPERTY = "jdk.certpath.disabledAlgorithms";

    private static final Pattern KEY_SIZE_PATTERN = Pattern.compile(
        "keySize\\s*(<=|<|==|!=|>=|>)\\s*(\\d+)", Pattern.CASE_INSENSITIVE);

    private final List<DisabledAlgorithm> disabledAlgorithms;

    @Inject
    public CryptoPolicyValidator() {
        this(Security.getProperty(CERTPATH_DISABLED_ALGORITHMS_PROPERTY));
    }

    /**
     * Constructs a validator from a raw disabled algorithms string (the format used by
     * {@code jdk.certpath.disabledAlgorithms}). Useful for testing with controlled policy values.
     *
     * @param disabledAlgorithmsProperty
     *  the raw comma-separated disabled algorithms string, or null/empty for no restrictions
     */
    public CryptoPolicyValidator(String disabledAlgorithmsProperty) {
        this.disabledAlgorithms = Collections.unmodifiableList(parse(disabledAlgorithmsProperty));

        if (this.disabledAlgorithms.isEmpty()) {
            log.info("No crypto policy restrictions found in {}", CERTPATH_DISABLED_ALGORITHMS_PROPERTY);
        }
        else {
            log.info("Loaded {} crypto policy restriction(s) from {}: {}",
                this.disabledAlgorithms.size(), CERTPATH_DISABLED_ALGORITHMS_PROPERTY,
                this.disabledAlgorithms);
        }
    }

    /**
     * Validates the given scheme against the system crypto policy. Throws a
     * {@link ConfigurationException} if the scheme's algorithms or key size violate the policy.
     *
     * @param scheme
     *  the scheme to validate
     *
     * @throws ConfigurationException
     *  if the scheme violates the crypto policy
     */
    public void validateScheme(Scheme scheme) {
        if (scheme == null) {
            throw new IllegalArgumentException("scheme is null");
        }

        List<String> violations = checkAlgorithms(
            scheme.signatureAlgorithm(),
            scheme.keyAlgorithm(),
            scheme.keySize().orElse(null));

        if (!violations.isEmpty()) {
            throw new ConfigurationException(String.format(
                "Scheme \"%s\" violates the system crypto policy (%s): %s",
                scheme.name(), CERTPATH_DISABLED_ALGORITHMS_PROPERTY, String.join("; ", violations)));
        }
    }

    /**
     * Validates algorithms from a manifest's {@link SchemeFile} against the system crypto policy.
     * The key size is extracted from the embedded X.509 certificate if present.
     *
     * @param schemeFile
     *  the scheme file from a manifest to validate
     *
     * @throws ConfigurationException
     *  if the scheme file's algorithms violate the crypto policy
     */
    public void validateSchemeFile(SchemeFile schemeFile) {
        if (schemeFile == null) {
            throw new IllegalArgumentException("schemeFile is null");
        }

        Integer keySize = extractKeySizeFromCertificate(schemeFile.certificate());

        List<String> violations = checkAlgorithms(
            schemeFile.signatureAlgorithm(),
            schemeFile.keyAlgorithm(),
            keySize);

        if (!violations.isEmpty()) {
            throw new ConfigurationException(String.format(
                "Manifest scheme \"%s\" violates the system crypto policy (%s): %s",
                schemeFile.name(), CERTPATH_DISABLED_ALGORITHMS_PROPERTY,
                String.join("; ", violations)));
        }
    }

    /**
     * Checks the given algorithms and key size against the loaded crypto policy restrictions, returning
     * a list of human-readable violation descriptions. An empty list means no violations.
     *
     * @param signatureAlgorithm
     *  the signature algorithm (e.g. "SHA256withRSA")
     *
     * @param keyAlgorithm
     *  the key algorithm (e.g. "RSA")
     *
     * @param keySize
     *  the key size in bits, or null if not specified
     *
     * @return
     *  a list of violation descriptions, empty if compliant
     */
    List<String> checkAlgorithms(String signatureAlgorithm, String keyAlgorithm, Integer keySize) {
        List<String> violations = new ArrayList<>();

        for (DisabledAlgorithm disabled : this.disabledAlgorithms) {
            if (disabled.keySizeConstraint() != null) {
                if (matchesAlgorithmName(disabled.name(), keyAlgorithm) && keySize != null) {
                    if (disabled.keySizeConstraint().isViolatedBy(keySize)) {
                        violations.add(String.format(
                            "key algorithm \"%s\" with key size %d violates constraint \"%s keySize %s %d\"",
                            keyAlgorithm, keySize, disabled.name(),
                            disabled.keySizeConstraint().operator(), disabled.keySizeConstraint().value()));
                    }
                }
            }
            else {
                if (matchesAlgorithmName(disabled.name(), keyAlgorithm)) {
                    violations.add(String.format(
                        "key algorithm \"%s\" is disabled by crypto policy (disabled: \"%s\")",
                        keyAlgorithm, disabled.name()));
                }

                if (signatureAlgorithm != null &&
                    isSignatureAlgorithmAffected(disabled.name(), signatureAlgorithm)) {
                    violations.add(String.format(
                        "signature algorithm \"%s\" contains disabled sub-element \"%s\"",
                        signatureAlgorithm, disabled.name()));
                }
            }
        }

        return violations;
    }

    /**
     * Returns the parsed list of disabled algorithm entries. Visible for testing.
     *
     * @return an unmodifiable list of disabled algorithm entries
     */
    List<DisabledAlgorithm> getDisabledAlgorithms() {
        return this.disabledAlgorithms;
    }

    /**
     * Parses the {@code jdk.certpath.disabledAlgorithms} property value into structured entries.
     * Entries with non-keySize constraints (jdkCA, denyAfter, usage) are skipped, as they are
     * context-specific and not applicable to primitive crypto operations.
     */
    private static List<DisabledAlgorithm> parse(String property) {
        if (property == null || property.isBlank()) {
            return List.of();
        }

        List<DisabledAlgorithm> result = new ArrayList<>();
        String[] entries = property.split(",");

        for (String entry : entries) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            // Handle "include <property>" directives by resolving them recursively
            if (trimmed.toLowerCase(Locale.ROOT).startsWith("include ")) {
                String includedProperty = trimmed.substring("include ".length()).trim();
                String includedValue = Security.getProperty(includedProperty);
                result.addAll(parse(includedValue));
                continue;
            }

            // Skip entries with conditional constraints that don't apply to primitive operations
            String lowerTrimmed = trimmed.toLowerCase(Locale.ROOT);
            if (lowerTrimmed.contains("jdkca") || lowerTrimmed.contains("denyafter") ||
                lowerTrimmed.contains("usage ")) {
                log.debug("Skipping context-specific crypto policy entry: {}", trimmed);
                continue;
            }

            Matcher keySizeMatcher = KEY_SIZE_PATTERN.matcher(trimmed);
            if (keySizeMatcher.find()) {
                String algorithmName = trimmed.substring(0, keySizeMatcher.start()).trim();
                String operator = keySizeMatcher.group(1);
                int value = Integer.parseInt(keySizeMatcher.group(2));
                result.add(new DisabledAlgorithm(algorithmName, new KeySizeConstraint(operator, value)));
            }
            else {
                // Plain disabled algorithm with no constraints
                result.add(new DisabledAlgorithm(trimmed, null));
            }
        }

        return result;
    }

    /**
     * Checks if a disabled algorithm name matches a given algorithm name (case-insensitive, exact match).
     */
    private static boolean matchesAlgorithmName(String disabledName, String algorithmName) {
        return disabledName.equalsIgnoreCase(algorithmName);
    }

    /**
     * Checks if a disabled algorithm name affects a composite signature algorithm using JDK sub-element
     * matching rules. The signature algorithm is split on "with" (case-insensitive) into its hash and
     * signing components. A match occurs if either component equals the disabled name (case-insensitive).
     *
     * <p>For example, if "SHA1" is disabled, then "SHA1withRSA" and "SHA1withECDSA" are both affected.
     * If "DSA" is disabled, then "SHA1withDSA" is affected, but "SHA1withECDSA" is not (because "DSA"
     * is not equal to "ECDSA").
     */
    private static boolean isSignatureAlgorithmAffected(String disabledName, String signatureAlgorithm) {
        // Direct match on the full algorithm name
        if (disabledName.equalsIgnoreCase(signatureAlgorithm)) {
            return true;
        }

        // Sub-element matching: split on "with" (case-insensitive)
        String lower = signatureAlgorithm.toLowerCase(Locale.ROOT);
        int withIdx = lower.indexOf("with");
        if (withIdx > 0) {
            String hashPart = signatureAlgorithm.substring(0, withIdx);
            String sigPart = signatureAlgorithm.substring(withIdx + 4);
            return disabledName.equalsIgnoreCase(hashPart) || disabledName.equalsIgnoreCase(sigPart);
        }

        return false;
    }

    /**
     * Attempts to extract the key size (in bits) from a Base64-encoded X.509 certificate. Returns null
     * if the certificate cannot be parsed or the key type is unrecognized.
     */
    private static Integer extractKeySizeFromCertificate(String base64Certificate) {
        if (base64Certificate == null || base64Certificate.isBlank()) {
            return null;
        }

        try {
            byte[] certBytes = Base64.getDecoder().decode(base64Certificate);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(
                new ByteArrayInputStream(certBytes));

            java.security.PublicKey publicKey = cert.getPublicKey();
            if (publicKey instanceof RSAPublicKey rsaKey) {
                return rsaKey.getModulus().bitLength();
            }
            else if (publicKey instanceof ECPublicKey ecKey) {
                return ecKey.getParams().getOrder().bitLength();
            }
            else if (publicKey instanceof DSAPublicKey dsaKey) {
                return dsaKey.getParams().getP().bitLength();
            }

            return null;
        }
        catch (CertificateException | IllegalArgumentException e) {
            log.warn("Unable to extract key size from manifest certificate", e);
            return null;
        }
    }

    /**
     * Represents a single disabled algorithm entry parsed from the crypto policy.
     *
     * @param name
     *  the algorithm name (e.g. "RSA", "MD2")
     * @param keySizeConstraint
     *  the optional key size constraint, or null if the algorithm is fully disabled
     */
    record DisabledAlgorithm(String name, KeySizeConstraint keySizeConstraint) {
        @Override
        public String toString() {
            if (keySizeConstraint != null) {
                return String.format("%s keySize %s %d", name,
                    keySizeConstraint.operator(), keySizeConstraint.value());
            }
            return name;
        }
    }

    /**
     * Represents a key size constraint from the crypto policy (e.g. "keySize < 2048").
     *
     * @param operator
     *  the comparison operator as a string
     * @param value
     *  the key size threshold in bits
     */
    record KeySizeConstraint(String operator, int value) {
        boolean isViolatedBy(int keySize) {
            return switch (operator) {
                case "<" -> keySize < value;
                case "<=" -> keySize <= value;
                case "==" -> keySize == value;
                case "!=" -> keySize != value;
                case ">=" -> keySize >= value;
                case ">" -> keySize > value;
                default -> false;
            };
        }
    }
}
