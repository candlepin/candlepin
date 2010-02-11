package org.fedoraproject.candlepin.guice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.inject.Module;

import org.fedoraproject.candlepin.configuration.CandlepinConfiguration;
import org.junit.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Set;

public class CustomizableModulesTest {

    @Test
    public void shouldLoadAndParseConfigurationFile() throws Exception {
        Set<Module> loaded = 
            new CustomizableModulesForTesting(new CandlepinConfigurationForTesting("customizable_modules_test.conf")).load();
        
        assertEquals(1, loaded.size());
        assertTrue(loaded.iterator().next() instanceof DummyModuleForTesting);
    }
    
    @Test
    public void shouldFailWhenConfigurationContainsMissingClass() throws Exception {
        try {
            new CustomizableModulesForTesting(
                    new CandlepinConfigurationForTesting("customizable_modules_with_missing_class.conf")
            ).load();
            fail();
        } catch (RuntimeException e) {
            assertTrue(e.getCause() instanceof ClassNotFoundException);
        }
    }
    
    public static class CustomizableModulesForTesting extends CustomizableModules {
        private CandlepinConfiguration config;
        
        public CustomizableModulesForTesting(CandlepinConfiguration config) {
            this.config = config;
        }
        
        protected CandlepinConfiguration configuration() {
            return config;
        }
    }
    
    public static class CandlepinConfigurationForTesting extends CandlepinConfiguration {
        public CandlepinConfigurationForTesting(String fileName) throws URISyntaxException {
            CONFIGURATION_FILE = new File(getClass().getResource(fileName).toURI());
            initializeMap();
        }
    }
}

