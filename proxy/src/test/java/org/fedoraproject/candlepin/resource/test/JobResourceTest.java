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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.exceptions.NotFoundException;
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
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * JobResourceTest
 */
public class JobResourceTest {

    private JobResource jobResource;
    @Mock private JobCurator jobCurator;
    @Mock private PinsetterKernel pinsetterKernel;
    private I18n i18n;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        jobResource = new JobResource(jobCurator, pinsetterKernel, i18n);
    }

    @Test
    public void getStatusesNoArgs() {
        try {
            jobResource.getStatuses(null, null, null);
            fail("Should have thrown a BadRequestException");
        }
        catch (BadRequestException e) {
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

    @Test
    public void getStatusesByPrincipal() {
        List<JobStatus> statuses = new ArrayList<JobStatus>();
        JobStatus status = new JobStatus();
        statuses.add(status);
        when(jobCurator.findByPrincipalName(eq("admin"))).thenReturn(statuses);
        Collection<JobStatus> real = jobResource.getStatuses(null, null, "admin");
        assertNotNull(real);
        assertEquals(1, real.size());
    }

    @Test
    public void getStatusesByOwner() {
        List<JobStatus> statuses = new ArrayList<JobStatus>();
        JobStatus status = new JobStatus();
        statuses.add(status);
        when(jobCurator.findByOwnerKey(eq("admin"))).thenReturn(statuses);
        Collection<JobStatus> real = jobResource.getStatuses("admin", null, null);
        assertNotNull(real);
        assertEquals(1, real.size());
    }

    @Test
    public void getStatusesByUuid() {
        List<JobStatus> statuses = new ArrayList<JobStatus>();
        JobStatus status = new JobStatus();
        statuses.add(status);
        when(jobCurator.findByConsumerUuid(eq("abcd"))).thenReturn(statuses);
        Collection<JobStatus> real = jobResource.getStatuses(null, "abcd", null);
        assertNotNull(real);
        assertEquals(1, real.size());
    }

    @Test(expected = NotFoundException.class)
    public void statusForPrincipalNotFound() {
        when(jobCurator.findByPrincipalName(eq("foo"))).thenReturn(null);
        jobResource.getStatuses(null, null, "foo");
    }

    @Test(expected = NotFoundException.class)
    public void statusForOwnerNotFound() {
        when(jobCurator.findByOwnerKey(eq("foo"))).thenReturn(null);
        jobResource.getStatuses("foo", null, null);
    }

    @Test(expected = NotFoundException.class)
    public void statusForConsumerNotFound() {
        when(jobCurator.findByConsumerUuid(eq("foo"))).thenReturn(null);
        jobResource.getStatuses(null, "foo", null);
    }

    @Test(expected = BadRequestException.class)
    public void cannotSpecifyAllParams() {
        jobResource.getStatuses("fi", "fi", "fofum");
    }

    @Test(expected = BadRequestException.class)
    public void cannotSpecifyMoreThanOne() {
        jobResource.getStatuses("fi", "fi", null);
    }

    @Test
    public void emptyStringIsAlsoValid() {
        List<JobStatus> statuses = new ArrayList<JobStatus>();
        JobStatus status = new JobStatus();
        statuses.add(status);
        when(jobCurator.findByPrincipalName(eq("foo"))).thenReturn(statuses);
        jobResource.getStatuses(null, "", "foo");
    }

    /**
     * Returns true if a BadRequestException was thrown, otherwise
     * returns false.
     * @param o param1
     * @param c param2
     * @param p param3
     * @return true if a BadRequestException was thrown, otherwise
     * returns false.
     */
    private boolean expectException(String o, String c, String p) {
        try {
            jobResource.getStatuses(o, c, p);
        }
        catch (BadRequestException bre) {
            return false;
        }
        return true;
    }

    @Test
    public void verifyInput() {
        assertFalse(expectException("owner", "uuid", "pname"));
        assertFalse(expectException("owner", null, "pname"));
        assertFalse(expectException("owner", "uuid", null));
        assertFalse(expectException(null, "uuid", "pname"));
        assertTrue(expectException("owner", null, null));
        assertTrue(expectException("owner", "", null));
        assertTrue(expectException(null, "uuid", null));
        assertTrue(expectException("", "uuid", null));
        assertTrue(expectException(null, null, "pname"));
        assertTrue(expectException(null, "", "pname"));
    }
}
