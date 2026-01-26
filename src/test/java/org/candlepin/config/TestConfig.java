/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.config;

import org.candlepin.pki.Scheme;
import org.candlepin.test.CryptoUtil;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyException;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// TODO: FIXME: This sucks. A lot. TestConfig, DevConfig, and all the other nonsense we do to have mutable
// test configuration are about as poorly implemented as can possibly be. It's resulted in tons of duplicate
// code and even more goofy hacks and workarounds as a result. None of this should exist and, at most,
// DevConfig should be an extension of Configuration that defines the mutability functionality, and then
// TestConfig (or whatever) becomes little more than a map subclass that implements DevConfig. Even if we were
// to keep this general layout, why are DevConfig and the defaults provided here separate? What value is being
// added by further separating TestConfig and DevConfig? It makes no sense.
//
// At some point the entire configuration package should be overhauled to be less *this* and more usable in a
// way that isn't clunky and constantly in the way. As it is, it's rubbish and a maintenance disaster.

public final class TestConfig {

    public static final int BULK_SET_CONSUMER_ENV_MAX_CONSUMER_LIMIT = 100;
    public static final int BULK_SET_CONSUMER_ENV_MAX_ENV_LIMIT = 10;

    private static final DevConfig DEFAULT_CONFIG;
    private static final String LEGACY_CA_KEY_PASSWORD = "password";

    static {
        try {
            DevConfig config = new DevConfig(ConfigProperties.DEFAULT_PROPERTIES);

            Map<String, Scheme> schemes = CryptoUtil.generateSupportedSchemes()
                .collect(Collectors.toMap(Scheme::name, Function.identity()));

            String upstreamCertRepo = TestConfig.class.getClassLoader().getResource("certs/upstream")
                .toURI().getPath();

            // Generate configuration for each scheme
            for (Scheme scheme : schemes.values()) {
                CryptoUtil.generateSchemeConfiguration(config, scheme, null);
            }

            // Write the standard crypto config
            config.setProperty(ConfigProperties.CRYPTO_SCHEMES, String.join(", ", schemes.keySet()));
            config.setProperty(ConfigProperties.CRYPTO_UPSTREAM_CERT_REPO, upstreamCertRepo);

            // Write legacy configuration to ensure we don't break tests that are looking specifically for it
            // before we have a chance to update them.
            String legacyCertPath = TestConfig.class.getResource("candlepin-ca.crt").toURI().getPath();
            String legacyKeyPath = TestConfig.class.getResource("candlepin-ca.key").toURI().getPath();

            config.setProperty(ConfigProperties.LEGACY_CA_CERT, legacyCertPath);
            config.setProperty(ConfigProperties.LEGACY_CA_KEY, legacyKeyPath);
            config.setProperty(ConfigProperties.LEGACY_CA_KEY_PASSWORD, LEGACY_CA_KEY_PASSWORD);
            config.setProperty(ConfigProperties.LEGACY_CA_CERT_UPSTREAM, upstreamCertRepo);

            // Write other misc configurations...
            config.setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp");
            config.setProperty(ConfigProperties.ACTIVEMQ_LARGE_MSG_SIZE, "0");
            config.setProperty(ConfigProperties.HIDDEN_RESOURCES, "");
            config.setProperty(DatabaseConfigFactory.IN_OPERATOR_BLOCK_SIZE, "10");
            config.setProperty(DatabaseConfigFactory.CASE_OPERATOR_BLOCK_SIZE, "10");
            config.setProperty(DatabaseConfigFactory.BATCH_BLOCK_SIZE, "10");
            config.setProperty(DatabaseConfigFactory.QUERY_PARAMETER_LIMIT, "32000");
            config.setProperty(ConfigProperties.CACHE_ANON_CERT_CONTENT_TTL, "120000");
            config.setProperty(ConfigProperties.CACHE_ANON_CERT_CONTENT_MAX_ENTRIES, "10000");
            config.setProperty(ConfigProperties.PAGING_DEFAULT_PAGE_SIZE, "100");
            config.setProperty(ConfigProperties.PAGING_MAX_PAGE_SIZE, "10000");
            config.setProperty(ConfigProperties.BULK_SET_CONSUMER_ENV_MAX_CONSUMER_LIMIT,
                String.valueOf(BULK_SET_CONSUMER_ENV_MAX_CONSUMER_LIMIT));
            config.setProperty(ConfigProperties.BULK_SET_CONSUMER_ENV_MAX_ENV_LIMIT,
                String.valueOf(BULK_SET_CONSUMER_ENV_MAX_ENV_LIMIT));
            config.setProperty(ConfigProperties.SCA_X509_CERT_EXPIRY_THRESHOLD, "5");

            // Assign the default config. This is somewhat pointless because we can't make this immutable, but
            // whatever.
            DEFAULT_CONFIG = config;
        }
        catch (KeyException | IOException | URISyntaxException e) {
            throw new RuntimeException("Unable to generate standardized test crypto configuration", e);
        }
    }

    private TestConfig() {
        throw new UnsupportedOperationException();
    }

    public static DevConfig custom(Map<String, String> config) {
        return new DevConfig(config);
    }

    public static DevConfig defaults() {
        return new DevConfig()
            .setPropertiesFrom(DEFAULT_CONFIG);
    }

}
