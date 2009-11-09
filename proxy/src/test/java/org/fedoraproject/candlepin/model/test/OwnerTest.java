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

import org.fedoraproject.candlepin.model.BaseModel;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.ObjectFactory;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.User;

import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * 
 *
 */
public class OwnerTest {

    @Test
    public void testOwner() throws Exception {
        Owner o = new Owner(BaseModel.generateUUID());
        assertNotNull(o);
    }
    
    @Test
    public void testLookup() throws Exception {
        
        Owner o = TestUtil.createOwner();
        String lookedUp = o.getUuid();
        o = (Owner) ObjectFactory.get().
            lookupByUUID(Owner.class, lookedUp);
        assertNotNull(o);
    }
    
    @Test
    public void testList() throws Exception {
        for (int i = 0; i < 10; i++) {
            TestUtil.createOwner();
        }
        
        List orgs =  ObjectFactory.get().listObjectsByClass(Owner.class);
        assertNotNull(orgs);
        assertTrue(orgs.size() >= 10);
    }
    
    @Test
    public void testObjectRelationships() throws Exception {
        Owner owner = new Owner(BaseModel.generateUUID());
        owner.setName("test-owner");
        // Product
        Product rhel = new Product(BaseModel.generateUUID());
        rhel.setName("Red Hat Enterprise Linux");
        
        // User
        User u = new User();
        u.setLogin("test-login");
        u.setPassword("redhat");
        owner.addUser(u);
        assertEquals(1, owner.getUsers().size());
        
        // Consumer
        Consumer c = new Consumer(BaseModel.generateUUID());
        c.setOwner(owner);
        owner.addConsumer(c);
        c.addConsumedProduct(rhel);
        assertEquals(1, owner.getConsumers().size());
        assertEquals(1, c.getConsumedProducts().size());
        
        // EntitlementPool
        EntitlementPool pool = new EntitlementPool(BaseModel.generateUUID());
        owner.addEntitlementPool(pool);
        pool.setProduct(rhel);
        assertEquals(1, owner.getEntitlementPools().size());
        
    }
}
