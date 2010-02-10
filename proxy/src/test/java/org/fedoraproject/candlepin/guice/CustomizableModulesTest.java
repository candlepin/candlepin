package org.fedoraproject.candlepin.guice;

import static org.junit.Assert.*;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Set;

import org.junit.Test;

import com.google.inject.Module;

public class CustomizableModulesTest {

    @Test
    public void shouldLoadAndParseConfigurationFile() throws Exception {
        Set<Module> loaded = new CustomizableModulesForTesting("customizable_modules_test.conf").load();
        
        assertEquals(1, loaded.size());
        assertTrue(loaded.iterator().next() instanceof DummyModuleForTesting);
    }
    
    @Test
    public void shouldFailWhenConfigurationContainsMissingClass() throws Exception {
        try {
            new CustomizableModulesForTesting("customizable_modules_with_missing_class.conf").load();
            fail();
        } catch (RuntimeException e) {
            assertTrue(e.getCause() instanceof ClassNotFoundException);
        }
    }
    
    public static class CustomizableModulesForTesting extends CustomizableModules {
        public CustomizableModulesForTesting(String fileName) throws URISyntaxException {
            CONFIGURATION_FILE = new File(getClass().getResource(fileName).toURI()); 
        }
    }
}

