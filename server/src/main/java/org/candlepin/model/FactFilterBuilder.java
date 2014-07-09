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

import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Disjunction;
import org.hibernate.criterion.LikeExpression;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.Subqueries;

/**
 * FactFilterBuilder
 *
 * Builds criteria to find consumers based upon their facts
 */
public class FactFilterBuilder extends FilterBuilder {

    @Override
    protected Criterion buildCriteriaForKey(String key, List<String> values) {
        Disjunction valuesCriteria = Restrictions.disjunction();
        for (String value : values) {
            if (StringUtils.isEmpty(value)) {
                valuesCriteria.add(Restrictions.isNull("cfacts.elements"));
                valuesCriteria.add(Restrictions.eq("cfacts.elements", ""));
            }
            else {
                valuesCriteria.add(new FilterLikeExpression("cfacts.elements", value, true));
            }
        }

        DetachedCriteria dc = DetachedCriteria.forClass(Consumer.class, "subcons")
            .add(Restrictions.eqProperty("this.id", "subcons.id"))
            .createAlias("subcons.facts", "cfacts")
            // Match the key, case sensitive
            .add(new FilterLikeExpression("cfacts.indices", key, false))
            // Match values, case insensitive
            .add(valuesCriteria)
            .setProjection(Projections.property("subcons.id"));

        return Subqueries.exists(dc);
    }

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
