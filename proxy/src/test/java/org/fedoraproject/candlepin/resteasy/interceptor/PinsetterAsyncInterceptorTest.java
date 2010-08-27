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
package org.fedoraproject.candlepin.resteasy.interceptor;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;

import com.google.inject.Provider;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.auth.UserPrincipal;
import org.fedoraproject.candlepin.exceptions.ServiceUnavailableException;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.pinsetter.core.PinsetterException;
import org.fedoraproject.candlepin.pinsetter.core.PinsetterJobListener;
import org.fedoraproject.candlepin.pinsetter.core.PinsetterKernel;
import org.fedoraproject.candlepin.pinsetter.core.model.JobStatus;
import org.jboss.resteasy.core.ServerResponse;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class PinsetterAsyncInterceptorTest {

    @Mock private ServerResponse response;
    @Mock private Provider<Principal> principalProvider;
    @Mock private PinsetterKernel pinsetterKernel;

    private PinsetterAsyncInterceptor interceptor;

    @Before
    public void init() {
        this.interceptor = new PinsetterAsyncInterceptor(this.pinsetterKernel,
            this.principalProvider);
    }

    @Test
    public void noJobMapPrincipal() {
        Principal principal = new UserPrincipal("testing", new Owner("test_owner"), null);
        when(this.principalProvider.get()).thenReturn(principal);

        JobDetail detail = new JobDetail();
        when(response.getEntity()).thenReturn(detail);
        
        this.interceptor.postProcess(response);

        Assert.assertEquals(principal, 
                detail.getJobDataMap().get(PinsetterJobListener.PRINCIPAL_KEY));
    }

    @Test
    public void existingJobMapPrincipal() {
        Principal principal = new UserPrincipal("testing", new Owner("test_owner"), null);
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

}
