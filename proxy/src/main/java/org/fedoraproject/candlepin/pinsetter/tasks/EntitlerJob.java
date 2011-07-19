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
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.util.Util;

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

    @Override
    public void execute(JobExecutionContext ctx) throws JobExecutionException {
        JobDataMap map = ctx.getMergedJobDataMap();
        Entitler entitler = (Entitler) map.get("entitler");
        Consumer c = (Consumer) map.get("consumer");
        Integer qty = map.getInt("quantity");

        if (map.containsKey("pool_id")) {
            // bindByPool
            String poolId = map.getString("pool_id");
            List<Entitlement> ents = entitler.bindByPool(poolId, c, qty);
            entitler.sendEvents(ents);
        }
        else if (map.containsKey("product_ids")) {
            String[] prodIds = (String[]) map.get("product_ids");
            List<Entitlement> ents = entitler.bindByProducts(prodIds, c, qty);
            entitler.sendEvents(ents);
        }

        ctx.setResult("Entitlements created for owner");
    }

    public static JobDetail bindByPool(String poolId, Consumer c, Integer qty,
        Entitler entitler) {

        JobDetail detail = new JobDetail("bind_by_pool_" + Util.generateUUID(),
            EntitlerJob.class);

        JobDataMap map = new JobDataMap();
        map.put("pool_id", poolId);
        map.put("consumer", c);
        map.put("quantity", qty);
        map.put("entitler", entitler);

        detail.setJobDataMap(map);

        return detail;
    }

    public static JobDetail bindByProducts(String[] prodIds, Consumer c,
        Integer qty, Entitler entitler) {

        JobDetail detail = new JobDetail("bind_by_products_" +
            Util.generateUUID(), EntitlerJob.class);

        JobDataMap map = new JobDataMap();
        map.put("product_ids", prodIds);
        map.put("consumer", c);
        map.put("quantity", qty);
        map.put("entitler", entitler);

        detail.setJobDataMap(map);

        return detail;
    }
}
