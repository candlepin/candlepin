/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.auth;

import org.fedoraproject.candlepin.util.Util;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementCertificate;
import org.fedoraproject.candlepin.model.Owner;

import org.fedoraproject.candlepin.model.Permission;
import org.fedoraproject.candlepin.model.Pool;

/**
 * An entity interacting with Candlepin
 */
public abstract class Principal implements Serializable {

    public static final String USER_TYPE = "user";
    public static final String CONSUMER_TYPE = "consumer";
    public static final String NO_AUTH_TYPE = "no_auth";
    public static final String SYSTEM_TYPE = "system";

    private Collection<Permission> permissions = new HashSet<Permission>();

    public Principal(Collection<Permission> permissions) {
        this.permissions = permissions;

        if (this.permissions == null) {
            this.permissions = new HashSet<Permission>();
        }
    }

    public boolean isSuperAdmin() {
        for (Permission permission : this.permissions) {
            if (permission.getVerb().equals(Verb.SUPER_ADMIN)) {
                return true;
            }
        }

        return false;
    }

    public abstract String getType();

    public Collection<Permission> getPermissions() {
        return permissions;
    }

    public void setPermissions(Collection<Permission> permissions) {
        this.permissions = permissions;
    }
    
    public String getPrincipalName() {
        return "";
    }
    
    public PrincipalData getData() {
        return new PrincipalData(getPermissions(), this.getType(),
                this.getPrincipalName());
    }

    @Override
    public String toString() {
        return Util.toJson(this.getData());
    }

    // Access permissions

    public abstract boolean canAccess(Owner owner);

    public abstract boolean canAccess(Consumer consumer);

    public abstract boolean canAccess(Entitlement entitlement);

    public abstract boolean canAccess(EntitlementCertificate entitlementCert);

    public abstract boolean canAccess(Pool pool);

}
