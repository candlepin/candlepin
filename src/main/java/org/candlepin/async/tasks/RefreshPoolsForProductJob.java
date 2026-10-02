/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
import org.candlepin.controller.RefresherFactory;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.service.SubscriptionServiceAdapter;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.LockAcquisitionException;

import java.util.Objects;

import jakarta.inject.Inject;


public class RefreshPoolsForProductJob implements AsyncJob {

    public static final String JOB_KEY = "RefreshPoolsForProductJob";
    public static final String JOB_NAME = "Refresh Pools For Product";

    private static final String PRODUCT_KEY = "product_key";

    private static final int REFRESH_RETRY_COUNT = 4;

    private final ProductCurator productCurator;
    private final SubscriptionServiceAdapter subAdapter;
    private final RefresherFactory refresherFactory;


    @Inject
    public RefreshPoolsForProductJob(
        ProductCurator productCurator,
        SubscriptionServiceAdapter subAdapter,
        RefresherFactory refresherFactory) {

        this.productCurator = Objects.requireNonNull(productCurator);
        this.subAdapter = Objects.requireNonNull(subAdapter);
        this.refresherFactory = Objects.requireNonNull(refresherFactory);
    }

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        final JobArguments args = context.getJobArguments();

        final String productUuid = args.getAsString(PRODUCT_KEY);
        final StringBuilder result = new StringBuilder();

        try {
            final Product product = this.productCurator.get(productUuid);

            if (product != null) {
                this.refresherFactory.getRefresher(this.subAdapter)
                    .setLazyCertificateRegeneration(true)
                    .add(product)
                    .run();

                result.append("Pools refreshed for product: ")
                    .append(productUuid)
                    .append("\n");
            }
            else {
                result.append("Unable to refresh pools for product \"")
                    .append(productUuid)
                    .append("\": Could not find a product with the specified UUID");
            }

            context.setJobResult(result.toString());
        }
        catch (Exception e) {
            boolean transientRace = isCausedByTransientRace(e);
            throw new JobExecutionException(e.getMessage(), e, !transientRace);
        }
    }

    /**
     * Checks whether the given exception's cause chain contains a transient constraint violation
     * or deadlock, indicating the job failed only because it collided with another concurrent
     * operation, rather than a genuine problem.
     *
     * @param exception
     *  the exception to inspect
     *
     * @return
     *  true if the exception was caused by a transient constraint violation or deadlock; false
     *  otherwise
     */
    private static boolean isCausedByTransientRace(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException || cause instanceof LockAcquisitionException) {
                return true;
            }
        }

        return false;
    }

    /**
     * Creates a JobConfig configured to execute the refresh pools for product
     * job. Callers may further manipulate the JobConfig as necessary before
     * queuing it.
     *
     * @return
     *  a JobConfig instance configured to execute the refresh pools for product job
     */
    public static RefreshPoolsForProductJobConfig createJobConfig() {
        return new RefreshPoolsForProductJobConfig();
    }

    /**
     * Job configuration object for the refresh pools for product job
     */
    public static class RefreshPoolsForProductJobConfig extends JobConfig<RefreshPoolsForProductJobConfig> {

        RefreshPoolsForProductJobConfig() {
            this.setJobKey(JOB_KEY)
                .setJobName(JOB_NAME)
                .setRetryCount(REFRESH_RETRY_COUNT);
        }

        public RefreshPoolsForProductJobConfig setProduct(final Product product) {
            final String uuid = Objects.requireNonNull(product).getUuid();
            this.setJobArgument(PRODUCT_KEY, uuid);
            return this;
        }

        @Override
        public void validate() throws JobConfigValidationException {
            super.validate();

            try {
                final JobArguments args = this.getJobArguments();

                final String productUuid = args.getAsString(PRODUCT_KEY);

                if (productUuid == null || productUuid.isEmpty()) {
                    final String errmsg = "Product UUID has not been set";
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
