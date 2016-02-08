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

import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.policy.js.entitlement.Enforcer;
import org.candlepin.policy.js.entitlement.EntitlementRules;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.DateSourceForTesting;
import org.candlepin.test.TestDateUtil;
import org.candlepin.test.TestUtil;

import com.google.inject.AbstractModule;
import com.google.inject.Module;

import org.junit.Before;
import org.junit.Test;

import javax.inject.Inject;

/**
 * ConsumerResourceEntitlementRulesTest
 */
public class ConsumerResourceEntitlementRulesTest extends DatabaseTestFixture {
    @Inject private OwnerCurator ownerCurator;
    @Inject private ProductCurator productCurator;
    @Inject private PoolCurator poolCurator;
    @Inject private ConsumerCurator consumerCurator;
    @Inject private ConsumerTypeCurator consumerTypeCurator;
    @Inject private DateSourceForTesting dateSource;
    @Inject private ConsumerResource consumerResource;

    private ConsumerType standardSystemType;
    private Consumer consumer;
    private Product product;
    private Pool pool;

    private Owner owner;

    @Before
    public void setUp() {
        standardSystemType = consumerTypeCurator.create(
                new ConsumerType("standard-system"));
        owner = ownerCurator.create(new Owner("test-owner"));
        ownerCurator.create(owner);

        consumer = TestUtil.createConsumer(standardSystemType, owner);
        consumerCurator.create(consumer);

        product = TestUtil.createProduct(owner);
        productCurator.create(product);

        pool = createPool(owner, product, 10L,
            TestDateUtil.date(2010, 1, 1), TestDateUtil.date(2020, 12, 31));
        poolCurator.create(pool);
    }

    @Test(expected = ForbiddenException.class)
    public void testMaxMembership() {
        // 10 entitlements available, lets try to entitle 11 consumers.
        for (int i = 0; i < pool.getQuantity(); i++) {
            Consumer c = TestUtil.createConsumer(consumer.getType(), owner);
            consumerCurator.create(c);
            consumerResource.bind(c.getUuid(), pool.getId(),
                null, 1, null, null, false, null, null, null, null);
        }

        // Now for the 11th:
        Consumer c = TestUtil.createConsumer(consumer.getType(), owner);
        consumerCurator.create(c);
        consumerResource.bind(c.getUuid(), pool.getId(), null, 1, null, null,
            false, null, null, null, null);
    }

    @Test(expected = RuntimeException.class)
    public void testEntitlementsHaveExpired() {
        dateSource.currentDate(TestDateUtil.date(2030, 1, 13));
        consumerResource.bind(consumer.getUuid(), pool.getId(), null,
            null, null, null, false, null, null, null, null);
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
