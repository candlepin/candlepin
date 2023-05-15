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

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

public final class TestConfig {

    private TestConfig() {
        throw new UnsupportedOperationException();
    }

    public static DevConfig custom(Map<String, String> config) {
        return new DevConfig(config);
    }

    public static DevConfig defaults() {
        return new DevConfig(loadProperties());
    }

    private static HashMap<String, String> loadProperties() {
        // set ssl cert/key path for testing
        HashMap<String, String> defaults = new HashMap<>(ConfigProperties.DEFAULT_PROPERTIES);
        try {
            String cert = TestConfig.class.getResource("candlepin-ca.crt").toURI().getPath();
            String key = TestConfig.class.getResource("candlepin-ca.key").toURI().getPath();
            String certUpstream = TestConfig.class.getClassLoader()
                .getResource("certs/upstream").toURI().getPath();
            defaults.put(ConfigProperties.CA_CERT, cert);
            defaults.put(ConfigProperties.CA_CERT_UPSTREAM, certUpstream);
            defaults.put(ConfigProperties.CA_KEY, key);
        }
        catch (URISyntaxException e) {
            throw new RuntimeException("Error loading cert/key resources!", e);
        }

        defaults.put(ConfigProperties.CA_KEY_PASSWORD, "password");
        defaults.put(ConfigProperties.SYNC_WORK_DIR, "/tmp");
        defaults.put(ConfigProperties.ACTIVEMQ_LARGE_MSG_SIZE, "0");
        defaults.put(ConfigProperties.HIDDEN_RESOURCES, "");
        defaults.put(DatabaseConfigFactory.IN_OPERATOR_BLOCK_SIZE, "10");
        defaults.put(DatabaseConfigFactory.CASE_OPERATOR_BLOCK_SIZE, "10");
        defaults.put(DatabaseConfigFactory.BATCH_BLOCK_SIZE, "10");
        defaults.put(DatabaseConfigFactory.QUERY_PARAMETER_LIMIT, "32000");

        return defaults;
    }

}
