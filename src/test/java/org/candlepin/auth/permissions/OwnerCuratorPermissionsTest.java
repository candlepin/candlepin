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
package org.candlepin.auth.permissions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;

import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.model.Owner;
import org.candlepin.model.User;
import org.candlepin.test.DatabaseTestFixture;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OwnerCuratorPermissionsTest extends DatabaseTestFixture {

    private Owner owner1;
    private Owner owner2;
    private Owner owner3;
    private Principal principal;

    @Before
    public void setUpTestObjects() {
        owner1 = new Owner("Example Corporation");
        owner2 = new Owner("Example Corporation 2");
        owner3 = new Owner("Example Corporation 3");
        ownerCurator.create(owner1);
        ownerCurator.create(owner2);
        ownerCurator.create(owner3);

        // Setup a principal with access to org 1 and 2, but not 3.
        Set<Permission> perms = new HashSet<Permission>();
        User u = new User("fakeuser", "dontcare");
        perms.add(new UsernameConsumersPermission(u, owner1));
        perms.add(new OwnerPermission(owner1, Access.ALL));
        perms.add(new OwnerPermission(owner2, Access.READ_ONLY)); // just read for org 2
        principal = new UserPrincipal(u.getUsername(), perms, false);
        setupPrincipal(principal);
    }

    @Test
    public void testListAllOwnerPermissionFiltering() {
        List<Owner> results = ownerCurator.listAll();
        assertEquals(2, results.size());
        assertTrue(results.contains(owner1));
        assertTrue(results.contains(owner2));
    }

    @Test
    public void testListAllByIdsOwnerPermissionFiltering() {
        List<String> ids = Arrays.asList(owner1.getId(), owner2.getId(), owner3.getId());
        List<Owner> results = ownerCurator.listAllByIds(ids);
        // Even though we asked for three by ID, we should only get two returned:
        assertEquals(2, results.size());
        assertTrue(results.contains(owner1));
        assertTrue(results.contains(owner2));
    }

    @Test
    public void testLookupByKeyOwnerPermissionFiltering() {
        assertEquals(owner1, ownerCurator.lookupByKey(owner1.getKey()));
        assertEquals(owner2, ownerCurator.lookupByKey(owner2.getKey()));
        assertNull(ownerCurator.lookupByKey(owner3.getKey()));
    }

}
