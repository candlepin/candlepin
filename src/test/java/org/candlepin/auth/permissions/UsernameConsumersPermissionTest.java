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
package org.candlepin.auth.permissions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.candlepin.auth.Access;
import org.candlepin.auth.SubResource;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.User;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.From;


public class UsernameConsumersPermissionTest {

    private UsernameConsumersPermission perm;
    private Owner owner;
    private final String username = "bill";

    @BeforeEach
    public void init() {
        User u = new User(username, "dontcare");
        owner = new Owner()
            .setId(TestUtil.randomString())
            .setKey("ownerkey")
            .setDisplayName("My Org");

        perm = new UsernameConsumersPermission(u, owner);
    }

    @Test
    public void allowsUsernameConsumersModification() {
        Consumer c = new Consumer()
            .setName("consumer")
            .setUsername(username)
            .setOwner(owner);

        assertTrue(perm.canAccess(c, SubResource.NONE, Access.ALL));
        assertTrue(perm.canAccess(c, SubResource.NONE, Access.CREATE));
        assertTrue(perm.canAccess(c, SubResource.NONE, Access.READ_ONLY));
    }

    @Test
    public void allowsRegisterOrgConsumers() {
        Consumer c = new Consumer()
            .setName("consumer")
            .setUsername(username)
            .setOwner(owner);

        assertTrue(perm.canAccess(owner, SubResource.CONSUMERS, Access.CREATE));
    }

    @Test
    public void disallowsRegistrationToOrgWithoutCreatePermission() {
        Owner owner2 = new Owner()
            .setId(TestUtil.randomString())
            .setKey("ownerkey2")
            .setDisplayName("My Org 2");

        assertFalse(perm.canAccess(owner2, SubResource.CONSUMERS, Access.CREATE));
    }

    @Test
    public void allowsListOrgConsumers() {
        Consumer c = new Consumer()
            .setName("consumer")
            .setUsername(username)
            .setOwner(owner);

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
        Owner other = new Owner()
            .setId(TestUtil.randomString())
            .setKey("ownerkey2")
            .setDisplayName("My Org 2");

        Consumer c = new Consumer()
            .setName("consumer")
            .setUsername(username)
            .setOwner(other);

        assertFalse(perm.canAccess(c, SubResource.NONE, Access.READ_ONLY));
        assertFalse(perm.canAccess(c, SubResource.NONE, Access.ALL));
    }

    @Test
    public void blocksOtherUsernameConsumers() {
        Consumer c = new Consumer()
            .setName("consumer")
            .setUsername("somebodyelse")
            .setOwner(owner);

        assertFalse(perm.canAccess(c, SubResource.NONE, Access.READ_ONLY));
        assertFalse(perm.canAccess(c, SubResource.NONE, Access.ALL));
    }

    @Test
    public void allowsUsernameConsumersUnbind() {
        Consumer c = new Consumer()
            .setName("consumer")
            .setUsername(username)
            .setOwner(owner);

        Entitlement e = new Entitlement();
        e.setOwner(owner);
        e.setConsumer(c);
        assertTrue(perm.canAccess(e, SubResource.NONE, Access.ALL));
    }

    @Test
    public void blocksOtherUsernameConsumersUnbind() {
        Consumer c = new Consumer()
            .setName("consumer")
            .setUsername("somebodyelse")
            .setOwner(owner);

        Entitlement e = new Entitlement();
        e.setOwner(owner);
        e.setConsumer(c);
        assertFalse(perm.canAccess(e, SubResource.NONE, Access.ALL));
    }

    @Test
    public void shouldNotReturnQueryRestrictionsWhenEntityIsNotConsumer() {
        assertNull(perm.getQueryRestriction(Owner.class, mock(CriteriaBuilder.class), mock(From.class)));
    }
}
