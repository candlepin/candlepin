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
package org.candlepin.pinsetter.core;

import static org.mockito.Mockito.*;

import org.candlepin.controller.ModeManager;
import org.candlepin.model.JobCurator;
import org.candlepin.pinsetter.core.model.JobStatus;

import org.junit.Before;
import org.junit.Test;
import org.quartz.JobKey;
import org.quartz.SchedulerException;
import org.quartz.Trigger;

import java.util.Date;

/**
 * Created by wpoteat on 7/13/17.
 */
public class PinsetterTriggerListenerTest {

    private ModeManager modeManager;
    private JobCurator jobCurator;

    @Before
    public void init() throws SchedulerException {
        modeManager = mock(ModeManager.class);
        jobCurator = mock(JobCurator.class);
    }

    @Test
    public void triggerMisfireRunAgain() {
        PinsetterTriggerListener ptl = new PinsetterTriggerListener(modeManager, jobCurator);
        Trigger trigger = mock(Trigger.class);
        JobStatus jobStatus = new JobStatus();
        JobKey jobKey = new JobKey("mockName");
        when(trigger.mayFireAgain()).thenReturn(true);
        when(trigger.getJobKey()).thenReturn(jobKey);
        when(trigger.getNextFireTime()).thenReturn(new Date());
        when(jobCurator.get(anyString())).thenReturn(jobStatus);

        ptl.triggerMisfired(trigger);
        assert (jobStatus.getResult().startsWith("Will reattempt job at or after"));
        assert (jobStatus.getState().equals(JobStatus.JobState.PENDING));

    }

    @Test
    public void triggerMisfireDontRunAgain() {
        PinsetterTriggerListener ptl = new PinsetterTriggerListener(modeManager, jobCurator);
        Trigger trigger = mock(Trigger.class);
        JobStatus jobStatus = new JobStatus();
        JobKey jobKey = new JobKey("mockName");
        when(trigger.mayFireAgain()).thenReturn(false);
        when(trigger.getJobKey()).thenReturn(jobKey);
        when(jobCurator.get(anyString())).thenReturn(jobStatus);

        ptl.triggerMisfired(trigger);
        assert (jobStatus.getResult().startsWith("Failed run. Will not attempt again."));
        assert (jobStatus.getState().equals(JobStatus.JobState.FAILED));
    }
}
