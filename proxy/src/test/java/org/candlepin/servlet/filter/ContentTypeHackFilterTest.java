/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.candlepin.servlet.filter;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * ContentTypeHackFilterTest
 */
public class ContentTypeHackFilterTest {
    private ContentTypeHackFilter filter;

    @Before
    public void init() {
        filter = new ContentTypeHackFilter();
    }

    @Test
    public void destroy() {
        // make sure any changes don't cause an issue
        filter.destroy();
    }

    @Test
    public void filterInit() throws ServletException {
        FilterConfig fconfig  = mock(FilterConfig.class);
        filter.init(fconfig);
        verifyNoMoreInteractions(fconfig);
    }

    @Test
    public void handlesNullContentType() throws IOException, ServletException {
        ArgumentCaptor<HttpServletRequest> reqarg =
            ArgumentCaptor.forClass(HttpServletRequest.class);
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(req.getContentType()).thenReturn(null);

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(reqarg.capture(), eq(resp));
        assertEquals("application/json", reqarg.getValue().getContentType());
    }

    @Test
    public void handlesContentType() throws IOException, ServletException {
        ArgumentCaptor<HttpServletRequest> reqarg =
            ArgumentCaptor.forClass(HttpServletRequest.class);
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(req.getContentType()).thenReturn("text/plain");

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(reqarg.capture(), eq(resp));
        assertEquals("text/plain", reqarg.getValue().getContentType());
    }
}
