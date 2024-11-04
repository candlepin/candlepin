/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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

import org.candlepin.async.AsyncJob;
import org.candlepin.async.JobArguments;
import org.candlepin.async.JobConfig;
import org.candlepin.async.JobConfigValidationException;
import org.candlepin.async.JobConstraints;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.config.ConfigProperties;
import org.candlepin.config.Configuration;
import org.candlepin.controller.PoolService;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.activationkeys.ActivationKeyCurator;

import com.google.common.collect.Iterables;
import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

/**
 * An async job responsible for revoking all entitlements for all non-manifest consumers that belong to a
 * specified owner. This job also removes activation key pools for the same owner.
 */
public class RevokeEntitlementsJob implements AsyncJob {
    private static final Logger log = LoggerFactory.getLogger(RevokeEntitlementsJob.class);
    private static final int MAX_RETRY_COUNT = 5;

    public static final String JOB_KEY = "EntitlementRevokingJob";
    public static final String JOB_NAME = "Entitlement Revoking Job";
    public static final String CFG_BATCH_SIZE = "batch_size";
    public static final String OWNER_KEY = "owner_key";

    private Configuration config;
    private ConsumerCurator consumerCurator;
    private OwnerCurator ownerCurator;
    private PoolService poolService;
    private ActivationKeyCurator activationKeyCurator;

    @Inject
    public RevokeEntitlementsJob(Configuration config, ConsumerCurator consumerCurator,
        OwnerCurator ownerCurator, PoolService poolService, ActivationKeyCurator activationKeyCurator) {
        this.config = Objects.requireNonNull(config);
        this.consumerCurator = Objects.requireNonNull(consumerCurator);
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.poolService = Objects.requireNonNull(poolService);
        this.activationKeyCurator = Objects.requireNonNull(activationKeyCurator);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobArguments jobArguments = context.getJobArguments();
        String ownerKey = jobArguments.getAsString(OWNER_KEY);

        log.info("{} has started for owner {}", JOB_KEY, ownerKey);
        Owner owner = ownerCurator.getByKey(ownerKey);
        if (owner == null) {
            String msg = String.format("Unable to find owner for owner key \"%s\"", ownerKey);
            log.error(msg);
            throw new JobExecutionException(msg, true);
        }

        int batchSize = getBatchSize();

        int removedPools = activationKeyCurator.removeActivationKeyPools(ownerKey);
        log.debug("Removed {} activation key pools", removedPools);

        /*
         * Revoke all entitlements for batches of consumers. Each batch is processed in a seperate
         * transaction.
         */
        List<String> consumerUuids = consumerCurator.getSystemConsumerUuidsByOwner(owner.getKey());
        int entRevokeCount = 0;
        for (List<String> batch : Iterables.partition(consumerUuids, batchSize)) {
            log.debug("Revoking entitlements for consumers with UUIDs: {}", batch);
            entRevokeCount += poolService.revokeAllEntitlements(batch, false);
        }

        log.info("{} has run! {} entitlements revoked and {} activation key pools removed.", JOB_KEY,
            entRevokeCount, removedPools);
        String msg = "%s completed successfully. %d entitlements revoked and %d pools removed from " +
            "activation keys.";
        context.setJobResult(msg, JOB_NAME, entRevokeCount, removedPools);
    }

    /**
     * Job configuration object for the {@link RevokeEntitlementsJob}
     */
    public static class RevokeEntitlementsJobConfig extends JobConfig<RevokeEntitlementsJobConfig> {

        public RevokeEntitlementsJobConfig() throws JobExecutionException {
            this.setJobKey(JOB_KEY)
                .setJobName(JOB_NAME)
                .addConstraint(JobConstraints.uniqueByArguments(OWNER_KEY))
                .setRetryCount(MAX_RETRY_COUNT);
        }

        /**
         * Sets the owner for this {@link RevokeEntitlementsJobConfig}.
         *
         * @param owner
         *  the owner to set
         *
         * @throws IllegalArgumentException
         *  if the owner is null or the owner's key is null or blank
         *
         * @return a reference to this RevokeEntitlementsJobConfig
         */
        @Override
        public RevokeEntitlementsJobConfig setOwner(Owner owner) {
            if (owner == null) {
                throw new IllegalArgumentException("owner is null");
            }

            if (owner.getKey() == null || owner.getKey().isBlank()) {
                throw new IllegalArgumentException("owner key is null or blank");
            }

            this.setContextOwner(owner);
            this.setJobArgument(OWNER_KEY, owner.getKey());

            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void validate() throws JobConfigValidationException {
            super.validate();

            JobArguments arguments = this.getJobArguments();

            String ownerKey = arguments.getAsString(OWNER_KEY);
            if (ownerKey == null || ownerKey.isBlank()) {
                throw new JobConfigValidationException("Owner key has not been set or is blank");
            }
        }
    }

    private int getBatchSize() throws JobExecutionException {
        String configKey = ConfigProperties.jobConfig(JOB_KEY, CFG_BATCH_SIZE);
        int batchSize = config.getInt(configKey);
        if (batchSize <= 0) {
            String errorMessage = String.format(
                "Invalid value for configuration \"%s\", must be a positive integer: %s", configKey,
                batchSize);

            log.error(errorMessage);
            throw new JobExecutionException(errorMessage);
        }

        return batchSize;
    }
}
