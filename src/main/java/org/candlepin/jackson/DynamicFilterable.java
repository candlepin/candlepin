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
package org.candlepin.jackson;

import org.codehaus.jackson.map.annotate.JsonFilter;

/**
 * DynamicFilterable
 *
 * An interface to allow objects to be filtered
 * dynamically.  We have a list of attributes,
 * and a boolean flag to tell whether the
 * list is of items to include, or exclude
 */
@JsonFilter("DynamicFilter")
public interface DynamicFilterable {
    boolean isAttributeFiltered(String attribute);
    /**
     * @param blacklist whether to exclude the given attributes
     * or include only the given attributes
     */
    void setExcluding(boolean excluding);
    void excludeAttribute(String attribute);
    void includeAttribute(String attribute);
}
