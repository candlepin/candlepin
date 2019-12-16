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

import org.candlepin.model.AsyncJobStatus.JobState;
import org.candlepin.model.AsyncJobStatusCurator.AsyncJobStatusQueryBuilder;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.util.Util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class AsyncJobStatusCuratorTest extends DatabaseTestFixture {

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

    private boolean setLastUpdatedTime(AsyncJobStatus job, Date lastUpdated) {
        String jpql = "UPDATE AsyncJobStatus SET updated = :date WHERE id = :id";

        int count = this.getEntityManager()
            .createQuery(jpql)
            .setParameter("date", lastUpdated)
            .setParameter("id", job.getId())
            .executeUpdate();

        return count > 0;
    }

    private AsyncJobStatus createJob(String name, String key, JobState state, Owner owner, String principal,
        String origin, String executor, Object result, Date startTime, Date endTime, Date lastUpdated) {

        AsyncJobStatus job = new AsyncJobStatus()
            .setName(name)
            .setJobKey(key)
            .setState(state)
            .setContextOwner(owner)
            .setPrincipalName(principal)
            .setOrigin(origin)
            .setExecutor(executor)
            .setJobResult(result)
            .setStartTime(startTime)
            .setEndTime(endTime);

        job = this.asyncJobCurator.create(job);

        if (lastUpdated != null) {
            assertTrue(this.setLastUpdatedTime(job, lastUpdated));
            this.asyncJobCurator.refresh(job);
        }

        this.asyncJobCurator.flush();

        return job;
    }

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


    /**
     * Creates a bunch of jobs for the given owners. Used primarily for the tests of the findJobs
     * method.
     */
    private List<AsyncJobStatus> createJobsForQueryTests(Collection<String> keys, Collection<JobState> states,
        Collection<Owner> owners, Collection<String> principals, Collection<String> origins,
        Collection<String> executors) {

        List<AsyncJobStatus> created = new LinkedList<>();
        int count = 0;

        Date now = new Date();
        Date d2da = Util.addDaysToDt(-2);
        Date d1da = Util.addDaysToDt(-1);
        Date d1dl = Util.addDaysToDt(1);
        Date d2dl = Util.addDaysToDt(2);

        if (keys == null) {
            keys = Arrays.asList("job_key");
        }

        if (states == null) {
            states = Arrays.asList(JobState.values());
        }

        if (owners == null) {
            owners = Arrays.asList((Owner) null);
        }

        if (principals == null) {
            principals = Arrays.asList((String) null);
        }

        if (origins == null) {
            origins = Arrays.asList((String) null);
        }

        if (executors == null) {
            executors = Arrays.asList((String) null);
        }

        for (String key : keys) {
            for (JobState state : states) {
                for (Owner owner : owners) {
                    for (String principal : principals) {
                        for (String origin : origins) {
                            for (String executor : executors) {
                                created.add(this.createJob("Job-" + ++count, key, state, owner, principal,
                                    origin, executor, null, null, null, now));

                                created.add(this.createJob("Job-" + ++count, key, state, owner, principal,
                                    origin, executor, null, d2da, null, d2da));

                                created.add(this.createJob("Job-" + ++count, key, state, owner, principal,
                                    origin, executor, null, d2da, d1da, d1da));

                                created.add(this.createJob("Job-" + ++count, key, state, owner, principal,
                                    origin, executor, null, d1dl, null, d1dl));

                                created.add(this.createJob("Job-" + ++count, key, state, owner, principal,
                                    origin, executor, null, d1dl, d2dl, d2dl));

                                created.add(this.createJob("Job-" + ++count, key, state, owner, principal,
                                    origin, executor, null, d1da, d1dl, d1dl));
                            }
                        }
                    }
                }
            }
        }

        return created;
    }

    public static Stream<Arguments> jobQueryGenericStringInputProvider() {
        List<String> create = Arrays.asList("value-1", "value-2", "value-3");

        return Stream.of(
            Arguments.of(create, Arrays.asList("value-1")),
            Arguments.of(create, Arrays.asList("value-1", "value-2")),
            Arguments.of(create, Arrays.asList("value-1", "value-2", "value-4", "value-6")),
            Arguments.of(create, Arrays.asList("value-4", "value-6")),
            Arguments.of(create, Arrays.asList((String) null)),
            Arguments.of(create, Arrays.asList("")));
    }

    @ParameterizedTest
    @ValueSource(strings = { "1", "3" })
    public void testFindJobsByJobIds(int inputSize) {
        List<String> keys = Arrays.asList("job_key-1", "job_key-2", "job_key-3");
        List<JobState> states = Arrays.asList(JobState.values());
        List<Owner> owners = Arrays.asList(null, this.createOwner(), this.createOwner());

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        List<String> jobIds = new Random().ints(Math.min(inputSize, created.size()), 0, created.size())
            .mapToObj(i -> created.get(i).getId())
            .collect(Collectors.toList());

        long expected = jobIds.size();

        assertTrue(expected > 0);

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder()
            .setJobIds(jobIds);

        List<AsyncJobStatus> fetched = this.asyncJobCurator.findJobs(queryBuilder);

        assertNotNull(fetched);
        assertEquals(expected, fetched.size());

        for (AsyncJobStatus job : fetched) {
            assertTrue(jobIds.contains(job.getId()));
        }
    }

    @Test
    public void testFindJobsByJobIdsWithExtraneous() {
        List<String> keys = Arrays.asList("job_key-1", "job_key-2", "job_key-3");
        List<JobState> states = Arrays.asList(JobState.values());
        List<Owner> owners = Arrays.asList(null, this.createOwner(), this.createOwner());

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        List<String> expectedJobIds = new Random().ints(Math.min(3, created.size()), 0, created.size())
            .mapToObj(i -> created.get(i).getId())
            .collect(Collectors.toList());

        long expected = expectedJobIds.size();
        assertTrue(expected > 0);

        List<String> extraneousJobIds = Arrays.asList("extra_id-1", "extra_id-2", "extra_id-3");

        List<String> jobIds = new LinkedList<>();
        jobIds.addAll(expectedJobIds);
        jobIds.addAll(extraneousJobIds);

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder()
            .setJobIds(jobIds);

        List<AsyncJobStatus> fetched = this.asyncJobCurator.findJobs(queryBuilder);

        assertNotNull(fetched);
        assertEquals(expected, fetched.size());

        for (AsyncJobStatus job : fetched) {
            assertTrue(expectedJobIds.contains(job.getId()));
            assertFalse(extraneousJobIds.contains(job.getId()));
        }
    }

    @Test
    public void testFindJobsBySingleKey() {
        List<String> keys = Arrays.asList("job_key-1", "job_key-2", "job_key-3");
        List<JobState> states = Arrays.asList(JobState.values());
        List<Owner> owners = Arrays.asList(null, this.createOwner(), this.createOwner());

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        List<String> jobKeys = Arrays.asList("job_key-1");

        long expected = created.stream()
            .filter(job -> jobKeys.contains(job.getJobKey()))
            .count();

        assertTrue(expected > 0);

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder()
            .setJobKeys(jobKeys);

        List<AsyncJobStatus> fetched = this.asyncJobCurator.findJobs(queryBuilder);

        assertNotNull(fetched);
        assertEquals(expected, fetched.size());

        for (AsyncJobStatus job : fetched) {
            assertTrue(jobKeys.contains(job.getJobKey()));
        }
    }

    @Test
    public void testFindJobsByMultipleKeys() {
        List<String> keys = Arrays.asList("job_key-1", "job_key-2", "job_key-3");
        List<JobState> states = Arrays.asList(JobState.values());
        List<Owner> owners = Arrays.asList(null, this.createOwner(), this.createOwner());

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        List<String> jobKeys = Arrays.asList("job_key-1", "job_key-3");

        long expected = created.stream()
            .filter(job -> jobKeys.contains(job.getJobKey()))
            .count();

        assertTrue(expected > 0);

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder();
        queryBuilder.setJobKeys(jobKeys);

        List<AsyncJobStatus> fetched = this.asyncJobCurator.findJobs(queryBuilder);

        assertNotNull(fetched);
        assertEquals(expected, fetched.size());

        for (AsyncJobStatus job : fetched) {
            assertTrue(jobKeys.contains(job.getJobKey()));
        }
    }

    @Test
    public void testFindJobsByMultipleKeysWithExtraneousKeys() {
        List<String> keys = Arrays.asList("job_key-1", "job_key-2", "job_key-3");
        List<JobState> states = Arrays.asList(JobState.values());
        List<Owner> owners = Arrays.asList(null, this.createOwner(), this.createOwner());

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        List<String> expectedKeys = Arrays.asList("job_key-1", "job_key-3");
        List<String> extraneousKeys = Arrays.asList("job_key-4", "job_key-6");

        long expected = created.stream()
            .filter(job -> expectedKeys.contains(job.getJobKey()))
            .count();

        assertTrue(expected > 0);

        List<String> jobKeys = new LinkedList<>();
        jobKeys.addAll(expectedKeys);
        jobKeys.addAll(extraneousKeys);

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder();
        queryBuilder.setJobKeys(jobKeys);

        List<AsyncJobStatus> fetched = this.asyncJobCurator.findJobs(queryBuilder);

        assertNotNull(fetched);
        assertEquals(expected, fetched.size());

        for (AsyncJobStatus job : fetched) {
            assertTrue(jobKeys.contains(job.getJobKey()));
            assertFalse(extraneousKeys.contains(job.getJobKey()));
        }
    }

    @Test
    public void testFindJobsBySingleJobState() {
        List<String> keys = Arrays.asList("job_key-1", "job_key-2");
        List<JobState> states = Arrays.asList(JobState.values());
        List<Owner> owners = Arrays.asList(null, this.createOwner(), this.createOwner());

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        List<JobState> jobStates = Arrays.asList(JobState.RUNNING);

        long expected = created.stream()
            .filter(job -> jobStates.contains(job.getState()))
            .count();

        assertTrue(expected > 0);

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder();
        queryBuilder.setJobStates(jobStates);

        List<AsyncJobStatus> fetched = this.asyncJobCurator.findJobs(queryBuilder);

        assertNotNull(fetched);
        assertEquals(expected, fetched.size());

        for (AsyncJobStatus job : fetched) {
            assertTrue(jobStates.contains(job.getState()));
        }
    }

    @Test
    public void testFindJobsByMultipleJobStates() {
        List<String> keys = Arrays.asList("job_key-1", "job_key-2");
        List<JobState> states = Arrays.asList(JobState.values());
        List<Owner> owners = Arrays.asList(null, this.createOwner(), this.createOwner());

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        List<JobState> jobStates = Arrays.asList(JobState.QUEUED, JobState.FINISHED);

        long expected = created.stream()
            .filter(job -> jobStates.contains(job.getState()))
            .count();

        assertTrue(expected > 0);

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder();
        queryBuilder.setJobStates(jobStates);

        List<AsyncJobStatus> fetched = this.asyncJobCurator.findJobs(queryBuilder);

        assertNotNull(fetched);
        assertEquals(expected, fetched.size());

        for (AsyncJobStatus job : fetched) {
            assertTrue(jobStates.contains(job.getState()));
        }
    }

    @Test
    public void testFindJobsByMultipleJobStatesWithExtraneousStates() {
        List<String> keys = Arrays.asList("job_key-1", "job_key-2");
        List<JobState> states = Arrays.asList(JobState.QUEUED, JobState.RUNNING, JobState.FINISHED);
        List<Owner> owners = Arrays.asList(null, this.createOwner(), this.createOwner());

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        List<JobState> expectedStates = Arrays.asList(JobState.QUEUED, JobState.FINISHED);
        List<JobState> extraneousStates = Arrays.asList(JobState.FAILED, JobState.ABORTED);

        long expected = created.stream()
            .filter(job -> expectedStates.contains(job.getState()))
            .count();

        assertTrue(expected > 0);

        List<JobState> jobStates = new LinkedList<>();
        jobStates.addAll(expectedStates);
        jobStates.addAll(extraneousStates);

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder();
        queryBuilder.setJobStates(jobStates);

        List<AsyncJobStatus> fetched = this.asyncJobCurator.findJobs(queryBuilder);

        assertNotNull(fetched);
        assertEquals(expected, fetched.size());

        for (AsyncJobStatus job : fetched) {
            assertTrue(expectedStates.contains(job.getState()));
            assertFalse(extraneousStates.contains(job.getState()));
        }
    }

    @Test
    public void testFindJobsBySingleOwnerId() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();

        List<String> keys = Arrays.asList("job_key-1", "job_key-2");
        List<JobState> states = Arrays.asList(JobState.values());
        List<Owner> owners = Arrays.asList(null, owner1, owner2, owner3);

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        List<String> ownerIds = Arrays.asList(owner1.getId());

        long expected = created.stream()
            .filter(j -> j.getContextOwner() != null && ownerIds.contains(j.getContextOwner().getId()))
            .count();

        assertTrue(expected > 0);

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder();
        queryBuilder.setOwnerIds(ownerIds);

        List<AsyncJobStatus> fetched = this.asyncJobCurator.findJobs(queryBuilder);

        assertNotNull(fetched);
        assertEquals(expected, fetched.size());

        for (AsyncJobStatus job : fetched) {
            assertNotNull(job.getContextOwner());
            assertTrue(ownerIds.contains(job.getContextOwner().getId()));
        }
    }

    @Test
    public void testFindJobsByMultipleOwnerIds() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();

        List<String> keys = Arrays.asList("job_key-1", "job_key-2");
        List<JobState> states = Arrays.asList(JobState.values());
        List<Owner> owners = Arrays.asList(null, owner1, owner2, owner3);

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        List<String> ownerIds = Arrays.asList(owner1.getId(), owner3.getId());

        long expected = created.stream()
            .filter(j -> j.getContextOwner() != null && ownerIds.contains(j.getContextOwner().getId()))
            .count();

        assertTrue(expected > 0);

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder();
        queryBuilder.setOwnerIds(ownerIds);

        List<AsyncJobStatus> fetched = this.asyncJobCurator.findJobs(queryBuilder);

        assertNotNull(fetched);
        assertEquals(expected, fetched.size());

        for (AsyncJobStatus job : fetched) {
            assertNotNull(job.getContextOwner());
            assertTrue(ownerIds.contains(job.getContextOwner().getId()));
        }
    }

    @Test
    public void testFindJobsByMultipleOwnerIdsWithExtraneousKeys() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();

        List<String> keys = Arrays.asList("job_key-1", "job_key-2");
        List<JobState> states = Arrays.asList(JobState.values());
        List<Owner> owners = Arrays.asList(null, owner1, owner2, owner3);

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        List<String> expectedKeys = Arrays.asList(owner1.getId(), owner3.getId());
        List<String> extraneousKeys = Arrays.asList("extra_key-1", "extra_key-2");

        long expected = created.stream()
            .filter(j -> j.getContextOwner() != null && expectedKeys.contains(j.getContextOwner().getId()))
            .count();

        assertTrue(expected > 0);

        List<String> ownerIds = new LinkedList<>();
        ownerIds.addAll(expectedKeys);
        ownerIds.addAll(extraneousKeys);

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder();
        queryBuilder.setOwnerIds(ownerIds);

        List<AsyncJobStatus> fetched = this.asyncJobCurator.findJobs(queryBuilder);

        assertNotNull(fetched);
        assertEquals(expected, fetched.size());

        for (AsyncJobStatus job : fetched) {
            assertNotNull(job.getContextOwner());

            assertTrue(expectedKeys.contains(job.getContextOwner().getId()));
            assertFalse(extraneousKeys.contains(job.getContextOwner().getId()));
        }
    }

    @Test
    public void testFindJobsByNullOwnerId() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();

        List<String> keys = Arrays.asList("job_key-1", "job_key-2");
        List<JobState> states = Arrays.asList(JobState.values());
        List<Owner> owners = Arrays.asList(null, owner1, owner2, owner3);

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        List<String> ownerIds = new LinkedList<>();
        ownerIds.add(null);

        long expected = created.stream()
            .filter(job -> job.getContextOwner() == null)
            .count();

        assertTrue(expected > 0);

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder();
        queryBuilder.setOwnerIds(ownerIds);

        List<AsyncJobStatus> fetched = this.asyncJobCurator.findJobs(queryBuilder);

        assertNotNull(fetched);
        assertEquals(expected, fetched.size());

        for (AsyncJobStatus job : fetched) {
            assertNull(job.getContextOwner());
        }
    }

    @Test
    public void testFindJobsByMixedOwnerIds() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();

        List<String> keys = Arrays.asList("job_key-1", "job_key-2");
        List<JobState> states = Arrays.asList(JobState.values());
        List<Owner> owners = Arrays.asList(null, owner1, owner2, owner3);

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        List<String> ownerIds = new LinkedList<>();
        ownerIds.add(null);
        ownerIds.add(owner2.getId());

        long expected = created.stream()
            .filter(j -> j.getContextOwner() == null || ownerIds.contains(j.getContextOwner().getId()))
            .count();

        assertTrue(expected > 0);

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder();
        queryBuilder.setOwnerIds(ownerIds);

        List<AsyncJobStatus> fetched = this.asyncJobCurator.findJobs(queryBuilder);

        assertNotNull(fetched);
        assertEquals(expected, fetched.size());

        for (AsyncJobStatus job : fetched) {
            assertTrue(job.getContextOwner() == null || ownerIds.contains(job.getContextOwner().getId()));
        }
    }

    @Test
    public void testFindJobsByLastUpdateTimeBeforeDate() {
        List<String> keys = Arrays.asList("job_key-1", "job_key-2");
        List<JobState> states = Arrays.asList(JobState.values());
        List<Owner> owners = Arrays.asList(null, this.createOwner(), this.createOwner());

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        Date now = new Date();

        long expected = created.stream()
            .filter(job -> now.compareTo(job.getUpdated()) >= 0)
            .count();

        assertTrue(expected > 0);

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder()
            .setEndDate(now);

        List<AsyncJobStatus> fetched = this.asyncJobCurator.findJobs(queryBuilder);

        assertNotNull(fetched);
        assertEquals(expected, fetched.size());

        for (AsyncJobStatus job : fetched) {
            assertTrue(now.compareTo(job.getUpdated()) >= 0);
        }
    }

    @Test
    public void testFindJobsByLastUpdateTimeAfterDate() {
        List<String> keys = Arrays.asList("job_key-1", "job_key-2");
        List<JobState> states = Arrays.asList(JobState.values());
        List<Owner> owners = Arrays.asList(null, this.createOwner(), this.createOwner());

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        Date now = new Date();

        long expected = created.stream()
            .filter(job -> now.compareTo(job.getUpdated()) <= 0)
            .count();

        assertTrue(expected > 0);

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder()
            .setStartDate(now);

        List<AsyncJobStatus> fetched = this.asyncJobCurator.findJobs(queryBuilder);

        assertNotNull(fetched);
        assertEquals(expected, fetched.size());

        for (AsyncJobStatus job : fetched) {
            assertTrue(now.compareTo(job.getUpdated()) <= 0);
        }
    }

    @Test
    public void testFindJobsByLastUpdateTimeInRange() {
        List<String> keys = Arrays.asList("job_key-1", "job_key-2");
        List<JobState> states = Arrays.asList(JobState.values());
        List<Owner> owners = Arrays.asList(null, this.createOwner(), this.createOwner());

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        Date start = Util.addDaysToDt(-1);
        Date end = Util.addDaysToDt(1);

        long expected = created.stream()
            .filter(job -> start.compareTo(job.getUpdated()) <= 0 && end.compareTo(job.getUpdated()) >= 0)
            .count();

        assertTrue(expected > 0);

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder()
            .setStartDate(start)
            .setEndDate(end);

        List<AsyncJobStatus> fetched = this.asyncJobCurator.findJobs(queryBuilder);

        assertNotNull(fetched);
        assertEquals(expected, fetched.size());

        for (AsyncJobStatus job : fetched) {
            assertTrue(start.compareTo(job.getUpdated()) <= 0);
            assertTrue(end.compareTo(job.getUpdated()) >= 0);
        }
    }

    @ParameterizedTest
    @MethodSource("jobQueryGenericStringInputProvider")
    public void testFindJobsByPrincipal(Collection<String> create, Collection<String> query) {
        List<String> keys = Arrays.asList("job_key-1", "job_key-2", "job_key-3");
        List<JobState> states = Arrays.asList(JobState.QUEUED, JobState.RUNNING, JobState.FINISHED);
        List<Owner> owners = Arrays.asList(null, this.createOwner(), this.createOwner());

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, create, null, null);

        List<String> expected = new LinkedList<>(query);
        expected.retainAll(create);

        long expectedCount = created.stream()
            .filter(job -> expected.contains(job.getPrincipalName()))
            .count();

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder()
            .setPrincipalNames(query);

        List<AsyncJobStatus> fetched = this.asyncJobCurator.findJobs(queryBuilder);

        assertNotNull(fetched);
        assertEquals(expectedCount, fetched.size());

        for (AsyncJobStatus job : fetched) {
            assertTrue(expected.contains(job.getPrincipalName()));
        }
    }

    @ParameterizedTest
    @MethodSource("jobQueryGenericStringInputProvider")
    public void testFindJobsByOrigin(Collection<String> create, Collection<String> query) {
        List<String> keys = Arrays.asList("job_key-1", "job_key-2", "job_key-3");
        List<JobState> states = Arrays.asList(JobState.QUEUED, JobState.RUNNING, JobState.FINISHED);
        List<Owner> owners = Arrays.asList(null, this.createOwner(), this.createOwner());

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, create, null);

        List<String> expected = new LinkedList<>(query);
        expected.retainAll(create);

        long expectedCount = created.stream()
            .filter(job -> expected.contains(job.getOrigin()))
            .count();

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder()
            .setOrigins(query);

        List<AsyncJobStatus> fetched = this.asyncJobCurator.findJobs(queryBuilder);

        assertNotNull(fetched);
        assertEquals(expectedCount, fetched.size());

        for (AsyncJobStatus job : fetched) {
            assertTrue(expected.contains(job.getOrigin()));
        }
    }

    @ParameterizedTest
    @MethodSource("jobQueryGenericStringInputProvider")
    public void testFindJobsByExecutor(Collection<String> create, Collection<String> query) {
        List<String> keys = Arrays.asList("job_key-1", "job_key-2", "job_key-3");
        List<JobState> states = Arrays.asList(JobState.QUEUED, JobState.RUNNING, JobState.FINISHED);
        List<Owner> owners = Arrays.asList(null, this.createOwner(), this.createOwner());

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, create);

        List<String> expected = new LinkedList<>(query);
        expected.retainAll(create);

        long expectedCount = created.stream()
            .filter(job -> expected.contains(job.getExecutor()))
            .count();

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder()
            .setExecutors(query);

        List<AsyncJobStatus> fetched = this.asyncJobCurator.findJobs(queryBuilder);

        assertNotNull(fetched);
        assertEquals(expectedCount, fetched.size());

        for (AsyncJobStatus job : fetched) {
            assertTrue(expected.contains(job.getExecutor()));
        }
    }

    @Test
    public void testFindJobsByMultipleParameters() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        List<String> keys = Arrays.asList("job_key-1", "job_key-2", "job_key-3");
        List<Owner> owners = Arrays.asList(null, owner1, owner2);
        List<JobState> states = Arrays.asList(JobState.QUEUED, JobState.RUNNING,
            JobState.FAILED, JobState.FINISHED);
        List<String> principals = Arrays.asList("principal-1", "principal-2", "principal-3");
        List<String> origins = Arrays.asList("origin-1", "origin-2", "origin-3");
        List<String> executors = Arrays.asList("executor-1", "executor-2", "executor-3");

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners,
            principals, origins, executors);

        List<String> jobKeys = Arrays.asList("job_key-1");
        List<JobState> jobStates = Arrays.asList(JobState.QUEUED, JobState.RUNNING);
        List<String> ownerIds = Arrays.asList(owner2.getId());
        List<String> jobPrincipals = Arrays.asList("principal-2");
        List<String> jobOrigins = Arrays.asList("origin-3");
        List<String> jobExecutors = Arrays.asList("executor-1");
        Date start = Util.addDaysToDt(-1);
        Date end = Util.addDaysToDt(1);

        long expected = created.stream()
            .filter(job -> jobKeys.contains(job.getJobKey()))
            .filter(job -> jobStates.contains(job.getState()))
            .filter(j   -> j.getContextOwner() != null && ownerIds.contains(j.getContextOwner().getId()))
            .filter(job -> start.compareTo(job.getUpdated()) <= 0 && end.compareTo(job.getUpdated()) >= 0)
            .filter(job -> jobPrincipals.contains(job.getPrincipalName()))
            .filter(job -> jobOrigins.contains(job.getOrigin()))
            .filter(job -> jobExecutors.contains(job.getExecutor()))
            .count();

        assertTrue(expected > 0);

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder()
            .setJobKeys(jobKeys)
            .setJobStates(jobStates)
            .setOwnerIds(ownerIds)
            .setPrincipalNames(jobPrincipals)
            .setOrigins(jobOrigins)
            .setExecutors(jobExecutors)
            .setStartDate(start)
            .setEndDate(end);

        List<AsyncJobStatus> fetched = this.asyncJobCurator.findJobs(queryBuilder);

        assertNotNull(fetched);
        assertEquals(expected, fetched.size());

        for (AsyncJobStatus job : fetched) {
            assertTrue(jobKeys.contains(job.getJobKey()));
            assertTrue(jobStates.contains(job.getState()));
            assertTrue(job.getContextOwner() != null && ownerIds.contains(job.getContextOwner().getId()));
            assertTrue(start.compareTo(job.getUpdated()) <= 0 && end.compareTo(job.getUpdated()) >= 0);
            assertTrue(jobPrincipals.contains(job.getPrincipalName()));
            assertTrue(jobOrigins.contains(job.getOrigin()));
            assertTrue(jobExecutors.contains(job.getExecutor()));
        }
    }

    @Test
    public void testFindJobsWithoutParameters() {
        List<String> keys = Arrays.asList("job_key-1", "job_key-2");
        List<JobState> states = Arrays.asList(JobState.values());
        List<Owner> owners = Arrays.asList(null, this.createOwner(), this.createOwner());

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder();
        List<AsyncJobStatus> fetched = this.asyncJobCurator.findJobs(queryBuilder);

        assertNotNull(fetched);
        assertEquals(created.size(), fetched.size());

        for (AsyncJobStatus job : fetched) {
            assertTrue(created.contains(job));
        }
    }

    @Test
    public void testFindJobsWithNullBuilder() {
        List<String> keys = Arrays.asList("job_key-1", "job_key-2");
        List<JobState> states = Arrays.asList(JobState.values());
        List<Owner> owners = Arrays.asList(null, this.createOwner(), this.createOwner());

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        List<AsyncJobStatus> fetched = this.asyncJobCurator.findJobs(null);

        assertNotNull(fetched);
        assertEquals(created.size(), fetched.size());

        for (AsyncJobStatus job : fetched) {
            assertTrue(created.contains(job));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "1", "3" })
    public void testDeleteJobsByJobIds(int count) {
        List<String> keys = Arrays.asList("job_key-1", "job_key-2", "job_key-3");
        List<JobState> states = Arrays.asList(JobState.values());
        List<Owner> owners = Arrays.asList(null, this.createOwner(), this.createOwner());

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        List<AsyncJobStatus> expected = new Random().ints(Math.min(count, created.size()), 0, created.size())
            .mapToObj(i -> created.get(i))
            .collect(Collectors.toList());

        List<String> jobIds = expected.stream()
            .map(job -> job.getId())
            .collect(Collectors.toList());

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder()
            .setJobIds(jobIds);

        int deleted = this.asyncJobCurator.deleteJobs(queryBuilder);
        assertEquals(expected.size(), deleted);

        List<AsyncJobStatus> remaining = this.asyncJobCurator.listAll().list();
        assertEquals(created.size() - expected.size(), remaining.size());

        for (AsyncJobStatus job : remaining) {
            assertTrue(created.contains(job));
            assertFalse(expected.contains(job));
        }
    }

    @Test
    public void testDeleteJobsByIdsWithExtraneous() {
        List<String> keys = Arrays.asList("job_key-1", "job_key-2", "job_key-3");
        List<JobState> states = Arrays.asList(JobState.values());
        List<Owner> owners = Arrays.asList(null, this.createOwner(), this.createOwner());

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        List<AsyncJobStatus> expected = new Random().ints(Math.min(3, created.size()), 0, created.size())
            .mapToObj(i -> created.get(i))
            .collect(Collectors.toList());

        List<String> expectedJobIds = expected.stream()
            .map(job -> job.getId())
            .collect(Collectors.toList());

        List<String> extraneousJobIds = Arrays.asList("extra_id-1", "extra_id-2", "extra_id-3");

        List<String> jobIds = new LinkedList<>();
        jobIds.addAll(expectedJobIds);
        jobIds.addAll(extraneousJobIds);

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder()
            .setJobIds(jobIds);

        int deleted = this.asyncJobCurator.deleteJobs(queryBuilder);
        assertEquals(expected.size(), deleted);

        List<AsyncJobStatus> remaining = this.asyncJobCurator.listAll().list();
        assertEquals(created.size() - expected.size(), remaining.size());

        for (AsyncJobStatus job : remaining) {
            assertTrue(created.contains(job));
            assertFalse(expected.contains(job));
        }
    }

    @Test
    public void testDeleteJobsBySingleKey() {
        List<String> keys = Arrays.asList("job_key-1", "job_key-2", "job_key-3");
        List<JobState> states = Arrays.asList(JobState.values());
        List<Owner> owners = Arrays.asList(null, this.createOwner(), this.createOwner());

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        List<String> jobKeys = Arrays.asList("job_key-1");

        List<AsyncJobStatus> expected = created.stream()
            .filter(job -> jobKeys.contains(job.getJobKey()))
            .collect(Collectors.toList());

        assertTrue(expected.size() > 0);

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder();
        queryBuilder.setJobKeys(jobKeys);

        int deleted = this.asyncJobCurator.deleteJobs(queryBuilder);
        assertEquals(deleted, expected.size());

        List<AsyncJobStatus> remaining = this.asyncJobCurator.listAll().list();
        assertEquals(created.size() - expected.size(), remaining.size());

        for (AsyncJobStatus job : remaining) {
            assertTrue(created.contains(job));
            assertFalse(expected.contains(job));
        }
    }

    @Test
    public void testDeleteJobsByMultipleKeys() {
        List<String> keys = Arrays.asList("job_key-1", "job_key-2", "job_key-3");
        List<JobState> states = Arrays.asList(JobState.values());
        List<Owner> owners = Arrays.asList(null, this.createOwner(), this.createOwner());

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        List<String> jobKeys = Arrays.asList("job_key-1", "job_key-3");

        List<AsyncJobStatus> expected = created.stream()
            .filter(job -> jobKeys.contains(job.getJobKey()))
            .collect(Collectors.toList());

        assertTrue(expected.size() > 0);

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder();
        queryBuilder.setJobKeys(jobKeys);

        int deleted = this.asyncJobCurator.deleteJobs(queryBuilder);
        assertEquals(deleted, expected.size());

        List<AsyncJobStatus> remaining = this.asyncJobCurator.listAll().list();
        assertEquals(created.size() - expected.size(), remaining.size());

        for (AsyncJobStatus job : remaining) {
            assertTrue(created.contains(job));
            assertFalse(expected.contains(job));
        }
    }

    @Test
    public void testDeleteJobsByMultipleKeysWithExtraneousKeys() {
        List<String> keys = Arrays.asList("job_key-1", "job_key-2", "job_key-3");
        List<JobState> states = Arrays.asList(JobState.values());
        List<Owner> owners = Arrays.asList(null, this.createOwner(), this.createOwner());

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        List<String> expectedKeys = Arrays.asList("job_key-1", "job_key-3");
        List<String> extraneousKeys = Arrays.asList("job_key-4", "job_key-6");

        List<AsyncJobStatus> expected = created.stream()
            .filter(job -> expectedKeys.contains(job.getJobKey()))
            .collect(Collectors.toList());

        assertTrue(expected.size() > 0);

        List<String> jobKeys = new LinkedList<>();
        jobKeys.addAll(expectedKeys);
        jobKeys.addAll(extraneousKeys);

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder();
        queryBuilder.setJobKeys(jobKeys);

        int deleted = this.asyncJobCurator.deleteJobs(queryBuilder);
        assertEquals(deleted, expected.size());

        List<AsyncJobStatus> remaining = this.asyncJobCurator.listAll().list();
        assertEquals(created.size() - expected.size(), remaining.size());

        for (AsyncJobStatus job : remaining) {
            assertTrue(created.contains(job));
            assertFalse(expected.contains(job));
        }
    }

    @Test
    public void testDeleteJobsBySingleJobState() {
        List<String> keys = Arrays.asList("job_key-1", "job_key-2");
        List<JobState> states = Arrays.asList(JobState.values());
        List<Owner> owners = Arrays.asList(null, this.createOwner(), this.createOwner());

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        List<JobState> jobStates = Arrays.asList(JobState.RUNNING);

        List<AsyncJobStatus> expected = created.stream()
            .filter(job -> jobStates.contains(job.getState()))
            .collect(Collectors.toList());

        assertTrue(expected.size() > 0);

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder();
        queryBuilder.setJobStates(jobStates);

        int deleted = this.asyncJobCurator.deleteJobs(queryBuilder);
        assertEquals(deleted, expected.size());

        List<AsyncJobStatus> remaining = this.asyncJobCurator.listAll().list();
        assertEquals(created.size() - expected.size(), remaining.size());

        for (AsyncJobStatus job : remaining) {
            assertTrue(created.contains(job));
            assertFalse(expected.contains(job));
        }
    }

    @Test
    public void testDeleteJobsByMultipleJobStates() {
        List<String> keys = Arrays.asList("job_key-1", "job_key-2");
        List<JobState> states = Arrays.asList(JobState.values());
        List<Owner> owners = Arrays.asList(null, this.createOwner(), this.createOwner());

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        List<JobState> jobStates = Arrays.asList(JobState.QUEUED, JobState.FINISHED);

        List<AsyncJobStatus> expected = created.stream()
            .filter(job -> jobStates.contains(job.getState()))
            .collect(Collectors.toList());

        assertTrue(expected.size() > 0);

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder();
        queryBuilder.setJobStates(jobStates);

        int deleted = this.asyncJobCurator.deleteJobs(queryBuilder);
        assertEquals(deleted, expected.size());

        List<AsyncJobStatus> remaining = this.asyncJobCurator.listAll().list();
        assertEquals(created.size() - expected.size(), remaining.size());

        for (AsyncJobStatus job : remaining) {
            assertTrue(created.contains(job));
            assertFalse(expected.contains(job));
        }
    }

    @Test
    public void testDeleteJobsByMultipleJobStatesWithExtraneousStates() {
        List<String> keys = Arrays.asList("job_key-1", "job_key-2");
        List<JobState> states = Arrays.asList(JobState.QUEUED, JobState.RUNNING, JobState.FINISHED);
        List<Owner> owners = Arrays.asList(null, this.createOwner(), this.createOwner());

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        List<JobState> expectedStates = Arrays.asList(JobState.QUEUED, JobState.FINISHED);
        List<JobState> extraneousStates = Arrays.asList(JobState.FAILED, JobState.ABORTED);

        List<AsyncJobStatus> expected = created.stream()
            .filter(job -> expectedStates.contains(job.getState()))
            .collect(Collectors.toList());

        assertTrue(expected.size() > 0);

        List<JobState> jobStates = new LinkedList<>();
        jobStates.addAll(expectedStates);
        jobStates.addAll(extraneousStates);

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder();
        queryBuilder.setJobStates(jobStates);

        int deleted = this.asyncJobCurator.deleteJobs(queryBuilder);
        assertEquals(deleted, expected.size());

        List<AsyncJobStatus> remaining = this.asyncJobCurator.listAll().list();
        assertEquals(created.size() - expected.size(), remaining.size());

        for (AsyncJobStatus job : remaining) {
            assertTrue(created.contains(job));
            assertFalse(expected.contains(job));
        }
    }

    @Test
    public void testDeleteJobsBySingleOwnerId() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();

        List<String> keys = Arrays.asList("job_key-1", "job_key-2");
        List<JobState> states = Arrays.asList(JobState.values());
        List<Owner> owners = Arrays.asList(null, owner1, owner2, owner3);

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        List<String> ownerIds = Arrays.asList(owner1.getId());

        List<AsyncJobStatus> expected = created.stream()
            .filter(j -> j.getContextOwner() != null && ownerIds.contains(j.getContextOwner().getId()))
            .collect(Collectors.toList());

        assertTrue(expected.size() > 0);

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder();
        queryBuilder.setOwnerIds(ownerIds);

        int deleted = this.asyncJobCurator.deleteJobs(queryBuilder);
        assertEquals(deleted, expected.size());

        List<AsyncJobStatus> remaining = this.asyncJobCurator.listAll().list();
        assertEquals(created.size() - expected.size(), remaining.size());

        for (AsyncJobStatus job : remaining) {
            assertTrue(created.contains(job));
            assertFalse(expected.contains(job));
        }
    }

    @Test
    public void testDeleteJobsByMultipleOwnerIds() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();

        List<String> keys = Arrays.asList("job_key-1", "job_key-2");
        List<JobState> states = Arrays.asList(JobState.values());
        List<Owner> owners = Arrays.asList(null, owner1, owner2, owner3);

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        List<String> ownerIds = Arrays.asList(owner1.getId(), owner3.getId());

        List<AsyncJobStatus> expected = created.stream()
            .filter(j -> j.getContextOwner() != null && ownerIds.contains(j.getContextOwner().getId()))
            .collect(Collectors.toList());

        assertTrue(expected.size() > 0);

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder();
        queryBuilder.setOwnerIds(ownerIds);

        int deleted = this.asyncJobCurator.deleteJobs(queryBuilder);
        assertEquals(deleted, expected.size());

        List<AsyncJobStatus> remaining = this.asyncJobCurator.listAll().list();
        assertEquals(created.size() - expected.size(), remaining.size());

        for (AsyncJobStatus job : remaining) {
            assertTrue(created.contains(job));
            assertFalse(expected.contains(job));
        }
    }

    @Test
    public void testDeleteJobsByMultipleOwnerIdsWithExtraneousKeys() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();

        List<String> keys = Arrays.asList("job_key-1", "job_key-2");
        List<JobState> states = Arrays.asList(JobState.values());
        List<Owner> owners = Arrays.asList(null, owner1, owner2, owner3);

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        List<String> expectedKeys = Arrays.asList(owner1.getId(), owner3.getId());
        List<String> extraneousKeys = Arrays.asList("extra_key-1", "extra_key-2");

        List<AsyncJobStatus> expected = created.stream()
            .filter(j -> j.getContextOwner() != null && expectedKeys.contains(j.getContextOwner().getId()))
            .collect(Collectors.toList());

        assertTrue(expected.size() > 0);

        List<String> ownerIds = new LinkedList<>();
        ownerIds.addAll(expectedKeys);
        ownerIds.addAll(extraneousKeys);

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder();
        queryBuilder.setOwnerIds(ownerIds);

        int deleted = this.asyncJobCurator.deleteJobs(queryBuilder);
        assertEquals(deleted, expected.size());

        List<AsyncJobStatus> remaining = this.asyncJobCurator.listAll().list();
        assertEquals(created.size() - expected.size(), remaining.size());

        for (AsyncJobStatus job : remaining) {
            assertTrue(created.contains(job));
            assertFalse(expected.contains(job));
        }
    }

    @Test
    public void testDeleteJobsByNullOwnerId() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();

        List<String> keys = Arrays.asList("job_key-1", "job_key-2");
        List<JobState> states = Arrays.asList(JobState.values());
        List<Owner> owners = Arrays.asList(null, owner1, owner2, owner3);

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        List<String> ownerIds = new LinkedList<>();
        ownerIds.add(null);

        List<AsyncJobStatus> expected = created.stream()
            .filter(job -> job.getContextOwner() == null)
            .collect(Collectors.toList());

        assertTrue(expected.size() > 0);

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder();
        queryBuilder.setOwnerIds(ownerIds);

        int deleted = this.asyncJobCurator.deleteJobs(queryBuilder);
        assertEquals(deleted, expected.size());

        List<AsyncJobStatus> remaining = this.asyncJobCurator.listAll().list();
        assertEquals(created.size() - expected.size(), remaining.size());

        for (AsyncJobStatus job : remaining) {
            assertTrue(created.contains(job));
            assertFalse(expected.contains(job));
        }
    }

    @Test
    public void testDeleteJobsByMixedOwnerIds() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();
        Owner owner3 = this.createOwner();

        List<String> keys = Arrays.asList("job_key-1", "job_key-2");
        List<JobState> states = Arrays.asList(JobState.values());
        List<Owner> owners = Arrays.asList(null, owner1, owner2, owner3);

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        List<String> ownerIds = new LinkedList<>();
        ownerIds.add(null);
        ownerIds.add(owner2.getId());

        List<AsyncJobStatus> expected = created.stream()
            .filter(j -> j.getContextOwner() == null || ownerIds.contains(j.getContextOwner().getId()))
            .collect(Collectors.toList());

        assertTrue(expected.size() > 0);

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder();
        queryBuilder.setOwnerIds(ownerIds);

        int deleted = this.asyncJobCurator.deleteJobs(queryBuilder);
        assertEquals(deleted, expected.size());

        List<AsyncJobStatus> remaining = this.asyncJobCurator.listAll().list();
        assertEquals(created.size() - expected.size(), remaining.size());

        for (AsyncJobStatus job : remaining) {
            assertTrue(created.contains(job));
            assertFalse(expected.contains(job));
        }
    }

    @Test
    public void testDeleteJobsByLastUpdateTimeBeforeDate() {
        List<String> keys = Arrays.asList("job_key-1", "job_key-2");
        List<JobState> states = Arrays.asList(JobState.values());
        List<Owner> owners = Arrays.asList(null, this.createOwner(), this.createOwner());

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        Date now = new Date();

        List<AsyncJobStatus> expected = created.stream()
            .filter(job -> now.compareTo(job.getUpdated()) >= 0)
            .collect(Collectors.toList());

        assertTrue(expected.size() > 0);

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder()
            .setEndDate(now);

        int deleted = this.asyncJobCurator.deleteJobs(queryBuilder);
        assertEquals(deleted, expected.size());

        List<AsyncJobStatus> remaining = this.asyncJobCurator.listAll().list();
        assertEquals(created.size() - expected.size(), remaining.size());

        for (AsyncJobStatus job : remaining) {
            assertTrue(created.contains(job));
            assertFalse(expected.contains(job));
        }
    }

    @Test
    public void testDeleteJobsByLastUpdateTimeAfterDate() {
        List<String> keys = Arrays.asList("job_key-1", "job_key-2");
        List<JobState> states = Arrays.asList(JobState.values());
        List<Owner> owners = Arrays.asList(null, this.createOwner(), this.createOwner());

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        Date now = new Date();

        List<AsyncJobStatus> expected = created.stream()
            .filter(job -> now.compareTo(job.getUpdated()) <= 0)
            .collect(Collectors.toList());

        assertTrue(expected.size() > 0);

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder()
            .setStartDate(now);

        int deleted = this.asyncJobCurator.deleteJobs(queryBuilder);
        assertEquals(deleted, expected.size());

        List<AsyncJobStatus> remaining = this.asyncJobCurator.listAll().list();
        assertEquals(created.size() - expected.size(), remaining.size());

        for (AsyncJobStatus job : remaining) {
            assertTrue(created.contains(job));
            assertFalse(expected.contains(job));
        }
    }

    @Test
    public void testDeleteJobsByLastUpdateTimeInRange() {
        List<String> keys = Arrays.asList("job_key-1", "job_key-2");
        List<JobState> states = Arrays.asList(JobState.values());
        List<Owner> owners = Arrays.asList(null, this.createOwner(), this.createOwner());

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        Date start = Util.addDaysToDt(-1);
        Date end = Util.addDaysToDt(1);

        List<AsyncJobStatus> expected = created.stream()
            .filter(job -> start.compareTo(job.getUpdated()) <= 0 && end.compareTo(job.getUpdated()) >= 0)
            .collect(Collectors.toList());

        assertTrue(expected.size() > 0);

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder()
            .setStartDate(start)
            .setEndDate(end);

        int deleted = this.asyncJobCurator.deleteJobs(queryBuilder);
        assertEquals(deleted, expected.size());

        List<AsyncJobStatus> remaining = this.asyncJobCurator.listAll().list();
        assertEquals(created.size() - expected.size(), remaining.size());

        for (AsyncJobStatus job : remaining) {
            assertTrue(created.contains(job));
            assertFalse(expected.contains(job));
        }
    }

    @ParameterizedTest
    @MethodSource("jobQueryGenericStringInputProvider")
    public void testDeleteJobsByPrincipal(Collection<String> create, Collection<String> query) {
        List<String> keys = Arrays.asList("job_key-1", "job_key-2", "job_key-3");
        List<JobState> states = Arrays.asList(JobState.QUEUED, JobState.RUNNING, JobState.FINISHED);
        List<Owner> owners = Arrays.asList(null, this.createOwner(), this.createOwner());

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, create, null, null);

        List<String> expected = new LinkedList<>(query);
        expected.retainAll(create);

        List<AsyncJobStatus> toDelete = created.stream()
            .filter(job -> expected.contains(job.getPrincipalName()))
            .collect(Collectors.toList());

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder()
            .setPrincipalNames(query);

        int deleted = this.asyncJobCurator.deleteJobs(queryBuilder);
        assertEquals(toDelete.size(), deleted);

        List<AsyncJobStatus> remaining = this.asyncJobCurator.listAll().list();
        assertEquals(created.size() - toDelete.size(), remaining.size());

        for (AsyncJobStatus job : remaining) {
            assertTrue(created.contains(job));
            assertFalse(toDelete.contains(job));
        }
    }

    @ParameterizedTest
    @MethodSource("jobQueryGenericStringInputProvider")
    public void testDeleteJobsByOrigin(Collection<String> create, Collection<String> query) {
        List<String> keys = Arrays.asList("job_key-1", "job_key-2", "job_key-3");
        List<JobState> states = Arrays.asList(JobState.QUEUED, JobState.RUNNING, JobState.FINISHED);
        List<Owner> owners = Arrays.asList(null, this.createOwner(), this.createOwner());

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, create, null);

        List<String> expected = new LinkedList<>(query);
        expected.retainAll(create);

        List<AsyncJobStatus> toDelete = created.stream()
            .filter(job -> expected.contains(job.getOrigin()))
            .collect(Collectors.toList());

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder()
            .setOrigins(query);

        int deleted = this.asyncJobCurator.deleteJobs(queryBuilder);
        assertEquals(toDelete.size(), deleted);

        List<AsyncJobStatus> remaining = this.asyncJobCurator.listAll().list();
        assertEquals(created.size() - toDelete.size(), remaining.size());

        for (AsyncJobStatus job : remaining) {
            assertTrue(created.contains(job));
            assertFalse(toDelete.contains(job));
        }
    }

    @ParameterizedTest
    @MethodSource("jobQueryGenericStringInputProvider")
    public void testDeleteJobsByExecutor(Collection<String> create, Collection<String> query) {
        List<String> keys = Arrays.asList("job_key-1", "job_key-2", "job_key-3");
        List<JobState> states = Arrays.asList(JobState.QUEUED, JobState.RUNNING, JobState.FINISHED);
        List<Owner> owners = Arrays.asList(null, this.createOwner(), this.createOwner());

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, create);

        List<String> expected = new LinkedList<>(query);
        expected.retainAll(create);

        List<AsyncJobStatus> toDelete = created.stream()
            .filter(job -> expected.contains(job.getExecutor()))
            .collect(Collectors.toList());

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder()
            .setExecutors(query);

        int deleted = this.asyncJobCurator.deleteJobs(queryBuilder);
        assertEquals(toDelete.size(), deleted);

        List<AsyncJobStatus> remaining = this.asyncJobCurator.listAll().list();
        assertEquals(created.size() - toDelete.size(), remaining.size());

        for (AsyncJobStatus job : remaining) {
            assertTrue(created.contains(job));
            assertFalse(toDelete.contains(job));
        }
    }

    @Test
    public void testDeleteJobsByMultipleParameters() {
        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        List<String> keys = Arrays.asList("job_key-1", "job_key-2", "job_key-3");
        List<Owner> owners = Arrays.asList(null, owner1, owner2);
        List<JobState> states = Arrays.asList(JobState.QUEUED, JobState.RUNNING,
            JobState.FAILED, JobState.FINISHED);
        List<String> principals = Arrays.asList("principal-1", "principal-2", "principal-3");
        List<String> origins = Arrays.asList("origin-1", "origin-2", "origin-3");
        List<String> executors = Arrays.asList("executor-1", "executor-2", "executor-3");

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners,
            principals, origins, executors);

        List<String> jobKeys = Arrays.asList("job_key-1");
        List<JobState> jobStates = Arrays.asList(JobState.QUEUED, JobState.RUNNING);
        List<String> ownerIds = Arrays.asList(owner2.getId());
        List<String> jobPrincipals = Arrays.asList("principal-2");
        List<String> jobOrigins = Arrays.asList("origin-3");
        List<String> jobExecutors = Arrays.asList("executor-1");
        Date start = Util.addDaysToDt(-1);
        Date end = Util.addDaysToDt(1);

        List<AsyncJobStatus> expected = created.stream()
            .filter(job -> jobKeys.contains(job.getJobKey()))
            .filter(job -> jobStates.contains(job.getState()))
            .filter(j   -> j.getContextOwner() != null && ownerIds.contains(j.getContextOwner().getId()))
            .filter(job -> start.compareTo(job.getUpdated()) <= 0 && end.compareTo(job.getUpdated()) >= 0)
            .filter(job -> jobPrincipals.contains(job.getPrincipalName()))
            .filter(job -> jobOrigins.contains(job.getOrigin()))
            .filter(job -> jobExecutors.contains(job.getExecutor()))
            .collect(Collectors.toList());

        assertTrue(expected.size() > 0);

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder()
            .setJobKeys(jobKeys)
            .setJobStates(jobStates)
            .setOwnerIds(ownerIds)
            .setPrincipalNames(jobPrincipals)
            .setOrigins(jobOrigins)
            .setExecutors(jobExecutors)
            .setStartDate(start)
            .setEndDate(end);

        int deleted = this.asyncJobCurator.deleteJobs(queryBuilder);
        assertEquals(deleted, expected.size());

        List<AsyncJobStatus> remaining = this.asyncJobCurator.listAll().list();
        assertEquals(created.size() - expected.size(), remaining.size());

        for (AsyncJobStatus job : remaining) {
            assertTrue(created.contains(job));
            assertFalse(expected.contains(job));
        }
    }

    @Test
    public void testDeleteJobsWithoutParameters() {
        List<String> keys = Arrays.asList("job_key-1", "job_key-2");
        List<JobState> states = Arrays.asList(JobState.values());
        List<Owner> owners = Arrays.asList(null, this.createOwner(), this.createOwner());

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder();
        int deleted = this.asyncJobCurator.deleteJobs(queryBuilder);

        // The sanity check should cause this to delete nothing.
        assertEquals(0, deleted);

        List<AsyncJobStatus> remaining = this.asyncJobCurator.listAll().list();
        assertEquals(created.size(), remaining.size());

        for (AsyncJobStatus job : remaining) {
            assertTrue(created.contains(job));
        }
    }

    @Test
    public void testDeleteJobsWithNullBuilder() {
        List<String> keys = Arrays.asList("job_key-1", "job_key-2");
        List<JobState> states = Arrays.asList(JobState.values());
        List<Owner> owners = Arrays.asList(null, this.createOwner(), this.createOwner());

        List<AsyncJobStatus> created = this.createJobsForQueryTests(keys, states, owners, null, null, null);

        int deleted = this.asyncJobCurator.deleteJobs(null);

        // The sanity check should cause this to delete nothing.
        assertEquals(0, deleted);

        List<AsyncJobStatus> remaining = this.asyncJobCurator.listAll().list();
        assertEquals(created.size(), remaining.size());

        for (AsyncJobStatus job : remaining) {
            assertTrue(created.contains(job));
        }
    }


}
