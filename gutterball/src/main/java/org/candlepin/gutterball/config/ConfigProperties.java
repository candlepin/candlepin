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
    public static final String AMQP_CONNECT_STRING = "gutterball.amqp.connect";
    public static final String AMQP_KEYSTORE = "gutterball.amqp.keystore";
    public static final String AMQP_KEYSTORE_PASSWORD = "gutterball.amqp.keystore_password";
    public static final String AMQP_TRUSTSTORE = "gutterball.amqp.truststore";
    public static final String AMQP_TRUSTSTORE_PASSWORD = "gutterball.amqp.truststore_password";

    public static final String MONGODB_HOST = "gutterball.mongodb.host";
    public static final String MONGODB_PORT = "gutterball.mongodb.port";
    public static final String MONGODB_DATABASE = "gutterball.mongodb.database";
    public static final String MONGODB_USERNAME = "gutterball.mongodb.username";
    public static final String MONGODB_PASSWORD = "gutterball.mongodb.password";

    public static final Map<String, String> DEFAULT_PROPERTIES =
        new HashMap<String, String>() {

            private static final long serialVersionUID = 1L;
            {
                this.put(AMQP_CONNECT_STRING, "https://localhost");
                this.put(
                        AMQP_CONNECT_STRING,
                        "amqp://guest:guest@localhost/test?brokerlist='tcp://localhost:5671?ssl='true'&ssl_cert_alias='gutterball'");
                this.put(AMQP_KEYSTORE,
                        "/etc/gutterball/certs/amqp/gutterball.jks");
                this.put(AMQP_KEYSTORE_PASSWORD, "password");
                this.put(AMQP_TRUSTSTORE,
                        "/etc/gutterball/certs/amqp/gutterball.truststore");
                this.put(AMQP_TRUSTSTORE_PASSWORD, "password");
            }
        };
}
