/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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

package org.candlepin.spec.bootstrap.data.util;

import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.RoleDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.resource.client.v1.RolesApi;
import org.candlepin.resource.client.v1.UsersApi;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.data.builder.Roles;

import org.jetbrains.annotations.NotNull;

public final class UserUtil {

    private UserUtil() {
        throw new UnsupportedOperationException();
    }

    public static UserDTO createAdminUser(ApiClient client, OwnerDTO owner) {
        return createUsers(client, true, Roles.all(owner));
    }

    public static UserDTO createUser(ApiClient client, OwnerDTO owner) {
        return createUsers(client, false, Roles.all(owner));
    }

    public static UserDTO createReadOnlyUser(ApiClient client, OwnerDTO owner) {
        return createUsers(client, false, Roles.readOnly(owner));
    }

    @NotNull
    private static UserDTO createUsers(
        ApiClient client, boolean superAdmin, RoleDTO role) {
        RolesApi roles = client.roles();
        UsersApi usersClient = client.users();
        UserDTO user = new UserDTO()
            .username(StringUtil.random("test_user"))
            .password("password")
            .superAdmin(superAdmin);
        usersClient.createUser(user);
        RoleDTO userRole = roles.createRole(role);
        roles.addUserToRole(userRole.getName(), user.getUsername());

        return user;
    }

}
