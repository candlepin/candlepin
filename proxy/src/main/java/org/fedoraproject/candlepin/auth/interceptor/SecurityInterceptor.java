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


import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.auth.Access;
import org.fedoraproject.candlepin.exceptions.ForbiddenException;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.fedoraproject.candlepin.model.ConsumerCurator;
import org.fedoraproject.candlepin.model.EntitlementCertificateCurator;
import org.fedoraproject.candlepin.model.EntitlementCurator;
import org.fedoraproject.candlepin.model.OwnerCurator;

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

    @Inject private OwnerCurator ownerCurator;
    @Inject private ConsumerCurator consumerCurator;
    @Inject private EntitlementCurator entitlementCurator;
    @Inject private EntitlementCertificateCurator entitlementCertificateCurator;
    
    private static Logger log = Logger.getLogger(SecurityInterceptor.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Principal principal = this.principalProvider.get();
        log.debug("Invoked.");


        if (isProtected(invocation)) {
            Map<Object, Class> parameters = findVerifiedParameters(invocation);

            for (Entry<Object, Class> param : parameters.entrySet()) {
                //principal.
            }
        }



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

    private boolean isProtected(MethodInvocation invocation) {
        return invocation.getMethod().isAnnotationPresent(Protected.class);
    }
    
    /**
     * Scans the parameters for the method being invoked. When we find one annotated with
     * PathParam of the given name, we return the value of that parameter. (assumed to be
     * a string)
     *
     * @param pathParamName
     * @return
     */
    private Map<String, Class> findVerifiedParameters(MethodInvocation invocation) {
        Map<String, Class> parameters = new HashMap<String, Class>();
        Method m = invocation.getMethod();

        for (int i = 0; i < m.getParameterAnnotations().length; i++) {
            for (Annotation a : m.getParameterAnnotations()[i]) {
                if (a instanceof Verify) {

                    Class verifyType = ((Verify) a).value();
                    String verifyParam = (String) invocation.getArguments()[i];
                    
                    parameters.put(verifyParam, verifyType);
                }
            }
        }

        return parameters;
    }

    private interface EntityStore {
        Object lookup(String key);
    }

    private class OwnerStore implements EntityStore {
        @Override
        public Object lookup(String key) {
            return ownerCurator.lookupByKey(key);
        }
    }

    private class ConsumerStore implements EntityStore {
        @Override
        public Object lookup(String key) {
            return consumerCurator.findByUuid(key);
        }
    }

    private class EntitlementStore implements EntityStore {
        @Override
        public Object lookup(String key) {
            return entitlementCurator.findByCertificateSerial(Long.parseLong(key));
        }
    }
    

}
