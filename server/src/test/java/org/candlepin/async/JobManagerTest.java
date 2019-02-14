/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.async;

import com.google.inject.Injector;
import com.google.inject.persist.UnitOfWork;
import org.candlepin.async.temp.TestJob1;
import org.candlepin.audit.EventSink;
import org.candlepin.guice.CandlepinRequestScope;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.AsyncJobStatusCurator;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.hamcrest.core.StringContains;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.InOrder;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class JobManagerTest {

    private static final String JOB_ID = "jobId";
    private static final String JOB_KEY = TestJob1.getJobKey();
    private static final String REQUEST_TYPE_KEY = "requestType";
    private static final String REQUEST_UUID_KEY = "requestUuid";
    private static final String ORG_KEY = "org";
    private static final String ORG_LOG_LEVEL_KEY = "orgLogLevel";
    private static final String OWNER_ID = "ownerId";
    private static final String OWNER_LOG_LEVEL = "ownerLogLevel";
    private static final String REQUEST_UUID = "requestUuid";
    private static final String REQUEST_TYPE = "job";

    private AsyncJobStatusCurator jobCurator;
    private PrincipalProvider principalProvider;
    private CandlepinRequestScope scope;
    private JobMessageDispatcher dispatcher;
    private Injector injector;
    private EventSink eventSink;
    private UnitOfWork uow;


    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setUp() {
        this.jobCurator = mock(AsyncJobStatusCurator.class);
        this.principalProvider = mock(PrincipalProvider.class);
        this.scope = mock(CandlepinRequestScope.class);
        this.dispatcher = mock(JobMessageDispatcher.class);
        this.injector = mock(Injector.class);
        this.eventSink = mock(EventSink.class);
        this.uow = mock(UnitOfWork.class);

        doReturn(this.eventSink).when(this.injector).getInstance(EventSink.class);
        doReturn(this.jobCurator).when(this.injector).getInstance(AsyncJobStatusCurator.class);
        doReturn(this.uow).when(this.injector).getInstance(UnitOfWork.class);
    }

    @Test
    public void jobShouldFailWhenJobStatusIsNotFound()
        throws PreJobExecutionException, JobExecutionException {
        doReturn(null).when(jobCurator).get(anyString());
        final JobManager manager = createJobManager();

        thrown.expect(PreJobExecutionException.class);
        thrown.expectMessage(StringContains.containsString("not found"));

        manager.executeJob(new JobMessage(JOB_ID, JOB_KEY));
    }

    @Test
    public void jobShouldFailWhenJobWasCancelled() throws PreJobExecutionException, JobExecutionException {
        doReturn(new AsyncJobStatus().setState(AsyncJobStatus.JobState.CANCELED))
            .when(jobCurator).get(anyString());
        final JobManager manager = createJobManager();

        thrown.expect(PreJobExecutionException.class);
        thrown.expectMessage(StringContains.containsString("CANCELLED"));

        manager.executeJob(new JobMessage(JOB_ID, JOB_KEY));
    }


    @Test
    public void shouldFailWhenJobCouldNotBeConstructed()
        throws PreJobExecutionException, JobExecutionException {
        doReturn(new AsyncJobStatus().setState(AsyncJobStatus.JobState.QUEUED))
            .when(jobCurator).get(anyString());
        final JobManager manager = createJobManager();

        thrown.expect(PreJobExecutionException.class);
        thrown.expectMessage(StringContains.containsString("could not be created"));

        manager.executeJob(new JobMessage(JOB_ID, JOB_KEY));
    }

    @Test
    public void jobShouldBeExecuted() throws PreJobExecutionException, JobExecutionException {
        final AsyncJob spy = mock(AsyncJob.class);
        doReturn(spy).when(injector).getInstance(TestJob1.class);
        doReturn(new AsyncJobStatus().setState(AsyncJobStatus.JobState.QUEUED))
            .when(jobCurator).get(anyString());
        final JobManager manager = createJobManager();

        manager.executeJob(new JobMessage(JOB_ID, JOB_KEY));

        verify(spy).execute(any());
    }

    @Test
    public void shouldSetupDMC() throws JobExecutionException, PreJobExecutionException {
        final AsyncJobStatus status = mock(AsyncJobStatus.class);
        final Map<String, Object> map = new HashMap<>();
        map.put(REQUEST_UUID_KEY, REQUEST_UUID);
        map.put(JobStatus.OWNER_ID, OWNER_ID);
        map.put(JobStatus.OWNER_LOG_LEVEL, OWNER_LOG_LEVEL);
        doReturn(new JobDataMap(map)).when(status).getJobData();
        doReturn(mock(AsyncJob.class)).when(injector).getInstance(TestJob1.class);
        doReturn(status).when(jobCurator).get(JOB_ID);
        final JobManager manager = createJobManager();

        manager.executeJob(new JobMessage(JOB_ID, JOB_KEY));

        assertEquals(REQUEST_TYPE, MDC.get(REQUEST_TYPE_KEY));
        assertEquals(REQUEST_UUID, MDC.get(REQUEST_UUID_KEY));
        assertEquals(OWNER_ID, MDC.get(ORG_KEY));
        assertEquals(OWNER_LOG_LEVEL, MDC.get(ORG_LOG_LEVEL_KEY));
    }

    @Test
    public void shouldSendEvents() throws JobExecutionException, PreJobExecutionException {
        doReturn(mock(AsyncJob.class)).when(injector).getInstance(TestJob1.class);
        doReturn(new AsyncJobStatus().setState(AsyncJobStatus.JobState.QUEUED))
            .when(jobCurator).get(anyString());
        final JobManager manager = createJobManager();

        manager.executeJob(new JobMessage(JOB_ID, JOB_KEY));

        verify(this.eventSink).sendEvents();
    }

    @Test
    public void shouldRollbackEventsOfFailedJob() {
        final AsyncJob job = jdata -> {
            throw new JobExecutionException();
        };
        doReturn(job).when(injector).getInstance(TestJob1.class);
        doReturn(new AsyncJobStatus().setState(AsyncJobStatus.JobState.QUEUED))
            .when(jobCurator).get(anyString());
        final JobManager manager = createJobManager();

        try {
            manager.executeJob(new JobMessage(JOB_ID, JOB_KEY));
            fail("Should not happen");
        }
        catch (PreJobExecutionException | JobExecutionException e) {
            verify(this.eventSink).rollback();
        }
    }

    @Test
    public void successfulJobShouldEndAsCompleted() throws JobExecutionException, PreJobExecutionException {
        final StateCollectingStatus status = new StateCollectingStatus();
        doReturn(mock(AsyncJob.class)).when(injector).getInstance(TestJob1.class);
        doReturn(new AsyncJobStatus().setState(AsyncJobStatus.JobState.QUEUED))
            .when(jobCurator).get(anyString());
        doReturn(status).when(this.jobCurator).get(JOB_ID);
        final JobManager manager = createJobManager();

        manager.executeJob(new JobMessage(JOB_ID, JOB_KEY));

        assertTrue(status.containsAll(
            AsyncJobStatus.JobState.RUNNING,
            AsyncJobStatus.JobState.COMPLETED));
    }

    @Test
    public void shouldSetJobExecSource() throws PreJobExecutionException, JobExecutionException {
        final AsyncJobStatus status = new AsyncJobStatus();
        doReturn(mock(AsyncJob.class)).when(injector).getInstance(TestJob1.class);
        doReturn(new AsyncJobStatus().setState(AsyncJobStatus.JobState.QUEUED))
            .when(jobCurator).get(anyString());
        doReturn(status).when(this.jobCurator).get(JOB_ID);
        final JobManager manager = createJobManager();

        manager.executeJob(new JobMessage(JOB_ID, JOB_KEY));

        final String execSource = status.getJobExecSource();
        assertFalse(execSource == null || execSource.isEmpty());
    }

    @Test
    public void failedJobShouldEndAsFailed() throws PreJobExecutionException {
        final StateCollectingStatus status = new StateCollectingStatus();
        final AsyncJob mock = jdata -> {
            throw new JobExecutionException();
        };
        doReturn(mock).when(injector).getInstance(TestJob1.class);
        doReturn(status).when(this.jobCurator).get(JOB_ID);
        final JobManager manager = createJobManager();

        try {
            manager.executeJob(new JobMessage(JOB_ID, JOB_KEY));
            fail("Should not happen");
        }
        catch (JobExecutionException e) {
            assertTrue(status.containsAll(
                AsyncJobStatus.JobState.RUNNING,
                AsyncJobStatus.JobState.FAILED));
        }
    }

    @Test
    public void shouldSurroundJobExecutionWithUnitOfWork()
        throws JobExecutionException, PreJobExecutionException {
        final AsyncJob job = mock(AsyncJob.class);
        doReturn(job).when(injector).getInstance(TestJob1.class);
        doReturn(new AsyncJobStatus().setState(AsyncJobStatus.JobState.QUEUED))
            .when(jobCurator).get(anyString());
        final InOrder inOrder = inOrder(uow, job, uow);
        final JobManager manager = createJobManager();

        manager.executeJob(new JobMessage(JOB_ID, JOB_KEY));

        inOrder.verify(uow).begin();
        inOrder.verify(job).execute(any());
        inOrder.verify(uow).end();
    }

    @Test
    public void shouldProperlyEndUnitOfWorkOfFailedJobs()
        throws JobExecutionException, PreJobExecutionException {
        final AsyncJob job = mock(AsyncJob.class);
        doReturn(job).when(injector).getInstance(TestJob1.class);
        doReturn(new AsyncJobStatus().setState(AsyncJobStatus.JobState.QUEUED))
            .when(jobCurator).get(anyString());
        doThrow(new JobExecutionException()).when(job).execute(any());
        final InOrder inOrder = inOrder(uow, job, uow);
        final JobManager manager = createJobManager();

        try {
            manager.executeJob(new JobMessage(JOB_ID, JOB_KEY));
            fail("Must not happen!");
        }
        catch (JobExecutionException e) {
            inOrder.verify(uow).begin();
            inOrder.verify(job).execute(any());
            inOrder.verify(uow).end();
        }
    }

    private JobManager createJobManager() {
        return new JobManager(
            jobCurator, dispatcher, principalProvider, scope, injector);
    }

    private static class StateCollectingStatus extends AsyncJobStatus {

        private final List<JobState> states = new ArrayList<>();

        @Override
        public AsyncJobStatus setState(final JobState state) {
            super.setState(state);
            this.states.add(state);
            return this;
        }

        public boolean containsAll(JobState... states) {
            return new HashSet<>(this.states).containsAll(Arrays.asList(states));
        }
    }

}
