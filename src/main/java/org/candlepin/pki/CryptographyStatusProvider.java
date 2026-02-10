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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Provides cryptographic scheme metadata for status reporting.
 * Unlike {@link SchemeReader}, this class only reads configuration metadata
 * without attempting to load certificates or private keys.
 */
@Singleton
public class CryptographyStatusProvider {

    private final Configuration config;

    @Inject
    public CryptographyStatusProvider(Configuration config) {
        this.config = Objects.requireNonNull(config);
    }

    /**
     * Returns an ordered list of scheme metadata for all configured cryptographic schemes.
     * Schemes earlier in the list are preferred by the server.
     *
     * @return an unmodifiable list of scheme metadata, or an empty list if no schemes are configured
     */
    public List<SchemeMetadata> getSupportedSchemes() {
        List<String> schemeNames;
        try {
            schemeNames = this.config.getList(ConfigProperties.CRYPTO_SCHEMES);
        }
        catch (NoSuchElementException e) {
            return Collections.emptyList();
        }

        if (schemeNames == null || schemeNames.isEmpty()) {
            return Collections.emptyList();
        }

        List<SchemeMetadata> schemes = new ArrayList<>();
        for (String schemeName : schemeNames) {
            SchemeMetadata metadata = this.readSchemeMetadata(schemeName);
            if (metadata != null) {
                schemes.add(metadata);
            }
        }

        return Collections.unmodifiableList(schemes);
    }

    /**
     * Returns the name of the default cryptographic scheme used for legacy clients.
     *
     * @return the name of the default scheme, or null if not configured
     */
    public String getDefaultSchemeName() {
        try {
            return this.config.getString(ConfigProperties.CRYPTO_DEFAULT_SCHEME);
        }
        catch (NoSuchElementException e) {
            return null;
        }
    }

    /**
     * Returns true if any cryptographic schemes are configured.
     *
     * @return true if schemes are configured, false otherwise
     */
    public boolean hasSchemes() {
        return !this.getSupportedSchemes().isEmpty();
    }

    /**
     * Reads scheme metadata from configuration without loading certificates or keys.
     *
     * @param schemeName the name of the scheme to read
     * @return the scheme metadata, or null if the scheme name is invalid
     */
    private SchemeMetadata readSchemeMetadata(String schemeName) {
        if (schemeName == null || schemeName.isBlank()) {
            return null;
        }

        String signatureAlgorithm = this.getOptionalString(
            ConfigProperties.schemeConfig(schemeName, ConfigProperties.CRYPTO_SCHEME_SIGNATURE_ALGORITHM));

        String keyAlgorithm = this.getOptionalString(
            ConfigProperties.schemeConfig(schemeName, ConfigProperties.CRYPTO_SCHEME_KEY_ALGORITHM));

        Integer keySize = this.getOptionalInt(
            ConfigProperties.schemeConfig(schemeName, ConfigProperties.CRYPTO_SCHEME_KEY_SIZE));

        return new SchemeMetadata(schemeName, signatureAlgorithm, keyAlgorithm, keySize);
    }

    private String getOptionalString(String key) {
        try {
            return this.config.getString(key);
        }
        catch (NoSuchElementException e) {
            return null;
        }
    }

    private Integer getOptionalInt(String key) {
        try {
            return this.config.getInt(key);
        }
        catch (NoSuchElementException e) {
            return null;
        }
    }

    /**
     * Immutable metadata about a cryptographic scheme for status reporting.
     *
     * @param name               the name of the cryptographic scheme
     * @param signatureAlgorithm the algorithm used to sign certificates and payloads
     * @param keyAlgorithm       the algorithm used to generate key pairs
     * @param keySize            the size of keys generated under this scheme;
     *                           may be null for algorithms with fixed key sizes
     */
    public record SchemeMetadata(String name, String signatureAlgorithm, String keyAlgorithm,
                                 Integer keySize) {

    }
}
