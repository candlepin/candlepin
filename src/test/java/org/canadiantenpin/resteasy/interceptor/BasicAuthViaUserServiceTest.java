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
package org.canadianTenPin.resteasy.interceptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

import org.canadianTenPin.auth.Access;
import org.canadianTenPin.auth.UserPrincipal;
import org.canadianTenPin.auth.permissions.OwnerPermission;
import org.canadianTenPin.auth.permissions.Permission;
import org.canadianTenPin.exceptions.UnauthorizedException;
import org.canadianTenPin.model.Owner;
import org.canadianTenPin.model.User;
import org.canadianTenPin.service.UserServiceAdapter;

import com.google.inject.Injector;

import org.apache.commons.codec.binary.Base64;
import org.jboss.resteasy.specimpl.HttpHeadersImpl;
import org.jboss.resteasy.specimpl.MultivaluedMapImpl;
import org.jboss.resteasy.spi.HttpRequest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class BasicAuthViaUserServiceTest {

    @Mock private HttpRequest request;
    private HttpHeadersImpl headers;
    @Mock private UserServiceAdapter userService;
    @Mock private Injector injector;
    private BasicAuth auth;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        headers = new HttpHeadersImpl();
        headers.setRequestHeaders(new MultivaluedMapImpl<String, String>());
        when(request.getHttpHeaders()).thenReturn(headers);
        I18n i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        when(injector.getInstance(I18n.class)).thenReturn(i18n);
        this.auth = new BasicAuth(userService, injector);
    }

    /**
     * No authentication header is defined.
     *
     * @throws Exception
     */
    @Test
    public void noAuth() throws Exception {
        assertNull(this.auth.getPrincipal(request));
    }

    /**
     * Authentication head is not BASIC
     *
     * @throws Exception
     */
    @Test
    public void notBasicAuth() throws Exception {
        headers.getRequestHeaders().add("Authorization", "DIGEST username=billy");
        assertNull(this.auth.getPrincipal(request));
    }

    /**
     * The user service indicates that the given credentials are invalid
     *
     * @throws Exception
     */
    @Test(expected = UnauthorizedException.class)
    public void invalidUserPassword() throws Exception {
        setUserAndPassword("billy", "madison");
        when(userService.validateUser("billy", "madison")).thenReturn(false);
        assertNull(this.auth.getPrincipal(request));
    }

    /**
     * Valid credentials are given - checks if the correct principal is created.
     *
     * @throws Exception
     */
    @Test
    public void correctPrincipal() throws Exception {
        Owner owner = new Owner("user", "user");

        setUserAndPassword("user", "redhat");
        when(userService.validateUser("user", "redhat")).thenReturn(true);
        // TODO: test will fail, need to mock the permissions setup

        Set<OwnerPermission> permissions = new HashSet<OwnerPermission>();
        permissions.add(new OwnerPermission(owner, Access.ALL));

        when(userService.findByLogin("user")).thenReturn(new User());

        UserPrincipal expected = new UserPrincipal("user",
                new ArrayList<Permission>(permissions), false);
        assertEquals(expected, this.auth.getPrincipal(request));
    }

    @Test
    public void correctPrincipalColonPassword() throws Exception {
        Owner owner = new Owner("user", "user");

        setUserAndPassword("user", "1:2");
        when(userService.validateUser("user", "1:2")).thenReturn(true);


        Set<OwnerPermission> permissions = new HashSet<OwnerPermission>();
        permissions.add(new OwnerPermission(owner, Access.ALL));

        when(userService.findByLogin("user")).thenReturn(new User());

        UserPrincipal expected = new UserPrincipal("user",
                new ArrayList<Permission>(permissions), false);
        assertEquals(expected, this.auth.getPrincipal(request));
    }
    @Test
    public void correctPrincipalNoPassword() throws Exception {
        Owner owner = new Owner("user", "user");

        setUserNoPassword("user");
        when(userService.validateUser("user", null)).thenReturn(true);


        Set<OwnerPermission> permissions = new HashSet<OwnerPermission>();
        permissions.add(new OwnerPermission(owner, Access.ALL));

        when(userService.findByLogin("user")).thenReturn(new User());

        UserPrincipal expected = new UserPrincipal("user",
                new ArrayList<Permission>(permissions), false);
        assertEquals(expected, this.auth.getPrincipal(request));
    }

    // TODO:  Add in owner creation/retrieval tests?

    private void setUserAndPassword(String username, String password) {
        headers.getRequestHeaders().add("Authorization",
            "BASIC " + encodeUserAndPassword(username, password));
    }

    private void setUserNoPassword(String username) {
        headers.getRequestHeaders().add("Authorization",
            "BASIC " + encodeUserNoPassword(username));
    }

    private String encodeUserNoPassword(String username) {
        String decoded = username;
        byte[] encoded = Base64.encodeBase64(decoded.getBytes());
        return new String(encoded);
    }

    private String encodeUserAndPassword(String username, String password) {
        String decoded = username + ":" + password;
        byte[] encoded = Base64.encodeBase64(decoded.getBytes());

        return new String(encoded);
    }
}
