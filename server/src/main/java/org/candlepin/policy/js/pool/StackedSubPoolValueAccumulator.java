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
package org.candlepin.policy.js.pool;

import org.candlepin.model.Entitlement;
import org.candlepin.model.Product;

import java.util.Collection;
import java.util.Date;


/**
 * This class is responsible for determining the accumulated values
 * that can be used to update a stacked sub pool from a collection
 * of stacked entitlements.
 */
public class StackedSubPoolValueAccumulator {

    // Accumulate the expected values before we set anything on the pool:
    private Entitlement eldest;
    private Entitlement eldestWithVirtLimit;
    private Date startDate;
    private Date endDate;

    public StackedSubPoolValueAccumulator(Collection<Entitlement> stackedEnts) {
        for (Entitlement nextStacked : stackedEnts) {
            updateEldest(nextStacked);
            accumulateDateRange(nextStacked);
            updateEldestWithVirtLimit(nextStacked);
        }
    }

    /**
     * Check if the next stacked entitlement is the eldest in the stack.
     *
     * @param nextStacked the entitlement to check.
     */
    private void updateEldest(Entitlement nextStacked) {
        // if the date is null, must be a entitlement about to be created
        Date createdDate = (nextStacked.getCreated() == null) ?
            new Date() : nextStacked.getCreated();

        if (eldest == null || createdDate.before(eldest.getCreated())) {
            eldest = nextStacked;
        }
    }

    /**
     * Stacked sub pool should have a date range that spans the date range
     * of the entire stack. Check to see if this entitlement will affect
     * the start or end date.
     *
     * @param nextStacked the entitlement to check.
     */
    private void accumulateDateRange(Entitlement nextStacked) {
        // the pool should be updated to have the earliest start date.
        if (startDate == null || nextStacked.getStartDate().before(startDate)) {
            startDate = nextStacked.getStartDate();
        }

        // The pool should be updated to have the latest end date.
        if (endDate == null || nextStacked.getEndDate().after(endDate)) {
            endDate = nextStacked.getEndDate();
        }
    }

    /**
     * Check if the entitlement is the eldest with a specified virt limit.
     *
     * @param nextStacked the entitlement to check.
     * @param nextStacked
     */
    private void updateEldestWithVirtLimit(Entitlement nextStacked) {
        // Keep track of the eldest with virt limit so that we can change the
        // quantity of the sub pool.
        if (nextStacked.getPool().hasMergedAttribute(Product.Attributes.VIRT_LIMIT)) {
            // if the date is null, must be a entitlement about to be created
            Date createdDate = (nextStacked.getCreated() == null) ?
                new Date() : nextStacked.getCreated();
            if (eldestWithVirtLimit == null ||
                createdDate.before(eldestWithVirtLimit.getCreated())) {
                eldestWithVirtLimit = nextStacked;
            }
        }
    }

    public Entitlement getEldest() {
        return eldest;
    }

    public Entitlement getEldestWithVirtLimit() {
        return eldestWithVirtLimit;
    }

    public Date getStartDate() {
        return startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

}
