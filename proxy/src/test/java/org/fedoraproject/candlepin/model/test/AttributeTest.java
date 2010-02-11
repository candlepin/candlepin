package org.fedoraproject.candlepin.model.test;

import static org.junit.Assert.assertEquals;

import org.fedoraproject.candlepin.model.Attribute;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

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
        String someName = "OwesUsMoney";
        
        newAttr.setName(someName);
        persistAndCommit(newAttr);
        assertEquals(someName, newAttr.getName());
    }
    
    @Test
    public void testAttributeSetQuantity() {
        Attribute newAttr = new Attribute();
        Long someNumber = new Long(100);
        String someName = "OwesUsMoney_100";
        newAttr.setName(someName);
        newAttr.setQuantity(someNumber);
        persistAndCommit(newAttr);
    }
    
    
    @Test
    public void testAttributeGetQuantity() {
        Attribute newAttr = new Attribute();
        Long someNumber = new Long(200);
        String someName = "OwesUsMoney_100";
        newAttr.setName(someName);
        newAttr.setQuantity(someNumber);
        persistAndCommit(newAttr);
        assertEquals(someNumber, newAttr.getQuantity());
    }
    
    @Test
    public void testLookup() {
        Attribute newAttr = new Attribute();
        Long someNumber = new Long(100);
        String someName = "OwesUsMoney_100";
        newAttr.setName(someName);
        newAttr.setQuantity(someNumber);
        persistAndCommit(newAttr);
        
        Attribute foundAttr = attributeCurator.find(newAttr.getId());
        assertEquals(newAttr.getName(), foundAttr.getName());
    }
    
    @Test
    public void testList() throws Exception {
        List<Attribute> attributes = attributeCurator.findAll(); 
        int beforeCount = attributes.size();
     
        
        attributes =  attributeCurator.findAll();
        int afterCount = attributes.size();
//        assertEquals(10, afterCount - beforeCount);
    }
    
    
}