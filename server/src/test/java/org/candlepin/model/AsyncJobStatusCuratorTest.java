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
package org.candlepin.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.model.AsyncJobStatus.JobState;
import org.candlepin.test.DatabaseTestFixture;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;


public class AsyncJobStatusCuratorTest extends DatabaseTestFixture {

    @Test
    public void testGetJobsInStateSingleState() {
        int perState = 3;

        this.createJobsInStates("test_job-", perState, JobState.values());
        List<AsyncJobStatus> result;

        int counter = 0;
        for (JobState state : JobState.values()) {
            result = this.asyncJobCurator.getJobsInState(state);

            assertNotNull(result);
            assertEquals(perState, result.size());

            for (AsyncJobStatus status : result) {
                assertEquals("test_job-" + ++counter, status.getName());
            }
        }
    }

    @Test
    public void testGetJobsInStateMultiState() {
        int perState = 3;
        int statesPerLoop = 2;

        this.createJobsInStates("test_job-", perState, JobState.values());
        List<AsyncJobStatus> result;

        int counter = 0;
        JobState[] states = JobState.values();

        int extra = states.length % statesPerLoop;
        int max = states.length - extra;

        for (int i = 0; i < max; i += statesPerLoop) {
            JobState[] group = Arrays.copyOfRange(states, i, i + statesPerLoop);
            result = this.asyncJobCurator.getJobsInState(group);

            assertNotNull(result);
            assertEquals(perState * statesPerLoop, result.size());

            for (AsyncJobStatus status : result) {
                assertEquals("test_job-" + ++counter, status.getName());
            }
        }
    }

    @Test
    public void testGetJobsInStateSingleStateAsCollection() {
        int perState = 3;

        this.createJobsInStates("test_job-", perState, JobState.values());
        List<AsyncJobStatus> result;

        int counter = 0;
        for (JobState state : JobState.values()) {
            result = this.asyncJobCurator.getJobsInState(Arrays.asList(state));

            assertNotNull(result);
            assertEquals(perState, result.size());

            for (AsyncJobStatus status : result) {
                assertEquals("test_job-" + ++counter, status.getName());
            }
        }
    }

    @Test
    public void testGetJobsInStateMultiStateAsCollection() {
        int perState = 3;
        int statesPerLoop = 2;

        this.createJobsInStates("test_job-", perState, JobState.values());
        List<AsyncJobStatus> result;

        int counter = 0;
        JobState[] states = JobState.values();

        int extra = states.length % statesPerLoop;
        int max = states.length - extra;

        for (int i = 0; i < max; i += statesPerLoop) {
            JobState[] group = Arrays.copyOfRange(states, i, i + statesPerLoop);
            result = this.asyncJobCurator.getJobsInState(Arrays.asList(group));

            assertNotNull(result);
            assertEquals(perState * statesPerLoop, result.size());

            for (AsyncJobStatus status : result) {
                assertEquals("test_job-" + ++counter, status.getName());
            }
        }
    }

    @Test
    public void testGetJobsInNonTerminalStates() {
        int perState = 3;

        this.createJobsInStates("test_job-", perState, JobState.values());

        List<JobState> nonTerminals = new LinkedList<>();

        for (JobState state : JobState.values()) {
            if (!state.isTerminal()) {
                nonTerminals.add(state);
            }
        }

        List<AsyncJobStatus> result = this.asyncJobCurator.getNonTerminalJobs();

        assertNotNull(result);
        assertEquals(perState * nonTerminals.size(), result.size());

        for (AsyncJobStatus status : result) {
            assertTrue(nonTerminals.contains(status.getState()));
        }
    }

    private void createJobsInStates(String namePrefix, int perState, JobState... states) {
        int counter = 0;

        for (JobState state : states) {
            for (int i = 0; i < perState; ++i) {
                AsyncJobStatus job = new AsyncJobStatus();

                job.setName(namePrefix + ++counter);
                job.setState(state);

                this.asyncJobCurator.create(job);
            }
        }

        this.asyncJobCurator.flush();
    }

}
