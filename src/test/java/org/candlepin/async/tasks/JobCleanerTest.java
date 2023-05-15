/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.async.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.async.JobManager;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.model.AsyncJobStatus.JobState;
import org.candlepin.model.AsyncJobStatusCurator.AsyncJobStatusQueryArguments;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@ExtendWith(MockitoExtension.class)
public class JobCleanerTest {

    private DevConfig config;
    private JobManager jobManager;

    @BeforeEach
    public void init() {
        this.jobManager = mock(JobManager.class);
        this.config = TestConfig.defaults();
    }

    private JobCleaner createJobInstance() {
        return new JobCleaner(this.config, this.jobManager);
    }

    private void setMaxAgeConfig(String cfgName, int maxAgeInMinutes) {
        String cfg = ConfigProperties.jobConfig(JobCleaner.JOB_KEY, cfgName);
        this.config.setProperty(cfg, String.valueOf(maxAgeInMinutes));
    }

    private long subtractMinutes(long baseTime, int minutes) {
        return baseTime - minutes * 60 * 1000;
    }

    private Set<JobState> getExpectedTerminalJobStates() {
        return Arrays.stream(JobState.values())
            .filter(JobState::isTerminal)
            .collect(Collectors.toSet());
    }

    private Set<JobState> getExpectedNonTerminalJobStates() {
        return Arrays.stream(JobState.values())
            .filter(state -> !state.isTerminal() && state != JobState.RUNNING)
            .collect(Collectors.toSet());
    }

    private Set<JobState> getExpectedRunningJobStates() {
        return Arrays.stream(JobState.values())
            .filter(state -> state == JobState.RUNNING)
            .collect(Collectors.toSet());
    }

    private static Stream<Arguments> maxAgeProvider() {
        return Stream.of(
            Arguments.of(10080),
            Arguments.of(4320),
            Arguments.of(2880),
            Arguments.of(60),
            Arguments.of(1));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @MethodSource("maxAgeProvider")
    public void testStandardExecution(int maxAge) throws JobExecutionException {
        this.setMaxAgeConfig(JobCleaner.CFG_MAX_TERMINAL_JOB_AGE, maxAge);
        this.setMaxAgeConfig(JobCleaner.CFG_MAX_NONTERMINAL_JOB_AGE, maxAge);
        this.setMaxAgeConfig(JobCleaner.CFG_MAX_RUNNING_JOB_AGE, maxAge);

        ArgumentCaptor<AsyncJobStatusQueryArguments> termCaptor =
            ArgumentCaptor.forClass(AsyncJobStatusQueryArguments.class);

        ArgumentCaptor<AsyncJobStatusQueryArguments> nontermCaptor =
            ArgumentCaptor.forClass(AsyncJobStatusQueryArguments.class);

        long minTime = this.subtractMinutes(System.currentTimeMillis(), maxAge);

        JobExecutionContext context = mock(JobExecutionContext.class);
        JobCleaner job = this.createJobInstance();
        ArgumentCaptor<Object> resultCaptor = ArgumentCaptor.forClass(Object.class);

        job.execute(context);

        verify(context, times(1)).setJobResult(resultCaptor.capture());
        Object result = resultCaptor.getValue();

        long maxTime = this.subtractMinutes(System.currentTimeMillis(), maxAge);
        assertNotNull(result);

        verify(this.jobManager, times(1)).cleanupJobs(termCaptor.capture());
        verify(this.jobManager, times(2)).abortNonTerminalJobs(nontermCaptor.capture());

        AsyncJobStatusQueryArguments termArgs = termCaptor.getValue();
        AsyncJobStatusQueryArguments nontermArgs = nontermCaptor.getAllValues().get(0);
        AsyncJobStatusQueryArguments runningArgs = nontermCaptor.getAllValues().get(1);

        this.verifyQueryArguments(termArgs, this.getExpectedTerminalJobStates(), minTime, maxTime);
        this.verifyQueryArguments(nontermArgs, this.getExpectedNonTerminalJobStates(), minTime, maxTime);
        this.verifyQueryArguments(runningArgs, this.getExpectedRunningJobStates(), minTime, maxTime);
    }

    private void verifyQueryArguments(AsyncJobStatusQueryArguments args, Set<JobState> expectedStates,
        long minTime, long maxTime) {

        assertNotNull(args);

        // The job cleaner is not job-specific nor owner-specific
        assertNull(args.getJobKeys());
        assertNull(args.getOwnerIds());

        // It should also not care about the origin, executor, or principals
        assertNull(args.getOrigins());
        assertNull(args.getExecutors());
        assertNull(args.getPrincipalNames());

        // Verify the states are as we expect
        Collection<JobState> states = args.getJobStates();

        assertNotNull(states);
        assertEquals(expectedStates.size(), states.size());

        for (JobState expectedState : expectedStates) {
            assertTrue(states.contains(expectedState));
        }

        // It should not define an "after" date limit
        assertNull(args.getStartDate());

        // The cutoff date should be defined as the "end" date/time:
        Date endTime = args.getEndDate();

        assertNotNull(endTime);

        // We allow a bit of leeway in the date to account for deltas in actual test
        // execution time
        assertTrue(endTime.getTime() >= minTime);
        assertTrue(endTime.getTime() <= maxTime);
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = { "0", "-50" })
    public void testBadTerminalJobAgeConfig(int maxAge) {
        this.setMaxAgeConfig(JobCleaner.CFG_MAX_TERMINAL_JOB_AGE, maxAge);
        this.setMaxAgeConfig(JobCleaner.CFG_MAX_NONTERMINAL_JOB_AGE, 60);
        this.setMaxAgeConfig(JobCleaner.CFG_MAX_RUNNING_JOB_AGE, 60);

        JobExecutionContext context = mock(JobExecutionContext.class);
        JobCleaner job = this.createJobInstance();
        assertThrows(JobExecutionException.class, () -> job.execute(context));
    }

    @ParameterizedTest(name = "{displayName} {index}: {0}")
    @ValueSource(strings = { "0", "-50" })
    public void testDisabledNonTerminalJobAgeConfig(int maxAge) throws Exception {
        this.setMaxAgeConfig(JobCleaner.CFG_MAX_TERMINAL_JOB_AGE, 60);
        this.setMaxAgeConfig(JobCleaner.CFG_MAX_NONTERMINAL_JOB_AGE, maxAge);
        this.setMaxAgeConfig(JobCleaner.CFG_MAX_RUNNING_JOB_AGE, maxAge);

        JobExecutionContext context = mock(JobExecutionContext.class);
        JobCleaner job = this.createJobInstance();
        ArgumentCaptor<Object> resultCaptor = ArgumentCaptor.forClass(Object.class);

        job.execute(context);

        verify(context, times(1)).setJobResult(resultCaptor.capture());
        Object result = resultCaptor.getValue();
        assertNotNull(result);

        verify(this.jobManager, times(1)).cleanupJobs(any(AsyncJobStatusQueryArguments.class));
        verify(this.jobManager, times(0)).abortNonTerminalJobs(any(AsyncJobStatusQueryArguments.class));
    }

}
