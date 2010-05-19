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
package org.fedoraproject.candlepin.policy.js;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.fedoraproject.candlepin.model.Pool;

/**
 * represents a read-only entitlement pool
 */
public class ReadOnlyEntitlementPool {

    private Pool entPool;

    /**
     * @param entPool
     *            the read-write version of the EntitlementPool to copy.
     */
    public ReadOnlyEntitlementPool(Pool entPool) {
        this.entPool = entPool;
    }

    /**
     * Returns true if there are available entitlements remaining.
     * 
     * @return true if there are available entitlements remaining.
     */
    public Boolean entitlementsAvailable() {
        return entPool.entitlementsAvailable();
    }

    public Long getId() {
        return entPool.getId();
    }

    public Long getMaxMembers() {
        return entPool.getQuantity();
    }

    public Long getCurrentMembers() {
        return entPool.getConsumed();
    }

    public Date getStartDate() {
        return entPool.getStartDate();
    }

    public Date getEndDate() {
        return entPool.getEndDate();
    }

    public String getProductId() {
        return entPool.getProductId();
    }
    
    public static List<ReadOnlyEntitlementPool> fromCollection(Collection<Pool> pools) {
        List<ReadOnlyEntitlementPool> toReturn 
            = new ArrayList<ReadOnlyEntitlementPool>(pools.size());
        for (Pool pool : pools) {
            toReturn.add(new ReadOnlyEntitlementPool(pool));
        }
        return toReturn;
    }
}
