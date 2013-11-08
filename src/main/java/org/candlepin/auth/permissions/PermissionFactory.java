/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.auth.permissions;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.candlepin.model.PermissionBlueprint;

import com.google.inject.Inject;

/**
 * PermissionFactory: Creates concrete Java permission classes based on the
 * permission blueprints from the database.
 */
public class PermissionFactory {

    /**
     * PermissionType: Key used to determine which class to create.
     */
    public enum PermissionType {
        OWNER,
        USERS_CONSUMERS
    }

    @Inject
    public PermissionFactory() {
    }

    public List<Permission> createPermissions(Collection<PermissionBlueprint> dbPerms) {
        List<Permission> perms = new LinkedList<Permission>();
        for (PermissionBlueprint hint : dbPerms) {
            perms.add(createPermission(hint));
        }
        return perms;
    }

    public Permission createPermission(PermissionBlueprint permBp) {
        switch (permBp.getType()) {
        // TODO: what if an entity isn't found?
            case OWNER:
                Permission p = new OwnerPermission(permBp.getOwner(),
                    permBp.getAccess());
                return p;
            default:
                return null; // TODO
        }
    }
}
