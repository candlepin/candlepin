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

import java.sql.Date;

import java.util.Calendar;

import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;

import org.fedoraproject.candlepin.test.DatabaseTestFixture;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;


public class EntitlementPoolTest extends DatabaseTestFixture {

    private EntitlementPool pool;
    private Product prod;
    private Owner owner;

    @Before
    public void createObjects() {
        beginTransaction();
        String ownerName = "Example Corporation";
        owner = new Owner(ownerName);
        em.persist(owner);
        prod = new Product("cptest-label", "My Product");
        em.persist(prod);
        commitTransaction();
        beginTransaction();
        pool = new EntitlementPool(owner, prod, new Long(1000),
               createDate(2009, 11, 30), createDate(2015, 11, 30)); 
        em.persist(pool);
        commitTransaction();
    }

    private Date createDate(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
            
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month);
        cal.set(Calendar.DATE, day);

        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        Date jsqlD = new Date(cal.getTime().getTime());
        return jsqlD;
    }

    @Test
    public void testCreate() {
        EntitlementPool lookedUp = (EntitlementPool)em.find(EntitlementPool.class, pool.getId());
        assertNotNull(lookedUp);
        assertEquals(owner.getId(), lookedUp.getOwner().getId());
        assertEquals(prod.getId(), lookedUp.getProduct().getId());

    }

}
