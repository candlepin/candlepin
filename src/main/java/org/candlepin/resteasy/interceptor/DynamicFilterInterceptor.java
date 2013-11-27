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

import java.util.List;
import java.util.Map;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.ext.Provider;

import org.candlepin.jackson.DynamicFilterData;
import org.jboss.resteasy.annotations.interception.ServerInterceptor;
import org.jboss.resteasy.core.ResourceMethod;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.spi.Failure;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.spi.interception.PostProcessInterceptor;
import org.jboss.resteasy.spi.interception.PreProcessInterceptor;

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

    @Override
    public ServerResponse preProcess(HttpRequest request, ResourceMethod method)
        throws Failure, WebApplicationException {
        Map<String, List<String>> queryParams = request.getUri().getQueryParameters();
        boolean containsExcl = queryParams.containsKey("exclude");
        boolean containsIncl = queryParams.containsKey("include");
        // Cannot do both types of filtering together,
        // no point in continuing if neither are present
        if ((containsExcl && containsIncl) ||
            !(containsExcl || containsIncl)) {
            return null;
        }
        // We wait the list to be a blacklist by default when neither include
        // nor exclude is provided, so we don't accidentally filter anything
        DynamicFilterData filterData = new DynamicFilterData(!containsIncl);

        if (containsExcl) {
            for (String toExclude : queryParams.get("exclude")) {
                filterData.addAttribute(toExclude);
            }
        }
        else if (containsIncl) {
            for (String toInclude : queryParams.get("include")) {
                filterData.addAttribute(toInclude);
            }
        }
        ResteasyProviderFactory.pushContext(DynamicFilterData.class, filterData);
        return null;
    }

    @Override
    public void postProcess(ServerResponse response) {
        DynamicFilterData filterData =
            ResteasyProviderFactory.getContextData(DynamicFilterData.class);
        if (filterData != null) {
            Object obj = response.getEntity();
            filterData.setupFilters(obj);
        }
    }
}
