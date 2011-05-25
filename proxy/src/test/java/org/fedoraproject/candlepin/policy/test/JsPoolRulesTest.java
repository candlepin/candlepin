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
package org.fedoraproject.candlepin.policy.test;

import org.fedoraproject.candlepin.auth.UserPrincipal;
import org.fedoraproject.candlepin.model.Owner;
import java.io.InputStream;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import org.fedoraproject.candlepin.controller.PoolManager;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.PoolAttribute;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductAttribute;
import org.fedoraproject.candlepin.model.ProvidedProduct;
import org.fedoraproject.candlepin.model.Rules;
import org.fedoraproject.candlepin.model.RulesCurator;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.policy.PoolRules;
import org.fedoraproject.candlepin.policy.js.JsRulesProvider;
import org.fedoraproject.candlepin.policy.js.pool.JsPoolRules;
import org.fedoraproject.candlepin.policy.js.pool.PoolUpdate;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;
import org.fedoraproject.candlepin.test.TestUtil;
import org.fedoraproject.candlepin.util.Util;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;


/**
 * JsPoolRulesTest: Tests for the default rules.
 */
@RunWith(MockitoJUnitRunner.class)
public class JsPoolRulesTest {
    
    private PoolRules poolRules;
    
    private static final String RULES_FILE = "/rules/default-rules.js";
    
    @Mock private RulesCurator rulesCuratorMock;
    @Mock private ProductServiceAdapter productAdapterMock;
    @Mock private PoolManager poolManagerMock;

    private UserPrincipal principal;
    private Owner owner;

    @Before
    public void setUp() {

        // Load the default production rules:
        InputStream is = this.getClass().getResourceAsStream(RULES_FILE);
        Rules rules = new Rules(Util.readFile(is));

        when(rulesCuratorMock.getUpdated()).thenReturn(new Date());
        when(rulesCuratorMock.getRules()).thenReturn(rules);
        
        JsRulesProvider provider = new JsRulesProvider(rulesCuratorMock);
        poolRules = new JsPoolRules(provider.get(), poolManagerMock, productAdapterMock);
        principal = TestUtil.createOwnerPrincipal();
        owner = principal.getOwners().get(0);
    }
    
    private Pool copyFromSub(Subscription sub) {
        Pool p = new Pool(sub.getOwner(), sub.getProduct().getId(), 
            sub.getProduct().getName(), new HashSet<ProvidedProduct>(), 
            sub.getQuantity(), sub.getStartDate(),
            sub.getEndDate(), sub.getContractNumber(), sub.getAccountNumber());
        p.setSubscriptionId(sub.getId());
        return p;
    }
    
    @Test
    public void providedProductsChanged() {
        // Subscription with two provided products:
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct());
        Product product1 = TestUtil.createProduct();
        Product product2 = TestUtil.createProduct();
        Product product3 = TestUtil.createProduct();
        s.getProvidedProducts().add(product1);
        s.getProvidedProducts().add(product2);

        // Setup a pool with a single (different) provided product:
        Pool p = copyFromSub(s);
        p.getProvidedProducts().clear();
        p.getProvidedProducts().add(
            new ProvidedProduct(product3.getId(), product3.getName(), p));
        
        List<Pool> existingPools = new java.util.LinkedList<Pool>();
        existingPools.add(p);
        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools);
        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertTrue(update.getProductsChanged());
        assertFalse(update.getDatesChanged());
        assertFalse(update.getQuantityChanged());
    }
    
    @Test
    public void productNameChanged() {
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct());

        // Setup a pool with a single (different) provided product:
        Pool p = copyFromSub(s);
        p.setProductName("somethingelse");
        
        List<Pool> existingPools = new java.util.LinkedList<Pool>();
        existingPools.add(p);
        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools);
        
        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertTrue(update.getProductsChanged());
        assertFalse(update.getDatesChanged());
        assertFalse(update.getQuantityChanged());
        assertEquals(s.getProduct().getName(), update.getPool().getProductName());
    }

    @Test
    public void datesNameChanged() {
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct());

        // Setup a pool with a single (different) provided product:
        Pool p = copyFromSub(s);
        p.setEndDate(new Date());
        
        List<Pool> existingPools = new java.util.LinkedList<Pool>();
        existingPools.add(p);
        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools);
        
        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertFalse(update.getProductsChanged());
        assertTrue(update.getDatesChanged());
        assertFalse(update.getQuantityChanged());
        assertEquals(s.getEndDate(), update.getPool().getEndDate());
    }

    @Test
    public void quantityChanged() {
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct());

        // Setup a pool with a single (different) provided product:
        Pool p = copyFromSub(s);
        p.setQuantity(2000L);
        
        List<Pool> existingPools = new java.util.LinkedList<Pool>();
        existingPools.add(p);
        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools);
        
        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertFalse(update.getProductsChanged());
        assertFalse(update.getDatesChanged());
        assertTrue(update.getQuantityChanged());
        assertEquals(s.getQuantity(), update.getPool().getQuantity());
    }
    
    @Test
    public void virtOnlyQuantityChanged() {
        Subscription s = TestUtil.createSubscription(owner, TestUtil.createProduct());
        s.getProduct().addAttribute(new ProductAttribute("virt_limit", "5"));
        s.setQuantity(10L);

        // Setup a pool with a single (different) provided product:
        Pool p = copyFromSub(s);
        p.addAttribute(new PoolAttribute("virt_only", "true"));
        p.setQuantity(40L);
        
        List<Pool> existingPools = new java.util.LinkedList<Pool>();
        existingPools.add(p);
        List<PoolUpdate> updates = this.poolRules.updatePools(s, existingPools);
        
        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertFalse(update.getProductsChanged());
        assertFalse(update.getDatesChanged());
        assertTrue(update.getQuantityChanged());
        assertEquals(new Long(50), update.getPool().getQuantity());
    }
}
