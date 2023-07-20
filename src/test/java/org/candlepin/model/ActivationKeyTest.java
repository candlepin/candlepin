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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;



/**
 * ActivationKeyTest
 */
public class ActivationKeyTest extends DatabaseTestFixture {

    private Owner owner;

    @BeforeEach
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
        assertEquals(1, owner.getActivationKeys().size());
    }

    @Test
    public void testPoolRelationship() {
        ActivationKey key = createActivationKey(owner);
        Product prod = TestUtil.createProduct();
        productCurator.create(prod);
        Pool pool = createPool(owner, prod, 12L,
            new Date(), new Date(System.currentTimeMillis() + (365 * 24 * 60 * 60 * 1000)));
        key.addPool(pool, 5L);
        activationKeyCurator.create(key);
        activationKeyCurator.refresh(key);
        assertNotNull(poolCurator.getActivationKeysForPool(pool));
        assertNotNull(key.getPools());
        assertEquals(1, key.getPools().size());
        assertEquals(5L, key.getPools().iterator().next().getQuantity());
    }

    @Test
    public void testNullPoolRelationship() {
        ActivationKey key = createActivationKey(owner);
        Product prod = TestUtil.createProduct();
        productCurator.create(prod);
        Pool pool = createPool(owner, prod, 12L,
            new Date(), new Date(System.currentTimeMillis() + (365 * 24 * 60 * 60 * 1000)));
        key.addPool(pool, null);
        activationKeyCurator.create(key);
        activationKeyCurator.refresh(key);
        assertNotNull(poolCurator.getActivationKeysForPool(pool));
        assertNotNull(key.getPools());
        assertEquals(1, key.getPools().size());
        assertNull(key.getPools().iterator().next().getQuantity());
    }

    @Test
    public void testActivationKeyHasPool() {
        ActivationKey key = this.createActivationKey(this.owner);
        Product prod = TestUtil.createProduct();
        productCurator.create(prod);
        Pool pool = createPool(
            this.owner,
            prod,
            12L,
            new Date(),
            new Date(System.currentTimeMillis() + (365 * 24 * 60 * 60 * 1000)));

        assertTrue(!key.hasPool(pool));

        key.addPool(pool, 1L);
        assertTrue(key.hasPool(pool));

        key.removePool(pool);
        assertTrue(!key.hasPool(pool));
    }

    @Test
    public void testActivationKeyHasProduct() {
        ActivationKey key = this.createActivationKey(this.owner);
        Product product = TestUtil.createProduct();

        assertTrue(!key.hasProduct(product));

        key.addProduct(product);
        assertTrue(key.hasProduct(product));

        key.removeProduct(product);
        assertTrue(!key.hasProduct(product));
    }
}
