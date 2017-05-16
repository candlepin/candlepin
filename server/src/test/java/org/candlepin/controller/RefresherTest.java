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
package org.candlepin.controller;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.SourceSubscription;
import org.candlepin.model.dto.ProductData;
import org.candlepin.model.dto.Subscription;
import org.candlepin.service.OwnerServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.util.Util;
import org.candlepin.test.TestUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.List;
import java.util.Map;



/**
 * RefresherTest
 */
@RunWith(MockitoJUnitRunner.class)
public class RefresherTest {

    private CandlepinPoolManager poolManager;
    private OwnerServiceAdapter ownerAdapter;
    private SubscriptionServiceAdapter subAdapter;
    private OwnerManager ownerManager;

    private Refresher refresher;

    @Before
    public void setUp() {
        poolManager = mock(CandlepinPoolManager.class);
        subAdapter = mock(SubscriptionServiceAdapter.class);
        ownerManager = mock(OwnerManager.class);

        refresher = new Refresher(poolManager, subAdapter, ownerAdapter, ownerManager, false);
    }

    @Test
    public void testOwnerOnlyExaminedOnce() {
        Owner owner = TestUtil.createOwner();

        refresher.add(owner);
        refresher.add(owner);
        refresher.run();

        verify(poolManager, times(1)).refreshPoolsWithRegeneration(eq(subAdapter), eq(owner), eq(false));
    }

    @Test
    public void testProductOnlyExaminedOnce() {
        Product product = mock(Product.class);
        ProductData productData = mock(ProductData.class);

        when(product.toDTO()).thenReturn(productData);

        refresher.add(product);
        refresher.add(product);
        refresher.run();

        verify(subAdapter, times(1)).getSubscriptions(eq(productData));
    }

    @Test
    public void testPoolOnlyExaminedOnceProductAndOwner() {
        Owner owner = TestUtil.createOwner();
        Product product = mock(Product.class);
        ProductData productData = mock(ProductData.class);

        when(product.getUuid()).thenReturn("product id");
        when(product.toDTO()).thenReturn(productData);

        Pool pool = new Pool();
        pool.setSourceSubscription(new SourceSubscription("subId", "master"));
        pool.setOwner(owner);
        Subscription subscription = new Subscription();
        subscription.setId("subId");
        subscription.setOwner(owner);

        List<Pool> pools = Util.newList();
        pools.add(pool);
        List<Subscription> subscriptions = Util.newList();
        subscriptions.add(subscription);

        when(subAdapter.getSubscriptions(eq(productData))).thenReturn(subscriptions);
        when(subAdapter.getSubscriptions(owner)).thenReturn(subscriptions);
        when(subAdapter.getSubscription("subId")).thenReturn(subscription);

        when(poolManager.lookupBySubscriptionId(owner, "subId")).thenReturn(pools);

        refresher.add(owner);
        refresher.add(product);
        refresher.run();

        verify(poolManager, times(1)).refreshPoolsWithRegeneration(eq(subAdapter), eq(owner), eq(false));
        verify(poolManager, times(0)).updatePoolsForMasterPool(any(List.class),
            any(Pool.class), eq(pool.getQuantity()), eq(false), any(Map.class));
    }

    @Test
    public void testPoolOnlyExaminedOnceTwoProducts() {
        Product product = mock(Product.class);
        Product product2 = mock(Product.class);
        ProductData productData = mock(ProductData.class);
        ProductData productData2 = mock(ProductData.class);

        when(product.getUuid()).thenReturn("product id");
        when(product2.getUuid()).thenReturn("product id 2");
        when(product.toDTO()).thenReturn(productData);
        when(product2.toDTO()).thenReturn(productData2);

        Pool pool = new Pool();
        pool.setSourceSubscription(new SourceSubscription("subId", "master"));
        Subscription subscription = new Subscription();
        subscription.setId("subId");
        Owner owner = TestUtil.createOwner();
        subscription.setOwner(owner);

        List<Pool> pools = Util.newList();
        pools.add(pool);
        List<Subscription> subscriptions = Util.newList();
        subscriptions.add(subscription);

        when(subAdapter.getSubscriptions(eq(productData))).thenReturn(subscriptions);
        when(subAdapter.getSubscriptions(eq(productData2))).thenReturn(subscriptions);
        when(subAdapter.getSubscription("subId")).thenReturn(subscription);
        when(poolManager.lookupBySubscriptionId(owner, "subId")).thenReturn(pools);

        Pool mainPool = TestUtil.copyFromSub(subscription);
        when(poolManager.convertToMasterPool(subscription)).thenReturn(mainPool);
        refresher.add(product);
        refresher.add(product2);
        refresher.run();

        verify(poolManager, times(1)).refreshPoolsForMasterPool(eq(mainPool), eq(true), eq(false),
            any(Map.class));
    }
}
