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

import org.hibernate.Criteria;
import org.hibernate.criterion.Criterion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;



/**
 * PoolFilterBuilder
 *
 * Builds criteria to find pools based upon their attributes and product attributes
 */
public class PoolFilterBuilder extends FilterBuilder {

    private String alias = "";
    private List<String> matchFilters = new ArrayList<>();
    private Set<String> productIds = new HashSet<>();
    private String subscriptionIdFilter;

    public PoolFilterBuilder() {
        super();
    }

    public PoolFilterBuilder(String aliasName) {
        this.alias = aliasName + ".";
    }

    @Override
    public void applyTo(Criteria parentCriteria) {
        super.applyTo(parentCriteria);
    }

    public void setProductIdFilter(String productId) {
        this.productIds.clear();
        this.productIds.add(productId);
    }

    public void setProductIdFilter(Collection<String> productIds) {
        this.productIds.clear();
        this.productIds.addAll(productIds);
    }

    public Collection<String> getProductIdFilter() {
        return Collections.unmodifiableSet(this.productIds);
    }

    public void setSubscriptionIdFilter(String subscriptionId) {
        this.subscriptionIdFilter = subscriptionId;
    }

    public String getSubscriptionIdFilter() {
        return this.subscriptionIdFilter;
    }

    /**
     * Add filters to search only for pools matching the given text. A number of
     * fields on the pool are searched including it's SKU, SKU product name,
     * contract number, SLA, and provided (engineering) product IDs and their names.
     *
     * NOTE: Parent criteria requires that an alias exists for pool.product,
     * pool.providedProduct and pool.providedProductContent.
     *
     * @param matches Text to search for in various fields on the pool. Basic
     * wildcards are supported for everything or a single character. (* and ? respectively)
     */
    public void addMatchesFilter(String matches) {
        this.matchFilters.add(matches);
    }

    public boolean hasMatchFilters() {
        return !matchFilters.isEmpty();
    }

    public Collection<String> getMatchesFilters() {
        return Collections.unmodifiableList(this.matchFilters);
    }

    @Override
    protected Criterion buildCriteriaForKey(String key, List<String> values) {
        throw new UnsupportedOperationException("This should not be used at present");
    }

}
