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
import org.candlepin.model.AsyncJobStatusCurator;

import java.util.Collection;
import java.util.Collections;
import java.util.List;



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

    /**
     * @{inheritDoc}
     */
    @Override
    public Collection<String> test(AsyncJobStatusCurator jobCurator, AsyncJobStatus inbound) {
        if (jobCurator == null) {
            throw new IllegalArgumentException("jobCurator is null");
        }

        if (inbound == null) {
            throw new IllegalArgumentException("inbound is null");
        }

        List<String> matching = jobCurator.fetchJobIdsByArguments(inbound.getJobKey(), null);
        return (matching != null && matching.size() > this.limit) ? matching : Collections.emptyList();
    }

}
