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
import org.candlepin.controller.RefresherFactory;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import javax.inject.Inject;



public class RefreshPoolsForProductsJob implements AsyncJob {

    public static final String JOB_KEY = "RefreshPoolsForProductsJob";
    public static final String JOB_NAME = "Refresh Pools For Products";

    private static final String LAZY_REGEN_KEY = "lazy_regen";
    private static final String PRODUCT_IDS_KEY = "product_ids";

    private final ProductCurator productCurator;
    private final SubscriptionServiceAdapter subAdapter;
    private final ProductServiceAdapter prodAdapter;
    private final RefresherFactory refresherFactory;


    @Inject
    public RefreshPoolsForProductsJob(
        ProductCurator productCurator,
        SubscriptionServiceAdapter subAdapter,
        ProductServiceAdapter prodAdapter,
        RefresherFactory refresherFactory) {

        this.productCurator = Objects.requireNonNull(productCurator);
        this.subAdapter = Objects.requireNonNull(subAdapter);
        this.prodAdapter = Objects.requireNonNull(prodAdapter);
        this.refresherFactory = Objects.requireNonNull(refresherFactory);
    }

    @Override
    public void execute(final JobExecutionContext context) {
        final JobArguments args = context.getJobArguments();

        final String[] productIds = args.getAs(PRODUCT_IDS_KEY, String[].class);
        final Boolean lazy = args.getAsBoolean(LAZY_REGEN_KEY);
        final StringBuilder result = new StringBuilder();

        final Map<String, Product> products = this.productCurator.getProductsByIds(Arrays.asList(productIds));

        // TODO: should we verify the number of products we fetch matches the input count?

        if (products != null && !products.isEmpty()) {
            this.refresherFactory.getRefresher(this.subAdapter, this.prodAdapter)
                .setLazyCertificateRegeneration(lazy)
                .addProducts(products.values())
                .run();

            result.append("Pools refreshed for products: ")
                .append(Arrays.toString(productIds))
                .append("\n");
        }
        else {
            result.append("No products found for the given product IDs: ")
                .append(Arrays.toString(productIds));
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
    public static RefreshPoolsForProductsJobConfig createJobConfig() {
        return new RefreshPoolsForProductsJobConfig();
    }

    /**
     * Job configuration object for the refresh pools for product job
     */
    public static class RefreshPoolsForProductsJobConfig extends JobConfig<RefreshPoolsForProductsJobConfig> {

        public RefreshPoolsForProductsJobConfig() {
            this.setJobKey(JOB_KEY)
                .setJobName(JOB_NAME);
        }

        public RefreshPoolsForProductsJobConfig setProducts(Collection<Product> products) {
            if (products == null) {
                throw new IllegalArgumentException("products is null");
            }

            String[] productIds = products.stream()
                .filter(Objects::nonNull)
                .map(Product::getId)
                .filter(Objects::nonNull)
                .filter(Predicate.not(String::isBlank))
                .toArray(String[]::new);

            return this.setJobArgument(PRODUCT_IDS_KEY, productIds);
        }

        // TODO: Add methods for setting products by ID as needed

        public RefreshPoolsForProductsJobConfig setLazy(final boolean lazy) {
            return this.setJobArgument(LAZY_REGEN_KEY, lazy);
        }

        @Override
        public void validate() throws JobConfigValidationException {
            super.validate();

            try {
                final JobArguments args = this.getJobArguments();

                String[] productIds = args.getAs(PRODUCT_IDS_KEY, String[].class);
                Boolean lazy = args.getAsBoolean(LAZY_REGEN_KEY);

                if (productIds == null || productIds.length == 0) {
                    final String errmsg = "Product IDs have not been set";
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
