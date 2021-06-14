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
import org.candlepin.dto.ObjectTranslator;
import org.candlepin.service.model.PermissionBlueprintInfo;
import org.candlepin.service.model.RoleInfo;
import org.candlepin.service.model.UserInfo;
import org.candlepin.util.Util;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;



/**
 * The RoleInfoTranslator provides translation from Role model objects to
 * RoleDTOs
 */
public class RoleInfoTranslator implements ObjectTranslator<RoleInfo, RoleDTO> {

    /**
     * {@inheritDoc}
     */
    @Override
    public RoleDTO translate(RoleInfo source) {
        return this.translate(null, source);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RoleDTO translate(ModelTranslator translator, RoleInfo source) {
        return source != null ? this.populate(translator, source, new RoleDTO()) : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RoleDTO populate(RoleInfo source, RoleDTO destination) {
        return this.populate(null, source, destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("checkstyle:indentation")
    public RoleDTO populate(ModelTranslator translator, RoleInfo source, RoleDTO dest) {
        if (source == null) {
            throw new IllegalArgumentException("source is null");
        }

        if (dest == null) {
            throw new IllegalArgumentException("dest is null");
        }

        dest.id(null) // Service model objects don't provide an ID
            .created(Util.toDateTime(source.getCreated()))
            .updated(Util.toDateTime(source.getUpdated()))
            .name(source.getName());

        if (translator != null) {
            // Users
            Collection<? extends UserInfo> users = source.getUsers();

            if (users != null) {
                dest.setUsers(users.stream()
                    .map(translator.getStreamMapper(UserInfo.class, UserDTO.class))
                    .collect(Collectors.toSet()));
            }
            else {
                dest.setUsers(Collections.<UserDTO>emptySet());
            }

            // Permissions
            Collection<? extends PermissionBlueprintInfo> permissions = source.getPermissions();

            if (permissions != null) {
                dest.setPermissions(permissions.stream()
                    .map(translator.getStreamMapper(PermissionBlueprintInfo.class,
                        PermissionBlueprintDTO.class))
                    .collect(Collectors.toList()));
            }
            else {
                dest.setPermissions(Collections.<PermissionBlueprintDTO>emptyList());
            }
        }
        else {
            dest.setUsers(Collections.<UserDTO>emptySet());
            dest.setPermissions(Collections.<PermissionBlueprintDTO>emptyList());
        }

        return dest;
    }

}
