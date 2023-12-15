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
package org.candlepin.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.controller.PoolManager;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.policy.js.entitlement.Enforcer;
import org.candlepin.policy.js.entitlement.EntitlementRules;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import com.google.inject.AbstractModule;
import com.google.inject.Module;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;



/**
 * PoolCuratorEntitlementRulesTest
 */
public class PoolCuratorEntitlementRulesTest extends DatabaseTestFixture {

    private PoolManager poolManager;

    private Owner owner;
    private Product product;
    private Consumer consumer;

    @BeforeEach
    public void setUp() {
        poolManager = injector.getInstance(PoolManager.class);

        owner = this.createOwner();

        product = this.createProduct();

        ConsumerType ctype = new ConsumerType("system");
        ctype = this.consumerTypeCurator.create(ctype);

        consumer = this.createConsumer(owner);
        consumer.setFact("cpu_cores", "4");
        consumer.setType(ctype);
        consumer = consumerCurator.merge(consumer);
    }

    @Test
    public void concurrentCreationOfEntitlementsShouldWorkIfUnderMaxMemberLimit() throws Exception {
        Long numAvailEntitlements = 2L;

        Product newProduct = TestUtil.createProduct();
        newProduct.setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");
        newProduct = this.createProduct(newProduct);

        Pool consumerPool = createPool(owner, newProduct,
            numAvailEntitlements, TestUtil.createDate(2009, 11, 30),
            TestUtil.createDate(2050, 11, 30));
        consumerPool = poolCurator.create(consumerPool);

        PoolManager anotherEntitler = injector.getInstance(PoolManager.class);

        Map<String, Integer> poolQuantities = new HashMap<>();
        poolQuantities.put(consumerPool.getId(), 1);
        anotherEntitler.entitleByPools(consumer, poolQuantities);
        poolQuantities.put(consumerPool.getId(), 1);
        anotherEntitler.entitleByPools(consumer, poolQuantities);

        assertFalse(poolCurator.get(consumerPool.getId()).entitlementsAvailable(1));
    }

    @Test
    public void concurrentCreationOfEntitlementsShouldFailIfOverMaxMemberLimit() throws Exception {
        Long numAvailEntitlements = 1L;

        Product newProduct = this.createProduct();

        Pool consumerPool = createPool(owner, newProduct, numAvailEntitlements,
            TestUtil.createDate(2009, 11, 30), TestUtil.createDate(2050, 11, 30));
        poolCurator.create(consumerPool);

        PoolManager anotherEntitler = injector.getInstance(PoolManager.class);

        Map<String, Integer> poolQuantities = new HashMap<>();
        poolQuantities.put(consumerPool.getId(), 1);
        List<Entitlement> e1 = poolManager.entitleByPools(consumer, poolQuantities);
        assertEquals(1, e1.size());
        poolQuantities.put(consumerPool.getId(), 1);
        assertThrows(EntitlementRefusedException.class,
            () -> anotherEntitler.entitleByPools(consumer, poolQuantities));
    }

    @Override
    protected Module getGuiceOverrideModule() {
        return new AbstractModule() {

            @Override
            protected void configure() {
                bind(Enforcer.class).to(EntitlementRules.class);
            }
        };
    }
}
