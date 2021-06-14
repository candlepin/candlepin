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
package org.candlepin.async.tasks;

import org.candlepin.async.ArgumentConversionException;
import org.candlepin.async.AsyncJob;
import org.candlepin.async.JobArguments;
import org.candlepin.async.JobConfig;
import org.candlepin.async.JobConfigValidationException;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;

import com.google.inject.Inject;

import java.util.Objects;



/**
 * RefreshPoolsForProductJob
 */
public class RefreshPoolsForProductJob implements AsyncJob {

    public static final String JOB_KEY = "RefreshPoolsForProductJob";
    public static final String JOB_NAME = "Refresh Pools For Product";

    private static final String LAZY_KEY = "lazy_regen";
    private static final String PRODUCT_KEY = "product_key";
    private static final String OWNER_KEY = "org";

    private final PoolManager poolManager;
    private final ProductCurator productCurator;
    private final SubscriptionServiceAdapter subAdapter;
    private final ProductServiceAdapter prodAdapter;


    @Inject
    public RefreshPoolsForProductJob(
        final ProductCurator productCurator,
        final PoolManager poolManager,
        final SubscriptionServiceAdapter subAdapter,
        final ProductServiceAdapter prodAdapter) {

        this.poolManager = Objects.requireNonNull(poolManager);
        this.productCurator = Objects.requireNonNull(productCurator);
        this.subAdapter = Objects.requireNonNull(subAdapter);
        this.prodAdapter = Objects.requireNonNull(prodAdapter);
    }

    @Override
    public void execute(final JobExecutionContext context) {
        final JobArguments args = context.getJobArguments();

        final String productUuid = args.getAsString(PRODUCT_KEY);
        final Boolean lazy = args.getAsBoolean(LAZY_KEY);
        final StringBuilder result = new StringBuilder();

        final Product product = this.productCurator.get(productUuid);

        if (product != null) {
            poolManager.getRefresher(this.subAdapter, this.prodAdapter, lazy)
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
                .setJobName(JOB_NAME);
        }

        public RefreshPoolsForProductJobConfig setProduct(final Product product) {
            final String uuid = Objects.requireNonNull(product).getUuid();
            this.setJobArgument(PRODUCT_KEY, uuid);
            return this;
        }

        public RefreshPoolsForProductJobConfig setLazy(final boolean lazy) {
            this.setJobArgument(LAZY_KEY, lazy);
            return this;
        }

        @Override
        public void validate() throws JobConfigValidationException {
            super.validate();

            try {
                final JobArguments args = this.getJobArguments();

                final String productUuid = args.getAsString(PRODUCT_KEY);
                final Boolean lazy = args.getAsBoolean(LAZY_KEY);

                if (productUuid == null || productUuid.isEmpty()) {
                    final String errmsg = "Product UUID has not been set";
                    throw new JobConfigValidationException(errmsg);
                }

                if (lazy == null) {
                    final String errmsg = "Lazy flag has not been set";
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
