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

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.quartz.JobKey.jobKey;

import com.google.inject.persist.UnitOfWork;

import org.apache.commons.lang.RandomStringUtils;
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

    @Test
    public void bug863518WasExecuted() {
        JobDetail detail = mock(JobDetail.class);
        when(detail.getKey()).thenReturn(jobKey("foo"));
        when(ctx.getJobDetail()).thenReturn(detail);
        when(jcurator.find(any(String.class))).thenThrow(new RuntimeException());
        try {
            listener.jobWasExecuted(ctx, null);
        }
        catch (RuntimeException re) {
            // do nothing, we're really trying to verify end is called
        }
        verify(unitOfWork, atLeastOnce()).end();
    }

    @Test
    public void bug863518ToBeExecuted() {
        Principal principal = mock(Principal.class);
        JobDataMap map = new JobDataMap();
        JobDetail detail = mock(JobDetail.class);

        map.put(PinsetterJobListener.PRINCIPAL_KEY, principal);

        when(ctx.getMergedJobDataMap()).thenReturn(map);
        when(detail.getKey()).thenReturn(jobKey("foo"));
        when(ctx.getJobDetail()).thenReturn(detail);
        when(jcurator.find(any(String.class))).thenThrow(new RuntimeException());
        try {
            listener.jobToBeExecuted(ctx);
        }
        catch (RuntimeException re) {
            // do nothing, we're really trying to verify end is called
        }
        verify(unitOfWork, atLeastOnce()).end();
    }

    @Test
    public void ensureProperLengthOfResult() {
        JobExecutionException e = mock(JobExecutionException.class);
        JobDetail detail = mock(JobDetail.class);
        JobStatus status = mock(JobStatus.class);

        when(detail.getKey()).thenReturn(jobKey("foo"));
        when(ctx.getJobDetail()).thenReturn(detail);
        when(jcurator.find(eq("foo"))).thenReturn(status);
        String longstr = RandomStringUtils.randomAlphanumeric(
            JobStatus.RESULT_COL_LENGTH);
        when(e.getMessage()).thenReturn(longstr);

        listener.jobWasExecuted(ctx, e);

        verify(status).setState(eq(JobState.FAILED));
        verify(status).setResult(eq(longstr));
        verify(status, never()).update(eq(ctx));
        verify(jcurator).merge(eq(status));
    }

    @Test
    public void handleResultTooLong() {
        JobExecutionException e = mock(JobExecutionException.class);
        JobDetail detail = mock(JobDetail.class);
        JobDataMap map = new JobDataMap();
        map.put(JobStatus.TARGET_TYPE, JobStatus.TargetType.OWNER);

        when(detail.getKey()).thenReturn(jobKey("name", "group"));
        when(detail.getJobDataMap()).thenReturn(map);
        when(ctx.getJobDetail()).thenReturn(detail);

        JobStatus status = new JobStatus(detail);
        when(jcurator.find(eq("name"))).thenReturn(status);

        String longstr = RandomStringUtils.randomAlphanumeric(300);
        when(e.getMessage()).thenReturn(longstr);

        listener.jobWasExecuted(ctx, e);

        assertEquals(longstr.substring(0, JobStatus.RESULT_COL_LENGTH), status.getResult());
        assertEquals(JobState.FAILED, status.getState());
        verify(jcurator).merge(eq(status));
    }
}
