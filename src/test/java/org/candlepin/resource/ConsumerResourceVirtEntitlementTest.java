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
package org.candlepin.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.async.JobException;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.PoolManager;
import org.candlepin.dto.manifest.v1.OwnerDTO;
import org.candlepin.dto.manifest.v1.ProductDTO;
import org.candlepin.dto.manifest.v1.SubscriptionDTO;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.policy.js.entitlement.Enforcer;
import org.candlepin.policy.js.entitlement.EntitlementRules;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.impl.ImportSubscriptionServiceAdapter;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import com.google.inject.AbstractModule;
import com.google.inject.Module;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;



/**
 * ConsumerResourceVirtEntitlementTest
 */
public class ConsumerResourceVirtEntitlementTest extends DatabaseTestFixture {

    @Inject private ConsumerResource consumerResource;
    @Inject private PoolManager poolManager;
    @Inject private SubscriptionServiceAdapter subAdapter;
    @Inject private ProductServiceAdapter prodAdapter;

    private ConsumerType manifestType;
    private ConsumerType systemType;
    private Consumer manifestConsumer;
    private Consumer systemConsumer;
    private Product productLimit;
    private Product productUnlimit;
    private List<Pool> limitPools;
    private List<Pool> unlimitPools;

    private Owner owner;

    @Override
    protected Module getGuiceOverrideModule() {
        return new ConsumerResourceVirtEntitlementModule();
    }

    @BeforeEach
    public void setUp() {
        List<SubscriptionDTO> subscriptions = new ArrayList<>();
        subAdapter = new ImportSubscriptionServiceAdapter(subscriptions);

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
        productLimit.setAttribute(Product.Attributes.VIRT_LIMIT, "10");
        productLimit.setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");
        productLimit = this.createProduct(productLimit, owner);

        SubscriptionDTO limitSub = new SubscriptionDTO();
        limitSub.setId(Util.generateDbUUID());
        limitSub.setOwner(this.modelTranslator.translate(owner, OwnerDTO.class));
        limitSub.setProduct(this.modelTranslator.translate(productLimit, ProductDTO.class));
        limitSub.setQuantity(10L);
        limitSub.setStartDate(TestUtil.createDate(2010, 1, 1));
        limitSub.setEndDate(TestUtil.createDateOffset(1, 0, 0));
        limitSub.setLastModified(TestUtil.createDate(2000, 1, 1));
        subscriptions.add(limitSub);

        limitPools = poolManager.createAndEnrichPools(limitSub);

        // create a physical pool with unlimited virt_limit
        productUnlimit = TestUtil.createProduct();
        productUnlimit.setAttribute(Product.Attributes.VIRT_LIMIT, "unlimited");
        productUnlimit.setAttribute(Pool.Attributes.MULTI_ENTITLEMENT, "yes");
        productUnlimit = this.createProduct(productUnlimit, owner);

        SubscriptionDTO unlimitSub = new SubscriptionDTO();
        unlimitSub.setId(Util.generateDbUUID());
        unlimitSub.setOwner(this.modelTranslator.translate(owner, OwnerDTO.class));
        unlimitSub.setProduct(this.modelTranslator.translate(productUnlimit, ProductDTO.class));
        unlimitSub.setQuantity(10L);
        unlimitSub.setStartDate(TestUtil.createDate(2010, 1, 1));
        unlimitSub.setEndDate(TestUtil.createDateOffset(1, 0, 0));
        unlimitSub.setLastModified(TestUtil.createDate(2000, 1, 1));
        subscriptions.add(unlimitSub);

        unlimitPools = poolManager.createAndEnrichPools(unlimitSub);
    }

    /**
     * Checking behavior when the physical pool has a numeric virt_limit
     */
    @Test
    public void testLimitPool() throws JobException {
        List<Pool> subscribedTo = new ArrayList<>();
        Consumer guestConsumer = TestUtil.createConsumer(systemType, owner);
        guestConsumer.setFact("virt.is_guest", "true");

        consumerCurator.create(guestConsumer);
        Pool parentPool = null;

        assertTrue(limitPools != null && limitPools.size() == 2);
        for (Pool p : limitPools) {
            // bonus pools have the attribute pool_derived
            if (null != p.getAttributeValue(Pool.Attributes.DERIVED_POOL)) {
                // consume 2 times so one can get revoked later.
                consumerResource.bind(guestConsumer.getUuid(), p.getId(), null,
                    10, null, null, false, null, null);
                consumerResource.bind(guestConsumer.getUuid(), p.getId(), null,
                    10, null, null, false, null, null);
                p = poolManager.get(p.getId());
                // ensure the correct # consumed from the bonus pool
                assertEquals(20L, p.getConsumed());
                assertEquals(100L, p.getQuantity());
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
            p = poolManager.get(p.getId());
            assertEquals(20L, p.getConsumed());
            assertEquals(30L, p.getQuantity());
        }
        // manifest consume from the physical pool and then check bonus pool quantities.
        //   Should result in a revocation of one of the 10 count entitlements.
        consumerResource.bind(manifestConsumer.getUuid(), parentPool.getId(), null, 2, null,
            null, false, null, null);
        for (Pool p : subscribedTo) {
            p = poolManager.get(p.getId());
            assertEquals(10L, p.getConsumed());
            assertEquals(10L, p.getQuantity());
        }
        // system consume from the physical pool and then check bonus pool quantities.
        //   Should result in no change in the entitlements for the guest.
        consumerResource.bind(systemConsumer.getUuid(), parentPool.getId(), null, 1, null,
            null, false, null, null);
        for (Pool p : subscribedTo) {
            p = poolManager.get(p.getId());
            assertEquals(10L, p.getConsumed());
            assertEquals(10L, p.getQuantity());
        }
    }

    @Test
    public void testUnlimitPool() throws JobException {
        List<Pool> subscribedTo = new ArrayList<>();
        Consumer guestConsumer = TestUtil.createConsumer(systemType, owner);
        guestConsumer.setFact("virt.is_guest", "true");

        consumerCurator.create(guestConsumer);
        Pool parentPool = null;

        assertTrue(unlimitPools != null && unlimitPools.size() == 2);
        for (Pool p : unlimitPools) {
            // bonus pools have the attribute pool_derived
            if (null != p.getAttributeValue(Pool.Attributes.DERIVED_POOL)) {
                // consume 2 times so they can get revoked separately.
                consumerResource.bind(guestConsumer.getUuid(), p.getId(), null,
                    10, null, null, false, null, null);
                consumerResource.bind(guestConsumer.getUuid(), p.getId(), null,
                    10, null, null, false, null, null);
                p = poolManager.get(p.getId());
                assertEquals(20L, p.getConsumed());
                assertEquals(-1L, p.getQuantity());

                poolManager.getRefresher(this.subAdapter, this.prodAdapter)
                    .add(owner)
                    .run();

                // double check after pools refresh
                assertEquals(20L, p.getConsumed());
                assertEquals(-1L, p.getQuantity());
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
            assertEquals(20, p.getConsumed());
            assertEquals(-1L, p.getQuantity());
        }
        // Full consumption of physical pool causes revocation of bonus pool entitlements
        //   and quantity change to 0
        consumerResource.bind(manifestConsumer.getUuid(), parentPool.getId(), null, 3, null,
            null, false, null, null);
        for (Pool p : subscribedTo) {
            p = poolManager.get(p.getId());
            assertEquals(0L, p.getConsumed());
            assertEquals(0L, p.getQuantity());
        }
    }

    private static class ConsumerResourceVirtEntitlementModule extends AbstractModule {
        @Override
        protected void configure() {
            Configuration config = new CandlepinCommonTestConfig();
            config.setProperty(ConfigProperties.STANDALONE, "false");
            config.setProperty(ConfigProperties.CONSUMER_FACTS_MATCHER, "^virt.*");
            config.setProperty(ConfigProperties.CONSUMER_SYSTEM_NAME_PATTERN,
                "[\\#\\?\\'\\`\\!@{}()\\[\\]\\?&\\w-\\.]+");
            config.setProperty(ConfigProperties.CONSUMER_PERSON_NAME_PATTERN,
                "[\\#\\?\\'\\`\\!@{}()\\[\\]\\?&\\w-\\.]+");
            bind(Configuration.class).toInstance(config);
            bind(Enforcer.class).to(EntitlementRules.class);
        }
    }
}
