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

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.util.Util;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

/**
 * ConsumerComplianceJob
 */
public class ConsumerComplianceJob extends UniqueByEntityJob {
    private static Logger log = LoggerFactory.getLogger(ConsumerComplianceJob.class);

    protected ComplianceRules complianceRules;
    protected ConsumerCurator consumerCurator;
    protected static String prefix = "consumer_compliance_";

    @Inject
    public ConsumerComplianceJob(ConsumerCurator curator, ComplianceRules rules) {
        this.consumerCurator = curator;
        this.complianceRules = rules;
    }

    @Override
    @Transactional
    public void toExecute(JobExecutionContext ctx) throws JobExecutionException {
        try {
            JobDataMap map = ctx.getMergedJobDataMap();
            String uuid = (String) map.get(JobStatus.TARGET_ID);
            Date onDate = (Date) map.get("onDate");
            Boolean calculateComplianceUntill = (Boolean) map.get("calculate_compliance_until");
            Boolean forceUpdate = (Boolean) map.get("force_update");
            Boolean update = (Boolean) map.get("update");
            Consumer consumer = consumerCurator.verifyAndLookupConsumer(uuid);
            consumerCurator.lockAndLoad(consumer);
            // Check consumer's new compliance status and save:
            complianceRules.getStatus(consumer, onDate, calculateComplianceUntill, update);
            if (forceUpdate) {
                consumerCurator.update(consumer);
            }
            ctx.setResult("Compliance computed for consumer: " + consumer.getUuid());
        }
        catch (Exception e) {
            log.error("ConsumerComplianceJob encountered a problem.", e);
            ctx.setResult(e.getMessage());
            throw new JobExecutionException(e.getMessage(), e, false);
        }
    }

    /*
     * Simple check, passes arguments as is, and updates consumer only if needed
     */
    public static JobDetail scheduleStatusCheck(Consumer consumer, Date date,
            boolean calculateCompliantUntil, boolean updateConsumer) {
        JobDataMap map = new JobDataMap();
        if (date != null) {
            map.put("onDate", date);
        }
        map.put("calculate_compliance_until", calculateCompliantUntil);
        map.put("update", updateConsumer);
        map.put("force_update", false);
        return scheduleUsingMap(consumer, map);

    }

    /*
     * Updates consumer unconditionally. convenience API used in a couple of
     * places.
     */
    public static JobDetail scheduleWithForceUpdate(Consumer consumer) {
        JobDataMap map = new JobDataMap();
        map.put("calculate_compliance_until", false);
        map.put("update", false);
        map.put("force_update", true);
        return scheduleUsingMap(consumer, map);

    }

    private static JobDetail scheduleUsingMap(Consumer consumer, JobDataMap map) {

        map.put(JobStatus.OWNER_ID, consumer.getOwner().getKey());
        map.put(JobStatus.TARGET_TYPE, JobStatus.TargetType.CONSUMER);
        map.put(JobStatus.TARGET_ID, consumer.getUuid());

        JobDetail detail = newJob(ConsumerComplianceJob.class).withIdentity(prefix + Util.generateUUID())
                .usingJobData(map).storeDurably(true) // required if we have to
                                                      // postpone the job
                .build();

        return detail;
    }

}
