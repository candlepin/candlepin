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

import java.util.Date;
import java.util.List;

import org.candlepin.controller.Entitler;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.OwnerCurator;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.util.Util;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

/**
 * HealEntireOrgJob
 */
public class HealEntireOrgJob extends UniqueByOwnerJob {
    private static Logger log = LoggerFactory.getLogger(HealEntireOrgJob.class);
    protected OwnerCurator ownerCurator;
    protected Entitler entitler;
    protected ConsumerCurator consumerCurator;
    protected static String prefix = "heal_entire_org_";

    @Inject
    public HealEntireOrgJob(Entitler e,
            ConsumerCurator c, OwnerCurator o) {
        this.entitler = e;
        this.consumerCurator = c;
        this.ownerCurator = o;
    }

    @Override
    public void toExecute(JobExecutionContext ctx) throws JobExecutionException {
        try {
            JobDataMap map = ctx.getMergedJobDataMap();
            String ownerId = (String) map.get("ownerId");
            Date entitleDate = (Date) map.get("entitle_date");
            List<String> uuids = ownerCurator.getConsumerUuids(ownerId);
            for (String uuid : uuids) {
                // Do not send in product IDs.  CandlepinPoolManager will take care
                // of looking up the non or partially compliant products to bind.
                try {
                    Consumer consumer = consumerCurator.getConsumer(uuid);
                    List<Entitlement> ents = entitler.bindByProducts(null, consumer,
                        entitleDate, true);
                    entitler.sendEvents(ents);
                }
                // We want to catch everything and continue.
                // Perhaps add something to surface errors later
                catch (Exception e) {
                    log.debug("Healing failed for UUID " + uuid +
                        " with message: " + e.getMessage());
                }
            }
        }
        catch (Exception e) {
            log.error("EntitlerJob encountered a problem.", e);
            ctx.setResult(e.getMessage());
            throw new JobExecutionException(e.getMessage(), e, false);
        }
    }

    public static JobDetail healEntireOrg(String ownerId, Date entitleDate) {
        JobDataMap map = new JobDataMap();
        map.put("ownerId", ownerId);
        map.put(JobStatus.TARGET_TYPE, JobStatus.TargetType.OWNER);
        map.put(JobStatus.TARGET_ID, ownerId);
        map.put("entitle_date", entitleDate);
        JobDetail detail = newJob(HealEntireOrgJob.class)
            .withIdentity("heal_entire_org_" + Util.generateUUID())
            .usingJobData(map)
            .storeDurably(true) //required if we have to postpone the job
            .build();

        return detail;
    }
}
