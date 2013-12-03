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

import org.candlepin.auth.Access;
import org.candlepin.auth.SubResource;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Restrictions;

/**
 * Allows viewing and attaching a subscription for a specific pool in an org.
 *
 * Must be combined with another permission to list all pools in the org.
 */
public class AttachPermission extends TypedPermission<Pool> {

    private Owner owner;

    public AttachPermission(Owner owner) {
        this.owner = owner;
    }

    @Override
    public Class<Pool> getTargetType() {
        return Pool.class;
    }

    @Override
    public boolean canAccessTarget(Pool target, SubResource subResource,
        Access required) {

        // Allow viewing a specific pool:
        if (subResource.equals(SubResource.NONE) && Access.READ_ONLY.equals(required)) {
            return target.getOwner().getKey().equals(owner.getKey());
        }

        // Allow subscribing to a pool:
        else if (subResource.equals(SubResource.ENTITLEMENTS) &&
            Access.CREATE.equals(required)) {
            return target.getOwner().getKey().equals(owner.getKey());
        }

        return false;
    }

    @Override
    public Criterion getCriteriaRestrictions(Class entityClass) {
        if (entityClass.equals(Pool.class)) {
            return Restrictions.eq("owner", owner);
        }

        return null;
    }

    @Override
    public Owner getOwner() {
        return owner;
    }

}
