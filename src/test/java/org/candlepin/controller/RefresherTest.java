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
package org.candlepin.controller;

import static org.candlepin.model.SourceSubscription.PRIMARY_POOL_SUB_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.model.SourceSubscription;
import org.candlepin.model.dto.Subscription;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.exception.product.ProductServiceException;
import org.candlepin.service.exception.subscription.SubscriptionServiceException;
import org.candlepin.service.model.SubscriptionInfo;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Transactional;
import org.candlepin.util.Util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collection;
import java.util.Date;
import java.util.List;



@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class RefresherTest {
    @Mock
    private PoolManager poolManager;
    @Mock
    private SubscriptionServiceAdapter subAdapter;
    @Mock
    private OwnerCurator ownerCurator;
    @Mock
    private PoolCurator poolCurator;
    @Mock
    private PoolConverter poolConverter;

    private Refresher refresher;

    @BeforeEach
    public void setUp() {
        Transactional transaction = mock(Transactional.class);
        when(poolCurator.transactional()).thenReturn(transaction);
        when(transaction.allowExistingTransactions()).thenReturn(transaction);

        refresher = new Refresher(poolManager, subAdapter, ownerCurator, poolCurator, poolConverter)
            .setLazyCertificateRegeneration(false);
    }

    @Test
    public void testOwnerOnlyExaminedOnce() {
        Owner owner = TestUtil.createOwner();

        refresher.add(owner);
        refresher.add(owner);
        refresher.run();

        verify(poolManager, times(1))
            .refreshPoolsWithRegeneration(subAdapter, owner, false);
    }

    @Test
    public void testRefreshDateSet() {
        Date initial = Util.yesterday();
        Owner owner = TestUtil.createOwner();

        refresher.add(owner);
        refresher.run();

        verify(ownerCurator).merge(owner);

        assertNotNull(owner.getLastRefreshed());
        assertNotEquals(owner.getLastRefreshed(), initial);
        assertTrue(initial.before(owner.getLastRefreshed()));
    }

    @Test
    public void testProductOnlyExaminedOnce() {
        Product product = TestUtil.createProduct();

        refresher.add(product);
        refresher.add(product);
        refresher.run();

        verify(subAdapter, times(1)).getSubscriptionsByProductId(product.getId());
    }

    @Test
    public void testPoolOnlyExaminedOnceProductAndOwner() {
        Owner owner = TestUtil.createOwner();
        Product product = TestUtil.createProduct();
        product.setUuid("product uuid");

        Pool pool = new Pool();
        pool.setSourceSubscription(new SourceSubscription("subId", PRIMARY_POOL_SUB_KEY));
        pool.setOwner(owner);
        Subscription subscription = new Subscription();
        subscription.setId("subId");
        subscription.setOwner(owner);

        List<SubscriptionInfo> subscriptions = List.of(subscription);

        this.mockAdapterSubs(owner.getKey(), subscriptions);
        this.mockAdapterProductSubs(product.getId(), subscriptions);
        when(subAdapter.getSubscription("subId")).thenReturn(subscription);

        refresher.add(owner);
        refresher.add(product);
        refresher.run();

        verify(poolManager, times(1))
            .refreshPoolsWithRegeneration(subAdapter, owner, false);
        verify(poolManager, times(0)).updatePoolsForPrimaryPool(anyList(),
            any(Pool.class), eq(pool.getQuantity()), eq(false), anyMap());
    }

    @Test
    public void testGetSubscriptionServiceException() {
        Owner owner = TestUtil.createOwner();
        Product product = TestUtil.createProduct();
        when(subAdapter.getSubscriptionsByProductId(anyString()))
            .thenThrow(SubscriptionServiceException.class);

        refresher.add(owner);
        refresher.add(product);
        assertEquals(String.format("Unable to retrieve subscriptions by product id '%s'", product.getId()),
            assertThrows(RuntimeException.class, () -> refresher.run()).getMessage());
    }

    @Test
    public void testRefreshPoolsSubscriptionServiceException() {
        Owner owner = TestUtil.createOwner();
        Product product = TestUtil.createProduct();
        doThrow(new SubscriptionServiceException())
            .when(poolManager).refreshPoolsWithRegeneration(any(SubscriptionServiceAdapter.class),
                any(Owner.class), any(Boolean.class));

        refresher.add(owner);
        refresher.add(product);
        assertEquals(String.format("Unexpected subscription error for organization '%s'", owner.getKey()),
            assertThrows(RuntimeException.class, () -> refresher.run()).getMessage());
    }

    @Test
    public void testRefreshPoolsProductServiceException() {
        Owner owner = TestUtil.createOwner();
        Product product = TestUtil.createProduct();
        doThrow(new ProductServiceException())
            .when(poolManager).refreshPoolsWithRegeneration(any(SubscriptionServiceAdapter.class),
                any(Owner.class), any(Boolean.class));

        refresher.add(owner);
        refresher.add(product);
        assertEquals("Unexpected product error in pool refresh",
            assertThrows(RuntimeException.class, () -> refresher.run()).getMessage());
    }

    @Test
    public void testPoolOnlyExaminedOnceTwoProducts() {
        Product product = TestUtil.createProduct();
        Product product2 = TestUtil.createProduct();
        product.setUuid("product id");
        product2.setUuid("product id 2");

        Pool pool = new Pool();
        pool.setSourceSubscription(new SourceSubscription("subId", PRIMARY_POOL_SUB_KEY));
        Subscription subscription = new Subscription();
        subscription.setId("subId");
        Owner owner = TestUtil.createOwner();
        subscription.setOwner(owner);

        List<SubscriptionInfo> subscriptions = List.of(subscription);

        this.mockAdapterProductSubs(product.getId(), subscriptions);
        this.mockAdapterProductSubs(product2.getId(), subscriptions);
        when(subAdapter.getSubscription("subId")).thenReturn(subscription);

        Pool mainPool = TestUtil.copyFromSub(subscription);
        when(poolConverter.convertToPrimaryPool(subscription)).thenReturn(mainPool);
        refresher.add(product);
        refresher.add(product2);
        refresher.run();

        verify(poolManager, times(1)).refreshPoolsForPrimaryPool(eq(mainPool), eq(true), eq(false),
            anyMap());
    }

    protected void mockAdapterSubs(String input, Collection<? extends SubscriptionInfo> output) {
        doAnswer(iom -> output).when(this.subAdapter).getSubscriptions(input);
    }

    protected void mockAdapterProductSubs(String input, Collection<? extends SubscriptionInfo> output) {
        doAnswer(iom -> output).when(this.subAdapter).getSubscriptionsByProductId(input);
    }

}
