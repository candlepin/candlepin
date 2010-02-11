package org.fedoraproject.candlepin.guice;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.fedoraproject.candlepin.configuration.CandlepinConfiguration;

import com.google.inject.Module;

public class CustomizableModules {
    
    public final static String MODULE_CONFIG_PREFIX = "module.config";
    
    public Set<Module> load() {
        Map<String, String> loaded = configuration().configurationWithPrefix(MODULE_CONFIG_PREFIX);
        if (loaded.isEmpty()) {
            return defaultConfiguration();
        }
        return customizedConfiguration(loaded);
    }
    
    public Set<Module> defaultConfiguration() {
//        bind(SubscriptionServiceAdapter.class).to(OnSiteSubscriptionServiceAdapter.class);
        return new HashSet<Module>();
    }
    
    @SuppressWarnings("unchecked")
    public Set<Module> customizedConfiguration(Map<String, String> loadedConfiguration) {
        try {
            Set toReturn = new HashSet();
            
            for (String guiceModuleName: loadedConfiguration.keySet()) {
                toReturn.add(Class.forName(loadedConfiguration.get(guiceModuleName)).newInstance());
            }
            
            return toReturn; 
        } catch (Exception e) {
            throw new RuntimeException("Exception when instantiation guice module.", e);
        }
    }
    
    @SuppressWarnings("unchecked")
    public Map<String, String> loadCustomConfiguration(InputStream input) throws IOException {
        Properties loaded = new Properties();
        loaded.load(input);
        return new HashMap(loaded);
    }
    
    protected CandlepinConfiguration configuration() {
        return new CandlepinConfiguration();
    }
}
