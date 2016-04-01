/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.pinsetter.tasks;

import static org.quartz.JobBuilder.newJob;

import org.candlepin.common.exceptions.ForbiddenException;
import org.candlepin.controller.ManifestManager;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.sync.ExportResult;
import org.candlepin.util.Util;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * A job that generates a compressed file representation of a Consumer. Once the job
 * has completed, its result data will contain the details of the newly created file
 * that can be used to download the file.
 *
 * @see ExportResult
 *
 */
public class ExportJob extends UniqueByEntityJob {

    protected static final String CDN_LABEL = "cdn_label";
    protected static final String WEBAPP_PREFIX = "webapp_prefix";
    protected static final String API_URL = "api_url";

    private static Logger log = LoggerFactory.getLogger(ExportJob.class);

    private ManifestManager manifestManager;
    private ConsumerCurator consumerCurator;
    private I18n i18n;

    @Inject
    public ExportJob(ManifestManager manifestManager, ConsumerCurator consumerCurator,
        Provider<I18n> i18nProvider) {
        this.manifestManager = manifestManager;
        this.consumerCurator = consumerCurator;
        this.i18n = i18nProvider.get();
    }

    @Override
    public void toExecute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap map = context.getMergedJobDataMap();
        String consumerUuid = map.getString(JobStatus.TARGET_ID);
        String cdnLabel = map.getString(CDN_LABEL);
        String webAppPrefix = map.getString(WEBAPP_PREFIX);
        String apiUrl = map.getString(API_URL);

        Consumer consumer = validateConsumer(consumerUuid);

        log.info("Starting async export for {}", consumerUuid);
        try {
            ExportResult result =
                manifestManager.generateAndStoreManifest(consumer, cdnLabel, webAppPrefix, apiUrl);
            context.setResult(result);
            log.info("Async export complete.");
        }
        catch (Exception e) {
            throw new JobExecutionException(e.getMessage(), e, false);
        }
    }

    private Consumer validateConsumer(String consumerUuid) {
        // FIXME Should this be testing the CdnLabel as well?
        Consumer consumer = consumerCurator.verifyAndLookupConsumer(consumerUuid);
        if (consumer.getType() == null ||
            !consumer.getType().isManifest()) {
            throw new ForbiddenException(
                i18n.tr(
                    "Unit {0} cannot be exported. " +
                    "A manifest cannot be made for units of type ''{1}''.",
                    consumerUuid, consumer.getType().getLabel()));
        }
        return consumer;
    }

    /**
     * Schedules the generation of a consumer export. This job starts immediately.
     *
     * @param consumer the target consumer
     * @param cdnLabel
     * @param webAppPrefix
     * @param apiUrl
     * @return a JobDetail representing the job to be started.
     */
    public static JobDetail scheduleExport(Consumer consumer, String cdnLabel, String webAppPrefix,
        String apiUrl) {
        JobDataMap map = new JobDataMap();
        map.put(JobStatus.OWNER_ID, consumer.getOwner().getKey());
        map.put(JobStatus.TARGET_TYPE, JobStatus.TargetType.CONSUMER);
        map.put(JobStatus.TARGET_ID, consumer.getUuid());
        map.put(CDN_LABEL, cdnLabel);
        map.put(WEBAPP_PREFIX, webAppPrefix);
        map.put(API_URL, apiUrl);

        return newJob(ExportJob.class)
            .withIdentity("export_" + Util.generateUUID())
            .usingJobData(map)
            .build();
    }

}
