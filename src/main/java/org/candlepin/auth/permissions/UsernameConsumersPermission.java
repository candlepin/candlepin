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
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.User;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Restrictions;

/**
 * A permission allowing a user access to consumers in their org only if they were the ones
 * to register them, as determined by the username on the consumer.
 *
 * Assumes the user has full access to those consumers including creation, update,
 * and deletion.
 *
 * Allows the user to create and manage entitlements for the consumer as well.
 */
public class UsernameConsumersPermission implements Permission {

    private final User user;
    private final Owner owner;

    public UsernameConsumersPermission(User u, Owner o) {
        this.user = u;
        this.owner = o;
    }


    @Override
    public boolean canAccess(Object target, SubResource subResource, Access required) {

        // Implied full access to the relevant Consumers:
        if (target.getClass().equals(Consumer.class)) {
            return ((Consumer) target).getOwner().getKey().equals(owner.getKey()) &&
                ((Consumer) target).getUsername().equals(user.getUsername()) &&
                Access.ALL.provides(required);
        }

        // TODO: Should this be broken out into two typed permissions, one for Consumer,
        // one for Owner + subresource of consumers?

        // Implied create access to the owner's consumers collection, which includes
        // read as well:
        if (target.getClass().equals(Owner.class) &&
            subResource.equals(SubResource.CONSUMERS) &&
            Access.CREATE.provides(required)) {
            return true;
        }

        // Must allow the user to manage their system's entitlements.
        if (target.getClass().equals(Entitlement.class)) {
            Entitlement ent = (Entitlement) target;
            if (ent.getConsumer().getUsername().equals(user.getUsername()) &&
                ent.getOwner().getKey().equals(owner.getKey())) {
                return true;
            }
        }

        return false;
    }

    @Override
    public Criterion getCriteriaRestrictions(Class entityClass) {
        if (entityClass.equals(Consumer.class)) {
            return Restrictions.eq("username", user.getUsername());
        }
        return null;
    }

    @Override
    public Owner getOwner() {
        return owner;
    }

    public String getUsername() {
        return user.getUsername();
    }

}
