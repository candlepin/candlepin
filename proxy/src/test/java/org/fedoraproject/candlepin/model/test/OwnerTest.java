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
import static org.junit.Assert.assertNotNull;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.ConsumerType;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Test;

public class OwnerTest extends DatabaseTestFixture {

    @Test
    public void testCreate() throws Exception {
        String ownerName = "Example-Corporation";
        Owner o = new Owner(ownerName);
        ownerCurator.create(o);

        Owner result = (Owner) entityManager().createQuery(
                "select o from Owner o where o.key = :key").setParameter(
                "key", ownerName).getSingleResult();

        assertNotNull(result);
        assertEquals(ownerName, result.getKey());
        assertEquals(ownerName, result.getDisplayName());
        assertNotNull(result.getId());
        assertEquals(o.getId(), result.getId());
    }

    @Test
    public void testList() throws Exception {
        int beforeCount = entityManager().createQuery(
                "select o from Owner as o").getResultList().size();

        for (int i = 0; i < 10; i++) {
            ownerCurator.create(new Owner("Corp " + i));
        }

        int afterCount = entityManager()
                .createQuery("select o from Owner as o").getResultList().size();
        assertEquals(10, afterCount - beforeCount);
    }

    @Test
    public void testObjectRelationships() throws Exception {
        Owner owner = new Owner("test-owner");
        // Product
        Product rhel = new Product("Red Hat Enterprise Linux",
                "Red Hat Enterprise Linux");

        // Consumer
        Consumer c = new Consumer();
        c.setOwner(owner);
        owner.addConsumer(c);
        assertEquals(1, owner.getConsumers().size());

        // EntitlementPool
        Pool pool = TestUtil.createPool(owner, rhel);
        owner.addEntitlementPool(pool);
        assertEquals(1, owner.getPools().size());
    }

    @Test
    public void bidirectionalConsumers() throws Exception {
        beginTransaction();
        Owner o = createOwner();
        ConsumerType consumerType = TestUtil.createConsumerType();
        Consumer c1 = TestUtil.createConsumer(consumerType, o);
        Consumer c2 = TestUtil.createConsumer(consumerType, o);
        o.addConsumer(c1);
        o.addConsumer(c2);

        ownerCurator.create(o);
        consumerTypeCurator.create(consumerType);
        consumerCurator.create(c1);
        consumerCurator.create(c2);

        assertEquals(2, o.getConsumers().size());

        Owner lookedUp = ownerCurator.find(o.getId());
        assertEquals(2, lookedUp.getConsumers().size());
    }

}
