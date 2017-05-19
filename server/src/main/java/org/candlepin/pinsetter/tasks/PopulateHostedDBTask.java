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

import com.google.common.collect.Iterables;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProductCurator;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.util.Util;

import com.google.inject.Inject;

import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;



/**
 * The PopulatedHostedDBTask is the asynchronous worker implementation for populating Hosted's DB.
 *
 * This class will likely be removed once the multiorg migration is complete.
 */
public class PopulateHostedDBTask extends KingpinJob {
    private static Logger log = LoggerFactory.getLogger(PopulateHostedDBTask.class);

    /** Constant config name used to store the "skip_existing" variable in the JobDataMap */
    public static final String SKIP_EXISTING = "skip_existing";

    private ProductServiceAdapter productService;
    private ProductCurator productCurator;
    private ContentCurator contentCurator;
    private PoolCurator poolCurator;
    private Configuration config;


    @Inject
    public PopulateHostedDBTask(ProductServiceAdapter productService, ProductCurator productCurator,
        ContentCurator contentCurator, PoolCurator poolCurator, Configuration config) {

        this.productService = productService;
        this.productCurator = productCurator;
        this.contentCurator = contentCurator;
        this.poolCurator = poolCurator;
        this.config = config;
    }

    @Override
    public void toExecute(JobExecutionContext context) throws JobExecutionException {
        if (this.config.getBoolean(ConfigProperties.STANDALONE)) {
            log.warn("Aborting populate DB task in standalone environment");
            context.setResult("Aborting populate DB task in standalone environment");
            return;
        }

        int confBatchSize = config.getInt(ConfigProperties.POPULATE_HOSTED_DB_JOB_PROD_LOOKUP_BATCH_SIZE);
        if (confBatchSize < 1) {
            String error = String.format("Aborting populate DB task. Invalid configuration setting for {0}.",
                ConfigProperties.POPULATE_HOSTED_DB_JOB_PROD_LOOKUP_BATCH_SIZE);
            log.warn(error);
            context.setResult(error);
            return;
        }

        JobDataMap map = context.getMergedJobDataMap();
        Boolean skipExisting = map.getBoolean(SKIP_EXISTING);

        int pcount = 0;
        int ccount = 0;
        int ecount = 0;
        log.info("Populating Hosted DB");

        Set<String> productCache = new HashSet<String>();
        Set<String> productIds = this.poolCurator.getAllKnownProductIds();
        Set<String> dependentProducts = new HashSet<String>();

        if (Boolean.TRUE.equals(skipExisting)) {
            log.info("Checking known new products...");

            Collection<String> existingProductIds = this.productCurator.getExistingProductIds();
            log.info("Skipping {} existing products...", existingProductIds.size());

            productIds.removeAll(existingProductIds);
            log.info("Importing data for {} known new products...", productIds.size());
        }
        else {
            log.info("Importing data for all {} known products...", productIds.size());
        }

        while (productIds.size() > 0) {
            log.info("Fetching and processing {} products from the adapter.", productIds.size());
            // Batch fetch and process the product ids to avoid long request
            // times from the adapter causing c3p0 to drop idle connections.
            int adapterLookupCount = 0;
            for (List<String> productIdBatch : Iterables.partition(productIds, confBatchSize)) {
                // Nested loop to allow DB activity.
                ++adapterLookupCount;
                log.info("Batch fetching products from adapter: {} of {} batches of {}.", adapterLookupCount,
                    (int) Math.ceil((double) productIds.size() / (double) confBatchSize), confBatchSize);
                for (Product product : this.productService.getProductsByIds(productIdBatch)) {

                    if (product != null) {
                        log.info("Processing product with ID: {}", product.getId());
                        log.info("Storing product: {}", product);

                        dependentProducts.addAll(product.getDependentProductIds());

                        for (ProductContent pcontent : product.getProductContent()) {
                            log.info("  Storing product content: {}", pcontent.getContent());
                            this.contentCurator.createOrUpdate(pcontent.getContent());
                            ++ccount;
                        }

                        this.productCurator.createOrUpdate(product);
                        productCache.add(product.getId());
                        ++pcount;
                    }
                }
            }

            // Verify that we received the products we expected to receive...
            productIds.removeAll(productCache);
            if (productIds.size() > 0) {
                ecount += productIds.size();

                for (String productId : productIds) {
                    log.warn("Unable to find product for referenced product ID: {}", productId);
                }
            }

            // Process dependent products
            log.info("Importing data for dependent products...");
            dependentProducts.removeAll(productCache);

            // Clear the product ID set and swap with the dependent products set
            productIds.clear();
            Set<String> temp = productIds;
            productIds = dependentProducts;
            dependentProducts = temp;
        }

        // TODO: Should this be translated?
        String result = String.format(
            "Finished populating hosted DB. Received %d product(s) and %d content " +
            "with %d unresolved reference(s)",
            pcount, ccount, ecount
        );

        log.info(result);
        context.setResult(result);
    }

////////////////////////////////////////////////////////////////////////////////////////////////////

    public static JobDetail createAsyncTask(boolean skipExisting) {
        JobDataMap map = new JobDataMap();
        map.put(SKIP_EXISTING, skipExisting);

        JobDetail detail = JobBuilder.newJob(PopulateHostedDBTask.class)
            .withIdentity("populated_hosted_db-" + Util.generateUUID())
            .usingJobData(map)
            .requestRecovery(true)
            .build();

        return detail;
    }

}
