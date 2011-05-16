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
package org.fedoraproject.candlepin.auth.interceptor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.PathParam;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.auth.UserPrincipal;
import org.fedoraproject.candlepin.exceptions.ForbiddenException;
import org.fedoraproject.candlepin.exceptions.IseException;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * Interceptor for enforcing role based access to REST API methods.
 * 
 * This interceptor deals with coarse grained access, it only answers the question of
 * can a principal with these roles access this method. It does not support any paramaters
 * such as verifying the call is being made on a visible consumer/owner, this is handled
 * instead by the filtering mechanism.
 */
public class SecurityInterceptor implements MethodInterceptor {

    @Inject private Provider<Principal> principalProvider;
    @Inject private Provider<I18n> i18nProvider;
    
    private static Logger log = Logger.getLogger(SecurityInterceptor.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Principal currentUser = this.principalProvider.get();
        log.debug("Invoked.");
        
        Set<Role> allowedRoles = new HashSet<Role>();
        
        // Super admins can access any URL:
        allowedRoles.add(Role.SUPER_ADMIN);
        log.debug(invocation.getClass().getName());
        log.debug(invocation.getClass().getAnnotations().length);
        
        
        AllowRoles annotation = invocation.getMethod().getAnnotation(AllowRoles.class);
        log.debug("Method annotation: " + annotation);
        if (annotation != null) {
            for (Role allowed : annotation.roles()) {
                log.debug("   allowing role: " + allowed);
                allowedRoles.add(allowed);
            }
        }
        
        boolean foundRole = false;
        for (Role allowed : allowedRoles) {
            if (currentUser.hasRole(allowed)) {
                foundRole = true;
                if (log.isDebugEnabled()) {
                    log.debug("Granting access for " + currentUser + " due to role: " + 
                        allowed);
                }
                break;
            }
        }
        
        I18n i18n = this.i18nProvider.get();
        if (!foundRole) {
            log.warn("Refusing principal: " + currentUser + " access to: " + 
                invocation.getMethod().getName());

            String error = "Insufficient permission";
            throw new ForbiddenException(i18n.tr(error));
        }
        
        // Verify a username path param. If the current principal is a user principal, who
        // does *not* have the super admin role, we need to make sure their username matches
        // the username being requested:
        if (annotation != null && !annotation.verifyUser().equals("")) {
            verifyUser(invocation, currentUser, annotation, i18n);
        }

        return invocation.proceed();
    }

    /*
     * Verify a username PathParam matches the currently authenticated user. (if the
     * current principal is a user principal who does not have the super admin role)
     */
    private void verifyUser(MethodInvocation invocation, Principal currentUser,
        AllowRoles annotation, I18n i18n) {
        String usernameAccessed = findParameterValue(invocation,
            annotation.verifyUser(), i18n);
        if (currentUser.getType().equals(Principal.USER_TYPE) &&
            !currentUser.hasRole(Role.SUPER_ADMIN)) {
            if (!usernameAccessed.equals(((UserPrincipal) currentUser).getUsername())) {
                throw new ForbiddenException(i18n.tr("Access denied for user: " +
                    usernameAccessed));
            }

        }
    }

    /**
     * Scans the parameters for the method being invoked. When we find one annotated with
     * PathParam of the given name, we return the value of that parameter. (assumed to be
     * a string)
     *
     * @param pathParamName
     * @return
     */
    private String findParameterValue(MethodInvocation invocation, String pathParamName,
        I18n i18n) {
        Method m = invocation.getMethod();

        for (int i = 0; i < m.getParameterAnnotations().length; i++) {
            for (Annotation a : m.getParameterAnnotations()[i]) {
                if (a instanceof PathParam) {

                    String pathParam = ((PathParam) a).value();
                    if (pathParam.equals(pathParamName)) {
                        return (String) invocation.getArguments()[i];
                    }
                }
            }
        }

        // If we reach this point the code is probably incorrect (AcceptRoles annotation
        // trying to verify a PathParam that couldn't be found in the method signature)
        log.error("Unable to find PathParam: " + pathParamName);

        // Intentionally being vague for message that end client will see:
        throw new IseException(i18n.tr("Internal server error"));
    }
    
}
