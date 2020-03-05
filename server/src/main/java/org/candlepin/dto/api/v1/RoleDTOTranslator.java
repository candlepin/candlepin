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

import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.model.PermissionBlueprint;
import org.candlepin.model.Role;
import org.candlepin.model.User;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The RoleDTOTranslator provides translation from RoleDTO to Role model objects.
 */
public class RoleDTOTranslator implements ObjectTranslator<RoleDTO, Role> {

    @Override
    public Role translate(RoleDTO source) {
        return this.translate(null, source);
    }

    @Override
    public Role translate(ModelTranslator modelTranslator, RoleDTO source) {
        return source != null ? this.populate(modelTranslator, source, new Role()) : null;
    }

    @Override
    public Role populate(RoleDTO source, Role destination) {
        return this.populate(null, source, destination);
    }

    @Override
    public Role populate(ModelTranslator modelTranslator, RoleDTO source, Role destination) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (destination == null) {
            throw new IllegalArgumentException("destination is null");
        }

        destination.setCreated(source.getCreated() != null ?
            new Date(source.getCreated().toInstant().toEpochMilli()) : null);
        destination.setUpdated(source.getUpdated() != null ?
            new Date(source.getUpdated().toInstant().toEpochMilli()) : null);
        destination.setId(source.getId());
        destination.setName(source.getName());

        if (modelTranslator != null) {
            // Users
            Set<UserDTO> users = source.getUsers();

            if (users != null) {
                destination.setUsers(users.stream()
                    .map(modelTranslator.getStreamMapper(UserDTO.class, User.class))
                    .collect(Collectors.toSet()));
            }
            else {
                destination.setUsers(Collections.emptySet());
            }

            // Permissions
            List<PermissionBlueprintDTO> permissions = source.getPermissions();

            if (permissions != null) {
                destination.setPermissions(permissions.stream()
                    .map(modelTranslator.getStreamMapper(PermissionBlueprintDTO.class,
                    PermissionBlueprint.class))
                    .collect(Collectors.toSet()));
            }
            else {
                destination.setPermissions(Collections.emptySet());
            }
        }
        else {
            destination.setUsers(Collections.emptySet());
            destination.setPermissions(Collections.emptySet());
        }

        return destination;
    }
}
