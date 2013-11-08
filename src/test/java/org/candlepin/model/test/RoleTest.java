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
package org.candlepin.model.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.apache.commons.lang.RandomStringUtils;
import org.candlepin.auth.Access;
import org.candlepin.auth.permissions.PermissionFactory.PermissionType;
import org.candlepin.model.Owner;
import org.candlepin.model.PermissionBlueprint;
import org.candlepin.model.Role;
import org.candlepin.model.User;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

public class RoleTest extends DatabaseTestFixture {

    private Owner owner;

    @Before
    public void setUp() throws Exception {
        owner = createOwner();
    }

    @Test
    public void testCreate() throws Exception {

        Role r = createRole(owner);

        Role lookedUp = roleCurator.find(r.getId());
        assertEquals(1, lookedUp.getPermissions().size());
        assertEquals(1, lookedUp.getUsers().size());
    }

    private Role createRole(Owner o) {
        User user = new User(RandomStringUtils.random(5), "pass");
        userCurator.create(user);

        PermissionBlueprint p = new PermissionBlueprint(PermissionType.OWNER, o,
            Access.ALL);

        Role r = new Role("role" + TestUtil.randomInt());
        r.addPermission(p);
        r.addUser(user);
        roleCurator.create(r);
        return r;
    }

    @Test
    public void testListForOwner() {
        Owner o2 = createOwner();

        Role r1 = createRole(owner);
        createRole(o2);

        List<Role> roles = roleCurator.listForOwner(owner);
        assertEquals(1, roles.size());
        assertEquals(r1, roles.get(0));
        assertEquals(1, roles.get(0).getUsers().size());
        assertEquals(1, roles.get(0).getPermissions().size());
    }

    @Test
    public void testAddPermission() {
        Role role = new Role("myrole");
        roleCurator.create(role);
        role.addPermission(new PermissionBlueprint(PermissionType.OWNER, owner,
            Access.ALL));
        role = roleCurator.find(role.getId());
        assertEquals(1, role.getPermissions().size());
        PermissionBlueprint perm = role.getPermissions().iterator().next();
        assertNotNull(perm.getId());
    }

}
