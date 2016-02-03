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

import com.google.common.base.Function;
import com.google.common.collect.Lists;

import org.hibernate.Criteria;
import org.hibernate.criterion.Conjunction;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Disjunction;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Property;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.Subqueries;
import org.hibernate.sql.JoinType;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;



/**
 * PoolFilterBuilder
 *
 * Builds criteria to find pools based upon their attributes and product attributes
 */
public class PoolFilterBuilder extends FilterBuilder {

    private String alias = "";
    private List<String> matchFilters = new ArrayList<String>();
    private String productIdFilter;
    private String subscriptionIdFilter;

    public PoolFilterBuilder() {
        super();
    }

    public PoolFilterBuilder(String aliasName) {
        this.alias = aliasName + ".";
    }

    @Override
    public void applyTo(Criteria parentCriteria) {
        if (productIdFilter != null && !productIdFilter.isEmpty()) {
            applyProductIdFilter(parentCriteria);
        }

        if (subscriptionIdFilter != null && !subscriptionIdFilter.isEmpty()) {
            applySubscriptionIdFilter(parentCriteria);
        }

        for (String matches : matchFilters) {
            applyMatchFilter(matches);
        }
        super.applyTo(parentCriteria);
    }

    public void setProductIdFilter(String productId) {
        this.productIdFilter = productId;
    }

    public void setSubscriptionIdFilter(String subscriptionId) {
        this.subscriptionIdFilter = subscriptionId;
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

    private void applyProductIdFilter(Criteria parent) {
        String originalPoolAlias = this.alias.isEmpty() ? "this." : alias;
        DetachedCriteria prodCrit = DetachedCriteria.forClass(Pool.class, "Pool2")
            .createAlias("product", "product")
            .createAlias("providedProducts", "provided", JoinType.LEFT_OUTER_JOIN)
            .add(Property.forName(originalPoolAlias + "id").eqProperty("Pool2.id"))
            .add(Restrictions.disjunction()
                .add(Restrictions.eq("product.id", this.productIdFilter))
                .add(Restrictions.eq("provided.id", productIdFilter)))
            .setProjection(Projections.property("Pool2.id"));

        parent.add(Subqueries.exists(prodCrit));
    }

    private void applySubscriptionIdFilter(Criteria parent) {
        String originalPoolAlias = this.alias.isEmpty() ? "this." : alias;
        DetachedCriteria subCrit = DetachedCriteria.forClass(Pool.class, "Pool2")
            .createAlias("sourceSubscription", "sourceSubscription")
            .add(Property.forName(originalPoolAlias + "id").eqProperty("Pool2.id"))
            .add(Restrictions.disjunction()
                .add(Restrictions.eq("sourceSubscription.subscriptionId", this.subscriptionIdFilter)))
            .setProjection(Projections.property("Pool2.id"));
        parent.add(Subqueries.exists(subCrit));
    }

    private void applyMatchFilter(String matches) {
        String originalPoolAlias = this.alias.isEmpty() ? "this." : alias;

        Disjunction textOr = Restrictions.disjunction();
        textOr.add(new FilterLikeExpression("product.name", matches, true));
        textOr.add(new FilterLikeExpression("product.id", matches, true));
        textOr.add(new FilterLikeExpression(alias + "contractNumber", matches, true));
        textOr.add(new FilterLikeExpression(alias + "orderNumber", matches, true));

        DetachedCriteria ppCriteria = DetachedCriteria.forClass(Pool.class, "Pool2")
                .createAlias("providedProducts", "provided", JoinType.INNER_JOIN)
                .createAlias("provided.productContent", "ppcw", JoinType.LEFT_OUTER_JOIN)
                .createAlias("ppcw.content", "ppContent", JoinType.LEFT_OUTER_JOIN)
                .add(Property.forName(originalPoolAlias + "id").eqProperty("Pool2.id"))
                .add(Restrictions.disjunction()
                   .add(new FilterLikeExpression("provided.id", matches, true))
                   .add(new FilterLikeExpression("provided.name", matches, true))
                   .add(new FilterLikeExpression("ppContent.name", matches, true))
                   .add(new FilterLikeExpression("ppContent.label", matches, true)))
                .setProjection(Projections.property("Pool2.id"));
        textOr.add(Subqueries.exists(ppCriteria));

        textOr.add(Subqueries.exists(
            this.createProductAttributeCriteria(
                "support_level",
                Arrays.asList(matches)
            )
        ));

        this.otherCriteria.add(textOr);
    }

    @Override
    protected Criterion buildCriteriaForKey(String key, List<String> values) {
        List<String> negatives = new ArrayList<String>();
        for (String predicate : values) {
            if (predicate.startsWith("!")) {
                negatives.add(predicate);
            }
        }

        values.removeAll(negatives);

        // Strip off all the exclamation points
        negatives = Lists.transform(negatives, new Function<String, String>() {
            @Override
            public String apply(String input) {
                return input.substring(1);
            }
        });

        Conjunction conjunction = new Conjunction();

        if (!values.isEmpty()) {
            conjunction.add(
                Restrictions.or(
                    Subqueries.exists(this.createPoolAttributeCriteria(key, values)),
                    Subqueries.exists(this.createProductAttributeCriteria(key, values))
                )
            );
        }

        if (!negatives.isEmpty()) {
            conjunction.add(
                Restrictions.not(
                    Restrictions.or(
                        Subqueries.exists(this.createProductAttributeCriteria(key, negatives)),
                        Subqueries.exists(this.createPoolAttributeCriteria(key, negatives))
                    )
                )
            );
        }

        return conjunction;
    }


    private DetachedCriteria createPoolAttributeCriteria(String attribute, List<String> values) {
        DetachedCriteria subquery = DetachedCriteria.forClass(PoolAttribute.class, "attr");
        subquery.add(new FilterLikeExpression("name", attribute, false));

        // It would be nice to be able to use an 'in' restriction here, but
        // hibernate does not support ignoring case with its 'in' restriction.
        // We could probably roll our own, but would involve duplicating some
        // hibernate code to achieve it.
        Disjunction disjunction = Restrictions.disjunction();
        for (String value : values) {
            if (value == null || value.isEmpty()) {
                disjunction.add(Restrictions.isNull("value"));
                disjunction.add(Restrictions.eq("value", ""));
            }
            else {
                disjunction.add(new FilterLikeExpression("value", value, true));
            }
        }

        subquery.add(disjunction);

        String originalPoolAlias = this.alias.isEmpty() ? "this." : alias;
        subquery.add(Property.forName(originalPoolAlias + "id").eqProperty("attr.pool.id"));
        subquery.setProjection(Projections.property("attr.id"));

        return subquery;
    }

    private DetachedCriteria createProductAttributeCriteria(String attribute, List<String> values) {
        DetachedCriteria subquery = DetachedCriteria.forClass(Pool.class, "PoolI")
            .createAlias("PoolI.product", "ProdI")
            .createAlias("ProdI.attributes", "ProdAttrI");

        subquery.add(new FilterLikeExpression("ProdAttrI.name", attribute, false));

        Disjunction disjunction = Restrictions.disjunction();
        for (String value : values) {
            if (value == null || value.isEmpty()) {
                disjunction.add(Restrictions.isNull("ProdAttrI.value"));
                disjunction.add(Restrictions.eq("ProdAttrI.value", ""));
            }
            else {
                disjunction.add(new FilterLikeExpression("ProdAttrI.value", value, true));
            }
        }

        subquery.add(disjunction);

        String originalPoolAlias = this.alias.isEmpty() ? "this." : alias;
        subquery.add(Property.forName(originalPoolAlias + "id").eqProperty("PoolI.id"));
        subquery.setProjection(Projections.property("PoolI.id"));

        // We don't want to match Product Attributes that have been overridden
        DetachedCriteria overridden = DetachedCriteria.forClass(PoolAttribute.class, "PoolAttrI")
            // If we're using wildcards in the name, we should block exact matches
            .add(Restrictions.eqProperty("PoolAttrI.name", "ProdAttrI.name"))
            .add(Restrictions.eqProperty("PoolI.id", "PoolAttrI.pool.id"))
            .setProjection(Projections.property("PoolAttrI.pool.id"));
        subquery.add(Subqueries.notExists(overridden));

        return subquery;
    }

}
