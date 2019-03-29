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
import org.candlepin.model.AsyncJobStatus.JobState;
import org.candlepin.model.AsyncJobStatusCurator;
import org.candlepin.pinsetter.core.model.JobStatus;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.hamcrest.core.StringContains;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import org.mockito.InOrder;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;



@RunWith(JUnitParamsRunner.class)
public class JobManagerTest {

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

    /**
     * A JobMessageDispatcher implementation that collects the messages sent for verification
     */
    private static class CollectingJobMessageDispatcher implements JobMessageDispatcher {
        private List<JobMessage> messages = new ArrayList<>();

        @Override
        public void sendJobMessage(JobMessage jobMessage) throws Exception {
            this.messages.add(jobMessage);
        }

        public List<JobMessage> getSentMessages() {
            return this.messages;
        }
    }


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
    private CollectingJobMessageDispatcher dispatcher;
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
        this.dispatcher = new CollectingJobMessageDispatcher();
        this.injector = mock(Injector.class);
        this.eventSink = mock(EventSink.class);
        this.uow = mock(UnitOfWork.class);

        doReturn(this.eventSink).when(this.injector).getInstance(EventSink.class);
        doReturn(this.jobCurator).when(this.injector).getInstance(AsyncJobStatusCurator.class);
        doReturn(this.uow).when(this.injector).getInstance(UnitOfWork.class);
        doAnswer(returnsFirstArg()).when(this.jobCurator).merge(any(AsyncJobStatus.class));
    }

    private JobManager createJobManager() {
        return new JobManager(
            jobCurator, dispatcher, principalProvider, scope, injector);
    }

    @Test
    public void jobShouldFailWhenJobStatusIsNotFound() throws JobException {
        doReturn(null).when(jobCurator).get(anyString());
        final JobManager manager = createJobManager();

        thrown.expect(JobInitializationException.class);
        thrown.expectMessage(StringContains.containsString("Unable to find"));

        manager.executeJob(new JobMessage(JOB_ID, JOB_KEY));
    }

    public Object[] getTerminalJobStates() {
        List<JobState> states = new ArrayList<>();

        for (JobState state : JobState.values()) {
            if (state.isTerminal()) {
                states.add(state);
            }
        }

        return states.toArray();
    }

    @Test
    @Parameters(method = "getTerminalJobStates")
    public void jobShouldFailWhenJobStateIsTerminal(JobState state) throws JobException {
        doReturn(new AsyncJobStatus().setState(state)).when(jobCurator).get(anyString());
        final JobManager manager = createJobManager();

        thrown.expect(JobInitializationException.class);
        thrown.expectMessage(StringContains.containsString("unknown or terminal state"));

        manager.executeJob(new JobMessage(JOB_ID, JOB_KEY));
    }

    @Test
    public void shouldFailWhenJobCouldNotBeConstructed() throws JobException {
        doReturn(new AsyncJobStatus().setState(JobState.QUEUED))
            .when(jobCurator).get(anyString());
        final JobManager manager = createJobManager();

        thrown.expect(JobInitializationException.class);
        thrown.expectMessage(StringContains.containsString("Unable to instantiate"));

        manager.executeJob(new JobMessage(JOB_ID, JOB_KEY));
    }

    @Test
    public void jobShouldBeExecuted() throws JobException {
        final AsyncJob spy = mock(AsyncJob.class);
        doReturn(spy).when(injector).getInstance(TestJob1.class);
        doReturn(new AsyncJobStatus().setState(JobState.QUEUED))
            .when(jobCurator).get(anyString());
        final JobManager manager = createJobManager();

        manager.executeJob(new JobMessage(JOB_ID, JOB_KEY));

        verify(spy).execute(any());
    }

    @Test
    public void shouldSetupDMC() throws JobException {
        final Map<String, Object> map = new HashMap<>();
        map.put(REQUEST_UUID_KEY, REQUEST_UUID);
        map.put(JobStatus.OWNER_ID, OWNER_ID);
        map.put(JobStatus.OWNER_LOG_LEVEL, OWNER_LOG_LEVEL);

        final AsyncJobStatus status = new AsyncJobStatus()
            .setJobData(map);

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
    public void shouldSendEvents() throws JobException {
        doReturn(mock(AsyncJob.class)).when(injector).getInstance(TestJob1.class);
        doReturn(new AsyncJobStatus().setState(JobState.QUEUED))
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
        doReturn(new AsyncJobStatus().setState(JobState.QUEUED))
            .when(jobCurator).get(anyString());
        final JobManager manager = createJobManager();

        try {
            manager.executeJob(new JobMessage(JOB_ID, JOB_KEY));
            fail("Should not happen");
        }
        catch (JobException e) {
            verify(this.eventSink).rollback();
        }
    }

    @Test
    public void successfulJobShouldEndAsCompleted() throws JobException {
        final StateCollectingStatus status = new StateCollectingStatus();
        doReturn(mock(AsyncJob.class)).when(injector).getInstance(TestJob1.class);
        doReturn(new AsyncJobStatus().setState(JobState.QUEUED))
            .when(jobCurator).get(anyString());
        doReturn(status).when(this.jobCurator).get(JOB_ID);


        final JobManager manager = createJobManager();

        manager.executeJob(new JobMessage(JOB_ID, JOB_KEY));

        assertTrue(status.containsAll(
            JobState.RUNNING,
            JobState.COMPLETED));
    }

    @Test
    public void shouldSetJobExecSource() throws JobException {
        final AsyncJobStatus status = new AsyncJobStatus();
        doReturn(mock(AsyncJob.class)).when(injector).getInstance(TestJob1.class);
        doReturn(new AsyncJobStatus().setState(JobState.QUEUED))
            .when(jobCurator).get(anyString());
        doReturn(status).when(this.jobCurator).get(JOB_ID);
        final JobManager manager = createJobManager();

        manager.executeJob(new JobMessage(JOB_ID, JOB_KEY));

        final String execSource = status.getJobExecSource();
        assertFalse(execSource == null || execSource.isEmpty());
    }

    @Test
    public void failedJobShouldEndAsFailed() throws JobInitializationException {
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
        catch (JobException e) {
            assertTrue(status.containsAll(
                JobState.RUNNING,
                JobState.FAILED));
        }
    }

    @Test
    public void shouldSurroundJobExecutionWithUnitOfWork() throws JobException {
        final AsyncJob job = mock(AsyncJob.class);
        doReturn(job).when(injector).getInstance(TestJob1.class);
        doReturn(new AsyncJobStatus().setState(JobState.QUEUED))
            .when(jobCurator).get(anyString());
        final InOrder inOrder = inOrder(uow, job, uow);
        final JobManager manager = createJobManager();

        manager.executeJob(new JobMessage(JOB_ID, JOB_KEY));

        inOrder.verify(uow).begin();
        inOrder.verify(job).execute(any());
        inOrder.verify(uow).end();
    }

    @Test
    public void shouldProperlyEndUnitOfWorkOfFailedJobs() throws JobException {
        final AsyncJob job = mock(AsyncJob.class);
        doReturn(job).when(injector).getInstance(TestJob1.class);
        doReturn(new AsyncJobStatus().setState(JobState.QUEUED))
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

    @Test
    public void testStartTimeIsSetOnExecution() throws JobException {
        AsyncJob job = jdata -> { return null; };
        AsyncJobStatus status = new AsyncJobStatus()
            .setState(JobState.QUEUED);

        doReturn(job).when(this.injector).getInstance(TestJob1.class);
        doReturn(status).when(this.jobCurator).get(anyString());

        assertNull(status.getStartTime());
        Date start = new Date();

        JobManager manager = this.createJobManager();
        manager.executeJob(new JobMessage(JOB_ID, JOB_KEY));

        Date end = new Date();

        assertNotNull(status.getStartTime());
        assertTrue(start.compareTo(status.getStartTime()) <= 0);
        assertTrue(end.compareTo(status.getStartTime()) >= 0);
    }

    @Test
    public void testEndTimeIsSetOnExecution() throws JobException {
        AsyncJob job = jdata -> { return null; };
        AsyncJobStatus status = new AsyncJobStatus()
            .setState(JobState.QUEUED);

        doReturn(job).when(this.injector).getInstance(TestJob1.class);
        doReturn(status).when(this.jobCurator).get(anyString());

        assertNull(status.getEndTime());
        Date start = new Date();

        JobManager manager = this.createJobManager();
        manager.executeJob(new JobMessage(JOB_ID, JOB_KEY));

        Date end = new Date();

        assertNotNull(status.getEndTime());
        assertTrue(start.compareTo(status.getEndTime()) <= 0);
        assertTrue(end.compareTo(status.getEndTime()) >= 0);
    }

    @Test
    public void testEndTimeIsSetOnExecutionFailure() {
        AsyncJob job = jdata -> { throw new JobExecutionException("kaboom"); };
        AsyncJobStatus status = new AsyncJobStatus()
            .setState(JobState.QUEUED);

        doReturn(job).when(this.injector).getInstance(TestJob1.class);
        doReturn(status).when(this.jobCurator).get(anyString());

        assertNull(status.getEndTime());
        Date start = new Date();

        try {
            JobManager manager = this.createJobManager();
            manager.executeJob(new JobMessage(JOB_ID, JOB_KEY));

            fail("Expected exception was not thrown by JobManager.executeJob");
        }
        catch (JobException e) {
            // This is expected
            assertEquals("kaboom", e.getMessage());
        }

        Date end = new Date();

        assertNotNull(status.getEndTime());
        assertTrue(start.compareTo(status.getEndTime()) <= 0);
        assertTrue(end.compareTo(status.getEndTime()) >= 0);
    }

    @Test
    public void testAttemptCountIsIncrementedOnExecution() throws JobException {
        AsyncJob job = jdata -> { return null; };
        AsyncJobStatus status = new AsyncJobStatus()
            .setState(JobState.QUEUED);

        doReturn(job).when(this.injector).getInstance(TestJob1.class);
        doReturn(status).when(this.jobCurator).get(anyString());

        assertEquals(0, status.getAttempts());

        JobManager manager = this.createJobManager();
        manager.executeJob(new JobMessage(JOB_ID, JOB_KEY));

        assertEquals(1, status.getAttempts());
    }

    @Test
    public void testAttemptCountRemainsIncrementedOnExecutionFailure() throws JobException {
        AsyncJob job = jdata -> { throw new JobExecutionException("kaboom"); };
        AsyncJobStatus status = new AsyncJobStatus()
            .setState(JobState.QUEUED);

        doReturn(job).when(this.injector).getInstance(TestJob1.class);
        doReturn(status).when(this.jobCurator).get(anyString());

        assertEquals(0, status.getAttempts());

        try {
            JobManager manager = this.createJobManager();
            manager.executeJob(new JobMessage(JOB_ID, JOB_KEY));

            fail("Expected exception was not thrown by JobManager.executeJob");
        }
        catch (JobException e) {
            // This is expected
            assertEquals("kaboom", e.getMessage());
        }

        assertEquals(1, status.getAttempts());
    }

    @Test
    public void testFailingWithRetryGeneratesNewJobMessage() {
        AsyncJob job = jdata -> { throw new JobExecutionException("kaboom"); };
        AsyncJobStatus status = spy(new AsyncJobStatus()
            .setJobKey(JOB_KEY)
            .setState(JobState.QUEUED)
            .setMaxAttempts(3));

        // TODO: Stop doing this when we stop relying on Hibernate to generate the ID for us
        doReturn(JOB_ID).when(status).getId();

        doReturn(job).when(this.injector).getInstance(TestJob1.class);
        doReturn(status).when(this.jobCurator).get(anyString());

        assertEquals(0, this.dispatcher.getSentMessages().size());

        try {
            JobManager manager = this.createJobManager();
            manager.executeJob(new JobMessage(JOB_ID, JOB_KEY));

            fail("Expected exception was not thrown by JobManager.executeJob");
        }
        catch (JobException e) {
            // This is expected
            assertEquals("kaboom", e.getMessage());
        }

        assertEquals(1, this.dispatcher.getSentMessages().size());

        JobMessage message = this.dispatcher.getSentMessages().get(0);
        assertEquals(JOB_ID, message.getJobId());
        assertEquals(JOB_KEY, message.getJobKey());

        assertEquals(JobState.FAILED_WITH_RETRY, status.getState());
    }

    @Test
    public void testFailingWithRetryDoesNotRetryWhenAttemptsExhaused() {
        AsyncJob job = jdata -> { throw new JobExecutionException("kaboom"); };
        AsyncJobStatus status = spy(new AsyncJobStatus()
            .setJobKey(JOB_KEY)
            .setState(JobState.QUEUED)
            .incrementAttempts()
            .setMaxAttempts(2));

        // TODO: Stop doing this when we stop relying on Hibernate to generate the ID for us
        doReturn(JOB_ID).when(status).getId();

        doReturn(job).when(this.injector).getInstance(TestJob1.class);
        doReturn(status).when(this.jobCurator).get(anyString());

        assertEquals(0, this.dispatcher.getSentMessages().size());

        try {
            JobManager manager = this.createJobManager();
            manager.executeJob(new JobMessage(JOB_ID, JOB_KEY));

            fail("Expected exception was not thrown by JobManager.executeJob");
        }
        catch (JobException e) {
            // This is expected
            assertEquals("kaboom", e.getMessage());
        }

        assertEquals(0, this.dispatcher.getSentMessages().size());
        assertEquals(JobState.FAILED, status.getState());
    }

    @Test
    public void testFailingWithRetryDoesNotRetryOnTerminalFailure() {
        AsyncJob job = jdata -> { throw new JobExecutionException("kaboom", true); };
        AsyncJobStatus status = spy(new AsyncJobStatus()
            .setJobKey(JOB_KEY)
            .setState(JobState.QUEUED)
            .setMaxAttempts(3));

        // TODO: Stop doing this when we stop relying on Hibernate to generate the ID for us
        doReturn(JOB_ID).when(status).getId();

        doReturn(job).when(this.injector).getInstance(TestJob1.class);
        doReturn(status).when(this.jobCurator).get(anyString());

        assertEquals(0, this.dispatcher.getSentMessages().size());

        try {
            JobManager manager = this.createJobManager();
            manager.executeJob(new JobMessage(JOB_ID, JOB_KEY));

            fail("Expected exception was not thrown by JobManager.executeJob");
        }
        catch (JobException e) {
            // This is expected
            assertEquals("kaboom", e.getMessage());
        }

        assertEquals(0, this.dispatcher.getSentMessages().size());
        assertEquals(JobState.FAILED, status.getState());
    }
}
