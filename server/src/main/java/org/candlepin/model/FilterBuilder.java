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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FilterBuilder
 *
 * Contains the logic to apply filter Criterion to a base criteria.
 */
public abstract class FilterBuilder {

    private static Logger log = LoggerFactory.getLogger(FilterBuilder.class);
    public static final String WILDCARD_REGEX = "((?:[^*?\\\\]*(?:\\\\.?)*)*)([*?]|\\z)";
    public static final Pattern WILDCARD_PATTERN = Pattern.compile(WILDCARD_REGEX);


    private Map<String, List<String>> attributeFilters;
    private List<String> idFilters;
    protected List<Criterion> otherCriteria;

    public FilterBuilder() {
        this.attributeFilters = new HashMap<String, List<String>>();
        this.idFilters = new LinkedList<String>();
        this.otherCriteria = new LinkedList<Criterion>();
    }

    public FilterBuilder addIdFilter(String id) {
        idFilters.add(id);
        return this;
    }

    public FilterBuilder addIdFilters(Collection<String> ids) {
        if (ids != null) {
            idFilters.addAll(ids);
        }
        return this;
    }

    public void addAttributeFilter(String attrName, String attrValue) {
        if (!attributeFilters.containsKey(attrName)) {
            attributeFilters.put(attrName, new LinkedList<String>());
        }
        attributeFilters.get(attrName).add(attrValue);
    }

    public void applyTo(Criteria parentCriteria) {
        if (!attributeFilters.isEmpty() || !idFilters.isEmpty() ||
                !otherCriteria.isEmpty()) {
            parentCriteria.add(getCriteria());
        }
    }

    public Criterion getCriteria() {
        Conjunction all = Restrictions.conjunction();
        if (!attributeFilters.isEmpty()) {
            all.add(buildAttributeCriteria());
        }
        if (!idFilters.isEmpty()) {
            all.add(buildIdFilters());
        }
        if (!otherCriteria.isEmpty()) {
            for (Criterion c : otherCriteria) {
                all.add(c);
            }
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
            log.debug("Searching for entries like: ", raw);
            String dbEscaped = raw.replace("!", "!!")
                // Escape anything that would be a database wildcard
                .replace("_", "!_").replace("%", "!%");
            log.debug("DB characters excaped: ", dbEscaped);

            // Possibly could merge this with FilterBuilder.FilterLikeExpression:
            Matcher matcher = WILDCARD_PATTERN.matcher(dbEscaped);
            StringBuffer searchBuf = new StringBuffer();
            while (matcher.find()) {

                if (!matcher.group(1).isEmpty()) {
                    searchBuf.append(matcher.group(1));
                }
                if (matcher.group(2).equals("*")) {
                    searchBuf.append("%");
                }
                else if (matcher.group(2).equals("?")) {
                    searchBuf.append("_");
                }
            }
            // We didn't find anything to match on (the one character is the assumed %), must
            // be a plain search string.
            if (searchBuf.length() == 0) {
                searchBuf.append(dbEscaped);
            }

            String searchString = searchBuf.toString();
            log.debug("Final database search string: {} -> {}", raw,
                    searchString);


            return searchString;
        }
    }
}
