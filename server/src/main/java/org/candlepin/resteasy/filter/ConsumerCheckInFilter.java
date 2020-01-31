/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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

import org.candlepin.auth.ConsumerPrincipal;
import org.candlepin.auth.Principal;
import org.candlepin.auth.UpdateConsumerCheckIn;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.resteasy.AnnotationLocator;

import org.jboss.resteasy.core.ResteasyContext;

import java.io.IOException;
import java.lang.reflect.Method;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.ext.Provider;



/** This filter is applied to resource methods annotated with @UpdateConsumerCheckIn.  It
 * will inspect the principal and if the principal is a ConsumerPrincipal, it will update
 * the consumer's check-in time.
 */
@Priority(Priorities.USER)
@Provider
public class ConsumerCheckInFilter implements ContainerRequestFilter {
    private final ConsumerCurator consumerCurator;
    private final AnnotationLocator annotationLocator;

    @Inject
    public ConsumerCheckInFilter(ConsumerCurator consumerCurator, AnnotationLocator annotationLocator) {
        this.consumerCurator = consumerCurator;
        this.annotationLocator = annotationLocator;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        ResourceInfo resourceInfo = ResteasyContext.getContextData(ResourceInfo.class);
        Method method = resourceInfo.getResourceMethod();

        Principal principal = ResteasyContext.getContextData(Principal.class);
        if (principal instanceof ConsumerPrincipal &&
            annotationLocator.getAnnotation(method, UpdateConsumerCheckIn.class) != null) {
            ConsumerPrincipal p = (ConsumerPrincipal) principal;
            consumerCurator.updateLastCheckin(p.getConsumer());
        }
    }
}
