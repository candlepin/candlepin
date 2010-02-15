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

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Defines the default Candlepin configuration
 */
public class CandlepinConfiguration {
    protected File CONFIGURATION_FILE = new File("/etc/candlepin/candlepin.conf");
    protected static TreeMap<String, String> configuration = null;
   
    /**
     * TODO: not sure what this returns
     * @return TODO: something
     */
    public Map<String, String> configurationWithPrefix(String prefix) {
        if (configuration == null) {
            loadConfiguration();
        }
        return configuration.subMap(prefix, prefix + Character.MAX_VALUE);
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
            return new ConfigurationFileLoader(CONFIGURATION_FILE).loadProperties();
        }
        catch (IOException e) {
            throw new RuntimeException("Problem loading candlepin configuration file.", e);
        }
    }
}
