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
package org.candlepin.model;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.quartz.JobBuilder.newJob;

import org.candlepin.auth.Principal;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.pinsetter.core.PinsetterJobListener;
import org.candlepin.pinsetter.core.PinsetterKernel;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.pinsetter.core.model.JobStatus.JobState;
import org.candlepin.pinsetter.tasks.HealEntireOrgJob;
import org.candlepin.pinsetter.tasks.RefreshPoolsJob;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.util.Util;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * JobCuratorTest
 */
public class JobCuratorTest extends DatabaseTestFixture {

    private JobCurator curator;

    @Before
    @Override
    public void init() {
        super.init();
        this.curator = this.injector.getInstance(JobCurator.class);
    }


    /**
     *All the job status objects which have executed successfully and
     *are clear for deletion should be swept away from the db.
     */
    @Test
    public void completedAndSelectedByDateCriteriaShouldBeDeleted() {
        newJobStatus().startTime(new Date())
            .finishTime(Util.tomorrow()).create();
        this.curator.cleanUpOldCompletedJobs(Util.addDaysToDt(2));
        assertEquals(0, this.curator.listAll().size());
    }

    /**
     * Jobs which have not completed execution should stay in db
     */
    @Test
    public void notCompletedButSelectedByDateCriteriaShouldNotBeDeleted() {
        newJobStatus().finishTime(Util.yesterday()).create();
        this.curator.cleanUpOldCompletedJobs(Util.tomorrow());
        assertEquals(1, this.curator.listAll().size());
    }

    /**
     * Jobs which are completed but don't pass the selection criteria
     * should stay in the db.
     */
    @Test
    public void completedButNotSelectedByDateCriteriaShouldNotBeDeleted() {
        newJobStatus().startTime(Util.yesterday()).finishTime(new Date())
            .create();
        this.curator.cleanUpOldCompletedJobs(Util.yesterday());
        assertEquals(1, this.curator.listAll().size());
    }

    /**
     * Jobs which neither completed nor pass selection criteria
     * should stay in db.
     */
    @Test
    public void notCompletedAndNotSelectedByDateCriteriaShouldNotBeDeleted() {
        newJobStatus().startTime(Util.yesterday()).create();
        this.curator.cleanUpOldCompletedJobs(Util.tomorrow());
        assertEquals(1, this.curator.listAll().size());
    }

    @Test
    public void failedJobs() {
        newJobStatus().startTime(Util.yesterday()).finishTime(null)
            .result("wrong pool").state(JobState.FAILED).create();
        this.curator.cleanupAllOldJobs(new Date());
        assertEquals(0, this.curator.listAll().size());
    }

    @Test
    public void findByPrincipalName() {
        JobStatus job = newJobStatus().principalName("donald").owner("ducks").create();
        List<JobStatus> jobs = this.curator.findByPrincipalName("donald");
        assertNotNull(jobs);
        assertEquals("donald", job.getPrincipalName());
        assertEquals(job, jobs.get(0));
    }

    @Test(expected = NotFoundException.class)
    public void cancelNonExistentJob() {
        curator.cancel("dont_exist");
    }

    @Test
    public void cancel() {
        String jobid = newJobStatus().owner("ducks")
            .startTime(Util.yesterday()).create().getId();
        JobStatus job = curator.cancel(jobid);
        assertNotNull(job);
        assertEquals(jobid, job.getId());
        assertEquals(JobStatus.JobState.CANCELED, job.getState());
    }

    @Test
    public void updateWithLargeResult() {
        String longstr = RandomStringUtils.randomAlphanumeric(300);
        JobExecutionContext ctx = mock(JobExecutionContext.class);
        when(ctx.getFireTime()).thenReturn(new Date());
        when(ctx.getJobRunTime()).thenReturn(1000L);
        when(ctx.getResult()).thenReturn(longstr);

        JobStatus status = newJobStatus().owner("terps").create();
        status.update(ctx);
        curator.merge(status);
    }

    @Test
    public void findWaitingJobsTest() {
        JobStatus waitingJob1 = newJobStatus().state(JobStatus.JobState.WAITING)
            .startTime(Util.yesterday()).create();
        JobStatus waitingJob2 = newJobStatus().state(JobStatus.JobState.WAITING)
            .startTime(Util.yesterday()).create();
        JobStatus createdJob = newJobStatus().state(JobStatus.JobState.CREATED)
            .startTime(Util.yesterday()).create();
        JobStatus finishedJob = newJobStatus().state(JobStatus.JobState.FINISHED)
            .startTime(Util.yesterday()).create();
        List<JobStatus> waitingList = curator.findWaitingJobs();
        assertTrue(waitingList.contains(waitingJob1));
        assertTrue(waitingList.contains(waitingJob2));
        assertFalse(waitingList.contains(createdJob));
        assertFalse(waitingList.contains(finishedJob));
    }

    @Test
    public void findNumRunningByOwnerAndClass() {
        newJobStatus().state(JobStatus.JobState.WAITING)
            .owner("some_owner").create();
        newJobStatus().state(JobStatus.JobState.WAITING)
            .owner("my_owner").create();
        newJobStatus().state(JobStatus.JobState.RUNNING)
            .owner("my_owner").create();
        newJobStatus().state(JobStatus.JobState.CREATED)
            .owner("some_owner").create();
        newJobStatus().state(JobStatus.JobState.FINISHED)
            .owner("my_owner").create();
        newJobStatus().state(JobStatus.JobState.RUNNING)
            .owner("some_owner").create();
        newJobStatus().state(JobStatus.JobState.RUNNING)
            .owner("other_owner").create();
        newJobStatus().state(JobStatus.JobState.RUNNING)
            .jobClass(RefreshPoolsJob.class)
            .owner("my_owner").create();
        newJobStatus().state(JobStatus.JobState.RUNNING)
            .jobClass(HealEntireOrgJob.class)
            .owner("my_owner").create();
        long result = curator.findNumRunningByOwnerAndClass("my_owner",
            RefreshPoolsJob.class);
        assertEquals(1, result);
    }

    @Test
    public void getLatestByClassAndOwner() {
        newJobStatus().state(JobStatus.JobState.WAITING)
            .owner("my_owner")
            .jobClass(HealEntireOrgJob.class).create();
        newJobStatus().state(JobStatus.JobState.RUNNING)
            .owner("my_owner")
            .jobClass(HealEntireOrgJob.class).create();
        newJobStatus().state(JobStatus.JobState.RUNNING)
            .owner("my_owner")
            .jobClass(HealEntireOrgJob.class).create();
        JobStatus expected = newJobStatus().state(JobStatus.JobState.CREATED)
            .jobClass(HealEntireOrgJob.class)
            .owner("my_owner").create();

        // Would be chosen if the job class was correct
        newJobStatus().state(JobStatus.JobState.WAITING)
            .owner("my_owner")
            .jobClass(RefreshPoolsJob.class).create();
        // Would be chosen if the owner was correct
        newJobStatus().state(JobStatus.JobState.WAITING)
            .owner("some_owner")
            .jobClass(HealEntireOrgJob.class).create();
        // Would be chosen if the jobstate wasn't done
        newJobStatus().state(JobStatus.JobState.FINISHED)
            .jobClass(HealEntireOrgJob.class)
            .owner("my_owner").create();
        JobStatus result = curator.getByClassAndOwner("my_owner",
            HealEntireOrgJob.class);
        assertEquals(expected, result);
    }

    @Test
    public void cancelOrphanedJobs() throws InterruptedException {
        JobStatus status1 = newJobStatus().state(JobStatus.JobState.WAITING)
            .id("1").create();
        JobStatus status2 = newJobStatus().state(JobStatus.JobState.WAITING)
            .id("2").create();
        JobStatus status3 = newJobStatus().state(JobStatus.JobState.RUNNING)
            .id("3").create();
        JobStatus status4 = newJobStatus().state(JobStatus.JobState.CREATED)
            .id("4").create();
        JobStatus status5 = newJobStatus().state(JobStatus.JobState.RUNNING)
            .id("5").create();
        List<String> activeIds = new LinkedList<String>();
        activeIds.add(status1.getId());
        activeIds.add(status3.getId());
        activeIds.add(status4.getId());
        int updated = curator.cancelOrphanedJobs(activeIds, 0L);
        assertEquals(2, updated);
        curator.refresh(status1);
        curator.refresh(status2);
        curator.refresh(status3);
        curator.refresh(status4);
        curator.refresh(status5);
        assertEquals(JobStatus.JobState.WAITING, status1.getState());
        assertEquals(JobStatus.JobState.CANCELED, status2.getState());
        assertEquals(JobStatus.JobState.RUNNING, status3.getState());
        assertEquals(JobStatus.JobState.CREATED, status4.getState());
        assertEquals(JobStatus.JobState.CANCELED, status5.getState());
    }

    private JobStatusBuilder newJobStatus() {
        return new JobStatusBuilder();
    }

    private class JobStatusBuilder{
        private String id;
        private Date startDt;
        private Date endDt;
        private String result;
        private JobState state;
        private String ownerkey;
        private String principalName;
        private JobDataMap map;
        private Class<? extends Job> jobClass = Job.class;

        public JobStatusBuilder() {
            id("id" + Math.random());
            map = new JobDataMap();
        }

        public JobStatusBuilder id(String id) {
            this.id = id;
            return this;
        }

        public JobStatusBuilder startTime(Date dt) {
            this.startDt = dt;
            return this;
        }

        public JobStatusBuilder finishTime(Date dt) {
            this.endDt = dt;
            return this;
        }

        public JobStatusBuilder result(String result) {
            this.result = result;
            return this;
        }

        public JobStatusBuilder state(JobState state) {
            this.state = state;
            return this;
        }

        public JobStatusBuilder owner(String key) {
            this.ownerkey = key;
            return this;
        }

        public JobStatusBuilder principalName(String name) {
            this.principalName = name;
            return this;
        }

        public JobStatusBuilder jobClass(Class<? extends Job> clazz) {
            this.jobClass = clazz;
            return this;
        }

        @SuppressWarnings("serial")
        public JobStatus create() {
            //sigh - all of this pain to construct a JobDetail
            //which does not have setters!
            Principal p = mock(Principal.class);
            when(p.getPrincipalName()).thenReturn(principalName);

            map.put(PinsetterJobListener.PRINCIPAL_KEY, p);
            map.put(JobStatus.TARGET_TYPE, JobStatus.TargetType.OWNER);
            map.put(JobStatus.TARGET_ID, ownerkey);
            JobStatus status = new JobStatus(
                newJob(jobClass).withIdentity(id, PinsetterKernel.SINGLE_JOB_GROUP)
                .usingJobData(map).build());
            JobExecutionContext context = mock(JobExecutionContext.class);
            when(context.getFireTime()).thenReturn(startDt);
            long time = -1;
            if (endDt != null && startDt != null) {
                time = endDt.getTime() - startDt.getTime();
            }
            when(context.getJobRunTime()).thenReturn(time);
            when(context.getResult()).thenReturn(result);
            status.update(context);
            if (state != null) {
                status.setState(state);
            }
            return curator.create(status);
        }

    }
}
