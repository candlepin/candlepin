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

import com.google.inject.Inject;
import org.candlepin.async.AsyncJob;
import org.candlepin.async.JobConfig;
import org.candlepin.async.JobConfigValidationException;
import org.candlepin.async.JobDataMap;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.async.JobConstraints;
import org.candlepin.async.JobManager;
import org.candlepin.controller.ManifestManager;
import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;
import org.candlepin.sync.ExportResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;



/**
 * A job that generates a compressed file representation of a Consumer. Once the job
 * has completed, its result data will contain the details of the newly created file
 * that can be used to download the file.
 *
 * @see ExportResult
 */
public class ExportJob implements AsyncJob {
    private static Logger log = LoggerFactory.getLogger(ExportJob.class);

    protected static final String OWNER_KEY = "org";
    protected static final String CONSUMER_KEY = "consumer_uuid";
    protected static final String CDN_LABEL = "cdn_label";
    protected static final String WEBAPP_PREFIX = "webapp_prefix";
    protected static final String API_URL = "api_url";
    protected static final String EXTENSION_DATA = "extension_data";

    private static final String JOB_KEY = "EXPORT_JOB";
    private static final String JOB_NAME = "export_manifest";

    /**
     * Job configuration object for the export job
     */
    public static class ExportJobConfig extends JobConfig {
        public ExportJobConfig() {
            this.setJobKey(JOB_KEY)
                .setJobName(JOB_NAME)
                .addConstraint(JobConstraints.uniqueByArgument(CONSUMER_KEY));
        }

        /**
         * Sets the consumer for this export job. The consumer must be
         *
         * @param consumer
         *  the consumer to set for this job
         *
         * @return
         *  a reference to this job config
         */
        public ExportJobConfig setConsumer(Consumer consumer) {
            if (consumer == null) {
                throw new IllegalArgumentException("consumer is null");
            }

            this.setJobArgument(CONSUMER_KEY, consumer.getUuid());
            return this;
        }

        /**
         * Sets the owner for this export job. The owner is not required, but provides the org
         * context in which the job will be executed.
         *
         * @param owner
         *  the owner to set for this job
         *
         * @return
         *  a reference to this job config
         */
        public ExportJobConfig setOwner(Owner owner) {
            if (owner == null) {
                throw new IllegalArgumentException("owner is null");
            }

            this.setJobMetadata(OWNER_KEY, owner.getKey())
                .setLogLevel(owner.getLogLevel());

            return this;
        }

        /**
         * Sets the CDN label for this export job. The CDN label is required by the export job.
         *
         * @param label
         *  the label to set for this job
         *
         * @return
         *  a reference to this job config
         */
        public ExportJobConfig setCdnLabel(String label) {
            this.setJobArgument(CDN_LABEL, label);
            return this;
        }

        /**
         * Sets the web app prefix for this export job.
         *
         * @param prefix
         *  the web app prefix to set for this job
         *
         * @return
         *  a reference to this job config
         */
        public ExportJobConfig setWebAppPrefix(String prefix) {
            this.setJobArgument(WEBAPP_PREFIX, prefix);
            return this;
        }

        /**
         * Sets the api url for this export job.
         *
         * @param url
         *  the api url to set for this job
         *
         * @return
         *  a reference to this job config
         */
        public ExportJobConfig setApiUrl(String url) {
            this.setJobArgument(API_URL, url);
            return this;
        }

        /**
         * Sets the extension data for this export job.
         *
         * @param extData
         *  the extension data to set for this job
         *
         * @return
         *  a reference to this job config
         */
        public ExportJobConfig setExtensionData(Map<String, String> extData) {
            this.setJobArgument(EXTENSION_DATA, extData);
            return this;
        }

        @Override
        public void validate() throws JobConfigValidationException {
            super.validate();

            Map<String, Object> arguments = this.getJobArguments();

            Object consumerUuid = arguments.get(CONSUMER_KEY);
            Object cdnLabel = arguments.get(CDN_LABEL);

            if (!(consumerUuid instanceof String) || ((String) consumerUuid).isEmpty()) {
                String errmsg = "consumer has not been set, or the provided consumer lacks a UUID";
                throw new JobConfigValidationException(errmsg);
            }

            if (!(cdnLabel instanceof String) || ((String) cdnLabel).isEmpty()) {
                String errmsg = "CDN label has not been set, or the provided label is empty";
                throw new JobConfigValidationException(errmsg);
            }
        }
    }


    // Register the job with the JobManager
    static {
        JobManager.registerJob(JOB_KEY, ExportJob.class);
    }

    // NOTE: In order the get the above static block to run when candlepin is initialized,
    //       we need to wrap the static key access in this method. When 'static final'
    //       is used, the static block isn't run due to the way java processes them.
    //
    //       'public static String' can be used but checkstyle warns against this.
    // TODO Are we Ok with this?
    public static String getJobKey() {
        return JOB_KEY;
    }

    private ManifestManager manifestManager;

    @Inject
    public ExportJob(ManifestManager manifestManager) {
        this.manifestManager = manifestManager;
    }

    @Override
    public Object execute(final JobExecutionContext jdata) throws JobExecutionException {
        final JobDataMap map = jdata.getJobData();
        final String consumerUuid = map.getAsString(CONSUMER_KEY);
        final String cdnLabel = map.getAsString(CDN_LABEL);
        final String webAppPrefix = map.getAsString(WEBAPP_PREFIX);
        final String apiUrl = map.getAsString(API_URL);
        final Map<String, String> extensionData = (Map<String, String>) map.get(EXTENSION_DATA);

        log.info("Starting async export for {}", consumerUuid);
        try {
            final ExportResult result = manifestManager.generateAndStoreManifest(
                consumerUuid, cdnLabel, webAppPrefix, apiUrl, extensionData);
            log.info("Async export complete.");
            return result;
        }
        catch (Exception e) {
            throw new JobExecutionException(e.getMessage(), e, false);
        }
    }

    /**
     * Creates a JobConfig configured to execute the export job. Callers may further manipulate
     * the JobConfig as necessary before queuing it.
     *
     * @return
     *  a JobConfig instance configured to execute the export job
     */
    public static ExportJobConfig createJobConfig() {
        return new ExportJobConfig();
    }
}
