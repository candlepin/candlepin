/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
import org.candlepin.controller.PoolManager;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import javax.inject.Inject;


/**
 * The RegenProductEntitlementCertsJob regenerates entitlement certificates for a given product
 * across all orgs using it, as applicable.
 */
public class RegenProductEntitlementCertsJob implements AsyncJob {
    private static final Logger log = LoggerFactory.getLogger(RegenProductEntitlementCertsJob.class);

    public static final String JOB_KEY = "RegenProductEntitlementCertsJob";
    public static final String JOB_NAME = "Regen Product Entitlement Certificates";

    private static final String ARG_PRODUCT_ID = "product_id";
    private static final String ARG_LAZY_REGEN = "lazy_regen";

    /**
     * Job configuration object
     */
    public static class RegenProductEntitlementCertsConfig extends
        JobConfig<RegenProductEntitlementCertsConfig> {

        public RegenProductEntitlementCertsConfig() {
            this.setJobKey(JOB_KEY)
                .setJobName(JOB_NAME)
                .addConstraint(JobConstraints.uniqueByArguments(ARG_PRODUCT_ID));
        }

        /**
         * Sets the ID of the product for which to regenerate entitlement certificates.
         *
         * @param productId
         *  the ID of the product to set for this job
         *
         * @return
         *  a reference to this job config
         */
        public RegenProductEntitlementCertsConfig setProductId(String productId) {
            if (productId == null || productId.isEmpty()) {
                throw new IllegalArgumentException("productId is null or empty");
            }

            this.setJobArgument(ARG_PRODUCT_ID, productId);
            return this;
        }

        /**
         * Sets whether or not the regeneration of certificates should be done lazily or not.
         * Defaults to true.
         *
         * @param lazy
         *  whether or not to regenerate certificates lazily
         *
         * @return
         *  a reference to this job config
         */
        public RegenProductEntitlementCertsConfig setLazyRegeneration(boolean lazy) {
            this.setJobArgument(ARG_LAZY_REGEN, lazy);
            return this;
        }

        @Override
        public void validate() throws JobConfigValidationException {
            super.validate();

            try {
                JobArguments arguments = this.getJobArguments();

                String productId = arguments.getAsString(ARG_PRODUCT_ID);

                if (productId == null || productId.isEmpty()) {
                    String errmsg = "product has not been set, or the provided product lacks an ID";
                    throw new JobConfigValidationException(errmsg);
                }
            }
            catch (ArgumentConversionException e) {
                String errmsg = "One or more required arguments are of the wrong type";
                throw new JobConfigValidationException(errmsg, e);
            }
        }
    }


    private final PoolManager poolManager;
    private final OwnerCurator ownerCurator;

    /**
     * Instantiates a new instance of the RegenProductEntitlementCertsJob
     *
     * @param poolManager
     *  the PoolManager instance to use for regenerating entitlement certificates
     *
     * @param ownerCurator
     *  the OwnerCurator instance to use for looking up owners related to the given product
     */
    @Inject
    public RegenProductEntitlementCertsJob(PoolManager poolManager, OwnerCurator ownerCurator) {
        this.poolManager = Objects.requireNonNull(poolManager);
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobArguments args = context.getJobArguments();

        String productId = args.getAsString(ARG_PRODUCT_ID);
        boolean lazyRegen = args.getAsBoolean(ARG_LAZY_REGEN, true);

        // Find a set of owners that actually have the product...
        Set<Owner> owners = this.ownerCurator.getOwnersWithProducts(Collections.singleton(productId));

        // Regenerate if we found any...
        if (!owners.isEmpty()) {
            log.info("Regenerating entitlement certificates for {} owners with product: {}",
                owners.size(), productId);

            for (Owner owner : owners) {
                this.poolManager.regenerateCertificatesOf(owner, productId, lazyRegen);
            }
        }
        else {
            log.debug("Nothing to regenerate; no owners currently using product: {}", productId);
        }

        context.setJobResult("Entitlements regenerated for %d owners using product: %s",
            owners.size(), productId);
    }

    /**
     * Creates a JobConfig configured to execute this job. Callers may further manipulate the
     * JobConfig as necessary before queuing it.
     *
     * @return
     *  a JobConfig instance configured to execute this job
     */
    public static RegenProductEntitlementCertsConfig createJobConfig() {
        return new RegenProductEntitlementCertsConfig();
    }
}
