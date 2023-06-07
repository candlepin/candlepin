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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.candlepin.async.JobArguments;
import org.candlepin.async.JobConfig;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.controller.EntitlementCertificateGenerator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.util.Util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;


public class RegenProductEntitlementCertsJobTest {

    private EntitlementCertificateGenerator ecGenerator;
    private OwnerCurator ownerCurator;

    @BeforeEach
    public void setUp() {
        this.ecGenerator = mock(EntitlementCertificateGenerator.class);
        this.ownerCurator = mock(OwnerCurator.class);
    }

    public RegenProductEntitlementCertsJob buildTestJob() {
        return new RegenProductEntitlementCertsJob(this.ecGenerator, this.ownerCurator);
    }

    @Test
    public void testBasicConfig() {
        JobConfig config = RegenProductEntitlementCertsJob.createJobConfig();

        assertEquals(RegenProductEntitlementCertsJob.JOB_KEY, config.getJobKey());
        assertEquals(RegenProductEntitlementCertsJob.JOB_NAME, config.getJobName());
    }

    @Test
    public void testConfigSetProductId() {
        String productId = "test_prod_id";

        JobConfig config = RegenProductEntitlementCertsJob.createJobConfig()
            .setProductId(productId);

        // Verify the owner key was set
        JobArguments args = config.getJobArguments();

        assertNotNull(args);
        assertEquals(1, args.size());

        // We aren't concerned with the key it's stored under, just that it's stored. As such, we
        // need to get the key from the key set so we can reference it for use with getAsString.
        String argKey = args.keySet().iterator().next();

        assertNotNull(argKey);
        assertFalse(argKey.isEmpty());
        assertEquals(productId, args.getAsString(argKey));
    }

    @Test
    public void testConfigSetLazyRegen() {
        boolean lazyRegen = true;

        JobConfig config = RegenProductEntitlementCertsJob.createJobConfig()
            .setLazyRegeneration(lazyRegen);

        // Verify the owner key was set
        JobArguments args = config.getJobArguments();

        assertNotNull(args);
        assertEquals(1, args.size());

        // We aren't concerned with the key it's stored under, just that it's stored. As such, we
        // need to get the key from the key set so we can reference it for use with getAsBoolean.
        String argKey = args.keySet().iterator().next();

        assertNotNull(argKey);
        assertFalse(argKey.isEmpty());
        assertEquals(lazyRegen, args.getAsBoolean(argKey));
    }

    @Test
    public void testExecution() throws JobExecutionException {
        String productId = "test_prod_id";
        boolean lazyRegen = true;

        Set<String> productIds = Collections.singleton(productId);

        Owner owner1 = new Owner("test_owner_key-1", "test_owner_name-1");
        Owner owner2 = new Owner("test_owner_key-2", "test_owner_name-2");
        Owner owner3 = new Owner("test_owner_key-3", "test_owner_name-3");

        Set<Owner> matchingOwners = Util.asSet(owner1, owner2, owner3);
        doReturn(matchingOwners).when(this.ownerCurator).getOwnersWithProducts(productIds);

        JobConfig config = RegenProductEntitlementCertsJob.createJobConfig()
            .setProductId(productId)
            .setLazyRegeneration(lazyRegen);

        JobExecutionContext context = mock(JobExecutionContext.class);
        doReturn(config.getJobArguments()).when(context).getJobArguments();


        RegenProductEntitlementCertsJob testJob = this.buildTestJob();
        testJob.execute(context);

        verify(this.ownerCurator, times(1)).getOwnersWithProducts(productIds);
        verify(this.ecGenerator, times(1))
            .regenerateCertificatesOf(owner1, productId, lazyRegen);
        verify(this.ecGenerator, times(1))
            .regenerateCertificatesOf(owner2, productId, lazyRegen);
        verify(this.ecGenerator, times(1))
            .regenerateCertificatesOf(owner3, productId, lazyRegen);
    }

    @Test
    public void testExecutionWithNoMatchingOwners() throws JobExecutionException {
        String productId = "test_prod_id";
        boolean lazyRegen = true;

        Set<String> productIds = Collections.singleton(productId);
        Set<Owner> matchingOwners = Util.asSet();
        doReturn(matchingOwners).when(this.ownerCurator).getOwnersWithProducts(productIds);

        JobConfig config = RegenProductEntitlementCertsJob.createJobConfig()
            .setProductId(productId)
            .setLazyRegeneration(lazyRegen);

        JobExecutionContext context = mock(JobExecutionContext.class);
        doReturn(config.getJobArguments()).when(context).getJobArguments();

        RegenProductEntitlementCertsJob testJob = this.buildTestJob();
        testJob.execute(context);

        verify(this.ownerCurator, times(1)).getOwnersWithProducts(productIds);
        verify(this.ecGenerator, never())
            .regenerateCertificatesOf(any(Owner.class), anyString(), anyBoolean());
    }
}
