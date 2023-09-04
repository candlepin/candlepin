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

import org.xnap.commons.i18n.I18n;

import java.io.IOException;
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

/**
 * CandlepinContentTypeFilter
 *
 * A servlet filter used to filter out the invalid content type from a
 * request.
 */

@Singleton
public class CandlepinContentTypeFilter implements Filter {

    private final Provider<I18n> i18nProvider;

    @Inject
    public CandlepinContentTypeFilter(Provider<I18n> i18nProvider) {
        this.i18nProvider = Objects.requireNonNull(i18nProvider);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
        FilterChain chain) throws IOException, ServletException {
        // Validate media type contains a slash, to avoid RESTEasy's inability to handle this
        String contentType = request.getContentType();
        if (contentType == null || contentType.contains("/")) {
            chain.doFilter(request, response);
        }
        else {
            I18n i18n = this.i18nProvider.get();

            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setContentType("text/plain");
            httpResponse.setCharacterEncoding("UTF-8");
            httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            httpResponse.getWriter().write(i18n.tr("Invalid Content-Type {0}", contentType));
        }
    }

    @Override
    public void destroy() {
    }

    @Override
    public void init(FilterConfig config) throws ServletException {
    }

}
