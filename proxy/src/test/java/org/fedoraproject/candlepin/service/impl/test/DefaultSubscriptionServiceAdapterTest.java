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
package org.fedoraproject.candlepin.service.impl.test;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.config.ConfigProperties;
import org.fedoraproject.candlepin.model.Consumer;

import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.service.SubscriptionServiceAdapter;
import org.fedoraproject.candlepin.service.impl.DefaultSubscriptionServiceAdapter;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class DefaultSubscriptionServiceAdapterTest extends DatabaseTestFixture {
    
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
    public void activationPrefix() {
        Config config = Mockito.mock(Config.class);
        Mockito.when(config.getString(ConfigProperties.ACTIVATION_DEBUG_PREFIX))
                .thenReturn("mega");

        Consumer consumer = Mockito.mock(Consumer.class);
        Mockito.when(consumer.getName()).thenReturn("megaman");

        SubscriptionServiceAdapter adapter =
                new DefaultSubscriptionServiceAdapter(subCurator, config, null, null);

        assertTrue(adapter.canActivateSubscription(consumer));
    }

    @Test
    public void activationPrefixFailure() {
        Config config = Mockito.mock(Config.class);
        Mockito.when(config.getString(ConfigProperties.ACTIVATION_DEBUG_PREFIX))
                .thenReturn("mega");

        Consumer consumer = Mockito.mock(Consumer.class);
        Mockito.when(consumer.getName()).thenReturn("superman");

        SubscriptionServiceAdapter adapter =
                new DefaultSubscriptionServiceAdapter(subCurator, config, null, null);

        assertFalse(adapter.canActivateSubscription(consumer));
    }

    @Test
    public void activationPrefixEmpty() {
        Config config = Mockito.mock(Config.class);
        Mockito.when(config.getString(ConfigProperties.ACTIVATION_DEBUG_PREFIX))
                .thenReturn("");

        Consumer consumer = Mockito.mock(Consumer.class);
        Mockito.when(consumer.getName()).thenReturn("anything");

        SubscriptionServiceAdapter adapter =
                new DefaultSubscriptionServiceAdapter(subCurator, config, null, null);

        assertFalse(adapter.canActivateSubscription(consumer));
    }

    @Test
    public void activationPrefixNull() {
        Config config = Mockito.mock(Config.class);
        Mockito.when(config.getString(ConfigProperties.ACTIVATION_DEBUG_PREFIX))
                .thenReturn(null);

        Consumer consumer = Mockito.mock(Consumer.class);
        Mockito.when(consumer.getName()).thenReturn("anything");

        SubscriptionServiceAdapter adapter =
                new DefaultSubscriptionServiceAdapter(subCurator, config, null, null);

        assertFalse(adapter.canActivateSubscription(consumer));
    }

}
