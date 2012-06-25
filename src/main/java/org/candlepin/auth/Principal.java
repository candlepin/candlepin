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
package org.candlepin.auth;

import org.apache.log4j.Logger;
import org.candlepin.auth.permissions.Permission;
import org.candlepin.util.Util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * An entity interacting with Candlepin
 */
public abstract class Principal implements Serializable {

    private static Logger log = Logger.getLogger(Principal.class);
    protected List<Permission> permissions = new ArrayList<Permission>();

    public abstract String getType();

    public abstract boolean hasFullAccess();

    protected void addPermission(Permission permission) {
        this.permissions.add(permission);
    }

    public boolean canAccess(Object target, Access access) {
        log.debug(this.getClass().getName() + " principal checking for access to: " +
            target);

        if (hasFullAccess()) {
            return true;
        }

        for (Permission permission : permissions) {
            log.debug(" perm class: " + permission.getClass().getName());
            if (permission.canAccess(target, access)) {
                log.debug("  permission granted");
                // if any of the principal's permissions allows access, then
                // we are good to go
                return true;
            }
        }

        // none of the permissions grants access, so this target is not allowed
        log.warn("Refused principal: '" + getPrincipalName() + "' access to: " +
            target.getClass().getName());
        return false;
    }

    public abstract String getPrincipalName();

    public PrincipalData getData() {
        return new PrincipalData(this.getType(), this.getPrincipalName());
    }

    /**
     * @return Username for this principal, null if there is not one.
     */
    public String getUsername() {
        return null;
    }

    @Override
    public String toString() {
        return Util.toJson(this.getData());
    }

}
