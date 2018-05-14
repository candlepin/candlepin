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

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.TimestampedEntityTranslator;
import org.candlepin.model.PermissionBlueprint;
import org.candlepin.model.Role;
import org.candlepin.model.User;

import java.util.Collection;
import java.util.Collections;



/**
 * The RoleTranslator provides translation from Role model objects to
 * RoleDTOs
 */
public class RoleTranslator extends TimestampedEntityTranslator<Role, RoleDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public RoleDTO translate(Role source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RoleDTO translate(ModelTranslator translator, Role source) {
        return source != null ? this.populate(translator, source, new RoleDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RoleDTO populate(Role source, RoleDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RoleDTO populate(ModelTranslator translator, Role source, RoleDTO dest) {
        dest = super.populate(translator, source, dest);

        dest.setId(source.getId());
        dest.setName(source.getName());

        if (translator != null) {
            Collection<User> users = source.getUsers();
            dest.setUsers(Collections.<UserDTO>emptyList());

            if (users != null) {
                for (User user : users) {
                    dest.addUser(translator.translate(user, UserDTO.class));
                }
            }

            Collection<PermissionBlueprint> permissions = source.getPermissions();
            dest.setPermissions(Collections.<PermissionBlueprintDTO>emptyList());

            if (permissions != null) {
                for (PermissionBlueprint permission : permissions) {
                    dest.addPermission(translator.translate(permission, PermissionBlueprintDTO.class));
                }
            }
        }
        else {
            dest.setUsers(Collections.<UserDTO>emptyList());
            dest.setPermissions(Collections.<PermissionBlueprintDTO>emptyList());
        }

        return dest;
    }

}
