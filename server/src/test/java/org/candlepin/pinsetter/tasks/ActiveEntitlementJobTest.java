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
package org.candlepin.pinsetter.tasks;

import static org.junit.Assert.*;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.util.Util;

import org.junit.Before;
import org.junit.Test;
import org.quartz.JobExecutionException;

import javax.inject.Inject;

/**
 * TestActiveEntitlementJob
 */
public class ActiveEntitlementJobTest extends DatabaseTestFixture {
    @Inject private OwnerCurator ownerCurator;
    @Inject private ProductCurator productCurator;
    @Inject private ConsumerCurator consumerCurator;
    @Inject private ConsumerTypeCurator consumerTypeCurator;
    @Inject private EntitlementCurator entitlementCurator;
    @Inject private ActiveEntitlementJob job;

    private Owner owner;
    private ConsumerType ct;
    private Consumer consumer;
    private Product prod;

    @Before
    public void setUp() {
        owner = new Owner("test-owner", "Test Owner");
        owner = ownerCurator.create(owner);

        prod = new Product("1", "2", owner);
        productCurator.create(prod);

        ct = new ConsumerType(ConsumerTypeEnum.SYSTEM);
        ct = consumerTypeCurator.create(ct);

        consumer = new Consumer("a consumer", "username", owner, ct);
        consumer.addInstalledProduct(new ConsumerInstalledProduct(
                prod.getId(), prod.getName()));
        consumerCurator.create(consumer);
    }

    @Test
    public void testActiveEntitlementJob() throws JobExecutionException {
        Pool p = createPoolAndSub(owner, prod, 5L, Util.yesterday(), Util.tomorrow());
        Entitlement ent = this.createEntitlement(owner, consumer, p,
                createEntitlementCertificate("entkey", "ecert"));
        // Needs to be flipped
        ent.setUpdatedOnStart(false);
        entitlementCurator.create(ent);

        consumerCurator.refresh(consumer);
        assertFalse("valid".equals(consumer.getEntitlementStatus()));

        job.toExecute(null);
        consumerCurator.refresh(consumer);
        assertEquals("valid", consumer.getEntitlementStatus());

        // Should have changed
        assertTrue(entitlementCurator.find(ent.getId()).isUpdatedOnStart());
    }

    @Test
    public void testActiveEntitlementJobNoChange() throws JobExecutionException {
        Pool p = createPoolAndSub(owner, prod, 5L, Util.yesterday(), Util.tomorrow());
        Entitlement ent = this.createEntitlement(owner, consumer, p,
                createEntitlementCertificate("entkey", "ecert"));
        // Already done
        ent.setUpdatedOnStart(true);
        entitlementCurator.create(ent);

        job.toExecute(null);

        // Unchanged
        assertTrue(entitlementCurator.find(ent.getId()).isUpdatedOnStart());
    }

    @Test
    public void testActiveEntitlementJobStillInactive() throws JobExecutionException {
        // Future pool
        Pool p = createPoolAndSub(owner, prod, 5L, Util.tomorrow(), Util.addDaysToDt(10));
        Entitlement ent = this.createEntitlement(owner, consumer, p,
                createEntitlementCertificate("entkey", "ecert"));
        // Needs to be flipped, eventually
        ent.setUpdatedOnStart(false);
        entitlementCurator.create(ent);

        consumerCurator.refresh(consumer);
        assertFalse("valid".equals(consumer.getEntitlementStatus()));

        job.toExecute(null);
        consumerCurator.refresh(consumer);
        // still not valid.  Probably not even set, but that doesn't matter
        assertFalse("valid".equals(consumer.getEntitlementStatus()));

        // Should not have changed
        assertFalse(entitlementCurator.find(ent.getId()).isUpdatedOnStart());
    }
}
