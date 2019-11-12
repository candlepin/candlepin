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

import static org.quartz.JobBuilder.*;

import org.candlepin.common.filter.LoggingFilter;
import org.candlepin.controller.AutobindDisabledForOwnerException;
import org.candlepin.controller.AutobindHypervisorDisabledException;
import org.candlepin.controller.Entitler;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.resource.dto.AutobindData;
import org.candlepin.util.Util;

import org.apache.log4j.MDC;
import org.jboss.resteasy.spi.BadRequestException;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.Date;
import java.util.List;

/**
 * HealEntireOrgJob
 */
public class HealEntireOrgJob extends UniqueByEntityJob {
    private static Logger log = LoggerFactory.getLogger(HealEntireOrgJob.class);
    protected static String prefix = "heal_entire_org_";

    protected OwnerCurator ownerCurator;
    protected Entitler entitler;
    protected ConsumerCurator consumerCurator;
    private I18n i18n;

    @Inject
    public HealEntireOrgJob(Entitler e, ConsumerCurator c, OwnerCurator o, I18n i18n) {
        this.entitler = e;
        this.consumerCurator = c;
        this.ownerCurator = o;
        this.i18n = i18n;
    }

    @Override
    public void toExecute(JobExecutionContext ctx) throws JobExecutionException {
        try {
            // NOTE: ownerId is actually the owner key here.

            JobDataMap map = ctx.getMergedJobDataMap();
            String ownerId = (String) map.get(JobStatus.TARGET_ID);
            Owner owner = ownerCurator.getByKey(ownerId);
            if (owner.isAutobindDisabled() || owner.isContentAccessEnabled()) {
                String caMessage = owner.isContentAccessEnabled() ?
                    " because of the content access mode setting" : "";
                throw new BadRequestException(i18n.tr("Auto-attach is disabled for owner {0}{1}.",
                    owner.getKey(), caMessage));
            }

            Date entitleDate = (Date) map.get("entitle_date");

            for (String uuid : ownerCurator.getConsumerUuids(owner).list()) {
                // Do not send in product IDs.  CandlepinPoolManager will take care
                // of looking up the non or partially compliant products to bind.
                try {
                    Consumer consumer = consumerCurator.getConsumer(uuid);
                    healSingleConsumer(consumer, owner, entitleDate);
                }
                // We want to catch everything and continue.
                // Perhaps add something to surface errors later
                catch (Exception e) {
                    log.debug("Healing failed for UUID \"{}\" with message: {}", uuid, e.getMessage());
                }
            }
        }
        catch (Exception e) {
            log.error("EntitlerJob encountered a problem.", e);
            ctx.setResult(e.getMessage());
            throw new JobExecutionException(e.getMessage(), e, false);
        }
    }

    /*
     * Each consumer heal should be a separate transaction
     */
    @Transactional
    private void healSingleConsumer(Consumer consumer, Owner owner, Date date)
        throws AutobindDisabledForOwnerException, AutobindHypervisorDisabledException {

        List<Entitlement> ents = entitler.bindByProducts(AutobindData.create(consumer, owner).on(date), true);
        entitler.sendEvents(ents);
    }

    public static JobDetail healEntireOrg(Owner owner, Date entitleDate) {
        JobDataMap map = new JobDataMap();
        map.put(JobStatus.OWNER_ID, owner.getKey());
        map.put(JobStatus.OWNER_LOG_LEVEL, owner.getLogLevel());
        map.put(JobStatus.TARGET_TYPE, JobStatus.TargetType.OWNER);
        map.put(JobStatus.TARGET_ID, owner.getKey());
        map.put("entitle_date", entitleDate);
        map.put(JobStatus.CORRELATION_ID, MDC.get(LoggingFilter.CSID));

        JobDetail detail = newJob(HealEntireOrgJob.class)
            .withIdentity("heal_entire_org_" + Util.generateUUID())
            .usingJobData(map)
            .storeDurably(true) //required if we have to postpone the job
            .build();

        return detail;
    }
}
