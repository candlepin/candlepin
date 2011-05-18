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
package org.fedoraproject.candlepin.resteasy.interceptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

import org.apache.commons.codec.binary.Base64;
import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.auth.UserPrincipal;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.model.Permission;
import org.fedoraproject.candlepin.service.UserServiceAdapter;
import org.jboss.resteasy.specimpl.HttpHeadersImpl;
import org.jboss.resteasy.specimpl.MultivaluedMapImpl;
import org.jboss.resteasy.spi.HttpRequest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.google.inject.Injector;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

public class BasicAuthViaUserServiceTest {

    @Mock private HttpRequest request;
    private HttpHeadersImpl headers;
    @Mock private UserServiceAdapter userService;
    @Mock private OwnerCurator ownerCurator;
    @Mock private Injector injector;
    private BasicAuth auth;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        headers = new HttpHeadersImpl();
        headers.setRequestHeaders(new MultivaluedMapImpl<String, String>());
        when(request.getHttpHeaders()).thenReturn(headers);
        this.auth = new BasicAuth(userService, ownerCurator, injector);
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
    @Test
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
        
        when(ownerCurator.lookupByKey("user")).thenReturn(owner);
        List<Permission> permissions = Arrays.asList(new Permission[] {
            new Permission(owner, EnumSet.of(Role.OWNER_ADMIN))
        });
        UserPrincipal expected = new UserPrincipal("user", permissions);
        assertEquals(expected, this.auth.getPrincipal(request));
    }

    // TODO:  Add in owner creation/retrieval tests?

    private void setUserAndPassword(String username, String password) {
        headers.getRequestHeaders().add("Authorization", 
            "BASIC " + encodeUserAndPassword(username, password));
    }

    private String encodeUserAndPassword(String username, String password) {
        String decoded = username + ":" + password;
        byte[] encoded = Base64.encodeBase64(decoded.getBytes());

        return new String(encoded);
    }
}
