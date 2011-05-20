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

import javax.ws.rs.PathParam;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.auth.Access;
import org.fedoraproject.candlepin.auth.UserPrincipal;
import org.fedoraproject.candlepin.exceptions.ForbiddenException;
import org.fedoraproject.candlepin.exceptions.IseException;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.EnumSet;
import org.fedoraproject.candlepin.model.Permission;

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




        // Super admins can access any URL:
        // TODO: Re-address this, is implied super admin access better than explicit?
        // should super admin be handled outside of roles/permissions and in principals
        // instead.
        EnumSet<Access> allowedRoles = EnumSet.of(Access.SUPER_ADMIN);
        
        AllowAccess annotation = invocation.getMethod().getAnnotation(AllowAccess.class);
        log.debug("Method annotation: " + annotation);
        if (annotation != null) {
            for (Access allowed : annotation.types()) {
                log.debug("   allowing role: " + allowed);
                allowedRoles.add(allowed);
            }
        }
        
        boolean foundRole = false;
        for (Access allowed : allowedRoles) {
            if (hasRole(currentUser, allowed)) {
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

    // TODO:  This should also go away - when this whole interceptor dies!!!
    private boolean hasRole(Principal principal, Access role) {
        for (Permission permission : principal.getPermissions()) {
            if (permission.getVerb() == role) {
                return true;
            }
        }

        return false;
    }

    /*
     * Verify a username PathParam matches the currently authenticated user. (if the
     * current principal is a user principal who does not have the super admin role)
     */
    private void verifyUser(MethodInvocation invocation, Principal currentUser,
        AllowAccess annotation, I18n i18n) {
        String usernameAccessed = findParameterValue(invocation,
            annotation.verifyUser(), i18n);
        if (currentUser.getType().equals(Principal.USER_TYPE) && 
                !currentUser.isSuperAdmin()) {
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
