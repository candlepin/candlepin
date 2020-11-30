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
package org.candlepin.resteasy.filter;

import org.candlepin.common.util.VersionUtil;

import org.springframework.stereotype.Component;

import java.util.Map;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;

/**
 * VersionResponseFilter
 */
@Component
@Provider
@Priority(Priorities.HEADER_DECORATOR)
public class VersionResponseFilter implements ContainerResponseFilter {
    @Override
    public void filter(ContainerRequestContext reqContext, ContainerResponseContext respContext) {
        /* TODO: spring- Remove this print statement */
        System.out.println("VersionResponseFilter");

        Map<String, String> map = VersionUtil.getVersionMap();
        respContext.getHeaders().add(VersionUtil.VERSION_HEADER,
            map.get("version") + "-" + map.get("release"));
    }
}
