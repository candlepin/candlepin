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

import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Restrictions;

/**
 * ConsumerOrgHypervisorPermission
 * Permission allowing consumers to check in hypervisors for their owner.
 *
 * NOTE: consumer principals do not get full read access to their owner,
 * this should only provide permission for hypervisorCheckIn.
 */
public class ConsumerOrgHypervisorPermission extends TypedPermission<Owner> {

    private Owner owner;

    public ConsumerOrgHypervisorPermission(Owner owner) {
        this.owner = owner;
    }

    @Override
    public Class<Owner> getTargetType() {
        return Owner.class;
    }

    @Override
    public boolean canAccessTarget(Owner target, SubResource subResource,
        Access required) {
        return subResource.equals(SubResource.HYPERVISOR) &&
            Access.READ_ONLY.provides(required) &&
            this.owner.getKey().equals(target.getKey());
    }

    @Override
    public Criterion getCriteriaRestrictions(Class entityClass) {
        if (entityClass.equals(Owner.class)) {
            return Restrictions.eq("key", owner.getKey());
        }
        return null;
    }

    @Override
    public Owner getOwner() {
        return owner;
    }
}
