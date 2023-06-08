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

import org.candlepin.async.AsyncJob;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.async.JobManager;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.model.AsyncJobStatus.JobState;
import org.candlepin.model.AsyncJobStatusCurator.AsyncJobStatusQueryArguments;
import org.candlepin.util.Util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;



/**
 * The JobCleaner job deletes terminal jobs older than the max job age (default: 7 days)
 */
public class JobCleaner implements AsyncJob {
    private static final Logger log = LoggerFactory.getLogger(JobCleaner.class);

    // Every noon
    public static final String DEFAULT_SCHEDULE = "0 0 12 * * ?";

    public static final String JOB_KEY = "JobCleaner";
    public static final String JOB_NAME = "Job Cleaner";

    public static final String CFG_MAX_TERMINAL_JOB_AGE = "max_terminal_job_age";
    public static final String CFG_MAX_NONTERMINAL_JOB_AGE = "max_nonterminal_job_age";
    public static final String CFG_MAX_RUNNING_JOB_AGE = "max_running_job_age";
    // 7 days
    public static final String DEFAULT_MAX_TERMINAL_AGE = "10080";
    // 3 days
    public static final String DEFAULT_MAX_NONTERMINAL_AGE = "4320";
    // 2 days
    public static final String DEFAULT_MAX_RUNNING_AGE = "2880";

    private final Configuration config;
    private final JobManager jobManager;

    @Inject
    public JobCleaner(Configuration config, JobManager jobManager) {
        this.config = Objects.requireNonNull(config);
        this.jobManager = Objects.requireNonNull(jobManager);
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Date terminalCutoff = this.parseMaxJobAgeConfig(CFG_MAX_TERMINAL_JOB_AGE, true);
        Date nonterminalCutoff = this.parseMaxJobAgeConfig(CFG_MAX_NONTERMINAL_JOB_AGE, false);
        Date runningCutoff = this.parseMaxJobAgeConfig(CFG_MAX_RUNNING_JOB_AGE, false);

        StringBuilder result = new StringBuilder();

        int removed = this.cleanupTerminalJobs(terminalCutoff);
        result.append(String.format("Removed %1$d terminal jobs older than %2$tF %2$tT%2$tz%n",
            removed, terminalCutoff));

        if (nonterminalCutoff != null) {
            int aborted = this.abortNonTerminalJobs(nonterminalCutoff);
            result.append(
                String.format("Aborted %1$d non-running, non-terminal jobs older than %2$tF %2$tT%2$tz%n",
                aborted, nonterminalCutoff));
        }

        if (runningCutoff != null) {
            int aborted = this.abortAbandonedRunningJobs(runningCutoff);
            result.append(String.format("Aborted %1$d running jobs older than %2$tF %2$tT%2$tz%n",
                aborted, runningCutoff));
        }

        context.setJobResult(result.toString());
    }

    private Date parseMaxJobAgeConfig(String cfgName, boolean required)
        throws JobExecutionException {

        String fqcn = ConfigProperties.jobConfig(JOB_KEY, cfgName);
        int value = this.config.getInt(fqcn);

        Date output = null;

        if (value > 0) {
            output = Util.addMinutesToDt(value * -1);
        }
        else if (required) {
            String errmsg = String.format(
                "Invalid value for configuration \"%s\", must be a positive integer: %s", fqcn, value);

            log.error(errmsg);
            throw new JobExecutionException(errmsg, true);
        }

        return output;
    }

    private int cleanupTerminalJobs(Date cutoff) {
        // We're targeting every terminal job
        Set<JobState> jobStates = Arrays.stream(JobState.values())
            .filter(JobState::isTerminal)
            .collect(Collectors.toSet());

        // Build the query builder with our config
        AsyncJobStatusQueryArguments queryArgs = new AsyncJobStatusQueryArguments()
            .setJobStates(jobStates)
            .setEndDate(cutoff);

        int removed = this.jobManager.cleanupJobs(queryArgs);
        log.info("Removed {} terminal jobs older than {}", removed, cutoff);

        return removed;
    }

    private int abortNonTerminalJobs(Date cutoff) {
        // We're targeting every non-terminal, non-running job
        Set<JobState> jobStates = Arrays.stream(JobState.values())
            .filter(state -> !state.isTerminal() && state != JobState.RUNNING)
            .collect(Collectors.toSet());

        // Build the query builder with our config
        AsyncJobStatusQueryArguments queryArgs = new AsyncJobStatusQueryArguments()
            .setJobStates(jobStates)
            .setEndDate(cutoff);

        int aborted = this.jobManager.abortNonTerminalJobs(queryArgs);
        log.info("Aborted {} non-running jobs older than {}", aborted, cutoff);

        return aborted;
    }

    private int abortAbandonedRunningJobs(Date cutoff) {
        // We're targeting only running jobs
        Set<JobState> jobStates = Util.asSet(JobState.RUNNING);

        // Build the query builder with our config
        AsyncJobStatusQueryArguments queryArgs = new AsyncJobStatusQueryArguments()
            .setJobStates(jobStates)
            .setEndDate(cutoff);

        int aborted = this.jobManager.abortNonTerminalJobs(queryArgs);
        log.info("Aborted {} running jobs older than {}", aborted, cutoff);

        return aborted;
    }
}
