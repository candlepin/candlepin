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

import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.Entitler;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.JobCurator;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.dto.PoolIdAndErrors;
import org.candlepin.model.dto.PoolIdAndQuantity;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.policy.ValidationError;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.js.entitlement.EntitlementRulesTranslator;
import org.candlepin.util.Util;

import com.google.inject.Inject;

import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * EntitlerJob
 */
public class EntitlerJob extends KingpinJob {
    private static Logger log = LoggerFactory.getLogger(EntitlerJob.class);

    @Inject private static Configuration conf;

    protected I18n i18n;
    protected Entitler entitler;
    protected ConsumerCurator consumerCurator;
    protected PoolCurator poolCurator;

    @Inject
    public EntitlerJob(Entitler e, ConsumerCurator c, PoolCurator p, I18n i18n) {
        this.entitler = e;
        this.consumerCurator = c;
        this.i18n = i18n;
        this.poolCurator = p;
    }

    @Override
    public void toExecute(JobExecutionContext ctx) throws JobExecutionException {
        try {
            JobDataMap map = ctx.getMergedJobDataMap();
            String uuid = (String) map.get(JobStatus.TARGET_ID);
            PoolIdAndQuantity[] poolQuantities = (PoolIdAndQuantity[]) map.get("pool_and_quanities");
            Map<String, Integer> poolMap = new HashMap<String, Integer>();
            for (PoolIdAndQuantity poolIdAndQuantity : poolQuantities) {
                poolMap.put(poolIdAndQuantity.getPoolId(), poolIdAndQuantity.getQuantity());
            }
            List<Entitlement> ents = entitler.bindByPoolQuantities(uuid, poolMap);
            entitler.sendEvents(ents);

            PoolIdAndQuantity[] consumed = new PoolIdAndQuantity[ents.size()];
            for (int i = 0; i < ents.size(); i++) {
                consumed[i] = new PoolIdAndQuantity(ents.get(i).getPool().getId(), ents.get(i)
                        .getQuantity());
            }
            ctx.setResult(consumed);
        }
        catch (EntitlementRefusedException e) {
            log.error("EntitlerJob encountered a problem.", e);
            log.debug("translating errors");
            Map<String, ValidationResult> validationResults = e.getResults();
            List<Pool> pools = poolCurator.listAllByIds(validationResults.keySet());

            EntitlementRulesTranslator translator = new EntitlementRulesTranslator(i18n);
            List<PoolIdAndErrors> poolErrors = new ArrayList<PoolIdAndErrors>();
            for (Pool pool : pools) {
                List<String> errorMessages = new ArrayList<String>();
                for (ValidationError error : validationResults.get(pool.getId()).getErrors()) {
                    errorMessages.add(translator.poolErrorToMessage(pool, error));
                }
                poolErrors.add(new PoolIdAndErrors(pool.getId(), errorMessages));
            }
            ctx.setResult(poolErrors);
        }
        // Catch any exception that is fired and re-throw as a JobExecutionException
        // so that the job will be properly cleaned up on failure.
        catch (Exception e) {
            log.error("EntitlerJob encountered a problem.", e);
            throw new JobExecutionException(e.getMessage(), e, false);
        }
    }

    public static JobDetail bindByPool(String poolId, Consumer consumer, Integer qty) {
        PoolIdAndQuantity[] poolQuantities = new PoolIdAndQuantity[1];
        poolQuantities[0] = new PoolIdAndQuantity(poolId, qty);
        return bindByPoolAndQuantities(consumer, poolQuantities);
    }

    public static JobDetail bindByPoolAndQuantities(Consumer consumer, PoolIdAndQuantity... poolQuantities) {
        JobDataMap map = new JobDataMap();
        map.put(JobStatus.OWNER_ID, consumer.getOwner().getKey());
        map.put("pool_and_quanities", poolQuantities);
        map.put(JobStatus.TARGET_TYPE, JobStatus.TargetType.CONSUMER);
        map.put(JobStatus.TARGET_ID, consumer.getUuid());

        JobDetail detail = newJob(EntitlerJob.class)
            .withIdentity("bind_by_pool_" + Util.generateUUID())
            .requestRecovery(false) // do not recover the job upon restarts
            .storeDurably()
            .usingJobData(map)
            .build();

        return detail;
    }

    public static boolean isSchedulable(JobCurator jobCurator, JobStatus status) {
        long running = jobCurator.findNumRunningByClassAndTarget(
            status.getTargetId(), status.getJobClass());
        // We can start the job if there are less than N others running
        int throttle = conf.getInt(ConfigProperties.ENTITLER_JOB_THROTTLE);
        return running < throttle;
    }

    public static JobStatus scheduleJob(JobCurator jobCurator,
        Scheduler scheduler, JobDetail detail, Trigger trigger) throws SchedulerException {

        JobStatus status = jobCurator.getByClassAndTarget(
            detail.getJobDataMap().getString(JobStatus.TARGET_ID),
            EntitlerJob.class);

        // Insert as a waiting job if a bunch of EntitlerJobs are already running
        if (status != null && !isSchedulable(jobCurator, status)) {
            trigger = null;
        }
        return KingpinJob.scheduleJob(jobCurator, scheduler, detail, trigger);
    }
}
