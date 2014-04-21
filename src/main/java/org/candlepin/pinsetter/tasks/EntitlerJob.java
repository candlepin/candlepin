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

import org.candlepin.controller.Entitler;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.util.Util;

import com.google.inject.Inject;

import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * EntitlerJob
 */
public class EntitlerJob extends KingpinJob {

    private static Logger log = LoggerFactory.getLogger(EntitlerJob.class);
    protected Entitler entitler;
    protected ConsumerCurator consumerCurator;

    @Inject
    public EntitlerJob(Entitler e, ConsumerCurator c) {
        entitler = e;
        consumerCurator = c;
    }

    @Override
    public void toExecute(JobExecutionContext ctx) throws JobExecutionException {
        try {
            JobDataMap map = ctx.getMergedJobDataMap();
            Integer qty = (Integer) map.get("quantity");
            String uuid = (String) map.get(JobStatus.TARGET_ID);

            String poolId = map.getString("pool_id");
            List<Entitlement> ents = entitler.bindByPool(poolId, uuid, qty);
            entitler.sendEvents(ents);

            ctx.setResult("Entitlements created for owner");
        }
        // Catch any exception that is fired and re-throw as a JobExecutionException
        // so that the job will be properly cleaned up on failure.
        catch (Exception e) {
            log.error("EntitlerJob encountered a problem.", e);
            ctx.setResult(e.getMessage());
            throw new JobExecutionException(e.getMessage(), e, false);
        }
    }

    public static JobDetail bindByPool(String poolId, String uuid, Integer qty) {
        JobDataMap map = new JobDataMap();
        map.put("pool_id", poolId);
        map.put(JobStatus.TARGET_TYPE, JobStatus.TargetType.CONSUMER);
        map.put(JobStatus.TARGET_ID, uuid);
        map.put("quantity", qty);

        JobDetail detail = newJob(EntitlerJob.class)
            .withIdentity("bind_by_pool_" + Util.generateUUID())
            .requestRecovery(false) // do not recover the job upon restarts
            .usingJobData(map)
            .build();

        return detail;
    }
}
