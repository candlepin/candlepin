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
import org.candlepin.service.model.PermissionBlueprintInfo;
import org.candlepin.service.model.RoleInfo;
import org.candlepin.service.model.UserInfo;

import org.apache.commons.lang.builder.EqualsBuilder;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;



/**
 * Test suite for the EntitlementTranslator class.
 */
public class RoleInfoTranslatorTest extends AbstractTranslatorTest<RoleInfo, RoleDTO, RoleInfoTranslator> {

    protected RoleInfoTranslator translator = new RoleInfoTranslator();

    protected PermissionBlueprintInfoTranslatorTest permTranslatorTest =
        new PermissionBlueprintInfoTranslatorTest();

    protected UserInfoTranslatorTest userTranslatorTest = new UserInfoTranslatorTest();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        this.userTranslatorTest.initModelTranslator(modelTranslator);
        this.permTranslatorTest.initModelTranslator(modelTranslator);

        modelTranslator.registerTranslator(this.translator, RoleInfo.class, RoleDTO.class);
    }

    @Override
    protected RoleInfoTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected RoleInfo initSourceObject() {
        // Role is a RoleInfo
        Role source = new Role();

        Set<User> users = new HashSet<>();
        Set<PermissionBlueprint> permissions = new HashSet<>();

        for (int i = 0; i < 5; ++i) {
            User user = (User) this.userTranslatorTest.initSourceObject();
            user.setId("test-user-" + i);

            PermissionBlueprint permission = (PermissionBlueprint) this.permTranslatorTest.initSourceObject();
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
    protected void verifyOutput(RoleInfo source, RoleDTO dest, boolean childrenGenerated) {
        if (source != null) {
            // Service model objects don't provide IDs
            assertNull(dest.getId());

            assertEquals(source.getName(), dest.getName());

            if (childrenGenerated) {
                // Verify users
                for (UserInfo user : source.getUsers()) {
                    for (UserDTO userDto : dest.getUsers()) {
                        assertNotNull(user.getUsername());
                        assertNotNull(userDto.getUsername());

                        if (user.getUsername().equals(userDto.getUsername())) {
                            this.userTranslatorTest.verifyOutput(user, userDto, childrenGenerated);
                        }
                    }
                }

                // Verify permissions
                int matches = 0;
                for (PermissionBlueprintInfo pinfo : source.getPermissions()) {
                    for (PermissionBlueprintDTO pdto : dest.getPermissions()) {
                        // Since we don't have an ID to use for comparison, we have to just
                        // compare all fields and hope for the best.

                        String piOwnerKey = pinfo.getOwner() != null ? pinfo.getOwner().getKey() : null;
                        String pdOwnerKey = pdto.getOwner() != null ? pdto.getOwner().getKey() : null;

                        EqualsBuilder builder = new EqualsBuilder()
                            .append(pinfo.getTypeName(), pdto.getType())
                            .append(pinfo.getAccessLevel(), pdto.getAccess())
                            .append(piOwnerKey, pdOwnerKey);

                        if (builder.isEquals()) {
                            ++matches;
                            break;
                        }
                    }
                }

                assertEquals("Permission object mismatch", matches, source.getPermissions().size());
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
