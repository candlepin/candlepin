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

import java.util.LinkedList;
import java.util.List;

import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Restrictions;
import org.hibernate.type.StandardBasicTypes;
import org.hibernate.type.Type;

/**
 * FactFilterBuilder
 *
 * Builds criteria to find consumers based upon their facts
 */
public class FactFilterBuilder extends FilterBuilder {

    /*
     * This MUST be tested in spec tests.  It is sql, not hql, therefore
     * very database dependant.  Make sure tests are run on all target databases.
     */
    @Override
    protected Criterion buildCriteriaForKey(String key, List<String> values) {
        FactSqlRestrictionHelper helper = new FactSqlRestrictionHelper();

        StringBuilder sb = new StringBuilder();
        sb.append("{alias}.id in (select cp_consumer_facts.cp_consumer_id " +
            "from cp_consumer_facts " +
            // Dependant subquery works better if there are multiple facts
            "where {alias}.id = cp_consumer_facts.cp_consumer_id " +
            "and cp_consumer_facts.mapkey like ");

        // complete the like statement, with key, more-or-less. Let hibernate escape
        // everything, but transform * to %
        sb.append(matchWithWild(key, helper));
        sb.append("and ");

        // Build and add "or" restrictions to the query
        for (int i = 0, vsize = values.size(); i < vsize; i++) {
            String value = values.get(i);
            if (value == null || value.isEmpty()) {
                sb.append("(cp_consumer_facts.element IS NULL OR" +
                    "cp_consumer_facts.element = '') ");
            }
            else {
                // Case insensitive on value, not on key
                sb.append("lower(cp_consumer_facts.element) like lower(");
                sb.append(matchWithWild(value, helper));
                sb.append(')');
            }
            if (i != vsize - 1) {
                sb.append("or ");
            }
        }

        // Close the subquery
        sb.append(')');
        return Restrictions.sqlRestriction(
            sb.toString(), helper.getParams(), helper.getTypes());
    }

    /*
     * Returns a representation of the string with * replaced with %
     *
     * We want to let the hibernate library escape everything for us,
     * then add wildcards in the place of *
     */
    private String matchWithWild(String input, FactSqlRestrictionHelper helper) {
        StringBuilder sb = new StringBuilder();
        // Max split -1 so "value*" will give us "value", "", not just "value"
        String[] parts = input.split("\\*", -1);
        // some databases only support concat(a,b), others support any number of args.
        // we have to ensure only 2
        for (int i = 0; i < parts.length; i++) {
            helper.add(parts[i]);
            boolean notLast = i < parts.length - 1;
            if (notLast) {
                sb.append("concat(");
            }
            sb.append("? ");
            if (notLast) {
                sb.append(", concat('%', ");
            }
        }
        for (int i = 0; i < 2 * (parts.length - 1); i++) {
            sb.append(')');
        }
        // Always leave whitespace at the end
        sb.append(' ');
        return sb.toString();
    }

    /*
     * This class makes it easier to build the sqlRestriction for facts
     * by building the list of types and parameters we are passing
     * into the query.  Currently we only use strings, so there is
     * no type checking.
     */
    private class FactSqlRestrictionHelper {

        private List<Type> types = new LinkedList<Type>();
        private List<Object> params = new LinkedList<Object>();

        private void add(String toAdd) {
            types.add(StandardBasicTypes.STRING);
            params.add(toAdd);
        }

        public Type[] getTypes() {
            return types.toArray(new Type[types.size()]);
        }

        public Object[] getParams() {
            return params.toArray(new Object[params.size()]);
        }
    }
}
