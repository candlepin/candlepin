/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.servlet.filter.auth;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.auth.UserPrincipal;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.service.UserServiceAdapter;
import org.fedoraproject.candlepin.service.UserServiceAdapter.OwnerInfo;

import org.apache.commons.codec.binary.Base64;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class BasicAuthViaUserServiceTest {

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain chain;
    @Mock private UserServiceAdapter userService;
    @Mock private OwnerCurator ownerCurator;

    private Filter filter;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        this.filter = new BasicAuthViaUserServiceFilter(userService,
                ownerCurator);
    }

    /**
     * No authentication header is defined.
     *
     * @throws Exception
     */
    @Test
    public void noAuth() throws Exception {
        this.filter.doFilter(request, response, chain);

        verify(request, never()).setAttribute(eq(FilterConstants.PRINCIPAL_ATTR),
                any(Principal.class));
    }

    /**
     * Authentication head is not BASIC
     *
     * @throws Exception
     */
    @Test
    public void notBasicAuth() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("DIGEST username=billy");
        this.filter.doFilter(request, response, chain);

        verify(request, never()).setAttribute(eq(FilterConstants.PRINCIPAL_ATTR),
                any(Principal.class));
    }

    /**
     * The user service indicates that the given credentials are invalid
     *
     * @throws Exception
     */
    @Test
    public void invalidUserPassword() throws Exception {
        setUserAndPassword("billy", "madison");
        when(userService.validateUser("billy", "madison")).thenReturn(false);
        this.filter.doFilter(request, response, chain);

        verify(request, never()).setAttribute(eq(FilterConstants.PRINCIPAL_ATTR),
                any(Principal.class));
    }

    /**
     * Valid credentials are given - checks if the correct principal is created.
     *
     * @throws Exception
     */
    @Test
    public void correctPrincipal() throws Exception {
        setUserAndPassword("user", "redhat");
        when(userService.validateUser("user", "redhat")).thenReturn(true);
        when(userService.getOwnerInfo("user")).thenReturn(new OwnerInfo("user", "user"));
        UserPrincipal expected = new UserPrincipal("user", null, null);
        this.filter.doFilter(request, response, chain);

        verify(request).setAttribute(FilterConstants.PRINCIPAL_ATTR, expected);
    }

    // TODO:  Add in owner creation/retrieval tests?

    private void setUserAndPassword(String username, String password) {
        when(request.getHeader("Authorization")).thenReturn(
                "BASIC " + encodeUserAndPassword(username, password));
    }

    private String encodeUserAndPassword(String username, String password) {
        String decoded = username + ":" + password;
        byte[] encoded = Base64.encodeBase64(decoded.getBytes());

        return new String(encoded);
    }

}
