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

import org.candlepin.async.AsyncJob;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.async.JobManager;
import org.candlepin.async.MaxJobAgeProvider;
import org.candlepin.model.AsyncJobStatus.JobState;
import org.candlepin.model.AsyncJobStatusCurator.AsyncJobStatusQueryBuilder;
import org.candlepin.util.Util;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;



/**
 * The JobCleaner job deletes terminal jobs older than the max job age (default: 7 days)
 */
public class JobCleaner implements AsyncJob {
    private static final Logger log = LoggerFactory.getLogger(JobCleaner.class);

    public static final String DEFAULT_SCHEDULE = "0 0 12 * * ?";

    public static final String JOB_KEY = "JobCleaner";
    public static final String JOB_NAME = "Job Cleaner";

    private final JobManager jobManager;
    private final MaxJobAgeProvider maxJobAgeProvider;

    @Inject
    public JobCleaner(MaxJobAgeProvider maxJobAgeProvider, JobManager jobManager) {
        this.maxJobAgeProvider = Objects.requireNonNull(maxJobAgeProvider);
        this.jobManager = Objects.requireNonNull(jobManager);
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        int maxAgeInMinutes = this.maxJobAgeProvider.inMinutes();

        // Set cutoff (end) date to now - max age in minutes
        Date cutoff = Util.addMinutesToDt(maxAgeInMinutes * -1);

        // We're targeting every terminal job
        Set<JobState> jobStates = Arrays.stream(JobState.values())
            .filter(JobState::isTerminal)
            .collect(Collectors.toSet());

        // Build the query builder with our config
        AsyncJobStatusQueryBuilder queryBuilder = new AsyncJobStatusQueryBuilder()
            .setJobStates(jobStates)
            .setEndDate(cutoff);

        int deleted = this.jobManager.cleanupJobs(queryBuilder);

        String result = String.format("Removed %d terminal jobs older than %2$tF %2$tT%2$tz",
            deleted, cutoff);

        log.info(result);
        context.setJobResult(result);
    }
}
