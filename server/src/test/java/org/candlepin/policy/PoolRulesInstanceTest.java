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
package org.candlepin.policy;

import static org.junit.Assert.*;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.model.Subscription;
import org.candlepin.policy.js.ProductCache;
import org.candlepin.policy.js.pool.PoolRules;
import org.candlepin.policy.js.pool.PoolUpdate;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.InputStream;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;


/**
 * JsPoolRulesInstanceTest: Tests for refresh pools code on instance based subscriptions.
 */
@RunWith(MockitoJUnitRunner.class)
public class PoolRulesInstanceTest {

    private PoolRules poolRules;

    @Mock private RulesCurator rulesCuratorMock;
    @Mock private ProductServiceAdapter productAdapterMock;
    @Mock private PoolManager poolManagerMock;
    @Mock private Configuration configMock;
    @Mock private EntitlementCurator entCurMock;

    private ProductCache productCache;

    @Before
    public void setUp() {
        InputStream is = this.getClass().getResourceAsStream(
            RulesCurator.DEFAULT_RULES_FILE);
        Rules rules = new Rules(Util.readFile(is));

        when(rulesCuratorMock.getUpdated()).thenReturn(new Date());
        when(rulesCuratorMock.getRules()).thenReturn(rules);

        when(configMock.getInt(eq(ConfigProperties.PRODUCT_CACHE_MAX))).thenReturn(100);
        productCache = new ProductCache(configMock, productAdapterMock);

        poolRules = new PoolRules(poolManagerMock, productCache, configMock, entCurMock);
    }

    @Test
    public void hostedCreateInstanceBasedPool() {
        Subscription s = createInstanceBasedSub("INSTANCEPROD", 100, 2, false);
        List<Pool> pools = poolRules.createPools(s);
        assertEquals(1, pools.size());

        Pool pool = pools.get(0);

        // Quantity should be doubled:
        assertEquals(new Long(200), pool.getQuantity());
    }

    @Test
    public void standaloneCreateInstanceBasedPool() {
        Subscription s = createInstanceBasedSub("INSTANCEPROD", 100, 2, true);
        List<Pool> pools = poolRules.createPools(s);
        assertEquals(1, pools.size());

        Pool pool = pools.get(0);

        // In this case the exported entitlement becomes a subscription, the quantity
        // was already doubled in hosted, so from then on whenever it is exported we
        // respect it's quantity.
        assertEquals(new Long(100), pool.getQuantity());
    }

    @Test
    public void hostedInstanceBasedUpdatePool() {
        Subscription s = createInstanceBasedSub("INSTANCEPROD", 100, 2, false);
        List<Pool> pools = poolRules.createPools(s);
        assertEquals(1, pools.size());
        Pool pool = pools.get(0);

        // Change the value of instance multiplier:
        s.getProduct().setAttribute("instance_multiplier", "4");
        // Change the quantity:
        s.setQuantity(new Long(200));

        List<Pool> existingPools = new LinkedList<Pool>();
        existingPools.add(pool);
        List<PoolUpdate> updates = poolRules.updatePools(s, existingPools,
                TestUtil.stubChangedProducts(s.getProduct()));

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertTrue(update.getQuantityChanged());
        assertEquals(new Long(800), update.getPool().getQuantity());
    }

    @Test
    public void hostedInstanceBasedRemoved() {
        Subscription s = createInstanceBasedSub("INSTANCEPROD", 100, 2, false);
        List<Pool> pools = poolRules.createPools(s);
        assertEquals(1, pools.size());
        Pool pool = pools.get(0);

        // Remove the instance multiplier attribute entirely, pool quantity should
        // revert to half of what it was. No existing entitlements need to be adjusted,
        // we will let a (future) overconsumption routine handle that.
        ProductAttribute pa = s.getProduct().getAttribute("instance_multiplier");
        s.getProduct().getAttributes().remove(pa);

        List<Pool> existingPools = new LinkedList<Pool>();
        existingPools.add(pool);
        List<PoolUpdate> updates = poolRules.updatePools(s, existingPools,
                TestUtil.stubChangedProducts(s.getProduct()));

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertTrue(update.getQuantityChanged());
        assertEquals(new Long(100), update.getPool().getQuantity());
        assertFalse(update.getPool().getProduct().hasAttribute("instance_multiplier"));
    }

    @Test
    public void standaloneInstanceBasedUpdatePool() {
        Subscription s = createInstanceBasedSub("INSTANCEPROD", 100, 2, true);
        List<Pool> pools = poolRules.createPools(s);
        assertEquals(1, pools.size());
        Pool pool = pools.get(0);

        // Change the value of instance multiplier:
        s.getProduct().setAttribute("instance_multiplier", "4");
        // Change the quantity as well:
        s.setQuantity(new Long(200));

        List<Pool> existingPools = new LinkedList<Pool>();
        existingPools.add(pool);
        List<PoolUpdate> updates = poolRules.updatePools(s, existingPools,
                TestUtil.stubChangedProducts(s.getProduct()));

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertTrue(update.getQuantityChanged());

        // Change in instance multiplier would have no impact on a standalone update, we
        // only need to worry about an actual change on the subscription quantity.
        assertEquals(new Long(200), update.getPool().getQuantity());
    }

    private Subscription createInstanceBasedSub(String productId, int quantity,
        int instanceMultiplier, boolean exported) {
        Owner owner = new Owner("Test Corporation");
        Product product = new Product(productId, productId, owner);
        product.setAttribute("instance_multiplier",
            Integer.toString(instanceMultiplier));
        when(productAdapterMock.getProductById(productId)).thenReturn(product);
        Subscription s = TestUtil.createSubscription(product);
        if (exported) {
            s.setUpstreamPoolId("SOMETHING");
        }
        s.setQuantity(new Long(quantity));
        return s;
    }

}
