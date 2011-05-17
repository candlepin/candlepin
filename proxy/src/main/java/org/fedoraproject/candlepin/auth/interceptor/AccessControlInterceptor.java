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

import org.fedoraproject.candlepin.auth.ConsumerPrincipal;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.auth.UserPrincipal;
import org.fedoraproject.candlepin.exceptions.ForbiddenException;
import org.fedoraproject.candlepin.model.AbstractHibernateCurator;
import org.fedoraproject.candlepin.model.AccessControlEnforced;

import com.google.inject.Inject;
import com.google.inject.Provider;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.log4j.Logger;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Permission;

/**
 * AccessControlInterceptor
 */
public class AccessControlInterceptor implements MethodInterceptor {
    
    private Logger log = Logger.getLogger(AccessControlInterceptor.class);

    @Inject private Provider<Principal> principalProvider;
    
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        
        String invokedMethodName = invocation.getMethod().getName();
        if (invokedMethodName.startsWith("list")) {
            Object entity = ((AbstractHibernateCurator) invocation.getThis()).entityType();
            if (!isAccessControlled((Class) entity)) {
                return invocation.proceed();
            }
            listFilter(invocation);
        }
        else if (invokedMethodName.startsWith("find")) {
            Object toReturn = invocation.proceed();
            if ((toReturn != null) && isAccessControlled(toReturn.getClass())) {
                crudAccessControl(toReturn);
            }
            return toReturn;
        }
        else {
            Object entity = invocation.getArguments()[0];
            if ((entity == null) || !isAccessControlled(entity.getClass())) {
                return invocation.proceed();
            }
            crudAccessControl(entity);
        }
            
        return invocation.proceed();
    }

    private boolean isAccessControlled(Class clazz) {
        return Arrays.asList(clazz.getInterfaces())
            .contains(AccessControlEnforced.class);
    }

    private void listFilter(MethodInvocation invocation) {
        Principal currentUser = this.principalProvider.get();
        // TODO:  This was already checking only the first role on the principal,
        // which seems bad - this is basically doing this same thing...
        Role role = currentUser.getPermissions().get(0).getRoles().iterator().next();
        
        if (Role.OWNER_ADMIN == role) { 
            enableOwnerFilter(currentUser, invocation.getThis(), role);
        } 
        else if (Role.CONSUMER == role) {
            enableConsumerFilter(currentUser, invocation.getThis(), role);
        }
    }

    private void crudAccessControl(Object entity) {
        Principal currentUser = this.principalProvider.get();
        // TODO:  This was already checking only the first role on the principal,
        // which seems bad - this is basically doing this same thing...
        Role role = currentUser.getPermissions().get(0).getRoles().iterator().next();

        // Only available on entities that implement AccessControlEnforced interface
        if (currentUser.isSuperAdmin()) {
            return;
        }
        else if (Role.CONSUMER == role) {
            ConsumerPrincipal consumer = (ConsumerPrincipal) currentUser;
            if (!((AccessControlEnforced) entity).shouldGrantAccessTo(
                consumer.consumer())) {
                log.warn("Denying: " + currentUser + " access to: " + entity);
                throw new ForbiddenException("access denied.");
            }
        }
        else if (Role.OWNER_ADMIN == role) {
            if (!hasAccessTo(currentUser, (AccessControlEnforced) entity)) {
                log.warn("Denying: " + currentUser + " access to: " + entity);
                throw new ForbiddenException("access denied.");
            }
        }
        else {
            log.warn("Denying: " + currentUser + " access to: " + entity);
            throw new ForbiddenException("access denied.");
        }
    }

    // Grant access if ANY of the principal's owners has permission to see the entity
    // TODO:  This will need to be changed for checking specific permissions!
    private boolean hasAccessTo(Principal principal, AccessControlEnforced entity) {
        for (Permission permission : principal.getPermissions()) {
            if (entity.shouldGrantAccessTo(permission.getOwner())) {
                return true;
            }
        }

        return false;
    }
    
    private void enableConsumerFilter(Principal currentUser, Object target, Role role) {
        AbstractHibernateCurator curator = (AbstractHibernateCurator) target;
        ConsumerPrincipal user = (ConsumerPrincipal) currentUser;
        
        String filterName = filterName(curator.entityType(), role); 
        curator.enableFilter(filterName, "consumer_id", user.consumer().getId());
    }

    // Gets the owner ids for a user principal
    private List<String> getOwnerIds(UserPrincipal principal) {
        List<String> ownerIds = new LinkedList<String>();

        for (Permission permission : principal.getPermissions()) {
            ownerIds.add(permission.getOwner().getId());
        }

        return ownerIds;
    }

    private void enableOwnerFilter(Principal currentUser, Object target, Role role) {
        AbstractHibernateCurator curator = (AbstractHibernateCurator) target;
        UserPrincipal user = (UserPrincipal) currentUser;

        String filterName = filterName(curator.entityType(), role); 
        curator.enableFilterList(filterName, "owner_ids", getOwnerIds(user));
    }
    
    private String filterName(Class<?> entity, Role role) {
        return entity.getSimpleName() +
            (role == Role.CONSUMER ? "_CONSUMER_FILTER" : "_OWNER_FILTER");
    }
}
