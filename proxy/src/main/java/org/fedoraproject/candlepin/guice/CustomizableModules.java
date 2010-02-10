package org.fedoraproject.candlepin.guice;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.google.inject.Module;

public class CustomizableModules {
    
    static private String CONFIGURATION_FILE_NAME = "/etc/candlepin/candlepin.conf";
    protected File CONFIGURATION_FILE = new File(CONFIGURATION_FILE_NAME);
    
    public Set<Module> load() {
        if (CONFIGURATION_FILE.canRead()) {
            try {
                return customizedConfiguration();
            } catch (Exception e) {
                throw new RuntimeException("Exception when loading customized module configuration.", e);
            }
        }
        return defaultConfiguration();
    }
    
    public Set<Module> defaultConfiguration() {
//        bind(SubscriptionServiceAdapter.class).to(OnSiteSubscriptionServiceAdapter.class);
        return new HashSet<Module>();
    }
    
    @SuppressWarnings("unchecked")
    public Set<Module> customizedConfiguration() 
            throws IOException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        
        Map<String, String> loaded = 
            loadCustomConfiguration(new BufferedInputStream(new FileInputStream(CONFIGURATION_FILE)));
        Set toReturn = new HashSet();
        
        for (String guiceModuleName: loaded.keySet()) {
            toReturn.add(Class.forName(loaded.get(guiceModuleName)).newInstance());
        }
        
        return toReturn;
    }
    
    @SuppressWarnings("unchecked")
    public Map<String, String> loadCustomConfiguration(InputStream input) throws IOException {
        Properties loaded = new Properties();
        loaded.load(input);
        return new HashMap(loaded);
    }
}
