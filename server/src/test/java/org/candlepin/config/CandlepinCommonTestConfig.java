/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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

import org.candlepin.common.config.MapConfiguration;

import java.net.URISyntaxException;

/**
 * CandlepinCommonTestConfig
 */
public class CandlepinCommonTestConfig extends MapConfiguration {

    public CandlepinCommonTestConfig() {
        super(ConfigProperties.DEFAULT_PROPERTIES);
        loadProperties();
    }

    protected void loadProperties() {
        // set ssl cert/key path for testing
        try {
            String cert = getClass().getResource("candlepin-ca.crt").toURI().getPath();
            String key = getClass().getResource("candlepin-ca.key").toURI().getPath();
            String certUpstream = getClass().getClassLoader().getResource("certs/upstream").toURI().getPath();

            setProperty(ConfigProperties.CA_CERT, cert);
            setProperty(ConfigProperties.CA_CERT_UPSTREAM, certUpstream);
            setProperty(ConfigProperties.CA_KEY, key);
            setProperty(ConfigProperties.CA_KEY_PASSWORD, "password");
            setProperty(ConfigProperties.SYNC_WORK_DIR, "/tmp");
            setProperty(ConfigProperties.ACTIVEMQ_LARGE_MSG_SIZE, "0");

            setProperty(DatabaseConfigFactory.IN_OPERATOR_BLOCK_SIZE, "10");
            setProperty(DatabaseConfigFactory.CASE_OPERATOR_BLOCK_SIZE, "10");
            setProperty(DatabaseConfigFactory.BATCH_BLOCK_SIZE, "10");
            setProperty(DatabaseConfigFactory.QUERY_PARAMETER_LIMIT, "32000");
        }
        catch (URISyntaxException e) {
            throw new RuntimeException("Error loading cert/key resources!", e);
        }
    }
}
