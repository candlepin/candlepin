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
package org.candlepin.common.resteasy.filter;

import org.candlepin.common.config.Configuration;
import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.spi.LinkHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map.Entry;

import javax.annotation.Priority;
import javax.servlet.ServletContext;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.ext.Provider;



/**
 * LinkHeaderResponseFilter inserts a Link header into the HTTP response to a request that asked for paging.
 * The Link header is defined in RFC 5988 and is used to communicated to the client the URLs for the next
 * page, previous page, first page, and last page.
 */
@Provider
@Priority(Priorities.HEADER_DECORATOR)
public class LinkHeaderResponseFilter implements ContainerResponseFilter {
    private static Logger log = LoggerFactory.getLogger(LinkHeaderResponseFilter.class);

    // This needs to be low enough that *all* headers can still reasonably fit after putting in
    // all four links. Given that the default header buffer size is 8k, we need to make sure
    // we still leave plenty of space for other headers.
    public static final int MAX_LINK_LENGTH = 1024;

    public static final String LINK_HEADER = "Link";
    public static final String TOTAL_RECORDS_COUNT = "X-total-count";

    public static final String LINK_TYPE = MediaType.APPLICATION_JSON;

    private String apiUrlPrefixKey;
    private Configuration config;
    private String contextPath;

    @Inject
    public LinkHeaderResponseFilter(Configuration config,
        @Named("PREFIX_APIURL_KEY") String apiUrlPrefixKey) {
        this.config = config;
        this.apiUrlPrefixKey = apiUrlPrefixKey;
    }

    @SuppressWarnings("rawtypes")
    public void filter(ContainerRequestContext reqContext, ContainerResponseContext respContext) {
        Page page = ResteasyContext.getContextData(Page.class);

        // Make sure we have page information in the context
        if (page == null) {
            return;
        }

        // If we aren't paging, then no need for Link headers.
        if (page.getPageRequest() == null || !page.getPageRequest().isPaging()) {
            return;
        }

        UriBuilder builder = buildBaseUrl(reqContext);
        // If builder is null, we couldn't read the request URI, so stop.
        if (builder == null) {
            return;
        }

        MultivaluedMap<String, String> params = null;
        params = reqContext.getUriInfo().getQueryParameters();

        builder = addUnchangingQueryParams(builder, params);
        //TODO add missing parameters like the default limit if no limit is given.

        try {
            LinkHeader header = new LinkHeader();

            Integer next = getNextPage(page);
            if (next != null) {
                header.addLink("next", "next", buildPageLink(builder, next), LINK_TYPE);
            }

            Integer prev = getPrevPage(page);
            if (prev != null) {
                header.addLink("prev", "prev", buildPageLink(builder, prev), LINK_TYPE);
            }

            header.addLink("first", "first", buildPageLink(builder, 1), LINK_TYPE);
            header.addLink("last", "last", buildPageLink(builder, getLastPage(page)), LINK_TYPE);

            respContext.getHeaders().add(LINK_HEADER, header.toString());
        }
        catch (LinkTooLongException e) {
            log.warn("Link length exceeded maximum length ({}). " +
                "Link headers will be omitted from this response.",
                MAX_LINK_LENGTH, e);
        }

        respContext.getHeaders().add(TOTAL_RECORDS_COUNT, page.getMaxRecords());
    }

    protected String buildPageLink(UriBuilder b, int value) {
        // Copy so we can use the same builder for building each link.
        UriBuilder builder = b.clone();
        builder.queryParam(PageRequest.PAGE_PARAM, String.valueOf(value));

        String link = builder.build().toString();

        if (link.length() > MAX_LINK_LENGTH) {
            throw new LinkTooLongException(link);
        }

        return link;
    }

    protected Integer getLastPage(Page<?> page) {
        PageRequest pageRequest = page.getPageRequest();

        // The last page is ceiling(maxRecords/recordsPerPage)
        int lastPage = page.getMaxRecords() / pageRequest.getPerPage();

        if (page.getMaxRecords() % pageRequest.getPerPage() != 0) {
            lastPage++;
        }

        return lastPage;
    }

    protected Integer getPrevPage(Page<?> page) {
        Integer prev = page.getPageRequest().getPage() - 1;
        // if the calculated page is out of bounds, return null
        return (prev < 1 || prev >= getLastPage(page)) ? null : prev;
    }

    protected Integer getNextPage(Page<?> page) {
        Integer next = page.getPageRequest().getPage() + 1;
        return (next > getLastPage(page)) ? null : next;
    }

    protected UriBuilder buildBaseUrl(ContainerRequestContext reqContext) {
        if (config.containsKey(this.apiUrlPrefixKey) && !"".equals(config.getString(this.apiUrlPrefixKey))) {
            ServletContext servletContext = ResteasyContext.getContextData(ServletContext.class);
            contextPath = servletContext.getContextPath();

            StringBuffer url = new StringBuffer(config.getString(this.apiUrlPrefixKey));
            // The default value of PREFIX_APIURL doesn't specify a scheme.
            if (url.indexOf("://") == -1) {
                url = new StringBuffer("https://").append(url);
            }

            String requestUri = reqContext.getUriInfo().getRequestUri().toString();

            int offset = requestUri.lastIndexOf(contextPath);
            if (offset >= 0) {
                // Strip off the context
                url.append(requestUri.substring(offset + contextPath.length()));
            }
            else {
                log.warn("Could not find servlet context in {}", requestUri);
                return null;
            }

            try {
                UriBuilder builder = UriBuilder.fromUri(url.toString());
                return builder;
            }
            catch (IllegalArgumentException e) {
                log.warn("Couldn't build URI for link header using {}", url, e);
                return null;
            }
        }
        else {
            return reqContext.getUriInfo().getRequestUriBuilder();
        }
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
