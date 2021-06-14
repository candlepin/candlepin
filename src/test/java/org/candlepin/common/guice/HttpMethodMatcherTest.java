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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;

/**
 * HttpMethodMatcherTest
 */
public class HttpMethodMatcherTest {
    interface RestApiInterface {
        @POST
        void post();

        @GET
        void get();

        @PUT
        void put();

        @DELETE
        void delete();

        @HEAD
        void head();

        @OPTIONS
        void options();
    }

    public static class TestResource implements RestApiInterface {
        @Override
        public void post() {

        }

        @Override
        public void get() {

        }

        @Override
        public void put() {

        }

        @Override
        public void delete() {

        }

        @Override
        public void head() {

        }

        @Override
        public void options() {

        }

        public void foo() {

        }
    }

    @Test
    public void testMatches() {
        HttpMethodMatcher matcher = new HttpMethodMatcher();
        List<Method> classDeclaredMethods = Arrays.asList(TestResource.class.getDeclaredMethods());
        List<String> methodNames = Arrays.asList(RestApiInterface.class.getDeclaredMethods())
            .stream().map(Method::getName).collect(Collectors.toList());

        for (Method m : classDeclaredMethods) {
            if (methodNames.contains(m.getName())) {
                assertTrue(matcher.matches(m));
            }
            else {
                assertFalse(matcher.matches(m));
            }
        }
    }
}
