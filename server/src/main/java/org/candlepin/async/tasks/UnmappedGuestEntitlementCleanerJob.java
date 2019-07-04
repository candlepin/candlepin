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
import org.candlepin.controller.Entitler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

/**
 * UnmappedGuestEntitlementCleanerJob removes 24 hour unmapped guest entitlements
 * after the entitlement has expired.  Entitlements normally last until a pool expires.
 */
public class UnmappedGuestEntitlementCleanerJob implements AsyncJob {
    private static Logger log = LoggerFactory.getLogger(UnmappedGuestEntitlementCleanerJob.class);

    public static final String JOB_KEY = "UNMAPPED_GUEST_ENT_CLEANER";
    public static final String JOB_NAME = "Unmapped guest entitlement cleaner";

    // Run at 3 AM and every 12 hours afterwards
    public static final String DEFAULT_SCHEDULE = "0 0 3/12 * * ?";

    private Entitler entitler;

    @Inject
    public UnmappedGuestEntitlementCleanerJob(Entitler entitler) {
        this.entitler = entitler;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object execute(JobExecutionContext context) throws JobExecutionException {
        int total = entitler.revokeUnmappedGuestEntitlements();

        String outcome;
        if (total > 0) {
            outcome = String.format("Reaped %d unmapped guest entitlements due to expiration.", total);
            log.info(outcome);
        }
        else {
            outcome = "No unmapped guest entitlements need reaping.";
            log.debug(outcome);
        }
        return outcome;
    }
}
