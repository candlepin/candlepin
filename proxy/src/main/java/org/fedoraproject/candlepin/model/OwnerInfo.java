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

package org.fedoraproject.candlepin.model;

import java.util.HashMap;
import java.util.Map;

/**
 * OwnerInfo
 * 
 * NOTE: this class only contains dynamic values. it should *not* be saved to the db.
 */
public class OwnerInfo {

    private Map<String, Integer> consumerCounts;
    private Map<String, Integer> entitlementsConsumedByType;
    
    public OwnerInfo() {
        consumerCounts = new HashMap<String, Integer>();
        entitlementsConsumedByType = new HashMap<String, Integer>();
    }
    
    public Map<String, Integer> getConsumerCounts() {
        return consumerCounts;
    }

    public Map<String, Integer> getEntitlementsConsumedByType() {
        return entitlementsConsumedByType;
    }
    
    public void addTypeTotal(ConsumerType type, int consumers, int entitlements) {
        consumerCounts.put(type.getLabel(), consumers);
        entitlementsConsumedByType.put(type.getLabel(), entitlements);
    }
}
