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

import org.candlepin.common.config.Configuration;
import org.candlepin.controller.ManifestManager;

import com.google.inject.Inject;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ExportCleaner
 *
 * This pinsetter task examines the directory where the exporter compiles its
 * information and resultant zip file. Data that is more that a day old will
 * be expunged.
 *
 * @deprecated This job has been deprecated and now does noting. It has
 * been replaced by the {@link ManifestCleanerJob} and exists as to not break
 * older deployments.
 *
 * @see ManifestCleanerJob
 *
 */
@Deprecated
public class ExportCleaner extends KingpinJob {

    public static final String DEFAULT_SCHEDULE = "0 0 12 * * ?";
    private static Logger log = LoggerFactory.getLogger(ExportCleaner.class);

    private Configuration config;
    private ManifestManager manifestManager;

    @Inject
    public ExportCleaner(Configuration config, ManifestManager manifestService) {
        this.config = config;
        this.manifestManager = manifestService;
    }

    @Override
    public void toExecute(JobExecutionContext arg0) throws JobExecutionException {
        log.warn("ExportCleaner job has been deprecated. Please unschedule.");
    }

}
