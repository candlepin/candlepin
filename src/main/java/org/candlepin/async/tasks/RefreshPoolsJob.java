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
import org.candlepin.controller.PoolManager;
import org.candlepin.controller.Refresher;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;

import com.google.inject.Inject;



/**
 * Asynchronous job for refreshing the entitlement pools for specific {@link Owner}.
 */
public class RefreshPoolsJob implements AsyncJob {

    public static final String JOB_KEY = "RefreshPoolsJob";
    public static final String JOB_NAME = "Refresh Pools";

    protected static final String OWNER_KEY = "org";
    protected static final String LAZY_REGEN = "lazy_regen";

    protected OwnerCurator ownerCurator;
    protected PoolManager poolManager;
    protected SubscriptionServiceAdapter subAdapter;
    protected ProductServiceAdapter prodAdapter;


    /**
     * Job configuration object for the refresh pools job
     */
    public static class RefreshPoolsJobConfig extends JobConfig<RefreshPoolsJobConfig> {
        public RefreshPoolsJobConfig() {
            this.setJobKey(JOB_KEY)
                .setJobName(JOB_NAME)
                .addConstraint(JobConstraints.uniqueByArguments(OWNER_KEY));
        }

        /**
         * Sets the owner for this refresh pools job. The owner is required, and also provides the org
         * context in which the job will be executed.
         *
         * @param owner
         *  the owner to set for this job
         *
         * @return
         *  a reference to this job config
         */
        public RefreshPoolsJobConfig setOwner(Owner owner) {
            if (owner == null) {
                throw new IllegalArgumentException("owner is null");
            }

            // The owner is both part of context & arguments in this job.
            this.setContextOwner(owner)
                .setJobArgument(OWNER_KEY, owner.getKey());

            return this;
        }

        /**
         * Sets whether or not to generate the certificates immediately, or mark them as dirty and allow
         * them to be regenerated on-demand.
         *
         * @param lazy
         *  whether or not to generate the certificates immediately
         *
         * @return
         *  a reference to this job config
         */
        public RefreshPoolsJobConfig setLazyRegeneration(boolean lazy) {
            this.setJobArgument(LAZY_REGEN, lazy);

            return this;
        }

        @Override
        public void validate() throws JobConfigValidationException {
            super.validate();

            try {
                JobArguments arguments = this.getJobArguments();

                String ownerKey = arguments.getAsString(OWNER_KEY);
                Boolean lazyRegeneration = arguments.getAsBoolean(LAZY_REGEN);

                if (ownerKey == null || ownerKey.isEmpty()) {
                    String errmsg = "owner has not been set, or the provided owner lacks a key";
                    throw new JobConfigValidationException(errmsg);
                }

                if (lazyRegeneration == null) {
                    String errmsg = "lazy regeneration has not been set";
                    throw new JobConfigValidationException(errmsg);
                }
            }
            catch (ArgumentConversionException e) {
                String errmsg = "One or more required arguments are of the wrong type";
                throw new JobConfigValidationException(errmsg, e);
            }
        }
    }

    @Inject
    public RefreshPoolsJob(OwnerCurator ownerCurator, PoolManager poolManager,
        SubscriptionServiceAdapter subAdapter, ProductServiceAdapter prodAdapter) {

        this.ownerCurator = ownerCurator;
        this.poolManager = poolManager;
        this.subAdapter = subAdapter;
        this.prodAdapter = prodAdapter;
    }

    /**
     * {@inheritDoc}
     *
     * Executes {@link Refresher#run()} for all products of a specific Owner as a job.
     *
     * @param context the job's execution context
     */
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobArguments args = context.getJobArguments();
        String ownerKey = args.getAsString(OWNER_KEY);
        boolean lazy = args.getAsBoolean(LAZY_REGEN);
        Owner owner = ownerCurator.getByKey(ownerKey);

        if (owner == null) {
            throw new JobExecutionException("Nothing to do; owner no longer exists: " + ownerKey, true);
        }

        try {
            // Assume that we verified the request in the resource layer:
            poolManager.getRefresher(this.subAdapter, this.prodAdapter, lazy)
                .add(owner)
                .run();
        }
        catch (Exception e) {
            throw new JobExecutionException(e.getMessage(), e, false);
        }

        context.setJobResult("Pools refreshed for owner: %s", owner.getDisplayName());
    }

    /**
     * Creates a JobConfig configured to execute the refresh pools job. Callers may further manipulate
     * the JobConfig as necessary before queuing it.
     *
     * @return
     *  a JobConfig instance configured to execute the refresh pools job
     */
    public static RefreshPoolsJobConfig createJobConfig() {
        return new RefreshPoolsJobConfig();
    }
}
