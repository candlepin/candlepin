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
package org.fedoraproject.candlepin.configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class JPAConfiguration {
    final public static String JPA_CONFIG_PREFIX = "jpa.config";
    final public static int PREFIX_LENGTH = JPA_CONFIG_PREFIX.length();
    
    final public static String URL_CONFIG = "hibernate.connection.url";
    final public static String USER_CONFIG = "hibernate.connection.username";
    final public static String PASSWORD_CONFIG = "hibernate.connection.password";
    
    
    public static Properties parseConfig(Map<String, String> inputConfiguration) {
        Properties toReturn = stripPrefixFromConfigKeys(inputConfiguration);
        
        if (!toReturn.containsKey(URL_CONFIG)) {
            defaultConfigurationSettings().get(URL_CONFIG);
        }
        
        if (!toReturn.containsKey(USER_CONFIG)) {
            defaultConfigurationSettings().get(USER_CONFIG);
        }
        
        if (!toReturn.containsKey(PASSWORD_CONFIG)) {
            defaultConfigurationSettings().get(PASSWORD_CONFIG);
        }
        
        toReturn.putAll(immutableConfigurationSettings());
        
        return toReturn;
    }

    public static Properties stripPrefixFromConfigKeys(Map<String, String> inputConfiguration) {
        Properties toReturn = new Properties();
        
        for(String key: inputConfiguration.keySet()) {
            toReturn.put(key.substring(PREFIX_LENGTH + 1), inputConfiguration.get(key));
        }
        return toReturn;
    }
    
    public static Map<String, String> immutableConfigurationSettings() {
        return new HashMap<String, String>() {{
            put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
            put("hibernate.connection.driver_class", "org.postgresql.Driver");
            put("hibernate.show_sql", "false");
        }};
    }
    
    public static Map<String, String> defaultConfigurationSettings() {
        return new HashMap<String, String>() {{
            put(URL_CONFIG, "jdbc:postgresql:candlepin");
            put(USER_CONFIG, "candlepin");
            put(PASSWORD_CONFIG, "");
        }};
    }    
}
