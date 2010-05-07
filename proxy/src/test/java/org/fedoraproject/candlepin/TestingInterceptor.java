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
package org.fedoraproject.candlepin;

import java.util.concurrent.atomic.AtomicBoolean;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;


/**
 * TestingInterceptor
 */
public class TestingInterceptor implements MethodInterceptor {
    
    private final MethodInterceptor wrapped;
    private AtomicBoolean enabled = new AtomicBoolean(false);
    
    public TestingInterceptor(final MethodInterceptor wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        if (!enabled.get()) {
            return invocation.proceed();
        }
        return wrapped.invoke(invocation);
    }
    
    public void enable() {
        enabled.getAndSet(true);
    }
    
    public void disable() {
        enabled.getAndSet(false);
    }
}
