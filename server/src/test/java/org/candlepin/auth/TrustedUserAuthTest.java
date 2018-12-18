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
package org.candlepin.auth;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.candlepin.auth.permissions.PermissionFactory;
import org.candlepin.model.User;
import org.candlepin.service.UserServiceAdapter;

import org.jboss.resteasy.specimpl.MultivaluedMapImpl;
import org.jboss.resteasy.spi.HttpRequest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.List;
import java.util.Locale;

import javax.inject.Provider;
import javax.ws.rs.core.HttpHeaders;

public class TrustedUserAuthTest {

    @Mock private HttpRequest request;
    private MultivaluedMapImpl<String, String> headerMap;
    @Mock private HttpHeaders mockHeaders;
    @Mock private UserServiceAdapter userService;
    @Mock private Provider<I18n> mockI18n;
    @Mock private PermissionFactory mockPermissionFactory;
    private TrustedUserAuth auth;

    private static final String USERNAME = "myusername";

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        headerMap = new MultivaluedMapImpl<>();
        when(mockHeaders.getRequestHeaders()).thenReturn(headerMap);
        when(mockHeaders.getRequestHeader(anyString())).then(new Answer<List<String>>() {
            public List<String> answer(InvocationOnMock invocation) {
                Object[] args = invocation.getArguments();
                return headerMap.get(args[0]);
            }
        });

        when(request.getHttpHeaders()).thenReturn(mockHeaders);
        I18n i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        when(mockI18n.get()).thenReturn(i18n);

        this.auth = new TrustedUserAuth(userService, mockI18n, mockPermissionFactory);
    }

    @Test
    public void missingUsernameHeader() throws Exception {
        Principal p = auth.getPrincipal(request);
        assertNull(p);
    }

    @Test
    public void normalTrustedAuth() throws Exception {
        headerMap.add(TrustedUserAuth.USER_HEADER, USERNAME);
        TrustedUserPrincipal p = (TrustedUserPrincipal) auth.getPrincipal(request);
        verify(userService, never()).validateUser(any(String.class), any(String.class));
        verify(userService, never()).findByLogin(any(String.class));
        assertTrue(p.hasFullAccess());
    }

    @Test
    public void trustedAuthWithPermissionsLookup() throws Exception {
        headerMap.add(TrustedUserAuth.USER_HEADER, USERNAME);

        // Adding this header should cause the user to be loaded from the adapter:
        headerMap.add(TrustedUserAuth.LOOKUP_PERMISSIONS_HEADER, "true");

        User u = new User(USERNAME, "pass");
        when(userService.findByLogin(eq(USERNAME))).thenReturn(u);

        UserPrincipal p = (UserPrincipal) auth.getPrincipal(request);

        // This shouldn't attempt to verify a password:
        verify(userService, never()).validateUser(any(String.class), any(String.class));

        // It *should* ask for a user object which carries roles and thus, permissions:
        verify(userService).findByLogin(eq(USERNAME));

        assertFalse(p.hasFullAccess());
        assertEquals(USERNAME, p.getUsername());
    }

}
