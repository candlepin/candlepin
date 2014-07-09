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

import org.candlepin.controller.PoolManager;

import com.google.inject.Inject;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ExpiredPoolsJob: Runs periodically throughout the day to look for any pools past their
 * expiration date.
 *
 * If found we clean up the subscription, pool, and it's entitlements. This is primarily
 * done on a scheduled basis to make sure we re-source derived pools if the stack has
 * other still valid entitlements.
 */
public class ExpiredPoolsJob extends KingpinJob {

    // Every 4 hours:
    public static final String DEFAULT_SCHEDULE = "0 0 0/4 * * ?";

    private PoolManager poolManager;

    private static Logger log = LoggerFactory.getLogger(ExpiredPoolsJob.class);

    @Inject
    public ExpiredPoolsJob(PoolManager poolManager) {
        this.poolManager = poolManager;
    }

    public void toExecute(JobExecutionContext ctx) throws JobExecutionException {

        log.info("Executing ExpiredPoolsJob");
        poolManager.cleanupExpiredPools();
    }
}
