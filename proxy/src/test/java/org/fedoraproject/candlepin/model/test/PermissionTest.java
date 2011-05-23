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

public class PermissionTest extends DatabaseTestFixture {

    @Test
    public void testCreate() throws Exception {
        
        Owner o = createOwner();
        OwnerPermission p = new OwnerPermission(o, Access.OWNER_ADMIN);
        permissionCurator.create(p);
        
        OwnerPermission lookedUp = permissionCurator.find(p.getId());
        assertNotNull(lookedUp);
        assertEquals(o.getId(), lookedUp.getOwner().getId());
        assertEquals(Access.OWNER_ADMIN, lookedUp.getVerb());
    }
    
    @Test
    public void testEquality() throws Exception {
        Owner o = createOwner();
        OwnerPermission basePerm = new OwnerPermission(o, Access.OWNER_ADMIN);
        
        OwnerPermission equalPerm = new OwnerPermission(o, Access.OWNER_ADMIN);
        assertFalse(basePerm == equalPerm);
    }

}
