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

import java.lang.reflect.Method;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.fedoraproject.candlepin.auth.ConsumerPrincipal;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.exceptions.ForbiddenException;

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
        
        AccessControlSecured secured = 
            entity.getClass().getAnnotation(AccessControlSecured.class);
        
        if (secured == null || !(currentUser instanceof ConsumerPrincipal)) {
            return invocation.proceed();
        }
        
        ConsumerPrincipal consumer = (ConsumerPrincipal) currentUser;
        
        String validationPath = secured.path();
        Class<?> entityClass = entity.getClass();
        Method identity = entityClass.getDeclaredMethod("getUuid");
        String uuid = identity.invoke(entity).toString();
        
        if (!consumer.consumer().getUuid().equals(uuid)) {
            throw new ForbiddenException("access denied.");
        }
        
        return invocation.proceed();
    }
}
