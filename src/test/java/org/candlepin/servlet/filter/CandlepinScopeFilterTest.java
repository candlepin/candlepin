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
package org.candlepin.servlet.filter;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.doThrow;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.candlepin.guice.CandlepinSingletonScope;
import org.junit.Before;
import org.junit.Test;

/**
 * CandlepinScopeFilterTest
 */
public class CandlepinScopeFilterTest {

    private HttpServletRequest request;
    private HttpServletResponse response;
    private CandlepinSingletonScope scope;
    private CandlepinScopeFilter filter;
    private FilterChain chain;

    @Before
    public void init() {
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        scope = mock(CandlepinSingletonScope.class);
        filter = new CandlepinScopeFilter(scope);
    }

    @Test
    public void ensureFilterEntersAndExitsScope() throws Exception {
        filter.doFilter(request, response, chain);
        verify(scope).enter();
        verify(scope).exit();
    }

    @Test
    public void ensureFilterExitsScopeOnError() throws Exception {
        doThrow(new ServletException()).when(chain).doFilter(request, response);
        try {
            filter.doFilter(request, response, chain);
            fail("Expected an exception to be thrown.");
        }
        catch(ServletException e) {
            // Expected. We want to make sure that our scope is properly
            // exited.
        }
        verify(scope).enter();
        verify(scope).exit();
    }

}
