package org.fedoraproject.candlepin.config;

import java.util.Map;
import java.util.Properties;

public abstract class ConfigurationParser {

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
            toReturn.put(key.substring(prefixLength() + 1), inputConfiguration.get(key));
        }
        return toReturn;
    }    

    public abstract int prefixLength();
}
