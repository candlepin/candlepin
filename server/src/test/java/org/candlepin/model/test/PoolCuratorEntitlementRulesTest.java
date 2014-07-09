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
package org.candlepin.model.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.candlepin.controller.CandlepinPoolManager;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.policy.js.entitlement.Enforcer;
import org.candlepin.policy.js.entitlement.EntitlementRules;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Module;

/**
 * PoolCuratorEntitlementRulesTest
 */
public class PoolCuratorEntitlementRulesTest extends DatabaseTestFixture {
    private Owner owner;
    private Product product;
    private Consumer consumer;

    @Before
    public void setUp() {
        owner = createOwner();
        ownerCurator.create(owner);

        product = TestUtil.createProduct();
        productCurator.create(product);

        consumer = TestUtil.createConsumer(owner);
        consumer.setFact("cpu_cores", "4");
        consumer.setType(new ConsumerType("system"));
        consumerTypeCurator.create(consumer.getType());
        consumerCurator.create(consumer);
    }

    @Test
    public void concurrentCreationOfEntitlementsShouldWorkIfUnderMaxMemberLimit()
        throws Exception {
        Long numAvailEntitlements = 2L;

        Product newProduct = TestUtil.createProduct();
        newProduct.addAttribute(new ProductAttribute("multi-entitlement", "yes"));
        productCurator.create(newProduct);

        Pool consumerPool = createPoolAndSub(owner, newProduct,
            numAvailEntitlements, TestUtil.createDate(2009, 11, 30),
            TestUtil.createDate(2050, 11, 30));
        consumerPool = poolCurator.create(consumerPool);

        CandlepinPoolManager anotherEntitler =
            injector.getInstance(CandlepinPoolManager.class);

        anotherEntitler.entitleByPool(consumer, consumerPool, 1);
        anotherEntitler.entitleByPool(consumer, consumerPool, 1);

        assertFalse(poolCurator.find(consumerPool.getId())
                .entitlementsAvailable(1));
    }

    @Test(expected = EntitlementRefusedException.class)
    public void concurrentCreationOfEntitlementsShouldFailIfOverMaxMemberLimit()
        throws Exception {
        Long numAvailEntitlements = 1L;

        Product newProduct = TestUtil.createProduct();
        productCurator.create(newProduct);

        Pool consumerPool = createPoolAndSub(owner, newProduct, numAvailEntitlements,
            TestUtil.createDate(2009, 11, 30), TestUtil.createDate(2050, 11, 30));
        poolCurator.create(consumerPool);

        CandlepinPoolManager anotherEntitler =
            injector.getInstance(CandlepinPoolManager.class);

        Entitlement e1 = poolManager.entitleByPool(consumer, consumerPool, 1);
        assertNotNull(e1);

        anotherEntitler.entitleByPool(consumer, consumerPool, 1);
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
