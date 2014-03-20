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
import org.hibernate.criterion.Restrictions;

/**
 * FilterBuilder
 *
 * Contains the logic to apply filter Criterion to a base criteria.
 */
public abstract class FilterBuilder {


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
        for (Entry<String, List<String>> entry : attributeFilters.entrySet()) {
            all.add(this.buildCriteriaForKey(entry.getKey(), entry.getValue()));
        }

        // Currently all attributes of different names are ANDed.
        return Restrictions.and(all.toArray(new Criterion[all.size()]));
    }

    protected abstract Criterion buildCriteriaForKey(String key, List<String> values);
}
