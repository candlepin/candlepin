/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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
import org.candlepin.async.JobConfig;
import org.candlepin.async.JobException;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.async.JobManager;
import org.candlepin.model.ClaimedOwner;
import org.candlepin.model.OwnerCurator;

import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

import javax.inject.Inject;


/**
 * The job will periodically scan claimed owners that still have un-migrated consumers
 * and trigger migration for each such owner.
 */
public class ClaimedOwnerConsumerDetectionJob implements AsyncJob {
    private static final Logger log = LoggerFactory.getLogger(ClaimedOwnerConsumerDetectionJob.class);

    public static final String JOB_KEY = "ClaimedOwnerConsumerDetectionJob";
    public static final String JOB_NAME = "Claimed Owner Consumer Detection";
    // Every 1 PM
    public static final String DEFAULT_SCHEDULE = "0 0 13 * * ?";

    private final OwnerCurator ownerCurator;
    private final JobManager jobManager;

    @Inject
    public ClaimedOwnerConsumerDetectionJob(OwnerCurator ownerCurator, JobManager jobManager) {
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.jobManager = Objects.requireNonNull(jobManager);
    }

    @Override
    @Transactional
    public void execute(JobExecutionContext context) throws JobExecutionException {
        List<ClaimedOwner> unMigratedOwners = this.ownerCurator.findClaimedUnMigratedOwners();
        log.info("Found {} un-migrated owners", unMigratedOwners.size());

        for (ClaimedOwner unMigratedOwner : unMigratedOwners) {
            JobConfig<?> job = ConsumerMigrationJob.createConfig()
                .setOriginOwner(unMigratedOwner.ownerKey())
                .setDestinationOwner(unMigratedOwner.claimantKey());

            try {
                this.jobManager.queueJob(job);
            }
            catch (JobException e) {
                throw new JobExecutionException("Failed to schedule a migration of owner: %s"
                    .formatted(unMigratedOwner.ownerKey()));
            }
        }
    }

}
