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
import org.candlepin.auth.SubResource;
import org.candlepin.model.Consumer;
import org.candlepin.model.Environment;
import org.candlepin.model.Owned;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.activationkeys.ActivationKey;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Restrictions;

/**
 * A permission represents an owner to be accessed in some fashion, and a verb which
 * the permission is granting.
 */
public class OwnerPermission implements Permission, Serializable {

    private final Owner owner;

    private final Access access;

    public OwnerPermission(Owner owner, Access access) {
        this.owner = owner;
        this.access = access;
    }

    @Override
    public boolean canAccess(Object target, SubResource subResource,
        Access requiredAccess) {
        if (target instanceof Owned) {
            // First make sure the owner matches:
            if (owner.getKey().equals(((Owned) target).getOwner().getKey()) &&
                access.provides(requiredAccess)) {
                return true;
            }
        }

        // If asked to verify access to an object that does not implement Owned,
        // as far as this permission goes, we probably have to deny access.
        return false;
    }

    @Override
    public Criterion getCriteriaRestrictions(Class entityClass) {
        if (entityClass.equals(Owner.class)) {
            return Restrictions.eq("key", owner.getKey());
        }
        else if (entityClass.equals(Consumer.class)) {
            return Restrictions.eq("owner", owner);
        }
        else if (entityClass.equals(Pool.class)) {
            return Restrictions.eq("owner", owner);
        }
        else if (entityClass.equals(ActivationKey.class)) {
            return Restrictions.eq("owner", owner);
        }
        else if (entityClass.equals(Environment.class)) {
            return Restrictions.eq("owner", owner);
        }

        return null;
    }

    @Override
    public Owner getOwner() {
        return owner;
    }

    public Access getAccess() {
        return access;
    }

}
