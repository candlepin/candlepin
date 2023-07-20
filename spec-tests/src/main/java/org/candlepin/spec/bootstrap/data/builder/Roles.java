/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.spec.bootstrap.data.builder;

import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PermissionBlueprintDTO;
import org.candlepin.dto.api.client.v1.RoleDTO;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import java.util.List;



public final class Roles {

    private Roles() {
        throw new UnsupportedOperationException();
    }

    public static RoleDTO ownerAll(OwnerDTO owner) {
        return with(Permissions.OWNER.all(owner));
    }

    public static RoleDTO ownerReadOnly(OwnerDTO owner) {
        return with(Permissions.OWNER.readOnly(owner));
    }

    public static RoleDTO with(PermissionBlueprintDTO... permissions) {
        return new RoleDTO()
            .name(StringUtil.random("test-role"))
            .permissions(List.of(permissions));
    }

}
