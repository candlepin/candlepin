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

import org.candlepin.ApiException;
import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.dto.api.v1.RoleDTO;
import org.candlepin.dto.api.v1.UserDTO;
import org.candlepin.resource.RolesApi;
import org.candlepin.resource.UsersApi;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.data.builder.Roles;

public final class UserUtil {

    private UserUtil() {
        throw new UnsupportedOperationException();
    }

    public static UserDTO createUser(ApiClient client, OwnerDTO owner) throws ApiException {
        RolesApi roles = client.roles();
        UsersApi usersClient = client.users();
        UserDTO user = new UserDTO()
            .username(StringUtil.random("test_user"))
            .password("password")
            .superAdmin(false);
        usersClient.createUser(user);
        RoleDTO role = roles.createRole(Roles.admin(owner));
        roles.addUserToRole(role.getName(), user.getUsername());

        return user;
    }

}
