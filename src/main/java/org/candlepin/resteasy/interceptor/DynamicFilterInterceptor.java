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

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.ext.Provider;

import org.apache.log4j.Logger;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.jackson.DynamicFilterable;
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
 */
@Provider
@ServerInterceptor
public class DynamicFilterInterceptor implements PreProcessInterceptor,
        PostProcessInterceptor {
    private static Logger log = Logger.getLogger(DynamicFilterInterceptor.class);

    private static ThreadLocal<Set<String>> attributes = new ThreadLocal<Set<String>>();
    private static ThreadLocal<Boolean> blacklist = new ThreadLocal<Boolean>();
    
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
        blacklist.set(!containsIncl);
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
        if (!attributes.get().isEmpty()) {
            this.addFilters(obj);
        }
    }

    private void addFilters(Object obj) {
        if (obj instanceof Collection) {
            Collection<?> collection = (Collection<?>) obj;
            for (Object o : collection) {
                addFilters(o);
            }
        }
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            for (Object o : map.keySet()) {
                addFilters(o);
                addFilters(map.get(o));
            }
        }
        else if (obj instanceof DynamicFilterable) {
            //If the object is dynamically filterable, add filter options
            DynamicFilterable df = (DynamicFilterable) obj;
            df.setBlacklist(blacklist.get());
            if (blacklist.get()) {
                for (String filter : attributes.get()) {
                    df.filterAttribute(filter);
                }
            }
            else {
                for (String allow : attributes.get()) {
                    df.allowAttribute(allow);
                }
            }
        }
    }
}
