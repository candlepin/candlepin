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

import static org.quartz.JobBuilder.newJob;

import org.candlepin.controller.PoolManager;
import org.candlepin.model.Product;
import org.candlepin.pinsetter.core.model.JobStatus;
import org.candlepin.service.ProductServiceAdapter;
import org.candlepin.util.Util;

import com.google.inject.Inject;

import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * RefreshPoolsForProductJob
 */
public class RefreshPoolsForProductJob extends KingpinJob {

    private ProductServiceAdapter productAdapter;
    private PoolManager poolManager;

    public static final String LAZY_REGEN = "lazy_regen";

    @Inject
    public RefreshPoolsForProductJob(ProductServiceAdapter productAdapter,
        PoolManager poolManager) {
        this.productAdapter = productAdapter;
        this.poolManager = poolManager;
    }

    @Override
    public void toExecute(JobExecutionContext context)
        throws JobExecutionException {
        String productId = context.getMergedJobDataMap().getString(JobStatus.TARGET_ID);
        Boolean lazy = context.getMergedJobDataMap().getBoolean(LAZY_REGEN);

        poolManager.getRefresher(lazy).add(
            productAdapter.getProductById(productId)).run();

        context.setResult("Pools refreshed for product " + productId);
    }

    public static JobDetail forProduct(Product product, Boolean lazy) {
        JobDataMap map = new JobDataMap();
        map.put(JobStatus.TARGET_TYPE, JobStatus.TargetType.PRODUCT);
        map.put(JobStatus.TARGET_ID, product.getId());
        map.put(LAZY_REGEN, lazy);

        JobDetail detail = newJob(RefreshPoolsForProductJob.class)
            .withIdentity("refresh_pools_for_product" + Util.generateUUID())
            .requestRecovery(true) // recover the job upon restarts
            .usingJobData(map)
            .build();

        return detail;
    }
}
