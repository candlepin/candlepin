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
import org.candlepin.resteasy.ResourceLocatorMap;
import org.candlepin.util.Util;

import com.google.inject.Inject;

import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Priority;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Priorities;
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

    @Inject
    public VerifyAuthorizationFilter(javax.inject.Provider<I18n> i18nProvider, StoreFactory storeFactory,
        ResourceLocatorMap locatorMap) {

        super(i18nProvider, storeFactory, locatorMap);
    }

    @Override
    public void runFilter(ContainerRequestContext requestContext) {

        HttpRequest request = ResteasyProviderFactory.getContextData(HttpRequest.class);
        Principal principal = (Principal) requestContext.getSecurityContext().getUserPrincipal();
        ResourceInfo resourceInfo = ResteasyProviderFactory.getContextData(ResourceInfo.class);
        Method method = resourceInfo.getResourceMethod();

        if (log.isDebugEnabled()) {
            log.debug("Authorization check for {} mapping to {}.{}",
                requestContext.getUriInfo().getPath(),
                method.getDeclaringClass().getName(),
                method.getName());
        }

        Map<Verify, Object> argMap = getArguments(request, method);

        // Couldn't find a match in Resteasy for method
        if (argMap.isEmpty()) {
            /* It would also be possible to get here if a super-admin only method
             * were inadvertently being filtered through this filter.  Normally the
             * AuthorizationFeature takes care of sending methods without any @Verify
             * annotations through the SuperAdminAuthorizationFilter */
            throw new IseException("Could not get parameters for " + method);
        }

        Access defaultAccess = getDefaultAccess(method);
        Owner owner = ResteasyProviderFactory.getContextData(Owner.class);

        if (!hasAccess(argMap, principal, owner, defaultAccess)) {
            denyAccess(principal, method);
        }
    }

    protected boolean hasAccess(Map<Verify, Object> argMap, Principal principal,
        Owner owner, Access defaultAccess) {
        boolean hasAccess = false;

        // Impl note:
        // This needs a complete rehash if/when we do object sharing

        for (Map.Entry<Verify, Object> entry : argMap.entrySet()) {
            List<Persisted> accessedObjects = new ArrayList<Persisted>();
            Object obj = entry.getValue();
            Verify verify = entry.getKey();
            Class<?> verifyType = verify.value();

            accessedObjects.addAll(getAccessedEntities(verify, obj, owner));

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

                // Is this even necessary? What does it accomplish?
                // if (entity instanceof SharedEntity) {
                //     if (owner == null) {
                //         // No context owner to verify access to a shared entity.
                //         hasAccess = false;
                //         break;
                //     }

                //     Collection<Owner> owners = ((SharedEntity) entity).getOwners();
                //     if (owners == null || !owners.contains(owner)) {
                //         // Entity is not yet owned by any owners or not owned by the contextual
                //         // owner
                //         hasAccess = false;
                //         break;
                //     }
                // }

                hasAccess = true;
            }

            // Stop all further checking with any authorization failure
            if (!hasAccess) {
                break;
            }
        }

        return hasAccess;
    }

    @SuppressWarnings("unchecked")
    protected List<Persisted> getAccessedEntities(Verify verify, Object requestValue, Owner owner) {
        // Nothing to access!
        if (verify.nullable() && null == requestValue) {
            return Collections.emptyList();
        }

        List<Persisted> entities = new ArrayList<Persisted>();
        Class<?> verifyType = verify.value();

        if (requestValue instanceof String) {
            String verifyParam = (String) requestValue;
            Persisted entity = null;

            entity = storeFactory.getFor(verifyType).lookup(verifyParam, owner);

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
        else {
            Collection<String> verifyParams = (Collection<String>) requestValue;

            // If the request is for a list of items, we'll leave it
            // up to the requester to determine if something is missing or not.
            if (verifyParams != null && !verifyParams.isEmpty()) {
                entities.addAll(storeFactory.getFor(verifyType).lookup(verifyParams, owner));
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
