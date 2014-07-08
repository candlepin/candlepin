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
package org.canadianTenPin.servlet.filter;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.canadianTenPin.guice.CanadianTenPinSingletonScope;

import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * CanadianTenPinScopeFilter
 *
 * A servlet filter used to wrap a request in a custom guice scope providing
 * a custom per request injection. Our object graph has been loaded by guice
 * before the filtering is complete.
 *
 * NOTE: It is important that this filter is the first to be processed.
 *
 */

@Singleton
public class CanadianTenPinScopeFilter implements Filter {

    private CanadianTenPinSingletonScope singletonScope;

    @Inject
    public CanadianTenPinScopeFilter(CanadianTenPinSingletonScope singletonScope) {
        this.singletonScope = singletonScope;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
        FilterChain chain) throws IOException, ServletException {
        singletonScope.enter();
        try {
            chain.doFilter(request, response);
        }
        finally {
            singletonScope.exit();
        }
    }

    @Override
    public void destroy() {
    }

    @Override
    public void init(FilterConfig config) throws ServletException {
    }

}
