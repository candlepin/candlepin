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
import org.candlepin.model.Owner;
import org.candlepin.model.Owner_;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Predicate;



/**
 * Permission allowing consumers to view their owner's service levels.
 *
 * NOTE: consumer principals do not get full read access to their owner.
 */
public class ConsumerServiceLevelsPermission extends TypedPermission<Owner> {

    private final Consumer consumer;
    private final Owner owner;

    public ConsumerServiceLevelsPermission(Consumer consumer, Owner owner) {
        this.consumer = consumer;
        this.owner = owner;
        this.access = Access.READ_ONLY;
    }

    @Override
    public Class<Owner> getTargetType() {
        return Owner.class;
    }

    @Override
    public boolean canAccessTarget(Owner target, SubResource subResource, Access required) {
        return target.getKey().equals(owner.getKey()) &&
            subResource.equals(SubResource.SERVICE_LEVELS) && access.provides(required);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> Predicate getQueryRestriction(Class<T> entityClass, CriteriaBuilder builder, From<?, T> path) {
        if (Owner.class.equals(entityClass)) {
            return builder.equal(((From<?, Owner>) path).get(Owner_.key), this.getOwner().getKey());
        }

        return null;
    }

    @Override
    public Owner getOwner() {
        return owner;
    }

    public Consumer getConsumer() {
        return consumer;
    }

}
