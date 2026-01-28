/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.candlepin.config.ConfigProperties;
import org.candlepin.config.DevConfig;
import org.candlepin.config.TestConfig;
import org.candlepin.dto.api.server.v1.Link;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import javax.ws.rs.Path;


/**
 * RootResourceTest
 */
public class RootResourceTest {

    @Path("/foo")
    public static class FooResource {

        @Path("/slash")
        public void methodWithSlash() {
        }

        @Path("noslash")
        public void methodWithoutSlash() {
        }

        @Path("trailingslash/")
        public void methodWithTrailingSlash() {
        }

        @Path("/linked")
        @RootResource.LinkedResource
        public void methodWithLinkedAnnotation() {
        }
    }

    private RootResource rootResource;

    @BeforeEach
    public void setUp() {
        rootResource = new RootResource(TestConfig.defaults());
    }

    @Test
    public void getMethodEntryWithSlash() throws NoSuchMethodException {
        Method m = FooResource.class.getMethod("methodWithSlash");
        Link result = rootResource.methodLink("hello_world", m);
        assertEquals("hello_world", result.getRel());
        assertEquals("/foo/slash", result.getHref());
    }

    @Test
    public void getMethodEntryWithOutSlash() throws NoSuchMethodException {
        Method m = FooResource.class.getMethod("methodWithoutSlash");
        Link result = rootResource.methodLink("hello_world", m);
        assertEquals("hello_world", result.getRel());
        assertEquals("/foo/noslash", result.getHref());
    }

    @Test
    public void getMethodEntryWithTrailingSlash() throws NoSuchMethodException {
        Method m = FooResource.class.getMethod("methodWithTrailingSlash");
        Link result = rootResource.methodLink("hello_world", m);
        assertEquals("hello_world", result.getRel());
        assertEquals("/foo/trailingslash", result.getHref());
    }

    @Test
    public void getResourceEntry() {
        Link result = rootResource.resourceLink(FooResource.class, null);
        assertEquals("foo", result.getRel());
        assertEquals("/foo", result.getHref());
    }

    @Test void testLinkedAnnotation() throws NoSuchMethodException {
        Method m = FooResource.class.getMethod("methodWithLinkedAnnotation");
        Link result = rootResource.methodLink("annotated_method", m);
        assertEquals("annotated_method", result.getRel());
        assertEquals("/foo/linked", result.getHref());
    }

    @Test
    public void testIncludePackagesLinkWhenCombinedReportingEnabled() {
        DevConfig config = TestConfig.defaults();
        config.setProperty(ConfigProperties.COMBINED_REPORTING_ENABLED, "true");

        RootResource resource = new RootResource(config);
        assertThat(resource.createLinks())
            .isNotNull()
            .map(Link::getRel)
            .contains("packages");
    }

    @Test
    public void testExcludePackagesLinkWhenCombinedReportingDisabled() {
        DevConfig config = TestConfig.defaults();
        config.setProperty(ConfigProperties.COMBINED_REPORTING_ENABLED, "false");

        RootResource resource = new RootResource(config);
        assertThat(resource.createLinks())
            .isNotNull()
            .map(Link::getRel)
            .doesNotContain("packages");
    }
}
