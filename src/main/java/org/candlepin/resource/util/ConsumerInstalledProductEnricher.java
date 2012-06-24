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
import java.util.List;

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
        cip.setArch(prod.getAttributeValue("arch"));
        cip.setVersion(prod.getAttributeValue("version"));
        cip.setStatus(getStatus(prod.getId()));

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
        // We only return a DateRange for valid products.
        String status = getStatus(product.getId());
        if (status != GREEN_STATUS) {
            return null;
        }

        // The status is GREEN_STATUS so we should definatly get entitlements from the
        // consumer. Check to make sure.
        List<Entitlement> allEntitlements = getEntitlementsForProduct(product);
        if (allEntitlements.isEmpty()) {
            return null;
        }

        List<Entitlement> possible = getEntitlementsSpanningStatusDate(allEntitlements);
        if (possible.isEmpty()) {
            return null;
        }

        Date startDate = null;
        Date endDate = null;
        Entitlement lastProcessed = null;
        possible = sortByStartDate(possible);
        for (int i = 0; i < possible.size(); i++) {
            boolean last = i == possible.size() - 1;
            Entitlement next = possible.get(i);
            Date entStart = next.getStartDate();
            Date entEnd = next.getEndDate();

            boolean entValidOnStart = isEntitlementValidOnDate(next, possible, entStart);
            boolean entValidOnEnd = isEntitlementValidOnDate(next, possible, entEnd);

            boolean validAfterLast = true;
            if (lastProcessed != null && !last) {
                Date afterLastProcessed = getDatePlusOneSecond(lastProcessed.getEndDate());
                validAfterLast = isEntitlementValidOnDate(lastProcessed, possible,
                    afterLastProcessed);
                if (!validAfterLast) {
                    startDate = null;
                }
            }

            if (entValidOnStart && validAfterLast) {
                if (startDate == null || startDate.after(entStart)) {
                    startDate = entStart;
                }
            }

            if (entValidOnEnd) {
                if (endDate == null || endDate.before(entEnd)) {
                    endDate = entEnd;
                }
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
        DateRange entToCheckRange = new DateRange(ent.getStartDate(), ent.getEndDate());
        boolean entToCheckActiveOnDate = entToCheckRange.contains(date);

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
        else if (!complianceRules.isEntitlementCompliant(this.consumer, ent)) {
            return false;
        }

        // At this point, we consider the entitlement valid if the entitlement
        // covers the specified date.
        return entToCheckActiveOnDate;
    }

    /**
     * Gets a list of entitlements that form a continuous span across the date
     * specified in {@link ComplianceStatus}. Stacking is not considered here.
     *
     * @param allEntitlements all entitlements to check.
     * @return a list of all entitlements making up a span across status.date.
     */
    private List<Entitlement> getEntitlementsSpanningStatusDate(
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
        List<Entitlement> productEnts = new ArrayList<Entitlement>();
        for (Entitlement ent : this.consumer.getEntitlements()) {
            if (ent.getPool().provides(product.getId())) {
                productEnts.add(ent);
            }
        }
        return productEnts;
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
