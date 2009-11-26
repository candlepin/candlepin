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

import org.fedoraproject.candlepin.model.Consumer;
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
    private Consumer consumer;
    private ConsumerType consumerType;
    private static final String CONSUMER_TYPE_NAME = "test-consumer-type";
    private static final String CONSUMER_NAME = "Test Consumer";
    
    @Before
    public void setUpTestObjects() {
        em.getTransaction().begin();
        
        String ownerName = "Example Corporation";
        owner = new Owner(ownerName);
        rhel = new Product("label", "Red Hat Enterprise Linux");
        em.persist(owner);
        em.persist(rhel);
        
        consumerType = new ConsumerType(CONSUMER_TYPE_NAME);
        em.persist(consumerType);
        consumer = new Consumer(CONSUMER_NAME, owner, consumerType);
        em.persist(consumer);
        
        em.getTransaction().commit();
    }

    @Test
    public void testLookup() throws Exception {
        
        Consumer lookedUp = (Consumer)em.createQuery(
                "from Consumer c where c.name = :name").
                setParameter("name", CONSUMER_NAME).
                getSingleResult();
        assertEquals(consumer.getId(), lookedUp.getId());
        assertEquals(consumer.getName(), lookedUp.getName());
        assertEquals(consumer.getType().getLabel(), lookedUp.getType().getLabel());
//        
//        Consumer c = TestUtil.createConsumer(o);
//        c.addConsumedProduct(rhel);
    }
    
//    @Test
//    public void testProperties() {
//        Owner o = TestUtil.createOwner();
//        Consumer c = TestUtil.createConsumer(o);
//        c.setMetadataField("cpu", "2");
//        
//        assertEquals(c.getMetadataField("cpu"), "2");
//    }
}
