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

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import com.google.inject.persist.UnitOfWork;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

/**
 * CandlepinPersistFilterTest
 */
public class CandlepinPersistFilterTest {

    private CandlepinPersistFilter filter;
    private UnitOfWork work;

    @Before
    public void init() {
        work = mock(UnitOfWork.class);
        filter = new CandlepinPersistFilter(work);
    }

    @Test
    public void transaction() throws IOException, ServletException {
        ServletRequest req = mock(ServletRequest.class);
        ServletResponse rsp = mock(ServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(req, rsp, chain);
        verify(work, atLeastOnce()).begin();
        verify(work, atLeastOnce()).end();
        verify(chain, atLeastOnce()).doFilter(eq(req), eq(rsp));
    }

    @Test
    public void destroy() {
        // make sure any changes don't cause an issue
        filter.destroy();
        verifyZeroInteractions(work);
    }

    @Test
    public void filterInit() throws ServletException {
        FilterConfig fconfig  = mock(FilterConfig.class);
        filter.init(fconfig);
        verifyNoMoreInteractions(fconfig);
    }
}
