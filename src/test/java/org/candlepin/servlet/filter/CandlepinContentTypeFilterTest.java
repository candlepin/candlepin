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

package org.candlepin.servlet.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Locale;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

class CandlepinContentTypeFilterTest {

    private CandlepinContentTypeFilter filter;

    @BeforeEach
    public void init() {
        I18n i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);

        this.filter = new CandlepinContentTypeFilter(() -> i18n);
    }

    @Test
    public void validContentType() throws IOException, ServletException {
        ServletRequest request = mock(ServletRequest.class);
        ServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getContentType()).thenReturn("application/json");

        filter.doFilter(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
        verifyNoMoreInteractions(response);
    }

    @Test
    public void invalidContentType() throws IOException, ServletException {
        ServletRequest request = mock(ServletRequest.class);
        ServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getContentType()).thenReturn("json");
        StringWriter out = new StringWriter();
        PrintWriter writer = new PrintWriter(out);
        when(response.getWriter()).thenReturn(writer);

        filter.doFilter(request, response, chain);

        writer.flush();
        assertEquals("Invalid Content-Type json", out.toString());
    }

}
