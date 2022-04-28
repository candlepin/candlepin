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

package org.candlepin.resteasy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.candlepin.resource.OwnerResource;

import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;


@ExtendWith(MockitoExtension.class)
public class ResourceLocatorMapTest {

    @BeforeEach
    public void setUp() throws Exception {
        ResteasyContext.clearContextData();
    }

    /**
     * We are using an actual class and method here
     *  Creating it as test data is an unneeded extra step
     */
    @Test
    public void testIsAnnotationPresentInInterface() {
        Method m = null;
        try {
            m = OwnerResource.class.getMethod("getUpstreamConsumers", String.class);
        }
        catch (NoSuchMethodException nsme) {
            // no method? just fail the test
            fail("test tries to use non-existent method");
        }
        assertFalse(ResourceLocatorMap.isAnnotationPresentInInterface(m, Consumes.class));
        assertTrue(ResourceLocatorMap.isAnnotationPresentInInterface(m, Produces.class));
    }

    /**
     * We are using an actual class and method here
     *  Creating it as test data is an unneeded extra step
     */
    @Test
    public void testGetConsumesMediaTypesFromMethod() {
        Method m = null;
        try {
            m = OwnerResource.class.getMethod("importManifest",
                String.class, List.class, MultipartInput.class);
        }
        catch (NoSuchMethodException nsme) {
            // no method? just fail the test
            fail("test tries to use non-existent method");
        }
        List<String> expected = new ArrayList<>();
        expected.addAll(Arrays.asList("multipart/form-data"));
        assertEquals(ResourceLocatorMap.getConsumesMediaTypesFromMethod(m), expected);
    }
}
