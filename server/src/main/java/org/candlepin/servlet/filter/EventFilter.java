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

import org.candlepin.audit.EventSink;
import org.candlepin.common.filter.TeeHttpServletResponse;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response.Status;

/**
 * CandlepinScopeFilter
 *
 * A servlet filter used to dispatch queued events *if* the request was successful.
 *
 */

@Singleton
public class EventFilter implements Filter {

    private static Logger log = LoggerFactory.getLogger(EventFilter.class);

    private final EventSink eventSink;

    @Inject
    public EventFilter(EventSink eventSink) {
        this.eventSink = eventSink;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
        FilterChain chain) throws IOException, ServletException {

        TeeHttpServletResponse resp = new TeeHttpServletResponse(
                (HttpServletResponse) response);
        chain.doFilter(request, resp);
        Status status = Status.fromStatusCode(resp.getStatus());
        if (status.getFamily() == Status.Family.SUCCESSFUL) {
            eventSink.sendEvents();
        }
        else {
            eventSink.rollback();
            log.debug("Request failed, skipping event sending, status={}", status);
        }
    }

    @Override
    public void destroy() {
    }

    @Override
    public void init(FilterConfig config) throws ServletException {
    }

}
