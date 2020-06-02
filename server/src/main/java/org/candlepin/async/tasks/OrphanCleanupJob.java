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

import org.candlepin.async.AsyncJob;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Content;
import org.candlepin.model.ContentCurator;
import org.candlepin.model.OwnerContentCurator;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.ProductCurator;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

import javax.persistence.LockModeType;



/**
 * The OrphanCleanupJob searches for orphaned entities (products and content and the time of
 * writing) and removes them.
 */
public class OrphanCleanupJob implements AsyncJob  {
    private static Logger log = LoggerFactory.getLogger(OrphanCleanupJob.class);

    public static final String JOB_KEY = "OrphanCleanupJob";
    public static final String JOB_NAME = "Orphan Cleanup";

    // Every Sunday at 3:00am
    public static final String DEFAULT_SCHEDULE = "0 0 3 ? * 1";

    private ContentCurator contentCurator;
    private OwnerContentCurator ownerContentCurator;
    private ProductCurator productCurator;
    private OwnerProductCurator ownerProductCurator;

    @Inject
    public OrphanCleanupJob(ContentCurator contentCurator, OwnerContentCurator ownerContentCurator,
        ProductCurator productCurator, OwnerProductCurator ownerProductCurator) {

        this.ownerContentCurator = ownerContentCurator;
        this.contentCurator = contentCurator;
        this.ownerProductCurator = ownerProductCurator;
        this.productCurator = productCurator;
    }

    @Override
    @Transactional
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.debug("Deleting orphaned entities");

        int orphanedContent = this.deleteOrphanedContent();
        int orphanedProducts = this.deleteOrphanedProducts();

        String format = "Orphan cleanup completed;" +
            "\n  %d orphaned content deleted" +
            "\n  %d orphaned products deleted";

        context.setJobResult(format, orphanedContent, orphanedProducts);
    }

    private int deleteOrphanedContent() {
        int count = 0;
        CandlepinQuery<Content> contentQuery = this.ownerContentCurator.getOrphanedContent()
            .setLockMode(LockModeType.PESSIMISTIC_WRITE);

        for (Content content : contentQuery) {
            this.contentCurator.delete(content);
            ++count;
        }

        this.contentCurator.flush();
        log.debug("{} orphaned product entities deleted", count);

        return count;
    }

    private int deleteOrphanedProducts() {
        List<String> orphanedProductUuids = this.ownerProductCurator.getOrphanedProductUuids();

        Set<Pair<String, String>> activePoolProducts = this.productCurator
            .getProductsWithPools(orphanedProductUuids);

        if (activePoolProducts != null && !activePoolProducts.isEmpty()) {
            log.warn("Found {} pools referencing orphaned products:", activePoolProducts.size());

            for (Pair<String, String> pair : activePoolProducts) {
                log.warn("  Pool: {}, Product UUID: {}", pair.getValue(), pair.getKey());
                orphanedProductUuids.remove(pair.getKey());
            }
        }

        int count = this.productCurator.bulkDeleteByUuids(orphanedProductUuids);

        log.debug("{} orphaned content entities deleted", count);

        return count;
    }
}
