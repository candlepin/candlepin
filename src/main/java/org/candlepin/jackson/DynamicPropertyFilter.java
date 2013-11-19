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
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.SerializerProvider;
import org.codehaus.jackson.map.ser.BeanPropertyWriter;

/**
 * DynamicPropertyFilter
 *
 * Class to filter objects on a per-object basis, based upon
 * query parameters.
 */
public class DynamicPropertyFilter extends CheckableBeanPropertyFilter {

    private static Logger log = LoggerFactory.getLogger(DynamicPropertyFilter.class);

    private static ThreadLocal<Set<String>> attributes = new ThreadLocal<Set<String>>();
    private static ThreadLocal<Boolean> excluding = new ThreadLocal<Boolean>();
    private static ThreadLocal<Map<Object, Set<String>>> filterMappings
        = new ThreadLocal<Map<Object, Set<String>>>();

    public boolean isSerializable(Object obj, JsonGenerator jsonGenerator,
        SerializerProvider serializerProvider, BeanPropertyWriter writer) {
        return !DynamicPropertyFilter.isAttributeExcluded(writer.getName(), obj);
    }

    public static void setupFilters(Object obj) {
        if (getAttributes() != null) {
            // Using an identity hash map, which takes advantage of
            // System.identityHashCode(ob) (memory location) because
            // abstract hibernate objects hashCode function can cause NPE
            setFilterMappings(new IdentityHashMap<Object, Set<String>>());
            addFilters(obj, getAttributes());
        }
    }

    public static boolean isAttributeExcluded(String attribute, Object obj) {
        // If the object isn't mapped, or attributes have not been setup
        if (getAttributes() == null ||
                getFilterMappings() == null ||
                obj == null ||
                !getFilterMappings().containsKey(obj)) {
            return false;
        }
        if (getExcluding()) {
            return getFilterMappings().get(obj).contains(attribute);
        }
        // In the case that we're including:
        return !getFilterMappings().get(obj).contains(attribute);
    }

    public static Set<String> getAttributes() {
        return attributes.get();
    }

    public static void setAttributes(Set<String> attrs) {
        attributes.set(attrs);
    }

    public static boolean getExcluding() {
        return excluding.get();
    }

    public static void setExcluding(boolean excl) {
        excluding.set(excl);
    }

    public static Map<Object, Set<String>> getFilterMappings() {
        return filterMappings.get();
    }

    public static void setFilterMappings(Map<Object, Set<String>> mappings) {
        filterMappings.set(mappings);
    }

    private static void allowAttribute(Object obj, String attr) {
        Map<Object, Set<String>> mapping = getFilterMappings();
        if (getExcluding()) {
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

    private static void addAttributeMapping(Object obj, String attr) {
        Map<Object, Set<String>> mapping = getFilterMappings();
        if (!mapping.containsKey(obj)) {
            mapping.put(obj, new HashSet<String>());
        }
        mapping.get(obj).add(attr);
    }

    private static void addFilters(Object obj, Set<String> attributes) {
        if (obj instanceof Collection) {
            Collection<?> collection = (Collection<?>) obj;
            for (Object o : collection) {
                addFilters(o, attributes);
            }
        }
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            for (Object o : map.keySet()) {
                addFilters(map.get(o), attributes);
            }
        }
        else {
            for (String attr : attributes) {
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
    private static boolean addFiltersSubObject(Object obj, String attr) {
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
