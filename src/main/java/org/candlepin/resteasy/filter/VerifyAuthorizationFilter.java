/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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

import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.auth.SubResource;
import org.candlepin.auth.Verify;
import org.candlepin.exceptions.IseException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.logging.LoggingUtil;
import org.candlepin.model.Owner;
import org.candlepin.model.Persisted;
import org.candlepin.resteasy.AnnotationLocator;
import org.candlepin.util.Util;

import org.jboss.resteasy.core.ResteasyContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.xnap.commons.i18n.I18n;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ResourceInfo;



/**
 * VerifyAuthorizationFilter is responsible for determining whether or not
 * the principal has access to the called method.  Note there is no Provider annotation on
 * this class.  That is because the AuthorizationFeature takes care of registering this filter
 * to the appropriate methods at servlet initialization time.
 */
@Priority(Priorities.AUTHORIZATION)
public class VerifyAuthorizationFilter extends AbstractAuthorizationFilter {
    private static final Logger log = LoggerFactory.getLogger(VerifyAuthorizationFilter.class);

    private StoreFactory storeFactory;
    private AnnotationLocator annotationLocator;

    @Inject
    public VerifyAuthorizationFilter(jakarta.inject.Provider<I18n> i18nProvider, StoreFactory storeFactory,
        AnnotationLocator annotationLocator) {

        this.i18nProvider = i18nProvider;
        this.storeFactory = storeFactory;
        this.annotationLocator = annotationLocator;
    }

    @Override
    public void runFilter(ContainerRequestContext requestContext) {
        Principal principal = (Principal) requestContext.getSecurityContext().getUserPrincipal();
        ResourceInfo resourceInfo = ResteasyContext.getContextData(ResourceInfo.class);
        Method method = resourceInfo.getResourceMethod();

        if (log.isDebugEnabled()) {
            log.debug("Authorization check for {} mapping to {}.{}",
                requestContext.getUriInfo().getPath(),
                method.getDeclaringClass().getName(),
                method.getName());
        }

        List<VerifiedArgument> arguments = getArguments(requestContext, method);

        // Couldn't find a match in Resteasy for method
        if (arguments.isEmpty()) {
            /* It would also be possible to get here if a super-admin only method
             * were inadvertently being filtered through this filter.  Normally the
             * AuthorizationFeature takes care of sending methods without any @Verify
             * annotations through the SuperAdminAuthorizationFilter */
            throw new IseException("Could not get parameters for " + method);
        }

        Access defaultAccess = getDefaultAccess(method);

        if (!hasAccess(arguments, principal, defaultAccess)) {
            denyAccess(principal, method);
        }
    }

    /**
     * Fetches the arguments for parameters flagged with the Verify annotation from the request
     * context. If the method does not have any Verify-annotated parameters, this method returns
     * an empty list.
     * <p></p>
     * The list returned preserves order of annotations/arguments as they appear on a resource.
     *
     * @param requestContext
     *  the context data for the request
     *
     * @param method
     *  the method handling the request
     *
     * @return
     *  the verified arguments in resource method parameter order
     */
    protected List<VerifiedArgument> getArguments(ContainerRequestContext requestContext, Method method) {
        // Impl. note: @Verify annotations with identical values could occur on multiple parameters.
        // We replaced the LinkedHashMap<Verify, Object> with an ArrayList<VerifiedArgument> to avoid
        // identical @Verify annotations overwriting each other, but at the same preserving the insertion
        // ordering that the LinkedHashMap provided before.
        //
        // While no such API url case exists in Candlepin today, this is guarding future cases, such as this
        // example: 'POST /candlepin/consumers/{consumerA}/copy/{consumerB}' where two
        // @Verify(Consumer.class) annotations are set on the endpoint (one for each consumer).
        List<VerifiedArgument> arguments = new ArrayList<>();

        Annotation[][] annotations = annotationLocator.getParameterAnnotations(method);

        Map<String, List<String>> pathParams = null;
        Map<String, List<String>> queryParams = null;
        Map<String, List<String>> headers = null;

        for (int i = 0; i < annotations.length; ++i) {
            Verify verify = null;
            Object value = null;

            for (Annotation annotation : annotations[i]) {
                if (annotation instanceof Verify) {
                    verify = (Verify) annotation;
                }
                else {
                    List<String> values = null;

                    if (annotation instanceof PathParam) {
                        if (pathParams == null) {
                            pathParams = requestContext.getUriInfo().getPathParameters(true);
                        }

                        values = pathParams.get(((PathParam) annotation).value());
                    }
                    else if (annotation instanceof QueryParam) {
                        if (queryParams == null) {
                            queryParams = requestContext.getUriInfo().getQueryParameters(true);
                        }

                        values = queryParams.get(((QueryParam) annotation).value());
                    }
                    else if (annotation instanceof HeaderParam) {
                        if (headers == null) {
                            headers = requestContext.getHeaders();
                        }

                        values = headers.get(((HeaderParam) annotation).value());
                    }

                    // This is technically incorrect, as we should be returning either the collection
                    // or individual value based on the parameter typing. However, this works for the
                    // purposes of performing the verification task.
                    if (values != null) {
                        value = values.size() > 1 ? values : values.get(0);
                    }
                }
            }

            if (verify != null) {
                if (!verify.nullable() && value == null) {
                    throw new IllegalStateException("Null passed to a non-nullable Verify annotation.");
                }

                arguments.add(new VerifiedArgument(verify, value));
            }
        }

        return arguments;
    }

    /**
     * Determines if the provided principal is allowed to access the resource method, based on whether
     * the principal has the correct permissions to access all the entities present in all @Verify
     * annotations.
     * <p><b>
     * IMPORTANT: If the principal is forbidden access to AT LEAST ONE entity, then this method fails closed
     * immediately, and the principal is denied access.
     * </b></p>
     * There is also a special case of this method returning false when no entities were specified in the
     * request and the @Verify annotation(s) were marked as nullable.
     *
     * @param arguments the verified arguments in resource method parameter order
     * @param principal the principal making the request
     * @param defaultAccess the default access mode for this resource method (e.g. read only, create, all).
     *      This can be overridden by the resource method itself
     * @return true if the principal is allowed to access the resource, or false otherwise
     */
    protected boolean hasAccess(List<VerifiedArgument> arguments, Principal principal,
        Access defaultAccess) {

        boolean anyEntityVerified = false;
        Owner owner = null;

        for (VerifiedArgument argument : arguments) {
            List<Persisted> accessedObjects = new ArrayList<>();
            Object obj = argument.value();
            Verify verify = argument.annotation();
            Class<? extends Persisted>[] verifyTypes = verify.value();

            accessedObjects.addAll(getAccessedEntities(verify, obj));

            Access requiredAccess = defaultAccess;
            if (verify.require() != Access.NONE) {
                requiredAccess = verify.require();
            }

            log.debug("Verifying {} access to {}: {}", requiredAccess, verifyTypes, obj);

            SubResource subResource = verify.subResource();
            for (Persisted entity : accessedObjects) {
                if (!principal.canAccess(entity, subResource, requiredAccess)) {
                    // Stop all further checking with any authorization failure
                    return false;
                }

                anyEntityVerified = true;

                if (!storeFactory.canValidate(entity.getClass())) {
                    continue;
                }

                Owner entityOwner = ((EntityStore) storeFactory.getFor(entity.getClass())).getOwner(entity);
                if (entityOwner != null) {
                    if (owner != null && !owner.equals(entityOwner)) {
                        log.error("Found entities from multiple orgs in a single request");
                        throw new IseException("Found entities from multiple orgs in a single request");
                    }

                    owner = entityOwner;
                }
            }
        }

        if (owner != null) {
            MDC.put(LoggingUtil.MDC_OWNER_KEY, owner.getKey());

            if (owner.getLogLevel() != null) {
                MDC.put(LoggingUtil.MDC_LOG_LEVEL_KEY, owner.getLogLevel());
            }
        }

        // At this point, if this returns false, no @Verify parameter resolved to an entity (all were
        // nullable and absent), so there is nothing to authorize against.
        return anyEntityVerified;
    }

    protected record VerifiedArgument(Verify annotation, Object value) {
    }

    protected VerifiedArgument createVerifiedArgument(Verify annotation, Object value) {
        return new VerifiedArgument(annotation, value);
    }

    @SuppressWarnings("unchecked")
    protected List<Persisted> getAccessedEntities(Verify verify, Object requestValue) {
        // Nothing to access!
        if (verify.nullable() && null == requestValue) {
            return Collections.emptyList();
        }

        List<Persisted> entities = new ArrayList<>();
        Class<? extends Persisted>[] verifyTypes = verify.value();

        for (Class<? extends Persisted> verifyType : verifyTypes) {
            if (requestValue instanceof String verifyParam) {
                Persisted entity = storeFactory.getFor(verifyType).lookup(verifyParam);
                if (entity == null) {
                    String typeName = Util.getClassName(verifyType);
                    if (typeName.equals("Owner")) {
                        typeName = i18nProvider.get().tr("Organization");
                    }

                    log.info("No such entity: {}, id: {}", typeName, verifyParam);
                    continue;
                }

                entities.add(entity);
            }
            else if (requestValue instanceof Collection) {
                Collection<String> verifyParams = (Collection<String>) requestValue;

                // If the request is for a list of items, we'll leave it
                // up to the requester to determine if something is missing or not.
                if (!verifyParams.isEmpty()) {
                    entities.addAll(storeFactory.getFor(verifyType).lookup(verifyParams));
                }
            }
        }

        if (entities.isEmpty()) {
            String msg = i18nProvider.get().tr("{0} with ID(s) {1} could not be found.",
                verifyTypesToString(verifyTypes), requestValue);
            throw new NotFoundException(msg);
        }

        return entities;
    }

    protected Access getDefaultAccess(Method method) {
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

    String verifyTypesToString(Class<? extends Persisted>[] verifyTypes) {
        if (verifyTypes == null) {
            return "";
        }
        List<String> asList = Arrays.stream(verifyTypes)
            .map(Util::getClassName)
            .toList();
        if (asList.size() == 1) {
            return asList.get(0);
        }
        else {
            return asList.toString();
        }
    }
}
