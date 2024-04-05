/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

import org.candlepin.async.JobConfigValidationException;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.async.tasks.RevokeEntitlementsJob.RevokeEntitlementsJobConfig;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.controller.PoolService;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

@ExtendWith(MockitoExtension.class)
public class RevokeEntitlementsJobTest {
    private static final String JOB_KEY = "EntitlementRevokingJob";
    private static final String CFG_BATCH_SIZE = "batch_size";

    @Mock
    private Configuration config;
    @Mock
    private ConsumerCurator consumerCurator;
    @Mock
    private OwnerCurator ownerCurator;
    @Mock
    private PoolService poolService;
    @Mock
    private ActivationKeyCurator activationKeyCurator;

    @ParameterizedTest(name = "{displayName} {index}: {0} {1}")
    @NullAndEmptySource
    public void testRevokeEntitlementsJobConfigSetOwnerWithInvalidKey(String ownerKey)
        throws JobExecutionException {
        RevokeEntitlementsJobConfig jobConfig = new RevokeEntitlementsJobConfig();

        Owner owner = new Owner()
            .setKey(ownerKey);

        assertThrows(IllegalArgumentException.class, () -> jobConfig.setOwner(owner));
    }

    @Test
    public void testRevokeEntitlementsJobConfigSetOwnerWithNullOwner()
        throws JobExecutionException {
        RevokeEntitlementsJobConfig jobConfig = new RevokeEntitlementsJobConfig();

        assertThrows(IllegalArgumentException.class, () -> jobConfig.setOwner(null));
    }

    @Test
    public void testRevokeEntitlementsJobConfigValidateWithMissingOwnerKey() throws JobExecutionException {
        RevokeEntitlementsJobConfig jobConfig = new RevokeEntitlementsJobConfig();

        assertThrows(JobConfigValidationException.class, () -> jobConfig.validate());
    }

    @Test
    public void testRevokeEntitlementsJobConfigValidate() throws Exception {
        Owner owner = new Owner()
            .setKey(TestUtil.randomString());

        RevokeEntitlementsJobConfig jobConfig = new RevokeEntitlementsJobConfig()
            .setOwner(owner);

        jobConfig.validate();

        // Assert no exception
    }

    @Test
    public void testExecuteWithNoExistingOwner() throws JobExecutionException {
        RevokeEntitlementsJob job = new RevokeEntitlementsJob(config, consumerCurator, ownerCurator,
            poolService, activationKeyCurator);

        Owner owner = new Owner()
            .setKey(TestUtil.randomString());

        RevokeEntitlementsJobConfig jobConfig = new RevokeEntitlementsJobConfig()
            .setOwner(owner);

        AsyncJobStatus jobStatus = new AsyncJobStatus()
            .setJobArguments(jobConfig.getJobArguments());
        JobExecutionContext context = new JobExecutionContext(jobStatus);

        assertThrows(JobExecutionException.class, () -> job.execute(context));
    }

    @Test
    public void testExecuteWithInvalidBatchSize() throws JobExecutionException {
        RevokeEntitlementsJob job = new RevokeEntitlementsJob(config, consumerCurator, ownerCurator,
            poolService, activationKeyCurator);

        String ownerKey = TestUtil.randomString();
        Owner owner = new Owner();
        owner.setKey(ownerKey);
        doReturn(owner).when(ownerCurator).getByKey(ownerKey);

        RevokeEntitlementsJobConfig jobConfig = new RevokeEntitlementsJobConfig()
            .setOwner(owner);

        AsyncJobStatus jobStatus = new AsyncJobStatus()
            .setJobArguments(jobConfig.getJobArguments());
        JobExecutionContext context = new JobExecutionContext(jobStatus);

        doReturn(-10).when(config).getInt(ConfigProperties.jobConfig(JOB_KEY, CFG_BATCH_SIZE));

        assertThrows(JobExecutionException.class, () -> job.execute(context));
    }

    @Test
    public void testExecute() throws JobExecutionException {
        RevokeEntitlementsJob job = new RevokeEntitlementsJob(config, consumerCurator, ownerCurator,
            poolService, activationKeyCurator);

        String ownerKey = TestUtil.randomString();
        Owner owner = new Owner();
        owner.setKey(ownerKey);
        doReturn(owner).when(ownerCurator).getByKey(ownerKey);

        RevokeEntitlementsJobConfig jobConfig = new RevokeEntitlementsJobConfig()
            .setOwner(owner);

        AsyncJobStatus jobStatus = new AsyncJobStatus()
            .setJobArguments(jobConfig.getJobArguments());
        JobExecutionContext context = new JobExecutionContext(jobStatus);

        int removedPools = 2;
        doReturn(removedPools).when(activationKeyCurator).removeActivationKeyPools(ownerKey);
        doReturn(1).when(config).getInt(ConfigProperties.jobConfig(JOB_KEY, CFG_BATCH_SIZE));
        List<String> consumerUuids = List.of(TestUtil.randomString(), TestUtil.randomString());
        doReturn(consumerUuids).when(consumerCurator).getSystemConsumerUuidsByOwner(ownerKey);
        doReturn(1).when(poolService).revokeAllEntitlements(any(List.class), eq(false));

        job.execute(context);

        assertThat(jobStatus.getJobResult())
            // Validating that one entitlement per consumer was revoked
            .contains(String.valueOf(consumerUuids.size()))
            // Validating the pools was removed
            .contains(String.valueOf(removedPools));
    }
}
