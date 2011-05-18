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


import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.auth.Verb;
import org.fedoraproject.candlepin.exceptions.ForbiddenException;
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
        EnumSet<Verb> allowedRoles = EnumSet.of(Verb.SUPER_ADMIN);
        
        AllowRoles annotation = invocation.getMethod().getAnnotation(AllowRoles.class);
        log.debug("Method annotation: " + annotation);
        if (annotation != null) {
            for (Verb allowed : annotation.roles()) {
                log.debug("   allowing role: " + allowed);
                allowedRoles.add(allowed);
            }
        }
        
        boolean foundRole = false;
        for (Verb allowed : allowedRoles) {
            if (hasRole(currentUser, allowed)) {
                foundRole = true;
                if (log.isDebugEnabled()) {
                    log.debug("Granting access for " + currentUser + " due to role: " + 
                        allowed);
                }
                break;
            }
        }
        
        if (!foundRole) {
            log.warn("Refusing principal: " + currentUser + " access to: " + 
                invocation.getMethod().getName());
            I18n i18n = this.i18nProvider.get();
            String error = "Insufficient permission";
            throw new ForbiddenException(i18n.tr(error));
        }
        
        return invocation.proceed();
    }

    // TODO:  This should also go away - when this whole interceptor dies!!!
    private boolean hasRole(Principal principal, Verb role) {
        for (Permission permission : principal.getPermissions()) {
            if (permission.getVerb().equals(role)) {
                return true;
            }
        }

        return false;
    }
    
}
