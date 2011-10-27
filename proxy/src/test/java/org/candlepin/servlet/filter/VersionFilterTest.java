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

import org.junit.After;
import org.junit.Test;

import java.io.File;
import java.io.PrintStream;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * VersionFilterTest
 */
public class VersionFilterTest {

    @Test
    public void doFilter() throws Exception {
        PrintStream ps = new PrintStream(new File(this.getClass()
            .getClassLoader().getResource("candlepin_info.properties").toURI()));
        ps.println("version=1.3.0");
        ps.println("release=1%{?dist}");
        ps.close();

        HttpServletResponse rsp = mock(HttpServletResponse.class);
        HttpServletRequest req = mock(HttpServletRequest.class);
        FilterChain chain = mock(FilterChain.class);

        VersionFilter filter = new VersionFilter();
        filter.doFilter(req, rsp, chain);
        verify(chain, atLeastOnce()).doFilter(eq(req), eq(rsp));
        verify(rsp, atLeastOnce()).addHeader(eq(VersionFilter.VERSION_HEADER),
            eq("1.3.0-1%{?dist}"));
    }

    @Test
    public void unknown() throws Exception {
        PrintStream ps = new PrintStream(new File(this.getClass()
            .getClassLoader().getResource("candlepin_info.properties").toURI()));
        ps.println("corrupted");
        ps.close();

        HttpServletResponse rsp = mock(HttpServletResponse.class);
        HttpServletRequest req = mock(HttpServletRequest.class);
        FilterChain chain = mock(FilterChain.class);

        VersionFilter filter = new VersionFilter();
        filter.doFilter(req, rsp, chain);
        verify(chain, atLeastOnce()).doFilter(eq(req), eq(rsp));
        verify(rsp, atLeastOnce()).addHeader(eq(VersionFilter.VERSION_HEADER),
            eq("Unknown-Unknown"));
    }

    @After
    public void cleanup() throws Exception {
        PrintStream ps = new PrintStream(new File(this.getClass()
            .getClassLoader().getResource("candlepin_info.properties").toURI()));
        ps.println("version=${version}");
        ps.println("release=${release}");
        ps.close();
    }
}
