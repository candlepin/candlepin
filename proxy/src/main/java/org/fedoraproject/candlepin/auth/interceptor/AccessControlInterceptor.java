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
import org.fedoraproject.candlepin.auth.Access;
import org.fedoraproject.candlepin.auth.UserPrincipal;
import org.fedoraproject.candlepin.exceptions.ForbiddenException;
import org.fedoraproject.candlepin.model.AbstractHibernateCurator;

import com.google.inject.Inject;
import com.google.inject.Provider;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.log4j.Logger;


/**
 * AccessControlInterceptor
 */
public class AccessControlInterceptor implements MethodInterceptor {
    
    private static Logger log = Logger.getLogger(AccessControlInterceptor.class);

    @Inject private Provider<Principal> principalProvider;
    
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {

        // bypass anyone with full access
        if (principalProvider.get().hasFullAccess()) {
            return invocation.proceed();
        }
        
        String invokedMethodName = invocation.getMethod().getName();
        if (invokedMethodName.startsWith("list")) {
            Object entity = ((AbstractHibernateCurator) invocation.getThis()).entityType();
            if (entity == null) {
                return invocation.proceed();
            }
            listFilter(invocation);
        }
        else if (invokedMethodName.startsWith("find")) {
            Object toReturn = invocation.proceed();
            if (toReturn != null) {
                crudAccessControl(toReturn);
            }
            return toReturn;
        }
        else {
            Object entity = invocation.getArguments()[0];
            if (entity == null) {
                return invocation.proceed();
            }
            crudAccessControl(entity);
        }
            
        return invocation.proceed();
    }

    private void listFilter(MethodInvocation invocation) {
        Principal currentUser = this.principalProvider.get();
        // Either way this is a little hacky - either check type by using
        // instanceof or getType - there is a better OO way to go about this!

        if (currentUser instanceof UserPrincipal) {
            enableOwnerFilter((UserPrincipal) currentUser, invocation.getThis());
        } 
        else if (currentUser instanceof ConsumerPrincipal) {
            enableConsumerFilter((ConsumerPrincipal) currentUser, invocation.getThis());
        }
    }

    private void crudAccessControl(Object entity) {
        Principal currentUser = this.principalProvider.get();

        // TODO:  Here we need to figure out how to get the the Access mode.
        if (!currentUser.canAccess(entity, Access.ALL)) {
            throw new ForbiddenException("access denied.");
        }
    }
    
    private void enableConsumerFilter(ConsumerPrincipal currentUser, Object target) {
        AbstractHibernateCurator curator = (AbstractHibernateCurator) target;
        
        String filterName = curator.entityType().getSimpleName() + "_CONSUMER_FILTER";
        curator.enableFilter(filterName, "consumer_id", currentUser.getConsumer().getId());
    }

    private void enableOwnerFilter(UserPrincipal currentUser, Object target) {
        AbstractHibernateCurator curator = (AbstractHibernateCurator) target;

        String filterName = curator.entityType().getSimpleName() + "_OWNER_FILTER";
        curator.enableFilterList(filterName, "owner_ids", currentUser.getOwnerIds());
    }

}
