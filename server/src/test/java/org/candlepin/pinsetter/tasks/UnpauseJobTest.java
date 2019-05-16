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
import static org.mockito.Mockito.*;
import static org.quartz.JobBuilder.*;

import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.EmptyCandlepinQuery;
import org.candlepin.model.JobCurator;
import org.candlepin.pinsetter.core.PinsetterException;
import org.candlepin.pinsetter.core.PinsetterKernel;
import org.candlepin.pinsetter.core.model.JobStatus;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;
import org.quartz.JobKey;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * UnpauseJobTest
 */
public class UnpauseJobTest extends BaseJobTest{
    private UnpauseJob unpauseJob;
    @Mock private JobCurator jobCurator;
    @Mock private PinsetterKernel pk;
    @Mock private JobExecutionContext ctx;


    @Before
    public void init() {
        super.init();
        MockitoAnnotations.initMocks(this);
        unpauseJob = new UnpauseJob(jobCurator, pk);
        injector.injectMembers(unpauseJob);
    }

    @Test
    public void noUnPausesTest() throws JobExecutionException {
        when(jobCurator.findWaitingJobs()).thenReturn(new EmptyCandlepinQuery<>());
        unpauseJob.execute(ctx);
        try {
            verify(pk, never()).addTrigger(any(JobStatus.class));
        }
        catch (SchedulerException e) {
            fail("Should not be executed, much less fail");
        }
    }

    @Test
    public void unPauseTest() throws JobExecutionException, PinsetterException {
        JobDetail jd = newJob(KingpinJob.class)
            .withIdentity("Kayfabe", "Deluxe")
            .build();

        JobStatus js = new JobStatus(jd, true);
        List<JobStatus> jl = new ArrayList<>();
        jl.add(js);

        CandlepinQuery query = mock(CandlepinQuery.class);
        when(query.list()).thenReturn(jl);
        when(jobCurator.findWaitingJobs()).thenReturn(query);

        unpauseJob.execute(ctx);
        try {
            verify(pk, atLeastOnce()).addTrigger(js);
        }
        catch (SchedulerException e) {
            fail("Should not throw an exception");
        }
    }

    @Test
    public void ensureJobCancelledWhenJobClassNoLongerExists() throws Exception {
        JobDetail detail = Mockito.mock(JobDetail.class);
        JobKey key = new JobKey("fake-job");
        when(detail.getKey()).thenReturn(key);

        // Allowing setting the value of JobStatus.jobClass since it is private.
        // Do not want to expose the setter.
        Class<? extends JobStatus> statusClass = JobStatus.class;
        Field jobClassField = statusClass.getDeclaredField("jobClass");
        jobClassField.setAccessible(true);

        JobStatus status = new JobStatus();
        jobClassField.set(status, "unknown.class");
        status.setState(JobStatus.JobState.CREATED);
        assertEquals("unknown.class", status.getJobClass());

        List<JobStatus> jl = new ArrayList<>();
        jl.add(status);

        CandlepinQuery query = mock(CandlepinQuery.class);
        when(query.list()).thenReturn(jl);
        when(jobCurator.findWaitingJobs()).thenReturn(query);

        unpauseJob.execute(ctx);

        assertEquals(JobStatus.JobState.CANCELED, status.getState());
        assertEquals("Job canceled because job class no longer exists.", status.getResult());
        verify(jobCurator).merge(eq(status));
    }

}
