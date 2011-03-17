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
package org.fedoraproject.candlepin.pinsetter.core;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.model.JobCurator;
import org.fedoraproject.candlepin.pinsetter.core.model.JobStatus;
import org.junit.Before;
import org.junit.Test;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;


/**
 * PinsetterJobListenerTest
 */
public class PinsetterJobListenerTest {
    private PinsetterJobListener listener;
    private JobCurator jcurator;       
    private JobExecutionContext ctx;
    
    @Before
    public void init() {
        jcurator = mock(JobCurator.class);
        listener = new PinsetterJobListener(jcurator);
        ctx = mock(JobExecutionContext.class);
    }

    @Test
    public void name() {
        assertEquals(PinsetterJobListener.LISTENER_NAME, listener.getName());
    }
    
    @Test
    public void tobeExecuted() {
        Principal principal = mock(Principal.class);
        JobDataMap map = new JobDataMap();
        JobDetail detail = mock(JobDetail.class);
        JobStatus status = mock(JobStatus.class);

        map.put(PinsetterJobListener.PRINCIPAL_KEY, principal);
        
        when(ctx.getMergedJobDataMap()).thenReturn(map);
        when(detail.getName()).thenReturn("foo");
        when(ctx.getJobDetail()).thenReturn(detail);
        when(jcurator.find(eq("foo"))).thenReturn(status);
        
        listener.jobToBeExecuted(ctx);
        
        verify(status).update(eq(ctx));
        verify(jcurator).merge(eq(status));
    }
    
    @Test
    public void vetoed() {
        listener.jobExecutionVetoed(ctx);
        verifyZeroInteractions(ctx);
        verifyZeroInteractions(jcurator);
    }
    
    @Test
    public void executed() {
        JobExecutionException e = mock(JobExecutionException.class);
        JobDetail detail = mock(JobDetail.class);
        JobStatus status = mock(JobStatus.class);

        when(detail.getName()).thenReturn("foo");
        when(ctx.getJobDetail()).thenReturn(detail);
        when(jcurator.find(eq("foo"))).thenReturn(status);
        
        listener.jobWasExecuted(ctx, e);
        
        verify(status).update(eq(ctx));
        verify(jcurator).merge(eq(status));
        verifyZeroInteractions(e);
    }
    
    @Test
    public void executedNullStatus() {
        JobExecutionException e = mock(JobExecutionException.class);
        JobDetail detail = mock(JobDetail.class);
        JobStatus status = mock(JobStatus.class);

        when(detail.getName()).thenReturn("foo");
        when(ctx.getJobDetail()).thenReturn(detail);
        when(jcurator.find(eq("foo"))).thenReturn(null);
        
        listener.jobWasExecuted(ctx, e);

        verifyZeroInteractions(status);
        verify(jcurator, never()).merge(eq(status));
        verifyZeroInteractions(e);
    }
    
    @Test
    public void tobeExecutedNull() {
        Principal principal = mock(Principal.class);
        JobDataMap map = new JobDataMap();
        JobDetail detail = mock(JobDetail.class);
        JobStatus status = mock(JobStatus.class);

        map.put(PinsetterJobListener.PRINCIPAL_KEY, principal);
        
        when(ctx.getMergedJobDataMap()).thenReturn(map);
        when(detail.getName()).thenReturn("foo");
        when(ctx.getJobDetail()).thenReturn(detail);
        when(jcurator.find(eq("foo"))).thenReturn(null);
        
        listener.jobToBeExecuted(ctx);
        
        verifyZeroInteractions(status);
        verify(jcurator, never()).merge(eq(status));
    }
}
