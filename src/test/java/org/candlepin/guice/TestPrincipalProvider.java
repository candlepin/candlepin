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
package org.candlepin.guice;

import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.auth.permissions.OwnerPermission;
import org.candlepin.auth.permissions.Permission;
import org.candlepin.model.Owner;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

/**
 * Test principal provider that stores per-thread principal state via ThreadLocal. This
 * enables parallel test execution at the class level, where each test class runs on its
 * own thread. Each thread maintains its own principal independently.
 *
 * <p>Note: this assumes each test is single-threaded. Tests that spawn additional threads
 * will not inherit the principal set on the test's thread.
 */
public class TestPrincipalProvider extends PrincipalProvider {

    private static final String OWNER_NAME = "Default-Owner";

    // Per-thread principal storage for parallel test isolation
    private static final ThreadLocal<Principal> PRINCIPAL = new ThreadLocal<>();

    @Inject
    public TestPrincipalProvider() {
    }

    /**
     * Sets the principal for the current thread.
     *
     * @param principal
     *     the principal to set, or null to clear
     */
    public static void setPrincipal(Principal principal) {
        PRINCIPAL.set(principal);
    }

    /**
     * Clears the principal for the current thread, removing the ThreadLocal entry.
     */
    public static void clearPrincipal() {
        PRINCIPAL.remove();
    }

    @Override
    public Principal get() {
        Principal principal = PRINCIPAL.get();
        if (principal == null) {
            List<Permission> permissions = new ArrayList<>();

            Owner owner = new Owner()
                .setKey(OWNER_NAME)
                .setDisplayName(OWNER_NAME);

            permissions.add(new OwnerPermission(owner, Access.ALL));

            principal = new UserPrincipal("Default User", permissions, true);
        }
        return principal;
    }

}
