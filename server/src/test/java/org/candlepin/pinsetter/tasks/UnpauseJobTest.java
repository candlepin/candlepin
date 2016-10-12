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
import static org.mockito.Matchers.*;
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
import org.mockito.MockitoAnnotations;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;

import java.util.ArrayList;
import java.util.List;

/**
 * UnpauseJobTest
 */
public class UnpauseJobTest {
    private UnpauseJob unpauseJob;
    @Mock private JobCurator j;
    @Mock private PinsetterKernel pk;
    @Mock private JobExecutionContext ctx;


    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        unpauseJob = new UnpauseJob(j, pk);
    }

    @Test
    public void noUnPausesTest() throws JobExecutionException {
        when(j.findWaitingJobs()).thenReturn(new EmptyCandlepinQuery<JobStatus>());
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
        List<JobStatus> jl = new ArrayList<JobStatus>();
        jl.add(js);

        CandlepinQuery query = mock(CandlepinQuery.class);
        when(query.list()).thenReturn(jl);
        when(j.findWaitingJobs()).thenReturn(query);

        unpauseJob.execute(ctx);
        try {
            verify(pk, atLeastOnce()).addTrigger(js);
        }
        catch (SchedulerException e) {
            fail("Should not throw an exception");
        }
    }
}
