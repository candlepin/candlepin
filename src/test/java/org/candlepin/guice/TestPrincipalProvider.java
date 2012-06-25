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
import org.candlepin.model.OwnerCurator;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.candlepin.auth.Access;
import org.candlepin.auth.permissions.Permission;
import org.candlepin.model.OwnerPermission;

/**
 *
 */
public class TestPrincipalProvider extends PrincipalProvider {

    private static final String OWNER_NAME = "Default-Owner";

    private OwnerCurator ownerCurator;

    @Inject
    public TestPrincipalProvider(OwnerCurator ownerCurator) {
        this.ownerCurator = ownerCurator;
    }

    @Override
    public Principal get() {
        TestPrincipalProviderSetter principalSingleton = TestPrincipalProviderSetter.get();
        Principal principal = principalSingleton.getPrincipal();
        if (principal == null) {

            Owner owner = ownerCurator.lookupByKey(OWNER_NAME);

            if (owner == null) {
                owner = new Owner(OWNER_NAME);
                ownerCurator.create(owner);
            }

            List<Permission> permissions = new ArrayList<Permission>();
            permissions.add(new OwnerPermission(owner, Access.ALL));

            principal = new UserPrincipal("Default User", permissions, false);
        }
        return principal;
    }

}
