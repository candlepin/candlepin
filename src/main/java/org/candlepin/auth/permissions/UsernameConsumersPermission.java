/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
import org.candlepin.model.Consumer_;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.service.model.UserInfo;

import java.io.Serializable;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Predicate;



/**
 * A permission allowing a user access to consumers in their org only if they were the ones
 * to register them, as determined by the username on the consumer.
 *
 * Assumes the user has full access to those consumers including creation, update,
 * and deletion.
 *
 * Allows the user to create and manage entitlements for the consumer as well.
 */
public class UsernameConsumersPermission implements Permission, Serializable {
    private static final long serialVersionUID = 571612156736570455L;

    private final UserInfo user;
    private final Owner owner;

    public UsernameConsumersPermission(UserInfo user, Owner owner) {
        this.user = user;
        this.owner = owner;
    }


    @Override
    public boolean canAccess(Object target, SubResource subResource, Access required) {
        // Implied full access to the relevant Consumers:
        if (target.getClass().equals(Consumer.class)) {
            return ((Consumer) target).getOwnerId().equals(owner.getId()) &&
                ((Consumer) target).getUsername().equals(user.getUsername()) &&
                Access.ALL.provides(required);
        }

        // TODO: Should this be broken out into two typed permissions, one for Consumer,
        // one for Owner + subresource of consumers?

        // Implied create access to the owner's consumers collection, which includes
        // read as well:
        if (target.getClass().equals(Owner.class) && subResource.equals(SubResource.CONSUMERS) &&
            Access.CREATE.provides(required) && ((Owner) target).getId().equals(owner.getId())) {
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

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> Predicate getQueryRestriction(Class<T> entityClass, CriteriaBuilder builder, From<?, T> path) {
        if (Consumer.class.equals(entityClass)) {
            return builder.equal(((From<?, Consumer>) path).get(Consumer_.username), this.getUsername());
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
