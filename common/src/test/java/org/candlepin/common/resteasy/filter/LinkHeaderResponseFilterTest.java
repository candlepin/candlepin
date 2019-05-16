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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.common.config.Configuration;
import org.candlepin.common.config.MapConfiguration;
import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;

import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.specimpl.MultivaluedMapImpl;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.HashMap;

import javax.servlet.ServletContext;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriBuilder;

@ExtendWith(MockitoExtension.class)
public class LinkHeaderResponseFilterTest {

    @Mock private Configuration config;
    @Mock private ServerResponse response;
    @Mock private Page<Object> page;
    @Mock private PageRequest pageRequest;

    @Mock private ContainerRequestContext mockRequestContext;
    @Mock private ContainerResponseContext mockResponseContext;

    @Mock private ServletContext mockServletContext;

    private MockHttpRequest mockReq;
    private String apiUrlPrefixKey;

    private LinkHeaderResponseFilter interceptor;

    /* We do not want to load candlepin.conf off the filesystem which is what
     * happens in Config's constructor.  Therefore, we subclass.
     */
    private static class ConfigForTesting extends MapConfiguration {
        public ConfigForTesting() {
            super(new HashMap<String, String>() {
                /* constructor */ {
                    this.put("test_prefix_key", "localhost:8443/candlepin");
                }
            });
        }
    }

    @BeforeEach
    public void setUp() throws Exception {
        this.apiUrlPrefixKey = "test_prefix_key";
        ResteasyProviderFactory.pushContext(ServletContext.class, mockServletContext);
        interceptor = new LinkHeaderResponseFilter(config, apiUrlPrefixKey);
    }

    @AfterEach
    public void tearDown() throws Exception {
        ResteasyProviderFactory.clearContextData();
    }

    @Test
    public void testBuildBaseUrlWithConfigPropertyEmpty() throws Exception {
        when(config.containsKey(eq(this.apiUrlPrefixKey))).thenReturn(true);
        when(config.getString(eq(this.apiUrlPrefixKey))).thenReturn("");

        mockReq = MockHttpRequest.get("https://example.com/candlepin");
        when(mockRequestContext.getUriInfo()).thenReturn(mockReq.getUri());

        UriBuilder builder = interceptor.buildBaseUrl(mockRequestContext);
        assertEquals("https://example.com/candlepin", builder.build().toString());
    }


    @Test
    public void testBuildBaseUrlWithNoConfigProperty() throws Exception {
        when(config.containsKey(eq(this.apiUrlPrefixKey))).thenReturn(false);

        mockReq = MockHttpRequest.get("https://example.com/candlepin");
        when(mockRequestContext.getUriInfo()).thenReturn(mockReq.getUri());

        UriBuilder builder = interceptor.buildBaseUrl(mockRequestContext);
        assertEquals("https://example.com/candlepin", builder.build().toString());
    }

    @Test
    public void testBuildBaseUrlWithConfigDefault() throws Exception {
        when(mockServletContext.getContextPath()).thenReturn("/candlepin");
        mockReq = MockHttpRequest.get("https://example.com/candlepin/resource");
        when(mockRequestContext.getUriInfo()).thenReturn(mockReq.getUri());

        LinkHeaderResponseFilter interceptorWithDefault =
            new LinkHeaderResponseFilter(new ConfigForTesting(), this.apiUrlPrefixKey);

        UriBuilder builder = interceptorWithDefault.buildBaseUrl(mockRequestContext);
        assertEquals("https://localhost:8443/candlepin/resource", builder.build().toString());
    }

    @Test
    public void testBuildBaseUrlWithNoSchemeProvided() throws Exception {
        when(mockServletContext.getContextPath()).thenReturn("/candlepin");
        when(config.containsKey(eq(this.apiUrlPrefixKey))).thenReturn(true);
        when(config.getString(eq(this.apiUrlPrefixKey))).thenReturn(
            "localhost:8443/candlepin");

        mockReq = MockHttpRequest.get("https://example.com/candlepin/resource");
        when(mockRequestContext.getUriInfo()).thenReturn(mockReq.getUri());

        UriBuilder builder = interceptor.buildBaseUrl(mockRequestContext);
        assertEquals("https://localhost:8443/candlepin/resource", builder.build().toString());
    }

    @Test
    public void testBuildBaseUrlWithBadConfigReturnsNull() throws Exception {
        when(config.containsKey(eq(this.apiUrlPrefixKey))).thenReturn(true);
        when(config.getString(eq(this.apiUrlPrefixKey))).thenReturn("localhost:8443/subscriptions");

        mockReq = MockHttpRequest.get("https://example.com/candlepin");
        when(mockRequestContext.getUriInfo()).thenReturn(mockReq.getUri());

        when(mockServletContext.getContextPath()).thenReturn("/subscriptions");
        interceptor = new LinkHeaderResponseFilter(config, apiUrlPrefixKey);

        UriBuilder builder = interceptor.buildBaseUrl(mockRequestContext);
        assertNull(builder);
    }

    @Test
    public void testAddsUnchangingQueryParameters() {
        MultivaluedMap<String, String> map = new MultivaluedMapImpl<>();
        map.add("baz", "qu=ux");
        UriBuilder bu = UriBuilder.fromUri("https://localhost:8443/candlepin/resource");
        URI returned = interceptor.addUnchangingQueryParams(bu, map).build();
        assertEquals(URI.create("https://localhost:8443/candlepin/resource?baz=qu%3Dux"), returned);
    }

    @Test
    public void testDoesNotAddChangingQueryParameters() {
        MultivaluedMap<String, String> map = new MultivaluedMapImpl<>();
        map.add("page", "10");
        UriBuilder bu = UriBuilder.fromUri("https://localhost:8443/candlepin/resource");
        URI returned = interceptor.addUnchangingQueryParams(bu, map).build();
        assertEquals(URI.create("https://localhost:8443/candlepin/resource"), returned);
    }

    @Test
    public void testDoesNotAddAnythingWhenNoQueryParameters() {
        UriBuilder bu = UriBuilder.fromUri("https://localhost:8443/candlepin/resource");
        URI returned = interceptor.addUnchangingQueryParams(bu, null).build();
        assertEquals(URI.create("https://localhost:8443/candlepin/resource"), returned);
    }

    @Test
    public void testBuildPageLink() {
        UriBuilder bu = UriBuilder.fromUri("https://localhost:8443/candlepin/resource");
        assertEquals("https://localhost:8443/candlepin/resource?page=5",
            interceptor.buildPageLink(bu, 5));
    }

    @Test
    public void testGetPrevPage() {
        Page<Object> p = new Page<>();
        p.setMaxRecords(55);

        PageRequest pr = new PageRequest();
        p.setPageRequest(pr);

        pr.setPerPage(10);
        pr.setPage(3);

        assertEquals(Integer.valueOf(2), interceptor.getPrevPage(p));
    }

    @Test
    public void testGetPrevPageWhenOnFirstPage() {
        Page<Object> p = new Page<>();
        p.setMaxRecords(55);

        PageRequest pr = new PageRequest();
        p.setPageRequest(pr);

        pr.setPerPage(10);
        pr.setPage(1);

        assertNull(interceptor.getPrevPage(p));
    }

    @Test
    public void testGetNextPage() {
        Page<Object> p = new Page<>();
        p.setMaxRecords(55);

        PageRequest pr = new PageRequest();
        p.setPageRequest(pr);

        pr.setPerPage(10);
        pr.setPage(3);

        assertEquals(Integer.valueOf(4), interceptor.getNextPage(p));
    }

    @Test
    public void testGetNextPageWhenNoNextAvailable() {
        Page<Object> p = new Page<>();
        p.setMaxRecords(55);

        PageRequest pr = new PageRequest();
        p.setPageRequest(pr);

        pr.setPerPage(10);
        pr.setPage(6);

        assertNull(interceptor.getNextPage(p));
    }

    @Test
    public void testGetLastPage() {
        Page<Object> p = new Page<>();
        p.setMaxRecords(55);

        PageRequest pr = new PageRequest();
        p.setPageRequest(pr);

        pr.setPerPage(10);
        pr.setPage(1);

        assertEquals(Integer.valueOf(6), interceptor.getLastPage(p));
    }

    @Test
    public void testGetLastPageWhenMaxRecordsLessThanLimit() {
        Page<Object> p = new Page<>();
        p.setMaxRecords(8);

        PageRequest pr = new PageRequest();
        p.setPageRequest(pr);

        pr.setPerPage(10);
        pr.setPage(1);

        assertEquals(Integer.valueOf(1), interceptor.getLastPage(p));
    }

    @Test
    public void testGetLastPageWhenEvenlyDivisible() {
        Page<Object> p = new Page<>();
        p.setMaxRecords(10);

        PageRequest pr = new PageRequest();
        p.setPageRequest(pr);

        pr.setPerPage(10);
        pr.setPage(1);

        assertEquals(Integer.valueOf(1), interceptor.getLastPage(p));
    }

    @Test
    public void testPagesWithOutOfBoundsInitialPage() {
        Page<Object> p = new Page<>();
        p.setMaxRecords(8);

        PageRequest pr = new PageRequest();
        p.setPageRequest(pr);

        pr.setPerPage(10);
        pr.setPage(2);

        assertNull(interceptor.getPrevPage(p));
        assertNull(interceptor.getNextPage(p));
        assertEquals(Integer.valueOf(1), interceptor.getLastPage(p));
    }

    @Test
    public void testPostProcessWithNullPage() {
        // Should just not throw an exception
        interceptor.filter(mockRequestContext, mockResponseContext);
    }

    @Test
    public void testPostProcessWithNullPageRequest() {
        ResteasyProviderFactory.pushContext(Page.class, page);
        when(page.getPageRequest()).thenReturn(null);
        interceptor.filter(mockRequestContext, mockResponseContext);
        verify(page).getPageRequest();
    }

    @Test
    public void testPostProcessWithNonPagingPresentation() {
        when(page.getPageRequest()).thenReturn(pageRequest);
        when(pageRequest.isPaging()).thenReturn(false);
        ResteasyProviderFactory.pushContext(Page.class, page);
        interceptor.filter(mockRequestContext, mockResponseContext);
        verify(page, times(2)).getPageRequest();
        verify(pageRequest).isPaging();
    }

    @Test
    public void testPostProcessWithPaging() throws Exception {
        when(page.getPageRequest()).thenReturn(pageRequest);
        when(page.getMaxRecords()).thenReturn(15);
        when(pageRequest.isPaging()).thenReturn(true);
        when(pageRequest.getPage()).thenReturn(2);
        when(pageRequest.getPerPage()).thenReturn(5);

        // We're going to take the quick path through buildBaseUrl.
        when(config.containsKey(eq(this.apiUrlPrefixKey))).thenReturn(false);

        MultivaluedMap<String, Object> map = new MultivaluedMapImpl<>();

        ResteasyProviderFactory.pushContext(Page.class, page);

        mockReq = MockHttpRequest.create("GET",
                new URI("/candlepin/resource?order=asc&page=1&per_page=10"),
                new URI("https://example.com"));
        when(mockRequestContext.getUriInfo()).thenReturn(mockReq.getUri());

        when(mockResponseContext.getHeaders()).thenReturn(map);

        interceptor.filter(mockRequestContext, mockResponseContext);
        String header = (String) map.getFirst(LinkHeaderResponseFilter.LINK_HEADER);

        // It would be a bit much to parse the entire header, so let's just make
        // sure that we have first, last, next, and prev links.
        assertTrue(header.contains("rel=\"first\""));
        assertTrue(header.contains("rel=\"last\""));
        assertTrue(header.contains("rel=\"next\""));
        assertTrue(header.contains("rel=\"prev\""));
    }
}
