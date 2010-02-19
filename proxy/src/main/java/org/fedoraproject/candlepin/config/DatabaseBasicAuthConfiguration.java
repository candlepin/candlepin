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
package org.fedoraproject.candlepin.config;

import java.util.Map;
import java.util.Properties;

/**
 * DatabaseBasicAuthConfiguration
 * @version $Rev$
 */
public class DatabaseBasicAuthConfiguration {
    /** Basic auth configuration prefix */
    public static final String BASIC_AUTH_CONFIG_PREFIX = "basic.auth.config";
    /** Length of the <code>BASIC_AUTH_CONFIG_PREFIX</code> */
    public static final int PREFIX_LENGTH = BASIC_AUTH_CONFIG_PREFIX.length();
    
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
    
    
    /**
     * Converts the given Map into a Properties object. 
     * @param inputConfiguration Configuration to be converted.
     * @return config as a Properties file
     */
    public Properties parseConfig(Map<String, String> inputConfiguration) {
        
        Properties toReturn = new Properties();
        toReturn.putAll(stripPrefixFromConfigKeys(inputConfiguration));
        return toReturn;
    }

    /**
     * Return a copy of the input without the prefixes.
     * @param inputConfiguration Configuration to be converted.
     * @return config as a Properties object without the prefixes.
     */
    public Properties stripPrefixFromConfigKeys(Map<String, String> inputConfiguration) {
        Properties toReturn = new Properties();
        
        for (String key : inputConfiguration.keySet()) {
            toReturn.put(key.substring(PREFIX_LENGTH + 1), inputConfiguration.get(key));
        }
        return toReturn;
    }    
}
