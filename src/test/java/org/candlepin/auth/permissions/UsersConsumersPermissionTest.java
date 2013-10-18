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
import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;
import org.candlepin.model.User;
import org.junit.Before;
import org.junit.Test;

public class UsersConsumersPermissionTest {

    private UsersConsumersPermission readOnlyPerm;
    private UsersConsumersPermission writePerm;
    private Owner owner;
    private final String username = "bill";

    @Before
    public void init() {
        User u = new User(username, "dontcare");
        owner = new Owner("ownerkey", "My Org");
        readOnlyPerm = new UsersConsumersPermission(u, owner, Access.READ_ONLY);
        writePerm = new UsersConsumersPermission(u, owner, Access.ALL);
    }

    @Test
    public void allowsUsersConsumersReadOnly() {
        Consumer c = new Consumer("consumer", username, owner, null);
        assertTrue(readOnlyPerm.canAccess(c, Access.READ_ONLY));
        assertFalse(readOnlyPerm.canAccess(c, Access.ALL));
    }

    @Test
    public void allowsUsersConsumersWrite() {
        Consumer c = new Consumer("consumer", username, owner, null);
        assertTrue(writePerm.canAccess(c, Access.ALL));
        assertTrue(writePerm.canAccess(c, Access.READ_ONLY));
    }

    @Test
    public void blocksConsumersInOtherOrgDespiteSameUsername() {
        Owner other = new Owner("ownerkey2", "My Org 2");
        Consumer c = new Consumer("consumer", username, other, null);
        assertFalse(readOnlyPerm.canAccess(c, Access.READ_ONLY));
        assertFalse(readOnlyPerm.canAccess(c, Access.ALL));
    }

    @Test
    public void blocksOwnerConsumers() {
        Consumer c = new Consumer("consumer", "somebodyelse", owner, null);
        assertFalse(readOnlyPerm.canAccess(c, Access.READ_ONLY));
        assertFalse(readOnlyPerm.canAccess(c, Access.ALL));
    }

}
