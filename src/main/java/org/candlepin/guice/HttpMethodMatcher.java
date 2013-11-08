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

import com.google.inject.matcher.AbstractMatcher;

import java.lang.reflect.Method;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;

/**
 * HttpMethodMatcher
 */
public class HttpMethodMatcher extends AbstractMatcher<Method> {
    @Override
    public boolean matches(Method t) {
        return t.isAnnotationPresent(GET.class) ||
            t.isAnnotationPresent(PUT.class) ||
            t.isAnnotationPresent(DELETE.class) ||
            t.isAnnotationPresent(POST.class) ||
            t.isAnnotationPresent(HEAD.class) ||
            t.isAnnotationPresent(OPTIONS.class) ||
            t.isAnnotationPresent(HttpMethod.class);
    }
}
