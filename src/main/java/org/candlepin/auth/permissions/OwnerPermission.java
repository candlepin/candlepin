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

import java.io.Serializable;

import org.candlepin.auth.Access;
import org.candlepin.model.Owned;
import org.candlepin.model.Owner;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Restrictions;

/**
 * A permission represents an owner to be accessed in some fashion, and a verb which
 * the permissions is granting.
 */
public class OwnerPermission implements Permission, Serializable {

    private Owner owner;

    private Access access;

    public OwnerPermission(Owner owner, Access access) {
        this.owner = owner;
        this.access = access;
    }

    @Override
    public boolean canAccess(Object target, Access requiredAccess) {
        if (target instanceof Owned) {
            // First make sure the owner matches:
            if (owner.getKey().equals(((Owned) target).getOwner().getKey()) &&
                providesAccess(requiredAccess)) {
                return true;
            }
        }

        // If asked to verify access to an object that does not implement Owned,
        // as far as this permission goes, we probably have to deny access.
        return false;
    }

    /**
     * Return true if this permission provides the requested access type.
     * If we have ALL, assume a match, otherwise do an explicit comparison.
     *
     * @return true if we provide the given access level.
     */
    public boolean providesAccess(Access requiredAccess) {
        return (this.access == Access.ALL || this.access == requiredAccess);
    }

    @Override
    public Criterion getCriteriaRestrictions(Class entityClass) {
        if (entityClass.equals(Owner.class)) {
            return Restrictions.eq("key", owner.getKey());
        }
        // TODO: Since this is not a typed permission, it would be good to do some
        // filtering for other classes here as well;
        return null;
    }

    @Override
    public Owner getOwner() {
        return owner;
    }
}
