/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.resteasy.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.guice.I18nProvider;
import org.candlepin.paging.PageRequest;

import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.xnap.commons.i18n.I18n;

import java.net.URISyntaxException;

import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ContainerRequestContext;



/**
 * PageRequestFilterTest
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PageRequestFilterTest {

    private DevConfig config;
    private Provider<I18n> i18nProvider;

    @BeforeEach
    public void setUp() {
        this.config = TestConfig.defaults();

        HttpServletRequest mockServletRequest = mock(HttpServletRequest.class);
        this.i18nProvider = new I18nProvider(() -> mockServletRequest);
    }

    private PageRequestFilter buildPageRequestFilter() {
        return new PageRequestFilter(this.config, this.i18nProvider);
    }

    private ContainerRequestContext mockRequestContext(String method, String uri) throws URISyntaxException {
        MockHttpRequest mockRequest = MockHttpRequest.create(method, uri);

        ContainerRequestContext mockRequestContext = mock(ContainerRequestContext.class);
        doReturn(mockRequest.getUri()).when(mockRequestContext).getUriInfo();

        return mockRequestContext;
    }

    @Test
    public void testNoAnything() throws Exception {
        ContainerRequestContext mockRequestContext = this.mockRequestContext("GET",
            "http://localhost/candlepin/status");

        PageRequestFilter interceptor = this.buildPageRequestFilter();
        interceptor.filter(mockRequestContext);

        PageRequest p = ResteasyContext.getContextData(PageRequest.class);
        assertNull(p);
    }

    @Test
    public void testBothLimitAndPage() throws Exception {
        ContainerRequestContext mockRequestContext = this.mockRequestContext("GET",
            "http://localhost/candlepin/status?per_page=10&page=4");

        PageRequestFilter interceptor = this.buildPageRequestFilter();
        interceptor.filter(mockRequestContext);

        PageRequest p = ResteasyContext.getContextData(PageRequest.class);
        assertEquals(Integer.valueOf(10), p.getPerPage());
        assertEquals(Integer.valueOf(4), p.getPage());
        assertEquals(PageRequest.DEFAULT_ORDER, p.getOrder());
        assertNull(p.getSortBy());
    }

    @Test
    public void testNoLimitButPage() throws Exception {
        int defaultPageSize = 15;
        this.config.setProperty(ConfigProperties.PAGING_DEFAULT_PAGE_SIZE, String.valueOf(defaultPageSize));

        ContainerRequestContext mockRequestContext = this.mockRequestContext("GET",
            "http://localhost/candlepin/status?page=5");

        PageRequestFilter interceptor = this.buildPageRequestFilter();
        interceptor.filter(mockRequestContext);

        PageRequest p = ResteasyContext.getContextData(PageRequest.class);
        assertEquals(defaultPageSize, p.getPerPage());
        assertEquals(Integer.valueOf(5), p.getPage());
        assertEquals(PageRequest.DEFAULT_ORDER, p.getOrder());
        assertNull(p.getSortBy());
    }

    @Test
    public void testLimitButNoPage() throws Exception {
        ContainerRequestContext mockRequestContext = this.mockRequestContext("GET",
            "http://localhost/candlepin/status?per_page=10");

        PageRequestFilter interceptor = this.buildPageRequestFilter();
        interceptor.filter(mockRequestContext);

        PageRequest p = ResteasyContext.getContextData(PageRequest.class);
        assertEquals(Integer.valueOf(10), p.getPerPage());
        assertEquals(PageRequest.DEFAULT_PAGE, p.getPage());
        assertEquals(PageRequest.DEFAULT_ORDER, p.getOrder());
        assertNull(p.getSortBy());
    }

    @Test
    public void testBadIntegerValue() throws Exception {
        ContainerRequestContext mockRequestContext = this.mockRequestContext("GET",
            "http://localhost/candlepin/status?page=foo&per_page=456");

        PageRequestFilter interceptor = this.buildPageRequestFilter();
        assertThrows(BadRequestException.class, () -> interceptor.filter(mockRequestContext));
    }

    @Test
    public void testDoesNotAllowPageZero() throws Exception {
        ContainerRequestContext mockRequestContext = this.mockRequestContext("GET",
            "http://localhost/candlepin/status?page=0&per_page=456");

        PageRequestFilter interceptor = this.buildPageRequestFilter();
        assertThrows(BadRequestException.class, () -> interceptor.filter(mockRequestContext));
    }

    @Test
    public void testNoPagingIfJustOrderAndSortBy() throws Exception {
        ContainerRequestContext mockRequestContext = this.mockRequestContext("GET",
            "http://localhost/candlepin/status?order=asc&sort_by=id");

        PageRequestFilter interceptor = this.buildPageRequestFilter();
        interceptor.filter(mockRequestContext);

        PageRequest p = ResteasyContext.getContextData(PageRequest.class);
        assertFalse(p.isPaging());
        assertEquals(PageRequest.Order.ASCENDING, p.getOrder());
        assertEquals("id", p.getSortBy());
    }

    @Test
    public void testUsesDefaultOrderIfNoOrderProvided() throws Exception {
        ContainerRequestContext mockRequestContext = this.mockRequestContext("GET",
            "http://localhost/candlepin/status?sort_by=id");

        PageRequestFilter interceptor = this.buildPageRequestFilter();
        interceptor.filter(mockRequestContext);

        PageRequest p = ResteasyContext.getContextData(PageRequest.class);
        assertFalse(p.isPaging());
        assertEquals(PageRequest.DEFAULT_ORDER, p.getOrder());
        assertEquals("id", p.getSortBy());
    }

    @Test
    public void testDescendingOrder() throws Exception {
        ContainerRequestContext mockRequestContext = this.mockRequestContext("GET",
            "http://localhost/candlepin/status?order=descending&sort_by=id");

        PageRequestFilter interceptor = this.buildPageRequestFilter();
        interceptor.filter(mockRequestContext);

        PageRequest p = ResteasyContext.getContextData(PageRequest.class);
        assertFalse(p.isPaging());
        assertEquals(PageRequest.Order.DESCENDING, p.getOrder());
        assertEquals("id", p.getSortBy());
    }

    @Test
    public void testFilterEnforcesMaxPageSize() throws Exception {
        ContainerRequestContext mockRequestContext = this.mockRequestContext("GET",
            "http://localhost/candlepin/status?per_page=35");

        this.config.setProperty(ConfigProperties.PAGING_MAX_PAGE_SIZE, "30");

        PageRequestFilter interceptor = this.buildPageRequestFilter();
        BadRequestException exception = assertThrows(BadRequestException.class, () ->
            interceptor.filter(mockRequestContext));

        String errmsg = exception.getMessage();
        assertNotNull(errmsg);
        assertTrue(errmsg.contains("page size cannot exceed"));
    }

    @Test
    public void testFilterAllowsMaxPageSize() throws Exception {
        int maxPageSize = this.config.getInt(ConfigProperties.PAGING_MAX_PAGE_SIZE);

        ContainerRequestContext mockRequestContext = this.mockRequestContext("GET",
            "http://localhost/candlepin/status?per_page=" + maxPageSize);

        PageRequestFilter interceptor = this.buildPageRequestFilter();
        interceptor.filter(mockRequestContext);

        PageRequest pageRequest = ResteasyContext.getContextData(PageRequest.class);
        assertNotNull(pageRequest);
        assertEquals(maxPageSize, pageRequest.getPerPage());
    }

}
