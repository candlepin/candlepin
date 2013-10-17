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
import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;
import org.candlepin.model.User;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Restrictions;

/**
 * A permission allowing a user access to consumers in their org only if they were the ones
 * to register them, as determined by the username on the consumer.
 *
 * Access can be used to determine if this is READ_ONLY or ALL access.
 */
public class UsersConsumersPermission extends TypedPermission<Consumer> {

    private User user;
    private Owner owner;

    public UsersConsumersPermission(User u, Owner o, Access a) {
        this.user = u;
        this.owner = o;
        this.access = a;
    }

    @Override
    public Class<Consumer> getTargetType() {
        return Consumer.class;
    }

    @Override
    public boolean canAccessTarget(Consumer target, Access action) {
        return target.getOwner().getKey().equals(owner.getKey()) &&
            target.getUsername().equals(user.getUsername()) && providesAccess(action);
    }

    /* (non-Javadoc)
     * @see org.candlepin.auth.permissions.Permission#getCriteriaRestrictions(java.lang.Class)
     */
    @Override
    public Criterion getCriteriaRestrictions(Class entityClass) {
        if (entityClass.equals(Consumer.class)) {
            return Restrictions.eq("username", user.getUsername());
        }
        return null;
    }
}
