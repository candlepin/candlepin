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
package org.candlepin.async;


import org.candlepin.async.tasks.JobCleaner;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;


/**
 * Provides maximum age for which a job can live.
 */
public class MaxJobAgeProvider {

    private static final Logger log = LoggerFactory.getLogger(MaxJobAgeProvider.class);
    public static final String CFG_MAX_JOB_AGE = "max_job_age_in_minutes";
    public static final int CFG_DEFAULT_MAX_JOB_AGE = 10080; // 7 days

    private final Configuration config;

    @Inject
    public MaxJobAgeProvider(Configuration config) {
        this.config = Objects.requireNonNull(config);
    }

    public int inMinutes() throws JobExecutionException {
        String cfgName = ConfigProperties.jobConfig(JobCleaner.JOB_KEY, CFG_MAX_JOB_AGE);
        int maxAgeInMinutes = this.config.getInt(cfgName, CFG_DEFAULT_MAX_JOB_AGE);
        if (maxAgeInMinutes < 1) {
            String errmsg = String.format("Invalid value for max age, must be a positive integer: %s",
                maxAgeInMinutes);

            log.error(errmsg);
            throw new JobExecutionException(errmsg, true);
        }

        return maxAgeInMinutes;
    }

}
