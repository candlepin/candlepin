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
import static org.mockito.Mockito.when;

import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.paging.DataPresentation;
import org.candlepin.paging.Page;

import org.jboss.resteasy.specimpl.MultivaluedMapImpl;
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

        DataPresentation dp = new DataPresentation();
        p.setPresentation(dp);

        dp.setPerPage(10);
        dp.setPage(3);

        assertEquals(new Integer(2), interceptor.getPrevPage(p));
    }

    @Test
    public void testGetPrevPageWhenOnFirstPage() {
        Page p = new Page();
        p.setMaxRecords(55);

        DataPresentation dp = new DataPresentation();
        p.setPresentation(dp);

        dp.setPerPage(10);
        dp.setPage(1);

        assertNull(interceptor.getPrevPage(p));
    }

    @Test
    public void testGetNextPage() {
        Page p = new Page();
        p.setMaxRecords(55);

        DataPresentation dp = new DataPresentation();
        p.setPresentation(dp);

        dp.setPerPage(10);
        dp.setPage(3);

        assertEquals(new Integer(4), interceptor.getNextPage(p));
    }

    @Test
    public void testGetNextPageWhenNoNextAvailable() {
        Page p = new Page();
        p.setMaxRecords(55);

        DataPresentation dp = new DataPresentation();
        p.setPresentation(dp);

        dp.setPerPage(10);
        dp.setPage(6);

        assertNull(interceptor.getNextPage(p));
    }

    @Test
    public void testGetLastPage() {
        Page p = new Page();
        p.setMaxRecords(55);

        DataPresentation dp = new DataPresentation();
        p.setPresentation(dp);

        dp.setPerPage(10);
        dp.setPage(1);

        assertEquals(new Integer(6), interceptor.getLastPage(p));
    }

    @Test
    public void testGetLastPageWhenMaxRecordsLessThanLimit() {
        Page p = new Page();
        p.setMaxRecords(8);

        DataPresentation dp = new DataPresentation();
        p.setPresentation(dp);

        dp.setPerPage(10);
        dp.setPage(1);

        assertEquals(new Integer(1), interceptor.getLastPage(p));
    }

    @Test
    public void testPagesWithOutOfBoundsInitialPage() {
        Page p = new Page();
        p.setMaxRecords(8);

        DataPresentation dp = new DataPresentation();
        p.setPresentation(dp);

        dp.setPerPage(10);
        dp.setPage(2);

        assertNull(interceptor.getPrevPage(p));
        assertNull(interceptor.getNextPage(p));
    }
}
