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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.SourceSubscription;
import org.candlepin.model.dto.Subscription;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.model.SubscriptionInfo;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;



/**
 * RefresherTest
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class RefresherTest {

    private CandlepinPoolManager poolManager;
    private SubscriptionServiceAdapter subAdapter;
    private ProductServiceAdapter prodAdapter;
    private OwnerManager ownerManager;

    private Refresher refresher;

    @BeforeEach
    public void setUp() {
        poolManager = mock(CandlepinPoolManager.class);
        subAdapter = mock(SubscriptionServiceAdapter.class);
        prodAdapter = mock(ProductServiceAdapter.class);
        ownerManager = mock(OwnerManager.class);

        refresher = new Refresher(poolManager, subAdapter, prodAdapter, ownerManager, false);
    }

    @Test
    public void testOwnerOnlyExaminedOnce() {
        Owner owner = TestUtil.createOwner();

        refresher.add(owner);
        refresher.add(owner);
        refresher.run();

        verify(poolManager, times(1))
            .refreshPoolsWithRegeneration(eq(subAdapter), eq(prodAdapter), eq(owner), eq(false));
    }

    @Test
    public void testRefreshDateSet() {
        Owner owner = TestUtil.createOwner();

        refresher.add(owner);
        refresher.run();

        verify(ownerManager).updateRefreshDate(owner);
    }

    @Test
    public void testProductOnlyExaminedOnce() {
        Product product = TestUtil.createProduct();

        refresher.add(product);
        refresher.add(product);
        refresher.run();

        verify(subAdapter, times(1)).getSubscriptionsByProductId(eq(product.getId()));
    }

    @Test
    public void testPoolOnlyExaminedOnceProductAndOwner() {
        Owner owner = TestUtil.createOwner();
        Product product = TestUtil.createProduct();
        product.setUuid("product uuid");

        Pool pool = new Pool();
        pool.setSourceSubscription(new SourceSubscription("subId", "master"));
        pool.setOwner(owner);
        Subscription subscription = new Subscription();
        subscription.setId("subId");
        subscription.setOwner(owner);

        List<Pool> pools = Arrays.asList(pool);
        List<SubscriptionInfo> subscriptions = Arrays.asList(subscription);

        this.mockAdapterSubs(owner.getKey(), subscriptions);
        this.mockAdapterProductSubs(product.getId(), subscriptions);
        when(subAdapter.getSubscription("subId")).thenReturn(subscription);

        when(poolManager.getBySubscriptionId(owner, "subId")).thenReturn(pools);

        refresher.add(owner);
        refresher.add(product);
        refresher.run();

        verify(poolManager, times(1))
            .refreshPoolsWithRegeneration(eq(subAdapter), eq(prodAdapter), eq(owner), eq(false));
        verify(poolManager, times(0)).updatePoolsForMasterPool(any(List.class),
            any(Pool.class), eq(pool.getQuantity()), eq(false), any(Map.class));
    }

    @Test
    public void testPoolOnlyExaminedOnceTwoProducts() {
        Product product = TestUtil.createProduct();
        Product product2 = TestUtil.createProduct();
        product.setUuid("product id");
        product2.setUuid("product id 2");

        Pool pool = new Pool();
        pool.setSourceSubscription(new SourceSubscription("subId", "master"));
        Subscription subscription = new Subscription();
        subscription.setId("subId");
        Owner owner = TestUtil.createOwner();
        subscription.setOwner(owner);

        List<Pool> pools = Arrays.asList(pool);
        List<SubscriptionInfo> subscriptions = Arrays.asList(subscription);

        this.mockAdapterProductSubs(product.getId(), subscriptions);
        this.mockAdapterProductSubs(product2.getId(), subscriptions);
        when(subAdapter.getSubscription("subId")).thenReturn(subscription);
        when(poolManager.getBySubscriptionId(owner, "subId")).thenReturn(pools);

        Pool mainPool = TestUtil.copyFromSub(subscription);
        when(poolManager.convertToMasterPool(subscription)).thenReturn(mainPool);
        refresher.add(product);
        refresher.add(product2);
        refresher.run();

        verify(poolManager, times(1)).refreshPoolsForMasterPool(eq(mainPool), eq(true), eq(false),
            any(Map.class));
    }

    protected void mockAdapterSubs(String input, Collection<? extends SubscriptionInfo> output) {
        doAnswer(iom -> output).when(this.subAdapter).getSubscriptions(eq(input));
    }

    protected void mockAdapterProductSubs(String input, Collection<? extends SubscriptionInfo> output) {
        doAnswer(iom -> output).when(this.subAdapter).getSubscriptionsByProductId(eq(input));
    }

}
