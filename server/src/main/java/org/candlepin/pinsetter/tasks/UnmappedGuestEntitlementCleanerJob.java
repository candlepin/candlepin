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

import org.candlepin.controller.PoolManager;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.service.SubscriptionServiceAdapter;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

import javax.inject.Inject;

/**
 *
 * UnmappedGuestEntitlementCleanerJob removes 24 hour unmapped guest entitlements
 * after the entitlement has expired.  Entitlements normally last until a pool expires.
 */
public class UnmappedGuestEntitlementCleanerJob extends KingpinJob {
    // Run at 3 AM and every 12 hours afterwards
    public static final String DEFAULT_SCHEDULE = "0 0 3/12 * * ?";

    private static final Logger log = LoggerFactory.getLogger(UnmappedGuestEntitlementCleanerJob.class);

    private EntitlementCurator entitlementCurator;
    private PoolManager poolManager;
    private SubscriptionServiceAdapter subAdapter;

    @Inject
    public UnmappedGuestEntitlementCleanerJob(EntitlementCurator entitlementCurator, PoolManager manager,
            SubscriptionServiceAdapter subAdapter) {
        this.entitlementCurator = entitlementCurator;
        this.poolManager = manager;
        this.subAdapter = subAdapter;
    }

    @Override
    public void toExecute(JobExecutionContext context)
        throws JobExecutionException {
        Date now = new Date();

        List<Entitlement> unmappedGuestEntitlements =
            entitlementCurator.findByPoolAttribute("unmapped_guests_only", "true");

        int total = 0;
        for (Entitlement e : unmappedGuestEntitlements) {
            if (isLapsed(e, now)) {
                poolManager.revokeEntitlement(subAdapter, e);
                total++;
            }
        }

        if (total > 0) {
            log.info("Reaped {} unmapped guest entitlements due to expiration.", total);
        }
        else {
            log.debug("No unmapped guest entitlements need reaping.");
        }
    }

    protected boolean isLapsed(Entitlement e, Date now) {
        Date consumerCreation = e.getConsumer().getCreated();
        Date lapseDate = new Date(consumerCreation.getTime() + 24L * 60L * 60L * 1000L);
        return lapseDate.before(now);
    }
}
