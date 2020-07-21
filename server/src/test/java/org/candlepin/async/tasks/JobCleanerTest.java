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
package org.candlepin.async.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.async.JobManager;
import org.candlepin.async.MaxJobAgeProvider;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.CandlepinCommonTestConfig;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.AsyncJobStatus.JobState;
import org.candlepin.model.AsyncJobStatusCurator.AsyncJobStatusQueryBuilder;

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



/**
 * JobCleanerTest
 */
@ExtendWith(MockitoExtension.class)
public class JobCleanerTest {

    private MaxJobAgeProvider maxJobAgeProvider;
    private Configuration config;
    private JobManager jobManager;

    @BeforeEach
    public void init() {
        this.jobManager = mock(JobManager.class);
        this.config = new CandlepinCommonTestConfig();
        this.maxJobAgeProvider = new MaxJobAgeProvider(config);
    }

    private JobCleaner createJobInstance() {
        return new JobCleaner(this.maxJobAgeProvider, this.jobManager);
    }

    private void setMaxAgeConfig(int maxAgeInMinutes) {
        String cfg = ConfigProperties.jobConfig(JobCleaner.JOB_KEY, MaxJobAgeProvider.CFG_MAX_JOB_AGE);
        this.config.setProperty(cfg, String.valueOf(maxAgeInMinutes));
    }

    private long subtractMinutes(long baseTime, int minutes) {
        return baseTime - minutes * 60 * 1000;
    }

    private Set<JobState> getExpectedJobStates() {
        return Arrays.stream(JobState.values())
            .filter(JobState::isTerminal)
            .collect(Collectors.toSet());
    }

    private static Stream<Arguments> maxAgeProvider() {
        return Stream.of(
            Arguments.of(MaxJobAgeProvider.CFG_DEFAULT_MAX_JOB_AGE),
            Arguments.of(60),
            Arguments.of(1));
    }

    @ParameterizedTest
    @MethodSource("maxAgeProvider")
    public void testStandardExecution(int maxAge) throws JobExecutionException {
        this.setMaxAgeConfig(maxAge);

        ArgumentCaptor<AsyncJobStatusQueryBuilder> captor =
            ArgumentCaptor.forClass(AsyncJobStatusQueryBuilder.class);

        long minTime = this.subtractMinutes(System.currentTimeMillis(), maxAge);

        JobExecutionContext context = mock(JobExecutionContext.class);
        JobCleaner job = this.createJobInstance();
        ArgumentCaptor<Object> resultCaptor = ArgumentCaptor.forClass(Object.class);

        job.execute(context);

        verify(context, times(1)).setJobResult(resultCaptor.capture());
        Object result = resultCaptor.getValue();

        long maxTime = this.subtractMinutes(System.currentTimeMillis(), maxAge);
        assertNotNull(result);

        verify(this.jobManager, times(1)).cleanupJobs(captor.capture());

        AsyncJobStatusQueryBuilder builder = captor.getValue();

        // The job cleaner is not job-specific nor owner-specific
        assertNull(builder.getJobKeys());
        assertNull(builder.getOwnerIds());

        // It should also not define an "after" date limit
        assertNull(builder.getStartDate());

        // Job states should be defined as all terminal states
        Set<JobState> expectedStates = this.getExpectedJobStates();
        Collection<JobState> states = builder.getJobStates();

        assertNotNull(states);
        assertEquals(expectedStates.size(), states.size());

        for (JobState expectedState : expectedStates) {
            assertTrue(states.contains(expectedState));
        }

        // The cutoff date should be defined as the "end" date/time:
        Date endTime = builder.getEndDate();

        assertNotNull(endTime);

        // We allow a bit of leeway in the date to account for deltas in actual test
        // execution time
        assertTrue(endTime.getTime() >= minTime && endTime.getTime() <= maxTime);
    }

    @ParameterizedTest
    @ValueSource(strings = { "0", "-50" })
    public void testBadAgeConfig(int maxAge) {
        this.setMaxAgeConfig(maxAge);

        JobExecutionContext context = mock(JobExecutionContext.class);
        JobCleaner job = this.createJobInstance();
        assertThrows(JobExecutionException.class, () -> job.execute(context));
    }



}
