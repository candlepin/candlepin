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

import java.util.Set;

import org.candlepin.auth.Access;
import org.candlepin.auth.SubResource;
import org.candlepin.auth.permissions.Permission;
import org.candlepin.model.Owner;
import org.candlepin.model.User;
import org.candlepin.test.DatabaseTestFixture;
import org.hibernate.criterion.Criterion;
import org.junit.Test;

public class UserTest extends DatabaseTestFixture {

    @Test
    public void testCreate() throws Exception {
        String username = "TESTUSER";
        String password = "sekretpassword";
        String hashedPassword = "b58db974af4ea7b7b1b51a999f93ab5b67173799";
        User user = new User(username, password);

        beginTransaction();
        entityManager().persist(user);
        commitTransaction();

        User lookedUp = entityManager().find(User.class, user.getId());
        assertEquals(username, lookedUp.getUsername());
        assertEquals(hashedPassword, lookedUp.getHashedPassword());
    }

    @Test
    public void testGetOwners() {
        String username = "TESTUSER";
        String password = "sekretpassword";
        Owner owner1 = new Owner("owner1", "owner one");
        Owner owner2 = new Owner("owner2", "owner two");
        User user = new User(username, password);

        Set<Owner> owners = user.getOwners(Access.ALL);
        assertEquals(0, owners.size());
        user.addPermissions(new TestPermission(owner1));
        user.addPermissions(new TestPermission(owner2));

        // Adding the new permissions should give us access
        // to both new owners
        owners = user.getOwners(Access.ALL);
        assertEquals(2, owners.size());
    }

    @Test
    public void testGetOwnersNonOwnerPerm() {
        String username = "TESTUSER";
        String password = "sekretpassword";
        Owner owner1 = new Owner("owner1", "owner one");
        Owner owner2 = new Owner("owner2", "owner two");
        User user = new User(username, password);

        Set<Owner> owners = user.getOwners(Access.ALL);
        assertEquals(0, owners.size());
        user.addPermissions(new OtherPermission(owner1));
        user.addPermissions(new OtherPermission(owner2));

        // Adding the new permissions should not give us access
        // to either of the new owners
        owners = user.getOwners(Access.ALL);
        assertEquals(0, owners.size());
    }

    private class TestPermission implements Permission {

        private Owner owner;

        public TestPermission(Owner o) {
            owner = o;
        }

        @Override
        public boolean canAccess(Object target, SubResource subResource,
            Access access) {
            if (target instanceof Owner) {
                Owner targetOwner = (Owner) target;
                return targetOwner.getKey().equals(this.getOwner().getKey());
            }
            return false;
        }

        @Override
        public Criterion getCriteriaRestrictions(Class entityClass) {
            return null;
        }

        @Override
        public Owner getOwner() {
            return owner;
        }
    }

    private class OtherPermission implements Permission {

        private Owner owner;

        public OtherPermission(Owner o) {
            owner = o;
        }

        @Override
        public boolean canAccess(Object target, SubResource subResource,
            Access access) {
            return false;
        }

        @Override
        public Criterion getCriteriaRestrictions(Class entityClass) {
            return null;
        }

        @Override
        public Owner getOwner() {
            return owner;
        }
    }
}
