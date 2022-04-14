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

package org.candlepin.spec.bootstrap.data.builder;

import org.candlepin.dto.api.v1.OwnerDTO;
import org.candlepin.dto.api.v1.PermissionBlueprintDTO;
import org.candlepin.dto.api.v1.RoleDTO;
import org.candlepin.spec.bootstrap.data.util.StringUtil;

import java.util.List;

public final class Roles {

    private Roles() {
        throw new UnsupportedOperationException();
    }

    public static RoleDTO admin(OwnerDTO owner) {
        return createRole(owner, "ALL");
    }

    public static RoleDTO readOnly(OwnerDTO owner) {
        return createRole(owner, "READ_ONLY");
    }

    private static RoleDTO createRole(OwnerDTO owner, String access) {
        List<PermissionBlueprintDTO> permissions = List.of(new PermissionBlueprintDTO()
            .owner(Owners.toNested(owner))
            .type("OWNER")
            .access(access)
        );
        return new RoleDTO()
            .name(StringUtil.random("test-role"))
            .permissions(permissions);
    }
}
