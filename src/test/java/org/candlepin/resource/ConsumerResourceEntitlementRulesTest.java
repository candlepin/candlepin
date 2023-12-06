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
package org.candlepin.resource;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.async.JobException;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.policy.js.entitlement.Enforcer;
import org.candlepin.policy.js.entitlement.EntitlementRules;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import com.google.inject.AbstractModule;
import com.google.inject.Module;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;



/**
 * ConsumerResourceEntitlementRulesTest
 */
public class ConsumerResourceEntitlementRulesTest extends DatabaseTestFixture {
    private ConsumerResource consumerResource;
    private ConsumerType standardSystemType;
    private Consumer consumer;
    private Product product;
    private Pool pool;

    private Owner owner;

    @BeforeEach
    public void setUp() {
        consumerResource = injector.getInstance(ConsumerResource.class);
        standardSystemType = consumerTypeCurator.create(new ConsumerType("system"));
        this.owner = this.createOwner("test-owner");

        consumer = TestUtil.createConsumer(standardSystemType, owner);
        consumerCurator.create(consumer);

        product = this.createProduct();

        pool = createPool(owner, product, 10L,
            TestUtil.createDate(2010, 1, 1), TestUtil.createDate(2029, 12, 31));
        poolCurator.create(pool);
    }

    @Test
    public void testMaxMembership() throws JobException {
        // 10 entitlements available, lets try to entitle 11 consumers.
        for (int i = 0; i < pool.getQuantity(); i++) {
            Consumer c = TestUtil.createConsumer(standardSystemType, owner);
            consumerCurator.create(c);
            consumerResource.bind(c.getUuid(), pool.getId(),
                null, 1, null, null, false, null, null);
        }

        // Now for the 11th:
        Consumer c = TestUtil.createConsumer(standardSystemType, owner);
        consumerCurator.create(c);
        assertThrows(ForbiddenException.class,
            () -> consumerResource.bind(c.getUuid(), pool.getId(), null, 1, null, null, false, null, null));
    }

    @Test
    public void testEntitlementsHaveExpired() {
        dateSource.currentDate(TestUtil.createDate(2030, 1, 13));
        assertThrows(ForbiddenException.class, () -> consumerResource.bind(consumer.getUuid(), pool.getId(),
            null, null, null, null, false, null, null));
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
