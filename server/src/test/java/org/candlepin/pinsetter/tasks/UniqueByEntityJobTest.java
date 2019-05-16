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

import static org.mockito.Mockito.*;
import static org.quartz.JobBuilder.newJob;

import org.candlepin.model.JobCurator;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.pinsetter.core.model.JobStatus.JobState;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.ListenerManager;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;

public class UniqueByEntityJobTest {

    @Mock private JobCurator jobCurator;
    @Mock private Scheduler scheduler;
    @Mock private ListenerManager lm;
    @Mock private Trigger t;
    @Mock private JobStatus status;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    /*
     * if a UniqueByEntityJob's skipIfExists is true, don't schedule another job
     */
    @Test
    public void skipIfExistsTest() throws JobExecutionException, SchedulerException {
        JobDataMap map = new JobDataMap();
        map.put(JobStatus.TARGET_ID, "TaylorSwift");
        JobKey jobKey = new JobKey("name", "group");
        JobDetail detail = newJob(TestUniqueByEntityJob.class)
            .withIdentity(jobKey)
            .requestRecovery(true)
            .usingJobData(map).storeDurably(true)
            .build();
        JobStatus preExistingJobStatus = new JobStatus();
        preExistingJobStatus.setState(JobState.WAITING);
        TestUniqueByEntityJob job = new TestUniqueByEntityJob();
        when(jobCurator.getByClassAndTarget(eq("TaylorSwift"), any(Class.class))).thenReturn(
            preExistingJobStatus);

        JobStatus resultStatus = job.scheduleJob(jobCurator, null, detail, null);
        assertEquals(preExistingJobStatus, resultStatus);
    }

    /*
     * if a job creation fails, ensure we update status and re-throw the runtime exception
     */
    @Test
    public void failsCreationGracefullyTest() throws JobExecutionException, SchedulerException {
        JobDataMap map = new JobDataMap();
        map.put(JobStatus.TARGET_ID, "TaylorSwift");
        JobKey jobKey = new JobKey("name", "group");
        JobDetail detail = newJob(TestUniqueByEntityJob.class).withIdentity(jobKey).requestRecovery(true)
            .usingJobData(map).storeDurably(true).build();
        JobStatus preExistingJobStatus = new JobStatus();
        preExistingJobStatus.setState(JobState.WAITING);
        TestUniqueByEntityJob job = new TestUniqueByEntityJob();

        when(jobCurator.create(any(JobStatus.class))).thenReturn(status);
        when(scheduler.getListenerManager()).thenReturn(lm);
        when(scheduler.scheduleJob(any(JobDetail.class), any(Trigger.class))).thenThrow(new
            RuntimeException("covfefe"));
        try {
            job.scheduleJob(jobCurator, scheduler, detail, t);
            fail();
        }
        catch (RuntimeException e) {
            assertEquals(e.getMessage(), "covfefe");
        }

        verify(status, times(1)).setState(JobState.FAILED);
    }
}
