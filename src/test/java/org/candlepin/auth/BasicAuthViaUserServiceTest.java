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
package org.candlepin.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;

import org.candlepin.auth.permissions.OwnerPermission;
import org.candlepin.auth.permissions.PermissionFactory;
import org.candlepin.exceptions.NotAuthorizedException;
import org.candlepin.model.Owner;
import org.candlepin.model.User;
import org.candlepin.service.UserServiceAdapter;

import org.apache.commons.codec.binary.Base64;
import org.jboss.resteasy.specimpl.MultivaluedMapImpl;
import org.jboss.resteasy.spi.HttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.inject.Provider;
import javax.ws.rs.core.HttpHeaders;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class BasicAuthViaUserServiceTest {

    @Mock
    private HttpRequest request;
    private MultivaluedMapImpl<String, String> headerMap;
    @Mock
    private HttpHeaders mockHeaders;
    @Mock
    private UserServiceAdapter userService;
    @Mock
    private Provider<I18n> mockI18n;
    @Mock
    private PermissionFactory mockPermissionFactory;
    private BasicAuth auth;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        headerMap = new MultivaluedMapImpl<>();
        when(mockHeaders.getRequestHeaders()).thenReturn(headerMap);
        when(request.getHttpHeaders()).thenReturn(mockHeaders);
        when(mockHeaders.getRequestHeader(anyString())).then((Answer<List<String>>) invocation -> {
            Object[] args = invocation.getArguments();
            return headerMap.get(args[0]);
        });

        I18n i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
        when(mockI18n.get()).thenReturn(i18n);

        this.auth = new BasicAuth(userService, mockI18n, mockPermissionFactory);
    }

    /**
     * No authentication header is defined.
     *
     */
    @Test
    public void noAuth() {
        assertNull(this.auth.getPrincipal(request));
    }

    /**
     * Authentication head is not BASIC
     *
     */
    @Test
    public void notBasicAuth() {
        headerMap.add("Authorization", "DIGEST username=billy");
        assertNull(this.auth.getPrincipal(request));
    }

    /**
     * The user service indicates that the given credentials are invalid
     */
    @Test
    public void invalidUserPassword() throws Exception {
        setUserAndPassword("billy", "madison");
        when(userService.validateUser("billy", "madison")).thenReturn(false);
        assertThrows(NotAuthorizedException.class, () -> this.auth.getPrincipal(request));
    }

    /**
     * Valid credentials are given - checks if the correct principal is created.
     */
    @Test
    public void correctPrincipal() throws Exception {
        Owner owner = new Owner()
            .setKey("user")
            .setDisplayName("user");

        setUserAndPassword("user", "redhat");
        when(userService.validateUser("user", "redhat")).thenReturn(true);
        // TODO: test will fail, need to mock the permissions setup

        Set<OwnerPermission> permissions = new HashSet<>();
        permissions.add(new OwnerPermission(owner, Access.ALL));

        when(userService.findByLogin("user")).thenReturn(new User());

        UserPrincipal expected = new UserPrincipal("user", new ArrayList<>(permissions), false);
        assertEquals(expected, this.auth.getPrincipal(request));
    }

    @Test
    public void correctPrincipalColonPassword() throws Exception {
        Owner owner = new Owner()
            .setKey("user")
            .setDisplayName("user");

        setUserAndPassword("user", "1:2");
        when(userService.validateUser("user", "1:2")).thenReturn(true);

        Set<OwnerPermission> permissions = new HashSet<>();
        permissions.add(new OwnerPermission(owner, Access.ALL));

        when(userService.findByLogin("user")).thenReturn(new User());

        UserPrincipal expected = new UserPrincipal("user", new ArrayList<>(permissions), false);
        assertEquals(expected, this.auth.getPrincipal(request));
    }

    @Test
    public void correctPrincipalNoPassword() throws Exception {
        Owner owner = new Owner()
            .setKey("user")
            .setDisplayName("user");

        setUserNoPassword("user");
        when(userService.validateUser("user", null)).thenReturn(true);


        Set<OwnerPermission> permissions = new HashSet<>();
        permissions.add(new OwnerPermission(owner, Access.ALL));

        when(userService.findByLogin("user")).thenReturn(new User());

        UserPrincipal expected = new UserPrincipal("user", new ArrayList<>(permissions), false);
        assertEquals(expected, this.auth.getPrincipal(request));
    }

    // TODO:  Add in owner creation/retrieval tests?

    private void setUserAndPassword(String username, String password) {
        headerMap.add("Authorization",
            "BASIC " + encodeUserAndPassword(username, password));
    }

    private void setUserNoPassword(String username) {
        headerMap.add("Authorization",
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
