package org.fedoraproject.candlepin.configuration;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class ConfigurationFileLoader {
    protected File configurationFile;
    
    public ConfigurationFileLoader(File configurationFile) {
        this.configurationFile = configurationFile;
    }

    public Map<String, String> loadProperties() throws IOException {
        if (configurationFile.canRead()) {
            return loadConfiguration(new BufferedInputStream(new FileInputStream(configurationFile)));
        }
        return new HashMap<String, String>();
    }
    
    @SuppressWarnings("unchecked")
    protected Map<String, String> loadConfiguration(InputStream input) throws IOException {
        Properties loaded = new Properties();
        loaded.load(input);
        return new HashMap(loaded);
    }
}
