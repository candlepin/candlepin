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
package org.candlepin.resteasy.interceptor;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.ext.Provider;

import org.apache.log4j.Logger;
import org.candlepin.exceptions.BadRequestException;
import org.jboss.resteasy.annotations.interception.ServerInterceptor;
import org.jboss.resteasy.core.ResourceMethod;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.spi.Failure;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.interception.PostProcessInterceptor;
import org.jboss.resteasy.spi.interception.PreProcessInterceptor;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;

/**
 * DynamicFilterInterceptor
 *
 * A class to intercept api calls, and filter the json response based
 * on "include" and "exclude" parameter arrays.
 *
 * If an attribute name is mistyped, we want to continue gracefully
 * with no messages.  It is important in order to preserve compatibility.
 *
 * This supports subtypes, ex: ?exclude=owner.href
 */
@Provider
@ServerInterceptor
public class DynamicFilterInterceptor implements PreProcessInterceptor,
        PostProcessInterceptor {
    private static Logger log = Logger.getLogger(DynamicFilterInterceptor.class);

    private static ThreadLocal<Set<String>> attributes = new ThreadLocal<Set<String>>();
    private static ThreadLocal<Boolean> excluding = new ThreadLocal<Boolean>();
    private static ThreadLocal<Map<Object, Set<String>>> filterMappings
        = new ThreadLocal<Map<Object, Set<String>>>();

    private I18n i18n;

    @Inject
    public DynamicFilterInterceptor(I18n i18n) {
        this.i18n = i18n;
    }

    @Override
    public ServerResponse preProcess(HttpRequest request, ResourceMethod method)
        throws Failure, WebApplicationException {
        attributes.set(new HashSet<String>());
        Map<String, List<String>> queryParams = request.getUri().getQueryParameters();
        boolean containsExcl = queryParams.containsKey("exclude");
        boolean containsIncl = queryParams.containsKey("include");
        // We wait the list to be a blacklist by default when neither include
        // nor exclude is provided, so we don't accidentally filter anything
        excluding.set(!containsIncl);
        // Cannot do both types of filtering together
        if (containsExcl && containsIncl) {
            throw new BadRequestException(
                i18n.tr("Cannot use 'include' and 'exclude' parameters together"));
        }
        if (containsExcl) {
            for (String toExclude : queryParams.get("exclude")) {
                attributes.get().add(toExclude);
            }
        }
        else if (containsIncl) {
            for (String toInclude : queryParams.get("include")) {
                attributes.get().add(toInclude);
            }
        }
        return null;
    }

    @Override
    public void postProcess(ServerResponse response) {
        Object obj = response.getEntity();
        if (!getAttributes().isEmpty()) {
            filterMappings.set(new HashMap<Object, Set<String>>());
            this.addFilters(obj, attributes.get());
        }
    }

    private void addFilters(Object obj, Set<String> attributes) {
        if (obj instanceof Collection) {
            Collection<?> collection = (Collection<?>) obj;
            for (Object o : collection) {
                addFilters(o, attributes);
            }
        }
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            for (Object o : map.keySet()) {
                addFilters(o, attributes);
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

    private static Set<String> getAttributes() {
        return attributes.get();
    }

    private static boolean getExcluding() {
        return excluding.get();
    }

    private static Map<Object, Set<String>> getFilterMappings() {
        return filterMappings.get();
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

    public static boolean isAttributeExcluded(String attribute, Object obj) {
        if (getAttributes() == null || getFilterMappings() == null) {
            return false;
        }
        if (getExcluding()) {
            if (!getFilterMappings().containsKey(obj)) {
                return false;
            }
            else {
                return getFilterMappings().get(obj).contains(attribute);
            }
        }
        else {
            // whitelist
            if (!getFilterMappings().containsKey(obj)) {
                return true;
            }
            else {
                return !getFilterMappings().get(obj).contains(attribute);
            }
        }
    }
}
