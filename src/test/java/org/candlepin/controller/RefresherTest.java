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

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.List;

import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.model.Subscription;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.util.Util;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * RefresherTest
 */
@RunWith(MockitoJUnitRunner.class)
public class RefresherTest {

    private CandlepinPoolManager poolManager;
    private SubscriptionServiceAdapter subAdapter;
    private PoolCurator poolCurator;

    private Refresher refresher;

    @Before
    public void setUp() {
        poolManager = mock(CandlepinPoolManager.class);
        subAdapter = mock(SubscriptionServiceAdapter.class);
        poolCurator = mock(PoolCurator.class);

        refresher = new Refresher(poolManager, subAdapter, poolCurator, false);
    }

    @Test
    public void testOwnerOnlyExaminedOnce() {
        Owner owner = mock(Owner.class);

        refresher.add(owner);
        refresher.add(owner);
        refresher.run();

        verify(poolManager, times(1)).refreshPoolsWithoutRegeneration(owner);
    }

    @Test
    public void testProductOnlyExaminedOnce() {
        Product product = mock(Product.class);

        refresher.add(product);
        refresher.add(product);
        refresher.run();

        verify(subAdapter, times(1)).getSubscriptions(product);
    }

    @Test
    public void testPoolOnlyExaminedOnceProductAndOwner() {
        Owner owner = mock(Owner.class);
        Product product = mock(Product.class);

        when(product.getId()).thenReturn("product id");

        Pool pool = new Pool();
        pool.setSubscriptionId("subId");
        pool.setOwner(owner);
        Subscription subscription = new Subscription();
        subscription.setId("subId");
        subscription.setOwner(owner);

        List<Pool> pools = Util.newList();
        pools.add(pool);
        List<Subscription> subscriptions = Util.newList();
        subscriptions.add(subscription);

        when(subAdapter.getSubscriptions(product)).thenReturn(subscriptions);
        when(subAdapter.getSubscriptions(owner)).thenReturn(subscriptions);
        when(subAdapter.getSubscription("subId")).thenReturn(subscription);

        when(poolCurator.listAvailableEntitlementPools(null, owner, null, null, false,
            false)).thenReturn(pools);
        when(poolCurator.lookupBySubscriptionId("subId")).thenReturn(pools);

        refresher.add(owner);
        refresher.add(product);
        refresher.run();

        verify(poolManager, times(1)).refreshPoolsWithoutRegeneration(owner);
        verify(poolManager, times(0)).updatePoolsForSubscription(any(List.class),
            any(Subscription.class));
    }

    @Test
    public void testPoolOnlyExaminedOnceTwoProducts() {
        Product product = mock(Product.class);
        Product product2 = mock(Product.class);

        when(product.getId()).thenReturn("product id");
        when(product2.getId()).thenReturn("product id 2");

        Pool pool = new Pool();
        pool.setSubscriptionId("subId");
        Subscription subscription = new Subscription();
        subscription.setId("subId");

        List<Pool> pools = Util.newList();
        pools.add(pool);
        List<Subscription> subscriptions = Util.newList();
        subscriptions.add(subscription);

        when(subAdapter.getSubscriptions(product)).thenReturn(subscriptions);
        when(subAdapter.getSubscriptions(product2)).thenReturn(subscriptions);
        when(subAdapter.getSubscription("subId")).thenReturn(subscription);
        when(poolCurator.lookupBySubscriptionId("subId")).thenReturn(pools);
        refresher.add(product);
        refresher.add(product2);
        refresher.run();

        verify(poolManager, times(1)).updatePoolsForSubscription(any(List.class),
            any(Subscription.class));
    }
}
