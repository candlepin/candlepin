/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


/**
 * Contains the logic to apply filter Criterion to a base criteria.
 */
public abstract class FilterBuilder {
    private final Map<String, List<String>> attributeFilters;
    private final List<String> idFilters;

    public FilterBuilder() {
        this.attributeFilters = new HashMap<>();
        this.idFilters = new LinkedList<>();
    }

    public FilterBuilder addIdFilters(String... ids) {
        return addIdFilters(List.of(ids));
    }

    public FilterBuilder addIdFilters(Collection<String> ids) {
        if (ids != null) {
            idFilters.addAll(ids);
        }
        return this;
    }

    public Collection<String> getIdFilters() {
        return Collections.unmodifiableList(this.idFilters);
    }

    public void addAttributeFilter(String attrName) {
        this.attributeFilters.computeIfAbsent(attrName, s -> new LinkedList<>());
    }

    public void addAttributeFilter(String attrName, String attrValue) {
        this.attributeFilters.computeIfAbsent(attrName, s -> new LinkedList<>())
            .add(attrValue);
    }

    public Map<String, List<String>> getAttributeFilters() {
        return Collections.unmodifiableMap(this.attributeFilters);
    }

}
