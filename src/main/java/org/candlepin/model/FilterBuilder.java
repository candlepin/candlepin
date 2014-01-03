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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.hibernate.Criteria;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Property;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.Subqueries;


/**
 * FilterBuilder
 *
 * Contains the logic to apply filter Criterion to a base criteria.
 */
public class FilterBuilder {

    private Map<String, List<String>> attributeFilters;

    public FilterBuilder() {
        this.attributeFilters = new HashMap<String, List<String>>();
    }

    public void addAttributeFilter(String attrName, String attrValue) {
        if (!this.attributeFilters.containsKey(attrName)) {
            List<String> values = new ArrayList<String>();
            values.add(attrValue);
            this.attributeFilters.put(attrName, values);
        }
        else {
            this.attributeFilters.get(attrName).add(attrValue);
        }
    }

    public void applyTo(Criteria parentCriteria) {
        // Only apply attribute filters if any were specified.
        if (!this.attributeFilters.isEmpty()) {
            parentCriteria.add(this.buildAttributeCriteria());
        }
    }

    private Criterion buildAttributeCriteria() {
        List<Criterion> all = new ArrayList<Criterion>();
        for (Entry<String, List<String>> entry : this.attributeFilters.entrySet()) {
            DetachedCriteria productPoolAttrMatch =
                createAttributeCriteria(PoolAttribute.class, entry.getKey(),
                    entry.getValue());

            DetachedCriteria poolAttrMatch =
                createAttributeCriteria(ProductPoolAttribute.class, entry.getKey(),
                    entry.getValue());

            all.add(Restrictions.or(Subqueries.exists(productPoolAttrMatch),
                Subqueries.exists(poolAttrMatch)));
        }

        // Currently all attributes of different names are ANDed.
        return Restrictions.and(all.toArray(new Criterion[all.size()]));
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
