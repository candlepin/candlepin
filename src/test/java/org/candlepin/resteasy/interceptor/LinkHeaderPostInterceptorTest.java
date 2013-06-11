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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.paging.Page;
import org.candlepin.paging.PageRequest;

import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.specimpl.MultivaluedMapImpl;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriBuilder;

public class LinkHeaderPostInterceptorTest {

    @Mock private HttpServletRequest request;
    @Mock private Config config;
    @Mock private ServerResponse response;
    @Mock private Page page;
    @Mock private PageRequest pageRequest;

    private LinkHeaderPostInterceptor interceptor;

    /* We do not want to load candlepin.conf off the filesystem which is what
     * happens in Config's constructor.  Therefore, we subclass.
     */
    private static class ConfigForTesting extends Config {
        public ConfigForTesting() {
            super(ConfigProperties.DEFAULT_PROPERTIES);
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        interceptor = new LinkHeaderPostInterceptor(config);
    }

    @After
    public void tearDown() throws Exception {
        ResteasyProviderFactory.clearContextData();
    }

    @Test
    public void testBuildBaseUrlWithNoConfigProperty() {
        when(config.containsKey(eq(ConfigProperties.PREFIX_APIURL))).thenReturn(false);
        when(request.getRequestURL()).thenReturn(
            new StringBuffer("https://example.com/candlepin"));

        UriBuilder builder = interceptor.buildBaseUrl(request);
        assertEquals("https://example.com/candlepin", builder.build().toString());
    }

    @Test
    public void testBuildBaseUrlWithConfigPropertyEmpty() {
        when(config.containsKey(eq(ConfigProperties.PREFIX_APIURL))).thenReturn(true);
        when(config.getString(eq(ConfigProperties.PREFIX_APIURL))).thenReturn("");
        when(request.getRequestURL()).thenReturn(
            new StringBuffer("https://example.com/candlepin"));

        UriBuilder builder = interceptor.buildBaseUrl(request);
        assertEquals("https://example.com/candlepin", builder.build().toString());
    }

    @Test
    public void testBuildBaseUrlWithConfigDefault() {
        LinkHeaderPostInterceptor interceptorWithDefault =
            new LinkHeaderPostInterceptor(new ConfigForTesting());

        when(request.getContextPath()).thenReturn("/candlepin");
        when(request.getRequestURI()).thenReturn("/candlepin/resource");
        UriBuilder builder = interceptorWithDefault.buildBaseUrl(request);
        assertEquals("https://localhost:8443/candlepin/resource",
            builder.build().toString());
    }

    @Test
    public void testBuildBaseUrlWithNoSchemeProvided() {
        when(config.containsKey(eq(ConfigProperties.PREFIX_APIURL))).thenReturn(true);
        when(config.getString(eq(ConfigProperties.PREFIX_APIURL))).thenReturn(
            "example.com/candlepin");
        when(request.getContextPath()).thenReturn("/candlepin");
        when(request.getRequestURI()).thenReturn("/candlepin/resource");

        UriBuilder builder = interceptor.buildBaseUrl(request);
        assertEquals("https://example.com/candlepin/resource", builder.build().toString());
    }

    @Test
    public void testBuildBaseUrlWithBadUriReturnsNull() {
        when(config.containsKey(eq(ConfigProperties.PREFIX_APIURL))).thenReturn(false);
        when(request.getRequestURL()).thenReturn(
            new StringBuffer("^$&#**("));

        UriBuilder builder = interceptor.buildBaseUrl(request);
        assertNull(builder);
    }

    @Test
    public void testParseQueryString() throws UnsupportedEncodingException {
        String input = "a=b&a=c%2F&z=&x";
        MultivaluedMap<String, String> map = interceptor.extractParameters(input);
        assertTrue(map.containsKey("a"));
        assertTrue(map.containsKey("x"));
        assertTrue(map.containsKey("z"));

        List<String> a = map.get("a");
        assertTrue(a.contains("b"));
        assertTrue(a.contains("c/"));

        List<String> x = map.get("x");
        assertTrue(x.contains(""));

        List<String> z = map.get("z");
        assertTrue(z.contains(""));
    }

    @Test
    public void testParseEmptyQueryString() throws UnsupportedEncodingException {
        assertNull(interceptor.extractParameters(""));
    }

    @Test
    public void testAddsUnchangingQueryParameters() {
        MultivaluedMap<String, String> map = new MultivaluedMapImpl<String, String>();
        map.add("baz", "qu=ux");
        UriBuilder bu = UriBuilder.fromUri("https://localhost:8443/candlepin/resource");
        URI returned = interceptor.addUnchangingQueryParams(bu, map).build();
        assertEquals(URI.create("https://localhost:8443/candlepin/resource?" +
                "baz=qu%3Dux"), returned);
    }

    @Test
    public void testDoesNotAddChangingQueryParameters() {
        MultivaluedMap<String, String> map = new MultivaluedMapImpl<String, String>();
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
        Page p = new Page();
        p.setMaxRecords(55);

        PageRequest pr = new PageRequest();
        p.setPageRequest(pr);

        pr.setPerPage(10);
        pr.setPage(3);

        assertEquals(Integer.valueOf(2), interceptor.getPrevPage(p));
    }

    @Test
    public void testGetPrevPageWhenOnFirstPage() {
        Page p = new Page();
        p.setMaxRecords(55);

        PageRequest pr = new PageRequest();
        p.setPageRequest(pr);

        pr.setPerPage(10);
        pr.setPage(1);

        assertNull(interceptor.getPrevPage(p));
    }

    @Test
    public void testGetNextPage() {
        Page p = new Page();
        p.setMaxRecords(55);

        PageRequest pr = new PageRequest();
        p.setPageRequest(pr);

        pr.setPerPage(10);
        pr.setPage(3);

        assertEquals(Integer.valueOf(4), interceptor.getNextPage(p));
    }

    @Test
    public void testGetNextPageWhenNoNextAvailable() {
        Page p = new Page();
        p.setMaxRecords(55);

        PageRequest pr = new PageRequest();
        p.setPageRequest(pr);

        pr.setPerPage(10);
        pr.setPage(6);

        assertNull(interceptor.getNextPage(p));
    }

    @Test
    public void testGetLastPage() {
        Page p = new Page();
        p.setMaxRecords(55);

        PageRequest pr = new PageRequest();
        p.setPageRequest(pr);

        pr.setPerPage(10);
        pr.setPage(1);

        assertEquals(Integer.valueOf(6), interceptor.getLastPage(p));
    }

    @Test
    public void testGetLastPageWhenMaxRecordsLessThanLimit() {
        Page p = new Page();
        p.setMaxRecords(8);

        PageRequest pr = new PageRequest();
        p.setPageRequest(pr);

        pr.setPerPage(10);
        pr.setPage(1);

        assertEquals(Integer.valueOf(1), interceptor.getLastPage(p));
    }

    @Test
    public void testGetLastPageWhenEvenlyDivisible() {
        Page p = new Page();
        p.setMaxRecords(10);

        PageRequest pr = new PageRequest();
        p.setPageRequest(pr);

        pr.setPerPage(10);
        pr.setPage(1);

        assertEquals(Integer.valueOf(1), interceptor.getLastPage(p));
    }

    @Test
    public void testPagesWithOutOfBoundsInitialPage() {
        Page p = new Page();
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
        interceptor.postProcess(response);
    }

    @Test
    public void testPostProcessWithNullPageRequest() {
        ResteasyProviderFactory.pushContext(Page.class, page);
        when(page.getPageRequest()).thenReturn(null);
        interceptor.postProcess(response);
        verify(page).getPageRequest();
    }

    @Test
    public void testPostProcessWithNonPagingPresentation() {
        when(page.getPageRequest()).thenReturn(pageRequest);
        when(pageRequest.isPaging()).thenReturn(false);
        ResteasyProviderFactory.pushContext(Page.class, page);
        interceptor.postProcess(response);
        verify(page, times(2)).getPageRequest();
        verify(pageRequest).isPaging();
    }

    @Test
    public void testPostProcessWithPaging() {
        when(page.getPageRequest()).thenReturn(pageRequest);
        when(page.getMaxRecords()).thenReturn(15);
        when(pageRequest.isPaging()).thenReturn(true);
        when(pageRequest.getPage()).thenReturn(2);
        when(pageRequest.getPerPage()).thenReturn(5);

        // We're going to take the quick path through buildBaseUrl.
        when(config.containsKey(eq(ConfigProperties.PREFIX_APIURL))).thenReturn(false);
        when(request.getRequestURL()).thenReturn(
            new StringBuffer("https://example.com/candlepin"));
        when(request.getQueryString()).thenReturn("order=asc&page=1&per_page=10");

        MultivaluedMap<String, Object> map = new MultivaluedMapImpl<String, Object>();
        when(response.getMetadata()).thenReturn(map);

        ResteasyProviderFactory.pushContext(Page.class, page);
        ResteasyProviderFactory.pushContext(HttpServletRequest.class, request);

        interceptor.postProcess(response);
        String header = (String) map.getFirst(LinkHeaderPostInterceptor.LINK_HEADER);

        // It would be a bit much to parse the entire header, so let's just make
        // sure that we have first, last, next, and prev links.
        assertTrue(header.contains("rel=\"first\""));
        assertTrue(header.contains("rel=\"last\""));
        assertTrue(header.contains("rel=\"next\""));
        assertTrue(header.contains("rel=\"prev\""));
    }
}
