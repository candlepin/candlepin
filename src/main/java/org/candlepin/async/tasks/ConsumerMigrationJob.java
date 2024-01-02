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

import org.candlepin.async.ArgumentConversionException;
import org.candlepin.async.AsyncJob;
import org.candlepin.async.JobArguments;
import org.candlepin.async.JobConfig;
import org.candlepin.async.JobConfigValidationException;
import org.candlepin.async.JobConstraints;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.controller.ConsumerMigration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import javax.inject.Inject;


/**
 * Runs a consumer migration of a single anonymous org.
 */
public class ConsumerMigrationJob implements AsyncJob {
    private static final Logger log = LoggerFactory.getLogger(ConsumerMigrationJob.class);

    public static final String JOB_KEY = "ConsumerMigrationJob";
    public static final String JOB_NAME = "Consumer Migration Job";

    private static final String ORIGIN_OWNER = "origin_owner";
    private static final String DESTINATION_OWNER = "destination_owner";

    private final ConsumerMigration consumerMigration;

    @Inject
    public ConsumerMigrationJob(ConsumerMigration consumerMigration) {
        this.consumerMigration = Objects.requireNonNull(consumerMigration);
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String originOwnerKey = context.getJobArguments().getAsString(ORIGIN_OWNER);
        String destinationOwnerKey = context.getJobArguments().getAsString(DESTINATION_OWNER);
        log.info("Starting consumer migration from: {} to: {}", originOwnerKey, destinationOwnerKey);

        try {
            this.consumerMigration.migrate(originOwnerKey, destinationOwnerKey);
        }
        catch (Exception e) {
            throw new JobExecutionException(e);
        }

        log.debug("Consumer migration finished");
    }

    public static ConsumerMigrationJobConfig createConfig() {
        return new ConsumerMigrationJobConfig();
    }

    public static class ConsumerMigrationJobConfig extends JobConfig<ConsumerMigrationJobConfig> {

        public ConsumerMigrationJobConfig() {
            this.setJobKey(JOB_KEY).setJobName(JOB_NAME)
                .addConstraint(JobConstraints.uniqueByArguments(ORIGIN_OWNER))
                .setRetryCount(3);
        }

        /**
         * Sets the origin owner key for this job
         *
         * @param ownerKey
         *  key of the origin owner for this job
         * @return a reference to this job config
         */
        public ConsumerMigrationJobConfig setOriginOwner(String ownerKey) {
            if (ownerKey == null || ownerKey.isBlank()) {
                throw new IllegalArgumentException("origin owner key is null or empty");
            }

            this.setJobArgument(ORIGIN_OWNER, ownerKey);

            return this;
        }

        /**
         * Sets the destination owner key for this job
         *
         * @param ownerKey
         *  key of the destination owner for this job
         * @return a reference to this job config
         */
        public ConsumerMigrationJobConfig setDestinationOwner(String ownerKey) {
            if (ownerKey == null || ownerKey.isBlank()) {
                throw new IllegalArgumentException("destination owner key is null or empty");
            }

            this.setJobArgument(DESTINATION_OWNER, ownerKey);

            return this;
        }

        @Override
        public void validate() throws JobConfigValidationException {
            super.validate();

            try {
                JobArguments arguments = this.getJobArguments();

                String originOwnerKey = arguments.getAsString(ORIGIN_OWNER);
                String destinationOwnerKey = arguments.getAsString(DESTINATION_OWNER);

                if (originOwnerKey == null || originOwnerKey.isBlank()) {
                    String errmsg = "Origin owner key has not been set, or is empty";
                    throw new JobConfigValidationException(errmsg);
                }

                if (destinationOwnerKey == null || destinationOwnerKey.isBlank()) {
                    String errmsg = "Destination owner key has not been set, or is empty";
                    throw new JobConfigValidationException(errmsg);
                }
            }
            catch (ArgumentConversionException e) {
                String errmsg = "One or more required arguments are of the wrong type";
                throw new JobConfigValidationException(errmsg, e);
            }
        }
    }
}
