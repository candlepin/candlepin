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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.candlepin.controller.CandlepinPoolManager;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.policy.Enforcer;
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

    private static final String CPU_LIMITED_PRODUCT = "CPULIMITED001";

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
    public void testListAllForConsumerConsumerIncludesWarnings() {
        Product p = new Product(CPU_LIMITED_PRODUCT, CPU_LIMITED_PRODUCT);
        p.addAttribute(new ProductAttribute("sockets", "2"));
        productCurator.create(p);

        Pool pool = createPoolAndSub(owner, p, 100L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2050, 3, 2));
        poolCurator.create(pool);

        consumer.setFact("cpu.sockets", "4");
        List<Pool> results =
            poolCurator.listAvailableEntitlementPools(consumer, consumer.getOwner(),
                null, null, true, true);
        assertEquals(1, results.size());
    }


    @Test
    public void testListAllForConsumerExcludesErrors() {
        Product p = new Product(CPU_LIMITED_PRODUCT, CPU_LIMITED_PRODUCT);
        productCurator.create(p);

        // Creating a pool with no entitlements available, which will trigger
        // a rules error:
        Pool pool = createPoolAndSub(owner, p, 0L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2050, 3, 2));
        poolCurator.create(pool);

        List<Pool> results =
            poolCurator.listAvailableEntitlementPools(consumer, consumer.getOwner(),
                null, null, true, true);
        assertEquals(0, results.size());
    }

    @Test
    public void testListForConsumerExcludesWarnings() {

        Product p = new Product(CPU_LIMITED_PRODUCT, CPU_LIMITED_PRODUCT);
        p.addAttribute(new ProductAttribute("sockets", "2"));
        productCurator.create(p);

        Pool pool = createPoolAndSub(owner, p, 100L,
            TestUtil.createDate(2000, 3, 2), TestUtil.createDate(2050, 3, 2));
        poolCurator.create(pool);

        consumer.setFact("cpu.cpu_socket(s)", "4");
        List<Pool> results =
            poolCurator.listByConsumer(consumer);
        assertEquals(0, results.size());
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

        anotherEntitler.entitleByProduct(consumer, newProduct.getId());
        anotherEntitler.entitleByProduct(consumer, newProduct.getId());

        assertFalse(poolCurator.find(consumerPool.getId())
                .entitlementsAvailable(1));
    }

    @Test(expected = RuntimeException.class)
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

        Entitlement e1 = poolManager.entitleByProduct(consumer, newProduct.getId());
        assertNotNull(e1);

        anotherEntitler.entitleByProduct(consumer, newProduct.getId());
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
