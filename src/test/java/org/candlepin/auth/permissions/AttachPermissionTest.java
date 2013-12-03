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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.candlepin.auth.Access;
import org.candlepin.auth.SubResource;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.junit.Before;
import org.junit.Test;

public class AttachPermissionTest {

    private AttachPermission perm;
    private Owner owner;

    @Before
    public void init() {
        owner = new Owner("ownerkey", "My Org");
        perm = new AttachPermission(owner);
    }

    @Test
    public void allowsGetPool() {
        Pool p = new Pool();
        p.setOwner(owner);
        assertTrue(perm.canAccess(p, SubResource.NONE, Access.READ_ONLY));
    }

    @Test
    public void blocksModifyPool() {
        Pool p = new Pool();
        p.setOwner(owner);
        assertFalse(perm.canAccess(p, SubResource.NONE, Access.ALL));
    }

    @Test
    public void allowsBindToOrgPools() {
        Pool p = new Pool();
        p.setOwner(owner);
        assertTrue(perm.canAccess(p, SubResource.ENTITLEMENTS, Access.CREATE));
    }

    @Test
    public void blocksBindToOtherOrgPools() {
        Pool p = new Pool();
        Owner owner2 = new Owner("someother", "whatever");
        p.setOwner(owner2);
        assertFalse(perm.canAccess(p, SubResource.ENTITLEMENTS, Access.CREATE));
    }

    @Test
    public void blocksListEntitlementsInOrgPools() {
        Pool p = new Pool();
        p.setOwner(owner);
        assertFalse(perm.canAccess(owner, SubResource.ENTITLEMENTS, Access.READ_ONLY));
    }

}
