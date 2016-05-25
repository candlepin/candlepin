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

import static org.quartz.JobBuilder.*;

import org.candlepin.controller.PoolManager;
import org.candlepin.controller.Refresher;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.util.Util;

import com.google.inject.Inject;

import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.util.List;
import java.util.LinkedList;



/**
 * RefreshPoolsForProductJob
 */
public class RefreshPoolsForProductJob extends KingpinJob {

    private OwnerProductCurator ownerProductCurator;
    private PoolManager poolManager;
    private ProductCurator productCurator;
    private SubscriptionServiceAdapter subAdapter;

    public static final String LAZY_REGEN = "lazy_regen";

    @Inject
    public RefreshPoolsForProductJob(OwnerProductCurator ownerProductCurator, ProductCurator productCurator,
        PoolManager poolManager, SubscriptionServiceAdapter subAdapter) {

        this.ownerProductCurator = ownerProductCurator;
        this.poolManager = poolManager;
        this.productCurator = productCurator;
        this.subAdapter = subAdapter;
    }

    @Override
    public void toExecute(JobExecutionContext context) throws JobExecutionException {
        String productUuid = context.getMergedJobDataMap().getString(JobStatus.TARGET_ID);
        Boolean lazy = context.getMergedJobDataMap().getBoolean(LAZY_REGEN);
        StringBuilder result = new StringBuilder();

        Product product = this.productCurator.find(productUuid);

        if (product != null) {
            Refresher refresher = poolManager.getRefresher(subAdapter, lazy);

            refresher.add(product);
            refresher.run();

            result.append("Pools refreshed for product: ")
                .append(productUuid)
                .append("\n");
        }
        else {
            result.append("Unable to refresh pools for product \"")
                .append(productUuid)
                .append("\": Could not find a product with the specified UUID");
        }

        context.setResult(result.toString());
    }

    public static JobDetail forProduct(Product product, Boolean lazy) {
        JobDataMap map = new JobDataMap();

        map.put(JobStatus.TARGET_TYPE, JobStatus.TargetType.PRODUCT);
        map.put(JobStatus.TARGET_ID, product.getUuid());
        map.put(LAZY_REGEN, lazy);

        JobDetail detail = newJob(RefreshPoolsForProductJob.class)
            .withIdentity("refresh_pools_for_product" + Util.generateUUID())
            .requestRecovery(true) // recover the job upon restarts
            .usingJobData(map)
            .build();

        return detail;
    }
}
