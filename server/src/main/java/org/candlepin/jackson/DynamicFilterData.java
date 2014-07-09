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

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DynamicFilterData
 *
 * Class to hold filtering data to be passed from DynamicFilterInterceptor
 * to DynamicPropertyFilter
 */
public class DynamicFilterData {

    private static Logger log = LoggerFactory.getLogger(DynamicFilterData.class);

    private Set<String> attributes;
    private boolean excluding = true;
    private Map<Object, Set<String>> filterMappings;

    public DynamicFilterData(boolean excluding) {
        attributes = new HashSet<String>();
        this.excluding = excluding;
        filterMappings = new IdentityHashMap<Object, Set<String>>();
    }

    public void addAttribute(String attr) {
        attributes.add(attr);
    }

    public void setupFilters(Object obj) {
        addFilters(obj, attributes);
    }

    public boolean isAttributeExcluded(String attribute, Object obj) {
        // If the object is null or unmapped
        if (obj == null ||
                !filterMappings.containsKey(obj)) {
            return false;
        }
        if (excluding) {
            return filterMappings.get(obj).contains(attribute);
        }
        // In the case that we're including:
        return !filterMappings.get(obj).contains(attribute);
    }

    private void allowAttribute(Object obj, String attr) {
        Map<Object, Set<String>> mapping = filterMappings;
        if (excluding) {
            if (mapping.containsKey(obj)) {
                mapping.get(obj).remove(attr);
            }
        }
        else {
            if (!mapping.containsKey(obj) || !mapping.get(obj).contains(obj)) {
                addAttributeMapping(obj, attr);
            }
        }
    }

    private void addAttributeMapping(Object obj, String attr) {
        Map<Object, Set<String>> mapping = filterMappings;
        if (!mapping.containsKey(obj)) {
            mapping.put(obj, new HashSet<String>());
        }
        mapping.get(obj).add(attr);
    }

    private void addFilters(Object obj, Set<String> attrs) {
        if (obj instanceof Collection) {
            Collection<?> collection = (Collection<?>) obj;
            for (Object o : collection) {
                addFilters(o, attrs);
            }
        }
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            for (Entry<?, ?> entry : map.entrySet()) {
                addFilters(entry.getValue(), attrs);
            }
        }
        else {
            for (String attr : attrs) {
                if (addFiltersSubObject(obj, attr)) {
                    addAttributeMapping(obj, attr);
                }
            }
        }
    }

    /*
     * This method is used for attributes in encapsulated classes.
     *
     * Returns true if the attribute was not handled for encapsulated classes
     * This method always allows the local attribute if there is an encapsulated
     * class, even if it is not valid/serialized.
     */
    private boolean addFiltersSubObject(Object obj, String attr) {
        boolean proceed = true;
        int index = attr.indexOf('.');
        if (index != -1 && index != attr.length() - 1) {
            String localAttr = attr.substring(0, index);
            allowAttribute(obj, localAttr);
            proceed = false;
            String subAttrs = attr.substring(index + 1);
            try {
                // "is" getter should only be used for booleans.
                // here we only care about objects.
                String getterName = "get" + localAttr.substring(0, 1).toUpperCase();
                if (localAttr.length() > 1) {
                    getterName += localAttr.substring(1);
                }
                Method getter = obj.getClass().getMethod(getterName, new Class[] {});
                Object result = getter.invoke(obj);
                Set<String> sublist = new HashSet<String>();
                sublist.add(subAttrs);
                addFilters(result, sublist);
            }
            catch (Exception e) {
                // This doesn't need to be more sever than a debug log because
                // it may be hit with a bad filter option.  Probably not worth
                // the time to log the entire exception either.
                log.debug("failed to set filters on sub-object " + e.getMessage());
            }
        }
        return proceed;
    }
}
