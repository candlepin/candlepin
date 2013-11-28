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
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.User;
import org.junit.Before;
import org.junit.Test;

public class UsernameConsumersPermissionTest {

    private UsernameConsumersPermission perm;
    private Owner owner;
    private final String username = "bill";

    @Before
    public void init() {
        User u = new User(username, "dontcare");
        owner = new Owner("ownerkey", "My Org");
        perm = new UsernameConsumersPermission(u, owner);
    }

    @Test
    public void allowsUsernameConsumersModification() {
        Consumer c = new Consumer("consumer", username, owner, null);
        assertTrue(perm.canAccess(c, SubResource.NONE, Access.ALL));
        assertTrue(perm.canAccess(c, SubResource.NONE, Access.CREATE));
        assertTrue(perm.canAccess(c, SubResource.NONE, Access.READ_ONLY));
    }

    @Test
    public void allowsRegisterOrgConsumers() {
        Consumer c = new Consumer("consumer", username, owner, null);
        assertTrue(perm.canAccess(owner, SubResource.CONSUMERS, Access.CREATE));
    }

    @Test
    public void allowsListOrgConsumers() {
        Consumer c = new Consumer("consumer", username, owner, null);
        assertTrue(perm.canAccess(owner, SubResource.CONSUMERS, Access.READ_ONLY));
    }

    @Test
    public void blocksAccessToOrgPools() {
        // Such a user probably has an owner permission which allows this, but this
        // permission should not grant it itself:
        assertFalse(perm.canAccess(owner, SubResource.POOLS, Access.READ_ONLY));
    }

    @Test
    public void blocksAccessToOrg() {
        assertFalse(perm.canAccess(owner, SubResource.NONE, Access.READ_ONLY));
        assertFalse(perm.canAccess(owner, SubResource.NONE, Access.ALL));
        assertFalse(perm.canAccess(owner, SubResource.NONE, Access.CREATE));
    }

    @Test
    public void blocksConsumersInOtherOrgDespiteSameUsername() {
        Owner other = new Owner("ownerkey2", "My Org 2");
        Consumer c = new Consumer("consumer", username, other, null);
        assertFalse(perm.canAccess(c, SubResource.NONE, Access.READ_ONLY));
        assertFalse(perm.canAccess(c, SubResource.NONE, Access.ALL));
    }

    @Test
    public void blocksOtherUsernameConsumers() {
        Consumer c = new Consumer("consumer", "somebodyelse", owner, null);
        assertFalse(perm.canAccess(c, SubResource.NONE, Access.READ_ONLY));
        assertFalse(perm.canAccess(c, SubResource.NONE, Access.ALL));
    }

    @Test
    public void allowsUsernameConsumersUnbind() {
        Consumer c = new Consumer("consumer", username, owner, null);
        Entitlement e = new Entitlement();
        e.setOwner(owner);
        e.setConsumer(c);
        assertTrue(perm.canAccess(e, SubResource.NONE, Access.ALL));
    }

    @Test
    public void blocksOtherUsernameConsumersUnbind() {
        Consumer c = new Consumer("consumer", "somebodyelse", owner, null);
        Entitlement e = new Entitlement();
        e.setOwner(owner);
        e.setConsumer(c);
        assertFalse(perm.canAccess(e, SubResource.NONE, Access.ALL));
    }

}
