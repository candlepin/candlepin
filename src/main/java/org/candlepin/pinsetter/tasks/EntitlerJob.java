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

import org.apache.log4j.Logger;
import org.candlepin.controller.Entitler;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.util.Util;

import com.google.inject.Inject;
import com.google.inject.persist.UnitOfWork;

import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * EntitlerJob
 */
public class EntitlerJob extends CpJob {
    private static Logger log = Logger.getLogger(EntitlerJob.class);

    private Entitler entitler;
    private ConsumerCurator consumerCurator;

    @Inject
    public EntitlerJob(Entitler e, ConsumerCurator c, UnitOfWork unitOfWork) {
        super(unitOfWork);
        entitler = e;
        consumerCurator = c;
    }

    public void toExecute(JobExecutionContext ctx) throws JobExecutionException {
        try {
            JobDataMap map = ctx.getMergedJobDataMap();
            Integer qty = map.getInt("quantity");
            String uuid = (String) map.get(JobStatus.TARGET_ID);
            Date entitleDate = (Date) map.get("entitle_date");

            if (map.containsKey("pool_id")) {
                // bindByPool
                String poolId = map.getString("pool_id");
                List<Entitlement> ents = entitler.bindByPool(poolId, uuid, qty);
                entitler.sendEvents(ents);
            }
            else if (map.containsKey("product_ids")) {
                String[] prodIds = (String[]) map.get("product_ids");
                List<Entitlement> ents = entitler.bindByProducts(prodIds, uuid,
                    entitleDate);
                entitler.sendEvents(ents);
            }
            else if (map.containsKey("consumers")) {
                Set<String> consumers = (Set<String>) map.get("consumers");
                executeHealEntireOrg(consumers, entitleDate);
            }

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

    public static JobDetail bindByProducts(String[] prodIds, String uuid,
        Date entitleDate) {
        JobDataMap map = new JobDataMap();
        map.put("product_ids", prodIds);
        map.put(JobStatus.TARGET_TYPE, JobStatus.TargetType.CONSUMER);
        map.put(JobStatus.TARGET_ID, uuid);
        map.put("quantity", 1);
        map.put("entitle_date", entitleDate);

        JobDetail detail = newJob(EntitlerJob.class)
            .withIdentity("bind_by_products_" + Util.generateUUID())
            .usingJobData(map)
            .build();

        return detail;
    }

    public static JobDetail healEntireOrg(Set<String> consumers, Date entitleDate) {
        JobDataMap map = new JobDataMap();
        map.put("consumers", consumers);
        map.put(JobStatus.TARGET_TYPE, JobStatus.TargetType.OWNER);
        map.put(JobStatus.TARGET_ID, "target_id"); // unnecessary, but prevents exception
        map.put("quantity", 1);
        map.put("entitle_date", entitleDate);
        JobDetail detail = newJob(EntitlerJob.class)
            .withIdentity("heal_entire_org_" + Util.generateUUID())
            .usingJobData(map)
            .build();

        return detail;
    }

    private void executeHealEntireOrg(Set<String> consumersIds, Date entitleDate) {
        for (String consumerUuid : consumersIds) {
            Consumer consumer = consumerCurator.getConsumer(consumerUuid);
            // Do not send in product ids.  CandlepinPoolManager will take care
            // of looking up the non or partially compliant products to bind.
            try {
                List<Entitlement> ents = entitler.bindByProducts(null, consumer,
                    entitleDate, true);
                entitler.sendEvents(ents);
            }
            // We want to catch everything and continue.
            // Perhaps add something to surface errors later
            catch (Exception e) {
                log.debug("Healing failed for UUID " + consumerUuid +
                    " with message: " + e.getMessage());
            }
        }
    }
}
