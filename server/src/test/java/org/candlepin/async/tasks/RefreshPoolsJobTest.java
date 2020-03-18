/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;

import org.candlepin.async.JobArguments;
import org.candlepin.async.JobConfig;
import org.candlepin.async.JobConfigValidationException;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.controller.PoolManager;
import org.candlepin.controller.Refresher;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;



@ExtendWith(MockitoExtension.class)
public class RefreshPoolsJobTest {

    private RefreshPoolsJob job;
    @Mock protected OwnerCurator ownerCurator;
    @Mock protected PoolManager poolManager;
    @Mock protected SubscriptionServiceAdapter subAdapter;
    @Mock protected Refresher refresher;
    @Mock private JobExecutionContext ctx;

    @BeforeEach
    public void setupTest() {
        job = new RefreshPoolsJob(ownerCurator, poolManager, subAdapter);
    }

    private Owner createTestOwner(String key, String logLevel) {
        Owner owner = TestUtil.createOwner();

        owner.setId(TestUtil.randomString());
        owner.setKey(key);
        owner.setLogLevel(logLevel);

        return owner;
    }

    @Test
    public void testJobConfigSetOwner() {
        Owner owner = this.createTestOwner("owner_key", "log_level");

        JobConfig config = RefreshPoolsJob.createJobConfig()
            .setOwner(owner);

        assertEquals(owner, config.getContextOwner());
    }

    @Test
    public void testJobConfigSetLazyRegeneration() {
        JobConfig config = RefreshPoolsJob.createJobConfig()
            .setLazyRegeneration(true);

        JobArguments args = config.getJobArguments();

        assertTrue(args.containsKey(RefreshPoolsJob.LAZY_REGEN));
        assertEquals(true, args.getAsBoolean(RefreshPoolsJob.LAZY_REGEN));
    }

    @Test
    public void testValidate() throws JobConfigValidationException {
        Owner owner = this.createTestOwner("owner_key", "log_level");

        JobConfig config = RefreshPoolsJob.createJobConfig()
            .setOwner(owner)
            .setLazyRegeneration(false);

        config.validate();
    }

    @Test
    public void testValidateNoOwner() {
        JobConfig config = RefreshPoolsJob.createJobConfig()
            .setLazyRegeneration(true);

        assertThrows(JobConfigValidationException.class, config::validate);
    }

    @Test
    public void testValidateNoLazyRegeneration() {
        Owner owner = this.createTestOwner("owner_key", "log_level");

        JobConfig config = RefreshPoolsJob.createJobConfig()
            .setOwner(owner);

        assertThrows(JobConfigValidationException.class, config::validate);
    }

    @Test
    public void ensureJobSuccess() throws Exception {
        Owner owner = createTestOwner("my-test-owner", "test-log-level");
        owner.setDisplayName("my-test-owner-displayname");
        JobConfig jobConfig = RefreshPoolsJob.createJobConfig()
            .setOwner(owner)
            .setLazyRegeneration(true);

        doReturn(jobConfig.getJobArguments()).when(ctx).getJobArguments();
        doReturn(owner).when(ownerCurator).getByKey(eq("my-test-owner"));
        doReturn(refresher).when(poolManager).getRefresher(eq(subAdapter), eq(true));
        doReturn(refresher).when(refresher).add(eq(owner));

        Object actualResult = this.job.execute(ctx);

        assertEquals("Pools refreshed for owner my-test-owner-displayname", actualResult);
    }

    @Test
    public void ensureJobFailure() {
        Owner owner = createTestOwner("my-test-owner", "test-log-level");

        JobConfig jobConfig = RefreshPoolsJob.createJobConfig()
            .setOwner(owner)
            .setLazyRegeneration(false);

        doReturn(jobConfig.getJobArguments()).when(ctx).getJobArguments();
        doReturn(owner).when(ownerCurator).getByKey(eq("my-test-owner"));
        doReturn(refresher).when(poolManager).getRefresher(eq(subAdapter), eq(false));
        doReturn(refresher).when(refresher).add(eq(owner));
        doThrow(new RuntimeException("something went wrong with refresh")).when(refresher).run();

        Exception e = assertThrows(JobExecutionException.class, () -> job.execute(ctx));
        assertEquals("something went wrong with refresh", e.getMessage());
    }

    @Test
    public void ensureJobExceptionThrownIfOwnerNotFound() {
        Owner owner = createTestOwner("my-test-owner", "test-log-level");

        JobConfig jobConfig = RefreshPoolsJob.createJobConfig()
            .setOwner(owner)
            .setLazyRegeneration(false);

        doReturn(jobConfig.getJobArguments()).when(ctx).getJobArguments();
        doReturn(null).when(ownerCurator).getByKey(eq("my-test-owner"));

        Exception e = assertThrows(JobExecutionException.class, () -> job.execute(ctx));
        assertEquals("Nothing to do. Owner no longer exists", e.getMessage());
    }
}
