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
package org.candlepin.resteasy.interceptor;

import org.candlepin.auth.Access;
import org.candlepin.auth.ConsumerPrincipal;
import org.candlepin.auth.NoAuthPrincipal;
import org.candlepin.auth.Principal;
import org.candlepin.auth.SubResource;
import org.candlepin.auth.interceptor.SecurityHole;
import org.candlepin.auth.interceptor.Verify;
import org.candlepin.config.Config;
import org.candlepin.exceptions.ForbiddenException;
import org.candlepin.exceptions.GoneException;
import org.candlepin.exceptions.IseException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.exceptions.UnauthorizedException;
import org.candlepin.guice.HttpMethodMatcher;
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
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyCurator;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.servlet.filter.logging.LoggingFilter;
import org.candlepin.servlet.filter.logging.ServletLogger;
import org.candlepin.servlet.filter.logging.TeeHttpServletRequest;
import org.candlepin.util.Util;

import com.google.inject.Inject;
import com.google.inject.Injector;

import org.jboss.resteasy.annotations.interception.SecurityPrecedence;
import org.jboss.resteasy.annotations.interception.ServerInterceptor;
import org.jboss.resteasy.core.ResourceMethod;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.spi.Failure;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.HttpResponse;
import org.jboss.resteasy.spi.MethodInjector;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.jboss.resteasy.spi.interception.AcceptedByMethod;
import org.jboss.resteasy.spi.interception.PreProcessInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.xnap.commons.i18n.I18n;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.ext.Provider;

/**
 * AuthInterceptor is responsible for determining the principal and whether or not
 * that principal has access to the called method.
 */
@Provider
@ServerInterceptor
@SecurityPrecedence
public class AuthInterceptor implements PreProcessInterceptor, AcceptedByMethod {
    private static Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    private Injector injector;
    private ConsumerCurator consumerCurator;
    private DeletedConsumerCurator deletedConsumerCurator;
    private Config config;
    private UserServiceAdapter userService;
    private List<AuthProvider> providers = new ArrayList<AuthProvider>();
    private I18n i18n;
    private Marker duplicate;

    @SuppressWarnings("rawtypes")
    private final Map<Class, EntityStore> storeMap = new HashMap<Class, EntityStore>();

    @Inject
    public AuthInterceptor(Config config, UserServiceAdapter userService,
        ConsumerCurator consumerCurator,
        DeletedConsumerCurator deletedConsumerCurator, Injector injector,
        I18n i18n) {
        super();
        this.consumerCurator = consumerCurator;
        this.injector = injector;
        this.config = config;
        this.userService = userService;
        this.deletedConsumerCurator = deletedConsumerCurator;
        this.i18n = i18n;
        this.duplicate = MarkerFactory.getMarker("DUPLICATE");

        createStoreMap();
        setupAuthStrategies();
    }

    /**
     * Note that this method is called during application deployment and not
     * every time a method is invoked.
     *
     * @return true if the method has an HttpMethod or HttpMethod descendant annotation.
     */
    @Override
    public boolean accept(Class declaring, Method method) {
        return new HttpMethodMatcher().matches(method);
    }

    /**
     * Set up the various providers which can be used to authenticate the user
     */
    public void setupAuthStrategies() {
        // use oauth
        if (config.oAuthEnabled()) {
            log.debug("OAuth Authentication is enabled.");
            TrustedConsumerAuth consumerAuth =
                new TrustedConsumerAuth(consumerCurator, deletedConsumerCurator, i18n);
            TrustedUserAuth userAuth = new TrustedUserAuth(userService, injector);
            TrustedExternalSystemAuth systemAuth = new TrustedExternalSystemAuth();
            providers
                .add(new OAuth(consumerAuth, userAuth, systemAuth, injector, config));
        }

        // basic http access
        if (config.basicAuthEnabled()) {
            log.debug("Basic Authentication is enabled.");
            providers.add(new BasicAuth(userService, injector));
        }
        // consumer certificates
        if (config.sslAuthEnabled()) {
            log.debug("Certificate Based Authentication is enabled.");
            providers.add(
                new SSLAuth(consumerCurator,
                    deletedConsumerCurator,
                    i18n));
        }
        // trusted headers
        if (config.trustedAuthEnabled()) {
            log.debug("Trusted Authentication is enabled.");
            providers.add(
                new TrustedConsumerAuth(consumerCurator,
                    deletedConsumerCurator,
                    i18n));
            providers.add(new TrustedUserAuth(userService, injector));
        }
    }

    public void createStoreMap() {
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
     * Sets the principal for the request and verifies that the principal
     * has access to the items in the request.
     *
     * @throws WebApplicationException when no auths result in a valid principal
     * @throws Failure when there is an unknown failure in the code
     * @return the Server Response
     */
    public ServerResponse preProcess(HttpRequest request, ResourceMethod method)
        throws Failure, WebApplicationException {

        SecurityHole securityHole = AuthUtil.checkForSecurityHoleAnnotation(
            method.getMethod());

        Principal principal = establishPrincipal(request, method, securityHole);
        try {
            verifyAccess(method.getMethod(), principal, getArguments(request, method),
                securityHole);
        }
        finally {
            /* If a turbo filter returns ACCEPT, a logger will return true for
             * isEnabled for any level.  Since we have a turbo filter that sets
             * log level on a per org basis, this block will execute if our org
             * is set to log at debug or below.
             */
            if (log.isDebugEnabled()) {
                /* If the logging filter is debug enabled, we want to mark these
                 * log statements as duplicates so we can filter them out if we
                 * want.
                 */
                Marker m =
                    (LoggerFactory.getLogger(LoggingFilter.class).isDebugEnabled()) ?
                    duplicate : null;
                try {
                    TeeHttpServletRequest teeRequest = new TeeHttpServletRequest(
                        ResteasyProviderFactory.getContextData(HttpServletRequest.class));
                    log.debug(m, "{}", ServletLogger.logBasicRequestInfo(teeRequest));
                    log.debug(m, "{}", ServletLogger.logRequest(teeRequest));
                }
                catch (IOException e) {
                    log.info("Couldn't log request information", e);
                }
            }
        }

        return null;
    }

    protected Principal establishPrincipal(HttpRequest request, ResourceMethod method,
        SecurityHole securityHole) {

        Principal principal = null;

        if (log.isDebugEnabled()) {
            log.debug("Authentication check for " + request.getUri().getPath());
        }

        // Check for anonymous calls, and let them through
        if (securityHole != null && securityHole.anon()) {
            log.debug("Request is anonymous, adding NoAuth Principal");
            principal = new NoAuthPrincipal();
        }
        else {
            // This method is not anonymous, so attempt to
            // establish the identity.
            for (AuthProvider provider : providers) {
                principal = provider.getPrincipal(request);

                if (principal != null) {
                    break;
                }
            }
        }

        // At this point, there is no provider that has given a valid principal,
        // so we use the NoAuthPrincipal here if it is set.
        if (principal == null) {
            if (securityHole != null && securityHole.noAuth()) {
                log.debug("No auth allowed for resource; setting NoAuth principal");
                principal = new NoAuthPrincipal();
            }
            else {
                throw new UnauthorizedException("Invalid credentials.");
            }
        }

        // Expose the principal for Resteasy to inject via @Context
        ResteasyProviderFactory.pushContext(Principal.class, principal);

        if (principal instanceof ConsumerPrincipal) {
            // HACK: We need to do this after the principal has been pushed,
            // lest our security settings start getting upset when we try to
            // update a consumer without any roles:
            ConsumerPrincipal p = (ConsumerPrincipal) principal;
            consumerCurator.updateLastCheckin(p.getConsumer());
        }

        return principal;
    }

    protected void verifyAccess(Method method, Principal principal, Object[] parameters,
        SecurityHole securityHole) {

        if (securityHole != null) {
            log.debug("Allowing invokation to proceed with no authentication: {}",
                method.getName());
            return;
        }

        Access defaultAccess = getAssumedAccessType(method);

        // Need to check after examining all parameters to see if we found any:
        boolean foundVerifiedParameters = false;
        Owner owner = null;

        Annotation[][] annotations = method.getParameterAnnotations();
        for (int i = 0; i < annotations.length; i++) {
            for (Annotation a : annotations[i]) {
                if (a instanceof Verify) {
                    foundVerifiedParameters = true;
                    Access requiredAccess = defaultAccess;

                    @SuppressWarnings("rawtypes")
                    Class verifyType = ((Verify) a).value();
                    if (((Verify) a).require() != Access.NONE) {
                        requiredAccess = ((Verify) a).require();
                    }
                    SubResource subResource = ((Verify) a).subResource();

                    // Use the correct curator (in storeMap) to look up the actual
                    // entity with the annotated argument
                    if (!storeMap.containsKey(verifyType)) {
                        log.error("No store configured to verify: " + verifyType);
                        throw new IseException(i18n.tr("Unable to verify request."));
                    }

                    List entities = new ArrayList();

                    Object argument = parameters[i];

                    // if the argument is null, we don't have to check anything
                    if (argument == null && ((Verify) a).nullable()) {
                        continue;
                    }
                    else if (argument == null) {
                        log.info("null argument is not allowed");
                        throw new NotFoundException(i18n.tr(
                            "{0} with id {1} could not be found.",
                            Util.getClassName(verifyType), null));
                    }
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
                            " access to collection of {}: {}", verifyType, verifyParams);
                        // If the request is for a list of items, we'll leave it
                        // up to the requester to determine if something is missing or not.
                        if (verifyParams != null && !verifyParams.isEmpty()) {
                            entities = storeMap.get(verifyType).lookup(verifyParams);
                        }
                    }

                    for (Object entity : entities) {
                        if (!principal.canAccess(entity, subResource, requiredAccess)) {
                            denyAccess(principal, method);
                        }
                        else {
                            // Access granted, grab the org key for logging purposes:
                            Owner o = storeMap.get(verifyType).getOwner((Persisted) entity);

                            if (o != null) {
                                if (owner != null && !o.equals(owner)) {
                                    log.warn("Found entities from multiple orgs in " +
                                        "one request.");
                                }
                                owner = o;
                            }
                        }
                    }
                    if (owner != null) {
                        MDC.put("org", owner.getKey());
                        if (owner.getLogLevel() != null) {
                            MDC.put("orgLogLevel", owner.getLogLevel());
                        }
                    }
                }
            }
        }

        // If we found no parameters, this method can only be called by super admins:
        if (!foundVerifiedParameters && !principal.hasFullAccess()) {
            denyAccess(principal, method);
        }
    }

    /**
     * Get an array of the parameters that will be passed by RestEasy into our
     * method.
     *
     * @param request
     * @param method
     * @return
     */
    protected Object[] getArguments(HttpRequest request, ResourceMethod method) {
        HttpResponse response =
            ResteasyProviderFactory.getContextData(HttpResponse.class);

        MethodInjector methodInjector =
            ResteasyProviderFactory.getInstance().getInjectorFactory()
            .createMethodInjector(method.getResourceClass(), method.getMethod());

        return methodInjector.injectArguments(request, response);
    }

    protected void denyAccess(Principal principal, Method method) {
        log.warn("Refusing principal: {} access to: {} ", principal,
                    method.getName());

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
    protected Access getAssumedAccessType(Method method) {
        // Assume the minimum level to start with, and bump up as we see
        // stricter annotations
        Access minimumLevel = Access.READ_ONLY;

        // If we had write or delete access types, that would go here,
        // and we'd only break on the access.all type.
        for (Annotation annotation : method.getAnnotations()) {
            if (annotation instanceof POST) {
                minimumLevel = Access.CREATE;
            }

            // May want to split out UPDATE here someday if it becomes useful.
            if (annotation instanceof PUT ||
                annotation instanceof DELETE) {
                minimumLevel = Access.ALL;
                break;
            }
            // Other annotations are GET, HEAD, and OPTIONS. assume read only for those.
        }
        return minimumLevel;
    }

    private interface EntityStore<E extends Persisted> {
        E lookup(String key);
        List<E> lookup(Collection<String> keys);
        Owner getOwner(E entity);
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

        @Override
        public Owner getOwner(Owner entity) {
            return entity;
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
            return envCurator.secureFind(key);
        }

        @Override
        public List<Environment> lookup(Collection<String> keys) {
            initialize();
            return envCurator.listAllByIds(keys);
        }

        @Override
        public Owner getOwner(Environment entity) {
            return entity.getOwner();
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

        @Override
        public Owner getOwner(Consumer entity) {
            return entity.getOwner();
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
            return entitlementCurator.secureFind(key);
        }

        @Override
        public List<Entitlement> lookup(Collection<String> keys) {
            initialize();
            return entitlementCurator.listAllByIds(keys);
        }

        @Override
        public Owner getOwner(Entitlement entity) {
            return entity.getOwner();
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
            return poolCurator.secureFind(key);
        }

        @Override
        public List<Pool> lookup(Collection<String> keys) {
            initialize();
            return poolCurator.listAllByIds(keys);
        }

        @Override
        public Owner getOwner(Pool entity) {
            return entity.getOwner();
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
            return activationKeyCurator.secureFind(key);
        }

        @Override
        public List<ActivationKey> lookup(Collection<String> keys) {
            initialize();
            return activationKeyCurator.listAllByIds(keys);
        }

        @Override
        public Owner getOwner(ActivationKey entity) {
            return entity.getOwner();
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
            return productCurator.secureFind(key);
        }

        @Override
        public List<Product> lookup(Collection<String> keys) {
            initialize();
            return productCurator.listAllByIds(keys);
        }

        @Override
        public Owner getOwner(Product entity) {
            // Products do not belong to an org:
            return null;
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

        @Override
        public Owner getOwner(User entity) {
            // Users do not (necessarily) belong to a specific org:
            return null;
        }
    }
}
