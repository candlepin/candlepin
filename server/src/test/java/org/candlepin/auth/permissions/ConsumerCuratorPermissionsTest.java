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
import static org.junit.Assert.assertNull;

import org.candlepin.auth.Access;
import org.candlepin.auth.Principal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Owner;
import org.candlepin.model.User;
import org.candlepin.test.DatabaseTestFixture;
import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConsumerCuratorPermissionsTest extends DatabaseTestFixture {

    private Owner owner;
    private ConsumerType consumerType;
    private static final String CONSUMER_TYPE_NAME = "test-consumer-type";

    @Before
    public void setUpTestObjects() {
        owner = new Owner("Example Corporation");

        ownerCurator.create(owner);

        consumerType = new ConsumerType(CONSUMER_TYPE_NAME);
        consumerTypeCurator.create(consumerType);
    }

    @Test
    public void testListForOwnerPermissionFiltering() {
        User u = setupOnlyMyConsumersPrincipal();

        Consumer c1 = new Consumer("c1", u.getUsername(), owner, consumerType);
        consumerCurator.create(c1);
        Consumer c2 = new Consumer("c2", "anotheruser", owner, consumerType);
        consumerCurator.create(c2);

        List<Consumer> results = consumerCurator.listByOwner(owner);
        assertEquals(1, results.size());
        assertEquals(c1.getName(), results.get(0).getName());
    }

    @Test
    public void testListForOwnerEditMineViewAllPermissionFiltering() {
        User u = setupEditMyConsumersViewAllPrincipal();

        Consumer c1 = new Consumer("c1", u.getUsername(), owner, consumerType);
        consumerCurator.create(c1);
        Consumer c2 = new Consumer("c2", "anotheruser", owner, consumerType);
        consumerCurator.create(c2);

        List<Consumer> results = consumerCurator.listByOwner(owner);
        assertEquals(2, results.size());
    }

    @Test
    public void testFindByUuidPermissionFiltering() {
        User u = setupOnlyMyConsumersPrincipal();

        Consumer c1 = new Consumer("c1", u.getUsername(), owner, consumerType);
        consumerCurator.create(c1);
        Consumer c2 = new Consumer("c2", "anotheruser", owner, consumerType);
        consumerCurator.create(c2);

        assertEquals(c1, consumerCurator.findByUuid(c1.getUuid()));
        assertNull(consumerCurator.findByUuid(c2.getUuid()));
    }

    private User setupOnlyMyConsumersPrincipal() {
        Set<Permission> perms = new HashSet<Permission>();
        User u = new User("fakeuser", "dontcare");
        perms.add(new UsernameConsumersPermission(u, owner));
        Principal p = new UserPrincipal(u.getUsername(), perms, false);
        setupPrincipal(p);
        return u;
    }

    private User setupEditMyConsumersViewAllPrincipal() {
        Set<Permission> perms = new HashSet<Permission>();
        User u = new User("fakeuser", "dontcare");
        perms.add(new UsernameConsumersPermission(u, owner));
        perms.add(new OwnerPermission(owner, Access.READ_ONLY));
        Principal p = new UserPrincipal(u.getUsername(), perms, false);
        setupPrincipal(p);
        return u;
    }
}
