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

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import org.fedoraproject.candlepin.ConfigDirectory;

/**
 * Defines the default Candlepin configuration
 */
public class Config {
    protected File CONFIGURATION_FILE = new File(ConfigDirectory.directory(), "candlepin.conf");
    protected static TreeMap<String, String> configuration = null;
   

    /**
     * Return configuration entry for the given prefix.
     * @param prefix prefix for the entry sought.
     * @return configuration entry for the given prefix.
     */
    public Map<String, String> configurationWithPrefix(String prefix) {
        if (configuration == null) {
            loadConfiguration();
        }
        return configuration.subMap(prefix, prefix + Character.MAX_VALUE);
    }

    public Properties getNamespaceProperties(String prefix) {
        Properties p = new Properties();
        p.putAll(configurationWithPrefix(prefix));
        return p;
    }
   
    /**
     * Returns the JPA Configuration properties.
     * @return the JPA Configuration properties.
     */
    public Properties jpaConfiguration() {
        if (configuration == null) {
            loadConfiguration();
        }
        return new JPAConfiguration().parseConfig(configuration);
    }
    
    /**
     * Returns the Database Basic Authentication Configuration properties
     * @return the Database Basic Authentication Configuration properties
     */
    public Properties dbBasicAuthConfiguration(){
        if (configuration == null) {
            loadConfiguration();
        }
        return new DatabaseBasicAuthConfiguration().parseConfig(configuration);
    }
    
    protected synchronized void loadConfiguration() {
        if (configuration == null) {
            initializeMap();
        }
    }
    
    protected void initializeMap() {
        configuration = new TreeMap<String, String>(loadProperties());
    }
    
    protected Map<String, String> loadProperties() {
        try {
            return new ConfigurationFileLoader().loadProperties(CONFIGURATION_FILE);
        }
        catch (IOException e) {
            throw new RuntimeException("Problem loading candlepin configuration file.", e);
        }
    }

    /**
     * @param s configuration key
     * @return value associated with the given configuration key.
     */
    public String getString(String s) {
        return configuration.get(s);
    }

    /**
     * get the config entry for string s
     *
     * @param s string to get the value of
     * @return the value as an array
     */
    public String[] getStringArray(String s) {
        if (s == null) {
            return null;
        }
        String value = getString(s);

        if (value == null) {
            return null;
        }

        return value.split(",");
    }
}
