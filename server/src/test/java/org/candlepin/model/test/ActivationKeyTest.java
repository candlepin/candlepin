/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.model.test;

import static org.junit.Assert.*;

import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.Before;
import org.junit.Test;

import java.util.Date;

import javax.inject.Inject;

/**
 * ActivationKeyTest
 */
public class ActivationKeyTest extends DatabaseTestFixture {
    @Inject private OwnerCurator ownerCurator;
    @Inject private ProductCurator productCurator;
    @Inject private PoolCurator poolCurator;
    @Inject private ActivationKeyCurator activationKeyCurator;

    private Owner owner;

    @Before
    public void setUp() {
        owner = createOwner();
        ownerCurator.create(owner);
    }

    @Test
    public void testCreate() {
        ActivationKey key = createActivationKey(owner);
        activationKeyCurator.create(key);
        assertNotNull(key.getId());
        assertNotNull(key.getName());
        assertNotNull(key.getServiceLevel());
        assertNotNull(key.getDescription());
        assertEquals(owner, key.getOwner());
    }

    @Test
    public void testOwnerRelationship() {
        ActivationKey key = createActivationKey(owner);
        activationKeyCurator.create(key);
        ownerCurator.refresh(owner);
        assertNotNull(owner.getActivationKeys());
        assertTrue("The count of keys should be 1", owner.getActivationKeys().size() == 1);
    }

    @Test
    public void testPoolRelationship() {
        ActivationKey key = createActivationKey(owner);
        Product prod = TestUtil.createProduct();
        productCurator.create(prod);
        Pool pool = createPoolAndSub(owner, prod, 12L,
            new Date(), new Date(System.currentTimeMillis() + (365 * 24 * 60 * 60 * 1000)));
        key.addPool(pool, 5L);
        activationKeyCurator.create(key);
        activationKeyCurator.refresh(key);
        assertNotNull(poolCurator.getActivationKeysForPool(pool));
        assertNotNull(key.getPools());
        assertTrue("The count of pools should be 1", key.getPools().size() == 1);
        assertEquals(new Long(5), key.getPools().iterator().next().getQuantity());
    }

    @Test
    public void testNullPoolRelationship() {
        ActivationKey key = createActivationKey(owner);
        Product prod = TestUtil.createProduct();
        productCurator.create(prod);
        Pool pool = createPoolAndSub(owner, prod, 12L,
            new Date(), new Date(System.currentTimeMillis() + (365 * 24 * 60 * 60 * 1000)));
        key.addPool(pool, null);
        activationKeyCurator.create(key);
        activationKeyCurator.refresh(key);
        assertNotNull(poolCurator.getActivationKeysForPool(pool));
        assertNotNull(key.getPools());
        assertTrue("The count of pools should be 1", key.getPools().size() == 1);
        assertEquals(null, key.getPools().iterator().next().getQuantity());
    }
}
