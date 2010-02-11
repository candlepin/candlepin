package org.fedoraproject.candlepin.configuration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

public class CandlepinConfiguration {
    protected File CONFIGURATION_FILE = new File("/etc/candlepin/candlepin.conf");
    static protected TreeMap<String, String> configuration = null;
    
    public Map<String, String> configurationWithPrefix(String prefix) {
        if (configuration == null) {
            loadConfiguration();
        }
        return configuration.subMap(prefix, prefix + Character.MAX_VALUE);
    }
    
    public Properties jpaConfiguration() {
        if (configuration == null) {
            loadConfiguration();
        }
        return JPAConfiguration.parseConfig(configuration);
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
        } catch (IOException e) {
            throw new RuntimeException("Exception when loading candlepin configuration file.", e);
        }
    }
}
