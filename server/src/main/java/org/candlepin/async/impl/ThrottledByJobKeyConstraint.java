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
import org.candlepin.model.AsyncJobStatus;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;


/**
 * The UniqueByArgConstraint constrains queuing of a job if another job with same same key and
 * value of a given parameter, or set of parameters, already exists in a non-terminal state.
 */
public class ThrottledByJobKeyConstraint implements JobConstraint {

    private final String jobKey;
    private final int limit;

    public ThrottledByJobKeyConstraint(String jobKey, int limit) {
        if (jobKey == null || jobKey.isEmpty()) {
            throw new IllegalArgumentException("Job key must be provided!");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be a positive integer!");
        }
        this.jobKey = jobKey;
        this.limit = limit;
    }

    @Override
    public Collection<AsyncJobStatus> test(AsyncJobStatus inbound, Collection<AsyncJobStatus> existing) {
        if (inbound == null) {
            throw new IllegalArgumentException("Inbound job is null!");
        }
        if (existing == null) {
            throw new IllegalArgumentException("Existing  jobs are null!");
        }
        List<AsyncJobStatus> conflicting = conflictingJobs(existing);
        if (conflicting.size() > this.limit) {
            return conflicting;
        }
        else {
            return Collections.emptyList();
        }
    }

    private List<AsyncJobStatus> conflictingJobs(Collection<AsyncJobStatus> existing) {
        return existing.stream()
            .filter(this::isConflicting)
            .collect(Collectors.toList());
    }

    private boolean isConflicting(AsyncJobStatus status) {
        return this.jobKey.equalsIgnoreCase(status.getJobKey());
    }

}
