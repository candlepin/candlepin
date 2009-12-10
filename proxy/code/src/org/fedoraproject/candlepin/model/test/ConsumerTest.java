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
        beginTransaction();
        
        String ownerName = "Example Corporation";
        owner = new Owner(ownerName);
        rhel = new Product("rhel", "Red Hat Enterprise Linux");
        jboss = new Product("jboss", "JBoss");
        em.persist(owner);
        em.persist(rhel);
        em.persist(jboss);
        
        consumerType = new ConsumerType(CONSUMER_TYPE_NAME);
        em.persist(consumerType);
        consumer = new Consumer(CONSUMER_NAME, owner, consumerType);
        consumer.setMetadataField("foo", "bar");
        consumer.setMetadataField("foo1", "bar1");
        consumer.addConsumedProduct(rhel);
        consumer.addConsumedProduct(jboss);
        em.persist(consumer);
        
        commitTransaction();
    }
    
    @Test(expected = PersistenceException.class)
    public void testConsumerTypeRequired() {
        Consumer newConsumer = new Consumer();
        newConsumer.setName("cname");
        newConsumer.setOwner(owner);
        beginTransaction();
        em.persist(newConsumer);
        commitTransaction();
    }

    @Test
    public void testLookup() throws Exception {
        
        Consumer lookedUp = (Consumer)em.find(Consumer.class, consumer.getId());
        assertEquals(consumer.getId(), lookedUp.getId());
        assertEquals(consumer.getName(), lookedUp.getName());
        assertEquals(consumer.getType().getLabel(), lookedUp.getType().getLabel());
        assertNotNull(consumer.getUuid());
    }
    
    @Test
    public void testInfo() {
        Consumer lookedUp = (Consumer)em.find(Consumer.class, consumer.getId());
        Map<String, String> metadata = lookedUp.getInfo().getMetadata();
        assertEquals(2, metadata.keySet().size());
        assertEquals("bar", metadata.get("foo"));
        assertEquals("bar", lookedUp.getInfo().getMetadataField("foo"));
        assertEquals("bar1", metadata.get("foo1"));
        assertEquals("bar1", lookedUp.getInfo().getMetadataField("foo1"));
        
    }
    
    @Test
    public void testMetadataInfo() {
        beginTransaction();
        Consumer consumer2 = new Consumer("consumer2", owner, consumerType);
        consumer2.setMetadataField("foo", "bar2");
        em.persist(consumer2);
        commitTransaction();
        
        Consumer lookedUp = (Consumer)em.find(Consumer.class, consumer.getId());
        Map<String, String> metadata = lookedUp.getInfo().getMetadata();
        assertEquals(2, metadata.keySet().size());
        assertEquals("bar", metadata.get("foo"));
        assertEquals("bar", lookedUp.getInfo().getMetadataField("foo"));
        assertEquals("bar1", metadata.get("foo1"));
        assertEquals("bar1", lookedUp.getInfo().getMetadataField("foo1"));
        assertEquals(consumer.getId(), lookedUp.getInfo().getConsumer().getId());
        
        Consumer lookedUp2 = (Consumer)em.find(Consumer.class, consumer2.getId());
        metadata = lookedUp2.getInfo().getMetadata();
        assertEquals(1, metadata.keySet().size());
        assertEquals("bar2", metadata.get("foo"));

    }
    
    @Test
    public void testModifyMetadata() {
        beginTransaction();
        consumer.setMetadataField("foo", "notbar");
        commitTransaction();
        
        Consumer lookedUp = (Consumer)em.find(Consumer.class, consumer.getId());
        assertEquals("notbar", lookedUp.getMetadataField("foo"));
    }
    
    @Test
    public void testMetadataDeleteCascading() {
        ConsumerInfo info = consumer.getInfo();
        Long infoId = info.getId();
        
        ConsumerInfo lookedUp = (ConsumerInfo)em.find(ConsumerInfo.class, infoId);
        assertNotNull(lookedUp);
        
        beginTransaction();
        em.remove(consumer);
        commitTransaction();
        
        lookedUp = (ConsumerInfo)em.find(ConsumerInfo.class, infoId);
        assertNull(lookedUp);
    }

    @Test
    public void testConsumedProducts() {
        em.clear();
        Consumer lookedUp = (Consumer)em.find(Consumer.class, consumer.getId());
        assertEquals(2, lookedUp.getConsumedProducts().size());
    }
    
    @Test
    public void testRemoveConsumedProducts() {
        beginTransaction();
        em.remove(consumer);
        commitTransaction();
        
        Consumer lookedUp = (Consumer)em.find(Consumer.class, consumer.getId());
        assertNull(lookedUp);
    }
    
    @Test
    public void testConsumerHierarchy() {
        beginTransaction();

        Consumer child1 = new Consumer("child1", owner, consumerType);
        child1.setMetadataField("foo", "bar");
        em.persist(child1);
        commitTransaction();

        beginTransaction();
        Consumer child2 = new Consumer("child2", owner, consumerType);
        child2.setMetadataField("foo", "bar");
        em.persist(child2);
        commitTransaction();

        beginTransaction();
        consumer.addChildConsumer(child1);
        consumer.addChildConsumer(child2);
        em.persist(consumer);
        commitTransaction();

        em.clear();
        Consumer lookedUp = (Consumer)em.find(Consumer.class, consumer.getId());
        assertEquals(2, lookedUp.getChildConsumers().size());
    }
    
    @Test
    public void testChildDeleteNoCascade() {
        beginTransaction();

        Consumer child1 = new Consumer("child1", owner, consumerType);
        child1.setMetadataField("foo", "bar");
        consumer.addChildConsumer(child1);
        em.persist(consumer);
        commitTransaction();

        em.clear();
        Long childId = child1.getId();
        child1 = (Consumer)em.find(Consumer.class, childId);
        beginTransaction();
        em.remove(child1);
        commitTransaction();
        
        child1 = (Consumer)em.find(Consumer.class, childId);
        assertNull(child1);
        
        em.clear();
        Consumer lookedUp = (Consumer)em.find(Consumer.class, consumer.getId());
        assertEquals(0, lookedUp.getChildConsumers().size());
    }
    
    @Test
    public void testParentDeleteCascadesToChildren() {
        beginTransaction();

        Consumer child1 = new Consumer("child1", owner, consumerType);
        child1.setMetadataField("foo", "bar");
        consumer.addChildConsumer(child1);
        em.persist(consumer);
        commitTransaction();
        
        Long childId = child1.getId();
        Long parentId = consumer.getId();
        
        beginTransaction();
        em.remove(consumer);
        commitTransaction();
        
        em.clear();
        Consumer lookedUp = (Consumer)em.find(Consumer.class, parentId);
        assertNull(lookedUp);
        lookedUp = (Consumer)em.find(Consumer.class, childId);
        assertNull(lookedUp);
    }
    
    // This this looks like a stupid test but this was actually failing at one point. :)
    @Test
    public void testMultipleConsumersSameConsumedProduct() {
        beginTransaction();
        
        // Default consumer already consumes RHEL:
        Consumer child1 = new Consumer("child1", owner, consumerType);
        child1.setMetadataField("foo", "bar");
        child1.addConsumedProduct(rhel);
        em.persist(child1);
        commitTransaction();
    }
    
    @Test
    public void testEntitlements() {
        beginTransaction();
        EntitlementPool pool = TestUtil.createEntitlementPool();
        em.persist(pool.getProduct());
        em.persist(pool.getOwner());
        em.persist(pool);
        
        Entitlement e1 = TestUtil.createEntitlement(pool);
        Entitlement e2 = TestUtil.createEntitlement(pool);
        Entitlement e3 = TestUtil.createEntitlement(pool);
        em.persist(e1);
        em.persist(e2);
        em.persist(e3);
        
        consumer.addEntitlement(e1);
        consumer.addEntitlement(e2);
        consumer.addEntitlement(e3);
        commitTransaction();
        
        Consumer lookedUp = (Consumer)em.find(Consumer.class, consumer.getId());
        assertEquals(3, lookedUp.getEntitlements().size());
    }

}
