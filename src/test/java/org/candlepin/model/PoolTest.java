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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import org.candlepin.model.Pool.PoolType;
import org.candlepin.test.DatabaseTestFixture;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;

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

    @Test
    public void testPoolWithNullManagedFieldInDBIsNotFlaggedAsManaged() throws Exception {
        // This test verifies that if the Pool.managed flag somehow ends up null in the DB, we don't crash
        // out while trying to access it.

        Owner owner = new Owner();
        Product prod = new Product();

        Pool pool = new Pool()
            .setOwner(owner)
            .setProduct(prod)
            .setQuantity(1000L);

        // Use reflection to set the field to null, since we have no normal way of doing that
        Field managedField = Pool.class.getDeclaredField("managed");
        managedField.setAccessible(true);
        managedField.set(pool, null);

        // Assert default value of "false" when managed ends up as a null
        assertFalse(pool.isManaged());
    }

    @Nested
    @DisplayName("Field Access Tests")
    public class FieldAccessTest extends DatabaseTestFixture {

        @Test
        public void testGetProductUuidWithDesyncedProduct() throws Exception {
            Owner owner = this.createOwner();
            Product product = this.createProduct();
            Pool pool = this.createPool(owner, product);

            // We don't want to persist the upcoming changes to the pool, so commit now
            this.commitTransaction();
            this.getEntityManager().clear();

            Field prodField = pool.getClass().getDeclaredField("product");
            prodField.setAccessible(true);
            prodField.set(pool, null);

            assertNull(pool.getProductUuid());
        }

        @Test
        public void testGetProductUuidWithNullProductUuidAndHydratedProduct() throws Exception {
            Owner owner = this.createOwner();
            Product product1 = this.createProduct();
            Product product2 = this.createProduct();
            Pool pool1 = this.createPool(owner, product1);
            Pool pool2 = this.createPool(owner, product2);

            this.commitTransaction();
            this.getEntityManager().clear();

            Pool retrievedPool1 = this.poolCurator.get(pool1.getId());
            Pool retrievedPool2 = this.poolCurator.get(pool2.getId());
            assertFalse(Hibernate.isInitialized(retrievedPool1.getProduct()));
            assertFalse(Hibernate.isInitialized(retrievedPool2.getProduct()));

            // Hydrate pool2's product
            Hibernate.initialize(retrievedPool2.getProduct());
            Product prodSpy = spy(retrievedPool2.getProduct());
            retrievedPool1.setProduct(prodSpy);
            assertTrue(Hibernate.isInitialized(retrievedPool1.getProduct()));

            String actual = retrievedPool1.getProductUuid();
            assertEquals(product2.getUuid(), actual);
            verify(prodSpy).getUuid();
        }

        @Test
        public void testGetProductUuidWithNullProductUuidAndUnhydratedProduct() throws Exception {
            Owner owner = this.createOwner();
            Product product1 = this.createProduct();
            Product product2 = this.createProduct();
            Pool pool1 = this.createPool(owner, product1);
            Pool pool2 = this.createPool(owner, product2);

            this.commitTransaction();
            this.getEntityManager().clear();

            Pool retrievedPool1 = this.poolCurator.get(pool1.getId());
            Pool retrievedPool2 = this.poolCurator.get(pool2.getId());
            assertFalse(Hibernate.isInitialized(retrievedPool1.getProduct()));
            assertFalse(Hibernate.isInitialized(retrievedPool2.getProduct()));

            // Should read the product uuid value from the productuuid field and not from the Product object
            String actual = retrievedPool1.getProductUuid();
            assertEquals(product1.getUuid(), actual);
            assertFalse(Hibernate.isInitialized(retrievedPool1.getProduct()));

            // Set the product to another proxy object.
            Product prodSpy = spy(retrievedPool2.getProduct());
            retrievedPool1.setProduct(prodSpy);
            assertFalse(Hibernate.isInitialized(retrievedPool1.getProduct()));

            // Perform a lazy lookup on the Product object to retrieve the uuid
            actual = retrievedPool1.getProductUuid();
            assertEquals(product2.getUuid(), actual);
            verify(prodSpy).getUuid();
            // The product's uuid is cached in the ByteBuddyInterceptor on the Proxy object and that is used
            // instead of a lazy lookup.
            assertFalse(Hibernate.isInitialized(retrievedPool1.getProduct()));
        }

        @Test
        public void testGetProductUuidWithPopulatedProductUuidAndUnhydratedProduct() throws Exception {
            Owner owner = this.createOwner();
            Product product = this.createProduct();
            Pool pool = this.createPool(owner, product);

            this.commitTransaction();
            this.getEntityManager().clear();

            Pool retrievedPool = this.poolCurator.get(pool.getId());
            Product prodSpy = spy(retrievedPool.getProduct());
            Field prodField = pool.getClass().getDeclaredField("product");
            prodField.setAccessible(true);
            prodField.set(pool, prodSpy);

            String actual = retrievedPool.getProductUuid();

            assertEquals(product.getUuid(), actual);
            verify(prodSpy, never()).getUuid();
            assertFalse(Hibernate.isInitialized(retrievedPool.getProduct()));
        }

        @Test
        public void testGetOwnerKey() {
            Owner owner = this.createOwner();
            Product product = this.createProduct();
            Pool pool = this.createPool(owner, product);

            assertEquals(owner.getKey(), pool.getOwnerKey());
        }

        @Test
        public void testGetOwnerKeyWithNoOwner() {
            Pool pool = new Pool();

            assertNull(pool.getOwnerKey());
        }
    }
}
