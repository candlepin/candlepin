package org.fedoraproject.candlepin.model.test;

import org.fedoraproject.candlepin.model.Rules;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;

import org.junit.Before;
import org.junit.Test;

public class RulesTest extends DatabaseTestFixture {
    
    

    @Before
    public void setupTestObjects() {
        String rulesBlob = "document.write(\"Hello World!\");";
    }
    
    @Test
    public void testCreateRules() {
        Rules newRules = new Rules();
        
    }
}
