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
package org.candlepin.resteasy.interceptor;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.auth.permissions.Permission;
import org.candlepin.exceptions.ServiceUnavailableException;
import org.candlepin.model.JobCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerPermission;
import org.candlepin.pinsetter.core.PinsetterException;
import org.candlepin.pinsetter.core.PinsetterJobListener;
import org.candlepin.pinsetter.core.PinsetterKernel;
import org.candlepin.pinsetter.core.model.JobStatus;

import com.google.inject.Provider;

import org.jboss.resteasy.core.ServerResponse;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class PinsetterAsyncInterceptorTest {

    @Mock private ServerResponse response;
    @Mock private Provider<Principal> principalProvider;
    @Mock private PinsetterKernel pinsetterKernel;
    @Mock private JobCurator jobCurator;

    private PinsetterAsyncInterceptor interceptor;

    @Before
    public void init() {
        this.interceptor = new PinsetterAsyncInterceptor(this.pinsetterKernel,
            this.principalProvider, jobCurator);
    }

    @Test
    public void noJobMapPrincipal() {
        List<Permission> permissions = Arrays.asList(new Permission[] {
            new OwnerPermission(new Owner("test_owner"), Access.ALL)
        });
        Principal principal = new UserPrincipal("testing", permissions, false);
        when(this.principalProvider.get()).thenReturn(principal);

        JobDetail detail = new JobDetail();
        when(response.getEntity()).thenReturn(detail);

        this.interceptor.postProcess(response);

        Assert.assertEquals(principal,
                detail.getJobDataMap().get(PinsetterJobListener.PRINCIPAL_KEY));
    }

    @Test
    public void existingJobMapPrincipal() {
        List<Permission> permissions = Arrays.asList(new Permission[] {
            new OwnerPermission(new Owner("test_owner"), Access.ALL)
        });
        Principal principal = new UserPrincipal("testing", permissions, false);

        when(this.principalProvider.get()).thenReturn(principal);

        JobDetail detail = new JobDetail();
        JobDataMap map = new JobDataMap();
        map.put("Temp", "something");
        detail.setJobDataMap(map);
        when(response.getEntity()).thenReturn(detail);

        this.interceptor.postProcess(response);

        Assert.assertSame(principal,
                detail.getJobDataMap().get(PinsetterJobListener.PRINCIPAL_KEY));
    }

    @Test
    public void checkStatusCode() {
        when(response.getEntity()).thenReturn(new JobDetail());
        this.interceptor.postProcess(response);

        // Should we use the resteasy static variable for this?
        verify(response).setStatus(202);
    }

    @Test
    public void nullEntityNoInteraction() throws PinsetterException {
        this.interceptor.postProcess(response);
        verify(this.pinsetterKernel, never()).scheduleSingleJob(any(JobDetail.class));
    }

    @Test
    public void nonJobDetailEntityNoInteraction() throws PinsetterException {
        when(response.getEntity()).thenReturn("This is not a job detail");

        this.interceptor.postProcess(response);
        verify(this.pinsetterKernel, never()).scheduleSingleJob(any(JobDetail.class));
    }

    @Test
    public void jobScheduled() throws PinsetterException {
        JobDetail detail = new JobDetail();
        when(response.getEntity()).thenReturn(detail);

        this.interceptor.postProcess(response);

        verify(this.pinsetterKernel).scheduleSingleJob(detail);
    }

    @Test
    public void jobStatusSet() throws PinsetterException {
        JobDetail detail = new JobDetail();
        JobStatus status = new JobStatus();

        when(response.getEntity()).thenReturn(detail);
        when(this.pinsetterKernel.scheduleSingleJob(detail)).thenReturn(status);

        this.interceptor.postProcess(response);

        verify(response).setEntity(status);
    }

    @Test(expected = ServiceUnavailableException.class)
    public void schedulingError() throws PinsetterException {
        JobDetail detail = new JobDetail();
        when(response.getEntity()).thenReturn(detail);
        when(this.pinsetterKernel.scheduleSingleJob(detail))
                .thenThrow(new PinsetterException("Error scheduling job!"));

        this.interceptor.postProcess(response);
    }

    @Test
    public void ownerJobWithActiveJobs() throws PinsetterException {
        JobDetail detail = new JobDetail();
        detail.setName("refresh_pools_123");
        detail.setGroup(PinsetterKernel.SINGLE_JOB_GROUP);
        detail.getJobDataMap().put(JobStatus.TARGET_TYPE, JobStatus.TargetType.OWNER);
        detail.getJobDataMap().put(JobStatus.TARGET_ID, "admin");
        when(response.getEntity()).thenReturn(detail);
        List<JobStatus> statuses = new ArrayList<JobStatus>();
        JobStatus status = mock(JobStatus.class);
        statuses.add(status);
        when(jobCurator.findActiveByOwnerKeyAndName(eq("admin"),
            eq("refresh_pools_"))).thenReturn(statuses);
        when(pinsetterKernel.scheduleSingleJob(eq(detail),
            any(Date.class))).thenReturn(status);

        this.interceptor.postProcess(response);

        verify(pinsetterKernel).scheduleSingleJob(eq(detail), any(Date.class));
        verify(pinsetterKernel).pauseJob(eq("refresh_pools_123"),
            eq(PinsetterKernel.SINGLE_JOB_GROUP));
        verify(pinsetterKernel, never()).scheduleSingleJob(any(JobDetail.class));
        verify(response).setEntity(status);
    }

    @Test
    public void noExistingJob() throws PinsetterException {
        JobDetail detail = new JobDetail();
        detail.setName("refresh_pools_123");
        detail.setGroup(PinsetterKernel.SINGLE_JOB_GROUP);
        detail.getJobDataMap().put(JobStatus.TARGET_TYPE, JobStatus.TargetType.OWNER);
        detail.getJobDataMap().put(JobStatus.TARGET_ID, "admin");
        when(response.getEntity()).thenReturn(detail);
        when(jobCurator.findActiveByOwnerKeyAndName(eq("admin"),
            eq("refresh_pools_"))).thenReturn(new ArrayList<JobStatus>());

        this.interceptor.postProcess(response);

        verify(pinsetterKernel, never()).scheduleSingleJob(eq(detail), any(Date.class));
        verify(pinsetterKernel, never()).pauseJob(eq("refresh_pools_123"),
            eq(PinsetterKernel.SINGLE_JOB_GROUP));
        verify(pinsetterKernel).scheduleSingleJob(eq(detail));
    }
}
