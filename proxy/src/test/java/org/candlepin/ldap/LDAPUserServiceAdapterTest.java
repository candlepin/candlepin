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
package org.candlepin.ldap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.config.Config;
import org.candlepin.model.User;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;


/**
 * LDAPUserServiceAdapterTest
 */
public class LDAPUserServiceAdapterTest {

    private LDAPUserServiceAdapter lusa;
    private Config config;

    @Before
    public void init() {
        config = mock(Config.class);
        when(config.getString(eq("ldap.base"), any(String.class)))
            .thenReturn("dc=test,dc=com");
        when(config.getInt(eq("ldap.port"))).thenReturn(9898);
        when(config.getString(eq("ldap.host"), any(String.class)))
            .thenReturn("localhost");
        lusa = new LDAPUserServiceAdapter(config);
    }

    @Test
    public void getDN() {
        assertEquals("uid=foomanchu,dc=test,dc=com", lusa.getDN("foomanchu"));
    }

    @Ignore("needs mock LDAP server") @Test
    public void validateUser() {
        assertTrue(lusa.validateUser("user", "securepassword"));
    }

    @Test
    public void deleteUser() {
        User user = mock(User.class);
        lusa.deleteUser(user);
    }
}
