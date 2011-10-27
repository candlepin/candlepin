/**
 * Copyright (c) 2009 Red Hat, Inc.
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

import static org.mockito.Mockito.when;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


import org.candlepin.model.JobCurator;
import org.candlepin.pinsetter.core.PinsetterException;
import org.candlepin.pinsetter.core.PinsetterKernel;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeastOnce;

import org.quartz.Job;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

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
        when(j.findCanceledJobs()).thenReturn(new ArrayList<JobStatus>());
        cancelJobJob.execute(ctx);
    }

    @Test
    public void cancelTest() throws JobExecutionException, PinsetterException {
        JobDetail jd = new JobDetail("Kayfabe", "Deluxe", Job.class);
        JobStatus js = new JobStatus(jd);
        List<JobStatus> jl = new ArrayList<JobStatus>();
        jl.add(js);
        when(j.findCanceledJobs()).thenReturn(jl);
        cancelJobJob.execute(ctx);
        verify(pk, atLeastOnce()).cancelJob((Serializable) "Kayfabe", "Deluxe");
    }

}
