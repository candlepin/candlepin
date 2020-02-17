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

import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.auth.SubResource;
import org.candlepin.auth.Verify;
import org.candlepin.common.exceptions.IseException;
import org.candlepin.common.exceptions.NotFoundException;
import org.candlepin.model.Owner;
import org.candlepin.model.Persisted;
import org.candlepin.resteasy.AnnotationLocator;
import org.candlepin.util.Util;

import com.google.inject.Inject;

import org.jboss.resteasy.core.ResteasyContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.xnap.commons.i18n.I18n;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Priority;
import javax.ws.rs.DELETE;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.PathParam;
import javax.ws.rs.Priorities;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;



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
    public VerifyAuthorizationFilter(javax.inject.Provider<I18n> i18nProvider, StoreFactory storeFactory,
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

        Map<Verify, Object> argMap = getArguments(requestContext, method);

        // Couldn't find a match in Resteasy for method
        if (argMap.isEmpty()) {
            /* It would also be possible to get here if a super-admin only method
             * were inadvertently being filtered through this filter.  Normally the
             * AuthorizationFeature takes care of sending methods without any @Verify
             * annotations through the SuperAdminAuthorizationFilter */
            throw new IseException("Could not get parameters for " + method);
        }

        Access defaultAccess = getDefaultAccess(method);

        if (!hasAccess(argMap, principal, defaultAccess)) {
            denyAccess(principal, method);
        }
    }

    /**
     * Fetches the arguments for parameters flagged with the Verify annotation from the request
     * context. If the method does not have any Verify-annotated parameters, this method returns
     * an empty map.
     *
     * @param requestContext
     *  the context data for the request
     *
     * @param method
     *  the method handling the request
     *
     * @return
     *  a mapping of verify annotations to their respective arguments
     */
    protected Map<Verify, Object> getArguments(ContainerRequestContext requestContext, Method method) {

        // LinkedHashMap preserves insertion order
        Map<Verify, Object> argMap = new LinkedHashMap<>();

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

                argMap.put(verify, value);
            }
        }

        return argMap;
    }

    protected boolean hasAccess(Map<Verify, Object> argMap, Principal principal, Access defaultAccess) {
        boolean hasAccess = false;
        Owner owner = null;

        for (Map.Entry<Verify, Object> entry : argMap.entrySet()) {
            List<Persisted> accessedObjects = new ArrayList<>();
            Object obj = entry.getValue();
            Verify verify = entry.getKey();
            Class<? extends Persisted> verifyType = verify.value();

            accessedObjects.addAll(getAccessedEntities(verify, obj));

            Access requiredAccess = defaultAccess;
            if (verify.require() != Access.NONE) {
                requiredAccess = verify.require();
            }

            log.debug("Verifying {} access to {}: {}", requiredAccess, verifyType, obj);

            SubResource subResource = verify.subResource();
            for (Persisted entity : accessedObjects) {
                if (!principal.canAccess(entity, subResource, requiredAccess)) {
                    hasAccess = false;
                    break;
                }

                hasAccess = true;

                Owner entityOwner = ((EntityStore) storeFactory.getFor(verifyType)).getOwner(entity);
                if (entityOwner != null) {
                    if (owner != null && !owner.equals(entityOwner)) {
                        log.error("Found entities from multiple orgs in a single request");
                        throw new IseException("Found entities from multiple orgs in a single request");
                    }

                    owner = entityOwner;
                }
            }

            // Stop all further checking with any authorization failure
            if (!hasAccess) {
                break;
            }
        }

        if (hasAccess && owner != null) {
            MDC.put("org", owner.getKey());

            if (owner.getLogLevel() != null) {
                MDC.put("orgLogLevel", owner.getLogLevel());
            }
        }

        return hasAccess;
    }

    @SuppressWarnings("unchecked")
    protected List<Persisted> getAccessedEntities(Verify verify, Object requestValue) {
        // Nothing to access!
        if (verify.nullable() && null == requestValue) {
            return Collections.emptyList();
        }

        List<Persisted> entities = new ArrayList<>();
        Class<? extends Persisted> verifyType = verify.value();

        if (requestValue instanceof String) {
            String verifyParam = (String) requestValue;
            Persisted entity = null;

            entity = storeFactory.getFor(verifyType).lookup(verifyParam);

            // If the request is just for a single item, throw an exception
            // if it is not found.
            if (entity == null) {
                // This is bad, we're verifying a parameter with an ID which
                // doesn't seem to exist in the DB. Error will be thrown in
                // invoke though.
                String typeName = Util.getClassName(verifyType);
                if (typeName.equals("Owner")) {
                    typeName = i18nProvider.get().tr("Organization");
                }
                String msg = i18nProvider.get().tr("{0} with id {1} could not be found.",
                    typeName, verifyParam);
                log.info("No such entity: {}, id: {}", typeName, verifyParam);
                throw new NotFoundException(msg);
            }

            entities.add(entity);
        }
        else if (requestValue instanceof Collection) {
            Collection<String> verifyParams = (Collection<String>) requestValue;

            // If the request is for a list of items, we'll leave it
            // up to the requester to determine if something is missing or not.
            if (verifyParams != null && !verifyParams.isEmpty()) {
                entities.addAll(storeFactory.getFor(verifyType).lookup(verifyParams));
            }
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
}
