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
package org.candlepin.model;

import org.candlepin.controller.CandlepinPoolManager;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.policy.entitlement.Enforcer;
import org.candlepin.policy.entitlement.EntitlementRules;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * PoolCuratorEntitlementRulesTest
 */
public class PoolCuratorEntitlementRulesTest extends DatabaseTestFixture {

    @Inject private CandlepinPoolManager poolManager;
    @Inject private Injector injector;

    private Owner owner;
    private Product product;
    private Consumer consumer;

    @BeforeEach
    public void setUp() {
        owner = this.createOwner();

        product = this.createProduct(owner);

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
        newProduct = this.createProduct(newProduct, owner);

        Pool consumerPool = createPool(owner, newProduct,
            numAvailEntitlements, TestUtil.createDate(2009, 11, 30),
            TestUtil.createDate(2050, 11, 30));
        consumerPool = poolCurator.create(consumerPool);

        CandlepinPoolManager anotherEntitler = injector.getInstance(CandlepinPoolManager.class);

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

        Product newProduct = this.createProduct(owner);

        Pool consumerPool = createPool(owner, newProduct, numAvailEntitlements,
            TestUtil.createDate(2009, 11, 30), TestUtil.createDate(2050, 11, 30));
        poolCurator.create(consumerPool);

        CandlepinPoolManager anotherEntitler = injector.getInstance(CandlepinPoolManager.class);

        Map<String, Integer> poolQuantities = new HashMap<>();
        poolQuantities.put(consumerPool.getId(), 1);
        List<Entitlement> e1 = poolManager.entitleByPools(consumer, poolQuantities);
        assertEquals(1, e1.size());
        poolQuantities.put(consumerPool.getId(), 1);
        assertThrows(EntitlementRefusedException.class, () ->
            anotherEntitler.entitleByPools(consumer, poolQuantities)
        );
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
