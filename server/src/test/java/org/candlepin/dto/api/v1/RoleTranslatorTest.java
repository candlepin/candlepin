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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.PermissionBlueprint;
import org.candlepin.model.Role;
import org.candlepin.model.User;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;



/**
 * Test suite for the EntitlementTranslator class.
 */
public class RoleTranslatorTest extends AbstractTranslatorTest<Role, RoleDTO, RoleTranslator> {

    protected RoleTranslator translator = new RoleTranslator();

    protected UserTranslatorTest userTranslatorTest = new UserTranslatorTest();
    protected PermissionBlueprintTranslatorTest permTranslatorTest = new PermissionBlueprintTranslatorTest();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        this.userTranslatorTest.initModelTranslator(modelTranslator);
        this.permTranslatorTest.initModelTranslator(modelTranslator);

        modelTranslator.registerTranslator(this.translator, Role.class, RoleDTO.class);
    }

    @Override
    protected RoleTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected Role initSourceObject() {
        Role source = new Role();

        Set<User> users = new HashSet<>();
        Set<PermissionBlueprint> permissions = new HashSet<>();

        for (int i = 0; i < 5; ++i) {
            User user = this.userTranslatorTest.initSourceObject();
            user.setId("test-user-" + i);

            PermissionBlueprint permission = this.permTranslatorTest.initSourceObject();
            permission.setId("test-perm-" + i);

            users.add(user);
            permissions.add(permission);
        }

        source.setId("test-role-id");
        source.setName("test-role-name");
        source.setUsers(users);
        source.setPermissions(permissions);

        source.setCreated(new Date());
        source.setUpdated(new Date());

        return source;
    }

    @Override
    protected RoleDTO initDestinationObject() {
        return new RoleDTO();
    }

    @Override
    protected void verifyOutput(Role source, RoleDTO dest, boolean childrenGenerated) {

        if (source != null) {
            assertEquals(source.getId(), dest.getId());
            assertEquals(source.getName(), dest.getName());

            if (childrenGenerated) {
                // Verify users
                for (User user : source.getUsers()) {
                    for (UserDTO userDto : dest.getUsers()) {
                        assertNotNull(user.getId());

                        if (user.getId() != null) {
                            this.userTranslatorTest.verifyOutput(user, userDto, childrenGenerated);
                        }
                    }
                }

                // Verify permissions
                for (PermissionBlueprint permission : source.getPermissions()) {
                    for (PermissionBlueprintDTO permissionDto : dest.getPermissions()) {
                        assertNotNull(permission.getId());
                        assertNotNull(permissionDto.getId());

                        if (permission.getId().equals(permissionDto.getId())) {
                            this.permTranslatorTest.verifyOutput(permission, permissionDto,
                                childrenGenerated);
                        }
                    }
                }
            }
            else {
                assertNotNull(dest.getUsers());
                assertTrue(dest.getUsers().isEmpty());

                assertNotNull(dest.getPermissions());
                assertTrue(dest.getPermissions().isEmpty());
            }
        }
        else {
            assertNull(dest);
        }
    }
}
