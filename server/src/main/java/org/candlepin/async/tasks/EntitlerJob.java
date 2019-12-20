/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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

package org.candlepin.async.tasks;

import org.candlepin.async.ArgumentConversionException;
import org.candlepin.async.AsyncJob;
import org.candlepin.async.JobArguments;
import org.candlepin.async.JobConfig;
import org.candlepin.async.JobConfigValidationException;
import org.candlepin.async.JobConstraints;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.controller.Entitler;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.dto.PoolIdAndErrors;
import org.candlepin.model.dto.PoolIdAndQuantity;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.policy.RulesValidationError;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.entitlement.EntitlementRulesTranslator;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * EntitlerJob
 */
public class EntitlerJob implements AsyncJob {
    private static Logger log = LoggerFactory.getLogger(EntitlerJob.class);
    public static final String JOB_KEY = "EntitlerJob";
    private static final String JOB_NAME = "bind_by_pool";
    private static final String OWNER_KEY = "org";
    private static final String CONSUMER_UUID_KEY = "consumer_uuid";
    private static final String POOL_ID_KEY = "pool_id";
    private static final String POOL_QUANTITY_KEY = "pool_quantity";

    public static final String CFG_JOB_THROTTLE = "throttle";
    public static final int DEFAULT_THROTTLE = 7;

    private final I18n i18n;
    private final Entitler entitler;
    private final PoolCurator poolCurator;

    @Inject
    public EntitlerJob(final Entitler e, final PoolCurator p, final I18n i18n) {
        this.entitler = Objects.requireNonNull(e);
        this.i18n = Objects.requireNonNull(i18n);
        this.poolCurator = Objects.requireNonNull(p);
    }

    @Override
    public Object execute(final JobExecutionContext context) throws JobExecutionException {
        try {
            final JobArguments arguments = context.getJobArguments();
            final String uuid = arguments.getAsString(CONSUMER_UUID_KEY);
            final String poolId = arguments.getAsString(POOL_ID_KEY);
            final Integer poolQuantity = arguments.getAsInteger(POOL_QUANTITY_KEY);

            final Map<String, Integer> poolMap = new HashMap<>();
            poolMap.put(poolId, poolQuantity);
            final List<Entitlement> ents = entitler.bindByPoolQuantities(uuid, poolMap);
            entitler.sendEvents(ents);

            final List<PoolIdAndQuantity> consumed = ents.stream()
                .map(ent -> new PoolIdAndQuantity(ent.getPool().getId(), ent.getQuantity()))
                .collect(Collectors.toList());
            poolCurator.clear();
            return consumed;
        }
        catch (EntitlementRefusedException e) {
            log.error("EntitlerJob encountered a problem, translating errors", e);
            final Map<String, ValidationResult> validationResults = e.getResults();
            return collectErrors(validationResults);
        }
        // Catch any exception that is fired and re-throw as a JobExecutionException
        // so that the job will be properly cleaned up on failure.
        catch (Exception e) {
            log.error("EntitlerJob encountered a problem.", e);
            throw new JobExecutionException(e.getMessage(), e, false);
        }
    }

    private List<PoolIdAndErrors> collectErrors(final Map<String, ValidationResult> results) {
        final EntitlementRulesTranslator translator = new EntitlementRulesTranslator(i18n);
        final List<PoolIdAndErrors> poolErrors = new ArrayList<>();

        for (Pool pool : poolCurator.listAllByIds(results.keySet())) {
            final List<String> errorMessages = new ArrayList<>();
            for (RulesValidationError error : results.get(pool.getId()).getErrors()) {
                errorMessages.add(translator.poolErrorToMessage(pool, error));
            }
            poolErrors.add(new PoolIdAndErrors(pool.getId(), errorMessages));
        }
        return poolErrors;
    }

    public static EntitlerJobConfig createConfig(int limit) {
        return new EntitlerJobConfig(limit);
    }

    /**
     * Job configuration object for the entitle by pool job
     */
    public static class EntitlerJobConfig extends JobConfig<EntitlerJobConfig> {

        public EntitlerJobConfig(int limit) {
            this.setJobKey(JOB_KEY)
                .setJobName(JOB_NAME)
                .addConstraint(JobConstraints.throttledByJobKey(JOB_KEY, limit));
        }

        public EntitlerJobConfig setConsumer(final Consumer consumer) {
            if (consumer == null) {
                throw new IllegalArgumentException("Consumer is null");
            }
            this.setJobArgument(CONSUMER_UUID_KEY, consumer.getUuid());

            return this;
        }

        public EntitlerJobConfig setPoolQuantity(final String poolId, final Integer qty) {
            if (poolId == null || poolId.isEmpty()) {
                throw new IllegalArgumentException("Pool ID must be present");
            }
            if (qty < 0) {
                throw new IllegalArgumentException("Quantity must be greater or equal to zero ");
            }

            this.setJobArgument(POOL_ID_KEY, poolId);
            this.setJobArgument(POOL_QUANTITY_KEY, qty);

            return this;
        }

        @Override
        public void validate() throws JobConfigValidationException {
            super.validate();

            try {
                final JobArguments arguments = this.getJobArguments();

                final String consumerUuid = arguments.getAsString(CONSUMER_UUID_KEY);
                final String poolId = arguments.getAsString(POOL_ID_KEY);
                final Integer poolQuantity = arguments.getAsInteger(POOL_QUANTITY_KEY);

                if (consumerUuid == null || consumerUuid.isEmpty()) {
                    final String errmsg = "Consumer UUID has not been set!";
                    throw new JobConfigValidationException(errmsg);
                }
                if (poolId == null || poolId.isEmpty()) {
                    final String errmsg = "Pool id has not been set!";
                    throw new JobConfigValidationException(errmsg);
                }
                if (poolQuantity == null || poolQuantity < 0) {
                    final String errmsg = "Pool quantity has not been set!";
                    throw new JobConfigValidationException(errmsg);
                }
            }
            catch (ArgumentConversionException e) {
                final String errmsg = "One or more required arguments are of the wrong type";
                throw new JobConfigValidationException(errmsg, e);
            }
        }
    }
}
