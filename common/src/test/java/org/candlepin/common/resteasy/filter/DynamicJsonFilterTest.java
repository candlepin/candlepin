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

import org.candlepin.common.jackson.DynamicFilterData;

import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.Arrays;

import javax.ws.rs.container.ContainerRequestContext;


/**
 * DynamicJsonFilterTest
 */
@ExtendWith(MockitoExtension.class)
public class DynamicJsonFilterTest {

    @Mock private MockHttpRequest mockReq;
    @Mock private ContainerRequestContext mockRequestContext;

    private DynamicJsonFilter interceptor;

    @BeforeEach
    public void init() {
        this.interceptor = new DynamicJsonFilter();
        ResteasyProviderFactory.popContextData(DynamicFilterData.class);
    }

    @AfterEach
    public void tearDown() {
        ResteasyProviderFactory.clearContextData();
    }

    @Test
    public void testNoFilters() throws Exception {
        mockReq = MockHttpRequest.create("GET",
                new URI("/candlepin/status"),
                new URI("http://localhost"));
        when(mockRequestContext.getUriInfo()).thenReturn(mockReq.getUri());

        interceptor.filter(mockRequestContext);

        DynamicFilterData filterData = ResteasyProviderFactory.getContextData(DynamicFilterData.class);
        assertNull(filterData);
    }

    @Test
    public void testSimpleBlacklist() throws Exception {
        mockReq = MockHttpRequest.create("GET",
            new URI("/candlepin/status?exclude=a2"),
            new URI("http://localhost"));
        when(mockRequestContext.getUriInfo()).thenReturn(mockReq.getUri());

        interceptor.filter(mockRequestContext);

        DynamicFilterData filterData = ResteasyProviderFactory.getContextData(DynamicFilterData.class);
        assertNotNull(filterData);

        assertFalse(filterData.isAttributeExcluded("a1"));
        assertFalse(filterData.isAttributeExcluded("a1.c1"));
        assertFalse(filterData.isAttributeExcluded("a1.c2"));
        assertTrue(filterData.isAttributeExcluded("a2"));
        assertTrue(filterData.isAttributeExcluded("a2.c1"));
        assertTrue(filterData.isAttributeExcluded("a2.c2"));
        assertFalse(filterData.isAttributeExcluded("a3"));
        assertFalse(filterData.isAttributeExcluded("a3.c1"));
        assertFalse(filterData.isAttributeExcluded("a3.c2"));

        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a1", "c1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a1", "c2")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a2")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a2", "c1")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a2", "c2")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a3")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a3", "c1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a3", "c2")));
    }

    @Test
    public void testSimpleMultiBlacklist() throws Exception {
        mockReq = MockHttpRequest.create("GET",
            new URI("/candlepin/status?exclude=a2&exclude=a3"),
            new URI("http://localhost"));
        when(mockRequestContext.getUriInfo()).thenReturn(mockReq.getUri());

        interceptor.filter(mockRequestContext);

        DynamicFilterData filterData = ResteasyProviderFactory.getContextData(DynamicFilterData.class);
        assertNotNull(filterData);

        assertFalse(filterData.isAttributeExcluded("a1"));
        assertFalse(filterData.isAttributeExcluded("a1.c1"));
        assertFalse(filterData.isAttributeExcluded("a1.c2"));
        assertTrue(filterData.isAttributeExcluded("a2"));
        assertTrue(filterData.isAttributeExcluded("a2.c1"));
        assertTrue(filterData.isAttributeExcluded("a2.c2"));
        assertTrue(filterData.isAttributeExcluded("a3"));
        assertTrue(filterData.isAttributeExcluded("a3.c1"));
        assertTrue(filterData.isAttributeExcluded("a3.c2"));

        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a1", "c1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a1", "c2")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a2")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a2", "c1")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a2", "c2")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a3")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a3", "c1")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a3", "c2")));
    }

    @Test
    public void testNestedAttributeBlacklist() throws Exception {
        mockReq = MockHttpRequest.create("GET",
            new URI("/candlepin/status?exclude=a2&exclude=a3.c1"),
            new URI("http://localhost"));
        when(mockRequestContext.getUriInfo()).thenReturn(mockReq.getUri());

        interceptor.filter(mockRequestContext);

        DynamicFilterData filterData = ResteasyProviderFactory.getContextData(DynamicFilterData.class);
        assertNotNull(filterData);

        assertFalse(filterData.isAttributeExcluded("a1"));
        assertFalse(filterData.isAttributeExcluded("a1.c1"));
        assertFalse(filterData.isAttributeExcluded("a1.c2"));
        assertTrue(filterData.isAttributeExcluded("a2"));
        assertTrue(filterData.isAttributeExcluded("a2.c1"));
        assertTrue(filterData.isAttributeExcluded("a2.c2"));
        assertFalse(filterData.isAttributeExcluded("a3"));
        assertTrue(filterData.isAttributeExcluded("a3.c1"));
        assertFalse(filterData.isAttributeExcluded("a3.c2"));

        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a1", "c1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a1", "c2")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a2")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a2", "c1")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a2", "c2")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a3")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a3", "c1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a3", "c2")));
    }

    @Test
    public void testSimpleWhitelist() throws Exception {
        mockReq = MockHttpRequest.create("GET",
            new URI("/candlepin/status?include=a2"),
            new URI("http://localhost"));
        when(mockRequestContext.getUriInfo()).thenReturn(mockReq.getUri());

        interceptor.filter(mockRequestContext);

        DynamicFilterData filterData = ResteasyProviderFactory.getContextData(DynamicFilterData.class);
        assertNotNull(filterData);

        assertTrue(filterData.isAttributeExcluded("a1"));
        assertTrue(filterData.isAttributeExcluded("a1.c1"));
        assertTrue(filterData.isAttributeExcluded("a1.c2"));
        assertFalse(filterData.isAttributeExcluded("a2"));
        assertFalse(filterData.isAttributeExcluded("a2.c1"));
        assertFalse(filterData.isAttributeExcluded("a2.c2"));
        assertTrue(filterData.isAttributeExcluded("a3"));
        assertTrue(filterData.isAttributeExcluded("a3.c1"));
        assertTrue(filterData.isAttributeExcluded("a3.c2"));

        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a1")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a1", "c1")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a1", "c2")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a2")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a2", "c1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a2", "c2")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a3")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a3", "c1")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a3", "c2")));
    }

    @Test
    public void testSimpleMultiWhitelist() throws Exception {
        mockReq = MockHttpRequest.create("GET",
            new URI("/candlepin/status?include=a1&include=a3"),
            new URI("http://localhost"));
        when(mockRequestContext.getUriInfo()).thenReturn(mockReq.getUri());

        interceptor.filter(mockRequestContext);

        DynamicFilterData filterData = ResteasyProviderFactory.getContextData(DynamicFilterData.class);
        assertNotNull(filterData);

        assertFalse(filterData.isAttributeExcluded("a1"));
        assertFalse(filterData.isAttributeExcluded("a1.c1"));
        assertFalse(filterData.isAttributeExcluded("a1.c2"));
        assertTrue(filterData.isAttributeExcluded("a2"));
        assertTrue(filterData.isAttributeExcluded("a2.c1"));
        assertTrue(filterData.isAttributeExcluded("a2.c2"));
        assertFalse(filterData.isAttributeExcluded("a3"));
        assertFalse(filterData.isAttributeExcluded("a3.c1"));
        assertFalse(filterData.isAttributeExcluded("a3.c2"));

        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a1", "c1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a1", "c2")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a2")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a2", "c1")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a2", "c2")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a3")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a3", "c1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a3", "c2")));
    }

    @Test
    public void testNestedAttributeWhitelist() throws Exception {
        mockReq = MockHttpRequest.create("GET",
            new URI("/candlepin/status?include=a2&include=a3.c1"),
            new URI("http://localhost"));
        when(mockRequestContext.getUriInfo()).thenReturn(mockReq.getUri());

        interceptor.filter(mockRequestContext);

        DynamicFilterData filterData = ResteasyProviderFactory.getContextData(DynamicFilterData.class);
        assertNotNull(filterData);

        assertTrue(filterData.isAttributeExcluded("a1"));
        assertTrue(filterData.isAttributeExcluded("a1.c1"));
        assertTrue(filterData.isAttributeExcluded("a1.c2"));
        assertFalse(filterData.isAttributeExcluded("a2"));
        assertFalse(filterData.isAttributeExcluded("a2.c1"));
        assertFalse(filterData.isAttributeExcluded("a2.c2"));
        assertFalse(filterData.isAttributeExcluded("a3"));
        assertFalse(filterData.isAttributeExcluded("a3.c1"));
        assertTrue(filterData.isAttributeExcluded("a3.c2"));

        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a1")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a1", "c1")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a1", "c2")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a2")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a2", "c1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a2", "c2")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a3")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a3", "c1")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a3", "c2")));
    }

    @Test
    public void testIncludesWithExcludes() throws Exception {
        mockReq = MockHttpRequest.create("GET",
            new URI("/candlepin/status?include=a.b1&exclude=a.b1.c2&exclude=a.b2&include=a.b2.d2"),
            new URI("http://localhost"));
        when(mockRequestContext.getUriInfo()).thenReturn(mockReq.getUri());

        interceptor.filter(mockRequestContext);

        DynamicFilterData filterData = ResteasyProviderFactory.getContextData(DynamicFilterData.class);
        assertNotNull(filterData);

        // a: { b1: { c1, c2, c3 }, b2: { d1, d2, d3 } }

        assertFalse(filterData.isAttributeExcluded("a"));
        assertFalse(filterData.isAttributeExcluded("a.b1"));
        assertFalse(filterData.isAttributeExcluded("a.b1.c1"));
        assertTrue(filterData.isAttributeExcluded("a.b1.c2"));
        assertFalse(filterData.isAttributeExcluded("a.b1.c3"));
        assertFalse(filterData.isAttributeExcluded("a.b2"));
        assertTrue(filterData.isAttributeExcluded("a.b2.d1"));
        assertFalse(filterData.isAttributeExcluded("a.b2.d2"));
        assertTrue(filterData.isAttributeExcluded("a.b2.d3"));

        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a", "b1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a", "b1", "c1")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a", "b1", "c2")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a", "b1", "c3")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a", "b2")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a", "b2", "d1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a", "b2", "d2")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a", "b2", "d3")));
    }

    @Test
    public void testIncludesWithExcludesUsingWhitelist() throws Exception {
        mockReq = MockHttpRequest.create("GET",
            new URI("/candlepin/status?include=a.b1&exclude=a.b1.c2&include=a.b2.d2&filtermode=whitelist"),
            new URI("http://localhost"));
        when(mockRequestContext.getUriInfo()).thenReturn(mockReq.getUri());

        interceptor.filter(mockRequestContext);

        DynamicFilterData filterData = ResteasyProviderFactory.getContextData(DynamicFilterData.class);
        assertNotNull(filterData);

        // a: { b1: { c1, c2, c3 }, b2: { d1, d2, d3 } }

        assertFalse(filterData.isAttributeExcluded("a"));
        assertFalse(filterData.isAttributeExcluded("a.b1"));
        assertFalse(filterData.isAttributeExcluded("a.b1.c1"));
        assertTrue(filterData.isAttributeExcluded("a.b1.c2"));
        assertFalse(filterData.isAttributeExcluded("a.b1.c3"));
        assertFalse(filterData.isAttributeExcluded("a.b2"));
        assertTrue(filterData.isAttributeExcluded("a.b2.d1"));
        assertFalse(filterData.isAttributeExcluded("a.b2.d2"));
        assertTrue(filterData.isAttributeExcluded("a.b2.d3"));

        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a", "b1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a", "b1", "c1")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a", "b1", "c2")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a", "b1", "c3")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a", "b2")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a", "b2", "d1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a", "b2", "d2")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a", "b2", "d3")));
    }
}
