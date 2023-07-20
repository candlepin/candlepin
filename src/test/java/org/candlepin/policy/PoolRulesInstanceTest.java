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
package org.candlepin.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.controller.PoolConverter;
import org.candlepin.controller.PoolService;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.model.dto.Subscription;
import org.candlepin.policy.js.pool.PoolRules;
import org.candlepin.policy.js.pool.PoolUpdate;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.InputStream;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;



/**
 * JsPoolRulesInstanceTest: Tests for refresh pools code on instance based subscriptions.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PoolRulesInstanceTest {

    private PoolRules poolRules;

    @Mock
    private RulesCurator rulesCurator;
    @Mock
    private PoolService poolService;
    @Mock
    private Configuration config;
    @Mock
    private EntitlementCurator entCurator;
    @Mock
    private PoolConverter poolConverter;

    @BeforeEach
    public void setUp() {
        InputStream is = this.getClass().getResourceAsStream(RulesCurator.DEFAULT_RULES_FILE);
        Rules rules = new Rules(Util.readFile(is));

        when(rulesCurator.getUpdated()).thenReturn(new Date());
        when(rulesCurator.getRules()).thenReturn(rules);

        when(config.getInt(ConfigProperties.PRODUCT_CACHE_MAX)).thenReturn(100);

        poolRules = new PoolRules(config, entCurator, poolConverter);
    }

    @Test
    public void hostedCreateInstanceBasedPool() {
        Subscription s = createInstanceBasedSub("INSTANCEPROD", 100, 2, false);
        Pool p = TestUtil.copyFromSub(s);
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<>());
        assertEquals(1, pools.size());

        Pool pool = pools.get(0);

        // Quantity should be doubled:
        assertEquals(200L, pool.getQuantity());
    }

    @Test
    public void standaloneCreateInstanceBasedPool() {
        Subscription s = createInstanceBasedSub("INSTANCEPROD", 100, 2, true);
        Pool p = TestUtil.copyFromSub(s);
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<>());
        assertEquals(1, pools.size());

        Pool pool = pools.get(0);

        // In this case the exported entitlement becomes a subscription, the quantity
        // was already doubled in hosted, so from then on whenever it is exported we
        // respect it's quantity.
        assertEquals(100L, pool.getQuantity());
    }

    @Test
    public void hostedInstanceBasedUpdatePool() {
        Subscription s = createInstanceBasedSub("INSTANCEPROD", 100, 2, false);
        Pool p = TestUtil.copyFromSub(s);
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<>());
        assertEquals(1, pools.size());
        Pool pool = pools.get(0);

        p = TestUtil.copyFromSub(s);
        // Change the value of instance multiplier:
        p.getProduct().setAttribute(Product.Attributes.INSTANCE_MULTIPLIER, "4");
        // Change the quantity:
        p.setQuantity(200L);

        List<Pool> existingPools = new LinkedList<>();
        existingPools.add(pool);
        List<PoolUpdate> updates = poolRules.updatePools(p, existingPools, p.getQuantity(),
            TestUtil.stubChangedProducts(p.getProduct()));

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertTrue(update.getQuantityChanged());
        assertEquals(800L, update.getPool().getQuantity());
    }

    @Test
    public void hostedInstanceBasedRemoved() {
        Subscription s = createInstanceBasedSub("INSTANCEPROD", 100, 2, false);
        Pool primaryPool = TestUtil.copyFromSub(s);
        List<Pool> pools = poolRules.createAndEnrichPools(primaryPool, new LinkedList<>());
        assertEquals(1, pools.size());
        Pool pool = pools.get(0);

        // Remove the instance multiplier attribute entirely, pool quantity should
        // revert to half of what it was. No existing entitlements need to be adjusted,
        // we will let a (future) overconsumption routine handle that.
        primaryPool = TestUtil.copyFromSub(s);
        primaryPool.getProduct().removeAttribute(Product.Attributes.INSTANCE_MULTIPLIER);

        List<Pool> existingPools = new LinkedList<>();
        existingPools.add(pool);
        List<PoolUpdate> updates = poolRules.updatePools(primaryPool, existingPools, s.getQuantity(),
            TestUtil.stubChangedProducts(primaryPool.getProduct()));

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertTrue(update.getQuantityChanged());
        assertEquals(100L, update.getPool().getQuantity());
        assertFalse(update.getPool().getProduct().hasAttribute(Product.Attributes.INSTANCE_MULTIPLIER));
    }

    @Test
    public void standaloneInstanceBasedUpdatePool() {
        Subscription s = createInstanceBasedSub("INSTANCEPROD", 100, 2, true);
        Pool primaryPool = TestUtil.copyFromSub(s);
        List<Pool> pools = poolRules.createAndEnrichPools(primaryPool, new LinkedList<>());
        assertEquals(1, pools.size());
        Pool pool = pools.get(0);

        primaryPool = TestUtil.copyFromSub(s);
        // Change the value of instance multiplier:
        primaryPool.getProduct().setAttribute(Product.Attributes.INSTANCE_MULTIPLIER, "4");
        // Change the quantity as well:
        primaryPool.setQuantity(200L);

        List<Pool> existingPools = new LinkedList<>();
        existingPools.add(pool);
        List<PoolUpdate> updates = poolRules.updatePools(primaryPool, existingPools,
            primaryPool.getQuantity(), TestUtil.stubChangedProducts(primaryPool.getProduct()));

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertTrue(update.getQuantityChanged());

        // Change in instance multiplier would have no impact on a standalone update, we
        // only need to worry about an actual change on the subscription quantity.
        assertEquals(200L, update.getPool().getQuantity());
    }

    private Subscription createInstanceBasedSub(String productId, long quantity, int instanceMultiplier,
        boolean exported) {

        Owner owner = new Owner()
            .setKey("Test Corporation")
            .setDisplayName("Test Corporation");

        Product product = TestUtil.createProduct(productId, productId);
        product.setAttribute(Product.Attributes.INSTANCE_MULTIPLIER, Integer.toString(instanceMultiplier));
        Subscription s = TestUtil.createSubscription(owner, product);
        if (exported) {
            s.setUpstreamPoolId("SOMETHING");
        }
        s.setQuantity(quantity);
        return s;
    }

}
