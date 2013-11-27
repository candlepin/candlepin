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

import java.util.HashSet;
import java.util.Set;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.SerializerProvider;
import org.codehaus.jackson.map.ser.BeanPropertyWriter;

/**
 * MultiFilter
 *
 * A jackson attribute filter class used to combine
 * multiple filters into one.  If any filters do not allow
 * serialization, the attribute will not be written
 *
 * Can be used with other MultiFilters.  Be careful not
 * to add yourself, that will loop infinitely.  However
 * I don't think it's worth the resources to check that
 * you're not adding yourself.
 */
public class MultiFilter extends CheckableBeanPropertyFilter {

    private Set<CheckableBeanPropertyFilter> filters;

    public MultiFilter(CheckableBeanPropertyFilter... filters) {
        this.filters = new HashSet<CheckableBeanPropertyFilter>();
        for (CheckableBeanPropertyFilter filter : filters) {
            this.addFilter(filter);
        }
    }

    public void addFilter(CheckableBeanPropertyFilter filter) {
        // Could theoretically check if we're adding ourself here,
        // if that becomes a problem.  We don't populate this
        // dynamically so it's unnecessary at this point.
        this.filters.add(filter);
    }

    public Set<CheckableBeanPropertyFilter> getFilters() {
        return filters;
    }

    /*
     * If any filters cannot serialize the attribute, do not write it
     */
    @Override
    public boolean isSerializable(Object obj, JsonGenerator jsonGenerator,
        SerializerProvider serializerProvider, BeanPropertyWriter writer) {
        for (CheckableBeanPropertyFilter filter : filters) {
            if (!filter.isSerializable(obj, jsonGenerator, serializerProvider, writer)) {
                return false;
            }
        }
        // Default true, we want to be sure nothing is blocking the write.
        return true;
    }
}
