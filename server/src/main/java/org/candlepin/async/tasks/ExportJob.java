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
import org.candlepin.async.JobBuilder;
import org.candlepin.async.JobDataMap;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.async.JobConstraints;
import org.candlepin.async.JobManager;
import org.candlepin.controller.ManifestManager;
import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;
import org.candlepin.sync.ExportResult;
import org.candlepin.util.Util;

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

    protected static final String OWNER_KEY = "org";
    protected static final String CONSUMER_KEY = "consumer_uuid";
    protected static final String CDN_LABEL = "cdn_label";
    protected static final String WEBAPP_PREFIX = "webapp_prefix";
    protected static final String API_URL = "api_url";
    protected static final String EXTENSION_DATA = "extension_data";

    private static final String JOB_KEY = "EXPORT_JOB";

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

    private static Logger log = LoggerFactory.getLogger(ExportJob.class);

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
     * Schedules the generation of a consumer export. This job starts immediately.
     *
     * @param consumer the target consumer
     * @param cdnLabel the cdn label
     * @param webAppPrefix the web app prefix
     * @param apiUrl the api url
     * @return a JobDetail representing the job to be started.
     */
    public static JobBuilder scheduleExport(
        final Consumer consumer,
        final Owner owner,
        final String cdnLabel,
        final String webAppPrefix,
        final String apiUrl,
        final Map<String, String> extensionData
    ) {
        return JobBuilder.forJob(JOB_KEY)
            .setJobName("export-" + Util.generateUUID())
            .setJobArgument(CONSUMER_KEY, consumer.getUuid())
            .setJobArgument(CDN_LABEL, cdnLabel)
            .setJobArgument(WEBAPP_PREFIX, webAppPrefix)
            .setJobArgument(API_URL, apiUrl)
            .setJobArgument(EXTENSION_DATA, extensionData)
            .setJobMetadata(OWNER_KEY, owner.getKey())
            .setLogLevel(owner.getLogLevel())
            .addConstraint(JobConstraints.uniqueByArgument(CONSUMER_KEY));
    }
}
