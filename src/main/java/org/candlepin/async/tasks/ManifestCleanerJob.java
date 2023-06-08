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
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.controller.ManifestManager;
import org.candlepin.model.ManifestFileRecord;
import org.candlepin.sync.file.ManifestFileService;
import org.candlepin.util.Util;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Objects;

import javax.inject.Inject;



/**
* The manifest cleaner job is responsible for cleaning up all artifacts that may result
* from importing/exporting manifest files. It has three key responsibilities:
* <p>
* 1) It examines the scratch directory where the importer/exporter writes temp
*    files and deletes any that are older than a configured age (default 24 hours).
* <p>
* 2) Deletes expired files that live on the {@link ManifestFileService} (default 24 hours).
* <p>
* 3) Deletes any rogue {@link ManifestFileRecord}s that exist without a file in the service. This case should
*    only happen if the {@link ManifestFileService} implementation is remote, has already deleted the file,
*    and deleting of the {@link ManifestFileRecord} fails for some reason.
*
*/
public class ManifestCleanerJob implements AsyncJob {
    private static final Logger log = LoggerFactory.getLogger(ManifestCleanerJob.class);

    public static final String JOB_KEY = "ManifestCleanerJob";
    public static final String JOB_NAME = "Manifest Cleaner";

    public static final String CFG_MAX_AGE_IN_MINUTES = "max_age_in_minutes";
    public static final int DEFAULT_MAX_AGE_IN_MINUTES = 1440;
    // Every noon
    public static final String DEFAULT_SCHEDULE = "0 0 12 * * ?";

    private final Configuration config;
    private final ManifestManager manifestManager;

    @Inject
    public ManifestCleanerJob(Configuration config, ManifestManager manifestService) {
        this.config = Objects.requireNonNull(config);
        this.manifestManager = Objects.requireNonNull(manifestService);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        File baseDir = new File(config.getString(ConfigProperties.SYNC_WORK_DIR));
        int maxAgeInMinutes = config.getInt(ConfigProperties.jobConfig(JOB_KEY, CFG_MAX_AGE_IN_MINUTES));

        if (maxAgeInMinutes < 1) {
            String errmsg = String.format("Invalid value for max age, must be a positive integer: %s",
                maxAgeInMinutes);

            log.error(errmsg);
            throw new JobExecutionException(errmsg, true);
        }

        log.info("Manifest cleanup started");
        log.info("Max Age: {} mins ({} hours)", maxAgeInMinutes, maxAgeInMinutes / 60);
        cleanupExportWorkDirs(baseDir, maxAgeInMinutes);
        manifestServiceCleanup(maxAgeInMinutes);

        context.setJobResult("Manifest cleanup completed successfully");
    }

    private void cleanupExportWorkDirs(File baseDir, int maxAgeInMinutes) {
        long dirCount = 0;
        long delCount = 0;

        Date cutOff = Util.addMinutesToDt(maxAgeInMinutes * -1);

        if (baseDir.listFiles() != null) {
            dirCount = baseDir.listFiles().length;
            for (File f : baseDir.listFiles()) {
                if (f.lastModified() < cutOff.getTime()) {
                    try {
                        log.info("Deleting old manifest directory: {}", f.getAbsolutePath());

                        FileUtils.deleteDirectory(f);
                        delCount++;
                    }
                    catch (IOException io) {
                        String errorMsg = String.format("Unable to delete export directory that is old " +
                            "enough to delete: %s", f.getAbsolutePath());
                        log.error(errorMsg, io);
                    }
                }
            }
        }

        log.info("Begining directory count: {}", dirCount);
        log.info("Directories deleted: {}", delCount);
        log.info("Directories remaining: {}", (dirCount - delCount));
    }

    private void manifestServiceCleanup(int maxAgeInMinutes) {
        int deleted = manifestManager.cleanup(maxAgeInMinutes);
        log.info("Deleted from file service: {}", deleted);
    }
}
