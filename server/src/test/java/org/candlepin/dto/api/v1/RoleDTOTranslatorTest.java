/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.PermissionBlueprint;
import org.candlepin.model.Role;
import org.candlepin.model.User;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Test suite for the RoleDTOTranslator class.
 */
public class RoleDTOTranslatorTest extends
    AbstractTranslatorTest<RoleDTO, Role, RoleDTOTranslator> {

    protected RoleDTOTranslator translator = new RoleDTOTranslator();

    protected PermissionBlueprintDTOTranslatorTest permDTOTranslatorTest =
        new PermissionBlueprintDTOTranslatorTest();

    protected UserDTOTranslatorTest userDTOTranslatorTest = new UserDTOTranslatorTest();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        this.userDTOTranslatorTest.initModelTranslator(modelTranslator);
        this.permDTOTranslatorTest.initModelTranslator(modelTranslator);

        modelTranslator.registerTranslator(this.translator, RoleDTO.class, Role.class);
    }

    @Override
    protected RoleDTOTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected RoleDTO initSourceObject() {
        RoleDTO source = new RoleDTO();

        Set<UserDTO> users = new HashSet<>();
        List<PermissionBlueprintDTO> permissions = new ArrayList<>();

        for (int i = 0; i < 5; ++i) {
            UserDTO user = (UserDTO) this.userDTOTranslatorTest.initSourceObject();
            user.setUsername("user_username" + i);

            PermissionBlueprintDTO permission = (PermissionBlueprintDTO)
                this.permDTOTranslatorTest.initSourceObject();
            permission.setId("test-perm-" + i);

            users.add(user);
            permissions.add(permission);
        }

        source.id("test-role-id")
            .name("test-role-name")
            .users(users)
            .permissions(permissions)
            .created(OffsetDateTime.now())
            .updated(OffsetDateTime.now());

        return source;
    }

    @Override
    protected Role initDestinationObject() {
        return new Role();
    }

    @Override
    protected void verifyOutput(RoleDTO source, Role dest, boolean childrenGenerated) {
        if (source != null) {

            assertEquals(source.getId(), dest.getId());
            assertEquals(source.getName(), dest.getName());

            if (childrenGenerated) {
                // Verify users
                for (UserDTO userDto : source.getUsers()) {
                    for (User user : dest.getUsers()) {
                        assertNotNull(user.getUsername());
                        assertNotNull(userDto.getUsername());

                        if (user.getId() != null) {
                            this.userDTOTranslatorTest.verifyOutput(userDto, user, childrenGenerated);
                        }
                    }
                }
                // Verify permissions
                for (PermissionBlueprintDTO pdto : source.getPermissions()) {
                    for (PermissionBlueprint pbp : dest.getPermissions()) {

                        if (pbp.getId().equals(pdto.getId())) {
                            this.permDTOTranslatorTest.verifyOutput(pdto, pbp, childrenGenerated);
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
