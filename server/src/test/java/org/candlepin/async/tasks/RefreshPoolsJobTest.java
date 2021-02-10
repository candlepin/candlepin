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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.async.JobArguments;
import org.candlepin.async.JobConfig;
import org.candlepin.async.JobConfigValidationException;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.controller.PoolManager;
import org.candlepin.controller.Refresher;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;



@ExtendWith(MockitoExtension.class)
public class RefreshPoolsJobTest {

    @Mock protected OwnerCurator ownerCurator;
    @Mock protected PoolManager poolManager;
    @Mock protected ProductServiceAdapter prodAdapter;
    @Mock protected SubscriptionServiceAdapter subAdapter;
    @Mock protected Refresher refresher;

    private RefreshPoolsJob buildRefreshPoolsJob() {
        return new RefreshPoolsJob(this.ownerCurator, this.poolManager, this.subAdapter, this.prodAdapter);
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

        RefreshPoolsJob job = this.buildRefreshPoolsJob();

        AsyncJobStatus status = mock(AsyncJobStatus.class);
        JobExecutionContext context = spy(new JobExecutionContext(status));
        doReturn(jobConfig.getJobArguments()).when(status).getJobArguments();

        doReturn(owner).when(ownerCurator).getByKey(eq("my-test-owner"));
        doReturn(refresher).when(poolManager).getRefresher(eq(subAdapter), eq(prodAdapter), eq(true));
        doReturn(refresher).when(refresher).add(eq(owner));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

        job.execute(context);

        verify(context, times(1)).setJobResult(captor.capture());
        Object result = captor.getValue();

        assertEquals("Pools refreshed for owner: my-test-owner-displayname", result);
    }

    @Test
    public void ensureJobFailure() {
        Owner owner = createTestOwner("my-test-owner", "test-log-level");

        JobConfig jobConfig = RefreshPoolsJob.createJobConfig()
            .setOwner(owner)
            .setLazyRegeneration(false);

        RefreshPoolsJob job = this.buildRefreshPoolsJob();

        AsyncJobStatus status = mock(AsyncJobStatus.class);
        JobExecutionContext context = spy(new JobExecutionContext(status));
        doReturn(jobConfig.getJobArguments()).when(status).getJobArguments();

        doReturn(owner).when(ownerCurator).getByKey(eq("my-test-owner"));
        doReturn(refresher).when(poolManager).getRefresher(eq(subAdapter), eq(prodAdapter), eq(false));
        doReturn(refresher).when(refresher).add(eq(owner));
        doThrow(new RuntimeException("something went wrong with refresh")).when(refresher).run();

        Exception e = assertThrows(JobExecutionException.class, () -> job.execute(context));
        assertEquals("something went wrong with refresh", e.getMessage());
    }

    @Test
    public void ensureJobExceptionThrownIfOwnerNotFound() {
        Owner owner = createTestOwner("my-test-owner", "test-log-level");

        JobConfig jobConfig = RefreshPoolsJob.createJobConfig()
            .setOwner(owner)
            .setLazyRegeneration(false);

        RefreshPoolsJob job = this.buildRefreshPoolsJob();

        AsyncJobStatus status = mock(AsyncJobStatus.class);
        JobExecutionContext context = spy(new JobExecutionContext(status));
        doReturn(jobConfig.getJobArguments()).when(status).getJobArguments();

        doReturn(null).when(ownerCurator).getByKey(eq("my-test-owner"));

        Exception e = assertThrows(JobExecutionException.class, () -> job.execute(context));
        assertEquals("Nothing to do; owner no longer exists: " + owner.getKey(), e.getMessage());
    }
}
