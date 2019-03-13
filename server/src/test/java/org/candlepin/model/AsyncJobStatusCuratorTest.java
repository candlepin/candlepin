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

import static org.junit.Assert.*;

import org.candlepin.test.DatabaseTestFixture;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;


/**
 * Test suite for the ConsumerTypeCurator
 */
public class AsyncJobStatusCuratorTest extends DatabaseTestFixture {

    @Inject private AsyncJobStatusCurator asyncJobCurator;

    public AsyncJobStatus createJobStatus(String name, String group, String constraintHash,
        AsyncJobStatus.JobState state) {

        AsyncJobStatus status = new AsyncJobStatus()
            .setName(name)
            .setGroup(group)
            .setUniqueConstraintHash(constraintHash)
            .setState(state);

        return this.asyncJobCurator.create(status);
    }

    @Test
    public void testFindJobsByConstraintsReturnsEmptyWithNoName() {
        for (int i = 0; i < 3; ++i) {
            this.createJobStatus("job_name-" + i, "job_group-" + i, "constraint-" + i,
                AsyncJobStatus.JobState.values()[i]);
        }

        for (int i = 0; i < 3; ++i) {
            Collection<AsyncJobStatus> fetched = this.asyncJobCurator.findJobsByConstraints(null,
                "job_group-" + i, Arrays.asList(AsyncJobStatus.JobState.values()[i]), "constraint-" + i);

            assertNotNull(fetched);
            assertEquals(0, fetched.size());
        }
    }

    @Test
    public void testFindJobsByConstraintsReturnsEmptyWithNoConstraint() {
        for (int i = 0; i < 3; ++i) {
            this.createJobStatus("job_name-" + i, "job_group-" + i, "constraint-" + i,
                AsyncJobStatus.JobState.values()[i]);
        }

        for (int i = 0; i < 3; ++i) {
            Collection<AsyncJobStatus> fetched = this.asyncJobCurator.findJobsByConstraints("job_group-" + i,
                "job_group-" + i, Arrays.asList(AsyncJobStatus.JobState.values()[i]), null);

            assertNotNull(fetched);
            assertEquals(0, fetched.size());
        }
    }

    @Test
    public void testFindJobsByConstraintsRestrictsByNameAndConstraints() {
        for (int i = 0; i < 3; ++i) {
            this.createJobStatus("job_name-" + i, "job_group-" + i, "constraint-" + i,
                AsyncJobStatus.JobState.values()[i]);
        }

        for (int i = 0; i < 3; ++i) {
            Collection<AsyncJobStatus> fetched = this.asyncJobCurator.findJobsByConstraints("job_name-" + i,
                null, null, "constraint-" + i);

            assertNotNull("Job lookup returned null for simple test " + i, fetched);
            assertEquals("Job lookup returned null for simple test " + i, 1, fetched.size());

            AsyncJobStatus actual = fetched.iterator().next();
            assertEquals("job_name-" + i, actual.getName());
            assertEquals("constraint-" + i, actual.getUniqueConstraintHash());
        }
    }

    @Test
    public void testFindJobsByConstraintsRestrictsByNameConstraintsAndGroup() {
        Map<Integer, AsyncJobStatus> jobs = new HashMap<>();

        for (int i = 0; i < 5; ++i) {
            jobs.put(i, this.createJobStatus("job_name", "job_group-" + i, "constraint",
                AsyncJobStatus.JobState.values()[i]));
        }

        for (int i = 0; i < 5; ++i) {
            Collection<AsyncJobStatus> fetched = this.asyncJobCurator.findJobsByConstraints("job_name",
                "job_group-" + i, null, "constraint");

            assertNotNull("Job lookup returned null for group test " + i, fetched);
            assertEquals("Failed to fetch job for group test " + i, 1, fetched.size());

            AsyncJobStatus expected = jobs.get(i);
            AsyncJobStatus actual = fetched.iterator().next();

            assertEquals("Job equality test failed for group test " + i, expected.getId(), actual.getId());
        }
    }

    @Test
    public void testFindJobsByConstraintsRestrictsByNameConstraintsAndState() {
        Map<Integer, AsyncJobStatus> jobs = new HashMap<>();

        for (int i = 0; i < 5; ++i) {
            jobs.put(i, this.createJobStatus("job_name", "job_group", "constraint",
                AsyncJobStatus.JobState.values()[i]));
        }

        for (int i = 0; i < 5; ++i) {
            Collection<AsyncJobStatus> fetched = this.asyncJobCurator.findJobsByConstraints("job_name",
                "job_group", Arrays.asList(AsyncJobStatus.JobState.values()[i]), "constraint");

            assertNotNull("Job lookup returned null for state test " + i, fetched);
            assertEquals("Failed to fetch job for state test " + i, 1, fetched.size());

            AsyncJobStatus expected = jobs.get(i);
            AsyncJobStatus actual = fetched.iterator().next();

            assertEquals("Job equality test failed for state test " + i, expected.getId(), actual.getId());
        }
    }

    @Test
    public void testFindJobsByConstraintsRestrictsByNameConstraintsAndStates() {
        Map<Integer, AsyncJobStatus> jobs = new HashMap<>();

        for (int i = 0; i < 5; ++i) {
            jobs.put(i, this.createJobStatus("job_name", "job_group", "constraint",
                AsyncJobStatus.JobState.values()[i]));
        }

        Collection<AsyncJobStatus> fetched = this.asyncJobCurator.findJobsByConstraints("job_name",
            "job_group", Arrays.asList(AsyncJobStatus.JobState.values()), "constraint");

        assertNotNull(fetched);
        assertEquals(5, fetched.size());

        for (AsyncJobStatus job : jobs.values()) {
            assertTrue(fetched.contains(job));
        }
    }

}
