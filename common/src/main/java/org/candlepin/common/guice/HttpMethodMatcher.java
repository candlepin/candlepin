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
package org.candlepin.common.guice;

import com.google.inject.matcher.AbstractMatcher;

import org.jboss.resteasy.util.IsHttpMethod;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * This class is used to see whether or not we should apply our security
 * Intercepter to a method.  In actuality, this class is a bit of a no-op
 * in the real application because RestEasy uses IsHttpMethod to pick up the
 * ResourceMethods that it will handle.  However, we need this matcher for testing
 * because we test with a AOP method intercepter which we don't want to apply
 * to just plain public/protected methods.  Also note that this matcher runs
 * on application deployment and not on every request.
 */
public class HttpMethodMatcher extends AbstractMatcher<Method> {
    @Override
    public boolean matches(Method m) {
        Set<String> verbs = IsHttpMethod.getHttpMethods(m);
        return verbs != null && !verbs.isEmpty() && !m.isSynthetic();
    }
}
