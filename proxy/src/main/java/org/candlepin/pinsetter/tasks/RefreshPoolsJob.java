/**
 * Copyright (c) 2009 Red Hat, Inc.
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
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.util.Util;

import com.google.inject.Inject;

import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * Asynchronous job for refreshing the entitlement pools for specific
 * {@link Owner}.
 */
public class RefreshPoolsJob implements Job {

    private OwnerCurator ownerCurator;
    private PoolManager poolManager;

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
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String ownerKey = context.getMergedJobDataMap().getString(JobStatus.TARGET_ID);
        Owner owner = ownerCurator.lookupByKey(ownerKey);
        if (owner == null) {
            context.setResult("Nothing to do. Owner no longer exists");
            return;
        }

        // Assume that we verified the request in the resource layer:
        poolManager.refreshPools(owner);

        context.setResult("Pools refreshed for owner " + owner.getDisplayName());
    }

    /**
     * Creates a {@link JobDetail} that runs this job for the given {@link Owner}.
     *
     * @param owner the owner to refresh
     * @return a {@link JobDetail} that describes the job run
     */
    public static JobDetail forOwner(Owner owner) {
        // Not sure if this is the best way to go:
        // Give each job a UUID to ensure that it is unique
        JobDetail detail = new JobDetail("refresh_pools_" + Util.generateUUID(),
                RefreshPoolsJob.class);
        // recover the job upon restarts
        detail.setRequestsRecovery(true);
        JobDataMap map = new JobDataMap();
        map.put(JobStatus.TARGET_TYPE, JobStatus.TargetType.OWNER);
        map.put(JobStatus.TARGET_ID, owner.getKey());

        detail.setJobDataMap(map);

        return detail;
    }

}
