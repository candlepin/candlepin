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

import org.candlepin.model.DerivedProductPoolAttribute;
import org.candlepin.model.DerivedProvidedProduct;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.model.ProductPoolAttribute;
import org.candlepin.model.ProvidedProduct;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private Set<ProvidedProduct> expectedProvidedProds = new HashSet<ProvidedProduct>();

    // Store the product pool attributes in a map by name so that
    // we don't end up with multiple attributes with the same name.
    private Map<String, ProductPoolAttribute> expectedAttrs =
        new HashMap<String, ProductPoolAttribute>();

    public StackedSubPoolValueAccumulator(Pool stackedSubPool,
        List<Entitlement> stackedEnts) {
        for (Entitlement nextStacked : stackedEnts) {
            Pool nextStackedPool = nextStacked.getPool();
            updateEldest(nextStacked);
            accumulateDateRange(nextStacked);
            updateEldestWithVirtLimit(nextStacked);
            accumulateProvidedProducts(stackedSubPool, nextStackedPool);
            accumulateProductAttributes(stackedSubPool, nextStackedPool);
        }
    }

    /**
     * Check if the next stacked entitlement is the eldest in the stack.
     *
     * @param nextStacked the entitlement to check.
     */
    private void updateEldest(Entitlement nextStacked) {
        if (eldest == null || nextStacked.getCreated().before(eldest.getCreated())) {
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
     * @param nextStackedPool
     */
    private void updateEldestWithVirtLimit(Entitlement nextStacked) {
        // Keep track of the eldest with virt limit so that we can change the
        // quantity of the sub pool.
        if (nextStacked.getPool().hasProductAttribute("virt_limit")) {
            if (eldestWithVirtLimit == null ||
                nextStacked.getCreated().before(eldestWithVirtLimit.getCreated())) {
                eldestWithVirtLimit = nextStacked;
            }
        }
    }

    /**
     * Add the provided products from the specified entitlement to the
     * collection of ProvidedProducts for the stack.
     *
     * @param stackedSubPool
     * @param nextStackedPool
     */
    private void accumulateProvidedProducts(Pool stackedSubPool, Pool nextStackedPool) {
        if (nextStackedPool.getDerivedProductId() == null) {
            for (ProvidedProduct pp : nextStackedPool.getProvidedProducts()) {
                expectedProvidedProds.add(
                    new ProvidedProduct(pp.getProductId(), pp.getProductName(),
                        stackedSubPool));
            }
        }
        else {
            for (DerivedProvidedProduct pp :
                nextStackedPool.getDerivedProvidedProducts()) {
                expectedProvidedProds.add(
                    new ProvidedProduct(pp.getProductId(), pp.getProductName(),
                        stackedSubPool));
            }
        }
    }

    /**
     * Update the product pool attributes - we need to be sure to check for any
     * derived products for the sub pool. If it exists, then we need to use the
     * derived product pool attributes.
     *
     * Using the pool's *current* product ID here, we may have to change it later
     * if it changes.
     *
     * @param stackedSubPool
     * @param nextStackedPool
     */
    private void accumulateProductAttributes(Pool stackedSubPool, Pool nextStackedPool) {

        if (nextStackedPool.getDerivedProductId() == null) {
            for (ProductPoolAttribute attr : nextStackedPool.getProductAttributes()) {
                expectedAttrs.put(attr.getName(),
                    new ProductPoolAttribute(attr.getName(), attr.getValue(),
                        stackedSubPool.getProductId()));
            }
        }
        else {
            for (DerivedProductPoolAttribute attr :
                nextStackedPool.getDerivedProductAttributes()) {
                expectedAttrs.put(attr.getName(),
                    new ProductPoolAttribute(attr.getName(), attr.getValue(),
                        stackedSubPool.getProductId()));
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

    public Set<ProvidedProduct> getExpectedProvidedProds() {
        return expectedProvidedProds;
    }

    public Set<ProductPoolAttribute> getExpectedAttributes() {
        return new HashSet<ProductPoolAttribute>(expectedAttrs.values());
    }

}
