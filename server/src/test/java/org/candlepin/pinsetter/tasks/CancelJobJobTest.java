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

import org.candlepin.model.JobCurator;
import org.candlepin.pinsetter.core.PinsetterException;
import org.candlepin.pinsetter.core.PinsetterKernel;
import org.candlepin.pinsetter.core.model.JobStatus;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.SchedulerException;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * CancelJobJobTest
 */
public class CancelJobJobTest {
    private CancelJobJob cancelJobJob;
    @Mock private JobCurator j;
    @Mock private PinsetterKernel pk;
    @Mock private JobExecutionContext ctx;


    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        cancelJobJob = new CancelJobJob(j, pk);
    }

    @Test
    public void noCancellationsTest() throws JobExecutionException {
        when(j.findCanceledJobs(any(Collection.class))).thenReturn(Collections.<JobStatus>emptySet());
        cancelJobJob.execute(ctx);

        try {
            verify(pk, never()).cancelJob(any(Serializable.class), any(String.class));
        }
        catch (PinsetterException e) {
            fail("Should not be executed, much less fail");
        }
    }

    @Test
    public void cancelTest() throws JobExecutionException, PinsetterException, SchedulerException {
        JobDetail jd = newJob(Job.class)
            .withIdentity("Kayfabe", "Deluxe")
            .build();

        JobStatus js = new JobStatus(jd);
        Set<JobStatus> jl = new HashSet<JobStatus>();
        jl.add(js);

        Set<JobKey> jobKeys = new HashSet<JobKey>();
        jobKeys.add(new JobKey("Kayfabe"));

        when(pk.getSingleJobKeys()).thenReturn(jobKeys);
        when(j.findCanceledJobs(any(Collection.class))).thenReturn(jl);

        cancelJobJob.execute(ctx);
        verify(pk, atLeastOnce()).cancelJob((Serializable) "Kayfabe", "Deluxe");
    }

}
