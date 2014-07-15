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

import org.candlepin.controller.PoolManager;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.util.Util;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Asynchronous job for refreshing the entitlement pools for specific
 * {@link Owner}.
 */
public class RefreshPoolsJob extends UniqueByOwnerJob {

    private static Logger log = LoggerFactory.getLogger(RefreshPoolsJob.class);
    private OwnerCurator ownerCurator;
    private PoolManager poolManager;

    public static final String LAZY_REGEN = "lazy_regen";
    protected static String prefix = "refresh_pools_";

    @Inject
    public RefreshPoolsJob(OwnerCurator ownerCurator, PoolManager poolManager) {
        this.ownerCurator = ownerCurator;
        this.poolManager = poolManager;
    }

    /**
     * {@inheritDoc}
     *
     * Executes {@link PoolManager#refreshPools(org.candlepin.model.Owner)}
     * as a pinsetter job.
     *
     * @param context the job's execution context
     */
    @Transactional
    public void toExecute(JobExecutionContext context) throws JobExecutionException {
        try {
            JobDataMap map = context.getMergedJobDataMap();
            String ownerKey = map.getString(JobStatus.TARGET_ID);
            Boolean lazy = map.getBoolean(LAZY_REGEN);
            Owner owner = ownerCurator.lookupByKey(ownerKey);
            if (owner == null) {
                context.setResult("Nothing to do. Owner no longer exists");
                return;
            }

            // Assume that we verified the request in the resource layer:
            poolManager.getRefresher(lazy).add(owner).run();
            context.setResult("Pools refreshed for owner " + owner.getDisplayName());
        }
        // Catch any exception that is fired and re-throw as a
        // JobExecutionException so that the job will be properly
        // cleaned up on failure.
        catch (Exception e) {
            log.error("RefreshPoolsJob encountered a problem.", e);
            context.setResult(e.getMessage());
            throw new JobExecutionException(e.getMessage(), e, false);
        }
    }

    /**
     * Creates a {@link JobDetail} that runs this job for the given {@link Owner}.
     *
     * @param owner the owner to refresh
     * @return a {@link JobDetail} that describes the job run
     */
    public static JobDetail forOwner(Owner owner, Boolean lazy) {
        JobDataMap map = new JobDataMap();
        map.put(JobStatus.TARGET_TYPE, JobStatus.TargetType.OWNER);
        map.put(JobStatus.TARGET_ID, owner.getKey());
        map.put(LAZY_REGEN, lazy);

        // Not sure if this is the best way to go:
        // Give each job a UUID to ensure that it is unique
        JobDetail detail = newJob(RefreshPoolsJob.class)
            .withIdentity("refresh_pools_" + Util.generateUUID())
            .requestRecovery(true) // recover the job upon restarts
            .usingJobData(map)
            .storeDurably(true) // required if we have to postpone the job
            .build();

        return detail;
    }

}
