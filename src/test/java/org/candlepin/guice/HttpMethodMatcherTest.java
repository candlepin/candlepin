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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Method;

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

    public static class TestResource {
        @POST
        public void post() {

        }

        @GET
        public void get() {

        }

        @PUT
        public void put() {

        }

        @DELETE
        public void delete() {

        }

        @HEAD
        public void head() {

        }

        @OPTIONS
        public void options() {

        }

        public void foo() {

        }
    }

    @Test
    public void testMatches() {
        HttpMethodMatcher matcher = new HttpMethodMatcher();
        Method[] methods = TestResource.class.getMethods();

        for (int i = 0; i < methods.length; i++) {
            if (methods[i].getAnnotations().length != 0) {
                assertTrue(matcher.matches(methods[i]));
            }
            else {
                assertFalse(matcher.matches(methods[i]));
            }
        }
    }
}
