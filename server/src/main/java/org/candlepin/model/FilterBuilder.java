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
import org.hibernate.criterion.Conjunction;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.LikeExpression;
import org.hibernate.criterion.Restrictions;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * FilterBuilder
 *
 * Contains the logic to apply filter Criterion to a base criteria.
 */
public abstract class FilterBuilder {

    private Map<String, List<String>> attributeFilters;
    private List<String> idFilters;

    public FilterBuilder() {
        this.attributeFilters = new HashMap<String, List<String>>();
        this.idFilters = new LinkedList<String>();
    }

    public FilterBuilder addIdFilter(String id) {
        idFilters.add(id);
        return this;
    }

    public FilterBuilder addIdFilters(Collection<String> ids) {
        idFilters.addAll(ids);
        return this;
    }

    public FilterBuilder addAttributeFilter(String attrName, String attrValue) {
        if (!attributeFilters.containsKey(attrName)) {
            attributeFilters.put(attrName, new LinkedList<String>());
        }
        attributeFilters.get(attrName).add(attrValue);
        return this;
    }

    public void applyTo(Criteria parentCriteria) {
        // Only apply attribute filters if any were specified.
        if (!attributeFilters.isEmpty()) {
            parentCriteria.add(buildCriteria());
        }
    }

    public Criterion getCriteria() {
        return buildCriteria();
    }

    private Criterion buildCriteria() {
        Conjunction all = Restrictions.conjunction();
        if (!attributeFilters.isEmpty()) {
            all.add(buildAttributeCriteria());
        }
        if (!idFilters.isEmpty()) {
            all.add(buildIdFilters());
        }
        return all;
    }

    private Criterion buildIdFilters() {
        return Restrictions.in("id", idFilters);
    }

    private Criterion buildAttributeCriteria() {
        Conjunction all = Restrictions.conjunction();
        for (Entry<String, List<String>> entry : attributeFilters.entrySet()) {
            all.add(buildCriteriaForKey(entry.getKey(), entry.getValue()));
        }

        // Currently all attributes of different names are ANDed.
        return all;
    }

    protected abstract Criterion buildCriteriaForKey(String key, List<String> values);

    /**
     * FilterLikeExpression to easily build like clauses, escaping all sql wildcards
     * from input while allowing us to use a custom wildcard
     */
    @SuppressWarnings("serial")
    public static class FilterLikeExpression extends LikeExpression {

        public FilterLikeExpression(String propertyName, String value, boolean ignoreCase) {
            super(propertyName, escape(value), '!', ignoreCase);
        }

        private static String escape(String raw) {
            // If our escape char is already here, escape it
            return raw.replace("!", "!!")
                // Escape anything that would be a wildcard
                .replace("_", "!_").replace("%", "!%")
                // Now use * as wildcard
                .replace("*", "%");
        }
    }
}
