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

package org.candlepin.model;

import org.candlepin.dto.api.v1.ConsumptionTypeCountsDTO;
import org.candlepin.dto.api.v1.OwnerInfo;

import java.util.List;

/**
 * OwnerInfoBuilder is used to accumulate data to build OwnerInfo with dynamic values.
 */

public class OwnerInfoBuilder {

    private OwnerInfo ownerInfo;

    public static final String PHYSICAL = "physical";
    public static final String GUEST = "guest";

    public OwnerInfoBuilder() {
        ownerInfo = new OwnerInfo();
        ownerInfo.putConsumerGuestCounts(GUEST, 0);
        ownerInfo.putConsumerGuestCounts(PHYSICAL, 0);
    }

    public void addTypeTotal(ConsumerType type, int consumers, int entitlements) {
        ownerInfo.putConsumerCounts(type.getLabel(), consumers);
        ownerInfo.putEntitlementsConsumedByType(type.getLabel(), entitlements);
    }

    public OwnerInfo build() {
        return this.ownerInfo;
    }

    public void addToConsumerTypeCountByPool(ConsumerType type, int toAdd) {
        Integer count = ownerInfo.getConsumerTypeCountByPool().get(type.getLabel());
        if (count == null) {
            count = 0;
        }
        ownerInfo.putConsumerTypeCountByPool(type.getLabel(), count + toAdd);
    }

    public void addToEnabledConsumerTypeCountByPool(ConsumerType type, int toAdd) {
        Integer count = ownerInfo.getEnabledConsumerTypeCountByPool().get(type.getLabel());
        if (count == null) {
            count = 0;
        }
        ownerInfo.putEnabledConsumerTypeCountByPool(type.getLabel(), count + toAdd);
    }

    public void setConsumerTypesByPool(List<ConsumerType> consumerTypes) {
        for (ConsumerType c : consumerTypes) {
            ownerInfo.putConsumerTypeCountByPool(c.getLabel(), 0);
        }
    }

    public void addToEntitlementsConsumedByFamily(String family, int physical,
        int virtual) {
        ConsumptionTypeCountsDTO typeCounts;
        if (!ownerInfo.getEntitlementsConsumedByFamily().containsKey(family)) {
            typeCounts = new ConsumptionTypeCountsDTO().physical(0).guest(0);
            ownerInfo.putEntitlementsConsumedByFamily(family, typeCounts);
        }
        else {
            typeCounts = ownerInfo.getEntitlementsConsumedByFamily().get(family);
        }

        typeCounts.setPhysical(typeCounts.getPhysical() + physical);
        typeCounts.setGuest(typeCounts.getGuest() + virtual);
    }

    public void addDefaultEntitlementsConsumedByFamily(int physical, int virtual) {
        for (String key : ownerInfo.getEntitlementsConsumedByFamily().keySet()) {
            ConsumptionTypeCountsDTO count = ownerInfo.getEntitlementsConsumedByFamily().get(key);
            physical -= count.getPhysical();
            virtual -= count.getGuest();
        }

        // just ignore the default family if we have nothing to put in it.
        if (physical > 0 || virtual > 0) {
            addToEntitlementsConsumedByFamily("none", physical, virtual);
        }
    }

    public void addDefaultEnabledConsumerTypeCount(int activePools) {
        for (String key : ownerInfo.getConsumerTypeCountByPool().keySet()) {
            // don't want to count systems twice!
            if (key.equals("system")) {
                continue;
            }
            activePools -= ownerInfo.getConsumerTypeCountByPool().get(key);
        }
        ownerInfo.putConsumerTypeCountByPool("system", activePools);
    }

    public void setGuestCount(Integer count) {
        ownerInfo.putConsumerGuestCounts(GUEST, count);
    }

    public void setPhysicalCount(Integer count) {
        ownerInfo.putConsumerGuestCounts(PHYSICAL, count);
    }

    public void setConsumerCountByComplianceStatus(String status, Integer count) {
        ownerInfo.putConsumerCountsByComplianceStatus(status, count);
    }
}
