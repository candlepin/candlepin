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
import org.candlepin.common.config.Configuration;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.ModeManager;
import org.candlepin.guice.CandlepinRequestScope;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.AsyncJobStatus.JobState;
import org.candlepin.model.AsyncJobStatusCurator;
import org.candlepin.model.CandlepinModeChange;
import org.candlepin.model.CandlepinModeChange.Mode;

import org.apache.commons.lang3.tuple.ImmutablePair;

import org.hamcrest.core.StringContains;

import org.hibernate.Session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.mockito.InOrder;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerFactory;
import org.quartz.Trigger;

import org.slf4j.MDC;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;



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
        public void postJobMessage(JobMessage jobMessage) {
            this.messages.add(jobMessage);
        }

        public List<JobMessage> getSentMessages() {
            return this.messages;
        }

        @Override
        public void commit() {
            // Intentionally left empty
        }

        @Override
        public void rollback() {
            // Intentionally left empty
        }
    }


    private static final String JOB_ID = "jobId";
    private static final String JOB_KEY = TestJob1.getJobKey();

    private Configuration configuration;
    private SchedulerFactory schedulerFactory;
    private ModeManager modeManager;
    private AsyncJobStatusCurator jobCurator;
    private PrincipalProvider principalProvider;
    private CandlepinRequestScope requestScope;
    private CollectingJobMessageDispatcher dispatcher;
    private Injector injector;
    private EventSink eventSink;
    private UnitOfWork uow;

    private Scheduler scheduler;
    private List<ImmutablePair<String, String>> scheduledJobs;


    @BeforeEach
    public void setUp() throws Exception {
        this.configuration = new CandlepinCommonTestConfig();
        this.schedulerFactory = mock(SchedulerFactory.class);
        this.modeManager = mock(ModeManager.class);
        this.jobCurator = mock(AsyncJobStatusCurator.class);
        this.principalProvider = mock(PrincipalProvider.class);
        this.requestScope = mock(CandlepinRequestScope.class);
        this.dispatcher = new CollectingJobMessageDispatcher();
        this.injector = mock(Injector.class);
        this.eventSink = mock(EventSink.class);
        this.uow = mock(UnitOfWork.class);

        this.scheduler = mock(Scheduler.class);
        this.scheduledJobs = new LinkedList<>();

        Session session = mock(Session.class);

        doReturn(this.eventSink).when(this.injector).getInstance(EventSink.class);
        doReturn(this.jobCurator).when(this.injector).getInstance(AsyncJobStatusCurator.class);
        doReturn(this.uow).when(this.injector).getInstance(UnitOfWork.class);
        doReturn(session).when(this.jobCurator).currentSession();
        doAnswer(returnsFirstArg()).when(this.jobCurator).merge(any(AsyncJobStatus.class));
        doAnswer(returnsFirstArg()).when(this.jobCurator).create(any(AsyncJobStatus.class));
        doReturn(this.scheduler).when(this.schedulerFactory).getScheduler();
        doAnswer(new Answer<Date>() {
            @Override
            public Date answer(InvocationOnMock invocation) {
                Object[] args = invocation.getArguments();
                String key = null;
                String schedule = null;

                if (args[0] != null) {
                    if (((JobDetail) args[0]).getKey() != null) {
                        key = ((JobDetail) args[0]).getKey().getName();
                    }
                    else {
                        key = "null job key";
                    }
                }

                if (args[1] instanceof CronTrigger) {
                    schedule = ((CronTrigger) args[1]).getCronExpression();
                }
                else {
                    schedule = "unknown trigger: " +
                        (args[1] != null ? args[1].getClass().getName() : "null");
                }

                scheduledJobs.add(new ImmutablePair<>(key, schedule));
                return null; // This is probably bad, but at the time of writing, it'll work.
            }
        }).when(this.scheduler).scheduleJob(any(JobDetail.class), any(Trigger.class));
    }

    private void verifyScheduledJob(String key, String schedule) {
        String actual = null;

        for (ImmutablePair<String, String> job : this.scheduledJobs) {
            if (key.equals(job.getKey())) {
                actual = job.getValue();
            }
        }

        if (schedule != null) {
            assertEquals(schedule, actual);
        }
        else {
            assertNull(actual);
        }
    }

    private JobManager createJobManager() {
        return createJobManager(this.dispatcher);
    }

    private JobManager createJobManager(JobMessageDispatcher dispatcher) {
        return new JobManager(this.configuration, this.schedulerFactory, this.modeManager, this.jobCurator,
            dispatcher, this.principalProvider, this.requestScope, this.injector);
    }

    private JobArguments buildJobArguments(Map<String, Object> args) {
        Map<String, String> serialized = new HashMap<>();

        if (args != null) {
            for (Map.Entry<String, Object> entry : args.entrySet()) {
                serialized.put(entry.getKey(), JobArguments.serialize(entry.getValue()));
            }
        }

        return new JobArguments(serialized);
    }

    @Test
    public void jobShouldFailWhenJobStatusIsNotFound() {
        doReturn(null).when(jobCurator).get(anyString());
        final JobManager manager = createJobManager();


        Throwable throwable = assertThrows(
            JobInitializationException.class,
            () -> manager.executeJob(new JobMessage(JOB_ID, JOB_KEY)));
        assertThat(throwable.getMessage(), StringContains.containsString("Unable to find"));
    }

    public static Object[] getTerminalJobStates() {
        List<JobState> states = new ArrayList<>();

        for (JobState state : JobState.values()) {
            if (state.isTerminal()) {
                states.add(state);
            }
        }

        return states.toArray();
    }

    @ParameterizedTest
    @MethodSource("getTerminalJobStates")
    public void jobShouldFailWhenJobStateIsTerminal(JobState state) {
        AsyncJobStatus status = spy(new AsyncJobStatus()
            .setJobKey(JOB_KEY)
            .setState(state));

        doReturn(status).when(this.jobCurator).get(anyString());
        final JobManager manager = createJobManager();

        Throwable throwable = assertThrows(
            JobInitializationException.class,
            () -> manager.executeJob(new JobMessage(JOB_ID, JOB_KEY)));
        assertThat(throwable.getMessage(), StringContains.containsString("unknown or terminal state"));
    }

    @Test
    public void shouldFailWhenJobCouldNotBeConstructed() throws JobException {
        AsyncJobStatus status = spy(new AsyncJobStatus()
            .setJobKey(JOB_KEY));

        doReturn(status).when(jobCurator).get(anyString());
        final JobManager manager = createJobManager();

        Throwable throwable = assertThrows(
            JobInitializationException.class,
            () -> manager.executeJob(new JobMessage(JOB_ID, JOB_KEY)));
        assertThat(throwable.getMessage(), StringContains.containsString("Unable to instantiate"));
    }

    @Test
    public void jobShouldBeExecuted() throws JobException {
        AsyncJobStatus status = spy(new AsyncJobStatus()
            .setJobKey(JOB_KEY)
            .setState(JobState.QUEUED));

        final AsyncJob spy = mock(AsyncJob.class);
        doReturn(spy).when(injector).getInstance(TestJob1.class);
        doReturn(status).when(jobCurator).get(anyString());
        final JobManager manager = createJobManager();

        manager.executeJob(new JobMessage(JOB_ID, JOB_KEY));

        verify(spy).execute(any());
    }

    @Test
    public void shouldConfigureLoggingContext() throws JobException {
        final AsyncJobStatus status = spy(new AsyncJobStatus()
            .setJobKey(JOB_KEY)
            .addMetadata("owner_key", "test_owner")
            .addMetadata("some_key", "some_value")
            .setLogLevel("TRACE"));

        doReturn(JOB_ID).when(status).getId();

        doReturn(mock(AsyncJob.class)).when(injector).getInstance(TestJob1.class);
        doReturn(status).when(jobCurator).get(JOB_ID);

        final JobManager manager = createJobManager();
        manager.executeJob(new JobMessage(JOB_ID, JOB_KEY));

        assertEquals("job", MDC.get("requestType"));
        assertEquals(status.getId(), MDC.get("requestUuid"));
        assertEquals("test_owner", MDC.get("owner_key"));
        assertEquals("some_value", MDC.get("some_key"));
        assertEquals("TRACE", MDC.get("logLevel"));
    }

    @Test
    public void shouldSendEvents() throws JobException {
        AsyncJobStatus status = spy(new AsyncJobStatus()
            .setJobKey(JOB_KEY)
            .setState(JobState.QUEUED));

        doReturn(mock(AsyncJob.class)).when(injector).getInstance(TestJob1.class);
        doReturn(status).when(jobCurator).get(anyString());
        final JobManager manager = createJobManager();

        manager.executeJob(new JobMessage(JOB_ID, JOB_KEY));

        verify(this.eventSink).sendEvents();
    }

    @Test
    public void shouldRollbackEventsOfFailedJob() {
        final AsyncJob job = jdata -> {
            throw new JobExecutionException();
        };

        AsyncJobStatus status = spy(new AsyncJobStatus()
            .setJobKey(JOB_KEY)
            .setState(JobState.QUEUED));

        doReturn(job).when(injector).getInstance(TestJob1.class);
        doReturn(status).when(jobCurator).get(anyString());
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
        status.setJobKey(JOB_KEY);

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
    public void shouldSetJobExecutor() throws JobException {
        final AsyncJobStatus status = new AsyncJobStatus()
            .setJobKey(JOB_KEY);

        doReturn(mock(AsyncJob.class)).when(injector).getInstance(TestJob1.class);
        doReturn(new AsyncJobStatus().setState(JobState.QUEUED))
            .when(jobCurator).get(anyString());
        doReturn(status).when(this.jobCurator).get(JOB_ID);
        final JobManager manager = createJobManager();

        manager.executeJob(new JobMessage(JOB_ID, JOB_KEY));

        final String executor = status.getExecutor();
        assertFalse(executor == null || executor.isEmpty());
    }

    @Test
    public void failedJobShouldEndAsFailed() throws JobInitializationException {
        final StateCollectingStatus status = new StateCollectingStatus();
        status.setJobKey(JOB_KEY);

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
        AsyncJobStatus status = new AsyncJobStatus()
            .setJobKey(JOB_KEY)
            .setState(JobState.QUEUED);

        final AsyncJob job = mock(AsyncJob.class);
        doReturn(job).when(injector).getInstance(TestJob1.class);
        doReturn(status).when(jobCurator).get(anyString());
        final InOrder inOrder = inOrder(uow, job, uow);
        final JobManager manager = createJobManager();

        manager.executeJob(new JobMessage(JOB_ID, JOB_KEY));

        inOrder.verify(uow).begin();
        inOrder.verify(job).execute(any());
        inOrder.verify(uow).end();
    }

    @Test
    public void shouldProperlyEndUnitOfWorkOfFailedJobs() throws JobException {
        AsyncJobStatus status = new AsyncJobStatus()
            .setJobKey(JOB_KEY)
            .setState(JobState.QUEUED);

        final AsyncJob job = mock(AsyncJob.class);
        doReturn(job).when(injector).getInstance(TestJob1.class);
        doReturn(status).when(jobCurator).get(anyString());
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
            .setJobKey(JOB_KEY)
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
            .setJobKey(JOB_KEY)
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
            .setJobKey(JOB_KEY)
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
            .setJobKey(JOB_KEY)
            .setState(JobState.QUEUED);

        doReturn(job).when(this.injector).getInstance(TestJob1.class);
        doReturn(status).when(this.jobCurator).get(anyString());

        assertEquals(0, status.getAttempts());

        JobManager manager = this.createJobManager();
        manager.executeJob(new JobMessage(JOB_ID, JOB_KEY));

        assertEquals(1, status.getAttempts());
    }

    @Test
    public void testAttemptCountRemainsIncrementedOnExecutionFailure() {
        AsyncJob job = jdata -> { throw new JobExecutionException("kaboom"); };
        AsyncJobStatus status = new AsyncJobStatus()
            .setJobKey(JOB_KEY)
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

        assertEquals(JobState.QUEUED, status.getState());
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

    @Test
    public void testFailedStateUpdateResultsInStateManagementExceptionDuringRetryExecution()
        throws JobException {

        AsyncJobStatus status = spy(new AsyncJobStatus()
            .setJobKey(JOB_KEY)
            .setState(JobState.QUEUED)
            .setMaxAttempts(3));

        AsyncJob job = jdata -> {
            doThrow(new SQLException()).when(jobCurator).merge(status);
            throw new JobExecutionException("kaboom");
        };

        // TODO: Stop doing this when we stop relying on Hibernate to generate the ID for us
        doReturn(JOB_ID).when(status).getId();
        doReturn(status).when(this.jobCurator).get(JOB_ID);
        doReturn(job).when(this.injector).getInstance(TestJob1.class);

        JobManager manager = this.createJobManager();
        assertThrows(JobStateManagementException.class,
            () -> manager.executeJob(new JobMessage(JOB_ID, JOB_KEY)));
    }

    @Test
    public void testFailedMessageDispatchResultsInMessageDispatchExceptionDuringRetryExecution()
        throws JobException {

        AsyncJobStatus status = spy(new AsyncJobStatus()
            .setJobKey(JOB_KEY)
            .setState(JobState.QUEUED)
            .setMaxAttempts(3));

        AsyncJob job = jdata -> {
            throw new JobExecutionException("kaboom");
        };

        // TODO: Stop doing this when we stop relying on Hibernate to generate the ID for us
        doReturn(JOB_ID).when(status).getId();
        doReturn(status).when(this.jobCurator).get(JOB_ID);
        doReturn(job).when(this.injector).getInstance(TestJob1.class);

        JobMessageDispatcher dispatcher = mock(JobMessageDispatcher.class);
        doThrow(new JobMessageDispatchException()).when(dispatcher).postJobMessage(any());

        JobManager manager = this.createJobManager(dispatcher);
        assertThrows(JobMessageDispatchException.class,
            () -> manager.executeJob(new JobMessage(JOB_ID, JOB_KEY)));
    }

    @Test
    public void testJobExecutionFailsWithNoJobKey() throws JobException {
        AsyncJobStatus status = spy(new AsyncJobStatus()
            .setState(JobState.QUEUED)
            .setMaxAttempts(3));

        AsyncJob job = jdata -> { return null; };

        doReturn(JOB_ID).when(status).getId();
        doReturn(status).when(this.jobCurator).get(JOB_ID);
        doReturn(job).when(this.injector).getInstance(TestJob1.class);

        JobManager manager = this.createJobManager();
        assertThrows(JobInitializationException.class,
            () -> manager.executeJob(new JobMessage(JOB_ID, JOB_KEY)));
    }

    @Test
    public void testJobIsEnabledDefaultsEnabledWithNoConfiguration() {
        JobManager manager = this.createJobManager();

        boolean result = manager.isJobEnabled(JOB_KEY);
        assertTrue(result);
    }

    @Test
    public void testJobIsEnabledProperlyWhitelistsJobs() {
        List<String> jobs = Arrays.asList("a", "b", "c", "d", "e");
        List<String> whitelist = jobs.subList(1, 3);

        this.configuration.setProperty(ConfigProperties.ASYNC_JOBS_WHITELIST, String.join(",", whitelist));

        JobManager manager = this.createJobManager();

        for (String jobKey : jobs) {
            boolean expected = whitelist.contains(jobKey);
            boolean result = manager.isJobEnabled(jobKey);

            assertEquals(expected, result);
        }
    }

    @Test
    public void testJobIsEnabledProperlyBlacklistsJobs() {
        List<String> jobs = Arrays.asList("a", "b", "c", "d", "e");
        List<String> blacklist = jobs.subList(1, 3);

        this.configuration.setProperty(ConfigProperties.ASYNC_JOBS_BLACKLIST, String.join(",", blacklist));

        JobManager manager = this.createJobManager();

        for (String jobKey : jobs) {
            boolean expected = !blacklist.contains(jobKey);
            boolean result = manager.isJobEnabled(jobKey);

            assertEquals(expected, result);
        }
    }

    @Test
    public void testJobIsEnabledAllowsJobsToBeDisabled() {
        List<String> jobs = Arrays.asList("a", "b", "c", "d", "e");
        List<String> disabled = jobs.subList(1, 3);

        for (String jobKey : disabled) {
            this.configuration.setProperty(ConfigProperties.ASYNC_JOBS_PREFIX + jobKey + '.' +
                ConfigProperties.ASYNC_JOBS_SUFFIX_ENABLED, "false");
        }

        JobManager manager = this.createJobManager();

        for (String jobKey : jobs) {
            boolean expected = !disabled.contains(jobKey);
            boolean result = manager.isJobEnabled(jobKey);

            assertEquals(expected, result);
        }
    }

    @Test
    public void testJobIsEnabledProperlyCombinesWhitelistAndBlacklist() {
        List<String> jobs = Arrays.asList("a", "b", "c", "d", "e");
        List<String> whitelist = jobs.subList(1, 2);
        List<String> blacklist = jobs.subList(2, 4);

        this.configuration.setProperty(ConfigProperties.ASYNC_JOBS_WHITELIST, String.join(",", whitelist));
        this.configuration.setProperty(ConfigProperties.ASYNC_JOBS_BLACKLIST, String.join(",", blacklist));

        JobManager manager = this.createJobManager();

        for (String jobKey : jobs) {
            boolean expected = whitelist.contains(jobKey) && !blacklist.contains(jobKey);
            boolean result = manager.isJobEnabled(jobKey);

            assertEquals(expected, result);
        }
    }

    @Test
    public void testJobIsEnabledProperlyCombinesWhitelistAndBlacklistAndPerJobConfig() {
        List<String> jobs = Arrays.asList("a", "b", "c", "d", "e");
        List<String> whitelist = jobs.subList(1, 3);
        List<String> blacklist = jobs.subList(2, 4);
        List<String> disabled = jobs.subList(3, 5);

        this.configuration.setProperty(ConfigProperties.ASYNC_JOBS_WHITELIST, String.join(",", whitelist));
        this.configuration.setProperty(ConfigProperties.ASYNC_JOBS_BLACKLIST, String.join(",", blacklist));
        for (String jobKey : disabled) {
            this.configuration.setProperty(ConfigProperties.ASYNC_JOBS_PREFIX + jobKey + '.' +
                ConfigProperties.ASYNC_JOBS_SUFFIX_ENABLED, "false");
        }

        JobManager manager = this.createJobManager();

        for (String jobKey : jobs) {
            boolean expected = whitelist.contains(jobKey) && !blacklist.contains(jobKey) &&
                !disabled.contains(jobKey);
            boolean result = manager.isJobEnabled(jobKey);

            assertEquals(expected, result);
        }
    }

    @Test
    public void testJobScheduling() {
        String schedule = "%d * * * * ?";
        int jobs = 3;

        for (int i = 0; i < jobs; ++i) {
            this.configuration.setProperty(ConfigProperties.ASYNC_JOBS_PREFIX + JOB_KEY + '-' + i + '.' +
                ConfigProperties.ASYNC_JOBS_SUFFIX_SCHEDULE, String.format(schedule, i));
        }

        JobManager manager = this.createJobManager();
        manager.initialize();

        for (int i = 0; i < jobs; ++i) {
            this.verifyScheduledJob(JOB_KEY + '-' + i, String.format(schedule, i));
        }
    }

    @Test
    public void testJobSchedulingDoesNotScheduleDisabledJobs() {
        this.configuration.setProperty(ConfigProperties.ASYNC_JOBS_PREFIX + JOB_KEY + '.' +
            ConfigProperties.ASYNC_JOBS_SUFFIX_SCHEDULE, "5 * * * * ?");
        this.configuration.setProperty(ConfigProperties.ASYNC_JOBS_PREFIX + JOB_KEY + '.' +
            ConfigProperties.ASYNC_JOBS_SUFFIX_ENABLED, "false");

        JobManager manager = this.createJobManager();
        manager.initialize();

        assertEquals(0, this.scheduledJobs.size());
    }

    @Test
    public void testJobSchedulingDoesNotScheduleBlacklistedJobs() {
        this.configuration.setProperty(ConfigProperties.ASYNC_JOBS_PREFIX + JOB_KEY + '.' +
            ConfigProperties.ASYNC_JOBS_SUFFIX_SCHEDULE, "5 * * * * ?");

        this.configuration.setProperty(ConfigProperties.ASYNC_JOBS_BLACKLIST, "a,b," + JOB_KEY + ",d,e");

        JobManager manager = this.createJobManager();
        manager.initialize();

        assertEquals(0, this.scheduledJobs.size());
    }

    @Test
    public void testJobSchedulingDoesNotScheduleJobsNotOnWhitelist() {
        this.configuration.setProperty(ConfigProperties.ASYNC_JOBS_PREFIX + JOB_KEY + '.' +
            ConfigProperties.ASYNC_JOBS_SUFFIX_SCHEDULE, "5 * * * * ?");

        this.configuration.setProperty(ConfigProperties.ASYNC_JOBS_WHITELIST, "a,b,c,d,e");

        JobManager manager = this.createJobManager();
        manager.initialize();

        assertEquals(0, this.scheduledJobs.size());
    }

    @Test
    public void verifyModeChangePausesSchedulerOnSuspendMode() throws Exception {
        JobManager manager = this.createJobManager();
        manager.initialize();
        manager.start();

        manager.modeChanged(Mode.SUSPEND);

        verify(this.scheduler, times(1)).standby();
    }

    @Test
    public void verifyModeChangeResumesSchedulerOnNormalMode() throws Exception {
        JobManager manager = this.createJobManager();
        manager.initialize();

        manager.modeChanged(Mode.NORMAL);

        verify(this.scheduler, times(1)).start();
    }

    @Test
    public void testManagerCanBeInitializedAfterCreation() {
        JobManager manager = this.createJobManager();
        manager.initialize();
    }

    @Test
    public void testManagerCanBeShutdownAfterCreation() {
        JobManager manager = this.createJobManager();
        manager.shutdown();
    }

    @Test
    public void testManagerCannotBePausedBeforeInit() {
        JobManager manager = this.createJobManager();

        assertThrows(IllegalStateException.class, manager::pause);
    }

    @Test
    public void testManagerCannotBeResumedBeforeInit() {
        JobManager manager = this.createJobManager();

        assertThrows(IllegalStateException.class, manager::resume);
    }

    @Test
    public void testManagerCannotBeInitializedTwice() {
        JobManager manager = this.createJobManager();

        manager.initialize();
        assertThrows(IllegalStateException.class, manager::initialize);
    }

    @Test
    public void testManagerCannotBeInitializedAfterShutdown() {
        JobManager manager = this.createJobManager();
        manager.shutdown();

        assertThrows(IllegalStateException.class, manager::initialize);
    }

    @Test
    public void testManagerCannotBePausedAfterShutdown() {
        JobManager manager = this.createJobManager();
        manager.shutdown();

        assertThrows(IllegalStateException.class, manager::pause);
    }

    @Test
    public void testManagerCannotBeResumedAfterShutdown() {
        JobManager manager = this.createJobManager();
        manager.shutdown();

        assertThrows(IllegalStateException.class, manager::pause);
    }

    @Test
    public void testManagerCannotBeStartedInSuspendMode() {
        JobManager manager = this.createJobManager();
        manager.initialize();

        CandlepinModeChange mode = new CandlepinModeChange(new Date(), Mode.SUSPEND);
        doReturn(mode).when(this.modeManager).getLastCandlepinModeChange();

        assertThrows(IllegalStateException.class, manager::start);
    }

    @Test
    public void testJobIsQueuedIfConstraintsPass() throws Exception {
        Map<String, Object> ejobData1 = new HashMap<>();
        ejobData1.put("arg1", "val1");

        Map<String, Object> ejobData2 = new HashMap<>();
        ejobData2.put("arg1", "val2");

        AsyncJobStatus ejob1 = spy(new AsyncJobStatus()
            .setJobKey(JOB_KEY)
            .setState(JobState.QUEUED)
            .setJobArguments(this.buildJobArguments(ejobData1)));

        AsyncJobStatus ejob2 = spy(new AsyncJobStatus()
            .setJobKey(JOB_KEY)
            .setState(JobState.QUEUED)
            .setJobArguments(this.buildJobArguments(ejobData2)));

        JobConfig builder = JobConfig.forJob(JOB_KEY)
            .addConstraint(JobConstraints.uniqueByArgument("arg1"))
            .setJobArgument("arg1", "val3");

        doReturn(Arrays.asList(ejob1, ejob2)).when(this.jobCurator).getNonTerminalJobs();

        JobManager manager = this.createJobManager();
        AsyncJobStatus result = manager.queueJob(builder);

        assertNotNull(result);
        assertEquals(JobState.QUEUED, result.getState());
    }

    @Test
    public void testJobDoesNotQueueIfConstraintFails() throws Exception {
        Map<String, Object> ejobData1 = new HashMap<>();
        ejobData1.put("arg1", "val1");

        Map<String, Object> ejobData2 = new HashMap<>();
        ejobData2.put("arg1", "val2");

        AsyncJobStatus ejob1 = spy(new AsyncJobStatus()
            .setJobKey(JOB_KEY)
            .setState(JobState.QUEUED)
            .setJobArguments(this.buildJobArguments(ejobData1)));

        AsyncJobStatus ejob2 = spy(new AsyncJobStatus()
            .setJobKey(JOB_KEY)
            .setState(JobState.QUEUED)
            .setJobArguments(this.buildJobArguments(ejobData2)));

        JobConfig builder = JobConfig.forJob(JOB_KEY)
            .addConstraint(JobConstraints.uniqueByArgument("arg1"))
            .setJobArgument("arg1", "val2");

        doReturn(Arrays.asList(ejob1, ejob2)).when(this.jobCurator).getNonTerminalJobs();

        JobManager manager = this.createJobManager();
        AsyncJobStatus result = manager.queueJob(builder);

        assertNotNull(result);
        assertEquals(JobState.ABORTED, result.getState());
    }

}
