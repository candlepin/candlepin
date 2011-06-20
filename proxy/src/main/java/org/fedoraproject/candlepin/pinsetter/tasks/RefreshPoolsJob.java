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
package org.fedoraproject.candlepin.pinsetter.tasks;

import com.google.inject.Inject;

import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.auth.SystemPrincipal;
import org.fedoraproject.candlepin.controller.PoolManager;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.pinsetter.core.model.JobStatus;
import org.fedoraproject.candlepin.util.Util;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
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
     * Executes {@link PoolManager#refreshPools(org.fedoraproject.candlepin.model.Owner)}
     * as a pinsetter job.
     *
     * @param context the job's execution context
     */
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        String ownerKey = context.getMergedJobDataMap().getString(JobStatus.OWNER_KEY);
        Owner owner = ownerCurator.lookupByKey(ownerKey);

        // Assume that we verified the request in the resource layer:
        Principal system = new SystemPrincipal();
        ResteasyProviderFactory.pushContext(Principal.class, system);
        poolManager.refreshPools(owner);
        ResteasyProviderFactory.popContextData(Principal.class);

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
        JobDataMap map = new JobDataMap();
        map.put(JobStatus.OWNER_KEY, owner.getKey());

        detail.setJobDataMap(map);

        return detail;
    }

}
