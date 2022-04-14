/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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

package org.candlepin.spec.bootstrap.client;

/**
 * Specifies all configuration keys used by the spec tests
 */
public enum ConfigKey {
    HOST("spec.test.client.host"),
    PORT("spec.test.client.port"),
    PREFIX("spec.test.client.prefix"),
    USERNAME("spec.test.client.username"),
    PASSWORD("spec.test.client.password"),
    DEBUG("spec.test.client.debug"),
    CONNECT_TIMEOUT("spec.test.client.timeout.connect.sec"),
    READ_TIMEOUT("spec.test.client.timeout.read.sec"),
    WRITE_TIMEOUT("spec.test.client.timeout.write.sec");

    private final String key;

    ConfigKey(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

}
