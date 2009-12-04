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

import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;


public class EntitlementPoolTest extends DatabaseTestFixture {

    private EntitlementPool pool;
    private Product prod;
    private Owner owner;

    @Before
    public void createObjects() {
        beginTransaction();
        
        pool = TestUtil.createEntitlementPool();
        owner = pool.getOwner();
        prod = pool.getProduct();
        em.persist(owner);
        em.persist(prod);
        em.persist(pool);
        
        commitTransaction();
    }

    @Test
    public void testCreate() {
        EntitlementPool lookedUp = (EntitlementPool)em.find(EntitlementPool.class, 
                pool.getId());
        assertNotNull(lookedUp);
        assertEquals(owner.getId(), lookedUp.getOwner().getId());
        assertEquals(prod.getId(), lookedUp.getProduct().getId());
    }

}
