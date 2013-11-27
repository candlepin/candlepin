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
package org.candlepin.policy.js.compliance;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.candlepin.model.Entitlement;
import org.codehaus.jackson.map.annotate.JsonFilter;

/**
 * ComplianceStatus
 *
 * Represents the compliance status for a given consumer. Carries information
 * about which products are fully entitled, not entitled, or partially entitled. (stacked)
 */
@JsonFilter("ComplianceFilter")
public class ComplianceStatus {
    public static final String GREEN = "valid";
    public static final String YELLOW = "partial";
    public static final String RED = "invalid";

    private Date date;
    private Date compliantUntil;
    private Set<String> nonCompliantProducts;
    private Map<String, Set<Entitlement>> compliantProducts;
    private Map<String, Set<Entitlement>> partiallyCompliantProducts; // stacked
    private Map<String, Set<Entitlement>> partialStacks;
    private Set<ComplianceReason> reasons;

    public ComplianceStatus() {
        this.nonCompliantProducts = new HashSet<String>();
        this.compliantProducts = new HashMap<String, Set<Entitlement>>();
        this.partiallyCompliantProducts = new HashMap<String, Set<Entitlement>>();
        this.partialStacks = new HashMap<String, Set<Entitlement>>();
        this.reasons = new HashSet<ComplianceReason>();
    }

    public ComplianceStatus(Date date) {
        this();
        this.date = date;
    }

    /**
     * @return Map of compliant product IDs and the entitlements that provide them.
     */
    public Map<String, Set<Entitlement>> getCompliantProducts() {
        return compliantProducts;
    }

    /**
     *
     * @return Date this compliance status was checked for.
     */
    public Date getDate() {
        return date;
    }

    /**
     * @return Set of product IDs installed on the consumer, but not provided by any
     * entitlement. (not even partially)
     */
    public Set<String> getNonCompliantProducts() {
        return nonCompliantProducts;
    }

    public void addNonCompliantProduct(String productId) {
        this.nonCompliantProducts.add(productId);
    }

    /**
     * Partially compliant products may be partially stacked, or just non-stacked regular
     * entitlements which carry a socket limitation which the consumer system exceeds.
     *
     * @return Map of compliant product IDs and the entitlements that partially
     * provide them.
     */
    public Map<String, Set<Entitlement>> getPartiallyCompliantProducts() {
        return partiallyCompliantProducts;
    }

    public void addPartiallyCompliantProduct(String productId, Entitlement entitlement) {
        if (!partiallyCompliantProducts.containsKey(productId)) {
            partiallyCompliantProducts.put(productId, new HashSet<Entitlement>());
        }
        partiallyCompliantProducts.get(productId).add(entitlement);
    }

    public void addCompliantProduct(String productId, Entitlement entitlement) {
        if (!compliantProducts.containsKey(productId)) {
            compliantProducts.put(productId, new HashSet<Entitlement>());
        }
        compliantProducts.get(productId).add(entitlement);
    }

    /**
     * @return Map of stack ID to entitlements for each partially completed stack.
     * This will contain all the entitlements in the partially compliant list, but also
     * entitlements which are partially stacked but do not provide any installed product.
     *
     */
    public Map<String, Set<Entitlement>> getPartialStacks() {
        return partialStacks;
    }

    public void addPartialStack(String stackId, Entitlement entitlement) {
        if (!partialStacks.containsKey(stackId)) {
            partialStacks.put(stackId, new HashSet<Entitlement>());
        }
        partialStacks.get(stackId).add(entitlement);
    }

    public Date getCompliantUntil() {
        return this.compliantUntil;
    }

    public void setCompliantUntil(Date date) {
        this.compliantUntil = date;
    }

    public boolean isCompliant() {
        return reasons.isEmpty();
    }

    public String getStatus() {
        if (isCompliant()) {
            return GREEN;
        }
        if (nonCompliantProducts.isEmpty()) {
            return YELLOW;
        }
        return RED;
    }

    public Set<ComplianceReason> getReasons() {
        return reasons;
    }

    public void setReasons(Set<ComplianceReason> reasons) {
        this.reasons = reasons;
    }
}
