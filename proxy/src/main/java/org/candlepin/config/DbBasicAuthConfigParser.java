/**
 * Copyright (c) 2009 Red Hat, Inc.
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

import java.util.HashSet;
import java.util.Set;


/**
 * DatabaseBasicAuthConfiguration
 * @version $Rev$
 */
class DbBasicAuthConfigParser extends EncryptedValueConfigurationParser {
    /**
     * @param config
     */
    public DbBasicAuthConfigParser(Config config) {
        super(config);
    }

    /** Basic auth configuration prefix */
    public static final String BASIC_AUTH_CONFIG_PREFIX = "basic.auth.config";

    /** database connection url */
    public static final String DB_URL_CONFIG = "database.connection.url";
    /** Comment for <code>DB_USERNAME_CONFIG</code> */
    public static final String DB_USERNAME_CONFIG = "database.connection.username";
    /** Comment for <code>DB_PASSWORD_CONFIG</code> */
    public static final String DB_PASSWORD_CONFIG = "database.connection.password";
    /** Comment for <code>DB_DRIVER_CONFIG</code> */
    public static final String DB_DRIVER_CONFIG = "database.connection.driver";
    /** Comment for <code>USER_QUERY_CONFIG</code> */
    public static final String USER_QUERY_CONFIG = "database.query";
    /** Comment for <code>PASSWORD_COLUMN_CONFIG</code> */
    public static final String PASSWORD_COLUMN_CONFIG = "database.password.column";

    public String getPrefix() {
        return BASIC_AUTH_CONFIG_PREFIX;
    }

    /* returns a list of config keys to check if they are encrypted */
    public Set<String> encryptedConfigKeys() {
        Set<String> encKeys = new HashSet<String>();
        encKeys.add("database.connection.password");
        return encKeys;
    }
}
