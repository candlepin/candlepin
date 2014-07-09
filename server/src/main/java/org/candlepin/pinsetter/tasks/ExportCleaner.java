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

import java.io.File;
import java.io.IOException;
import java.util.Date;

import org.apache.commons.io.FileUtils;
import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.util.Util;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

/**
 * ExportCleaner
 *
 * This pinsetter task examines the directory where the exporter compiles its
 * information and resultant zip file. Data that is more that a day old will
 * be expunged.
 *
 */
public class ExportCleaner extends KingpinJob {

    public static final String DEFAULT_SCHEDULE = "0 0 12 * * ?";
    private Config config;
    private static Logger log = LoggerFactory.getLogger(ExportCleaner.class);

    @Inject
    public ExportCleaner(Config config) {
        this.config = config;
    }

    @Override
    public void toExecute(JobExecutionContext arg0) throws JobExecutionException {
        File baseDir = new File(config.getString(ConfigProperties.SYNC_WORK_DIR));
        Date deadLineDt = Util.yesterday();

        long dirCount = 0;
        long delCount = 0;
        long leftCount = 0;
        if (baseDir.listFiles() != null) {
            dirCount =  baseDir.listFiles().length;
            for (File f : baseDir.listFiles()) {
                if (f.lastModified() < deadLineDt.getTime()) {
                    try {
                        FileUtils.deleteDirectory(f);
                        delCount++;
                    }
                    catch (IOException io) {
                        log.error("Unable to delete export directory that is old enough " +
                            "to delete", io);
                    }
                }
                else {
                    leftCount++;
                }
            }
        }
        log.info("Export Data Cleaner run:");
        log.info("Begining directory count: " + dirCount);
        log.info("Directories deleted: " + delCount);
        log.info("Directories remaining: " + leftCount);
    }
}
