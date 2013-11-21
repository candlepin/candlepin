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
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
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

        // We only return a DateRange for valid products.
        String status = getStatus(product.getId());

        // This just saves some time, not necessary for correctness
        if (status == RED_STATUS) {
            return null;
        }

        // The status is GREEN_STATUS so we should definitely get entitlements from the
        // consumer. Check to make sure.
        List<Entitlement> allEntitlements = getEntitlementsForProduct(product);
        if (allEntitlements.isEmpty()) {
            return null;
        }

        List<Entitlement> possible = getSortedEntitlementsSpanningStatusDate(
            allEntitlements);
        if (possible.isEmpty()) {
            return null;
        }

        /*
         * Given NOW, start date == the first date in the past where there is a
         * gap in the entitlements for a product. AND end date would be first
         * date in the future that the product is not fully covered by an
         * entitlement. So the markers for end and start dates of the range are
         * when you are NOT green.
         *
         * this is where this method gets interesting. We want to loop through
         * the sorted list of possible entitlements, checking each one is valid
         * for its start and end dates. Also, comparing the current entitlement
         * with the previous entitlement processed.
         *
         * We adjust the end date to match the end date of the latest
         * entitlement until there is no gap in coverage.
         */
        Date startDate = null;
        Date endDate = null;
        Entitlement lastProcessed = null;
        for (int i = 0; i < possible.size(); i++) {
            boolean last = i == possible.size() - 1;
            Entitlement next = possible.get(i);
            Date entStart = next.getStartDate();
            Date entEnd = next.getEndDate();

            boolean entValidOnStart;
            boolean entValidOnEnd;
            if (status == GREEN_STATUS) {
                entValidOnStart = isEntitlementValidOnDate(next, possible, entStart);
                entValidOnEnd = isEntitlementValidOnDate(next, possible, entEnd);
            }
            else {
                entValidOnStart = true;
                entValidOnEnd = true;
            }

            boolean validAfterLast = true;

            // compare against the previous one if we're not the last item
            // this is key, because if the currently processed entitlement (i.e.
            // next) is not valid within the date range of the previous
            // entitlement, we have found a gap and need to adjust the start
            // date.
            if (lastProcessed != null && !last &&
                lastProcessed.getEndDate().before(next.getEndDate())) {

                Date afterLastProcessed = getDatePlusOneSecond(lastProcessed.getEndDate());

                if (status == GREEN_STATUS) {
                    // bz#959967: pass in next instead of lastProcessed
                    validAfterLast = isEntitlementValidOnDate(next, possible,
                        afterLastProcessed);
                }
                else {
                    validAfterLast = isEntitlementDateValid(next, afterLastProcessed);
                }
                if (!validAfterLast) {
                    startDate = null;
                }
            }

            // adjust the start date only if the new start date is BEFORE the
            // previous entitlement's start date, otherwise leave it alone.
            if (entValidOnStart && validAfterLast) {
                if (startDate == null || startDate.after(entStart)) {
                    startDate = entStart;
                }
            }

            // adjust the end date only if the new end date is AFTER the
            // previous entitlement's end date, otherwise leave it alone.
            if (entValidOnEnd && (endDate == null || endDate.before(entEnd))) {
                endDate = entEnd;
            }

            lastProcessed = next;
        }

        if (startDate == null || endDate == null) {
            return null;
        }

        return new DateRange(startDate, endDate);
    }

    /**
     * Determine whether the specified entitlement is valid on the specified date.
     *
     * An entitlement is considered valid if:
     * <pre>
     *     1) Its stack is valid, or there is a non-stackable entitlement covering this
     *        date.
     *
     *     2) the entitlement is compliant according to the rules file (socket coverage).
     *
     *     3) the non-stacking entitlement spans the specified date.
     * </pre>
     *
     * @param ent the entitlement to check.
     * @param possible a list of possible entitlements to check (entitlements already
     *                 filtered b product id.
     * @param date the date to check.
     * @return true if the entitlement is valid on this date, false otherwise.
     */
    private boolean isEntitlementValidOnDate(Entitlement ent,
        List<Entitlement> possible, Date date) {
        boolean entToCheckActiveOnDate = isEntitlementDateValid(ent, date);

        // Check if entitlement is stackable.
        if (ent.getPool().hasProductAttribute("stacking_id")) {
            List<Entitlement> activeOnDate = new ArrayList<Entitlement>();
            for (Entitlement next : possible) {
                DateRange validRange = new DateRange(next.getStartDate(),
                    next.getEndDate());
                if (validRange.contains(date)) {
                    activeOnDate.add(next);
                }
            }
            // Entitlement is valid if its stack is valid.
            String stackId = ent.getPool().getProductAttribute("stacking_id").getValue();
            boolean isStackCompliant = this.complianceRules.isStackCompliant(this.consumer,
                stackId, activeOnDate);
            if (isStackCompliant) {
                return true;
            }

            // There may be an entitlement already covering the product on this date.
            for (Entitlement next : activeOnDate) {
                if (!next.getPool().hasProductAttribute("stacking_id") &&
                    entToCheckActiveOnDate) {
                    return true;
                }
            }
            return false;
        }
        // Non-stackable entitlement may not be valid according to the rules file.
        else if (!complianceRules.isEntitlementCompliant(this.consumer, ent, date)) {
            return false;
        }

        // At this point, we consider the entitlement valid if the entitlement
        // covers the specified date.
        return entToCheckActiveOnDate;
    }

    private boolean isEntitlementDateValid(Entitlement ent, Date date) {
        DateRange entToCheckRange = new DateRange(ent.getStartDate(), ent.getEndDate());
        return entToCheckRange.contains(date);
    }

    /**
     * Gets a list of entitlements that form a continuous span across the date
     * specified in {@link ComplianceStatus}. Stacking is not considered here.
     *
     * @param allEntitlements all entitlements to check.
     * @return a list of all entitlements making up a span across status.date.
     */
    private List<Entitlement> getSortedEntitlementsSpanningStatusDate(
        List<Entitlement> allEntitlements) {
        List<Entitlement> sorted = sortByStartDate(allEntitlements);

        List<List<Entitlement>> groups = new ArrayList<List<Entitlement>>();
        List<Entitlement> nextGroup = new ArrayList<Entitlement>();
        for (int i = 0; i < sorted.size(); i++) {
            Entitlement ent = sorted.get(i);
            nextGroup.add(ent);

            boolean last = i == sorted.size() - 1;
            if (last || gapExistsBetween(ent, sorted.get(i + 1))) {
                groups.add(new ArrayList<Entitlement>(nextGroup));
                nextGroup.clear();
            }

        }

        Date statusDate = status.getDate();
        for (List<Entitlement> group : groups) {
            for (Entitlement ent : group) {
                DateRange range = new DateRange(ent.getStartDate(), ent.getEndDate());
                if (range.contains(statusDate)) {
                    return group;
                }
            }
        }
        return new ArrayList<Entitlement>();
    }

    /**
     * Determine if a gap exists b/w the specified entitlements. A gap may exist
     * either before or after the start date of ent1.
     *
     * @param ent1
     * @param ent2
     * @return true if a gap exists, false otherwise.
     */
    private boolean gapExistsBetween(Entitlement ent1, Entitlement ent2) {

        DateRange range1 = new DateRange(ent1.getStartDate(), ent1.getEndDate());
        if (range1.contains(ent2.getStartDate()) || range1.contains(ent2.getEndDate())) {
            return false;
        }

        DateRange range2 = new DateRange(ent2.getStartDate(), ent2.getEndDate());
        if (range2.contains(ent1.getStartDate()) || range2.contains(ent1.getEndDate())) {
            return false;
        }

        return true;
    }

    /**
     * Return a copy of the specified list of <code>Entitlement</code>s sorted by
     * start date ASC.
     *
     * @param toSort the list to sort.
     * @return
     */
    private List<Entitlement> sortByStartDate(List<Entitlement> toSort) {
        List<Entitlement> sorted = new ArrayList<Entitlement>(toSort);
        Collections.sort(sorted, new Comparator<Entitlement>() {
            @Override
            public int compare(Entitlement ent1, Entitlement ent2) {
                return ent1.getStartDate().compareTo(ent2.getStartDate());
            }
        });
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
            //Save the stacking id so we don't have to loop over everything again
            if (ent.getPool().hasProductAttribute("stacking_id")) {
                String key = ent.getPool().getProductAttribute("stacking_id").getValue();
                if (!stackIdMap.containsKey(key)) {
                    stackIdMap.put(key, new HashSet<Entitlement>());
                }
                stackIdMap.get(key).add(ent);
            }
        }
        //Add entitlements that provide via a stack,
        //however may not physically provide the product
        for (String stackId : stackIds) {
            productEnts.addAll(stackIdMap.get(stackId));
        }
        //Cast the set back to a List
        return new ArrayList<Entitlement>(productEnts);
    }

    /**
     * Add one second to the specified date, and return a new {@link Date}.
     *
     * @param date the date to add one second too.
     * @return
     */
    private Date getDatePlusOneSecond(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.SECOND, 1);
        return cal.getTime();
    }

}
