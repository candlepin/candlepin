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
package org.candlepin.gutterball.config;

import java.util.HashMap;
import java.util.Map;

/**
 * ConfigProperties
 */
public class ConfigProperties {
    private ConfigProperties() {
    }

    public static final String DEFAULT_CONFIG_FILE = "/etc/gutterball/gutterball.conf";
    public static final String AMQP_CONNECT_STRING = "gutterball.amqp.connect";
    public static final String AMQP_CONNECTION_RETRY_ATTEMPTS = "gutterball.amqp.connection.retry_attempts";
    public static final String AMQP_CONNECTION_RETRY_INTERVAL = "gutterball.amqp.connection.retry_interval";
    public static final String AMQP_KEYSTORE = "gutterball.amqp.keystore";
    public static final String AMQP_KEYSTORE_PASSWORD = "gutterball.amqp.keystore_password";
    public static final String AMQP_TRUSTSTORE = "gutterball.amqp.truststore";
    public static final String AMQP_TRUSTSTORE_PASSWORD = "gutterball.amqp.truststore_password";

    public static final String JPA_DRIVER = "jpa.config.hibernate.connection.driver_class";
    public static final String JPA_CONNECTION_URL = "jpa.config.hibernate.connection.url";
    public static final String JPA_DIALECT = "jpa.config.hibernate.dialect";
    public static final String JPA_DB_USERNAME = "jpa.config.hibernate.connection.username";
    public static final String JPA_DB_PASSWORD = "jpa.config.hibernate.connection.password";

    public static final String PREFIX_APIURL = "gutterball.export.prefix.apiurl";

    public static final String DATA_CLEANUP_TASK_ENABLED = "gutterball.tasks.data_cleanup.enabled";
    public static final String DATA_CLEANUP_TASK_SCHEDULE = "gutterball.tasks.data_cleanup.schedule";
    public static final String DATA_CLEANUP_TASK_MAX_EVENT_AGE =
        "gutterball.tasks.data_cleanup.max_data_age";
    public static final String DATA_CLEANUP_TASK_MAX_EVENT_AGE_UNIT =
        "gutterball.tasks.data_cleanup.max_data_age_units";

    // Authentication
    public static final String OAUTH_AUTHENTICATION = "gutterball.auth.oauth.enable";
    public static final String OAUTH_CONSUMER_REGEX =
        "\\Agutterball\\.auth\\.oauth\\.consumer\\.([^.]+)\\.secret\\z";

    public static final Map<String, String> DEFAULT_PROPERTIES =
        new HashMap<String, String>() {

            private static final long serialVersionUID = 1L;
            {
                // AMQP (Qpid) defaults
                this.put(AMQP_CONNECT_STRING, "amqp://guest:guest@localhost/test?brokerlist=" +
                        "'tcp://localhost:5671?ssl='true'&ssl_cert_alias='gutterball''");
                this.put(AMQP_CONNECTION_RETRY_INTERVAL, "10"); // Every 10 seconds
                this.put(AMQP_CONNECTION_RETRY_ATTEMPTS, "12"); // Try for 2 minutes (10s * 12)
                this.put(AMQP_KEYSTORE, "/etc/gutterball/certs/amqp/gutterball.jks");
                this.put(AMQP_KEYSTORE_PASSWORD, "password");
                this.put(AMQP_TRUSTSTORE,
                        "/etc/gutterball/certs/amqp/gutterball.truststore");
                this.put(AMQP_TRUSTSTORE_PASSWORD, "password");

                // JPA/hibernate Configuration
                this.put(JPA_DRIVER, "org.postgresql.Driver");
                this.put(JPA_CONNECTION_URL, "jdbc:postgresql:gutterball");
                this.put(JPA_DIALECT, "org.hibernate.dialect.PostgreSQLDialect");
                this.put(JPA_DB_USERNAME, "gutterball");
                this.put(JPA_DB_PASSWORD, "");


                this.put(DATA_CLEANUP_TASK_ENABLED, "false");
                this.put(DATA_CLEANUP_TASK_SCHEDULE, "0 3 * * *");
                this.put(DATA_CLEANUP_TASK_MAX_EVENT_AGE, "30");
                this.put(DATA_CLEANUP_TASK_MAX_EVENT_AGE_UNIT, "days");
            }
        };
}
