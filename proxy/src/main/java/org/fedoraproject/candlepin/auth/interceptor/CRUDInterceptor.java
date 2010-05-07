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

import java.util.Arrays;
import java.util.List;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.fedoraproject.candlepin.auth.ConsumerPrincipal;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.exceptions.ForbiddenException;
import org.fedoraproject.candlepin.model.AccessControlEnforced;

import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * CRUDInterceptor
 */
public class CRUDInterceptor implements MethodInterceptor {
    
    @Inject private Provider<Principal> principalProvider;
    
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        
        Principal currentUser = this.principalProvider.get();
        Object entity = invocation.getArguments()[0];
        Role role = currentUser.getRoles().get(0);
        
        List<Class<?>> interfaces = Arrays.asList(entity.getClass().getInterfaces());
        if (!interfaces.contains(AccessControlEnforced.class)) {
            return invocation.proceed();
        }

        if (Role.CONSUMER == role) {
            ConsumerPrincipal consumer = (ConsumerPrincipal) currentUser;
            if (!((AccessControlEnforced) entity).shouldGrantAcessTo(consumer.consumer())) {
                throw new ForbiddenException("access denied.");
            }
        }
        else if (Role.OWNER_ADMIN == role) {
            if (!((AccessControlEnforced) entity)
                .shouldGrantAcessTo(currentUser.getOwner())) {
                throw new ForbiddenException("access denied.");
            }
        }
        else {
            throw new ForbiddenException("access denied.");
        }
            
        return invocation.proceed();
    }
}
