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
package org.candlepin.auth.interceptor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.log4j.Logger;
import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.exceptions.GoneException;
import org.candlepin.exceptions.IseException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.ActivationKey;
import org.candlepin.model.ActivationKeyCurator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.User;
import org.candlepin.resteasy.interceptor.AuthUtil;
import org.candlepin.util.Util;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;

/**
 * Interceptor for enforcing role based access to REST API methods.
 *
 * This interceptor deals with coarse grained access, it only answers the
 * question of can a principal with these roles access this method. It does not
 * support any paramaters such as verifying the call is being made on a visible
 * consumer/owner, this is handled instead by the filtering mechanism.
 */
public class SecurityInterceptor implements MethodInterceptor {

    @Inject private Provider<Principal> principalProvider;
    @Inject private Provider<I18n> i18nProvider;
    @Inject private Injector injector;

    // TODO:  This would not really be needed if we were consistent about what
    //        we use as IDs in our urls!
    @SuppressWarnings("rawtypes")
    private final Map<Class, EntityStore> storeMap;

    private static Logger log = Logger.getLogger(SecurityInterceptor.class);

    @SuppressWarnings("rawtypes")
    public SecurityInterceptor() {
        this.storeMap = new HashMap<Class, EntityStore>();

        storeMap.put(Owner.class, new OwnerStore());
        storeMap.put(Environment.class, new EnvironmentStore());
        storeMap.put(Consumer.class, new ConsumerStore());
        storeMap.put(Entitlement.class, new EntitlementStore());
        storeMap.put(Pool.class, new PoolStore());
        storeMap.put(User.class, new UserStore());
        storeMap.put(ActivationKey.class, new ActivationKeyStore());

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {

        log.debug("Invoked security interceptor " + invocation.getMethod());

        SecurityHole securityHole = AuthUtil.checkForSecurityHoleAnnotation(
            invocation.getMethod());

        // If method is annotated with SecurityHole and requires no authentication,
        // allow to proceed:
        if (securityHole != null && securityHole.noAuth()) {
            log.warn("Allowing invocation to proceed with no authentication required.");
            return invocation.proceed();
        }

        Principal principal = this.principalProvider.get();

        if (securityHole != null) {
            return invocation.proceed();
        }

        Access defaultAccess = getAssumedAccessType(invocation);

        verifyParameters(invocation, principal, defaultAccess);

        return invocation.proceed();
    }

    private void denyAccess(Principal principal, MethodInvocation invocation) {
        I18n i18n = this.i18nProvider.get();
        log.warn("Refusing principal: " + principal + " access to: " +
                    invocation.getMethod().getName());

        String error = "Insufficient permissions";
        throw new ForbiddenException(i18n.tr(error));
    }

    /**
     * Scans the method annotations for RESTEasy annotations, to determine
     * the HTTP verbs used. We'll assume this is the required access type, but any
     * Verify annotation which specifies an access type can override this later.
     *
     * @param invocation method invocation object
     * @return the required minimum access type
     */
    private Access getAssumedAccessType(MethodInvocation invocation) {

        // Assume the minimum level to start with, and bump up as we see
        // stricter annotations
        Access minimumLevel = Access.READ_ONLY;

        // If we had write or delete access types, that would go here,
        // and we'd only break on the access.all type.
        for (Annotation annotation : invocation.getMethod().getAnnotations()) {
            if (annotation instanceof PUT ||
                annotation instanceof POST ||
                annotation instanceof DELETE) {
                minimumLevel = Access.ALL;
                break;
            }
            // Other annotations are GET, HEAD, and OPTIONS. assume read only for those.
        }
        return minimumLevel;
    }

    /**
     * Scans the parameters for the method being invoked looking for those annotated with
     * Verify, and checks that the principal has access to them all.
     */
    private void verifyParameters(MethodInvocation invocation,
        Principal principal, Access defaultAccess) {

        I18n i18n = this.i18nProvider.get();
        Method m = invocation.getMethod();

        // Need to check after examining all parameters to see if we found any:
        boolean foundVerifiedParameters = false;

        for (int i = 0; i < m.getParameterAnnotations().length; i++) {
            for (Annotation a : m.getParameterAnnotations()[i]) {
                if (a instanceof Verify) {
                    foundVerifiedParameters = true;
                    Access requiredAccess = defaultAccess;

                    @SuppressWarnings("rawtypes")
                    Class verifyType = ((Verify) a).value();
                    if (((Verify) a).require() != Access.NONE) {
                        requiredAccess = ((Verify) a).require();
                    }
                    String verifyParam = (String) invocation.getArguments()[i];

                    log.debug("Verifying " + requiredAccess + " access to " + verifyType +
                        ": " + verifyParam);

                    // Use the correct curator (in storeMap) to look up the actual
                    // entity with the annotated argument
                    if (!storeMap.containsKey(verifyType)) {
                        log.error("No store configured to verify: " + verifyType);
                        throw new IseException(i18n.tr("Unable to verify request."));
                    }
                    Object entity = storeMap.get(verifyType).lookup(verifyParam);
                    if (entity == null) {
                        // This is bad, we're verifying a parameter with an ID which
                        // doesn't seem to exist in the DB. Error will be thrown in
                        // invoke though.
                        log.error("No such entity: " + verifyType + " id: " + verifyParam);
                        throw new NotFoundException(
                            i18n.tr("{0} with id {1} could not be found",
                                Util.getClassName(verifyType), verifyParam));
                    }

                    // Deny access if the entity to be verified turns out to be null, or
                    // the principal cannot access it:
                    if (!principal.canAccess(entity, requiredAccess)) {
                        denyAccess(principal, invocation);
                    }
                }
            }
        }

        // If we found no parameters, this method can only be called by super admins:
        if (!foundVerifiedParameters && !principal.hasFullAccess()) {
            denyAccess(principal, invocation);
        }
    }

    private interface EntityStore {
        Object lookup(String key);
    }

    private class OwnerStore implements EntityStore {
        private OwnerCurator ownerCurator;

        @Override
        public Object lookup(String key) {
            if (ownerCurator == null) {
                ownerCurator = injector.getInstance(OwnerCurator.class);
            }

            return ownerCurator.lookupByKey(key);
        }
    }

    private class EnvironmentStore implements EntityStore {
        private EnvironmentCurator envCurator;

        @Override
        public Object lookup(String key) {
            if (envCurator == null) {
                envCurator = injector.getInstance(EnvironmentCurator.class);
            }

            return envCurator.find(key);
        }
    }

    private class ConsumerStore implements EntityStore {
        private ConsumerCurator consumerCurator;

        @Override
        public Object lookup(String key) {
            if (consumerCurator == null) {
                consumerCurator = injector.getInstance(ConsumerCurator.class);
            }
            // XXX: mockup code for testing
            if ("deleted-consumer-id".equals(key)) {
                // XXX: 410 lookup
                log.info("Key is deleted, throwing GoneException");
                throw new GoneException("Consumer " + key + " has been deleted");
            }
            return consumerCurator.findByUuid(key);
        }
    }

    private class EntitlementStore implements EntityStore {
        private EntitlementCurator entitlementCurator;

        @Override
        public Object lookup(String key) {
            if (entitlementCurator == null) {
                entitlementCurator = injector.getInstance(EntitlementCurator.class);
            }
            return entitlementCurator.find(key);
        }
    }

    private class PoolStore implements EntityStore {
        private PoolCurator poolCurator;

        @Override
        public Object lookup(String key) {
            if (poolCurator == null) {
                poolCurator = injector.getInstance(PoolCurator.class);
            }

            return poolCurator.find(key);
        }
    }

    private class ActivationKeyStore implements EntityStore {
        private ActivationKeyCurator activationKeyCurator;

        @Override
        public Object lookup(String key) {
            if (activationKeyCurator == null) {
                activationKeyCurator = injector.getInstance(ActivationKeyCurator.class);
            }

            return activationKeyCurator.find(key);
        }
    }


    private class UserStore implements EntityStore {
        @Override
        public Object lookup(String username) {

            /* WARNING: Semi-risky business here, we need a user object for the security
             * code to validate, but in this area we seem to only need the username.
             */

            return new User(username, null);
        }
    }

}
