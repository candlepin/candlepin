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

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Mockito.*;

import org.candlepin.audit.EventSink;
import org.candlepin.auth.Access;
import org.candlepin.auth.SystemPrincipal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.auth.permissions.OwnerPermission;
import org.candlepin.auth.permissions.Permission;
import org.candlepin.async.JobManager.ManagerState;
import org.candlepin.common.config.Configuration;
import org.candlepin.common.filter.LoggingFilter;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.mode.CandlepinModeManager;
import org.candlepin.controller.mode.CandlepinModeManager.Mode;
import org.candlepin.guice.CandlepinRequestScope;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.AsyncJobStatus;
import org.candlepin.model.AsyncJobStatus.JobState;
import org.candlepin.model.AsyncJobStatusCurator;
import org.candlepin.model.AsyncJobStatusCurator.AsyncJobStatusQueryBuilder;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.util.Util;

import com.google.inject.Injector;

import org.apache.commons.lang3.tuple.ImmutablePair;

import org.hamcrest.core.StringContains;
import org.hibernate.Session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.mockito.Mockito;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.mockito.quality.Strictness;

import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerFactory;
import org.quartz.Trigger;

import org.slf4j.MDC;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;



/**
 * Test suite for the JobManager class
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class JobManagerTest {

    private static class StateCollectingStatus extends AsyncJobStatus {
        private final List<JobState> states = new ArrayList<>();
        private final String id;

        public StateCollectingStatus(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return this.id;
        }

        @Override
        public AsyncJobStatus setState(final JobState state) {
            super.setState(state);
            this.states.add(state);
            return this;
        }

        public List<JobState> getCollectedStates() {
            return this.states;
        }
    }

    /** Job class to use for testing */
    private static class TestJob implements AsyncJob {
        public static final String JOB_KEY = "TestJob";

        @Override
        public Object execute(JobExecutionContext context) throws JobExecutionException {
            return null;
        }
    }


    private static final String JOB_ID = "jobId";

    private Configuration config;
    private SchedulerFactory schedulerFactory;
    private CandlepinModeManager modeManager;
    private AsyncJobStatusCurator jobCurator;
    private OwnerCurator ownerCurator;
    private JobMessageDispatcher dispatcher;
    private JobMessageReceiver receiver;
    private PrincipalProvider principalProvider;
    private CandlepinRequestScope requestScope;
    private Injector injector;
    private EventSink eventSink;

    private Scheduler scheduler;
    private List<ImmutablePair<String, String>> scheduledJobs;


    @BeforeEach
    public void setUp() throws Exception {
        MDC.clear();

        JobManager.registerJob(TestJob.JOB_KEY, TestJob.class);

        this.config = new CandlepinCommonTestConfig();
        this.schedulerFactory = mock(SchedulerFactory.class);
        this.modeManager = mock(CandlepinModeManager.class);
        this.ownerCurator = mock(OwnerCurator.class);
        this.dispatcher = mock(JobMessageDispatcher.class);
        this.receiver = mock(JobMessageReceiver.class);
        this.jobCurator = mock(AsyncJobStatusCurator.class);
        this.principalProvider = mock(PrincipalProvider.class);
        this.requestScope = mock(CandlepinRequestScope.class);
        this.injector = mock(Injector.class);
        this.eventSink = mock(EventSink.class);

        this.scheduler = mock(Scheduler.class);
        this.scheduledJobs = new LinkedList<>();

        Session session = mock(Session.class);

        doReturn(this.eventSink).when(this.injector).getInstance(EventSink.class);
        doReturn(this.jobCurator).when(this.injector).getInstance(AsyncJobStatusCurator.class);
        doReturn(session).when(this.jobCurator).currentSession();
        doAnswer(returnsFirstArg()).when(this.jobCurator).merge(Mockito.any(AsyncJobStatus.class));
        doAnswer(returnsFirstArg()).when(this.jobCurator).create(Mockito.any(AsyncJobStatus.class));
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
        }).when(this.scheduler).scheduleJob(Mockito.any(JobDetail.class), Mockito.any(Trigger.class));
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

    private void verifyNotScheduled(String key) {
        for (ImmutablePair<String, String> job : this.scheduledJobs) {
            assertNotEquals(key, job.getKey());
        }
    }

    private JobManager createJobManager() {
        return createJobManager(this.dispatcher);
    }

    private JobManager createJobManager(JobMessageDispatcher dispatcher) {
        return new JobManager(this.config, this.schedulerFactory, this.modeManager, this.jobCurator,
            this.ownerCurator, dispatcher, this.receiver, this.principalProvider, this.requestScope,
            this.injector);
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

    private AsyncJobStatus createJobStatus(String jobId) {
        return createJobStatus(jobId, null, null);
    }

    private AsyncJobStatus createJobStatus(String jobId, Owner owner) {
        return createJobStatus(jobId, owner, null);
    }

    private AsyncJobStatus createJobStatus(String jobId, Owner owner, JobState state) {
        AsyncJobStatus status = spy(new AsyncJobStatus());

        // TODO: Stop doing this when we stop relying on Hibernate to generate the ID for us
        when(status.getId()).thenReturn(jobId);


        if (state != null) {
            status.setState(state);
        }

        status.setContextOwner(owner);

        return status;
    }

    private AsyncJobStatus injectMockedJobStatus(AsyncJobStatus status) {
        String id = status.getId();

        doReturn(status).when(this.jobCurator).get(id);
        doReturn(status).when(this.jobCurator).lockAndLoad(id);

        return status;
    }

    @Test
    public void jobShouldFailWhenJobStatusIsNotFound() {
        doReturn(null).when(jobCurator).get(anyString());

        JobManager manager = createJobManager();
        manager.initialize();
        manager.start();

        Throwable throwable = assertThrows(
            JobInitializationException.class,
            () -> manager.executeJob(new JobMessage(JOB_ID, TestJob.JOB_KEY)));
        assertThat(throwable.getMessage(), StringContains.containsString("Unable to find"));
    }

    public static Stream<Arguments> terminalJobStatesProviderSansCanceled() {
        return Arrays.stream(JobState.values())
            .filter(state -> state != null && state.isTerminal())
            .filter(state -> state != null && state != JobState.CANCELED)
            .map(state -> Arguments.of(state));
    }

    @ParameterizedTest
    @MethodSource("terminalJobStatesProviderSansCanceled")
    public void jobShouldFailWhenJobStateIsTerminal(JobState state) {
        AsyncJobStatus status = this.createJobStatus(JOB_ID)
            .setJobKey(TestJob.JOB_KEY)
            .setState(state);

        this.injectMockedJobStatus(status);

        JobManager manager = createJobManager();
        manager.initialize();
        manager.start();

        Throwable throwable = assertThrows(
            JobInitializationException.class,
            () -> manager.executeJob(new JobMessage(JOB_ID, TestJob.JOB_KEY)));
        assertThat(throwable.getMessage(), StringContains.containsString("unknown or terminal state"));
    }

    @Test
    public void shouldFailWhenJobCouldNotBeConstructed() {
        AsyncJobStatus status = this.createJobStatus(JOB_ID)
            .setJobKey(TestJob.JOB_KEY);

        this.injectMockedJobStatus(status);

        JobManager manager = createJobManager();
        manager.initialize();
        manager.start();

        Throwable throwable = assertThrows(
            JobInitializationException.class,
            () -> manager.executeJob(new JobMessage(JOB_ID, TestJob.JOB_KEY)));
        assertThat(throwable.getMessage(), StringContains.containsString("Unable to instantiate"));
    }

    @Test
    public void jobShouldBeExecuted() throws JobException {
        AsyncJobStatus status = this.createJobStatus(JOB_ID)
            .setJobKey(TestJob.JOB_KEY)
            .setState(JobState.QUEUED);

        final AsyncJob spy = mock(AsyncJob.class);
        doReturn(spy).when(injector).getInstance(TestJob.class);
        this.injectMockedJobStatus(status);

        JobManager manager = createJobManager();
        manager.initialize();
        manager.start();

        manager.executeJob(new JobMessage(JOB_ID, TestJob.JOB_KEY));

        verify(spy).execute(any());
    }

    @Test
    public void testJobManagerDoesNotExecuteCanceledJob() throws JobException {
        AsyncJobStatus status = this.createJobStatus(JOB_ID)
            .setJobKey(TestJob.JOB_KEY)
            .setState(JobState.CANCELED);

        final AsyncJob spy = mock(AsyncJob.class);
        doReturn(spy).when(injector).getInstance(TestJob.class);
        this.injectMockedJobStatus(status);

        JobManager manager = createJobManager();
        manager.initialize();
        manager.start();

        manager.executeJob(new JobMessage(JOB_ID, TestJob.JOB_KEY));

        verify(spy, never()).execute(any());
    }

    public static Stream<Arguments> loggingContextArgProvider() {
        Owner owner1 = new Owner("test_owner-1", "owner_name-1");
        owner1.setId("test_owner_id-1");

        Owner owner2 = new Owner("test_owner-2", "owner_name-2");
        owner2.setId("test_owner_id-2");
        owner2.setLogLevel(Level.DEBUG);

        String jobLogLevel = Level.TRACE.name();

        return Stream.of(
            Arguments.of(null, null, null),
            Arguments.of(null, jobLogLevel, jobLogLevel),
            Arguments.of(owner1, null, owner1.getLogLevel()),
            Arguments.of(owner1, jobLogLevel, jobLogLevel),
            Arguments.of(owner2, null, owner2.getLogLevel()),
            Arguments.of(owner2, jobLogLevel, jobLogLevel));
    }

    @ParameterizedTest
    @MethodSource("loggingContextArgProvider")
    public void testLoggingContextConfiguration(Owner owner, String jobLogLevel, String expectedLogLevel)
        throws JobException {

        int metadataEntries = 3;

        // Setup environment
        AsyncJobStatus status = this.createJobStatus(JOB_ID, owner)
            .setJobKey(TestJob.JOB_KEY)
            .setLogLevel(jobLogLevel);

        for (int i = 0; i < metadataEntries; ++i) {
            status.addMetadata("md_key-" + i, "md_val-" + i);
        }

        Map<String, String> mdcCapture = new HashMap<>();
        AsyncJob captureJob = jdata -> {
            mdcCapture.putAll(MDC.getCopyOfContextMap());
            return null;
        };

        doReturn(captureJob).when(injector).getInstance(TestJob.class);
        this.injectMockedJobStatus(status);

        if (owner != null) {
            doReturn(owner).when(this.ownerCurator).get(owner.getId());
        }

        JobManager manager = createJobManager();
        manager.initialize();
        manager.start();

        manager.executeJob(new JobMessage(JOB_ID, TestJob.JOB_KEY));

        assertEquals("job", mdcCapture.get("requestType"));
        assertEquals(status.getId(), mdcCapture.get("requestUuid"));
        assertEquals(expectedLogLevel, mdcCapture.get("logLevel"));

        for (int i = 0; i < metadataEntries; ++i) {
            assertEquals("md_val-" + i, mdcCapture.get("md_key-" + i));
        }

        if (owner != null) {
            assertEquals(owner.getKey(), mdcCapture.get(LoggingFilter.OWNER_KEY));
        }
        else {
            assertNull(mdcCapture.get(LoggingFilter.OWNER_KEY));
        }
    }

    @Test
    public void testEnvironmentSetupGracefullyHandlesOwnerLookupFailure() throws JobException {
        Owner owner = new Owner("test_owner");
        owner.setId("dead owner id");

        AsyncJobStatus status = spy(new AsyncJobStatus()
            .setJobKey(TestJob.JOB_KEY)
            .setContextOwner(owner));

        doReturn(JOB_ID).when(status).getId();
        doReturn(mock(AsyncJob.class)).when(injector).getInstance(TestJob.class);
        this.injectMockedJobStatus(status);

        JobManager manager = createJobManager();
        manager.initialize();
        manager.start();

        manager.executeJob(new JobMessage(JOB_ID, TestJob.JOB_KEY));
        // This should not fail
    }

    @Test
    public void testMDCStateIsMaintained() throws JobException {
        AsyncJobStatus status = spy(new AsyncJobStatus()
            .setJobKey(TestJob.JOB_KEY)
            .addMetadata("job_metadata", "job_metadata_val")
            .addMetadata("some_shared_key", "job_value"));

        doReturn(JOB_ID).when(status).getId();
        doReturn(mock(AsyncJob.class)).when(injector).getInstance(TestJob.class);
        this.injectMockedJobStatus(status);

        MDC.put("some_shared_key", "global_value");
        MDC.put("some_other_key", "global_value");

        JobManager manager = createJobManager();
        manager.initialize();
        manager.start();

        manager.executeJob(new JobMessage(JOB_ID, TestJob.JOB_KEY));

        // We should only have the two values we set directly
        assertEquals("global_value", MDC.get("some_shared_key"));
        assertEquals("global_value", MDC.get("some_other_key"));
        assertNull(MDC.get("job_metadata"));
    }

    @Test
    public void shouldSendEvents() throws JobException {
        AsyncJobStatus status = this.createJobStatus(JOB_ID)
            .setJobKey(TestJob.JOB_KEY)
            .setState(JobState.QUEUED);

        doReturn(mock(AsyncJob.class)).when(injector).getInstance(TestJob.class);
        this.injectMockedJobStatus(status);

        JobManager manager = createJobManager();
        manager.initialize();
        manager.start();

        manager.executeJob(new JobMessage(JOB_ID, TestJob.JOB_KEY));

        verify(this.eventSink).sendEvents();
    }

    @Test
    public void shouldRollbackEventsOfFailedJob() {
        final AsyncJob job = jdata -> {
            throw new JobExecutionException();
        };

        AsyncJobStatus status = this.createJobStatus(JOB_ID)
            .setJobKey(TestJob.JOB_KEY)
            .setState(JobState.QUEUED);

        doReturn(job).when(injector).getInstance(TestJob.class);
        this.injectMockedJobStatus(status);

        JobManager manager = createJobManager();
        manager.initialize();
        manager.start();

        try {
            manager.executeJob(new JobMessage(JOB_ID, TestJob.JOB_KEY));
            fail("Should not happen");
        }
        catch (JobException e) {
            verify(this.eventSink).rollback();
        }
    }

    @Test
    public void successfulJobShouldEndAsCompleted() throws JobException {
        final StateCollectingStatus status = new StateCollectingStatus(JOB_ID);
        status.setJobKey(TestJob.JOB_KEY);

        doReturn(mock(AsyncJob.class)).when(injector).getInstance(TestJob.class);
        doReturn(new AsyncJobStatus().setState(JobState.QUEUED)).when(jobCurator).get(anyString());
        this.injectMockedJobStatus(status);

        JobManager manager = createJobManager();
        manager.initialize();
        manager.start();

        manager.executeJob(new JobMessage(JOB_ID, TestJob.JOB_KEY));

        List<JobState> collected = status.getCollectedStates();

        assertNotNull(collected);
        assertEquals(2, collected.size());
        assertThat(collected, hasItems(JobState.RUNNING, JobState.FINISHED));
    }

    @Test
    public void shouldSetJobExecutor() throws JobException {
        final AsyncJobStatus status = this.createJobStatus(JOB_ID)
            .setJobKey(TestJob.JOB_KEY);

        doReturn(mock(AsyncJob.class)).when(injector).getInstance(TestJob.class);
        doReturn(new AsyncJobStatus().setState(JobState.QUEUED)).when(jobCurator).get(anyString());
        this.injectMockedJobStatus(status);

        final JobManager manager = createJobManager();
        manager.initialize();
        manager.start();

        manager.executeJob(new JobMessage(JOB_ID, TestJob.JOB_KEY));

        assertThat(status.getExecutor(), not(isEmptyOrNullString()));
    }

    @Test
    public void failedJobShouldEndAsFailed() {
        final StateCollectingStatus status = new StateCollectingStatus(JOB_ID);
        status.setJobKey(TestJob.JOB_KEY);

        final AsyncJob mock = jdata -> {
            throw new JobExecutionException();
        };

        doReturn(mock).when(injector).getInstance(TestJob.class);
        this.injectMockedJobStatus(status);

        JobManager manager = createJobManager();
        manager.initialize();
        manager.start();

        try {
            manager.executeJob(new JobMessage(JOB_ID, TestJob.JOB_KEY));
            fail("Should not happen");
        }
        catch (JobException e) {
            List<JobState> collected = status.getCollectedStates();

            assertNotNull(collected);
            assertEquals(2, collected.size());
            assertThat(collected, hasItems(JobState.RUNNING, JobState.FAILED));
        }
    }

    @Test
    public void testStartTimeIsSetOnExecution() throws JobException {
        AsyncJob job = jdata -> { return null; };
        AsyncJobStatus status = this.createJobStatus(JOB_ID)
            .setJobKey(TestJob.JOB_KEY)
            .setState(JobState.QUEUED);

        doReturn(job).when(this.injector).getInstance(TestJob.class);
        this.injectMockedJobStatus(status);

        assertNull(status.getStartTime());
        Date start = new Date();

        JobManager manager = this.createJobManager();
        manager.initialize();
        manager.start();

        manager.executeJob(new JobMessage(JOB_ID, TestJob.JOB_KEY));

        Date end = new Date();

        assertNotNull(status.getStartTime());
        assertThat(status.getStartTime(), greaterThanOrEqualTo(start));
        assertThat(status.getStartTime(), lessThanOrEqualTo(end));
    }

    @Test
    public void testEndTimeIsSetOnExecution() throws JobException {
        AsyncJob job = jdata -> { return null; };
        AsyncJobStatus status = this.createJobStatus(JOB_ID)
            .setJobKey(TestJob.JOB_KEY)
            .setState(JobState.QUEUED);

        doReturn(job).when(this.injector).getInstance(TestJob.class);
        this.injectMockedJobStatus(status);

        assertNull(status.getEndTime());
        Date start = new Date();

        JobManager manager = this.createJobManager();
        manager.initialize();
        manager.start();

        manager.executeJob(new JobMessage(JOB_ID, TestJob.JOB_KEY));

        Date end = new Date();

        assertNotNull(status.getEndTime());
        assertThat(status.getEndTime(), greaterThanOrEqualTo(start));
        assertThat(status.getEndTime(), lessThanOrEqualTo(end));
    }

    @Test
    public void testEndTimeIsSetOnExecutionFailure() {
        AsyncJob job = jdata -> { throw new JobExecutionException("kaboom"); };
        AsyncJobStatus status = this.createJobStatus(JOB_ID)
            .setJobKey(TestJob.JOB_KEY)
            .setState(JobState.QUEUED);

        doReturn(job).when(this.injector).getInstance(TestJob.class);
        this.injectMockedJobStatus(status);

        assertNull(status.getEndTime());
        Date start = new Date();

        try {
            JobManager manager = this.createJobManager();
            manager.initialize();
            manager.start();

            manager.executeJob(new JobMessage(JOB_ID, TestJob.JOB_KEY));

            fail("Expected exception was not thrown by JobManager.executeJob");
        }
        catch (JobException e) {
            // This is expected
            assertEquals("kaboom", e.getMessage());
        }

        Date end = new Date();

        assertNotNull(status.getEndTime());
        assertThat(status.getEndTime(), greaterThanOrEqualTo(start));
        assertThat(status.getEndTime(), lessThanOrEqualTo(end));
    }

    @Test
    public void testAttemptCountIsIncrementedOnExecution() throws JobException {
        AsyncJob job = jdata -> { return null; };
        AsyncJobStatus status = this.createJobStatus(JOB_ID)
            .setJobKey(TestJob.JOB_KEY)
            .setState(JobState.QUEUED);

        doReturn(job).when(this.injector).getInstance(TestJob.class);
        this.injectMockedJobStatus(status);

        assertEquals(0, status.getAttempts());

        JobManager manager = this.createJobManager();
        manager.initialize();
        manager.start();

        manager.executeJob(new JobMessage(JOB_ID, TestJob.JOB_KEY));

        assertEquals(1, status.getAttempts());
    }

    @Test
    public void testAttemptCountRemainsIncrementedOnExecutionFailure() {
        AsyncJob job = jdata -> { throw new JobExecutionException("kaboom"); };
        AsyncJobStatus status = this.createJobStatus(JOB_ID)
            .setJobKey(TestJob.JOB_KEY)
            .setState(JobState.QUEUED);

        doReturn(job).when(this.injector).getInstance(TestJob.class);
        this.injectMockedJobStatus(status);

        assertEquals(0, status.getAttempts());

        try {
            JobManager manager = this.createJobManager();
            manager.initialize();
            manager.start();

            manager.executeJob(new JobMessage(JOB_ID, TestJob.JOB_KEY));

            fail("Expected exception was not thrown by JobManager.executeJob");
        }
        catch (JobException e) {
            // This is expected
            assertEquals("kaboom", e.getMessage());
        }

        assertEquals(1, status.getAttempts());
    }

    @Test
    public void testFailingWithRetryGeneratesNewJobMessage() throws Exception  {
        AsyncJob job = jdata -> { throw new JobExecutionException("kaboom"); };
        AsyncJobStatus status = this.createJobStatus(JOB_ID)
            .setJobKey(TestJob.JOB_KEY)
            .setState(JobState.QUEUED)
            .setMaxAttempts(3);

        doReturn(job).when(this.injector).getInstance(TestJob.class);
        this.injectMockedJobStatus(status);

        ArgumentCaptor<JobMessage> captor = ArgumentCaptor.forClass(JobMessage.class);

        try {
            JobManager manager = this.createJobManager();
            manager.initialize();
            manager.start();

            manager.executeJob(new JobMessage(JOB_ID, TestJob.JOB_KEY));

            fail("Expected exception was not thrown by JobManager.executeJob");
        }
        catch (JobException e) {
            // This is expected
            assertEquals("kaboom", e.getMessage());
        }

        JobManager manager = this.createJobManager();

        // Verify input is passed through, unmodified
        verify(this.dispatcher, times(1)).postJobMessage(captor.capture());
        JobMessage message = captor.getValue();

        assertNotNull(message);
        assertEquals(JOB_ID, message.getJobId());
        assertEquals(TestJob.JOB_KEY, message.getJobKey());

        assertEquals(JobState.QUEUED, status.getState());
    }

    @Test
    public void testFailingWithRetryDoesNotRetryWhenAttemptsExhaused() throws Exception  {
        AsyncJob job = jdata -> { throw new JobExecutionException("kaboom"); };
        AsyncJobStatus status = this.createJobStatus(JOB_ID)
            .setJobKey(TestJob.JOB_KEY)
            .setState(JobState.QUEUED)
            .incrementAttempts()
            .setMaxAttempts(2);

        doReturn(job).when(this.injector).getInstance(TestJob.class);
        this.injectMockedJobStatus(status);

        verify(this.dispatcher, never()).postJobMessage(Mockito.any(JobMessage.class));

        try {
            JobManager manager = this.createJobManager();
            manager.initialize();
            manager.start();

            manager.executeJob(new JobMessage(JOB_ID, TestJob.JOB_KEY));

            fail("Expected exception was not thrown by JobManager.executeJob");
        }
        catch (JobException e) {
            // This is expected
            assertEquals("kaboom", e.getMessage());
        }

        verify(this.dispatcher, never()).postJobMessage(Mockito.any(JobMessage.class));
        assertEquals(JobState.FAILED, status.getState());
    }

    @Test
    public void testFailingWithRetryDoesNotRetryOnTerminalFailure() throws Exception {
        AsyncJob job = jdata -> { throw new JobExecutionException("kaboom", true); };
        AsyncJobStatus status = spy(new AsyncJobStatus()
            .setJobKey(TestJob.JOB_KEY)
            .setState(JobState.QUEUED)
            .setMaxAttempts(3));

        // TODO: Stop doing this when we stop relying on Hibernate to generate the ID for us
        doReturn(JOB_ID).when(status).getId();

        doReturn(job).when(this.injector).getInstance(TestJob.class);
        this.injectMockedJobStatus(status);

        verify(this.dispatcher, never()).postJobMessage(Mockito.any(JobMessage.class));

        try {
            JobManager manager = this.createJobManager();
            manager.initialize();
            manager.start();

            manager.executeJob(new JobMessage(JOB_ID, TestJob.JOB_KEY));

            fail("Expected exception was not thrown by JobManager.executeJob");
        }
        catch (JobException e) {
            // This is expected
            assertEquals("kaboom", e.getMessage());
        }

        verify(this.dispatcher, never()).postJobMessage(Mockito.any(JobMessage.class));
        assertEquals(JobState.FAILED, status.getState());
    }

    @Test
    public void testFailedStateUpdateResultsInStateManagementExceptionDuringRetryExecution() {
        AsyncJobStatus status = spy(new AsyncJobStatus()
            .setJobKey(TestJob.JOB_KEY)
            .setState(JobState.QUEUED)
            .setMaxAttempts(3));

        AsyncJob job = jdata -> {
            doThrow(new RuntimeException()).when(jobCurator).merge(status);
            throw new JobExecutionException("kaboom");
        };

        // TODO: Stop doing this when we stop relying on Hibernate to generate the ID for us
        doReturn(JOB_ID).when(status).getId();
        doReturn(job).when(this.injector).getInstance(TestJob.class);
        this.injectMockedJobStatus(status);

        JobManager manager = this.createJobManager();
        manager.initialize();
        manager.start();

        assertThrows(JobStateManagementException.class,
            () -> manager.executeJob(new JobMessage(JOB_ID, TestJob.JOB_KEY)));
    }

    @Test
    public void testFailedMessageDispatchResultsInMessageDispatchExceptionDuringRetryExecution()
        throws JobException {

        AsyncJobStatus status = spy(new AsyncJobStatus()
            .setJobKey(TestJob.JOB_KEY)
            .setState(JobState.QUEUED)
            .setMaxAttempts(3));

        AsyncJob job = jdata -> {
            throw new JobExecutionException("kaboom");
        };

        // TODO: Stop doing this when we stop relying on Hibernate to generate the ID for us
        doReturn(JOB_ID).when(status).getId();
        doReturn(job).when(this.injector).getInstance(TestJob.class);
        this.injectMockedJobStatus(status);

        JobMessageDispatcher dispatcher = mock(JobMessageDispatcher.class);
        doThrow(new JobMessageDispatchException()).when(dispatcher).postJobMessage(any());

        JobManager manager = this.createJobManager(dispatcher);
        manager.initialize();
        manager.start();

        assertThrows(JobMessageDispatchException.class,
            () -> manager.executeJob(new JobMessage(JOB_ID, TestJob.JOB_KEY)));
    }

    @Test
    public void testJobExecutionFailsWithNoJobKey() {
        AsyncJobStatus status = spy(new AsyncJobStatus()
            .setState(JobState.QUEUED)
            .setMaxAttempts(3));

        AsyncJob job = jdata -> { return null; };

        doReturn(JOB_ID).when(status).getId();
        doReturn(job).when(this.injector).getInstance(TestJob.class);
        this.injectMockedJobStatus(status);

        JobManager manager = this.createJobManager();
        manager.initialize();
        manager.start();

        assertThrows(JobInitializationException.class,
            () -> manager.executeJob(new JobMessage(JOB_ID, TestJob.JOB_KEY)));
    }

    @Test
    public void testJobIsEnabledDefaultsEnabledWithNoConfiguration() {
        JobManager manager = this.createJobManager();

        boolean result = manager.isJobEnabled(TestJob.JOB_KEY);
        assertTrue(result);
    }

    @Test
    public void testJobIsEnabledProperlyWhitelistsJobs() {
        List<String> jobs = Arrays.asList("a", "b", "c", "d", "e");
        List<String> whitelist = jobs.subList(1, 3);

        this.config.setProperty(ConfigProperties.ASYNC_JOBS_WHITELIST, String.join(",", whitelist));

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

        this.config.setProperty(ConfigProperties.ASYNC_JOBS_BLACKLIST, String.join(",", blacklist));

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
            this.config.setProperty(ConfigProperties.ASYNC_JOBS_PREFIX + jobKey + '.' +
                ConfigProperties.ASYNC_JOBS_JOB_ENABLED, "false");
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

        this.config.setProperty(ConfigProperties.ASYNC_JOBS_WHITELIST, String.join(",", whitelist));
        this.config.setProperty(ConfigProperties.ASYNC_JOBS_BLACKLIST, String.join(",", blacklist));

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

        this.config.setProperty(ConfigProperties.ASYNC_JOBS_WHITELIST, String.join(",", whitelist));
        this.config.setProperty(ConfigProperties.ASYNC_JOBS_BLACKLIST, String.join(",", blacklist));
        for (String jobKey : disabled) {
            this.config.setProperty(ConfigProperties.ASYNC_JOBS_PREFIX + jobKey + '.' +
                ConfigProperties.ASYNC_JOBS_JOB_ENABLED, "false");
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
            String cfgName = ConfigProperties.jobConfig(TestJob.JOB_KEY + '-' + i,
                ConfigProperties.ASYNC_JOBS_JOB_SCHEDULE);

            this.config.setProperty(cfgName, String.format(schedule, i));
        }

        JobManager manager = this.createJobManager();
        manager.initialize();

        for (int i = 0; i < jobs; ++i) {
            this.verifyScheduledJob(TestJob.JOB_KEY + '-' + i, String.format(schedule, i));
        }
    }

    @Test
    public void testJobSchedulingDoesNotScheduleDisabledJobs() {
        this.config.setProperty(
            ConfigProperties.jobConfig(TestJob.JOB_KEY, ConfigProperties.ASYNC_JOBS_JOB_SCHEDULE),
            "5 * * * * ?");

        this.config.setProperty(
            ConfigProperties.jobConfig(TestJob.JOB_KEY, ConfigProperties.ASYNC_JOBS_JOB_ENABLED),
            "false");

        JobManager manager = this.createJobManager();
        manager.initialize();

        // Verify the job is not scheduled
        this.verifyNotScheduled(TestJob.JOB_KEY);
    }

    @Test
    public void testJobSchedulingDoesNotScheduleBlacklistedJobs() {
        this.config.setProperty(
            ConfigProperties.jobConfig(TestJob.JOB_KEY, ConfigProperties.ASYNC_JOBS_JOB_SCHEDULE),
            "5 * * * * ?");

        this.config.setProperty(ConfigProperties.ASYNC_JOBS_BLACKLIST, "a,b," + TestJob.JOB_KEY + ",d,e");

        JobManager manager = this.createJobManager();
        manager.initialize();

        // Verify the job is not scheduled
        this.verifyNotScheduled(TestJob.JOB_KEY);
    }

    @Test
    public void testJobSchedulingDoesNotScheduleJobsNotOnWhitelist() {
        this.config.setProperty(
            ConfigProperties.jobConfig(TestJob.JOB_KEY, ConfigProperties.ASYNC_JOBS_JOB_SCHEDULE),
            "5 * * * * ?");

        this.config.setProperty(ConfigProperties.ASYNC_JOBS_WHITELIST, "a,b,c,d,e");

        JobManager manager = this.createJobManager();
        manager.initialize();

        // Verify the job is not scheduled
        this.verifyNotScheduled(TestJob.JOB_KEY);
    }

    @Test
    public void verifyModeChangeSuspendsSchedulerOnSuspendMode() throws Exception {
        JobManager manager = this.createJobManager();
        manager.initialize();
        manager.start();

        manager.handleModeChange(this.modeManager, Mode.NORMAL, Mode.SUSPEND);

        verify(this.scheduler, times(1)).standby();
        verify(this.receiver, times(1)).suspend();
    }

    @Test
    public void verifyModeChangeResumesSchedulerOnNormalMode() throws Exception {
        JobManager manager = this.createJobManager();
        manager.initialize();

        reset(this.scheduler);
        reset(this.receiver);

        manager.handleModeChange(this.modeManager, Mode.SUSPEND, Mode.NORMAL);

        verify(this.scheduler, times(1)).start();
        verify(this.receiver, times(1)).start();
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
    public void testManagerCannotBeSuspendedBeforeInit() {
        JobManager manager = this.createJobManager();

        assertThrows(IllegalStateException.class, manager::suspend);
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
    public void testManagerCannotBeSuspendedAfterShutdown() {
        JobManager manager = this.createJobManager();
        manager.shutdown();

        assertThrows(IllegalStateException.class, manager::suspend);
    }

    @Test
    public void testManagerCannotBeResumedAfterShutdown() {
        JobManager manager = this.createJobManager();
        manager.shutdown();

        assertThrows(IllegalStateException.class, manager::resume);
    }

    @Test
    public void testManagerStartsInSuspendModeWhenCandlepinIsSuspended() {
        JobManager manager = this.createJobManager();

        doReturn(Mode.SUSPEND).when(this.modeManager).getCurrentMode();

        manager.initialize();
        manager.start();

        ManagerState state = manager.getManagerState();
        assertEquals(ManagerState.SUSPENDED, state);
    }

    @Test
    public void testJobIsQueuedIfConstraintsPass() throws Exception {
        Map<String, Object> ejobData1 = new HashMap<>();
        ejobData1.put("arg1", "val1");

        Map<String, Object> ejobData2 = new HashMap<>();
        ejobData2.put("arg1", "val2");

        AsyncJobStatus ejob1 = spy(new AsyncJobStatus()
            .setJobKey(TestJob.JOB_KEY)
            .setState(JobState.QUEUED)
            .setJobArguments(this.buildJobArguments(ejobData1)));

        AsyncJobStatus ejob2 = spy(new AsyncJobStatus()
            .setJobKey(TestJob.JOB_KEY)
            .setState(JobState.QUEUED)
            .setJobArguments(this.buildJobArguments(ejobData2)));

        JobConfig builder = JobConfig.forJob(TestJob.JOB_KEY)
            .addConstraint(JobConstraints.uniqueByArguments("arg1"))
            .setJobArgument("arg1", "val3");

        doReturn(Arrays.asList(ejob1, ejob2)).when(this.jobCurator).getNonTerminalJobs();

        JobManager manager = this.createJobManager();
        manager.initialize();
        manager.start();

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
            .setJobKey(TestJob.JOB_KEY)
            .setState(JobState.QUEUED)
            .setJobArguments(this.buildJobArguments(ejobData1)));

        AsyncJobStatus ejob2 = spy(new AsyncJobStatus()
            .setJobKey(TestJob.JOB_KEY)
            .setState(JobState.QUEUED)
            .setJobArguments(this.buildJobArguments(ejobData2)));

        JobConfig builder = JobConfig.forJob(TestJob.JOB_KEY)
            .addConstraint(JobConstraints.uniqueByArguments("arg1"))
            .setJobArgument("arg1", "val2");

        doReturn(Arrays.asList(ejob1, ejob2)).when(this.jobCurator).getNonTerminalJobs();

        JobManager manager = this.createJobManager();
        manager.initialize();
        manager.start();

        AsyncJobStatus result = manager.queueJob(builder);

        assertNotNull(result);
        assertEquals(JobState.ABORTED, result.getState());
    }

    @Test
    public void jobStatusFound() {
        String jobId = "jobId";
        Owner owner = new Owner("ownerKey", "owner");
        owner.setId("test_owner");

        JobManager manager = this.createJobManager();

        doReturn(new SystemPrincipal()).when(this.principalProvider).get();
        this.injectMockedJobStatus(this.createJobStatus(jobId, owner));

        AsyncJobStatus result = manager.findJob(jobId);

        assertNotNull(result);
        assertEquals(result.getId(), jobId);
    }

    @Test
    public void jobStatusFoundWithPermissions() {
        String jobId = "jobId";

        Owner owner = new Owner("ownerKey", "owner");
        owner.setId("test_owner");

        List<Permission> permissions = Collections
            .singletonList(new OwnerPermission(owner, Access.READ_ONLY));
        JobManager manager = this.createJobManager();

        doReturn(new UserPrincipal("user", permissions, false)).when(this.principalProvider).get();
        this.injectMockedJobStatus(this.createJobStatus(jobId, owner));

        AsyncJobStatus result = manager.findJob(jobId);

        assertNotNull(result);
        assertEquals(result.getId(), jobId);
    }

    @Test
    public void jobStatusNotFound() {
        String jobId = "jobId";
        JobManager manager = this.createJobManager();

        assertNull(manager.findJob(jobId));
    }

    @Test
    public void testFindJobs() {
        AsyncJobStatus status1 = this.createJobStatus("test_job-1");
        AsyncJobStatus status2 = this.createJobStatus("test_job-2");
        AsyncJobStatus status3 = this.createJobStatus("test_job-3");
        List<AsyncJobStatus> expected = Arrays.asList(status1, status2, status3);

        Set<String> jobKeys = Util.asSet("job_key-1", "job_key-2", "job_key-3");
        Set<JobState> jobStates = Util.asSet(JobState.QUEUED, JobState.RUNNING, JobState.FINISHED);
        Set<String> ownerIds = Util.asSet("owner-1", "owner-2", "owner-3");
        Date start = Util.yesterday();
        Date end = Util.tomorrow();

        AsyncJobStatusQueryBuilder input = new AsyncJobStatusQueryBuilder()
            .setJobKeys(jobKeys)
            .setJobStates(jobStates)
            .setOwnerIds(ownerIds)
            .setStartDate(start)
            .setEndDate(end);

        ArgumentCaptor<AsyncJobStatusQueryBuilder> captor =
            ArgumentCaptor.forClass(AsyncJobStatusQueryBuilder.class);

        doReturn(expected).when(this.jobCurator).findJobs(eq(input));

        JobManager manager = this.createJobManager();
        List<AsyncJobStatus> output = manager.findJobs(input);

        // Verify output is passed through properly
        assertNotNull(output);
        assertEquals(expected, output);

        // Verify input is passed through, unmodified
        verify(this.jobCurator, times(1)).findJobs(captor.capture());

        AsyncJobStatusQueryBuilder captured = captor.getValue();

        assertNotNull(captured);
        assertEquals(input, captured);
    }

    @Test
    public void testFindJobsHandlesNull() {
        AsyncJobStatus status1 = this.createJobStatus("test_job-1");
        AsyncJobStatus status2 = this.createJobStatus("test_job-2");
        AsyncJobStatus status3 = this.createJobStatus("test_job-3");
        List<AsyncJobStatus> expected = Arrays.asList(status1, status2, status3);

        ArgumentCaptor<AsyncJobStatusQueryBuilder> captor =
            ArgumentCaptor.forClass(AsyncJobStatusQueryBuilder.class);

        doReturn(expected).when(this.jobCurator).findJobs(eq(null));

        JobManager manager = this.createJobManager();
        List<AsyncJobStatus> output = manager.findJobs(null);

        // Verify output is passed through properly
        assertNotNull(output);
        assertEquals(expected, output);

        // Verify input is passed through, unmodified
        verify(this.jobCurator, times(1)).findJobs(captor.capture());

        AsyncJobStatusQueryBuilder captured = captor.getValue();
        assertNull(captured);
    }

    @Test
    public void testCleanupJobs() {
        Set<String> jobKeys = Util.asSet("job_key-1", "job_key-2", "job_key-3");
        Set<JobState> jobStates = Util.asSet(JobState.FINISHED, JobState.FAILED, JobState.CANCELED);
        Set<String> ownerIds = Util.asSet("owner-1", "owner-2", "owner-3");
        Date start = Util.yesterday();
        Date end = Util.tomorrow();

        AsyncJobStatusQueryBuilder input = new AsyncJobStatusQueryBuilder()
            .setJobKeys(jobKeys)
            .setJobStates(jobStates)
            .setOwnerIds(ownerIds)
            .setStartDate(start)
            .setEndDate(end);

        int expected = new Random().nextInt();

        ArgumentCaptor<AsyncJobStatusQueryBuilder> captor =
            ArgumentCaptor.forClass(AsyncJobStatusQueryBuilder.class);

        doReturn(expected).when(this.jobCurator).deleteJobs(eq(input));

        JobManager manager = this.createJobManager();
        int output = manager.cleanupJobs(input);

        // Verify output is passed through properly
        assertEquals(expected, output);

        // Verify input is passed through, unmodified
        verify(this.jobCurator, times(1)).deleteJobs(captor.capture());

        AsyncJobStatusQueryBuilder captured = captor.getValue();

        assertNotNull(captured);
        assertEquals(input, captured);
    }

    @Test
    public void testCleanupJobsLimitsJobStatesToTerminalStates() {
        Set<JobState> jobStates = Util.asSet(JobState.values());

        AsyncJobStatusQueryBuilder input = new AsyncJobStatusQueryBuilder()
            .setJobStates(jobStates);

        int expected = new Random().nextInt();

        ArgumentCaptor<AsyncJobStatusQueryBuilder> captor =
            ArgumentCaptor.forClass(AsyncJobStatusQueryBuilder.class);

        doReturn(expected).when(this.jobCurator).deleteJobs(eq(input));

        JobManager manager = this.createJobManager();
        int output = manager.cleanupJobs(input);

        verify(this.jobCurator, times(1)).deleteJobs(captor.capture());

        AsyncJobStatusQueryBuilder captured = captor.getValue();

        assertNotNull(captured);
        Collection<JobState> actual = captured.getJobStates();

        assertNotNull(actual);
        assertThat(actual, not(empty()));
        assertThat(actual.size(), lessThan(jobStates.size()));

        for (JobState state : actual) {
            assertNotNull(state);
            assertTrue(state.isTerminal());
        }
    }

    @Test
    public void testCleanupJobsDefaultsToTerminalStates() {
        ArgumentCaptor<AsyncJobStatusQueryBuilder> captor =
            ArgumentCaptor.forClass(AsyncJobStatusQueryBuilder.class);

        JobManager manager = this.createJobManager();
        int output = manager.cleanupJobs(null);

        verify(this.jobCurator, times(1)).deleteJobs(captor.capture());

        AsyncJobStatusQueryBuilder captured = captor.getValue();

        assertNotNull(captured);
        Collection<JobState> actual = captured.getJobStates();
        Set<JobState> expected = Arrays.stream(JobState.values())
            .filter(state -> state != null && state.isTerminal())
            .collect(Collectors.toSet());

        assertThat(expected, not(empty()));

        assertNotNull(actual);
        assertEquals(expected.size(), actual.size());

        for (JobState state : expected) {
            assertThat(actual, hasItem(state));
        }
    }

    @Test
    public void testCancelJob() {
        String jobId = "job_id";
        AsyncJobStatus expected = this.createJobStatus(jobId, null, JobState.QUEUED);
        this.injectMockedJobStatus(expected);

        JobManager manager = this.createJobManager();
        AsyncJobStatus output = manager.cancelJob(expected.getId());

        assertNotNull(output);
        assertEquals(expected.getId(), output.getId());
        assertEquals(JobState.CANCELED, output.getState());
    }

    @Test
    public void testCancelJobDisablesRetryForRunningJobs() {
        String jobId = "job_id";
        AsyncJobStatus expected = this.createJobStatus(jobId, null, JobState.RUNNING)
            .setMaxAttempts(5);

        this.injectMockedJobStatus(expected);

        JobManager manager = this.createJobManager();
        AsyncJobStatus output = manager.cancelJob(expected.getId());

        assertNotNull(output);
        assertEquals(expected.getId(), output.getId());
        assertEquals(JobState.RUNNING, output.getState());
        assertEquals(1, output.getMaxAttempts());
    }

    public static Stream<Arguments> terminalJobStatesProvider() {
        return Arrays.stream(JobState.values())
            .filter(state -> state != null && state.isTerminal())
            .map(state -> Arguments.of(state));
    }

    @ParameterizedTest
    @MethodSource("terminalJobStatesProvider")
    public void testCancelJobWontCancelTerminalJobs(JobState state) {
        assertNotNull(state);
        assertTrue(state.isTerminal());

        String jobId = "job_id";
        AsyncJobStatus expected = this.createJobStatus(jobId, null, state);
        this.injectMockedJobStatus(expected);

        JobManager manager = this.createJobManager();
        assertThrows(IllegalStateException.class, () -> manager.cancelJob(expected.getId()));
    }

    @Test
    public void testSchedulerCanBeDisabled() {
        this.config.setProperty(ConfigProperties.ASYNC_JOBS_SCHEDULER_ENABLED, "false");

        JobManager manager = this.createJobManager();

        // This should return false
        assertFalse(manager.isSchedulerEnabled());

        manager.initialize();
        manager.start();

        // Ensure the scheduler wasn't actually invoked
        verifyZeroInteractions(this.schedulerFactory);
        verifyZeroInteractions(this.scheduler);
    }

    @Test
    public void testSchedulerDefaultsEnabled() throws Exception {
        JobManager manager = this.createJobManager();

        assertTrue(manager.isSchedulerEnabled());

        manager.initialize();
        manager.start();

        // If mockito ever gets a way to verify the inverse of "zero interactions", do that here
        // instead of specifically looking for the start op.
        verify(this.scheduler, atLeastOnce()).start();
    }
}
