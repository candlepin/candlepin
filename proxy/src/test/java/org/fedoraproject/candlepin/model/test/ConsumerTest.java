/**
 * Copyright (c) 2009 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.fedoraproject.candlepin.model.test;

import static org.junit.Assert.assertEquals;

import static org.junit.Assert.*;

import java.util.Map;

import javax.persistence.PersistenceException;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerInfo;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;



public class ConsumerTest extends DatabaseTestFixture {
    
    private Owner owner;
    private Product rhel;
    private Product jboss;
    private Consumer consumer;
    private ConsumerType consumerType;
    private static final String CONSUMER_TYPE_NAME = "test-consumer-type";
    private static final String CONSUMER_NAME = "Test Consumer";
    
    @Before
    public void setUpTestObjects() {
        owner = new Owner("Example Corporation");
        rhel = new Product("rhel", "Red Hat Enterprise Linux");
        jboss = new Product("jboss", "JBoss");
        
        ownerCurator.create(owner);
        productCurator.create(rhel);
        productCurator.create(jboss);
        
        consumerType = new ConsumerType(CONSUMER_TYPE_NAME);
        consumerTypeCurator.create(consumerType);
        consumer = new Consumer(CONSUMER_NAME, owner, consumerType);
        consumer.setMetadataField("foo", "bar");
        consumer.setMetadataField("foo1", "bar1");
        consumer.addConsumedProduct(rhel);
        consumer.addConsumedProduct(jboss);
        consumerCurator.create(consumer);
    }
    
    @Test(expected = PersistenceException.class)
    public void testConsumerTypeRequired() {
        Consumer newConsumer = new Consumer();
        newConsumer.setName("cname");
        newConsumer.setOwner(owner);
        
        consumerCurator.create(newConsumer);
    }

    @Test
    public void testLookup() throws Exception {
        Consumer lookedUp = consumerCurator.find(consumer.getId()); 
        assertEquals(consumer.getId(), lookedUp.getId());
        assertEquals(consumer.getName(), lookedUp.getName());
        assertEquals(consumer.getType().getLabel(), lookedUp.getType().getLabel());
        assertNotNull(consumer.getUuid());
    }
    
    @Test
    public void testInfo() {
        Consumer lookedUp = consumerCurator.find(consumer.getId());
        Map<String, String> metadata = lookedUp.getInfo().getMetadata();
        assertEquals(2, metadata.keySet().size());
        assertEquals("bar", metadata.get("foo"));
        assertEquals("bar", lookedUp.getInfo().getMetadataField("foo"));
        assertEquals("bar1", metadata.get("foo1"));
        assertEquals("bar1", lookedUp.getInfo().getMetadataField("foo1"));
    }
    
    @Test
    public void testMetadataInfo() {
        Consumer consumer2 = new Consumer("consumer2", owner, consumerType);
        consumer2.setMetadataField("foo", "bar2");
        consumerCurator.create(consumer2);
        
        Consumer lookedUp = consumerCurator.find(consumer.getId());
        Map<String, String> metadata = lookedUp.getInfo().getMetadata();
        assertEquals(2, metadata.keySet().size());
        assertEquals("bar", metadata.get("foo"));
        assertEquals("bar", lookedUp.getInfo().getMetadataField("foo"));
        assertEquals("bar1", metadata.get("foo1"));
        assertEquals("bar1", lookedUp.getInfo().getMetadataField("foo1"));
        assertEquals(consumer.getId(), lookedUp.getInfo().getConsumer().getId());
        
        Consumer lookedUp2 = consumerCurator.find(consumer2.getId());
        metadata = lookedUp2.getInfo().getMetadata();
        assertEquals(1, metadata.keySet().size());
        assertEquals("bar2", metadata.get("foo"));
    }
    
    @Test
    public void testModifyMetadata() {
        consumer.setMetadataField("foo", "notbar");
        consumerCurator.update(consumer);
        
        Consumer lookedUp = consumerCurator.find(consumer.getId());
        assertEquals("notbar", lookedUp.getMetadataField("foo"));
    }
    
    @Test
    public void testMetadataDeleteCascading() {
        Consumer attachedConsumer = consumerCurator.find(consumer.getId());
        Long consumerInfoId = attachedConsumer.getInfo().getId();

        assertNotNull((ConsumerInfo)entityManager().find(ConsumerInfo.class, consumerInfoId));
        
        consumerCurator.delete(attachedConsumer);
        
        assertNull((ConsumerInfo)entityManager().find(ConsumerInfo.class, consumerInfoId));
    }

    @Test
    public void testConsumedProducts() {
        entityManager().clear();
        Consumer lookedUp = (Consumer)entityManager().find(Consumer.class, consumer.getId());
        assertEquals(2, lookedUp.getConsumedProducts().size());
    }
    
    @Test
    public void testRemoveConsumedProducts() {
        consumerCurator.delete(consumerCurator.find(consumer.getId()));
        assertNull(consumerCurator.find(consumer.getId()));
    }
    
    @Test
    public void testConsumerHierarchy() {
        Consumer child1 = new Consumer("child1", owner, consumerType);
        child1.setMetadataField("foo", "bar");
        consumerCurator.create(child1);

        Consumer child2 = new Consumer("child2", owner, consumerType);
        child2.setMetadataField("foo", "bar");
        consumerCurator.create(child2);

        consumer.addChildConsumer(child1);
        consumer.addChildConsumer(child2);
        consumerCurator.update(consumer);

        Consumer lookedUp = consumerCurator.find(consumer.getId());
        assertEquals(2, lookedUp.getChildConsumers().size());
    }
    
    @Test
    public void testChildDeleteNoCascade() {
        Consumer child1 = new Consumer("child1", owner, consumerType);
        child1.setMetadataField("foo", "bar");
        consumer.addChildConsumer(child1);
        consumerCurator.update(consumer);

        child1 = consumerCurator.find(child1.getId());
        consumerCurator.delete(child1);
        
        assertNull(consumerCurator.find(child1.getId()));
        
        Consumer lookedUp = consumerCurator.find(consumer.getId());
        assertEquals(0, lookedUp.getChildConsumers().size());
    }
    
    @Test
    public void testParentDeleteCascadesToChildren() {
        Consumer child1 = new Consumer("child1", owner, consumerType);
        child1.setMetadataField("foo", "bar");
        consumer.addChildConsumer(child1);
        consumerCurator.update(consumer);
        
        consumerCurator.delete(consumer);
        
        assertNull(consumerCurator.find(consumer.getId()));
        assertNull(consumerCurator.find(child1.getId()));
    }
    
    // This this looks like a stupid test but this was actually failing at one point. :)
    @Test
    public void testMultipleConsumersSameConsumedProduct() {
        beginTransaction();
        
        // Default consumer already consumes RHEL:
        Consumer child1 = new Consumer("child1", owner, consumerType);
        child1.setMetadataField("foo", "bar");
        child1.addConsumedProduct(rhel);
        entityManager().persist(child1);
        commitTransaction();
    }
    
    @Test
    public void testAddEntitlements() {
        EntitlementPool pool = TestUtil.createEntitlementPool();
        entityManager().persist(pool.getProduct());
        entityManager().persist(pool.getOwner());
        entityManager().persist(pool);
        
        Entitlement e1 = TestUtil.createEntitlement(pool);
        Entitlement e2 = TestUtil.createEntitlement(pool);
        Entitlement e3 = TestUtil.createEntitlement(pool);
        entityManager().persist(e1);
        entityManager().persist(e2);
        entityManager().persist(e3);
        
        consumer.addEntitlement(e1);
        consumer.addEntitlement(e2);
        consumer.addEntitlement(e3);
        consumerCurator.update(consumer);
        
        Consumer lookedUp = consumerCurator.find(consumer.getId());
        assertEquals(3, lookedUp.getEntitlements().size());
    }
    
}
