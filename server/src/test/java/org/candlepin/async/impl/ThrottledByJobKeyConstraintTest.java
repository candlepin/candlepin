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

package org.candlepin.async.impl;

import org.candlepin.async.JobConstraint;
import org.candlepin.async.tasks.EntitlerJob;
import org.candlepin.model.AsyncJobStatus;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThrottledByJobKeyConstraintTest {

    private static final String TEST_KEY = EntitlerJob.JOB_KEY;
    private static final int LIMIT = 2;

    @Test
    void shouldThrottleMessagesExceedingTheLimit() {
        List<AsyncJobStatus> allJobs = conflictingJobs(3);
        JobConstraint constraint = new ThrottledByJobKeyConstraint(TEST_KEY, LIMIT);

        Collection<AsyncJobStatus> conflicting = constraint.test(conflictingJob(), allJobs);

        assertEquals(3, conflicting.size());
    }

    @Test
    void shouldNotThrottleMessagesWhenInLimit() {
        List<AsyncJobStatus> allJobs = conflictingJobs(1);
        JobConstraint constraint = new ThrottledByJobKeyConstraint(TEST_KEY, LIMIT);

        Collection<AsyncJobStatus> conflicting = constraint.test(conflictingJob(), allJobs);

        assertTrue(conflicting.isEmpty());
    }

    private List<AsyncJobStatus> conflictingJobs(int n) {
        return Stream.generate(this::conflictingJob)
            .limit(n)
            .collect(Collectors.toList());
    }

    private AsyncJobStatus conflictingJob() {
        return new AsyncJobStatus()
            .setJobKey(TEST_KEY);
    }

}
