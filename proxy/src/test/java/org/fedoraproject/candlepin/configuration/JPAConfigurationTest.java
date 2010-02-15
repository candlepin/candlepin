package org.fedoraproject.candlepin.configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.Test;
import static org.junit.Assert.*;

public class JPAConfigurationTest {

    @Test
    public void shouldStripJPAConfigKeyPrefixes() {
        final String key1 = "key1";
        final String key2 = "key1.key2";
        
        Map<String, String> configuraton = new HashMap<String, String>() {{
            put(JPAConfiguration.JPA_CONFIG_PREFIX + "." + key1, "value");
            put(JPAConfiguration.JPA_CONFIG_PREFIX + "." + key2, "value");
        }};
        
        Properties stripped = new JPAConfiguration().stripPrefixFromConfigKeys(configuraton);
        
        assertEquals(2, stripped.size());
        assertTrue(stripped.containsKey(key1));
        assertTrue(stripped.containsKey(key2));
    }
    
    @Test
    public void shouldReturnPropertyElementsOfConfigurationFile() throws Exception {
        Properties loaded = 
            new JPAConfiguration().loadDefaultConfigurationSettings("testing", "persistence_for_testing.xml");
        
        assertEquals(3, loaded.size());
        assertEquals("first", (String) loaded.get("first"));
        assertEquals("second", (String) loaded.get("second"));
        assertEquals("third", (String) loaded.get("third"));
    }
}
