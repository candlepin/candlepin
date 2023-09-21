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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.candlepin.model.Consumer;
import org.candlepin.model.Pool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


public class TrustedUserPrincipalTest {
    private TrustedUserPrincipal principal;

    @BeforeEach
    public void init() {
        principal = new TrustedUserPrincipal("admin");
    }

    @Test
    public void name() {
        assertEquals("admin", principal.getPrincipalName());
        assertEquals(principal.getUsername(), principal.getPrincipalName());
    }

    @Test
    public void username() {
        assertEquals("admin", principal.getUsername());
        assertEquals(principal.getPrincipalName(), principal.getUsername());
    }

    @Test
    public void type() {
        assertEquals("trusteduser", principal.getType());
    }

    @Test
    public void hasFullAccess() {
        assertTrue(principal.hasFullAccess());
    }

    @Test
    public void authMethod() {
        assertEquals(AuthenticationMethod.TRUSTED_USER, principal.getAuthenticationMethod());
    }

    @Test
    public void access() {
        Consumer c = mock(Consumer.class);
        Pool p = mock(Pool.class);
        assertTrue(principal.canAccess(c, SubResource.NONE, Access.ALL));
        assertTrue(principal.canAccess(null, SubResource.NONE, Access.NONE));
        assertTrue(principal.canAccessAll(null, SubResource.NONE, Access.NONE));
        assertTrue(principal.canAccess(p, SubResource.NONE, Access.ALL));
        assertTrue(principal.canAccess("always true", SubResource.NONE, Access.READ_ONLY));
    }

    @Test
    public void equalsNull() {
        assertNotEquals(null, principal);
    }

    @Test
    public void equalsOtherObject() {
        assertNotEquals(principal, new Object());
    }

    @Test
    public void equalsAnotherConsumerPrincipal() {
        TrustedUserPrincipal tup = new TrustedUserPrincipal("admin");
        assertEquals(principal, tup);
    }

    @Test
    public void equalsDifferentConsumer() {
        TrustedUserPrincipal tup = new TrustedUserPrincipal("donald");
        assertNotEquals(principal, tup);
    }
}
