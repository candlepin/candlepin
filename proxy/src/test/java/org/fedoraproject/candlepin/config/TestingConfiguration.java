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

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;


public class TestingConfiguration extends Config {

    private String file;
    
    public TestingConfiguration(String configFile) { 
        file = configFile;
    }
    /**
     * Returns the Database Basic Authentication Configuration properties
     * @return the Database Basic Authentication Configuration properties
     */
    public Properties dbBasicAuthConfiguration() {
        configuration = null;
        loadConfiguration();
        return new DbBasicAuthConfigParser().parseConfig(configuration);
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
        Map<String, String> propertyMap = new TreeMap<String, String>();
        try {
            InputStream in =  ClassLoader.getSystemClassLoader().getResourceAsStream(file);
            Properties prop = new Properties();
            prop.load(in);
            for (Object key : prop.keySet()) {
                propertyMap.put((String) key, (String) prop.get(key));
            }
        }
        catch (IOException e) {
            throw new RuntimeException("Problem loading candlepin configuration file.", e);
        }
        return propertyMap;
    }
}
