package org.fedoraproject.candlepin.model.test;

import org.fedoraproject.candlepin.test.DatabaseTestFixture;

import org.fedoraproject.candlepin.model.Attribute;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class AttributeTest extends DatabaseTestFixture {


    @Before
    public void setupTestObjects() {
        String some_attribute = "canEatCheese";
    }
    
    @Test
    public void testCreateAttr() {
        Attribute newAttr = new Attribute();
        
    }
    
    @Test
    public void testAttributeSetName() {
        Attribute newAttr = new Attribute();
        newAttr.setName("OwesUsMoney");
        persistAndCommit(newAttr);
    }
    
    
    @Test
    public void testAttributeGetName() {
        Attribute newAttr = new Attribute();
        String some_name = "OwesUsMoney";
        
        newAttr.setName(some_name);
        persistAndCommit(newAttr);
        assertEquals(some_name, newAttr.getName());
    }
    
    @Test
    public void testAttributeSetQuantity() {
        Attribute newAttr = new Attribute();
        Long some_number = new Long(100);
        newAttr.setQuantity(some_number);
        persistAndCommit(newAttr);
    }
    
    
    @Test
    public void testAttributeGetQuantity() {
        Attribute newAttr = new Attribute();
        Long some_number = new Long(200);
        
        newAttr.setQuantity(some_number);
        persistAndCommit(newAttr);
        assertEquals(some_number, newAttr.getQuantity());
    }
    
}