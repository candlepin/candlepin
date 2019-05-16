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
package org.candlepin.resteasy.filter;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import org.candlepin.auth.Verify;
import org.candlepin.common.auth.SecurityHole;
import org.candlepin.model.Consumer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.lang.reflect.Method;

import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.FeatureContext;

@RunWith(MockitoJUnitRunner.class)
public class AuthorizationFeatureTest {
    public static class FakeResource {
        @SecurityHole
        public void methodWithSecurityHole(String s) {

        }

        public void superAdminOnlyMethod(String s) {

        }

        public void methodWithVerify(@Verify(Consumer.class) String s) {

        }
    }

    @Mock private VerifyAuthorizationFilter verifyFilter;
    @Mock private SuperAdminAuthorizationFilter superAdminFilter;
    @Mock private SecurityHoleAuthorizationFilter securityHoleFilter;

    @Mock private FeatureContext context;
    @Mock private ResourceInfo resourceInfo;

    private AuthorizationFeature authorizationFeature;

    @Before
    public void setUp() throws Exception {
        this.authorizationFeature = new AuthorizationFeature(
            verifyFilter, superAdminFilter, securityHoleFilter);

        doReturn(FakeResource.class).when(resourceInfo).getResourceClass();
    }

    @Test
    public void testConfigureWithSecurityHole() throws Exception {
        Method m = FakeResource.class.getMethod("methodWithSecurityHole", String.class);

        when(resourceInfo.getResourceMethod()).thenReturn(m);
        authorizationFeature.configure(resourceInfo, context);

        verify(context).register(eq(securityHoleFilter));
    }

    @Test
    public void testConfigureWithSuperAdminMethod() throws Exception {
        Method m = FakeResource.class.getMethod("superAdminOnlyMethod", String.class);

        when(resourceInfo.getResourceMethod()).thenReturn(m);
        authorizationFeature.configure(resourceInfo, context);

        verify(context).register(eq(superAdminFilter));
    }

    @Test
    public void testConfigureWithVerifyAnnotation() throws Exception {
        Method m = FakeResource.class.getMethod("methodWithVerify", String.class);

        when(resourceInfo.getResourceMethod()).thenReturn(m);
        authorizationFeature.configure(resourceInfo, context);

        verify(context).register(eq(verifyFilter));
    }

    @Test
    public void testVerifyIsNotSuperAdminOnly() throws Exception {
        Method m = FakeResource.class.getMethod("methodWithVerify", String.class);
        boolean isSuperAdminOnly = authorizationFeature.isSuperAdminOnly(m);

        assertEquals(false, isSuperAdminOnly);
    }

    @Test
    public void testIsSuperAdminOnly() throws Exception {
        Method m = FakeResource.class.getMethod("superAdminOnlyMethod", String.class);
        boolean isSuperAdminOnly = authorizationFeature.isSuperAdminOnly(m);

        assertEquals(true, isSuperAdminOnly);
    }
}
