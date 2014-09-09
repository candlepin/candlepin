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
package org.candlepin.guice;

import com.google.inject.Inject;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * This method intercepts method calls, and hooks in the JPA local
 * transaction interceptor to ensure we use a transaction per request.
 */
public class CandlepinResourceTxnInterceptor implements MethodInterceptor {

    @Inject
    private TransactionalInvoker invoker;

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        return invoker.invoke(invocation);
    }
}
