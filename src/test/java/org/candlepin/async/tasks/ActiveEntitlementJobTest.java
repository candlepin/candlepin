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
package org.candlepin.async.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.util.Util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;



public class ActiveEntitlementJobTest extends DatabaseTestFixture {

    private ActiveEntitlementJob job;

    private Owner owner;
    private ConsumerType ct;
    private Consumer consumer;
    private Product prod;

    @BeforeEach
    public void init() throws Exception {
        super.init(false);

        job = this.injector.getInstance(ActiveEntitlementJob.class);
        this.owner = this.ownerCurator.create(new Owner()
            .setKey("test-owner")
            .setDisplayName("Test Owner")
            .setContentAccessMode("entitlement"));

        prod = this.createProduct("1", "2");

        ct = new ConsumerType(ConsumerType.ConsumerTypeEnum.SYSTEM);
        ct = consumerTypeCurator.create(ct);

        consumer = new Consumer()
            .setType(ct)
            .setOwner(owner)
            .setName("a consumer")
            .setUsername("username");

        consumer.addInstalledProduct(new ConsumerInstalledProduct()
            .setProductId(prod.getId())
            .setProductName(prod.getName()));

        consumerCurator.create(consumer);
    }

    @Test
    public void testActiveEntitlementJob() throws JobExecutionException {
        Pool p = createPool(owner, prod, 5L, Util.yesterday(), Util.tomorrow());
        Entitlement ent = this.createEntitlement(owner, consumer, p,
            createEntitlementCertificate("entkey", "ecert"));
        // Needs to be flipped
        ent.setUpdatedOnStart(false);
        entitlementCurator.create(ent);

        consumerCurator.refresh(consumer);
        assertNotEquals("valid", consumer.getEntitlementStatus());

        JobExecutionContext context = mock(JobExecutionContext.class);
        job.execute(context);

        consumerCurator.refresh(consumer);
        assertEquals("valid", consumer.getEntitlementStatus());

        // Should have changed
        assertTrue(entitlementCurator.get(ent.getId()).isUpdatedOnStart());
    }

    @Test
    public void testActiveEntitlementJobNoChange() throws JobExecutionException {
        Pool p = createPool(owner, prod, 5L, Util.yesterday(), Util.tomorrow());
        Entitlement ent = this
            .createEntitlement(owner, consumer, p, createEntitlementCertificate("entkey", "ecert"));
        // Already done
        ent.setUpdatedOnStart(true);
        entitlementCurator.create(ent);

        JobExecutionContext context = mock(JobExecutionContext.class);
        job.execute(context);

        // Unchanged
        assertTrue(entitlementCurator.get(ent.getId()).isUpdatedOnStart());
    }

    @Test
    public void testActiveEntitlementJobStillInactive() throws JobExecutionException {
        // Future pool
        Pool p = createPool(owner, prod, 5L, Util.tomorrow(), Util.addDaysToDt(10));
        Entitlement ent = this
            .createEntitlement(owner, consumer, p, createEntitlementCertificate("entkey", "ecert"));
        // Needs to be flipped, eventually
        ent.setUpdatedOnStart(false);
        entitlementCurator.create(ent);

        consumerCurator.refresh(consumer);
        assertNotEquals("valid", consumer.getEntitlementStatus());

        JobExecutionContext context = mock(JobExecutionContext.class);
        job.execute(context);
        consumerCurator.refresh(consumer);
        // still not valid. Probably not even set, but that doesn't matter
        assertNotEquals("valid", consumer.getEntitlementStatus());

        // Should not have changed
        assertFalse(entitlementCurator.get(ent.getId()).isUpdatedOnStart());
    }
}
