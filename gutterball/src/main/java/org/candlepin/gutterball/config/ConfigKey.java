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

/**
 * enum of configuration keys?
 */
public enum ConfigKey {
    AMQP_CONNECT_STRING("gutterball.amqp.connect"),
    AMQP_KEYSTORE("gutterball.amqp.keystore"),
    AMQP_KEYSTORE_PASSWORD("gutterball.amqp.keystore_password"),
    AMQP_TRUSTSTORE("gutterball.amqp.truststore"),
    AMQP_TRUSTSTORE_PASSWORD("gutterball.amqp.truststore_password"),

    MONGODB_HOST("gutterball.mongodb.host"),
    MONGODB_PORT("gutterball.mongodb.port"),
    MONGODB_DATABASE("gutterball.mongodb.database"),
    MONGODB_USERNAME("gutterball.mongodb.username"),
    MONGODB_PASSWORD("gutterball.mongodb.password");

    private String key;
    private ConfigKey(String key) {
        this.key = key;
    }

    public String toString() {
        return key;
    }
}
