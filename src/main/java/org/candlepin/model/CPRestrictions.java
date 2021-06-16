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

import org.candlepin.config.Configuration;
import org.candlepin.config.DatabaseConfigFactory;

import com.google.common.collect.Iterables;

import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.LikeExpression;
import org.hibernate.criterion.Restrictions;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Provider;

/**
 * The CPRestrictions class provides utility Criterion building methods to be used with Hibernate's
 * fluent-style query building.
 */
public class CPRestrictions {
    @Inject private static Provider<Configuration> configProvider;

    private static class CPLikeExpression extends LikeExpression {
        public CPLikeExpression(String property, String value, Character escape, boolean ignoreCase) {
            super(property, value, escape, ignoreCase);
        }
    }

    private CPRestrictions() {
        // Static methods only
    }

    /**
     * Apply an "in" constraint to the named property or expression. If the collection of values
     * exceeds known database limitations on collection sizes, it will be broken up into several
     * in-clauses connected with or-operators.
     *
     * @param expression
     *  the string expression against which we are searching values
     *
     * @param values
     *  the values being searched for the expression
     *
     * @throws IllegalArgumentException
     *  if values is null or empty
     *
     * @return
     *  a Criterion representing the "in" constraint on the given property or expression
     */
    public static <T extends Object> Criterion in(String expression, Iterable<T> values) {
        if (values == null || !values.iterator().hasNext()) {
            throw new IllegalArgumentException("values is null or empty");
        }

        Iterator<List<T>> blocks = Iterables.partition(
            values, configProvider.get().getInt(DatabaseConfigFactory.IN_OPERATOR_BLOCK_SIZE)
        ).iterator();

        Criterion criterion = Restrictions.in(expression, blocks.next());

        while (blocks.hasNext()) {
            criterion = Restrictions.or(criterion, Restrictions.in(expression, blocks.next()));
        }

        return criterion;
    }

    /**
     * Apply an "in" constraint to the named property or expression. If the collection of values
     * exceeds known database limitations on collection sizes, it will be broken up into several
     * in-clauses connected with or-operators.
     *
     * @param expression
     *  the string expression against which we are searching values
     *
     * @param values
     *  the values being searched for the expression
     *
     * @throws IllegalArgumentException
     *  if values is null or empty
     *
     * @return
     *  a Criterion representing the "in" constraint on the given property or expression
     */
    public static Criterion in(String expression, Object[] values) {
        return in(expression, Arrays.asList(values));
    }

    /**
     * Applies a case-sensitive "like" constraint to the named property or expression.
     *
     * @param expression
     *  the string expression to apply the restriction
     *
     * @param value
     *  the value to compare to the expression
     *
     * @return
     *  a Criterion representing a case-sensitive "like" constraint on the given property or
     *  expression
     */
    public static Criterion like(String expression, String value) {
        return new CPLikeExpression(expression, value, null, false);
    }

    /**
     * Applies a case-sensitive "like" constraint to the named property or expression, using the
     * given character as an escape character.
     *
     * @param expression
     *  the string expression to apply the restriction
     *
     * @param value
     *  the value to compare to the expression
     *
     * @param escapeChar
     *  the character to use to denote a character literal
     *
     * @return
     *  a Criterion representing a case-sensitive "like" constraint on the given property or
     *  expression
     */
    public static Criterion like(String expression, String value, char escapeChar) {
        return new CPLikeExpression(expression, value, escapeChar, false);
    }

    /**
     * Applies a case-insensitive "like" constraint to the named property or expression.
     *
     * @param expression
     *  the string expression to apply the restriction
     *
     * @param value
     *  the value to compare to the expression
     *
     * @return
     *  a Criterion representing a case-sensitive "like" constraint on the given property or
     *  expression
     */
    public static Criterion ilike(String expression, String value) {
        return new CPLikeExpression(expression, value, null, true);
    }

    /**
     * Applies a case-insensitive "like" constraint to the named property or expression, using the
     * given character as an escape character.
     *
     * @param expression
     *  the string expression to apply the restriction
     *
     * @param value
     *  the value to compare to the expression
     *
     * @param escapeChar
     *  the character to use to denote a character literal
     *
     * @return
     *  a Criterion representing a case-sensitive "like" constraint on the given property or
     *  expression
     */
    public static Criterion ilike(String expression, String value, char escapeChar) {
        return new CPLikeExpression(expression, value, escapeChar, true);
    }

}
