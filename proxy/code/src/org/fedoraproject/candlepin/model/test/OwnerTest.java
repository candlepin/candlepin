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
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.User;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;

import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;

public class OwnerTest extends DatabaseTestFixture {

    @Test
    public void testCreate() throws Exception {
        String ownerName = "Example Corporation";
        Owner o = new Owner(ownerName);
        persistAndCommit(o);
        Owner result = (Owner)em.createQuery(
                "select o from Owner o where o.name = :name")
                .setParameter("name", ownerName).getSingleResult();
        assertNotNull(result);
        assertEquals(ownerName, result.getName());
//        assertEquals(0, result.getConsumers().size());
//        assertEquals(0, result.getEntitlementPools().size());
//        assertEquals(0, result.getUsers().size());
        assertTrue(result.getId() > 0);
        assertEquals(o.getId(), result.getId());
    }
    
    @Test
    public void testList() throws Exception {
        beginTransaction();

        List<Owner> orgs =  em.createQuery("select o from Owner as o")
        .getResultList();
        int beforeCount = orgs.size();
        
        for (int i = 0; i < 10; i++) {
            em.persist(new Owner("Corp " + i));
        }
        commitTransaction();
        
        orgs =  em.createQuery("select o from Owner as o")
            .getResultList();
        int afterCount = orgs.size();
        assertEquals(10, afterCount - beforeCount);
    }
    
    @Test
    public void testObjectRelationships() throws Exception {
        Owner owner = new Owner("test-owner");
        // Product
        Product rhel = new Product();
        rhel.setName("Red Hat Enterprise Linux");
        
        // User
        User u = new User();
        u.setLogin("test-login");
        u.setPassword("redhat");
        owner.addUser(u);
        assertEquals(1, owner.getUsers().size());
        
        // Consumer
        Consumer c = new Consumer();
        c.setOwner(owner);
        owner.addConsumer(c);
        c.addConsumedProduct(rhel);
        assertEquals(1, owner.getConsumers().size());
        assertEquals(1, c.getConsumedProducts().size());
        
        // EntitlementPool
        EntitlementPool pool = new EntitlementPool();
        owner.addEntitlementPool(pool);
        pool.setProduct(rhel);
        assertEquals(1, owner.getEntitlementPools().size());
        
    }
}
