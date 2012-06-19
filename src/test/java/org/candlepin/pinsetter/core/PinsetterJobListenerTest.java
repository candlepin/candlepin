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
package org.candlepin.pinsetter.core;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.quartz.JobKey.jobKey;

import org.candlepin.auth.Principal;
import org.candlepin.model.JobCurator;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.pinsetter.core.model.JobStatus.JobState;

import org.junit.Before;
import org.junit.Test;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.google.inject.persist.UnitOfWork;


/**
 * PinsetterJobListenerTest
 */
public class PinsetterJobListenerTest {
    private PinsetterJobListener listener;
    private JobCurator jcurator;
    private UnitOfWork unitOfWork;
    private JobExecutionContext ctx;

    @Before
    public void init() {
        jcurator = mock(JobCurator.class);
        unitOfWork = mock(UnitOfWork.class);
        listener = new PinsetterJobListener(jcurator, unitOfWork);
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
        when(detail.getKey()).thenReturn(jobKey("foo"));
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
        JobDetail detail = mock(JobDetail.class);
        JobStatus status = mock(JobStatus.class);

        when(detail.getKey()).thenReturn(jobKey("foo"));
        when(ctx.getJobDetail()).thenReturn(detail);
        when(jcurator.find(eq("foo"))).thenReturn(status);

        listener.jobWasExecuted(ctx, null);

        verify(status).update(eq(ctx));
        verify(jcurator).merge(eq(status));
    }

    @Test
    public void executedNullStatus() {
        JobExecutionException e = mock(JobExecutionException.class);
        JobDetail detail = mock(JobDetail.class);
        JobStatus status = mock(JobStatus.class);

        when(detail.getKey()).thenReturn(jobKey("foo"));
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
        when(detail.getKey()).thenReturn(jobKey("foo"));
        when(ctx.getJobDetail()).thenReturn(detail);
        when(jcurator.find(eq("foo"))).thenReturn(null);

        listener.jobToBeExecuted(ctx);

        verifyZeroInteractions(status);
        verify(jcurator, never()).merge(eq(status));
    }

    @Test
    public void handleNullException() {
        JobStatus status = mock(JobStatus.class);
        JobDetail detail = mock(JobDetail.class);

        when(detail.getKey()).thenReturn(jobKey("foo"));
        when(ctx.getJobDetail()).thenReturn(detail);
        when(jcurator.find(eq("foo"))).thenReturn(status);

        listener.jobWasExecuted(ctx, null);

        verify(status).update(eq(ctx));
        verify(jcurator).merge(eq(status));
    }

    @Test
    public void handleException() {
        JobExecutionException e = mock(JobExecutionException.class);
        JobDetail detail = mock(JobDetail.class);
        JobStatus status = mock(JobStatus.class);

        when(detail.getKey()).thenReturn(jobKey("foo"));
        when(ctx.getJobDetail()).thenReturn(detail);
        when(jcurator.find(eq("foo"))).thenReturn(status);
        when(e.getMessage()).thenReturn("job errored");

        listener.jobWasExecuted(ctx, e);

        verify(status).setState(eq(JobState.FAILED));
        verify(status).setResult(eq("job errored"));
        verify(status, never()).update(eq(ctx));
        verify(jcurator).merge(eq(status));
    }
}
