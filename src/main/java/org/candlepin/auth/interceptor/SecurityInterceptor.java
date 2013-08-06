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
package org.candlepin.auth.interceptor;

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
import org.candlepin.model.DeletedConsumerCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Persisted;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.User;
import org.candlepin.resteasy.interceptor.AuthUtil;
import org.candlepin.util.Util;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.log4j.Logger;
import org.xnap.commons.i18n.I18n;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;

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
        storeMap.put(Product.class, new ProductStore());
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
            log.debug("Allowing invocation to proceed with no authentication required.");
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

                    // Use the correct curator (in storeMap) to look up the actual
                    // entity with the annotated argument
                    if (!storeMap.containsKey(verifyType)) {
                        log.error("No store configured to verify: " + verifyType);
                        throw new IseException(i18n.tr("Unable to verify request."));
                    }

                    List entities = new ArrayList();

                    Object argument = invocation.getArguments()[i];
                    if (argument instanceof String) {
                        String verifyParam = (String) argument;
                        log.debug("Verifying " + requiredAccess +
                            " access to " + verifyType + ": " + verifyParam);

                        Object entity = storeMap.get(verifyType).lookup(verifyParam);
                        // If the request is just for a single item, throw an exception
                        // if it is not found.
                        if (entity == null) {
                            // This is bad, we're verifying a parameter with an ID which
                            // doesn't seem to exist in the DB. Error will be thrown in
                            // invoke though.
                            String typeName = Util.getClassName(verifyType);
                            if (typeName.equals("Owner")) {
                                typeName = i18n.tr("Organization");
                            }
                            log.info("No such entity: " + typeName + " id: " +
                                verifyParam);

                            throw new NotFoundException(i18n.tr(
                                "{0} with id {1} could not be found.",
                                typeName, verifyParam));
                        }

                        entities.add(entity);
                    }
                    else {
                        Collection<String> verifyParams = (Collection<String>) argument;
                        log.debug("Verifying " + requiredAccess +
                            " access to collection of" + verifyType + ": " + verifyParams);
                        // If the request is for a list of items, we'll leave it
                        // up to the requester to determine if something is missing or not.
                        if (verifyParams != null && !verifyParams.isEmpty()) {
                            entities = storeMap.get(verifyType).lookup(verifyParams);
                        }
                    }

                    for (Object entity : entities) {
                        if (!principal.canAccess(entity, requiredAccess)) {
                            denyAccess(principal, invocation);
                        }
                    }
                }
            }
        }

        // If we found no parameters, this method can only be called by super admins:
        if (!foundVerifiedParameters && !principal.hasFullAccess()) {
            denyAccess(principal, invocation);
        }
    }

    private interface EntityStore<E extends Persisted> {
        E lookup(String key);
        List<E> lookup(Collection<String> keys);
    }

    private class OwnerStore implements EntityStore<Owner> {
        private OwnerCurator ownerCurator;

        private void initialize() {
            if (ownerCurator == null) {
                ownerCurator = injector.getInstance(OwnerCurator.class);
            }
        }

        @Override
        public Owner lookup(String key) {
            initialize();
            return ownerCurator.lookupByKey(key);
        }

        @Override
        public List<Owner> lookup(Collection<String> keys) {
            initialize();
            return ownerCurator.lookupByKeys(keys);
        }
    }

    private class EnvironmentStore implements EntityStore<Environment> {
        private EnvironmentCurator envCurator;

        private void initialize() {
            if (envCurator == null) {
                envCurator = injector.getInstance(EnvironmentCurator.class);
            }
        }

        @Override
        public Environment lookup(String key) {
            initialize();
            return envCurator.find(key);
        }

        @Override
        public List<Environment> lookup(Collection<String> keys) {
            initialize();
            return envCurator.listAllByIds(keys);
        }
    }

    private class ConsumerStore implements EntityStore<Consumer> {
        private ConsumerCurator consumerCurator;
        private DeletedConsumerCurator deletedConsumerCurator;

        private void initialize() {
            if (consumerCurator == null) {
                consumerCurator = injector.getInstance(ConsumerCurator.class);
            }

            if (deletedConsumerCurator == null) {
                deletedConsumerCurator = injector.getInstance(DeletedConsumerCurator.class);
            }
        }

        @Override
        public Consumer lookup(String key) {
            initialize();
            if (deletedConsumerCurator.countByConsumerUuid(key) > 0) {
                log.debug("Key " + key + " is deleted, throwing GoneException");
                I18n i18n = injector.getInstance(I18n.class);
                throw new GoneException(i18n.tr("Unit {0} has been deleted", key), key);
            }

            return consumerCurator.findByUuid(key);
        }

        @Override
        public List<Consumer> lookup(Collection<String> keys) {
            initialize();
            // Do not look for deleted consumers because we do not want to throw
            // an exception and reject the whole request just because one of
            // the requested items is deleted.
            return consumerCurator.findByUuids(keys);
        }
    }

    private class EntitlementStore implements EntityStore<Entitlement> {
        private EntitlementCurator entitlementCurator;

        private void initialize() {
            if (entitlementCurator == null) {
                entitlementCurator = injector.getInstance(EntitlementCurator.class);
            }
        }

        @Override
        public Entitlement lookup(String key) {
            initialize();
            return entitlementCurator.find(key);
        }

        @Override
        public List<Entitlement> lookup(Collection<String> keys) {
            initialize();
            return entitlementCurator.listAllByIds(keys);
        }
    }

    private class PoolStore implements EntityStore<Pool> {
        private PoolCurator poolCurator;

        private void initialize() {
            if (poolCurator == null) {
                poolCurator = injector.getInstance(PoolCurator.class);
            }
        }

        @Override
        public Pool lookup(String key) {
            initialize();
            return poolCurator.find(key);
        }

        @Override
        public List<Pool> lookup(Collection<String> keys) {
            initialize();
            return poolCurator.listAllByIds(keys);
        }
    }

    private class ActivationKeyStore implements EntityStore<ActivationKey> {
        private ActivationKeyCurator activationKeyCurator;

        private void initialize() {
            if (activationKeyCurator == null) {
                activationKeyCurator = injector.getInstance(ActivationKeyCurator.class);
            }
        }

        @Override
        public ActivationKey lookup(String key) {
            initialize();
            return activationKeyCurator.find(key);
        }

        @Override
        public List<ActivationKey> lookup(Collection<String> keys) {
            initialize();
            return activationKeyCurator.listAllByIds(keys);
        }
    }

    private class ProductStore implements EntityStore<Product> {
        private ProductCurator productCurator;

        private void initialize() {
            if (productCurator == null) {
                productCurator = injector.getInstance(ProductCurator.class);
            }
        }

        @Override
        public Product lookup(String key) {
            initialize();
            return productCurator.find(key);
        }

        @Override
        public List<Product> lookup(Collection<String> keys) {
            initialize();
            return productCurator.listAllByIds(keys);
        }
    }

    private static class UserStore implements EntityStore<User> {
        @Override
        public User lookup(String username) {
            /* WARNING: Semi-risky business here, we need a user object for the security
             * code to validate, but in this area we seem to only need the username.
             */
            return new User(username, null);
        }

        @Override
        public List<User> lookup(Collection<String> keys) {
            List<User> users = new ArrayList<User>();
            for (String username : keys) {
                users.add(new User(username, null));
            }

            return users;
        }
    }

}
