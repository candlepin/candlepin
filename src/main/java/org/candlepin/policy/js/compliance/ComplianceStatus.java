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

import org.candlepin.dto.api.v1.DateRange;
import org.candlepin.model.Entitlement;

import com.fasterxml.jackson.annotation.JsonFilter;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;



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
    public static final String GRAY = "disabled";

    private Date date;
    private Date compliantUntil;
    private Set<String> nonCompliantProducts;
    private Map<String, Set<Entitlement>> compliantProducts;
    private Map<String, Set<Entitlement>> partiallyCompliantProducts; // stacked
    private Map<String, Set<Entitlement>> partialStacks;
    private Map<String, DateRange> productComplianceDateRanges;
    private Set<ComplianceReason> reasons;
    private boolean disabled = false;

    public ComplianceStatus() {
        this.nonCompliantProducts = new HashSet<>();
        this.compliantProducts = new HashMap<>();
        this.partiallyCompliantProducts = new HashMap<>();
        this.partialStacks = new HashMap<>();
        this.productComplianceDateRanges = new HashMap<>();
        this.reasons = new HashSet<>();
    }

    public ComplianceStatus(Date date) {
        this();
        this.setDate(date);
    }

    /**
     * @return Map of compliant product IDs and the entitlements that provide them.
     */
    public Map<String, Set<Entitlement>> getCompliantProducts() {
        return compliantProducts;
    }

    public void setCompliantProducts(Map<String, Set<Entitlement>> compliantProducts) {
        if (compliantProducts != null) {
            if (this.compliantProducts == null) {
                this.compliantProducts = new HashMap<>();
            }
            else {
                this.compliantProducts.clear();
            }
            this.compliantProducts.putAll(compliantProducts);
        }
        else {
            this.compliantProducts = null;
        }
    }

    public void setDate(Date date) {
        this.date = date;
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

    public void setPartiallyCompliantProducts(Map<String, Set<Entitlement>> partiallyCompliantProducts) {
        if (partiallyCompliantProducts != null) {
            if (this.partiallyCompliantProducts == null) {
                this.partiallyCompliantProducts = new HashMap<>();
            }
            else {
                this.partiallyCompliantProducts.clear();
            }
            this.partiallyCompliantProducts.putAll(partiallyCompliantProducts);
        }
        else {
            this.partiallyCompliantProducts = null;
        }
    }

    public void addPartiallyCompliantProduct(String productId, Entitlement entitlement) {
        if (!partiallyCompliantProducts.containsKey(productId)) {
            partiallyCompliantProducts.put(productId, new HashSet<>());
        }

        partiallyCompliantProducts.get(productId).add(entitlement);
    }

    public void addCompliantProduct(String productId, Entitlement entitlement) {
        if (!compliantProducts.containsKey(productId)) {
            compliantProducts.put(productId, new HashSet<>());
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

    public void setPartialStacks(Map<String, Set<Entitlement>> partialStacks) {
        if (partialStacks != null) {
            if (this.partialStacks == null) {
                this.partialStacks = new HashMap<>();
            }
            else {
                this.partialStacks.clear();
            }
            this.partialStacks.putAll(partialStacks);
        }
        else {
            this.partialStacks = null;
        }
    }

    public void addPartialStack(String stackId, Entitlement entitlement) {
        if (!partialStacks.containsKey(stackId)) {
            partialStacks.put(stackId, new HashSet<>());
        }

        partialStacks.get(stackId).add(entitlement);
    }

    public Map<String, DateRange> getProductComplianceDateRanges() {
        return this.productComplianceDateRanges;
    }

    public void addProductComplianceDateRange(String productId, DateRange dateRange) {
        if (!productComplianceDateRanges.containsKey(productId)) {
            productComplianceDateRanges.put(productId, dateRange);
        }
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
        if (isDisabled()) {
            return GRAY;
        }
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
        if (reasons == null) {
            reasons = new HashSet<>();
        }

        this.reasons = reasons;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public boolean isDisabled() {
        return this.disabled;
    }
}
