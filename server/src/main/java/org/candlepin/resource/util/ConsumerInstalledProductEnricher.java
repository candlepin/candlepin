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
package org.candlepin.resource.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerInstalledProduct;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Product;
import org.candlepin.policy.js.compliance.ComplianceRules;
import org.candlepin.policy.js.compliance.ComplianceStatus;

/**
 * Responsible for enriching a {@link ConsumerInstalledProduct} with
 * product data.
 */
public class ConsumerInstalledProductEnricher {

    private static final String RED_STATUS = "red";
    private static final String YELLOW_STATUS = "yellow";
    private static final String GREEN_STATUS = "green";

    private static final String[] GLOBAL_ATTRS = {"guest_limit"};

    private ComplianceStatus status;
    private Consumer consumer;
    private ComplianceRules complianceRules;

    public ConsumerInstalledProductEnricher(Consumer consumer,
        ComplianceStatus populatedComplianceStatus, ComplianceRules complianceRules) {
        this.status = populatedComplianceStatus;
        this.consumer = consumer;
        this.complianceRules = complianceRules;
    }

    /**
     * Enrich a {@link ConsumerInstalledProduct} with data from the specified product.
     *
     * @param cip the installed product to enrich.
     * @param prod the product to pull the data from.
     */
    public void enrich(ConsumerInstalledProduct cip, Product prod) {
        cip.setStatus(getStatus(prod.getId()));
        if (cip.getVersion() == null) {
            cip.setVersion(prod.getAttributeValue("version"));
        }
        if (cip.getArch() == null) {
            cip.setArch(prod.getAttributeValue("arch"));
        }
        DateRange range = getValidDateRange(prod);
        cip.setStartDate(range != null ? range.getStartDate() : null);
        cip.setEndDate(range != null ? range.getEndDate() : null);
    }

    /**
     * Get the status of the product.
     *
     * @param prodId the id of the product to check.
     * @return the status string
     */
    protected String getStatus(String prodId) {
        String status = "";
        if (this.status.getNonCompliantProducts().contains(prodId)) {
            status = RED_STATUS;
        }
        else if (this.status.getPartiallyCompliantProducts()
            .containsKey(prodId)) {
            status = YELLOW_STATUS;
        }
        else if (this.status.getCompliantProducts().containsKey(
            prodId)) {
            status = GREEN_STATUS;
        }
        return status;
    }

    /**
     * Get the {@link DateRange} that the specified product will be valid (inclusive).
     *
     * @param product the product
     * @return the valid date range of the product.
     */
    protected DateRange getValidDateRange(Product product) {
        // TODO: This date range's meaning changes depending if you are green or yellow
        // currently. If green, the range is the time you are green. If yellow, the range
        // is the time you are yellow or better. The valid date range should always mean
        // the same thing, either it's the range of time you're green, or the range of
        // time you're yellow or better. i.e. if I'm green now and then going to be yellow,
        // the date range should show me the whole span of time until I go red.
        String prodStatus = getStatus(product.getId());
        if (prodStatus == RED_STATUS) {
            return null;
        }
        Date current = status.getDate();
        List<Entitlement> allEntitlements = getEntitlementsForProduct(product);
        List<Date> dates = getDatesAndNowSorted(current, allEntitlements);
        if (dates.size() <= 1) {
            return null;
        }
        int currentIdx = dates.indexOf(current);
        Date startDate = null;
        Date endDate = null;
        for (int i = currentIdx - 1; i >= 0; i--) {
            if (!isProductValidOnDate(product.getId(), dates.get(i))) {
                startDate = dates.get(i + 1);
                break;
            }
        }
        if (startDate == null) {
            startDate = new Date(dates.get(0).getTime());
        }

        for (int i = currentIdx + 1; i < dates.size(); i++) {
            if (!isProductValidOnDate(product.getId(), dates.get(i))) {
                endDate = new Date(dates.get(i).getTime() - 1);
                break;
            }
        }
        return new DateRange(startDate, endDate);
    }

    private boolean isProductValidOnDate(String prodId, Date date) {
        if (this.getStatus(prodId) == GREEN_STATUS) {
            // Calculating compliantUntil is expensive, and useless in this case
            ComplianceStatus status = complianceRules.getStatus(consumer, date, false);
            return status.getCompliantProducts().containsKey(prodId);
        }
        return !getConsumerEntsProvidingOnDate(prodId, date).isEmpty();
    }

    private List<Entitlement> getConsumerEntsProvidingOnDate(String productId, Date date) {
        List<Entitlement> active = new LinkedList<Entitlement>();
        for (Entitlement ent : consumer.getEntitlements()) {
            if (ent.isValidOnDate(date) && ent.getPool().provides(productId)) {
                active.add(ent);
            }
        }
        return active;
    }

    private List<Date> getDatesAndNowSorted(Date current, List<Entitlement> ents) {
        Set<Date> dates = new HashSet<Date>();
        dates.add(current);
        for (Entitlement ent : ents) {
            dates.add(ent.getStartDate());
            Date end = new Date(ent.getEndDate().getTime() + 1);
            dates.add(end);
        }
        List<Date> sorted = new ArrayList<Date>(dates);
        Collections.sort(sorted);
        return sorted;
    }

    /**
     * Collect all entitlements from the consumer covering the specified product.
     *
     * @param product the product to match.
     * @return all entitlements from the consumer covering the specified product.
     */
    private List<Entitlement> getEntitlementsForProduct(Product product) {
        Set<Entitlement> productEnts = new HashSet<Entitlement>();
        Set<String> stackIds = new HashSet<String>();
        Map<String, Set<Entitlement>> stackIdMap = new HashMap<String, Set<Entitlement>>();

        // Track global attribute usage.  If an entitlement that provies one of our products
        // uses a global attribute, we need to send in everything that provides it
        Map<String, List<Entitlement>> globalAttrMap = createGlobalsMap();
        Set<String> requiredGlobalAttrs = new HashSet<String>();

        for (Entitlement ent : this.consumer.getEntitlements()) {
            if (ent.getPool().provides(product.getId())) {
                productEnts.add(ent);
                //If this entitlement is stackable,
                //the whole stack may be required, even if
                //this is the only ent that provides the product
                if (ent.getPool().hasProductAttribute("stacking_id")) {
                    stackIds.add(ent.getPool()
                        .getProductAttribute("stacking_id").getValue());
                }
            }
            // Save the stacking id so we don't have to loop over everything again
            if (ent.getPool().hasProductAttribute("stacking_id")) {
                String key = ent.getPool().getProductAttribute("stacking_id").getValue();
                if (!stackIdMap.containsKey(key)) {
                    stackIdMap.put(key, new HashSet<Entitlement>());
                }
                stackIdMap.get(key).add(ent);
            }
            // Save the global attributes so we don't need to loop over them again.
            for (String attribute : globalAttrMap.keySet()) {
                if (ent.getPool().hasProductAttribute(attribute)) {
                    globalAttrMap.get(attribute).add(ent);
                    if (ent.getPool().provides(product.getId())) {
                        requiredGlobalAttrs.add(attribute);
                    }
                }
            }
        }
        //Add entitlements that provide via a stack,
        //however may not physically provide the product
        for (String stackId : stackIds) {
            productEnts.addAll(stackIdMap.get(stackId));
        }

        for (String requiredAttr : requiredGlobalAttrs) {
            productEnts.addAll(globalAttrMap.get(requiredAttr));
        }

        //Cast the set back to a List
        return new ArrayList<Entitlement>(productEnts);
    }

    private Map<String, List<Entitlement>> createGlobalsMap() {
        Map<String, List<Entitlement>> globalAttrMap = new HashMap<String, List<Entitlement>>();
        for (String attribute : GLOBAL_ATTRS) {
            globalAttrMap.put(attribute, new LinkedList<Entitlement>());
        }
        return globalAttrMap;
    }
}
