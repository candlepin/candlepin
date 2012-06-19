/**
 * Copyright (c) 2009 Red Hat, Inc.
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

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import com.google.inject.Inject;
import org.candlepin.controller.PoolManager;

/**
 * The Class RegenEntitlementCertsJob.
 */
public class RegenProductEntitlementCertsJob implements Job {

    private PoolManager poolManager;
    public static final String PROD_ID = "product_id";
    public static final String LAZY_REGEN = "lazy_regen";

    @Inject
    public RegenProductEntitlementCertsJob(PoolManager poolManager) {
        this.poolManager = poolManager;
    }

    @Override
    public void execute(JobExecutionContext arg0) throws JobExecutionException {
        String prodId = arg0.getJobDetail().getJobDataMap().getString(
            PROD_ID);
        boolean lazy = arg0.getJobDetail().getJobDataMap().getBoolean(LAZY_REGEN);
        this.poolManager.regenerateCertificatesOf(prodId, lazy);
    }
}
