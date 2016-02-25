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

    private ProductCurator productCurator;
    private SubscriptionServiceAdapter subAdapter;
    private PoolManager poolManager;

    public static final String LAZY_REGEN = "lazy_regen";
    public static final String OWNER_IDS = "owner_ids";

    @Inject
    public RefreshPoolsForProductJob(ProductCurator productCurator,
        SubscriptionServiceAdapter subAdapter, PoolManager poolManager) {

        this.productCurator = productCurator;
        this.subAdapter = subAdapter;
        this.poolManager = poolManager;
    }

    @Override
    public void toExecute(JobExecutionContext context)
        throws JobExecutionException {
        String productId = context.getMergedJobDataMap().getString(JobStatus.TARGET_ID);
        List<String> ownerIds = (List<String>) context.getMergedJobDataMap().get(OWNER_IDS);
        Boolean lazy = context.getMergedJobDataMap().getBoolean(LAZY_REGEN);
        Refresher refresher = poolManager.getRefresher(subAdapter, lazy);
        int count = 0;

        // PER-ORG PRODUCT VERSIONING TODO:
        // This whole thing needs to be revisited. We need to address a couple of shortcomings in
        // this method:
        //  1: Products can be removed from an org after the job is scheduled but before we run;
        //      how should we handle this situation?
        //  2: Varying success states cannot really be set on the result very gracefully

        StringBuilder result = new StringBuilder();

        for (String ownerId : ownerIds) {
            Product product = this.productCurator.lookupById(ownerId, productId);

            if (product != null) {
                refresher.add(product);
                ++count;
            }
            else {
                result.append(
                    "Unable to refresh pools for product \"" + productId + "\"" +
                    ": Could not find the specified product for owner \"" + ownerId + "\"\n"
                );
            }
        }

        if (count > 0) {
            refresher.run();
            result.append("Pools refreshed for product " + productId + "\n");
        }

        context.setResult(result.toString());
    }

    public static JobDetail forProduct(Product product, Boolean lazy) {
        JobDataMap map = new JobDataMap();
        List<String> ownerIds = new LinkedList<String>();

        for (Owner owner : product.getOwners()) {
            ownerIds.add(owner.getId());
        }

        map.put(JobStatus.TARGET_TYPE, JobStatus.TargetType.PRODUCT);
        map.put(JobStatus.TARGET_ID, product.getId());
        map.put(OWNER_IDS, ownerIds);
        map.put(LAZY_REGEN, lazy);

        JobDetail detail = newJob(RefreshPoolsForProductJob.class)
            .withIdentity("refresh_pools_for_product" + Util.generateUUID())
            .requestRecovery(true) // recover the job upon restarts
            .usingJobData(map)
            .build();

        return detail;
    }
}
