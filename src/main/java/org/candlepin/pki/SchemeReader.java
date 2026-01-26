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

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.config.ConfigurationException;

import java.security.KeyException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.inject.Inject;



/**
 * Utility class which reads and validates schemes from the configuration
 */
public class SchemeReader {
    private static final Pattern REGEX_SCHEMES_LIST = Pattern.compile(
        "^(?:[a-z0-9][a-z0-9\\-_]*)(?:\\s*,\\s*([a-z0-9][a-z0-9\\-_]*))*$", Pattern.CASE_INSENSITIVE);

    private static final Pattern REGEX_SCHEME_NAME = Pattern.compile("^(?:[a-z0-9][a-z0-9\\-_]*)$",
        Pattern.CASE_INSENSITIVE);

    /** The name of the magic legacy scheme, defining the original, hard-coded crypto scheme */
    public static final String LEGACY_SCHEME = "legacy";

    // Default values for the legacy scheme
    public static final String LEGACY_SCHEME_DEFAULT_CERT = "/etc/candlepin/certs/candlepin-ca.crt";
    public static final String LEGACY_SCHEME_DEFAULT_KEY = "/etc/candlepin/certs/candlepin-ca.key";
    public static final String LEGACY_SCHEME_DEFAULT_SIGNATURE_ALGORITHM = "SHA256withRSA";
    public static final String LEGACY_SCHEME_DEFAULT_KEY_ALGORITHM = "RSA";
    public static final int LEGACY_SCHEME_DEFAULT_KEY_SIZE = 4096;

    /**
     * The default name of the default scheme to use when a context-appropriate scheme cannot be determined.
     * Used when the configuration for the default scheme is absent or otherwise not defined.
     */
    public static final String DEFAULT_DEFAULT_SCHEME = LEGACY_SCHEME;

    private final Configuration config;
    private final PrivateKeyReader keyReader;
    private final CertificateReader certReader;

    @Inject
    public SchemeReader(Configuration config, PrivateKeyReader keyReader, CertificateReader certReader) {
        this.config = Objects.requireNonNull(config);
        this.keyReader = Objects.requireNonNull(keyReader);
        this.certReader = Objects.requireNonNull(certReader);
    }

    /**
     * Attempts to read the file at the given path and load it as an X.509 certificate. If the file does not
     * exist, or otherwise cannot be read, this method throws an exception.
     *
     * @param path
     *  the path to the certificate file to load
     *
     * @throws ConfigurationException
     *  if the certificate at the indicated path cannot be loaded
     *
     * @return
     *  an X509Certificate generated from the specified file path
     */
    private X509Certificate readCertificate(String path) {
        try {
            return this.certReader.read(path);
        }
        catch (CertificateException e) {
            throw new ConfigurationException("Unable to read certificate at path: " + path, e);
        }
    }

    /**
     * Attempts to read the file at the given path and load it as an PEM-encoded private key. If the file does
     * not exist, or otherwise cannot be read, this method throws an exception.
     *
     * @param path
     *  the path to the private key file to load
     *
     * @throws ConfigurationException
     *  if the key at the indicated path cannot be loaded
     *
     * @return
     *  a PrivateKey generated from the specified file path
     */
    private PrivateKey readPrivateKey(String path, String password) {
        try {
            return this.keyReader.read(path, password);
        }
        catch (KeyException e) {
            throw new ConfigurationException("Unable to read private key at path: " + path, e);
        }
    }

    private X509Certificate readSchemeCert(String schemeName) {
        String path = this.config.getString(ConfigProperties.schemeConfig(schemeName,
            ConfigProperties.CRYPTO_SCHEME_CERT));

        return this.readCertificate(path);
    }

    private PrivateKey readSchemeKey(String schemeName) {
        String path = this.config.getString(ConfigProperties.schemeConfig(schemeName,
            ConfigProperties.CRYPTO_SCHEME_KEY));

        Optional<String> password = this.config.getOptionalString(ConfigProperties.schemeConfig(schemeName,
            ConfigProperties.CRYPTO_SCHEME_KEY_PASSWORD));

        return this.readPrivateKey(path, password.orElse(null));
    }

    private String readSchemeSignatureAlgorithm(String schemeName) {
        return this.config.getString(ConfigProperties.schemeConfig(schemeName,
            ConfigProperties.CRYPTO_SCHEME_SIGNATURE_ALGORITHM));
    }

    private String readSchemeKeyAlgorithm(String schemeName) {
        return this.config.getString(ConfigProperties.schemeConfig(schemeName,
            ConfigProperties.CRYPTO_SCHEME_KEY_ALGORITHM));
    }

    private Integer readSchemeKeySize(String schemeName) {
        return this.config.getOptionalInt(ConfigProperties.schemeConfig(schemeName,
            ConfigProperties.CRYPTO_SCHEME_KEY_SIZE))
            .orElse(null);
    }

    private Scheme readLegacyScheme() {
        // This is a bit of a mess. We need to load a scheme that mostly comes from old config values, but
        // could also come from new config values.

        String certPath = this.config.getOptionalString(ConfigProperties.schemeConfig(LEGACY_SCHEME,
            ConfigProperties.CRYPTO_SCHEME_CERT))
            .or(() -> this.config.getOptionalString(ConfigProperties.LEGACY_CA_CERT))
            .orElse(LEGACY_SCHEME_DEFAULT_CERT);

        String keyPath = this.config.getOptionalString(ConfigProperties.schemeConfig(LEGACY_SCHEME,
            ConfigProperties.CRYPTO_SCHEME_KEY))
            .or(() -> this.config.getOptionalString(ConfigProperties.LEGACY_CA_KEY))
            .orElse(LEGACY_SCHEME_DEFAULT_KEY);

        Optional<String> keyPass = this.config.getOptionalString(ConfigProperties.schemeConfig(LEGACY_SCHEME,
            ConfigProperties.CRYPTO_SCHEME_KEY_PASSWORD));

        String signatureAlgorithm = this.config.getOptionalString(ConfigProperties.schemeConfig(LEGACY_SCHEME,
            ConfigProperties.CRYPTO_SCHEME_SIGNATURE_ALGORITHM))
            .orElse(LEGACY_SCHEME_DEFAULT_SIGNATURE_ALGORITHM);

        String keyAlgorithm = this.config.getOptionalString(ConfigProperties.schemeConfig(LEGACY_SCHEME,
            ConfigProperties.CRYPTO_SCHEME_KEY_ALGORITHM))
            .orElse(LEGACY_SCHEME_DEFAULT_KEY_ALGORITHM);

        // Key size is a bit "special": we only want to fall back to the legacy value if and only if we're
        // using the legacy key algorithm. Otherwise we default to null and expect the user to fill it in if
        // required.
        Integer defaultKeySize = keyAlgorithm.equalsIgnoreCase(LEGACY_SCHEME_DEFAULT_KEY_ALGORITHM) ?
            LEGACY_SCHEME_DEFAULT_KEY_SIZE :
            null;

        Integer keySize = this.config.getOptionalInt(ConfigProperties.schemeConfig(LEGACY_SCHEME,
            ConfigProperties.CRYPTO_SCHEME_KEY_SIZE))
            .orElse(defaultKeySize);

        return new Scheme.Builder()
            .setName(LEGACY_SCHEME)
            .setCertificate(this.readCertificate(certPath))
            .setPrivateKey(this.readPrivateKey(keyPath, keyPass.orElse(null)))
            .setSignatureAlgorithm(signatureAlgorithm)
            .setKeyAlgorithm(keyAlgorithm)
            .setKeySize(keySize)
            .build();
    }

    /**
     * Reads a single scheme from the configuration by name. If the scheme is not defined in the
     * configuration or otherwise cannot be loaded, this method throws an exception. This method never returns
     * null.
     *
     * @param schemeName
     *  the name of the scheme to read from the backing configuration; cannot be null or empty
     *
     * @throws IllegalArgumentException
     *  if the given scheme name is null or empty
     *
     * @throws ConfigurationException
     *  if the scheme cannot be read from the configuration
     *
     * @return
     *  a scheme instance
     */
    private Scheme readScheme(String schemeName) {
        if (schemeName == null || schemeName.isBlank()) {
            throw new IllegalArgumentException("schemeName is null or empty");
        }

        try {
            // If we're attempting to load the legacy scheme, jump to the specialized legacy scheme loader
            if (LEGACY_SCHEME.equalsIgnoreCase(schemeName)) {
                return this.readLegacyScheme();
            }

            return new Scheme.Builder()
                .setName(schemeName)
                .setCertificate(this.readSchemeCert(schemeName))
                .setPrivateKey(this.readSchemeKey(schemeName))
                .setSignatureAlgorithm(this.readSchemeSignatureAlgorithm(schemeName))
                .setKeyAlgorithm(this.readSchemeKeyAlgorithm(schemeName))
                .setKeySize(this.readSchemeKeySize(schemeName))
                .build();
        }
        catch (NoSuchElementException e) {
            throw new ConfigurationException("Unable to read scheme: " + schemeName, e);
        }
    }

    /**
     * Fetches the list of schemes from the Candlepin configuration. The list of schemes will be ordered
     * following the declaration order of the schemes within the configuration. If a scheme is listed multiple
     * times in the configuration, it will appear in the resultant list multiple times. If the scheme list is
     * absent from the configuration, or does not follow the scheme declaration format, this method throws an
     * exception. This method will never return null.
     * <p>
     * The scheme declaration is expected to be a comma-delimited list of scheme names, where each scheme
     * name is formatted according to the following regular expression: [A-Za-z0-9][A-Za-z0-9\-_]*
     *
     * @throws ConfigurationException
     *  if the scheme list is absent or malformed
     *
     * @return
     *  the list of schemes declared in the Candlepin configuration in declaration order
     */
    public List<Scheme> readSchemes() {
        try {
            String schemes = this.config.getString(ConfigProperties.CRYPTO_SCHEMES);
            if (!REGEX_SCHEMES_LIST.matcher(schemes).matches()) {
                throw new ConfigurationException("Malformed crypto schemes declaration: " + schemes);
            }

            return Stream.of(schemes.split("\\s*,\\s*"))
                .map(this::readScheme)
                .toList();
        }
        catch (NoSuchElementException e) {
            throw new ConfigurationException(String.format("No crypto schemes defined @ %s",
                ConfigProperties.CRYPTO_SCHEMES), e);
        }
    }

    /**
     * Fetches the default scheme from the Candlepin configuration. If the default scheme name is malformed or
     * cannot be loaded, this method throws an exception. This method will never return null.
     *
     * @throws ConfigurationException
     *  if the default scheme is malformed, or the default scheme cannot be loaded
     *
     * @return
     *  the default crypto scheme
     */
    public Scheme readDefaultScheme() {
        String scheme = this.config.getOptionalString(ConfigProperties.CRYPTO_DEFAULT_SCHEME)
            .orElse(DEFAULT_DEFAULT_SCHEME);

        if (!REGEX_SCHEME_NAME.matcher(scheme).matches()) {
            throw new ConfigurationException("Malformed default crypto scheme declaration: " + scheme);
        }

        return this.readScheme(scheme);
    }

}
