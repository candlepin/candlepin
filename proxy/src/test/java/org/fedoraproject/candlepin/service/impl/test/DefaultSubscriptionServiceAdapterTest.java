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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.List;

import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.model.SubscriptionToken;
import org.fedoraproject.candlepin.service.SubscriptionServiceAdapter;
import org.fedoraproject.candlepin.service.impl.DefaultSubscriptionServiceAdapter;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.fedoraproject.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

public class DefaultSubscriptionServiceAdapterTest extends DatabaseTestFixture {
    
    private Owner owner;
    private Product p;
    private Subscription s1;
    private SubscriptionServiceAdapter adapter;

    @Before
    public void setUp() {
        owner = createOwner();
        ownerCurator.create(owner);
        p = TestUtil.createProduct();
        productCurator.create(p);
        
        
        s1 = new Subscription(owner, p.getId().toString(), new Long(100), 
                TestUtil.createDate(2010, 2, 8), TestUtil.createDate(2050, 2, 8),
                TestUtil.createDate(2010, 2, 1));
        subCurator.create(s1);
        
        adapter = injector.getInstance(DefaultSubscriptionServiceAdapter.class);
    }
    
    @Test
    public void testGetSubscriptions() {
        List<Subscription> subs = adapter.getSubscriptions(owner, p.getId().toString());
        assertEquals(1, subs.size());
    }

    @Test
    public void testGetSubscriptionsNoneExist() {
        Owner owner2 = createOwner();
        ownerCurator.create(owner2);
        List<Subscription> subs = adapter.getSubscriptions(owner2, p.getId().toString());
        assertEquals(0, subs.size());
    }
    
    @Test
    public void testGetSubscription() {
        Subscription s = adapter.getSubscription(s1.getId());
        assertNotNull(s);
        assertEquals(new Long(100), s.getQuantity());
        
        s = adapter.getSubscription(new Long(-15));
        assertNull(s);
    }
    
    @Test
    public void testGetSubscriptionByBadToken() {
        List<Subscription> s = adapter.getSubscriptionForToken("NotARealToken");
        
        //assertNull(s);
        assertEquals(s.size(), 0);
    }
    
    @Test
    public void TestGetSubscriptionByRegnum() {
        
        Subscription sub = TestUtil.createSubscription();
        SubscriptionToken st = TestUtil.createSubscriptionToken();

        st.setSubscription(s1);
//        s1.
//        s1.getToken();
        
        
        List<Subscription> s = adapter.getSubscriptionForToken("this_is_a_test_token");
        
        
        assertEquals(s.get(0).getProductId(), s1.getProductId());
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


}
