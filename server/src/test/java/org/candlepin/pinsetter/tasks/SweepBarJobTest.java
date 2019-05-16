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

import org.candlepin.model.JobCurator;
import org.candlepin.pinsetter.core.PinsetterKernel;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.SchedulerException;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * SweepBarJobTest
 */
public class SweepBarJobTest extends BaseJobTest {

    public static final String TEST_KEY = "test key";
    private SweepBarJob sweepBarJob;
    @Mock private JobCurator j;
    @Mock private PinsetterKernel pk;
    @Mock private JobExecutionContext ctx;

    @Before
    public void startup() throws SchedulerException {
        super.init();
        MockitoAnnotations.initMocks(this);
        sweepBarJob = new SweepBarJob(j, pk);
        injector.injectMembers(sweepBarJob);
        Set<JobKey> mockJK = new HashSet<>();
        JobKey jk = new JobKey(TEST_KEY);
        mockJK.add(jk);
        when(pk.getSingleJobKeys()).thenReturn(mockJK);
    }

    @Test
    public void testSweepBarJob() throws Exception {
        sweepBarJob.execute(ctx);
        Collection<String> expected = new HashSet<>();
        expected.add(TEST_KEY);
        verify(j, atLeastOnce()).cancelOrphanedJobs(eq(expected));
    }
}
