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
import org.candlepin.async.ArgumentConversionException;
import org.candlepin.async.JobArguments;
import org.candlepin.async.JobConfig;
import org.candlepin.async.JobConfigValidationException;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.common.filter.LoggingFilter;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Owner;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Objects;



/**
 * Asynchronous job for refreshing the last check-in time for a specific reporter ID
 */
public class HypervisorHeartbeatUpdateJob implements AsyncJob {
    private static final Logger log = LoggerFactory.getLogger(HypervisorHeartbeatUpdateJob.class);

    public static final String JOB_KEY = "HYPERVISOR_HEARTBEAT_UPDATE";
    public static final String JOB_NAME = "hypervisor_heartbeat_update";

    private static final String REPORTER_ID = "reporter_id";
    private static final String OWNER_KEY = "owner_key";

    private final ConsumerCurator consumerCurator;

    /**
     * Job configuration object for the export job
     */
    public static class HypervisorHeartbeatUpdateJobConfig extends JobConfig {
        public HypervisorHeartbeatUpdateJobConfig() {
            this.setJobKey(JOB_KEY)
                .setJobName(JOB_NAME);

            // TODO: Should this be unique by (owner_key, reporter_id) or owner_key? If it's the
            // former, we'll need to update the UniqueByArgConstraint to support examining multiple
            // parameters
        }

        /**
         * Sets the owner for this job
         *
         * @param owner
         *  the owner to set for this job
         *
         * @return
         *  a reference to this job config
         */
        public HypervisorHeartbeatUpdateJobConfig setOwner(Owner owner) {
            if (owner == null) {
                throw new IllegalArgumentException("owner is null");
            }

            this.setJobArgument(OWNER_KEY, owner.getKey())
                .setJobMetadata(LoggingFilter.OWNER_KEY, owner.getKey())
                .setLogLevel(owner.getLogLevel());

            return this;
        }

        /**
         * Sets the reporter ID for this job
         *
         * @param reporterId
         *  the reporter ID to set for this job
         *
         * @return
         *  a reference to this job config
         */
        public HypervisorHeartbeatUpdateJobConfig setReporterId(String reporterId) {
            if (reporterId == null || reporterId.isEmpty()) {
                throw new IllegalArgumentException("reporterId is null or empty");
            }

            this.setJobArgument(REPORTER_ID, reporterId);

            return this;
        }


        @Override
        public void validate() throws JobConfigValidationException {
            super.validate();

            try {
                JobArguments arguments = this.getJobArguments();

                String ownerKey = arguments.getAsString(OWNER_KEY);
                String reporterId = arguments.getAsString(REPORTER_ID);

                if (ownerKey == null || ownerKey.isEmpty()) {
                    String errmsg = "owner has not been set, or the provided owner lacks a key";
                    throw new JobConfigValidationException(errmsg);
                }

                if (reporterId == null || reporterId.isEmpty()) {
                    String errmsg = "reporter has not been set, or the provided reporter is empty";
                    throw new JobConfigValidationException(errmsg);
                }
            }
            catch (ArgumentConversionException e) {
                String errmsg = "One or more required arguments are of the wrong type";
                throw new JobConfigValidationException(errmsg, e);
            }
        }
    }


    @Inject
    public HypervisorHeartbeatUpdateJob(ConsumerCurator consumerCurator) {
        this.consumerCurator = Objects.requireNonNull(consumerCurator);
    }

    @Override
    public Object execute(JobExecutionContext context) throws JobExecutionException {
        JobArguments args = context.getJobArguments();

        String ownerKey = args.getAsString(OWNER_KEY);
        String reporterId = args.getAsString(REPORTER_ID);

        log.debug("Starting hypervisor check-in update for owner: {}", ownerKey);

        this.consumerCurator.heartbeatUpdate(reporterId, new Date(), ownerKey);

        return String.format("Last hypervisor check-in updated for owner \"%s\" by reporter: %s",
            ownerKey, reporterId);
    }

    /**
     * Creates a JobConfig configured to execute the hypervisor heartbeat update job. Callers may
     * further manipulate the JobConfig as necessary before queuing it.
     *
     * @return
     *  a JobConfig instance configured to execute the hypervisor heartbeat update job
     */
    public static HypervisorHeartbeatUpdateJobConfig createJobConfig() {
        return new HypervisorHeartbeatUpdateJobConfig();
    }

}
