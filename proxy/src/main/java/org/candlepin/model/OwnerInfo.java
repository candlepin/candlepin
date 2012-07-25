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

package org.candlepin.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OwnerInfo NOTE: this class only contains dynamic values. it should *not* be
 * saved to the db.
 */
public class OwnerInfo {

    private Map<String, Integer> consumerCounts;
    private Map<String, Integer> consumerGuestCounts;
    private Map<String, Integer> entitlementsConsumedByType;
    private Map<String, Integer> consumerTypeCountByPool;
    private Map<String, Integer> enabledConsumerTypeCountByPool;
    private Map<String, Integer> consumerCountsByComplianceStatus;
    private Map<String, ConsumptionTypeCounts> entitlementsConsumedByFamily;
    private Pool poolNearestToExpiry;

    public static final String GUEST = "guest";
    public static final String PHYSICAL = "physical";

    public OwnerInfo() {
        consumerCounts = new HashMap<String, Integer>();
        entitlementsConsumedByType = new HashMap<String, Integer>();
        consumerTypeCountByPool = new HashMap<String, Integer>();
        enabledConsumerTypeCountByPool = new HashMap<String, Integer>();
        consumerCountsByComplianceStatus = new HashMap<String, Integer>();
        entitlementsConsumedByFamily = new HashMap<String, ConsumptionTypeCounts>();

        consumerGuestCounts = new HashMap<String, Integer>();
        consumerGuestCounts.put(GUEST, 0);
        consumerGuestCounts.put(PHYSICAL, 0);
    }

    public Map<String, Integer> getConsumerCounts() {
        return consumerCounts;
    }

    public Map<String, Integer> getEntitlementsConsumedByType() {
        return entitlementsConsumedByType;
    }

    public Map<String, Integer> getConsumerTypeCountByPool() {
        return consumerTypeCountByPool;
    }

    public Map<String, Integer> getEnabledConsumerTypeCountByPool() {
        return enabledConsumerTypeCountByPool;
    }

    public Map<String, Integer> getConsumerCountsByComplianceStatus() {
        return consumerCountsByComplianceStatus;
    }

    public Integer getConsumerCountByStatus(String status) {
        // Consider the count as zero if the status was not found. This handles a
        // case where there are no consumers of a particular status.
        if (!consumerCountsByComplianceStatus.containsKey(status)) {
            return 0;
        }
        return consumerCountsByComplianceStatus.get(status);
    }

    public void setConsumerCountByComplianceStatus(String status, Integer count) {
        consumerCountsByComplianceStatus.put(status, count);
    }

    public Map<String, ConsumptionTypeCounts> getEntitlementsConsumedByFamily() {
        return entitlementsConsumedByFamily;
    }

    public void addTypeTotal(ConsumerType type, int consumers, int entitlements) {
        consumerCounts.put(type.getLabel(), consumers);
        entitlementsConsumedByType.put(type.getLabel(), entitlements);
    }

    public void addToConsumerTypeCountByPool(ConsumerType type) {
        Integer count = consumerTypeCountByPool.get(type.getLabel());
        if (count == null) {
            count = 0;
        }
        consumerTypeCountByPool.put(type.getLabel(), ++count);
    }

    public void addToEnabledConsumerTypeCountByPool(ConsumerType type) {
        Integer count = enabledConsumerTypeCountByPool.get(type.getLabel());
        if (count == null) {
            count = 0;
        }
        enabledConsumerTypeCountByPool.put(type.getLabel(), ++count);
    }

    public void setConsumerTypesByPool(List<ConsumerType> consumerTypes) {
        for (ConsumerType c : consumerTypes) {
            consumerTypeCountByPool.put(c.getLabel(), 0);
        }
    }

    public void addToEntitlementsConsumedByFamily(String family, int physical,
        int virtual) {
        ConsumptionTypeCounts typeCounts;
        if (!entitlementsConsumedByFamily.containsKey(family)) {
            typeCounts = new ConsumptionTypeCounts(0, 0);
            entitlementsConsumedByFamily.put(family, typeCounts);
        }
        else {
            typeCounts = entitlementsConsumedByFamily.get(family);
        }

        typeCounts.physical += physical;
        typeCounts.guest += virtual;
    }

    /**
     * ConsumptionTypeCounts - stores virtual / physical entitlement consumption counts
     */
    public static class ConsumptionTypeCounts {
        private int physical;
        private int guest;

        public ConsumptionTypeCounts(int physical, int guest) {
            this.physical = physical;
            this.guest = guest;
        }

        public int getPhysical() {
            return physical;
        }

        public int getGuest() {
            return guest;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ConsumptionTypeCounts)) {
                return false;
            }

            ConsumptionTypeCounts other = (ConsumptionTypeCounts) o;
            return this.physical == other.physical && this.guest == other.guest;
        }

        @Override
        public int hashCode() {
            return physical * guest;
        }

        public String toString() {
            return String.format("Physical: %d, Virtual: %d", physical, guest);
        }
    }

    public Map<String, Integer> getConsumerGuestCounts() {
        return consumerGuestCounts;
    }

    public void setGuestCount(Integer count) {
        consumerGuestCounts.put(GUEST, count);
    }

    public void setPhysicalCount(Integer count) {
        consumerGuestCounts.put(PHYSICAL, count);
    }

    public Pool getPoolNearestToExpiry() {
        return poolNearestToExpiry;
    }

    public void setPoolNearestToExpiry(Pool poolNearestToExpiry) {
        this.poolNearestToExpiry = poolNearestToExpiry;
    }

}
