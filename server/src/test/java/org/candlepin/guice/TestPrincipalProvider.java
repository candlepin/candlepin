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
package org.candlepin.guice;

import org.candlepin.auth.Principal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.model.Owner;

import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;

import org.candlepin.auth.Access;
import org.candlepin.auth.permissions.OwnerPermission;
import org.candlepin.auth.permissions.Permission;

/**
 *
 */
public class TestPrincipalProvider extends PrincipalProvider {

    private static final String OWNER_NAME = "Default-Owner";

    @Inject
    public TestPrincipalProvider() {
    }

    @Override
    public Principal get() {
        TestPrincipalProviderSetter principalSingleton = TestPrincipalProviderSetter.get();
        Principal principal = principalSingleton.getPrincipal();
        if (principal == null) {
            List<Permission> permissions = new ArrayList<Permission>();
            permissions.add(new OwnerPermission(new Owner(OWNER_NAME), Access.ALL));

            principal = new UserPrincipal("Default User", permissions, true);
        }
        return principal;
    }

}
