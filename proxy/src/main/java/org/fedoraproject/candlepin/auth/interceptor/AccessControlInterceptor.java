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
import org.fedoraproject.candlepin.model.Owner;

import com.google.inject.Inject;
import com.google.inject.Provider;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.log4j.Logger;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

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
        Role role = currentUser.getRoles().get(0);
        
        if (Role.OWNER_ADMIN == role) { 
            enableOwnerFilter(currentUser, invocation.getThis(), role);
        } 
        else if (Role.CONSUMER == role) {
            enableConsumerFilter(currentUser, invocation.getThis(), role);
        }
    }

    private void crudAccessControl(Object entity) {
        Principal currentUser = this.principalProvider.get();
        Role role = currentUser.getRoles().get(0);

        // Only available on entities that implement AccessControlEnforced interface
        if (currentUser.getRoles().contains(Role.SUPER_ADMIN)) {
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
            if (!((AccessControlEnforced) entity)
                .shouldGrantAccessTo(currentUser.getOwner())) {
                log.warn("Denying: " + currentUser + " access to: " + entity);
                throw new ForbiddenException("access denied.");
            }
        }
        else {
            log.warn("Denying: " + currentUser + " access to: " + entity);
            throw new ForbiddenException("access denied.");
        }
    }
    
    private void enableConsumerFilter(Principal currentUser, Object target, Role role) {
        AbstractHibernateCurator curator = (AbstractHibernateCurator) target;
        ConsumerPrincipal user = (ConsumerPrincipal) currentUser;
        
        String filterName = filterName(curator.entityType(), role); 
        curator.enableFilter(filterName, "consumer_id", user.consumer().getId());
    }

    // TODO: Address this once User -> Owner relationship is N-M
    private List<String> convertOwner(Owner owner) {
        List<String> ownerIds = new LinkedList<String>();
        ownerIds.add(owner.getId());
        return ownerIds;
    }

    private void enableOwnerFilter(Principal currentUser, Object target, Role role) {
        AbstractHibernateCurator curator = (AbstractHibernateCurator) target;
        UserPrincipal user = (UserPrincipal) currentUser;

        String filterName = filterName(curator.entityType(), role); 
        curator.enableFilterList(filterName, "owner_ids", convertOwner(user.getOwner()));
    }
    
    private String filterName(Class<?> entity, Role role) {
        return entity.getSimpleName() +
            (role == Role.CONSUMER ? "_CONSUMER_FILTER" : "_OWNER_FILTER");
    }
}
