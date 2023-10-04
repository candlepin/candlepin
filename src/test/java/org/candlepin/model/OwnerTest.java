/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.jupiter.api.Test;


public class OwnerTest extends DatabaseTestFixture {

    @Test
    public void testCreate() {
        String ownerName = "Example-Corporation";
        String prefix = "PhredPrefix";

        Owner o = TestUtil.createOwner(ownerName);
        o.setId(null);
        o.setContentPrefix(prefix);
        ownerCurator.create(o);

        Owner result = (Owner) this.getEntityManager().createQuery("select o from Owner o where o.key = :key")
            .setParameter("key", ownerName)
            .getSingleResult();

        assertNotNull(result);
        assertEquals(ownerName, result.getKey());
        assertEquals(ownerName, result.getDisplayName());
        assertEquals(prefix, result.getContentPrefix());
        assertNotNull(result.getId());
        assertEquals(o.getId(), result.getId());
    }

    @Test
    public void testList() {
        int beforeCount = this.getEntityManager().createQuery("select o from Owner as o")
            .getResultList()
            .size();

        for (int i = 0; i < 10; i++) {
            this.createOwner("Corp " + i);
        }

        int afterCount = this.getEntityManager().createQuery("select o from Owner as o")
            .getResultList()
            .size();

        assertEquals(10, afterCount - beforeCount);
    }

    @Test
    public void testObjectRelationships() {
        Owner owner = TestUtil.createOwner("test-owner");

        // Product
        Product rhel = TestUtil.createProduct("Red Hat Enterprise Linux", "Red Hat Enterprise Linux");

        // Consumer
        Consumer c = new Consumer().setUuid(Util.generateUUID());
        c.setOwner(owner);
        owner.addConsumer(c);
        assertEquals(1, owner.getConsumers().size());

        // EntitlementPool
        Pool pool = TestUtil.createPool(owner, rhel);
        owner.addPool(pool);
        assertEquals(1, owner.getPools().size());
    }

    @Test
    public void bidirectionalConsumers() {
        beginTransaction();
        Owner o = this.createOwner();
        ConsumerType consumerType = this.createConsumerType();
        Consumer c1 = this.createConsumer(o, consumerType);
        Consumer c2 = this.createConsumer(o, consumerType);
        o.addConsumer(c1);
        o.addConsumer(c2);

        ownerCurator.merge(o);
        commitTransaction();

        assertEquals(2, o.getConsumers().size());

        Owner lookedUp = ownerCurator.get(o.getId());
        assertEquals(2, lookedUp.getConsumers().size());
    }

    @Test
    public void testAutobindDisabledUtilityMethod() {
        Owner o = createOwner();
        o.setAutobindDisabled(true);
        assertTrue(o.isAutobindDisabled());

        o.setAutobindDisabled(false);
        assertFalse(o.isAutobindDisabled());
    }

}

