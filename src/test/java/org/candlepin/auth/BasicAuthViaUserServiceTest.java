/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;

import org.candlepin.auth.permissions.OwnerPermission;
import org.candlepin.auth.permissions.PermissionFactory;
import org.candlepin.exceptions.CandlepinException;
import org.candlepin.exceptions.NotAuthorizedException;
import org.candlepin.model.Owner;
import org.candlepin.model.User;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.service.exception.user.UserDisabledException;
import org.candlepin.service.exception.user.UserInvalidException;
import org.candlepin.service.exception.user.UserServiceException;
import org.candlepin.service.exception.user.UserUnacceptedTermsException;
import org.candlepin.service.exception.user.UserUnauthorizedException;
import org.candlepin.test.TestLogCapture;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;

import org.apache.commons.codec.binary.Base64;
import org.jboss.resteasy.specimpl.MultivaluedMapImpl;
import org.jboss.resteasy.spi.HttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.util.stream.Stream;

import jakarta.inject.Provider;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response.Status;

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

        NotAuthorizedException result = assertThrows(NotAuthorizedException.class,
            () -> this.auth.getPrincipal(request));

        assertEquals(Status.UNAUTHORIZED, result.httpReturnCode());
        assertEquals("Invalid Credentials", result.getMessage());
        assertThat(result.headers()).containsEntry("WWW-Authenticate", "Basic Realm=candlepin");
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

    private static Stream<Arguments> expectedAuthenticationFailures() {
        return Stream.of(false, true).flatMap(lookup -> Stream.of(
            Arguments.of(lookup, new UserInvalidException("alice"), "User \"alice\" is not valid."),
            Arguments.of(lookup, new UserDisabledException("alice"), "has been disabled"),
            Arguments.of(lookup, new UserUnacceptedTermsException("alice"), "accept Red Hat's Terms and conditions"),
            Arguments.of(lookup, new UserUnauthorizedException("alice"), "Invalid username or password.")));
    }

    @ParameterizedTest
    @MethodSource("expectedAuthenticationFailures")
    public void testExpectedFailuresHideAccountDetails(boolean lookup, UserServiceException failure,
        String logDetail) {

        setUserAndPassword("alice", "password");
        if (lookup) {
            when(userService.validateUser("alice", "password")).thenReturn(true);
            when(userService.findByLogin("alice")).thenThrow(failure);
        }
        else {
            when(userService.validateUser("alice", "password")).thenThrow(failure);
        }

        try (TestLogCapture authLogs = new TestLogCapture(BasicAuth.class);
            TestLogCapture lookupLogs = new TestLogCapture(UserAuth.class)) {

            CandlepinException result = assertThrows(CandlepinException.class,
                () -> this.auth.getPrincipal(request));

            assertEquals(Status.UNAUTHORIZED, result.httpReturnCode());
            assertEquals("Invalid Credentials", result.message().getDisplayMessage());
            assertThat(result.headers()).containsEntry("WWW-Authenticate", "Basic Realm=candlepin");
            assertThat(result.isLogException()).isFalse();
            assertNull(result.getCause());

            List<ILoggingEvent> events = Stream.concat(authLogs.getEvents().stream(), lookupLogs.getEvents().stream())
                .toList();
            assertThat(events)
                .singleElement()
                .satisfies(event -> {
                    assertEquals(Level.WARN, event.getLevel());
                    assertThat(event.getFormattedMessage()).contains(logDetail);
                    assertNull(event.getThrowableProxy());
                });
        }
    }

    @Test
    public void testUnexpectedCredentialValidationFailureReturnsServiceUnavailable() {
        setUserAndPassword("alice", "password");
        UserServiceException failure = new UserServiceException("unavailable");
        when(userService.validateUser("alice", "password")).thenThrow(failure);

        CandlepinException result = assertThrows(CandlepinException.class,
            () -> this.auth.getPrincipal(request));

        assertEquals(Status.SERVICE_UNAVAILABLE, result.httpReturnCode());
        assertEquals("Error contacting user service", result.getMessage());
        assertSame(failure, result.getCause());
    }

    @Test
    public void testRetainsServiceUnavailableForUnexpectedUserLookupFailure() {
        setUserAndPassword("alice", "password");
        when(userService.validateUser("alice", "password")).thenReturn(true);
        UserServiceException failure = new UserServiceException("unavailable");
        when(userService.findByLogin("alice")).thenThrow(failure);

        CandlepinException result = assertThrows(CandlepinException.class,
            () -> this.auth.getPrincipal(request));

        assertEquals(Status.SERVICE_UNAVAILABLE, result.httpReturnCode());
        assertEquals("Error contacting user service", result.getMessage());
        assertSame(failure, result.getCause());
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
