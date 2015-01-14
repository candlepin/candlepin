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

import org.candlepin.model.ContentCurator;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.model.ProductCurator;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.util.Util;

import com.google.inject.Inject;

import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;



/**
 * The PopulatedHostedDBTask is the asynchronous worker implementation for populating Hosted's DB.
 *
 * This class will likely be removed once the multiorg migration is complete.
 */
public class PopulateHostedDBTask extends KingpinJob {
    private static Logger log = LoggerFactory.getLogger(PopulateHostedDBTask.class);

    private ProductServiceAdapter productService;
    private ProductCurator productCurator;
    private ContentCurator contentCurator;
    private PoolCurator poolCurator;


    @Inject
    public PopulateHostedDBTask(ProductServiceAdapter productService, ProductCurator productCurator,
        ContentCurator contentCurator, PoolCurator poolCurator) {

        this.productService = productService;
        this.productCurator = productCurator;
        this.contentCurator = contentCurator;
        this.poolCurator = poolCurator;
    }

    @Override
    public void toExecute(JobExecutionContext context) throws JobExecutionException {
        int pcount = 0;
        int ccount = 0;
        log.info("Populating Hosted DB");

        Set<String> productIds = this.poolCurator.getAllKnownProductIds();

        for (Product product : this.productService.getProductsByIds(productIds)) {
            log.info("Updating product: " + product.toString());
            this.productCurator.createOrUpdate(product);
            ++pcount;

            for (ProductContent pcontent : product.getProductContent()) {
                log.info("Inserting product content: " + pcontent.getContent().toString());
                this.contentCurator.createOrUpdate(pcontent.getContent());
                ++ccount;
            }
        }

        log.info(String.format(
            "Finished populating Hosted DB. Inserted/updated %d product(s) and %d content",
            pcount, ccount
        ));
    }

////////////////////////////////////////////////////////////////////////////////////////////////////

    public static JobDetail createAsyncTask() {
        JobDetail detail = JobBuilder.newJob(PopulateHostedDBTask.class)
            .withIdentity("populated_hosted_db-" + Util.generateUUID())
            .requestRecovery(true) // TODO: Do we need recovery for this task?
            .build();

        return detail;
    }

}
