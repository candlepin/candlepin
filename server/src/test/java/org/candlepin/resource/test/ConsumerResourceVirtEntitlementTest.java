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
package org.candlepin.resource.test;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.CandlepinPoolManager;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.Subscription;
import org.candlepin.policy.js.entitlement.Enforcer;
import org.candlepin.policy.js.entitlement.EntitlementRules;
import org.candlepin.resource.ConsumerResource;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import com.google.inject.AbstractModule;
import com.google.inject.Module;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * ConsumerResourceVirtEntitlementTest
 */
public class ConsumerResourceVirtEntitlementTest extends DatabaseTestFixture {
    private ConsumerType manifestType;
    private ConsumerType systemType;
    private Consumer manifestConsumer;
    private Consumer systemConsumer;
    private Product productLimit;
    private Product productUnlimit;
    private List<Pool> limitPools;
    private List<Pool> unlimitPools;

    private ConsumerResource consumerResource;
    private PoolManager poolManager;
    private Owner owner;

    @Override
    protected Module getGuiceOverrideModule() {
        return new ConsumerResourceVirtEntitlementModule();
    }

    @Before
    public void setUp() {
        consumerResource = injector.getInstance(ConsumerResource.class);
        poolManager = injector.getInstance(CandlepinPoolManager.class);

        manifestType = consumerTypeCurator.create(
            new ConsumerType(ConsumerType.ConsumerTypeEnum.CANDLEPIN));
        systemType = consumerTypeCurator.create(
            new ConsumerType(ConsumerType.ConsumerTypeEnum.SYSTEM));
        owner = ownerCurator.create(new Owner("test-owner"));
        ownerCurator.create(owner);

        manifestConsumer = TestUtil.createConsumer(manifestType, owner);
        consumerCurator.create(manifestConsumer);

        systemConsumer = TestUtil.createConsumer(systemType, owner);
        consumerCurator.create(systemConsumer);

        // create a physical pool with numeric virt_limit
        productLimit = TestUtil.createProduct();
        productLimit.setAttribute("virt_limit", "10");
        productLimit.setAttribute("multi-entitlement", "yes");
        productCurator.create(productLimit);

        Subscription limitSub = new Subscription(owner,
            productLimit, new HashSet<Product>(),
            10L,
            TestUtil.createDate(2010, 1, 1),
            TestUtil.createDate(2020, 1, 1),
            TestUtil.createDate(2000, 1, 1));
        subCurator.create(limitSub);

        limitPools = poolManager.createPoolsForSubscription(limitSub);

        // create a physical pool with unlimited virt_limit
        productUnlimit = TestUtil.createProduct();
        productUnlimit.setAttribute("virt_limit", "unlimited");
        productUnlimit.setAttribute("multi-entitlement", "yes");
        productCurator.create(productUnlimit);

        Subscription unlimitSub = new Subscription(owner,
            productUnlimit, new HashSet<Product>(),
            10L,
            TestUtil.createDate(2010, 1, 1),
            TestUtil.createDate(2020, 1, 1),
            TestUtil.createDate(2000, 1, 1));
        subCurator.create(unlimitSub);

        unlimitPools = poolManager.createPoolsForSubscription(unlimitSub);
    }

    /**
     * Checking behavior when the physical pool has a numeric virt_limit
     */
    @Test
    public void testLimitPool() {
        List<Pool> subscribedTo = new ArrayList<Pool>();
        Consumer guestConsumer = TestUtil.createConsumer(systemType, owner);
        guestConsumer.setFact("virt.is_guest", "true");

        consumerCurator.create(guestConsumer);
        Pool parentPool = null;

        assertTrue(limitPools != null && limitPools.size() == 2);
        for (Pool p : limitPools) {
            // bonus pools have the attribute pool_derived
            if (null != p.getAttributeValue("pool_derived")) {
                // consume 2 times so one can get revoked later.
                consumerResource.bind(guestConsumer.getUuid(), p.getId(), null,
                    10, null, null, false, null, null);
                consumerResource.bind(guestConsumer.getUuid(), p.getId(), null,
                    10, null, null, false, null, null);
                // ensure the correct # consumed from the bonus pool
                assertTrue(p.getConsumed() == 20);
                assertTrue(p.getQuantity() == 100);
                // keep this list so we don't need to search again
                subscribedTo.add(p);
            }
            else {
                parentPool = p;
            }
        }
        // manifest consume from the physical pool and then check bonus pool quantities
        consumerResource.bind(manifestConsumer.getUuid(), parentPool.getId(), null, 7, null,
            null, false, null, null);
        for (Pool p : subscribedTo) {
            assertTrue(p.getConsumed() == 20);
            assertTrue(p.getQuantity() == 30);
        }
        // manifest consume from the physical pool and then check bonus pool quantities.
        //   Should result in a revocation of one of the 10 count entitlements.
        consumerResource.bind(manifestConsumer.getUuid(), parentPool.getId(), null, 2, null,
            null, false, null, null);
        for (Pool p : subscribedTo) {
            assertTrue(p.getConsumed() == 10);
            assertTrue(p.getQuantity() == 10);
        }
        // system consume from the physical pool and then check bonus pool quantities.
        //   Should result in no change in the entitlements for the guest.
        consumerResource.bind(systemConsumer.getUuid(), parentPool.getId(), null, 1, null,
            null, false, null, null);
        for (Pool p : subscribedTo) {
            assertTrue(p.getConsumed() == 10);
            assertTrue(p.getQuantity() == 10);
        }
    }

    @Test
    public void testUnlimitPool() {
        List<Pool> subscribedTo = new ArrayList<Pool>();
        Consumer guestConsumer = TestUtil.createConsumer(systemType, owner);
        guestConsumer.setFact("virt.is_guest", "true");

        consumerCurator.create(guestConsumer);
        Pool parentPool = null;

        assertTrue(unlimitPools != null && unlimitPools.size() == 2);
        for (Pool p : unlimitPools) {
            // bonus pools have the attribute pool_derived
            if (null != p.getAttributeValue("pool_derived")) {
                // consume 2 times so they can get revoked separately.
                consumerResource.bind(guestConsumer.getUuid(), p.getId(), null,
                    10, null, null, false, null, null);
                consumerResource.bind(guestConsumer.getUuid(), p.getId(), null,
                    10, null, null, false, null, null);
                assertTrue(p.getConsumed() == 20);
                assertTrue(p.getQuantity() == -1);
                poolManager.getRefresher().add(owner).run();
                // double check after pools refresh
                assertTrue(p.getConsumed() == 20);
                assertTrue(p.getQuantity() == -1);
                subscribedTo.add(p);
            }
            else {
                parentPool = p;
            }
        }
        // Incomplete consumption of physical pool leaves unlimited pool unchanged.
        consumerResource.bind(manifestConsumer.getUuid(), parentPool.getId(), null, 7, null,
            null, false, null, null);
        for (Pool p : subscribedTo) {
            assertTrue(p.getConsumed() == 20);
            assertTrue(p.getQuantity() == -1);
        }
        // Full consumption of physical pool causes revocation of bonus pool entitlements
        //   and quantity change to 0
        consumerResource.bind(manifestConsumer.getUuid(), parentPool.getId(), null, 3, null,
            null, false, null, null);
        for (Pool p : subscribedTo) {
            assertEquals(new Long(0), p.getConsumed());
            assertTrue(p.getQuantity() == 0);
        }
    }

    private static class ConsumerResourceVirtEntitlementModule extends AbstractModule {
        @Override
        protected void configure() {
            Config config = mock(Config.class);
            when(config.standalone()).thenReturn(false);
            when(config.getString(eq(ConfigProperties.CONSUMER_FACTS_MATCHER)))
                .thenReturn("^virt.*");
            when(config.getString(eq(ConfigProperties.CONSUMER_SYSTEM_NAME_PATTERN)))
                .thenReturn("[\\#\\?\\'\\`\\!@{}()\\[\\]\\?&\\w-\\.]+");
            when(config.getString(eq(ConfigProperties.CONSUMER_PERSON_NAME_PATTERN)))
                .thenReturn("[\\#\\?\\'\\`\\!@{}()\\[\\]\\?&\\w-\\.]+");
            bind(Config.class).toInstance(config);
            bind(Enforcer.class).to(EntitlementRules.class);
        }
    }
}
