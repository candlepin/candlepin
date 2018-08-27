/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.dto.api.v1;

import static org.junit.Assert.*;

import org.candlepin.dto.AbstractDTOTest;
import org.junit.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import java.util.Collection;
import java.util.LinkedList;



/**
 * Test suite for the EntitlementDTO class
 */
public class RoleDTOTest extends AbstractDTOTest<RoleDTO> {

    protected Map<String, Object> values;

    public RoleDTOTest() {
        super(RoleDTO.class);

        this.values = new HashMap<>();

        Collection<UserDTO> users = new LinkedList<>();
        Collection<PermissionBlueprintDTO> permissions = new LinkedList<>();

        for (int i = 0; i < 5; ++i) {
            UserDTO user = new UserDTO();
            user.setId("test-user-" + i);
            user.setUsername(user.getId());

            PermissionBlueprintDTO permission = new PermissionBlueprintDTO();
            permission.setId("test-perm-" + i);

            users.add(user);
            permissions.add(permission);
        }

        this.values.put("Id", "test-id");
        this.values.put("Name", "test-name");
        this.values.put("Users", users);
        this.values.put("Permissions", permissions);

        this.values.put("Created", new Date());
        this.values.put("Updated", new Date());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object getInputValueForMutator(String field) {
        return this.values.get(field);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object getOutputValueForAccessor(String field, Object input) {
        // Nothing to do here
        return input;
    }

    @Test
    public void testAddUser() {
        RoleDTO dto = this.getDTOInstance();

        UserDTO user1 = new UserDTO();
        user1.setId("test-user-1");
        user1.setUsername(user1.getId());

        UserDTO user2 = new UserDTO();
        user2.setId("test-user-2");
        user2.setUsername(user2.getId());

        Collection<UserDTO> users = dto.getUsers();
        assertNull(users);

        boolean added = dto.addUser(user1);
        assertTrue(added);

        users = dto.getUsers();
        assertNotNull(users);
        assertEquals(1, users.size());
        assertTrue(users.contains(user1));
        assertFalse(users.contains(user2));

        added = dto.addUser(user2);
        assertTrue(added);

        users = dto.getUsers();
        assertNotNull(users);
        assertEquals(2, users.size());
        assertTrue(users.contains(user1));
        assertTrue(users.contains(user2));
    }

    @Test
    public void testAddUserAlreadyExists() {
        RoleDTO dto = this.getDTOInstance();

        UserDTO user = new UserDTO();
        user.setId("test-user");
        user.setUsername(user.getId());

        Collection<UserDTO> users = dto.getUsers();
        assertNull(users);

        boolean added = dto.addUser(user);
        assertTrue(added);

        users = dto.getUsers();
        assertNotNull(users);
        assertEquals(1, users.size());
        assertTrue(users.contains(user));

        added = dto.addUser(user);
        assertFalse(added);

        users = dto.getUsers();
        assertNotNull(users);
        assertEquals(1, users.size());
        assertTrue(users.contains(user));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddUserNullUser() {
        RoleDTO dto = this.getDTOInstance();

        dto.addUser(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddUserIncompleteUser() {
        RoleDTO dto = this.getDTOInstance();
        UserDTO user = new UserDTO();

        dto.addUser(user);
    }

    @Test
    public void testRemoveUser() {
        RoleDTO dto = this.getPopulatedDTOInstance();

        Collection<UserDTO> users = new LinkedList<>(dto.getUsers());
        Collection<UserDTO> removed = new LinkedList<>();

        for (UserDTO user : users) {
            // The first remove request should succeed
            boolean result = dto.removeUser(user);
            assertTrue(result);

            removed.add(user);

            Collection<UserDTO> remaining = dto.getUsers();
            assertNotNull(remaining);
            assertEquals(users.size() - removed.size(), remaining.size());

            for (UserDTO existing : users) {
                assertEquals(!removed.contains(existing), remaining.contains(existing));
            }

            // Verify that removing the same user multiple times doesn't change the state
            result = dto.removeUser(user);
            assertFalse(result);

            remaining = dto.getUsers();
            assertNotNull(remaining);
            assertEquals(users.size() - removed.size(), remaining.size());

            for (UserDTO existing : users) {
                assertEquals(!removed.contains(existing), remaining.contains(existing));
            }
        }
    }

    @Test
    public void testRemoveUserDoesntExist() {
        RoleDTO dto = this.getDTOInstance();

        UserDTO user = new UserDTO();
        user.setId("test-user");
        user.setUsername(user.getId());

        Collection<UserDTO> users = dto.getUsers();
        assertNull(users);

        boolean result = dto.removeUser(user);
        assertFalse(result);

        users = dto.getUsers();
        assertNull(users);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRemoveUserNullUser() {
        RoleDTO dto = this.getDTOInstance();
        UserDTO user = null;

        dto.removeUser(user);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRemoveUserIncompleteUser() {
        RoleDTO dto = this.getDTOInstance();
        UserDTO user = new UserDTO();

        dto.removeUser(user);
    }

    @Test
    public void testRemoveUserById() {
        RoleDTO dto = this.getPopulatedDTOInstance();

        Collection<UserDTO> users = new LinkedList<>(dto.getUsers());
        Collection<UserDTO> removed = new LinkedList<>();

        for (UserDTO user : users) {
            // The first remove request should succeed
            boolean result = dto.removeUser(user);
            assertTrue(result);

            removed.add(user);

            Collection<UserDTO> remaining = dto.getUsers();
            assertNotNull(remaining);
            assertEquals(users.size() - removed.size(), remaining.size());

            for (UserDTO existing : users) {
                assertEquals(!removed.contains(existing), remaining.contains(existing));
            }

            // Verify that removing the same user multiple times doesn't change the state
            result = dto.removeUser(user);
            assertFalse(result);

            remaining = dto.getUsers();
            assertNotNull(remaining);
            assertEquals(users.size() - removed.size(), remaining.size());

            for (UserDTO existing : users) {
                assertEquals(!removed.contains(existing), remaining.contains(existing));
            }
        }
    }

    @Test
    public void testRemoveUserByIdDoesntExist() {
        RoleDTO dto = this.getDTOInstance();

        UserDTO user = new UserDTO();
        user.setId("test-user");
        user.setUsername(user.getId());

        Collection<UserDTO> users = dto.getUsers();
        assertNull(users);

        boolean result = dto.removeUser(user);
        assertFalse(result);

        users = dto.getUsers();
        assertNull(users);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRemoveUserByIdNullId() {
        RoleDTO dto = this.getDTOInstance();
        String userId = null;

        dto.removeUser(userId);
    }

}
