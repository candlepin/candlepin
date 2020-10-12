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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.Rules;
import org.candlepin.model.RulesCurator;
import org.candlepin.model.dto.Subscription;
import org.candlepin.policy.js.pool.PoolRules;
import org.candlepin.policy.js.pool.PoolUpdate;
import org.candlepin.service.ProductServiceAdapter;
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

    @Mock private RulesCurator rulesCuratorMock;
    @Mock private ProductServiceAdapter productAdapterMock;
    @Mock private PoolManager poolManagerMock;
    @Mock private Configuration configMock;
    @Mock private EntitlementCurator entCurMock;
    @Mock private OwnerProductCurator ownerProdCuratorMock;
    @Mock private ProductCurator productCurator;

    @BeforeEach
    public void setUp() {
        InputStream is = this.getClass().getResourceAsStream(RulesCurator.DEFAULT_RULES_FILE);
        Rules rules = new Rules(Util.readFile(is));

        when(rulesCuratorMock.getUpdated()).thenReturn(new Date());
        when(rulesCuratorMock.getRules()).thenReturn(rules);

        when(configMock.getInt(eq(ConfigProperties.PRODUCT_CACHE_MAX))).thenReturn(100);

        poolRules = new PoolRules(configMock, entCurMock, ownerProdCuratorMock,
            productCurator);
    }

    @Test
    public void hostedCreateInstanceBasedPool() {
        Subscription s = createInstanceBasedSub("INSTANCEPROD", 100, 2, false);
        Pool p = TestUtil.copyFromSub(s);
        List<Pool> pools = poolRules.createAndEnrichPools(p, new LinkedList<>());
        assertEquals(1, pools.size());

        Pool pool = pools.get(0);

        // Quantity should be doubled:
        assertEquals(new Long(200), pool.getQuantity());
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
        assertEquals(new Long(100), pool.getQuantity());
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
        p.setQuantity(new Long(200));

        List<Pool> existingPools = new LinkedList<>();
        existingPools.add(pool);
        List<PoolUpdate> updates = poolRules.updatePools(p, existingPools, p.getQuantity(),
            TestUtil.stubChangedProducts(p.getProduct()));

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertTrue(update.getQuantityChanged());
        assertEquals(new Long(800), update.getPool().getQuantity());
    }

    @Test
    public void hostedInstanceBasedRemoved() {
        Subscription s = createInstanceBasedSub("INSTANCEPROD", 100, 2, false);
        Pool masterPool = TestUtil.copyFromSub(s);
        List<Pool> pools = poolRules.createAndEnrichPools(masterPool, new LinkedList<>());
        assertEquals(1, pools.size());
        Pool pool = pools.get(0);

        // Remove the instance multiplier attribute entirely, pool quantity should
        // revert to half of what it was. No existing entitlements need to be adjusted,
        // we will let a (future) overconsumption routine handle that.
        masterPool = TestUtil.copyFromSub(s);
        masterPool.getProduct().removeAttribute(Product.Attributes.INSTANCE_MULTIPLIER);

        List<Pool> existingPools = new LinkedList<>();
        existingPools.add(pool);
        List<PoolUpdate> updates = poolRules.updatePools(masterPool, existingPools, s.getQuantity(),
            TestUtil.stubChangedProducts(masterPool.getProduct()));

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertTrue(update.getQuantityChanged());
        assertEquals(new Long(100), update.getPool().getQuantity());
        assertFalse(update.getPool().getProduct().hasAttribute(Product.Attributes.INSTANCE_MULTIPLIER));
    }

    @Test
    public void standaloneInstanceBasedUpdatePool() {
        Subscription s = createInstanceBasedSub("INSTANCEPROD", 100, 2, true);
        Pool masterPool = TestUtil.copyFromSub(s);
        List<Pool> pools = poolRules.createAndEnrichPools(masterPool, new LinkedList<>());
        assertEquals(1, pools.size());
        Pool pool = pools.get(0);

        masterPool = TestUtil.copyFromSub(s);
        // Change the value of instance multiplier:
        masterPool.getProduct().setAttribute(Product.Attributes.INSTANCE_MULTIPLIER, "4");
        // Change the quantity as well:
        masterPool.setQuantity(new Long(200));

        List<Pool> existingPools = new LinkedList<>();
        existingPools.add(pool);
        List<PoolUpdate> updates = poolRules.updatePools(masterPool, existingPools,
            masterPool.getQuantity(), TestUtil.stubChangedProducts(masterPool.getProduct()));

        assertEquals(1, updates.size());
        PoolUpdate update = updates.get(0);
        assertTrue(update.getQuantityChanged());

        // Change in instance multiplier would have no impact on a standalone update, we
        // only need to worry about an actual change on the subscription quantity.
        assertEquals(new Long(200), update.getPool().getQuantity());
    }

    private Subscription createInstanceBasedSub(String productId, int quantity, int instanceMultiplier,
        boolean exported) {
        Owner owner = new Owner("Test Corporation");
        Product product = TestUtil.createProduct(productId, productId);
        product.setAttribute(Product.Attributes.INSTANCE_MULTIPLIER, Integer.toString(instanceMultiplier));
        Subscription s = TestUtil.createSubscription(owner, product);
        if (exported) {
            s.setUpstreamPoolId("SOMETHING");
        }
        s.setQuantity(new Long(quantity));
        return s;
    }

}
