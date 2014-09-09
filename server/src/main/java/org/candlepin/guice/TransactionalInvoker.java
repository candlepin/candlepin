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
import com.google.inject.persist.Transactional;

import org.aopalliance.intercept.MethodInvocation;

/**
 * Invoker used to automatically open a transaction on any methods which match the
 * criteria defined in {@link CandlepinModule}. (i.e. any REST api methods)
 */
public class TransactionalInvoker {

    @Inject
    public TransactionalInvoker() {
    }

    @Transactional
    public Object invoke(MethodInvocation invocation) throws Throwable {
        return invocation.proceed();
    }
}
