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
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.controller.Entitler;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;



/**
 * Asynchronous job for entitlement by products. A job will wait for a running
 * job of the same Owner to finish before beginning execution
 */
public class EntitleByProductsJob implements AsyncJob {
    private static final Logger log = LoggerFactory.getLogger(EntitleByProductsJob.class);

    public static final String JOB_KEY = "EntitleByProductsJob";
    public static final String JOB_NAME = "Entitle by Products";

    private static final String CONSUMER_UUID_KEY = "consumer_uuid";
    private static final String ENTITLE_DATE_KEY = "entitle_date";
    private static final String PROD_IDS_KEY = "product_ids";
    private static final String FROM_POOLS_KEY = "from_pools";

    protected Entitler entitler;

    @Inject
    public EntitleByProductsJob(Entitler entitler) {
        this.entitler = Objects.requireNonNull(entitler);
    }

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        try {
            final JobArguments arguments = context.getJobArguments();
            final String consumerUuid = arguments.getAsString(CONSUMER_UUID_KEY);
            final Date entitleDate = arguments.getAs(ENTITLE_DATE_KEY, Date.class);
            final String[] prodIds = arguments.getAs(PROD_IDS_KEY, String[].class);
            final String[] pools = arguments.getAs(FROM_POOLS_KEY, String[].class);

            final List<Entitlement> ents = this.entitler
                .bindByProducts(Arrays.asList(prodIds), consumerUuid, entitleDate, Arrays.asList(pools));

            entitler.sendEvents(ents);

            context.setJobResult("%d entitlements created for owner", ents.size());
        }
        // Catch any exception that is fired and re-throw as a JobExecutionException
        // so that the job will be properly cleaned up on failure.
        catch (Exception e) {
            log.error("EntitleByProductsJob encountered a problem", e);
            throw new JobExecutionException(e.getMessage(), e, false);
        }
    }

    public static EntitleByProductsJobConfig createConfig() {
        return new EntitleByProductsJobConfig();
    }

    /**
     * Job configuration object for the entitle by products job
     */
    public static class EntitleByProductsJobConfig extends JobConfig<EntitleByProductsJobConfig> {

        public EntitleByProductsJobConfig() {
            this.setJobKey(JOB_KEY)
                .setJobName(JOB_NAME);
        }

        public EntitleByProductsJobConfig setConsumer(final Consumer consumer) {
            if (consumer == null) {
                throw new IllegalArgumentException("Consumer is null");
            }

            this.setJobArgument(CONSUMER_UUID_KEY, consumer.getUuid());

            return this;
        }

        public EntitleByProductsJobConfig setProductIds(final Collection<String> prodIds) {
            if (prodIds == null) {
                throw new IllegalArgumentException("Product ids is null");
            }

            this.setJobArgument(PROD_IDS_KEY, prodIds.toArray());

            return this;
        }

        public EntitleByProductsJobConfig setEntitleDate(final Date entitleDate) {
            this.setJobArgument(ENTITLE_DATE_KEY, entitleDate);

            return this;
        }

        public EntitleByProductsJobConfig setPools(final Collection<String> pools) {
            if (pools == null) {
                throw new IllegalArgumentException("From pools is null");
            }

            this.setJobArgument(FROM_POOLS_KEY, pools.toArray());

            return this;
        }

        @Override
        public void validate() throws JobConfigValidationException {
            super.validate();

            try {
                final JobArguments arguments = this.getJobArguments();

                final String consumerUuid = arguments.getAsString(CONSUMER_UUID_KEY);
                final String[] prodIds = arguments.getAs(PROD_IDS_KEY, String[].class);
                final String[] pools = arguments.getAs(FROM_POOLS_KEY, String[].class);

                if (consumerUuid == null || consumerUuid.isEmpty()) {
                    final String errmsg = "Consumer UUID has not been set!";
                    throw new JobConfigValidationException(errmsg);
                }

                if (pools == null) {
                    final String errmsg = "Pools has not been set!";
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
