/**
 * Copyright (c) 2009 - 2021 Red Hat, Inc.
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

import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.model.Pool.PoolType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;



/**
 * Test suite for the Pool object
 */
public class PoolTest {

    @Test
    public void testUnlimitedPoolsNeverOverflow() {
        Pool pool = new Pool()
            .setConsumed(9001L)
            .setQuantity(-1L);

        assertFalse(pool.isOverflowing());
    }

    @Test
    public void testIsOverflowingUnconsumed() {
        Pool pool = new Pool()
            .setConsumed(0L)
            .setQuantity(10L);

        assertFalse(pool.isOverflowing());
    }

    @Test
    public void testIsOverflowingUnderConsumed() {
        Pool pool = new Pool()
            .setConsumed(5L)
            .setQuantity(10L);

        assertFalse(pool.isOverflowing());
    }

    @Test
    public void testIsOverflowingOverConsumed() {
        Pool pool = new Pool()
            .setConsumed(50L)
            .setQuantity(10L);

        assertTrue(pool.isOverflowing());
    }

    @Test
    public void testAdjustQuantity() {
        Pool pool = new Pool()
            .setQuantity(10L);

        long quantity = pool.adjustQuantity(2L);
        assertEquals(12L, quantity);

        // NOTE: pool.adjustQuantity DOES NOT CHANGE THE POOL'S QUANTITY (what!?)
        assertEquals(10L, pool.getQuantity());

        quantity = pool.adjustQuantity(-2L);
        assertEquals(8L, quantity);
        assertEquals(10L, pool.getQuantity());
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(longs = { -2L, -20L, -200L })
    public void testAdjustQuantityDoesNotReturnNegativeQuantities(long adjustment) {
        Pool pool = new Pool()
            .setQuantity(1L);

        long quantity = pool.adjustQuantity(adjustment);
        assertEquals(0L, quantity);
        assertEquals(1L, pool.getQuantity());
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(ints = { -1, 0, 1, 10, 100, 1000, 10000, 1000000 })
    public void testEntitlementsAlwaysAvailableForUnlimitedPools(int qtyToConsume) {
        Pool pool = new Pool()
            .setQuantity(-1L);

        assertTrue(pool.entitlementsAvailable(qtyToConsume));
    }

    @Test
    public void testPoolTypeNormalAsDefault() {
        Pool pool = new Pool();

        assertEquals(PoolType.NORMAL, pool.getType());
    }

    @Test
    public void testPoolTypeBonus() {
        Pool pool = new Pool()
            .setAttribute(Pool.Attributes.DERIVED_POOL, "true");

        assertEquals(PoolType.BONUS, pool.getType());
    }

    @Test
    public void testPoolTypeEntitlementDerived() {
        Pool pool = new Pool()
            .setAttribute(Pool.Attributes.DERIVED_POOL, "true")
            .setSourceEntitlement(new Entitlement())
            .setSourceStack(null);

        assertEquals(PoolType.ENTITLEMENT_DERIVED, pool.getType());
    }

    @Test
    public void testPoolTypeStackDerived() {
        Pool pool = new Pool()
            .setAttribute(Pool.Attributes.DERIVED_POOL, "true")
            .setSourceEntitlement(null)
            .setSourceStack(new SourceStack(new Consumer(), "something"));

        assertEquals(PoolType.STACK_DERIVED, pool.getType());
    }

    @Test
    public void testPoolTypeUnmappedGuestFromAttribute() {
        Pool pool = new Pool()
            .setAttribute(Pool.Attributes.DERIVED_POOL, "true")
            .setAttribute(Pool.Attributes.UNMAPPED_GUESTS_ONLY, "true")
            .setSourceEntitlement(null)
            .setSourceStack(null);

        assertEquals(PoolType.UNMAPPED_GUEST, pool.getType());
    }

    @Test
    public void testPoolTypeUnmappedGuest() {
        Pool pool = new Pool()
            .setAttribute(Pool.Attributes.DERIVED_POOL, "true")
            .setAttribute(Pool.Attributes.UNMAPPED_GUESTS_ONLY, "true")
            .setSourceEntitlement(new Entitlement())
            .setSourceStack(null);

        assertEquals(PoolType.UNMAPPED_GUEST, pool.getType());
    }

    @Test
    public void testPoolTypeNormalWithStack() {
        Pool pool = new Pool()
            .setSourceStack(new SourceStack(new Consumer(), "something"));

        assertEquals(PoolType.NORMAL, pool.getType());
    }

    @Test
    public void testSetSourceSubIdFromNull() {
        Pool pool = new Pool();

        assertNull(pool.getSourceSubscription());

        pool.setSubscriptionId("test_sub_id");

        assertNotNull(pool.getSourceSubscription());
        assertEquals("test_sub_id", pool.getSourceSubscription().getSubscriptionId());
        assertNull(pool.getSourceSubscription().getSubscriptionSubKey());
    }

    @Test
    public void testUpdateSourceSubIdFromValue() {
        SourceSubscription srcsub = new SourceSubscription()
            .setSubscriptionId("test_sub_id")
            .setSubscriptionSubKey("test_sub_key");

        Pool pool = new Pool()
            .setSourceSubscription(srcsub);

        assertEquals(srcsub, pool.getSourceSubscription());

        pool.setSubscriptionId("updated_sub_id");

        assertNotNull(pool.getSourceSubscription());
        assertEquals("updated_sub_id", pool.getSourceSubscription().getSubscriptionId());
        assertEquals("test_sub_key", pool.getSourceSubscription().getSubscriptionSubKey());
    }

    /**
     * Verifies that the SourceSubscription object is cleared when clearing the subscription ID
     * when the subscription subkey is also null.
     */
    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testClearSourceSubIdClearsSourceSub(String value) {
        SourceSubscription srcsub = new SourceSubscription()
            .setSubscriptionId("test_sub_id")
            .setSubscriptionSubKey(null);

        Pool pool = new Pool()
            .setSourceSubscription(srcsub);

        assertNotNull(pool.getSourceSubscription());

        pool.setSubscriptionId(value);
        assertNull(pool.getSourceSubscription());
    }

    @Test
    public void testSetSourceSubKeyFromNull() {
        Pool pool = new Pool();

        assertNull(pool.getSourceSubscription());

        pool.setSubscriptionSubKey("test_sub_key");

        assertNotNull(pool.getSourceSubscription());
        assertEquals("test_sub_key", pool.getSourceSubscription().getSubscriptionSubKey());
        assertNull(pool.getSourceSubscription().getSubscriptionId());
    }

    @Test
    public void testUpdateSourceSubKeyFromValue() {
        SourceSubscription srcsub = new SourceSubscription()
            .setSubscriptionId("test_sub_id")
            .setSubscriptionSubKey("test_sub_key");

        Pool pool = new Pool()
            .setSourceSubscription(srcsub);

        assertEquals(srcsub, pool.getSourceSubscription());

        pool.setSubscriptionSubKey("updated_sub_key");

        assertNotNull(pool.getSourceSubscription());
        assertEquals("updated_sub_key", pool.getSourceSubscription().getSubscriptionSubKey());
        assertEquals("test_sub_id", pool.getSourceSubscription().getSubscriptionId());
    }

    /**
     * Verifies that the SourceSubscription object is cleared when clearing the subscription subkey
     * when the subscription ID is also null.
     */
    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @NullAndEmptySource
    public void testClearSourceSubKeyClearsSourceSub(String value) {
        SourceSubscription srcsub = new SourceSubscription()
            .setSubscriptionId(null)
            .setSubscriptionSubKey("test_sub_key");

        Pool pool = new Pool()
            .setSourceSubscription(srcsub);

        assertNotNull(pool.getSourceSubscription());

        pool.setSubscriptionSubKey(value);
        assertNull(pool.getSourceSubscription());
    }

    @Test
    public void testIsDerivedPool() {
        Owner owner = new Owner();
        Product prod = new Product();

        Pool pool = new Pool()
            .setOwner(owner)
            .setProduct(prod)
            .setQuantity(1000L)
            .setAttribute(Pool.Attributes.DERIVED_POOL, "true");

        assertTrue(pool.isDerived());
    }

    @Test
    public void testIsNotDerivedPool() {
        Owner owner = new Owner();
        Product prod = new Product();

        Pool pool = new Pool()
            .setOwner(owner)
            .setProduct(prod)
            .setQuantity(1000L);

        assertFalse(pool.isDerived());
    }
}
