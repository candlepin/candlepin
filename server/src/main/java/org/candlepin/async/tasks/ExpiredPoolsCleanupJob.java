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

import org.candlepin.async.AsyncJob;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.controller.PoolManager;

import com.google.inject.Inject;



/**
 * ExpiredPoolsCleanupJob: Runs periodically throughout the day to look for any pools past their
 * expiration date.
 *
 * If found we clean up the subscription, pool, and it's entitlements. This is primarily
 * done on a scheduled basis to make sure we re-source derived pools if the stack has
 * other still valid entitlements.
 */
public class ExpiredPoolsCleanupJob implements AsyncJob {
    public static final String JOB_KEY = "EXPIRED_POOLS_CLEANUP";
    public static final String JOB_NAME = "expired pools cleanup";

    public static final String DEFAULT_SCHEDULE = "0 0 0/1 * * ?"; // Every hour

    private PoolManager poolManager;

    @Inject
    public ExpiredPoolsCleanupJob(PoolManager poolManager) {
        if (poolManager == null) {
            throw new IllegalArgumentException("poolManager is null");
        }

        this.poolManager = poolManager;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object execute(JobExecutionContext context) throws JobExecutionException {
        this.poolManager.cleanupExpiredPools();
        return "Expired pools cleanup completed successfully";
    }
}
