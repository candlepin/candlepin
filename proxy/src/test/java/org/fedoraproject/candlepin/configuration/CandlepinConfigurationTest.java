package org.fedoraproject.candlepin.configuration;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Test;

public class CandlepinConfigurationTest {

    @Test
    public void returnAllKeysWithAPrefixFromHead() {
        CandlepinConfiguration config = new CandlepinConfigurationForTesting(
                new HashMap<String, String>() {{
                    put("a.b.a.b", "value");
                    put("a.b.c.d", "value");
                    put("a.b.e.f", "value");
                    put("a.c.a.a", "value");
                }});
        
        Map<String, String> withPrefix = config.configurationWithPrefix("a.b");
        assertEquals(3, withPrefix.size());
        assertTrue(withPrefix.containsKey("a.b.a.b"));
        assertTrue(withPrefix.containsKey("a.b.c.d"));
        assertTrue(withPrefix.containsKey("a.b.e.f"));
    }
    
    @Test
    public void returnAllKeysWithAPrefixInTheMiddle() {
        CandlepinConfiguration config = new CandlepinConfigurationForTesting(
                new HashMap<String, String>() {{
                    put("a.b.a.b", "value");
                    put("a.b.c.d", "value");
                    put("a.c.a.b", "value");
                    put("a.c.c.d", "value");
                    put("a.c.e.f", "value");
                    put("a.d.a.b", "value");
                }});
        
        Map<String, String> withPrefix = config.configurationWithPrefix("a.c");
        assertEquals(3, withPrefix.size());
        assertTrue(withPrefix.containsKey("a.c.a.b"));
        assertTrue(withPrefix.containsKey("a.c.c.d"));
        assertTrue(withPrefix.containsKey("a.c.e.f"));
    }
    
    @Test
    public void returnAllKeysWithAPrefixFromTail() {
        CandlepinConfiguration config = new CandlepinConfigurationForTesting(
                new HashMap<String, String>() {{
                    put("a.b.a.b", "value");
                    put("a.b.c.d", "value");
                    put("a.c.a.b", "value");
                    put("a.c.c.d", "value");
                    put("a.c.e.f", "value");
                }});
        
        Map<String, String> withPrefix = config.configurationWithPrefix("a.c");
        assertEquals(3, withPrefix.size());
        assertTrue(withPrefix.containsKey("a.c.a.b"));
        assertTrue(withPrefix.containsKey("a.c.c.d"));
        assertTrue(withPrefix.containsKey("a.c.e.f"));
    }
    
    public static class CandlepinConfigurationForTesting extends CandlepinConfiguration {
        public CandlepinConfigurationForTesting(Map<String, String> inConfig) {
            configuration = new TreeMap<String, String>(inConfig);
        }
    }
}
