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

import java.util.ArrayList;
import java.util.List;

import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Property;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.Subqueries;


/**
 * PoolFilterBuilder
 *
 * Builds criteria to find pools based upon their attributes and product attributes
 */
public class PoolFilterBuilder extends FilterBuilder {

    @Override
    protected Criterion buildCriteriaForKey(String key, List<String> values) {
        DetachedCriteria productPoolAttrMatch =
            createAttributeCriteria(PoolAttribute.class, key, values);

        DetachedCriteria poolAttrMatch =
            createAttributeCriteria(ProductPoolAttribute.class, key, values);

        return Restrictions.or(Subqueries.exists(productPoolAttrMatch),
            Subqueries.exists(poolAttrMatch));
    }

    private DetachedCriteria createAttributeCriteria(
        Class<? extends AbstractPoolAttribute> entityClass, String attributeName,
        List<String> possibleValues) {
        DetachedCriteria attrMatch = DetachedCriteria.forClass(
            entityClass, "attr");
        attrMatch.add(Restrictions.eq("name", attributeName));

        // It would be nice to be able to use an 'in' restriction here, but
        // hibernate does not support ignoring case with its 'in' restriction.
        // We could probably roll our own, but would involve duplicating some
        // hibernate code to achieve it.
        List<Criterion> attrOrs = new ArrayList<Criterion>();
        for (String val : possibleValues) {
            // Setting an attribute value as '' may end up being set to null,
            // so we check both.
            if (val == null || val.isEmpty()) {
                attrOrs.add(Restrictions.isNull("value"));
                attrOrs.add(Restrictions.eq("value", ""));
            }
            else {
                attrOrs.add(Restrictions.eq("value", val).ignoreCase());
            }
        }
        attrMatch.add(Restrictions.or(
            attrOrs.toArray(new Criterion[attrOrs.size()]))
        );

        attrMatch.add(Property.forName("this.id").eqProperty("attr.pool.id"));
        attrMatch.setProjection(Projections.property("attr.id"));
        return attrMatch;
    }
}
