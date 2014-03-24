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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.candlepin.model.Branding;
import org.candlepin.model.Owner;
import org.candlepin.model.Product;
import org.candlepin.model.Subscription;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.impl.DefaultSubscriptionServiceAdapter;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

/*
 * Yes, this is called SubscriptionCuratorTest, but it uses the
 * SubscriptionServiceAdapter. All the calls  that are in here on the
 * adapter are just thin wrappers around the curator.
 */
public class SubscriptionCuratorTest extends DatabaseTestFixture {

    private Owner owner;
    private Product parentProduct;
    private Product childProduct;
    private Subscription s1;
    private SubscriptionServiceAdapter adapter;

    @Before
    public void setUp() {
        owner = createOwner();
        ownerCurator.create(owner);
        parentProduct = TestUtil.createProduct();

        childProduct = TestUtil.createProduct();
        productCurator.create(childProduct);
        productCurator.create(parentProduct);

        Set<Product> providedProducts = new HashSet<Product>();
        providedProducts.add(childProduct);
        s1 = new Subscription(owner, parentProduct, providedProducts, 100L,
                TestUtil.createDate(2010, 2, 8), TestUtil.createDate(2050, 2, 8),
                TestUtil.createDate(2010, 2, 1));

        subCurator.create(s1);

        adapter = injector.getInstance(DefaultSubscriptionServiceAdapter.class);
    }

    @Test
    public void testGetSubscriptions() {
        List<Subscription> subs = adapter.getSubscriptions(owner,
            parentProduct.getId().toString());
        assertEquals(1, subs.size());
    }

    @Test
    public void testGetSubscriptionsNoneExist() {
        Owner owner2 = createOwner();
        ownerCurator.create(owner2);
        List<Subscription> subs = adapter.getSubscriptions(owner2,
            parentProduct.getId().toString());
        assertEquals(0, subs.size());
    }

    @Test
    public void testGetSubscription() {
        Subscription s = adapter.getSubscription(s1.getId());
        assertNotNull(s);
        assertEquals(Long.valueOf(100), s.getQuantity());

        s = adapter.getSubscription("-15");
        assertNull(s);
    }


    @Test
    public void testGetAllSubscriptionsSince() {
        List<Subscription> subs = adapter.getSubscriptionsSince(
                TestUtil.createDate(2010, 1, 20));
        assertEquals(1, subs.size());
        assertEquals(s1.getId(), subs.get(0).getId());

        subs = adapter.getSubscriptionsSince(
                TestUtil.createDate(2010, 2, 2));
        assertEquals(0, subs.size());
    }

    @Test
    public void testGetSubscriptionsSince() {
        List<Subscription> subs = adapter.getSubscriptionsSince(owner,
            TestUtil.createDate(2010, 1, 20));
        assertEquals(1, subs.size());
        assertEquals(s1.getId(), subs.get(0).getId());
    }

    @Test
    public void testGetSubscriptionsProviding() {
        List<Subscription> subIds = adapter.getSubscriptions(owner,
            parentProduct.getId());
        assertEquals(1, subIds.size());

        subIds = adapter.getSubscriptions(owner, childProduct.getId());
        assertEquals(1, subIds.size());
    }

    @Test
    public void testLookupSubscriptionByProduct() {
        Owner owner = createOwner();
        Product product = TestUtil.createProduct();
        productCurator.create(product);

        Subscription sub = TestUtil.createSubscription(owner, product);
        adapter.createSubscription(sub);

        List<Subscription> results = adapter.getSubscriptions(product);

        assertEquals(1, results.size());
        assertTrue(results.contains(sub));
    }

    @Test
    public void testLookupMultipleSubscriptionsByProduct() {
        Owner owner = createOwner();

        Product product = TestUtil.createProduct();
        productCurator.create(product);

        Subscription sub = TestUtil.createSubscription(owner, product);
        adapter.createSubscription(sub);

        Subscription sub2 = TestUtil.createSubscription(owner, product);
        adapter.createSubscription(sub2);

        List<Subscription> results = adapter.getSubscriptions(product);
        assertEquals(2, results.size());
        assertTrue(results.contains(sub));
        assertTrue(results.contains(sub2));
    }

    @Test
    public void testLookupMultipleSubscriptionsByProductManyOwners() {
        Owner owner = createOwner();
        Owner owner2 = createOwner();

        Product product = TestUtil.createProduct();
        productCurator.create(product);

        Subscription sub = TestUtil.createSubscription(owner, product);
        adapter.createSubscription(sub);

        Subscription sub2 = TestUtil.createSubscription(owner2, product);
        adapter.createSubscription(sub2);

        List<Subscription> results = adapter.getSubscriptions(product);
        assertEquals(2, results.size());
        assertTrue(results.contains(sub));
        assertTrue(results.contains(sub2));
    }

    @Test
    public void testLookupSubscriptionByProductProvidedProduct() {
        Owner owner = createOwner();

        Product product = TestUtil.createProduct();
        Product provided = TestUtil.createProduct();
        productCurator.create(product);
        productCurator.create(provided);

        Set<Product> providedProducts = new HashSet<Product>();
        providedProducts.add(provided);
        Subscription sub = TestUtil.createSubscription(owner, product, providedProducts);
        adapter.createSubscription(sub);

        List<Subscription> results = adapter.getSubscriptions(provided);
        assertEquals(1, results.size());
        assertTrue(results.contains(sub));
    }

    /*
     * For the degenerate case where we have the same product as both provided and
     * the main product, make sure the row only comes back once.
     *
     * Our schema should prevent the same product from being provided twice.
     */
    @Test
    public void testLookupSubscriptionGivesUniqueResult() {
        Owner owner = createOwner();

        Product product = TestUtil.createProduct();
        productCurator.create(product);

        Set<Product> providedProducts = new HashSet<Product>();
        providedProducts.add(product);
        Subscription sub = TestUtil.createSubscription(owner, product, providedProducts);
        adapter.createSubscription(sub);

        List<Subscription> results = adapter.getSubscriptions(product);
        assertEquals(1, results.size());
        assertTrue(results.contains(sub));
    }

    @Test
    public void testLookupSubscriptionByProductMixedMainAndProvidedProduct() {
        Owner owner = createOwner();

        Product product = TestUtil.createProduct();
        Product product2 = TestUtil.createProduct();
        Product product3 = TestUtil.createProduct();
        productCurator.create(product);
        productCurator.create(product2);
        productCurator.create(product3);

        Set<Product> providedProducts = new HashSet<Product>();
        providedProducts.add(product);

        Subscription sub = TestUtil.createSubscription(owner, product2, providedProducts);
        adapter.createSubscription(sub);

        Set<Product> providedProducts2 = new HashSet<Product>();
        providedProducts2.add(product3);
        Subscription sub2 = TestUtil.createSubscription(owner, product, providedProducts2);
        adapter.createSubscription(sub2);

        List<Subscription> results = adapter.getSubscriptions(product);
        assertEquals(2, results.size());
        assertTrue(results.contains(sub));
        assertTrue(results.contains(sub2));
    }

    @Test
    public void testLookupSubscriptionByProductDoesNotIncludeExtraSubscriptions() {
        Owner owner = createOwner();

        Product product = TestUtil.createProduct();
        Product product2 = TestUtil.createProduct();
        Product product3 = TestUtil.createProduct();
        productCurator.create(product);
        productCurator.create(product2);
        productCurator.create(product3);

        Set<Product> providedProducts = new HashSet<Product>();
        providedProducts.add(product);

        Subscription sub = TestUtil.createSubscription(owner, product2, providedProducts);
        adapter.createSubscription(sub);

        Set<Product> providedProducts2 = new HashSet<Product>();
        providedProducts2.add(product3);
        Subscription sub2 = TestUtil.createSubscription(owner, product, providedProducts2);
        adapter.createSubscription(sub2);

        List<Subscription> results = adapter.getSubscriptions(product3);
        assertEquals(1, results.size());
        assertTrue(results.contains(sub2));
    }

    @Test
    public void testAddBranding() {
        s1.getBranding().add(new Branding("8000", "OS", "Awesome OS Branded"));
        subCurator.merge(s1);
        Subscription lookedUp = subCurator.find(s1.getId());
        assertEquals(1, lookedUp.getBranding().size());
        assertEquals("8000", lookedUp.getBranding().iterator().next().getProductId());
    }

    @Test
    public void testRemoveBranding() {
        s1.getBranding().add(new Branding("8000", "OS", "Awesome OS Branded"));
        subCurator.merge(s1);
        Subscription lookedUp = subCurator.find(s1.getId());
        assertEquals(1, lookedUp.getBranding().size());

        lookedUp.getBranding().clear();
        subCurator.merge(lookedUp);

        lookedUp = subCurator.find(s1.getId());
        assertEquals(0, lookedUp.getBranding().size());
    }

}
