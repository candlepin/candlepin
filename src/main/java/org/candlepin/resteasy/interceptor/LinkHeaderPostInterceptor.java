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

import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.paging.Page;
import org.candlepin.paging.PageRequest;
import org.candlepin.paging.Paginate;

import com.google.inject.Inject;

import org.jboss.resteasy.annotations.interception.Precedence;
import org.jboss.resteasy.annotations.interception.ServerInterceptor;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.specimpl.MultivaluedMapImpl;
import org.jboss.resteasy.spi.LinkHeader;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.spi.interception.AcceptedByMethod;
import org.jboss.resteasy.spi.interception.PostProcessInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.util.List;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.ext.Provider;

/**
 * LinkHeaderPostProcessor
 */
@Provider
@ServerInterceptor
@Precedence("HEADER_DECORATOR")
public class LinkHeaderPostInterceptor implements PostProcessInterceptor, AcceptedByMethod {
    private static Logger log = LoggerFactory.getLogger(LinkHeaderPostInterceptor.class);
    public static final String LINK_HEADER = "Link";

    private Config config;

    @Inject
    public LinkHeaderPostInterceptor(Config config) {
        this.config = config;
    }

    @Override
    public boolean accept(Class declaring, Method method) {
        return method.isAnnotationPresent(Paginate.class);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void postProcess(ServerResponse response) {
        Page page = ResteasyProviderFactory.getContextData(Page.class);

        if (page == null) {
            log.warn("Method marked for pagination, but no page exists in the context.");
            return;
        }

        // If we aren't paging, then no need for Link headers.
        if (page.getPageRequest() == null || !page.getPageRequest().isPaging()) {
            return;
        }

        HttpServletRequest request = ResteasyProviderFactory.getContextData(
            HttpServletRequest.class);
        UriBuilder builder = buildBaseUrl(request);
        // If builder is null, we couldn't read the request URI, so stop.
        if (builder == null) {
            return;
        }

        MultivaluedMap<String, String> params = null;
        try {
            params = extractParameters(request.getQueryString());
        }
        catch (UnsupportedEncodingException e) {
            log.warn("Could not find UTF-8 encoding", e);
            return;
        }

        builder = addUnchangingQueryParams(builder, params);
        //TODO add missing parameters like the default limit if no limit is given.

        LinkHeader header = new LinkHeader();

        Integer next = getNextPage(page);
        if (next != null) {
            header.addLink(null, "next", buildPageLink(builder, next), null);
        }

        Integer prev = getPrevPage(page);
        if (prev != null) {
            header.addLink(null, "prev", buildPageLink(builder, prev), null);
        }

        header.addLink(null, "first", buildPageLink(builder, 1), null);
        header.addLink(null, "last", buildPageLink(builder, getLastPage(page)), null);
        response.getMetadata().add(LINK_HEADER, header.toString());
    }

    protected String buildPageLink(UriBuilder b, int value) {
        // Copy so we can use the same builder for building each link.
        UriBuilder builder = b.clone();
        builder.queryParam(PageRequest.PAGE_PARAM, String.valueOf(value));
        return builder.build().toString();
    }

    protected Integer getLastPage(Page page) {
        PageRequest pageRequest = page.getPageRequest();

        // The last page is ceiling(maxRecords/recordsPerPage)
        int lastPage = page.getMaxRecords() / pageRequest.getPerPage();

        if (page.getMaxRecords() % pageRequest.getPerPage() != 0) {
            lastPage++;
        }

        return lastPage;
    }

    protected Integer getPrevPage(Page page) {
        Integer prev = page.getPageRequest().getPage() - 1;
        // if the calculated page is out of bounds, return null
        return (prev < 1 || prev >= getLastPage(page)) ? null : prev;
    }

    protected Integer getNextPage(Page page) {
        Integer next = page.getPageRequest().getPage() + 1;
        return (next > getLastPage(page)) ? null : next;
    }

    protected UriBuilder buildBaseUrl(HttpServletRequest request) {
        StringBuffer url;
        if (config.containsKey(ConfigProperties.PREFIX_APIURL) &&
            !"".equals(config.getString(ConfigProperties.PREFIX_APIURL))) {
            url = new StringBuffer(config.getString(ConfigProperties.PREFIX_APIURL));
            // The default value of PREFIX_APIURL doesn't specify a scheme.
            if (url.indexOf("://") == -1) {
                url = new StringBuffer("https://").append(url);
            }

            // Now add on the resource they requested
            // We can't just use request.getServletPath().  Something RESTEasy is doing?

            // Context path should equal something like /candlepin
            String context = request.getContextPath();

            // Request URI should equal something like /candlepin/resource
            String requestUri = request.getRequestURI();

            int offset = requestUri.lastIndexOf(context);
            if (offset >= 0) {
                // Strip off the context
                url.append(requestUri.substring(offset + context.length()));
            }
            else {
                // Something has gone really wrong if the context isn't in the
                // request URI.
                log.warn("Could not determine resource path.");
                return null;
            }
        }
        else {
            url = request.getRequestURL();
        }

        try {
            UriBuilder builder = UriBuilder.fromUri(url.toString());
            return builder;
        }
        catch (IllegalArgumentException e) {
            log.warn("Could not build URI from " + url, e);
            return null;
        }
    }

    /**
     * Based on RESTEasy's UriInfoImpl.extractParameters()
     * @param queryString
     * @return
     * @throws UnsupportedEncodingException
     */
    protected MultivaluedMap<String, String> extractParameters(String queryString)
        throws UnsupportedEncodingException {
        if (queryString == null || "".equals(queryString)) {
            return null;
        }

        MultivaluedMap<String, String> map = new MultivaluedMapImpl<String, String>();
        String[] params = queryString.split("&");

        for (String param : params) {
            if (param.indexOf('=') >= 0) {
                String[] nv = param.split("=");
                // Have to decode because UriBuilder re-encodes.
                String name = URLDecoder.decode(nv[0], "UTF-8");
                String val = (nv.length > 1) ? nv[1] : "";
                map.add(name, URLDecoder.decode(val, "UTF-8"));
            }
            else {
                map.add(param, "");
            }
        }

        return map;
    }

    protected UriBuilder addUnchangingQueryParams(UriBuilder builder,
        MultivaluedMap<String, String> params) {
        // This will take care of adding back any order, per_page, or sort_by
        // parameters provided too.
        if (params != null) {
            for (Entry<String, List<String>> e : params.entrySet()) {
                if (!e.getKey().equals(PageRequest.PAGE_PARAM)) {
                    for (String v : e.getValue()) {
                        builder = builder.queryParam(e.getKey(), v);
                    }
                }
            }
        }
        return builder;
    }
}
