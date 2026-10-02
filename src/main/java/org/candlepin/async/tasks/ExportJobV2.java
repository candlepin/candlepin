/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
import org.candlepin.controller.ManifestManager;
import org.candlepin.model.Consumer;
import org.candlepin.sync.ExportResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExportJobV2 implements AsyncJob {
    private static final Logger log = LoggerFactory.getLogger(ExportJobV2.class);

    public static final String JOB_KEY = "ExportJobV2";
    public static final String JOB_NAME = "Export Manifest V2";

    protected static final String CONSUMER_KEY = "consumer_uuid";

    public static ExportJobConfigV2 createJobConfig() {
        return new ExportJobConfigV2();
    }

    /**
     * Job configuration object for the export job
     */
    public static class ExportJobConfigV2 extends JobConfig<ExportJobConfigV2> {

        public ExportJobConfigV2() {
            this.setJobKey(JOB_KEY)
                .setJobName(JOB_NAME)
                .addConstraint(JobConstraints.uniqueByArguments(CONSUMER_KEY));
        }
        /**
         * Sets the consumer for this export job. The consumer is required, and also provides the
         * context in which the job will be executed.
         *
         * @param consumer
         *  the consumer to set for this job
         *
         * @return
         *  a reference to this job config
         */
        public ExportJobConfigV2 setConsumer(Consumer consumer) {
            if (consumer == null) {
                throw new IllegalArgumentException("consumer is null");
            }

            this.setJobArgument(CONSUMER_KEY, consumer.getUuid());
            return this;
        }

        @Override
        public void validate() throws JobConfigValidationException {
            super.validate();

            try {
                JobArguments arguments = this.getJobArguments();

                String consumerUuid = arguments.getAsString(CONSUMER_KEY);

                if (consumerUuid == null || consumerUuid.isEmpty()) {
                    String errmsg = "consumer has not been set, or the provided consumer lacks a UUID";
                    throw new JobConfigValidationException(errmsg);
                }
            }
            catch (ArgumentConversionException e) {
                String errmsg = "One or more required arguments are of the wrong type";
                throw new JobConfigValidationException(errmsg, e);
            }
        }

    }
    private final ManifestManager manifestManager;

    public ExportJobV2(ManifestManager manifestManager) {
        this.manifestManager = manifestManager;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobArguments args = context.getJobArguments();

        String consumerUuid = args.getAsString(CONSUMER_KEY);

        log.info("Starting async export for {}", consumerUuid);
        try {
            ExportResult result = manifestManager.generateAndStoreManifestV2(consumerUuid);

            log.info("Async export complete");

            context.setJobResult(result);
        }
        catch (Exception e) {
            throw new JobExecutionException(e.getMessage(), e, false);
        }
    }
}
