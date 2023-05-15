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

import org.candlepin.async.AsyncJob;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.controller.ContentManager;
import org.candlepin.controller.ProductManager;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.ProductCurator;

import com.google.inject.persist.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.inject.Inject;
import javax.persistence.LockModeType;



/**
 * The OrphanCleanupJob searches for orphaned entities (products and content and the time of
 * writing) and removes them.
 */
public class OrphanCleanupJob implements AsyncJob  {
    private static final Logger log = LoggerFactory.getLogger(OrphanCleanupJob.class);

    public static final String JOB_KEY = "OrphanCleanupJob";
    public static final String JOB_NAME = "Orphan Cleanup";

    // Every Sunday at 3:00am
    public static final String DEFAULT_SCHEDULE = "0 0 3 ? * 1";

    private final ContentCurator contentCurator;
    private final ProductCurator productCurator;

    @Inject
    public OrphanCleanupJob(ContentCurator contentCurator, ProductCurator productCurator) {
        this.contentCurator = Objects.requireNonNull(contentCurator);
        this.productCurator = Objects.requireNonNull(productCurator);
    }

    @Override
    @Transactional
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.debug("Obtaining system locks...");
        this.contentCurator.getSystemLock(ContentManager.SYSTEM_LOCK, LockModeType.PESSIMISTIC_WRITE);
        this.productCurator.getSystemLock(ProductManager.SYSTEM_LOCK, LockModeType.PESSIMISTIC_WRITE);

        log.debug("Fetching candidate orphaned entities...");
        List<String> orphanedContentUuids = this.contentCurator.getOrphanedContentUuids();
        List<String> orphanedProductUuids = this.productCurator.getOrphanedProductUuids();

        // Impl note: products need to be filtered first, so we hold onto any orphaned content that
        // is attached to an orphaned product which needs to be kept around due to some busted
        // mappings
        log.debug("Verifying orphaned entities are cleared for deletion...");
        this.filterOrphanedProducts(orphanedProductUuids);
        this.filterOrphanedContent(orphanedContentUuids, orphanedProductUuids);

        log.debug("Deleting orphaned entities...");
        // Impl note: again, process/delete products first, so we don't fail out trying to delete
        // a content that's used by a product we're also removing
        int orphanedProductsRemoved = this.deleteOrphanedProducts(orphanedProductUuids);
        int orphanedContentRemoved = this.deleteOrphanedContent(orphanedContentUuids);

        String format = "Orphan cleanup completed;" +
            "\n  %d orphaned content deleted" +
            "\n  %d orphaned products deleted";

        context.setJobResult(format, orphanedContentRemoved, orphanedProductsRemoved);
    }

    /**
     * Filters the provided list of orphaned products by removing any that are detected to be in
     * use -- even erroneously -- by one or more other entities, such as pools or other products.
     *
     * @param orphanedProductUuids
     *  the list of orphaned product UUIDs to filter; must be a mutable list
     */
    private void filterOrphanedProducts(List<String> orphanedProductUuids) {
        // Filter orphaned products still referenced by one or more pools
        Map<String, Set<String>> productPoolReferences = this.productCurator
            .getPoolsReferencingProducts(orphanedProductUuids);

        if (productPoolReferences != null && !productPoolReferences.isEmpty()) {
            log.warn("Found {} orphaned products referenced by one or more pools; " +
                "omitting products from cleanup:",
                productPoolReferences.size());

            for (Map.Entry<String, Set<String>> entry : productPoolReferences.entrySet()) {
                log.warn("  Product UUID: {}, Referenced by pools: {}", entry.getKey(), entry.getValue());
                orphanedProductUuids.remove(entry.getKey());
            }
        }

        // Filter orphaned products still referenced by one or more active products
        Map<String, Set<String>> productProductReferences = this.productCurator
            .getProductsReferencingProducts(orphanedProductUuids);

        if (productProductReferences != null) {
            // Check our references so we don't get hung up on an orphaned product referencing
            // another orphaned product. Such referents will hang around until the next invocation
            // of this job, but that's better than either failing or needlessly yelling about it.
            Iterator<Map.Entry<String, Set<String>>> iterator = productProductReferences.entrySet()
                .iterator();

            while (iterator.hasNext()) {
                Map.Entry<String, Set<String>> entry = iterator.next();
                String orphanedProductUuid = entry.getKey();
                Set<String> referrers = entry.getValue();

                if (orphanedProductUuids.containsAll(referrers)) {
                    log.debug("Orphaned product {} is referenced by {} other orphaned products: {};" +
                        "omitting referent from cleanup",
                        orphanedProductUuid, referrers.size(), referrers);

                    iterator.remove();
                    orphanedProductUuids.remove(orphanedProductUuid);
                }
            }

            // Complain about any remaining references
            if (!productProductReferences.isEmpty()) {
                log.warn("Found {} orphaned product(s) referenced by one or more products; " +
                    "omitting products from cleanup:",
                    productProductReferences.size());

                for (Map.Entry<String, Set<String>> entry : productProductReferences.entrySet()) {
                    log.warn("  Product UUID: {}, Referenced by products: {}", entry.getKey(),
                        entry.getValue());

                    orphanedProductUuids.remove(entry.getKey());
                }
            }
        }
    }

    /**
     * Filters the provided list of orphaned content by removing any that are detected to be in
     * use -- even erroneously -- by one or more other entities, such as products or environments.
     *
     * @param orphanedContentUuids
     *  the list of orphaned content UUIDs to filter; must be a mutable list
     *
     * @param orphanedProductUuids
     *  a list of UUIDs representing detected orphaned products
     */
    private void filterOrphanedContent(List<String> orphanedContentUuids, List<String> orphanedProductUuids) {
        // Filter orphaned content still referenced by one or more active products
        Map<String, Set<String>> contentProductReferences = this.contentCurator
            .getProductsReferencingContent(orphanedContentUuids);

        if (contentProductReferences != null) {
            // Continue cleanup on any referenced content if the references are all orphans themselves
            Iterator<Map.Entry<String, Set<String>>> iterator = contentProductReferences.entrySet()
                .iterator();

            while (iterator.hasNext()) {
                Map.Entry<String, Set<String>> entry = iterator.next();
                String orphanedContentUuid = entry.getKey();
                Set<String> referrers = entry.getValue();

                if (orphanedProductUuids.containsAll(referrers)) {
                    log.trace("Orphaned content {} is referenced by {} orphaned products: {}; " +
                        "proceeding with cleanup",
                        orphanedContentUuid, referrers.size(), referrers);

                    iterator.remove();
                }
            }

            if (!contentProductReferences.isEmpty()) {
                log.warn("Found {} orphaned content(s) referenced by one or more products; " +
                    "omitting content from cleanup:",
                    contentProductReferences.size());

                for (Map.Entry<String, Set<String>> entry : contentProductReferences.entrySet()) {
                    log.warn("  Content UUID: {}, Referenced by products: {}", entry.getKey(),
                        entry.getValue());

                    orphanedContentUuids.remove(entry.getKey());
                }
            }
        }
    }

    /**
     * Deletes orphaned content specified by the list of content UUIDs.
     *
     * @param orphanedContentUuids
     *  a collection of UUIDs of orphaned content to delete
     *
     * @return
     *  the number of content entities deleted as a result of this operation
     */
    private int deleteOrphanedContent(List<String> orphanedContentUuids) {
        int count = this.contentCurator.bulkDeleteByUuids(orphanedContentUuids);
        log.debug("{} orphaned content entities deleted", count);

        return count;
    }

    /**
     * Deletes orphaned products specified by the list of product UUIDs.
     *
     * @param orphanedProductUuids
     *  a collection of UUIDs of orphaned products to delete
     *
     * @return
     *  the number of product entities deleted as a result of this operation
     */
    private int deleteOrphanedProducts(List<String> orphanedProductUuids) {
        int count = this.productCurator.bulkDeleteByUuids(orphanedProductUuids);
        log.debug("{} orphaned product entities deleted", count);

        return count;
    }
}
