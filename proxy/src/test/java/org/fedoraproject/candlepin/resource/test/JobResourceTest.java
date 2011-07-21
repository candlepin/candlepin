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
package org.fedoraproject.candlepin.resource.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;


import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.model.JobCurator;
import org.fedoraproject.candlepin.pinsetter.core.PinsetterException;
import org.fedoraproject.candlepin.pinsetter.core.PinsetterKernel;
import org.fedoraproject.candlepin.pinsetter.core.model.JobStatus;
import org.fedoraproject.candlepin.pinsetter.core.model.JobStatus.JobState;
import org.fedoraproject.candlepin.resource.JobResource;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * JobResourceTest
 */
public class JobResourceTest {
    
    private JobResource jobResource;
    @Mock private JobCurator jobCurator;
    @Mock private PinsetterKernel pinsetterKernel;
    
    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        jobResource = new JobResource(jobCurator, pinsetterKernel);
    }
    
    @Test
    public void getStatusesNoArgs() {
        try {
            jobResource.getStatuses(null);
            fail("Should have thrown a BadRequestException");
        } catch (BadRequestException e) {
            //expected, return
            return;
        }
    }
    
    @Test
    public void schedulerPausedTest() throws PinsetterException {
        when(pinsetterKernel.getSchedulerStatus()).thenReturn(true);
        assertTrue(jobResource.getSchedulerStatus().isRunning());
        
        when(pinsetterKernel.getSchedulerStatus()).thenReturn(false);
        assertFalse(jobResource.getSchedulerStatus().isRunning());   
    }
    
    @Test
    public void getStatusAndDeleteIfFinishedTest() {
        //nothing to delete..
        when(jobCurator.find("bogus_id")).thenReturn(new JobStatus());
        jobResource.getStatusAndDeleteIfFinished("foobar");
        verify(jobCurator, never()).delete(any(JobStatus.class));
        
        //now lets make a deletable JobStatus
        JobStatus finishedJobStatus = new JobStatus();
        finishedJobStatus.setState(JobState.FINISHED);
        when(jobCurator.find("deletable_id")).thenReturn(finishedJobStatus);
        jobResource.getStatusAndDeleteIfFinished("deletable_id");
        verify(jobCurator, atLeastOnce()).delete(finishedJobStatus);               
    }
    
    @Test
    public void cancelJob() {
        //we are just testing that the cancellation gets into the db
        JobStatus createdJobStatus = new JobStatus();
        createdJobStatus.setState(JobState.CREATED);
        JobStatus cancelledJobStatus = new JobStatus();
        cancelledJobStatus.setState(JobState.CANCELLED);
        
        when(jobCurator.find("cancel_id")).thenReturn(createdJobStatus);
        when(jobCurator.cancel("cancel_id")).thenReturn(cancelledJobStatus);
        jobResource.cancel("cancel_id");
        verify(jobCurator, atLeastOnce()).cancel("cancel_id");       
    }
}
