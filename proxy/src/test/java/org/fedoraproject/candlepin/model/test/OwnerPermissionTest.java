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
package org.fedoraproject.candlepin.model.test;

import static org.junit.Assert.*;

import org.fedoraproject.candlepin.auth.Access;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerPermission;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;


import org.junit.Test;

public class OwnerPermissionTest extends DatabaseTestFixture {

    @Test
    public void testCreate() throws Exception {

        Owner o = createOwner();
        OwnerPermission p = new OwnerPermission(o, Access.ALL);
        permissionCurator.create(p);

        OwnerPermission lookedUp = permissionCurator.find(p.getId());
        assertNotNull(lookedUp);
        assertEquals(o.getId(), lookedUp.getOwner().getId());
        assertEquals(Access.ALL, lookedUp.getAccess());
    }

    @Test
    public void testEquality() throws Exception {
        Owner o = createOwner();
        OwnerPermission basePerm = new OwnerPermission(o, Access.ALL);

        OwnerPermission equalPerm = new OwnerPermission(o, Access.ALL);
        assertFalse(basePerm == equalPerm);
    }

    @Test
    public void testFindOrCreate() throws Exception {
        Owner o = createOwner();
        int count = permissionCurator.listAll().size();
        OwnerPermission p = permissionCurator.findOrCreate(o, Access.ALL);
        assertEquals(count + 1, permissionCurator.listAll().size());
        OwnerPermission p2 = permissionCurator.findOrCreate(o, Access.ALL);
        assertEquals(count + 1, permissionCurator.listAll().size());
    }

    @Test
    public void testFindByOwnerAndAccess() throws Exception {
        Owner o = createOwner();
        OwnerPermission p = permissionCurator.findOrCreate(o, Access.ALL);
        OwnerPermission lookedUp = permissionCurator.findByOwnerAndAccess(o, Access.ALL);
        assertEquals(p.getId(), lookedUp.getId());
    }
}
