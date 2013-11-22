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
package org.candlepin.resteasy.interceptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.auth.Principal;
import org.candlepin.auth.TrustedUserPrincipal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.model.User;
import org.candlepin.service.UserServiceAdapter;

import com.google.inject.Injector;

import org.jboss.resteasy.specimpl.HttpHeadersImpl;
import org.jboss.resteasy.specimpl.MultivaluedMapImpl;
import org.jboss.resteasy.spi.HttpRequest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Locale;

public class TrustedUserAuthTest {

    @Mock private HttpRequest request;
    private HttpHeadersImpl headers;
    @Mock private UserServiceAdapter userService;
    @Mock private Injector injector;
    private TrustedUserAuth auth;

    private static final String USERNAME = "myusername";

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        headers = new HttpHeadersImpl();
        headers.setRequestHeaders(new MultivaluedMapImpl<String, String>());
        when(request.getHttpHeaders()).thenReturn(headers);
        I18n i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        when(injector.getInstance(I18n.class)).thenReturn(i18n);
        this.auth = new TrustedUserAuth(userService, injector);
    }

    @Test
    public void missingUsernameHeader() throws Exception {
        Principal p = auth.getPrincipal(request);
        assertNull(p);
    }

    @Test
    public void normalTrustedAuth() throws Exception {
        headers.getRequestHeaders().add(TrustedUserAuth.USER_HEADER, USERNAME);
        Principal p = auth.getPrincipal(request);
        assertTrue(p instanceof TrustedUserPrincipal);
        verify(userService, never()).validateUser(any(String.class), any(String.class));
        verify(userService, never()).findByLogin(any(String.class));
        assertTrue(p.hasFullAccess());
    }

    @Test
    public void trustedAuthWithPermissionsLookup() throws Exception {
        headers.getRequestHeaders().add(TrustedUserAuth.USER_HEADER, USERNAME);

        // Adding this header should cause the user to be loaded from the adapter:
        headers.getRequestHeaders().add(TrustedUserAuth.LOOKUP_PERMISSIONS_HEADER, "true");

        User u = new User(USERNAME, "pass");
        when(userService.findByLogin(eq(USERNAME))).thenReturn(u);

        Principal p = auth.getPrincipal(request);
        assertTrue(p instanceof UserPrincipal);

        // This shouldn't attempt to verify a password:
        verify(userService, never()).validateUser(any(String.class), any(String.class));

        // It *should* ask for a user object which carries roles and thus, permissions:
        verify(userService).findByLogin(eq(USERNAME));

        assertFalse(p.hasFullAccess());
        assertEquals(USERNAME, p.getUsername());
    }

}
