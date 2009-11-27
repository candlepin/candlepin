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

import java.util.Map;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerInfo;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;



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

    @Test
    public void testLookup() throws Exception {
        
        Consumer lookedUp = (Consumer)em.find(Consumer.class, consumer.getId());
        assertEquals(consumer.getId(), lookedUp.getId());
        assertEquals(consumer.getName(), lookedUp.getName());
        assertEquals(consumer.getType().getLabel(), lookedUp.getType().getLabel());
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
        Consumer lookedUp = (Consumer)em.find(Consumer.class, consumer.getId());
        Map<String, String> metadata = lookedUp.getInfo().getMetadata();
        assertEquals(2, metadata.keySet().size());
        assertEquals("bar", metadata.get("foo"));
        assertEquals("bar", lookedUp.getInfo().getMetadataField("foo"));
        assertEquals("bar1", metadata.get("foo1"));
        assertEquals("bar1", lookedUp.getInfo().getMetadataField("foo1"));
        commitTransaction();
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
    
}
