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

import org.fedoraproject.candlepin.controller.Entitler;
import org.fedoraproject.candlepin.exceptions.CandlepinException;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.pinsetter.core.model.JobStatus;
import org.fedoraproject.candlepin.util.Util;

import com.google.inject.Inject;

import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.List;

/**
 * EntitlerJob
 */
public class EntitlerJob implements Job {

    private Entitler entitler;

    @Inject
    public EntitlerJob(Entitler e) {
        entitler = e;
    }

    @Override
    public void execute(JobExecutionContext ctx) throws JobExecutionException {
        try {
            JobDataMap map = ctx.getMergedJobDataMap();
            Integer qty = map.getInt("quantity");
            String uuid = (String) map.get(JobStatus.TARGET_ID);

            if (map.containsKey("pool_id")) {
                // bindByPool
                String poolId = map.getString("pool_id");
                List<Entitlement> ents = entitler.bindByPool(poolId, uuid, qty);
                entitler.sendEvents(ents);
            }
            else if (map.containsKey("product_ids")) {
                String[] prodIds = (String[]) map.get("product_ids");
                List<Entitlement> ents = entitler.bindByProducts(prodIds, uuid);
                entitler.sendEvents(ents);
            }

            ctx.setResult("Entitlements created for owner");
        }
        catch (CandlepinException ce) {
            throw new JobExecutionException(ce.getMessage(), ce, false);
        }
    }

    public static JobDetail bindByPool(String poolId, String uuid, Integer qty) {

        JobDetail detail = new JobDetail("bind_by_pool_" + Util.generateUUID(),
            EntitlerJob.class);

        JobDataMap map = new JobDataMap();
        map.put("pool_id", poolId);
        map.put(JobStatus.TARGET_TYPE, JobStatus.TargetType.CONSUMER);
        map.put(JobStatus.TARGET_ID, uuid);
        map.put("quantity", qty);

        detail.setJobDataMap(map);

        return detail;
    }

    public static JobDetail bindByProducts(String[] prodIds, String uuid) {

        JobDetail detail = new JobDetail("bind_by_products_" +
            Util.generateUUID(), EntitlerJob.class);

        JobDataMap map = new JobDataMap();
        map.put("product_ids", prodIds);
        map.put(JobStatus.TARGET_TYPE, JobStatus.TargetType.CONSUMER);
        map.put(JobStatus.TARGET_ID, uuid);
        map.put("quantity", 1);

        detail.setJobDataMap(map);

        return detail;
    }
}
