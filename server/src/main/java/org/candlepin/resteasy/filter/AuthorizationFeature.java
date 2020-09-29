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

import org.candlepin.auth.Verify;
import org.candlepin.common.auth.SecurityHole;
import org.candlepin.resteasy.AnnotationLocator;

import io.swagger.jaxrs.listing.ApiListingResource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import javax.ws.rs.container.DynamicFeature;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.ext.Provider;


/**
 * DynamicFeature implementation that determines when to apply the
 * security interceptor.  This Feature is run as part of the JAX-RS
 * bootstrap process not on every request.
 *
 * Guice will throw a ProvisionException if nothing is bound to the
 * AuthorizationFilter annotation.
 */
@Component
@Provider
public class AuthorizationFeature implements DynamicFeature {
    private static final Logger log = LoggerFactory.getLogger(AuthorizationFeature.class);

    private AbstractAuthorizationFilter authorizationFilter;
    private AbstractAuthorizationFilter superAdminFilter;
    private AbstractAuthorizationFilter securityHoleFilter;
    private AnnotationLocator annotationLocator;

    @Autowired
    public AuthorizationFeature(VerifyAuthorizationFilter authorizationFilter,
        SuperAdminAuthorizationFilter superAdminFilter,
        SecurityHoleAuthorizationFilter securityHoleFilter,
        AnnotationLocator annotationLocator) {


        this.authorizationFilter = authorizationFilter;
        this.superAdminFilter = superAdminFilter;
        this.securityHoleFilter = securityHoleFilter;
        this.annotationLocator = annotationLocator;

    }

    @Override
    public void configure(ResourceInfo resourceInfo, FeatureContext context) {
        Method method = resourceInfo.getResourceMethod();

        SecurityHole securityHole = annotationLocator.getAnnotation(method, SecurityHole.class);

        String name = method.getDeclaringClass().getName() + "." + method.getName();
        if (securityHole != null) {
            log.debug("Not registering authorization filter on {}", name);
            context.register(securityHoleFilter);
        }
        else if (resourceInfo.getResourceClass().equals(ApiListingResource.class)) {
            log.debug("Not registering authorization filter for Swagger: {}", name);
            context.register(securityHoleFilter);
        }
        else if (isSuperAdminOnly(method)) {
            log.debug("Registering superadmin only on {}", name);
            context.register(superAdminFilter);
        }
        else {
            log.debug("Registering standard authorization on {}", name);
            context.register(authorizationFilter);
        }
    }

    protected boolean isSuperAdminOnly(Method method) {
        Annotation[][] allAnnotations = annotationLocator.getParameterAnnotations(method);

        // Any occurrence of the Verify annotation means the method is not superadmin exclusive.
        for (int i = 0; i < allAnnotations.length; i++) {
            for (Annotation a : allAnnotations[i]) {
                if (a instanceof Verify) {
                    return false;
                }
            }
        }
        return true;
    }
}
